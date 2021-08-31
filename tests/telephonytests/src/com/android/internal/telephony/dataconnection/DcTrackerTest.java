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

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static com.android.internal.telephony.dataconnection.ApnSettingTest.createApnSetting;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.hardware.radio.V1_0.SetupDataCallResult;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicyManager;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.Annotation;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.TrafficDescriptor;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;

import androidx.test.filters.FlakyTest;

import com.android.internal.R;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.DataConnectionReasons.DataDisallowedReasonType;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DcTrackerTest extends TelephonyTest {
    public static final String FAKE_APN1 = "FAKE APN 1";
    public static final String FAKE_APN2 = "FAKE APN 2";
    public static final String FAKE_APN3 = "FAKE APN 3";
    public static final String FAKE_APN4 = "FAKE APN 4";
    public static final String FAKE_APN5 = "FAKE APN 5";
    public static final String FAKE_APN6 = "FAKE APN 6";
    public static final String FAKE_APN7 = "FAKE APN 7";
    public static final String FAKE_APN8 = "FAKE APN 8";
    public static final String FAKE_APN9 = "FAKE APN 9";
    public static final String FAKE_IFNAME = "FAKE IFNAME";
    public static final String FAKE_PCSCF_ADDRESS = "22.33.44.55";
    public static final String FAKE_GATEWAY = "11.22.33.44";
    public static final String FAKE_DNS = "55.66.77.88";
    public static final String FAKE_ADDRESS = "99.88.77.66";
    private static final int NETWORK_TYPE_NR_BITMASK =
            1 << (TelephonyManager.NETWORK_TYPE_NR - 1);
    private static final int NETWORK_TYPE_LTE_BITMASK =
            1 << (TelephonyManager.NETWORK_TYPE_LTE - 1);
    private static final int NETWORK_TYPE_EHRPD_BITMASK =
            1 << (TelephonyManager.NETWORK_TYPE_EHRPD - 1);
    private static final Uri PREFERAPN_URI = Uri.parse(
            Telephony.Carriers.CONTENT_URI + "/preferapn");
    private static final int DATA_ENABLED_CHANGED = 0;
    private static final String FAKE_PLMN = "44010";
    private static final long TEST_TIMEOUT = 1000;

    @Mock
    ISub mIsub;
    @Mock
    IBinder mBinder;
    @Mock
    SubscriptionInfo mSubscriptionInfo;
    @Mock
    ApnContext mApnContext;
    @Mock
    DataConnection mDataConnection;
    @Mock
    Handler mHandler;
    @Mock
    NetworkPolicyManager mNetworkPolicyManager;

    private DcTracker mDct;
    private DcTrackerTestHandler mDcTrackerTestHandler;

    private AlarmManager mAlarmManager;

    private PersistableBundle mBundle;

    private SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangedListener;

    private final ApnSettingContentProvider mApnSettingContentProvider =
            new ApnSettingContentProvider();

    private Message mMessage;

    private CellularDataService mCellularDataService;

    private void addDataService() {
        mCellularDataService = new CellularDataService();
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = "com.android.phone";
        serviceInfo.permission = "android.permission.BIND_TELEPHONY_DATA_SERVICE";
        IntentFilter filter = new IntentFilter();
        mContextFixture.addService(
                DataService.SERVICE_INTERFACE,
                null,
                "com.android.phone",
                mCellularDataService.mBinder,
                serviceInfo,
                filter);
    }

    private class DcTrackerTestHandler extends HandlerThread {

        private DcTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mDct = new DcTracker(mPhone, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            setReady(true);
        }
    }

    private class ApnSettingContentProvider extends MockContentProvider {
        public final String[] FAKE_APN_COLUMNS = new String[]{
                Telephony.Carriers._ID, Telephony.Carriers.NUMERIC,
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
                Telephony.Carriers.MODEM_PERSIST,
                Telephony.Carriers.MAX_CONNECTIONS,
                Telephony.Carriers.WAIT_TIME_RETRY,
                Telephony.Carriers.TIME_LIMIT_FOR_MAX_CONNECTIONS,
                Telephony.Carriers.MTU,
                Telephony.Carriers.MVNO_TYPE,
                Telephony.Carriers.MVNO_MATCH_DATA,
                Telephony.Carriers.NETWORK_TYPE_BITMASK,
                Telephony.Carriers.APN_SET_ID,
                Telephony.Carriers.CARRIER_ID,
                Telephony.Carriers.SKIP_464XLAT
        };

        private int mPreferredApnSet = 0;

        private Object[] mPreferredApn = null;

        private String mFakeApn1Types = "default,supl";

        private String mFakeApn5Types = "dun";

        private int mFakeApn1Bitmask = NETWORK_TYPE_LTE_BITMASK;

        private int mRowIdOffset = 0;

        public void setFakeApn1Types(String apnTypes) {
            mFakeApn1Types = apnTypes;
        }

        public void setFakeApn5Types(String apnTypes) {
            mFakeApn5Types = apnTypes;
        }

        public void setFakeApn1NetworkTypeBitmask(int bitmask) {
            mFakeApn1Bitmask = bitmask;
        }

        public void setRowIdOffset(int rowIdOffset) {
            mRowIdOffset = rowIdOffset;
        }

        public void setFakePreferredApn(Object[] fakeApn) {
            mPreferredApn = fakeApn;
        }

        public Object[] getFakeApn1() {
            return new Object[]{
                    2163 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
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
                    mFakeApn1Types,         // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    mFakeApn1Bitmask,       // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn2() {
            return new Object[]{
                    2164 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
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
                    ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer,
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    NETWORK_TYPE_LTE_BITMASK, // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn3() {
            return new Object[]{
                    2165 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "b-mobile for Nexus",   // name
                    FAKE_APN3,              // apn
                    "",                     // proxy
                    "",                     // port
                    "",                     // mmsc
                    "",                     // mmsproxy
                    "",                     // mmsport
                    "",                     // user
                    "",                     // password
                    -1,                     // authtype
                    "ims",                  // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    0,                      // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    0,                      // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn4() {
            return new Object[]{
                    2166 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "sp-mode ehrpd",        // name
                    FAKE_APN4,              // apn
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
                    ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD, // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    NETWORK_TYPE_EHRPD_BITMASK, // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn5() {
            return new Object[]{
                    2167 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "b-mobile for Nexus",   // name
                    FAKE_APN5,              // apn
                    "",                     // proxy
                    "",                     // port
                    "",                     // mmsc
                    "",                     // mmsproxy
                    "",                     // mmsport
                    "",                     // user
                    "",                     // password
                    -1,                     // authtype
                    mFakeApn5Types,         // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    0,                      // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    0,                      // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn6() {
            return new Object[]{
                    2168 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "sp-mode",              // name
                    FAKE_APN6,              // apn
                    "",                     // proxy
                    "",                     // port
                    "",                     // mmsc
                    "",                     // mmsproxy
                    "",                     // mmsport
                    "",                     // user
                    "",                     // password
                    -1,                     // authtype
                    "mms,xcap",             // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    NETWORK_TYPE_LTE_BITMASK, // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn7() {
            return new Object[]{
                    2169 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "sp-mode",              // name
                    FAKE_APN7,              // apn
                    "",                     // proxy
                    "",                     // port
                    "",                     // mmsc
                    "",                     // mmsproxy
                    "",                     // mmsport
                    "",                     // user
                    "",                     // password
                    -1,                     // authtype
                    "default",              // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    NETWORK_TYPE_LTE_BITMASK,  // network_type_bitmask
                    1,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn8() {
            return new Object[]{
                    2170 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "IMS",                  // name
                    FAKE_APN8,              // apn
                    "",                     // proxy
                    "",                     // port
                    "",                     // mmsc
                    "",                     // mmsproxy
                    "",                     // mmsport
                    "",                     // user
                    "",                     // password
                    -1,                     // authtype
                    "ims",                  // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    NETWORK_TYPE_LTE_BITMASK,  // network_type_bitmask
                    -1,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        public Object[] getFakeApn9() {
            return new Object[]{
                    2171 + mRowIdOffset,    // id
                    FAKE_PLMN,              // numeric
                    "sp-mode nr",           // name
                    FAKE_APN9,              // apn
                    "",                     // proxy
                    "",                     // port
                    "",                     // mmsc
                    "",                     // mmsproxy
                    "",                     // mmsport
                    "",                     // user
                    "",                     // password
                    -1,                     // authtype
                    "default,enterprise",   // types
                    "IP",                   // protocol
                    "IP",                   // roaming_protocol
                    1,                      // carrier_enabled
                    ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer
                    0,                      // bearer_bitmask
                    0,                      // profile_id
                    1,                      // modem_cognitive
                    0,                      // max_conns
                    0,                      // wait_time
                    0,                      // max_conns_time
                    0,                      // mtu
                    "",                     // mvno_type
                    "",                     // mnvo_match_data
                    NETWORK_TYPE_NR_BITMASK, // network_type_bitmask
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1                      // skip_464xlat
            };
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            logd("ApnSettingContentProvider: query");
            logd("   uri = " + uri);
            logd("   projection = " + Arrays.toString(projection));
            logd("   selection = " + selection);
            logd("   selectionArgs = " + Arrays.toString(selectionArgs));
            logd("   sortOrder = " + sortOrder);

            if (uri.compareTo(Telephony.Carriers.CONTENT_URI) == 0
                    || uri.toString().startsWith(Uri.withAppendedPath(
                            Telephony.Carriers.CONTENT_URI, "filtered").toString())
                    || uri.toString().startsWith(Uri.withAppendedPath(
                            Telephony.Carriers.SIM_APN_URI, "filtered").toString())) {
                if (projection == null) {

                    logd("Query '" + FAKE_PLMN + "' APN settings");
                    MatrixCursor mc = new MatrixCursor(FAKE_APN_COLUMNS);
                    mc.addRow(getFakeApn1());
                    mc.addRow(getFakeApn2());
                    mc.addRow(getFakeApn3());
                    mc.addRow(getFakeApn4());
                    mc.addRow(getFakeApn5());
                    mc.addRow(getFakeApn6());
                    mc.addRow(getFakeApn7());
                    mc.addRow(getFakeApn8());
                    mc.addRow(getFakeApn9());

                    return mc;
                }
            } else if (isPathPrefixMatch(uri,
                    Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "preferapnset"))) {
                MatrixCursor mc = new MatrixCursor(
                        new String[]{Telephony.Carriers.APN_SET_ID});
                // apn_set_id is the only field used with this URL
                mc.addRow(new Object[]{ mPreferredApnSet });
                mc.addRow(new Object[]{ 0 });
                return mc;
            } else if (isPathPrefixMatch(uri,
                    Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "preferapn_no_update"))) {
                if (mPreferredApn == null) {
                    return null;
                } else {
                    MatrixCursor mc = new MatrixCursor(FAKE_APN_COLUMNS);
                    mc.addRow(mPreferredApn);
                    return mc;
                }
            }

            return null;
        }

        @Override
        public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
            mPreferredApnSet = values.getAsInteger(Telephony.Carriers.APN_SET_ID);
            return 1;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DcTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        doReturn("fake.action_attached").when(mPhone).getActionAttached();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_LTE).when(mServiceState)
                .getRilDataRadioTechnology();

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

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mApnSettingContentProvider);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK, 0);

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mTransportManager)
                .getPreferredTransport(anyInt());
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        doReturn(true).when(mSST).getDesiredPowerState();
        doReturn(true).when(mSST).getPowerStateFromCarrier();
        doAnswer(
                new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        mOnSubscriptionsChangedListener =
                                (SubscriptionManager.OnSubscriptionsChangedListener)
                                        invocation.getArguments()[0];
                        return null;
                    }
                }
        ).when(mSubscriptionManager).addOnSubscriptionsChangedListener(any());
        doReturn(mSubscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(mNetworkPolicyManager).when(mContext)
                .getSystemService(Context.NETWORK_POLICY_SERVICE);
        doReturn(1).when(mIsub).getDefaultDataSubId();
        doReturn(mIsub).when(mBinder).queryLocalInterface(anyString());
        mServiceManagerMockedServices.put("isub", mBinder);

        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_cell_retries_per_error_code,
                new String[]{"36,2"});

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mBundle = mContextFixture.getCarrierConfigBundle();

        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);

        mSimulatedCommands.setDataCallResult(true, createSetupDataCallResult());
        addDataService();

        mDcTrackerTestHandler = new DcTrackerTestHandler(getClass().getSimpleName());
        mDcTrackerTestHandler.start();
        waitUntilReady();

        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);

        waitForMs(600);
        logd("DcTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("DcTrackerTest -tearDown");
        mDct.removeCallbacksAndMessages(null);
        mDct.stopHandlerThread();
        mDct = null;
        mDcTrackerTestHandler.quit();
        mDcTrackerTestHandler.join();
        mCellularDataService.onDestroy();
        waitForMs(100);
        super.tearDown();
    }

    // Create a successful data response
    private static SetupDataCallResult createSetupDataCallResult() {
        SetupDataCallResult result = new SetupDataCallResult();
        result.status = 0;
        result.suggestedRetryTime = -1;
        result.cid = 1;
        result.active = 2;
        result.type = "IP";
        result.ifname = FAKE_IFNAME;
        result.addresses = FAKE_ADDRESS;
        result.dnses = FAKE_DNS;
        result.gateways = FAKE_GATEWAY;
        result.pcscf = FAKE_PCSCF_ADDRESS;
        result.mtu = 1440;
        return result;
    }

    private void verifyDataProfile(DataProfile dp, String apn, int profileId,
                                   int supportedApnTypesBitmap, int type, int bearerBitmask) {
        assertEquals(profileId, dp.getProfileId());
        assertEquals(apn, dp.getApn());
        assertEquals(ApnSetting.PROTOCOL_IP, dp.getProtocolType());
        assertEquals(0, dp.getAuthType());
        assertEquals("", dp.getUserName());
        assertEquals("", dp.getPassword());
        assertEquals(type, dp.getType());
        assertEquals(0, dp.getWaitTime());
        assertTrue(dp.isEnabled());
        assertEquals(supportedApnTypesBitmap, dp.getSupportedApnTypesBitmask());
        assertEquals(ApnSetting.PROTOCOL_IP, dp.getRoamingProtocolType());
        assertEquals(bearerBitmask, dp.getBearerBitmask());
        assertEquals(0, dp.getMtu());
        assertTrue(dp.isPersistent());
        assertFalse(dp.isPreferred());
    }

    private void verifyDataConnected(final String apnSetting) {
        verify(mAlarmManager, times(1)).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                any(PendingIntent.class));

        assertEquals(apnSetting, mDct.getActiveApnString(ApnSetting.TYPE_DEFAULT_STRING));
        assertArrayEquals(new String[]{ApnSetting.TYPE_DEFAULT_STRING}, mDct.getActiveApnTypes());

        assertTrue(mDct.isAnyDataConnected());
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));

        LinkProperties linkProperties = mDct.getLinkProperties(ApnSetting.TYPE_DEFAULT_STRING);
        assertEquals(FAKE_IFNAME, linkProperties.getInterfaceName());
        assertEquals(1, linkProperties.getAddresses().size());
        assertEquals(FAKE_ADDRESS, linkProperties.getAddresses().get(0).getHostAddress());
        assertEquals(1, linkProperties.getDnsServers().size());
        assertEquals(FAKE_DNS, linkProperties.getDnsServers().get(0).getHostAddress());
        assertEquals(FAKE_GATEWAY, linkProperties.getRoutes().get(0).getGateway().getHostAddress());
    }

    private boolean isHandoverPending(int apnType) {
        try {
            Method method = DcTracker.class.getDeclaredMethod("isHandoverPending",
                    int.class);
            method.setAccessible(true);
            return (boolean) method.invoke(mDct, apnType);
        } catch (Exception e) {
            fail(e.toString());
            return false;
        }
    }

    private void addHandoverCompleteMsg(Message onCompleteMsg,
            @Annotation.ApnType int apnType) {
        try {
            Method method = DcTracker.class.getDeclaredMethod("addHandoverCompleteMsg",
                    Message.class, int.class);
            method.setAccessible(true);
            method.invoke(mDct, onCompleteMsg, apnType);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    private void sendInitializationEvents() {
        sendCarrierConfigChanged("");

        sendSimStateUpdated("");

        sendEventDataConnectionAttached("");

        waitForMs(200);
    }

    private void sendCarrierConfigChanged(String messagePrefix) {
        logd(messagePrefix + "Sending EVENT_CARRIER_CONFIG_CHANGED");
        mDct.sendEmptyMessage(DctConstants.EVENT_CARRIER_CONFIG_CHANGED);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    private void sendSimStateUpdated(String messagePrefix) {
        logd(messagePrefix + "Sending EVENT_SIM_STATE_UPDATED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_SIM_STATE_UPDATED,
                TelephonyManager.SIM_STATE_LOADED, 0));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    private void sendEventDataConnectionAttached(String messagePrefix) {
        logd(messagePrefix + "Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    // Test the unmetered APN setup when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupDataUnmeteredDefaultNotSelected() throws Exception {
        initApns(ApnSetting.TYPE_XCAP_STRING, new String[]{ApnSetting.TYPE_XCAP_STRING});
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mIsub).getDefaultDataSubId();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        sendInitializationEvents();

        mDct.enableApn(ApnSetting.TYPE_XCAP, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the normal data call setup scenario.
    @Test
    @MediumTest
    public void testDataSetup() throws Exception {
        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = mDct.isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);

        logd("Sending EVENT_ENABLE_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        sendInitializationEvents();

        dataConnectionReasons = new DataConnectionReasons();
        allowed = mDct.isDataAllowed(dataConnectionReasons);
        assertTrue(dataConnectionReasons.toString(), allowed);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        verifyDataConnected(FAKE_APN1);
    }

    // Test the scenario where the first data call setup is failed, and then retry the setup later.
    @Test
    @MediumTest
    public void testDataRetry() throws Exception {
        AsyncResult ar = new AsyncResult(null,
                new Pair<>(true, DataEnabledSettings.REASON_USER_DATA_ENABLED), null);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_CHANGED, ar));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // LOST_CONNECTION(0x10004) is a non-permanent failure, so we'll retry data setup later.
        SetupDataCallResult result = createSetupDataCallResult();
        result.status = 0x10004;

        // Simulate RIL fails the data call setup
        mSimulatedCommands.setDataCallResult(true, result);

        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = mDct.isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);

        logd("Sending EVENT_ENABLE_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        sendInitializationEvents();

        dataConnectionReasons = new DataConnectionReasons();
        allowed = mDct.isDataAllowed(dataConnectionReasons);
        assertTrue(dataConnectionReasons.toString(), allowed);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        // This time we'll let RIL command succeed.
        mSimulatedCommands.setDataCallResult(true, createSetupDataCallResult());

        //Send event for reconnecting data
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RECONNECT,
                        mPhone.getPhoneId(), DcTracker.REQUEST_TYPE_NORMAL, mApnContext));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN2, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        // Verify connected with APN2 setting.
        verifyDataConnected(FAKE_APN2);
    }

    @Test
    @MediumTest
    @Ignore
    @FlakyTest
    public void testUserDisableData() {
        //step 1: setup two DataCalls one for Metered: default, another one for Non-metered: IMS
        //set Default and MMS to be metered in the CarrierConfigManager
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending DATA_DISABLED_CMD");
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());
        AsyncResult ar = new AsyncResult(null,
                new Pair<>(false, DataEnabledSettings.REASON_USER_DATA_ENABLED), null);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_CHANGED, ar));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // expected tear down all metered DataConnections
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));
        assertTrue(mDct.isAnyDataConnected());
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_IMS_STRING));
    }

    @Test
    @MediumTest
    public void testTrySetupDataMmsAllowedDataDisabled() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_MMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));

        List<DataProfile> dataProfiles = dpCaptor.getAllValues();
        assertEquals(2, dataProfiles.size());

        //Verify FAKE_APN1
        Optional<DataProfile> fakeApn1 = dataProfiles.stream()
                .filter(dp -> dp.getApn().equals(FAKE_APN1))
                .findFirst();
        assertTrue(fakeApn1.isPresent());
        verifyDataProfile(fakeApn1.get(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        //Verify FAKE_APN6
        Optional<DataProfile> fakeApn6 = dataProfiles.stream()
                .filter(dp -> dp.getApn().equals(FAKE_APN6))
                .findFirst();
        assertTrue(fakeApn6.isPresent());
        verifyDataProfile(fakeApn6.get(), FAKE_APN6, 0, ApnSetting.TYPE_MMS | ApnSetting.TYPE_XCAP,
                1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending DATA_DISABLED_CMD for default data");
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());
        mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_OVERRIDE_RULES_CHANGED).sendToTarget();
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // expected tear down all metered DataConnections
        verify(mSimulatedCommandsVerifier, times(2)).deactivateDataCall(
                anyInt(), eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_MMS_STRING));

        clearInvocations(mSimulatedCommandsVerifier);
        doReturn(true).when(mDataEnabledSettings).isDataEnabled(ApnSetting.TYPE_MMS);
        mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_OVERRIDE_RULES_CHANGED).sendToTarget();
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_MMS_STRING));
    }

    @Test
    @MediumTest
    public void testTrySetupDataMmsAlwaysAllowedDataDisabled() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mApnSettingContentProvider.setFakeApn1Types("mms,xcap,default");
        mDct.enableApn(ApnSetting.TYPE_MMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();

        // Verify MMS was set up and is connected
        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0,
                ApnSetting.TYPE_MMS | ApnSetting.TYPE_XCAP | ApnSetting.TYPE_DEFAULT,
                1, NETWORK_TYPE_LTE_BITMASK);
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_MMS_STRING));

        // Verify DC has all capabilities specified in fakeApn1Types
        Map<Integer, ApnContext> apnContexts = mDct.getApnContexts().stream().collect(
                Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertTrue(apnContexts.get(ApnSetting.TYPE_MMS).getDataConnection()
                .getNetworkCapabilities().hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS));
        assertTrue(apnContexts.get(ApnSetting.TYPE_MMS).getDataConnection()
                .getNetworkCapabilities().hasCapability(NetworkCapabilities.NET_CAPABILITY_XCAP));
        assertTrue(apnContexts.get(ApnSetting.TYPE_MMS).getDataConnection()
                .getNetworkCapabilities().hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET));

        // Disable mobile data
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());
        doReturn(false).when(mDataEnabledSettings).isMmsAlwaysAllowed();
        mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_OVERRIDE_RULES_CHANGED).sendToTarget();
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Expected tear down all metered DataConnections
        waitForMs(200);
        verify(mSimulatedCommandsVerifier).deactivateDataCall(
                anyInt(), eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_MMS_STRING));

        // Allow MMS unconditionally
        clearInvocations(mSimulatedCommandsVerifier);
        doReturn(true).when(mDataEnabledSettings).isMmsAlwaysAllowed();
        doReturn(true).when(mDataEnabledSettings).isDataEnabled(ApnSetting.TYPE_MMS);
        mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_OVERRIDE_RULES_CHANGED).sendToTarget();
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Verify MMS was set up and is connected
        waitForMs(200);
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_MMS_STRING));

        // Ensure MMS data connection has the MMS capability only.
        apnContexts = mDct.getApnContexts().stream().collect(
                Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertTrue(apnContexts.get(ApnSetting.TYPE_MMS).getDataConnection()
                .getNetworkCapabilities().hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS));
        assertFalse(apnContexts.get(ApnSetting.TYPE_MMS).getDataConnection()
                .getNetworkCapabilities().hasCapability(NetworkCapabilities.NET_CAPABILITY_XCAP));
        assertFalse(apnContexts.get(ApnSetting.TYPE_MMS).getDataConnection()
                .getNetworkCapabilities().hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET));
    }

    @Test
    @MediumTest
    public void testUserDisableRoaming() {
        //step 1: setup two DataCalls one for Metered: default, another one for Non-metered: IMS
        //step 2: set roaming disabled, data is enabled
        //step 3: under roaming service
        //step 4: only tear down metered data connections.

        //set Default and MMS to be metered in the CarrierConfigManager
        boolean roamingEnabled = mDct.getDataRoamingEnabled();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});

        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForHandlerAction(mDct, 1000);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForHandlerAction(mDct, 1000);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        //user is in roaming
        doReturn(true).when(mServiceState).getDataRoaming();
        logd("Sending DISABLE_ROAMING_CMD");
        mDct.setDataRoamingEnabledByUser(false);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_ROAMING_ON));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // expected tear down all metered DataConnections
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));
        assertTrue(mDct.isAnyDataConnected());
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_IMS_STRING));

        // reset roaming settings / data enabled settings at end of this test
        mDct.setDataRoamingEnabledByUser(roamingEnabled);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    @Test
    @MediumTest
    public void testDataCallOnUserDisableRoaming() {
        //step 1: mock under roaming service and user disabled roaming from settings.
        //step 2: user toggled data settings on
        //step 3: only non-metered data call is established

        boolean roamingEnabled = mDct.getDataRoamingEnabled();
        doReturn(true).when(mServiceState).getDataRoaming();

        //set Default and MMS to be metered in the CarrierConfigManager
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        logd("Sending DISABLE_ROAMING_CMD");
        mDct.setDataRoamingEnabledByUser(false);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN3, 2, 64, 0, 0);

        assertTrue(mDct.isAnyDataConnected());
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_IMS_STRING));

        // reset roaming settings / data enabled settings at end of this test
        mDct.setDataRoamingEnabledByUser(roamingEnabled);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    // Test the default data switch scenario.
    @FlakyTest /* flakes 1.57% of the time */
    @Test
    @MediumTest
    public void testDDSResetAutoAttach() throws Exception {
        mContextFixture.putBooleanResource(
                com.android.internal.R.bool.config_auto_attach_data_on_creation, true);
        testDataSetup();
        assertTrue(mDct.shouldAutoAttach());
        mDct.sendEmptyMessage(DctConstants.EVENT_CARRIER_CONFIG_CHANGED);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        // The auto attach flag should be reset after update
        assertFalse(mDct.shouldAutoAttach());
    }

    // Test for API carrierActionSetMeteredApnsEnabled.
    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testCarrierActionSetMeteredApnsEnabled() {
        //step 1: setup two DataCalls one for Internet and IMS
        //step 2: set data is enabled
        //step 3: cold sim is detected
        //step 4: all data connection is torn down
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});

        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, NETWORK_TYPE_LTE_BITMASK);
        assertTrue(mDct.isAnyDataConnected());

        AsyncResult ar = new AsyncResult(null,
                new Pair<>(false, DataEnabledSettings.REASON_DATA_ENABLED_BY_CARRIER), null);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_ENABLED_CHANGED, ar));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // Validate all metered data connections have been torn down
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));
        assertTrue(mDct.isAnyDataConnected());
        assertEquals(DctConstants.State.IDLE, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
    }

    private void initApns(String targetApn, String[] canHandleTypes) {
        doReturn(targetApn).when(mApnContext).getApnType();
        doReturn(ApnSetting.getApnTypesBitmaskFromString(mApnContext.getApnType()))
                .when(mApnContext).getApnTypeBitmask();
        doReturn(true).when(mApnContext).isConnectable();
        ApnSetting apnSetting = createApnSetting(ApnSetting.getApnTypesBitmaskFromString(
                TextUtils.join(",", canHandleTypes)));
        doReturn(apnSetting).when(mApnContext).getNextApnSetting();
        doReturn(apnSetting).when(mApnContext).getApnSetting();
        doReturn(mDataConnection).when(mApnContext).getDataConnection();
        doReturn(true).when(mApnContext).isEnabled();
        doReturn(true).when(mApnContext).isDependencyMet();
        doReturn(true).when(mApnContext).isReady();
        doReturn(false).when(mApnContext).hasRestrictedRequests(eq(true));
    }

    // Test the emergency APN setup.
    @Test
    @SmallTest
    public void testTrySetupDataEmergencyApn() {
        initApns(ApnSetting.TYPE_EMERGENCY_STRING,
                new String[]{ApnSetting.TYPE_EMERGENCY_STRING});
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));
    }

    // Test the XCAP APN setup.
    @Test
    @SmallTest
    public void testTrySetupDataXcapApn() {
        initApns(ApnSetting.TYPE_XCAP_STRING, new String[]{ApnSetting.TYPE_XCAP_STRING});
        mDct.enableApn(ApnSetting.TYPE_XCAP, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));
    }

    // Test the ENTERPRISE APN setup.
    @Test
    public void testTrySetupDataEnterpriseApn() {
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();

        ArgumentCaptor<TrafficDescriptor> tdCaptor =
                ArgumentCaptor.forClass(TrafficDescriptor.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(FAKE_APN1, tdCaptor.getValue().getDataNetworkName());
        assertEquals(null, tdCaptor.getValue().getOsAppId());

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        SetupDataCallResult result = createSetupDataCallResult();
        result.cid = 10;
        mSimulatedCommands.setDataCallResult(true, result);
        mDct.enableApn(ApnSetting.TYPE_ENTERPRISE, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.NGRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(null, tdCaptor.getValue().getDataNetworkName());
        assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(),
                tdCaptor.getValue().getOsAppId()));
    }

    // Test the ENTERPRISE APN setup when default data is not set up yet.
    @Test
    public void testTrySetupDataEnterpriseApnNoDefaultData() {
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mDct.enableApn(ApnSetting.TYPE_ENTERPRISE, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();

        ArgumentCaptor<TrafficDescriptor> tdCaptor =
                ArgumentCaptor.forClass(TrafficDescriptor.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.NGRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(null, tdCaptor.getValue().getDataNetworkName());
        assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(),
                tdCaptor.getValue().getOsAppId()));

        // Check APN contexts with no DEFAULT set up
        Map<Integer, ApnContext> apnContexts = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertEquals(DctConstants.State.IDLE, apnContexts.get(ApnSetting.TYPE_DEFAULT).getState());
        assertEquals(DctConstants.State.FAILED,
                apnContexts.get(ApnSetting.TYPE_ENTERPRISE).getState());

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(FAKE_APN1, tdCaptor.getValue().getDataNetworkName());
        assertEquals(null, tdCaptor.getValue().getOsAppId());

        // Check APN contexts after DEFAULT is set up (and ENTERPRISE failure)
        apnContexts = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertEquals(DctConstants.State.CONNECTED,
                apnContexts.get(ApnSetting.TYPE_DEFAULT).getState());
        assertEquals(DctConstants.State.FAILED,
                apnContexts.get(ApnSetting.TYPE_ENTERPRISE).getState());

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        SetupDataCallResult result = createSetupDataCallResult();
        result.cid = 10;
        mSimulatedCommands.setDataCallResult(true, result);
        mDct.enableApn(ApnSetting.TYPE_ENTERPRISE, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.NGRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(null, tdCaptor.getValue().getDataNetworkName());
        assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(),
                tdCaptor.getValue().getOsAppId()));

        // Check APN contexts after DEFAULT is set up (and ENTERPRISE reenabled)
        apnContexts = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertEquals(DctConstants.State.CONNECTED,
                apnContexts.get(ApnSetting.TYPE_DEFAULT).getState());
        assertEquals(DctConstants.State.CONNECTED,
                apnContexts.get(ApnSetting.TYPE_ENTERPRISE).getState());
    }

    // Test the ENTERPRISE APN setup when the same CID is returned.
    @Test
    public void testTrySetupDataEnterpriseApnDuplicateCid() {
        mApnSettingContentProvider.setFakeApn1NetworkTypeBitmask(
                NETWORK_TYPE_LTE_BITMASK | NETWORK_TYPE_NR_BITMASK);
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        // mSimulatedCommandsVerifier will return the same CID in SetupDataCallResult
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_ENTERPRISE, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();
        waitForMs(200);

        ArgumentCaptor<TrafficDescriptor> tdCaptor =
                ArgumentCaptor.forClass(TrafficDescriptor.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.NGRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        List<TrafficDescriptor> tds = tdCaptor.getAllValues();
        // [0] is default and [1] is enterprise, since default should be set up first
        assertEquals(FAKE_APN1, tds.get(0).getDataNetworkName());
        assertEquals(null, tds.get(0).getOsAppId());
        assertEquals(null, tds.get(1).getDataNetworkName());
        assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(), tds.get(1).getOsAppId()));

        // Check APN contexts after DEFAULT and ENTERPRISE set up
        Map<Integer, ApnContext> apnContexts = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertEquals(DctConstants.State.CONNECTED,
                apnContexts.get(ApnSetting.TYPE_DEFAULT).getState());
        assertEquals(DctConstants.State.FAILED,
                apnContexts.get(ApnSetting.TYPE_ENTERPRISE).getState());
    }

    @Test
    @SmallTest
    public void testGetDataConnectionState() {
        initApns(ApnSetting.TYPE_SUPL_STRING,
                new String[]{ApnSetting.TYPE_SUPL_STRING, ApnSetting.TYPE_DEFAULT_STRING});
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_SUPL, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        // Assert that both APN_TYPE_SUPL & APN_TYPE_DEFAULT are connected even we only setup data
        // for APN_TYPE_SUPL
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_SUPL_STRING));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(ApnSetting.TYPE_DEFAULT_STRING));
    }

    // Test the unmetered APN setup when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupDataUnmeteredDataDisabled() {
        initApns(ApnSetting.TYPE_SUPL_STRING, new String[]{ApnSetting.TYPE_SUPL_STRING});
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_FOTA_STRING});

        mDct.enableApn(ApnSetting.TYPE_SUPL, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the unmetered default APN setup when data is disabled. Default APN should always honor
    // the users's setting.
    @Test
    @SmallTest
    public void testTrySetupDataUnmeteredDefaultDataDisabled() {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_MMS_STRING});

        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, never()).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }


    // Test the metered APN setup when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupMeteredDataDisabled() {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(anyInt(), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the restricted data request when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupRestrictedDataDisabled() {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        doReturn(false).when(mDataEnabledSettings).isDataEnabled();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        sendInitializationEvents();

        NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build();
        mDct.requestNetwork(nr, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(anyInt(), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the restricted data request when roaming is disabled.
    @Test
    @SmallTest
    public void testTrySetupRestrictedRoamingDisabled() {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        mDct.setDataRoamingEnabledByUser(false);
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        //user is in roaming
        doReturn(true).when(mServiceState).getDataRoaming();

        sendInitializationEvents();

        NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build();
        mDct.requestNetwork(nr, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(anyInt(), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the default data when data is not connectable.
    @Test
    @SmallTest
    public void testTrySetupNotConnectable() {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        doReturn(false).when(mApnContext).isConnectable();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(anyInt(), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the default data on IWLAN.
    @Test
    @SmallTest
    public void testTrySetupDefaultOnIWLAN() {
        doReturn(true).when(mTransportManager).isInLegacyMode();
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(anyInt(), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test the default data when the phone is in ECBM.
    @Test
    @SmallTest
    public void testTrySetupDefaultInECBM() {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        doReturn(true).when(mPhone).isInEcm();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        sendInitializationEvents();

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(anyInt(), any(DataProfile.class),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    // Test update waiting apn list when on data rat change
    @FlakyTest /* flakes 0.86% of the time */
    @Ignore
    @Test
    @SmallTest
    public void testUpdateWaitingApnListOnDataRatChange() {
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_EHRPD)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(AccessNetworkType.CDMA2000), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN4, 0, 21, 2, NETWORK_TYPE_EHRPD_BITMASK);
        assertTrue(mDct.isAnyDataConnected());

        //data rat change from ehrpd to lte
        logd("Sending EVENT_DATA_RAT_CHANGED");
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // Verify the disconnected data call due to rat change and retry manger schedule another
        // data call setup
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));
        verify(mAlarmManager, times(1)).setExact(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                anyLong(), any(PendingIntent.class));

        //Send event for reconnecting data
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RECONNECT,
                        mPhone.getPhoneId(), DcTracker.RELEASE_TYPE_NORMAL, mApnContext));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);
        assertTrue(mDct.isAnyDataConnected());
    }

    // Test for fetchDunApns()
    @Test
    @SmallTest
    public void testFetchDunApn() {

        sendInitializationEvents();

        String dunApnString = "[ApnSettingV3]HOT mobile PC,pc.hotm,,,,,,,,,440,10,,DUN,,,true,"
                + "0,,,,,,,,";
        ApnSetting dunApnExpected = ApnSetting.fromString(dunApnString);

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_APN, dunApnString);
        // should return APN from Setting
        ApnSetting dunApn = mDct.fetchDunApns().get(0);
        assertTrue(dunApnExpected.equals(dunApn));

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_APN, null);
        // should return APN from db
        dunApn = mDct.fetchDunApns().get(0);
        assertEquals(FAKE_APN5, dunApn.getApnName());
    }

    // Test for fetchDunApns() with apn set id
    @Test
    @SmallTest
    public void testFetchDunApnWithPreferredApnSet() {
        sendCarrierConfigChanged("testFetchDunApnWithPreferredApnSet: ");

        // apnSetId=1
        String dunApnString1 = "[ApnSettingV5]HOT mobile PC,pc.hotm,,,,,,,,,440,10,,DUN,,,true,"
                + "0,,,,,,,,,,1";
        // apnSetId=0
        String dunApnString2 = "[ApnSettingV5]HOT mobile PC,pc.coldm,,,,,,,,,440,10,,DUN,,,true,"
                + "0,,,,,,,,,,2";

        ApnSetting dunApnExpected = ApnSetting.fromString(dunApnString1);

        ContentResolver cr = mContext.getContentResolver();
        Settings.Global.putString(cr, Settings.Global.TETHER_DUN_APN,
                dunApnString1 + ";" + dunApnString2);

        // set that we prefer apn set 1
        ContentValues values = new ContentValues();
        values.put(Telephony.Carriers.APN_SET_ID, 1);
        cr.update(PREFERAPN_URI, values, null, null);

        // return APN from Setting with apnSetId=1
        ArrayList<ApnSetting> dunApns = mDct.fetchDunApns();
        assertEquals(1, dunApns.size());
        assertEquals(1, dunApns.get(0).getApnSetId());
        assertTrue(dunApnExpected.equals(dunApns.get(0)));

        // set that we prefer apn set 2
        values = new ContentValues();
        values.put(Telephony.Carriers.APN_SET_ID, 2);
        cr.update(PREFERAPN_URI, values, null, null);

        // return APN from Setting with apnSetId=2
        dunApns = mDct.fetchDunApns();
        assertEquals(1, dunApns.size());
        assertEquals(2, dunApns.get(0).getApnSetId());
        dunApnExpected = ApnSetting.fromString(dunApnString2);
        assertTrue(dunApnExpected.equals(dunApns.get(0)));
    }

    @Test
    @SmallTest
    public void testFetchDunApnWhileRoaming() {
        doReturn(true).when(mServiceState).getRoaming();
        mBundle.putBoolean(CarrierConfigManager
                .KEY_DISABLE_DUN_APN_WHILE_ROAMING_WITH_PRESET_APN_BOOL, true);

        sendInitializationEvents();

        String dunApnString = "[ApnSettingV3]HOT mobile PC,pc.hotm,,,,,,,,,440,10,,DUN,,,true,"
                + "0,,,,,,,,";

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_APN, dunApnString);

        DcTracker spyDct = spy(mDct);
        doReturn(true).when(spyDct).isPreferredApnUserEdited();
        // Expect non-empty DUN APN list
        assertEquals(1, spyDct.fetchDunApns().size());

        doReturn(false).when(spyDct).isPreferredApnUserEdited();
        // Expect empty DUN APN list
        assertEquals(0, spyDct.fetchDunApns().size());

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_APN, null);
    }

    /**
     * Test that fetchDunApns() returns list that prioritize the preferred APN when the preferred
     * APN including DUN type.
     */
    @Test
    public void testFetchDunApnWithPreferredApn() {
        // Set support APN types of FAKE_APN1 and FAKE_APN5
        mApnSettingContentProvider.setFakeApn1Types("default,dun");
        mApnSettingContentProvider.setFakeApn5Types("default,dun");

        // Set prefer apn set id.
        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Telephony.Carriers.APN_SET_ID, 0);
        cr.update(PREFERAPN_URI, values, null, null);
        // Set FAKE_APN5 as the preferred APN.
        mApnSettingContentProvider.setFakePreferredApn(mApnSettingContentProvider.getFakeApn5());

        sendInitializationEvents();

        // Return the APN list that set the preferred APN at the top.
        ArrayList<ApnSetting> dunApns = mDct.fetchDunApns();
        assertEquals(2, dunApns.size());
        assertEquals(FAKE_APN5, dunApns.get(0).getApnName());
        assertEquals(FAKE_APN1, dunApns.get(1).getApnName());
    }

    // This tests simulates the race case where the sim status change event is triggered, the
    // default data connection is attached, and then the carrier config gets changed which bumps
    // the database id which we want to ignore when cleaning up connections and matching against
    // the dun APN.  Tests b/158908392.
    @Test
    @SmallTest
    public void testCheckForCompatibleDataConnectionWithDunWhenIdsChange() {
        //Set dun as a support apn type of FAKE_APN1
        mApnSettingContentProvider.setFakeApn1Types("default,supl,dun");

        // Enable the default apn
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        //Load the sim and attach the data connection without firing the carrier changed event
        final String logMsgPrefix = "testCheckForCompatibleDataConnectionWithDunWhenIdsChange: ";
        sendSimStateUpdated(logMsgPrefix);
        sendEventDataConnectionAttached(logMsgPrefix);
        waitForMs(200);

        // Confirm that FAKE_APN1 comes up as a dun candidate
        ApnSetting dunApn = mDct.fetchDunApns().get(0);
        assertEquals(dunApn.getApnName(), FAKE_APN1);
        Map<Integer, ApnContext> apnContexts = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));

        //Double check that the default apn content is connected while the dun apn context is not
        assertEquals(apnContexts.get(ApnSetting.TYPE_DEFAULT).getState(),
                DctConstants.State.CONNECTED);
        assertNotEquals(apnContexts.get(ApnSetting.TYPE_DUN).getState(),
                DctConstants.State.CONNECTED);


        //Change the row ids the same way as what happens when we have old apn values in the
        //carrier table
        mApnSettingContentProvider.setRowIdOffset(100);
        sendCarrierConfigChanged(logMsgPrefix);
        waitForMs(200);

        mDct.enableApn(ApnSetting.TYPE_DUN, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        Map<Integer, ApnContext> apnContextsAfterRowIdsChanged = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));

        //Make sure that the data connection used earlier wasn't cleaned up and still in use.
        assertEquals(apnContexts.get(ApnSetting.TYPE_DEFAULT).getDataConnection(),
                apnContextsAfterRowIdsChanged.get(ApnSetting.TYPE_DEFAULT).getDataConnection());

        //Check that the DUN is using the same active data connection
        assertEquals(apnContexts.get(ApnSetting.TYPE_DEFAULT).getDataConnection(),
                apnContextsAfterRowIdsChanged.get(ApnSetting.TYPE_DUN).getDataConnection());
    }

    @Test
    @SmallTest
    public void testCheckForCompatibleDataConnectionWithEnterprise() {
        // Allow both DEFAULT and ENTERPRISE to use APN 1
        mApnSettingContentProvider.setFakeApn1NetworkTypeBitmask(
                NETWORK_TYPE_LTE_BITMASK | NETWORK_TYPE_NR_BITMASK);

        // Enable the DEFAULT APN
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        sendInitializationEvents();

        ArgumentCaptor<TrafficDescriptor> tdCaptor =
                ArgumentCaptor.forClass(TrafficDescriptor.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(FAKE_APN1, tdCaptor.getValue().getDataNetworkName());
        assertEquals(null, tdCaptor.getValue().getOsAppId());

        // Check APN contexts after DEFAULT is set up
        Map<Integer, ApnContext> apnContexts = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));
        assertEquals(apnContexts.get(ApnSetting.TYPE_DEFAULT).getState(),
                DctConstants.State.CONNECTED);
        assertNotEquals(apnContexts.get(ApnSetting.TYPE_ENTERPRISE).getState(),
                DctConstants.State.CONNECTED);

        // Enable the ENTERPRISE APN
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        SetupDataCallResult result = createSetupDataCallResult();
        result.cid = 10;
        mSimulatedCommands.setDataCallResult(true, result);
        mDct.enableApn(ApnSetting.TYPE_ENTERPRISE, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.NGRAN), any(DataProfile.class), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), tdCaptor.capture(),
                anyBoolean(), any(Message.class));
        assertEquals(null, tdCaptor.getValue().getDataNetworkName());
        assertTrue(Arrays.equals(DataConnection.getEnterpriseOsAppId(),
                tdCaptor.getValue().getOsAppId()));

        // Check APN contexts after ENTERPRISE is set up
        Map<Integer, ApnContext> apnContextsAfterRowIdsChanged = mDct.getApnContexts()
                .stream().collect(Collectors.toMap(ApnContext::getApnTypeBitmask, x -> x));

        // Make sure that the data connection used earlier wasn't cleaned up and still in use.
        assertEquals(apnContexts.get(ApnSetting.TYPE_DEFAULT).getDataConnection(),
                apnContextsAfterRowIdsChanged.get(ApnSetting.TYPE_DEFAULT).getDataConnection());

        // Check that ENTERPRISE isn't using the same data connection as DEFAULT
        assertNotEquals(apnContexts.get(ApnSetting.TYPE_DEFAULT).getDataConnection(),
                apnContextsAfterRowIdsChanged.get(ApnSetting.TYPE_ENTERPRISE).getDataConnection());
    }

    // Test for Data setup with APN Set ID
    @Test
    @SmallTest
    public void testDataSetupWithApnSetId() throws Exception {
        // Set the prefer apn set id to "1"
        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Telephony.Carriers.APN_SET_ID, 1);
        cr.update(PREFERAPN_URI, values, null, null);

        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));

        List<DataProfile> dataProfiles = dpCaptor.getAllValues();
        assertEquals(2, dataProfiles.size());

        // Verify to use FAKE APN7 which is Default APN with apnSetId=1(Same as the pereferred
        // APN's set id).
        Optional<DataProfile> fakeApn7 = dataProfiles.stream()
                .filter(dp -> dp.getApn().equals(FAKE_APN7)).findFirst();
        assertTrue(fakeApn7.isPresent());
        verifyDataProfile(fakeApn7.get(), FAKE_APN7, 0, 17, 1, NETWORK_TYPE_LTE_BITMASK);

        // Verify to use FAKE APN8 which is IMS APN with apnSetId=-1
        // (Telephony.Carriers.MATCH_ALL_APN_SET_ID).
        Optional<DataProfile> fakeApn8 = dataProfiles.stream()
                .filter(dp -> dp.getApn().equals(FAKE_APN8)).findFirst();
        assertTrue(fakeApn8.isPresent());
        verifyDataProfile(fakeApn8.get(), FAKE_APN8, 2, 64, 1, NETWORK_TYPE_LTE_BITMASK);
    }

    // Test oos
    @Test
    @SmallTest
    public void testDataRatChangeOOS() {
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_EHRPD)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(AccessNetworkType.CDMA2000), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN4, 0, 21, 2, NETWORK_TYPE_EHRPD_BITMASK);
        assertTrue(mDct.isAnyDataConnected());

        // Data rat change from ehrpd to unknown due to OOS
        logd("Sending EVENT_DATA_RAT_CHANGED");
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UNKNOWN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // Verify data connection is on
        verify(mSimulatedCommandsVerifier, times(0)).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));

        // Data rat resume from unknown to ehrpd
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_EHRPD)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Verify the same data connection
        assertEquals(FAKE_APN4, mDct.getActiveApnString(ApnSetting.TYPE_DEFAULT_STRING));
        assertTrue(mDct.isAnyDataConnected());
    }

    // Test provisioning
    /*@Test
    @SmallTest
    public void testDataEnableInProvisioning() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();

        assertEquals(1, Settings.Global.getInt(resolver, Settings.Global.MOBILE_DATA));
        assertTrue(mDct.isDataEnabled());
        assertTrue(mDct.isUserDataEnabled());

        mDct.setUserDataEnabled(false);
        waitForMs(200);

        assertEquals(0, Settings.Global.getInt(resolver, Settings.Global.MOBILE_DATA));
        assertFalse(mDct.isDataEnabled());
        assertFalse(mDct.isUserDataEnabled());

        // Changing provisioned to 0.
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DEVICE_PROVISIONED_CHANGE, null));
        waitForMs(200);

        assertTrue(mDct.isDataEnabled());
        assertTrue(mDct.isUserDataEnabled());

        // Enable user data during provisioning. It should write to
        // Settings.Global.MOBILE_DATA and keep data enabled when provisioned.
        mDct.setUserDataEnabled(true);
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 1);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DEVICE_PROVISIONED_CHANGE, null));
        waitForMs(200);

        assertTrue(mDct.isDataEnabled());
        assertTrue(mDct.isUserDataEnabled());
        assertEquals(1, Settings.Global.getInt(resolver, Settings.Global.MOBILE_DATA));
    }*/

    /*
    @Test
    @SmallTest
    public void testNotifyDataEnabledChanged() throws Exception {
        doAnswer(invocation -> {
            mMessage = (Message) invocation.getArguments()[0];
            return true;
        }).when(mHandler).sendMessageDelayed(any(), anyLong());

        // Test registration.
        mDct.registerForDataEnabledChanged(mHandler, DATA_ENABLED_CHANGED, null);
        verifyDataEnabledChangedMessage(true, DataEnabledSettings.REASON_REGISTERED);

        // Disable user data. Should receive data enabled change to false.
        mDct.setUserDataEnabled(false);
        waitForMs(200);
        verifyDataEnabledChangedMessage(false, DataEnabledSettings.REASON_USER_DATA_ENABLED);

        // Changing provisioned to 0. Shouldn't receive any message, as data enabled remains false.
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0);
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED,
                0);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DEVICE_PROVISIONED_CHANGE, null));
        waitForMs(200);
        assertFalse(mDct.isDataEnabled());
        verify(mHandler, never()).sendMessageDelayed(any(), anyLong());

        // Changing provisioningDataEnabled to 1. It should trigger data enabled change to true.
        Settings.Global.putInt(resolver,
                Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED, 1);
        mDct.sendMessage(mDct.obtainMessage(
                DctConstants.EVENT_DEVICE_PROVISIONING_DATA_SETTING_CHANGE, null));
        waitForMs(200);
        verifyDataEnabledChangedMessage(
                true, DataEnabledSettings.REASON_PROVISIONING_DATA_ENABLED_CHANGED);
    }*/

    @Test
    @SmallTest
    public void testNetworkStatusChangedRecoveryOFF() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending EVENT_NETWORK_STATUS_CHANGED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.VALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        logd("Sending EVENT_NETWORK_STATUS_CHANGED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.INVALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        waitForMs(200);

        // Verify that its no-op when the new data stall detection feature is disabled
        verify(mSimulatedCommandsVerifier, times(0)).getDataCallList(any(Message.class));
    }

    @FlakyTest
    @Test
    @SmallTest
    public void testNetworkStatusChangedRecoveryON() {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK, 1);
        Settings.System.putInt(resolver, "radio.data.stall.recovery.action", 0);
        doReturn(new SignalStrength()).when(mPhone).getSignalStrength();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, timeout(TEST_TIMEOUT).times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending EVENT_NETWORK_STATUS_CHANGED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.VALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        logd("Sending EVENT_NETWORK_STATUS_CHANGED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.INVALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mSimulatedCommandsVerifier, times(1)).getDataCallList(any(Message.class));
    }

    @FlakyTest
    @Test
    @SmallTest
    public void testRecoveryStepPDPReset() {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK, 1);
        Settings.Global.putLong(resolver,
                Settings.Global.MIN_DURATION_BETWEEN_RECOVERY_STEPS_IN_MS, 100);
        Settings.System.putInt(resolver, "radio.data.stall.recovery.action", 1);
        doReturn(new SignalStrength()).when(mPhone).getSignalStrength();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, timeout(TEST_TIMEOUT).times(2)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending EVENT_NETWORK_STATUS_CHANGED false");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.INVALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        waitForMs(200);

        // expected tear down all DataConnections
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));
    }


    @Test
    @SmallTest
    public void testRecoveryStepReRegister() {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK, 1);
        Settings.Global.putLong(resolver,
                Settings.Global.MIN_DURATION_BETWEEN_RECOVERY_STEPS_IN_MS, 100);
        Settings.System.putInt(resolver, "radio.data.stall.recovery.action", 2);
        doReturn(new SignalStrength()).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending EVENT_NETWORK_STATUS_CHANGED false");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.INVALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // expected to get preferred network type
        verify(mSST, times(1)).reRegisterNetwork(eq(null));
    }

    @Test
    @SmallTest
    public void testRecoveryStepRestartRadio() {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK, 1);
        Settings.Global.putLong(resolver,
                Settings.Global.MIN_DURATION_BETWEEN_RECOVERY_STEPS_IN_MS, 100);
        Settings.System.putInt(resolver, "radio.data.stall.recovery.action", 3);
        doReturn(new SignalStrength()).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        logd("Sending EVENT_NETWORK_STATUS_CHANGED false");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NETWORK_STATUS_CHANGED,
                NetworkAgent.INVALID_NETWORK, 1, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // expected to get preferred network type
        verify(mSST, times(1)).powerOffRadioSafely();
    }

    private void verifyDataEnabledChangedMessage(boolean enabled, int reason) {
        verify(mHandler, times(1)).sendMessageDelayed(any(), anyLong());
        Pair<Boolean, Integer> result = (Pair) ((AsyncResult) mMessage.obj).result;
        assertEquals(DATA_ENABLED_CHANGED, mMessage.what);
        assertEquals(enabled, result.first);
        assertEquals(reason, (int) result.second);
        clearInvocations(mHandler);
    }

    private void setUpSubscriptionPlans(boolean isNrUnmetered) throws Exception {
        List<SubscriptionPlan> plans = new ArrayList<>();
        if (isNrUnmetered) {
            plans.add(SubscriptionPlan.Builder
                    .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                            Period.ofMonths(1))
                    .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                            SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                    .setNetworkTypes(new int[] {TelephonyManager.NETWORK_TYPE_NR})
                    .build());
        }
        plans.add(SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .setDataUsage(500_000_000, System.currentTimeMillis())
                .build());
        replaceInstance(DcTracker.class, "mSubscriptionPlans", mDct, plans);
    }

    private void resetSubscriptionPlans() throws Exception {
        replaceInstance(DcTracker.class, "mSubscriptionPlans", mDct, null);
    }

    private void setUpSubscriptionOverride(int[] networkTypes, boolean isUnmetered)
            throws Exception {
        List<Integer> networkTypesList = null;
        if (networkTypes != null) {
            networkTypesList = new ArrayList<>();
            for (int networkType : networkTypes) {
                networkTypesList.add(networkType);
            }
        }
        replaceInstance(DcTracker.class, "mUnmeteredNetworkTypes", mDct, networkTypesList);
        replaceInstance(DcTracker.class, "mUnmeteredOverride", mDct, isUnmetered);
    }

    private void resetSubscriptionOverride() throws Exception {
        replaceInstance(DcTracker.class, "mUnmeteredNetworkTypes", mDct, null);
        replaceInstance(DcTracker.class, "mUnmeteredOverride", mDct, false);
    }

    private boolean isNetworkTypeUnmetered(int networkType) throws Exception {
        Method method = DcTracker.class.getDeclaredMethod(
                "isNetworkTypeUnmetered", int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(mDct, networkType);
    }

    private int setUpDataConnection() throws Exception {
        Field dc = DcTracker.class.getDeclaredField("mDataConnections");
        dc.setAccessible(true);
        Field uig = DcTracker.class.getDeclaredField("mUniqueIdGenerator");
        uig.setAccessible(true);
        int id = ((AtomicInteger) uig.get(mDct)).getAndIncrement();
        ((HashMap<Integer, DataConnection>) dc.get(mDct)).put(id, mDataConnection);
        return id;
    }

    private void resetDataConnection(int id) throws Exception {
        Field dc = DcTracker.class.getDeclaredField("mDataConnections");
        dc.setAccessible(true);
        ((HashMap<Integer, DataConnection>) dc.get(mDct)).remove(id);
    }

    private void setUpWatchdogTimer() {
        // Watchdog active for 10s
        mBundle.putLong(CarrierConfigManager.KEY_5G_WATCHDOG_TIME_MS_LONG, 10000);
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    private boolean getWatchdogStatus() throws Exception {
        Field field = DcTracker.class.getDeclaredField(("mWatchdog"));
        field.setAccessible(true);
        return (boolean) field.get(mDct);
    }

    private Map<Integer, List<Message>> getHandoverCompletionMessages() throws Exception {
        Field field = DcTracker.class.getDeclaredField(("mHandoverCompletionMsgs"));
        field.setAccessible(true);
        return (Map<Integer, List<Message>>) field.get(mDct);
    }

    private void setUpTempNotMetered() {
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_NR)
                .when(mPhone).getRadioAccessFamily();
        doReturn(1).when(mPhone).getSubId();
        mBundle.putBoolean(CarrierConfigManager.KEY_NETWORK_TEMP_NOT_METERED_SUPPORTED_BOOL, true);
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    @Test
    public void testIsNetworkTypeUnmetered() throws Exception {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});

        // only 5G unmetered
        setUpSubscriptionOverride(new int[]{TelephonyManager.NETWORK_TYPE_NR}, true);

        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_NR));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_LTE));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        // all network types metered
        setUpSubscriptionOverride(TelephonyManager.getAllNetworkTypes(), false);

        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_NR));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_LTE));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        // all network types unmetered
        setUpSubscriptionOverride(TelephonyManager.getAllNetworkTypes(), true);

        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_NR));
        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_LTE));
        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        resetSubscriptionOverride();
    }

    @Test
    public void testIsNetworkTypeUnmeteredViaSubscriptionPlans() throws Exception {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});

        // only 5G unmetered
        setUpSubscriptionPlans(true);

        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_NR));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_LTE));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        // all network types metered
        setUpSubscriptionPlans(false);

        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_NR));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_LTE));
        assertFalse(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        // all network types unmetered
        List<SubscriptionPlan> plans = new ArrayList<>();
        plans.add(SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                        SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                .build());
        replaceInstance(DcTracker.class, "mSubscriptionPlans", mDct, plans);

        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_NR));
        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_LTE));
        assertTrue(isNetworkTypeUnmetered(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        resetSubscriptionPlans();
    }

    @Test
    public void testIsNrUnmeteredSubscriptionPlans() throws Exception {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        int id = setUpDataConnection();
        setUpSubscriptionPlans(false);
        setUpWatchdogTimer();
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        setUpTempNotMetered();

        // NetCapability should be metered when connected to 5G with no unmetered plan or frequency
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(1)).onMeterednessChanged(false);

        // Set SubscriptionPlans unmetered
        setUpSubscriptionPlans(true);

        // NetCapability should switch to unmetered with an unmetered plan
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(1)).onMeterednessChanged(true);

        // Set MMWAVE frequency to unmetered
        mBundle.putBoolean(CarrierConfigManager.KEY_UNMETERED_NR_NSA_MMWAVE_BOOL, true);
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // NetCapability should switch to metered without fr=MMWAVE
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(2)).onMeterednessChanged(false);

        // NetCapability should switch to unmetered with fr=MMWAVE
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(2)).onMeterednessChanged(true);

        resetDataConnection(id);
        resetSubscriptionPlans();
    }

    @Test
    public void testIsNrUnmeteredCarrierConfigs() throws Exception {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        int id = setUpDataConnection();
        setUpSubscriptionPlans(false);
        setUpWatchdogTimer();
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        setUpTempNotMetered();

        // NetCapability should be metered when connected to 5G with no unmetered plan or frequency
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(1)).onMeterednessChanged(false);

        // Set MMWAVE frequency to unmetered
        mBundle.putBoolean(CarrierConfigManager.KEY_UNMETERED_NR_NSA_BOOL, true);
        mBundle.putBoolean(CarrierConfigManager.KEY_UNMETERED_NR_NSA_MMWAVE_BOOL, true);
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // NetCapability should switch to unmetered when fr=MMWAVE and MMWAVE unmetered
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(1)).onMeterednessChanged(true);

        // NetCapability should switch to metered when fr=SUB6 and MMWAVE unmetered
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(2)).onMeterednessChanged(false);

        // Set SUB6 frequency to unmetered
        doReturn(2).when(mPhone).getSubId();
        mBundle.putBoolean(CarrierConfigManager.KEY_UNMETERED_NR_NSA_MMWAVE_BOOL, false);
        mBundle.putBoolean(CarrierConfigManager.KEY_UNMETERED_NR_NSA_SUB6_BOOL, true);
        intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // NetCapability should switch to unmetered when fr=SUB6 and SUB6 unmetered
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mDataConnection, times(2)).onMeterednessChanged(true);

        resetDataConnection(id);
        resetSubscriptionPlans();
    }

    @Test
    public void testReevaluateUnmeteredConnectionsOnNetworkChange() throws Exception {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        int id = setUpDataConnection();
        setUpSubscriptionPlans(true);
        setUpWatchdogTimer();
        setUpTempNotMetered();

        // NetCapability should be unmetered when connected to 5G
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        verify(mDataConnection, times(1)).onMeterednessChanged(true);

        // NetCapability should be metered when disconnected from 5G
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        verify(mDataConnection, times(1)).onMeterednessChanged(false);

        resetDataConnection(id);
        resetSubscriptionPlans();
    }

    @Test
    public void testReevaluateUnmeteredConnectionsOnWatchdog() throws Exception {
        initApns(ApnSetting.TYPE_DEFAULT_STRING, new String[]{ApnSetting.TYPE_ALL_STRING});
        int id = setUpDataConnection();
        setUpSubscriptionPlans(true);
        setUpWatchdogTimer();

        // Watchdog inactive when unmetered and not connected to 5G
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_NR_TIMER_WATCHDOG));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        assertFalse(getWatchdogStatus());

        // Watchdog active when unmetered and connected to 5G
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        assertTrue(getWatchdogStatus());

        // Watchdog inactive when metered
        setUpSubscriptionPlans(false);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TELEPHONY_DISPLAY_INFO_CHANGED));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        assertFalse(getWatchdogStatus());

        resetDataConnection(id);
        resetSubscriptionPlans();
    }

    /**
     * Test if this is a path prefix match against the given Uri. Verifies that
     * scheme, authority, and atomic path segments match.
     *
     * Copied from frameworks/base/core/java/android/net/Uri.java
     */
    private boolean isPathPrefixMatch(Uri uriA, Uri uriB) {
        if (!Objects.equals(uriA.getScheme(), uriB.getScheme())) return false;
        if (!Objects.equals(uriA.getAuthority(), uriB.getAuthority())) return false;

        List<String> segA = uriA.getPathSegments();
        List<String> segB = uriB.getPathSegments();

        final int size = segB.size();
        if (segA.size() < size) return false;

        for (int i = 0; i < size; i++) {
            if (!Objects.equals(segA.get(i), segB.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Test
    public void testNoApnContextsWhenDataIsDisabled() throws java.lang.InterruptedException {
        //Check that apn contexts are loaded.
        assertTrue(mDct.getApnContexts().size() > 0);

        //Do work normally done in teardown.
        mDct.removeCallbacksAndMessages(null);
        mDcTrackerTestHandler.quit();
        mDcTrackerTestHandler.join();

        //Set isDataCapable to false for the new DcTracker being created in DcTrackerTestHandler.
        doReturn(false).when(mTelephonyManager).isDataCapable();
        mDcTrackerTestHandler = new DcTrackerTestHandler(getClass().getSimpleName());
        setReady(false);

        mDcTrackerTestHandler.start();
        waitUntilReady();
        assertEquals(0, mDct.getApnContexts().size());

        //No need to clean up handler because that work is done in teardown.
    }

    @Test
    public void testRatChanged() throws Exception {
        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = mDct.isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);

        logd("Sending EVENT_ENABLE_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.enableApn(ApnSetting.TYPE_DEFAULT, DcTracker.REQUEST_TYPE_NORMAL, null);

        sendInitializationEvents();

        dataConnectionReasons = new DataConnectionReasons();
        allowed = mDct.isDataAllowed(dataConnectionReasons);
        assertTrue(dataConnectionReasons.toString(), allowed);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(AccessNetworkType.EUTRAN), dpCaptor.capture(),
                eq(false), eq(false), eq(DataService.REQUEST_REASON_NORMAL), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 21, 1, NETWORK_TYPE_LTE_BITMASK);

        verifyDataConnected(FAKE_APN1);

        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS).when(mServiceState)
                .getRilDataRadioTechnology();

        logd("Sending EVENT_DATA_RAT_CHANGED");
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Data connection is running on a different thread. Have to wait.
        waitForMs(200);
        // expected tear down all metered DataConnections
        verify(mSimulatedCommandsVerifier).deactivateDataCall(
                eq(DataService.REQUEST_REASON_NORMAL), anyInt(),
                any(Message.class));
    }

    @Test
    public void testApnConfigRepositoryUpdatedOnCarrierConfigChange() {
        assertPriority(ApnSetting.TYPE_CBS_STRING, 2);
        assertPriority(ApnSetting.TYPE_MMS_STRING, 2);

        mBundle.putStringArray(CarrierConfigManager.KEY_APN_PRIORITY_STRING_ARRAY,
                new String[] {
                        ApnSetting.TYPE_CBS_STRING + ":11",
                        ApnSetting.TYPE_MMS_STRING + ":19",
                });

        sendInitializationEvents();

        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        mContext.sendBroadcast(intent);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        assertPriority(ApnSetting.TYPE_CBS_STRING, 11);
        assertPriority(ApnSetting.TYPE_MMS_STRING, 19);

        //Ensure apns are in sorted order.
        ApnContext lastApnContext = null;
        for (ApnContext apnContext : mDct.getApnContexts()) {
            if (lastApnContext != null) {
                assertTrue(apnContext.getPriority() <= lastApnContext.getPriority());
            }
            lastApnContext = apnContext;
        }
    }

    private void assertPriority(String type, int priority) {
        assertEquals(priority, mDct.getApnContexts().stream()
                .filter(x -> x.getApnType().equals(type))
                .findFirst().get().getPriority());
    }

    @Test
    public void testProvisionBroadcastReceiver() {
        Intent intent = new Intent("com.android.internal.telephony.PROVISION");
        intent.putExtra("provision.phone.id", mPhone.getPhoneId());
        try {
            mContext.sendBroadcast(intent);
        } catch (SecurityException e) {
            fail();
        }
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
    }

    @Test
    public void testRetryHandoverWhenDisconnecting() throws Exception {
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        setUpDataConnection();
        SparseArray<ApnContext> apnContextsByType = Mockito.mock(SparseArray.class);
        ConcurrentHashMap<String, ApnContext> apnContexts = Mockito.mock(ConcurrentHashMap.class);
        doReturn(mApnContext).when(apnContextsByType).get(eq(ApnSetting.TYPE_IMS));
        doReturn(mApnContext).when(apnContexts).get(eq(ApnSetting.TYPE_IMS_STRING));
        doReturn(false).when(mApnContext).isConnectable();
        doReturn(false).when(mDataEnabledSettings).isDataEnabled(anyInt());
        doReturn(DctConstants.State.DISCONNECTING).when(mApnContext).getState();
        replaceInstance(DcTracker.class, "mApnContextsByType", mDct, apnContextsByType);
        replaceInstance(DcTracker.class, "mApnContexts", mDct, apnContexts);

        sendInitializationEvents();

        logd("Sending EVENT_ENABLE_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_HANDOVER,
                mDct.obtainMessage(12345));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        assertTrue(isHandoverPending(ApnSetting.TYPE_IMS));

        // Verify no handover request was sent
        verify(mDataConnection, never()).bringUp(any(ApnContext.class), anyInt(), anyInt(),
                any(Message.class), anyInt(), anyInt(), anyInt(), anyBoolean());

        doReturn(DctConstants.State.RETRYING).when(mApnContext).getState();
        // Data now is disconnected
        doReturn(true).when(mApnContext).isConnectable();
        doReturn(true).when(mDataEnabledSettings).isDataEnabled(anyInt());
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DISCONNECT_DONE,
                new AsyncResult(Pair.create(mApnContext, 0), null, null)));

        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        verify(mDataConnection).bringUp(any(ApnContext.class), anyInt(), anyInt(),
                any(Message.class), anyInt(), eq(DcTracker.REQUEST_TYPE_HANDOVER), anyInt(),
                anyBoolean());
    }

    @Test
    public void testDataUnthrottled() throws Exception {
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        replaceInstance(DcTracker.class, "mDataThrottler", mDct, mDataThrottler);
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_APN_UNTHROTTLED,
                new AsyncResult(null, FAKE_APN3, null)));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        verify(mDataThrottler).setRetryTime(
                eq(ApnSetting.TYPE_IMS),
                eq(RetryManager.NO_SUGGESTED_RETRY_DELAY),
                eq(DcTracker.REQUEST_TYPE_NORMAL));
    }

    @Test
    public void testDataUnthrottledAfterAPNChanged() throws Exception {
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        replaceInstance(DcTracker.class, "mDataThrottler", mDct, mDataThrottler);

        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_APN_CHANGED, null));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());

        // Verify unthrottling
        verify(mDataThrottler, times(2)).reset();
    }

    @Test
    public void testDataUnthrottledOnSimStateChanged() throws Exception {
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        replaceInstance(DcTracker.class, "mDataThrottler", mDct, mDataThrottler);

        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();
        sendSimStateUpdated("testDataUnthrottledOnSimStateChanged");

        // Verify unthrottling
        verify(mDataThrottler, times(2)).reset();
    }

    @Test
    public void testHandlingSecondHandoverRequest() throws Exception {
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        setUpDataConnection();
        SparseArray<ApnContext> apnContextsByType = Mockito.mock(SparseArray.class);
        ConcurrentHashMap<String, ApnContext> apnContexts = Mockito.mock(ConcurrentHashMap.class);
        doReturn(mApnContext).when(apnContextsByType).get(eq(ApnSetting.TYPE_IMS));
        doReturn(mApnContext).when(apnContexts).get(eq(ApnSetting.TYPE_IMS_STRING));
        doReturn(false).when(mApnContext).isConnectable();
        doReturn(DctConstants.State.CONNECTING).when(mApnContext).getState();
        replaceInstance(DcTracker.class, "mApnContextsByType", mDct, apnContextsByType);
        replaceInstance(DcTracker.class, "mApnContexts", mDct, apnContexts);

        sendInitializationEvents();

        logd("Sending EVENT_ENABLE_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        Message msg = mDct.obtainMessage(12345);
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_HANDOVER, msg);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        Map<Integer, List<Message>> msgs = getHandoverCompletionMessages();
        // Make sure the messages was queued properly instead of fired right away.
        assertTrue(msgs.get(ApnSetting.TYPE_IMS).contains(msg));
    }

    @Test
    public void testDataThrottledNotAllowData() throws Exception {
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        replaceInstance(DcTracker.class, "mDataThrottler", mDct, mDataThrottler);
        doReturn(SystemClock.elapsedRealtime() + 100000).when(mDataThrottler)
                .getRetryTime(ApnSetting.TYPE_IMS);
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        sendInitializationEvents();

        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = mDct.isDataAllowed(mApnContext, DcTracker.REQUEST_TYPE_NORMAL,
                dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);
        assertTrue(dataConnectionReasons.contains(DataDisallowedReasonType.DATA_THROTTLED));

        // Makre sure no data setup request
        verify(mSimulatedCommandsVerifier, never()).setupDataCall(
                anyInt(), any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testNotifyDataDisconnected() {
        // Verify notify data disconnected on DCT constructor, initialized in setUp()
        ArgumentCaptor<PreciseDataConnectionState> captor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(13)).notifyDataConnection(captor.capture());
        for (PreciseDataConnectionState state : captor.getAllValues()) {
            assertEquals(TelephonyManager.DATA_DISCONNECTED, state.getState());
        }
    }

    /**
     * There is a corresponding test {@link DataConnectionTest#testDataServiceTempUnavailable()} to
     * test DataConnection behavior.
     */
    @Test
    public void testDataServiceTempUnavailable() {
        Handler handler = Mockito.mock(Handler.class);
        Message handoverCompleteMessage = Message.obtain(handler);
        addHandoverCompleteMsg(handoverCompleteMessage, ApnSetting.TYPE_IMS);
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_SETUP_COMPLETE,
                DcTracker.REQUEST_TYPE_HANDOVER, DataCallResponse.HANDOVER_FAILURE_MODE_UNKNOWN,
                new AsyncResult(Pair.create(mApnContext, 0),
                        DataFailCause.SERVICE_TEMPORARILY_UNAVAILABLE, new Exception())));
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        // Ensure handover is not completed yet
        verify(handler, never()).sendMessageDelayed(any(), anyLong());
    }

    @Test
    public void testNormalRequestDoesNotFailHandoverRequest() {
        Handler handler = Mockito.mock(Handler.class);
        Message handoverCompleteMessage = Message.obtain(handler);
        addHandoverCompleteMsg(handoverCompleteMessage, ApnSetting.TYPE_IMS);
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_NORMAL, null);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        // Ensure handover is not completed yet
        verify(handler, never()).sendMessageDelayed(any(), anyLong());
    }

    @Test
    public void testPreferenceChangedFallback() {
        Handler handler = Mockito.mock(Handler.class);
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mTransportManager)
                .getPreferredTransport(anyInt());
        Message handoverCompleteMessage = Message.obtain(handler);
        addHandoverCompleteMsg(handoverCompleteMessage, ApnSetting.TYPE_IMS);
        initApns(ApnSetting.TYPE_IMS_STRING, new String[]{ApnSetting.TYPE_IMS_STRING});
        mDct.enableApn(ApnSetting.TYPE_IMS, DcTracker.REQUEST_TYPE_HANDOVER,
                handoverCompleteMessage);
        waitForLastHandlerAction(mDcTrackerTestHandler.getThreadHandler());
        Bundle bundle = handoverCompleteMessage.getData();
        assertTrue(bundle.getBoolean("extra_handover_failure_fallback"));
    }
}
