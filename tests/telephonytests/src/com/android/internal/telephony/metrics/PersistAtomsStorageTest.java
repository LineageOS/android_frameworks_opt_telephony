/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import static android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_GPRS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_GSM;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import static com.android.internal.telephony.TelephonyStatsLog.GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY;
import static com.android.internal.telephony.TelephonyStatsLog.GBA_EVENT__FAILED_REASON__UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__CLIENT_PARAMS_SENT;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.telephony.DisconnectCause;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyProtoEnums;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.SipDelegateManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyStatsLog;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularDataServiceSwitch;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularServiceState;
import com.android.internal.telephony.nano.PersistAtomsProto.DataCallSession;
import com.android.internal.telephony.nano.PersistAtomsProto.GbaEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsDedicatedBearerEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsDedicatedBearerListenerEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationFeatureTagStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationServiceDescStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationTermination;
import com.android.internal.telephony.nano.PersistAtomsProto.PersistAtoms;
import com.android.internal.telephony.nano.PersistAtomsProto.PresenceNotifyEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.RcsAcsProvisioningStats;
import com.android.internal.telephony.nano.PersistAtomsProto.RcsClientProvisioningStats;
import com.android.internal.telephony.nano.PersistAtomsProto.SipDelegateStats;
import com.android.internal.telephony.nano.PersistAtomsProto.SipMessageResponse;
import com.android.internal.telephony.nano.PersistAtomsProto.SipTransportFeatureTagStats;
import com.android.internal.telephony.nano.PersistAtomsProto.SipTransportSession;
import com.android.internal.telephony.nano.PersistAtomsProto.UceEventStats;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallRatUsage;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallSession;
import com.android.internal.telephony.nano.TelephonyProto.TelephonyCallSession.Event.AudioCodec;
import com.android.internal.telephony.protobuf.nano.MessageNano;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;

public class PersistAtomsStorageTest extends TelephonyTest {
    private static final String TEST_FILE = "PersistAtomsStorageTest.pb";
    private static final int MAX_NUM_CALL_SESSIONS = 50;
    private static final long START_TIME_MILLIS = 2000L;
    private static final int CARRIER1_ID = 1;
    private static final int CARRIER2_ID = 1187;
    private static final int CARRIER3_ID = 1435;
    private static final int SLOT_ID1 = 1;
    private static final int SLOT_ID2 = 2;
    private static final int REGISTRATION1_TECH = 1;
    private static final int REGISTRATION2_TECH = 2;

    // Mocked classes
    private FileOutputStream mTestFileOutputStream;

    @Rule public TemporaryFolder mFolder = new TemporaryFolder();

    private File mTestFile;

    // call with SRVCC
    private VoiceCallSession mCall1Proto;

    // call held after another incoming call, ended before the other call
    private VoiceCallSession mCall2Proto;
    private VoiceCallSession mCall3Proto;

    // failed call
    private VoiceCallSession mCall4Proto;

    private VoiceCallRatUsage mCarrier1LteUsageProto;
    private VoiceCallRatUsage mCarrier1UmtsUsageProto;
    private VoiceCallRatUsage mCarrier2LteUsageProto;
    private VoiceCallRatUsage mCarrier3LteUsageProto;
    private VoiceCallRatUsage mCarrier3GsmUsageProto;

    private VoiceCallSession[] mVoiceCallSessions;
    private VoiceCallRatUsage[] mVoiceCallRatUsages;

    // data service state switch for slot 0 and 1
    private CellularDataServiceSwitch mServiceSwitch1Proto;
    private CellularDataServiceSwitch mServiceSwitch2Proto;

    // service states for slot 0 and 1
    private CellularServiceState mServiceState1Proto;
    private CellularServiceState mServiceState2Proto;
    private CellularServiceState mServiceState3Proto;
    private CellularServiceState mServiceState4Proto;
    private CellularServiceState mServiceState5Proto;

    private CellularDataServiceSwitch[] mServiceSwitches;
    private CellularServiceState[] mServiceStates;

    // IMS registrations for slot 0 and 1
    private ImsRegistrationStats mImsRegistrationStatsLte0;
    private ImsRegistrationStats mImsRegistrationStatsWifi0;
    private ImsRegistrationStats mImsRegistrationStatsLte1;

    // IMS registration terminations for slot 0 and 1
    private ImsRegistrationTermination mImsRegistrationTerminationLte;
    private ImsRegistrationTermination mImsRegistrationTerminationWifi;

    private ImsRegistrationStats[] mImsRegistrationStats;
    private ImsRegistrationTermination[] mImsRegistrationTerminations;

    // Data call sessions
    private DataCallSession mDataCallSession0;
    private DataCallSession mDataCallSession1;

    // RCS registration feature tags for slot 0 and 1
    private ImsRegistrationFeatureTagStats mImsRegistrationFeatureTagStats1Proto;
    private ImsRegistrationFeatureTagStats mImsRegistrationFeatureTagStats2Proto;
    private ImsRegistrationFeatureTagStats[] mImsRegistrationFeatureTagStatses;

    // RCS provisioning client stats for slot 0 and 1
    private RcsClientProvisioningStats mRcsClientProvisioningStats1Proto;
    private RcsClientProvisioningStats mRcsClientProvisioningStats2Proto;
    private RcsClientProvisioningStats[] mRcsClientProvisioningStatses;

    // RCS provisioning ACS stats for slot 0 and 1
    private RcsAcsProvisioningStats mRcsAcsProvisioningStats1Proto;
    private RcsAcsProvisioningStats mRcsAcsProvisioningStats2Proto;
    private RcsAcsProvisioningStats[] mRcsAcsProvisioningStatses;

    private ImsRegistrationServiceDescStats mImsRegistrationServiceIm;
    private ImsRegistrationServiceDescStats mImsRegistrationServiceFt;
    private ImsRegistrationServiceDescStats[] mImsRegistrationServiceDescStats;

    // IMS dedicated bearer listener event stats for slot 0 and 1
    private ImsDedicatedBearerListenerEvent mImsDedicatedBearerListenerEvent1;
    private ImsDedicatedBearerListenerEvent mImsDedicatedBearerListenerEvent2;
    private ImsDedicatedBearerListenerEvent[] mImsDedicatedBearerListenerEvents;

    // IMS dedicated bearer event stats for slot 0 and 1
    private ImsDedicatedBearerEvent mImsDedicatedBearerEvent1;
    private ImsDedicatedBearerEvent mImsDedicatedBearerEvent2;
    private ImsDedicatedBearerEvent[] mImsDedicatedBearerEvents;

    private UceEventStats mUceEventStats1;
    private UceEventStats mUceEventStats2;
    private UceEventStats[] mUceEventStatses;

    private PresenceNotifyEvent mPresenceNotifyEvent1;
    private PresenceNotifyEvent mPresenceNotifyEvent2;
    private PresenceNotifyEvent[] mPresenceNotifyEvents;

    private SipTransportFeatureTagStats mSipTransportFeatureTagStats1;
    private SipTransportFeatureTagStats mSipTransportFeatureTagStats2;
    private SipTransportFeatureTagStats[] mSipTransportFeatureTagStatsArray;

    private SipDelegateStats mSipDelegateStats1;
    private SipDelegateStats mSipDelegateStats2;
    private SipDelegateStats mSipDelegateStats3;
    private SipDelegateStats[] mSipDelegateStatsArray;

    private GbaEvent mGbaEvent1;
    private GbaEvent mGbaEvent2;
    private GbaEvent[] mGbaEvent;

    private SipMessageResponse mSipMessageResponse1;
    private SipMessageResponse mSipMessageResponse2;
    private SipMessageResponse[] mSipMessageResponse;

    private SipTransportSession mSipTransportSession1;
    private SipTransportSession mSipTransportSession2;
    private SipTransportSession[] mSipTransportSession;

