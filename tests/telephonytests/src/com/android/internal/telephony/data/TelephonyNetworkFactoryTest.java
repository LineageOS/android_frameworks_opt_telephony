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

package com.android.internal.telephony.data;

import static com.android.internal.telephony.NetworkFactory.CMD_CANCEL_REQUEST;
import static com.android.internal.telephony.NetworkFactory.CMD_REQUEST_NETWORK;

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
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.RadioConfig;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams.HandoverCallback;
import com.android.telephony.Rlog;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TelephonyNetworkFactoryTest extends TelephonyTest {
    private static final String LOG_TAG = "TelephonyNetworkFactoryTest";

    // Mocked classes
    PhoneSwitcher mPhoneSwitcher;
    private RadioConfig mMockRadioConfig;
    private DataConnection mDataConnection;

    private String mTestName = "";

    // List of all requests filed by a test
    private final ArraySet<TelephonyNetworkRequest> mAllNetworkRequestSet = new ArraySet<>();
    // List of requests active in DcTracker
    private final ArrayList<TelephonyNetworkRequest> mNetworkRequestList = new ArrayList<>();
    // List of complete messages associated with the network requests
    private final Map<TelephonyNetworkRequest, Message> mNetworkRequestMessageMap = new HashMap<>();

    private TelephonyNetworkFactory mTelephonyNetworkFactoryUT;
    private int mRequestId = 0;

    private void log(String str) {
        Rlog.d(LOG_TAG + " " + mTestName, str);
    }

    private NetworkRequest makeSubSpecificInternetRequest(int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
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
        NetworkCapabilities netCap = (new NetworkCapabilities())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
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
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_ACTIVE_PHONE_SWITCH);
    }

    private void activatePhoneInPhoneSwitcher(int phoneId, NetworkRequest nr, boolean active) {
        TelephonyNetworkRequest networkRequest = new TelephonyNetworkRequest(nr, mPhone);
        doReturn(active).when(mPhoneSwitcher).shouldApplyNetworkRequest(
                eq(networkRequest), eq(phoneId));
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_ACTIVE_PHONE_SWITCH);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mPhoneSwitcher = mock(PhoneSwitcher.class);
        mMockRadioConfig = mock(RadioConfig.class);
        mDataConnection = mock(DataConnection.class);
        replaceInstance(RadioConfig.class, "sRadioConfig", null, mMockRadioConfig);

        mContextFixture.putStringArrayResource(com.android.internal.R.array.networkAttributes,
                new String[]{"wifi,1,1,1,-1,true", "mobile,0,0,0,-1,true",
                        "mobile_mms,2,0,2,60000,true", "mobile_supl,3,0,2,60000,true",
                        "mobile_dun,4,0,2,60000,true", "mobile_hipri,5,0,3,60000,true",
                        "mobile_fota,10,0,2,60000,true", "mobile_ims,11,0,2,60000,true",
                        "mobile_cbs,12,0,2,60000,true", "wifi_p2p,13,1,0,-1,true",
                        "mobile_ia,14,0,2,-1,true", "mobile_emergency,15,0,2,-1,true"});

        doAnswer(invocation -> {
            final TelephonyNetworkRequest req =
                    (TelephonyNetworkRequest) invocation.getArguments()[0];
            //final Message msg = (Message) invocation.getArguments()[2];
            mNetworkRequestList.add(req);
            mAllNetworkRequestSet.add(req);
            //mNetworkRequestMessageMap.put(req, msg);
            return null;
        }).when(mDataNetworkController).addNetworkRequest(any());

        doAnswer(invocation -> {
            mNetworkRequestList.remove((TelephonyNetworkRequest) invocation.getArguments()[0]);
            return null;
        }).when(mDataNetworkController).removeNetworkRequest(any());
    }

    @After
    public void tearDown() throws Exception {
        mAllNetworkRequestSet.clear();
        mNetworkRequestList.clear();
        mNetworkRequestMessageMap.clear();
        mTelephonyNetworkFactoryUT = null;
        super.tearDown();
    }

    private void createMockedTelephonyComponents() throws Exception {
        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mPhoneSwitcher);

        mTelephonyNetworkFactoryUT = new TelephonyNetworkFactory(Looper.myLooper(), mPhone);
        final ArgumentCaptor<NetworkProvider> providerCaptor =
                ArgumentCaptor.forClass(NetworkProvider.class);
        verify(mConnectivityManager).registerNetworkProvider(providerCaptor.capture());
        // For NetworkFactory to function as expected, the provider ID must be set to some
        // number > 0.
        providerCaptor.getValue().setProviderId(1);
        verify(mPhoneSwitcher).registerForActivePhoneSwitch(any(), anyInt(), any());

        // Simulate the behavior of the system server. When offerNetwork is called, it will
        // update the factory about all requests that pass the registered filter, by calling
        // NetworkProvider#onNetworkNeeded or onNetworkUnneeded.
        // Note that this simulation is a little bit incomplete, as the system server will
        // *update* only for those requests for which the status has changed, but this
        // simulation will send REQUEST_NETWORK or CANCEL_REQUEST for all registered requests.
        // At this time it makes no difference in this test.
        // Also, this test reads from mAllNetworkRequestSet, which is not really the list of
        // requests sent to the system server as the test doesn't instrument that. Instead, it's
        // the list of requests ever sent to the factory. This also makes no difference in this
        // test at this time.
        doAnswer(invocation -> {
            final NetworkCapabilities capabilitiesFilter =
                    mTelephonyNetworkFactoryUT.makeNetworkFilter(
                            mSubscriptionController.getSubIdUsingPhoneId(0));
            for (final TelephonyNetworkRequest request : mAllNetworkRequestSet) {
                final int message = request.canBeSatisfiedBy(capabilitiesFilter)
                        ? CMD_REQUEST_NETWORK : CMD_CANCEL_REQUEST;
                mTelephonyNetworkFactoryUT.obtainMessage(message, 0, 0,
                        request.getNativeNetworkRequest()).sendToTarget();
            }
            return null;
        }).when(mConnectivityManager).offerNetwork(anyInt(), any(), any(), any());
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
    @Ignore("b/256052233")
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
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_ACTIVE_PHONE_SWITCH);
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
    @Ignore("b/256052233")
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

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
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
    @Ignore("b/256052233")
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
        Mockito.reset(mDataNetworkController);
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByApnType(anyString());
        doReturn(false).when(mDataConnection).isActive();

        HandoverParams hp = new HandoverParams(ApnSetting.TYPE_MMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, handoverCallback);
        AsyncResult ar = new AsyncResult(null, hp, null);
        h.sendMessage(h.obtainMessage(5, ar));
        processAllMessages();

        verify(mDataNetworkController, times(1)).removeNetworkRequest(any());
        verify(mDataNetworkController, times(1)).addNetworkRequest(any());
    }
}
