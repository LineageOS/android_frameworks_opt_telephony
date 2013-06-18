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
import android.os.Registrant;
import android.os.Handler;
import android.util.Log;

/**
 * Generic HTC Tegra3 RIL class
 *
 * {@hide}
 */
public class HTCTegra3RIL extends RIL implements CommandsInterface {
    private static final int RIL_UNSOL_CUSTOMIZE_SIM_INFO = 1550;
    private Object mCatProCmdBuffer;
    public HTCTegra3RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_CUSTOMIZE_SIM_INFO: ret = responseString(p); break;
            case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case RIL_UNSOL_CUSTOMIZE_SIM_INFO:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                break;
            case RIL_UNSOL_STK_PROACTIVE_COMMAND:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCatProCmdRegistrant != null) {
                    mCatProCmdRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                } else {
                    // The RIL will send a CAT proactive command before the
                    // registrant is registered. Buffer it to make sure it
                    // does not get ignored (and breaks CatService).
                    // see also SamsungExynos3RIL
                    mCatProCmdBuffer = ret;
                }
                break;
        }
    }

    @Override
    public void setOnCatProactiveCmd(Handler h, int what, Object obj) {
        mCatProCmdRegistrant = new Registrant (h, what, obj);
        if (mCatProCmdBuffer != null) {
            mCatProCmdRegistrant.notifyRegistrant(
                                new AsyncResult (null, mCatProCmdBuffer, null));
            mCatProCmdBuffer = null;
        }
    }

}
