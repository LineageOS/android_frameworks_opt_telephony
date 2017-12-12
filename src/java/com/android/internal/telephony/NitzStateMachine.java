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

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.TimeUtils;

import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

/**
 * {@hide}
 */
public final class NitzStateMachine {
    private static final String LOG_TAG = ServiceStateTracker.LOG_TAG;
    private static final boolean DBG = ServiceStateTracker.DBG;

    protected static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    /**
     * List of ISO codes for countries that can have an offset of
     * GMT+0 when not in daylight savings time.  This ignores some
     * small places such as the Canary Islands (Spain) and
     * Danmarkshavn (Denmark).  The list must be sorted by code.
     */
    private static final String[] GMT_COUNTRY_CODES = {
            "bf", // Burkina Faso
            "ci", // Cote d'Ivoire
            "eh", // Western Sahara
            "fo", // Faroe Islands, Denmark
            "gb", // United Kingdom of Great Britain and Northern Ireland
            "gh", // Ghana
            "gm", // Gambia
            "gn", // Guinea
            "gw", // Guinea Bissau
            "ie", // Ireland
            "lr", // Liberia
            "is", // Iceland
            "ma", // Morocco
            "ml", // Mali
            "mr", // Mauritania
            "pt", // Portugal
            "sl", // Sierra Leone
            "sn", // Senegal
            "st", // Sao Tome and Principe
            "tg", // Togo
    };

    private final LocalLog mTimeLog = new LocalLog(15);
    private final LocalLog mTimeZoneLog = new LocalLog(15);

    /**
     * Sometimes we get the NITZ time before we know what country we
     * are in. Keep the time zone information from the NITZ string in
     * mNitzData so we can fix the time zone once know the country.
     */
    private boolean mNeedFixZoneAfterNitz = false;

    private NitzData mNitzData;
    private boolean mGotCountryCode = false;
    private String mSavedTimeZoneId;
    private long mSavedTime;
    private long mSavedAtTime;