    private void makeTestData() {
        // MO call with SRVCC (LTE to UMTS)
        mCall1Proto = new VoiceCallSession();
        mCall1Proto.bearerAtStart = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
        mCall1Proto.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        mCall1Proto.direction = VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO;
        mCall1Proto.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
        mCall1Proto.setupFailed = false;
        mCall1Proto.disconnectReasonCode = DisconnectCause.LOCAL;
        mCall1Proto.disconnectExtraCode = 0;
        mCall1Proto.disconnectExtraMessage = "";
        mCall1Proto.ratAtStart = TelephonyManager.NETWORK_TYPE_LTE;
        mCall1Proto.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        mCall1Proto.ratSwitchCount = 1L;
        mCall1Proto.codecBitmask =
                (1 << AudioCodec.AUDIO_CODEC_EVS_SWB) | (1 << AudioCodec.AUDIO_CODEC_AMR);
        mCall1Proto.concurrentCallCountAtStart = 0;
        mCall1Proto.concurrentCallCountAtEnd = 0;
        mCall1Proto.simSlotIndex = 0;
        mCall1Proto.isMultiSim = false;
        mCall1Proto.isEsim = false;
        mCall1Proto.carrierId = CARRIER1_ID;
        mCall1Proto.srvccCompleted = true;
        mCall1Proto.srvccFailureCount = 0L;
        mCall1Proto.srvccCancellationCount = 0L;
        mCall1Proto.rttEnabled = false;
        mCall1Proto.isEmergency = false;
        mCall1Proto.isRoaming = false;

        // VoLTE MT call on DSDS/eSIM, hanged up by remote
        // concurrent with mCall3Proto, started first and ended first
        mCall2Proto = new VoiceCallSession();
        mCall2Proto.bearerAtStart = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
        mCall2Proto.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
        mCall2Proto.direction = VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT;
        mCall2Proto.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
        mCall2Proto.setupFailed = false;
        mCall2Proto.disconnectReasonCode = ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE;
        mCall2Proto.disconnectExtraCode = 0;
        mCall2Proto.disconnectExtraMessage = "normal call clearing";
        mCall2Proto.ratAtStart = TelephonyManager.NETWORK_TYPE_LTE;
        mCall2Proto.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        mCall2Proto.ratSwitchCount = 0L;
        mCall2Proto.codecBitmask = (1 << AudioCodec.AUDIO_CODEC_EVS_SWB);
        mCall2Proto.concurrentCallCountAtStart = 0;
        mCall2Proto.concurrentCallCountAtEnd = 1;
        mCall2Proto.simSlotIndex = 1;
        mCall2Proto.isMultiSim = true;
        mCall2Proto.isEsim = true;
        mCall2Proto.carrierId = CARRIER2_ID;
        mCall2Proto.srvccCompleted = false;
        mCall2Proto.srvccFailureCount = 0L;
        mCall2Proto.srvccCancellationCount = 0L;
        mCall2Proto.rttEnabled = false;
        mCall2Proto.isEmergency = false;
        mCall2Proto.isRoaming = false;

        // VoLTE MT call on DSDS/eSIM, hanged up by local, with RTT
        // concurrent with mCall2Proto, started last and ended last
        mCall3Proto = new VoiceCallSession();
        mCall3Proto.bearerAtStart = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
        mCall3Proto.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
        mCall3Proto.direction = VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT;
        mCall3Proto.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        mCall3Proto.setupFailed = false;
        mCall3Proto.disconnectReasonCode = ImsReasonInfo.CODE_USER_TERMINATED;
        mCall3Proto.disconnectExtraCode = 0;
        mCall3Proto.disconnectExtraMessage = "normal call clearing";
        mCall3Proto.ratAtStart = TelephonyManager.NETWORK_TYPE_LTE;
        mCall3Proto.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        mCall3Proto.ratSwitchCount = 0L;
        mCall3Proto.codecBitmask = (1 << AudioCodec.AUDIO_CODEC_EVS_SWB);
        mCall3Proto.concurrentCallCountAtStart = 1;
        mCall3Proto.concurrentCallCountAtEnd = 0;
        mCall3Proto.simSlotIndex = 1;
        mCall3Proto.isMultiSim = true;
        mCall3Proto.isEsim = true;
        mCall3Proto.carrierId = CARRIER2_ID;
        mCall3Proto.srvccCompleted = false;
        mCall3Proto.srvccFailureCount = 0L;
        mCall3Proto.srvccCancellationCount = 0L;
        mCall3Proto.rttEnabled = true;
        mCall3Proto.isEmergency = false;
        mCall3Proto.isRoaming = false;

        // CS MO emergency call while camped on LTE
        mCall4Proto = new VoiceCallSession();
        mCall4Proto.bearerAtStart = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        mCall4Proto.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        mCall4Proto.direction = VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO;
        mCall4Proto.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;
        mCall4Proto.setupFailed = true;
        mCall4Proto.disconnectReasonCode = DisconnectCause.NORMAL;
        mCall4Proto.disconnectExtraCode = 0;
        mCall4Proto.disconnectExtraMessage = "";
        mCall4Proto.ratAtStart = TelephonyManager.NETWORK_TYPE_LTE;
        mCall4Proto.ratAtEnd = TelephonyManager.NETWORK_TYPE_GSM;
        mCall4Proto.ratSwitchCount = 1L;
        mCall4Proto.codecBitmask = (1 << AudioCodec.AUDIO_CODEC_AMR);
        mCall4Proto.concurrentCallCountAtStart = 0;
        mCall4Proto.concurrentCallCountAtEnd = 0;
        mCall4Proto.simSlotIndex = 0;
        mCall4Proto.isMultiSim = true;
        mCall4Proto.isEsim = false;
        mCall4Proto.carrierId = CARRIER3_ID;
        mCall4Proto.srvccCompleted = false;
        mCall4Proto.srvccFailureCount = 0L;
        mCall4Proto.srvccCancellationCount = 0L;
        mCall4Proto.rttEnabled = false;
        mCall4Proto.isEmergency = true;
        mCall4Proto.isRoaming = true;

        mCarrier1LteUsageProto = new VoiceCallRatUsage();
        mCarrier1LteUsageProto.carrierId = CARRIER1_ID;
        mCarrier1LteUsageProto.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mCarrier1LteUsageProto.callCount = 1L;
        mCarrier1LteUsageProto.totalDurationMillis = 8000L;

        mCarrier1UmtsUsageProto = new VoiceCallRatUsage();
        mCarrier1UmtsUsageProto.carrierId = CARRIER1_ID;
        mCarrier1UmtsUsageProto.rat = TelephonyManager.NETWORK_TYPE_UMTS;
        mCarrier1UmtsUsageProto.callCount = 1L;
        mCarrier1UmtsUsageProto.totalDurationMillis = 6000L;

        mCarrier2LteUsageProto = new VoiceCallRatUsage();
        mCarrier2LteUsageProto.carrierId = CARRIER2_ID;
        mCarrier2LteUsageProto.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mCarrier2LteUsageProto.callCount = 2L;
        mCarrier2LteUsageProto.totalDurationMillis = 20000L;

        mCarrier3LteUsageProto = new VoiceCallRatUsage();
        mCarrier3LteUsageProto.carrierId = CARRIER3_ID;
        mCarrier3LteUsageProto.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mCarrier3LteUsageProto.callCount = 1L;
        mCarrier3LteUsageProto.totalDurationMillis = 1000L;

        mCarrier3GsmUsageProto = new VoiceCallRatUsage();
        mCarrier3GsmUsageProto.carrierId = CARRIER3_ID;
        mCarrier3GsmUsageProto.rat = TelephonyManager.NETWORK_TYPE_GSM;
        mCarrier3GsmUsageProto.callCount = 1L;
        mCarrier3GsmUsageProto.totalDurationMillis = 100000L;

        mVoiceCallRatUsages =
                new VoiceCallRatUsage[] {
                    mCarrier1UmtsUsageProto,
                    mCarrier1LteUsageProto,
                    mCarrier2LteUsageProto,
                    mCarrier3LteUsageProto,
                    mCarrier3GsmUsageProto
                };
        mVoiceCallSessions =
                new VoiceCallSession[] {mCall1Proto, mCall2Proto, mCall3Proto, mCall4Proto};

        // OOS to LTE on slot 0
        mServiceSwitch1Proto = new CellularDataServiceSwitch();
        mServiceSwitch1Proto.ratFrom = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mServiceSwitch1Proto.ratTo = TelephonyManager.NETWORK_TYPE_LTE;
        mServiceSwitch1Proto.simSlotIndex = 0;
        mServiceSwitch1Proto.isMultiSim = true;
        mServiceSwitch1Proto.carrierId = CARRIER1_ID;
        mServiceSwitch1Proto.switchCount = 1;

        // LTE to UMTS on slot 1
        mServiceSwitch2Proto = new CellularDataServiceSwitch();
        mServiceSwitch2Proto.ratFrom = TelephonyManager.NETWORK_TYPE_LTE;
        mServiceSwitch2Proto.ratTo = TelephonyManager.NETWORK_TYPE_UMTS;
        mServiceSwitch2Proto.simSlotIndex = 0;
        mServiceSwitch2Proto.isMultiSim = true;
        mServiceSwitch2Proto.carrierId = CARRIER2_ID;
        mServiceSwitch2Proto.switchCount = 2;

        // OOS on slot 0
        mServiceState1Proto = new CellularServiceState();
        mServiceState1Proto.voiceRat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mServiceState1Proto.dataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mServiceState1Proto.voiceRoamingType = ServiceState.ROAMING_TYPE_NOT_ROAMING;
        mServiceState1Proto.dataRoamingType = ServiceState.ROAMING_TYPE_NOT_ROAMING;
        mServiceState1Proto.isEndc = false;
        mServiceState1Proto.simSlotIndex = 0;
        mServiceState1Proto.isMultiSim = true;
        mServiceState1Proto.carrierId = CARRIER1_ID;
        mServiceState1Proto.totalTimeMillis = 5000L;
        mServiceState1Proto.isEmergencyOnly = false;

        // LTE with ENDC on slot 0
        mServiceState2Proto = new CellularServiceState();
        mServiceState2Proto.voiceRat = TelephonyManager.NETWORK_TYPE_LTE;
        mServiceState2Proto.dataRat = TelephonyManager.NETWORK_TYPE_LTE;
        mServiceState2Proto.voiceRoamingType = ServiceState.ROAMING_TYPE_NOT_ROAMING;
        mServiceState2Proto.dataRoamingType = ServiceState.ROAMING_TYPE_NOT_ROAMING;
        mServiceState2Proto.isEndc = true;
        mServiceState2Proto.simSlotIndex = 0;
        mServiceState2Proto.isMultiSim = true;
        mServiceState2Proto.carrierId = CARRIER1_ID;
        mServiceState2Proto.totalTimeMillis = 15000L;
        mServiceState2Proto.isEmergencyOnly = false;

        // LTE with WFC and roaming on slot 1
        mServiceState3Proto = new CellularServiceState();
        mServiceState3Proto.voiceRat = TelephonyManager.NETWORK_TYPE_IWLAN;
        mServiceState3Proto.dataRat = TelephonyManager.NETWORK_TYPE_LTE;
        mServiceState3Proto.voiceRoamingType = ServiceState.ROAMING_TYPE_INTERNATIONAL;
        mServiceState3Proto.dataRoamingType = ServiceState.ROAMING_TYPE_INTERNATIONAL;
        mServiceState3Proto.isEndc = false;
        mServiceState3Proto.simSlotIndex = 1;
        mServiceState3Proto.isMultiSim = true;
        mServiceState3Proto.carrierId = CARRIER2_ID;
        mServiceState3Proto.totalTimeMillis = 10000L;
        mServiceState3Proto.isEmergencyOnly = false;

        // UMTS with roaming on slot 1
        mServiceState4Proto = new CellularServiceState();
        mServiceState4Proto.voiceRat = TelephonyManager.NETWORK_TYPE_UMTS;
        mServiceState4Proto.dataRat = TelephonyManager.NETWORK_TYPE_UMTS;
        mServiceState4Proto.voiceRoamingType = ServiceState.ROAMING_TYPE_INTERNATIONAL;
        mServiceState4Proto.dataRoamingType = ServiceState.ROAMING_TYPE_INTERNATIONAL;
        mServiceState4Proto.isEndc = false;
        mServiceState4Proto.simSlotIndex = 1;
        mServiceState4Proto.isMultiSim = true;
        mServiceState4Proto.carrierId = CARRIER2_ID;
        mServiceState4Proto.totalTimeMillis = 10000L;
        mServiceState4Proto.isEmergencyOnly = false;

        // Limited service on slot 0
        mServiceState5Proto = new CellularServiceState();
        mServiceState5Proto.voiceRat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mServiceState5Proto.dataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mServiceState5Proto.voiceRoamingType = ServiceState.ROAMING_TYPE_NOT_ROAMING;
        mServiceState5Proto.dataRoamingType = ServiceState.ROAMING_TYPE_NOT_ROAMING;
        mServiceState5Proto.isEndc = false;
        mServiceState5Proto.simSlotIndex = 0;
        mServiceState5Proto.isMultiSim = true;
        mServiceState5Proto.carrierId = CARRIER1_ID;
        mServiceState5Proto.totalTimeMillis = 15000L;
        mServiceState5Proto.isEmergencyOnly = true;

        mServiceSwitches =
                new CellularDataServiceSwitch[] {mServiceSwitch1Proto, mServiceSwitch2Proto};
        mServiceStates =
                new CellularServiceState[] {
                    mServiceState1Proto,
                    mServiceState2Proto,
                    mServiceState3Proto,
                    mServiceState4Proto,
                    mServiceState5Proto
                };

        // IMS over LTE on slot 0, registered for 5 seconds
        mImsRegistrationStatsLte0 = new ImsRegistrationStats();
        mImsRegistrationStatsLte0.carrierId = CARRIER1_ID;
        mImsRegistrationStatsLte0.simSlotIndex = 0;
        mImsRegistrationStatsLte0.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mImsRegistrationStatsLte0.registeredMillis = 5000L;
        mImsRegistrationStatsLte0.voiceCapableMillis = 5000L;
        mImsRegistrationStatsLte0.voiceAvailableMillis = 5000L;
        mImsRegistrationStatsLte0.smsCapableMillis = 5000L;
        mImsRegistrationStatsLte0.smsAvailableMillis = 5000L;
        mImsRegistrationStatsLte0.videoCapableMillis = 5000L;
        mImsRegistrationStatsLte0.videoAvailableMillis = 5000L;
        mImsRegistrationStatsLte0.utCapableMillis = 5000L;
        mImsRegistrationStatsLte0.utAvailableMillis = 5000L;

        // IMS over WiFi on slot 0, registered for 10 seconds (voice only)
        mImsRegistrationStatsWifi0 = new ImsRegistrationStats();
        mImsRegistrationStatsWifi0.carrierId = CARRIER2_ID;
        mImsRegistrationStatsWifi0.simSlotIndex = 0;
        mImsRegistrationStatsWifi0.rat = TelephonyManager.NETWORK_TYPE_IWLAN;
        mImsRegistrationStatsWifi0.registeredMillis = 10000L;
        mImsRegistrationStatsWifi0.voiceCapableMillis = 10000L;
        mImsRegistrationStatsWifi0.voiceAvailableMillis = 10000L;

        // IMS over LTE on slot 1, registered for 20 seconds
        mImsRegistrationStatsLte1 = new ImsRegistrationStats();
        mImsRegistrationStatsLte1.carrierId = CARRIER1_ID;
        mImsRegistrationStatsLte1.simSlotIndex = 0;
        mImsRegistrationStatsLte1.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mImsRegistrationStatsLte1.registeredMillis = 20000L;
        mImsRegistrationStatsLte1.voiceCapableMillis = 20000L;
        mImsRegistrationStatsLte1.voiceAvailableMillis = 20000L;
        mImsRegistrationStatsLte1.smsCapableMillis = 20000L;
        mImsRegistrationStatsLte1.smsAvailableMillis = 20000L;
        mImsRegistrationStatsLte1.videoCapableMillis = 20000L;
        mImsRegistrationStatsLte1.videoAvailableMillis = 20000L;
        mImsRegistrationStatsLte1.utCapableMillis = 20000L;
        mImsRegistrationStatsLte1.utAvailableMillis = 20000L;

        // IMS terminations on LTE
        mImsRegistrationTerminationLte = new ImsRegistrationTermination();
        mImsRegistrationTerminationLte.carrierId = CARRIER1_ID;
        mImsRegistrationTerminationLte.isMultiSim = true;
        mImsRegistrationTerminationLte.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        mImsRegistrationTerminationLte.setupFailed = false;
        mImsRegistrationTerminationLte.reasonCode = ImsReasonInfo.CODE_REGISTRATION_ERROR;
        mImsRegistrationTerminationLte.extraCode = 999;
        mImsRegistrationTerminationLte.extraMessage = "Request Timeout";
        mImsRegistrationTerminationLte.count = 2;

        // IMS terminations on WiFi
        mImsRegistrationTerminationWifi = new ImsRegistrationTermination();
        mImsRegistrationTerminationWifi.carrierId = CARRIER2_ID;
        mImsRegistrationTerminationWifi.isMultiSim = true;
        mImsRegistrationTerminationWifi.ratAtEnd = TelephonyManager.NETWORK_TYPE_IWLAN;
        mImsRegistrationTerminationWifi.setupFailed = false;
        mImsRegistrationTerminationWifi.reasonCode = ImsReasonInfo.CODE_REGISTRATION_ERROR;
        mImsRegistrationTerminationWifi.extraCode = 0;
        mImsRegistrationTerminationWifi.extraMessage = "";
        mImsRegistrationTerminationWifi.count = 1;

        mImsRegistrationStats =
                new ImsRegistrationStats[] {
                    mImsRegistrationStatsLte0, mImsRegistrationStatsWifi0, mImsRegistrationStatsLte1
                };
        mImsRegistrationTerminations =
                new ImsRegistrationTermination[] {
                    mImsRegistrationTerminationLte, mImsRegistrationTerminationWifi
                };

        mDataCallSession0 = new DataCallSession();
        mDataCallSession0.dimension = 111;
        mDataCallSession0.carrierId = CARRIER1_ID;
        mDataCallSession0.oosAtEnd = false;
        mDataCallSession0.ratSwitchCount = 3L;
        mDataCallSession0.setupFailed = false;
        mDataCallSession0.durationMinutes = 20;
        mDataCallSession0.ongoing = true;
        mDataCallSession0.handoverFailureCauses = new int[]{3, 2, 1};
        mDataCallSession0.handoverFailureRat = new int[]{5, 5, 6};

        mDataCallSession1 = new DataCallSession();
        mDataCallSession1.dimension = 222;
        mDataCallSession1.carrierId = CARRIER2_ID;
        mDataCallSession1.oosAtEnd = true;
        mDataCallSession1.ratSwitchCount = 1L;
        mDataCallSession1.setupFailed = false;
        mDataCallSession1.durationMinutes = 5;
        mDataCallSession1.ongoing = false;

        // RCS registrtion feature tag slot 0
        mImsRegistrationFeatureTagStats1Proto = new ImsRegistrationFeatureTagStats();
        mImsRegistrationFeatureTagStats1Proto.carrierId = CARRIER1_ID;
        mImsRegistrationFeatureTagStats1Proto.slotId = 0;
        mImsRegistrationFeatureTagStats1Proto.featureTagName = 1;
        mImsRegistrationFeatureTagStats1Proto.registrationTech = TelephonyManager.NETWORK_TYPE_LTE;
        mImsRegistrationFeatureTagStats1Proto.registeredMillis = 3600L;

        // RCS registrtion feature tag slot 1
        mImsRegistrationFeatureTagStats2Proto = new ImsRegistrationFeatureTagStats();
        mImsRegistrationFeatureTagStats2Proto.carrierId = CARRIER2_ID;
        mImsRegistrationFeatureTagStats2Proto.slotId = 1;
        mImsRegistrationFeatureTagStats2Proto.featureTagName = 0;
        mImsRegistrationFeatureTagStats2Proto.registrationTech = TelephonyManager.NETWORK_TYPE_LTE;
        mImsRegistrationFeatureTagStats2Proto.registeredMillis = 3600L;

        mImsRegistrationFeatureTagStatses =
                new ImsRegistrationFeatureTagStats[] {
                        mImsRegistrationFeatureTagStats1Proto,
                        mImsRegistrationFeatureTagStats2Proto
                };

        // RCS client provisioning stats slot 0
        mRcsClientProvisioningStats1Proto = new RcsClientProvisioningStats();
        mRcsClientProvisioningStats1Proto.carrierId = CARRIER1_ID;
        mRcsClientProvisioningStats1Proto.slotId = 0;
        mRcsClientProvisioningStats1Proto.event =
                RCS_CLIENT_PROVISIONING_STATS__EVENT__CLIENT_PARAMS_SENT;
        mRcsClientProvisioningStats1Proto.count = 1;

        // RCS client provisioning stats slot 1
        mRcsClientProvisioningStats2Proto = new RcsClientProvisioningStats();
        mRcsClientProvisioningStats2Proto.carrierId = CARRIER2_ID;
        mRcsClientProvisioningStats2Proto.slotId = 1;
        mRcsClientProvisioningStats2Proto.event =
                RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION;
        mRcsClientProvisioningStats2Proto.count = 1;

        mRcsClientProvisioningStatses =
                new RcsClientProvisioningStats[] {
                        mRcsClientProvisioningStats1Proto,
                        mRcsClientProvisioningStats2Proto
                };

        // RCS ACS provisioning stats : error response
        mRcsAcsProvisioningStats1Proto = new RcsAcsProvisioningStats();
        mRcsAcsProvisioningStats1Proto.carrierId = CARRIER1_ID;
        mRcsAcsProvisioningStats1Proto.slotId = 0;
        mRcsAcsProvisioningStats1Proto.responseCode = 401;
        mRcsAcsProvisioningStats1Proto.responseType =
                RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR;
        mRcsAcsProvisioningStats1Proto.isSingleRegistrationEnabled = true;
        mRcsAcsProvisioningStats1Proto.count = 1;
        mRcsAcsProvisioningStats1Proto.stateTimerMillis = START_TIME_MILLIS;

        // RCS ACS provisioning stats : xml
        mRcsAcsProvisioningStats2Proto = new RcsAcsProvisioningStats();
        mRcsAcsProvisioningStats2Proto.carrierId = CARRIER1_ID;
        mRcsAcsProvisioningStats2Proto.slotId = 0;
        mRcsAcsProvisioningStats2Proto.responseCode = 200;
        mRcsAcsProvisioningStats2Proto.responseType =
                RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML;
        mRcsAcsProvisioningStats2Proto.isSingleRegistrationEnabled = true;
        mRcsAcsProvisioningStats2Proto.count = 1;
        mRcsAcsProvisioningStats2Proto.stateTimerMillis = START_TIME_MILLIS;

        mRcsAcsProvisioningStatses =
                new RcsAcsProvisioningStats[] {
                        mRcsAcsProvisioningStats1Proto,
                        mRcsAcsProvisioningStats2Proto
                };

        mImsRegistrationServiceIm = new ImsRegistrationServiceDescStats();
        mImsRegistrationServiceIm.carrierId = CARRIER1_ID;
        mImsRegistrationServiceIm.slotId = SLOT_ID1;
        mImsRegistrationServiceIm.serviceIdName = 0;
        mImsRegistrationServiceIm.serviceIdVersion = 1.0f;
        mImsRegistrationServiceIm.registrationTech = REGISTRATION1_TECH;
        mImsRegistrationServiceIm.publishedMillis = START_TIME_MILLIS;

        mImsRegistrationServiceFt = new ImsRegistrationServiceDescStats();
        mImsRegistrationServiceFt.carrierId = CARRIER2_ID;
        mImsRegistrationServiceFt.slotId = SLOT_ID2;
        mImsRegistrationServiceFt.serviceIdName = 1;
        mImsRegistrationServiceFt.serviceIdVersion = 2.0f;
        mImsRegistrationServiceFt.registrationTech = REGISTRATION2_TECH;
        mImsRegistrationServiceIm.publishedMillis = START_TIME_MILLIS;

        mImsRegistrationServiceDescStats =
            new ImsRegistrationServiceDescStats[] {
                mImsRegistrationServiceIm, mImsRegistrationServiceFt
            };


        mImsDedicatedBearerListenerEvent1 = new ImsDedicatedBearerListenerEvent();
        mImsDedicatedBearerListenerEvent1.carrierId = CARRIER1_ID;
        mImsDedicatedBearerListenerEvent1.slotId = SLOT_ID1;
        mImsDedicatedBearerListenerEvent1.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        mImsDedicatedBearerListenerEvent1.qci = 5;
        mImsDedicatedBearerListenerEvent1.dedicatedBearerEstablished = true;
        mImsDedicatedBearerListenerEvent1.eventCount = 1;

        mImsDedicatedBearerListenerEvent2 = new ImsDedicatedBearerListenerEvent();
        mImsDedicatedBearerListenerEvent2.carrierId = CARRIER2_ID;
        mImsDedicatedBearerListenerEvent2.slotId = SLOT_ID1;
        mImsDedicatedBearerListenerEvent2.ratAtEnd = TelephonyManager.NETWORK_TYPE_NR;
        mImsDedicatedBearerListenerEvent2.qci = 6;
        mImsDedicatedBearerListenerEvent2.dedicatedBearerEstablished = true;
        mImsDedicatedBearerListenerEvent2.eventCount = 1;

        mImsDedicatedBearerListenerEvents =
                new ImsDedicatedBearerListenerEvent[] {
                    mImsDedicatedBearerListenerEvent1, mImsDedicatedBearerListenerEvent2
                };


        mImsDedicatedBearerEvent1 = new ImsDedicatedBearerEvent();
        mImsDedicatedBearerEvent1.carrierId = CARRIER1_ID;
        mImsDedicatedBearerEvent1.slotId = SLOT_ID1;
        mImsDedicatedBearerEvent1.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        mImsDedicatedBearerEvent1.qci = 5;
        mImsDedicatedBearerEvent1.bearerState =
                TelephonyStatsLog.IMS_DEDICATED_BEARER_EVENT__BEARER_STATE__STATE_ADDED;
        mImsDedicatedBearerEvent1.localConnectionInfoReceived = true;
        mImsDedicatedBearerEvent1.remoteConnectionInfoReceived = true;
        mImsDedicatedBearerEvent1.hasListeners = true;
        mImsDedicatedBearerEvent1.count = 1;

        mImsDedicatedBearerEvent2 = new ImsDedicatedBearerEvent();
        mImsDedicatedBearerEvent2.carrierId = CARRIER1_ID;
        mImsDedicatedBearerEvent2.slotId = SLOT_ID1;
        mImsDedicatedBearerEvent2.ratAtEnd = TelephonyManager.NETWORK_TYPE_NR;
        mImsDedicatedBearerEvent2.qci = 6;
        mImsDedicatedBearerEvent2.bearerState =
                TelephonyStatsLog.IMS_DEDICATED_BEARER_EVENT__BEARER_STATE__STATE_MODIFIED;
        mImsDedicatedBearerEvent2.localConnectionInfoReceived = true;
        mImsDedicatedBearerEvent2.remoteConnectionInfoReceived = true;
        mImsDedicatedBearerEvent2.hasListeners = true;
        mImsDedicatedBearerEvent2.count = 1;

        mImsDedicatedBearerEvents =
                new ImsDedicatedBearerEvent[] {
                    mImsDedicatedBearerEvent1, mImsDedicatedBearerEvent2
                };


        mUceEventStats1 = new UceEventStats();
        mUceEventStats1.carrierId = CARRIER1_ID;
        mUceEventStats1.slotId = SLOT_ID1;
        mUceEventStats1.type = 1;
        mUceEventStats1.successful = true;
        mUceEventStats1.commandCode = 0;
        mUceEventStats1.networkResponse = 200;
        mUceEventStats1.count = 1;

        mUceEventStats2 = new UceEventStats();
        mUceEventStats2.carrierId = CARRIER2_ID;
        mUceEventStats2.slotId = SLOT_ID2;
        mUceEventStats2.type = 2;
        mUceEventStats2.successful = false;
        mUceEventStats2.commandCode = 2;
        mUceEventStats2.networkResponse = 0;
        mUceEventStats2.count = 1;
        mUceEventStatses = new UceEventStats[] {mUceEventStats1, mUceEventStats2};

        mPresenceNotifyEvent1 = new PresenceNotifyEvent();
        mPresenceNotifyEvent1.carrierId = CARRIER1_ID;
        mPresenceNotifyEvent1.slotId = SLOT_ID1;
        mPresenceNotifyEvent1.reason = 1;
        mPresenceNotifyEvent1.contentBodyReceived = true;
        mPresenceNotifyEvent1.rcsCapsCount = 1;
        mPresenceNotifyEvent1.mmtelCapsCount = 1;
        mPresenceNotifyEvent1.noCapsCount = 0;
        mPresenceNotifyEvent1.count = 1;

        mPresenceNotifyEvent2 = new PresenceNotifyEvent();
        mPresenceNotifyEvent2.carrierId = CARRIER2_ID;
        mPresenceNotifyEvent2.slotId = SLOT_ID2;
        mPresenceNotifyEvent2.reason = 1;
        mPresenceNotifyEvent2.contentBodyReceived = false;
        mPresenceNotifyEvent2.rcsCapsCount = 0;
        mPresenceNotifyEvent2.mmtelCapsCount = 0;
        mPresenceNotifyEvent2.noCapsCount = 1;
        mPresenceNotifyEvent2.count = 1;
        mPresenceNotifyEvents = new PresenceNotifyEvent[] {mPresenceNotifyEvent1,
                mPresenceNotifyEvent2};

        //A destroyed SipDelegate
        mSipDelegateStats1 = new SipDelegateStats();
        mSipDelegateStats1.carrierId = CARRIER1_ID;
        mSipDelegateStats1.slotId = SLOT_ID1;
        mSipDelegateStats1.uptimeMillis = 1000L;
        mSipDelegateStats1.destroyReason =
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP;

        //An active SipDelegate
        mSipDelegateStats2 = new SipDelegateStats();
        mSipDelegateStats2.carrierId = CARRIER1_ID;
        mSipDelegateStats2.slotId = SLOT_ID1;
        mSipDelegateStats2.uptimeMillis = 1000L;
        mSipDelegateStats2.destroyReason =
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD;

        //An active SipDelegate
        mSipDelegateStats3 = new SipDelegateStats();
        mSipDelegateStats3.carrierId = CARRIER2_ID;
        mSipDelegateStats3.slotId = SLOT_ID2;
        mSipDelegateStats3.uptimeMillis = 3000L;
        mSipDelegateStats3.destroyReason =
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN;

        //A registered SipTransportFeatureTag
        mSipTransportFeatureTagStats1 = new SipTransportFeatureTagStats();
        mSipTransportFeatureTagStats1.carrierId = CARRIER1_ID;
        mSipTransportFeatureTagStats1.slotId = SLOT_ID1;
        mSipTransportFeatureTagStats1.featureTagName = TelephonyProtoEnums.IMS_FEATURE_TAG_CHAT_IM;
        mSipTransportFeatureTagStats1.sipTransportDeniedReason =  RcsStats.NONE;
        mSipTransportFeatureTagStats1.sipTransportDeregisteredReason = RcsStats.NONE;
        mSipTransportFeatureTagStats1.associatedMillis = 1000L;

        //A denied SipTransportFeatureTag
        mSipTransportFeatureTagStats2 = new SipTransportFeatureTagStats();
        mSipTransportFeatureTagStats2.carrierId = CARRIER1_ID;
        mSipTransportFeatureTagStats2.slotId = SLOT_ID1;
        mSipTransportFeatureTagStats2.featureTagName = 1;
        mSipTransportFeatureTagStats2.sipTransportDeniedReason =
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE;
        mSipTransportFeatureTagStats2.sipTransportDeregisteredReason = RcsStats.NONE;
        mSipTransportFeatureTagStats2.associatedMillis = 1000L;

        mSipDelegateStatsArray = new SipDelegateStats[]{mSipDelegateStats2, mSipDelegateStats3};

        mSipTransportFeatureTagStatsArray = new SipTransportFeatureTagStats[]
                {mSipTransportFeatureTagStats1, mSipTransportFeatureTagStats2};

        mGbaEvent1 = new GbaEvent();
        mGbaEvent1.carrierId = CARRIER1_ID;
        mGbaEvent1.slotId = SLOT_ID1;
        mGbaEvent1.successful = true;
        mGbaEvent1.failedReason = GBA_EVENT__FAILED_REASON__UNKNOWN;
        mGbaEvent1.count = 1;

        mGbaEvent2 = new GbaEvent();
        mGbaEvent2.carrierId = CARRIER2_ID;
        mGbaEvent2.slotId = SLOT_ID2;
        mGbaEvent2.successful = false;
        mGbaEvent2.failedReason = GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY;
        mGbaEvent2.count = 1;

        mGbaEvent = new GbaEvent[] {mGbaEvent1, mGbaEvent2};

        //stats slot 0
        mSipMessageResponse1 = new SipMessageResponse();
        mSipMessageResponse1.carrierId = CARRIER1_ID;
        mSipMessageResponse1.slotId = SLOT_ID1;
        //"INVITE"
        mSipMessageResponse1.sipMessageMethod = 2;
        mSipMessageResponse1.sipMessageResponse = 200;
        mSipMessageResponse1.sipMessageDirection = 1;
        mSipMessageResponse1.messageError = 0;
        mSipMessageResponse1.count = 1;

        //stats slot 1
        mSipMessageResponse2 = new SipMessageResponse();
        mSipMessageResponse2.carrierId = CARRIER2_ID;
        mSipMessageResponse2.slotId = SLOT_ID2;
        //"INVITE"
        mSipMessageResponse2.sipMessageMethod = 2;
        mSipMessageResponse2.sipMessageResponse = 200;
        mSipMessageResponse2.sipMessageDirection = 0;
        mSipMessageResponse2.messageError = 0;
        mSipMessageResponse2.count = 1;

        mSipMessageResponse =
                new SipMessageResponse[] {mSipMessageResponse1, mSipMessageResponse2};

        // stats slot 0
        mSipTransportSession1 = new SipTransportSession();
        mSipTransportSession1.carrierId = CARRIER1_ID;
        mSipTransportSession1.slotId = SLOT_ID1;
        //"INVITE"
        mSipTransportSession1.sessionMethod = 2;
        mSipTransportSession1.sipMessageDirection = 1;
        mSipTransportSession1.sipResponse = 200;
        mSipTransportSession1.sessionCount = 1;
        mSipTransportSession1.endedGracefullyCount = 1;
        mSipTransportSession1.isEndedGracefully = true;

        // stats slot 1
        mSipTransportSession2 = new SipTransportSession();
        mSipTransportSession2.carrierId = CARRIER2_ID;
        mSipTransportSession2.slotId = SLOT_ID2;
        //"INVITE"
        mSipTransportSession2.sessionMethod = 2;
        mSipTransportSession2.sipMessageDirection = 0;
        mSipTransportSession2.sipResponse = 200;
        mSipTransportSession2.sessionCount = 1;
        mSipTransportSession2.endedGracefullyCount = 1;
        mSipTransportSession2.isEndedGracefully = true;

        mSipTransportSession =
                new SipTransportSession[] {mSipTransportSession1, mSipTransportSession2};
    }

