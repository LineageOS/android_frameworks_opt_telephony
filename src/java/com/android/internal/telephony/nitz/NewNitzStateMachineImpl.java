/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.nitz;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.timedetector.PhoneTimeSuggestion;
import android.content.Context;
import android.telephony.Rlog;
import android.util.TimestampedValue;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.NitzData;
import com.android.internal.telephony.NitzStateMachine;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TimeZoneLookupHelper;
import com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

// TODO Update this comment when NitzStateMachineImpl is deleted - it will no longer be appropriate
// to contrast the behavior of the two implementations.
/**
 * A new and more testable implementation of {@link NitzStateMachine}. It is intended to replace
 * {@link com.android.internal.telephony.NitzStateMachineImpl}.
 *
 * <p>This implementation differs in a number of ways:
 * <ul>
 *     <li>It is decomposed into multiple classes that perform specific, well-defined, usually
 *     stateless, testable behaviors.
 *     </li>
 *     <li>It splits responsibility for setting the device time zone with a "time zone detection
 *     service". The time zone detection service is stateful, recording the latest suggestion from
 *     possibly multiple sources. The {@link NewNitzStateMachineImpl} must now actively signal when
 *     it has no answer for the current time zone, allowing the service to arbitrate between
 *     multiple sources without polling each of them.
 *     </li>
 *     <li>Rate limiting of NITZ signals is performed for time zone as well as time detection.</li>
 * </ul>
 */
public final class NewNitzStateMachineImpl implements NitzStateMachine {

    /**
     * An interface for predicates applied to incoming NITZ signals to determine whether they must
     * be processed. See {@link NitzSignalInputFilterPredicateFactory#create(Context, DeviceState)}
     * for the real implementation. The use of an interface means the behavior can be tested
     * independently and easily replaced for tests.
     */
    @VisibleForTesting
    @FunctionalInterface
    public interface NitzSignalInputFilterPredicate {

        /**
         * See {@link NitzSignalInputFilterPredicate}.
         */
        boolean mustProcessNitzSignal(
                @Nullable TimestampedValue<NitzData> oldSignal,
                @NonNull TimestampedValue<NitzData> newSignal);
    }

    /**
     * An interface for the stateless component that generates suggestions using country and/or NITZ
     * information. The use of an interface means the behavior can be tested independently.
     */
    @VisibleForTesting
    public interface TimeZoneSuggester {

        /**
         * Generates a {@link PhoneTimeZoneSuggestion} given the information available. This method
         * must always return a non-null {@link PhoneTimeZoneSuggestion} but that object does not
         * have to contain a time zone if the available information is not sufficient to determine
         * one. {@link PhoneTimeZoneSuggestion#getDebugInfo()} provides debugging / logging
         * information explaining the choice.
         */
        @NonNull
        PhoneTimeZoneSuggestion getTimeZoneSuggestion(
                int phoneId, @Nullable String countryIsoCode,
                @Nullable TimestampedValue<NitzData> nitzSignal);
    }

    static final String LOG_TAG = "NewNitzStateMachineImpl";
    static final boolean DBG = true;

    // Miscellaneous dependencies and helpers not related to detection state.
    private final int mPhoneId;
    /** Accesses global information about the device. */
    private final DeviceState mDeviceState;
    /** Applied to NITZ signals during input filtering. */
    private final NitzSignalInputFilterPredicate mNitzSignalInputFilter;
    /** Creates {@link PhoneTimeZoneSuggestion} for passing to the time zone detection service. */
    private final TimeZoneSuggester mTimeZoneSuggester;
    /** A facade to the time / time zone detection services. */
    private final NewTimeServiceHelper mNewTimeServiceHelper;

    // Shared detection state.

    /**
     * The last / latest NITZ signal <em>processed</em> (i.e. after input filtering). It is used for
     * input filtering (e.g. rate limiting) and provides the NITZ information when time / time zone
     * needs to be recalculated when something else has changed.
     */
    @Nullable
    private TimestampedValue<NitzData> mLatestNitzSignal;

    // Time Zone detection state.

    /**
     * Records whether the device should have a country code available via
     * {@link DeviceState#getNetworkCountryIsoForPhone()}. Before this an NITZ signal
     * received is (almost always) not enough to determine time zone. On test networks the country
     * code should be available but can still be an empty string but this flag indicates that the
     * information available is unlikely to improve.
     */
    private boolean mGotCountryCode = false;

