/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telephony.ims;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsServiceController;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.ims.internal.IImsFeatureStatusCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for ImsService
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceTest {

    private static final int TEST_SLOT_0 = 0;
    private static final int TEST_SLOT_1 = 1;
    private static final int TEST_SUB_2 = 2;
    private static final int TEST_SUB_3 = 3;

    private TestImsServiceCompat mTestImsServiceCompat;
    private TestImsService mTestImsService;
    private IImsServiceController mTestImsServiceCompatBinder;
    private IImsServiceController mTestImsServiceBinder;

    private Context mMockContext;
    private IImsFeatureStatusCallback mTestCallback;
    private IBinder mImsFeatureStatusCallbackBinder;

    @Before
    public void setUp() throws Exception {
        mMockContext = mock(Context.class);
        mTestCallback = mock(IImsFeatureStatusCallback.class);
        mImsFeatureStatusCallbackBinder = mock(IBinder.class);
        when(mTestCallback.asBinder()).thenReturn(mImsFeatureStatusCallbackBinder);
        // Create an ImsService that uses slotId and not subId to test compatibility.
        mTestImsServiceCompat = new TestImsServiceCompat(mMockContext);
        mTestImsServiceCompatBinder = (IImsServiceController) mTestImsServiceCompat.onBind(
                new Intent(ImsService.SERVICE_INTERFACE));

        mTestImsService = new TestImsService(mMockContext);
        mTestImsServiceBinder = (IImsServiceController) mTestImsService.onBind(
                new Intent(ImsService.SERVICE_INTERFACE));
    }

    @After
    public void tearDown() throws Exception {
        mMockContext = null;
        mTestCallback = null;
        mTestImsServiceCompat = null;
        mTestImsServiceCompatBinder = null;
        mTestImsService = null;
        mTestImsServiceBinder = null;
    }

    @Test
    @SmallTest
    public void testCreateMMTelFeatureCompat() throws RemoteException {
        IImsMmTelFeature f = mTestImsServiceCompatBinder.createMmTelFeature(TEST_SLOT_0,
                TEST_SUB_2);
        mTestImsServiceCompatBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        mTestImsServiceCompat.mTestMmTelFeature.sendSetFeatureState(ImsFeature.STATE_READY);

        ImsFeature featureToVerify = mTestImsServiceCompat.getImsFeature(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL);
        MmTelFeature testMMTelFeature = null;
        if (featureToVerify instanceof MmTelFeature) {
            testMMTelFeature = (MmTelFeature) featureToVerify;
        } else {
            fail();
        }
        assertTrue(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        assertEquals(mTestImsServiceCompat.mSpyMmTelFeature, testMMTelFeature);
        // Verify that upon creating a feature, we assign the callback and get the set feature state
        // when querying it.
        verify(mTestImsServiceCompat.mSpyMmTelFeature)
                .addImsFeatureStatusCallback(eq(mTestCallback));
        assertEquals(ImsFeature.STATE_READY, f.getFeatureState());
    }

    @Test
    @SmallTest
    public void testNotCreateMMTelFeatureCompat() throws RemoteException {
        IImsMmTelFeature f = mTestImsServiceCompatBinder.createMmTelFeature(TEST_SLOT_0,
                TEST_SUB_2);
        mTestImsServiceCompatBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        mTestImsServiceCompat.mTestMmTelFeature.sendSetFeatureState(ImsFeature.STATE_READY);

        ImsFeature featureToVerify = mTestImsServiceCompat.getImsFeature(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL);
        MmTelFeature testMMTelFeature = null;
        if (featureToVerify instanceof MmTelFeature) {
            testMMTelFeature = (MmTelFeature) featureToVerify;
        } else {
            fail();
        }
        assertTrue(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        assertEquals(mTestImsServiceCompat.mSpyMmTelFeature, testMMTelFeature);
        // Verify that upon creating a feature, we assign the callback and get the set feature state
        // when querying it.
        verify(mTestImsServiceCompat.mSpyMmTelFeature)
                .addImsFeatureStatusCallback(eq(mTestCallback));
        assertEquals(ImsFeature.STATE_READY, f.getFeatureState());
        // Ensures feature is not created if already have one.
        IImsMmTelFeature f2 = mTestImsServiceCompatBinder.createMmTelFeature(TEST_SLOT_0,
                TEST_SUB_3);
        mTestImsServiceCompatBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        assertEquals(mTestImsServiceCompat.createMmtelfeatureCount, 1);
        verify(mTestImsServiceCompat.mSpyMmTelFeature, times(2))
                .addImsFeatureStatusCallback(eq(mTestCallback));
    }

    @Test
    @SmallTest
    public void testRemoveMMTelFeatureCompat() throws RemoteException {
        mTestImsServiceCompatBinder.createMmTelFeature(TEST_SLOT_0, TEST_SUB_2);
        mTestImsServiceCompatBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        assertTrue(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));

        mTestImsServiceCompatBinder.removeFeatureStatusCallback(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL, mTestCallback);
        mTestImsServiceCompatBinder.removeImsFeature(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL, false);

        verify(mTestImsServiceCompat.mSpyMmTelFeature).onFeatureRemoved();
        verify(mTestImsServiceCompat.mSpyMmTelFeature).removeImsFeatureStatusCallback(
                mTestCallback);
        assertNull(mTestImsServiceCompat.getImsFeature(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL));
        assertFalse(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));
    }

    @Test
    @SmallTest
    public void testNotRemoveMMTelFeatureCompat() throws RemoteException {
        mTestImsServiceCompatBinder.createMmTelFeature(TEST_SLOT_0, TEST_SUB_2);
        mTestImsServiceCompatBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        assertTrue(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));

        mTestImsServiceCompatBinder.removeFeatureStatusCallback(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL, mTestCallback);
        mTestImsServiceCompatBinder.removeImsFeature(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL, true);
        // make sure the feature is not removed because it is created with slot ID so is not
        // affected by sub ID changing
        verify(mTestImsServiceCompat.mSpyMmTelFeature, never()).onFeatureRemoved();
        verify(mTestImsServiceCompat.mSpyMmTelFeature).removeImsFeatureStatusCallback(
                mTestCallback);
        assertNotNull(mTestImsServiceCompat.getImsFeature(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL));
        assertTrue(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));
    }

    @Test
    @SmallTest
    public void testCreateMMTelFeature() throws RemoteException {
        IImsMmTelFeature f = mTestImsServiceBinder.createMmTelFeature(TEST_SLOT_0, TEST_SUB_2);
        mTestImsServiceBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        mTestImsService.mTestMmTelFeature.sendSetFeatureState(ImsFeature.STATE_READY);

        ImsFeature featureToVerify = mTestImsService.getImsFeature(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL);
        MmTelFeature testMMTelFeature = null;
        if (featureToVerify instanceof MmTelFeature) {
            testMMTelFeature = (MmTelFeature) featureToVerify;
        } else {
            fail();
        }
        assertFalse(mTestImsServiceCompat.isImsFeatureCreatedForSlot(TEST_SLOT_0,
                ImsFeature.FEATURE_MMTEL));
        assertEquals(mTestImsService.mSpyMmTelFeature, testMMTelFeature);
        // Verify that upon creating a feature, we assign the callback and get the set feature state
        // when querying it.
        verify(mTestImsService.mSpyMmTelFeature).addImsFeatureStatusCallback(eq(mTestCallback));
        assertEquals(ImsFeature.STATE_READY, f.getFeatureState());
    }

    @Test
    @SmallTest
    public void testRemoveMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createMmTelFeature(TEST_SLOT_0, TEST_SUB_2);
        mTestImsServiceBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);

        mTestImsServiceBinder.removeFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);
        mTestImsServiceBinder.removeImsFeature(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL, true);

        verify(mTestImsService.mSpyMmTelFeature).onFeatureRemoved();
        verify(mTestImsService.mSpyMmTelFeature).removeImsFeatureStatusCallback(mTestCallback);
        assertNull(mTestImsService.getImsFeature(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL));
    }

    @Test
    @SmallTest
    public void testCallMethodOnCreatedFeature() throws RemoteException {
        IImsMmTelFeature f = mTestImsServiceBinder.createMmTelFeature(TEST_SLOT_0, TEST_SUB_2);
        mTestImsServiceBinder.addFeatureStatusCallback(TEST_SLOT_0, ImsFeature.FEATURE_MMTEL,
                mTestCallback);

        f.getUtInterface();

        assertTrue(mTestImsService.mTestMmTelFeature.isUtInterfaceCalled);
    }

    /**
     * Tests that the ImsService will return the correct ImsFeatureConfiguration when queried.
     */
    @Test
    @SmallTest
    public void testQuerySupportedImsFeatures() throws RemoteException {
        ImsFeatureConfiguration config = new ImsFeatureConfiguration.Builder()
                .addFeature(0, ImsFeature.FEATURE_MMTEL)
                .addFeature(0, ImsFeature.FEATURE_RCS)
                .build();
        mTestImsService.testFeatureConfig = config;

        ImsFeatureConfiguration result = mTestImsServiceBinder.querySupportedImsFeatures();

        assertEquals(config, result);
    }

    /**
     * Tests that ImsService capability sanitization works correctly.
     */
    @Test
    @SmallTest
    public void testCapsSanitized() throws RemoteException {
        long validCaps = ImsService.CAPABILITY_SIP_DELEGATE_CREATION;
        // emergency over MMTEL should not be set here, but rather internally in Telephony.
        long invalidCaps = 0xDEADBEEF00000000L | ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL;
        invalidCaps |= validCaps;

        mTestImsService.testCaps = validCaps;
        assertEquals(validCaps, mTestImsServiceBinder.getImsServiceCapabilities());
        mTestImsService.testCaps = invalidCaps;
        // The extra bits should have been removed, leaving only the valid remaining
        assertEquals(validCaps, mTestImsServiceBinder.getImsServiceCapabilities());
    }
}
