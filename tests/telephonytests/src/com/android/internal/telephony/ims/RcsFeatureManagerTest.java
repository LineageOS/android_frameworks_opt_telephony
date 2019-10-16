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
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsException;
import com.android.ims.RcsFeatureConnection;
import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class RcsFeatureManagerTest extends TelephonyTest {

    private int mPhoneId;
    private PersistableBundle mCarrierConfigBundle;
    private RcsFeatureManager mRcsFeatureManager;
    @Mock
    RcsFeatureConnection mRcsFeatureConnection;
    @Mock
    RcsFeatureConnection.IRcsFeatureUpdate mStatusCallback;
    @Mock
    RcsFeatureManager.SubscriptionManagerProxy mSubscriptionManagerProxy;

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
     * Test ImsFeature status can be add/remove successfully
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

    @Test
    @SmallTest
    public void testChangeEnabledCapabilities() {
        // RCS UCE supported by carrier
        setIsSupportedByCarrier(true, true);
        // change enable UCE capability
        mRcsFeatureManager.changeEnabledCapabilitiesAfterRcsFeatureCreated();
        try {
            // verify the request "changeEnabledCapabilities" will be called
            verify(mRcsFeatureConnection).changeEnabledCapabilities(anyObject(), anyObject());
        } catch (RemoteException e) {
            throw new AssertionFailedError("testChangeEnabledCapabilities: " + e);
        }
    }
}
