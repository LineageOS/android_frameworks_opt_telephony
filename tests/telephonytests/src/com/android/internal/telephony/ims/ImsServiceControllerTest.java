/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.IImsServiceController;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.ims.ImsFeatureBinderRepository;
import com.android.ims.ImsFeatureContainer;
import com.android.ims.internal.IImsFeatureStatusCallback;
import com.android.ims.internal.IImsServiceFeatureCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashSet;

/**
 * Unit tests for ImsServiceController
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceControllerTest extends ImsTestBase {

    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;

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

    private static class TestCallback extends IImsServiceFeatureCallback.Stub {
        public ImsFeatureContainer container;

        @Override
        public void imsFeatureCreated(ImsFeatureContainer c) {
            container = c;
        }

        @Override
        public void imsFeatureRemoved(int reason) {
            container = null;
        }

        @Override
        public void imsStatusChanged(int stat) {
            container.setState(stat);
        }

        @Override
        public void updateCapabilities(long caps) {
            container.setCapabilities(caps);
        }
    }

    @Mock IImsMmTelFeature mMockMmTelFeature;
    @Mock IBinder mMockMmTelBinder;
    @Mock IImsRcsFeature mMockRcsFeature;
    @Mock IBinder mMockRcsBinder;
    @Mock IImsConfig mMockImsConfig;
    @Mock IImsRegistration mMockRcsRegistration;

    @Mock IImsServiceController mMockServiceControllerBinder;
    @Mock ImsServiceController.ImsServiceControllerCallbacks mMockCallbacks;
    @Mock Context mMockContext;
    private final ComponentName mTestComponentName = new ComponentName("TestPkg",
            "ImsServiceControllerTest");
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ImsServiceController mTestImsServiceController;
    private ImsFeatureBinderRepository mRepo;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mRepo = new ImsFeatureBinderRepository();
        mTestImsServiceController = new ImsServiceController(mMockContext, mTestComponentName,
                mMockCallbacks, mHandler, REBIND_RETRY, mRepo);
        when(mMockContext.bindService(any(), any(), anyInt())).thenReturn(true);
        when(mMockServiceControllerBinder.createMmTelFeature(anyInt()))
                .thenReturn(mMockMmTelFeature);
        when(mMockServiceControllerBinder.createRcsFeature(anyInt()))
                .thenReturn(mMockRcsFeature);
        when(mMockServiceControllerBinder.getConfig(anyInt()))
                .thenReturn(mMockImsConfig);
        when(mMockServiceControllerBinder.getRegistration(anyInt()))
                .thenReturn(mMockRcsRegistration);
        when(mMockMmTelFeature.asBinder()).thenReturn(mMockMmTelBinder);
        when(mMockRcsFeature.asBinder()).thenReturn(mMockRcsBinder);
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
     * Tests that Context.bindService is called with the correct parameters when we call bind.
     */
    @SmallTest
    @Test
    public void testBindService() {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ArgumentCaptor<Intent> intentCaptor =
                ArgumentCaptor.forClass(Intent.class);

        assertTrue(mTestImsServiceController.bind(testFeatures));

        int expectedFlags = Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
                | Context.BIND_IMPORTANT;
        verify(mMockContext).bindService(intentCaptor.capture(), any(), eq(expectedFlags));
        Intent testIntent = intentCaptor.getValue();
        assertEquals(ImsService.SERVICE_INTERFACE, testIntent.getAction());
        assertEquals(mTestComponentName, testIntent.getComponent());
    }

    /**
     * Verify that if bind is called multiple times, we only call bindService once.
     */
    @SmallTest
    @Test
    public void testBindFailureWhenBound() {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        bindAndConnectService(testFeatures);

        // already bound, should return false
        assertFalse(mTestImsServiceController.bind(testFeatures));

        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * connected.
     */
    @SmallTest
    @Test
    public void testBindServiceAndConnected() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));

        bindAndConnectService(testFeatures);

        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockServiceControllerBinder).createRcsFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        validateRcsFeatureContainerExists(SLOT_0);
    }

    /**
     * Tests ImsServiceController keeps SIP delegate creation flags if MMTEL and RCS are supported.
     */
    @SmallTest
    @Test
    public void testBindServiceSipDelegateCapability() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        when(mMockServiceControllerBinder.getImsServiceCapabilities()).thenReturn(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);

        bindAndConnectService(testFeatures);

        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockServiceControllerBinder).createRcsFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateFeatureContainerExistsWithSipDelegate(ImsFeature.FEATURE_MMTEL, SLOT_0);
        validateFeatureContainerExistsWithSipDelegate(ImsFeature.FEATURE_RCS, SLOT_0);
    }

    /**
     * Tests ImsServiceController loses SIP delegate creation flag if MMTEL and RCS are not both
     * supported.
     */
    @Ignore("Disabling for integration b/175766573")
    @SmallTest
    @Test
    public void testBindServiceSipDelegateCapabilityLost() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        when(mMockServiceControllerBinder.getImsServiceCapabilities()).thenReturn(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);

        bindAndConnectService(testFeatures);

        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        // verify CAPABILITY_SIP_DELEGATE_CREATION is not set because MMTEL and RCS are not set.
        validateFeatureContainerDoesNotHaveSipDelegate(ImsFeature.FEATURE_MMTEL, SLOT_0);
    }

    /**
     * Tests Emergency MMTEL ImsServiceController callbacks are properly called when an ImsService
     * is bound and connected.
     */
    @SmallTest
    @Test
    public void testBindEmergencyMmTel() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));

        bindAndConnectService(testFeatures);

        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0),
                eq(ImsFeature.FEATURE_EMERGENCY_MMTEL), eq(mTestImsServiceController));
        // Make sure this callback happens, which will notify the framework of emergency calling
        // availability.
        validateMmTelFeatureContainerExistsWithEmergency(SLOT_0);
    }

    /**
     * Tests to make sure that if EMERGENCY_MMTEL is specified, but not MMTEL, we do not bind to
     * MMTEL.
     */
    @SmallTest
    @Test
    public void testBindEmergencyMmTelButNotMmTel() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        // did not add FEATURE_MMTEL
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));

        bindAndConnectService(testFeatures);

        // Verify no MMTEL or EMERGENCY_MMTEL features are created
        verify(mMockServiceControllerBinder, never()).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder, never()).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks, never()).imsServiceFeatureCreated(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), eq(mTestImsServiceController));
        verify(mMockCallbacks, never()).imsServiceFeatureCreated(eq(SLOT_0),
                eq(ImsFeature.FEATURE_EMERGENCY_MMTEL), eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        // verify RCS feature is created
        verify(mMockServiceControllerBinder).createRcsFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateRcsFeatureContainerExists(SLOT_0);
    }

    /**
     * Tests that if a callback is added after the ImsServiceController is already bound, we get a
     * imsFeatureCreated callback.
     */
    @SmallTest
    @Test
    public void testCallbacksHappenWhenAddedAfterBind() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));

        bindAndConnectService(testFeatures);

        validateMmTelFeatureContainerExistsWithEmergency(SLOT_0);
        validateMmTelFeatureExistsInCallback(SLOT_0, ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL);
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * subsequently disconnected.
     */
    @SmallTest
    @Test
    public void testBindServiceAndConnectedDisconnected() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onServiceDisconnected(mTestComponentName);

        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateRcsFeatureContainerDoesntExist(SLOT_0);
    }

    /**
     * Tests that when unbind is called while the ImsService is disconnected, we still handle
     * unbinding to the service correctly.
     */
    @SmallTest
    @Test
    public void testBindServiceAndConnectedDisconnectedUnbind() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onServiceDisconnected(mTestComponentName);

        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateRcsFeatureContainerDoesntExist(SLOT_0);

        mTestImsServiceController.unbind();
        verify(mMockContext).unbindService(eq(conn));

        // Even though we unbound, this was already sent after service disconnected, so we shouldn't
        // see it again
        verify(mMockCallbacks, times(1)).imsServiceFeatureRemoved(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), eq(mTestImsServiceController));
        verify(mMockCallbacks, times(1)).imsServiceFeatureRemoved(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), eq(mTestImsServiceController));
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * subsequently unbound.
     */
    @SmallTest
    @Test
    public void testBindMoveToReady() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        TestCallback cb = new TestCallback();
        mRepo.registerForConnectionUpdates(SLOT_0, ImsFeature.FEATURE_MMTEL, cb, Runnable::run);

        bindAndConnectService(testFeatures);

        verify(mMockServiceControllerBinder).createMmTelFeature(eq(SLOT_0));
        ArgumentCaptor<IImsFeatureStatusCallback> captor =
                ArgumentCaptor.forClass(IImsFeatureStatusCallback.class);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), captor.capture());
        IImsFeatureStatusCallback mmtelStatusCb = captor.getValue();
        assertNotNull(mmtelStatusCb);
        validateMmTelFeatureContainerExists(SLOT_0);
        assertEquals(mMockMmTelBinder, cb.container.imsFeature);
        assertEquals(ImsFeature.STATE_UNAVAILABLE, cb.container.getState());

        mmtelStatusCb.notifyImsFeatureStatus(ImsFeature.STATE_READY);
        assertEquals(ImsFeature.STATE_READY, cb.container.getState());
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * subsequently unbound.
     */
    @SmallTest
    @Test
    public void testBindServiceBindUnbind() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        mTestImsServiceController.unbind();

        verify(mMockContext).unbindService(eq(conn));
        verify(mMockServiceControllerBinder).removeImsFeature(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL));
        verify(mMockServiceControllerBinder).removeFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockServiceControllerBinder).removeImsFeature(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS));
        verify(mMockServiceControllerBinder).removeFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), any());
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateRcsFeatureContainerDoesntExist(SLOT_0);
    }

    /**
     * Ensures that imsServiceFeatureRemoved is called when the binder dies in another process.
     */
    @SmallTest
    @Test
    public void testBindServiceAndBinderDied() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onBindingDied(null /*null*/);

        verify(mMockContext).unbindService(eq(conn));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateRcsFeatureContainerDoesntExist(SLOT_0);
    }

    /**
     * Ensures that imsServiceBindPermanentError is called when the binder returns null.
     */
    @SmallTest
    @Test
    public void testBindServiceAndReturnedNull() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));

        bindAndNullServiceError(testFeatures);

        verify(mMockCallbacks, never()).imsServiceFeatureCreated(anyInt(), anyInt(),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceBindPermanentError(eq(mTestComponentName));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateRcsFeatureContainerDoesntExist(SLOT_0);
    }

    /**
     * Ensures that ImsServiceController handles a null ImsFeature instance properly.
     */
    @SmallTest
    @Test
    public void testBindServiceAndImsFeatureReturnedNull() throws RemoteException {
        when(mMockServiceControllerBinder.createRcsFeature(anyInt()))
                .thenReturn(null);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));

        bindAndConnectService(testFeatures);

        verify(mMockServiceControllerBinder).createRcsFeature(SLOT_0);
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateRcsFeatureContainerDoesntExist(SLOT_0);
    }

    /**
     * Ensures ImsService and ImsResolver are notified when a feature is added.
     */
    @SmallTest
    @Test
    public void testBindServiceAndAddFeature() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        // Create a new list with an additional item
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeaturesWithAddition = new HashSet<>(
                testFeatures);
        testFeaturesWithAddition.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_1,
                ImsFeature.FEATURE_MMTEL));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithAddition);

        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_1);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_1), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        validateMmTelFeatureContainerExists(SLOT_1);
    }

    /**
     * Ensure changes in emergency calling status are tracked
     */
    @SmallTest
    @Test
    public void testBindServiceAndAddRemoveEmergency() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        TestCallback cb = new TestCallback();
        mRepo.registerForConnectionUpdates(SLOT_0, ImsFeature.FEATURE_MMTEL, cb, Runnable::run);
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        assertEquals(mMockMmTelBinder, cb.container.imsFeature);
        assertTrue((ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL
                & cb.container.getCapabilities()) == 0);

        // Add Emergency calling
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeaturesWithAddition = new HashSet<>(
                testFeatures);
        testFeaturesWithAddition.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithAddition);

        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0),
                eq(ImsFeature.FEATURE_EMERGENCY_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExistsWithEmergency(SLOT_0);
        assertEquals(mMockMmTelBinder, cb.container.imsFeature);

        assertTrue((ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL
                | cb.container.getCapabilities()) > 0);

        // Remove Emergency calling
        mTestImsServiceController.changeImsServiceFeatures(testFeatures);

        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0),
                eq(ImsFeature.FEATURE_EMERGENCY_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        assertEquals(mMockMmTelBinder, cb.container.imsFeature);
        assertTrue((ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL
                & cb.container.getCapabilities()) == 0);
    }

    /**
     * Ensures that the when EMERGENCY_MMTEL_FEATURE is defined but not MMTEL_FEATURE when the
     * features are changed, we do not bind to MMTEL.
     */
    @SmallTest
    @Test
    public void testBindServiceAndAddEmergencyButNotMmtel() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createRcsFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_RCS),
                eq(mTestImsServiceController));
        validateRcsFeatureContainerExists(SLOT_0);
        // Add FEATURE_EMERGENCY_MMTEL and ensure it doesn't cause MMTEL bind
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeaturesWithAddition = new HashSet<>(
                testFeatures);
        testFeaturesWithAddition.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_1,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithAddition);

        verify(mMockServiceControllerBinder, never()).createMmTelFeature(SLOT_1);
        verify(mMockServiceControllerBinder, never()).addFeatureStatusCallback(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks, never()).imsServiceFeatureCreated(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_1);
    }

    /**
     * Ensures ImsServiceController disregards changes to features that result in the same feature
     * set.
     */
    @SmallTest
    @Test
    public void testBindServiceCallChangeWithNoNewFeatures() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);

        // Call change with the same features and make sure it is disregarded
        mTestImsServiceController.changeImsServiceFeatures(testFeatures);

        verify(mMockServiceControllerBinder, times(1)).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder, times(1)).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockServiceControllerBinder, never()).removeImsFeature(anyInt(), anyInt());


        verify(mMockServiceControllerBinder, never()).removeFeatureStatusCallback(anyInt(),
                anyInt(), any());
        verify(mMockCallbacks, times(1)).imsServiceFeatureCreated(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), eq(mTestImsServiceController));
        verify(mMockCallbacks, never()).imsServiceFeatureRemoved(anyInt(), anyInt(), any());
        validateMmTelFeatureContainerExists(SLOT_0);
    }

    /**
     * Ensures ImsService and ImsResolver are notified when a feature is added and then removed.
     */
    @SmallTest
    @Test
    public void testBindServiceAndRemoveFeature() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_1,
                ImsFeature.FEATURE_MMTEL));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_1);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_1), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        validateMmTelFeatureContainerExists(SLOT_1);
        // Create a new list with one less item
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeaturesWithSubtraction =
                new HashSet<>(testFeatures);
        testFeaturesWithSubtraction.remove(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_1,
                ImsFeature.FEATURE_MMTEL));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithSubtraction);

        verify(mMockServiceControllerBinder).removeImsFeature(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL));
        verify(mMockServiceControllerBinder).removeFeatureStatusCallback(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_1), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        validateMmTelFeatureContainerDoesntExist(SLOT_1);
    }

    /**
     * Ensures ImsService and ImsResolver are notified when all features are removed.
     */
    @SmallTest
    @Test
    public void testBindServiceAndRemoveAllFeatures() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_1,
                ImsFeature.FEATURE_MMTEL));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_0);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockServiceControllerBinder).createMmTelFeature(SLOT_1);
        verify(mMockServiceControllerBinder).addFeatureStatusCallback(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(SLOT_1), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerExists(SLOT_0);
        validateMmTelFeatureContainerExists(SLOT_1);

        // Create a new empty list
        mTestImsServiceController.changeImsServiceFeatures(new HashSet<>());

        verify(mMockServiceControllerBinder).removeImsFeature(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL));
        verify(mMockServiceControllerBinder).removeFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_0), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        verify(mMockServiceControllerBinder).removeImsFeature(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL));
        verify(mMockServiceControllerBinder).removeFeatureStatusCallback(eq(SLOT_1),
                eq(ImsFeature.FEATURE_MMTEL), any());
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(SLOT_1), eq(ImsFeature.FEATURE_MMTEL),
                eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateMmTelFeatureContainerDoesntExist(SLOT_1);
    }

    /**
     * Verifies that nothing is notified of a feature change if the service is not bound.
     */
    @SmallTest
    @Test
    public void testBindUnbindServiceAndAddFeature() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        bindAndConnectService(testFeatures);
        mTestImsServiceController.unbind();
        // Create a new list with an additional item
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeaturesWithAddition = new HashSet<>(
                testFeatures);
        // Try to create an RCS feature
        testFeaturesWithAddition.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithAddition);

        verify(mMockServiceControllerBinder, never()).createRcsFeature(eq(SLOT_0));
        verify(mMockServiceControllerBinder, never()).removeFeatureStatusCallback(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), any());
        verify(mMockCallbacks, never()).imsServiceFeatureCreated(eq(SLOT_0),
                eq(ImsFeature.FEATURE_RCS), eq(mTestImsServiceController));
        validateMmTelFeatureContainerDoesntExist(SLOT_0);
        validateRcsFeatureContainerDoesntExist(SLOT_0);
    }

    /**
     * Verifies that the ImsServiceController automatically tries to bind again after an untimely
     * binder death.
     */
    @SmallTest
    @Test
    public void testAutoBindAfterBinderDied() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onBindingDied(null /*null*/);

        long delay = mTestImsServiceController.getRebindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        // The service should autobind after rebind event occurs
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    /**
     * Due to a bug in ServiceConnection, we will sometimes receive a null binding after the binding
     * dies. Ignore null binding in this case.
     */
    @SmallTest
    @Test
    public void testAutoBindAfterBinderDiedIgnoreNullBinding() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onBindingDied(null);
        // null binding should be ignored in this case.
        conn.onNullBinding(null);

        long delay = mTestImsServiceController.getRebindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        // The service should autobind after rebind event occurs
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that bindService has only been called once before automatic rebind occurs.
     */
    @SmallTest
    @Test
    public void testNoAutoBindBeforeTimeout() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onBindingDied(null /*null*/);

        // Be sure that there are no binds before the RETRY_TIMEOUT expires
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that calling unbind stops automatic rebind of the ImsService from occuring.
     */
    @SmallTest
    @Test
    public void testUnbindCauseAutoBindCancelAfterBinderDied() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onBindingDied(null /*null*/);
        mTestImsServiceController.unbind();

        long delay = mTestImsServiceController.getRebindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);

        // Unbind should stop the autobind from occurring.
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that calling bind causes the automatic rebinding to be cancelled or not cause another
     * call to bindService.
     */
    @SmallTest
    @Test
    public void testBindCauseAutoBindCancelAfterBinderDied() throws RemoteException {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures = new HashSet<>();
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        testFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(SLOT_0,
                ImsFeature.FEATURE_RCS));
        ServiceConnection conn = bindAndConnectService(testFeatures);
        conn.onBindingDied(null /*null*/);
        mTestImsServiceController.bind(testFeatures);

        long delay = mTestImsServiceController.getRebindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        // Should only see two binds, not three from the auto rebind that occurs.
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    private void validateMmTelFeatureContainerExists(int slotId) {

        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNotNull("MMTEL FeatureContainer should not be null", fc);
        assertEquals("ImsServiceController did not report MmTelFeature to service repo correctly",
                mMockMmTelBinder, fc.imsFeature);
        assertTrue((ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL & fc.getCapabilities()) == 0);
    }

    private void validateMmTelFeatureContainerExistsWithEmergency(int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNotNull("MMTEL FeatureContainer should not be null", fc);
        assertEquals("ImsServiceController did not report MmTelFeature to service repo correctly",
                mMockMmTelBinder, fc.imsFeature);
        assertTrue((ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL | fc.getCapabilities()) > 0);
    }

    private void validateFeatureContainerExistsWithSipDelegate(int featureType, int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, featureType).orElse(null);
        assertNotNull("FeatureContainer should not be null", fc);
        assertTrue((ImsService.CAPABILITY_SIP_DELEGATE_CREATION | fc.getCapabilities()) > 0);
    }

    private void validateFeatureContainerDoesNotHaveSipDelegate(int featureType, int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, featureType).orElse(null);
        assertNotNull("FeatureContainer should not be null", fc);
        assertEquals(0, (ImsService.CAPABILITY_SIP_DELEGATE_CREATION & fc.getCapabilities()));
    }


    private void validateMmTelFeatureExistsInCallback(int slotId, long expectedCaps) {
        TestCallback cb = new TestCallback();
        mRepo.registerForConnectionUpdates(slotId, ImsFeature.FEATURE_MMTEL, cb, Runnable::run);
        assertEquals(mMockMmTelBinder, cb.container.imsFeature);
        assertEquals(expectedCaps, cb.container.getCapabilities());
    }

    private void validateRcsFeatureContainerExists(int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_RCS).orElse(null);
        assertNotNull("RCS FeatureContainer should not be null", fc);
        assertEquals("ImsServiceController did not report RcsFeature to service repo correctly",
                mMockRcsBinder, fc.imsFeature);
    }

    private void validateMmTelFeatureContainerDoesntExist(int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNull("FeatureContainer should be null", fc);
    }

    private void validateRcsFeatureContainerDoesntExist(int slotId) {
        ImsFeatureContainer fc =
                mRepo.getIfExists(slotId, ImsFeature.FEATURE_RCS).orElse(null);
        assertNull("FeatureContainer should be null", fc);
    }

    private void bindAndNullServiceError(
            HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures) {
        ServiceConnection connection = bindService(testFeatures);
        connection.onNullBinding(mTestComponentName);
    }

    private ServiceConnection bindAndConnectService(
            HashSet<ImsFeatureConfiguration.FeatureSlotPair> testFeatures) {
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