    private static class TestablePersistAtomsStorage extends PersistAtomsStorage {
        private long mTimeMillis = START_TIME_MILLIS;

        TestablePersistAtomsStorage(Context context) {
            super(context);
            // Remove delay for saving to persistent storage during tests.
            mSaveImmediately = true;
        }

        @Override
        protected long getWallTimeMillis() {
            // NOTE: super class constructor will be executed before private field is set, which
            // gives the wrong start time (mTimeMillis will have its default value of 0L)
            return mTimeMillis == 0L ? START_TIME_MILLIS : mTimeMillis;
        }

        private void setTimeMillis(long timeMillis) {
            mTimeMillis = timeMillis;
        }

        private void incTimeMillis(long timeMillis) {
            mTimeMillis += timeMillis;
        }

        private PersistAtoms getAtomsProto() {
            // NOTE: unlike other methods in PersistAtomsStorage, this is not synchronized, but
            // should be fine since the test is single-threaded
            return mAtoms;
        }
    }

    private TestablePersistAtomsStorage mPersistAtomsStorage;

    private static final Comparator<MessageNano> sProtoComparator =
            new Comparator<>() {
                @Override
                public int compare(MessageNano o1, MessageNano o2) {
                    if (o1 == o2) {
                        return 0;
                    }
                    if (o1 == null) {
                        return -1;
                    }
                    if (o2 == null) {
                        return 1;
                    }
                    assertEquals(o1.getClass(), o2.getClass());
                    return o1.toString().compareTo(o2.toString());
                }
            };

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTestFileOutputStream = mock(FileOutputStream.class);
        makeTestData();

