/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.configupdate;

import static android.telephony.NetworkRegistrationInfo.FIRST_SERVICE_TYPE;
import static android.telephony.NetworkRegistrationInfo.LAST_SERVICE_TYPE;

import static com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver.NEW_CONFIG_CONTENT_PATH;
import static com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver.UPDATE_DIR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConfigParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TelephonyConfigUpdateInstallReceiverTest extends TelephonyTest {

    public static final String DOMAIN_SATELLITE = "satellite";
    @Mock
    private Executor mExecutor;
    @Mock
    private ConfigProviderAdaptor.Callback mCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testTelephonyConfigUpdateInstallReceiver() {
        TelephonyConfigUpdateInstallReceiver testReceiver =
                new TelephonyConfigUpdateInstallReceiver();
        assertEquals(UPDATE_DIR, testReceiver.getUpdateDir().toString());
        assertEquals(new File(new File(UPDATE_DIR), NEW_CONFIG_CONTENT_PATH).toString(),
                testReceiver.getUpdateContent().toString());
    }

    @Test
    public void testGetInstance() {
        TelephonyConfigUpdateInstallReceiver testReceiver1 =
                TelephonyConfigUpdateInstallReceiver.getInstance();
        TelephonyConfigUpdateInstallReceiver testReceiver2 =
                TelephonyConfigUpdateInstallReceiver.getInstance();
        assertSame(testReceiver1, testReceiver2);
    }

    @Test
    public void testPostInstall() throws Exception {
        // create spyTelephonyConfigUpdateInstallReceiver
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .copySourceFileToTargetFile(any(), any());
        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);

        assertSame(spyTelephonyConfigUpdateInstallReceiver,
                TelephonyConfigUpdateInstallReceiver.getInstance());

        // valid config data case
        // mVersion:4 | mSupportedServicesPerCarrier:{1={310160=[1, 2, 3], 310220=[3]}} |
        // mSatelliteRegionCountryCodes:[US] | mIsSatelliteRegionAllowed:true | s2CellFile size:10
        String mBase64StrForPBByteArray =
                "CjYIBBIeCAESDgoGMzEwMTYwEAEQAhADEgoKBjMxMDIyMBADGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
        byte[] mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getContentFromContentPath(any());

        // mock UpdatedParser
        SatelliteConfigParser spyValidParser =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));

        ConcurrentHashMap<Executor, ConfigProviderAdaptor.Callback> spyCallbackHashMap = spy(
                new ConcurrentHashMap<>());
        spyCallbackHashMap.put(mExecutor, mCallback);
        spyTelephonyConfigUpdateInstallReceiver.setCallbackMap(spyCallbackHashMap);

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(2)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(1))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);

        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "mConfigParser",
                spyTelephonyConfigUpdateInstallReceiver, spyValidParser);

        // valid config data but smaller version case
        // mVersion:3 | mSupportedServicesPerCarrier:{1={12345=[1, 2]}} |
        // mSatelliteRegionCountryCodes:[US] | mIsSatelliteRegionAllowed:true | s2CellFile size:10
        mBase64StrForPBByteArray =
                "CicIAxIPCAESCwoFMTIzNDUQARACGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
        mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);

        // mock UpdatedParser
        SatelliteConfigParser spyInvalidParser =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        doReturn(spyInvalidParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(any(), any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(0)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(0))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);

        // Empty config data case which is valid
        // mSupportedServicesPerCarrier:{} | mSatelliteRegionCountryCodes:[US] |
        // mIsSatelliteRegionAllowed:true | s2CellFile size:30
        mBase64StrForPBByteArray =
                "CioIDBomCh4wMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkSAlVTGAE=";
        mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getContentFromContentPath(any());

        // mock UpdatedParser
        SatelliteConfigParser spyValidEmptyParser =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        doReturn(spyValidEmptyParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(any(), any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());
        verify(spyCallbackHashMap, times(2)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(1))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);

        // Wrong plmn("1234") config data case
        // mSupportedServicesPerCarrier:{1={"1234"=[1, 2, 3]}} |
        // mSatelliteRegionCountryCodes:[US]
        // | mIsSatelliteRegionAllowed:true | s2CellFile size:10
        mBase64StrForPBByteArray =
                "CigIDBIQCAESDAoEMTIzNBABEAIQAxoSCgowMTIzNDU2Nzg5EgJVUxgB";
        mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getContentFromContentPath(any());

        // mock UpdatedParser
        spyInvalidParser =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        doReturn(spyInvalidParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(any(), any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(0)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(0))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);

        // Wrong service("8") config data case
        // mSupportedServicesPerCarrier:{1={12345=[6, "8"]}} |
        // mSatelliteRegionCountryCodes:[US] |
        // mIsSatelliteRegionAllowed:true | s2CellFile size:10
        mBase64StrForPBByteArray =
                "CicIDBIPCAESCwoFMTIzNDUQBhAIGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
        mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getContentFromContentPath(any());

        // mock UpdatedParser
        spyInvalidParser =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        doReturn(spyInvalidParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(any(), any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(0)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(0))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);
    }


    @Test
    public void testGetConfig() throws Exception {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());

        doReturn(null).when(
                spyTelephonyConfigUpdateInstallReceiver).getContentFromContentPath(any());

        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);
        assertNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_SATELLITE));

        String mBase64StrForPBByteArray =
                "CjYIBBIeCAESDgoGMzEwMTYwEAEQAhADEgoKBjMxMDIyMBADGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
        byte[] mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getContentFromContentPath(any());

        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);

        assertNotNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_SATELLITE));
    }

    @Test
    public void testRegisterUnRegisterCallback() {
        TelephonyConfigUpdateInstallReceiver testReceiver =
                TelephonyConfigUpdateInstallReceiver.getInstance();

        ConfigProviderAdaptor.Callback testCallback = new ConfigProviderAdaptor.Callback() {
            @Override
            public void onChanged(@Nullable ConfigParser config) {
                super.onChanged(config);
            }
        };
        Executor executor = Executors.newSingleThreadExecutor();

        testReceiver.registerCallback(executor, testCallback);
        assertSame(testCallback, testReceiver.getCallbackMap().get(executor));

        testReceiver.unregisterCallback(testCallback);
        assertEquals(0, testReceiver.getCallbackMap().size());
    }

    @Test
    public void testIsValidSatelliteCarrierConfigData() {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        SatelliteConfigParser mockParser = mock(SatelliteConfigParser.class);
        SatelliteConfig mockConfig = mock(SatelliteConfig.class);
        doReturn(new ArraySet<>()).when(mockConfig).getAllSatelliteCarrierIds();
        doReturn(mockConfig).when(mockParser).getConfig();

        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> validPlmnsServices = new HashMap<>();
        validPlmnsServices.put("123456", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE));
        validPlmnsServices.put("12345", Set.of(FIRST_SERVICE_TYPE, 4, LAST_SERVICE_TYPE));
        doReturn(validPlmnsServices).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();

        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices1 = new HashMap<>();
        invalidPlmnsServices1.put("123456", Set.of(FIRST_SERVICE_TYPE - 1, 3, LAST_SERVICE_TYPE));
        doReturn(invalidPlmnsServices1).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices2 = new HashMap<>();
        invalidPlmnsServices2.put("123456", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE + 1));
        doReturn(invalidPlmnsServices2).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices3 = new HashMap<>();
        invalidPlmnsServices3.put("1234", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE));
        doReturn(invalidPlmnsServices3).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices4 = new HashMap<>();
        invalidPlmnsServices4.put("1234567", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE));
        doReturn(invalidPlmnsServices4).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));
    }
}
