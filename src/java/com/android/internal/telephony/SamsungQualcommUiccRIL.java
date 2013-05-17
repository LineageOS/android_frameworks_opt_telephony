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
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.os.AsyncResult;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.PhoneNumberUtils;
import com.android.internal.telephony.RILConstants;

import java.util.Collections;
import java.util.ArrayList;

/**
 * Custom RIL to handle unique behavior of Hercules/Skyrocket/Note radio
 *
 * {@hide}
 */
public class SamsungQualcommUiccRIL extends SamsungQualcommRIL implements CommandsInterface {
    boolean RILJ_LOGV = false;
    boolean RILJ_LOGD = false;
    protected boolean samsungDriverCall;
    public SamsungQualcommUiccRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
        samsungDriverCall = (!(mRilVersion < 7));
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition();   // save off position within the Parcel
        int response     = p.readInt();

        switch(response) {
            case RIL_UNSOL_NITZ_TIME_RECEIVED:
                handleNitzTimeReceived(p);
                return;
            case 1038: ret = responseVoid(p); break; // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case 1038: // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED
                if (RILJ_LOGD) unsljLog(response);

                // Notifying on voice state change since it just causes a
                // GsmServiceStateTracker::pollState() like CAF RIL does.
                mVoiceNetworkStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
        }
    }

    protected void
    handleNitzTimeReceived(Parcel p) {
        String nitz = (String)responseString(p);
        if (RILJ_LOGD) unsljLogRet(RIL_UNSOL_NITZ_TIME_RECEIVED, nitz);

        // has bonus long containing milliseconds since boot that the NITZ
        // time was received
        long nitzReceiveTime = p.readLong();

        Object[] result = new Object[2];

        String fixedNitz = nitz;
        String[] nitzParts = nitz.split(",");
        if (nitzParts.length == 4) {
            // 0=date, 1=time+zone, 2=dst, 3=garbage that confuses GsmServiceStateTracker (so remove it)
            fixedNitz = nitzParts[0]+","+nitzParts[1]+","+nitzParts[2]+",";
        }

        result[0] = fixedNitz;
        result[1] = Long.valueOf(nitzReceiveTime);

        if (mNITZTimeRegistrant != null) {

            mNITZTimeRegistrant
                .notifyRegistrant(new AsyncResult (null, result, null));
        } else {
            // in case NITZ time registrant isnt registered yet
            mLastNITZTimeInfo = result;
        }
    }
  }
