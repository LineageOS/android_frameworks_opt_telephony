/*
 * Copyright (C) 2019 The Android Open Source Project
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

import junit.framework.AssertionFailedError;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsException;
import com.android.ims.RcsFeatureConnection;
import com.android.ims.RcsFeatureManager;
import com.android.ims.RcsFeatureManager.RcsCapabilityCallbackManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class RcsFeatureManagerTest extends TelephonyTest {

    private int mPhoneId;
    private PersistableBundle mCarrierConfigBundle;
    private RcsFeatureManager mRcsFeatureManager;
    @Mock RcsFeatureConnection mRcsFeatureConnection;
    @Mock RcsFeatureConnection.IRcsFeatureUpdate mStatusCallback;
    @Mock RcsCapabilityCallbackManager mCapabilityCallbackManager;
    @Mock RcsFeatureManager.SubscriptionManagerProxy mSubscriptionManagerProxy;

    @Before
    public void setUp() throws Exception {
        super.setUp("RcsFeatureManagerTest");
        mPhoneId = mPhone.getPhoneId();
        mCarrierConfigBundle = mContextFixture.getCarrierConfigBundle();

        doReturn(null).when(mContext).getMainLooper();
        doReturn(true).when(mPackageManager).hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);

        when(mSubscriptionManagerProxy.getSubId(0)).thenReturn(1);
        RcsFeatureManager.setSubscriptionManager(mSubscriptionManagerProxy);

        mRcsFeatureManager = new RcsFeatureManager(mContext, mPhoneId);
        mRcsFeatureManager.mRcsFeatureConnection = mRcsFeatureConnection;
        mRcsFeatureManager.mCapabilityCallbackManager = mCapabilityCallbackManager;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test RCS UCE feature is supported by carrier
     */
    @Test
    @SmallTest
    public void testRcsUceFeatureIsSupportedByCarrier() {
        int phoneId = mPhone.getPhoneId();

        // RCS UCE is supported by carrier
        setIsSupportedByCarrier(true, true);
        assertTrue(RcsFeatureManager.isRcsUceSupportedByCarrier(mContext, phoneId));

        // RCS UCE is not supported by carrier
        setIsSupportedByCarrier(false, false);
        assertFalse(RcsFeatureManager.isRcsUceSupportedByCarrier(mContext, phoneId));
    }

    private void setIsSupportedByCarrier(boolean isOptionsSupported, boolean isPresenceSupported) {
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, isOptionsSupported);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, isPresenceSupported);
    }

    /**
     * Test ImsFeature status can be added and removed successfully
     */
    @Test
    @SmallTest
    public void testAddRemoveStatusChangedCallback() {
        // Verify the size of callbacks after add a status callback into RcsFeatureManager
        when(mRcsFeatureConnection.isBinderAlive()).thenReturn(true);
        try {
            mRcsFeatureManager.addNotifyStatusChangedCallbackIfAvailable(mStatusCallback);
        } catch (ImsException e) {
            throw new AssertionFailedError("testAddStatusChangedCallback: " + e);
        }
        int currentSize = mRcsFeatureManager.mStatusCallbacks.size();
        assertEquals(1, currentSize);

        // Verify callback size is zero after remove a status callback into RcsFeatureManager
        mRcsFeatureManager.removeNotifyStatusChangedCallback(mStatusCallback);
        currentSize = mRcsFeatureManager.mStatusCallbacks.size();
        assertEquals(0, currentSize);
    }

    /**
     * Test IImsCapabilityCallback can be registered and unregistered
     */
    @Test
    @SmallTest
    public void testImsCapabilityCallbackRegistered() throws Exception {
        // Verify ImsCapabilityCallback can be registered
        mRcsFeatureManager.registerRcsAvailabilityCallback(anyObject());
        verify(mCapabilityCallbackManager).addCallbackForSubscription(anyObject(), anyInt());

        // Verify ImsCapabilityCallback can be unregistered
        mRcsFeatureManager.unregisterRcsAvailabilityCallback(anyObject());
        verify(mCapabilityCallbackManager).removeCallbackForSubscription(anyObject(), anyInt());
    }

    @Test
    @SmallTest
    public void testIsCapableQuery() throws Exception {
        // Verify the result is true if OPTIONS is capable
        boolean result = callIsCapable(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE, true);
        assertTrue(result);

        // Verify the result is false if OPTIONS is incapable
        result = callIsCapable(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE, false);
        assertFalse(result);
    }

    private boolean callIsCapable(int capability, boolean enabled) throws Exception {
        doAnswer(invocation -> {
            IImsCapabilityCallback cb = (IImsCapabilityCallback) invocation.getArguments()[2];
            cb.onQueryCapabilityConfiguration(capability, 1, enabled);
            return null;
        }).when(mRcsFeatureConnection).queryCapabilityConfiguration(
                eq(capability), anyInt(), any());

        return mRcsFeatureManager.isCapable(capability, 1);
    }

    @Test
    @SmallTest
    public void testIsAvailableQuery() throws Exception {
        final int capabilityOptions = RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE;
        final int capabilityPresence = RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE;

        when(mRcsFeatureConnection.queryCapabilityStatus()).thenReturn(capabilityOptions);
        boolean result = mRcsFeatureManager.isAvailable(capabilityOptions);
        assertTrue(result);

        when(mRcsFeatureConnection.queryCapabilityStatus()).thenReturn(capabilityPresence);
        result = mRcsFeatureManager.isAvailable(capabilityPresence);
        assertTrue(result);

        result = mRcsFeatureManager.isAvailable(capabilityOptions);
        assertFalse(result);
    }

    @Test
    @SmallTest
    public void testChangeEnabledCapabilities() {
        // RCS UCE supported by carrier
        setIsSupportedByCarrier(true, true);
        // update UCE capability
        mRcsFeatureManager.updateCapabilities();
        try {
            // verify the request "changeEnabledCapabilities" will be called
            verify(mRcsFeatureConnection, times(2)).changeEnabledCapabilities(anyObject(),
                    anyObject());
        } catch (RemoteException e) {
            throw new AssertionFailedError("testChangeEnabledCapabilities: " + e);
        }
    }
}
