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
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertFalse;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserManager;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.ims.ImsFeatureBinderRepository;
import com.android.internal.telephony.PhoneConfigurationManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for ImsResolver
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsResolverTest extends ImsTestBase {

    private static final ComponentName TEST_DEVICE_DEFAULT_NAME = new ComponentName("TestDevicePkg",
            "DeviceImsService");
    private static final ComponentName TEST_DEVICE2_DEFAULT_NAME = new ComponentName(
            "TestDevicePkg2", "DeviceImsService2");
    private static final ComponentName TEST_CARRIER_DEFAULT_NAME = new ComponentName(
            "TestCarrierPkg", "CarrierImsService");
    private static final ComponentName TEST_CARRIER_2_DEFAULT_NAME = new ComponentName(
            "TestCarrier2Pkg", "Carrier2ImsService");

    private static final int NUM_MAX_SLOTS = 2;
    private static final String TAG = ImsResolverTest.class.getSimpleName();

    @Mock
    Context mMockContext;
    @Mock
    PackageManager mMockPM;
    @Mock
    ImsResolver.SubscriptionManagerProxy mTestSubscriptionManagerProxy;
    @Mock
    ImsResolver.TelephonyManagerProxy mTestTelephonyManagerProxy;
    @Mock
    CarrierConfigManager mMockCarrierConfigManager;
    @Mock
    UserManager mMockUserManager;
    @Mock
    ImsResolver.ImsDynamicQueryManagerFactory mMockQueryManagerFactory;
    @Mock
    ImsServiceFeatureQueryManager mMockQueryManager;
    @Mock
    ImsFeatureBinderRepository mMockRepo;
    private ImsResolver mTestImsResolver;
    private BroadcastReceiver mTestPackageBroadcastReceiver;
    private BroadcastReceiver mTestCarrierConfigReceiver;
    private BroadcastReceiver mTestBootCompleteReceiver;
    private ImsServiceFeatureQueryManager.Listener mDynamicQueryListener;
    private PersistableBundle[] mCarrierConfigs;
    private TestableLooper mLooper;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        mTestImsResolver.destroy();
        mTestImsResolver = null;
        super.tearDown();
    }

    /**
     * Add a package to the package manager and make sure it is added to the cache of available
     * ImsServices in the ImsResolver.
     */
    @Test
    @SmallTest
    public void testAddDevicePackageToCache() {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        setupPackageQuery(TEST_DEVICE_DEFAULT_NAME, features, true);
        setupController();

        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        ImsResolver.ImsServiceInfo testCachedService =
                mTestImsResolver.getImsServiceInfoFromCache(
                        TEST_DEVICE_DEFAULT_NAME.getPackageName());
        assertNotNull(testCachedService);
        assertTrue(isImsServiceInfoEqual(TEST_DEVICE_DEFAULT_NAME, features, testCachedService));
    }

    /**
     * Add a carrier ImsService to the package manager and make sure the features declared here are
     * ignored. We should only allow dynamic query for these services.
     */
    @Test
    @SmallTest
    public void testAddCarrierPackageToCache() {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setupPackageQuery(TEST_CARRIER_DEFAULT_NAME, features, true);
        setupController();

        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        ImsResolver.ImsServiceInfo testCachedService =
                mTestImsResolver.getImsServiceInfoFromCache(
                        TEST_CARRIER_DEFAULT_NAME.getPackageName());
        assertNotNull(testCachedService);
        // none of the manifest features we added above should be reported for carrier package.
        assertTrue(testCachedService.getSupportedFeatures().isEmpty());
        // we should report that the service does not use metadata to report features.
        assertFalse(testCachedService.featureFromMetadata);
    }

    /**
     * Add a device ImsService and ensure that querying ImsResolver to see if an ImsService is
     * configured succeeds.
     */
    @Test
    @SmallTest
    public void testIsDeviceImsServiceConfigured() throws Exception {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        setupPackageQuery(TEST_DEVICE_DEFAULT_NAME, features, true);
        setupController();

        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        // device package name should be returned for both features.
        final Boolean[] isConfigured = new Boolean[1];
        // Calling this method will block us until the looper processes the command, so use
        // runWithLooper to allow the message to be processed.
        mLooper.runWithLooper(() ->
                isConfigured[0] = mTestImsResolver.isImsServiceConfiguredForFeature(0,
                        ImsFeature.FEATURE_MMTEL));
        assertTrue(isConfigured[0]);
        mLooper.runWithLooper(() ->
                isConfigured[0] = mTestImsResolver.isImsServiceConfiguredForFeature(0,
                        ImsFeature.FEATURE_RCS));
        assertTrue(isConfigured[0]);
    }

    /**
     * Add a device ImsService and ensure that querying the configured ImsService for all features
     * reports the device ImsService.
     */
    @Test
    @SmallTest
    public void testGetConfiguredImsServiceDevice() throws Exception {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        setupPackageQuery(TEST_DEVICE_DEFAULT_NAME, features, true);
        setupController();

        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        // device package name should be returned for both features.
        final String[] packageName = new String[1];
        // Calling this method will block us until the looper processes the command, so use
        // runWithLooper to allow the message to be processed.
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_MMTEL));
        assertEquals(TEST_DEVICE_DEFAULT_NAME.getPackageName(), packageName[0]);
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_RCS));
        assertEquals(TEST_DEVICE_DEFAULT_NAME.getPackageName(), packageName[0]);
    }

    /**
     * In the case that there is no device or carrier ImsService found, we return null for
     * configuration queries.
     */
    @Test
    @SmallTest
    public void testGetConfiguredImsServiceNoDeviceOrCarrier() throws Exception {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // package query returns null
        setupController();
        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        // device package name should be returned for both features.
        final String[] packageName = new String[1];
        // Calling this method will block us until the looper processes the command, so use
        // runWithLooper to allow the message to be processed.
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_MMTEL));
        assertNull(packageName[0]);
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_RCS));
        assertNull(packageName[0]);
    }

    /**
     * In the case that there is no device or carrier ImsService configured, we return null for
     * configuration queries.
     */
    @Test
    @SmallTest
    public void testGetConfiguredImsServiceNoDeviceConfig() throws Exception {
        // device configuration for MMTEL and RCS is null
        setupResolver(1 /*numSlots*/, null, null);
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // ImsService query does report a device ImsService
        setupPackageQuery(TEST_DEVICE_DEFAULT_NAME, features, true);
        setupController();
        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        // device package name should be returned for both features.
        final String[] packageName = new String[1];
        // Calling this method will block us until the looper processes the command, so use
        // runWithLooper to allow the message to be processed.
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_MMTEL));
        assertNull(packageName[0]);
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_RCS));
        assertNull(packageName[0]);
    }

    /**
     * Add a device and carrier ImsService and ensure that querying the configured ImsService for
     * all features reports the carrier ImsService and not device.
     */
    @Test
    @SmallTest
    public void testGetConfiguredImsServiceCarrier() throws Exception {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        setupController();

        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();
        // Setup the carrier features and response.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_MMTEL));
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_RCS));
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // carrier package name should be returned for both features.
        final String[] packageName = new String[1];
        // Calling this method will block us until the looper processes the command, so use
        // runWithLooper to allow the message to be processed.
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_MMTEL));
        assertEquals(TEST_CARRIER_DEFAULT_NAME.getPackageName(), packageName[0]);
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_RCS));
        assertEquals(TEST_CARRIER_DEFAULT_NAME.getPackageName(), packageName[0]);
    }

    /**
     * Add a device ImsService and ensure that querying the configured ImsService for all features
     * reports the device ImsService though there is a configuration for carrier (but no cached
     * ImsService).
     */
    @Test
    @SmallTest
    public void testGetConfiguredImsServiceCarrierDevice() throws Exception {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        HashSet<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Carrier service is configured
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        // Carrier ImsService is not found during package query.
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        setupPackageQuery(info);
        setupController();

        // Complete package manager lookup and cache.
        startBindCarrierConfigAlreadySet();

        final String[] packageName = new String[1];
        // Calling this method will block us until the looper processes the command, so use
        // runWithLooper to allow the message to be processed.
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_MMTEL));
        assertEquals(TEST_DEVICE_DEFAULT_NAME.getPackageName(), packageName[0]);
        mLooper.runWithLooper(() ->
                packageName[0] = mTestImsResolver.getConfiguredImsServicePackageName(0,
                        ImsFeature.FEATURE_RCS));
        assertEquals(TEST_DEVICE_DEFAULT_NAME.getPackageName(), packageName[0]);
    }

    /**
     * Set the carrier config override value and ensure that ImsResolver calls .bind on that
     * package name with the correct ImsFeatures.
     */
    @Test
    @SmallTest
    public void testCarrierPackageBind() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // Setup the carrier features
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> features = new HashSet<>();
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_MMTEL));
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        // Set CarrierConfig default package name and make it available as the CarrierConfig.
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setupPackageQuery(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true);
        ImsServiceController controller = setupController();

        // Start bind to carrier service
        startBindCarrierConfigAlreadySet();
        // setup features response
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, features, 1);

        verify(controller).bind(features);
        verify(controller, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Set the carrier config override value to many separate services for MMTEL and RCS and ensure
     * that ImsResolver calls .bind on those package names with the correct ImsFeatures.
     */
    @Test
    @SmallTest
    public void testDeviceCarrierPackageBindMultipleServices() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // Setup the carrier features: carrier 1 - MMTEL, slot 0; carrier 2 RCS, slot 0
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featuresMmTel = new HashSet<>();
        featuresMmTel.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_MMTEL));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featuresRcs = new HashSet<>();
        featuresRcs.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featuresAll = new HashSet<>(featuresMmTel);
        featuresAll.addAll(featuresRcs);
        // Setup the device features: MMTEL, RCS on slot 0,1
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featuresDevice = new HashSet<>();
        featuresDevice.add(new ImsFeatureConfiguration.FeatureSlotPair(1,
                ImsFeature.FEATURE_MMTEL));
        featuresDevice.add(new ImsFeatureConfiguration.FeatureSlotPair(1, ImsFeature.FEATURE_RCS));
        // Set CarrierConfig default package name and make it available as the CarrierConfig.
        setConfigCarrierStringMmTel(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setConfigCarrierStringRcs(0, TEST_CARRIER_2_DEFAULT_NAME.getPackageName());
        Set<String> deviceFeatures = new ArraySet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        List<ResolveInfo> info = new ArrayList<>();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        info.add(getResolveInfo(TEST_CARRIER_2_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController1 = mock(ImsServiceController.class);
        ImsServiceController carrierController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController1, carrierController2);

        // Start bind to carrier service
        startBindCarrierConfigAlreadySet();
        // setup features response
        // emulate mMockQueryManager returning information about pending queries.
        when(mMockQueryManager.isQueryInProgress()).thenReturn(true);
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, featuresAll, 1);
        when(mMockQueryManager.isQueryInProgress()).thenReturn(false);
        setupDynamicQueryFeatures(TEST_CARRIER_2_DEFAULT_NAME, featuresAll, 1);

        verify(deviceController).bind(featuresDevice);
        verify(deviceController, never()).unbind();
        verify(carrierController1).bind(featuresMmTel);
        verify(carrierController1, never()).unbind();
        verify(carrierController2).bind(featuresRcs);
        verify(carrierController2, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController1.getComponentName());
        assertEquals(TEST_CARRIER_2_DEFAULT_NAME, carrierController2.getComponentName());
    }

    /**
     * Set the carrier config override value to two separate services for MMTEL and RCS and ensure
     * that ImsResolver calls .bind on those package names with the correct ImsFeatures.
     */
    @Test
    @SmallTest
    public void testCarrierPackageBindOneConfigTwoSupport() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // Setup the carrier features
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featuresMmTel = new HashSet<>();
        featuresMmTel.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_MMTEL));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featuresRcs = new HashSet<>();
        featuresRcs.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        featuresRcs.add(new ImsFeatureConfiguration.FeatureSlotPair(1, ImsFeature.FEATURE_RCS));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> allFeatures = new HashSet<>(featuresMmTel);
        allFeatures.addAll(featuresRcs);
        // Set CarrierConfig default package name and make it available as the CarrierConfig.
        setConfigCarrierStringMmTel(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        // TEST_CARRIER_DEFAULT_NAME isnt configured for MMTEL on slot 1.
        featuresMmTel.remove(new ImsFeatureConfiguration.FeatureSlotPair(1,
                ImsFeature.FEATURE_MMTEL));
        setConfigCarrierStringRcs(0, TEST_CARRIER_2_DEFAULT_NAME.getPackageName());
        setConfigCarrierStringRcs(1, TEST_CARRIER_2_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        info.add(getResolveInfo(TEST_CARRIER_2_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController1 = mock(ImsServiceController.class);
        ImsServiceController carrierController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController1, carrierController2);

        // Start bind to carrier service
        startBindCarrierConfigAlreadySet();
        // setup features response
        // emulate mMockQueryManager returning information about pending queries.
        when(mMockQueryManager.isQueryInProgress()).thenReturn(true);
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, allFeatures, 1);
        when(mMockQueryManager.isQueryInProgress()).thenReturn(false);
        setupDynamicQueryFeatures(TEST_CARRIER_2_DEFAULT_NAME, allFeatures, 1);

        verify(deviceController, never()).bind(any());
        verify(deviceController, never()).unbind();
        verify(carrierController1).bind(featuresMmTel);
        verify(carrierController1, never()).unbind();
        verify(carrierController2).bind(featuresRcs);
        verify(carrierController2, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController1.getComponentName());
        assertEquals(TEST_CARRIER_2_DEFAULT_NAME, carrierController2.getComponentName());
    }

    /**
     * Creates a carrier ImsService that defines FEATURE_EMERGENCY_MMTEL and ensure that the
     * controller sets this capability.
     */
    @Test
    @SmallTest
    public void testCarrierPackageBindWithEmergencyCalling() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // Set CarrierConfig default package name and make it available to the package manager
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> features = new HashSet<>();
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_MMTEL));
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        setupPackageQuery(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true);
        ImsServiceController controller = setupController();

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, features, 1);

        verify(controller).bind(features);
        verify(controller, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Creates a carrier ImsService that defines FEATURE_EMERGENCY_MMTEL but not FEATURE_MMTEL and
     * ensure that the controller doesn't set FEATURE_EMERGENCY_MMTEL.
     */
    @Test
    @SmallTest
    public void testCarrierPackageBindWithEmergencyButNotMmtel() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // Set CarrierConfig default package name and make it available to the package manager
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> features = new HashSet<>();
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        setupPackageQuery(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true);
        ImsServiceController controller = setupController();

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, features, 1);

        // We will not bind with FEATURE_EMERGENCY_MMTEL
        features.remove(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        verify(controller).bind(features);
        verify(controller, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Creates a carrier ImsService that does not report FEATURE_EMERGENCY_MMTEL and then update the
     * ImsService to define it. Ensure that the controller sets this capability once enabled.
     */
    @Test
    @SmallTest
    public void testCarrierPackageChangeEmergencyCalling() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        // Set CarrierConfig default package name and make it available to the package manager
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> features = new HashSet<>();
        features.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_MMTEL));
        setupPackageQuery(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true);
        ImsServiceController controller = setupController();

        // Bind without emergency calling
        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, features, 1);
        verify(controller).bind(features);
        verify(controller, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, controller.getComponentName());

        packageChanged(TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> newFeatures = new HashSet<>();
        newFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_MMTEL));
        newFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_EMERGENCY_MMTEL));
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, newFeatures, 2);

        //Verify new feature is added to the carrier override.
        // add all features for slot 0
        verify(controller, atLeastOnce()).changeImsServiceFeatures(newFeatures);
    }

    /**
     * Ensure that no ImsService is bound if there is no carrier or device package explicitly set.
     */
    @Test
    @SmallTest
    public void testDontBindWhenNullCarrierPackage() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        setupPackageQuery(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true);
        ImsServiceController controller = setupController();

        // Set the CarrierConfig string to null so that ImsResolver will not bind to the available
        // Services
        setConfigCarrierStringMmTelRcs(0, null);
        startBindCarrierConfigAlreadySet();

        processAllMessages();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        verify(controller, never()).bind(any());
        verify(controller, never()).unbind();
    }

    /**
     * Test that the ImsService corresponding to the default device ImsService package name is
     * bound.
     */
    @Test
    @SmallTest
    public void testDevicePackageBind() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController controller = setupController();


        startBindNoCarrierConfig(1);
        processAllMessages();

        // There is no carrier override set, so make sure that the ImsServiceController binds
        // to all SIMs.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet = convertToHashSet(features, 0);
        verify(controller).bind(featureSet);
        verify(controller, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Test that the ImsService corresponding to the default device ImsService package name is
     * bound and when there is a configuration change from two SIMs to one, the features are
     * updated correctly.
     */
    @Test
    @SmallTest
    public void testDevicePackageBind_MsimToOneSim() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController controller = setupController();


        startBindNoCarrierConfig(1);
        processAllMessages();

        // There is no carrier override set, so make sure that the ImsServiceController binds
        // to all SIMs.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet = convertToHashSet(features, 0);
        featureSet.addAll(convertToHashSet(features, 1));
        verify(controller).bind(featureSet);
        verify(controller, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, controller.getComponentName());

        // Change number of SIMs and verify the features in the ImsServiceController are changed
        // as well
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();
        featureSet = convertToHashSet(features, 0);
        verify(controller).changeImsServiceFeatures(featureSet);
        verify(controller, never()).unbind();
    }

    /**
     * Test that the ImsService corresponding to the default device ImsService package name is
     * bound and when there is a configuration change from one to two SIMs, the features are
     * updated correctly.
     */
    @Test
    @SmallTest
    public void testDevicePackageBind_OneSimToMsim() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController controller = setupController();


        startBindNoCarrierConfig(1);
        processAllMessages();

        // There is no carrier override set, so make sure that the ImsServiceController binds
        // to all SIMs.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet = convertToHashSet(features, 0);
        verify(controller).bind(featureSet);
        verify(controller, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, controller.getComponentName());

        // Change number of SIMs and verify the features in the ImsServiceController are changed
        // as well
        PhoneConfigurationManager.notifyMultiSimConfigChange(2);
        // Carrier config changed should happen for slot 1 (independent of carrier ImsService)
        sendCarrierConfigChanged(1, 1);
        featureSet.addAll(convertToHashSet(features, 1));
        verify(controller).changeImsServiceFeatures(featureSet);
        verify(controller, never()).unbind();
    }

    /**
     * Test that the dynamic ims services are bound in the event that the user is not yet unlocked
     * but the carrier config changed event is fired.
     * @throws RemoteException
     */
    @Test
    @SmallTest
    public void testDeviceDynamicQueryBindsOnCarrierConfigChanged() throws RemoteException {
        //Set package names with no features set in metadata
        List<ResolveInfo> info = new ArrayList<>();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, new HashSet<>(), true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, new HashSet<>(), true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);

        //setupResolver
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE2_DEFAULT_NAME.getPackageName());

        //Set controllers
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);

        Map<String, ImsServiceController> controllerMap = new ArrayMap<>();
        controllerMap.put(TEST_DEVICE_DEFAULT_NAME.getPackageName(), deviceController);
        controllerMap.put(TEST_DEVICE2_DEFAULT_NAME.getPackageName(), deviceController2);
        controllerMap.put(TEST_CARRIER_DEFAULT_NAME.getPackageName(), carrierController);
        setImsServiceControllerFactory(controllerMap);

        //Set features to device ims services
        Set<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatures1 =
                convertToFeatureSlotPairs(0, ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE,
                        ImsResolver.METADATA_MMTEL_FEATURE);

        Set<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatures2 =
                convertToFeatureSlotPairs(0, ImsResolver.METADATA_RCS_FEATURE);

        startBindNoCarrierConfig(1);
        processAllMessages();
        // ensure that startQuery was called
        verify(mMockQueryManager, times(1)).startQuery(eq(TEST_DEVICE_DEFAULT_NAME),
                any(String.class));

        verify(mMockQueryManager, times(1)).startQuery(eq(TEST_DEVICE2_DEFAULT_NAME),
                any(String.class));

        mDynamicQueryListener.onComplete(TEST_DEVICE_DEFAULT_NAME, deviceFeatures1);
        mDynamicQueryListener.onComplete(TEST_DEVICE2_DEFAULT_NAME, deviceFeatures2);
        processAllMessages();

        verify(deviceController, times(2)).bind(eq(deviceFeatures1));
        verify(deviceController2, times(1)).bind(eq(deviceFeatures2));
    }

    /**
     * Test that when a device and carrier override package are set, both ImsServices are bound.
     * Verify that the carrier ImsService features are created and the device default features
     * are created for all features that are not covered by the carrier ImsService. When the device
     * configuration is changed from one SIM to MSIM, ensure that the capabilities are reflected.
     */
    @Test
    @SmallTest
    public void testDeviceAndCarrierPackageBind_OneSimToMsim() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        // set device features to MMTEL, RCS
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service only supports RCS on slot 0
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Only return info if not using the compat argument
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // Move to MSIM and verify the features in the ImsServiceController are changed as well
        PhoneConfigurationManager.notifyMultiSimConfigChange(2);
        setConfigCarrierStringMmTelRcs(1, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(1, ImsFeature.FEATURE_RCS));
        // Assume that there is a CarrierConfig change that kicks off query to carrier service.
        sendCarrierConfigChanged(1, 1);
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 2);
        verify(carrierController).changeImsServiceFeatures(carrierFeatures);
        deviceFeatureSet = convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 1));
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
        verify(deviceController, never()).unbind();
    }

    /**
     * Test that when a device and carrier override package are set, both ImsServices are bound.
     * Verify that the carrier ImsService features are created and the device default features
     * are created for all features that are not covered by the carrier ImsService. When the device
     * configuration is changed from one SIM to MSIM, ensure that the capabilities are reflected.
     */
    @Test
    @SmallTest
    public void testDeviceAndCarrierPackageBind_MsimToOneSim() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        // set device features to MMTEL, RCS
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0, slot 1 as RCS
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setConfigCarrierStringMmTelRcs(1, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(1, ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Only return info if not using the compat argument
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 1));
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // Move to single SIM and verify the features in the ImsServiceController are changed as
        // well.
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();
        carrierFeatures = new HashSet<>();
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        verify(carrierController).changeImsServiceFeatures(carrierFeatures);
        deviceFeatureSet = convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
        verify(deviceController, never()).unbind();
    }

    /**
     * Test that the ImsService corresponding to the default device ImsService package name is
     * bound to only RCS if METADATA_EMERGENCY_MMTEL_FEATURE but not METADATA_MMTEL_FEATURE.
     */
    @Test
    @SmallTest
    public void testDevicePackageInvalidMmTelBind() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController controller = setupController();


        startBindNoCarrierConfig(1);
        processAllMessages();

        // There is no carrier override set, so make sure that the ImsServiceController binds
        // to all SIMs.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet = convertToHashSet(features, 0);
        verify(controller).bind(featureSet);
        verify(controller, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Test that when a device and carrier override package are set, both ImsServices are bound.
     * Verify that the carrier ImsService features are created and the device default features
     * are created for all features that are not covered by the carrier ImsService.
     */
    @Test
    @SmallTest
    public void testDeviceAndCarrierPackageBind() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Only return info if not using the compat argument
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());
    }

    /**
     * Verify that the ImsServiceController is available for the feature specified
     * (carrier for VOICE/RCS and device for emergency).
     */
    @Test
    @SmallTest
    public void testGetDeviceCarrierFeatures() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);

        // Callback from mock ImsServiceControllers
        // All features on slot 1 should be the device default
        mTestImsResolver.imsServiceFeatureCreated(1, ImsFeature.FEATURE_MMTEL, deviceController);
        mTestImsResolver.imsServiceFeatureCreated(1, ImsFeature.FEATURE_RCS, deviceController);
        mTestImsResolver.imsServiceFeatureCreated(0, ImsFeature.FEATURE_MMTEL, deviceController);
        // The carrier override contains this feature
        mTestImsResolver.imsServiceFeatureCreated(0, ImsFeature.FEATURE_RCS, carrierController);
    }

    /**
     * Bind to device ImsService and change the feature set. Verify that changeImsServiceFeature
     * is called with the new feature set.
     */
    @Test
    @SmallTest
    public void testAddDeviceFeatureNoCarrier() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // Doesn't include RCS feature by default
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        setupPackageQuery(info);
        ImsServiceController controller = setupController();
        // Bind using default features
        startBindNoCarrierConfig(2);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet =
                convertToHashSet(features, 0);
        featureSet.addAll(convertToHashSet(features, 1));
        verify(controller).bind(featureSet);

        // add RCS to features list
        Set<String> newFeatures = new HashSet<>(features);
        newFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, newFeatures, true));

        packageChanged(TEST_DEVICE_DEFAULT_NAME.getPackageName());

        //Verify new feature is added to the device default.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> newFeatureSet =
                convertToHashSet(newFeatures, 0);
        newFeatureSet.addAll(convertToHashSet(newFeatures, 1));
        verify(controller).changeImsServiceFeatures(newFeatureSet);
    }

    /**
     * Bind to device ImsServices and change the feature set to include one that is not configured.
     * Ensure it is not added.
     */
    @Test
    @SmallTest
    public void testMultipleDeviceAddFeatureNoCarrier() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE2_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> featuresController1 = new HashSet<>();
        featuresController1.add(ImsResolver.METADATA_MMTEL_FEATURE);
        Set<String> featuresController2 = new HashSet<>();
        featuresController2.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, featuresController1, true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, featuresController2, true));
        setupPackageQuery(info);
        ImsServiceController deviceController1 = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController1, deviceController2, null, null);
        // Bind using default features
        startBindNoCarrierConfig(2);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet1 =
                convertToHashSet(featuresController1, 0);
        featureSet1.addAll(convertToHashSet(featuresController1, 1));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet2 =
                convertToHashSet(featuresController2, 0);
        featureSet2.addAll(convertToHashSet(featuresController2, 1));
        verify(deviceController1).bind(featureSet1);
        verify(deviceController2).bind(featureSet2);

        // add RCS to features list for device 1
        Set<String> newFeatures1 = new HashSet<>(featuresController1);
        newFeatures1.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, newFeatures1, true));

        packageChanged(TEST_DEVICE_DEFAULT_NAME.getPackageName());

        // verify the devices have not changed features (because their configurations are still
        // the same)
        verify(deviceController1, times(2)).changeImsServiceFeatures(featureSet1);
        verify(deviceController2, times(2)).changeImsServiceFeatures(featureSet2);
    }

    /**
     * Bind to device ImsService and change the feature set while not supporting that configuration.
     * Verify that changeImsServiceFeature is called with the original feature set.
     */
    @Test
    @SmallTest
    public void testAddDeviceFeatureNoCarrierRcsNotSupported() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(), "");
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // Doesn't include RCS feature by default
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        setupPackageQuery(info);
        ImsServiceController controller = setupController();
        // Bind using default features
        startBindNoCarrierConfig(2);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet =
                convertToHashSet(features, 0);
        featureSet.addAll(convertToHashSet(features, 1));
        verify(controller).bind(featureSet);

        // add RCS to features list
        Set<String> newFeatures = new HashSet<>(features);
        newFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, newFeatures, true));

        packageChanged(TEST_DEVICE_DEFAULT_NAME.getPackageName());

        // Verify new feature is not added to the device default, since it is not configured.
        // This happens twice because two CarrierConfigChanged events occur, causing a
        // changeImsServiceFeatures after bind() and then another after packageChanged.
        verify(controller, times(2)).changeImsServiceFeatures(featureSet);
    }

    /**
     * Bind to device ImsService and change the feature set. Verify that changeImsServiceFeature
     * is called with the new feature set on the sub that doesn't include the carrier override.
     */
    @Test
    @SmallTest
    public void testAddDeviceFeatureWithCarrier() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_MMTEL));
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // add RCS to features list
        Set<String> newDeviceFeatures = new HashSet<>();
        newDeviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        newDeviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, newDeviceFeatures, true));

        // Tell the package manager that a new device feature is installed
        packageChanged(TEST_DEVICE_DEFAULT_NAME.getPackageName());

        //Verify new feature is added to the device default.
        // add all features for slot 1
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> newDeviceFeatureSet =
                convertToHashSet(newDeviceFeatures, 1);
        newDeviceFeatureSet.addAll(convertToHashSet(newDeviceFeatures, 0));
        // remove carrier overrides for slot 0
        newDeviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).changeImsServiceFeatures(newDeviceFeatureSet);
        // features should be the same as before, ImsServiceController will disregard change if it
        // is the same feature set anyway.
        verify(carrierController).changeImsServiceFeatures(carrierFeatures);
    }

    /**
     * Bind to device ImsServices and change the feature set of the carrier overridden ImsService.
     * Verify that the device and carrier ImsServices are changed.
     */
    @Test
    @SmallTest
    public void testAddCarrierFeature() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE2_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures1 = new HashSet<>();
        deviceFeatures1.add(ImsResolver.METADATA_MMTEL_FEATURE);
        Set<String> deviceFeatures2 = new HashSet<>();
        deviceFeatures2.add(ImsResolver.METADATA_RCS_FEATURE);
        Set<String> allDeviceFeatures = new HashSet<>(deviceFeatures1);
        allDeviceFeatures.addAll(deviceFeatures2);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_MMTEL));
        // Use device default packages, which will load the ImsServices that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, allDeviceFeatures, true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, allDeviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController1 = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController1, deviceController2, carrierController,
                null);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);
        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controllers.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet1 =
                convertToHashSet(deviceFeatures1, 1);
        deviceFeatureSet1.removeAll(carrierFeatures);
        verify(deviceController1).bind(deviceFeatureSet1);
        verify(deviceController1, never()).unbind();
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet2 =
                convertToHashSet(deviceFeatures2, 0);
        deviceFeatureSet2.addAll(convertToHashSet(deviceFeatures2, 1));
        deviceFeatureSet2.removeAll(carrierFeatures);
        verify(deviceController2).bind(deviceFeatureSet2);
        verify(deviceController2, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController1.getComponentName());
        assertEquals(TEST_DEVICE2_DEFAULT_NAME, deviceController2.getComponentName());

        // add RCS to carrier features list
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));

        // A new carrier feature is installed
        packageChanged(TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 2);

        //Verify new feature is added to the carrier override.
        verify(carrierController).changeImsServiceFeatures(carrierFeatures);
        deviceFeatureSet1.removeAll(carrierFeatures);
        verify(deviceController1, times(2)).changeImsServiceFeatures(deviceFeatureSet1);
        deviceFeatureSet2.removeAll(carrierFeatures);
        verify(deviceController2).changeImsServiceFeatures(deviceFeatureSet2);
    }

    /**
     * Bind to device ImsService and change the feature set of the carrier overridden ImsService by
     * removing a feature.
     * Verify that the device and carrier ImsServices are changed.
     */
    @Test
    @SmallTest
    public void testRemoveCarrierFeature() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);
        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // change supported feature to MMTEL only
        carrierFeatures.clear();
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_MMTEL));
        //  new carrier feature has been removed
        packageChanged(TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 2);

        //Verify new feature is added to the carrier override.
        verify(carrierController).changeImsServiceFeatures(carrierFeatures);
        Set<String> newDeviceFeatures = new HashSet<>();
        newDeviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        newDeviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> newDeviceFeatureSet =
                convertToHashSet(newDeviceFeatures, 1);
        newDeviceFeatureSet.addAll(convertToHashSet(newDeviceFeatures, 0));
        newDeviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).changeImsServiceFeatures(newDeviceFeatureSet);
    }

    /**
     * Inform the ImsResolver that a Carrier ImsService has been installed and must be bound.
     */
    @Test
    @SmallTest
    public void testInstallCarrierImsService() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();

        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));

        // Tell the package manager that a new carrier app is installed
        packageChanged(TEST_CARRIER_DEFAULT_NAME.getPackageName());
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        // device features change
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform the ImsResolver that a carrier ImsService has been uninstalled and the device default
     * must now use those features.
     */
    @Test
    @SmallTest
    public void testUninstallCarrierImsService() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Tell the package manager that carrier app is uninstalled
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        packageRemoved(TEST_CARRIER_DEFAULT_NAME.getPackageName());

        // Verify that the carrier controller is unbound
        verify(carrierController).unbind();
        assertNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // device features change to include all supported functionality
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform ImsResolver that the carrier config has changed to none, requiring the device
     * ImsService to be bound/set up and the previous carrier ImsService to be unbound.
     */
    @Test
    @SmallTest
    public void testCarrierConfigChangedToNone() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        setConfigCarrierStringMmTelRcs(0, null);
        sendCarrierConfigChanged(0, 0);

        // Verify that the carrier controller is unbound
        verify(carrierController).unbind();
        assertNotNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // device features change
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform ImsResolver that the carrier config has changed to another, requiring the new carrier
     * ImsService to be bound/set up and the previous carrier ImsService to be unbound.
     */
    @Test
    @SmallTest
    public void testCarrierConfigChangedToAnotherService() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures1 = new HashSet<>();
        // Carrier service 1
        carrierFeatures1.add(new ImsFeatureConfiguration.FeatureSlotPair(0,
                ImsFeature.FEATURE_MMTEL));
        carrierFeatures1.add(
                new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures2 = new HashSet<>();
        // Carrier service 2 doesn't support the voice feature.
        carrierFeatures2.add(
                new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        info.add(getResolveInfo(TEST_CARRIER_2_DEFAULT_NAME, new HashSet<>(), true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController1 = mock(ImsServiceController.class);
        ImsServiceController carrierController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController1, carrierController2);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures1, 1);

        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_2_DEFAULT_NAME.getPackageName());
        sendCarrierConfigChanged(0, 0);
        setupDynamicQueryFeatures(TEST_CARRIER_2_DEFAULT_NAME, carrierFeatures2, 1);

        // Verify that carrier 1 is unbound
        verify(carrierController1).unbind();
        assertNotNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // Verify that carrier 2 is bound
        verify(carrierController2).bind(carrierFeatures2);
        assertNotNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_2_DEFAULT_NAME.getPackageName()));
        // device features change to accommodate for the features carrier 2 lacks
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        deviceFeatureSet.removeAll(carrierFeatures2);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform the ImsResolver that BOOT_COMPLETE has happened. A non-FBE enabled ImsService is now
     * available to be bound.
     */
    @Test
    @SmallTest
    public void testBootCompleteNonFbeEnabledCarrierImsService() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        // Bind with device ImsService
        startBindCarrierConfigAlreadySet();

        // Boot complete happens and the Carrier ImsService is now available.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        // Boot complete has happened and the carrier ImsService is now available.
        mTestBootCompleteReceiver.onReceive(null, new Intent(Intent.ACTION_BOOT_COMPLETED));
        processAllMessages();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that all features that have been defined for the carrier override are bound
        verify(carrierController).bind(carrierFeatures);
        // device features change
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * If a misbehaving ImsService returns null for the Binder connection when we perform a dynamic
     * feature query, verify we never perform a full bind for any features.
     */
    @Test
    @SmallTest
    public void testPermanentBindFailureDuringFeatureQuery() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        // dynamic query results in a failure.
        setupDynamicQueryFeaturesFailure(TEST_CARRIER_DEFAULT_NAME, 1);

        // Verify that a bind never occurs for the carrier controller.
        verify(carrierController, never()).bind(any());
        verify(carrierController, never()).unbind();
        // Verify that all features are used to bind to the device ImsService since the carrier
        // ImsService failed to bind properly.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 0);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());
    }

    /**
     * If a misbehaving ImsService returns null for the Binder connection when we perform bind,
     * verify the service is disconnected.
     */
    @Test
    @SmallTest
    public void testPermanentBindFailureDuringBind() throws RemoteException {
        setupResolver(1 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierStringMmTelRcs(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the voice feature.
        carrierFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(0, ImsFeature.FEATURE_RCS));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        startBindCarrierConfigAlreadySet();
        setupDynamicQueryFeatures(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, 1);

        // Verify that a bind never occurs for the carrier controller.
        verify(carrierController).bind(carrierFeatures);
        verify(carrierController, never()).unbind();
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> deviceFeatureSet =
                convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.removeAll(carrierFeatures);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        mTestImsResolver.imsServiceBindPermanentError(TEST_CARRIER_DEFAULT_NAME);
        processAllMessages();
        verify(carrierController).unbind();
        // Verify that the device ImsService features are changed to include the ones previously
        // taken by the carrier app.
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> originalDeviceFeatureSet =
                convertToHashSet(deviceFeatures, 0);
        verify(deviceController).changeImsServiceFeatures(originalDeviceFeatureSet);
    }

    /**
     * Bind to device ImsService only, which is configured to be the MMTEL ImsService. Ensure it
     * does not also try to bind to RCS.
     */
    @Test
    @SmallTest
    public void testDifferentDevicePackagesMmTelOnly() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(), "");
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController1 = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController1, deviceController2, null, null);

        startBindNoCarrierConfig(1);
        processAllMessages();

        Set<String> featureResult = new HashSet<>();
        featureResult.add(ImsResolver.METADATA_MMTEL_FEATURE);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureResultSet =
                convertToHashSet(featureResult, 0);
        featureResultSet.addAll(convertToHashSet(featureResult, 1));
        verify(deviceController1).bind(featureResultSet);
        verify(deviceController1, never()).unbind();
        verify(deviceController2, never()).bind(any());
        verify(deviceController2, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController1.getComponentName());
    }

    /**
     * Bind to device ImsService only, which is configured to be the RCS ImsService. Ensure it
     * does not also try to bind to MMTEL.
     */
    @Test
    @SmallTest
    public void testDifferentDevicePackagesRcsOnly() throws RemoteException {
        setupResolver(2 /*numSlots*/, "", TEST_DEVICE_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController1 = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController1, deviceController2, null, null);

        startBindNoCarrierConfig(1);
        processAllMessages();

        Set<String> featureResult = new HashSet<>();
        featureResult.add(ImsResolver.METADATA_RCS_FEATURE);
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureResultSet =
                convertToHashSet(featureResult, 0);
        featureResultSet.addAll(convertToHashSet(featureResult, 1));
        verify(deviceController1).bind(featureResultSet);
        verify(deviceController1, never()).unbind();
        verify(deviceController2, never()).bind(any());
        verify(deviceController2, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController1.getComponentName());
    }

    /**
     * Bind to multiple ImsServices, one for MMTEL and one for RCS. Ensure neither of them bind to
     * both.
     */
    @Test
    @SmallTest
    public void testDifferentDevicePackagesMmTelRcs() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE2_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features1 = new HashSet<>();
        features1.add(ImsResolver.METADATA_MMTEL_FEATURE);
        Set<String> features2 = new HashSet<>();
        features2.add(ImsResolver.METADATA_RCS_FEATURE);
        Set<String> allFeatures = new HashSet<>(features1);
        allFeatures.addAll(features2);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, allFeatures, true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, allFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController1 = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController1, deviceController2, null, null);

        startBindNoCarrierConfig(1);
        processAllMessages();

        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet1 =
                convertToHashSet(features1, 0);
        featureSet1.addAll(convertToHashSet(features1, 1));
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> featureSet2 =
                convertToHashSet(features2, 0);
        featureSet2.addAll(convertToHashSet(features2, 1));
        verify(deviceController1).bind(featureSet1);
        verify(deviceController1, never()).unbind();
        verify(deviceController2).bind(featureSet2);
        verify(deviceController2, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController1.getComponentName());
        assertEquals(TEST_DEVICE2_DEFAULT_NAME, deviceController2.getComponentName());
    }

    /**
     * Set the device configuration to opposite of the supported features in the metadata and ensure
     * there is no bind.
     */
    @Test
    @SmallTest
    public void testDifferentDevicePackagesNoSupported() throws RemoteException {
        setupResolver(2 /*numSlots*/, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                TEST_DEVICE2_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features1 = new HashSet<>();
        features1.add(ImsResolver.METADATA_RCS_FEATURE);
        Set<String> features2 = new HashSet<>();
        features2.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // The configuration is opposite of the device supported features
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features1, true));
        info.add(getResolveInfo(TEST_DEVICE2_DEFAULT_NAME, features2, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, new HashSet<>(), true));
        setupPackageQuery(info);
        ImsServiceController deviceController1 = mock(ImsServiceController.class);
        ImsServiceController deviceController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController1, deviceController2, null, null);

        startBindNoCarrierConfig(1);
        processAllMessages();

        verify(deviceController1, never()).bind(any());
        verify(deviceController1, never()).unbind();
        verify(deviceController2, never()).bind(any());
        verify(deviceController2, never()).unbind();
        verify(mMockQueryManager, never()).startQuery(any(), any());
    }

    private void setupResolver(int numSlots, String deviceMmTelPkgName,
            String deviceRcsPkgName) {
        // all tests call setupResolver before running
        when(mMockContext.getPackageManager()).thenReturn(mMockPM);
        when(mMockContext.createContextAsUser(any(), eq(0))).thenReturn(mMockContext);
        when(mMockContext.getSystemService(eq(Context.CARRIER_CONFIG_SERVICE))).thenReturn(
                mMockCarrierConfigManager);
        when(mMockContext.getSystemService(eq(Context.USER_SERVICE))).thenReturn(mMockUserManager);

        //If this is not false, then HANDLER_BOOT_COMPLETE is fired now but the controller factories
        //used in the test methods aren't created in time.
        when(mMockUserManager.isUserUnlocked()).thenReturn(false);

        // Support configs for MSIM always in case we are testing dynamic sim slot config changes.
        mCarrierConfigs = new PersistableBundle[NUM_MAX_SLOTS];
        for (int i = 0; i < NUM_MAX_SLOTS; i++) {
            mCarrierConfigs[i] = new PersistableBundle();
            when(mMockCarrierConfigManager.getConfigForSubId(eq(i))).thenReturn(
                    mCarrierConfigs[i]);
            when(mTestSubscriptionManagerProxy.getSlotIndex(eq(i))).thenReturn(i);
            when(mTestSubscriptionManagerProxy.getSubId(eq(i))).thenReturn(i);
            when(mTestTelephonyManagerProxy.getSimState(any(Context.class), eq(i))).thenReturn(
                    TelephonyManager.SIM_STATE_READY);
        }

        mTestImsResolver = new ImsResolver(mMockContext, deviceMmTelPkgName, deviceRcsPkgName,
                numSlots, mMockRepo);
        try {
            mLooper = new TestableLooper(mTestImsResolver.getHandler().getLooper());
            monitorTestableLooper(mLooper);
        } catch (Exception e) {
            fail("Unable to create looper from handler.");
        }

        mTestImsResolver.setSubscriptionManagerProxy(mTestSubscriptionManagerProxy);
        mTestImsResolver.setTelephonyManagerProxy(mTestTelephonyManagerProxy);
        when(mMockQueryManagerFactory.create(any(Context.class),
                any(ImsServiceFeatureQueryManager.Listener.class))).thenReturn(mMockQueryManager);
        mTestImsResolver.setImsDynamicQueryManagerFactory(mMockQueryManagerFactory);
        processAllMessages();
    }

    private void setupPackageQuery(List<ResolveInfo> infos) {
        // Only return info if not using the compat argument
        when(mMockPM.queryIntentServicesAsUser(
                argThat(argument -> ImsService.SERVICE_INTERFACE.equals(argument.getAction())),
                anyInt(), any())).thenReturn(infos);
    }

    private void setupPackageQuery(ComponentName name, Set<String> features,
            boolean isPermissionGranted) {
        List<ResolveInfo> info = new ArrayList<>();
        info.add(getResolveInfo(name, features, isPermissionGranted));
        // Only return info if not using the compat argument
        when(mMockPM.queryIntentServicesAsUser(
                argThat(argument -> ImsService.SERVICE_INTERFACE.equals(argument.getAction())),
                anyInt(), any())).thenReturn(info);
    }

    private ImsServiceController setupController() {
        ImsServiceController controller = mock(ImsServiceController.class);
        mTestImsResolver.setImsServiceControllerFactory(
                new ImsResolver.ImsServiceControllerFactory() {
                    @Override
                    public String getServiceInterface() {
                        return ImsService.SERVICE_INTERFACE;
                    }

                    @Override
                    public ImsServiceController create(Context context, ComponentName componentName,
                            ImsServiceController.ImsServiceControllerCallbacks callbacks,
                            ImsFeatureBinderRepository r) {
                        when(controller.getComponentName()).thenReturn(componentName);
                        return controller;
                    }
                });
        return controller;
    }

    /**
     * In this case, there is a CarrierConfig already set for the sub/slot combo when initializing.
     * This automatically kicks off the binding internally.
     */
    private void startBindCarrierConfigAlreadySet() {
        mTestImsResolver.initialize();
        ArgumentCaptor<BroadcastReceiver> receiversCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext, times(3)).registerReceiver(receiversCaptor.capture(), any());
        mTestPackageBroadcastReceiver = receiversCaptor.getAllValues().get(0);
        mTestCarrierConfigReceiver = receiversCaptor.getAllValues().get(1);
        mTestBootCompleteReceiver = receiversCaptor.getAllValues().get(2);
        ArgumentCaptor<ImsServiceFeatureQueryManager.Listener> queryManagerCaptor =
                ArgumentCaptor.forClass(ImsServiceFeatureQueryManager.Listener.class);
        verify(mMockQueryManagerFactory).create(any(Context.class), queryManagerCaptor.capture());
        mDynamicQueryListener = queryManagerCaptor.getValue();
        when(mMockQueryManager.startQuery(any(ComponentName.class), any(String.class)))
                .thenReturn(true);
        processAllMessages();
    }

    /**
     * In this case, there is no carrier config override, send CarrierConfig loaded intent to all
     * slots, indicating that the SIMs are loaded and to bind the device default.
     */
    private void startBindNoCarrierConfig(int numSlots) {
        mTestImsResolver.initialize();
        ArgumentCaptor<BroadcastReceiver> receiversCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext, times(3)).registerReceiver(receiversCaptor.capture(), any());
        mTestPackageBroadcastReceiver = receiversCaptor.getAllValues().get(0);
        mTestCarrierConfigReceiver = receiversCaptor.getAllValues().get(1);
        mTestBootCompleteReceiver = receiversCaptor.getAllValues().get(2);
        ArgumentCaptor<ImsServiceFeatureQueryManager.Listener> queryManagerCaptor =
                ArgumentCaptor.forClass(ImsServiceFeatureQueryManager.Listener.class);
        verify(mMockQueryManagerFactory).create(any(Context.class), queryManagerCaptor.capture());
        mDynamicQueryListener = queryManagerCaptor.getValue();
        processAllMessages();
        // For ease of testing, slotId = subId
        for (int i = 0; i < numSlots; i++) {
            sendCarrierConfigChanged(i, i);
        }
    }

    private void setupDynamicQueryFeatures(ComponentName name,
            HashSet<ImsFeatureConfiguration.FeatureSlotPair> features, int times) {
        processAllMessages();
        // ensure that startQuery was called
        verify(mMockQueryManager, times(times)).startQuery(eq(name), any(String.class));
        mDynamicQueryListener.onComplete(name, features);
        processAllMessages();
    }

    private void setupDynamicQueryFeaturesFailure(ComponentName name, int times) {
        processAllMessages();
        // ensure that startQuery was called
        verify(mMockQueryManager, times(times)).startQuery(eq(name), any(String.class));
        mDynamicQueryListener.onPermanentError(name);
        processAllMessages();
    }

    public void packageChanged(String packageName) {
        // Tell the package manager that a new device feature is installed
        Intent addPackageIntent = new Intent();
        addPackageIntent.setAction(Intent.ACTION_PACKAGE_CHANGED);
        addPackageIntent.setData(new Uri.Builder().scheme("package").opaquePart(packageName)
                .build());
        mTestPackageBroadcastReceiver.onReceive(null, addPackageIntent);
        processAllMessages();
    }

    public void packageRemoved(String packageName) {
        Intent removePackageIntent = new Intent();
        removePackageIntent.setAction(Intent.ACTION_PACKAGE_REMOVED);
        removePackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_CARRIER_DEFAULT_NAME.getPackageName()).build());
        mTestPackageBroadcastReceiver.onReceive(null, removePackageIntent);
        processAllMessages();
    }

    private void setImsServiceControllerFactory(Map<String, ImsServiceController> controllerMap) {
        mTestImsResolver.setImsServiceControllerFactory(
                new ImsResolver.ImsServiceControllerFactory() {
                    @Override
                    public String getServiceInterface() {
                        return ImsService.SERVICE_INTERFACE;
                    }

                    @Override
                    public ImsServiceController create(Context context, ComponentName componentName,
                            ImsServiceController.ImsServiceControllerCallbacks callbacks,
                            ImsFeatureBinderRepository r) {
                        return controllerMap.get(componentName.getPackageName());
                    }
                });
    }

    private void setImsServiceControllerFactory(ImsServiceController deviceController,
            ImsServiceController carrierController) {
        mTestImsResolver.setImsServiceControllerFactory(
                new ImsResolver.ImsServiceControllerFactory() {
                    @Override
                    public String getServiceInterface() {
                        return ImsService.SERVICE_INTERFACE;
                    }

                    @Override
                    public ImsServiceController create(Context context, ComponentName componentName,
                            ImsServiceController.ImsServiceControllerCallbacks callbacks,
                            ImsFeatureBinderRepository r) {
                        if (TEST_DEVICE_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(deviceController.getComponentName()).thenReturn(componentName);
                            return deviceController;
                        } else if (TEST_CARRIER_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(carrierController.getComponentName()).thenReturn(componentName);
                            return carrierController;
                        }
                        return null;
                    }
                });
    }

    private void setImsServiceControllerFactory(ImsServiceController deviceController,
            ImsServiceController carrierController1, ImsServiceController carrierController2) {
        mTestImsResolver.setImsServiceControllerFactory(
                new ImsResolver.ImsServiceControllerFactory() {
                    @Override
                    public String getServiceInterface() {
                        return ImsService.SERVICE_INTERFACE;
                    }

                    @Override
                    public ImsServiceController create(Context context, ComponentName componentName,
                            ImsServiceController.ImsServiceControllerCallbacks callbacks,
                            ImsFeatureBinderRepository r) {
                        if (TEST_DEVICE_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(deviceController.getComponentName()).thenReturn(componentName);
                            return deviceController;
                        } else if (TEST_CARRIER_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(carrierController1.getComponentName()).thenReturn(componentName);
                            return carrierController1;
                        } else if (TEST_CARRIER_2_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(carrierController2.getComponentName()).thenReturn(componentName);
                            return carrierController2;
                        }
                        return null;
                    }
                });
    }

    private void setImsServiceControllerFactory(ImsServiceController deviceController1,
            ImsServiceController deviceController2, ImsServiceController carrierController1,
            ImsServiceController carrierController2) {
        mTestImsResolver.setImsServiceControllerFactory(
                new ImsResolver.ImsServiceControllerFactory() {
                    @Override
                    public String getServiceInterface() {
                        return ImsService.SERVICE_INTERFACE;
                    }

                    @Override
                    public ImsServiceController create(Context context, ComponentName componentName,
                            ImsServiceController.ImsServiceControllerCallbacks callbacks,
                            ImsFeatureBinderRepository r) {
                        if (TEST_DEVICE_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(deviceController1.getComponentName()).thenReturn(componentName);
                            return deviceController1;
                        } else if (TEST_DEVICE2_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(deviceController2.getComponentName()).thenReturn(componentName);
                            return deviceController2;
                        } else if (TEST_CARRIER_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(carrierController1.getComponentName()).thenReturn(componentName);
                            return carrierController1;
                        } else if (TEST_CARRIER_2_DEFAULT_NAME.getPackageName().equals(
                                componentName.getPackageName())) {
                            when(carrierController2.getComponentName()).thenReturn(componentName);
                            return carrierController2;
                        }
                        return null;
                    }
                });
    }


    private void sendCarrierConfigChanged(int subId, int slotId) {
        Intent carrierConfigIntent = new Intent();
        carrierConfigIntent.putExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        carrierConfigIntent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, slotId);
        mTestCarrierConfigReceiver.onReceive(null, carrierConfigIntent);
        processAllMessages();
    }

    private void setConfigCarrierStringMmTelRcs(int subId, String packageName) {
        mCarrierConfigs[subId].putString(
                CarrierConfigManager.KEY_CONFIG_IMS_PACKAGE_OVERRIDE_STRING, packageName);
    }

    private void setConfigCarrierStringMmTel(int subId, String packageName) {
        mCarrierConfigs[subId].putString(
                CarrierConfigManager.KEY_CONFIG_IMS_MMTEL_PACKAGE_OVERRIDE_STRING, packageName);
    }

    private void setConfigCarrierStringRcs(int subId, String packageName) {
        mCarrierConfigs[subId].putString(
                CarrierConfigManager.KEY_CONFIG_IMS_RCS_PACKAGE_OVERRIDE_STRING, packageName);
    }

    private HashSet<ImsFeatureConfiguration.FeatureSlotPair> convertToHashSet(
            Set<String> features, int slotId) {
        return features.stream()
                // We do not count this as a valid feature set member.
                .filter(f -> !ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE.equals(f))
                .map(f -> new ImsFeatureConfiguration.FeatureSlotPair(slotId,
                        metadataStringToFeature(f)))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private HashSet<ImsFeatureConfiguration.FeatureSlotPair> convertToFeatureSlotPairs(
            int slotId, String... features) {
        return convertToHashSet(new ArraySet<>(features), slotId);
    }

    private int metadataStringToFeature(String f) {
        switch (f) {
            case ImsResolver.METADATA_MMTEL_FEATURE:
                return ImsFeature.FEATURE_MMTEL;
            case ImsResolver.METADATA_RCS_FEATURE:
                return ImsFeature.FEATURE_RCS;
        }
        return -1;
    }

    // Make sure the metadata provided in the service definition creates the associated features in
    // the ImsServiceInfo. Note: this only tests for one slot.
    private boolean isImsServiceInfoEqual(ComponentName name, Set<String> features,
            ImsResolver.ImsServiceInfo sInfo) {
        if (!Objects.equals(sInfo.name, name)) {
            return false;
        }
        for (String f : features) {
            switch (f) {
                case ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE:
                    if (!sInfo.getSupportedFeatures().contains(
                            new ImsFeatureConfiguration.FeatureSlotPair(0,
                                    ImsFeature.FEATURE_EMERGENCY_MMTEL))) {
                        return false;
                    }
                    break;
                case ImsResolver.METADATA_MMTEL_FEATURE:
                    if (!sInfo.getSupportedFeatures().contains(
                            new ImsFeatureConfiguration.FeatureSlotPair(0,
                                    ImsFeature.FEATURE_MMTEL))) {
                        return false;
                    }
                    break;
                case ImsResolver.METADATA_RCS_FEATURE:
                    if (!sInfo.getSupportedFeatures().contains(
                            new ImsFeatureConfiguration.FeatureSlotPair(0,
                                    ImsFeature.FEATURE_RCS))) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    private ResolveInfo getResolveInfo(ComponentName name, Set<String> features,
            boolean isPermissionGranted) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = name.getPackageName();
        info.serviceInfo.name = name.getClassName();
        info.serviceInfo.metaData = new Bundle();
        for (String s : features) {
            info.serviceInfo.metaData.putBoolean(s, true);
        }
        if (isPermissionGranted) {
            info.serviceInfo.permission = Manifest.permission.BIND_IMS_SERVICE;
        }
        return info;
    }
}
