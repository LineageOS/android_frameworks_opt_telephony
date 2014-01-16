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
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.telephony.CellInfo;
import android.telephony.Rlog;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DataCallResponse;

import java.util.ArrayList;

public class HTCQualcommRIL extends QualcommMSIM42RIL implements CommandsInterface {

    private static final int RIL_UNSOL_ENTER_LPM = 1523;
    private static final int RIL_UNSOL_CDMA_3G_INDICATOR = 3009;
    private static final int RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR = 3012;
    private static final int RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL = 3020;
    private static final int RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE = 6002;
    private static final int RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE_M7 = 4802;
    private static final int RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED_HTC = 21004;
    private static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED_HTC = 21005;
    private static final int RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED = 21007;
    private static final int RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED_M7 = 5757;

    public HTCQualcommRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        // get type from Settings.Global default to CDMA + LTE network mode
        boolean forceCdmaLte = needsOldRilFeature("forceCdmaLteNetworkType");
        if (forceCdmaLte) {
            int phoneType = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                            NETWORK_MODE_LTE_CDMA_EVDO);
            setPreferredNetworkType(phoneType, null);
        }

        return super.responseIccCardStatus(p);
    }

    @Override
    protected DataCallResponse getDataCallResponse(Parcel p, int version) {
        DataCallResponse dataCall = new DataCallResponse();

        dataCall.version = version;
        dataCall.status = p.readInt();
        dataCall.suggestedRetryTime = p.readInt();
        dataCall.cid = p.readInt();
        dataCall.active = p.readInt();
        dataCall.type = p.readString();
        dataCall.ifname = p.readString();
        /* Check dataCall.active != 0 so address, dns, gateways are provided
         * when switching LTE<->3G<->2G */
        if ((dataCall.status == DcFailCause.NONE.getErrorCode()) &&
                TextUtils.isEmpty(dataCall.ifname) && dataCall.active != 0) {
            throw new RuntimeException("getDataCallResponse, no ifname");
        }
        String addresses = p.readString();
        if (!TextUtils.isEmpty(addresses)) {
            dataCall.addresses = addresses.split(" ");
        }
        String dnses = p.readString();
        if (!TextUtils.isEmpty(dnses)) {
            dataCall.dnses = dnses.split(" ");
        }
        String gateways = p.readString();
        if (!TextUtils.isEmpty(gateways)) {
            dataCall.gateways = gateways.split(" ");
        }
        return dataCall;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_ENTER_LPM: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_3G_INDICATOR: ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR: ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL: ret = responseStrings(p); break;
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE_M7:
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE: ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED_HTC:
                ret = responseVoid(p);

                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mVoiceRadioTechChangedRegistrants != null) {
                    mVoiceRadioTechChangedRegistrants.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED_HTC: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED_M7:
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED: ret = responseInts(p); break;
            case RIL_UNSOL_RIL_CONNECTED:
                ret = responseInts(p);

                if (RILJ_LOGD) unsljLogRet(response, ret);

                // Initial conditions
                if (SystemProperties.get("ril.socket.reset").equals("1")) {
                    setRadioPower(false, null);
                }
                // Trigger socket reset if RIL connect is called again
                SystemProperties.set("ril.socket.reset", "1");
                setPreferredNetworkType(mPreferredNetworkType, null);
                setCdmaSubscriptionSource(mCdmaSubscription, null);
                setCellInfoListRate(Integer.MAX_VALUE, null);
                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                break;
            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
            return;
        }
    }
}
