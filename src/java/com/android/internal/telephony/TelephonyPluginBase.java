/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony;

import android.content.Context;

import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.GsmServiceStateTracker;

public abstract class TelephonyPluginBase implements TelephonyPluginInterface {
    private String TAG = "TelephonyPluginBase";

    @Override
    public void makeDefaultPhones(Context context) {
        PhoneFactory.makeDefaultPhones(context);
    }

    @Override
    public DcTracker makeDcTracker(PhoneBase phone) {
       return new DcTracker(phone);
    }

    @Override
    public DctController makeDctController(PhoneProxy[] phones) {
        return DctController.makeDctController(phones);
    }

    @Override
    public void initSubscriptionController(Context context,
            CommandsInterface[] commandsInterfaces) {
        SubscriptionController.init(context, commandsInterfaces);
    }

    @Override
    public SubscriptionInfoUpdater makeSubscriptionInfoUpdater(Context context,
            Phone[] phoneProxy, CommandsInterface[] commandsInterfaces) {
        return new SubscriptionInfoUpdater(context, phoneProxy, commandsInterfaces);
    }

    @Override
    public PhoneBase makeGSMPhone(Context context, CommandsInterface ci,
            PhoneNotifier notifier, int phoneId) {
        return new GSMPhone(context, ci, notifier, phoneId);
    }

    @Override
    public PhoneProxy makePhoneProxy(PhoneBase phone) {
        return new PhoneProxy(phone);
    }

    @Override
    public PhoneBase makeCDMALTEPhone(Context context, CommandsInterface ci,
            PhoneNotifier notifier, int phoneId) {
        return new CDMALTEPhone(context, ci, notifier, phoneId);
    }

    @Override
    public void initExtTelephonyClasses(Context context,
            Phone[] phoneProxy, CommandsInterface[] commandsInterfaces) {
    }

    @Override
    public GsmServiceStateTracker makeGsmServiceStateTracker(GSMPhone phone) {
        return new GsmServiceStateTracker(phone);
    }
}
