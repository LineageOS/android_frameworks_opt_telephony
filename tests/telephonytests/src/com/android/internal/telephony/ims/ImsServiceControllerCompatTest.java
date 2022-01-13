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

package com.android.internal.telephony.ims;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.ims.ImsFeatureBinderRepository;
import com.android.ims.ImsFeatureContainer;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsMMTelFeature;
import com.android.ims.internal.IImsServiceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
public class ImsServiceControllerCompatTest extends ImsTestBase {

    private static final int SLOT_0 = 0;

    private static final ImsServiceController.RebindRetry REBIND_RETRY =
            new ImsServiceController.RebindRetry() {
                @Override
                public long getStartDelay() {
                    return 50;
                }

                @Override
                public long getMaximumDelay() {
                    return 1000;
                }
            };

    @Mock MmTelInterfaceAdapter mMockMmTelInterfaceAdapter;
    @Mock IImsMMTelFeature mMockRemoteMMTelFeature;
    @Mock IBinder mMockMMTelBinder;
    @Mock IImsServiceController mMockServiceControllerBinder;
    @Mock ImsServiceController.ImsServiceControllerCallbacks mMockCallbacks;
    @Mock IImsConfig mMockImsConfig;
    @Mock Context mMockContext;
    private final ComponentName mTestComponentName = new ComponentName("TestPkg",
            "ImsServiceControllerTest");
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ImsServiceController mTestImsServiceController;
    private ImsFeatureBinderRepository mRepo;
    private MmTelFeatureCompatAdapter mMmTelCompatAdapterSpy;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mRepo = new ImsFeatureBinderRepository();
        // Can't mock MmTelFeatureCompatAdapter due to final getBinder() method.
        mMmTelCompatAdapterSpy = spy(new MmTelFeatureCompatAdapter(mMockContext, SLOT_0,
                mMockMmTelInterfaceAdapter));
        mTestImsServiceController = new ImsServiceControllerCompat(mMockContext, mTestComponentName,
                mMockCallbacks, mHandler, REBIND_RETRY, mRepo,
                (a, b, c) -> mMmTelCompatAdapterSpy);
        when(mMockContext.bindService(any(), any(), anyInt())).thenReturn(true);
        when(mMockServiceControllerBinder.createMMTelFeature(anyInt()))
                .thenReturn(mMockRemoteMMTelFeature);
        when(mMockRemoteMMTelFeature.getConfigInterface()).thenReturn(mMockImsConfig);
        when(mMockRemoteMMTelFeature.asBinder()).thenReturn(mMockMMTelBinder);
    }


    @After
    @Override
    public void tearDown() throws Exception {
        mTestImsServiceController.stopBackoffTimerForTesting();
        mTestImsServiceController = null;
        // Make sure the handler is empty before finishing the test.
        waitForHandlerAction(mHandler, 1000);
        super.tearDown();
    }

    /**
     * Tests that the MmTelFeatureCompatAdapter is cleaned up properly and ImsServiceController
     * callbacks are properly called when an ImsService is bound and then crashes.
     */
    @SmallTest
    @Test
    public void testBindServiceAndCrashCleanUp() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();
        // add the MMTelFeature
        verify(mMockServiceControllerBinder).createMMTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        // Remove the feature
        conn.onBindingDied(mTestComponentName);
        verify(mMmTelCompatAdapterSpy).onFeatureRemoved();
        verify(mMockServiceControllerBinder).removeImsFeature(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
    }

    private void validateMmTelFeatureContainerExists(int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNotNull("MMTEL FeatureContainer should not be null", fc);
        assertEquals("ImsServiceController did not report MmTelFeature to service repo correctly",
                mMmTelCompatAdapterSpy.getBinder(), fc.imsFeature);
        assertEquals(0, (android.telephony.ims.ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL
                & fc.getCapabilities()));
    }

    private void validateMmTelFeatureContainerDoesntExist(int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNull("FeatureContainer should be null", fc);
    }

    private ServiceConnection bindAndConnectService() {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        ServiceConnection connection = bindService(testFeatures);
        IImsServiceController.Stub controllerStub = mock(IImsServiceController.Stub.class);
        when(controllerStub.queryLocalInterface(any())).thenReturn(mMockServiceControllerBinder);
        connection.onServiceConnected(mTestComponentName, controllerStub);
        return connection;
    }

    private ServiceConnection bindService(
            HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures) {
        ArgumentCaptor<ServiceConnection> serviceCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        assertTrue(mTestImsServiceController.bind(testFeatures));
        verify(mMockContext).bindService(any(), serviceCaptor.capture(), anyInt());
        return serviceCaptor.getValue();
    }
}
