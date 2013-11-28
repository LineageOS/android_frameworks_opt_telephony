/*
 * Copyright (C) 2013 The CyanogenMod Project
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
import android.os.Parcel;
import android.provider.Settings;
import android.telephony.CellInfo;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;

public class HTCQualcommM7CDMARIL extends RIL implements CommandsInterface {

    static final int RIL_UNSOL_ENTER_LPM_M7 = 3023;
    static final int RIL_UNSOL_CDMA_3G_INDICATOR_M7 = 4259;
    static final int RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR_M7 = 4262;
    static final int RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL_M7 = 4270;
    static final int RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE_M7 = 4802;
    static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED_HTC_M7 = 5755;
    static final int RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED_M7 = 5757;
    private boolean isGSM = false;

    public HTCQualcommM7CDMARIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }
    @Override
    public void setPhoneType(int phoneType){
        super.setPhoneType(phoneType);
        isGSM = (phoneType != RILConstants.CDMA_PHONE);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplicationStatus appStatus;

        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();
        cardStatus.mImsSubscriptionAppIndex = p.readInt();

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];
        appStatus = new IccCardApplicationStatus();
        for (int i = 0 ; i < numApplications ; i++) {
            if (i != 0) {
                appStatus = new IccCardApplicationStatus();
            }
            appStatus.app_type       = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state      = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            appStatus.aid            = p.readString();
            appStatus.app_label      = p.readString();
            appStatus.pin1_replaced  = p.readInt();
            appStatus.pin1           = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2           = appStatus.PinStateFromRILInt(p.readInt());
            cardStatus.mApplications[i] = appStatus;
        }
        if (numApplications == 1 && !isGSM && appStatus.app_type == appStatus.AppTypeFromRILInt(2)) { // usim
            cardStatus.mApplications = new IccCardApplicationStatus[numApplications+2];
            cardStatus.mGsmUmtsSubscriptionAppIndex = 0;
            cardStatus.mApplications[cardStatus.mGsmUmtsSubscriptionAppIndex]=appStatus;
            cardStatus.mCdmaSubscriptionAppIndex = 1;
            cardStatus.mImsSubscriptionAppIndex = 2;
            IccCardApplicationStatus appStatus2 = new IccCardApplicationStatus();
            appStatus2.app_type       = appStatus2.AppTypeFromRILInt(4); // csim state
            appStatus2.app_state      = appStatus.app_state;
            appStatus2.perso_substate = appStatus.perso_substate;
            appStatus2.aid            = appStatus.aid;
            appStatus2.app_label      = appStatus.app_label;
            appStatus2.pin1_replaced  = appStatus.pin1_replaced;
            appStatus2.pin1           = appStatus.pin1;
            appStatus2.pin2           = appStatus.pin2;
            cardStatus.mApplications[cardStatus.mCdmaSubscriptionAppIndex] = appStatus2;
            IccCardApplicationStatus appStatus3 = new IccCardApplicationStatus();
            appStatus3.app_type       = appStatus3.AppTypeFromRILInt(5); // ims state
            appStatus3.app_state      = appStatus.app_state;
            appStatus3.perso_substate = appStatus.perso_substate;
            appStatus3.aid            = appStatus.aid;
            appStatus3.app_label      = appStatus.app_label;
            appStatus3.pin1_replaced  = appStatus.pin1_replaced;
            appStatus3.pin1           = appStatus.pin1;
            appStatus3.pin2           = appStatus.pin2;
            cardStatus.mApplications[cardStatus.mImsSubscriptionAppIndex] = appStatus3;
        }
        // get type from Settings.Global default to CDMA + LTE network mode
        boolean forceCdmaLte = needsOldRilFeature("forceCdmaLteNetworkType");
        if (forceCdmaLte) {
            int phoneType = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                            NETWORK_MODE_LTE_CDMA_EVDO);
            setPreferredNetworkType(phoneType, null);
        }
        return cardStatus;
    }

    @Override
    protected void
    processUnsolicited(Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch (response) {
            case RIL_UNSOL_ENTER_LPM_M7: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_3G_INDICATOR_M7: ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR_M7: ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_NETWORK_BASE_PLUSCODE_DIAL_M7: ret = responseStrings(p); break;
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE_M7: ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED_HTC_M7: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED_M7: ret = responseInts(p); break;
            case RIL_UNSOL_RIL_CONNECTED:
                ret = responseInts(p);

                // Initial conditions
                setRadioPower(false, null);
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
