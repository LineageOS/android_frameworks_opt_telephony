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
import android.telephony.DisconnectCause;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsReasonInfo;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.PersistAtoms;
import com.android.internal.telephony.nano.PersistAtomsProto.RawVoiceCallRatUsage;
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

    private RawVoiceCallRatUsage mCarrier1LteUsageProto;
    private RawVoiceCallRatUsage mCarrier1UmtsUsageProto;
    private RawVoiceCallRatUsage mCarrier2LteUsageProto;
    private RawVoiceCallRatUsage mCarrier3LteUsageProto;
    private RawVoiceCallRatUsage mCarrier3GsmUsageProto;

    private VoiceCallSession[] mVoiceCallSessions;
    private RawVoiceCallRatUsage[] mVoiceCallRatUsages;

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

        mCarrier1LteUsageProto = new RawVoiceCallRatUsage();
        mCarrier1LteUsageProto.carrierId = CARRIER1_ID;
        mCarrier1LteUsageProto.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mCarrier1LteUsageProto.callCount = 1L;
        mCarrier1LteUsageProto.totalDurationMillis = 8000L;

        mCarrier1UmtsUsageProto = new RawVoiceCallRatUsage();
        mCarrier1UmtsUsageProto.carrierId = CARRIER1_ID;
        mCarrier1UmtsUsageProto.rat = TelephonyManager.NETWORK_TYPE_UMTS;
        mCarrier1UmtsUsageProto.callCount = 1L;
        mCarrier1UmtsUsageProto.totalDurationMillis = 6000L;

        mCarrier2LteUsageProto = new RawVoiceCallRatUsage();
        mCarrier2LteUsageProto.carrierId = CARRIER2_ID;
        mCarrier2LteUsageProto.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mCarrier2LteUsageProto.callCount = 2L;
        mCarrier2LteUsageProto.totalDurationMillis = 20000L;

        mCarrier3LteUsageProto = new RawVoiceCallRatUsage();
        mCarrier3LteUsageProto.carrierId = CARRIER3_ID;
        mCarrier3LteUsageProto.rat = TelephonyManager.NETWORK_TYPE_LTE;
        mCarrier3LteUsageProto.callCount = 1L;
        mCarrier3LteUsageProto.totalDurationMillis = 1000L;

        mCarrier3GsmUsageProto = new RawVoiceCallRatUsage();
        mCarrier3GsmUsageProto.carrierId = CARRIER3_ID;
        mCarrier3GsmUsageProto.rat = TelephonyManager.NETWORK_TYPE_GSM;
        mCarrier3GsmUsageProto.callCount = 1L;
        mCarrier3GsmUsageProto.totalDurationMillis = 100000L;

        mVoiceCallRatUsages =
                new RawVoiceCallRatUsage[] {
                    mCarrier1UmtsUsageProto,
                    mCarrier1LteUsageProto,
                    mCarrier2LteUsageProto,
                    mCarrier3LteUsageProto,
                    mCarrier3GsmUsageProto
                };
        mVoiceCallSessions =
                new VoiceCallSession[] {mCall1Proto, mCall2Proto, mCall3Proto, mCall4Proto};
    }

    private static class TestablePersistAtomsStorage extends PersistAtomsStorage {
        private long mTimeMillis = START_TIME_MILLIS;

        TestablePersistAtomsStorage(Context context) {
            super(context);
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
            // NOTE: not guarded by mLock as usual, should be fine since the test is single-threaded
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
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(0, voiceCallRatUsage.length);
        assertNotNull(voiceCallSession);
        assertEquals(0, voiceCallSession.length);
    }

    @Test
    @SmallTest
    public void loadAtoms_unreadable() throws Exception {
        createEmptyTestFile();
        mTestFile.setReadable(false);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be empty, pull time should be start time
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(0, voiceCallRatUsage.length);
        assertNotNull(voiceCallSession);
        assertEquals(0, voiceCallSession.length);
    }

    @Test
    @SmallTest
    public void loadAtoms_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be empty, pull time should be start time
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(0, voiceCallRatUsage.length);
        assertNotNull(voiceCallSession);
        assertEquals(0, voiceCallSession.length);
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
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(0, voiceCallRatUsage.length);
        assertNotNull(voiceCallSession);
        assertEquals(0, voiceCallSession.length);
    }

    @Test
    @SmallTest
    public void loadAtoms_pullTimeMissing() throws Exception {
        createTestFile(0L);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);

        // no exception should be thrown, storage should be match, pull time should be start time
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS,
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertProtoArrayEquals(mVoiceCallRatUsages, voiceCallRatUsage);
        assertProtoArrayEquals(mVoiceCallSessions, voiceCallSession);
    }

    @Test
    @SmallTest
    public void loadAtoms_validContents() throws Exception {
        createTestFile(100L);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);

        // no exception should be thrown, storage and pull time should match
        assertEquals(
                100L, mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                100L, mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis);
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertProtoArrayEquals(mVoiceCallRatUsages, voiceCallRatUsage);
        assertProtoArrayEquals(mVoiceCallSessions, voiceCallSession);
    }

    @Test
    @SmallTest
    public void addVoiceCallSession_emptyProto() throws Exception {
        createEmptyTestFile();

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallSession(mCall1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // call should be added successfully, there should be no RAT usage, changes should be saved
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(0, voiceCallRatUsage.length);
        assertProtoArrayEquals(new VoiceCallSession[] {mCall1Proto}, voiceCallSession);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addVoiceCallSession_withExistingCalls() throws Exception {
        createTestFile(100L);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallSession(mCall1Proto);
        mPersistAtomsStorage.incTimeMillis(100L);

        // call should be added successfully, RAT usages should not change, changes should be saved
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(mVoiceCallRatUsages.length, voiceCallRatUsage.length);
        assertNotNull(voiceCallSession);
        // call lists are randomized, but sorted version should be identical
        VoiceCallSession[] expectedVoiceCallSessions =
                new VoiceCallSession[] {
                    mCall1Proto, mCall1Proto, mCall2Proto, mCall3Proto, mCall4Proto
                };
        Arrays.sort(expectedVoiceCallSessions, sProtoComparator);
        Arrays.sort(voiceCallSession, sProtoComparator);
        assertProtoArrayEquals(expectedVoiceCallSessions, voiceCallSession);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
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
        VoiceCallSession[] calls = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertHasCall(calls, mCall1Proto, 49);
        assertHasCall(calls, mCall2Proto, 1);
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
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        RawVoiceCallRatUsage[] expectedVoiceCallRatUsage = mVoiceCallRatUsages.clone();
        Arrays.sort(expectedVoiceCallRatUsage, sProtoComparator);
        Arrays.sort(voiceCallRatUsage, sProtoComparator);
        assertProtoArrayEquals(expectedVoiceCallRatUsage, voiceCallRatUsage);
        assertNotNull(voiceCallSession);
        assertEquals(0, voiceCallSession.length);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addVoiceCallRatUsage_withExistingUsages() throws Exception {
        createTestFile(100L);
        VoiceCallRatTracker ratTracker = VoiceCallRatTracker.fromProto(mVoiceCallRatUsages);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallRatUsage(ratTracker);
        mPersistAtomsStorage.incTimeMillis(100L);

        // RAT should be added successfully, calls should not change, changes should be saved
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        // call count and duration should become doubled since mVoiceCallRatUsages applied through
        // both file and addVoiceCallRatUsage()
        RawVoiceCallRatUsage[] expectedVoiceCallRatUsage =
                multiplyVoiceCallRatUsage(mVoiceCallRatUsages, 2);
        Arrays.sort(expectedVoiceCallRatUsage, sProtoComparator);
        Arrays.sort(voiceCallRatUsage, sProtoComparator);
        assertProtoArrayEquals(expectedVoiceCallRatUsage, voiceCallRatUsage);
        assertNotNull(voiceCallSession);
        assertEquals(mVoiceCallSessions.length, voiceCallSession.length);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(PersistAtoms.toByteArray(mPersistAtomsStorage.getAtomsProto())));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SmallTest
    public void addVoiceCallRatUsage_empty() throws Exception {
        createEmptyTestFile();
        VoiceCallRatTracker ratTracker = VoiceCallRatTracker.fromProto(new RawVoiceCallRatUsage[0]);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.addVoiceCallRatUsage(ratTracker);
        mPersistAtomsStorage.incTimeMillis(100L);

        // RAT should be added successfully, calls should not change
        // in this case it does not necessarily need to save
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(0L);
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(0L);
        assertNotNull(voiceCallRatUsage);
        assertEquals(0, voiceCallRatUsage.length);
        assertNotNull(voiceCallSession);
        assertEquals(0, voiceCallSession.length);
    }

    @Test
    @SmallTest
    public void getVoiceCallRatUsages_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(50L); // pull interval less than minimum
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(100L);

        // should be denied
        assertNull(voiceCallRatUsage);
    }

    @Test
    @SmallTest
    public void getVoiceCallRatUsages_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mPersistAtomsStorage = new TestablePersistAtomsStorage(mContext);
        mPersistAtomsStorage.incTimeMillis(100L);
        RawVoiceCallRatUsage[] voiceCallRatUsage1 = mPersistAtomsStorage.getVoiceCallRatUsages(50L);
        mPersistAtomsStorage.incTimeMillis(100L);
        RawVoiceCallRatUsage[] voiceCallRatUsage2 = mPersistAtomsStorage.getVoiceCallRatUsages(50L);
        long voiceCallSessionPullTimestampMillis =
                mPersistAtomsStorage.getAtomsProto().voiceCallSessionPullTimestampMillis;
        VoiceCallSession[] voiceCallSession = mPersistAtomsStorage.getVoiceCallSessions(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved, other fields should be unaffected
        assertProtoArrayEquals(mVoiceCallRatUsages, voiceCallRatUsage1);
        assertProtoArrayEquals(new RawVoiceCallRatUsage[0], voiceCallRatUsage2);
        assertEquals(
                START_TIME_MILLIS + 200L,
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis);
        assertProtoArrayEquals(mVoiceCallSessions, voiceCallSession);
        assertEquals(START_TIME_MILLIS, voiceCallSessionPullTimestampMillis);
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(
                START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).rawVoiceCallRatUsagePullTimestampMillis);
        assertEquals(
                START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).rawVoiceCallRatUsagePullTimestampMillis);
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
                mPersistAtomsStorage.getAtomsProto().rawVoiceCallRatUsagePullTimestampMillis;
        RawVoiceCallRatUsage[] voiceCallRatUsage = mPersistAtomsStorage.getVoiceCallRatUsages(50L);

        // first set of results should equal to file contents, second should be empty, corresponding
        // pull timestamp should be updated and saved, other fields should be unaffected
        assertProtoArrayEquals(mVoiceCallSessions, voiceCallSession1);
        assertProtoArrayEquals(new VoiceCallSession[0], voiceCallSession2);
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
                getAtomsWritten(inOrder).rawVoiceCallRatUsagePullTimestampMillis);
        inOrder.verifyNoMoreInteractions();
    }

    private void createEmptyTestFile() throws Exception {
        PersistAtoms atoms = new PersistAtoms();
        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write(PersistAtoms.toByteArray(atoms));
        stream.close();
    }

    private void createTestFile(long lastPullTimeMillis) throws Exception {
        PersistAtoms atoms = new PersistAtoms();
        atoms.rawVoiceCallRatUsagePullTimestampMillis = lastPullTimeMillis;
        atoms.voiceCallSessionPullTimestampMillis = lastPullTimeMillis;
        atoms.rawVoiceCallRatUsage = mVoiceCallRatUsages;
        atoms.voiceCallSession = mVoiceCallSessions;
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

    private static RawVoiceCallRatUsage[] multiplyVoiceCallRatUsage(
            RawVoiceCallRatUsage[] usages, int times) {
        RawVoiceCallRatUsage[] multipliedUsages = new RawVoiceCallRatUsage[usages.length];
        for (int i = 0; i < usages.length; i++) {
            multipliedUsages[i] = new RawVoiceCallRatUsage();
            multipliedUsages[i].carrierId = usages[i].carrierId;
            multipliedUsages[i].rat = usages[i].rat;
            multipliedUsages[i].callCount = usages[i].callCount * 2;
            multipliedUsages[i].totalDurationMillis = usages[i].totalDurationMillis * 2;
        }
        return multipliedUsages;
    }

    private static void assertProtoArrayEquals(MessageNano[] expected, MessageNano[] actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(
                    String.format(
                            "Message %d of %d differs:\n=== expected ===\n%s=== got ===\n%s",
                            i + 1, expected.length, expected[i].toString(), actual[i].toString()),
                    MessageNano.messageNanoEquals(expected[i], actual[i]));
        }
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
}
