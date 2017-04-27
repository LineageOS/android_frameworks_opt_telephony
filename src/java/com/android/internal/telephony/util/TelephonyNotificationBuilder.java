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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;

import com.android.internal.R;

public class TelephonyNotificationBuilder extends Notification.Builder {

    private final Context mContext;
    /**
     * list of {@link android.app.NotificationChannel} for telephony service.
     */
    public static final String CHANNEL_ID_ALERT = "alert";
    public static final String CHANNEL_ID_CALL_FORWARD = "callForward";
    public static final String CHANNEL_ID_MOBILE_DATA_ALERT = "mobileDataAlert";
    public static final String CHANNEL_ID_SMS = "sms";
    public static final String CHANNEL_ID_VOICE_MAIL = "voiceMail";
    public static final String CHANNEL_ID_WFC = "wfc";

    private static NotificationChannel createChannel(Context context, String channelId) {
        CharSequence name;
        int importance;
        boolean canShowBadge;
        boolean lights;
        boolean vibration;
        Uri sound = null;
        switch (channelId) {
            case CHANNEL_ID_ALERT:
                name = context.getText(R.string.notification_channel_network_alert);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                canShowBadge = false;
                lights = false;
                vibration = false;
                sound = Settings.System.DEFAULT_NOTIFICATION_URI;
                break;
            case CHANNEL_ID_CALL_FORWARD:
                name = context.getText(R.string.notification_channel_call_forward);
                importance = NotificationManager.IMPORTANCE_LOW;
                canShowBadge = false;
                lights = false;
                vibration = false;
                break;
            case CHANNEL_ID_MOBILE_DATA_ALERT:
                name = context.getText(R.string.notification_channel_mobile_data_alert);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                canShowBadge = false;
                lights = false;
                vibration = false;
                break;
            case CHANNEL_ID_SMS:
                name = context.getText(R.string.notification_channel_sms);
                importance = NotificationManager.IMPORTANCE_HIGH;
                canShowBadge = false;
                lights = false;
                vibration = false;
                sound = Settings.System.DEFAULT_NOTIFICATION_URI;
                break;
            case CHANNEL_ID_VOICE_MAIL:
                name = context.getText(R.string.notification_channel_voice_mail);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                canShowBadge = false;
                lights = false;
                vibration = false;
                sound = Settings.System.DEFAULT_NOTIFICATION_URI;
                break;
            case CHANNEL_ID_WFC:
                name = context.getText(R.string.notification_channel_wfc);
                importance = NotificationManager.IMPORTANCE_LOW;
                canShowBadge = false;
                lights = false;
                vibration = false;
                break;
            default:
                throw new IllegalArgumentException("Unknown channel: " + channelId);
        }

        NotificationChannel channel = new NotificationChannel(channelId, name, importance);
        if (sound != null) {
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        }
        channel.setShowBadge(canShowBadge);
        channel.enableLights(lights);
        channel.enableVibration(vibration);
        /**
         * Creates the notification channel and registers it with NotificationManager. If a channel
         * with the same ID is already registered, NotificationManager will ignore this call.
         */
        getNotificationManager(context).createNotificationChannel(channel);
        return channel;
    }

    private static NotificationManager getNotificationManager(Context context) {
        return context.getSystemService(NotificationManager.class);
    }

    /**
     * Specifies the channel the notification should be delivered on.
     * Will create the channel if it does not already exist.
     */
    @Override
    public Notification.Builder setChannel(String channelId) {
        // check if the channel has been created and registered
        NotificationChannel channel = getNotificationManager(mContext)
                .getNotificationChannel(channelId);
        if (channel == null) channel = createChannel(mContext, channelId);
        return super.setChannelId(channel.getId());
    }

    public TelephonyNotificationBuilder(Context context) {
        super(context);
        mContext = context;
    }
}