    /**
     * Creates an instance for the supplied {@link Phone}.
     */
    public static NewNitzStateMachineImpl createInstance(@NonNull Phone phone) {
        Objects.requireNonNull(phone);

        int phoneId = phone.getPhoneId();
        DeviceState deviceState = new DeviceStateImpl(phone);
        TimeZoneLookupHelper timeZoneLookupHelper = new TimeZoneLookupHelper();
        TimeZoneSuggester timeZoneSuggester =
                new TimeZoneSuggesterImpl(deviceState, timeZoneLookupHelper);
        NewTimeServiceHelper newTimeServiceHelper = new NewTimeServiceHelperImpl(phone);
        NitzSignalInputFilterPredicate nitzSignalFilter =
                NitzSignalInputFilterPredicateFactory.create(phone.getContext(), deviceState);
        return new NewNitzStateMachineImpl(
                phoneId, nitzSignalFilter, timeZoneSuggester, newTimeServiceHelper, deviceState);
    }

    /**
     * Creates an instance using the supplied components. Used during tests to supply fakes.
     * See {@link #createInstance(Phone)}
     */
    @VisibleForTesting
    public NewNitzStateMachineImpl(int phoneId,
            @NonNull NitzSignalInputFilterPredicate nitzSignalInputFilter,
            @NonNull TimeZoneSuggester timeZoneSuggester,
            @NonNull NewTimeServiceHelper newTimeServiceHelper, @NonNull DeviceState deviceState) {
        mPhoneId = phoneId;
        mTimeZoneSuggester = Objects.requireNonNull(timeZoneSuggester);
        mNewTimeServiceHelper = Objects.requireNonNull(newTimeServiceHelper);
        mDeviceState = Objects.requireNonNull(deviceState);
        mNitzSignalInputFilter = Objects.requireNonNull(nitzSignalInputFilter);
    }

    @Override
    public void handleNetworkAvailable() {
        // We no longer do any useful work here: we assume handleNetworkUnavailable() is reliable.
        // TODO: Remove this method when all implementations do nothing.
    }

    @Override
    public void handleNetworkUnavailable() {
        String reason = "handleNetworkUnavailable()";
        clearNetworkStateAndRerunDetection(reason);
    }

    private void clearNetworkStateAndRerunDetection(String reason) {
        // Assume any previous NITZ signals received are now invalid.
        mLatestNitzSignal = null;

        String countryIsoCode =
                mGotCountryCode ? mDeviceState.getNetworkCountryIsoForPhone() : null;

        if (DBG) {
            Rlog.d(LOG_TAG, reason + ": countryIsoCode=" + countryIsoCode);
        }

        // Generate a new time zone suggestion and update the service as needed.
        doTimeZoneDetection(countryIsoCode, null /* nitzSignal */, reason);

        // Generate a new time suggestion and update the service as needed.
        doTimeDetection(null /* nitzSignal */, reason);
    }

