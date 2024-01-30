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

package com.android.internal.telephony.domainselection;

import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;
import static android.telephony.TelephonyManager.HAL_SERVICE_NETWORK;

import static com.android.internal.telephony.RIL.RADIO_HAL_VERSION_2_0;
import static com.android.internal.telephony.RIL.RADIO_HAL_VERSION_2_1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.telephony.DomainSelectionService;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.HalVersion;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * Unit tests for DomainSelectionResolver.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DomainSelectionResolverTest extends TelephonyTest {
    // Mock classes
    private DomainSelectionController mDsController;
    private DomainSelectionConnection mDsConnection;
    private DomainSelectionService mDsService;

    private DomainSelectionResolver mDsResolver;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mDsController = Mockito.mock(DomainSelectionController.class);
        mDsConnection = Mockito.mock(DomainSelectionConnection.class);
        mDsService = Mockito.mock(DomainSelectionService.class);
    }

    @After
    public void tearDown() throws Exception {
        mDsResolver = null;
        mDsService = null;
        mDsConnection = null;
        mDsController = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetInstance() throws IllegalStateException {
        DomainSelectionResolver.setDomainSelectionResolver(null);

        assertThrows(IllegalStateException.class, () -> {
            DomainSelectionResolver.getInstance();
        });

        DomainSelectionResolver.make(mContext, true);
        DomainSelectionResolver resolver = DomainSelectionResolver.getInstance();

        assertNotNull(resolver);
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionSupportedWhenDeviceConfigDisabled() {
        setUpResolver(false, RADIO_HAL_VERSION_2_1);

        assertFalse(mDsResolver.isDomainSelectionSupported());
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionSupportedWhenHalVersionLessThan20() {
        setUpResolver(true, RADIO_HAL_VERSION_2_0);

        assertFalse(mDsResolver.isDomainSelectionSupported());
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionSupported() {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);

        assertTrue(mDsResolver.isDomainSelectionSupported());
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenNotInitialized() throws Exception {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);

        assertThrows(IllegalStateException.class, () -> {
            mDsResolver.getDomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true);
        });
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenPhoneNull() throws Exception {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);
        mDsResolver.initialize(mDsService);
        assertNull(mDsResolver.getDomainSelectionConnection(null, SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenImsPhoneNull() throws Exception {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);
        mDsResolver.initialize(mDsService);
        when(mPhone.getImsPhone()).thenReturn(null);

        assertNull(mDsResolver.getDomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenImsNotAvailable() throws Exception {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);
        mDsResolver.initialize(mDsService);
        when(mPhone.isImsAvailable()).thenReturn(false);
        when(mPhone.getImsPhone()).thenReturn(mImsPhone);

        assertNull(mDsResolver.getDomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, false));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenImsNotAvailableForEmergencyCall()
            throws Exception {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize(mDsService);
        when(mPhone.isImsAvailable()).thenReturn(false);
        when(mPhone.getImsPhone()).thenReturn(mImsPhone);

        assertNotNull(mDsResolver.getDomainSelectionConnection(mPhone,
                SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnection() throws Exception {
        setUpResolver(true, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize(mDsService);
        when(mPhone.isImsAvailable()).thenReturn(true);
        when(mPhone.getImsPhone()).thenReturn(mImsPhone);

        assertNotNull(mDsResolver.getDomainSelectionConnection(
                mPhone, SELECTOR_TYPE_CALLING, true));
    }

    private void setUpResolver(boolean deviceConfigEnabled, HalVersion halVersion) {
        mDsResolver = new DomainSelectionResolver(mContext, deviceConfigEnabled);
        when(mPhone.getHalVersion(eq(HAL_SERVICE_NETWORK))).thenReturn(halVersion);
    }

    private void setUpController() {
        mDsResolver.setDomainSelectionControllerFactory(
                new DomainSelectionResolver.DomainSelectionControllerFactory() {
                    @Override
                    public DomainSelectionController create(Context context,
                            DomainSelectionService service) {
                        return mDsController;
                    }
                });

        when(mDsController.getDomainSelectionConnection(any(Phone.class), anyInt(), anyBoolean()))
                .thenReturn(mDsConnection);
    }
}
