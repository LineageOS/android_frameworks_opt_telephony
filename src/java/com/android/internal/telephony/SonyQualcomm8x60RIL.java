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
import android.os.Message;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;

/*
 * Custom Qualcomm No SimReady RIL for Sony 8x60 Radio
 *
 * {@hide}
 */
public class SonyQualcomm8x60RIL extends RIL implements CommandsInterface {
    public SonyQualcomm8x60RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    public void
    dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);

        rr.mp.writeString(address);
        rr.mp.writeInt(clirMode);

        if (uusInfo == null) {
            rr.mp.writeInt(0); // UUS information is absent
        } else {
            rr.mp.writeInt(1); // UUS information is present
            rr.mp.writeInt(uusInfo.getType());
            rr.mp.writeInt(uusInfo.getDcs());
            rr.mp.writeByteArray(uusInfo.getUserData());
        }
        rr.mp.writeInt(255);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    setNetworkSelectionMode(String operatorNumeric, Message response) {
        RILRequest rr;

        if (operatorNumeric == null)
            rr = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC, response);
        else
            rr = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL, response);

        rr.mp.writeString(operatorNumeric);
        rr.mp.writeInt(-1);

        send(rr);
    }

    @Override
    public void
    setNetworkSelectionModeAutomatic(Message response) {
        setNetworkSelectionMode(null, response);
    }

    @Override
    public void
    setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        setNetworkSelectionMode(operatorNumeric, response);
    }

}
