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

import static com.android.internal.telephony.TimeZoneLookupHelper.CountryResult.QUALITY_DEFAULT_BOOSTED;
import static com.android.internal.telephony.TimeZoneLookupHelper.CountryResult.QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS;
import static com.android.internal.telephony.TimeZoneLookupHelper.CountryResult.QUALITY_MULTIPLE_ZONES_SAME_OFFSET;
import static com.android.internal.telephony.TimeZoneLookupHelper.CountryResult.QUALITY_SINGLE_ZONE;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.createEmptySuggestion;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.TimestampedValue;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.NitzData;
import com.android.internal.telephony.NitzStateMachine.DeviceState;
import com.android.internal.telephony.TimeZoneLookupHelper;
import com.android.internal.telephony.TimeZoneLookupHelper.CountryResult;
import com.android.internal.telephony.nitz.NewNitzStateMachineImpl.TimeZoneSuggester;
import com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion;

import java.util.Objects;

/**
 * The real implementation of {@link TimeZoneSuggester}.
 */
@VisibleForTesting
public class TimeZoneSuggesterImpl implements TimeZoneSuggester {

    private static final String LOG_TAG = NewNitzStateMachineImpl.LOG_TAG;

    private final DeviceState mDeviceState;
    private final TimeZoneLookupHelper mTimeZoneLookupHelper;

    @VisibleForTesting
    public TimeZoneSuggesterImpl(
            @NonNull DeviceState deviceState, @NonNull TimeZoneLookupHelper timeZoneLookupHelper) {
        mDeviceState = Objects.requireNonNull(deviceState);
        mTimeZoneLookupHelper = Objects.requireNonNull(timeZoneLookupHelper);
    }

    @Override
    @NonNull
    public PhoneTimeZoneSuggestion getTimeZoneSuggestion(int phoneId,
            @Nullable String countryIsoCode, @Nullable TimestampedValue<NitzData> nitzSignal) {
        try {
            // Check for overriding NITZ-based signals from Android running in an emulator.
            PhoneTimeZoneSuggestion overridingSuggestion = null;
            if (nitzSignal != null) {
                NitzData nitzData = nitzSignal.getValue();
                if (nitzData.getEmulatorHostTimeZone() != null) {
                    overridingSuggestion = new PhoneTimeZoneSuggestion(phoneId);
                    overridingSuggestion.setZoneId(nitzData.getEmulatorHostTimeZone().getID());
                    overridingSuggestion.setMatchType(PhoneTimeZoneSuggestion.EMULATOR_ZONE_ID);
                    overridingSuggestion.setQuality(PhoneTimeZoneSuggestion.SINGLE_ZONE);
                    overridingSuggestion.addDebugInfo("Emulator time zone override: " + nitzData);
                }
            }

            PhoneTimeZoneSuggestion suggestion;
            if (overridingSuggestion != null) {
                suggestion = overridingSuggestion;
            } else if (countryIsoCode == null) {
                if (nitzSignal == null) {
                    suggestion = createEmptySuggestion(phoneId,
                            "getTimeZoneSuggestion: nitzSignal=null, countryIsoCode=null");
                } else {
                    // NITZ only - wait until we have a country.
                    suggestion = createEmptySuggestion(phoneId, "getTimeZoneSuggestion:"
                            + " nitzSignal=" + nitzSignal + ", countryIsoCode=null");
                }
            } else { // countryIsoCode != null
                if (nitzSignal == null) {
                    if (countryIsoCode.isEmpty()) {
                        // This is assumed to be a test network with no NITZ data to go on.
                        suggestion = createEmptySuggestion(phoneId,
                                "getTimeZoneSuggestion: nitzSignal=null, countryIsoCode=\"\"");
                    } else {
                        // Country only
                        suggestion = findTimeZoneFromNetworkCountryCode(
                                phoneId, countryIsoCode, mDeviceState.currentTimeMillis());
                    }
                } else { // nitzSignal != null
                    if (countryIsoCode.isEmpty()) {
                        // We have been told we have a country code but it's empty. This is most
                        // likely because we're on a test network that's using a bogus MCC
                        // (eg, "001"). Obtain a TimeZone based only on the NITZ parameters: without
                        // a country it will be arbitrary, but it should at least have the correct
                        // offset.
                        suggestion = findTimeZoneForTestNetwork(phoneId, nitzSignal);
                    } else {
                        // We have both NITZ and Country code.
                        suggestion = findTimeZoneFromCountryAndNitz(
                                phoneId, countryIsoCode, nitzSignal);
                    }
                }
            }

            // Ensure the return value is never null.
            Objects.requireNonNull(suggestion);

            return suggestion;
        } catch (RuntimeException e) {
            // This would suggest a coding error. Log at a high level and try to avoid leaving the
            // device in a bad state by making an "empty" suggestion.
            String message = "getTimeZoneSuggestion: Error during lookup: "
                    + " countryIsoCode=" + countryIsoCode
                    + ", nitzSignal=" + nitzSignal
                    + ", e=" + e.getMessage();
            PhoneTimeZoneSuggestion errorSuggestion = createEmptySuggestion(phoneId, message);
            errorSuggestion.addDebugInfo(message);
            Rlog.w(LOG_TAG, message, e);
            return errorSuggestion;
        }
    }

