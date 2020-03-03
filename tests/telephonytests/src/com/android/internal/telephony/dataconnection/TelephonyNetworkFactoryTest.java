/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static com.android.internal.telephony.NetworkFactory.CMD_CANCEL_REQUEST;
import static com.android.internal.telephony.NetworkFactory.CMD_REQUEST_NETWORK;
import static com.android.internal.telephony.dataconnection.TelephonyNetworkFactory.EVENT_ACTIVE_PHONE_SWITCH;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.RadioConfig;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams.HandoverCallback;
import com.android.telephony.Rlog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TelephonyNetworkFactoryTest extends TelephonyTest {
    private final static String LOG_TAG = "TelephonyNetworkFactoryTest";

    @Mock
    PhoneSwitcher mPhoneSwitcher;
    @Mock
    private RadioConfig mMockRadioConfig;

    @Mock
    private DataConnection mDataConnection;

    private String mTestName = "";

    private final ArrayList<NetworkRequest> mNetworkRequestList = new ArrayList<>();

    private TelephonyNetworkFactory mTelephonyNetworkFactoryUT;
    private int mRequestId = 0;

    private void log(String str) {
        Rlog.d(LOG_TAG + " " + mTestName, str);
    }

    private NetworkRequest makeSubSpecificInternetRequest(int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities()).
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(subId).build());
        NetworkRequest networkRequest = new NetworkRequest(netCap, -1,
                mRequestId++, NetworkRequest.Type.REQUEST);
        mTelephonyNetworkFactoryUT.obtainMessage(CMD_REQUEST_NETWORK, 0, 0, networkRequest)
                .sendToTarget();
        return networkRequest;
    }

    private NetworkRequest makeDefaultInternetRequest() {
        NetworkCapabilities netCap = (new NetworkCapabilities())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        NetworkRequest networkRequest = new NetworkRequest(netCap, -1,
                mRequestId++, NetworkRequest.Type.REQUEST);
        mTelephonyNetworkFactoryUT.obtainMessage(CMD_REQUEST_NETWORK, 0, 0, networkRequest)
                .sendToTarget();
        return networkRequest;
    }

    private NetworkRequest makeSubSpecificMmsRequest(int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities()).
                addCapability(NetworkCapabilities.NET_CAPABILITY_MMS).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(subId).build());
        NetworkRequest networkRequest = new NetworkRequest(netCap, -1,
                mRequestId++, NetworkRequest.Type.REQUEST);
        mTelephonyNetworkFactoryUT.obtainMessage(CMD_REQUEST_NETWORK, 0, 0, networkRequest)
                .sendToTarget();
        return networkRequest;
    }

    private void releaseNetworkRequest(NetworkRequest networkRequest) {
        mTelephonyNetworkFactoryUT.obtainMessage(CMD_CANCEL_REQUEST, 0, 0, networkRequest)
                .sendToTarget();
    }

    private void activatePhoneInPhoneSwitcher(int phoneId, boolean active) {
        doReturn(active).when(mPhoneSwitcher).shouldApplyNetworkRequest(any(), eq(phoneId));
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(EVENT_ACTIVE_PHONE_SWITCH);
    }

    private void activatePhoneInPhoneSwitcher(int phoneId, NetworkRequest nr, boolean active) {
        doReturn(active).when(mPhoneSwitcher).shouldApplyNetworkRequest(eq(nr), eq(phoneId));
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(EVENT_ACTIVE_PHONE_SWITCH);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        replaceInstance(RadioConfig.class, "sRadioConfig", null, mMockRadioConfig);

        mContextFixture.putStringArrayResource(com.android.internal.R.array.networkAttributes,
                new String[]{"wifi,1,1,1,-1,true", "mobile,0,0,0,-1,true",
                        "mobile_mms,2,0,2,60000,true", "mobile_supl,3,0,2,60000,true",
                        "mobile_dun,4,0,2,60000,true", "mobile_hipri,5,0,3,60000,true",
                        "mobile_fota,10,0,2,60000,true", "mobile_ims,11,0,2,60000,true",
                        "mobile_cbs,12,0,2,60000,true", "wifi_p2p,13,1,0,-1,true",
                        "mobile_ia,14,0,2,-1,true", "mobile_emergency,15,0,2,-1,true"});

        doAnswer(invocation -> {
            mNetworkRequestList.add((NetworkRequest) invocation.getArguments()[0]);
            return null;
        }).when(mDcTracker).requestNetwork(any(), anyInt(), any());

        doAnswer(invocation -> {
            mNetworkRequestList.remove((NetworkRequest) invocation.getArguments()[0]);
            return null;
        }).when(mDcTracker).releaseNetwork(any(), anyInt());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void createMockedTelephonyComponents() throws Exception {
        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mPhoneSwitcher);

        mTelephonyNetworkFactoryUT = new TelephonyNetworkFactory(Looper.myLooper(), mPhone);
        verify(mConnectivityManager).registerNetworkProvider(any());
        verify(mPhoneSwitcher).registerForActivePhoneSwitch(any(), anyInt(), any());
    }

    /**
     * Test that phone active changes cause the DcTracker to get poked.
     */
    @FlakyTest
    @Test
    @SmallTest
    public void testActive() throws Exception {
        mTestName = "testActive";
        final int phoneId = 0;
        final int subId = 0;

        createMockedTelephonyComponents();

        doReturn(false).when(mPhoneSwitcher).shouldApplyNetworkRequest(any(), anyInt());
        doReturn(subId).when(mSubscriptionController).getSubIdUsingPhoneId(phoneId);
        // fake onSubscriptionChangedListener being triggered.
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);

        log("addDefaultRequest");
        makeDefaultInternetRequest();
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("setPhoneActive true: phoneId = " + phoneId);

        activatePhoneInPhoneSwitcher(phoneId, true);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        log("makeSubSpecificInternetRequest: subId = " + subId);
        NetworkRequest subSpecificDefault = makeSubSpecificInternetRequest(subId);
        processAllMessages();
        assertEquals(2, mNetworkRequestList.size());

        log("setPhoneActive false: phoneId = " + phoneId);
        activatePhoneInPhoneSwitcher(phoneId, false);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("makeSubSpecificInternetRequest: subId = " + subId);
        NetworkRequest subSpecificMms = makeSubSpecificMmsRequest(subId);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("setPhoneActive true: phoneId = " + phoneId);
        activatePhoneInPhoneSwitcher(phoneId, true);
        processAllMessages();
        assertEquals(3, mNetworkRequestList.size());

        log("releaseNetworkRequest: subSpecificDefault = " + subSpecificDefault);
        releaseNetworkRequest(subSpecificDefault);
        processAllMessages();
        assertEquals(2, mNetworkRequestList.size());

        log("setPhoneActive false: phoneId = " + phoneId);
        activatePhoneInPhoneSwitcher(phoneId, false);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("releaseNetworkRequest: subSpecificMms = " + subSpecificMms);
        releaseNetworkRequest(subSpecificMms);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("setPhoneActive true: phoneId = " + phoneId);
        activatePhoneInPhoneSwitcher(phoneId, true);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());
    }

    /**
     * Test that network request changes cause the DcTracker to get poked.
     */
    @Test
    @SmallTest
    public void testRequests() throws Exception {
        mTestName = "testActive";
        final int numberOfPhones = 2;
        final int phoneId = 0;
        final int altPhoneId = 1;
        final int subId = 0;
        final int altSubId = 1;
        final int unusedSubId = 2;

        createMockedTelephonyComponents();

        doReturn(subId).when(mSubscriptionController).getSubIdUsingPhoneId(phoneId);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        activatePhoneInPhoneSwitcher(phoneId, true);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        makeDefaultInternetRequest();
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        doReturn(altSubId).when(mSubscriptionController).getSubIdUsingPhoneId(altPhoneId);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        activatePhoneInPhoneSwitcher(phoneId, false);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(EVENT_ACTIVE_PHONE_SWITCH);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        NetworkRequest subSpecificMmsRequest = makeSubSpecificMmsRequest(subId);
        activatePhoneInPhoneSwitcher(phoneId, subSpecificMmsRequest, true);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        doReturn(unusedSubId).when(mSubscriptionController).getSubIdUsingPhoneId(phoneId);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        makeSubSpecificInternetRequest(subId);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        doReturn(subId).when(mSubscriptionController).getSubIdUsingPhoneId(phoneId);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);
        processAllMessages();

        activatePhoneInPhoneSwitcher(phoneId, true);
        processAllMessages();
        assertEquals(3, mNetworkRequestList.size());
    }

    /**
     * Test handover when there is no live data connection
     */
    @Test
    @SmallTest
    public void testHandoverNoLiveData() throws Exception {
        createMockedTelephonyComponents();
        doReturn(0).when(mSubscriptionController).getSubIdUsingPhoneId(0);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);

        activatePhoneInPhoneSwitcher(0, true);
        makeDefaultInternetRequest();

        makeSubSpecificMmsRequest(0);
        processAllMessages();

        Field f = TelephonyNetworkFactory.class.getDeclaredField("mInternalHandler");
        f.setAccessible(true);
        Handler h = (Handler) f.get(mTelephonyNetworkFactoryUT);

        HandoverCallback handoverCallback = mock(HandoverCallback.class);

        HandoverParams hp = new HandoverParams(ApnSetting.TYPE_MMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, handoverCallback);
        AsyncResult ar = new AsyncResult(null, hp, null);
        h.sendMessage(h.obtainMessage(5, ar));
        processAllMessages();

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mTransportManager)
                .getCurrentTransport(anyInt());

        hp = new HandoverParams(ApnSetting.TYPE_MMS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                handoverCallback);
        ar = new AsyncResult(null, hp, null);
        h.sendMessage(h.obtainMessage(5, ar));
        processAllMessages();
    }

    /**
     * Test handover when the data connection is being connected.
     */
    @Test
    @SmallTest
    public void testHandoverActivatingData() throws Exception {
        createMockedTelephonyComponents();
        doReturn(0).when(mSubscriptionController).getSubIdUsingPhoneId(0);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);

        activatePhoneInPhoneSwitcher(0, true);
        makeDefaultInternetRequest();

        makeSubSpecificMmsRequest(0);
        processAllMessages();

        Field f = TelephonyNetworkFactory.class.getDeclaredField("mInternalHandler");
        f.setAccessible(true);
        Handler h = (Handler) f.get(mTelephonyNetworkFactoryUT);

        HandoverCallback handoverCallback = mock(HandoverCallback.class);
        Mockito.reset(mDcTracker);
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByApnType(anyString());
        doReturn(false).when(mDataConnection).isActive();

        HandoverParams hp = new HandoverParams(ApnSetting.TYPE_MMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, handoverCallback);
        AsyncResult ar = new AsyncResult(null, hp, null);
        h.sendMessage(h.obtainMessage(5, ar));
        processAllMessages();

        verify(mDcTracker, times(1)).releaseNetwork(any(), eq(1));
        verify(mDcTracker, times(1)).requestNetwork(any(), eq(1), any());
    }

}
