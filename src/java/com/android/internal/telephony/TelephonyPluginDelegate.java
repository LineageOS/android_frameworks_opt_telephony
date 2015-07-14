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
import android.util.Log;
import android.telephony.Rlog;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.TelephonyPluginBase;
import com.android.internal.telephony.DefaultTelephonyPlugin;
import com.android.internal.R;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.io.File;

public class TelephonyPluginDelegate {
    static final String LOG_TAG = "TelephonyPluginDelegate";

    private static TelephonyPluginBase sPlugin = null;
    private static TelephonyPluginDelegate sMe = null;

    private TelephonyPluginDelegate() {
    }

    public static TelephonyPluginDelegate getInstance() {
        if (sMe == null) {
            Log.e(LOG_TAG, "error: TelephonyPluginDelegate instance is not created!!");
        }
        return sMe;
    }

    public static void init(Context context) {
        if (sMe == null) {
            String fullClsName = context.getResources()
                .getString(R.string.telephony_plugin_class_name);
            String libPath = context.getResources().getString(R.string.telephony_plugin_lib_path);

            PathClassLoader classLoader = new PathClassLoader(libPath,
                    ClassLoader.getSystemClassLoader());
            Rlog.d(LOG_TAG, "classLoader = " + classLoader);

            if (fullClsName == null || fullClsName.length() == 0) {
                Rlog.d(LOG_TAG, "No customized TelephonyPlugin available, fallback to default");
                fullClsName = "com.android.internal.telephony.DefaultTelephonyPlugin";
            }
            Class<?> cls = null;
            try {
                cls = Class.forName(fullClsName, false, classLoader);
                Rlog.d(LOG_TAG, "cls = " + cls);
                Constructor custMethod = cls.getConstructor();
                Rlog.d(LOG_TAG, "constructor method = " + custMethod);
                sPlugin = (TelephonyPluginBase) custMethod.newInstance();
            } catch (Exception  e) {
                e.printStackTrace();
                Rlog.e(LOG_TAG, "Error loading TelephonyPlugin");
                sPlugin = new DefaultTelephonyPlugin();
            }

            sMe = new TelephonyPluginDelegate();
        } else {
            Rlog.e(LOG_TAG, "Multiple init of TelephonyPluginDelegate not allowed");
        }
    }

    public DcTracker makeDcTracker(PhoneBase phone) {
        return sPlugin.makeDcTracker(phone);
    }

    public void makeDefaultPhones(Context context) {
        sPlugin.makeDefaultPhones(context);
    }

    public DctController makeDctController(PhoneProxy[] phones) {
        return sPlugin.makeDctController(phones);
    }

    public void initSubscriptionController(Context context,
            CommandsInterface[] commandsInterfaces) {
        sPlugin.initSubscriptionController(context, commandsInterfaces);
    }
}
