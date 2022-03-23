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
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;
import android.telephony.data.TrafficDescriptor.OsAppId;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import com.android.internal.telephony.data.DataProfileManager.DataProfileManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataProfileManagerTest extends TelephonyTest {
    private static final String GENERAL_PURPOSE_APN = "GP_APN";
    private static final String GENERAL_PURPOSE_APN1 = "GP_APN1";
    private static final String IMS_APN = "IMS_APN";
    private static final String TETHERING_APN = "DUN_APN";
    private static final String PLMN = "330123";

    @Mock
    private DataProfileManagerCallback mDataProfileManagerCallback;

    private DataProfileManager mDataProfileManagerUT;

    private final ApnSettingContentProvider mApnSettingContentProvider =
            new ApnSettingContentProvider();

    private int mPreferredApnId = 0;

    private DataNetworkControllerCallback mDataNetworkControllerCallback;

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
                        -1,                     // mtu
                        1280,                   // mtu_v4
                        1280,                   // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        0,                      // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0                       // always_on
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
                        0,                      // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0                       // always_on
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
                        0,                      // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0                       // alwys_on
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
                        0,                      // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0                       // always_on
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
                        -1,                     // mtu
                        -1,                     // mtu_v4
                        -1,                     // mtu_v6
                        "",                     // mvno_type
                        "",                     // mnvo_match_data
                        TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                                | TelephonyManager.NETWORK_TYPE_BITMASK_NR, // network_type_bitmask
                        0,                      // lingering_network_type_bitmask
                        0,                      // apn_set_id
                        -1,                     // carrier_id
                        -1,                     // skip_464xlat
                        0                       // always_on
                }
        );

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
                            mc.addRow((Object[]) apnSetting);
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
                    int id = (int) ((Object[]) apnSetting)[0];
                    if (id == mPreferredApnId) {
                        MatrixCursor mc = new MatrixCursor(APN_COLUMNS);
                        mc.addRow((Object[]) apnSetting);
                        return mc;
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
                logd("mPreferredApnId=" + mPreferredApnId);
            }
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

    private void dedupeDataProfiles(@NonNull List<DataProfile> dataProfiles) throws Exception {
        Class[] cArgs = new Class[1];
        cArgs[0] = List.class;
        Method method = DataProfileManager.class.getDeclaredMethod("dedupeDataProfiles", cArgs);
        method.setAccessible(true);
        method.invoke(mDataProfileManagerUT, dataProfiles);
    }

    @Before
    public void setUp() throws Exception {
        logd("DataProfileManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        doReturn(true).when(mPhone).isUsingNewDataStack();
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
                mMockedWwanDataServiceManager, Looper.myLooper(), mDataProfileManagerCallback);
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController).registerDataNetworkControllerCallback(
                        dataNetworkControllerCallbackCaptor.capture());
        mDataNetworkControllerCallback = dataNetworkControllerCallbackCaptor.getValue();
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
    public void testGetDataProfileForNetworkRequestNoCompatibleRat() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataProfile dp = mDataProfileManagerUT.getDataProfileForNetworkRequest(tnr,
                TelephonyManager.NETWORK_TYPE_GSM);
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
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
        logd("Set setLastSetupTimestamp on " + dataProfile);
        dataProfile.setLastSetupTimestamp(SystemClock.elapsedRealtime());

        // See if another one can be returned.
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN1);
    }

    @Test
    public void testGetDataProfileForEnterpriseNetworkRequest() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
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
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
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
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
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
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting()).isNull();
        OsAppId osAppId = new OsAppId(dataProfile.getTrafficDescriptor().getOsAppId());

        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("PRIORITIZE_BANDWIDTH");
        assertThat(osAppId.getDifferentiator()).isEqualTo(1);
    }


    @Test
    public void testSetPreferredDataProfile() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
        dataProfile.setLastSetupTimestamp(SystemClock.elapsedRealtime());
        dataProfile.setPreferred(true);
        mDataNetworkControllerCallback.onInternetDataNetworkConnected(List.of(dataProfile));
        processAllMessages();

        // See if the same one can be returned.
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo(GENERAL_PURPOSE_APN);
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
    public void testSimRemoval() {
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
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile).isNull();

        tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                        .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo("sos");

        tnr = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnName()).isEqualTo("ims");
    }

    @Test
    public void testSimInsertedAgain() throws Exception {
        testSimRemoval();
        Mockito.clearInvocations(mDataProfileManagerCallback);
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        doReturn(List.of(ApnSetting.TYPE_IMS))
                .when(mDataConfigManager).getAllowedInitialAttachApnTypes();

        mSimInserted = true;
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
                TelephonyManager.NETWORK_TYPE_LTE);
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
                TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
    }

    @Test
    public void testDedupeDataProfiles2() throws Exception {
        DataProfile dataProfile1 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName("general")
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
                        .build())
                .build();

        DataProfile dataProfile2 = new DataProfile.Builder()
                .setApnSetting(new ApnSetting.Builder()
                        .setEntryName("XCAP")
                        .setApnName("apn")
                        .setApnTypeBitmask(ApnSetting.TYPE_XCAP)
                        .setUser("user")
                        .setPassword("password")
                        .setAuthType(ApnSetting.AUTH_TYPE_CHAP)
                        .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setCarrierEnabled(true)
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
    public void testIsDataProfileValid() {
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone);
        DataProfile dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnSetId()).isEqualTo(
                Telephony.Carriers.NO_APN_SET_ID);
        assertThat(mDataProfileManagerUT.isDataProfileValid(dataProfile)).isTrue();

        tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                .build(), mPhone);
        dataProfile = mDataProfileManagerUT.getDataProfileForNetworkRequest(
                tnr, TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(dataProfile.getApnSetting().getApnSetId()).isEqualTo(
                Telephony.Carriers.MATCH_ALL_APN_SET_ID);
        assertThat(mDataProfileManagerUT.isDataProfileValid(dataProfile)).isTrue();
    }
}
