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

import static com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver.UPDATE_CONTENT_PATH;
import static com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver.UPDATE_DIR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.satellite.SatelliteConfigParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Base64;
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
        assertEquals(new File(new File(UPDATE_DIR), UPDATE_CONTENT_PATH).toString(),
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

        // mock BeforeParser
        String mBase64StrForPBByteArray =
                "CjYIBBIeCAESDgoGMzEwMTYwEAEQAhADEgoKBjMxMDIyMBADGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
        byte[] mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getCurrentContent();
        SatelliteConfigParser mMockSatelliteConfigParserBefore =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        doReturn(mMockSatelliteConfigParserBefore).when(
                spyTelephonyConfigUpdateInstallReceiver).getConfigParser(DOMAIN_SATELLITE);

        // mock UpdatedParser
        SatelliteConfigParser spySatelliteConfigParserAfter =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        doReturn(5).when(spySatelliteConfigParserAfter).getVersion();
        doReturn(spySatelliteConfigParserAfter).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(any(), any());

        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);

        assertSame(spyTelephonyConfigUpdateInstallReceiver,
                TelephonyConfigUpdateInstallReceiver.getInstance());

        ConcurrentHashMap<Executor, ConfigProviderAdaptor.Callback> spyCallbackHashMap = spy(
                new ConcurrentHashMap<>());
        spyCallbackHashMap.put(mExecutor, mCallback);
        spyTelephonyConfigUpdateInstallReceiver.setCallbackMap(spyCallbackHashMap);
        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, atLeast(1)).keySet();
    }


    @Test
    public void testGetConfig() throws Exception {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());

        doReturn(null).when(
                spyTelephonyConfigUpdateInstallReceiver).getCurrentContent();

        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);
        assertNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_SATELLITE));

        String mBase64StrForPBByteArray =
                "CjYIBBIeCAESDgoGMzEwMTYwEAEQAhADEgoKBjMxMDIyMBADGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
        byte[] mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);
        doReturn(mBytesProtoBuffer).when(
                spyTelephonyConfigUpdateInstallReceiver).getCurrentContent();

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
}
