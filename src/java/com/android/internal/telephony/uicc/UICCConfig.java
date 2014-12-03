
package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.*;
import android.telephony.Rlog;
import java.util.*;

public final class UICCConfig
{

    private UICCConfig()
    {
    }

    private static final String PREFERENCE_NAME = "UICCConfig";
    private static final String TAG = "UICCConfig";
    private static final boolean LOG_DEBUG = false;

    private static String mImsi;
    private static int mMncLength;

    public static String getIMSI(String defVal)
    {
    	if (mImsi == null) {
    		logd("Getting IMSI: null");
    		return defVal;
    	} else {
    		logd("Getting IMSI: " + mImsi);
    		return mImsi;
    	}
    }

    public static void setIMSI(String lImsi)
    {
    	logd("Setting IMSI: " + lImsi);
        mImsi = lImsi;
    }

    public static int getMncLength(int defVal)
    {
    	if (mMncLength <= -1) {
    		logd("Getting MncLength: " + Integer.toString(defVal));
    		return defVal;
    	} else {
    		logd("Getting MncLength: " + Integer.toString(mMncLength));
    		return mMncLength;
    	}
    }

    public static void setMncLength(int lMncLength)
    {
    	logd("Setting MncLength: " + Integer.toString(lMncLength));
        mMncLength = lMncLength;
    }

    public static void logd(String sLog)
    {
    	if (LOG_DEBUG) {
    		Rlog.d(TAG, sLog);
    	}
    }

    public static void loge(String sLog)
    {
        Rlog.e(TAG, sLog);
    }

}