    /**
     * Creates a {@link PhoneTimeZoneSuggestion} using only NITZ. This happens when the device
     * is attached to a test cell with an unrecognized MCC. In these cases we try to return a
     * suggestion for an arbitrary time zone that matches the NITZ offset information.
     */
    @NonNull
    private PhoneTimeZoneSuggestion findTimeZoneForTestNetwork(
            int phoneId, @NonNull TimestampedValue<NitzData> nitzSignal) {
        Objects.requireNonNull(nitzSignal);
        NitzData nitzData = Objects.requireNonNull(nitzSignal.getValue());

        PhoneTimeZoneSuggestion result = new PhoneTimeZoneSuggestion(phoneId);
        result.addDebugInfo("findTimeZoneForTestNetwork: nitzSignal=" + nitzSignal);
        TimeZoneLookupHelper.OffsetResult lookupResult =
                mTimeZoneLookupHelper.lookupByNitz(nitzData);
        if (lookupResult == null) {
            result.addDebugInfo("findTimeZoneForTestNetwork: No zone found");
        } else {
            result.setZoneId(lookupResult.getTimeZone().getID());
            result.setMatchType(PhoneTimeZoneSuggestion.TEST_NETWORK_OFFSET_ONLY);
            int quality = lookupResult.getIsOnlyMatch() ? PhoneTimeZoneSuggestion.SINGLE_ZONE
                    : PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_SAME_OFFSET;
            result.setQuality(quality);
            result.addDebugInfo("findTimeZoneForTestNetwork: lookupResult=" + lookupResult);
        }
        return result;
    }

    /**
     * Creates a {@link PhoneTimeZoneSuggestion} using network country code and NITZ.
     */
    @NonNull
    private PhoneTimeZoneSuggestion findTimeZoneFromCountryAndNitz(
            int phoneId, @NonNull String countryIsoCode,
            @NonNull TimestampedValue<NitzData> nitzSignal) {
        Objects.requireNonNull(countryIsoCode);
        Objects.requireNonNull(nitzSignal);

        PhoneTimeZoneSuggestion suggestion = new PhoneTimeZoneSuggestion(phoneId);
        suggestion.addDebugInfo("findTimeZoneFromCountryAndNitz: countryIsoCode=" + countryIsoCode
                + ", nitzSignal=" + nitzSignal);
        NitzData nitzData = Objects.requireNonNull(nitzSignal.getValue());
        if (isNitzSignalOffsetInfoBogus(countryIsoCode, nitzData)) {
            suggestion.addDebugInfo("findTimeZoneFromCountryAndNitz: NITZ signal looks bogus");
            return suggestion;
        }

        // Try to find a match using both country + NITZ signal.
        TimeZoneLookupHelper.OffsetResult lookupResult =
                mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, countryIsoCode);
        if (lookupResult != null) {
            suggestion.setZoneId(lookupResult.getTimeZone().getID());
            suggestion.setMatchType(PhoneTimeZoneSuggestion.NETWORK_COUNTRY_AND_OFFSET);
            int quality = lookupResult.getIsOnlyMatch()
                    ? PhoneTimeZoneSuggestion.SINGLE_ZONE
                    : PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_SAME_OFFSET;
            suggestion.setQuality(quality);
            suggestion.addDebugInfo("findTimeZoneFromCountryAndNitz: lookupResult=" + lookupResult);
            return suggestion;
        }

