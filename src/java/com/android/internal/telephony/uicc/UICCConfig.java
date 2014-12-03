/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.Rlog;

/**
 * A class that stores various UICC Settings/values.
 * @hide
 */
public final class UICCConfig
{
    private final String PREFERENCE_NAME = "UICCConfig";
    private final String TAG = "UICCConfig";
    private final boolean LOG_DEBUG = false;

    private String mImsi;
    private int mMncLength;

    /**
     * A method to get the stored Imsi.
     * @hide
     */
    public String getImsi() {
        if (mImsi == null) {
            logd("Getting IMSI: null");
        } else {
            logd("Getting IMSI: " + mImsi);
        }
        return mImsi;
    }

    /**
     * A method to set the stored Imsi.
     * @hide
     */
    public void setImsi(String lImsi) {
        logd("Setting IMSI: " + lImsi);
        mImsi = lImsi;
    }

    /**
     * A method to get the stored MncLength.
     * @hide
     */
    public int getMncLength() {
        logd("Getting MncLength: " + Integer.toString(mMncLength));
        return mMncLength;
    }

    /**
     * A method to set the stored MncLength.
     * @hide
     */
    public void setMncLength(int lMncLength) {
        logd("Setting MncLength: " + Integer.toString(lMncLength));
        mMncLength = lMncLength;
    }

    private void logd(String sLog) {
        if (LOG_DEBUG) {
            Rlog.d(TAG, sLog);
        }
    }

    private void loge(String sLog)
    {
        Rlog.e(TAG, sLog);
    }

}