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
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * An interface to various time / time zone detection behaviors that should be centralized into a
 * new service.
 */
// Non-final to allow mocking.
public class TimeServiceHelper {

    /**
     * Callback interface for automatic detection enable/disable changes.
     */
    public interface Listener {
        /**
         * Automatic time detection has been enabled or disabled.
         */
        void onTimeDetectionChange(boolean enabled);

        /**
         * Automatic time zone detection has been enabled or disabled.
         */
        void onTimeZoneDetectionChange(boolean enabled);
    }

    public static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    private final Context mContext;
    private final ContentResolver mCr;

    private Listener mListener;

    /** Creates a TimeServiceHelper */
    public TimeServiceHelper(Context context) {
        mContext = context;
        mCr = context.getContentResolver();
    }

    /**
     * Sets a listener that will be called when the automatic time / time zone detection setting
     * changes.
     */
    public void setListener(Listener listener) {
        if (listener == null) {
            throw new NullPointerException("listener==null");
        }
        if (mListener != null) {
            throw new IllegalStateException("listener already set");
        }
        this.mListener = listener;
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME), true,
                new ContentObserver(new Handler()) {
                    public void onChange(boolean selfChange) {
                        listener.onTimeDetectionChange(isTimeDetectionEnabled());
                    }
                });
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), true,
                new ContentObserver(new Handler()) {
                    public void onChange(boolean selfChange) {
                        listener.onTimeZoneDetectionChange(isTimeZoneDetectionEnabled());
                    }
                });
    }

    /**
     * Returns true if the device has an explicit time zone set.
     */
    public boolean isTimeZoneSettingInitialized() {
        String timeZoneId = SystemProperties.get(TIMEZONE_PROPERTY);
        return timeZoneId != null && timeZoneId.length() > 0;
    }

    /**
     * Returns true if automatic time detection is enabled in settings.
     */
    public boolean isTimeDetectionEnabled() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME) > 0;
        } catch (Settings.SettingNotFoundException snfe) {
            return true;
        }
    }

    /**
     * Returns true if automatic time zone detection is enabled in settings.
     */
    public boolean isTimeZoneDetectionEnabled() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME_ZONE) > 0;
        } catch (Settings.SettingNotFoundException snfe) {
            return true;
        }
    }

    /**
     * Set the device time zone and send out a sticky broadcast so the system can
     * determine if the timezone was set by the carrier.
     *
     * @param zoneId timezone set by carrier
     */
    public void setDeviceTimeZone(String zoneId) {
        AlarmManager alarm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarm.setTimeZone(zoneId);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time-zone", zoneId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * Set the time and Send out a sticky broadcast so the system can determine
     * if the time was set by the carrier.
     *
     * @param time time set by network
     */
    public void setDeviceTime(long time) {
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time", time);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }
}
