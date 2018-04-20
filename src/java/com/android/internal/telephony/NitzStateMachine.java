/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TimeZoneLookupHelper.CountryResult;
import com.android.internal.telephony.TimeZoneLookupHelper.OffsetResult;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.telephony.util.TimeStampedValue;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * {@hide}
 */
// Non-final to allow mocking.
public class NitzStateMachine {

    /**
     * A proxy over device state that allows things like system properties, system clock
     * to be faked for tests.
     */
    // Non-final to allow mocking.
    public static class DeviceState {
        private static final int NITZ_UPDATE_SPACING_DEFAULT = 1000 * 60 * 10;
        private final int mNitzUpdateSpacing;

        private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
        private final int mNitzUpdateDiff;

        private final GsmCdmaPhone mPhone;
        private final TelephonyManager mTelephonyManager;
        private final ContentResolver mCr;

        public DeviceState(GsmCdmaPhone phone) {
            mPhone = phone;

            Context context = phone.getContext();
            mTelephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            mCr = context.getContentResolver();
            mNitzUpdateSpacing =
                    SystemProperties.getInt("ro.nitz_update_spacing", NITZ_UPDATE_SPACING_DEFAULT);
            mNitzUpdateDiff =
                    SystemProperties.getInt("ro.nitz_update_diff", NITZ_UPDATE_DIFF_DEFAULT);
        }

        /**
         * If time between NITZ updates is less than {@link #getNitzUpdateSpacingMillis()} the
         * update may be ignored.
         */
        public int getNitzUpdateSpacingMillis() {
            return Settings.Global.getInt(mCr, Settings.Global.NITZ_UPDATE_SPACING,
                    mNitzUpdateSpacing);
        }

        /**
         * If {@link #getNitzUpdateSpacingMillis()} hasn't been exceeded but update is >
         * {@link #getNitzUpdateDiffMillis()} do the update
         */
        public int getNitzUpdateDiffMillis() {
            return Settings.Global.getInt(mCr, Settings.Global.NITZ_UPDATE_DIFF, mNitzUpdateDiff);
        }

        /**
         * Returns true if the {@code gsm.ignore-nitz} system property is set to "yes".
         */
        public boolean getIgnoreNitz() {
            String ignoreNitz = SystemProperties.get("gsm.ignore-nitz");
            return ignoreNitz != null && ignoreNitz.equals("yes");
        }

        public String getNetworkCountryIsoForPhone() {
            return mTelephonyManager.getNetworkCountryIsoForPhone(mPhone.getPhoneId());
        }
    }

    private static final String LOG_TAG = ServiceStateTracker.LOG_TAG;
    private static final boolean DBG = ServiceStateTracker.DBG;

    // Time detection state.

    /**
     * The last NITZ-sourced time considered. If auto time detection was off at the time this may
     * not have been used to set the device time, but it can be used if auto time detection is
     * re-enabled.
     */
    private TimeStampedValue<Long> mSavedNitzTime;

    // Time Zone detection state.

    /** We always keep the last NITZ signal received in mLatestNitzSignal. */
    private TimeStampedValue<NitzData> mLatestNitzSignal;

    /**
     * Sometimes we get the NITZ time before we know what country we are in.  We can find the time
     * zone once we know the country by using {@link #mLatestNitzSignal}.
     */
    private boolean mNeedCountryCodeForNitz = false;
    private boolean mGotCountryCode = false;

    /**
     * The last time zone ID that has been determined. It may not have been set as the device time
     * zone if automatic time zone detection is disabled but may later be used to set the time zone
     * if the user enables automatic time zone detection.
     */
    private String mSavedTimeZoneId;

    /**
     * Boolean is {@code true} if NITZ has been used to determine a time zone (which may not
     * ultimately have been used due to user settings). Cleared by {@link #handleNetworkAvailable()}
     * and {@link #handleNetworkUnavailable()}. The flag can be used when historic NITZ data may no
     * longer be valid. {@code false} indicates it is reasonable to try to set the time zone using
     * less reliable algorithms than NITZ-based detection such as by just using network country
     * code.
     */
    private boolean mNitzTimeZoneDetectionSuccessful = false;

