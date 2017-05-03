/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.internal.telephony.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.provider.Settings;

import com.android.internal.R;

import java.util.Arrays;
import java.util.List;


public class NotificationChannelController {

    /**
     * list of {@link android.app.NotificationChannel} for telephony service.
     */
    public static final String CHANNEL_ID_ALERT = "alert";
    public static final String CHANNEL_ID_CALL_FORWARD = "callForward";
    public static final String CHANNEL_ID_MOBILE_DATA_ALERT = "mobileDataAlert";
    public static final String CHANNEL_ID_SMS = "sms";
    public static final String CHANNEL_ID_VOICE_MAIL = "voiceMail";
    public static final String CHANNEL_ID_WFC = "wfc";

    /**
     * Creates all notification channels and registers with NotificationManager. If a channel
     * with the same ID is already registered, NotificationManager will ignore this call.
     */
    private static void createAll(Context context) {
        final NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ID_ALERT,
                context.getText(R.string.notification_channel_network_alert),
                NotificationManager.IMPORTANCE_DEFAULT);
        alertChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());

        final NotificationChannel voiceMailChannel = new NotificationChannel(
                CHANNEL_ID_VOICE_MAIL,
                context.getText(R.string.notification_channel_voice_mail),
                NotificationManager.IMPORTANCE_DEFAULT);
        voiceMailChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());

        context.getSystemService(NotificationManager.class)
                .createNotificationChannels(Arrays.asList(
                new NotificationChannel(CHANNEL_ID_CALL_FORWARD,
                        context.getText(R.string.notification_channel_call_forward),
                        NotificationManager.IMPORTANCE_LOW),
                new NotificationChannel(CHANNEL_ID_MOBILE_DATA_ALERT,
                        context.getText(R.string.notification_channel_mobile_data_alert),
                        NotificationManager.IMPORTANCE_DEFAULT),
                new NotificationChannel(CHANNEL_ID_SMS,
                        context.getText(R.string.notification_channel_sms),
                        NotificationManager.IMPORTANCE_HIGH),
                new NotificationChannel(CHANNEL_ID_WFC,
                        context.getText(R.string.notification_channel_wfc),
                        NotificationManager.IMPORTANCE_LOW),
                alertChannel, voiceMailChannel));
    }

    public NotificationChannelController(Context context) {
        context.registerReceiver(mLocaleChangeReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        createAll(context);
    }

    // rename all registered channels on locale change
    private BroadcastReceiver mLocaleChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            createAll(context);
        }
    };
}
