/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardApplicationStatus;
import com.android.internal.telephony.IccCardStatus;

import java.util.ArrayList;

public class HTCQualcommRIL extends RIL implements CommandsInterface {

    private static final int RIL_UNSOL_ENTER_LPM = 1523;
    private static final int RIL_UNSOL_CDMA_3G_INDICATOR = 3009;
    private static final int RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR = 3012;
    private static final int RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL = 3020;
    private static final int RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE = 6002;
    private static final int RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED = 21004;
    private static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED = 21005;
    private static final int RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED = 21007;

    public HTCQualcommRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        Object ret;

        boolean extraIccCardStates = needsOldRilFeature("extraicccardstates");

        if (extraIccCardStates) {
            int dataPosition = p.dataPosition();
            int cardState = p.readInt();

            if (cardState >= 3) {
                ret = responseVoid(p);
            } else {
                p.setDataPosition(dataPosition);
                ret = super.responseIccCardStatus(p);
            }
        } else {
            ret = super.responseIccCardStatus(p);
        }

        // force CDMA + LTE network mode
        boolean forceCdmaLte = needsOldRilFeature("forceCdmaLteNetworkType");

        if (forceCdmaLte) {
            setPreferredNetworkType(NETWORK_MODE_LTE_CDMA_EVDO, null);
        }

        return ret;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_ENTER_LPM: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_3G_INDICATOR:  ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR:  ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL:  ret = responseStrings(p); break;
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE:  ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case RIL_UNSOL_ENTER_LPM:
            case RIL_UNSOL_CDMA_3G_INDICATOR:
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR:
            case RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL:
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE:
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED:
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;
            case RIL_UNSOL_RIL_CONNECTED: {
                if (RILJ_LOGD) unsljLogRet(response, ret);

                boolean skipRadioPowerOff = needsOldRilFeature("skipradiooff");

                // Initial conditions
                if (!skipRadioPowerOff) {
                    setRadioPower(false, null);
                }
                setPreferredNetworkType(mPreferredNetworkType, null);
                setCdmaSubscriptionSource(mCdmaSubscription, null);
                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                break;
            }
        }
    }
}
