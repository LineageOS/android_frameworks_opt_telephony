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

package com.android.internal.telephony;

import android.app.AlarmManager;
import android.app.timedetector.PhoneTimeSuggestion;
import android.app.timedetector.TimeDetector;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;

/**
 * An interface to various time / time zone detection behaviors that should be centralized into a
 * new service.
 */
public final class TimeServiceHelperImpl implements TimeServiceHelper {

    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    private final Context mContext;
    private final ContentResolver mCr;
    private final TimeDetector mTimeDetector;

    private Listener mListener;

    /** Creates a TimeServiceHelper */
    public TimeServiceHelperImpl(Context context) {
        mContext = context;
        mCr = context.getContentResolver();
        mTimeDetector = context.getSystemService(TimeDetector.class);
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
    public boolean isTimeZoneSettingInitialized() {
        // timezone.equals("GMT") will be true and only true if the timezone was
        // set to a default value by the system server (when starting, system server
        // sets the persist.sys.timezone to "GMT" if it's not set). "GMT" is not used by
        // any code that sets it explicitly (in case where something sets GMT explicitly,
        // "Etc/GMT" Olsen ID would be used).
        // TODO(b/64056758): Remove "timezone.equals("GMT")" hack when there's a
        // better way of telling if the value has been defaulted.

        String timeZoneId = SystemProperties.get(TIMEZONE_PROPERTY);
        return timeZoneId != null && timeZoneId.length() > 0 && !timeZoneId.equals("GMT");

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
    public void setDeviceTimeZone(String zoneId) {
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setTimeZone(zoneId);
    }

    @Override
    public void suggestDeviceTime(PhoneTimeSuggestion phoneTimeSuggestion) {
        mTimeDetector.suggestPhoneTime(phoneTimeSuggestion);
    }
}
