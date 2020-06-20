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

package com.android.internal.telephony.dataconnection;

import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_ADDRESS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_DNS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_GATEWAY;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_IFNAME;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_PCSCF_ADDRESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.DataConnection.UpdateLinkPropertyResult;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DcControllerTest extends TelephonyTest {

    private static final int DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT = 1;
    private static final int EVENT_DATA_STATE_CHANGED = 0x00040007;

    @Mock
    private DataConnection mDc;
    @Mock
    private List<ApnContext> mApnContexts;
    @Mock
    private DataServiceManager mDataServiceManager;

    UpdateLinkPropertyResult mResult;

    private DcController mDcc;

    private IState getCurrentState() {
        try {
            Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
            method.setAccessible(true);
            return (IState) method.invoke(mDcc);
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        doReturn(1).when(mApnContexts).size();
        doReturn(mApnContexts).when(mDc).getApnContexts();

        LinkProperties lp = new LinkProperties();
        mResult = new UpdateLinkPropertyResult(lp);
        doReturn(mResult).when(mDc).updateLinkProperty(any(DataCallResponse.class));
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .when(mDataServiceManager).getTransportType();

        mDcc = DcController.makeDcc(mPhone, mDcTracker, mDataServiceManager,
                new Handler(Looper.myLooper()), "");
        mDcc.start();
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testDataDormant() throws Exception {
        assertEquals("DccDefaultState", getCurrentState().getName());
        ArrayList<DataCallResponse> l = new ArrayList<>();
        DataCallResponse dcResponse = new DataCallResponse.Builder()
                .setCause(0)
                .setSuggestedRetryTime(-1)
                .setId(1)
                .setLinkStatus(DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(FAKE_ADDRESS), 0)))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_DNS)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(
                        Arrays.asList(InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1440)
                .setMtuV6(1440)
                .build();
        l.add(dcResponse);

        mDc.mCid = 1;
        mDcc.addActiveDcByCid(mDc);

        mDcc.sendMessage(EVENT_DATA_STATE_CHANGED, new AsyncResult(null, l, null));
        processAllMessages();

        verify(mDcTracker, times(1)).sendStopNetStatPoll(eq(DctConstants.Activity.DORMANT));
    }
}