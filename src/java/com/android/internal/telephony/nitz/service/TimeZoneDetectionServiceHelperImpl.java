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

package com.android.internal.telephony.nitz.service;

import android.annotation.Nullable;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.IndentingPrintWriter;

import java.io.PrintWriter;

/**
 * The real implementation of {@link TimeZoneDetectionService.Helper}.
 */
public final class TimeZoneDetectionServiceHelperImpl implements TimeZoneDetectionService.Helper {

    private static final String LOG_TAG = TimeZoneDetectionService.LOG_TAG;
    private static final boolean DBG = TimeZoneDetectionService.DBG;
    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    private final Context mContext;
    private final ContentResolver mCr;
    private final LocalLog mTimeZoneLog = new LocalLog(30);

    private Listener mListener;

    /** Creates a TimeServiceHelper */
    public TimeZoneDetectionServiceHelperImpl(Context context) {
        mContext = context;
        mCr = context.getContentResolver();
    }

    @Override
    public void setListener(Listener listener) {
        if (listener == null) {
            throw new NullPointerException("listener==null");
        }
        if (mListener != null) {
            throw new IllegalStateException("listener already set");
        }
        this.mListener = listener;
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), true,
                new ContentObserver(new Handler()) {
                    public void onChange(boolean selfChange) {
                        listener.onTimeZoneDetectionChange(isTimeZoneDetectionEnabled());
                    }
                });
    }

    @Override
    public boolean isTimeZoneDetectionEnabled() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME_ZONE) > 0;
        } catch (Settings.SettingNotFoundException snfe) {
            return true;
        }
    }

    @Override
    public boolean isTimeZoneSettingInitialized() {
        // timezone.equals("GMT") will be true and only true if the time zone was
        // set to a default value by the system server (when starting, system server
        // sets the persist.sys.timezone to "GMT" if it's not set). "GMT" is not used by
        // any code that sets it explicitly (in case where something sets GMT explicitly,
        // "Etc/GMT" Olson ID would be used).

        String timeZoneId = getTimeZoneSetting();
        return timeZoneId != null && timeZoneId.length() > 0 && !timeZoneId.equals("GMT");
    }

    @Override
    public void setDeviceTimeZoneFromSuggestion(PhoneTimeZoneSuggestion timeZoneSuggestion) {
        String currentZoneId = getTimeZoneSetting();
        String newZoneId = timeZoneSuggestion.getZoneId();
        if (newZoneId.equals(currentZoneId)) {
            // No need to set the device time zone - the setting is already what we would be
            // suggesting.
            if (DBG) {
                Log.d(LOG_TAG, "setDeviceTimeZoneAsNeeded: No need to change the time zone;"
                        + " device is already set to the suggested zone."
                        + " timeZoneSuggestion=" + timeZoneSuggestion);
            }
            return;
        }

        String msg = "Changing device time zone. currentZoneId=" + currentZoneId
                + ", timeZoneSuggestion=" + timeZoneSuggestion;
        if (DBG) {
            Log.d(LOG_TAG, msg);
        }
        mTimeZoneLog.log(msg);

        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setTimeZone(newZoneId);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time-zone", newZoneId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Nullable
    private String getTimeZoneSetting() {
        return SystemProperties.get(TIMEZONE_PROPERTY);
    }

    @Override
    public void dumpState(PrintWriter pw) {
        pw.println(" TimeZoneDetectionServiceHelperImpl.getTimeZoneSetting()="
                + getTimeZoneSetting());
    }

    @Override
    public void dumpLogs(IndentingPrintWriter ipw) {
        ipw.println("TimeZoneDetectionServiceHelperImpl:");

        ipw.increaseIndent();
        ipw.println("Time zone logs:");
        ipw.increaseIndent();
        mTimeZoneLog.dump(ipw);
        ipw.decreaseIndent();

        ipw.decreaseIndent();
    }
}
