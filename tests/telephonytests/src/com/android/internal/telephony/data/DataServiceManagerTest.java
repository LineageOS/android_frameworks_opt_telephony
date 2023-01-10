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

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.R;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataServiceManagerTest extends TelephonyTest {
    private final DataProfile mGeneralPurposeDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2163)
                    .setOperatorNumeric("12345")
                    .setEntryName("internet_supl_mms_apn")
                    .setApnName("internet_supl_mms_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL
                            | ApnSetting.TYPE_MMS)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN
                            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT))
                    .setLingeringNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT
                            | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
                            | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                    .setProfileId(1234)
                    .setMaxConns(321)
                    .setWaitTime(456)
                    .setMaxConnsTime(789)
                    .build())
            .setPreferred(false)
            .build();

    private DataServiceManager mDataServiceManagerUT;
    private CellularDataService mCellularDataService;

    private Handler mHandler;
    private Handler mDataServiceHandler;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        mDataServiceManagerUT = null;
        super.tearDown();
    }

    private void createDataServiceManager(boolean validDataServiceExisting) throws Exception {
        if (validDataServiceExisting) {
            mContextFixture.putResource(R.string.config_wwan_data_service_package,
                    "com.android.phone");
        }

        mHandler = mock(Handler.class);

        mCellularDataService = new CellularDataService();

        Field field = DataService.class.getDeclaredField("mHandler");
        field.setAccessible(true);
        mDataServiceHandler = (Handler) field.get(mCellularDataService);

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

        mDataServiceManagerUT = new DataServiceManager(mPhone, Looper.myLooper(),
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    private void waitAndVerifyResult(Message message, int resultCode) {
        waitForLastHandlerAction(mDataServiceHandler);
        processAllMessages();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(message.getTarget()).sendMessageAtTime(messageCaptor.capture(), anyLong());
        assertThat(messageCaptor.getValue().arg1).isEqualTo(resultCode);
    }

    @Test
    public void testSetupDataCall() throws Exception {
        createDataServiceManager(true);
        Message message = Message.obtain(mHandler, 1234);
        mDataServiceManagerUT.setupDataCall(AccessNetworkType.NGRAN, mGeneralPurposeDataProfile,
                false, true, DataService.REQUEST_REASON_NORMAL, null, 1, null, null, false,
                message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_SUCCESS);
        verify(mSimulatedCommandsVerifier).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    @Test
    public void testSetupDataCallServiceNotBound() throws Exception {
        createDataServiceManager(false);
        Message message = Message.obtain(mHandler, 1234);
        mDataServiceManagerUT.setupDataCall(AccessNetworkType.NGRAN, mGeneralPurposeDataProfile,
                false, true, DataService.REQUEST_REASON_NORMAL, null, 1, null, null, false,
                message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
        verify(mSimulatedCommandsVerifier, never()).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    @Test
    public void testDeactivateDataCall() throws Exception {
        createDataServiceManager(true);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.deactivateDataCall(123, DataService.REQUEST_REASON_NORMAL, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_SUCCESS);
        verify(mSimulatedCommandsVerifier).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));
    }

    @Test
    public void testDeactivateDataCallServiceNotBound() throws Exception {
        createDataServiceManager(false);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.deactivateDataCall(123, DataService.REQUEST_REASON_NORMAL, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
        verify(mSimulatedCommandsVerifier, never()).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));
    }

    @Test
    public void testSetInitialAttachApn() throws Exception {
        createDataServiceManager(true);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.setInitialAttachApn(mGeneralPurposeDataProfile, false, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_SUCCESS);
        verify(mSimulatedCommandsVerifier).setInitialAttachApn(any(DataProfile.class),
                anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetInitialAttachApnServiceNotBound() throws Exception {
        createDataServiceManager(false);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.setInitialAttachApn(mGeneralPurposeDataProfile, false, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
        verify(mSimulatedCommandsVerifier, never()).setInitialAttachApn(any(DataProfile.class),
                anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetDataProfile() throws Exception {
        createDataServiceManager(true);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.setDataProfile(List.of(mGeneralPurposeDataProfile), false, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_SUCCESS);
        verify(mSimulatedCommandsVerifier).setDataProfile(any(DataProfile[].class), anyBoolean(),
                any(Message.class));
    }

    @Test
    public void testSetDataProfileServiceNotBound() throws Exception {
        createDataServiceManager(false);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.setDataProfile(List.of(mGeneralPurposeDataProfile), false, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
        verify(mSimulatedCommandsVerifier, never()).setDataProfile(any(DataProfile[].class),
                anyBoolean(), any(Message.class));
    }

    @Test
    public void testStartHandover() throws Exception {
        createDataServiceManager(true);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.startHandover(123, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_SUCCESS);
        verify(mSimulatedCommandsVerifier).startHandover(any(Message.class), anyInt());
    }

    @Test
    public void testStartHandoverServiceNotBound() throws Exception {
        createDataServiceManager(false);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.startHandover(123, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
        verify(mSimulatedCommandsVerifier, never()).startHandover(any(Message.class), anyInt());
    }

    @Test
    public void testCancelHandover() throws Exception {
        createDataServiceManager(true);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.cancelHandover(123, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_SUCCESS);
        verify(mSimulatedCommandsVerifier).cancelHandover(any(Message.class), anyInt());
    }

    @Test
    public void testCancelHandoverServiceNotBound() throws Exception {
        createDataServiceManager(false);
        Message message = mHandler.obtainMessage(1234);
        mDataServiceManagerUT.cancelHandover(123, message);
        waitAndVerifyResult(message, DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
        verify(mSimulatedCommandsVerifier, never()).cancelHandover(any(Message.class), anyInt());
    }
}
