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

package android.telephony.ims;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.SparseArray;

import com.android.ims.internal.IImsFeatureStatusCallback;
import com.android.ims.internal.IImsServiceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.READ_PHONE_STATE;
import static com.android.internal.telephony.ims.ImsResolver.SERVICE_INTERFACE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ImsService
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceTest {

    private static final int TEST_SLOT_0 = 0;
    private static final int TEST_SLOT_1 = 1;

    private TestImsService mTestImsService;
    private IImsServiceController mTestImsServiceBinder;

    @Mock
    private Context mMockContext;
    @Mock
    private IImsFeatureStatusCallback mTestCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestImsService = new TestImsService(mMockContext);
        mTestImsServiceBinder = (IImsServiceController) mTestImsService.onBind(
                new Intent(SERVICE_INTERFACE));
    }

    @After
    public void tearDown() throws Exception {
        mTestImsService = null;
        mTestImsServiceBinder = null;
    }

    @Test
    @SmallTest
    public void testCreateMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);
        when(mTestImsService.mMockMMTelFeature.getFeatureState()).thenReturn(
                ImsFeature.STATE_READY);

        SparseArray<ImsFeature> features = mTestImsService.getImsFeatureMap(TEST_SLOT_0);
        assertEquals(mTestImsService.mMockMMTelFeature,
                mTestImsService.getImsFeatureFromType(features, ImsFeature.MMTEL));
        // Verify that upon creating a feature, we assign the callback and get the set feature state
        // when querying it.
        verify(mTestImsService.mMockMMTelFeature).setImsFeatureStatusCallback(eq(mTestCallback));
        assertEquals(ImsFeature.STATE_READY, mTestImsServiceBinder.getFeatureStatus(TEST_SLOT_0,
                ImsFeature.MMTEL));
    }

    @Test
    @SmallTest
    public void testRemoveMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsServiceBinder.removeImsFeature(TEST_SLOT_0, ImsFeature.MMTEL);

        verify(mTestImsService.mMockMMTelFeature).notifyFeatureRemoved(eq(0));
        verify(mTestImsService.mMockMMTelFeature).setImsFeatureStatusCallback(null);
        SparseArray<ImsFeature> features = mTestImsService.getImsFeatureMap(TEST_SLOT_0);
        assertNull(mTestImsService.getImsFeatureFromType(features, ImsFeature.MMTEL));
    }

    @Test
    @SmallTest
    public void testCallMethodOnCreatedFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsServiceBinder.isConnected(TEST_SLOT_0, ImsFeature.MMTEL, 0 /*callSessionType*/,
                0 /*callType*/);

        verify(mTestImsService.mMockMMTelFeature).isConnected(anyInt(), anyInt());
    }

    @Test
    @SmallTest
    public void testCallMethodWithNoCreatedFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsServiceBinder.isConnected(TEST_SLOT_1, ImsFeature.MMTEL, 0 /*callSessionType*/,
                0 /*callType*/);

        verify(mTestImsService.mMockMMTelFeature, never()).isConnected(anyInt(), anyInt());
    }

    @Test
    @SmallTest
    public void testCreateFeatureWithNoPermissions() throws RemoteException {
        doThrow(new SecurityException()).when(mMockContext).enforceCallingOrSelfPermission(
                eq(MODIFY_PHONE_STATE), anyString());

        try {
            mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);
            fail();
        } catch (SecurityException e) {
            // Expected
        }
    }

    @Test
    @SmallTest
    public void testMethodWithNoPermissions() throws RemoteException {
        doThrow(new SecurityException()).when(mMockContext).enforceCallingOrSelfPermission(
                eq(READ_PHONE_STATE), anyString());
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        try {
            mTestImsServiceBinder.isConnected(TEST_SLOT_1, ImsFeature.MMTEL, 0 /*callSessionType*/,
                    0 /*callType*/);
            fail();
        } catch (SecurityException e) {
            // Expected
        }

        verify(mTestImsService.mMockMMTelFeature, never()).isConnected(anyInt(), anyInt());
    }
}
