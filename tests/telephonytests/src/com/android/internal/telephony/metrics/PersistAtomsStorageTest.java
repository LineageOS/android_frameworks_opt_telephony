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

import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.telephony.DisconnectCause;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsReasonInfo;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularDataServiceSwitch;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularServiceState;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationTermination;
import com.android.internal.telephony.nano.PersistAtomsProto.PersistAtoms;
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
import org.mockito.Mock;

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

    @Mock private FileOutputStream mTestFileOutputStream;

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

        // CS MO call while camped on LTE
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
        mCall4Proto.isEmergency = false;
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

        mServiceSwitches =
                new CellularDataServiceSwitch[] {mServiceSwitch1Proto, mServiceSwitch2Proto};
        mServiceStates =
                new CellularServiceState[] {
                    mServiceState1Proto,
                    mServiceState2Proto,
                    mServiceState3Proto,
                    mServiceState4Proto
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
        makeTestData();

        // by default, test loading with real file IO and saving with mocks
        mTestFile = mFolder.newFile(TEST_FILE);
        doReturn(mTestFileOutputStream).when(mContext).openFileOutput(anyString(), anyInt());
        doReturn(mTestFile).when(mContext).getFileStreamPath(anyString());
    }

    @After
    public void tearDown() throws Exception {
        mTestFile.delete();
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
                    mServiceState4Proto
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
                    mServiceState4Proto
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
        mPersistAtomsStorage.addImsRegistrationStats(mImsRegistrationStatsLte0);
        mPersistAtomsStorage.incTimeMillis(100L);

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
        mPersistAtomsStorage.addImsRegistrationStats(mImsRegistrationStatsLte0);

        mPersistAtomsStorage.addImsRegistrationStats(mImsRegistrationStatsWifi0);
        mPersistAtomsStorage.incTimeMillis(100L);

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
        mPersistAtomsStorage.incTimeMillis(100L);

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
        mPersistAtomsStorage.incTimeMillis(100L);
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
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().imsRegistrationStatsPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).imsRegistrationStatsPullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
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
}
