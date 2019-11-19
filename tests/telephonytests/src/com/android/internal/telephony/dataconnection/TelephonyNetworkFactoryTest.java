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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.StringNetworkSpecifier;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Messenger;
import android.telephony.AccessNetworkConstants;
import android.telephony.Rlog;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.RadioConfig;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams.HandoverCallback;
import com.android.internal.telephony.mocks.ConnectivityServiceMock;
import com.android.internal.telephony.mocks.PhoneSwitcherMock;
import com.android.internal.telephony.mocks.SubscriptionControllerMock;
import com.android.internal.telephony.mocks.TelephonyRegistryMock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.ArrayList;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TelephonyNetworkFactoryTest extends TelephonyTest {
    private final static String LOG_TAG = "TelephonyNetworkFactoryTest";

    @Mock
    private RadioConfig mMockRadioConfig;
    @Mock
    private IConnectivityManager mIConnectivityManager;

    private String mTestName = "";

    private TelephonyRegistryMock mTelephonyRegistryMock;
    private PhoneSwitcherMock mPhoneSwitcherMock;
    private SubscriptionControllerMock mSubscriptionControllerMock;
    private ConnectivityServiceMock mConnectivityServiceMock;
    private final ArrayList<NetworkRequest> mNetworkRequestList = new ArrayList<>();

    private TelephonyNetworkFactory mTelephonyNetworkFactoryUT;

    private void log(String str) {
        Rlog.d(LOG_TAG + " " + mTestName, str);
    }

    private NetworkRequest makeSubSpecificInternetRequest(int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities()).
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        return mConnectivityServiceMock.requestNetwork(netCap, null, 0, new Binder(), -1);
    }
    private NetworkRequest makeSubSpecificMmsRequest(int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities()).
                addCapability(NetworkCapabilities.NET_CAPABILITY_MMS).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        return mConnectivityServiceMock.requestNetwork(netCap, null, 0, new Binder(), -1);
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

        doAnswer(invocation -> {
            mConnectivityServiceMock.registerNetworkFactory(
                    (Messenger) invocation.getArguments()[0],
                    (String) invocation.getArguments()[1]);
            return null;
        }).when(mIConnectivityManager).registerNetworkFactory(any(), anyString());

        doAnswer(invocation -> {
            mConnectivityServiceMock.unregisterNetworkFactory(
                    (Messenger) invocation.getArguments()[0]);
            return null;
        }).when(mIConnectivityManager).unregisterNetworkFactory(any());
    }

    @After
    public void tearDown() throws Exception {
        mConnectivityServiceMock.die();
        super.tearDown();
    }

    private void createMockedTelephonyComponents(int numberOfPhones) throws Exception {
        mConnectivityServiceMock = new ConnectivityServiceMock(mContext);
        mContextFixture.setSystemService(Context.CONNECTIVITY_SERVICE,
                new ConnectivityManager(mContext, mIConnectivityManager));
        mTelephonyRegistryMock = new TelephonyRegistryMock();
        mSubscriptionControllerMock = new SubscriptionControllerMock(mContext,
                mTelephonyRegistryMock, numberOfPhones);
        mPhoneSwitcherMock = new PhoneSwitcherMock(
                numberOfPhones, Looper.myLooper(), mSubscriptionControllerMock);

        replaceInstance(SubscriptionController.class, "sInstance", null,
                mSubscriptionControllerMock);
        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mPhoneSwitcherMock);

        mTelephonyNetworkFactoryUT = new TelephonyNetworkFactory(Looper.myLooper(), mPhone);
        monitorTestableLooper(new TestableLooper(
                mConnectivityServiceMock.getHandlerThread().getLooper()));
    }

    /**
     * Test that phone active changes cause the DcTracker to get poked.
     */
    @FlakyTest
    @Test
    @SmallTest
    public void testActive() throws Exception {
        mTestName = "testActive";
        final int numberOfPhones = 1;
        final int phoneId = 0;
        final int subId = 0;

        createMockedTelephonyComponents(numberOfPhones);

        mPhoneSwitcherMock.setPreferredDataPhoneId(phoneId);
        mSubscriptionControllerMock.setDefaultDataSubId(subId);
        mSubscriptionControllerMock.setSlotSubId(phoneId, subId);
        // fake onSubscriptionChangedListener being triggered.
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);

        log("addDefaultRequest");
        mConnectivityServiceMock.addDefaultRequest();
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("setPhoneActive true: phoneId = " + phoneId);
        mPhoneSwitcherMock.setPhoneActive(phoneId, true);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        log("makeSubSpecificInternetRequest: subId = " + subId);
        NetworkRequest subSpecificDefault = makeSubSpecificInternetRequest(subId);
        processAllMessages();
        assertEquals(2, mNetworkRequestList.size());

        log("setPhoneActive false: phoneId = " + phoneId);
        mPhoneSwitcherMock.setPhoneActive(phoneId, false);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("makeSubSpecificInternetRequest: subId = " + subId);
        NetworkRequest subSpecificMms = makeSubSpecificMmsRequest(subId);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("setPhoneActive true: phoneId = " + phoneId);
        mPhoneSwitcherMock.setPhoneActive(phoneId, true);
        processAllMessages();
        assertEquals(3, mNetworkRequestList.size());

        log("releaseNetworkRequest: subSpecificDefault = " + subSpecificDefault);
        mConnectivityServiceMock.releaseNetworkRequest(subSpecificDefault);
        processAllMessages();
        assertEquals(2, mNetworkRequestList.size());

        log("setPhoneActive false: phoneId = " + phoneId);
        mPhoneSwitcherMock.setPhoneActive(phoneId, false);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("releaseNetworkRequest: subSpecificMms = " + subSpecificMms);
        mConnectivityServiceMock.releaseNetworkRequest(subSpecificMms);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        log("setPhoneActive true: phoneId = " + phoneId);
        mPhoneSwitcherMock.setPhoneActive(phoneId, true);
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

        createMockedTelephonyComponents(numberOfPhones);

        mPhoneSwitcherMock.setPreferredDataPhoneId(phoneId);
        mSubscriptionControllerMock.setDefaultDataSubId(subId);
        mSubscriptionControllerMock.setSlotSubId(phoneId, subId);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        mPhoneSwitcherMock.setPhoneActive(phoneId, true);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        mConnectivityServiceMock.addDefaultRequest();
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        mSubscriptionControllerMock.setSlotSubId(altPhoneId, altSubId);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        mPhoneSwitcherMock.setPreferredDataPhoneId(altPhoneId);
        mSubscriptionControllerMock.setDefaultDataSubId(altSubId);
        mPhoneSwitcherMock.notifyActivePhoneChange(phoneId);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        makeSubSpecificMmsRequest(subId);
        processAllMessages();
        assertEquals(1, mNetworkRequestList.size());

        mSubscriptionControllerMock.setSlotSubId(phoneId, unusedSubId);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        makeSubSpecificInternetRequest(subId);
        processAllMessages();
        assertEquals(0, mNetworkRequestList.size());

        mSubscriptionControllerMock.setSlotSubId(phoneId, subId);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);
        processAllMessages();

        mSubscriptionControllerMock.setDefaultDataSubId(subId);
        mPhoneSwitcherMock.setPreferredDataPhoneId(phoneId);
        mPhoneSwitcherMock.notifyActivePhoneChange(phoneId);
        processAllMessages();
        assertEquals(3, mNetworkRequestList.size());
    }

    /**
     * Test handover when there is no live data connection
     */
    @Test
    @SmallTest
    public void testHandoverNoLiveData() throws Exception {
        createMockedTelephonyComponents(1);
        mPhoneSwitcherMock.setPreferredDataPhoneId(0);
        mSubscriptionControllerMock.setDefaultDataSubId(0);
        mSubscriptionControllerMock.setSlotSubId(0, 0);
        mTelephonyNetworkFactoryUT.mInternalHandler.sendEmptyMessage(
                TelephonyNetworkFactory.EVENT_SUBSCRIPTION_CHANGED);

        mPhoneSwitcherMock.setPhoneActive(0, true);
        mConnectivityServiceMock.addDefaultRequest();

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
}
