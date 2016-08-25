/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.hardware.radio.V1_0.IRadioIndication;
import android.hardware.radio.V1_0.RadioIndicationType;
import android.hardware.radio.V1_0.SetupDataCallResult;
import android.hardware.radio.V1_0.SuppSvcNotification;
import android.hardware.radio.V1_0.SimRefreshResult;
import android.hardware.radio.V1_0.CdmaSignalInfoRecord;
import android.hardware.radio.V1_0.CdmaSmsMessage;
import android.hardware.radio.V1_0.CdmaCallWaiting;
import android.hardware.radio.V1_0.StkCcUnsolSsResult;
import android.hardware.radio.V1_0.LceDataInfo;
import android.hardware.radio.V1_0.PcoDataInfo;
import android.os.Message;

import java.util.ArrayList;
import static com.android.internal.telephony.RILConstants.*;

public class RadioIndication extends IRadioIndication.Stub {
    RIL mRil;

    RadioIndication(RIL ril) {
        mRil = ril;
    }

    /**
     * Indicates when radio state changes.
     * @param indicationType RadioIndicationType
     * @param radioState android.hardware.radio.V1_0.RadioState
     */
    public void radioStateChanged(int indicationType, int radioState) {
        mRil.processIndication(indicationType);
        CommandsInterface.RadioState newState = getRadioStateFromInt(radioState);
        mRil.riljLog("radioStateChanged: " + newState.toString());
        mRil.setRadioState(newState);
    }

    public void callStateChanged(int var1) {}

    public void voiceNetworkStateChanged(int var1) {}

    public void newSms(int var1, ArrayList<Byte> var2) {}

    public void newSmsStatusReport(int var1, ArrayList<Byte> var2) {}

    public void newSmsOnSim(int var1, int var2) {}

    public void onUssd(int var1, int var2, String var3) {}

    public void nitzTimeReceived(int var1, String var2, long var3) {}

    public void currentSignalStrength(int var1,
                                      android.hardware.radio.V1_0.SignalStrength var2) {}

    public void dataCallListChanged(int var1, ArrayList<SetupDataCallResult> var2) {}

    public void suppSvcNotify(int var1, SuppSvcNotification var2) {}

    public void stkSessionEnd(int var1) {}

    public void stkProactiveCommand(int var1, String var2) {}

    public void stkEventNotify(int var1, String var2) {}

    public void stkCallSetup(int var1, long var2) {}

    public void simSmsStorageFull(int var1) {}

    public void simRefresh(int var1, SimRefreshResult var2) {}

    public void callRing(int var1, boolean var2, CdmaSignalInfoRecord var3) {}

    public void simStatusChanged(int var1) {}

    public void cdmaNewSms(int var1, CdmaSmsMessage var2) {}

    public void newBroadcastSms(int var1, ArrayList<Byte> var2) {}

    public void cdmaRuimSmsStorageFull(int var1) {}

    public void restrictedStateChanged(int var1, int var2) {}

    public void enterEmergencyCallbackMode(int var1) {}

    public void cdmaCallWaiting(int var1, CdmaCallWaiting var2) {}

    public void cdmaOtaProvisionStatus(int var1, int var2) {}

    public void cdmaInfoRec(int var1,
                            android.hardware.radio.V1_0.CdmaInformationRecords var2) {}

    public void oemHookRaw(int var1, ArrayList<Byte> var2) {}

    public void indicateRingbackTone(int var1, boolean var2) {}

    public void resendIncallMute(int var1) {}

    public void cdmaSubscriptionSourceChanged(int var1, int var2) {}

    public void cdmaPrlChanged(int var1, int var2) {}

    public void exitEmergencyCallbackMode(int var1) {}

    public void rilConnected(int var1) {}

    public void voiceRadioTechChanged(int var1, int var2) {}

    public void cellInfoList(int var1, ArrayList<android.hardware.radio.V1_0.CellInfo> var2) {}

    public void imsNetworkStateChanged(int var1) {}

    public void subscriptionStatusChanged(int var1, boolean var2) {}

    public void srvccStateNotify(int var1, int var2) {}

    public void hardwareConfigChanged(
            int var1,
            ArrayList<android.hardware.radio.V1_0.HardwareConfig> var2) {}

    public void radioCapabilityIndication(int var1,
                                          android.hardware.radio.V1_0.RadioCapability var2) {}

    public void onSupplementaryServiceIndication(int var1, StkCcUnsolSsResult var2) {}

    public void stkCallControlAlphaNotify(int var1, String var2) {}

    public void lceData(int var1, LceDataInfo var2) {}

    public void pcoData(int var1, PcoDataInfo var2) {}

    public void modemReset(int var1, String var2) {}

    private CommandsInterface.RadioState getRadioStateFromInt(int stateInt) {
        CommandsInterface.RadioState state;

        switch(stateInt) {
            case android.hardware.radio.V1_0.RadioState.OFF:
                state = CommandsInterface.RadioState.RADIO_OFF;
                break;
            case android.hardware.radio.V1_0.RadioState.UNAVAILABLE:
                state = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case android.hardware.radio.V1_0.RadioState.ON:
                state = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RadioState: " + stateInt);
        }
        return state;
    }
}