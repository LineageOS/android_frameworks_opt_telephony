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

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_ADDRESS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_DNS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_GATEWAY;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_IFNAME;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_PCSCF_ADDRESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.net.KeepalivePacketData;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NattKeepalivePacketData;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.ServiceState.RegState;
import android.telephony.ServiceState.RilRadioTechnology;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.TrafficDescriptor;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.android.internal.R;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams;
import com.android.internal.telephony.dataconnection.DataConnection.DisconnectParams;
import com.android.internal.telephony.dataconnection.DataConnection.SetupResult;
import com.android.internal.telephony.metrics.DataCallSessionStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

public class DataConnectionTest extends TelephonyTest {
    private static final int DEFAULT_DC_CID = 10;

    @Mock
    DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    @Mock
    ConnectionParams mCp;
    @Mock
    DisconnectParams mDcp;
    @Mock
    ApnContext mApnContext;
    @Mock
    ApnContext mEnterpriseApnContext;
    @Mock
    DcFailBringUp mDcFailBringUp;
    @Mock
    DataCallSessionStats mDataCallSessionStats;
    @Mock
    DataConnection mDefaultDc;
    @Mock
    DataServiceManager mDataServiceManager;

    private DataConnection mDc;
    private DataConnectionTestHandler mDataConnectionTestHandler;
    private DcController mDcc;

    private ApnSetting mApn1 = ApnSetting.makeApnSetting(
            2163,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            null,                   // proxy
            -1,                     // port
            null,                   // mmsc
            null,                   // mmsproxy
            -1,                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL, // types
            ApnSetting.PROTOCOL_IP, // protocol
            ApnSetting.PROTOCOL_IP, // roaming_protocol
            true,                   // carrier_enabled
            0,                      // networktype_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            -1,                     // mvno_type
            "");                    // mnvo_match_data

    private ApnSetting mApn2 = ApnSetting.makeApnSetting(
            2164,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            null,                   // proxy
            -1,                     // port
            null,                   // mmsc
            null,                   // mmsproxy
            -1,                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_DUN, // types
            ApnSetting.PROTOCOL_IP, // protocol
            ApnSetting.PROTOCOL_IP, // roaming_protocol
            true,                   // carrier_enabled
            0,                      // networktype_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            -1,                     // mvno_type
            "");                    // mnvo_match_data

    private ApnSetting mApn3 = ApnSetting.makeApnSetting(
            2165,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            null,                   // proxy
            -1,                     // port
            null,                   // mmsc
            null,                   // mmsproxy
            -1,                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            ApnSetting.TYPE_DEFAULT, // types
            ApnSetting.PROTOCOL_IPV6, // protocol
            ApnSetting.PROTOCOL_IP, // roaming_protocol
            true,                   // carrier_enabled
            0,                      // networktype_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            -1,                     // mvno_type
            "",                     // mnvo_match_data
            0,                      // apn_set_id
            -1,                     // carrier_id
            1);                     // skip_464xlat

    private ApnSetting mApn4 = ApnSetting.makeApnSetting(
            2166,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            null,                   // proxy
            -1,                     // port
            null,                   // mmsc
            null,                   // mmsproxy
            -1,                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            ApnSetting.TYPE_IMS,    // types
            ApnSetting.PROTOCOL_IPV6, // protocol
            ApnSetting.PROTOCOL_IP, // roaming_protocol
            true,                   // carrier_enabled
            0,                      // networktype_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            -1,                     // mvno_type
            "");                    // mnvo_match_data

    private ApnSetting mApn5 = ApnSetting.makeApnSetting(
            2167,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            null,                   // proxy
            -1,                     // port
            null,                   // mmsc
            null,                   // mmsproxy
            -1,                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            ApnSetting.TYPE_IMS,    // types
            ApnSetting.PROTOCOL_IPV6, // protocol
            ApnSetting.PROTOCOL_IP, // roaming_protocol
            true,                   // carrier_enabled
            0,                      // networktype_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            -1,                     // mvno_type
            "",                     // mnvo_match_data
            0,                      // apn_set_id
            -1,                     // carrier_id
            0);                     // skip_464xlat

    private ApnSetting mApn6 = ApnSetting.makeApnSetting(
            2168,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            null,                   // proxy
            -1,                     // port
            null,                   // mmsc
            null,                   // mmsproxy
            -1,                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            ApnSetting.TYPE_EMERGENCY, // types
            ApnSetting.PROTOCOL_IP, // protocol
            ApnSetting.PROTOCOL_IP, // roaming_protocol
            true,                   // carrier_enabled
            0,                      // networktype_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            -1,                     // mvno_type
            "");                    // mnvo_match_data

    private class DataConnectionTestHandler extends HandlerThread {