        // The country + offset provided no match, so see if the country by itself would be enough.
        CountryResult countryResult = mTimeZoneLookupHelper.lookupByCountry(
                countryIsoCode, nitzData.getCurrentTimeInMillis());
        if (countryResult == null) {
            // Country not recognized.
            suggestion.addDebugInfo(
                    "findTimeZoneFromCountryAndNitz: lookupByCountry() country not recognized");
            return suggestion;
        }

        // If the country has a single zone, or it has multiple zones but the default zone is
        // "boosted" (i.e. the country default is considered a good suggestion in most cases) then
        // use it.
        if (countryResult.quality == QUALITY_SINGLE_ZONE
                || countryResult.quality == QUALITY_DEFAULT_BOOSTED) {
            suggestion.setZoneId(countryResult.zoneId);
            suggestion.setMatchType(PhoneTimeZoneSuggestion.NETWORK_COUNTRY_ONLY);
            suggestion.setQuality(PhoneTimeZoneSuggestion.SINGLE_ZONE);
            suggestion.addDebugInfo(
                    "findTimeZoneFromCountryAndNitz: high quality country-only suggestion:"
                            + " countryResult=" + countryResult);
            return suggestion;
        }

        // Quality is not high enough to set the zone using country only.
        suggestion.addDebugInfo("findTimeZoneFromCountryAndNitz: country-only suggestion quality"
                + " not high enough. countryResult=" + countryResult);
        return suggestion;
    }

    /**
     * Creates a {@link PhoneTimeZoneSuggestion} using only network country code; works well on
     * countries which only have one time zone or multiple zones with the same offset.
     *
     * @param countryIsoCode country code from network MCC
     * @param whenMillis the time to use when looking at time zone rules data
     */
    @NonNull
    private PhoneTimeZoneSuggestion findTimeZoneFromNetworkCountryCode(
            int phoneId, @NonNull String countryIsoCode, long whenMillis) {
        Objects.requireNonNull(countryIsoCode);
        if (TextUtils.isEmpty(countryIsoCode)) {
            throw new IllegalArgumentException("countryIsoCode must not be empty");
        }

        PhoneTimeZoneSuggestion result = new PhoneTimeZoneSuggestion(phoneId);
        result.addDebugInfo("findTimeZoneFromNetworkCountryCode:"
                + " whenMillis=" + whenMillis + ", countryIsoCode=" + countryIsoCode);
        CountryResult lookupResult = mTimeZoneLookupHelper.lookupByCountry(
                countryIsoCode, whenMillis);
        if (lookupResult != null) {
            result.setZoneId(lookupResult.zoneId);
            result.setMatchType(PhoneTimeZoneSuggestion.NETWORK_COUNTRY_ONLY);

            int quality;
            if (lookupResult.quality == QUALITY_SINGLE_ZONE
                    || lookupResult.quality == QUALITY_DEFAULT_BOOSTED) {
                quality = PhoneTimeZoneSuggestion.SINGLE_ZONE;
            } else if (lookupResult.quality == QUALITY_MULTIPLE_ZONES_SAME_OFFSET) {
                quality = PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_SAME_OFFSET;
            } else if (lookupResult.quality == QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS) {
                quality = PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS;
            } else {
                // This should never happen.
                throw new IllegalArgumentException(
                        "lookupResult.quality not recognized: countryIsoCode=" + countryIsoCode
                                + ", whenMillis=" + whenMillis + ", lookupResult=" + lookupResult);
            }
            result.setQuality(quality);
            result.addDebugInfo("findTimeZoneFromNetworkCountryCode: lookupResult=" + lookupResult);
        } else {
            result.addDebugInfo("findTimeZoneFromNetworkCountryCode: Country not recognized?");
        }
        return result;
    }

    /**
     * Returns true if the NITZ signal is definitely bogus, assuming that the country is correct.
     */
    private boolean isNitzSignalOffsetInfoBogus(String countryIsoCode, NitzData nitzData) {
        if (TextUtils.isEmpty(countryIsoCode)) {
            // We cannot say for sure.
            return false;
        }

        boolean zeroOffsetNitz = nitzData.getLocalOffsetMillis() == 0;
        return zeroOffsetNitz && !countryUsesUtc(countryIsoCode, nitzData);
    }

    private boolean countryUsesUtc(String countryIsoCode, NitzData nitzData) {
        return mTimeZoneLookupHelper.countryUsesUtc(
                countryIsoCode, nitzData.getCurrentTimeInMillis());
    }
}
