/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.AccessNetworksManager.QualifiedNetworks;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TransportManagerTest extends TelephonyTest {
    private static final int EVENT_HANDOVER_NEEDED = 1;

    @Mock
    private Handler mTestHandler;

    private TransportManager mTransportManager;

    private TransportManagerTestHandler mTransportManagerTestHandler;

    private class TransportManagerTestHandler extends HandlerThread {

        private TransportManagerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mTransportManager = new TransportManager(mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTransportManagerTestHandler = new TransportManagerTestHandler(TAG);
        mTransportManagerTestHandler.start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mTransportManagerTestHandler.quit();
        mTransportManagerTestHandler.join();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testHandoverNeeded() throws Exception {
        mTransportManager.registerForHandoverNeededEvent(mTestHandler, EVENT_HANDOVER_NEEDED);

        // Initial qualified networks
        List<QualifiedNetworks> networkList = new ArrayList<>(Arrays.asList(
                new QualifiedNetworks(ApnSetting.TYPE_IMS,
                        new int[]{AccessNetworkType.EUTRAN, AccessNetworkType.UTRAN,
                                AccessNetworkType.IWLAN})));
        mTransportManager.obtainMessage(1 /* EVENT_QUALIFIED_NETWORKS_CHANGED */,
                new AsyncResult(null, networkList, null)).sendToTarget();
        waitForMs(100);
        // Verify handover needed event was not sent
        verify(mTestHandler, never()).sendMessageAtTime(any(Message.class), anyLong());

        // Now change the order of qualified networks by putting IWLAN first
        networkList = new ArrayList<>(Arrays.asList(
                new QualifiedNetworks(ApnSetting.TYPE_IMS,
                        new int[]{AccessNetworkType.IWLAN, AccessNetworkType.UTRAN,
                                AccessNetworkType.EUTRAN})));
        mTransportManager.obtainMessage(1 /* EVENT_QUALIFIED_NETWORKS_CHANGED */,
                new AsyncResult(null, networkList, null)).sendToTarget();
        waitForMs(100);

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        // Verify handover needed event was sent and the the target transport is WLAN.
        verify(mTestHandler, times(1)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        Message message = messageArgumentCaptor.getValue();
        assertEquals(EVENT_HANDOVER_NEEDED, message.what);
        AsyncResult ar = (AsyncResult) message.obj;
        HandoverParams params = (HandoverParams) ar.result;
        assertEquals(ApnSetting.TYPE_IMS, params.apnType);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, params.targetTransport);

        // Now change the order of qualified networks by putting UTRAN first
        networkList = new ArrayList<>(Arrays.asList(
                new QualifiedNetworks(ApnSetting.TYPE_IMS,
                        new int[]{AccessNetworkType.UTRAN, AccessNetworkType.EUTRAN,
                                AccessNetworkType.IWLAN})));
        mTransportManager.obtainMessage(1 /* EVENT_QUALIFIED_NETWORKS_CHANGED */,
                new AsyncResult(null, networkList, null)).sendToTarget();
        waitForMs(100);

        messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        // Verify handover needed event was sent and the the target transport is WWAN.
        verify(mTestHandler, times(2)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        message = messageArgumentCaptor.getValue();
        assertEquals(EVENT_HANDOVER_NEEDED, message.what);
        ar = (AsyncResult) message.obj;
        params = (HandoverParams) ar.result;
        assertEquals(ApnSetting.TYPE_IMS, params.apnType);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, params.targetTransport);
    }

    @Test
    @SmallTest
    public void testHandoverNotNeeded() throws Exception {
        mTransportManager.registerForHandoverNeededEvent(mTestHandler, EVENT_HANDOVER_NEEDED);

        // Initial qualified networks
        List<QualifiedNetworks> networkList = new ArrayList<>(Arrays.asList(
                new QualifiedNetworks(ApnSetting.TYPE_IMS,
                        new int[]{AccessNetworkType.EUTRAN, AccessNetworkType.UTRAN,
                                AccessNetworkType.IWLAN})));
        mTransportManager.obtainMessage(1 /* EVENT_QUALIFIED_NETWORKS_CHANGED */,
                new AsyncResult(null, networkList, null)).sendToTarget();
        waitForMs(100);
        // Verify handover needed event was not sent
        verify(mTestHandler, never()).sendMessageAtTime(any(Message.class), anyLong());

        // Now change the order of qualified networks by swapping EUTRAN and UTRAN.
        networkList = new ArrayList<>(Arrays.asList(
                new QualifiedNetworks(ApnSetting.TYPE_IMS,
                        new int[]{AccessNetworkType.UTRAN, AccessNetworkType.EUTRAN,
                                AccessNetworkType.IWLAN})));
        mTransportManager.obtainMessage(1 /* EVENT_QUALIFIED_NETWORKS_CHANGED */,
                new AsyncResult(null, networkList, null)).sendToTarget();
        waitForMs(100);
        // Verify handover needed event was not sent
        verify(mTestHandler, never()).sendMessageAtTime(any(Message.class), anyLong());
    }
}