    /** Wake lock used while setting time of day. */
    private PowerManager.WakeLock mWakeLock;
    private static final String WAKELOCK_TAG = "NitzStateMachine";
    private final ContentResolver mCr;
    private final ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Rlog.i(LOG_TAG, "Auto time state changed");
            revertToNitzTime();
        }
    };

    private final ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Rlog.i(LOG_TAG, "Auto time zone state changed");
            revertToNitzTimeZone();
        }
    };

    /** Boolean is true if setTimeFromNITZ was called */
    private boolean mNitzUpdatedTime = false;

    /** if time between NITZ updates is less than mNitzUpdateSpacing the update may be ignored. */
    private static final int NITZ_UPDATE_SPACING_DEFAULT = 1000 * 60 * 10;
    private final int mNitzUpdateSpacing = SystemProperties.getInt("ro.nitz_update_spacing",
            NITZ_UPDATE_SPACING_DEFAULT);

    /** If mNitzUpdateSpacing hasn't been exceeded but update is > mNitzUpdate do the update */
    private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
    private final int mNitzUpdateDiff = SystemProperties.getInt("ro.nitz_update_diff",
            NITZ_UPDATE_DIFF_DEFAULT);

    private final Context mContext;
    private final GsmCdmaPhone mPhone;

    public NitzStateMachine(GsmCdmaPhone phone) {
        mPhone = phone;
        Context context = phone.getContext();
        mContext = context;

        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        mCr = context.getContentResolver();
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME), true,
                mAutoTimeObserver);
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), true,
                mAutoTimeZoneObserver);
    }

    /**
     * Called when the device's network country is known, allowing the time zone detection to be
     * substantially more precise.
     */
    public void fixTimeZone(String isoCountryCode) {
        // Capture the time zone property. This allows us to tell whether the device has a time zone
        // set. TimeZone.getDefault() returns a default zone (GMT) even when time zone is not
        // explicitly set making the system property a better indicator.
        final String zoneName = SystemProperties.get(TIMEZONE_PROPERTY);
        if (DBG) {
            Rlog.d(LOG_TAG, "fixTimeZone zoneName='" + zoneName
                    + "' mNitzData=" + mNitzData
                    + " iso-cc='" + isoCountryCode
                    + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode));
        }
        TimeZone zone;
        if ("".equals(isoCountryCode) && mNeedFixZoneAfterNitz) {
            // Country code not found.  This is likely a test network.
            // Get a TimeZone based only on the NITZ parameters (best guess).

            // mNeedFixZoneAfterNitz is only set to true when mNitzData is set so there's no need to
            // check mNitzData == null.
            zone = NitzData.guessTimeZone(mNitzData);
            if (DBG) {
                Rlog.d(LOG_TAG, "fixTimeZone(): guessNitzTimeZone returned "
                        + (zone == null ? zone : zone.getID()));
            }
        } else if ((mNitzData == null || nitzOffsetMightBeBogus(mNitzData))
                && (zoneName != null && zoneName.length() > 0)
                && (Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode) < 0)) {

            // This case means that (1) the device received no NITZ signal yet or received an NITZ
            // signal that looks bogus due to having a zero offset from UTC, (2) the device has a
            // time zone set explicitly, and (3) the iso tells us the country is NOT one that uses a
            // zero offset. This is interpreted as being NITZ incorrectly reporting a local time and
            // not a UTC time. The zone is left as the current device's zone setting, and the time
            // may be adjusted by assuming the current zone setting is correct.
            zone = TimeZone.getDefault();

            // Note that mNeedFixZoneAfterNitz => (implies) { mNitzData != null }. Therefore, if
            // mNitzData == null, mNeedFixZoneAfterNitz cannot be true. The code in this section
            // therefore means that when mNitzData == null (and the country is one that doesn't use
            // a zero UTC offset) the device will retain the existing time zone setting and not try
            // to derive one from the isoCountryCode.
            if (mNeedFixZoneAfterNitz) {
                long ctm = System.currentTimeMillis();
                long tzOffset = zone.getOffset(ctm);
                if (DBG) {
                    Rlog.d(LOG_TAG, "fixTimeZone: tzOffset=" + tzOffset
                            + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                }
                if (getAutoTime()) {
                    long adj = ctm - tzOffset;
                    if (DBG) {
                        Rlog.d(LOG_TAG, "fixTimeZone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                    }
                    setAndBroadcastNetworkSetTime(adj);
                } else {
                    // Adjust the saved NITZ time to account for tzOffset.
                    mSavedTime = mSavedTime - tzOffset;
                    if (DBG) {
                        Rlog.d(LOG_TAG, "fixTimeZone: adj mSavedTime=" + mSavedTime);
                    }
                }
            }
            if (DBG) {
                Rlog.d(LOG_TAG, "fixTimeZone: using default TimeZone");
            }
        } else if (mNitzData == null) {
            // The use of 1/1/1970 UTC is unusual but consistent with historical behavior when
            // it wasn't possible to detect whether a previous NITZ signal had been saved.
            zone = TimeUtils.getTimeZone(0 /* offset */, false /* dst */, 0 /* when */,
                    isoCountryCode);
            if (DBG) {
                Rlog.d(LOG_TAG, "fixTimeZone: No cached NITZ data available, using only country"
                        + " code. zone=" + zone);
            }
        } else {
            zone = TimeUtils.getTimeZone(mNitzData.getLocalOffsetMillis(), mNitzData.isDst(),
                    mNitzData.getCurrentTimeInMillis(), isoCountryCode);
            if (DBG) {
                Rlog.d(LOG_TAG, "fixTimeZone: using getTimeZone(off, dst, time, iso)");
            }
        }
        final String tmpLog = "fixTimeZone zoneName=" + zoneName + " mNitzData=" + mNitzData
                + " iso-cc=" + isoCountryCode + " mNeedFixZoneAfterNitz="
                + mNeedFixZoneAfterNitz + " zone=" + (zone != null ? zone.getID() : "NULL");
        mTimeZoneLog.log(tmpLog);

        if (zone != null) {
            Rlog.d(LOG_TAG, "fixTimeZone: zone != null zone.getID=" + zone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            } else {
                Rlog.d(LOG_TAG, "fixTimeZone: skip changing zone as getAutoTimeZone was false");
            }
            if (mNeedFixZoneAfterNitz) {
                saveNitzTimeZone(zone.getID());
            }
        } else {
            Rlog.d(LOG_TAG, "fixTimeZone: zone == null, do nothing for zone");
        }
        mNeedFixZoneAfterNitz = false;
    }

    /**
     * Returns {@code true} if the NITZ data looks like it might be incomplete or bogus, i.e. it has
     * a zero offset from UTC with either no DST information available or a zero DST offset.
     */
    private static boolean nitzOffsetMightBeBogus(NitzData nitzData) {
        return nitzData.getLocalOffsetMillis() == 0 && !nitzData.isDst();
    }

    /**
     * Handle a new NITZ signal being received.
     */
    public void setTimeAndTimeZoneFromNitz(NitzData newNitzData, long nitzReceiveTime) {
        setTimeZoneFromNitz(newNitzData, nitzReceiveTime);
        setTimeFromNitz(newNitzData, nitzReceiveTime);
    }

    private void setTimeZoneFromNitz(NitzData newNitzData, long nitzReceiveTime) {
        try {
            String iso = ((TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE))
                    .getNetworkCountryIsoForPhone(mPhone.getPhoneId());

            TimeZone zone;
            if (newNitzData.getEmulatorHostTimeZone() != null) {
                zone = newNitzData.getEmulatorHostTimeZone();
            } else {
                if (!mGotCountryCode) {
                    zone = null;
                } else if (iso != null && iso.length() > 0) {
                    zone = TimeUtils.getTimeZone(
                            newNitzData.getLocalOffsetMillis(),
                            newNitzData.isDst(),
                            newNitzData.getCurrentTimeInMillis(),
                            iso);
                } else {
                    // We don't have a valid iso country code.  This is
                    // most likely because we're on a test network that's
                    // using a bogus MCC (eg, "001"), so get a TimeZone
                    // based only on the NITZ parameters.
                    zone = NitzData.guessTimeZone(newNitzData);
                    if (DBG) {
                        Rlog.d(LOG_TAG, "setTimeFromNITZ(): guessNitzTimeZone returned "
                                + (zone == null ? zone : zone.getID()));
                    }
                }
            }

            int previousUtcOffset;
            boolean previousIsDst;
            if (mNitzData == null) {
                // No previously saved NITZ data. Use the same defaults as Android would have done
                // before it was possible to detect this case.
                previousUtcOffset = 0;
                previousIsDst = false;
            } else {
                previousUtcOffset = mNitzData.getLocalOffsetMillis();
                previousIsDst = mNitzData.isDst();
            }
            if ((zone == null)
                    || (newNitzData.getLocalOffsetMillis() != previousUtcOffset)
                    || (newNitzData.isDst() != previousIsDst)) {
                // We got the time before the country or the zone has changed
                // so we don't know how to identify the DST rules yet.  Save
                // the information and hope to fix it up later.
                mNeedFixZoneAfterNitz = true;
                mNitzData = newNitzData;
            }

            String tmpLog = "NITZ: newNitzData=" + newNitzData
                    + " nitzReceiveTime=" + nitzReceiveTime
                    + " zone=" + (zone != null ? zone.getID() : "NULL")
                    + " iso=" + iso + " mGotCountryCode=" + mGotCountryCode
                    + " mNeedFixZoneAfterNitz=" + mNeedFixZoneAfterNitz
                    + " getAutoTimeZone()=" + getAutoTimeZone();
            if (DBG) {
                Rlog.d(LOG_TAG, tmpLog);
            }
            mTimeZoneLog.log(tmpLog);

            if (zone != null) {
                if (getAutoTimeZone()) {
                    setAndBroadcastNetworkSetTimeZone(zone.getID());
                }
                saveNitzTimeZone(zone.getID());
            }
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "NITZ: Processing NITZ data " + newNitzData + " ex=" + ex);
        }
    }

    private void setTimeFromNitz(NitzData newNitzData, long nitzReceiveTime) {
        try {
            String ignore = SystemProperties.get("gsm.ignore-nitz");
            if (ignore != null && ignore.equals("yes")) {
                Rlog.d(LOG_TAG, "NITZ: Not setting clock because gsm.ignore-nitz is set");
                return;
            }

            try {
                mWakeLock.acquire();

                long millisSinceNitzReceived = SystemClock.elapsedRealtime() - nitzReceiveTime;
                if (millisSinceNitzReceived < 0) {
                    // Sanity check: something is wrong
                    if (DBG) {
                        Rlog.d(LOG_TAG, "NITZ: not setting time, clock has rolled "
                                + "backwards since NITZ time was received, "
                                + newNitzData);
                    }
                    return;
                }

                if (millisSinceNitzReceived > Integer.MAX_VALUE) {
                    // If the time is this far off, something is wrong > 24 days!
                    if (DBG) {
                        Rlog.d(LOG_TAG, "NITZ: not setting time, processing has taken "
                                + (millisSinceNitzReceived / (1000 * 60 * 60 * 24))
                                + " days");
                    }
                    return;
                }

                // Adjust the NITZ time by the delay since it was received.
                long adjustedCurrentTimeMillis = newNitzData.getCurrentTimeInMillis();
                adjustedCurrentTimeMillis += millisSinceNitzReceived;

                if (!mPhone.isPhoneTypeGsm() || getAutoTime()) {
                    String tmpLog = "NITZ: newNitaData=" + newNitzData
                            + " nitzReceiveTime=" + nitzReceiveTime
                            + " Setting time of day to " + adjustedCurrentTimeMillis
                            + " NITZ receive delay(ms): " + millisSinceNitzReceived
                            + " gained(ms): "
                            + (adjustedCurrentTimeMillis - System.currentTimeMillis());
                    if (DBG) {
                        Rlog.d(LOG_TAG, tmpLog);
                    }
                    mTimeLog.log(tmpLog);
                    if (mPhone.isPhoneTypeGsm()) {
                        setAndBroadcastNetworkSetTime(adjustedCurrentTimeMillis);
                        Rlog.i(LOG_TAG, "NITZ: after Setting time of day");
                    } else {
                        if (getAutoTime()) {
                            // Update system time automatically
                            long gained = adjustedCurrentTimeMillis - System.currentTimeMillis();
                            long timeSinceLastUpdate = SystemClock.elapsedRealtime() - mSavedAtTime;
                            int nitzUpdateSpacing = Settings.Global.getInt(mCr,
                                    Settings.Global.NITZ_UPDATE_SPACING, mNitzUpdateSpacing);
                            int nitzUpdateDiff = Settings.Global.getInt(mCr,
                                    Settings.Global.NITZ_UPDATE_DIFF, mNitzUpdateDiff);

                            if ((mSavedAtTime == 0) || (timeSinceLastUpdate > nitzUpdateSpacing)
                                    || (Math.abs(gained) > nitzUpdateDiff)) {
                                if (DBG) {
                                    Rlog.d(LOG_TAG, "NITZ: Auto updating time of day to "
                                            + adjustedCurrentTimeMillis
                                            + " NITZ receive delay=" + millisSinceNitzReceived
                                            + "ms gained=" + gained + "ms from " + newNitzData);
                                }

                                setAndBroadcastNetworkSetTime(adjustedCurrentTimeMillis);
                            } else {
                                if (DBG) {
                                    Rlog.d(LOG_TAG, "NITZ: ignore, a previous update was "
                                            + timeSinceLastUpdate + "ms ago and gained="
                                            + gained + "ms");
                                }
                                return;
                            }
                        }
                    }
                }
                saveNitzTime(adjustedCurrentTimeMillis);
            } finally {
                mWakeLock.release();
            }
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "NITZ: Processing NITZ data " + newNitzData + " ex=" + ex);
        }
    }

    private boolean getAutoTime() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME) > 0;
        } catch (Settings.SettingNotFoundException snfe) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME_ZONE) > 0;
        } catch (Settings.SettingNotFoundException snfe) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        mSavedTimeZoneId = zoneId;
    }

    private void saveNitzTime(long time) {
        SystemProperties.set("gsm.nitz.time", String.valueOf(time));
        mSavedTime = time;
        mSavedAtTime = SystemClock.elapsedRealtime();
        mNitzUpdatedTime = true;
    }

    /**
     * Set the timezone and send out a sticky broadcast so the system can
     * determine if the timezone was set by the carrier.
     *
     * @param zoneId timezone set by carrier
     */
    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        }
        AlarmManager alarm =
                (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarm.setTimeZone(zoneId);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time-zone", zoneId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        if (DBG) {
            Rlog.d(LOG_TAG,
                    "setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast"
                            + " zoneId=" + zoneId);
        }
    }

    /**
     * Set the time and Send out a sticky broadcast so the system can determine
     * if the time was set by the carrier.
     *
     * @param time time set by network
     */
    private void setAndBroadcastNetworkSetTime(long time) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setAndBroadcastNetworkSetTime: time=" + time + "ms");
        }
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time", time);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

        TelephonyMetrics.getInstance().writeNITZEvent(mPhone.getPhoneId(), time);
    }

    private void revertToNitzTime() {
        if (!getAutoTime()) {
            return;
        }
        if (DBG) {
            Rlog.d(LOG_TAG, "Reverting to NITZ Time: mSavedTime=" + mSavedTime
                    + " mSavedAtTime=" + mSavedAtTime);
        }
        if (mSavedTime != 0 && mSavedAtTime != 0) {
            long currTime = SystemClock.elapsedRealtime();
            mTimeLog.log("Reverting to NITZ time, currTime=" + currTime
                    + " mSavedAtTime=" + mSavedAtTime + " mSavedTime=" + mSavedTime);
            setAndBroadcastNetworkSetTime(mSavedTime + (currTime - mSavedAtTime));
        }
    }

    private void revertToNitzTimeZone() {
        if (!getAutoTimeZone()) {
            return;
        }
        String tmpLog = "Reverting to NITZ TimeZone: tz=" + mSavedTimeZoneId;
        if (DBG) {
            Rlog.d(LOG_TAG, tmpLog);
        }
        mTimeZoneLog.log(tmpLog);
        if (mSavedTimeZoneId != null) {
            setAndBroadcastNetworkSetTimeZone(mSavedTimeZoneId);
        } else {
            String iso = ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE))
                    .getNetworkCountryIsoForPhone(mPhone.getPhoneId());
            if (!TextUtils.isEmpty(iso)) {
                updateTimeZoneByNetworkCountryCode(iso);
            }
        }
    }

    /**
     * Dumps the current in-memory state to the supplied PrintWriter.
     */
    public void dumpState(PrintWriter pw) {
        pw.println(" mNeedFixZoneAfterNitz=" + mNeedFixZoneAfterNitz);
        pw.println(" mNitzData=" + mNitzData);
        pw.println(" mGotCountryCode=" + mGotCountryCode);
        pw.println(" mSavedTimeZone=" + mSavedTimeZoneId);
        pw.println(" mSavedTime=" + mSavedTime);
        pw.println(" mSavedAtTime=" + mSavedAtTime);
        pw.println(" mWakeLock=" + mWakeLock);
        pw.println(" mNitzUpdatedTime=" + mNitzUpdatedTime);
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
     * Update time zone by network country code, works on countries which only have one time zone.
     *
     * @param iso Country code from network MCC
     */
    public void updateTimeZoneByNetworkCountryCode(String iso) {
        // Test both paths if ignore nitz is true
        boolean testOneUniqueOffsetPath = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_IGNORE_NITZ, false)
                && ((SystemClock.uptimeMillis() & 1) == 0);

        List<String> uniqueZoneIds = TimeUtils.getTimeZoneIdsWithUniqueOffsets(iso);
        if ((uniqueZoneIds.size() == 1) || testOneUniqueOffsetPath) {
            String zoneId = uniqueZoneIds.get(0);
            if (DBG) {
                Rlog.d(LOG_TAG, "updateTimeZoneByNetworkCountryCode: no nitz but one TZ for iso-cc="
                        + iso
                        + " with zone.getID=" + zoneId
                        + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
            }
            mTimeZoneLog.log("updateTimeZoneByNetworkCountryCode: set time zone=" + zoneId
                    + " iso=" + iso);
            setAndBroadcastNetworkSetTimeZone(zoneId);
        } else {
            if (DBG) {
                Rlog.d(LOG_TAG,
                        "updateTimeZoneByNetworkCountryCode: there are " + uniqueZoneIds.size()
                                + " unique offsets for iso-cc='" + iso
                                + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath
                                + "', do nothing");
            }
        }
    }

    /**
     * Clear the mNitzUpdatedTime flag.
     */
    public void clearNitzUpdatedTime() {
        mNitzUpdatedTime = false;
    }

    /**
     * Get the mNitzUpdatedTime flag value.
     */
    public boolean getNitzUpdatedTime() {
        return mNitzUpdatedTime;
    }

    /**
     * Sets the mGotCountryCode flag to the specified value.
     */
    public void setNetworkCountryIsoAvailable(boolean gotCountryCode) {
        mGotCountryCode = gotCountryCode;
    }

    /**
     * Returns true if mNitzUpdatedTime and automatic time zone detection is enabled.
     */
    public boolean shouldUpdateTimeZoneUsingCountryCode() {
        return !mNitzUpdatedTime && getAutoTimeZone();
    }

    /**
     * Returns the last NITZ data that was cached.
     */
    public NitzData getCachedNitzData() {
        return mNitzData;
    }

    /**
     * Returns the time zone ID from the most recent time that a time zone could be determined by
     * this state machine.
     */
    public String getSavedTimeZoneId() {
        return mSavedTimeZoneId;
    }

    /**
     * Returns the mNeedFixZoneAfterNitz flag value.
     */
    public boolean fixTimeZoneCallNeeded() {
        return mNeedFixZoneAfterNitz;
    }
}