    // Miscellaneous dependencies and helpers not related to detection state.
    private final LocalLog mTimeLog = new LocalLog(15);
    private final LocalLog mTimeZoneLog = new LocalLog(15);
    private final GsmCdmaPhone mPhone;
    private final DeviceState mDeviceState;
    private final TimeServiceHelper mTimeServiceHelper;
    private final TimeZoneLookupHelper mTimeZoneLookupHelper;
    /** Wake lock used while setting time of day. */
    private final PowerManager.WakeLock mWakeLock;
    private static final String WAKELOCK_TAG = "NitzStateMachine";

    public NitzStateMachine(GsmCdmaPhone phone) {
        this(phone,
                new TimeServiceHelper(phone.getContext()),
                new DeviceState(phone),
                new TimeZoneLookupHelper());
    }

    @VisibleForTesting
    public NitzStateMachine(GsmCdmaPhone phone, TimeServiceHelper timeServiceHelper,
            DeviceState deviceState, TimeZoneLookupHelper timeZoneLookupHelper) {
        mPhone = phone;

        Context context = phone.getContext();
        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        mDeviceState = deviceState;
        mTimeZoneLookupHelper = timeZoneLookupHelper;
        mTimeServiceHelper = timeServiceHelper;
        mTimeServiceHelper.setListener(new TimeServiceHelper.Listener() {
            @Override
            public void onTimeDetectionChange(boolean enabled) {
                if (enabled) {
                    handleAutoTimeEnabled();
                }
            }

            @Override
            public void onTimeZoneDetectionChange(boolean enabled) {
                if (enabled) {
                    handleAutoTimeZoneEnabled();
                }
            }
        });
    }

