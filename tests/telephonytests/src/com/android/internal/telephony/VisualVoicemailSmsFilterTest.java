/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.internal.telephony;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailSmsFilterSettings;

import junit.framework.TestCase;

import org.mockito.Mockito;

public class VisualVoicemailSmsFilterTest extends TestCase {

    /**
     * b/29123941 iPhone style notification SMS is neither 3GPP nor 3GPP2, but some plain text
     * message. {@link android.telephony.SmsMessage.createFromPdu()} will fail to parse it and
     * return an invalid object, causing {@link NullPointerException} on any operation if not
     * handled.
     */
    public void testUnsupportedPdu() {
        Context context = Mockito.mock(Context.class);
        TelephonyManager telephonyManager = Mockito.mock(TelephonyManager.class);
        Mockito.when(context.getSystemServiceName(TelephonyManager.class))
                .thenReturn(Context.TELEPHONY_SERVICE);
        Mockito.when(context.getSystemService(Mockito.anyString())).thenReturn(telephonyManager);

        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .build();

        Mockito.when(telephonyManager
                .getVisualVoicemailSmsFilterSettings(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(settings);

        byte[][] pdus = {
                ("MBOXUPDATE?m=11;server=example.com;"
                        + "port=143;name=1234567890@example.com;pw=CphQJKnYS4jEiDO").getBytes()};
        VisualVoicemailSmsFilter.filter(context, pdus, SmsConstants.FORMAT_3GPP2, 0, 0);
    }

}