        private DataConnectionTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            Handler h = new Handler();
            mDcc = DcController.makeDcc(mPhone, mDcTracker, mDataServiceManager, h.getLooper(), "");
            mDc = DataConnection.makeDataConnection(mPhone, 0, mDcTracker, mDataServiceManager,
                    mDcTesterFailBringUpAll, mDcc);
        }
    }

    private void setSuccessfulSetupDataResponse(int cid) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            DataCallResponse response = new DataCallResponse.Builder()
                    .setCause(0)
                    .setRetryDurationMillis(-1L)
                    .setId(cid)
                    .setLinkStatus(2)
                    .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                    .setInterfaceName("ifname")
                    .setAddresses(Arrays.asList(
                            new LinkAddress(InetAddresses.parseNumericAddress("10.0.2.15"), 32),
                            new LinkAddress("2607:fb90:a620:651d:eabe:f8da:c107:44be/64")))
                    .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                            InetAddresses.parseNumericAddress("fd00:976a::9")))
                    .setGatewayAddresses(Arrays.asList(
                            InetAddresses.parseNumericAddress("10.0.2.15"),
                            InetAddresses.parseNumericAddress("fe80::2")))
                    .setPcscfAddresses(Arrays.asList(
                            InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                            InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                            InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                    .setMtu(1500)
                    .setMtuV4(1500)
                    .setMtuV6(1500)
                    .setPduSessionId(1)
                    .setQosBearerSessions(new ArrayList<>())
                    .setTrafficDescriptors(new ArrayList<>())
                    .build();
            msg.getData().putParcelable("data_call_response", response);
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.sendToTarget();
            return null;
        }).when(mDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    private void setFailedSetupDataResponse(@DataServiceCallback.ResultCode int resultCode) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];
            msg.arg1 = resultCode;
            msg.sendToTarget();
            return null;
        }).when(mDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        logd("+Setup!");
        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mApnContext);
        replaceInstance(ConnectionParams.class, "mRilRat", mCp,
                ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);
        doReturn(mApn1).when(mApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_DEFAULT_STRING).when(mApnContext).getApnType();
        doReturn(ApnSetting.TYPE_DEFAULT).when(mApnContext).getApnTypeBitmask();

        mDcFailBringUp.saveParameters(0, 0, -2);
        doReturn(mDcFailBringUp).when(mDcTesterFailBringUpAll).getDcFailBringUp();

        mContextFixture.putStringArrayResource(com.android.internal.R.array
                .config_mobile_tcp_buffers, new String[]{
                "umts:131072,262144,1452032,4096,16384,399360",
                "hspa:131072,262144,2441216,4096,16384,399360",
                "hsupa:131072,262144,2441216,4096,16384,399360",
                "hsdpa:131072,262144,2441216,4096,16384,399360",
                "hspap:131072,262144,2441216,4096,16384,399360",
                "edge:16384,32768,131072,4096,16384,65536",
                "gprs:4096,8192,24576,4096,8192,24576",
                "1xrtt:16384,32768,131070,4096,16384,102400",
                "evdo:131072,262144,1048576,4096,16384,524288",
                "lte:524288,1048576,8388608,262144,524288,4194304"});

        mContextFixture.putResource(R.string.config_wwan_data_service_package,
                "com.android.phone");

        mDcp.mApnContext = mApnContext;

        setSuccessfulSetupDataResponse(DEFAULT_DC_CID);

        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[2];
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.sendToTarget();
            return null;
        }).when(mDataServiceManager).deactivateDataCall(anyInt(), anyInt(), any(Message.class));

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mDataServiceManager)
                .getTransportType();

        mDataConnectionTestHandler = new DataConnectionTestHandler(getClass().getSimpleName());
        mDataConnectionTestHandler.start();

        waitForMs(200);
        mDc.setDataCallSessionStats(mDataCallSessionStats);

        logd("-Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        mDc = null;
        mDcc = null;
        mDataConnectionTestHandler.quit();
        mDataConnectionTestHandler.join();
        super.tearDown();
    }

    private long getSuggestedRetryDelay(DataCallResponse response) throws Exception {
        Class[] cArgs = new Class[1];
        cArgs[0] = DataCallResponse.class;
        Method method = DataConnection.class.getDeclaredMethod("getSuggestedRetryDelay", cArgs);
        method.setAccessible(true);
        return (long) method.invoke(mDc, response);
    }

    private boolean isUnmeteredUseOnly() throws Exception {
        Method method = DataConnection.class.getDeclaredMethod("isUnmeteredUseOnly");
        method.setAccessible(true);
        return (boolean) method.invoke(mDc);
    }

    private boolean isEnterpriseUse() throws Exception {
        Method method = DataConnection.class.getDeclaredMethod("isEnterpriseUse");
        method.setAccessible(true);
        return (boolean) method.invoke(mDc);
    }

    private boolean isSuspended() throws Exception {
        Field field = DataConnection.class.getDeclaredField("mIsSuspended");
        field.setAccessible(true);
        return field.getBoolean(mDc);
    }

    private SetupResult setLinkProperties(DataCallResponse response, LinkProperties linkProperties)
            throws Exception {
        Class[] cArgs = new Class[2];
        cArgs[0] = DataCallResponse.class;
        cArgs[1] = LinkProperties.class;
        Method method = DataConnection.class.getDeclaredMethod("setLinkProperties", cArgs);
        method.setAccessible(true);
        return (SetupResult) method.invoke(mDc, response, linkProperties);
    }

    @Test
    @SmallTest
    public void testConnectEvent() {
        assertTrue(mDc.isInactive());
        connectEvent(true);

        verify(mCT, times(1)).registerForVoiceCallStarted(any(Handler.class),
                eq(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_STARTED), eq(null));
        verify(mCT, times(1)).registerForVoiceCallEnded(any(Handler.class),
                eq(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_ENDED), eq(null));
        verify(mSimulatedCommandsVerifier, times(1))
                .registerForNattKeepaliveStatus(any(Handler.class),
                        eq(DataConnection.EVENT_KEEPALIVE_STATUS), eq(null));
        verify(mSimulatedCommandsVerifier, times(1))
                .registerForLceInfo(any(Handler.class),
                        eq(DataConnection.EVENT_LINK_CAPACITY_CHANGED), eq(null));
        verify(mVcnManager, atLeastOnce())
                .applyVcnNetworkPolicy(
                        argThat(caps ->
                                caps.hasCapability(
                                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)),
                        any());

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        ArgumentCaptor<TrafficDescriptor> tdCaptor =
                ArgumentCaptor.forClass(TrafficDescriptor.class);
        verify(mDataServiceManager, times(1)).setupDataCall(
                eq(AccessNetworkType.UTRAN), dpCaptor.capture(), eq(false),
                eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), tdCaptor.capture(), anyBoolean(), any(Message.class));

        verify(mSimulatedCommandsVerifier, times(0))
                .allocatePduSessionId(any());

        assertEquals("spmode.ne.jp", dpCaptor.getValue().getApn());
        if (tdCaptor.getValue() != null) {
            if (mApnContext.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
                assertEquals(null, tdCaptor.getValue().getDataNetworkName());
                assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(),
                        tdCaptor.getValue().getOsAppId()));
            } else {
                assertEquals("spmode.ne.jp", tdCaptor.getValue().getDataNetworkName());
                assertEquals(null, tdCaptor.getValue().getOsAppId());
            }
        }
        assertTrue(mDc.isActive());

        assertEquals(1, mDc.getPduSessionId());
        assertEquals(3, mDc.getPcscfAddresses().length);
        assertTrue(Arrays.stream(mDc.getPcscfAddresses()).anyMatch("fd00:976a:c305:1d::8"::equals));
        assertTrue(Arrays.stream(mDc.getPcscfAddresses()).anyMatch("fd00:976a:c202:1d::7"::equals));
        assertTrue(Arrays.stream(mDc.getPcscfAddresses()).anyMatch("fd00:976a:c305:1d::5"::equals));
    }

    @Test
    @SmallTest
    public void testConnectOnIwlan() throws Exception {
        assertTrue(mDc.isInactive());
        Field field = DataConnection.class.getDeclaredField("mTransportType");
        field.setAccessible(true);
        field.setInt(mDc, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        connectEvent(true);

        verify(mCT, times(1)).registerForVoiceCallStarted(any(Handler.class),
                eq(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_STARTED), eq(null));
        verify(mCT, times(1)).registerForVoiceCallEnded(any(Handler.class),
                eq(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_ENDED), eq(null));
        verify(mSimulatedCommandsVerifier, times(0))
                .registerForNattKeepaliveStatus(any(Handler.class),
                        eq(DataConnection.EVENT_KEEPALIVE_STATUS), eq(null));
        verify(mSimulatedCommandsVerifier, times(0))
                .registerForLceInfo(any(Handler.class),
                        eq(DataConnection.EVENT_LINK_CAPACITY_CHANGED), eq(null));
        verify(mVcnManager, atLeastOnce())
                .applyVcnNetworkPolicy(
                        argThat(caps ->
                                caps.hasCapability(
                                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)),
                        any());

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        ArgumentCaptor<TrafficDescriptor> tdCaptor =
                ArgumentCaptor.forClass(TrafficDescriptor.class);
        verify(mDataServiceManager, times(1)).setupDataCall(
                eq(AccessNetworkType.UTRAN), dpCaptor.capture(), eq(false),
                eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), tdCaptor.capture(), anyBoolean(), any(Message.class));

        verify(mSimulatedCommandsVerifier, times(1))
                .allocatePduSessionId(any());

        assertEquals("spmode.ne.jp", dpCaptor.getValue().getApn());
        if (tdCaptor.getValue() != null) {
            if (mApnContext.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
                assertEquals(null, tdCaptor.getValue().getDataNetworkName());
                assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(),
                        tdCaptor.getValue().getOsAppId()));
            } else {
                assertEquals("spmode.ne.jp", tdCaptor.getValue().getDataNetworkName());
                assertEquals(null, tdCaptor.getValue().getOsAppId());
            }
        }
        assertTrue(mDc.isActive());

        assertEquals(1, mDc.getPduSessionId());
        assertEquals(3, mDc.getPcscfAddresses().length);
        assertTrue(Arrays.stream(mDc.getPcscfAddresses()).anyMatch("fd00:976a:c305:1d::8"::equals));
        assertTrue(Arrays.stream(mDc.getPcscfAddresses()).anyMatch("fd00:976a:c202:1d::7"::equals));
        assertTrue(Arrays.stream(mDc.getPcscfAddresses()).anyMatch("fd00:976a:c305:1d::5"::equals));
    }

    @Test
    public void testConnectEventDuplicateContextIds() throws Exception {
        setUpDefaultData(DEFAULT_DC_CID);

        // Try to connect ENTERPRISE with the same CID as default
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mEnterpriseApnContext);
        doReturn(mApn1).when(mEnterpriseApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_ENTERPRISE_STRING).when(mEnterpriseApnContext).getApnType();
        doReturn(ApnSetting.TYPE_ENTERPRISE).when(mEnterpriseApnContext).getApnTypeBitmask();

        // Verify that ENTERPRISE wasn't set up
        connectEvent(false);
        assertTrue(mDc.isInactive());

        // Change the CID
        setSuccessfulSetupDataResponse(DEFAULT_DC_CID + 1);

        // Verify that ENTERPRISE was set up
        connectEvent(true);
        assertTrue(mDc.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
    }

    @Test
    public void testConnectEventNoDefaultData() throws Exception {
        assertFalse(mDefaultDc.isActive());

        // Try to connect ENTERPRISE when default data doesn't exist
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mEnterpriseApnContext);
        doReturn(mApn1).when(mEnterpriseApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_ENTERPRISE_STRING).when(mEnterpriseApnContext).getApnType();
        doReturn(ApnSetting.TYPE_ENTERPRISE).when(mEnterpriseApnContext).getApnTypeBitmask();

        // Verify that ENTERPRISE wasn't set up
        connectEvent(false);
        assertTrue(mDc.isInactive());

        // Set up default data
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mApnContext);
        setUpDefaultData(1);

        // Verify that ENTERPRISE was set up
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mEnterpriseApnContext);
        connectEvent(true);
        assertTrue(mDc.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
    }

    private void setUpDefaultData(int cid) throws Exception {
        replaceInstance(DataConnection.class, "mCid", mDefaultDc, cid);
        doReturn(true).when(mDefaultDc).isActive();
        doReturn(Arrays.asList(mApnContext)).when(mDefaultDc).getApnContexts();
        mDcc.addActiveDcByCid(mDefaultDc);
        assertTrue(mDefaultDc.getApnContexts().stream()
                .anyMatch(apn -> apn.getApnTypeBitmask() == ApnSetting.TYPE_DEFAULT));
    }

    @Test
    @SmallTest
    public void testDisconnectEvent() {
        testConnectEvent();

        mDc.setPduSessionId(5);
        disconnectEvent();

        verify(mSimulatedCommandsVerifier, times(1)).unregisterForLceInfo(any(Handler.class));
        verify(mSimulatedCommandsVerifier, times(1))
                .unregisterForNattKeepaliveStatus(any(Handler.class));
        verify(mDataServiceManager, times(1)).deactivateDataCall(eq(DEFAULT_DC_CID),
                eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));
        verify(mSimulatedCommandsVerifier, times(0))
                .releasePduSessionId(any(), eq(5));

        assertTrue(mDc.isInactive());
    }

    @Test
    @SmallTest
    public void testDisconnectOnIwlan() throws Exception {
        testConnectEvent();

        Field field = DataConnection.class.getDeclaredField("mTransportType");
        field.setAccessible(true);
        field.setInt(mDc, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        mDc.setPduSessionId(5);
        disconnectEvent();

        verify(mSimulatedCommandsVerifier, times(0)).unregisterForLceInfo(any(Handler.class));
        verify(mSimulatedCommandsVerifier, times(0))
                .unregisterForNattKeepaliveStatus(any(Handler.class));
        verify(mDataServiceManager, times(1)).deactivateDataCall(eq(DEFAULT_DC_CID),
                eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));
        verify(mSimulatedCommandsVerifier, times(1))
                .releasePduSessionId(any(), eq(5));

        assertTrue(mDc.isInactive());
    }

    @Test
    @SmallTest
    public void testModemSuggestRetry() throws Exception {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(0)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(response.getSuggestedRetryTime(), getSuggestedRetryDelay(response));

        response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(1000)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(response.getSuggestedRetryTime(), getSuggestedRetryDelay(response));

        response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(9999)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(response.getSuggestedRetryTime(), getSuggestedRetryDelay(response));
    }

    @Test
    @SmallTest
    public void testModemNotSuggestRetry() throws Exception {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY, getSuggestedRetryDelay(response));

        response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-5)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY, getSuggestedRetryDelay(response));

        response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(Long.MIN_VALUE)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY, getSuggestedRetryDelay(response));
    }

    @Test
    @SmallTest
    public void testModemSuggestNoRetry() throws Exception {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(Long.MAX_VALUE)
                .setId(1)
                .setLinkStatus(2)
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
        assertEquals(RetryManager.NO_RETRY, getSuggestedRetryDelay(response));
    }

    private NetworkCapabilities getNetworkCapabilities() throws Exception {
        Method method = DataConnection.class.getDeclaredMethod("getNetworkCapabilities");
        method.setAccessible(true);
        return (NetworkCapabilities) method.invoke(mDc);
    }

    private int getDisallowedApnTypes() throws Exception {
        Method method = DataConnection.class.getDeclaredMethod("getDisallowedApnTypes");
        method.setAccessible(true);
        return (int) method.invoke(mDc);
    }

    @Test
    @SmallTest
    public void testNetworkCapability() throws Exception {
        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] { "default" });
        doReturn(mApn2).when(mApnContext).getApnSetting();
        testConnectEvent();

        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN));
        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));

        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_WWAN_DISALLOWED_APN_TYPES_STRING_ARRAY,
                new String[] {"supl"});

        disconnectEvent();
        doReturn(mApn1).when(mApnContext).getApnSetting();
        connectEvent(true);

        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN));
        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
    }

    @Test
    @SmallTest
    public void testEnterpriseNetworkCapability() throws Exception {
        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] { "default" });
        doReturn(mApn2).when(mApnContext).getApnSetting();
        testConnectEvent();

        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN));
        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));

        disconnectEvent();
        setUpDefaultData(1);
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mEnterpriseApnContext);
        doReturn(mApn1).when(mEnterpriseApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_ENTERPRISE_STRING).when(mEnterpriseApnContext).getApnType();
        doReturn(ApnSetting.TYPE_ENTERPRISE).when(mEnterpriseApnContext).getApnTypeBitmask();
        connectEvent(true);

        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN));
        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertFalse("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL));
        assertTrue("capabilities: " + getNetworkCapabilities(), getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
    }

    @Test
    @SmallTest
    public void testMeteredCapability() throws Exception {

        mContextFixture.getCarrierConfigBundle().
                putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] {"default"});

        testConnectEvent();

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
    }

    @Test
    @SmallTest
    public void testNonMeteredCapability() throws Exception {

        doReturn(2819).when(mPhone).getSubId();
        mContextFixture.getCarrierConfigBundle().
                putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                        new String[] {"mms"});

        testConnectEvent();

        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
    }

    @Test
    public void testOverrideUnmetered() throws Exception {
        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] { "default" });
        testConnectEvent();

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_CONGESTED));

        mDc.onMeterednessChanged(true);
        waitForMs(100);

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_CONGESTED));

        mDc.onMeterednessChanged(false);
        waitForMs(100);

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_CONGESTED));
    }

    @Test
    public void testOverrideCongested() throws Exception {
        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] { "default" });
        testConnectEvent();

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_CONGESTED));

        mDc.onCongestednessChanged(true);
        waitForMs(100);

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_CONGESTED));

        mDc.onCongestednessChanged(false);
        waitForMs(100);

        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_METERED));
        assertFalse(getNetworkCapabilities().hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertTrue(getNetworkCapabilities().hasCapability(NET_CAPABILITY_NOT_CONGESTED));
    }

    @Test
    public void testSubscriptionIds() throws Exception {
        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] { "default" });
        testConnectEvent();

        assertEquals(Collections.singleton(0), getNetworkCapabilities().getSubscriptionIds());
    }

    @Test
    public void testShouldSkip464Xlat() throws Exception {
        assertFalse(testShouldSkip464XlatEvent(mApn1));
        disconnectEvent();

        assertTrue(testShouldSkip464XlatEvent(mApn3));
        disconnectEvent();

        assertTrue(testShouldSkip464XlatEvent(mApn4));
        disconnectEvent();

        assertFalse(testShouldSkip464XlatEvent(mApn5));
        disconnectEvent();
    }

    private boolean testShouldSkip464XlatEvent(ApnSetting apn) throws Exception {
        Method method = DataConnection.class.getDeclaredMethod("shouldSkip464Xlat");
        method.setAccessible(true);

        doReturn(apn).when(mApnContext).getApnSetting();
        doReturn(apn.getApnTypeBitmask()).when(mApnContext).getApnTypeBitmask();
        connectEvent(true);
        logd(getNetworkCapabilities().toString());

        return (Boolean) method.invoke(mDc);
    }

    private void connectEvent(boolean validate) {
        mDc.sendMessage(DataConnection.EVENT_CONNECT, mCp);
        waitForMs(200);
        if (validate) {
            assertTrue(mDc.isActive());
        }
    }

    private void disconnectEvent() {
        mDc.sendMessage(DataConnection.EVENT_DISCONNECT, mDcp);
        waitForMs(100);
        assertTrue(mDc.isInactive());
    }

    private void serviceStateChangedEvent(@RegState int dataRegState, @RilRadioTechnology int rat) {
        mDc.obtainMessage(DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED,
                new AsyncResult(null, new Pair<>(dataRegState, rat), null)).sendToTarget();
        waitForMs(100);
    }

    @Test
    @SmallTest
    public void testIsIpAddress() {
        // IPv4
        assertTrue(DataConnection.isIpAddress("1.2.3.4"));
        assertTrue(DataConnection.isIpAddress("127.0.0.1"));

        // IPv6
        assertTrue(DataConnection.isIpAddress("::1"));
        assertTrue(DataConnection.isIpAddress("2001:4860:800d::68"));
    }

    @Test
    @SmallTest
    public void testSetLinkProperties() throws Exception {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1)
                .setId(1)
                .setLinkStatus(2)
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

        LinkProperties linkProperties = new LinkProperties();
        assertEquals(SetupResult.SUCCESS, setLinkProperties(response, linkProperties));
        logd(linkProperties.toString());
        assertEquals(response.getInterfaceName(), linkProperties.getInterfaceName());
        assertEquals(response.getAddresses().size(), linkProperties.getAddresses().size());
        for (int i = 0; i < response.getAddresses().size(); ++i) {
            assertEquals(response.getAddresses().get(i).getAddress(),
                    InetAddresses.parseNumericAddress(linkProperties.getLinkAddresses().get(i)
                            .getAddress().getHostAddress()));
        }

        assertEquals(response.getDnsAddresses().size(), linkProperties.getDnsServers().size());
        for (int i = 0; i < response.getDnsAddresses().size(); ++i) {
            assertEquals("i = " + i, response.getDnsAddresses().get(i),
                    InetAddresses.parseNumericAddress(
                            linkProperties.getDnsServers().get(i).getHostAddress()));
        }

        assertEquals(response.getGatewayAddresses().size(), linkProperties.getRoutes().size());
        for (int i = 0; i < response.getGatewayAddresses().size(); ++i) {
            assertEquals("i = " + i, response.getGatewayAddresses().get(i),
                    InetAddresses.parseNumericAddress(linkProperties.getRoutes().get(i)
                            .getGateway().getHostAddress()));
        }

        assertEquals(response.getPcscfAddresses().size(), linkProperties.getPcscfServers().size());
        for (int i = 0; i < response.getPcscfAddresses().size(); ++i) {
            assertEquals("i = " + i, response.getPcscfAddresses().get(i),
                    InetAddresses.parseNumericAddress(linkProperties.getPcscfServers().get(i)
                            .getHostAddress()));
        }

        assertEquals(response.getMtu(), linkProperties.getMtu());
    }

    @Test
    @SmallTest
    public void testSetLinkPropertiesEmptyAddress() throws Exception {
        // 224.224.224.224 is an invalid address.
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1)
                .setId(1)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_DNS)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(
                        Arrays.asList(InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1440)
                .setMtuV6(1440)
                .build();

        LinkProperties linkProperties = new LinkProperties();
        assertEquals(SetupResult.ERROR_INVALID_ARG, setLinkProperties(response, linkProperties));
    }

    @Test
    @SmallTest
    public void testSetLinkPropertiesEmptyDns() throws Exception {
        // Empty dns entry.
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1)
                .setId(1)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(FAKE_ADDRESS), 0)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(
                        Arrays.asList(InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1440)
                .setMtuV6(1440)
                .build();

        // Make sure no exception was thrown
        LinkProperties linkProperties = new LinkProperties();
        assertEquals(SetupResult.SUCCESS, setLinkProperties(response, linkProperties));
    }

    @Test
    @SmallTest
    public void testStartKeepaliveWLAN() throws Exception {
        testConnectEvent();
        waitForMs(200);

        Field field = DataConnection.class.getDeclaredField("mTransportType");
        field.setAccessible(true);
        field.setInt(mDc, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        final int sessionHandle = 0xF00;
        final int slotId = 3;
        final int interval = 10; // seconds
        // Construct a new KeepalivePacketData request as we would receive from a Network Agent,
        // and check that the packet is sent to the RIL.
        KeepalivePacketData kd = NattKeepalivePacketData.nattKeepalivePacket(
                InetAddresses.parseNumericAddress("1.2.3.4"),
                1234,
                InetAddresses.parseNumericAddress("8.8.8.8"),
                4500);
        mDc.obtainMessage(
                DataConnection.EVENT_KEEPALIVE_START_REQUEST, slotId, interval, kd).sendToTarget();
        waitForMs(100);
        // testStartStopNattKeepalive() verifies that this request is passed with WWAN.
        // Thus, even though we can't see the response in NetworkAgent, we can verify that the
        // CommandsInterface never receives a request and infer that it was dropped due to WLAN.
        verify(mSimulatedCommandsVerifier, times(0))
                .startNattKeepalive(anyInt(), eq(kd), eq(interval * 1000), any(Message.class));
    }

    public void checkStartStopNattKeepalive(boolean useCondensedFlow) throws Exception {
        testConnectEvent();
        waitForMs(200);

        final int sessionHandle = 0xF00;
        final int slotId = 3;
        final int interval = 10; // seconds
        // Construct a new KeepalivePacketData request as we would receive from a Network Agent,
        // and check that the packet is sent to the RIL.
        KeepalivePacketData kd = NattKeepalivePacketData.nattKeepalivePacket(
                InetAddresses.parseNumericAddress("1.2.3.4"),
                1234,
                InetAddresses.parseNumericAddress("8.8.8.8"),
                4500);
        mDc.obtainMessage(
                DataConnection.EVENT_KEEPALIVE_START_REQUEST, slotId, interval, kd).sendToTarget();
        waitForMs(100);
        verify(mSimulatedCommandsVerifier, times(1))
                .startNattKeepalive(anyInt(), eq(kd), eq(interval * 1000), any(Message.class));

        Message kaStarted = mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STARTED, slotId, 0);
        if (useCondensedFlow) {
            // Send a singled condensed response that a keepalive have been requested and the
            // activation is completed. This flow should be used if the keepalive offload request
            // is handled by a high-priority signalling path.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_ACTIVE), null);
            kaStarted.sendToTarget();
        } else {
            // Send the sequential responses indicating first that the request was received and
            // then that the keepalive is running. This should create an active record of the
            // keepalive in DataConnection while permitting the status from a low priority or other
            // high-latency handler to activate the keepalive without blocking a request.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_PENDING), null);
            kaStarted.sendToTarget();
            Message kaRunning = mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STATUS);
            AsyncResult.forMessage(
                    kaRunning, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_ACTIVE), null);
            kaRunning.sendToTarget();
        }
        waitForMs(100);

        // Verify that we can stop the connection, which checks that the record in DataConnection
        // has a valid mapping between slotId (from network agent) to sessionHandle (from Radio).
        mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STOP_REQUEST, slotId).sendToTarget();
        waitForMs(100);
        verify(mSimulatedCommandsVerifier, times(1))
                .stopNattKeepalive(eq(sessionHandle), any(Message.class));

        Message kaStopped = mDc.obtainMessage(
                DataConnection.EVENT_KEEPALIVE_STOPPED, sessionHandle, slotId);
        AsyncResult.forMessage(kaStopped);
        kaStopped.sendToTarget();
        // Verify that after the connection is stopped, the mapping for a Keepalive Session is
        // removed. Thus, subsequent calls to stop the same keepalive are ignored.
        mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STOP_REQUEST, slotId).sendToTarget();
        waitForMs(100);
        // Check that the mock has not been called subsequent to the previous invocation
        // while avoiding the use of reset()
        verify(mSimulatedCommandsVerifier, times(1))
                .stopNattKeepalive(anyInt(), any(Message.class));
    }

    @Test
    @MediumTest
    public void testStartStopNattKeepalive() throws Exception {
        checkStartStopNattKeepalive(false);
    }

    @Test
    @MediumTest
    public void testStartStopNattKeepaliveCondensed() throws Exception {
        checkStartStopNattKeepalive(true);
    }

    public void checkStartNattKeepaliveFail(boolean useCondensedFlow) throws Exception {
        testConnectEvent();
        waitForMs(200);

        final int sessionHandle = 0xF00;
        final int slotId = 3;
        final int interval = 10; // seconds
        // Construct a new KeepalivePacketData request as we would receive from a Network Agent,
        // and check that the packet is sent to the RIL.
        KeepalivePacketData kd = NattKeepalivePacketData.nattKeepalivePacket(
                InetAddresses.parseNumericAddress("1.2.3.4"),
                1234,
                InetAddresses.parseNumericAddress("8.8.8.8"),
                4500);
        mDc.obtainMessage(
                DataConnection.EVENT_KEEPALIVE_START_REQUEST, slotId, interval, kd).sendToTarget();
        waitForMs(100);
        verify(mSimulatedCommandsVerifier, times(1))
                .startNattKeepalive(anyInt(), eq(kd), eq(interval * 1000), any(Message.class));

        Message kaStarted = mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STARTED, slotId, 0);
        if (useCondensedFlow) {
            // Indicate in the response that the keepalive has failed.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(KeepaliveStatus.ERROR_UNSUPPORTED), null);
            kaStarted.sendToTarget();
        } else {
            // Indicate that the keepalive is queued, and then signal a failure from the modem
            // such that a pending keepalive fails to activate.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_PENDING), null);
            kaStarted.sendToTarget();
            Message kaRunning = mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STATUS);
            AsyncResult.forMessage(
                    kaRunning, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_INACTIVE), null);
            kaRunning.sendToTarget();
        }
        waitForMs(100);
        // Verify that a failed connection request cannot be stopped due to no record in
        // the DataConnection.
        mDc.obtainMessage(DataConnection.EVENT_KEEPALIVE_STOP_REQUEST, slotId).sendToTarget();
        waitForMs(100);
        verify(mSimulatedCommandsVerifier, times(0))
                .stopNattKeepalive(anyInt(), any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartNattKeepaliveFail() throws Exception {
        checkStartNattKeepaliveFail(false);
    }

    @Test
    @SmallTest
    public void testStartNattKeepaliveFailCondensed() throws Exception {
        checkStartNattKeepaliveFail(true);
    }

    @Test
    @SmallTest
    public void testIsUnmeteredUseOnly() throws Exception {
        Field field = DataConnection.class.getDeclaredField("mTransportType");
        field.setAccessible(true);
        field.setInt(mDc, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        assertFalse(isUnmeteredUseOnly());

        field = DataConnection.class.getDeclaredField("mTransportType");
        field.setAccessible(true);
        field.setInt(mDc, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(ApnSetting.TYPE_MMS).when(mApnContext).getApnTypeBitmask();

        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] { "default" });

        assertTrue(isUnmeteredUseOnly());
    }

    @Test
    public void testIsEnterpriseUse() throws Exception {
        assertFalse(isEnterpriseUse());
        assertFalse(mDc.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));

        setUpDefaultData(1);
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mEnterpriseApnContext);
        doReturn(mApn1).when(mEnterpriseApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_ENTERPRISE_STRING).when(mEnterpriseApnContext).getApnType();
        doReturn(ApnSetting.TYPE_ENTERPRISE).when(mEnterpriseApnContext).getApnTypeBitmask();
        connectEvent(true);

        assertTrue(isEnterpriseUse());
        assertTrue(mDc.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
    }

    @Test
    @SmallTest
    public void testGetDisallowedApnTypes() throws Exception {
        mContextFixture.getCarrierConfigBundle().putStringArray(
                CarrierConfigManager.KEY_CARRIER_WWAN_DISALLOWED_APN_TYPES_STRING_ARRAY,
                new String[] { "mms", "supl", "fota" });
        testConnectEvent();

        assertEquals(ApnSetting.TYPE_MMS | ApnSetting.TYPE_SUPL | ApnSetting.TYPE_FOTA,
                getDisallowedApnTypes());
    }

    @Test
    public void testIsSuspended() throws Exception {
        // Return false if not active state
        assertTrue(mDc.isInactive());
        assertFalse(isSuspended());

        // Return false for emergency APN
        doReturn(mApn6).when(mApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_EMERGENCY).when(mApnContext).getApnTypeBitmask();
        connectEvent(true);
        assertFalse(isSuspended());

        // Back to DEFAULT APN
        disconnectEvent();
        assertTrue(mDc.isInactive());
        doReturn(mApn1).when(mApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_DEFAULT).when(mApnContext).getApnTypeBitmask();
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        connectEvent(true);

        // Before getting any service state event, the connection should not be suspended.
        assertFalse(isSuspended());

        // Return true if combined reg state is not in service
        serviceStateChangedEvent(ServiceState.STATE_OUT_OF_SERVICE,
                ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN);
        assertTrue(isSuspended());

        // Return false if in service and concurrent voice and data is allowed
        serviceStateChangedEvent(ServiceState.STATE_IN_SERVICE,
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
        assertFalse(isSuspended());

        // Return false if in service and concurrent voice/data not allowed but call state is idle
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        mDc.sendMessage(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_STARTED);
        waitForMs(100);
        assertFalse(isSuspended());

        // Return true if in service, concurrent voice/data not allowed, and call state not idle
        doReturn(PhoneConstants.State.RINGING).when(mCT).getState();
        mDc.sendMessage(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_STARTED);
        waitForMs(100);
        assertTrue(isSuspended());
    }

    @Test
    public void testDataCreatedWhenOutOfService() throws Exception {
        serviceStateChangedEvent(ServiceState.STATE_OUT_OF_SERVICE,
                ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN);
        ArgumentCaptor<NetworkCapabilities> ncCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);
        doReturn(mock(Network.class)).when(mConnectivityManager).registerNetworkAgent(
                any(), any(), any(), ncCaptor.capture(), any(), any(), anyInt());

        doReturn(mApn1).when(mApnContext).getApnSetting();
        doReturn(ApnSetting.TYPE_DEFAULT).when(mApnContext).getApnTypeBitmask();
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        connectEvent(true);
        waitForMs(100);

        NetworkCapabilities nc = ncCaptor.getValue();
        // The network must be created with NOT_SUSPENDED capability.
        assertTrue(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED));

        // But it's final state must be suspended.
        assertTrue(isSuspended());
    }

    @Test
    public void testDataServiceTempUnavailable() throws Exception {
        setFailedSetupDataResponse(DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE);
        replaceInstance(ConnectionParams.class, "mRequestType", mCp,
                DcTracker.REQUEST_TYPE_NORMAL);
        // Verify that no data was setup
        connectEvent(false);
        assertTrue(mDc.isInactive());

        // Verify that data service did not suggest any retry (i.e. Frameworks uses configured
        // retry timer).
        verify(mDataThrottler).setRetryTime(eq(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL),
                eq(RetryManager.NO_SUGGESTED_RETRY_DELAY), eq(DcTracker.REQUEST_TYPE_NORMAL));
    }

    @Test
    public void testDataHandoverFailed() throws Exception {
        doReturn(mDefaultDc).when(mDcTracker).getDataConnectionByApnType(anyString());

        doAnswer(invocation -> {
            final Consumer<Integer> consumer = (Consumer<Integer>) invocation.getArguments()[0];
            consumer.accept(DataServiceCallback.RESULT_SUCCESS);
            return null;
        }).when(mDefaultDc).startHandover(any(Consumer.class));

        replaceInstance(ConnectionParams.class, "mRequestType", mCp,
                DcTracker.REQUEST_TYPE_HANDOVER);
        assertTrue(mDc.isInactive());
        connectEvent(false);

        // Make sure the data connection is still in inactive state
        assertTrue(mDc.isInactive());
    }
}
