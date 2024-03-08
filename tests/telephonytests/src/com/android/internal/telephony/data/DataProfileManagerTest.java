/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.TrafficDescriptor.OsAppId;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataConfigManager.DataConfigManagerCallback;
import com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import com.android.internal.telephony.data.DataProfileManager.DataProfileManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataProfileManagerTest extends TelephonyTest {
    private static final String GENERAL_PURPOSE_APN = "GP_APN";
    private static final String GENERAL_PURPOSE_APN_LEGACY_RAT = "GP_APN_RAT";
    private static final String GENERAL_PURPOSE_APN1 = "GP_APN1";
    private static final String IMS_APN = "IMS_APN";
    private static final String TETHERING_APN = "DUN_APN";
    private static final String APN_SET_ID_1_APN = "APN_SET_ID_1_APN";
    private static final String RCS_APN = "RCS_APN";
    private static final String RCS_APN1 = "RCS_APN1";
    private static final String APN_SET_ID_1_TETHERING_APN = "APN_SET_ID_1_TETHERING_APN";
    private static final String MATCH_ALL_APN_SET_ID_IMS_APN = "MATCH_ALL_APN_SET_ID_IMS_APN";
    private static final String ESIM_BOOTSTRAP_PROVISIONING_APN = "ESIM_BOOTSTRAP_PROVISIONING_APN";
    private static final String TEST_BOOTSTRAP_APN =
            "TEST_BOOTSTRAP_APN";
    private static final String PLMN = "330123";
    private static final int DEFAULT_APN_SET_ID = Telephony.Carriers.NO_APN_SET_ID;
    private static final int APN_SET_ID_1 = 1;
    private static final int MATCH_ALL_APN_SET_ID = Telephony.Carriers.MATCH_ALL_APN_SET_ID;

    // Mocked classes
    private DataProfileManagerCallback mDataProfileManagerCallback;

    private DataProfileManager mDataProfileManagerUT;

    private final ApnSettingContentProvider mApnSettingContentProvider =
            new ApnSettingContentProvider();

    private int mPreferredApnId = 0;

    private DataNetworkControllerCallback mDataNetworkControllerCallback;

    private DataConfigManagerCallback mDataConfigManagerCallback;

    private boolean mSimInserted = true;

    private class ApnSettingContentProvider extends MockContentProvider {
        public final String[] APN_COLUMNS = new String[]{
                Telephony.Carriers._ID, Telephony.Carriers.NUMERIC,
                Telephony.Carriers.NAME, Telephony.Carriers.APN,
                Telephony.Carriers.PROXY, Telephony.Carriers.PORT,
                Telephony.Carriers.MMSC, Telephony.Carriers.MMSPROXY,
                Telephony.Carriers.MMSPORT, Telephony.Carriers.USER,
                Telephony.Carriers.PASSWORD, Telephony.Carriers.AUTH_TYPE,
                Telephony.Carriers.TYPE,
                Telephony.Carriers.PROTOCOL,
                Telephony.Carriers.ROAMING_PROTOCOL,
                Telephony.Carriers.CARRIER_ENABLED,
                Telephony.Carriers.PROFILE_ID,
                Telephony.Carriers.MODEM_PERSIST,
                Telephony.Carriers.MAX_CONNECTIONS,
                Telephony.Carriers.WAIT_TIME_RETRY,
                Telephony.Carriers.TIME_LIMIT_FOR_MAX_CONNECTIONS,
                Telephony.Carriers.MTU,
                Telephony.Carriers.MTU_V4,
                Telephony.Carriers.MTU_V6,
                Telephony.Carriers.MVNO_TYPE,
                Telephony.Carriers.MVNO_MATCH_DATA,
                Telephony.Carriers.NETWORK_TYPE_BITMASK,
                Telephony.Carriers.LINGERING_NETWORK_TYPE_BITMASK,
                Telephony.Carriers.APN_SET_ID,
                Telephony.Carriers.CARRIER_ID,
                Telephony.Carriers.SKIP_464XLAT,
                Telephony.Carriers.ALWAYS_ON,
                Telephony.Carriers.INFRASTRUCTURE_BITMASK,
                Telephony.Carriers.ESIM_BOOTSTRAP_PROVISIONING
        };

        private int mPreferredApnSet = 0;

        public List<Object> mAllApnSettings = List.of(
                new Object[]{
                        1,                      // id
                        PLMN,                   // numeric
                        GENERAL_PURPOSE_APN,    // name
                        GENERAL_PURPOSE_APN,    // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "default,supl,mms,ia",  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                      // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                // default internet data profile for RAT CDMA, to test update preferred data profile
                new Object[]{
                        9,                      // id
                        PLMN,                   // numeric
                        GENERAL_PURPOSE_APN_LEGACY_RAT,    // name
                        GENERAL_PURPOSE_APN_LEGACY_RAT,    // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "default,supl,mms,ia",  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_CDMA, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        2,                      // id
                        PLMN,                   // numeric
                        IMS_APN,                // name
                        IMS_APN,                // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "ims",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        0,                      // mtu_v4
                        0,                      // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        3,                      // id
                        PLMN,                   // numeric
                        TETHERING_APN,          // name
                        TETHERING_APN,          // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "dun",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        2,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        0,                      // mtu_v4
                        0,                      // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        4,                      // id
                        PLMN,                   // numeric
                        GENERAL_PURPOSE_APN1,   // name
                        GENERAL_PURPOSE_APN1,   // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "default,supl",         // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        0,                      // mtu_v4
                        0,                      // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                // This APN entry is created to test de-duping.
                new Object[]{
                        5,                      // id
                        PLMN,                   // numeric
                        GENERAL_PURPOSE_APN,    // name
                        GENERAL_PURPOSE_APN,    // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "fota",                 // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        0,                      // mtu_v4
                        0,                      // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        6,                      // id
                        PLMN,                   // numeric
                        APN_SET_ID_1_APN,       // name
                        APN_SET_ID_1_APN,       // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "default,supl,mms,ia",  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        APN_SET_ID_1,           // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        7,                      // id
                        PLMN,                   // numeric
                        APN_SET_ID_1_TETHERING_APN, // name
                        APN_SET_ID_1_TETHERING_APN, // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "dun",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        APN_SET_ID_1,           // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        8,                      // id
                        PLMN,                   // numeric
                        MATCH_ALL_APN_SET_ID_IMS_APN, // name
                        MATCH_ALL_APN_SET_ID_IMS_APN, // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "ims",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        MATCH_ALL_APN_SET_ID,   // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        0                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        9,                      // id
                        PLMN,                   // numeric
                        RCS_APN,                // name
                        RCS_APN,                // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "rcs",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        2,                      // INFRASTRUCTURE_SATELLITE
                        0                      // esim_bootstrap_provisioning
                },
                new Object[]{
                        10,                      // id
                        PLMN,                   // numeric
                        ESIM_BOOTSTRAP_PROVISIONING_APN, // name
                        ESIM_BOOTSTRAP_PROVISIONING_APN, // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "default,supl",          // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        MATCH_ALL_APN_SET_ID,   // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_CELLULAR
                        1                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        11,                      // id
                        PLMN,                   // numeric
                        IMS_APN,                // name
                        IMS_APN,                // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "ims",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        MATCH_ALL_APN_SET_ID,   // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        1,                       // INFRASTRUCTURE_SATELLITE
                        1                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        12,                     // id
                        PLMN,                   // numeric
                        TEST_BOOTSTRAP_APN,     // name
                        TEST_BOOTSTRAP_APN,      // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "default",              // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        MATCH_ALL_APN_SET_ID,   // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        2,                       // INFRASTRUCTURE_SATELLITE
                        1                       // esim_bootstrap_provisioning
                },
                new Object[]{
                        13,                      // id
                        PLMN,                   // numeric
                        RCS_APN1,                // name
                        RCS_APN1,                // apn
                        "",                     // proxy
                        "",                     // port
                        "",                     // mmsc
                        "",                     // mmsproxy
                        "",                     // mmsport
                        "",                     // user
                        "",                     // password
                        -1,                     // authtype
                        "rcs",                  // types
                        "IPV4V6",               // protocol
                        "IPV4V6",               // roaming_protocol
                        1,                      // carrier_enabled
                        0,                      // profile_id
                        1,                      // modem_cognitive
                        0,                      // max_conns
                        0,                      // wait_time
                        0,                      // max_conns_time
                        0,                      // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        DEFAULT_APN_SET_ID,     // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0,                      // always_on
                        2,                       // INFRASTRUCTURE_SATELLITE
                        1                       // esim_bootstrap_provisioning
                }
        );

        private Set<Object> mDeletedApns = new HashSet<>();

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

                    logd("Query '" + PLMN + "' APN settings");
                    MatrixCursor mc = new MatrixCursor(APN_COLUMNS);
                    if (mSimInserted) {
                        for (Object apnSetting : mAllApnSettings) {
                            if (!mDeletedApns.contains(apnSetting)) {
                                mc.addRow((Object[]) apnSetting);
                            }
                        }
                    }
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
            } else if (uri.isPathPrefixMatch(Telephony.Carriers.PREFERRED_APN_URI)) {
                for (Object apnSetting : mAllApnSettings) {
                    if (!mDeletedApns.contains(apnSetting)) {
                        int id = (int) ((Object[]) apnSetting)[0];
                        if (id == mPreferredApnId) {
                            MatrixCursor mc = new MatrixCursor(APN_COLUMNS);
                            mc.addRow((Object[]) apnSetting);
                            return mc;
                        }
                    }
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
            logd("ApnSettingContentProvider: uri=" + uri + ", values=" + values);
            if (uri.isPathPrefixMatch(Telephony.Carriers.PREFERRED_APN_URI)) {
                mPreferredApnId = values.getAsInteger(Telephony.Carriers.APN_ID);
                if (mPreferredApnId != -1) {
                    for (Object apnSetting : mAllApnSettings) {
                        int id = (int) ((Object[]) apnSetting)[0];
                        if (id == mPreferredApnId) {
                            mPreferredApnSet = (int) ((Object[]) apnSetting)[28]; //update setId too
                        }
                    }
                } else {
                    mPreferredApnSet = 0; // db is emptied
                }
                logd("mPreferredApnId=" + mPreferredApnId);
            }
            return null;
        }

        public boolean removeApnByApnId(int apnId) {
            for (Object apnSetting : mAllApnSettings) {
                int id = (int) ((Object[]) apnSetting)[0];
                if (apnId == id) {
                    mDeletedApns.add(apnSetting);
                    return true;
                }
            }
            return false;
        }

        public void restoreApnSettings() {
            mDeletedApns.clear();
        }

        public void setPreferredApn(String apnName) {
            for (Object apnSetting : mAllApnSettings) {
                if (apnName == ((Object[]) apnSetting)[3]) {
                    mPreferredApnId = (int) ((Object[]) apnSetting)[0];
                    mPreferredApnSet = (int) ((Object[]) apnSetting)[28];
                    logd("mPreferredApnId=" + mPreferredApnId + " ,mPreferredApnSet="
                            + mPreferredApnSet);
                    break;
                }
            }
        }

        public int getCountApnSpecifyType(String type) {
            int count = 0;
            for (Object apnSetting : mAllApnSettings) {
                String apnTypes = (String) ((Object[]) apnSetting)[12];
                if (apnTypes.contains(type)) {
                    count++;
                }
            }
            return count;
        }
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

    private void dedupeDataProfiles(@NonNull List<DataProfile> dataProfiles) throws Exception {
        Class[] cArgs = new Class[1];
        cArgs[0] = List.class;
        Method method = DataProfileManager.class.getDeclaredMethod("dedupeDataProfiles", cArgs);
        method.setAccessible(true);
        method.invoke(mDataProfileManagerUT, dataProfiles);
    }

    private @NonNull List<DataProfile> getAllDataProfiles() throws Exception {
        Field field = DataProfileManager.class.getDeclaredField("mAllDataProfiles");
        field.setAccessible(true);
        return (List<DataProfile>) field.get(mDataProfileManagerUT);
    }

    @Before
    public void setUp() throws Exception {
        logd("DataProfileManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mDataProfileManagerCallback = Mockito.mock(DataProfileManagerCallback.class);
        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mApnSettingContentProvider);

        doReturn(true).when(mDataConfigManager).isConfigCarrierSpecific();
        doReturn(List.of(ApnSetting.TYPE_IA, ApnSetting.TYPE_DEFAULT))
                .when(mDataConfigManager).getAllowedInitialAttachApnTypes();
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mDataProfileManagerCallback).invokeFromExecutor(any(Runnable.class));
        mDataProfileManagerUT = new DataProfileManager(mPhone, mDataNetworkController,
                mMockedWwanDataServiceManager, Looper.myLooper(), mFeatureFlags,
                mDataProfileManagerCallback);
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController).registerDataNetworkControllerCallback(
                        dataNetworkControllerCallbackCaptor.capture());
        mDataNetworkControllerCallback = dataNetworkControllerCallbackCaptor.getValue();
        ArgumentCaptor<DataConfigManagerCallback> dataConfigManagerCallbackCaptor =
                ArgumentCaptor.forClass(DataConfigManagerCallback.class);
        verify(mDataConfigManager).registerCallback(dataConfigManagerCallbackCaptor.capture());
        mDataConfigManagerCallback = dataConfigManagerCallbackCaptor.getValue();
        mDataConfigManagerCallback.onCarrierConfigChanged();
        processAllMessages();

        logd("DataProfileManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mDataProfileManagerUT = null;
        mDataNetworkControllerCallback = null;
        mDataConfigManagerCallback = null;
        super.tearDown();
    }

    @Test
    public void testGetDataProfileForNetworkRequest() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);

        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);

        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(IMS_APN);

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dp).isNull();

        doReturn(new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build()).when(mServiceState).getNetworkRegistrationInfo(anyInt(), anyInt());

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_NR, false, false , false);
        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(TETHERING_APN);
    }

    @Test
    public void testGetDataProfileForNetworkRequestNoCompatibleRat() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_GSM, false, false, false);
        // Should not find data profile due to RAT incompatible.
        assertThat(dp).isNull();
    }

    @Test
    public void testGetDataProfileForNetworkRequestRotation() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
        logd("Set setLastSetupTimestamp on " + dataProfile);
        dataProfile.setLastSetupTimestamp(SystemClock.elapsedRealtime());

        // See if another one can be returned.
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN1);
    }

    @Test
    public void testGetDataProfileForEnterpriseNetworkRequest() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting()).isNull();
        OsAppId osAppId = new OsAppId(dataProfile.getTrafficDescriptor().getOsAppId());

        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("ENTERPRISE");
        assertThat(osAppId.getDifferentiator()).isEqualTo(1);

        tnr = new TelephonyNetworkRequest(new NetworkRequest(new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                .addEnterpriseId(2), ConnectivityManager.TYPE_NONE,
                0, NetworkRequest.Type.REQUEST), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting()).isNull();
        osAppId = new OsAppId(dataProfile.getTrafficDescriptor().getOsAppId());

        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("ENTERPRISE");
        assertThat(osAppId.getDifferentiator()).isEqualTo(2);
    }

    @Test
    public void testGetDataProfileForUrllcNetworkRequest() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting()).isNull();
        OsAppId osAppId = new OsAppId(dataProfile.getTrafficDescriptor().getOsAppId());

        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("PRIORITIZE_LATENCY");
        assertThat(osAppId.getDifferentiator()).isEqualTo(1);
    }

    @Test
    public void testGetDataProfileForEmbbNetworkRequest() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting()).isNull();
        OsAppId osAppId = new OsAppId(dataProfile.getTrafficDescriptor().getOsAppId());

        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("PRIORITIZE_BANDWIDTH");
        assertThat(osAppId.getDifferentiator()).isEqualTo(1);
    }

    @Test
    public void testGetDataProfileForSatellite() {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_RCS)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, true, false, false);

        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(RCS_APN);
    }

    @Test
    public void testSetPreferredDataProfile() {
        doReturn(true).when(mFeatureFlags).refinePreferredDataProfileSelection();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
        dataProfile.setLastSetupTimestamp(SystemClock.elapsedRealtime());
        dataProfile.setPreferred(true);
        DataNetwork internetNetwork = Mockito.mock(DataNetwork.class);
        doReturn(dataProfile).when(internetNetwork).getDataProfile();
        doReturn(new DataNetworkController.NetworkRequestList(List.of(tnr)))
                .when(internetNetwork).getAttachedNetworkRequestList();
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(
                Set.of(internetNetwork));
        processAllMessages();

        // Test See if the same one can be returned.
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isTrue();

        // Test Another default internet network connected due to RAT changed. Verify the preferred
        // data profile is updated.
        DataProfile legacyRatDataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_CDMA, false, false, false);
        DataNetwork legacyRatInternetNetwork = Mockito.mock(DataNetwork.class);
        doReturn(legacyRatDataProfile).when(legacyRatInternetNetwork).getDataProfile();
        doReturn(new DataNetworkController.NetworkRequestList(List.of(tnr)))
                .when(legacyRatInternetNetwork).getAttachedNetworkRequestList();
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(Set.of(
                // Because internetNetwork is torn down due to network type mismatch
                legacyRatInternetNetwork));
        processAllMessages();

        assertThat(mDataProfileManagerUT.isDataProfilePreferred(legacyRatDataProfile)).isTrue();

        // Test Another dun default internet network temporarily connected. Verify preferred
        // doesn't change. Mock DUN | DEFAULT.
        TelephonyNetworkRequest dunTnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                        .build(), mPhone);
        DataProfile dunDataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                dunTnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        DataNetwork dunInternetNetwork = Mockito.mock(DataNetwork.class);
        doReturn(dunDataProfile).when(dunInternetNetwork).getDataProfile();
        doReturn(new DataNetworkController.NetworkRequestList(List.of(dunTnr)))
                .when(dunInternetNetwork).getAttachedNetworkRequestList();
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(Set.of(
                legacyRatInternetNetwork, dunInternetNetwork));
        processAllMessages();

        assertThat(mDataProfileManagerUT.isDataProfilePreferred(legacyRatDataProfile)).isTrue();

        // Test a single dun default internet network temporarily connected. Verify preferred
        // doesn't change. Mock DUN | DEFAULT and enforced single connection.
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(
                Set.of(dunInternetNetwork));
        processAllMessages();

        assertThat(mDataProfileManagerUT.isDataProfilePreferred(legacyRatDataProfile)).isTrue();

        // Test all internet networks disconnected. Verify preferred doesn't change.
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(
                Collections.emptySet());

        assertThat(mDataProfileManagerUT.isDataProfilePreferred(legacyRatDataProfile)).isTrue();
    }

    @Test
    public void testSetInitialAttachDataProfile() {
        ArgumentCaptor<DataProfile> dataProfileCaptor =
                ArgumentCaptor.forClass(DataProfile.class);

        verify(mMockedWwanDataServiceManager).setInitialAttachApn(dataProfileCaptor.capture(),
                eq(false), eq(null));
        assertThat(dataProfileCaptor.getValue().getApnSetting().getApnName())
                .isEqualTo(GENERAL_PURPOSE_APN);
    }

    @Test
    public void testSetInitialAttachDataProfileMultipleRequests() throws Exception {
        // This test case only applies to legacy modem, see b/227579876
        doReturn(false).when(mDataConfigManager).allowClearInitialAttachDataProfile();

        // Test: Modem Cleared IA, should always send IA to modem
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();

        Field IADPField = DataProfileManager.class.getDeclaredField("mInitialAttachDataProfile");
        IADPField.setAccessible(true);
        DataProfile dp = (DataProfile) IADPField.get(mDataProfileManagerUT);

        Mockito.clearInvocations(mMockedWwanDataServiceManager);
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();
        DataProfile dp2 = (DataProfile) IADPField.get(mDataProfileManagerUT);

        assertThat(Objects.equals(dp, dp2)).isTrue();
        verify(mMockedWwanDataServiceManager)
                .setInitialAttachApn(any(DataProfile.class), eq(false), eq(null));

        // Test: Modem did NOT clear IA, should not send IA to modem even if IA stays the same
        mDataProfileManagerUT.obtainMessage(2 /* EVENT_APN_DATABASE_CHANGED */).sendToTarget();
        processAllMessages();

        IADPField = DataProfileManager.class.getDeclaredField("mInitialAttachDataProfile");
        IADPField.setAccessible(true);
        dp = (DataProfile) IADPField.get(mDataProfileManagerUT);
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        mDataProfileManagerUT.obtainMessage(2 /* EVENT_APN_DATABASE_CHANGED */).sendToTarget();
        processAllMessages();
        dp2 = (DataProfile) IADPField.get(mDataProfileManagerUT);

        assertThat(Objects.equals(dp, dp2)).isTrue();
        verify(mMockedWwanDataServiceManager, Mockito.never())
                .setInitialAttachApn(any(DataProfile.class), eq(false), eq(null));
    }

    @Test
    public void testSimRemoval() {
        // This test case applies to the latest modem, see b/227579876.
        doReturn(true).when(mDataConfigManager).allowClearInitialAttachDataProfile();

        // SIM inserted
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();

        // SIM removed
        Mockito.clearInvocations(mDataProfileManagerCallback);
        changeSimStateTo(TelephonyManager.SIM_STATE_ABSENT);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        verify(mDataProfileManagerCallback).onDataProfilesChanged();

        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile).isNull();

        // expect default EIMS when SIM absent
        tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                        .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo("sos");

        // expect no default IMS when SIM absent
        tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile).isEqualTo(null);

        // Verify null as initial attached data profile is sent to modem
        verify(mMockedWwanDataServiceManager).setInitialAttachApn(null, false, null);
    }

    @Test
    public void testSimRemovalLegacy() {
        // This test case only applies to legacy modem, see b/227579876, where null IA won't be
        // updated to modem
        doReturn(false).when(mDataConfigManager).allowClearInitialAttachDataProfile();

        // SIM inserted
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();

        // SIM removed
        Mockito.clearInvocations(mDataProfileManagerCallback);
        mSimInserted = false;
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        verify(mDataProfileManagerCallback).onDataProfilesChanged();

        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile).isNull();

        // expect default EIMS when SIM absent
        tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                        .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo("sos");

        // expect no default IMS when SIM absent
        tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile).isEqualTo(null);

        // Verify in legacy mode, null IA should NOT be sent to modem
        verify(mMockedWwanDataServiceManager, Mockito.never())
                .setInitialAttachApn(null, false, null);
    }

    @Test
    public void testSimInsertedAgain() throws Exception {
        testSimRemoval();
        Mockito.clearInvocations(mDataProfileManagerCallback);
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        doReturn(List.of(ApnSetting.TYPE_IMS))
                .when(mDataConfigManager).getAllowedInitialAttachApnTypes();

        changeSimStateTo(TelephonyManager.SIM_STATE_LOADED);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        ArgumentCaptor<DataProfile> dataProfileCaptor =
                ArgumentCaptor.forClass(DataProfile.class);

        verify(mDataProfileManagerCallback).onDataProfilesChanged();
        verify(mMockedWwanDataServiceManager).setInitialAttachApn(dataProfileCaptor.capture(),
                eq(false), eq(null));

        // Should only use IMS APN for initial attach
        assertThat(dataProfileCaptor.getValue().getApnSetting().getApnName()).isEqualTo(IMS_APN);

        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                new TelephonyNetworkRequest(new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone),
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(IMS_APN);
    }

    @Test
    public void testDedupeDataProfiles() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_FOTA)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        // This should get the merged data profile after deduping.
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
    }

    @Test
    public void testDedupeDataProfiles2() throws Exception {
        DataProfile dataProfile1 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName("general")
                        .setOperatorNumeric("123456")
                        .setApnName("apn")
                        .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS
                                | ApnSetting.TYPE_SUPL | ApnSetting.TYPE_HIPRI)
                        .setUser("user")
                        .setPassword("password")
                        .setAuthType(ApnSetting.AUTH_TYPE_CHAP)
                        .setMmsc(Uri.parse("http://mms-s"))
                        .setMmsProxyAddress("mmsc.proxy")
                        .setMmsProxyPort(8080)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setMvnoMatchData("1")
                        .setMvnoType(1)
                        .build())
                .build();

        DataProfile dataProfile2 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName("XCAP")
                        .setOperatorNumeric("123456")
                        .setApnName("apn")
                        .setApnTypeBitmask(ApnSetting.TYPE_XCAP)
                        .setUser("user")
                        .setPassword("password")
                        .setAuthType(ApnSetting.AUTH_TYPE_CHAP)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setMvnoMatchData("2")
                        .setMvnoType(2)
                        .build())
                .build();

        logd("apn1=" + dataProfile1.getApnSetting());
        logd("apn2=" + dataProfile2.getApnSetting());
        logd("apn1 can handle default=" + dataProfile1.getApnSetting()
                .canHandleType(ApnSetting.TYPE_DEFAULT));
        logd("apn2 can handle default=" + dataProfile2.getApnSetting()
                .canHandleType(ApnSetting.TYPE_DEFAULT));
        assertThat(dataProfile1.getApnSetting().similar(dataProfile2.getApnSetting())).isTrue();

        List<DataProfile> dataProfiles = new ArrayList<>(Arrays.asList(dataProfile2, dataProfile1));

        dedupeDataProfiles(dataProfiles);
        // After deduping, there should be only one.
        assertThat(dataProfiles).hasSize(1);

        DataProfile dataProfile = dataProfiles.get(0);
        assertThat(dataProfile.getApnSetting()).isNotNull();

        logd("Merged profile=" + dataProfile);
        assertThat(dataProfile.getApnSetting().getEntryName()).isEqualTo("general");
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo("apn");
        assertThat(dataProfile.getApnSetting().getApnTypeBitmask()).isEqualTo(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_SUPL
                        | ApnSetting.TYPE_HIPRI | ApnSetting.TYPE_XCAP);
        assertThat(dataProfile.getApnSetting().getUser()).isEqualTo("user");
        assertThat(dataProfile.getApnSetting().getPassword()).isEqualTo("password");
        assertThat(dataProfile.getApnSetting().getAuthType()).isEqualTo(ApnSetting.AUTH_TYPE_CHAP);
        assertThat(dataProfile.getApnSetting().getMmsc()).isEqualTo(Uri.parse("http://mms-s"));
        assertThat(dataProfile.getApnSetting().getMmsProxyAddressAsString())
                .isEqualTo("mmsc.proxy");
        assertThat(dataProfile.getApnSetting().getMmsProxyPort()).isEqualTo(8080);
        assertThat(dataProfile.getApnSetting().getProtocol()).isEqualTo(ApnSetting.PROTOCOL_IPV4V6);
        assertThat(dataProfile.getApnSetting().getRoamingProtocol())
                .isEqualTo(ApnSetting.PROTOCOL_IPV4V6);
    }

    @Test
    public void testDedupeDataProfiles3() throws Exception {
        DataProfile dataProfile1 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName("BTT Lastgenphone")
                        .setId(1)
                        .setOperatorNumeric("123456")
                        .setApnName("lastgenphone")
                        .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS
                                | ApnSetting.TYPE_SUPL | ApnSetting.TYPE_FOTA)
                        .setMmsc(Uri.parse("http://mmsc.mobile.btt.net"))
                        .setMmsProxyAddress("proxy.mobile.btt.net")
                        .setMmsProxyPort(80)
                        .setMtuV4(1410)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .build())
                .build();

        DataProfile dataProfile2 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName("BTT XCAP")
                        .setId(5)
                        .setOperatorNumeric("123456")
                        .setApnName("lastgenphone")
                        .setApnTypeBitmask(ApnSetting.TYPE_XCAP)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .build())
                .build();

        List<DataProfile> dataProfiles = new ArrayList<>(Arrays.asList(dataProfile2, dataProfile1));

        logd("dp1.apnSetting, dp2.apnSetting similar="
                + dataProfile1.getApnSetting().similar(dataProfile2.getApnSetting()));

        dedupeDataProfiles(dataProfiles);
        // After deduping, there should be only one.
        assertThat(dataProfiles).hasSize(1);

        DataProfile dataProfile = dataProfiles.get(0);
        assertThat(dataProfile.getApnSetting()).isNotNull();


        logd("After merged: " + dataProfile);
        assertThat(dataProfile.getApnSetting().getApnTypeBitmask()).isEqualTo(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_SUPL
                        | ApnSetting.TYPE_FOTA | ApnSetting.TYPE_XCAP);
    }

    @Test
    public void testDefaultEmergencyDataProfileValid() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);

        assertThat(dataProfile.getApn()).isEqualTo("sos");
        assertThat(dataProfile.getTrafficDescriptor().getDataNetworkName()).isEqualTo("sos");
    }

    @Test
    public void testResetApn() {
        changeSimStateTo(TelephonyManager.SIM_STATE_LOADED);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
        dataProfile.setLastSetupTimestamp(SystemClock.elapsedRealtime());
        DataNetwork internetNetwork = Mockito.mock(DataNetwork.class);
        doReturn(dataProfile).when(internetNetwork).getDataProfile();

        doReturn(new DataNetworkController.NetworkRequestList(List.of(tnr)))
                .when(internetNetwork).getAttachedNetworkRequestList();
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(
                Set.of(internetNetwork));
        processAllMessages();

        // After internet connected, preferred APN should be set
        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isTrue();

        // APN reset
        mPreferredApnId = -1;
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        // preferred APN should set to be the last data profile that succeeded for internet setup
        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isTrue();

        // Test user selected a bad data profile, expects to adopt the last data profile that
        // succeeded for internet setup after APN reset
        // some bad profile that cannot be used for internet
        mApnSettingContentProvider.setPreferredApn(MATCH_ALL_APN_SET_ID_IMS_APN);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isFalse();

        // APN reset, preferred APN should set to be the last data profile that succeeded for
        // internet setup
        mPreferredApnId = -1;
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isTrue();

        // Test removed data profile(user created after reset) shouldn't show up
        mDataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(
                Set.of(internetNetwork));
        processAllMessages();
        //APN reset and removed GENERAL_PURPOSE_APN from APN DB
        mPreferredApnId = -1;
        mApnSettingContentProvider.removeApnByApnId(1);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        // There should be no preferred APN after APN reset because last working profile is removed
        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isFalse();

        // restore mApnSettingContentProvider
        mApnSettingContentProvider.restoreApnSettings();
    }

    @Test
    public void testResetApnWithPreferredConfig() {
        // carrier configured preferred data profile should be picked
        doReturn(GENERAL_PURPOSE_APN1).when(mDataConfigManager).getDefaultPreferredApn();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        changeSimStateTo(TelephonyManager.SIM_STATE_LOADED);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        // The carrier configured data profile should be the preferred APN after APN reset
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);

        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN1);
        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isTrue();

        // APN reset
        mPreferredApnId = -1;
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        Mockito.clearInvocations(mDataConfigManager);
        processAllMessages();

        // The carrier configured data profile should be the preferred APN after APN reset
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, false, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN1);
        assertThat(mDataProfileManagerUT.isDataProfilePreferred(dataProfile)).isTrue();
    }

    @Test
    public void testTetheringApnExisting() {
        assertThat(mDataProfileManagerUT.isTetheringDataProfileExisting(
                TelephonyManager.NETWORK_TYPE_NR)).isTrue();
        assertThat(mDataProfileManagerUT.isTetheringDataProfileExisting(
                TelephonyManager.NETWORK_TYPE_LTE)).isFalse();
    }

    @Test
    public void testTetheringApnDisabledForRoaming() {
        doReturn(true).when(mDataConfigManager).isTetheringProfileDisabledForRoaming();

        assertThat(mDataProfileManagerUT.isTetheringDataProfileExisting(
                TelephonyManager.NETWORK_TYPE_NR)).isTrue();

        ServiceState ss = new ServiceState();

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.setDataRoamingFromRegistration(true);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        assertThat(mDataProfileManagerUT.isTetheringDataProfileExisting(
                TelephonyManager.NETWORK_TYPE_NR)).isFalse();
    }

    @Test
    public void testNoDefaultIms() throws Exception {
        List<DataProfile> dataProfiles = getAllDataProfiles();
        int countIms =  mApnSettingContentProvider.getCountApnSpecifyType("ims");

        // Since the database already had IMS, there should not be default IMS created in the
        // database.
        assertThat(dataProfiles.stream()
                .filter(dp -> dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_IMS))
                .collect(Collectors.toList())).hasSize(countIms);
    }

    @Test
    public void testDataProfileCompatibility() {
        DataProfile enterpriseDataProfile = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null,
                        new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                                "ENTERPRISE", 1).getBytes()))
                .build();

        // Make sure the TD only profile is always compatible.
        assertThat(mDataProfileManagerUT.isDataProfileCompatible(enterpriseDataProfile)).isTrue();

        // Make sure the profile which is slightly modified is also compatible.
        DataProfile dataProfile1 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(GENERAL_PURPOSE_APN)
                        .setId(1)
                        .setOperatorNumeric(PLMN)
                        .setApnName(GENERAL_PURPOSE_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS
                                | ApnSetting.TYPE_SUPL | ApnSetting.TYPE_IA | ApnSetting.TYPE_FOTA)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_NONE)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setPersistent(true)
                        .setMtuV4(1280)
                        .setMtuV6(1280)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(DEFAULT_APN_SET_ID)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile1)).isTrue();
    }

    @Test
    public void testDataProfileCompatibility_FilteringWithPreferredApnSetIdAsDefault() {
        mApnSettingContentProvider.setPreferredApn(GENERAL_PURPOSE_APN);
        changeSimStateTo(TelephonyManager.SIM_STATE_LOADED);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        // Test DataProfile's apn_set_id is default so it match with current preferred apn_set_id.
        DataProfile dataProfile1 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(TETHERING_APN)
                        .setOperatorNumeric(PLMN)
                        .setApnName(TETHERING_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_DUN)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_UNKNOWN)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setProfileId(2)
                        .setPersistent(true)
                        .setMtuV4(0)
                        .setMtuV6(0)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(DEFAULT_APN_SET_ID)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile1)).isTrue();

        // Test DataProfile's apn_set_id is specified(1) so it not match with current
        // preferred apn_set_id.
        DataProfile dataProfile2 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(APN_SET_ID_1_TETHERING_APN)
                        .setOperatorNumeric(PLMN)
                        .setApnName(APN_SET_ID_1_TETHERING_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_DUN)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_UNKNOWN)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setPersistent(true)
                        .setMtuV4(1280)
                        .setMtuV6(1280)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(APN_SET_ID_1)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile2)).isFalse();

        // Test DataProfile has Telephony.Carriers.MATCH_ALL_APN_SET_ID so it matches any preferred
        // apn_set_id.
        DataProfile dataProfile3 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(MATCH_ALL_APN_SET_ID_IMS_APN)
                        .setOperatorNumeric(PLMN)
                        .setApnName(MATCH_ALL_APN_SET_ID_IMS_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_UNKNOWN)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setPersistent(true)
                        .setMtuV4(1280)
                        .setMtuV6(1280)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(MATCH_ALL_APN_SET_ID)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile3)).isTrue();
    }

    @Test
    public void testDataProfileCompatibility_FilteringWithPreferredApnSetIdAs1() {
        mApnSettingContentProvider.setPreferredApn(APN_SET_ID_1_APN);
        changeSimStateTo(TelephonyManager.SIM_STATE_LOADED);
        mDataProfileManagerUT.obtainMessage(2 /*EVENT_APN_DATABASE_CHANGED*/).sendToTarget();
        processAllMessages();

        // Test DataProfile's apn_set_id is same as preferred apn_set_id.
        DataProfile dataProfile1 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(APN_SET_ID_1_TETHERING_APN)
                        .setOperatorNumeric(PLMN)
                        .setApnName(APN_SET_ID_1_TETHERING_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_DUN)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_UNKNOWN)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setPersistent(true)
                        .setMtuV4(1280)
                        .setMtuV6(1280)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(APN_SET_ID_1)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile1)).isTrue();

        // Test DataProfile's apn_set_id is default. so it not match with current preferred
        // apn_set_id(1).
        DataProfile dataProfile2 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(TETHERING_APN)
                        .setOperatorNumeric(PLMN)
                        .setApnName(TETHERING_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_DUN)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_UNKNOWN)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setProfileId(2)
                        .setPersistent(true)
                        .setMtuV4(0)
                        .setMtuV6(0)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(DEFAULT_APN_SET_ID)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile2)).isFalse();

        // Test DataProfile has Telephony.Carriers.MATCH_ALL_APN_SET_ID so it matches any preferred
        // apn_set_id.
        DataProfile dataProfile3 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName(MATCH_ALL_APN_SET_ID_IMS_APN)
                        .setOperatorNumeric(PLMN)
                        .setApnName(MATCH_ALL_APN_SET_ID_IMS_APN)
                        .setProxyAddress("")
                        .setMmsProxyAddress("")
                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                        .setUser("")
                        .setPassword("")
                        .setAuthType(ApnSetting.AUTH_TYPE_UNKNOWN)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
                        .setPersistent(true)
                        .setMtuV4(1280)
                        .setMtuV6(1280)
                        .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                        .setMvnoMatchData("")
                        .setApnSetId(MATCH_ALL_APN_SET_ID)
                        .setInfrastructureBitmask(1)
                        .build())
                .build();

        assertThat(mDataProfileManagerUT.isDataProfileCompatible(dataProfile3)).isTrue();
    }

    @Test
    public void testPermanentFailureWithNoPreferredDataProfile() {
        // No preferred APN is set

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);

        // Mark the APN as permanent failed.
        dp.getApnSetting().setPermanentFailed(true);

        // Data profile manager should return a different data profile for setup as the previous
        // data profile has been marked as permanent failed.
        assertThat(mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false))
                .isNotEqualTo(dp);
    }

    @Test
    public void testPermanentFailureWithPreferredDataProfile() {
        // Set the preferred APN
        mApnSettingContentProvider.setPreferredApn(GENERAL_PURPOSE_APN);
        mDataProfileManagerUT.obtainMessage(2 /* EVENT_APN_DATABASE_CHANGED */).sendToTarget();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false);

        // Mark the APN as permanent failed.
        dp.getApnSetting().setPermanentFailed(true);

        // Since preferred APN is already set, and that data profile was marked as permanent failed,
        // so this should result in getting nothing.
        assertThat(mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false))
                .isNull();
    }

    private void changeSimStateTo(@TelephonyManager.SimState int simState) {
        mSimInserted = simState == TelephonyManager.SIM_STATE_LOADED;
        mDataNetworkControllerCallback.onSimStateChanged(simState);
    }

    @Test
    public void testClearAllDataProfilePermanentFailures() {
        testPermanentFailureWithPreferredDataProfile();

        // Reset all data profiles
        mDataProfileManagerUT.clearAllDataProfilePermanentFailures();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();

        // Verify the we can get the previously permanent failed data profile again.
        assertThat(mDataProfileManagerUT.getDataProfileForNetworkRequest(
                new TelephonyNetworkRequest(request, mPhone),
                TelephonyManager.NETWORK_TYPE_LTE, false, false, false))
                .isNotNull();
    }

    @Test
    public void testDifferentNetworkRequestProfilesOnEsimBootStrapProvisioning() {
        Mockito.clearInvocations(mDataProfileManagerCallback);
        Mockito.clearInvocations(mMockedWwanDataServiceManager);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        // SIM inserted
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();

        // expect default profile for internet network request, when esim bootstrap provisioning
        // flag is enabled at data profile, during esim bootstrap provisioning
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, true, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(
                "ESIM_BOOTSTRAP_PROVISIONING_APN");

        // expect IMS profile for ims network request, when esim bootstrap provisioning flag
        // is enabled at data profile, during esim bootstrap provisioning
        tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                 .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                 .build(), mPhone);
        dataProfile  = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, true, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo("IMS_APN");

        // expect no mms profile for mms network request, when esim bootstrap provisioning flag
        // is disabled at data profile, during esim bootstrap provisioning
        tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .build(), mPhone);
        dataProfile  = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, true, false);
        assertThat(dataProfile).isEqualTo(null);

        // expect no rcs profile for rcs network request, when esim bootstrap provisioning flag
        // is disabled at data profile, during esim bootstrap provisioning
        tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_RCS)
                .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, true, false);
        assertThat(dataProfile).isEqualTo(null);
    }

    @Test
    public void testEsimBootstrapProvisioningEnabled_MultipleProfile() {
        Mockito.clearInvocations(mDataProfileManagerCallback);
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        // SIM inserted
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();

        // expect initial default profile entry selected for internet network request, when
        // multiple esim bootstrap provisioning flag is enabled at data profile for same apn
        // type
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, false, true, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(
                ESIM_BOOTSTRAP_PROVISIONING_APN);
    }

    @Test
    public void testInfrastructureProfileOnEsimBootStrapProvisioning() {
        Mockito.clearInvocations(mDataProfileManagerCallback);
        Mockito.clearInvocations(mMockedWwanDataServiceManager);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        // SIM inserted
        mDataProfileManagerUT.obtainMessage(3 /* EVENT_SIM_REFRESH */).sendToTarget();
        processAllMessages();

        // expect initial default profile entry selected for internet network request, when
        // multiple esim bootstrap provisioning flag is enabled at data profile for same apn
        // type
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_RCS)
                .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE, true, true, false);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(RCS_APN1);
    }

}
