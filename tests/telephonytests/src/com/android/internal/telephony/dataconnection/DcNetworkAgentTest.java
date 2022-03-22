/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;

import static org.mockito.Mockito.doReturn;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkInfo;
import android.net.NetworkProvider;
import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.lang.reflect.Field;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DcNetworkAgentTest extends TelephonyTest {

    private DcNetworkAgent mDcNetworkAgent;
    private DataConnection mDc;
    private DcController mDcc;
    private DcFailBringUp mDcFailBringUp;

    @Mock
    private DataServiceManager mDataServiceManager;
    @Mock
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    @Mock
    private NetworkProvider mNetworkProvider;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        logd("+Setup!");
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        final NetworkAgentConfig.Builder configBuilder = new NetworkAgentConfig.Builder();
        configBuilder.setLegacyType(ConnectivityManager.TYPE_MOBILE);
        configBuilder.setLegacyTypeName("MOBILE");
        configBuilder.setLegacySubType(TelephonyManager.NETWORK_TYPE_LTE);
        configBuilder.setLegacySubTypeName("LTE");
        configBuilder.setLegacyExtraInfo("apn");

        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        mDcFailBringUp = new DcFailBringUp();
        mDcFailBringUp.saveParameters(0, 0, -2);
        doReturn(mDcFailBringUp).when(mDcTesterFailBringUpAll).getDcFailBringUp();

        mDcc = DcController.makeDcc(mPhone, mDcTracker, mDataServiceManager, Looper.myLooper(),
                "");
        mDc = DataConnection.makeDataConnection(mPhone, 0, mDcTracker, mDataServiceManager,
                mDcTesterFailBringUpAll, mDcc);

        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setInterfaceName("fake_iface");
        Field field = DataConnection.class.getDeclaredField("mLinkProperties");
        field.setAccessible(true);
        field.set(mDc, linkProperties);

        mDcNetworkAgent = new DcNetworkAgent(mDc, mPhone, 45, configBuilder.build(),
                mNetworkProvider, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        logd("-Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void verifyDisconnected() throws Exception {
        Field field = NetworkAgent.class.getDeclaredField("mNetworkInfo");
        field.setAccessible(true);
        NetworkInfo networkInfo = (NetworkInfo) field.get(mDcNetworkAgent);
        assertEquals(NetworkInfo.DetailedState.DISCONNECTED, networkInfo.getDetailedState());
    }

    @Test
    public void testUnwantedTimeout() throws Exception {
        mDcNetworkAgent.markConnected();
        mDcNetworkAgent.onNetworkUnwanted();
        processAllMessages();
        moveTimeForward(60000);
        processAllMessages();
        verifyDisconnected();
    }
}
