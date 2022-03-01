/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.TelephonyProtoEnums;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;
import android.util.Log;

import com.android.ims.rcs.uce.util.FeatureTags;
import com.android.internal.telephony.TelephonyStatsLog;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.GbaEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsDedicatedBearerEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsDedicatedBearerListenerEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationFeatureTagStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationServiceDescStats;
import com.android.internal.telephony.nano.PersistAtomsProto.PresenceNotifyEvent;
import com.android.internal.telephony.nano.PersistAtomsProto.RcsAcsProvisioningStats;
import com.android.internal.telephony.nano.PersistAtomsProto.RcsClientProvisioningStats;
import com.android.internal.telephony.nano.PersistAtomsProto.SipDelegateStats;
import com.android.internal.telephony.nano.PersistAtomsProto.SipMessageResponse;
import com.android.internal.telephony.nano.PersistAtomsProto.SipTransportFeatureTagStats;
import com.android.internal.telephony.nano.PersistAtomsProto.SipTransportSession;
import com.android.internal.telephony.nano.PersistAtomsProto.UceEventStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class RcsStatsTest extends TelephonyTest {
    private static final String TAG = RcsStatsTest.class.getSimpleName();

    private static final long START_TIME_MILLIS = 2000L;
    private static final int SLOT_ID = 0;
    private static final int SLOT2_ID = 1;
    private static final int INVALID_SLOT_ID = -1;
    private static final int CARRIER_ID = 100;
    private static final int CARRIER2_ID = 200;
    private static final int INVALID_CARRIER_ID = -1;
    private static final int INVALID_SUB_ID = Integer.MIN_VALUE;

    private class TestResult {
        public String tagName;
        public int tagValue;
        public long duration;
        public int deniedReason;
        public int deregiReason;
        TestResult(String tagName, int tagValue, long duration,
                int deniedReason, int deregiReason) {
            this.tagName = tagName;
            this.tagValue = tagValue;
            this.duration = duration;
            this.deniedReason = deniedReason;
            this.deregiReason = deregiReason;
        }
    }

    private final int mSubId = 10;
    private final int mSubId2 = 20;

    private TestableRcsStats mRcsStats;

    private class TestableRcsStats extends RcsStats {
        private long mTimeMillis = START_TIME_MILLIS;
        private boolean mEnabledInvalidSubId = false;

        TestableRcsStats() {
            super();
        }

        @Override
        protected int getSlotId(int subId) {
            if (mEnabledInvalidSubId) {
                return INVALID_SLOT_ID;
            }

            if (subId == mSubId) {
                return SLOT_ID;
            } else if (subId == mSubId2) {
                return SLOT2_ID;
            }
            return SLOT2_ID;
        }

        @Override
        protected int getCarrierId(int subId) {
            if (mEnabledInvalidSubId) {
                return INVALID_CARRIER_ID;
            }

            if (subId == mSubId) {
                return CARRIER_ID;
            } else if (subId == mSubId2) {
                return CARRIER2_ID;
            }
            return INVALID_CARRIER_ID;
        }

        @Override
        protected boolean isValidCarrierId(int carrierId) {
            if (carrierId == INVALID_CARRIER_ID) {
                return false;
            }
            return true;
        }

        @Override
        protected long getWallTimeMillis() {
            // NOTE: super class constructor will be executed before private field is set, which
            // gives the wrong start time (mTimeMillis will have its default value of 0L)
            Log.d(TAG, "getWallTimeMillis return value : " + mTimeMillis);
            return mTimeMillis == 0L ? START_TIME_MILLIS : mTimeMillis;
        }

        @Override
        protected void logd(String msg) {
            Log.w(TAG, msg);
        }

        @Override
        protected int getSubId(int slotId) {
            if (mEnabledInvalidSubId) {
                return INVALID_SUB_ID;
            }

            if (slotId == SLOT_ID) {
                return mSubId;
            } else if (slotId == SLOT2_ID) {
                return mSubId2;
            }
            return INVALID_SUB_ID;
        }

        public void setEnableInvalidSubId() {
            mEnabledInvalidSubId = true;
        }
        private void setTimeMillis(long timeMillis) {
            mTimeMillis = timeMillis;
        }

        private void incTimeMillis(long timeMillis) {
            mTimeMillis += timeMillis;
            Log.d(TAG, "incTimeMillis   mTimeMillis : " + mTimeMillis);
        }

        public int getRcsAcsProvisioningCachedSize() {
            return mRcsAcsProvisioningStatsList.size();
        }

        public int getImsRegistrationServiceDescCachedSize() {
            return mImsRegistrationServiceDescStatsList.size();
        }

        public long getRcsAcsProvisioningCachedTime(int carreirId, int slotId) {
            for (RcsAcsProvisioningStats stats : mRcsAcsProvisioningStatsList) {
                if (stats.carrierId == carreirId && stats.slotId == slotId) {
                    return stats.stateTimerMillis;
                }
            }
            return 0L;
        }

        public int getRcsProvisioningCallbackMapSize() {
            return mRcsProvisioningCallbackMap.size();
        }

        public ImsDedicatedBearerListenerEvent dedicatedBearerListenerEventMap_get(
                final int listenerId) {
            return mDedicatedBearerListenerEventMap.get(listenerId);
        }

        public boolean dedicatedBearerListenerEventMap_containsKey(final int listenerId) {
            return mDedicatedBearerListenerEventMap.containsKey(listenerId);
        }

        public void dedicatedBearerListenerEventMap_remove(final int listenerId) {
            mDedicatedBearerListenerEventMap.remove(listenerId);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mRcsStats = new TestableRcsStats();
    }

    @After
    public void tearDown() throws Exception {
        mRcsStats = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void onImsRegistrationFeatureTagStats_withAtoms() throws Exception {
        int slotId = SLOT_ID;
        int carrierId = CARRIER_ID;
        List<String> featureTagList = Arrays.asList(
                "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.im\"",
                "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"",
                "+g.3gpp.icsi-ref=\"hh%3Ashin%3A-b.a.b.o\"",
                "+g.gsma.rcs.isbot"
        );

        int registrationTech  = 0;

        mRcsStats.onImsRegistrationFeatureTagStats(
                mSubId, featureTagList, registrationTech);

        mRcsStats.onStoreCompleteImsRegistrationFeatureTagStats(mSubId);

        ArgumentCaptor<ImsRegistrationFeatureTagStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationFeatureTagStats.class);
        verify(mPersistAtomsStorage, times(featureTagList.size()))
                .addImsRegistrationFeatureTagStats(captor.capture());
        List<ImsRegistrationFeatureTagStats> captorValues = captor.getAllValues();

        assertEquals(captorValues.size(), featureTagList.size());
        for (int index = 0; index < captorValues.size(); index++) {
            ImsRegistrationFeatureTagStats stats = captorValues.get(index);
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            assertEquals(mRcsStats.convertTagNameToValue(featureTagList.get(index)),
                    stats.featureTagName);
            assertEquals(registrationTech, stats.registrationTech);
        }
    }

    @Test
    @SmallTest
    public void onRcsClientProvisioningStats_withAtoms() throws Exception {
        /*
         * RCS_CLIENT_PROVISIONING_STATS__EVENT__CLIENT_PARAMS_SENT
         * RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION
         * RCS_CLIENT_PROVISIONING_STATS__EVENT__DMA_CHANGED
         */
        int event =
                TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__CLIENT_PARAMS_SENT;

        mRcsStats.onRcsClientProvisioningStats(mSubId, event);

        ArgumentCaptor<RcsClientProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsClientProvisioningStats.class);
        verify(mPersistAtomsStorage).addRcsClientProvisioningStats(captor.capture());
        RcsClientProvisioningStats stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(event, stats.event);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onRcsAcsProvisioningStats_withAtoms() throws Exception {
        boolean isSingleRegistrationEnabled = true;
        int[] responseCode = {200, 401};
        /*
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PRE_PROVISIONING_XML
         */
        int[] responseType = {
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML,
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR};
        int[] slotIds = {SLOT_ID, SLOT_ID};
        int[] carrierIds = {CARRIER_ID, CARRIER_ID};

        // this will be cached
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId, responseCode[0], responseType[0], isSingleRegistrationEnabled);

        long timeGap = 6000L;
        mRcsStats.incTimeMillis(timeGap);

        // this will be cached, previous will be stored
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId, responseCode[1], responseType[1], isSingleRegistrationEnabled);

        ArgumentCaptor<RcsAcsProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsAcsProvisioningStats.class);
        verify(mPersistAtomsStorage).addRcsAcsProvisioningStats(captor.capture());
        RcsAcsProvisioningStats stats = captor.getValue();
        assertEquals(carrierIds[0], stats.carrierId);
        assertEquals(slotIds[0], stats.slotId);
        assertEquals(responseCode[0], stats.responseCode);
        assertEquals(responseType[0], stats.responseType);
        assertEquals(isSingleRegistrationEnabled, stats.isSingleRegistrationEnabled);
        assertEquals(timeGap, stats.stateTimerMillis);

        // the last atoms will be cached
        assertEquals(1, mRcsStats.getRcsAcsProvisioningCachedSize());
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onRcsAcsProvisioningStats_withAtomsInvalidSubId() throws Exception {
        boolean isSingleRegistrationEnabled = true;
        int[] responseCode = {200, 401};
        int[] responseType = {
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML,
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR};
        int[] slotIds = {SLOT_ID, SLOT_ID};
        int[] carrierIds = {CARRIER_ID, CARRIER_ID};

        // this will be cached
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId, responseCode[0], responseType[0], isSingleRegistrationEnabled);

        long timeGap = 6000L;
        mRcsStats.incTimeMillis(timeGap);

        // slotId and carrierId are invalid based on subId
        mRcsStats.setEnableInvalidSubId();

        // this will not be cached, previous will be stored
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId, responseCode[1], responseType[1], isSingleRegistrationEnabled);

        ArgumentCaptor<RcsAcsProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsAcsProvisioningStats.class);
        verify(mPersistAtomsStorage).addRcsAcsProvisioningStats(captor.capture());
        RcsAcsProvisioningStats stats = captor.getValue();
        assertEquals(carrierIds[0], stats.carrierId);
        assertEquals(slotIds[0], stats.slotId);
        assertEquals(responseCode[0], stats.responseCode);
        assertEquals(responseType[0], stats.responseType);
        assertEquals(isSingleRegistrationEnabled, stats.isSingleRegistrationEnabled);
        assertEquals(timeGap, stats.stateTimerMillis);
        // the last atoms will not be cached
        assertEquals(0, mRcsStats.getRcsAcsProvisioningCachedSize());

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onRcsAcsProvisioningStats_byCallBack() throws Exception {
        long timeGap = 6000L;
        boolean isSingleRegistrationEnabled = true;
        int responseCode = 200;
        int responseType =
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PRE_PROVISIONING_XML;
        byte[] config = new byte[0];

        RcsStats.RcsProvisioningCallback rcsProvisioningCallback =
                mRcsStats.getRcsProvisioningCallback(mSubId, isSingleRegistrationEnabled);
        // has one callback obj
        assertEquals(mRcsStats.getRcsProvisioningCallbackMapSize(), 1);

        rcsProvisioningCallback.onPreProvisioningReceived(config);
        mRcsStats.incTimeMillis(timeGap);
        rcsProvisioningCallback.onRemoved();
        // callback will be removed, Map is empty.
        assertEquals(mRcsStats.getRcsProvisioningCallbackMapSize(), 0);

        ArgumentCaptor<RcsAcsProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsAcsProvisioningStats.class);
        verify(mPersistAtomsStorage).addRcsAcsProvisioningStats(captor.capture());
        RcsAcsProvisioningStats stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(responseCode, stats.responseCode);
        assertEquals(responseType, stats.responseType);
        assertEquals(isSingleRegistrationEnabled, stats.isSingleRegistrationEnabled);
        assertEquals(timeGap, stats.stateTimerMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onRcsAcsProvisioningStats_byErrorCallBack() throws Exception {
        long timeGap = 6000L;
        boolean isSingleRegistrationEnabled = true;
        int responseCode = 401;
        int responseType =
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR;

        RcsStats.RcsProvisioningCallback rcsProvisioningCallback =
                mRcsStats.getRcsProvisioningCallback(mSubId, false);
        rcsProvisioningCallback =
                mRcsStats.getRcsProvisioningCallback(mSubId2, isSingleRegistrationEnabled);
        // has two callback obj, subId, subId2
        assertEquals(mRcsStats.getRcsProvisioningCallbackMapSize(), 2);

        rcsProvisioningCallback.onAutoConfigurationErrorReceived(responseCode, "responseCode");
        mRcsStats.incTimeMillis(timeGap);
        mRcsStats.onStoreCompleteRcsAcsProvisioningStats(mSubId2);
        rcsProvisioningCallback.onRemoved();
        // subId2's callback will be removed, Map has only one callback for subId.
        assertEquals(mRcsStats.getRcsProvisioningCallbackMapSize(), 1);

        // addRcsAcsProvisioningStats is called once.
        ArgumentCaptor<RcsAcsProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsAcsProvisioningStats.class);
        verify(mPersistAtomsStorage).addRcsAcsProvisioningStats(captor.capture());
        RcsAcsProvisioningStats stats = captor.getValue();
        assertEquals(CARRIER2_ID, stats.carrierId);
        assertEquals(SLOT2_ID, stats.slotId);
        assertEquals(responseCode, stats.responseCode);
        assertEquals(responseType, stats.responseType);
        assertEquals(isSingleRegistrationEnabled, stats.isSingleRegistrationEnabled);
        assertEquals(timeGap, stats.stateTimerMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onStoreCompleteRcsAcsProvisioningStats_withSubId() throws Exception {
        boolean isSingleRegistrationEnabled = true;
        int[] responseCode = {401, 200};
        /*
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PRE_PROVISIONING_XML
         */
        int[] responseType = {TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR,
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML};
        int[] slotIds = {SLOT_ID, SLOT2_ID};
        int[] carrierIds = {CARRIER_ID, CARRIER2_ID};

        // this will be cached
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId, responseCode[0], responseType[0], isSingleRegistrationEnabled);
        // this will be cached
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId2, responseCode[1], responseType[1], isSingleRegistrationEnabled);

        long timeGap = 6000L;
        mRcsStats.incTimeMillis(timeGap);

        // cached atoms will be stored and removed
        mRcsStats.onStoreCompleteRcsAcsProvisioningStats(mSubId);
        mRcsStats.onStoreCompleteRcsAcsProvisioningStats(mSubId2);

        ArgumentCaptor<RcsAcsProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsAcsProvisioningStats.class);
        verify(mPersistAtomsStorage, times(slotIds.length))
                .addRcsAcsProvisioningStats(captor.capture());
        List<RcsAcsProvisioningStats> statsList = captor.getAllValues();
        assertEquals(slotIds.length, statsList.size());
        for (int i = 0; i < statsList.size(); i++) {
            RcsAcsProvisioningStats stats = statsList.get(i);
            assertEquals(carrierIds[i], stats.carrierId);
            assertEquals(slotIds[i], stats.slotId);
            assertEquals(responseCode[i], stats.responseCode);
            assertEquals(responseType[i], stats.responseType);
            assertEquals(isSingleRegistrationEnabled, stats.isSingleRegistrationEnabled);
            assertEquals(timeGap, stats.stateTimerMillis);
        }
        // cached data should be empty
        assertEquals(0, mRcsStats.getRcsAcsProvisioningCachedSize());
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onFlushIncompleteRcsAcsProvisioningStats_withoutSubId() throws Exception {
        boolean isSingleRegistrationEnabled = true;
        int[] responseCode = {401, 200};
        /*
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML
         * RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PRE_PROVISIONING_XML
         */
        int[] responseType = {TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__ERROR,
                TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML};
        int[] slotIds = {SLOT_ID, SLOT2_ID};
        int[] carrierIds = {CARRIER_ID, CARRIER2_ID};

        // this will be cached
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId, responseCode[0], responseType[0], isSingleRegistrationEnabled);
        // this will be cached
        mRcsStats.onRcsAcsProvisioningStats(
                mSubId2, responseCode[1], responseType[1], isSingleRegistrationEnabled);

        long timeGap = 6000L;
        mRcsStats.incTimeMillis(timeGap);

        // cached atoms will be stored, but atoms are keeped
        mRcsStats.onFlushIncompleteRcsAcsProvisioningStats();

        ArgumentCaptor<RcsAcsProvisioningStats> captor =
                ArgumentCaptor.forClass(RcsAcsProvisioningStats.class);
        verify(mPersistAtomsStorage, times(slotIds.length))
                .addRcsAcsProvisioningStats(captor.capture());
        List<RcsAcsProvisioningStats> statsList = captor.getAllValues();
        assertEquals(slotIds.length, statsList.size());
        for (int i = 0; i < statsList.size(); i++) {
            RcsAcsProvisioningStats stats = statsList.get(i);
            assertEquals(carrierIds[i], stats.carrierId);
            assertEquals(slotIds[i], stats.slotId);
            assertEquals(responseCode[i], stats.responseCode);
            assertEquals(responseType[i], stats.responseType);
            assertEquals(isSingleRegistrationEnabled, stats.isSingleRegistrationEnabled);
            assertEquals(timeGap, stats.stateTimerMillis);

            // check cached atom's time should be updated
            assertEquals(mRcsStats.getWallTimeMillis(),
                    mRcsStats.getRcsAcsProvisioningCachedTime(carrierIds[i], slotIds[i]));
        }
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onSipDelegateStats_addStats() throws Exception {
        final int destroyReason = SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD;
        final long timeGap = 6000L;
        List<Set<String>> supportedTagsList = getSupportedTagsList();
        Set<String> registeredTags = supportedTagsList.get(0);
        // create and destroy a sipDelegate..
        mRcsStats.createSipDelegateStats(mSubId, registeredTags);
        mRcsStats.incTimeMillis(timeGap);
        mRcsStats.onSipDelegateStats(mSubId, registeredTags,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);

        ArgumentCaptor<SipDelegateStats> captor =
                ArgumentCaptor.forClass(SipDelegateStats.class);
        verify(mPersistAtomsStorage).addSipDelegateStats(captor.capture());
        SipDelegateStats stats = captor.getValue();
        assertTrue(stats.dimension != 0);
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(timeGap, stats.uptimeMillis);
        assertEquals(destroyReason, stats.destroyReason);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    private List<Set<String>> getSupportedTagsList() {
        List<Set<String>> registeredTagsList = new ArrayList<>();
        Set<String> supportedTags1 = new ArraySet<>();
        supportedTags1.add(FeatureTags.FEATURE_TAG_STANDALONE_MSG);
        supportedTags1.add(FeatureTags.FEATURE_TAG_CHAT_SESSION);
        registeredTagsList.add(supportedTags1);

        Set<String> supportedTags2 = new ArraySet<>();
        supportedTags2.add(FeatureTags.FEATURE_TAG_FILE_TRANSFER);
        supportedTags2.add(FeatureTags.FEATURE_TAG_CHAT_IM);
        supportedTags2.add(FeatureTags.FEATURE_TAG_CALL_COMPOSER_ENRICHED_CALLING);
        registeredTagsList.add(supportedTags2);

        Set<String> supportedTags3 = new ArraySet<>();
        supportedTags3.add(FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_SESSION);
        supportedTags3.add(FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_STANDALONE_MSG);
        supportedTags3.add(FeatureTags.FEATURE_TAG_CHATBOT_VERSION_SUPPORTED);
        registeredTagsList.add(supportedTags3);

        return registeredTagsList;
    }

    @Test
    @SmallTest
    public void onSipDelegateStats_addMultipleEntries() throws Exception {
        final long timeGap = 6000L;
        List<Integer> destroyReasonList = new ArrayList<>();
        destroyReasonList.add(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_UNKNOWN);
        destroyReasonList.add(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        destroyReasonList.add(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        final int testSize = destroyReasonList.size();
        List<Set<String>> supportedTagsList = getSupportedTagsList();

        // create and destroy a sipDelegate multiple times
        for (int i = 0; i < testSize; i++) {
            mRcsStats.createSipDelegateStats(mSubId, supportedTagsList.get(i));
        }

        for (int i = 0; i < testSize; i++) {
            mRcsStats.incTimeMillis(timeGap);
            mRcsStats.onSipDelegateStats(mSubId, supportedTagsList.get(i),
                    destroyReasonList.get(i));
        }

        List<ExpectedSipDelegateResult> expectedSipDelegateResults =
                getExpectedResult(destroyReasonList);
        final int expectedResultSize = expectedSipDelegateResults.size();
        ArgumentCaptor<SipDelegateStats> captor =
                ArgumentCaptor.forClass(SipDelegateStats.class);
        verify(mPersistAtomsStorage, times(expectedResultSize))
                .addSipDelegateStats(captor.capture());

        List<SipDelegateStats> captorValues = captor.getAllValues();
        assertEquals(captorValues.size(), expectedResultSize);
        for (int i = 0; i < expectedResultSize; i++) {
            SipDelegateStats stats = captorValues.get(i);
            ExpectedSipDelegateResult expectedResult = expectedSipDelegateResults.get(i);
            assertTrue(stats.dimension != 0);
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            assertEquals(timeGap * (i + 1), stats.uptimeMillis);
            assertEquals(expectedResult.destroyReason, stats.destroyReason);
        }
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    private class ExpectedSipDelegateResult {
        public int id;
        public int destroyReason;
        ExpectedSipDelegateResult(int id, int destroyReason) {
            this.id = id;
            this.destroyReason = destroyReason;
        }
    }

    private List<ExpectedSipDelegateResult> getExpectedResult(List<Integer> destroyReasonList) {
        List<ExpectedSipDelegateResult> results = new ArrayList<>();
        int size = destroyReasonList.size();

        for (int i = 0; i < size; i++) {
            results.add(new ExpectedSipDelegateResult(i, destroyReasonList.get(i)));
        }

        return results;
    }

    @Test
    @SmallTest
    public void onSipTransportFeatureTagStats_addMultipleEntries() throws Exception {
        final long timeGap = 6000L;
        Set<FeatureTagState> deniedTags = new ArraySet<>();
        Set<FeatureTagState> deRegiTags = new ArraySet<>();
        Set<String> regiTags = new ArraySet<>();

        // create new featureTags
        regiTags.add(FeatureTags.FEATURE_TAG_STANDALONE_MSG);
        deniedTags.add(new FeatureTagState(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE));
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);

        mRcsStats.incTimeMillis(timeGap);

        // change status of featureTags
        regiTags.clear();
        deRegiTags.add(new FeatureTagState(FeatureTags.FEATURE_TAG_STANDALONE_MSG,
                DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED));
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);

        mRcsStats.incTimeMillis(timeGap);

        List<TestResult> expectedResults = getTestResult(timeGap, false);

        int expectedResultSize = expectedResults.size();
        ArgumentCaptor<SipTransportFeatureTagStats> captor =
                ArgumentCaptor.forClass(SipTransportFeatureTagStats.class);
        verify(mPersistAtomsStorage, times(expectedResultSize))
                .addSipTransportFeatureTagStats(captor.capture());

        List<SipTransportFeatureTagStats> captorValues = captor.getAllValues();

        assertEquals(captorValues.size(), expectedResultSize);
        for (int i = 0; i < captorValues.size(); i++) {
            SipTransportFeatureTagStats stats = captorValues.get(i);
            TestResult expectedResult = expectedResults.get(i);
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            assertEquals(expectedResult.tagValue, stats.featureTagName);
            assertEquals(expectedResult.duration, stats.associatedMillis);
            assertEquals(expectedResult.deniedReason, stats.sipTransportDeniedReason);
            assertEquals(expectedResult.deregiReason, stats.sipTransportDeregisteredReason);
        }
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onSipTransportFeatureTagStats_addInvalidEntries() throws Exception {
        final long timeGap = 6000L;
        Set<FeatureTagState> deniedTags = new ArraySet<>();
        Set<FeatureTagState> deRegiTags = new ArraySet<>();
        Set<String> regiTags = new ArraySet<>();

        final int invalidSubId = INVALID_SUB_ID;

        // create new featureTags with an invalidId
        regiTags.add(FeatureTags.FEATURE_TAG_STANDALONE_MSG);
        deniedTags.add(new FeatureTagState(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE));
        mRcsStats.onSipTransportFeatureTagStats(invalidSubId, deniedTags, deRegiTags, regiTags);
        mRcsStats.incTimeMillis(timeGap);

        // change status of featureTags with an invalidId
        regiTags.clear();
        deRegiTags.add(new FeatureTagState(FeatureTags.FEATURE_TAG_STANDALONE_MSG,
                DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED));
        mRcsStats.onSipTransportFeatureTagStats(invalidSubId, deniedTags, deRegiTags, regiTags);
        mRcsStats.incTimeMillis(timeGap);

        verify(mPersistAtomsStorage, never()).addSipTransportFeatureTagStats(any());
    }


    @Test
    @SmallTest
    public void onSipTransportFeatureTagStats_addCustomTag() throws Exception {
        final long timeGap = 6000L;
        Set<FeatureTagState> deniedTags = new ArraySet<>();
        Set<FeatureTagState> deRegiTags = new ArraySet<>();
        Set<String> regiTags = new ArraySet<>();

        // create new featureTags
        String customTag = "custom@tag";
        regiTags.add(customTag);
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);

        mRcsStats.incTimeMillis(timeGap);

        // change status of featureTags
        regiTags.clear();
        deRegiTags.add(new FeatureTagState(customTag,
                DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED));
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);

        mRcsStats.incTimeMillis(timeGap);

        TestResult expectedResult = new TestResult(customTag,
                TelephonyProtoEnums.IMS_FEATURE_TAG_CUSTOM, timeGap, RcsStats.NONE, RcsStats.NONE);

        ArgumentCaptor<SipTransportFeatureTagStats> captor =
                ArgumentCaptor.forClass(SipTransportFeatureTagStats.class);

        verify(mPersistAtomsStorage).addSipTransportFeatureTagStats(captor.capture());
        SipTransportFeatureTagStats stats = captor.getValue();

        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(expectedResult.tagValue, stats.featureTagName);
        assertEquals(expectedResult.duration, stats.associatedMillis);
        assertEquals(expectedResult.deniedReason, stats.sipTransportDeniedReason);
        assertEquals(expectedResult.deregiReason, stats.sipTransportDeregisteredReason);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void concludeSipTransportFeatureTagsStat_addMultipleEntries() throws Exception {
        final long timeGap = 6000L;
        Set<FeatureTagState> deniedTags = new ArraySet<>();
        Set<FeatureTagState> deRegiTags = new ArraySet<>();
        Set<String> regiTags = new ArraySet<>();
        // create new featureTags
        regiTags.add(FeatureTags.FEATURE_TAG_STANDALONE_MSG);
        deniedTags.add(new FeatureTagState(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE));
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);

        mRcsStats.incTimeMillis(timeGap);

        // change status of featureTags
        regiTags.clear();
        deRegiTags.add(new FeatureTagState(FeatureTags.FEATURE_TAG_STANDALONE_MSG,
                DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED));
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);


        mRcsStats.incTimeMillis(timeGap);

        // change status of featureTags and metrics are pulled.
        deRegiTags.clear();
        regiTags.add(FeatureTags.FEATURE_TAG_STANDALONE_MSG);
        mRcsStats.onSipTransportFeatureTagStats(mSubId, deniedTags, deRegiTags, regiTags);

        mRcsStats.incTimeMillis(timeGap);
        mRcsStats.concludeSipTransportFeatureTagsStat();

        List<TestResult> expectedResults = getTestResult(timeGap, true);

        int expectedResultSize = expectedResults.size();
        ArgumentCaptor<SipTransportFeatureTagStats> captor =
                ArgumentCaptor.forClass(SipTransportFeatureTagStats.class);
        verify(mPersistAtomsStorage, times(expectedResultSize))
                .addSipTransportFeatureTagStats(captor.capture());

        List<SipTransportFeatureTagStats> captorValues = captor.getAllValues();

        assertEquals(captorValues.size(), expectedResultSize);
        for (int i = 0; i < captorValues.size(); i++) {
            SipTransportFeatureTagStats stats = captorValues.get(i);
            TestResult expectedResult = expectedResults.get(i);
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            assertEquals(expectedResult.tagValue, stats.featureTagName);
            assertEquals(expectedResult.duration, stats.associatedMillis);
            assertEquals(expectedResult.deniedReason, stats.sipTransportDeniedReason);
            assertEquals(expectedResult.deregiReason, stats.sipTransportDeregisteredReason);
        }
        verifyNoMoreInteractions(mPersistAtomsStorage);

    }

    private List<TestResult> getTestResult(long timeGap, boolean concludeTest) {
        List<TestResult> results = new ArrayList<>();
        results.add(new TestResult(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                TelephonyProtoEnums.IMS_FEATURE_TAG_FILE_TRANSFER,
                timeGap,
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE, RcsStats.NONE));
        results.add(new TestResult(FeatureTags.FEATURE_TAG_STANDALONE_MSG,
                TelephonyProtoEnums.IMS_FEATURE_TAG_STANDALONE_MSG,
                timeGap, RcsStats.NONE, RcsStats.NONE));
        if (concludeTest) {
            results.add(new TestResult(FeatureTags.FEATURE_TAG_STANDALONE_MSG,
                    TelephonyProtoEnums.IMS_FEATURE_TAG_STANDALONE_MSG,
                    timeGap, RcsStats.NONE,
                    DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED));
            results.add(new TestResult(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                    TelephonyProtoEnums.IMS_FEATURE_TAG_FILE_TRANSFER,
                    timeGap,
                    SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE, RcsStats.NONE));
            results.add(new TestResult(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                    TelephonyProtoEnums.IMS_FEATURE_TAG_FILE_TRANSFER,
                    timeGap,
                    SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE, RcsStats.NONE));
            results.add(new TestResult(FeatureTags.FEATURE_TAG_STANDALONE_MSG,
                    TelephonyProtoEnums.IMS_FEATURE_TAG_STANDALONE_MSG,
                    timeGap, RcsStats.NONE, RcsStats.NONE));

        }
        return results;
    }

    @Test
    @SmallTest
    public void onSipMessageResponse_withAtoms() throws Exception {
        String testSipMessageMethod = "MESSAGE";
        int testSipRequestMessageDirection = 1; //INCOMING: 0, OUTGOING: 1
        int testSipMessageResponse = 200;
        int testMessageError = 0;
        String testCallId = "testId";
        // Request message
        mRcsStats.onSipMessageRequest(testCallId, testSipMessageMethod,
                testSipRequestMessageDirection);
        // Response message
        mRcsStats.onSipMessageResponse(mSubId, testCallId, testSipMessageResponse,
                testMessageError);
        ArgumentCaptor<SipMessageResponse> captor =
                ArgumentCaptor.forClass(SipMessageResponse.class);
        verify(mPersistAtomsStorage).addSipMessageResponse(captor.capture());
        SipMessageResponse stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(TelephonyProtoEnums.SIP_REQUEST_MESSAGE, stats.sipMessageMethod);
        assertEquals(testSipRequestMessageDirection, stats.sipMessageDirection);
        assertEquals(testSipMessageResponse, stats.sipMessageResponse);
        assertEquals(testMessageError, stats.messageError);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onSipTransportSession_withAtoms() throws Exception {
        String testInviteSipMethod = "INVITE";
        String testCallId = "testId";
        int testSipResponse = 0;
        int testSipRequestMessageDirection = 1; //INCOMING: 0, OUTGOING: 1
        // Request Message
        mRcsStats.earlySipTransportSession(
                testInviteSipMethod, testCallId, testSipRequestMessageDirection);
        // gracefully close
        mRcsStats.onSipTransportSessionClosed(mSubId, testCallId, testSipResponse, true);
        ArgumentCaptor<SipTransportSession> captor =
                ArgumentCaptor.forClass(SipTransportSession.class);
        verify(mPersistAtomsStorage).addCompleteSipTransportSession(captor.capture());
        SipTransportSession stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(TelephonyProtoEnums.SIP_REQUEST_INVITE, stats.sessionMethod);
        assertEquals(testSipRequestMessageDirection, stats.sipMessageDirection);
        assertEquals(testSipResponse, stats.sipResponse);
        assertEquals(true/*isEndedGracefully*/, stats.isEndedGracefully);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsDedicatedBearerListenerEvent_Added() throws Exception {
        final int listenerId = 1;
        int ratAtEnd = TelephonyProtoEnums.NETWORK_TYPE_LTE;
        final int qci = 5;

        mRcsStats.dedicatedBearerListenerEventMap_remove(listenerId);
        mRcsStats.onImsDedicatedBearerListenerAdded(listenerId, SLOT_ID, ratAtEnd, qci);
        assertTrue(mRcsStats.dedicatedBearerListenerEventMap_containsKey(listenerId));
        ImsDedicatedBearerListenerEvent testProto =
                mRcsStats.dedicatedBearerListenerEventMap_get(listenerId);
        assertEquals(SLOT_ID, testProto.slotId);
        assertEquals(ratAtEnd, testProto.ratAtEnd);
        assertEquals(qci, testProto.qci);
        assertFalse(testProto.dedicatedBearerEstablished);
        verify(mPersistAtomsStorage, never()).addImsDedicatedBearerListenerEvent(any());

        // same listenerId, different contents. should be ignored
        ratAtEnd = TelephonyProtoEnums.NETWORK_TYPE_NR;
        mRcsStats.onImsDedicatedBearerListenerAdded(listenerId, SLOT_ID + 1, ratAtEnd + 1, qci + 1);
        testProto = mRcsStats.dedicatedBearerListenerEventMap_get(listenerId);
        assertEquals(SLOT_ID, testProto.slotId);
        assertNotEquals(ratAtEnd, testProto.ratAtEnd);
        assertEquals(qci, testProto.qci);
        verify(mPersistAtomsStorage, never()).addImsDedicatedBearerListenerEvent(any());

        mRcsStats.dedicatedBearerListenerEventMap_remove(listenerId);
    }

    @Test
    @SmallTest
    public void onImsDedicatedBearerListenerEvent_bearerEstablished() throws Exception {
        final int listenerId = 2;
        final int rat = TelephonyProtoEnums.NETWORK_TYPE_LTE;
        final int qci = 6;

        mRcsStats.dedicatedBearerListenerEventMap_remove(listenerId);
        mRcsStats.onImsDedicatedBearerListenerUpdateSession(listenerId, SLOT_ID, rat, qci, true);
        verify(mPersistAtomsStorage, never()).addImsDedicatedBearerListenerEvent(any());

        mRcsStats.dedicatedBearerListenerEventMap_remove(listenerId);
        mRcsStats.onImsDedicatedBearerListenerAdded(listenerId, SLOT_ID, rat, qci);
        assertTrue(mRcsStats.dedicatedBearerListenerEventMap_containsKey(listenerId));
        mRcsStats.onImsDedicatedBearerListenerUpdateSession(listenerId, SLOT_ID, rat, qci, true);
        ImsDedicatedBearerListenerEvent testProto =
                mRcsStats.dedicatedBearerListenerEventMap_get(listenerId);
        assertEquals(qci, testProto.qci);
        assertTrue(testProto.dedicatedBearerEstablished);

        verify(mPersistAtomsStorage, never()).addImsDedicatedBearerListenerEvent(any());
    }

    @Test
    @SmallTest
    public void onImsDedicatedBearerListenerEvent_Removed() throws Exception {
        final int listenerId = 3;
        final int rat = TelephonyProtoEnums.NETWORK_TYPE_LTE;
        final int qci = 7;

        mRcsStats.dedicatedBearerListenerEventMap_remove(listenerId);
        mRcsStats.onImsDedicatedBearerListenerRemoved(listenerId);
        verify(mPersistAtomsStorage, never()).addImsDedicatedBearerListenerEvent(any());

        mRcsStats.onImsDedicatedBearerListenerAdded(listenerId, SLOT_ID, rat, qci);
        mRcsStats.onImsDedicatedBearerListenerUpdateSession(listenerId, SLOT_ID, rat, qci, true);
        mRcsStats.onImsDedicatedBearerListenerRemoved(listenerId);
        verify(mPersistAtomsStorage, times(1)).addImsDedicatedBearerListenerEvent(any());

        // and values should be same
        ArgumentCaptor<ImsDedicatedBearerListenerEvent> captor =
                ArgumentCaptor.forClass(ImsDedicatedBearerListenerEvent.class);
        verify(mPersistAtomsStorage).addImsDedicatedBearerListenerEvent(captor.capture());
        ImsDedicatedBearerListenerEvent stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(rat, stats.ratAtEnd);
        assertEquals(qci, stats.qci);
        assertEquals(true, stats.dedicatedBearerEstablished);
        verifyNoMoreInteractions(mPersistAtomsStorage);

        assertFalse(mRcsStats.dedicatedBearerListenerEventMap_containsKey(listenerId));
    }

    @Test
    @SmallTest
    public void onImsDedicatedBearerEvent_withAtoms() throws Exception {
        // reference comments in test_imsDedicatedBearerListenerEvent for canditate value
        int ratAtEnd = TelephonyStatsLog
                .IMS_DEDICATED_BEARER_LISTENER_EVENT__RAT_AT_END__NETWORK_TYPE_LTE_CA;
        int qci = 6;
        /*
         * IMS_DEDICATED_BEARER_EVENT__BEARER_STATE__STATE_ADDED = 1;
         * IMS_DEDICATED_BEARER_EVENT__BEARER_STATE__STATE_MODIFIED = 2;
         * IMS_DEDICATED_BEARER_EVENT__BEARER_STATE__STATE_DELETED = 3;
         */
        int bearerState = TelephonyStatsLog.IMS_DEDICATED_BEARER_EVENT__BEARER_STATE__STATE_ADDED;
        boolean localConnectionInfoReceived = false;
        boolean remoteConnectionInfoReceived = true;
        boolean hasListeners = true;

        mRcsStats.onImsDedicatedBearerEvent(SLOT_ID, ratAtEnd, qci, bearerState,
                localConnectionInfoReceived, remoteConnectionInfoReceived, hasListeners);

        ArgumentCaptor<ImsDedicatedBearerEvent> captor =
                ArgumentCaptor.forClass(ImsDedicatedBearerEvent.class);
        verify(mPersistAtomsStorage).addImsDedicatedBearerEvent(captor.capture());
        ImsDedicatedBearerEvent stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(ratAtEnd, stats.ratAtEnd);
        assertEquals(qci, stats.qci);
        assertEquals(bearerState, stats.bearerState);
        assertEquals(localConnectionInfoReceived, stats.localConnectionInfoReceived);
        assertEquals(remoteConnectionInfoReceived, stats.remoteConnectionInfoReceived);
        assertEquals(hasListeners, stats.hasListeners);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsRegistrationServiceDescStats_withAtoms() throws Exception {
        int registrationTech  = 0; //ImsRegistrationImplBase.REGISTRATION_TECH_LTE
        ArrayList<String> serviceIdList = new ArrayList<>();
        serviceIdList.add("org.openmobilealliance:File-Transfer-HTTP");
        serviceIdList.add("org.openmobilealliance:IM-session");
        serviceIdList.add("Unknown1");
        ArrayList<String> serviceIdVersionList = new ArrayList<>();
        serviceIdVersionList.add("1.0");
        serviceIdVersionList.add("1.0");
        serviceIdVersionList.add("3.0");

        mRcsStats.onImsRegistrationServiceDescStats(mSubId, serviceIdList, serviceIdVersionList,
                registrationTech);

        // getWallTimeMillis
        /*
         * UCE_EVENT__TYPE__PUBLISH = 0;
         * UCE_EVENT__TYPE__SUBSCRIBE = 1;
         * UCE_EVENT__TYPE__INCOMING_OPTION = 2;
         * UCE_EVENT__TYPE__OUTGOING_OPTION = 3;
         */
        int type = TelephonyStatsLog.UCE_EVENT_STATS__TYPE__PUBLISH;
        boolean successful = true;
        /*
         * UCE_EVENT__COMMAND_CODE__SERVICE_UNKNOWN = 0;
         * UCE_EVENT__COMMAND_CODE__GENERIC_FAILURE = 1;
         * UCE_EVENT__COMMAND_CODE__INVALID_PARAM = 2;
         * UCE_EVENT__COMMAND_CODE__FETCH_ERROR = 3;
         * UCE_EVENT__COMMAND_CODE__REQUEST_TIMEOUT = 4;
         * UCE_EVENT__COMMAND_CODE__INSUFFICIENT_MEMORY = 5;
         * UCE_EVENT__COMMAND_CODE__LOST_NETWORK_CONNECTION = 6;
         * UCE_EVENT__COMMAND_CODE__NOT_SUPPORTED = 7;
         * UCE_EVENT__COMMAND_CODE__NOT_FOUND = 8;
         * UCE_EVENT__COMMAND_CODE__SERVICE_UNAVAILABLE = 9;
         * UCE_EVENT__COMMAND_CODE__NO_CHANGE = 10;
         */
        int commandCode = TelephonyStatsLog.UCE_EVENT_STATS__COMMAND_CODE__SERVICE_UNAVAILABLE;
        int networkResponse = 200;

        mRcsStats.onUceEventStats(mSubId, type, successful, commandCode, networkResponse);

        {
            ArgumentCaptor<UceEventStats> captor = ArgumentCaptor.forClass(UceEventStats.class);
            verify(mPersistAtomsStorage).addUceEventStats(captor.capture());
            UceEventStats stats = captor.getValue();
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            assertEquals(successful, stats.successful);
            assertEquals(commandCode, stats.commandCode);
            assertEquals(networkResponse, stats.networkResponse);
            verifyNoMoreInteractions(mPersistAtomsStorage);
        }

        long timeGap = 6000L;
        mRcsStats.incTimeMillis(timeGap);

        mRcsStats.onStoreCompleteImsRegistrationServiceDescStats(mSubId);

        ArgumentCaptor<ImsRegistrationServiceDescStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationServiceDescStats.class);
        verify(mPersistAtomsStorage, times(3))
                .addImsRegistrationServiceDescStats(captor.capture());
        List<ImsRegistrationServiceDescStats> captorValues = captor.getAllValues();

        assertEquals(captorValues.size(), serviceIdList.size());

        for (int index = 0; index < captorValues.size(); index++) {
            ImsRegistrationServiceDescStats stats = captorValues.get(index);
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            int serviceId = mRcsStats.convertServiceIdToValue(serviceIdList.get(index));
            assertEquals(serviceId, stats.serviceIdName);
            float serviceVersionFloat = Float.parseFloat(serviceIdVersionList.get(index));
            assertEquals(serviceVersionFloat, stats.serviceIdVersion, 0.1f);
            assertEquals(registrationTech, stats.registrationTech);
            assertEquals(timeGap, stats.publishedMillis);
        }
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsRegistrationServiceDescStats_withAtomsInvalidSubId() throws Exception {
        int registrationTech  = 0; //ImsRegistrationImplBase.REGISTRATION_TECH_LTE
        ArrayList<String> serviceIdList = new ArrayList<>();
        serviceIdList.add("org.openmobilealliance:File-Transfer-HTTP");
        serviceIdList.add("org.openmobilealliance:IM-session");
        serviceIdList.add("Unknown1");
        ArrayList<String> serviceIdVersionList = new ArrayList<>();
        serviceIdVersionList.add("1.0");
        serviceIdVersionList.add("1.0");
        serviceIdVersionList.add("3.0");

        mRcsStats.onImsRegistrationServiceDescStats(mSubId, serviceIdList, serviceIdVersionList,
                registrationTech);

        // getWallTimeMillis
        /*
         * UCE_EVENT__TYPE__PUBLISH = 0;
         * UCE_EVENT__TYPE__SUBSCRIBE = 1;
         * UCE_EVENT__TYPE__INCOMING_OPTION = 2;
         * UCE_EVENT__TYPE__OUTGOING_OPTION = 3;
         */
        int type = TelephonyStatsLog.UCE_EVENT_STATS__TYPE__PUBLISH;
        boolean successful = true;
        /*
         * UCE_EVENT__COMMAND_CODE__SERVICE_UNKNOWN = 0;
         * UCE_EVENT__COMMAND_CODE__GENERIC_FAILURE = 1;
         * UCE_EVENT__COMMAND_CODE__INVALID_PARAM = 2;
         * UCE_EVENT__COMMAND_CODE__FETCH_ERROR = 3;
         * UCE_EVENT__COMMAND_CODE__REQUEST_TIMEOUT = 4;
         * UCE_EVENT__COMMAND_CODE__INSUFFICIENT_MEMORY = 5;
         * UCE_EVENT__COMMAND_CODE__LOST_NETWORK_CONNECTION = 6;
         * UCE_EVENT__COMMAND_CODE__NOT_SUPPORTED = 7;
         * UCE_EVENT__COMMAND_CODE__NOT_FOUND = 8;
         * UCE_EVENT__COMMAND_CODE__SERVICE_UNAVAILABLE = 9;
         * UCE_EVENT__COMMAND_CODE__NO_CHANGE = 10;
         */
        int commandCode = TelephonyStatsLog.UCE_EVENT_STATS__COMMAND_CODE__SERVICE_UNAVAILABLE;
        int networkResponse = 200;
        mRcsStats.onUceEventStats(mSubId, type, successful, commandCode, networkResponse);

        // slotId and carrierId are invalid based on subId
        mRcsStats.setEnableInvalidSubId();
        long timeGap = 6000L;
        mRcsStats.incTimeMillis(timeGap);
        mRcsStats.onUceEventStats(mSubId, type, successful, commandCode, networkResponse);

        ArgumentCaptor<ImsRegistrationServiceDescStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationServiceDescStats.class);
        verify(mPersistAtomsStorage, times(3))
                .addImsRegistrationServiceDescStats(captor.capture());
        List<ImsRegistrationServiceDescStats> captorValues = captor.getAllValues();

        assertEquals(captorValues.size(), serviceIdList.size());

        for (int index = 0; index < captorValues.size(); index++) {
            ImsRegistrationServiceDescStats stats = captorValues.get(index);
            assertEquals(CARRIER_ID, stats.carrierId);
            assertEquals(SLOT_ID, stats.slotId);
            int serviceId = mRcsStats.convertServiceIdToValue(serviceIdList.get(index));
            assertEquals(serviceId, stats.serviceIdName);
            float serviceVersionFloat = Float.parseFloat(serviceIdVersionList.get(index));
            assertEquals(serviceVersionFloat, stats.serviceIdVersion, 0.1f);
            assertEquals(registrationTech, stats.registrationTech);
            assertEquals(timeGap, stats.publishedMillis);
        }
        assertEquals(0, mRcsStats.getImsRegistrationServiceDescCachedSize());
    }

    @Test
    @SmallTest
    public void onUceEventStats_withAtoms() throws Exception {
        int messageType = TelephonyStatsLog.UCE_EVENT_STATS__TYPE__PUBLISH;
        boolean successful = true;
        int commandCode = TelephonyStatsLog.UCE_EVENT_STATS__COMMAND_CODE__REQUEST_TIMEOUT;
        int networkResponse = 408;

        mRcsStats.onUceEventStats(mSubId, messageType, successful, commandCode, networkResponse);

        ArgumentCaptor<UceEventStats> captor = ArgumentCaptor.forClass(UceEventStats.class);
        verify(mPersistAtomsStorage).addUceEventStats(captor.capture());
        UceEventStats stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(successful, stats.successful);
        assertEquals(commandCode, stats.commandCode);
        assertEquals(networkResponse, stats.networkResponse);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onPresenceNotifyEvent_withAtoms() throws Exception {
        String reason = "deactivated";
        boolean contentBodyReceived = true;
        boolean rcsCaps = true;
        boolean mmtelCaps = false;
        boolean noCaps = false;

        mRcsStats.onPresenceNotifyEvent(mSubId, reason, contentBodyReceived,
                rcsCaps, mmtelCaps, noCaps);

        ArgumentCaptor<PresenceNotifyEvent> captor =
                ArgumentCaptor.forClass(PresenceNotifyEvent.class);
        verify(mPersistAtomsStorage).addPresenceNotifyEvent(captor.capture());
        PresenceNotifyEvent stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        int reasonInt = mRcsStats.convertPresenceNotifyReason(reason);
        assertEquals(reasonInt, stats.reason);
        assertEquals(contentBodyReceived, stats.contentBodyReceived);
        assertEquals(1, stats.rcsCapsCount);
        assertEquals(0, stats.mmtelCapsCount);
        assertEquals(0, stats.noCapsCount);
        assertEquals(1, stats.rcsCapsCount);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onGbaEvent_withAtoms() throws Exception {
        boolean successful = false;
        /*
         * GBA_EVENT__FAILED_REASON__UNKNOWN
         * GBA_EVENT__FAILED_REASON__FEATURE_NOT_SUPPORTED
         * GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY
         * GBA_EVENT__FAILED_REASON__NETWORK_FAILURE
         * GBA_EVENT__FAILED_REASON__INCORRECT_NAF_ID
         * GBA_EVENT__FAILED_REASON__SECURITY_PROTOCOL_NOT_SUPPORTED
         */
        int failedReason = TelephonyStatsLog.GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY;

        mRcsStats.onGbaFailureEvent(mSubId, failedReason);

        ArgumentCaptor<GbaEvent> captor = ArgumentCaptor.forClass(GbaEvent.class);
        verify(mPersistAtomsStorage).addGbaEvent(captor.capture());
        GbaEvent stats = captor.getValue();
        assertEquals(CARRIER_ID, stats.carrierId);
        assertEquals(SLOT_ID, stats.slotId);
        assertEquals(successful, stats.successful);
        assertEquals(failedReason, stats.failedReason);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }
}
