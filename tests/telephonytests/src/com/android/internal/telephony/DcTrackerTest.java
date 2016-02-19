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

package com.android.internal.telephony;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.LinkProperties;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.ServiceState;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DcTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DcTrackerTest extends TelephonyTest {

    private final static String[] sNetworkAttributes = new String[]{
            "mobile,0,0,0,-1,true", "mobile_mms,2,0,2,60000,true",
            "mobile_supl,3,0,2,60000,true", "mobile_dun,4,0,2,60000,true",
            "mobile_hipri,5,0,3,60000,true", "mobile_fota,10,0,2,60000,true",
            "mobile_ims,11,0,2,60000,true", "mobile_cbs,12,0,2,60000,true",
            "mobile_ia,14,0,2,-1,true", "mobile_emergency,15,0,2,-1,true"};

    private final List<String> sApnTypes = Arrays.asList(
            "default", "mms", "cbs", "fota", "supl", "ia", "emergency", "dun", "hipri", "ims");

    private final String FAKE_APN1 = "FAKE APN 1";
    private final String FAKE_APN2 = "FAKE APN 2";
    private final String FAKE_APN3 = "FAKE APN 3";
    private final String FAKE_IFNAME = "FAKE IFNAME";
    private final String FAKE_PCSCF_ADDRESS = "22.33.44.55";
    private final String FAKE_GATEWAY = "11.22.33.44";
    private final String FAKE_DNS = "55.66.77.88";
    private final String FAKE_ADDRESS = "99.88.77.66";

    private DcTracker mDct;

    private AlarmManager mAlarmManager;

    private final ApnSettingContentProvider mApnSettingContentProvider =
            new ApnSettingContentProvider();

    private class DcTrackerTestHandler extends HandlerThread {

        private DcTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mDct = new DcTracker(mPhone);
            setReady(true);
        }
    }

    private class ApnSettingContentProvider extends MockContentProvider {

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            logd("ApnSettingContentProvider: query");
            logd("   uri = " + uri);
            logd("   projection = " + Arrays.toString(projection));
            logd("   selection = " + selection);
            logd("   selectionArgs = " + Arrays.toString(selectionArgs));
            logd("   sortOrder = " + sortOrder);

            if (uri.compareTo(Telephony.Carriers.CONTENT_URI) == 0) {
                if (projection == null && selectionArgs == null && selection != null) {

                    Pattern pattern = Pattern.compile("^numeric = '([0-9]*)'");
                    Matcher matcher = pattern.matcher(selection);
                    if (!matcher.find()) {
                        logd("Cannot find MCC/MNC from " + selection);
                        return null;
                    }

                    String plmn = matcher.group(1);

                    logd("Query '" + plmn + "' APN settings");
                    MatrixCursor mc = new MatrixCursor(
                            new String[]{Telephony.Carriers._ID, Telephony.Carriers.NUMERIC,
                                    Telephony.Carriers.NAME, Telephony.Carriers.APN,
                                    Telephony.Carriers.PROXY, Telephony.Carriers.PORT,
                                    Telephony.Carriers.MMSC, Telephony.Carriers.MMSPROXY,
                                    Telephony.Carriers.MMSPORT, Telephony.Carriers.USER,
                                    Telephony.Carriers.PASSWORD, Telephony.Carriers.AUTH_TYPE,
                                    Telephony.Carriers.TYPE,
                                    Telephony.Carriers.PROTOCOL,
                                    Telephony.Carriers.ROAMING_PROTOCOL,
                                    Telephony.Carriers.CARRIER_ENABLED, Telephony.Carriers.BEARER,
                                    Telephony.Carriers.BEARER_BITMASK,
                                    Telephony.Carriers.PROFILE_ID,
                                    Telephony.Carriers.MODEM_COGNITIVE,
                                    Telephony.Carriers.MAX_CONNS, Telephony.Carriers.WAIT_TIME,
                                    Telephony.Carriers.MAX_CONNS_TIME, Telephony.Carriers.MTU,
                                    Telephony.Carriers.MVNO_TYPE,
                                    Telephony.Carriers.MVNO_MATCH_DATA});

                    mc.addRow(new Object[]{
                            2163,                   // id
                            plmn,                   // numeric
                            "sp-mode",              // name
                            FAKE_APN1,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "default,supl",         // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            0,                      // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    mc.addRow(new Object[]{
                            2164,                   // id
                            plmn,                   // numeric
                            "mopera U",             // name
                            FAKE_APN2,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "default,supl",         // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            0,                      // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    mc.addRow(new Object[]{
                            2165,                   // id
                            plmn,                   // numeric
                            "b-mobile for Nexus",   // name
                            FAKE_APN3,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            3,                      // authtype
                            "default,supl",         // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            0,                      // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    return mc;
                }
            }

            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DcTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        doReturn("fake.action_attached").when(mPhone).getActionAttached();
        doReturn("44010").when(mSimRecords).getOperatorNumeric();

        mContextFixture.putStringArrayResource(com.android.internal.R.array.networkAttributes,
                sNetworkAttributes);
        mContextFixture.putStringArrayResource(com.android.internal.R.array.
                config_mobile_tcp_buffers, new String[]{
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

        ((MockContentResolver) mContextFixture.getTestDouble().getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mApnSettingContentProvider);

        doReturn(true).when(mSimRecords).getRecordsLoaded();
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        doReturn(true).when(mSST).getDesiredPowerState();

        mAlarmManager = (AlarmManager) mContextFixture.getTestDouble().
                getSystemService(Context.ALARM_SERVICE);

        new DcTrackerTestHandler(getClass().getSimpleName()).start();
        waitUntilReady();
        waitForMs(600);
        logd("DcTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("DcTrackerTest -tearDown");
        mDct.removeCallbacksAndMessages(null);
        mDct = null;
        super.tearDown();
    }

    void verifyDataConnected() {
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS), eq(0), eq(FAKE_APN1),
                eq(""), eq(""), eq(0), eq("IP"), any(Message.class));

        verify(mPhone, times(1)).notifyDataConnection(eq(Phone.REASON_CONNECTED),
                eq(PhoneConstants.APN_TYPE_DEFAULT));

        verify(mAlarmManager, times(1)).set(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), anyLong(),
                any(PendingIntent.class));

        assertEquals(FAKE_APN1, mDct.getActiveApnString(PhoneConstants.APN_TYPE_DEFAULT));
        assertArrayEquals(new String[]{PhoneConstants.APN_TYPE_DEFAULT}, mDct.getActiveApnTypes());
        assertTrue(mDct.getAnyDataEnabled());
        assertTrue(mDct.getDataEnabled());

        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(PhoneConstants.APN_TYPE_DEFAULT));

        LinkProperties linkProperties = mDct.getLinkProperties(PhoneConstants.APN_TYPE_DEFAULT);
        assertEquals(FAKE_IFNAME, linkProperties.getInterfaceName());
        assertEquals(1, linkProperties.getAddresses().size());
        assertEquals(FAKE_ADDRESS, linkProperties.getAddresses().get(0).getHostAddress());
        assertEquals(1, linkProperties.getDnsServers().size());
        assertEquals(FAKE_DNS, linkProperties.getDnsServers().get(0).getHostAddress());
        assertEquals(FAKE_GATEWAY, linkProperties.getRoutes().get(0).getGateway().getHostAddress());
    }

    @Test
    @MediumTest
    public void testDataSetup() {

        DataCallResponse dcResponse = new DataCallResponse();
        dcResponse.version = 11;
        dcResponse.status = 0;
        dcResponse.suggestedRetryTime = -1;
        dcResponse.cid = 1;
        dcResponse.active = 2;
        dcResponse.type = "IP";
        dcResponse.ifname = FAKE_IFNAME;
        dcResponse.mtu = 1440;
        dcResponse.addresses = new String[]{FAKE_ADDRESS};
        dcResponse.dnses = new String[]{FAKE_DNS};
        dcResponse.gateways = new String[]{FAKE_GATEWAY};
        dcResponse.pcscf = new String[]{FAKE_PCSCF_ADDRESS};

        mSimulatedCommands.setDataCallResponse(dcResponse);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        ArgumentCaptor<String> apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_SIM_LOADED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_DATA_ATTACHED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_DATA_ENABLED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        logd("Sending EVENT_ENABLE_NEW_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.setEnabled(0, true);
        waitForMs(200);
        verifyDataConnected();
    }
}