    /**
     * Called when the network country is set on the Phone. Although set, the network country code
     * may be invalid.
     *
     * @param countryChanged true when the country code is known to have changed, false if it
     *     probably hasn't
     */
    public void handleNetworkCountryCodeSet(boolean countryChanged) {
        mGotCountryCode = true;

        String isoCountryCode = mDeviceState.getNetworkCountryIsoForPhone();
        if (!TextUtils.isEmpty(isoCountryCode) && !mNitzTimeZoneDetectionSuccessful) {
            updateTimeZoneByNetworkCountryCode(isoCountryCode);
        }

        if (countryChanged || mNeedCountryCodeForNitz) {
            // TimeZone.getDefault() returns a default zone (GMT) even when time zone have never
            // been set which makes it difficult to tell if it's what the user / time zone detection
            // has chosen. isTimeZoneSettingInitialized() tells us whether the time zone of the
            // device has ever been explicit set by the user or code.
            final boolean isTimeZoneSettingInitialized =
                    mTimeServiceHelper.isTimeZoneSettingInitialized();
            if (DBG) {
                Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet:"
                        + " isTimeZoneSettingInitialized=" + isTimeZoneSettingInitialized
                        + " mLatestNitzSignal=" + mLatestNitzSignal
                        + " isoCountryCode=" + isoCountryCode);
            }
            String zoneId;
            if (TextUtils.isEmpty(isoCountryCode) && mNeedCountryCodeForNitz) {
                // Country code not found.  This is likely a test network.
                // Get a TimeZone based only on the NITZ parameters (best guess).

                // mNeedCountryCodeForNitz is only set to true after an NITZ signal has been
                // received and so mLatestNitzSign must be non-null.
                OffsetResult lookupResult =
                        mTimeZoneLookupHelper.lookupByNitz(mLatestNitzSignal.mValue);
                if (DBG) {
                    Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: guessZoneIdByNitz() returned"
                            + " lookupResult=" + lookupResult);
                }
                zoneId = lookupResult != null ? lookupResult.zoneId : null;
            } else if (mLatestNitzSignal == null) {
                zoneId = null;
                if (DBG) {
                    Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: No cached NITZ data available,"
                            + " not setting zone");
                }
            } else { // mLatestNitzSignal != null
                if (isNitzSignalOffsetInfoBogus(mLatestNitzSignal, isoCountryCode)) {
                    String logMsg = "handleNetworkCountryCodeSet: Stored NITZ looks bogus, "
                            + " isoCountryCode=" + isoCountryCode
                            + " mLatestNitzSignal=" + mLatestNitzSignal;
                    if (DBG) {
                        Rlog.d(LOG_TAG, logMsg);
                    }
                    mTimeZoneLog.log(logMsg);

                    // No zone can be determined.
                    zoneId = null;
                } else {
                    NitzData nitzData = mLatestNitzSignal.mValue;
                    OffsetResult lookupResult =
                            mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, isoCountryCode);
                    if (DBG) {
                        Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: using"
                                + " guessZoneIdByNitzCountry(nitzData, isoCountryCode),"
                                + " nitzData=" + nitzData
                                + " isoCountryCode=" + isoCountryCode
                                + " lookupResult=" + lookupResult);
                    }
                    zoneId = lookupResult != null ? lookupResult.zoneId : null;
                }
            }
            final String tmpLog = "handleNetworkCountryCodeSet:"
                    + " isTimeZoneSettingInitialized=" + isTimeZoneSettingInitialized
                    + " mLatestNitzSignal=" + mLatestNitzSignal
                    + " isoCountryCode=" + isoCountryCode
                    + " mNeedCountryCodeForNitz=" + mNeedCountryCodeForNitz
                    + " zoneId=" + zoneId;
            mTimeZoneLog.log(tmpLog);

            if (zoneId != null) {
                Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: zoneId != null, zoneId=" + zoneId);
                if (mTimeServiceHelper.isTimeZoneDetectionEnabled()) {
                    setAndBroadcastNetworkSetTimeZone(zoneId);
                } else {
                    Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: skip changing zone as"
                            + " isTimeZoneDetectionEnabled() is false");
                }
                mSavedTimeZoneId = zoneId;
                mNitzTimeZoneDetectionSuccessful = true;
            } else {
                Rlog.d(LOG_TAG, "handleNetworkCountryCodeSet: lookupResult == null, do nothing");
            }
            mNeedCountryCodeForNitz = false;
        }
    }

    private boolean countryUsesUtc(
            String isoCountryCode, TimeStampedValue<NitzData> nitzSignal) {
        return mTimeZoneLookupHelper.countryUsesUtc(
                isoCountryCode,
                nitzSignal.mValue.getCurrentTimeInMillis());
    }

    /**
     * Informs the {@link NitzStateMachine} that the network has become available.
     */
    public void handleNetworkAvailable() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleNetworkAvailable: mNitzTimeZoneDetectionSuccessful="
                    + mNitzTimeZoneDetectionSuccessful
                    + ", Setting mNitzTimeZoneDetectionSuccessful=false");
        }
        mNitzTimeZoneDetectionSuccessful = false;
    }

    /**
     * Informs the {@link NitzStateMachine} that the network has become unavailable.
     */
    public void handleNetworkUnavailable() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleNetworkUnavailable");
        }

        mGotCountryCode = false;
        mNitzTimeZoneDetectionSuccessful = false;
    }

    /**
     * Handle a new NITZ signal being received.
     */
    public void handleNitzReceived(TimeStampedValue<NitzData> nitzSignal) {
        // Always store the latest NITZ signal received.
        mLatestNitzSignal = nitzSignal;

        handleTimeZoneFromNitz(nitzSignal);
        handleTimeFromNitz(nitzSignal);
    }

    /**
     * Returns true if the NITZ signal is definitely bogus, assuming that the country is correct.
     */
    private boolean isNitzSignalOffsetInfoBogus(
            TimeStampedValue<NitzData> nitzSignal, String isoCountryCode) {

        if (TextUtils.isEmpty(isoCountryCode)) {
            // We cannot say for sure.
            return false;
        }

        NitzData newNitzData = nitzSignal.mValue;
        boolean zeroOffsetNitz = newNitzData.getLocalOffsetMillis() == 0 && !newNitzData.isDst();
        return zeroOffsetNitz && !countryUsesUtc(isoCountryCode, nitzSignal);
    }

    private void handleTimeZoneFromNitz(TimeStampedValue<NitzData> nitzSignal) {
        try {
            NitzData newNitzData = nitzSignal.mValue;
            String isoCountryCode = mDeviceState.getNetworkCountryIsoForPhone();

            boolean needCountryCode;
            String zoneId;
            if (newNitzData.getEmulatorHostTimeZone() != null) {
                zoneId = newNitzData.getEmulatorHostTimeZone().getID();
                needCountryCode = false;
            } else {
                if (!mGotCountryCode) {
                    // We don't have a country code so we won't try to look up the time zone.
                    zoneId = null;
                    needCountryCode = true;
                } else if (TextUtils.isEmpty(isoCountryCode)) {
                    // We have a country code but it's empty. This is most likely because we're on a
                    // test network that's using a bogus MCC (eg, "001"), so get a TimeZone
                    // based only on the NITZ parameters.
                    OffsetResult lookupResult = mTimeZoneLookupHelper.lookupByNitz(newNitzData);
                    if (DBG) {
                        Rlog.d(LOG_TAG, "handleTimeZoneFromNitz: guessZoneIdByNitz returned"
                                + " lookupResult=" + lookupResult);
                    }
                    zoneId = lookupResult != null ? lookupResult.zoneId : null;
                    needCountryCode = false;
                } else if (isNitzSignalOffsetInfoBogus(nitzSignal, isoCountryCode)) {
                    String logMsg = "handleNitzReceived: Received NITZ looks bogus, "
                            + " isoCountryCode=" + isoCountryCode
                            + " nitzSignal=" + nitzSignal;
                    if (DBG) {
                        Rlog.d(LOG_TAG, logMsg);
                    }
                    mTimeZoneLog.log(logMsg);

                    zoneId = null;
                    needCountryCode = false;
                } else {
                    OffsetResult lookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                            newNitzData, isoCountryCode);
                    zoneId = lookupResult != null ? lookupResult.zoneId : null;
                    needCountryCode = false;
                }
            }

            // Set state as needed.
            if (needCountryCode) {
                mNeedCountryCodeForNitz = true;
            } else {
                mNeedCountryCodeForNitz = false;
                if (zoneId != null) {
                    if (mTimeServiceHelper.isTimeZoneDetectionEnabled()) {
                        setAndBroadcastNetworkSetTimeZone(zoneId);
                    }
                    mSavedTimeZoneId = zoneId;
                    mNitzTimeZoneDetectionSuccessful = true;
                }
            }

            String tmpLog = "handleTimeZoneFromNitz: nitzSignal=" + nitzSignal
                    + " zoneId=" + zoneId
                    + " mGotCountryCode=" + mGotCountryCode
                    + " isoCountryCode=" + isoCountryCode
                    + " mNeedCountryCodeForNitz=" + mNeedCountryCodeForNitz
                    + " mNitzTimeZoneDetectionSuccessful=" + mNitzTimeZoneDetectionSuccessful
                    + " mSavedTimeZoneId=" + mSavedTimeZoneId
                    + " isTimeZoneDetectionEnabled()="
                    + mTimeServiceHelper.isTimeZoneDetectionEnabled();
            if (DBG) {
                Rlog.d(LOG_TAG, tmpLog);
            }
            mTimeZoneLog.log(tmpLog);
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "handleTimeZoneFromNitz: Processing NITZ data"
                    + " nitzSignal=" + nitzSignal
                    + " ex=" + ex);
        }
    }

    private void handleTimeFromNitz(TimeStampedValue<NitzData> nitzSignal) {
        try {
            boolean ignoreNitz = mDeviceState.getIgnoreNitz();
            if (ignoreNitz) {
                Rlog.d(LOG_TAG,
                        "handleTimeFromNitz: Not setting clock because gsm.ignore-nitz is set");
                return;
            }

            try {
                // Acquire the wake lock as we are reading the elapsed realtime clock and system
                // clock.
                mWakeLock.acquire();

                // Validate the nitzTimeSignal to reject obviously bogus elapsedRealtime values.
                long elapsedRealtime = mTimeServiceHelper.elapsedRealtime();
                long millisSinceNitzReceived = elapsedRealtime - nitzSignal.mElapsedRealtime;
                if (millisSinceNitzReceived < 0 || millisSinceNitzReceived > Integer.MAX_VALUE) {
                    if (DBG) {
                        Rlog.d(LOG_TAG, "handleTimeFromNitz: not setting time, unexpected"
                                + " elapsedRealtime=" + elapsedRealtime
                                + " nitzSignal=" + nitzSignal);
                    }
                    return;
                }

                // Adjust the NITZ time by the delay since it was received to get the time now.
                long adjustedCurrentTimeMillis =
                        nitzSignal.mValue.getCurrentTimeInMillis() + millisSinceNitzReceived;
                long gained = adjustedCurrentTimeMillis - mTimeServiceHelper.currentTimeMillis();

                if (mTimeServiceHelper.isTimeDetectionEnabled()) {
                    String logMsg = "handleTimeFromNitz:"
                            + " nitzSignal=" + nitzSignal
                            + " adjustedCurrentTimeMillis=" + adjustedCurrentTimeMillis
                            + " millisSinceNitzReceived= " + millisSinceNitzReceived
                            + " gained=" + gained;

                    if (mSavedNitzTime == null) {
                        logMsg += ": First update received.";
                        setAndBroadcastNetworkSetTime(logMsg, adjustedCurrentTimeMillis);
                    } else {
                        long elapsedRealtimeSinceLastSaved = mTimeServiceHelper.elapsedRealtime()
                                - mSavedNitzTime.mElapsedRealtime;
                        int nitzUpdateSpacing = mDeviceState.getNitzUpdateSpacingMillis();
                        int nitzUpdateDiff = mDeviceState.getNitzUpdateDiffMillis();
                        if (elapsedRealtimeSinceLastSaved > nitzUpdateSpacing
                                || Math.abs(gained) > nitzUpdateDiff) {
                            // Either it has been a while since we received an update, or the gain
                            // is sufficiently large that we want to act on it.
                            logMsg += ": New update received.";
                            setAndBroadcastNetworkSetTime(logMsg, adjustedCurrentTimeMillis);
                        } else {
                            if (DBG) {
                                Rlog.d(LOG_TAG, logMsg + ": Update throttled.");
                            }

                            // Return early. This means that we don't reset the
                            // mSavedNitzTime for next time and that we may act on more
                            // NITZ time signals overall but should end up with a system clock that
                            // tracks NITZ more closely than if we saved throttled values (which
                            // would reset mSavedNitzTime.elapsedRealtime used to calculate time
                            // since the last NITZ signal was received).
                            return;
                        }
                    }
                }

                // Save the last NITZ time signal used so we can return to it later
                // if auto-time detection is toggled.
                mSavedNitzTime = new TimeStampedValue<>(
                        adjustedCurrentTimeMillis, nitzSignal.mElapsedRealtime);
            } finally {
                mWakeLock.release();
            }
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "handleTimeFromNitz: Processing NITZ data"
                    + " nitzSignal=" + nitzSignal
                    + " ex=" + ex);
        }
    }

    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setAndBroadcastNetworkSetTimeZone: zoneId=" + zoneId);
        }
        mTimeServiceHelper.setDeviceTimeZone(zoneId);
        if (DBG) {
            Rlog.d(LOG_TAG,
                    "setAndBroadcastNetworkSetTimeZone: called setDeviceTimeZone()"
                            + " zoneId=" + zoneId);
        }
    }

    private void setAndBroadcastNetworkSetTime(String msg, long time) {
        if (!mWakeLock.isHeld()) {
            Rlog.w(LOG_TAG, "setAndBroadcastNetworkSetTime: Wake lock not held while setting device"
                    + " time (msg=" + msg + ")");
        }

        msg = "setAndBroadcastNetworkSetTime: [Setting time to time=" + time + "]:" + msg;
        if (DBG) {
            Rlog.d(LOG_TAG, msg);
        }
        mTimeLog.log(msg);
        mTimeServiceHelper.setDeviceTime(time);
        TelephonyMetrics.getInstance().writeNITZEvent(mPhone.getPhoneId(), time);
    }

    private void handleAutoTimeEnabled() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleAutoTimeEnabled: Reverting to NITZ Time:"
                    + " mSavedNitzTime=" + mSavedNitzTime);
        }
        if (mSavedNitzTime != null) {
            try {
                // Acquire the wakelock as we're reading the elapsed realtime clock here.
                mWakeLock.acquire();

                long elapsedRealtime = mTimeServiceHelper.elapsedRealtime();
                String msg = "mSavedNitzTime: Reverting to NITZ time"
                        + " elapsedRealtime=" + elapsedRealtime
                        + " mSavedNitzTime=" + mSavedNitzTime;
                long adjustedCurrentTimeMillis =
                        mSavedNitzTime.mValue + (elapsedRealtime - mSavedNitzTime.mElapsedRealtime);
                setAndBroadcastNetworkSetTime(msg, adjustedCurrentTimeMillis);
            } finally {
                mWakeLock.release();
            }
        }
    }

    private void handleAutoTimeZoneEnabled() {
        String tmpLog = "handleAutoTimeZoneEnabled: Reverting to NITZ TimeZone:"
                + " mSavedTimeZoneId=" + mSavedTimeZoneId;
        if (DBG) {
            Rlog.d(LOG_TAG, tmpLog);
        }
        mTimeZoneLog.log(tmpLog);
        if (mSavedTimeZoneId != null) {
            setAndBroadcastNetworkSetTimeZone(mSavedTimeZoneId);
        }
    }

    /**
     * Dumps the current in-memory state to the supplied PrintWriter.
     */
    public void dumpState(PrintWriter pw) {
        // Time Detection State
        pw.println(" mSavedTime=" + mSavedNitzTime);

        // Time Zone Detection State
        pw.println(" mNeedCountryCodeForNitz=" + mNeedCountryCodeForNitz);
        pw.println(" mLatestNitzSignal=" + mLatestNitzSignal);
        pw.println(" mGotCountryCode=" + mGotCountryCode);
        pw.println(" mSavedTimeZoneId=" + mSavedTimeZoneId);
        pw.println(" mNitzTimeZoneDetectionSuccessful=" + mNitzTimeZoneDetectionSuccessful);

        // Miscellaneous
        pw.println(" mWakeLock=" + mWakeLock);
        pw.flush();
    }

    /**
     * Dumps the time / time zone logs to the supplied IndentingPrintWriter.
     */
    public void dumpLogs(FileDescriptor fd, IndentingPrintWriter ipw, String[] args) {
        ipw.println(" Time Logs:");
        ipw.increaseIndent();
        mTimeLog.dump(fd, ipw, args);
        ipw.decreaseIndent();

        ipw.println(" Time zone Logs:");
        ipw.increaseIndent();
        mTimeZoneLog.dump(fd, ipw, args);
        ipw.decreaseIndent();
    }

    /**
     * Update time zone by network country code, works well on countries which only have one time
     * zone or multiple zones with the same offset.
     *
     * @param iso Country code from network MCC
     */
    private void updateTimeZoneByNetworkCountryCode(String iso) {
        CountryResult lookupResult = mTimeZoneLookupHelper.lookupByCountry(
                iso, mTimeServiceHelper.currentTimeMillis());
        if (lookupResult != null && lookupResult.allZonesHaveSameOffset) {
            String logMsg = "updateTimeZoneByNetworkCountryCode: set time"
                    + " lookupResult=" + lookupResult
                    + " iso=" + iso;
            if (DBG) {
                Rlog.d(LOG_TAG, logMsg);
            }
            mTimeZoneLog.log(logMsg);
            String zoneId = lookupResult.zoneId;
            if (mTimeServiceHelper.isTimeZoneDetectionEnabled()) {
                setAndBroadcastNetworkSetTimeZone(zoneId);
            }
            mSavedTimeZoneId = zoneId;
        } else {
            if (DBG) {
                Rlog.d(LOG_TAG, "updateTimeZoneByNetworkCountryCode: no good zone for"
                        + " iso=" + iso
                        + " lookupResult=" + lookupResult);
            }
        }
    }

    /**
     * Get the mNitzTimeZoneDetectionSuccessful flag value.
     */
    public boolean getNitzTimeZoneDetectionSuccessful() {
        return mNitzTimeZoneDetectionSuccessful;
    }

    /**
     * Returns the last NITZ data that was cached.
     */
    public NitzData getCachedNitzData() {
        return mLatestNitzSignal != null ? mLatestNitzSignal.mValue : null;
    }

    /**
     * Returns the time zone ID from the most recent time that a time zone could be determined by
     * this state machine.
     */
    public String getSavedTimeZoneId() {
        return mSavedTimeZoneId;
    }

}
