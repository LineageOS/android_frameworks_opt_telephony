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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.DataProfile;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataProfileManager.DataProfileManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataProfileManagerTest extends TelephonyTest {
    private static final String GENERAL_PURPOSE_APN = "GP_APN";
    private static final String IMS_APN = "IMS_APN";
    private static final String TETHERING_APN = "DUN_APN";
    private static final String PLMN = "330123";

    @Mock
    private DataServiceManager mDataServiceManager;

    @Mock
    private DataProfileManagerCallback mDataProfileManagerCallback;

    private DataProfileManager mDataProfileManagerUT;

    private final ApnSettingContentProvider mApnSettingContentProvider =
            new ApnSettingContentProvider();

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
        };

        private int mPreferredApnSet = 0;

        private Object[] mPreferredApn = null;

        public Object[] getFakeApn1() {
            return new Object[]{
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
                    "default,supl,mms",     // types
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
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1,                     // skip_464xlat
                    0                       // always_on
            };
        }

        public Object[] getFakeApn2() {
            return new Object[]{
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
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1,                     // skip_464xlat
                    0                       // always_on
            };
        }

        public Object[] getFakeApn3() {
            return new Object[]{
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
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1,                     // skip_464xlat
                    0                       // alwys_on
            };
        }

        public Object[] getFakeApn4() {
            return new Object[]{
                    4,                      // id
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
                    0,                      // apn_set_id
                    -1,                     // carrier_id
                    -1,                     // skip_464xlat
                    0                       // always_on
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

                    logd("Query '" + PLMN + "' APN settings");
                    MatrixCursor mc = new MatrixCursor(APN_COLUMNS);
                    mc.addRow(getFakeApn1());
                    mc.addRow(getFakeApn2());
                    mc.addRow(getFakeApn3());
                    mc.addRow(getFakeApn4());

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
                    MatrixCursor mc = new MatrixCursor(APN_COLUMNS);
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

    @Before
    public void setUp() throws Exception {
        logd("DataProfileManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mApnSettingContentProvider);

        doReturn(true).when(mDataConfigManager).isConfigCarrierSpecific();
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mDataProfileManagerCallback).invokeFromExecutor(any(Runnable.class));
        mDataProfileManagerUT = new DataProfileManager(mPhone, mDataNetworkController,
                mDataServiceManager, Looper.myLooper(), mDataProfileManagerCallback);
        mDataProfileManagerUT.obtainMessage(1).sendToTarget();
        processAllMessages();

        logd("DataProfileManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetDataProfileForNetworkRequest() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE);

        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE);

        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(IMS_APN);

        request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                .build();
        tnr = new TelephonyNetworkRequest(request, mPhone);
        dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_LTE);
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
                TelephonyManager.NETWORK_TYPE_NR);
        assertThat(dp.canSatisfy(tnr.getCapabilities())).isTrue();
        assertThat(dp.getApnSetting().getApnName()).isEqualTo(TETHERING_APN);
    }

    @Test
    public void testGetDataProfileForNetworkCapabilities() {
        List<DataProfile> dataProfiles = mDataProfileManagerUT
                .getDataProfilesForNetworkCapabilities(
                        new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET});
        assertThat(dataProfiles.size()).isEqualTo(2);

        DataProfile dp1 = dataProfiles.get(0);
        DataProfile dp2 = dataProfiles.get(1);
        dp1.setLastSetupTimestamp(1234);
        dp2.setLastSetupTimestamp(5678);

        dataProfiles = mDataProfileManagerUT
                .getDataProfilesForNetworkCapabilities(
                        new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET});
        assertThat(dataProfiles.size()).isEqualTo(2);
        // Make sure the profiles that haven't been used for longest time gets returned at the head
        // of list.
        assertThat(dataProfiles.get(0)).isEqualTo(dp1);
        assertThat(dataProfiles.get(1)).isEqualTo(dp2);


        dp1.setLastSetupTimestamp(9876);
        dp2.setLastSetupTimestamp(5432);

        dataProfiles = mDataProfileManagerUT
                .getDataProfilesForNetworkCapabilities(
                        new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET});
        // Make sure the profiles that haven't been used for longest time gets returned at the head
        // of list.
        assertThat(dataProfiles).containsExactly(dp2, dp1).inOrder();
    }
}