    @Override
    public void handleNetworkCountryCodeSet(boolean countryChanged) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: countryChanged=" + countryChanged
                    + ", mLatestNitzSignal=" + mLatestNitzSignal);
        }
        mGotCountryCode = true;

        // Generate a new time zone suggestion and update the service as needed.
        String countryIsoCode = mDeviceState.getNetworkCountryIsoForPhone();
        doTimeZoneDetection(countryIsoCode, mLatestNitzSignal,
                "handleNetworkCountryCodeSet(" + countryChanged + ")");
    }

    @Override
    public void handleNetworkCountryCodeUnavailable() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleNetworkCountryCodeUnavailable:"
                    + " mLatestNitzSignal=" + mLatestNitzSignal);
        }
        mGotCountryCode = false;

        // Generate a new time zone suggestion and update the service as needed.
        doTimeZoneDetection(null /* countryIsoCode */, mLatestNitzSignal,
                "handleNetworkCountryCodeUnavailable()");
    }

    @Override
    public void handleNitzReceived(@NonNull TimestampedValue<NitzData> nitzSignal) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleNitzReceived: nitzSignal=" + nitzSignal);
        }
        Objects.requireNonNull(nitzSignal);

        // Perform input filtering to filter bad data and avoid processing signals too often.
        TimestampedValue<NitzData> previousNitzSignal = mLatestNitzSignal;
        if (!mNitzSignalInputFilter.mustProcessNitzSignal(previousNitzSignal, nitzSignal)) {
            return;
        }

        // Always store the latest valid NITZ signal to be processed.
        mLatestNitzSignal = nitzSignal;

        String reason = "handleNitzReceived(" + nitzSignal + ")";

        // Generate a new time zone suggestion and update the service as needed.
        String countryIsoCode =
                mGotCountryCode ? mDeviceState.getNetworkCountryIsoForPhone() : null;
        doTimeZoneDetection(countryIsoCode, nitzSignal, reason);

        // Generate a new time suggestion and update the service as needed.
        doTimeDetection(nitzSignal, reason);
    }

    @Override
    public void handleAirplaneModeChanged(boolean on) {
        // Treat entry / exit from airplane mode as a strong signal that the user wants to clear
        // cached state. If the user really is boarding a plane they won't want cached state from
        // before their flight influencing behavior.
        //
        // State is cleared on entry AND exit: on entry because the detection code shouldn't be
        // opinionated while in airplane mode, and on exit to avoid any unexpected signals received
        // while in airplane mode from influencing behavior afterwards.
        //
        // After clearing detection state, the time zone detection should work out from first
        // principles what the time / time zone is. This assumes calls like handleNetworkAvailable()
        // will be made after airplane mode is re-enabled as the device re-establishes network
        // connectivity.

        // Clear time zone detection state.
        mGotCountryCode = false;

        String reason = "handleAirplaneModeChanged(" + on + ")";
        clearNetworkStateAndRerunDetection(reason);
    }

    /**
     * Perform a round of time zone detection and notify the time zone detection service as needed.
     */
    private void doTimeZoneDetection(
            @Nullable String countryIsoCode, @Nullable TimestampedValue<NitzData> nitzSignal,
            @NonNull String reason) {
        try {
            Objects.requireNonNull(reason);

            PhoneTimeZoneSuggestion suggestion =
                    mTimeZoneSuggester.getTimeZoneSuggestion(mPhoneId, countryIsoCode, nitzSignal);
            suggestion.addDebugInfo("Detection reason=" + reason);
            if (DBG) {
                Rlog.d(LOG_TAG, "doTimeZoneDetection: countryIsoCode=" + countryIsoCode
                        + ", nitzSignal=" + nitzSignal + ", suggestion=" + suggestion
                        + ", reason=" + reason);
            }
            mNewTimeServiceHelper.maybeSuggestDeviceTimeZone(suggestion);
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "doTimeZoneDetection: Exception thrown"
                    + " mPhoneId=" + mPhoneId
                    + ", countryIsoCode=" + countryIsoCode
                    + ", nitzSignal=" + nitzSignal
                    + ", reason=" + reason
                    + ", ex=" + ex, ex);
        }
    }

    /**
     * Perform a round of time detection and notify the time detection service as needed.
     */
    private void doTimeDetection(@Nullable TimestampedValue<NitzData> nitzSignal,
            @NonNull String reason) {
        try {
            Objects.requireNonNull(reason);
            if (nitzSignal == null) {
                // Do nothing to withdraw previous suggestions: the service currently does not
                // support withdrawing suggestions.
                return;
            }

            Objects.requireNonNull(nitzSignal.getValue());

            TimestampedValue<Long> newNitzTime = new TimestampedValue<>(
                    nitzSignal.getReferenceTimeMillis(),
                    nitzSignal.getValue().getCurrentTimeInMillis());
            PhoneTimeSuggestion timeSuggestion = new PhoneTimeSuggestion(mPhoneId, newNitzTime);
            timeSuggestion.addDebugInfo("doTimeDetection: NITZ signal used"
                    + " nitzSignal=" + nitzSignal
                    + ", newNitzTime=" + newNitzTime
                    + ", reason=" + reason);
            mNewTimeServiceHelper.suggestDeviceTime(timeSuggestion);
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "doTimeDetection: Exception thrown"
                    + " mPhoneId=" + mPhoneId
                    + ", nitzSignal=" + nitzSignal
                    + ", reason=" + reason
                    + ", ex=" + ex, ex);
        }
    }

    @Override
    public void dumpState(PrintWriter pw) {
        pw.println(" NewNitzStateMachineImpl.mLatestNitzSignal=" + mLatestNitzSignal);
        pw.println(" NewNitzStateMachineImpl.mGotCountryCode=" + mGotCountryCode);
        mNewTimeServiceHelper.dumpState(pw);
        pw.flush();
    }

    @Override
    public void dumpLogs(FileDescriptor fd, IndentingPrintWriter ipw, String[] args) {
        mNewTimeServiceHelper.dumpLogs(ipw);
    }

    @Nullable
    public NitzData getCachedNitzData() {
        return mLatestNitzSignal != null ? mLatestNitzSignal.getValue() : null;
    }
}