        // by default, test loading with real file IO and saving with mocks
        mTestFile = mFolder.newFile(TEST_FILE);
        doReturn(mTestFileOutputStream).when(mContext).openFileOutput(anyString(), anyInt());
        doReturn(mTestFile).when(mContext).getFileStreamPath(anyString());
    }

    @After
    public void tearDown() throws Exception {
        mTestFile.delete();
        mTestFile = null;
        mFolder = null;
        mCall1Proto = null;
        mCall2Proto = null;
        mCall3Proto = null;
        mCall4Proto = null;
        mCarrier1LteUsageProto = null;
        mCarrier1UmtsUsageProto = null;
        mCarrier2LteUsageProto = null;
        mCarrier3LteUsageProto = null;
        mCarrier3GsmUsageProto = null;
        mVoiceCallRatUsages = null;
        mServiceSwitch1Proto = null;
        mServiceSwitch2Proto = null;
        mServiceState1Proto = null;
        mServiceState2Proto = null;
        mServiceState3Proto = null;
        mServiceState4Proto = null;
        mServiceState5Proto = null;
        mServiceSwitches = null;
        mServiceStates = null;
        mImsRegistrationStatsLte0 = null;
        mImsRegistrationStatsWifi0 = null;
        mImsRegistrationStatsLte1 = null;
        mImsRegistrationTerminationLte = null;
        mImsRegistrationTerminationWifi = null;
        mImsRegistrationStats = null;
        mImsRegistrationTerminations = null;
        mDataCallSession0 = null;
        mDataCallSession1 = null;
        mImsRegistrationFeatureTagStats1Proto = null;
        mImsRegistrationFeatureTagStats2Proto = null;
        mImsRegistrationFeatureTagStatses = null;
        mRcsClientProvisioningStats1Proto = null;
        mRcsClientProvisioningStats2Proto = null;
        mRcsClientProvisioningStatses = null;
        mRcsAcsProvisioningStats1Proto = null;
        mRcsAcsProvisioningStats2Proto = null;
        mRcsAcsProvisioningStatses = null;
        mImsRegistrationServiceIm = null;
        mImsRegistrationServiceFt = null;
        mImsRegistrationServiceDescStats = null;
        mImsDedicatedBearerListenerEvent1 = null;
        mImsDedicatedBearerListenerEvent2 = null;
        mImsDedicatedBearerListenerEvents = null;
        mImsDedicatedBearerEvent1 = null;
        mImsDedicatedBearerEvent2 = null;
        mImsDedicatedBearerEvents = null;
        mUceEventStats1 = null;
        mUceEventStats2 = null;
        mUceEventStatses = null;
        mPresenceNotifyEvent1 = null;
        mPresenceNotifyEvent2 = null;
        mPresenceNotifyEvents = null;
        mSipTransportFeatureTagStats1 = null;
        mSipTransportFeatureTagStats2 = null;
        mSipTransportFeatureTagStatsArray = null;
        mSipDelegateStats1 = null;
        mSipDelegateStats2 = null;
        mSipDelegateStats3 = null;
        mSipDelegateStatsArray = null;
        mGbaEvent1 = null;
        mGbaEvent2 = null;
        mGbaEvent = null;
        mSipMessageResponse1 = null;
        mSipMessageResponse2 = null;
        mSipMessageResponse = null;
        mSipTransportSession1 = null;
        mSipTransportSession2 = null;
        mSipTransportSession = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void loadAtoms_fileNotExist() throws Exception {
        mTestFile.delete();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be empty, pull time should be start time
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    @SmallTest
    public void loadAtoms_unreadable() throws Exception {
        createEmptyTestFile();
        mTestFile.setReadable(false);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be empty, pull time should be start time
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    @SmallTest
    public void loadAtoms_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be empty, pull time should be start time
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    @SmallTest
    public void loadAtoms_malformedFile() throws Exception {
        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write("This is not a proto file.".getBytes(StandardCharsets.UTF_8));
        stream.close();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be empty, pull time should be start time
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    @SmallTest
    public void loadAtoms_pullTimeMissing() throws Exception {
        // create test file with lastPullTimeMillis = 0L, i.e. default/unknown
        createTestFile(0L);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be match, pull time should be start time
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertProtoArrayEquals(mVoiceCallRatUsages, mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        assertProtoArrayEquals(mVoiceCallSessions, mPersistAtomsStorage.getVoiceCallSessions(0L));
        assertProtoArrayEqualsIgnoringOrder(
                mServiceStates, mPersistAtomsStorage.getCellularServiceStates(0L));
        assertProtoArrayEqualsIgnoringOrder(
                mServiceSwitches, mPersistAtomsStorage.getCellularDataServiceSwitches(0L));
    }

    @Test
    @SmallTest
    public void loadAtoms_validContents() throws Exception {
        createTestFile(/* lastPullTimeMillis= */ 100L);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // no exception should be thrown, storage and pull time should match
        assertAllPullTimestampEquals(100L);
        assertProtoArrayEquals(mVoiceCallRatUsages, mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        assertProtoArrayEquals(mVoiceCallSessions, mPersistAtomsStorage.getVoiceCallSessions(0L));
        assertProtoArrayEqualsIgnoringOrder(
                mServiceStates, mPersistAtomsStorage.getCellularServiceStates(0L));
        assertProtoArrayEqualsIgnoringOrder(
                mServiceSwitches, mPersistAtomsStorage.getCellularDataServiceSwitches(0L));
    }

    @Test
    @SmallTest
    public void addVoiceCallSession_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallSession(mCall1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // call should be added successfully, there should be no RAT usage, changes should be saved
        verifyCurrentStateSavedToFileOnce();
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertProtoArrayEquals(new VoiceCallSession[] {mCall1Proto}, voiceCallSession);
    }

    @Test
    @SmallTest
    public void addVoiceCallSession_withExistingCalls() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallSession(mCall1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // call should be added successfully, RAT usages should not change, changes should be saved
        assertProtoArrayEquals(mVoiceCallRatUsages, mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        VoiceCallSession[] expectedVoiceCallSessions =
                new VoiceCallSession[] {
                    mCall1Proto, mCall1Proto, mCall2Proto, mCall3Proto, mCall4Proto
                };
        // call list is randomized at this point
        verifyCurrentStateSavedToFileOnce();
        assertProtoArrayEqualsIgnoringOrder(
                expectedVoiceCallSessions, mPersistAtomsStorage.getVoiceCallSessions(0L));
    }

    @Test
    @SmallTest
    public void addVoiceCallSession_tooManyCalls() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        addRepeatedCalls(mPersistAtomsStorage, mCall1Proto, 50);
        mPersistAtomsStorage.addVoiceCallSession(mCall2Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // one previous call should be evicted, the new call should be added
        verifyCurrentStateSavedToFileOnce();
        VoiceCallSession[] calls = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertHasCall(calls, mCall1Proto, /* expectedCount= */ 49);
        assertHasCall(calls, mCall2Proto, /* expectedCount= */ 1);
    }

    @Test
    @SmallTest
    public void addVoiceCallSession_tooManyCalls_withEmergencyCalls() throws Exception {
        createEmptyTestFile();
        // We initially have storage full of emergency calls except one.
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        addRepeatedCalls(mPersistAtomsStorage, mCall4Proto, 49);
        mPersistAtomsStorage.addVoiceCallSession(mCall1Proto);

        mPersistAtomsStorage.addVoiceCallSession(mCall4Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // after adding one more emergency call, the previous non-emergency call should be evicted
        verifyCurrentStateSavedToFileOnce();
        VoiceCallSession[] calls = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertHasCall(calls, mCall4Proto, /* expectedCount= */ 50);
    }

    @Test
    @SmallTest
    public void addVoiceCallRatUsage_emptyProto() throws Exception {
        createEmptyTestFile();
        VoiceCallRatTracker ratTracker = VoiceCallRatTracker.fromProto(mVoiceCallRatUsages);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallRatUsage(ratTracker);
        mPersistAtomsStorage.incTimeMillis(100L);

        // RAT should be added successfully, calls should not change, changes should be saved
        verifyCurrentStateSavedToFileOnce();
        assertProtoArrayEqualsIgnoringOrder(
                mVoiceCallRatUsages, mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getVoiceCallSessions(0L));
    }

    @Test
    @SmallTest
    public void addVoiceCallRatUsage_withExistingUsages() throws Exception {
        createTestFile(START_TIME_MILLIS);
        VoiceCallRatTracker ratTracker = VoiceCallRatTracker.fromProto(mVoiceCallRatUsages);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallRatUsage(ratTracker);
        mPersistAtomsStorage.incTimeMillis(100L);

        // RAT should be added successfully, calls should not change, changes should be saved
        // call count and duration should become doubled since mVoiceCallRatUsages applied through
        // both file and addVoiceCallRatUsage()
        verifyCurrentStateSavedToFileOnce();
        VoiceCallRatUsage[] expectedVoiceCallRatUsage =
                multiplyVoiceCallRatUsage(mVoiceCallRatUsages, 2);
        assertProtoArrayEqualsIgnoringOrder(
                expectedVoiceCallRatUsage, mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        assertProtoArrayEquals(mVoiceCallSessions, mPersistAtomsStorage.getVoiceCallSessions(0L));
    }

    @Test
    @SmallTest
    public void addVoiceCallRatUsage_empty() throws Exception {
        createEmptyTestFile();
        VoiceCallRatTracker ratTracker = VoiceCallRatTracker.fromProto(new VoiceCallRatUsage[0]);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallRatUsage(ratTracker);
        mPersistAtomsStorage.incTimeMillis(100L);

        // RAT should be added successfully, calls should not change
        // in this case saving is unnecessarily
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getVoiceCallSessions(0L));
    }

    @Test
    @SmallTest
    public void getVoiceCallRatUsages_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        VoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(100L);

        // should be denied
        assertNull(voiceCallRatUsage);
    }

    @Test
    @SmallTest
    public void getVoiceCallRatUsages_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        VoiceCallRatUsage[] voiceCallRatUsage1 = mPersistAtomsStorage.getVoiceCallRatUsages(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        VoiceCallRatUsage[] voiceCallRatUsage2 = mPersistAtomsStorage.getVoiceCallRatUsages(50L);
        long voiceCallSessionPullTimestampMillis =
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis;
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved, other fields should be unaffected
        assertProtoArrayEquals(mVoiceCallRatUsages, voiceCallRatUsage1);
        assertProtoArrayIsEmpty(voiceCallRatUsage2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().voiceCallRatUsagePullTimestampMillis);
        assertProtoArrayEquals(mVoiceCallSessions, voiceCallSession);
        assertEquals(START_TIME_MILLIS, voiceCallSessionPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).voiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).voiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).voiceCallSessionPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void getVoiceCallSessions_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(100L);

        // should be denied
        assertNull(voiceCallSession);
    }

    @Test
    @SmallTest
    public void getVoiceCallSessions_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        VoiceCallSession[] voiceCallSession1 = mPersistAtomsStorage.getVoiceCallSessions(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        VoiceCallSession[] voiceCallSession2 = mPersistAtomsStorage.getVoiceCallSessions(50L);
        long voiceCallRatUsagePullTimestampMillis =
                mPersistAtomsStorage.getAtomsProto().voiceCallRatUsagePullTimestampMillis;
        VoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved, other fields should be unaffected
        assertProtoArrayEquals(mVoiceCallSessions, voiceCallSession1);
        assertProtoArrayIsEmpty(voiceCallSession2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        assertProtoArrayEquals(mVoiceCallRatUsages, voiceCallRatUsage);
        assertEquals(START_TIME_MILLIS, voiceCallRatUsagePullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).voiceCallSessionPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).voiceCallSessionPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).voiceCallRatUsagePullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addCellularServiceStateAndCellularDataServiceSwitch_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addCellularServiceStateAndCellularDataServiceSwitch(
                mServiceState1Proto, mServiceSwitch1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        CellularServiceState[] serviceStates = mPersistAtomsStorage.getCellularServiceStates(0L);
        CellularDataServiceSwitch[] serviceSwitches =
                mPersistAtomsStorage.getCellularDataServiceSwitches(0L);
        assertProtoArrayEquals(new CellularServiceState[] {mServiceState1Proto}, serviceStates);
        assertProtoArrayEquals(
                new CellularDataServiceSwitch[] {mServiceSwitch1Proto}, serviceSwitches);
    }

    @Test
    @SmallTest
    public void addCellularServiceStateAndCellularDataServiceSwitch_withExistingEntries()
            throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addCellularServiceStateAndCellularDataServiceSwitch(
                mServiceState1Proto, mServiceSwitch1Proto);

        mPersistAtomsStorage.addCellularServiceStateAndCellularDataServiceSwitch(
                mServiceState2Proto, mServiceSwitch2Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        CellularServiceState[] serviceStates = mPersistAtomsStorage.getCellularServiceStates(0L);
        CellularDataServiceSwitch[] serviceSwitches =
                mPersistAtomsStorage.getCellularDataServiceSwitches(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new CellularServiceState[] {mServiceState1Proto, mServiceState2Proto},
                serviceStates);
        assertProtoArrayEqualsIgnoringOrder(
                new CellularDataServiceSwitch[] {mServiceSwitch1Proto, mServiceSwitch2Proto},
                serviceSwitches);
    }

    @Test
    @SmallTest
    public void addCellularServiceStateAndCellularDataServiceSwitch_updateExistingEntries()
            throws Exception {
        createTestFile(START_TIME_MILLIS);
        CellularServiceState newServiceState1Proto = copyOf(mServiceState1Proto);
        CellularDataServiceSwitch newServiceSwitch1Proto = copyOf(mServiceSwitch1Proto);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addCellularServiceStateAndCellularDataServiceSwitch(
                copyOf(mServiceState1Proto), copyOf(mServiceSwitch1Proto));
        mPersistAtomsStorage.incTimeMillis(100L);

        // mServiceState1Proto's duration and mServiceSwitch1Proto's switch should be doubled
        verifyCurrentStateSavedToFileOnce();
        CellularServiceState[] serviceStates = mPersistAtomsStorage.getCellularServiceStates(0L);
        newServiceState1Proto.totalTimeMillis *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new CellularServiceState[] {
                    newServiceState1Proto,
                    mServiceState2Proto,
                    mServiceState3Proto,
                    mServiceState4Proto,
                    mServiceState5Proto
                },
                serviceStates);
        CellularDataServiceSwitch[] serviceSwitches =
                mPersistAtomsStorage.getCellularDataServiceSwitches(0L);
        newServiceSwitch1Proto.switchCount *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new CellularDataServiceSwitch[] {newServiceSwitch1Proto, mServiceSwitch2Proto},
                serviceSwitches);
    }

    @Test
    @SmallTest
    public void addCellularServiceStateAndCellularDataServiceSwitch_tooManyServiceStates()
            throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        Queue<CellularServiceState> expectedServiceStates = new LinkedList<>();
        Queue<CellularDataServiceSwitch> expectedServiceSwitches = new LinkedList<>();

        // Add 51 service states, with the first being least recent
        for (int i = 0; i < 51; i++) {
            CellularServiceState state = new CellularServiceState();
            state.voiceRat = i / 10;
            state.dataRat = i % 10;
            expectedServiceStates.add(state);
            CellularDataServiceSwitch serviceSwitch = new CellularDataServiceSwitch();
            serviceSwitch.ratFrom = i / 10;
            serviceSwitch.ratTo = i % 10;
            expectedServiceSwitches.add(serviceSwitch);
            mPersistAtomsStorage.addCellularServiceStateAndCellularDataServiceSwitch(
                    copyOf(state), copyOf(serviceSwitch));
            mPersistAtomsStorage.incTimeMillis(100L);
        }

        // The least recent (the first) service state should be evicted
        verifyCurrentStateSavedToFileOnce();
        CellularServiceState[] serviceStates = mPersistAtomsStorage.getCellularServiceStates(0L);
        expectedServiceStates.remove();
        assertProtoArrayEqualsIgnoringOrder(
                expectedServiceStates.toArray(new CellularServiceState[0]), serviceStates);
        CellularDataServiceSwitch[] serviceSwitches =
                mPersistAtomsStorage.getCellularDataServiceSwitches(0L);
        expectedServiceSwitches.remove();
        assertProtoArrayEqualsIgnoringOrder(
                expectedServiceSwitches.toArray(new CellularDataServiceSwitch[0]), serviceSwitches);
    }

    @Test
    @SmallTest
    public void getCellularDataServiceSwitches_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        CellularDataServiceSwitch[] serviceSwitches =
                mPersistAtomsStorage.getCellularDataServiceSwitches(100L);

        // should be denied
        assertNull(serviceSwitches);
    }

    @Test
    @SmallTest
    public void getCellularDataServiceSwitches_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        CellularDataServiceSwitch[] serviceSwitches1 =
                mPersistAtomsStorage.getCellularDataServiceSwitches(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        CellularDataServiceSwitch[] serviceSwitches2 =
                mPersistAtomsStorage.getCellularDataServiceSwitches(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new CellularDataServiceSwitch[] {mServiceSwitch1Proto, mServiceSwitch2Proto},
                serviceSwitches1);
        assertProtoArrayEquals(new CellularDataServiceSwitch[0], serviceSwitches2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().cellularDataServiceSwitchPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).cellularDataServiceSwitchPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).cellularDataServiceSwitchPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void getCellularServiceStates_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        CellularServiceState[] serviceStates = mPersistAtomsStorage.getCellularServiceStates(100L);

        // should be denied
        assertNull(serviceStates);
    }

    @Test
    @SmallTest
    public void getCellularServiceStates_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        CellularServiceState[] serviceStates1 = mPersistAtomsStorage.getCellularServiceStates(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        CellularServiceState[] serviceStates2 = mPersistAtomsStorage.getCellularServiceStates(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new CellularServiceState[] {
                    mServiceState1Proto,
                    mServiceState2Proto,
                    mServiceState3Proto,
                    mServiceState4Proto,
                    mServiceState5Proto
                },
                serviceStates1);
        assertProtoArrayEquals(new CellularServiceState[0], serviceStates2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().cellularServiceStatePullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).cellularServiceStatePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).cellularServiceStatePullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addImsRegistrationStats_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationStats(copyOf(mImsRegistrationStatsLte0));
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationStats[] regStats = mPersistAtomsStorage.getImsRegistrationStats(0L);
        assertProtoArrayEquals(new ImsRegistrationStats[] {mImsRegistrationStatsLte0}, regStats);
    }

    @Test
    @SmallTest
    public void addImsRegistrationStats_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationStats(copyOf(mImsRegistrationStatsLte0));

        mPersistAtomsStorage.addImsRegistrationStats(copyOf(mImsRegistrationStatsWifi0));
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationStats[] regStats = mPersistAtomsStorage.getImsRegistrationStats(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationStats[] {mImsRegistrationStatsLte0, mImsRegistrationStatsWifi0},
                regStats);
    }

    @Test
    @SmallTest
    public void addImsRegistrationStats_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);
        ImsRegistrationStats newImsRegistrationStatsLte0 = copyOf(mImsRegistrationStatsLte0);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addImsRegistrationStats(copyOf(mImsRegistrationStatsLte0));
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS);

        // mImsRegistrationStatsLte0's durations should be doubled
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationStats[] serviceStates = mPersistAtomsStorage.getImsRegistrationStats(0L);
        newImsRegistrationStatsLte0.registeredMillis *= 2;
        newImsRegistrationStatsLte0.voiceCapableMillis *= 2;
        newImsRegistrationStatsLte0.voiceAvailableMillis *= 2;
        newImsRegistrationStatsLte0.smsCapableMillis *= 2;
        newImsRegistrationStatsLte0.smsAvailableMillis *= 2;
        newImsRegistrationStatsLte0.videoCapableMillis *= 2;
        newImsRegistrationStatsLte0.videoAvailableMillis *= 2;
        newImsRegistrationStatsLte0.utCapableMillis *= 2;
        newImsRegistrationStatsLte0.utAvailableMillis *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationStats[] {
                    newImsRegistrationStatsLte0,
                    mImsRegistrationStatsWifi0,
                    mImsRegistrationStatsLte1
                },
                serviceStates);
    }

    @Test
    @SmallTest
    public void addImsRegistrationStats_tooManyRegistrationStats() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        Queue<ImsRegistrationStats> expectedRegistrationStats = new LinkedList<>();

        // Add 11 registration stats
        for (int i = 0; i < 11; i++) {
            ImsRegistrationStats stats = copyOf(mImsRegistrationStatsLte0);
            stats.rat = i;
            expectedRegistrationStats.add(stats);
            mPersistAtomsStorage.addImsRegistrationStats(stats);
            mPersistAtomsStorage.incTimeMillis(100L);
        }

        // The least recent (the first) registration stats should be evicted
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationStats[] stats = mPersistAtomsStorage.getImsRegistrationStats(0L);
        expectedRegistrationStats.remove();
        assertProtoArrayEqualsIgnoringOrder(
                expectedRegistrationStats.toArray(new ImsRegistrationStats[0]), stats);
    }

    @Test
    @SmallTest
    public void addImsRegistrationTermination_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationTermination(mImsRegistrationTerminationLte);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationTermination[] terminations =
                mPersistAtomsStorage.getImsRegistrationTerminations(0L);
        assertProtoArrayEquals(
                new ImsRegistrationTermination[] {mImsRegistrationTerminationLte}, terminations);
    }

    @Test
    @SmallTest
    public void addImsRegistrationTermination_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationTermination(mImsRegistrationTerminationLte);

        mPersistAtomsStorage.addImsRegistrationTermination(mImsRegistrationTerminationWifi);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationTermination[] terminations =
                mPersistAtomsStorage.getImsRegistrationTerminations(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationTermination[] {
                    mImsRegistrationTerminationLte, mImsRegistrationTerminationWifi
                },
                terminations);
    }

    @Test
    @SmallTest
    public void addImsRegistrationTermination_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);
        ImsRegistrationTermination newTermination = copyOf(mImsRegistrationTerminationWifi);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addImsRegistrationTermination(copyOf(mImsRegistrationTerminationWifi));
        mPersistAtomsStorage.incTimeMillis(100L);

        // mImsRegistrationTerminationWifi's count should be doubled
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationTermination[] terminations =
                mPersistAtomsStorage.getImsRegistrationTerminations(0L);
        newTermination.count *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationTermination[] {mImsRegistrationTerminationLte, newTermination},
                terminations);
    }

    @Test
    @SmallTest
    public void addImsRegistrationTermination_tooManyTerminations() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        Queue<ImsRegistrationTermination> expectedTerminations = new LinkedList<>();

        // Add 11 registration terminations
        for (int i = 0; i < 11; i++) {
            ImsRegistrationTermination termination = copyOf(mImsRegistrationTerminationLte);
            termination.reasonCode = i;
            expectedTerminations.add(termination);
            mPersistAtomsStorage.addImsRegistrationTermination(termination);
            mPersistAtomsStorage.incTimeMillis(100L);
        }

        // The least recent (the first) registration termination should be evicted
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationTermination[] terminations =
                mPersistAtomsStorage.getImsRegistrationTerminations(0L);
        expectedTerminations.remove();
        assertProtoArrayEqualsIgnoringOrder(
                expectedTerminations.toArray(new ImsRegistrationTermination[0]), terminations);
    }

    @Test
    @SmallTest
    public void getImsRegistrationStats_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        ImsRegistrationStats[] stats = mPersistAtomsStorage.getImsRegistrationStats(100L);

        // should be denied
        assertNull(stats);
    }

    @Test
    @SmallTest
    public void getImsRegistrationStats_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS);
        ImsRegistrationStats[] stats1 = mPersistAtomsStorage.getImsRegistrationStats(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationStats[] stats2 = mPersistAtomsStorage.getImsRegistrationStats(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationStats[] {
                    mImsRegistrationStatsLte0, mImsRegistrationStatsWifi0, mImsRegistrationStatsLte1
                },
                stats1);
        assertProtoArrayEquals(new ImsRegistrationStats[0], stats2);
        assertEquals(
                START_TIME_MILLIS + DAY_IN_MILLIS + 100L,
                mPersistAtomsStorage.getAtomsProto().imsRegistrationStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + DAY_IN_MILLIS,
                getAtomsWritten(inOrder).imsRegistrationStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + DAY_IN_MILLIS + 100L,
                getAtomsWritten(inOrder).imsRegistrationStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void getImsRegistrationTerminations_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        ImsRegistrationTermination[] terminations =
                mPersistAtomsStorage.getImsRegistrationTerminations(100L);

        // should be denied
        assertNull(terminations);
    }

    @Test
    @SmallTest
    public void getImsRegistrationTerminations_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationTermination[] terminations1 =
                mPersistAtomsStorage.getImsRegistrationTerminations(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationTermination[] terminations2 =
                mPersistAtomsStorage.getImsRegistrationTerminations(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationTermination[] {
                    mImsRegistrationTerminationLte, mImsRegistrationTerminationWifi
                },
                terminations1);
        assertProtoArrayEquals(new ImsRegistrationTermination[0], terminations2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().imsRegistrationTerminationPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).imsRegistrationTerminationPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).imsRegistrationTerminationPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addDataCallSession_newEntry()
            throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addDataCallSession(mDataCallSession0);
        mPersistAtomsStorage.addDataCallSession(mDataCallSession1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // there should be 2 data calls
        verifyCurrentStateSavedToFileOnce();
        DataCallSession[] dataCalls = mPersistAtomsStorage.getDataCallSessions(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new DataCallSession[]{mDataCallSession0, mDataCallSession1},
                dataCalls);
        for (DataCallSession dataCallSession : dataCalls) {
            if (dataCallSession.dimension == mDataCallSession0.dimension) {
                assertArrayEquals(new int[]{1, 2, 3}, dataCallSession.handoverFailureCauses);
                assertArrayEquals(new int[]{6, 5, 5}, dataCallSession.handoverFailureRat);
            }
        }
    }

    @Test
    @SmallTest
    public void addImsRegistrationFeatureTagStats_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage
                .addImsRegistrationFeatureTagStats(mImsRegistrationFeatureTagStats1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();

        ImsRegistrationFeatureTagStats[] expected =
                new ImsRegistrationFeatureTagStats[] {
                        mImsRegistrationFeatureTagStats1Proto
                };
        assertProtoArrayEquals(
                expected, mPersistAtomsStorage.getImsRegistrationFeatureTagStats(0L));
    }

    @Test
    @SmallTest
    public void addDataCallSession_existingEntry()
            throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        DataCallSession newDataCallSession0 = copyOf(mDataCallSession0);
        newDataCallSession0.ongoing = false;
        newDataCallSession0.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        newDataCallSession0.durationMinutes = 10;
        newDataCallSession0.ratSwitchCount = 5;
        newDataCallSession0.handoverFailureCauses = new int[]{4};
        newDataCallSession0.handoverFailureRat = new int[]{4};
        DataCallSession totalDataCallSession0 = copyOf(newDataCallSession0);
        totalDataCallSession0.durationMinutes =
                mDataCallSession0.durationMinutes + newDataCallSession0.durationMinutes;
        totalDataCallSession0.ratSwitchCount =
                mDataCallSession0.ratSwitchCount + newDataCallSession0.ratSwitchCount;
        totalDataCallSession0.handoverFailureCauses = new int[]{1, 2, 3, 4};
        totalDataCallSession0.handoverFailureRat = new int[]{6, 5, 5, 4};

        mPersistAtomsStorage.addDataCallSession(mDataCallSession0);
        mPersistAtomsStorage.addDataCallSession(newDataCallSession0);
        mPersistAtomsStorage.incTimeMillis(100L);

        // there should be 1 data call
        verifyCurrentStateSavedToFileOnce();
        DataCallSession[] dataCalls = mPersistAtomsStorage.getDataCallSessions(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new DataCallSession[]{totalDataCallSession0}, dataCalls);
    }

    @Test
    @SmallTest
    public void addImsRegistrationFeatureTagStats_withExistingEntries() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage
                .addImsRegistrationFeatureTagStats(mImsRegistrationFeatureTagStats1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);
        mPersistAtomsStorage
                .addImsRegistrationFeatureTagStats(mImsRegistrationFeatureTagStats2Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();

        ImsRegistrationFeatureTagStats[] expected =
                new ImsRegistrationFeatureTagStats[] {
                        mImsRegistrationFeatureTagStats1Proto,
                        mImsRegistrationFeatureTagStats2Proto
                };
        // 2 atoms stored on initially and when try to add 2 same atoms, should be increased.
        assertProtoArrayEqualsIgnoringOrder(
                expected, mPersistAtomsStorage.getImsRegistrationFeatureTagStats(0L));
    }

    @Test
    @SmallTest
    public void addImsRegistrationFeatureTagStats_tooManyEntries() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        int maxCount = 10;
        for (int i = 0; i < maxCount; i++) {
            mPersistAtomsStorage
                    .addImsRegistrationFeatureTagStats(
                            copyOf(mImsRegistrationFeatureTagStats1Proto));
            mPersistAtomsStorage.incTimeMillis(100L);
        }

        mPersistAtomsStorage
                .addImsRegistrationFeatureTagStats(copyOf(mImsRegistrationFeatureTagStats2Proto));

        verifyCurrentStateSavedToFileOnce();

        ImsRegistrationFeatureTagStats[] result =
                mPersistAtomsStorage.getImsRegistrationFeatureTagStats(0L);

        // tried store 26 statses, but only 2 statses stored
        // total time 3600L * maxCount
        assertHasStatsCountTime(result, mImsRegistrationFeatureTagStats1Proto, 1,
                maxCount * 3600L);
        // total time 3600L * 1
        assertHasStatsCountTime(result, mImsRegistrationFeatureTagStats2Proto, 1,
                1 * 3600L);
    }

    @Test
    @SmallTest
    public void getImsRegistrationFeatureTagStats_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        ImsRegistrationFeatureTagStats[] result =
                mPersistAtomsStorage.getImsRegistrationFeatureTagStats(100L);

        // should be denied
        assertNull(result);
    }

    @Test
    @SmallTest
    public void getImsRegistrationFeatureTagStats_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationFeatureTagStats[] statses1 =
                mPersistAtomsStorage.getImsRegistrationFeatureTagStats(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationFeatureTagStats[] statses2 =
                mPersistAtomsStorage.getImsRegistrationFeatureTagStats(50L);

        // first results of get should have two atoms, second should be empty
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(mImsRegistrationFeatureTagStatses, statses1);
        assertProtoArrayEquals(new ImsRegistrationFeatureTagStats[0], statses2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto()
                        .imsRegistrationFeatureTagStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).imsRegistrationFeatureTagStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).imsRegistrationFeatureTagStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addRcsClientProvisioningStats_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage
                .addRcsClientProvisioningStats(mRcsClientProvisioningStats1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);
        mPersistAtomsStorage
                .addRcsClientProvisioningStats(mRcsClientProvisioningStats2Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();

        RcsClientProvisioningStats[] expected =
                new RcsClientProvisioningStats[] {
                        mRcsClientProvisioningStats1Proto,
                        mRcsClientProvisioningStats2Proto
                };

        assertProtoArrayEqualsIgnoringOrder(
                expected, mPersistAtomsStorage.getRcsClientProvisioningStats(0L));
    }

    @Test
    @SmallTest
    public void addRcsClientProvisioningStats_tooManyEntries() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // store 11 same atoms, but only 1 atoms stored with count 11
        for (int i = 0; i < 11; i++) {
            mPersistAtomsStorage
                    .addRcsClientProvisioningStats(mRcsClientProvisioningStats1Proto);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        // store 1 different atom and count 1
        mPersistAtomsStorage
                .addRcsClientProvisioningStats(mRcsClientProvisioningStats2Proto);

        verifyCurrentStateSavedToFileOnce();

        RcsClientProvisioningStats[] result =
                mPersistAtomsStorage.getRcsClientProvisioningStats(0L);

        // first atom has count 11, the other has 1
        assertHasStatsAndCount(result, mRcsClientProvisioningStats1Proto, 11);
        assertHasStatsAndCount(result, mRcsClientProvisioningStats2Proto, 1);
    }

    @Test
    @SmallTest
    public void getRcsClientProvisioningStats_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        RcsClientProvisioningStats[] result =
                mPersistAtomsStorage.getRcsClientProvisioningStats(100L);

        // should be denied
        assertNull(result);
    }

    @Test
    @SmallTest
    public void getRcsClientProvisioningStats_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        RcsClientProvisioningStats[] statses1 =
                mPersistAtomsStorage.getRcsClientProvisioningStats(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        RcsClientProvisioningStats[] statses2 =
                mPersistAtomsStorage.getRcsClientProvisioningStats(50L);

        // first results of get should have two atoms, second should be empty
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(mRcsClientProvisioningStatses, statses1);
        assertProtoArrayEquals(new RcsClientProvisioningStats[0], statses2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto()
                        .rcsClientProvisioningStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).rcsClientProvisioningStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).rcsClientProvisioningStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addRcsAcsProvisioningStats_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage
                .addRcsAcsProvisioningStats(mRcsAcsProvisioningStats1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);
        mPersistAtomsStorage
                .addRcsAcsProvisioningStats(mRcsAcsProvisioningStats2Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();

        RcsAcsProvisioningStats[] expected =
                new RcsAcsProvisioningStats[] {
                        mRcsAcsProvisioningStats1Proto,
                        mRcsAcsProvisioningStats2Proto
                };

        assertProtoArrayEqualsIgnoringOrder(
                expected, mPersistAtomsStorage.getRcsAcsProvisioningStats(0L));
    }

    @Test
    @SmallTest
    public void addRcsAcsProvisioningStats_updateExistingEntries() throws Exception {
        final int maxCount = 5;
        final long duration = START_TIME_MILLIS;
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // store 5 same atoms (1Proto), but only 1 atoms stored with count 5, total time 2000L * 5
        // store 5 same atoms (2Proto), but only 1 atoms stored with count 5, total time 2000L * 5

        for (int i = 0; i < maxCount; i++) {
            mPersistAtomsStorage
                    .addRcsAcsProvisioningStats(copyOf(mRcsAcsProvisioningStats1Proto));
            mPersistAtomsStorage.incTimeMillis(100L);
            mPersistAtomsStorage
                    .addRcsAcsProvisioningStats(copyOf(mRcsAcsProvisioningStats2Proto));
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        // add one more atoms (2Proto), count 6, total time 2000L * 6
        mPersistAtomsStorage
                .addRcsAcsProvisioningStats(copyOf(mRcsAcsProvisioningStats2Proto));
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();

        RcsAcsProvisioningStats[] result =
                mPersistAtomsStorage.getRcsAcsProvisioningStats(0L);

        // atom (1Proto) : count = 5, time = 2000L * 5
        assertHasStatsAndCountDuration(
                result, mRcsAcsProvisioningStats1Proto, 5, duration * maxCount);
        // atom (2Proto) : count = 6, time = 2000L * 6
        assertHasStatsAndCountDuration(
                result, mRcsAcsProvisioningStats2Proto, 6, duration * (maxCount + 1));
    }

    @Test
    @SmallTest
    public void getRcsAcsProvisioningStats_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        RcsAcsProvisioningStats[] result =
                mPersistAtomsStorage.getRcsAcsProvisioningStats(100L);

        // should be denied
        assertNull(result);
    }

    @Test
    @SmallTest
    public void getRcsAcstProvisioningStats_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS);
        RcsAcsProvisioningStats[] statses1 =
                mPersistAtomsStorage.getRcsAcsProvisioningStats(DAY_IN_MILLIS - HOUR_IN_MILLIS);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS);
        RcsAcsProvisioningStats[] statses2 =
                mPersistAtomsStorage.getRcsAcsProvisioningStats(DAY_IN_MILLIS - HOUR_IN_MILLIS);

        // first results of get should have two atoms, second should be empty
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(mRcsAcsProvisioningStatses, statses1);
        assertProtoArrayEquals(new RcsAcsProvisioningStats[0], statses2);
        assertEquals(
                START_TIME_MILLIS + 2 * DAY_IN_MILLIS,
                mPersistAtomsStorage.getAtomsProto()
                        .rcsAcsProvisioningStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + DAY_IN_MILLIS,
                getAtomsWritten(inOrder).rcsAcsProvisioningStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 2 * DAY_IN_MILLIS,
                getAtomsWritten(inOrder).rcsAcsProvisioningStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addImsRegistrationServiceDescStats_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationServiceDescStats(mImsRegistrationServiceIm);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationServiceDescStats[] outputs =
            mPersistAtomsStorage.getImsRegistrationServiceDescStats(0L);
        assertProtoArrayEquals(
                new ImsRegistrationServiceDescStats[] {mImsRegistrationServiceIm}, outputs);
    }

    @Test
    @SmallTest
    public void addImsRegistrationServiceDescStats_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationServiceDescStats(mImsRegistrationServiceIm);

        mPersistAtomsStorage.addImsRegistrationServiceDescStats(mImsRegistrationServiceFt);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationServiceDescStats[] output =
            mPersistAtomsStorage.getImsRegistrationServiceDescStats(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationServiceDescStats[] {
                    mImsRegistrationServiceIm,
                    mImsRegistrationServiceFt
                },
                output);
    }

    @Test
    @SmallTest
    public void addImsRegistrationServiceDescStats_tooManyServiceDesc() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        Queue<ImsRegistrationServiceDescStats> expectedOutput = new LinkedList<>();
        // Add 101 registration terminations
        for (int i = 0; i < 26 + 1; i++) {
            ImsRegistrationServiceDescStats stats = copyOf(mImsRegistrationServiceIm);
            stats.registrationTech = i;
            expectedOutput.add(stats);
            mPersistAtomsStorage.addImsRegistrationServiceDescStats(stats);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        mPersistAtomsStorage.incTimeMillis(100L);

        // The least recent (the first) registration termination should be evicted
        verifyCurrentStateSavedToFileOnce();
        ImsRegistrationServiceDescStats[] output =
            mPersistAtomsStorage.getImsRegistrationServiceDescStats(0L);
        expectedOutput.remove();
        assertEquals(expectedOutput.size() - 1, output.length);
    }

    @Test
    @SmallTest
    public void getImsRegistrationServiceDescStats_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        ImsRegistrationServiceDescStats[] output =
            mPersistAtomsStorage.getImsRegistrationServiceDescStats(100L);

        // should be denied
        assertNull(output);
    }

    @Test
    @SmallTest
    public void getImsRegistrationServiceDescStats_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationServiceDescStats[] output1 =
            mPersistAtomsStorage.getImsRegistrationServiceDescStats(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        ImsRegistrationServiceDescStats[] output2 =
            mPersistAtomsStorage.getImsRegistrationServiceDescStats(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationServiceDescStats[] {
                    mImsRegistrationServiceIm,
                    mImsRegistrationServiceFt
                },
                output1);
        assertProtoArrayEquals(new ImsRegistrationServiceDescStats[0], output2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto()
                    .imsRegistrationServiceDescStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).imsRegistrationServiceDescStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).imsRegistrationServiceDescStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void getImsRegistrationStats_24hNormalization() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsRegistrationStats(copyOf(mImsRegistrationStatsWifi0));
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS / 2);

        ImsRegistrationStats[] serviceStates = mPersistAtomsStorage.getImsRegistrationStats(0L);
        mImsRegistrationStatsWifi0.registeredMillis *= 2;
        mImsRegistrationStatsWifi0.voiceCapableMillis *= 2;
        mImsRegistrationStatsWifi0.voiceAvailableMillis *= 2;
        mImsRegistrationStatsWifi0.smsCapableMillis *= 2;
        mImsRegistrationStatsWifi0.smsAvailableMillis *= 2;
        mImsRegistrationStatsWifi0.videoCapableMillis *= 2;
        mImsRegistrationStatsWifi0.videoAvailableMillis *= 2;
        mImsRegistrationStatsWifi0.utCapableMillis *= 2;
        mImsRegistrationStatsWifi0.utAvailableMillis *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new ImsRegistrationStats[] {
                    mImsRegistrationStatsWifi0
                },
                serviceStates);
    }

    @Test
    public void getRcsAcsProvisioningStats_24h_normalization() throws Exception {
        // in case pulling interval is greater than a day
        final long stateTimer = HOUR_IN_MILLIS;
        final long weightFactor = 2;
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        RcsAcsProvisioningStats mSubjectStats = copyOf(mRcsAcsProvisioningStats1Proto);

        mSubjectStats.stateTimerMillis = stateTimer;
        mPersistAtomsStorage.addRcsAcsProvisioningStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS * weightFactor);

        RcsAcsProvisioningStats[] savedStats =
                mPersistAtomsStorage.getRcsAcsProvisioningStats(0L);

        assertEquals(
                (START_TIME_MILLIS + stateTimer) / weightFactor, savedStats[0].stateTimerMillis);

        // in case pulling interval is smaller than a day
        long incTimeMillis = DAY_IN_MILLIS * 23 / 24 + 1;
        mSubjectStats = copyOf(mRcsAcsProvisioningStats1Proto);
        mSubjectStats.stateTimerMillis = stateTimer;
        mPersistAtomsStorage.addRcsAcsProvisioningStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(incTimeMillis);
        savedStats =
                mPersistAtomsStorage.getRcsAcsProvisioningStats(0L);


        assertEquals(stateTimer, savedStats[0].stateTimerMillis);
    }

    @Test
    public void getSipDelegateStats_24h_normalization() throws Exception {
        final long stateTimer = HOUR_IN_MILLIS;
        final long weightFactor = 2;
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        SipDelegateStats mSubjectStats = copyOf(mSipDelegateStats1);
        mSubjectStats.uptimeMillis = stateTimer;
        mPersistAtomsStorage.addSipDelegateStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS * weightFactor);
        SipDelegateStats[] savedStats =
                mPersistAtomsStorage.getSipDelegateStats(0L);
        for (SipDelegateStats stat : savedStats) {
            if (stat.destroyReason
                    == SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP) {
                assertEquals(stateTimer / weightFactor, stat.uptimeMillis);
            }
        }

        long incTimeMillis = DAY_IN_MILLIS * 23 / 24 + 1;
        mSubjectStats = copyOf(mSipDelegateStats1);
        mSubjectStats.uptimeMillis = stateTimer;
        mPersistAtomsStorage.addSipDelegateStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(incTimeMillis);
        savedStats =
                mPersistAtomsStorage.getSipDelegateStats(0L);
        for (SipDelegateStats stat : savedStats) {
            if (stat.destroyReason
                    == SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP) {
                assertEquals(stateTimer, stat.uptimeMillis);
            }
        }
    }

    @Test
    public void getSipTransportFeatureTagStats_24h_normalization() throws Exception {
        final long stateTimer = HOUR_IN_MILLIS;
        final long weightFactor = 2;
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        SipTransportFeatureTagStats mSubjectStats = copyOf(mSipTransportFeatureTagStats1);
        mSubjectStats.associatedMillis = stateTimer;
        mPersistAtomsStorage.addSipTransportFeatureTagStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS * weightFactor);
        SipTransportFeatureTagStats[] savedStats =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(0L);
        assertEquals((stateTimer) / weightFactor, savedStats[0].associatedMillis);

        long incTimeMillis = DAY_IN_MILLIS * 23 / 24 + 1;
        mSubjectStats = copyOf(mSipTransportFeatureTagStats1);
        mSubjectStats.associatedMillis = stateTimer;
        mPersistAtomsStorage.addSipTransportFeatureTagStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(incTimeMillis);
        savedStats =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(0L);
        assertEquals(stateTimer, savedStats[0].associatedMillis);
    }

    @Test
    public void getImsRegistrationServiceDescStats_24h_normalization() throws Exception {
        final long stateTimer = HOUR_IN_MILLIS;
        final long weightFactor = 2;
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        ImsRegistrationServiceDescStats mSubjectStats = copyOf(mImsRegistrationServiceIm);
        mSubjectStats.publishedMillis = stateTimer;
        mPersistAtomsStorage.addImsRegistrationServiceDescStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(DAY_IN_MILLIS * weightFactor);
        ImsRegistrationServiceDescStats[] savedStats =
                mPersistAtomsStorage.getImsRegistrationServiceDescStats(0L);
        assertEquals(
                (START_TIME_MILLIS + stateTimer) / weightFactor, savedStats[0].publishedMillis);

        long incTimeMillis = DAY_IN_MILLIS * 23 / 24 + 1;
        mSubjectStats = copyOf(mImsRegistrationServiceIm);
        mSubjectStats.publishedMillis = stateTimer;
        mPersistAtomsStorage.addImsRegistrationServiceDescStats(mSubjectStats);
        mPersistAtomsStorage.incTimeMillis(incTimeMillis);
        savedStats =
                mPersistAtomsStorage.getImsRegistrationServiceDescStats(0L);
        assertEquals(stateTimer, savedStats[0].publishedMillis);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerListenerEvent_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsDedicatedBearerListenerEvent(mImsDedicatedBearerListenerEvent1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerListenerEvent[] outputs =
                mPersistAtomsStorage.getImsDedicatedBearerListenerEvent(0L);
        assertProtoArrayEquals(
                new ImsDedicatedBearerListenerEvent[] {mImsDedicatedBearerListenerEvent1}, outputs);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerListenerEvent_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsDedicatedBearerListenerEvent(mImsDedicatedBearerListenerEvent1);

        mPersistAtomsStorage.addImsDedicatedBearerListenerEvent(mImsDedicatedBearerListenerEvent2);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerListenerEvent[] output =
                mPersistAtomsStorage.getImsDedicatedBearerListenerEvent(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new ImsDedicatedBearerListenerEvent[] {
                    mImsDedicatedBearerListenerEvent1, mImsDedicatedBearerListenerEvent2}, output);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerListenerEvent_withSameProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addImsDedicatedBearerListenerEvent(mImsDedicatedBearerListenerEvent1);
        mPersistAtomsStorage.addImsDedicatedBearerListenerEvent(mImsDedicatedBearerListenerEvent1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // The least recent (the first) registration termination should be evicted
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerListenerEvent[] output =
                mPersistAtomsStorage.getImsDedicatedBearerListenerEvent(0L);
        assertEquals(mImsDedicatedBearerListenerEvent1.carrierId, output[0].carrierId);
        assertEquals(mImsDedicatedBearerListenerEvent1.slotId, output[0].slotId);
        assertEquals(mImsDedicatedBearerListenerEvent1.ratAtEnd, output[0].ratAtEnd);
        assertEquals(mImsDedicatedBearerListenerEvent1.qci, output[0].qci);
        assertEquals(mImsDedicatedBearerListenerEvent1.dedicatedBearerEstablished,
                output[0].dedicatedBearerEstablished);
        assertEquals(mImsDedicatedBearerListenerEvent1.eventCount,
                output[0].eventCount);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerEvent_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerEvent[] outputs =
            mPersistAtomsStorage.getImsDedicatedBearerEvent(0L);
        assertProtoArrayEquals(
                new ImsDedicatedBearerEvent[] {mImsDedicatedBearerEvent1}, outputs);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerEvent_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent1);

        mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent2);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerEvent[] output =
            mPersistAtomsStorage.getImsDedicatedBearerEvent(0L);
        assertProtoArrayEqualsIgnoringOrder(
                    new ImsDedicatedBearerEvent[] {
                        mImsDedicatedBearerEvent1, mImsDedicatedBearerEvent2}, output);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerEvent_withSameProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent1);
        mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // The least recent (the first) registration termination should be evicted
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerEvent[] output =
            mPersistAtomsStorage.getImsDedicatedBearerEvent(0L);
        assertEquals(mImsDedicatedBearerEvent1.carrierId, output[0].carrierId);
        assertEquals(mImsDedicatedBearerEvent1.slotId, output[0].slotId);
        assertEquals(mImsDedicatedBearerEvent1.ratAtEnd, output[0].ratAtEnd);
        assertEquals(mImsDedicatedBearerEvent1.qci, output[0].qci);
        assertEquals(mImsDedicatedBearerEvent1.localConnectionInfoReceived,
                output[0].localConnectionInfoReceived);
        assertEquals(mImsDedicatedBearerEvent1.remoteConnectionInfoReceived,
                output[0].remoteConnectionInfoReceived);
        assertEquals(mImsDedicatedBearerEvent1.hasListeners,
                output[0].hasListeners);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerEvent_tooManyEntries() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // Add 11 stats, but max is 10
        for (int i = 0; i < 11; i++) {
            mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent1);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        mPersistAtomsStorage.addImsDedicatedBearerEvent(mImsDedicatedBearerEvent2);

        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerEvent[] stats =
            mPersistAtomsStorage.getImsDedicatedBearerEvent(0L);
        assertHasStatsAndCount(stats, mImsDedicatedBearerEvent1, 11);
        assertHasStatsAndCount(stats, mImsDedicatedBearerEvent2, 1);
    }

    @Test
    @SmallTest
    public void addImsDedicatedBearerEvent_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);
        ImsDedicatedBearerEvent newStats = copyOf(mImsDedicatedBearerEvent1);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addImsDedicatedBearerEvent(copyOf(mImsDedicatedBearerEvent1));
        mPersistAtomsStorage.incTimeMillis(100L);

        // mImsDedicatedBearerEvent1's count should be doubled
        verifyCurrentStateSavedToFileOnce();
        ImsDedicatedBearerEvent[] stats =
            mPersistAtomsStorage.getImsDedicatedBearerEvent(0L);
        newStats.count *= 2;
        assertProtoArrayEqualsIgnoringOrder(new ImsDedicatedBearerEvent[] {
                mImsDedicatedBearerEvent2, newStats}, stats);
    }


    @Test
    @SmallTest
    public void addUceEventStats_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addUceEventStats(mUceEventStats1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        UceEventStats[] outputs = mPersistAtomsStorage.getUceEventStats(0L);
        assertProtoArrayEquals(
                new UceEventStats[] {mUceEventStats1}, outputs);
    }

    @Test
    @SmallTest
    public void addUceEventStats_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addUceEventStats(mUceEventStats1);

        mPersistAtomsStorage.addUceEventStats(mUceEventStats2);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        UceEventStats[] output = mPersistAtomsStorage.getUceEventStats(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new UceEventStats[] {mUceEventStats1, mUceEventStats2}, output);
    }

    @Test
    @SmallTest
    public void addUceEventStats_withSameProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addUceEventStats(mUceEventStats1);
        mPersistAtomsStorage.addUceEventStats(mUceEventStats1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // The least recent (the first) registration termination should be evicted
        verifyCurrentStateSavedToFileOnce();
        UceEventStats[] output = mPersistAtomsStorage.getUceEventStats(0L);
        assertEquals(mUceEventStats1.carrierId, output[0].carrierId);
        assertEquals(mUceEventStats1.slotId, output[0].slotId);
        assertEquals(mUceEventStats1.type, output[0].type);
        assertEquals(mUceEventStats1.successful, output[0].successful);
        assertEquals(mUceEventStats1.commandCode, output[0].commandCode);
        assertEquals(mUceEventStats1.networkResponse, output[0].networkResponse);
        assertEquals(2, output[0].count);
    }

    @Test
    @SmallTest
    public void addPresenceNotifyEvent_withSameProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        PresenceNotifyEvent event1 = new PresenceNotifyEvent();
        event1.carrierId = CARRIER1_ID;
        event1.slotId = SLOT_ID1;
        event1.reason = 1;
        event1.contentBodyReceived = true;
        event1.rcsCapsCount = 1;
        event1.mmtelCapsCount = 1;
        event1.noCapsCount = 0;
        event1.count = 1;

        PresenceNotifyEvent event2 = copyOf(event1);
        event2.rcsCapsCount = 0;

        mPersistAtomsStorage.addPresenceNotifyEvent(event1);
        mPersistAtomsStorage.addPresenceNotifyEvent(event2);

        mPersistAtomsStorage.incTimeMillis(100L);

        // The least recent (the first) registration termination should be evicted
        verifyCurrentStateSavedToFileOnce();
        PresenceNotifyEvent[] output = mPersistAtomsStorage.getPresenceNotifyEvent(0L);

        assertEquals(event1.carrierId, output[0].carrierId);
        assertEquals(event1.slotId, output[0].slotId);
        assertEquals(event1.contentBodyReceived, output[0].contentBodyReceived);
        assertEquals(1, output[0].rcsCapsCount);
        assertEquals(2, output[0].mmtelCapsCount);
        assertEquals(2, output[0].count);

    }
    @Test
    @SmallTest
    public void addPresenceNotifyEvent_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addPresenceNotifyEvent(mPresenceNotifyEvent1);
        mPersistAtomsStorage.addPresenceNotifyEvent(mPresenceNotifyEvent2);
        mPersistAtomsStorage.incTimeMillis(100L);

        // service state and service switch should be added successfully
        verifyCurrentStateSavedToFileOnce();
        PresenceNotifyEvent[] output = mPersistAtomsStorage.getPresenceNotifyEvent(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new PresenceNotifyEvent[] {mPresenceNotifyEvent1, mPresenceNotifyEvent2}, output);
    }

    @Test
    @SmallTest
    public void getPresenceNotifyEvent_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        PresenceNotifyEvent[] output = mPersistAtomsStorage.getPresenceNotifyEvent(100L);

        // should be denied
        assertNull(output);
    }

    @Test
    @SmallTest
    public void getPresenceNotifyEvent_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        PresenceNotifyEvent[] output1 = mPersistAtomsStorage.getPresenceNotifyEvent(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        PresenceNotifyEvent[] output2 = mPersistAtomsStorage.getPresenceNotifyEvent(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new PresenceNotifyEvent[] {mPresenceNotifyEvent1, mPresenceNotifyEvent2}, output1);
        assertProtoArrayEquals(new PresenceNotifyEvent[0], output2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().presenceNotifyEventPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).presenceNotifyEventPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).presenceNotifyEventPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addSipTransportFeatureTag_emptyProto() throws Exception {
        // verify add atom into new file
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipTransportFeatureTagStats(mSipTransportFeatureTagStats1);
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        SipTransportFeatureTagStats[] outputs =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(0L);
        assertProtoArrayEquals(
                new SipTransportFeatureTagStats[] {mSipTransportFeatureTagStats1}, outputs);
    }

    @Test
    @SmallTest
    public void addSipTransportFeatureTagStats_withExistingEntries() throws Exception {
        // verify add atom on existing atom already stored
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        //Add two different SipTransportFeatureTagStats.
        mPersistAtomsStorage.addSipTransportFeatureTagStats(mSipTransportFeatureTagStats1);
        mPersistAtomsStorage.addSipTransportFeatureTagStats(mSipTransportFeatureTagStats2);
        mPersistAtomsStorage.incTimeMillis(100L);

        // SipTransportFeatureTagStats should be added successfully
        verifyCurrentStateSavedToFileOnce();
        SipTransportFeatureTagStats[] outputs =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(0L);

        assertProtoArrayEqualsIgnoringOrder(new SipTransportFeatureTagStats[]
                {mSipTransportFeatureTagStats1, mSipTransportFeatureTagStats2}, outputs);
    }

    @Test
    @SmallTest
    public void addSipTransportFeatureTagStats_tooManyEntries() throws Exception {
        // verify add atom excess MAX count (100)
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // Try to add 26 stats where MAX is 25
        int max = 26;
        SipTransportFeatureTagStats[] overMaxSipTransportFeatureTagStats =
                new SipTransportFeatureTagStats[max];

        for (int i = 0; i < max; i++) {
            overMaxSipTransportFeatureTagStats[i] = copyOf(mSipTransportFeatureTagStats1);
            overMaxSipTransportFeatureTagStats[i].sipTransportDeniedReason = i;
            mPersistAtomsStorage
                    .addSipTransportFeatureTagStats(overMaxSipTransportFeatureTagStats[i]);
            mPersistAtomsStorage.incTimeMillis(100L);
        }

        mPersistAtomsStorage
                .addSipTransportFeatureTagStats(mSipTransportFeatureTagStats2);
        verifyCurrentStateSavedToFileOnce();

        SipTransportFeatureTagStats[] outputs =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(0L);

        // The last added SipTransportFeatureTagStat remains
        // and two old stats should be removed
        assertHasStats(outputs, overMaxSipTransportFeatureTagStats, max - 2);
        assertHasStats(outputs, mSipTransportFeatureTagStats2, 1);
    }

    @Test
    @SmallTest
    public void addSipTransportFeatureTagStats_updateExistingEntries() throws Exception {
        // verify count
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipTransportFeatureTagStats(copyOf(mSipTransportFeatureTagStats1));
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        // SipTransportFeatureTag's durations should be doubled
        SipTransportFeatureTagStats newSipTransportFeatureTagStats1 =
                copyOf(mSipTransportFeatureTagStats1);
        newSipTransportFeatureTagStats1.associatedMillis *= 2;

        SipTransportFeatureTagStats[] outputs =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(0L);

        assertProtoArrayEqualsIgnoringOrder(
                new SipTransportFeatureTagStats[] {
                        newSipTransportFeatureTagStats1,
                        mSipTransportFeatureTagStats2
                }, outputs);
    }

    @Test
    @SmallTest
    public void getSipTransportFeatureTagStats_tooFrequent() throws Exception {
        // verify get frequently
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum

        SipTransportFeatureTagStats[] outputs =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(100L);

        // should be denied
        assertNull(outputs);
    }

    @Test
    @SmallTest
    public void getSipTransportFeatureTagStats_withSavedAtoms() throws Exception {
        // verify last get time after get atoms
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        SipTransportFeatureTagStats[] output1 =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        SipTransportFeatureTagStats[] output2 =
                mPersistAtomsStorage.getSipTransportFeatureTagStats(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new SipTransportFeatureTagStats[] {
                        mSipTransportFeatureTagStats1,
                        mSipTransportFeatureTagStats2
                }, output1);
        assertProtoArrayEquals(new SipTransportFeatureTagStats[0], output2);
        assertEquals(START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto()
                        .sipTransportFeatureTagStatsPullTimestampMillis);

        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).sipTransportFeatureTagStatsPullTimestampMillis);
        assertEquals(START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).sipTransportFeatureTagStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addSipDelegateStats_emptyProto() throws Exception {
        // verify add atom into new file
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipDelegateStats(mSipDelegateStats1);
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        SipDelegateStats[] outputs = mPersistAtomsStorage.getSipDelegateStats(0L);
        assertProtoArrayEquals(new SipDelegateStats[] {mSipDelegateStats1}, outputs);
    }

    @Test
    @SmallTest
    public void addSipDelegateStats_withExistingEntries() throws Exception {
        // verify add atom on existing atom already stored
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipDelegateStats(copyOf(mSipDelegateStats1));
        mPersistAtomsStorage.addSipDelegateStats(copyOf(mSipDelegateStats2));
        mPersistAtomsStorage.addSipDelegateStats(copyOf(mSipDelegateStats3));
        mPersistAtomsStorage.incTimeMillis(100L);
        // Three SipDelegateStats should be added successfully
        verifyCurrentStateSavedToFileOnce();

        SipDelegateStats[] outputs =
                mPersistAtomsStorage.getSipDelegateStats(0L);

        assertProtoArrayEqualsIgnoringOrder(
                new SipDelegateStats[] {mSipDelegateStats1, mSipDelegateStats2, mSipDelegateStats3},
                outputs);
    }

    @Test
    @SmallTest
    public void addSipDelegateStats_tooManyEntries() throws Exception {
        // verify add atom excess MAX count
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // Try to add 11 stats where MAX is 10
        int max = 11;
        SipDelegateStats[] overMaxSipDelegateStats = new SipDelegateStats[max];
        for (int i = 0; i < max; i++) {
            overMaxSipDelegateStats[i] = copyOf(mSipDelegateStats1);
            mPersistAtomsStorage
                    .addSipDelegateStats(overMaxSipDelegateStats[i]);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        mPersistAtomsStorage.addSipDelegateStats(mSipDelegateStats3);
        verifyCurrentStateSavedToFileOnce();

        SipDelegateStats[] outputs =
                mPersistAtomsStorage.getSipDelegateStats(0L);

        // The last added SipDelegate remains
        // and two old stats should be removed
        assertHasStats(outputs, overMaxSipDelegateStats, max - 2);
        assertHasStats(outputs, mSipDelegateStats3, 1);
    }

    @Test
    @SmallTest
    public void addSipDelegateStats_updateExistingEntries() throws Exception {
        // verify count
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        SipDelegateStats newSipDelegateStats3 = copyOf(mSipDelegateStats3);
        newSipDelegateStats3.destroyReason =
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD;
        mPersistAtomsStorage.addSipDelegateStats(newSipDelegateStats3);
        mPersistAtomsStorage.incTimeMillis(100L);

        SipDelegateStats newSipDelegateStats1 = copyOf(mSipDelegateStats1);
        newSipDelegateStats1.destroyReason =
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP;
        mPersistAtomsStorage.addSipDelegateStats(newSipDelegateStats1);
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        SipDelegateStats[] outputs = mPersistAtomsStorage.getSipDelegateStats(0L);

        assertProtoArrayEqualsIgnoringOrder(
                new SipDelegateStats[] {mSipDelegateStats2, mSipDelegateStats3,
                        newSipDelegateStats3, newSipDelegateStats1}, outputs);
    }

    @Test
    @SmallTest
    public void getSipDelegateStats_tooFrequent() throws Exception {
        // verify get frequently
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum

        SipDelegateStats[] outputs = mPersistAtomsStorage.getSipDelegateStats(100L);
        // should be denied
        assertNull(outputs);
    }

    @Test
    @SmallTest
    public void getSipDelegateStats_withSavedAtoms() throws Exception {
        // verify last get time after get atoms
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        SipDelegateStats[] output1 = mPersistAtomsStorage.getSipDelegateStats(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        SipDelegateStats[] output2 = mPersistAtomsStorage.getSipDelegateStats(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved
        assertProtoArrayEqualsIgnoringOrder(
                new SipDelegateStats[] {
                        mSipDelegateStats2,
                        mSipDelegateStats3}, output1);
        assertProtoArrayEquals(new SipDelegateStats[0], output2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().sipDelegateStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).sipDelegateStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).sipDelegateStatsPullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addGbaEvent_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addGbaEvent(mGbaEvent1);
        mPersistAtomsStorage.incTimeMillis(100L);

        // gba event should be added successfully
        verifyCurrentStateSavedToFileOnce();
        GbaEvent[] stats = mPersistAtomsStorage.getGbaEvent(0L);
        assertProtoArrayEquals(new GbaEvent[] {mGbaEvent1}, stats);
    }

    @Test
    @SmallTest
    public void addGbaEvent_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addGbaEvent(mGbaEvent1);
        mPersistAtomsStorage.incTimeMillis(100L);
        mPersistAtomsStorage.addGbaEvent(mGbaEvent2);
        mPersistAtomsStorage.incTimeMillis(100L);

        // gba event1, gba event2 should be added successfully
        verifyCurrentStateSavedToFileOnce();
        GbaEvent[] stats = mPersistAtomsStorage.getGbaEvent(0L);
        assertProtoArrayEqualsIgnoringOrder(
                new GbaEvent[] {mGbaEvent1, mGbaEvent2}, stats);
    }

    @Test
    @SmallTest
    public void addGbaEvent_tooManyEntries() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // Add 11 stats, but max is 10
        for (int i = 0; i < 11; i++) {
            mPersistAtomsStorage.addGbaEvent(mGbaEvent1);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        mPersistAtomsStorage.addGbaEvent(mGbaEvent2);

        verifyCurrentStateSavedToFileOnce();
        GbaEvent[] stats = mPersistAtomsStorage.getGbaEvent(0L);
        assertHasStatsAndCount(stats, mGbaEvent1, 11);
        assertHasStatsAndCount(stats, mGbaEvent2, 1);
    }

    @Test
    @SmallTest
    public void addGbaEvent_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);
        GbaEvent newStats = copyOf(mGbaEvent1);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        mPersistAtomsStorage.addGbaEvent(copyOf(mGbaEvent1));
        mPersistAtomsStorage.incTimeMillis(100L);

        // mGbaEvent1's count should be doubled
        verifyCurrentStateSavedToFileOnce();
        GbaEvent[] stats =
                mPersistAtomsStorage.getGbaEvent(0L);
        newStats.count *= 2;
        assertProtoArrayEqualsIgnoringOrder(new GbaEvent[] {mGbaEvent2, newStats}, stats);
    }

    @Test
    @SmallTest
    public void addSipMessageResponse_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipMessageResponse(mSipMessageResponse1);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();
        SipMessageResponse[] expected = mPersistAtomsStorage.getSipMessageResponse(0L);
        assertProtoArrayEquals(new SipMessageResponse[] {mSipMessageResponse1}, expected);
    }

    @Test
    @SmallTest
    public void addSipMessageResponse_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipMessageResponse(mSipMessageResponse1);
        mPersistAtomsStorage.incTimeMillis(100L);
        mPersistAtomsStorage.addSipMessageResponse(mSipMessageResponse2);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();
        SipMessageResponse[] expected =
                new SipMessageResponse[] {mSipMessageResponse1, mSipMessageResponse2};

        assertProtoArrayEqualsIgnoringOrder(
                expected, mPersistAtomsStorage.getSipMessageResponse(0L));
    }

    @Test
    @SmallTest
    public void addSipMessageResponse_tooManyEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // store 11 same atoms, but only 1 atoms stored with count 11
        for (int i = 0; i < 11; i++) {
            mPersistAtomsStorage.addSipMessageResponse(mSipMessageResponse1);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        // store 1 different atom and count 1
        mPersistAtomsStorage.addSipMessageResponse(mSipMessageResponse2);

        verifyCurrentStateSavedToFileOnce();
        SipMessageResponse[] result = mPersistAtomsStorage.getSipMessageResponse(0L);

        // first atom has count 11, the other has 1
        assertHasStats(result, mSipMessageResponse1, 11);
        assertHasStats(result, mSipMessageResponse2, 1);
    }

    @Test
    @SmallTest
    public void addSipMessageResponse_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addSipMessageResponse(copyOf(mSipMessageResponse2));
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        SipMessageResponse[] outputs = mPersistAtomsStorage.getSipMessageResponse(0L);
        SipMessageResponse newSipMessageResponse = copyOf(mSipMessageResponse2);
        newSipMessageResponse.count *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new SipMessageResponse[] {mSipMessageResponse1, newSipMessageResponse}, outputs);
    }

    @Test
    @SmallTest
    public void addCompleteSipTransportSession_emptyProto() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addCompleteSipTransportSession(mSipTransportSession1);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();
        SipTransportSession[] expected = mPersistAtomsStorage.getSipTransportSession(0L);
        assertProtoArrayEquals(new SipTransportSession[] {mSipTransportSession1}, expected);
    }

    @Test
    @SmallTest
    public void addCompleteSipTransportSession_withExistingEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addCompleteSipTransportSession(mSipTransportSession1);
        mPersistAtomsStorage.incTimeMillis(100L);
        mPersistAtomsStorage.addCompleteSipTransportSession(mSipTransportSession2);
        mPersistAtomsStorage.incTimeMillis(100L);

        verifyCurrentStateSavedToFileOnce();
        SipTransportSession[] expected =
                new SipTransportSession[] {mSipTransportSession1, mSipTransportSession2};

        assertProtoArrayEqualsIgnoringOrder(
                expected, mPersistAtomsStorage.getSipTransportSession(0L));
    }

    @Test
    @SmallTest
    public void addCompleteSipTransportSession_tooManyEntries() throws Exception {
        createEmptyTestFile();
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // store 11 same atoms, but only 1 atoms stored with count 11
        for (int i = 0; i < 11; i++) {
            mPersistAtomsStorage.addCompleteSipTransportSession(mSipTransportSession1);
            mPersistAtomsStorage.incTimeMillis(100L);
        }
        // store 1 different atom and count 1
        mPersistAtomsStorage.addCompleteSipTransportSession(mSipTransportSession2);

        verifyCurrentStateSavedToFileOnce();
        SipTransportSession[] result = mPersistAtomsStorage.getSipTransportSession(0L);

        // first atom has count 11, the other has 1
        assertHasStats(result, mSipTransportSession1, 11);
        assertHasStats(result, mSipTransportSession2, 1);
    }

    @Test
    @SmallTest
    public void addCompleteSipTransportSession_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addCompleteSipTransportSession(copyOf(mSipTransportSession2));
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        SipTransportSession[] outputs = mPersistAtomsStorage.getSipTransportSession(0L);
        SipTransportSession newSipTransportSession = copyOf(mSipTransportSession2);
        newSipTransportSession.sessionCount *= 2;
        newSipTransportSession.endedGracefullyCount *= 2;
        assertProtoArrayEqualsIgnoringOrder(
                new SipTransportSession[] {mSipTransportSession1,
                        newSipTransportSession}, outputs);
    }

    @Test
    @SmallTest
    public void getUnmeteredNetworks_noExistingEntry() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        InOrder inOrder = inOrder(mTestFileOutputStream);

        assertEquals(0L, mPersistAtomsStorage.getUnmeteredNetworks(1, 0));

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void getUnmeteredNetworks() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        InOrder inOrder = inOrder(mTestFileOutputStream);

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(NETWORK_TYPE_BITMASK_GPRS, mPersistAtomsStorage.getUnmeteredNetworks(0, 0));
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(0L, mPersistAtomsStorage.getUnmeteredNetworks(0, 0));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void getUnmeteredNetworks_carrierIdMismatch() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        InOrder inOrder = inOrder(mTestFileOutputStream);

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(0L, mPersistAtomsStorage.getUnmeteredNetworks(0, 1));
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(0L, mPersistAtomsStorage.getUnmeteredNetworks(0, 0));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addUnmeteredNetworks() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        InOrder inOrder = inOrder(mTestFileOutputStream);

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GSM);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(
                NETWORK_TYPE_BITMASK_GPRS | NETWORK_TYPE_BITMASK_GSM,
                mPersistAtomsStorage.getUnmeteredNetworks(0, 0));
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(0, mPersistAtomsStorage.getUnmeteredNetworks(0, 0));
        inOrder.verifyNoMoreInteractions();

        mPersistAtomsStorage.addUnmeteredNetworks(1, 2, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        mPersistAtomsStorage.addUnmeteredNetworks(1, 2, NETWORK_TYPE_BITMASK_GSM);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(
                NETWORK_TYPE_BITMASK_GPRS | NETWORK_TYPE_BITMASK_GSM,
                mPersistAtomsStorage.getUnmeteredNetworks(1, 2));
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(0, mPersistAtomsStorage.getUnmeteredNetworks(1, 2));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addUnmeteredNetworks_carrierIdMismatch() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        InOrder inOrder = inOrder(mTestFileOutputStream);

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        mPersistAtomsStorage.addUnmeteredNetworks(0, 1, NETWORK_TYPE_BITMASK_GSM);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(NETWORK_TYPE_BITMASK_GSM, mPersistAtomsStorage.getUnmeteredNetworks(0, 1));
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        assertEquals(0L, mPersistAtomsStorage.getUnmeteredNetworks(0, 1));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addUnmeteredNetworks_sameBitmask() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        InOrder inOrder = inOrder(mTestFileOutputStream);

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();

        mPersistAtomsStorage.addUnmeteredNetworks(0, 0, NETWORK_TYPE_BITMASK_GPRS);
        inOrder.verifyNoMoreInteractions();

        assertEquals(NETWORK_TYPE_BITMASK_GPRS, mPersistAtomsStorage.getUnmeteredNetworks(0, 0));
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void clearAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);
        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addCompleteSipTransportSession(copyOf(mSipTransportSession1));
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        mPersistAtomsStorage.addUceEventStats(mUceEventStats1);
        mPersistAtomsStorage.incTimeMillis(100L);
        verifyCurrentStateSavedToFileOnce();

        mPersistAtomsStorage.clearAtoms();
        verifyCurrentStateSavedToFileOnce();
        UceEventStats[] uceEventStats = mPersistAtomsStorage.getUceEventStats(0L);
        assertEquals(null, uceEventStats);
        SipTransportSession[] sipTransportSession = mPersistAtomsStorage.getSipTransportSession(0L);
        assertEquals(null, sipTransportSession);
    }

    /* Utilities */

    private void createEmptyTestFile() throws Exception {
        PersistAtoms atoms = new PersistAtoms();
        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write(PersistAtoms.toByteArray(atoms));
        stream.close();
    }

    private void createTestFile(long lastPullTimeMillis) throws Exception {
        PersistAtoms atoms = new PersistAtoms();
        atoms.buildFingerprint = Build.FINGERPRINT;
        atoms.voiceCallRatUsagePullTimestampMillis = lastPullTimeMillis;
        atoms.voiceCallRatUsage = mVoiceCallRatUsages;
        atoms.voiceCallSessionPullTimestampMillis = lastPullTimeMillis;
        atoms.voiceCallSession = mVoiceCallSessions;
        atoms.cellularServiceStatePullTimestampMillis = lastPullTimeMillis;
        atoms.cellularServiceState = mServiceStates;
        atoms.cellularDataServiceSwitchPullTimestampMillis = lastPullTimeMillis;
        atoms.cellularDataServiceSwitch = mServiceSwitches;
        atoms.imsRegistrationStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.imsRegistrationStats = mImsRegistrationStats;
        atoms.imsRegistrationTerminationPullTimestampMillis = lastPullTimeMillis;
        atoms.imsRegistrationTermination = mImsRegistrationTerminations;
        atoms.imsRegistrationFeatureTagStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.imsRegistrationFeatureTagStats = mImsRegistrationFeatureTagStatses;
        atoms.rcsClientProvisioningStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.rcsClientProvisioningStats = mRcsClientProvisioningStatses;
        atoms.rcsAcsProvisioningStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.rcsAcsProvisioningStats = mRcsAcsProvisioningStatses;
        atoms.imsRegistrationServiceDescStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.imsRegistrationServiceDescStats = mImsRegistrationServiceDescStats;
        atoms.imsDedicatedBearerListenerEventPullTimestampMillis = lastPullTimeMillis;
        atoms.imsDedicatedBearerListenerEvent = mImsDedicatedBearerListenerEvents;
        atoms.imsDedicatedBearerEventPullTimestampMillis = lastPullTimeMillis;
        atoms.imsDedicatedBearerEvent = mImsDedicatedBearerEvents;
        atoms.uceEventStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.uceEventStats = mUceEventStatses;
        atoms.presenceNotifyEventPullTimestampMillis = lastPullTimeMillis;
        atoms.presenceNotifyEvent = mPresenceNotifyEvents;
        atoms.sipTransportFeatureTagStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.sipTransportFeatureTagStats = mSipTransportFeatureTagStatsArray;
        atoms.sipDelegateStatsPullTimestampMillis = lastPullTimeMillis;
        atoms.sipDelegateStats = mSipDelegateStatsArray;
        atoms.gbaEventPullTimestampMillis = lastPullTimeMillis;
        atoms.gbaEvent = mGbaEvent;
        atoms.sipMessageResponsePullTimestampMillis = lastPullTimeMillis;
        atoms.sipMessageResponse = mSipMessageResponse;
        atoms.sipTransportSessionPullTimestampMillis = lastPullTimeMillis;
        atoms.sipTransportSession = mSipTransportSession;
        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write(PersistAtoms.toByteArray(atoms));
        stream.close();
    }

    private PersistAtoms getAtomsWritten(@Nullable InOrder inOrder) throws Exception {
        if (inOrder == null) {
            inOrder = inOrder(mTestFileOutputStream);
        }
        ArgumentCaptor bytesCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(mTestFileOutputStream, times(1)).write((byte[]) bytesCaptor.capture());
        PersistAtoms savedAtoms = PersistAtoms.parseFrom((byte[]) bytesCaptor.getValue());
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        return savedAtoms;
    }

    private static void addRepeatedCalls(
            PersistAtomsStorage storage, VoiceCallSession call, int count) {
        for (int i = 0; i < count; i++) {
            storage.addVoiceCallSession(call);
        }
    }

    private static VoiceCallRatUsage[] multiplyVoiceCallRatUsage(
            VoiceCallRatUsage[] usages, int times) {
        VoiceCallRatUsage[] multipliedUsages = new VoiceCallRatUsage[usages.length];
        for (int i = 0; i < usages.length; i++) {
            multipliedUsages[i] = new VoiceCallRatUsage();
            multipliedUsages[i].carrierId = usages[i].carrierId;
            multipliedUsages[i].rat = usages[i].rat;
            multipliedUsages[i].callCount = usages[i].callCount * 2;
            multipliedUsages[i].totalDurationMillis = usages[i].totalDurationMillis * 2;
        }
        return multipliedUsages;
    }

    private static CellularServiceState copyOf(CellularServiceState source) throws Exception {
        return CellularServiceState.parseFrom(MessageNano.toByteArray(source));
    }

    private static CellularDataServiceSwitch copyOf(CellularDataServiceSwitch source)
            throws Exception {
        return CellularDataServiceSwitch.parseFrom(MessageNano.toByteArray(source));
    }

    private static ImsRegistrationStats copyOf(ImsRegistrationStats source) throws Exception {
        return ImsRegistrationStats.parseFrom(MessageNano.toByteArray(source));
    }

    private static ImsRegistrationTermination copyOf(ImsRegistrationTermination source)
            throws Exception {
        return ImsRegistrationTermination.parseFrom(MessageNano.toByteArray(source));
    }

    private static DataCallSession copyOf(DataCallSession source)
            throws Exception {
        return DataCallSession.parseFrom(MessageNano.toByteArray(source));
    }

    private static ImsRegistrationFeatureTagStats copyOf(ImsRegistrationFeatureTagStats source)
            throws Exception {
        return ImsRegistrationFeatureTagStats.parseFrom(MessageNano.toByteArray(source));
    }

    private static RcsAcsProvisioningStats copyOf(RcsAcsProvisioningStats source)
            throws Exception {
        return RcsAcsProvisioningStats.parseFrom(MessageNano.toByteArray(source));
    }

    private static ImsRegistrationServiceDescStats copyOf(ImsRegistrationServiceDescStats source)
            throws Exception {
        return ImsRegistrationServiceDescStats.parseFrom(MessageNano.toByteArray(source));
    }

    private static ImsDedicatedBearerListenerEvent copyOf(ImsDedicatedBearerListenerEvent source)
            throws Exception {
        return ImsDedicatedBearerListenerEvent.parseFrom(MessageNano.toByteArray(source));
    }

    private static ImsDedicatedBearerEvent copyOf(ImsDedicatedBearerEvent source)
            throws Exception {
        return ImsDedicatedBearerEvent.parseFrom(MessageNano.toByteArray(source));
    }

    private static UceEventStats copyOf(UceEventStats source)
            throws Exception {
        return UceEventStats.parseFrom(MessageNano.toByteArray(source));
    }

    private static PresenceNotifyEvent copyOf(PresenceNotifyEvent source)
            throws Exception {
        return PresenceNotifyEvent.parseFrom(MessageNano.toByteArray(source));
    }

    private static SipDelegateStats copyOf(SipDelegateStats source)
            throws Exception {
        return SipDelegateStats.parseFrom(MessageNano.toByteArray(source));
    }
    private static SipTransportFeatureTagStats copyOf(SipTransportFeatureTagStats source)
            throws Exception {
        return SipTransportFeatureTagStats.parseFrom(MessageNano.toByteArray(source));
    }

    private static GbaEvent copyOf(GbaEvent source)
            throws Exception {
        return GbaEvent.parseFrom(MessageNano.toByteArray(source));
    }

    private static SipMessageResponse copyOf(SipMessageResponse source)
            throws Exception {
        return SipMessageResponse.parseFrom(MessageNano.toByteArray(source));
    }

    private static SipTransportSession copyOf(SipTransportSession source)
            throws Exception {
        return SipTransportSession.parseFrom(MessageNano.toByteArray(source));
    }

    private void assertAllPullTimestampEquals(long timestamp) {
        assertEquals(
                timestamp,
                mPersistAtomsStorage.getAtomsProto().voiceCallRatUsagePullTimestampMillis);
        assertEquals(
                timestamp,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        assertEquals(
                timestamp,
                mPersistAtomsStorage.getAtomsProto().cellularServiceStatePullTimestampMillis);
        assertEquals(
                timestamp,
                mPersistAtomsStorage.getAtomsProto().cellularDataServiceSwitchPullTimestampMillis);
    }

    private void assertStorageIsEmptyForAllAtoms() {
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getVoiceCallRatUsages(0L));
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getVoiceCallSessions(0L));
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getCellularServiceStates(0L));
        assertProtoArrayIsEmpty(mPersistAtomsStorage.getCellularDataServiceSwitches(0L));
    }

    private static <T extends MessageNano> void assertProtoArrayIsEmpty(T[] array) {
        assertNotNull(array);
        assertEquals(0, array.length);
    }

    private static void assertProtoArrayEquals(MessageNano[] expected, MessageNano[] actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        String message =
                "Expected:\n" + Arrays.toString(expected) + "\nGot:\n" + Arrays.toString(actual);
        assertEquals(message, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(message, MessageNano.messageNanoEquals(expected[i], actual[i]));
        }
    }

    private static void assertProtoArrayEqualsIgnoringOrder(
            MessageNano[] expected, MessageNano[] actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        expected = expected.clone();
        actual = actual.clone();
        Arrays.sort(expected, sProtoComparator);
        Arrays.sort(actual, sProtoComparator);
        assertProtoArrayEquals(expected, actual);
    }

    private static void assertHasCall(
            VoiceCallSession[] calls, @Nullable VoiceCallSession expectedCall, int expectedCount) {
        assertNotNull(calls);
        int actualCount = 0;
        for (VoiceCallSession call : calls) {
            if (call != null && expectedCall != null) {
                if (MessageNano.messageNanoEquals(call, expectedCall)) {
                    actualCount++;
                }
            }
        }
        assertEquals(expectedCount, actualCount);
    }

    private void verifyCurrentStateSavedToFileOnce() throws Exception {
        InOrder inOrder = inOrder(mTestFileOutputStream);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
    }

    private static void assertHasStatsCountTime(
            ImsRegistrationFeatureTagStats[] statses,
            @Nullable ImsRegistrationFeatureTagStats expectedStats,
            int expectedCount, long expectedTime) {
        assertNotNull(statses);
        int actualCount = 0;
        long actualTime = 0;
        for (ImsRegistrationFeatureTagStats stats : statses) {
            if (stats != null && expectedStats != null) {
                if (stats.carrierId == expectedStats.carrierId
                        && stats.slotId == expectedStats.slotId
                        && stats.featureTagName == expectedStats.featureTagName
                        && stats.registrationTech == expectedStats.registrationTech) {
                    actualCount++;
                    actualTime += stats.registeredMillis;
                }
            }
        }
        assertEquals(expectedCount, actualCount);
        assertEquals(expectedTime, actualTime);
    }

    private static void assertHasStatsAndCount(
            RcsClientProvisioningStats[] statses,
            @Nullable RcsClientProvisioningStats expectedStats, int expectedCount) {
        assertNotNull(statses);
        int actualCount = -1;
        for (RcsClientProvisioningStats stats : statses) {
            if (stats.carrierId == expectedStats.carrierId
                    && stats.slotId == expectedStats.slotId
                    && stats.event == expectedStats.event) {
                actualCount = stats.count;
            }
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertHasStats(
            ImsDedicatedBearerListenerEvent[] statses,
            @Nullable ImsDedicatedBearerListenerEvent expectedStats, int expectedCount) {
        assertNotNull(statses);
        int actualCount = 0;
        for (ImsDedicatedBearerListenerEvent stats : statses) {
            if (stats != null && expectedStats != null) {
                if (MessageNano.messageNanoEquals(stats, expectedStats)) {
                    actualCount++;
                }
            }
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertHasStatsAndCount(
            ImsDedicatedBearerEvent[] statses,
            @Nullable ImsDedicatedBearerEvent expectedStats, int expectedCount) {
        assertNotNull(statses);
        int actualCount = -1;
        for (ImsDedicatedBearerEvent stats : statses) {
            if (stats.carrierId == expectedStats.carrierId
                    && stats.slotId == expectedStats.slotId
                    && stats.ratAtEnd == expectedStats.ratAtEnd
                    && stats.qci == expectedStats.qci
                    && stats.bearerState == expectedStats.bearerState
                    && stats.localConnectionInfoReceived
                            == expectedStats.localConnectionInfoReceived
                    && stats.remoteConnectionInfoReceived
                            == expectedStats.remoteConnectionInfoReceived
                    && stats.hasListeners == expectedStats.hasListeners) {
                actualCount = stats.count;
            }
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertHasStatsAndCountDuration(
            RcsAcsProvisioningStats[] statses,
            @Nullable RcsAcsProvisioningStats expectedStats, int count, long duration) {
        assertNotNull(statses);
        int actualCount = -1;
        long actualDuration = -1;
        for (RcsAcsProvisioningStats stats : statses) {
            if (stats.carrierId == expectedStats.carrierId
                    && stats.slotId == expectedStats.slotId
                    && stats.responseCode == expectedStats.responseCode
                    && stats.responseType == expectedStats.responseType
                    && stats.isSingleRegistrationEnabled
                            == expectedStats.isSingleRegistrationEnabled) {
                actualCount = stats.count;
                actualDuration = stats.stateTimerMillis;
            }
        }
        assertEquals(count, actualCount);
        assertEquals(duration, actualDuration);
    }

    private static void assertHasStats(SipDelegateStats[] results,
            Object expectedStats, int expectedCount) {
        assertNotNull(results);
        assertNotNull(expectedStats);

        int realCount = 0;
        if (expectedStats instanceof SipDelegateStats[]) {
            SipDelegateStats[] expectedResults = (SipDelegateStats[]) expectedStats;
            for (SipDelegateStats stat: results) {
                for (SipDelegateStats estat : expectedResults) {
                    if (stat != null && estat != null) {
                        if (MessageNano.messageNanoEquals(stat, estat)) {
                            realCount++;
                            break;
                        }
                    }
                }
            }
        } else {
            SipDelegateStats expectedResult = (SipDelegateStats) expectedStats;
            for (SipDelegateStats stat : results) {
                if (stat != null && expectedStats != null) {
                    if (MessageNano.messageNanoEquals(stat, expectedResult)) {
                        realCount++;
                    }
                }
            }
        }
        assertEquals(expectedCount, realCount);
    }

    private static void assertHasStats(SipTransportFeatureTagStats[] results,
            Object expectedStats, int expectedCount) {
        assertNotNull(results);
        assertNotNull(expectedStats);

        int realCount = 0;
        if (expectedStats instanceof SipTransportFeatureTagStats[]) {
            SipTransportFeatureTagStats[] expectedResults =
                    (SipTransportFeatureTagStats[]) expectedStats;
            for (SipTransportFeatureTagStats stat: results) {
                for (SipTransportFeatureTagStats estat : expectedResults) {
                    if (stat != null && estat != null) {
                        if (MessageNano.messageNanoEquals(stat, estat)) {
                            realCount++;
                            break;
                        }
                    }
                }
            }
        } else {
            SipTransportFeatureTagStats expectedResult =
                    (SipTransportFeatureTagStats) expectedStats;
            for (SipTransportFeatureTagStats stat : results) {
                if (stat != null && expectedStats != null) {
                    if (MessageNano.messageNanoEquals(stat, expectedResult)) {
                        realCount++;
                    }
                }
            }
        }
        assertEquals(expectedCount, realCount);
    }

    private static void assertHasStatsAndCount(
            GbaEvent[] statses,
            @Nullable GbaEvent expectedStats, int expectedCount) {
        assertNotNull(statses);
        int actualCount = -1;
        for (GbaEvent stats : statses) {
            if (stats.carrierId == expectedStats.carrierId
                    && stats.slotId == expectedStats.slotId
                    && stats.successful == expectedStats.successful
                    && stats.failedReason == expectedStats.failedReason) {
                actualCount = stats.count;
            }
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertHasStats(
            SipMessageResponse[] statses,
            @Nullable SipMessageResponse expectedStats, int expectedCount) {
        assertNotNull(statses);
        int actualCount = -1;
        for (SipMessageResponse stats : statses) {
            if (stats.carrierId == expectedStats.carrierId
                    && stats.slotId == expectedStats.slotId
                    && stats.sipMessageMethod == expectedStats.sipMessageMethod
                    && stats.sipMessageResponse == expectedStats.sipMessageResponse
                    && stats.sipMessageDirection == expectedStats.sipMessageDirection) {
                actualCount = stats.count;
            }
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertHasStats(
            SipTransportSession[] statses,
            @Nullable SipTransportSession expectedStats, int expectedCount) {
        assertNotNull(statses);
        int actualCount = -1;
        for (SipTransportSession stats : statses) {
            if (stats.carrierId == expectedStats.carrierId
                    && stats.slotId == expectedStats.slotId
                    && stats.sessionMethod == expectedStats.sessionMethod
                    && stats.sipMessageDirection == expectedStats.sipMessageDirection
                    && stats.sipResponse == expectedStats.sipResponse) {
                actualCount = stats.sessionCount;
            }
        }
        assertEquals(expectedCount, actualCount);
    }
}
