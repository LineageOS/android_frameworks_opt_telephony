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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

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
    private static final String COMPONENT_NAME_STRING =
            "com.android.dss/.TelephonyDomainSelectionService";
    private static final ComponentName DSS_COMPONENT_NAME =
            ComponentName.unflattenFromString(COMPONENT_NAME_STRING);
    private static final String CLASS_NAME = "com.android.dss.TelephonyDomainSelectionService";
    private static final String EMPTY_COMPONENT_NAME_STRING = "/" + CLASS_NAME;
    private static final ComponentName EMPTY_COMPONENT_NAME =
            ComponentName.unflattenFromString(EMPTY_COMPONENT_NAME_STRING);
    private static final String NONE_COMPONENT_NAME_STRING =
            DomainSelectionResolver.PACKAGE_NAME_NONE + "/" + CLASS_NAME;
    private static final ComponentName NONE_COMPONENT_NAME =
            ComponentName.unflattenFromString(NONE_COMPONENT_NAME_STRING);
    private static final String OVERRIDDEN_COMPONENT_NAME_STRING = "test/" + CLASS_NAME;
    private static final ComponentName OVERRIDDEN_COMPONENT_NAME =
            ComponentName.unflattenFromString(OVERRIDDEN_COMPONENT_NAME_STRING);
    // Mock classes
    private DomainSelectionController mDsController;
    private DomainSelectionConnection mDsConnection;

    private DomainSelectionResolver mDsResolver;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mDsController = Mockito.mock(DomainSelectionController.class);
        mDsConnection = Mockito.mock(DomainSelectionConnection.class);
    }

    @After
    public void tearDown() throws Exception {
        mDsResolver = null;
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

        DomainSelectionResolver.make(mContext, COMPONENT_NAME_STRING);
        DomainSelectionResolver resolver = DomainSelectionResolver.getInstance();

        assertNotNull(resolver);
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionSupportedWhenComponentNameNotConfigured() {
        setUpResolver(null, RADIO_HAL_VERSION_2_1);

        assertFalse(mDsResolver.isDomainSelectionSupported());
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionSupportedWhenHalVersionLessThan20() {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_0);

        assertFalse(mDsResolver.isDomainSelectionSupported());
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionSupported() {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);

        assertTrue(mDsResolver.isDomainSelectionSupported());
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenNotInitialized() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);

        assertThrows(IllegalStateException.class, () -> {
            mDsResolver.getDomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true);
        });
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenPhoneNull() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        verify(mDsController).bind(eq(DSS_COMPONENT_NAME));

        assertNull(mDsResolver.getDomainSelectionConnection(null, SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenImsPhoneNull() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        verify(mDsController).bind(eq(DSS_COMPONENT_NAME));

        when(mPhone.getImsPhone()).thenReturn(null);
        assertNull(mDsResolver.getDomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenImsNotAvailable() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        verify(mDsController).bind(eq(DSS_COMPONENT_NAME));

        when(mPhone.isImsAvailable()).thenReturn(false);
        when(mPhone.getImsPhone()).thenReturn(mImsPhone);
        assertNull(mDsResolver.getDomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, false));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnectionWhenImsNotAvailableForEmergencyCall()
            throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        verify(mDsController).bind(eq(DSS_COMPONENT_NAME));

        when(mPhone.isImsAvailable()).thenReturn(false);
        when(mPhone.getImsPhone()).thenReturn(mImsPhone);
        assertNotNull(mDsResolver.getDomainSelectionConnection(mPhone,
                SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testGetDomainSelectionConnection() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        verify(mDsController).bind(eq(DSS_COMPONENT_NAME));

        when(mPhone.isImsAvailable()).thenReturn(true);
        when(mPhone.getImsPhone()).thenReturn(mImsPhone);
        assertNotNull(mDsResolver.getDomainSelectionConnection(
                mPhone, SELECTOR_TYPE_CALLING, true));
    }

    @Test
    @SmallTest
    public void testSetDomainSelectionServiceOverride() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        mDsResolver.setDomainSelectionServiceOverride(OVERRIDDEN_COMPONENT_NAME);
        verify(mDsController, never()).unbind();
        verify(mDsController).bind(eq(OVERRIDDEN_COMPONENT_NAME));
    }

    @Test
    @SmallTest
    public void testSetDomainSelectionServiceOverrideWithoutInitialize() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        assertFalse(mDsResolver.setDomainSelectionServiceOverride(OVERRIDDEN_COMPONENT_NAME));
        verify(mDsController, never()).bind(eq(OVERRIDDEN_COMPONENT_NAME));
    }

    @Test
    @SmallTest
    public void testSetDomainSelectionServiceOverrideWithEmptyComponentName() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        assertTrue(mDsResolver.setDomainSelectionServiceOverride(EMPTY_COMPONENT_NAME));
        verify(mDsController).unbind();
        verify(mDsController, never()).bind(eq(EMPTY_COMPONENT_NAME));
    }

    @Test
    @SmallTest
    public void testSetDomainSelectionServiceOverrideWithNoneComponentName() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        assertTrue(mDsResolver.setDomainSelectionServiceOverride(NONE_COMPONENT_NAME));
        verify(mDsController).unbind();
        verify(mDsController, never()).bind(eq(NONE_COMPONENT_NAME));
    }

    @Test
    @SmallTest
    public void testClearDomainSelectionServiceOverride() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        mDsResolver.initialize();
        mDsResolver.clearDomainSelectionServiceOverride();
        verify(mDsController).unbind();
        verify(mDsController, times(2)).bind(eq(DSS_COMPONENT_NAME));
    }

    @Test
    @SmallTest
    public void testClearDomainSelectionServiceOverrideWithoutInitialize() throws Exception {
        setUpResolver(COMPONENT_NAME_STRING, RADIO_HAL_VERSION_2_1);
        setUpController();
        assertFalse(mDsResolver.clearDomainSelectionServiceOverride());
        verify(mDsController, never()).unbind();
    }

    private void setUpResolver(String flattenedComponentName, HalVersion halVersion) {
        mDsResolver = new DomainSelectionResolver(mContext, flattenedComponentName);
        when(mPhone.getHalVersion(eq(HAL_SERVICE_NETWORK))).thenReturn(halVersion);
    }

    private void setUpController() {
        mDsResolver.setDomainSelectionControllerFactory(
                new DomainSelectionResolver.DomainSelectionControllerFactory() {
                    @Override
                    public DomainSelectionController create(Context context) {
                        return mDsController;
                    }
                });

        when(mDsController.getDomainSelectionConnection(any(Phone.class), anyInt(), anyBoolean()))
                .thenReturn(mDsConnection);
    }
}
