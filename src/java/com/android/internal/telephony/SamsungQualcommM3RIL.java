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
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.RILConstants;
import java.util.Collections;
import android.telephony.PhoneNumberUtils;

import java.util.ArrayList;

/**
 * Custom RIL to handle unique behavior of M3 radio
 *
 * {@hide}
 */
public class SamsungQualcommM3RIL extends SamsungQualcommRIL implements CommandsInterface {

    public SamsungQualcommM3RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition();
        int response = p.readInt();

        switch (response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch (response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                /* has bonus radio state int */
                int state = p.readInt();
                Log.d(LOG_TAG, "Radio state: " + state);

                switch (state) {
                    case 2:
                        // RADIO_UNAVAILABLE
                        state = 1;
                        break;
                    case 3:
                        // RADIO_ON
                        state = 10;
                        break;
                    case 4:
                        // RADIO_ON
                        state = 10;
                        // When SIM is PIN-unlocked, RIL doesn't respond with RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED.
                        // We notify the system here.
                        Log.d(LOG_TAG, "SIM is PIN-unlocked now");
                        if (mIccStatusChangedRegistrants != null) {
                            mIccStatusChangedRegistrants.notifyRegistrants();
                        }
                        break;
                }
                RadioState newState = getRadioStateFromInt(state);
                Log.d(LOG_TAG, "New Radio state: " + state + " (" + newState.toString() + ")");
                switchToRadioState(newState);
                break;
        }
    }

}
