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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates a list of ImsServices that are available to bind to based on the Device configuration
 * overlay values "config_ims_rcs_package" and "config_ims_mmtel_package" as well as Carrier
 * Configuration value "config_ims_rcs_package_override_string" and
 * "config_ims_mmtel_package_override_string".
 * These ImsServices are then bound to in the following order for each mmtel and rcs feature:
 *
 * 1. Carrier Config defined override value per SIM.
 * 2. Device overlay default value (including no SIM case).
 *
 * ImsManager can then retrieve the binding to the correct ImsService using
 * {@link #getImsServiceControllerAndListen} on a per-slot and per feature basis.
 */

public class ImsResolver implements ImsServiceController.ImsServiceControllerCallbacks {

    private static final String TAG = "ImsResolver";

    @VisibleForTesting
    public static final String METADATA_EMERGENCY_MMTEL_FEATURE =
            "android.telephony.ims.EMERGENCY_MMTEL_FEATURE";
    @VisibleForTesting
    public static final String METADATA_MMTEL_FEATURE = "android.telephony.ims.MMTEL_FEATURE";
    @VisibleForTesting
    public static final String METADATA_RCS_FEATURE = "android.telephony.ims.RCS_FEATURE";
    // Overrides the sanity permission check of android.permission.BIND_IMS_SERVICE for any
    // ImsService that is connecting to the platform.
    // This should ONLY be used for testing and should not be used in production ImsServices.
    private static final String METADATA_OVERRIDE_PERM_CHECK = "override_bind_check";

    // Based on updates from PackageManager
    private static final int HANDLER_ADD_PACKAGE = 0;
    // Based on updates from PackageManager
    private static final int HANDLER_REMOVE_PACKAGE = 1;
    // Based on updates from CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED
    private static final int HANDLER_CONFIG_CHANGED = 2;
    // A query has been started for an ImsService to relay the features they support.
    private static final int HANDLER_START_DYNAMIC_FEATURE_QUERY = 3;
    // A dynamic query to request ImsService features has completed.
    private static final int HANDLER_DYNAMIC_FEATURE_CHANGE = 4;
    // Testing: Overrides the current configuration for ImsService binding
    private static final int HANDLER_OVERRIDE_IMS_SERVICE_CONFIG = 5;
    // Based on boot complete indication. When this happens, there may be ImsServices that are not
    // direct boot aware that need to be started.
    private static final int HANDLER_BOOT_COMPLETE = 6;

    // Delay between dynamic ImsService queries.
    private static final int DELAY_DYNAMIC_QUERY_MS = 5000;


    /**
     * Stores information about an ImsService, including the package name, class name, and features
     * that the service supports.
     */
    @VisibleForTesting
    public static class ImsServiceInfo {
        public ComponentName name;
        // Determines if features were created from metadata in the manifest or through dynamic
        // query.
        public boolean featureFromMetadata = true;
        public ImsServiceControllerFactory controllerFactory;

        // Map slotId->Feature
        private final HashSet<ImsFeatureConfiguration.FeatureSlotPair> mSupportedFeatures;
        private final int mNumSlots;

        public ImsServiceInfo(int numSlots) {
            mNumSlots = numSlots;
            mSupportedFeatures = new HashSet<>();
        }

        void addFeatureForAllSlots(int feature) {
            for (int i = 0; i < mNumSlots; i++) {
                mSupportedFeatures.add(new ImsFeatureConfiguration.FeatureSlotPair(i, feature));
            }
        }

        void replaceFeatures(Set<ImsFeatureConfiguration.FeatureSlotPair> newFeatures) {
            mSupportedFeatures.clear();
            mSupportedFeatures.addAll(newFeatures);
        }

        @VisibleForTesting
        public Set<ImsFeatureConfiguration.FeatureSlotPair> getSupportedFeatures() {
            return mSupportedFeatures;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImsServiceInfo that = (ImsServiceInfo) o;

            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (!mSupportedFeatures.equals(that.mSupportedFeatures)) {
                return false;
            }
            return controllerFactory != null ? controllerFactory.equals(that.controllerFactory)
                    : that.controllerFactory == null;
        }

        @Override
        public int hashCode() {
            // We do not include mSupportedFeatures in hashcode because the internal structure
            // changes after adding.
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (controllerFactory != null ? controllerFactory.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "[ImsServiceInfo] name="
                    + name
                    + ", featureFromMetadata="
                    + featureFromMetadata
                    + ","
                    + printFeatures(mSupportedFeatures);
        }
    }

    // Receives broadcasts from the system involving changes to the installed applications. If
    // an ImsService that we are configured to use is installed, we must bind to it.
    private BroadcastReceiver mAppChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String packageName = intent.getData().getSchemeSpecificPart();
            switch (action) {
                case Intent.ACTION_PACKAGE_ADDED:
                    // intentional fall-through
                case Intent.ACTION_PACKAGE_REPLACED:
                    // intentional fall-through
                case Intent.ACTION_PACKAGE_CHANGED:
                    mHandler.obtainMessage(HANDLER_ADD_PACKAGE, packageName).sendToTarget();
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                    mHandler.obtainMessage(HANDLER_REMOVE_PACKAGE, packageName).sendToTarget();
                    break;
                default:
                    return;
            }
        }
    };

    // Receives the broadcast that a new Carrier Config has been loaded in order to possibly
    // unbind from one service and bind to another.
    private BroadcastReceiver mConfigChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            int slotId = intent.getIntExtra(CarrierConfigManager.EXTRA_SLOT_INDEX,
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX);

            if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.i(TAG, "Received CCC for invalid slot id.");
                return;
            }

            int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            int slotSimState = mTelephonyManagerProxy.getSimState(mContext, slotId);
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    && slotSimState != TelephonyManager.SIM_STATE_ABSENT) {
                // We only care about carrier config updates that happen when a slot is known to be
                // absent or populated and the carrier config has been loaded.
                Log.i(TAG, "Received CCC for slot " + slotId + " and sim state "
                        + slotSimState + ", ignoring.");
                return;
            }

            Log.i(TAG, "Received Carrier Config Changed for SlotId: " + slotId);

            mHandler.obtainMessage(HANDLER_CONFIG_CHANGED, slotId).sendToTarget();
        }
    };

    // Receives the broadcast that the device has finished booting (and the device is no longer
    // encrypted).
    private BroadcastReceiver mBootCompleted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received BOOT_COMPLETED");
            // Recalculate all cached services to pick up ones that have just been enabled since
            // boot complete.
            mHandler.obtainMessage(HANDLER_BOOT_COMPLETE, null).sendToTarget();
        }
    };

    /**
     * Testing interface used to mock SubscriptionManager in testing
     */
    @VisibleForTesting
    public interface SubscriptionManagerProxy {
        /**
         * Mock-able interface for {@link SubscriptionManager#getSubId(int)} used for testing.
         */
        int getSubId(int slotId);
        /**
         * Mock-able interface for {@link SubscriptionManager#getSlotIndex(int)} used for testing.
         */
        int getSlotIndex(int subId);
    }

    /**
     * Testing interface used to stub out TelephonyManager dependencies.
     */
    @VisibleForTesting
    public interface TelephonyManagerProxy {
        /**
         * @return the SIM state for the slot ID specified.
         */
        int getSimState(Context context, int slotId);
    }

    private TelephonyManagerProxy mTelephonyManagerProxy = new TelephonyManagerProxy() {
        @Override
        public int getSimState(Context context, int slotId) {
            TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            if (tm == null) {
                return TelephonyManager.SIM_STATE_UNKNOWN;
            }
            return tm.getSimState(slotId);
        }
    };

    private SubscriptionManagerProxy mSubscriptionManagerProxy = new SubscriptionManagerProxy() {
        @Override
        public int getSubId(int slotId) {
            int[] subIds = SubscriptionManager.getSubId(slotId);
            if (subIds != null) {
                // This is done in all other places getSubId is used.
                return subIds[0];
            }
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        @Override
        public int getSlotIndex(int subId) {
            return SubscriptionManager.getSlotIndex(subId);
        }
    };

    /**
     * Testing interface for injecting mock ImsServiceControllers.
     */
    @VisibleForTesting
    public interface ImsServiceControllerFactory {
        /**
         * @return the Service Interface String used for binding the ImsService.
         */
        String getServiceInterface();
        /**
         * @return the ImsServiceController created using the context and componentName supplied.
         */
        ImsServiceController create(Context context, ComponentName componentName,
                ImsServiceController.ImsServiceControllerCallbacks callbacks);
    }

    private ImsServiceControllerFactory mImsServiceControllerFactory =
            new ImsServiceControllerFactory() {

        @Override
        public String getServiceInterface() {
            return ImsService.SERVICE_INTERFACE;
        }

        @Override
        public ImsServiceController create(Context context, ComponentName componentName,
                ImsServiceController.ImsServiceControllerCallbacks callbacks) {
            return new ImsServiceController(context, componentName, callbacks);
        }
    };

    /**
     * Used for testing.
     */
    @VisibleForTesting
    public interface ImsDynamicQueryManagerFactory {
        ImsServiceFeatureQueryManager create(Context context,
                ImsServiceFeatureQueryManager.Listener listener);
    }

    private ImsServiceControllerFactory mImsServiceControllerFactoryCompat =
            new ImsServiceControllerFactory() {
                @Override
                public String getServiceInterface() {
                    return android.telephony.ims.compat.ImsService.SERVICE_INTERFACE;
                }

                @Override
                public ImsServiceController create(Context context, ComponentName componentName,
                        ImsServiceController.ImsServiceControllerCallbacks callbacks) {
                    return new ImsServiceControllerCompat(context, componentName, callbacks);
                }
            };

    private ImsDynamicQueryManagerFactory mDynamicQueryManagerFactory =
            ImsServiceFeatureQueryManager::new;

    private final CarrierConfigManager mCarrierConfigManager;
    private final Context mContext;
    // Special context created only for registering receivers for all users using UserHandle.ALL.
    // The lifetime of a registered receiver is bounded by the lifetime of the context it's
    // registered through, so we must retain the Context as long as we need the receiver to be
    // active.
    private final Context mReceiverContext;
    // Locks mBoundImsServicesByFeature only. Be careful to avoid deadlocks from
    // ImsServiceController callbacks.
    private final Object mBoundServicesLock = new Object();
    private final int mNumSlots;
    private SparseArray<Map<Integer, String>> mCarrierServices;
    // Package name of the default device services, Maps ImsFeature -> packageName.
    private Map<Integer, String> mDeviceServices;
    // Persistent Logging
    private final LocalLog mEventLog = new LocalLog(50);

    // Synchronize all events on a handler to ensure that the cache includes the most recent
    // version of the installed ImsServices.
    private Handler mHandler = new Handler(Looper.getMainLooper(), (msg) -> {
        switch (msg.what) {
            case HANDLER_ADD_PACKAGE: {
                String packageName = (String) msg.obj;
                maybeAddedImsService(packageName);
                break;
            }
            case HANDLER_REMOVE_PACKAGE: {
                String packageName = (String) msg.obj;
                maybeRemovedImsService(packageName);
                break;
            }
            case HANDLER_BOOT_COMPLETE: {
                mEventLog.log("handling BOOT_COMPLETE");
                // Re-evaluate bound services for all slots after requerying packagemanager
                maybeAddedImsService(null /*packageName*/);
                break;
            }
            case HANDLER_CONFIG_CHANGED: {
                int slotId = (Integer) msg.obj;
                carrierConfigChanged(slotId);
                break;
            }
            case HANDLER_START_DYNAMIC_FEATURE_QUERY: {
                ImsServiceInfo info = (ImsServiceInfo) msg.obj;
                startDynamicQuery(info);
                break;
            }
            case HANDLER_DYNAMIC_FEATURE_CHANGE: {
                SomeArgs args = (SomeArgs) msg.obj;
                ComponentName name = (ComponentName) args.arg1;
                Set<ImsFeatureConfiguration.FeatureSlotPair> features =
                        (Set<ImsFeatureConfiguration.FeatureSlotPair>) args.arg2;
                args.recycle();
                dynamicQueryComplete(name, features);
                break;
            }
            case HANDLER_OVERRIDE_IMS_SERVICE_CONFIG: {
                int slotId = msg.arg1;
                // arg2 will be equal to 1 if it is a carrier service.
                boolean isCarrierImsService = (msg.arg2 == 1);
                String packageName = (String) msg.obj;
                overrideService(isCarrierImsService, slotId, packageName);
                break;
            }
            default:
                return false;
        }
        return true;
    });

    // Results from dynamic queries to ImsService regarding the features they support.
    private ImsServiceFeatureQueryManager.Listener mDynamicQueryListener =
            new ImsServiceFeatureQueryManager.Listener() {

                @Override
                public void onComplete(ComponentName name,
                        Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
                    Log.d(TAG, "onComplete called for name: " + name + printFeatures(features));
                    handleFeaturesChanged(name, features);
                }

                @Override
                public void onError(ComponentName name) {
                    Log.w(TAG, "onError: " + name + "returned with an error result");
                    mEventLog.log("onError - dynamic query error for " + name);
                    scheduleQueryForFeatures(name, DELAY_DYNAMIC_QUERY_MS);
                }

                @Override
                public void onPermanentError(ComponentName name) {
                    Log.w(TAG, "onPermanentError: component=" + name);
                    mEventLog.log("onPermanentError - error for " + name);
                    mHandler.obtainMessage(HANDLER_REMOVE_PACKAGE,
                            name.getPackageName()).sendToTarget();
                }
            };

    // Used during testing, overrides the carrier services while non-empty.
    // Array index corresponds to slot Id associated with the service package name.
    private String[] mOverrideServices;
    // List index corresponds to Slot Id, Maps ImsFeature.FEATURE->bound ImsServiceController
    // Locked on mBoundServicesLock
    private List<SparseArray<ImsServiceController>> mBoundImsServicesByFeature;
    // not locked, only accessed on a handler thread.
    // Tracks list of all installed ImsServices
    private Map<ComponentName, ImsServiceInfo> mInstalledServicesCache = new HashMap<>();
    // not locked, only accessed on a handler thread.
    // Active ImsServiceControllers, which are bound to ImsServices.
    private Map<ComponentName, ImsServiceController> mActiveControllers = new HashMap<>();
    private ImsServiceFeatureQueryManager mFeatureQueryManager;

    public ImsResolver(Context context, String defaultImsPackageName, int numSlots) {
        mContext = context;
        mReceiverContext = context.createContextAsUser(UserHandle.ALL, 0 /*flags*/);

        mCarrierServices = new SparseArray<>(numSlots);
        mDeviceServices = new ArrayMap<>();
        // TODO: create separate device configurations for MMTEL and RCS
        setDeviceConfiguration(defaultImsPackageName, ImsFeature.FEATURE_EMERGENCY_MMTEL);
        setDeviceConfiguration(defaultImsPackageName, ImsFeature.FEATURE_MMTEL);
        setDeviceConfiguration(defaultImsPackageName, ImsFeature.FEATURE_RCS);
        mNumSlots = numSlots;
        mCarrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        mOverrideServices = new String[numSlots];
        mBoundImsServicesByFeature = Stream.generate(SparseArray<ImsServiceController>::new)
                .limit(mNumSlots).collect(Collectors.toList());

        IntentFilter appChangedFilter = new IntentFilter();
        appChangedFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        appChangedFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appChangedFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        appChangedFilter.addDataScheme("package");
        mReceiverContext.registerReceiver(mAppChangedReceiver, appChangedFilter);
        mReceiverContext.registerReceiver(mConfigChangedReceiver, new IntentFilter(
                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        mReceiverContext.registerReceiver(mBootCompleted, new IntentFilter(
                Intent.ACTION_BOOT_COMPLETED));
    }

    @VisibleForTesting
    public void setTelephonyManagerProxy(TelephonyManagerProxy proxy) {
        mTelephonyManagerProxy = proxy;
    }

    @VisibleForTesting
    public void setSubscriptionManagerProxy(SubscriptionManagerProxy proxy) {
        mSubscriptionManagerProxy = proxy;
    }

    @VisibleForTesting
    public void setImsServiceControllerFactory(ImsServiceControllerFactory factory) {
        mImsServiceControllerFactory = factory;
    }

    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }

    @VisibleForTesting
    public void setImsDynamicQueryManagerFactory(ImsDynamicQueryManagerFactory m) {
        mDynamicQueryManagerFactory = m;
    }

    /**
     * Needs to be called after the constructor to kick off the process of binding to ImsServices.
     */
    public void initialize() {
        mEventLog.log("Initializing");
        Log.i(TAG, "Initializing cache.");
        mFeatureQueryManager = mDynamicQueryManagerFactory.create(mContext, mDynamicQueryListener);

        // This will get all services with the correct intent filter from PackageManager
        List<ImsServiceInfo> infos = getImsServiceInfo(null);
        for (ImsServiceInfo info : infos) {
            if (!mInstalledServicesCache.containsKey(info.name)) {
                mInstalledServicesCache.put(info.name, info);
            }
        }
        // Update the package names of the carrier ImsServices if they do not exist already and
        // possibly bind if carrier configs exist. Otherwise wait for CarrierConfigChanged
        // indication.
        bindCarrierServicesIfAvailable();
    }

    // Only start the bind if there is an existing Carrier Configuration. Otherwise, wait for
    // carrier config changed.
    private void bindCarrierServicesIfAvailable() {
        boolean hasConfigChanged = false;
        for (int slotId = 0; slotId < mNumSlots; slotId++) {
            Map<Integer, String> featureMap = getImsPackageOverrideConfig(slotId);
            for (int f = ImsFeature.FEATURE_EMERGENCY_MMTEL; f < ImsFeature.FEATURE_MAX; f++) {
                String newPackageName = featureMap.getOrDefault(f, "");
                if (!TextUtils.isEmpty(newPackageName)) {
                    mEventLog.log("bindCarrierServicesIfAvailable - carrier package found: "
                            + newPackageName + " on slot " + slotId);
                    setCarrierConfiguration(newPackageName, slotId, f);
                    ImsServiceInfo info = getImsServiceInfoFromCache(newPackageName);
                    // We do not want to trigger feature configuration changes unless there is
                    // already a valid carrier config change.
                    if (info != null && info.featureFromMetadata) {
                        hasConfigChanged = true;
                    } else {
                        // Config will change when this query completes
                        scheduleQueryForFeatures(info);
                    }
                }
            }
        }
        if (hasConfigChanged) calculateFeatureConfigurationChange();
    }

    /**
     * Notify ImsService to enable IMS for the framework. This will trigger IMS registration and
     * trigger ImsFeature status updates.
     */
    public void enableIms(int slotId) {
        SparseArray<ImsServiceController> controllers = getImsServiceControllers(slotId);
        if (controllers != null) {
            for (int i = 0; i < controllers.size(); i++) {
                int key = controllers.keyAt(i);
                controllers.get(key).enableIms(slotId);
            }
        }
    }

    /**
     * Notify ImsService to disable IMS for the framework. This will trigger IMS de-registration and
     * trigger ImsFeature capability status to become false.
     */
    public void disableIms(int slotId) {
        SparseArray<ImsServiceController> controllers = getImsServiceControllers(slotId);
        if (controllers != null) {
            for (int i = 0; i < controllers.size(); i++) {
                int key = controllers.keyAt(i);
                controllers.get(key).disableIms(slotId);
            }
        }
    }

    /**
     * Returns the {@link IImsMmTelFeature} that corresponds to the given slot Id or {@link null} if
     * the service is not available. If an IImsMMTelFeature is available, the
     * {@link IImsServiceFeatureCallback} callback is registered as a listener for feature updates.
     * @param slotId The SIM slot that we are requesting the {@link IImsMmTelFeature} for.
     * @param callback Listener that will send updates to ImsManager when there are updates to
     * the feature.
     * @return {@link IImsMmTelFeature} interface or {@link null} if it is unavailable.
     */
    public IImsMmTelFeature getMmTelFeatureAndListen(int slotId,
            IImsServiceFeatureCallback callback) {
        ImsServiceController controller = getImsServiceControllerAndListen(slotId,
                ImsFeature.FEATURE_MMTEL, callback);
        return (controller != null) ? controller.getMmTelFeature(slotId) : null;
    }

    /**
     * Returns the {@link IImsRcsFeature} that corresponds to the given slot Id for emergency
     * calling or {@link null} if the service is not available. If an IImsMMTelFeature is
     * available, the {@link IImsServiceFeatureCallback} callback is registered as a listener for
     * feature updates.
     * @param slotId The SIM slot that we are requesting the {@link IImsRcsFeature} for.
     * @param callback listener that will send updates to ImsManager when there are updates to
     * the feature.
     * @return {@link IImsRcsFeature} interface or {@link null} if it is unavailable.
     */
    public IImsRcsFeature getRcsFeatureAndListen(int slotId, IImsServiceFeatureCallback callback) {
        ImsServiceController controller = getImsServiceControllerAndListen(slotId,
                ImsFeature.FEATURE_RCS, callback);
        return (controller != null) ? controller.getRcsFeature(slotId) : null;
    }

    /**
     * Returns the ImsRegistration structure associated with the slotId and feature specified.
     */
    public @Nullable IImsRegistration getImsRegistration(int slotId, int feature)
            throws RemoteException {
        ImsServiceController controller = getImsServiceController(slotId, feature);
        if (controller != null) {
            return controller.getRegistration(slotId);
        }
        return null;
    }

    /**
     * Returns the ImsConfig structure associated with the slotId and feature specified.
     */
    public @Nullable IImsConfig getImsConfig(int slotId, int feature)
            throws RemoteException {
        ImsServiceController controller = getImsServiceController(slotId, feature);
        if (controller != null) {
            return controller.getConfig(slotId);
        }
        return null;
    }

    @VisibleForTesting
    public ImsServiceController getImsServiceController(int slotId, int feature) {
        if (slotId < 0 || slotId >= mNumSlots) {
            return null;
        }
        ImsServiceController controller;
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                return null;
            }
            controller = services.get(feature);
        }
        return controller;
    }

    private  SparseArray<ImsServiceController> getImsServiceControllers(int slotId) {
        if (slotId < 0 || slotId >= mNumSlots) {
            return null;
        }
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                return null;
            }
            return services;
        }
    }

    @VisibleForTesting
    public ImsServiceController getImsServiceControllerAndListen(int slotId, int feature,
            IImsServiceFeatureCallback callback) {
        ImsServiceController controller = getImsServiceController(slotId, feature);

        if (controller != null) {
            controller.addImsServiceFeatureCallback(callback);
            return controller;
        }
        return null;
    }

    // Used for testing only.
    public boolean overrideImsServiceConfiguration(int slotId, boolean isCarrierService,
            String packageName) {
        if (slotId < 0 || slotId >= mNumSlots) {
            Log.w(TAG, "overrideImsServiceConfiguration: invalid slotId!");
            return false;
        }

        if (packageName == null) {
            Log.w(TAG, "overrideImsServiceConfiguration: null packageName!");
            return false;
        }

        // encode boolean to int for Message.
        int carrierService = isCarrierService ? 1 : 0;
        Message.obtain(mHandler, HANDLER_OVERRIDE_IMS_SERVICE_CONFIG, slotId, carrierService,
                packageName).sendToTarget();
        return true;
    }

    // not synchronized, access through handler ONLY.
    private String getDeviceConfiguration(@ImsFeature.FeatureType int featureType) {
        return mDeviceServices.getOrDefault(featureType, "");
    }

    // not synchronized, access in handler ONLY.
    private void setDeviceConfiguration(String name, @ImsFeature.FeatureType int featureType) {
        mDeviceServices.put(featureType, name);
    }

    // not synchronized, access in handler ONLY.
    private void setCarrierConfiguration(@NonNull String packageName, int slotId,
            @ImsFeature.FeatureType int featureType) {
        getCarrierConfigurations(slotId).put(featureType, packageName);
    }

    // not synchronized, access in handler ONLY.
    private @NonNull String getCarrierConfiguration(int slotId,
            @ImsFeature.FeatureType int featureType) {
        return getCarrierConfigurations(slotId).getOrDefault(featureType, "");
    }

    // not synchronized, access in handler ONLY.
    private @NonNull Map<Integer, String> getCarrierConfigurations(int slotId) {
        Map<Integer, String> carrierConfig = mCarrierServices.get(slotId);
        if (carrierConfig == null) {
            carrierConfig = new ArrayMap<>();
            mCarrierServices.put(slotId, carrierConfig);
        }
        return carrierConfig;
    }

    /**
     * @return true if there is a carrier configuration that exists for the slot & featureType pair
     * and the cached carrier ImsService associated with the configuration also supports the
     * requested ImsFeature type.
     */
    // not synchronized, access in handler ONLY.
    private boolean doesCarrierConfigurationExist(int slotId,
            @ImsFeature.FeatureType int featureType) {
        String carrierPackage = getCarrierConfiguration(slotId, featureType);
        if (TextUtils.isEmpty(carrierPackage)) {
            return false;
        }
        // Config exists, but the carrier ImsService also needs to support this feature
        ImsServiceInfo info = getImsServiceInfoFromCache(carrierPackage);
        return info != null && info.getSupportedFeatures().stream().anyMatch(
                feature -> feature.slotId == slotId && feature.featureType == featureType);
    }

    /**
     * @return the package name of the ImsService with the requested configuration.
     */
    // used in shell commands queries during testing only.
    public String getImsServiceConfiguration(int slotId, boolean isCarrierService,
            @ImsFeature.FeatureType int featureType) {
        if (slotId < 0 || slotId >= mNumSlots) {
            Log.w(TAG, "getImsServiceConfiguration: invalid slotId!");
            return "";
        }

        LinkedBlockingQueue<String> result = new LinkedBlockingQueue<>(1);
        // access the configuration on the handler.
        mHandler.post(() -> result.offer(isCarrierService
                ? getCarrierConfiguration(slotId, featureType) :
                getDeviceConfiguration(featureType)));
        return result.poll();
    }

    private void putImsController(int slotId, int feature, ImsServiceController controller) {
        if (slotId < 0 || slotId >= mNumSlots || feature <= ImsFeature.FEATURE_INVALID
                || feature >= ImsFeature.FEATURE_MAX) {
            Log.w(TAG, "putImsController received invalid parameters - slot: " + slotId
                    + ", feature: " + feature);
            return;
        }
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                services = new SparseArray<>();
                mBoundImsServicesByFeature.add(slotId, services);
            }
            mEventLog.log("putImsController - [" + slotId + ", "
                    + ImsFeature.FEATURE_LOG_MAP.get(feature) + "] -> " + controller);
            Log.i(TAG, "ImsServiceController added on slot: " + slotId + " with feature: "
                    + ImsFeature.FEATURE_LOG_MAP.get(feature) + " using package: "
                    + controller.getComponentName());
            services.put(feature, controller);
        }
    }

    private ImsServiceController removeImsController(int slotId, int feature) {
        if (slotId < 0 || slotId >= mNumSlots || feature <= ImsFeature.FEATURE_INVALID
                || feature >= ImsFeature.FEATURE_MAX) {
            Log.w(TAG, "removeImsController received invalid parameters - slot: " + slotId
                    + ", feature: " + feature);
            return null;
        }
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                return null;
            }
            ImsServiceController c = services.get(feature, null);
            if (c != null) {
                mEventLog.log("removeImsController - [" + slotId + ", "
                        + ImsFeature.FEATURE_LOG_MAP.get(feature) + "] -> " + c);
                Log.i(TAG, "ImsServiceController removed on slot: " + slotId + " with feature: "
                        + ImsFeature.FEATURE_LOG_MAP.get(feature) + " using package: "
                        + c.getComponentName());
                services.remove(feature);
            }
            return c;
        }
    }

    // Update the current cache with the new ImsService(s) if it has been added or update the
    // supported IMS features if they have changed.
    // Called from the handler ONLY
    private void maybeAddedImsService(String packageName) {
        Log.d(TAG, "maybeAddedImsService, packageName: " + packageName);
        List<ImsServiceInfo> infos = getImsServiceInfo(packageName);
        // Wait until all ImsServiceInfo is cached before calling
        // calculateFeatureConfigurationChange to reduce churn.
        boolean requiresCalculation = false;
        for (ImsServiceInfo info : infos) {
            // Checking to see if the ComponentName is the same, so we can update the supported
            // features. Will only be one (if it exists), since it is a set.
            ImsServiceInfo match = getInfoByComponentName(mInstalledServicesCache, info.name);
            if (match != null) {
                // for dynamic query the new "info" will have no supported features yet. Don't wipe
                // out the cache for the existing features or update yet. Instead start a query
                // for features dynamically.
                if (info.featureFromMetadata) {
                    mEventLog.log("maybeAddedImsService - updating features for " + info.name
                            + ": " + printFeatures(match.getSupportedFeatures()) + " -> "
                            + printFeatures(info.getSupportedFeatures()));
                    Log.i(TAG, "Updating features in cached ImsService: " + info.name);
                    Log.d(TAG, "Updating features - Old features: " + match + " new features: "
                            + info);
                    // update features in the cache
                    match.replaceFeatures(info.getSupportedFeatures());
                    requiresCalculation = true;
                } else {
                    mEventLog.log("maybeAddedImsService - scheduling query for " + info);
                    // start a query to get ImsService features
                    scheduleQueryForFeatures(info);
                }
            } else {
                Log.i(TAG, "Adding newly added ImsService to cache: " + info.name);
                mEventLog.log("maybeAddedImsService - adding new ImsService: " + info);
                mInstalledServicesCache.put(info.name, info);
                if (info.featureFromMetadata) {
                    requiresCalculation = true;
                } else {
                    // newly added ImsServiceInfo that has not had features queried yet. Start async
                    // bind and query features.
                    scheduleQueryForFeatures(info);
                }
            }
        }
        if (requiresCalculation) calculateFeatureConfigurationChange();
    }

    // Remove the ImsService from the cache. This may have been due to the ImsService being removed
    // from the device or was returning permanent errors when bound.
    // Called from the handler ONLY
    private boolean maybeRemovedImsService(String packageName) {
        ImsServiceInfo match = getInfoByPackageName(mInstalledServicesCache, packageName);
        if (match != null) {
            mInstalledServicesCache.remove(match.name);
            mEventLog.log("maybeRemovedImsService - removing ImsService: " + match);
            Log.i(TAG, "Removing ImsService: " + match.name);
            unbindImsService(match);
            calculateFeatureConfigurationChange();
            return true;
        }
        return false;
    }

    private boolean isDeviceService(ImsServiceInfo info) {
        if (info == null) return false;
        return mDeviceServices.containsValue(info.name.getPackageName());
    }

    private List<Integer> getSlotsForActiveCarrierService(ImsServiceInfo info) {
        if (info == null) return Collections.emptyList();
        List<Integer> slots = new ArrayList<>(mNumSlots);
        for (int i = 0; i < mNumSlots; i++) {
            if (!TextUtils.isEmpty(getCarrierConfigurations(i).values().stream()
                    .filter(e -> e.equals(info.name.getPackageName())).findAny().orElse(""))) {
                slots.add(i);
            }
        }
        return slots;
    }

    private ImsServiceController getControllerByServiceInfo(
            Map<ComponentName, ImsServiceController> searchMap, ImsServiceInfo matchValue) {
        return searchMap.values().stream()
                .filter(c -> Objects.equals(c.getComponentName(), matchValue.name))
                .findFirst().orElse(null);
    }

    private ImsServiceInfo getInfoByPackageName(Map<ComponentName, ImsServiceInfo> searchMap,
            String matchValue) {
        return searchMap.values().stream()
                .filter((i) -> Objects.equals(i.name.getPackageName(), matchValue))
                .findFirst().orElse(null);
    }

    private ImsServiceInfo getInfoByComponentName(
            Map<ComponentName, ImsServiceInfo> searchMap, ComponentName matchValue) {
        return searchMap.get(matchValue);
    }

    private void bindImsServiceWithFeatures(ImsServiceInfo info,
            Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
        // Only bind if there are features that will be created by the service.
        if (shouldFeaturesCauseBind(features)) {
            // Check to see if an active controller already exists
            ImsServiceController controller = getControllerByServiceInfo(mActiveControllers, info);
            if (controller != null) {
                Log.i(TAG, "ImsService connection exists, updating features " + features);
                try {
                    controller.changeImsServiceFeatures(features);
                    // Features have been set, there was an error adding/removing. When the
                    // controller recovers, it will add/remove again.
                } catch (RemoteException e) {
                    Log.w(TAG, "bindImsService: error=" + e.getMessage());
                }
            } else {
                controller = info.controllerFactory.create(mContext, info.name, this);
                Log.i(TAG, "Binding ImsService: " + controller.getComponentName()
                        + " with features: " + features);
                controller.bind(features);
                mEventLog.log("bindImsServiceWithFeatures - create new controller: "
                        + controller);
            }
            mActiveControllers.put(info.name, controller);
        }
    }

    // Clean up and unbind from an ImsService
    private void unbindImsService(ImsServiceInfo info) {
        if (info == null) {
            return;
        }
        ImsServiceController controller = getControllerByServiceInfo(mActiveControllers, info);
        if (controller != null) {
            // Calls imsServiceFeatureRemoved on all features in the controller
            try {
                Log.i(TAG, "Unbinding ImsService: " + controller.getComponentName());
                mEventLog.log("unbindImsService - unbinding and removing " + controller);
                controller.unbind();
            } catch (RemoteException e) {
                Log.e(TAG, "unbindImsService: Remote Exception: " + e.getMessage());
            }
            mActiveControllers.remove(info.name);
        }
    }

    // Calculate which features an ImsServiceController will need. If it is the carrier specific
    // ImsServiceController, it will be granted all of the features it requests on the associated
    // slot. If it is the device ImsService, it will get all of the features not covered by the
    // carrier implementation.
    private HashSet<ImsFeatureConfiguration.FeatureSlotPair> calculateFeaturesToCreate(
            ImsServiceInfo info) {
        HashSet<ImsFeatureConfiguration.FeatureSlotPair> imsFeaturesBySlot = new HashSet<>();
        List<Integer> slots = getSlotsForActiveCarrierService(info);
        if (!slots.isEmpty()) {
            // There is an active carrier config associated with this. Return with the ImsService's
            // supported features that are also within the carrier configuration
            imsFeaturesBySlot.addAll(info.getSupportedFeatures().stream()
                    .filter(feature -> info.name.getPackageName().equals(
                            getCarrierConfiguration(feature.slotId, feature.featureType)))
                    .collect(Collectors.toList()));
            return imsFeaturesBySlot;
        }
        if (isDeviceService(info)) {
            imsFeaturesBySlot.addAll(info.getSupportedFeatures().stream()
                    // only allow supported features that are also set for this package as the
                    // device configuration.
                    .filter(feature -> info.name.getPackageName().equals(
                            getDeviceConfiguration(feature.featureType)))
                    // filter out any separate carrier configuration, since that feature is handled
                    // by the carrier ImsService.
                    .filter(feature -> !doesCarrierConfigurationExist(feature.slotId,
                            feature.featureType))
                    .collect(Collectors.toList()));
        }
        return imsFeaturesBySlot;
    }

    /**
     * Implementation of
     * {@link ImsServiceController.ImsServiceControllerCallbacks#imsServiceFeatureCreated}, which
     * removes the ImsServiceController from the mBoundImsServicesByFeature structure.
     */
    public void imsServiceFeatureCreated(int slotId, int feature, ImsServiceController controller) {
        putImsController(slotId, feature, controller);
    }

    /**
     * Implementation of
     * {@link ImsServiceController.ImsServiceControllerCallbacks#imsServiceFeatureRemoved}, which
     * removes the ImsServiceController from the mBoundImsServicesByFeature structure.
     */
    public void imsServiceFeatureRemoved(int slotId, int feature, ImsServiceController controller) {
        removeImsController(slotId, feature);
    }

    /**
     * Implementation of
     * {@link ImsServiceController.ImsServiceControllerCallbacks#imsServiceFeaturesChanged, which
     * notify the ImsResolver of a change to the supported ImsFeatures of a connected ImsService.
     */
    public void imsServiceFeaturesChanged(ImsFeatureConfiguration config,
            ImsServiceController controller) {
        if (controller == null || config == null) {
            return;
        }
        Log.i(TAG, "imsServiceFeaturesChanged: config=" + config.getServiceFeatures()
                + ", ComponentName=" + controller.getComponentName());
        mEventLog.log("imsServiceFeaturesChanged - for " + controller + ", new config "
                + config.getServiceFeatures());
        handleFeaturesChanged(controller.getComponentName(), config.getServiceFeatures());
    }

    @Override
    public void imsServiceBindPermanentError(ComponentName name) {
        if (name == null) {
            return;
        }
        Log.w(TAG, "imsServiceBindPermanentError: component=" + name);
        mEventLog.log("imsServiceBindPermanentError - for " + name);
        mHandler.obtainMessage(HANDLER_REMOVE_PACKAGE, name.getPackageName()).sendToTarget();
    }

    /**
     * Determines if the features specified should cause a bind or keep a binding active to an
     * ImsService.
     * @return true if MMTEL or RCS features are present, false if they are not or only
     * EMERGENCY_MMTEL is specified.
     */
    private boolean shouldFeaturesCauseBind(Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
        long bindableFeatures = features.stream()
                // remove all emergency features
                .filter(f -> f.featureType != ImsFeature.FEATURE_EMERGENCY_MMTEL).count();
        return bindableFeatures > 0;
    }

    // Possibly rebind to another ImsService for testing.
    // Called from the handler ONLY
    private void overrideService(boolean isCarrierService, int slotId, String newPackageName) {
        mEventLog.log("overriding carrier ImsService to " + newPackageName
                + " on slot " + slotId);
        Map<Integer, String> overrideMap = new ArrayMap<>();
        overrideMap.put(ImsFeature.FEATURE_MMTEL, newPackageName);
        overrideMap.put(ImsFeature.FEATURE_RCS, newPackageName);
        if (isCarrierService) {
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                // not specified, replace package on all slots.
                for (int i = 0; i < mNumSlots; i++) {
                    mOverrideServices[i] = newPackageName;
                    updateBoundServices(i, overrideMap);
                }
            } else {
                mOverrideServices[slotId] = newPackageName;
                updateBoundServices(slotId, overrideMap);
            }
        } else {
            overrideBoundDeviceServices(overrideMap);
        }
    }

    // Called from handler ONLY.
    private void carrierConfigChanged(int slotId) {
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            // not specified, update carrier override cache and possibly rebind on all slots.
            for (int i = 0; i < mNumSlots; i++) {
                updateBoundServices(i, getImsPackageOverrideConfig(i));
            }
        }
        updateBoundServices(slotId, getImsPackageOverrideConfig(slotId));
    }

    private void updateBoundServices(int slotId, Map<Integer, String> featureMap) {
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX || slotId >= mNumSlots) {
            return;
        }
        boolean hasConfigChanged = false;
        String overridePackageName = mOverrideServices[slotId];
        for (int f = ImsFeature.FEATURE_EMERGENCY_MMTEL; f < ImsFeature.FEATURE_MAX; f++) {
            String oldPackageName = getCarrierConfiguration(slotId, f);
            String newPackageName = featureMap.getOrDefault(f, "");
            if (!TextUtils.isEmpty(overridePackageName)) {
                // Do not allow carrier config changes to change the override package while it
                // is in effect.
                Log.i(TAG, "CarrierConfig change ignored for " + newPackageName + " while "
                        + "override is in effect for " + overridePackageName);
                newPackageName = overridePackageName;
            }
            mEventLog.log("updateBoundServices - carrier package changed: "
                    + oldPackageName + " -> " + newPackageName + " on slot " + slotId);
            setCarrierConfiguration(newPackageName, slotId, f);
            // Carrier config may have not changed, but we still want to kick off a recalculation
            // in case there has been a change to the supported device features.
            ImsServiceInfo info = getImsServiceInfoFromCache(newPackageName);
            if (info == null || info.featureFromMetadata) {
                hasConfigChanged = true;
            } else {
                // Config will change when this query completes
                scheduleQueryForFeatures(info);
            }
        }
        if (hasConfigChanged) calculateFeatureConfigurationChange();
    }

    private @NonNull Map<Integer, String> getImsPackageOverrideConfig(int slotId) {
        int subId = mSubscriptionManagerProxy.getSubId(slotId);
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(subId);
        if (config == null) return Collections.emptyMap();
        // TODO: Create carrier configs for all feature types
        String packageName = config.getString(
                CarrierConfigManager.KEY_CONFIG_IMS_PACKAGE_OVERRIDE_STRING, null);
        Map<Integer, String> result = new ArrayMap<>();
        if (!TextUtils.isEmpty(packageName)) {
            result.put(ImsFeature.FEATURE_EMERGENCY_MMTEL, packageName);
            result.put(ImsFeature.FEATURE_MMTEL, packageName);
            result.put(ImsFeature.FEATURE_RCS, packageName);
        }
        return result;
    }

    private void overrideBoundDeviceServices(Map<Integer, String> overrideMap) {
        boolean hasConfigChanged = false;
        for (int f = ImsFeature.FEATURE_EMERGENCY_MMTEL; f < ImsFeature.FEATURE_MAX; f++) {
            String oldPackageName = getDeviceConfiguration(f);
            String overridePackageName = overrideMap.getOrDefault(f, "");
            if (!TextUtils.equals(oldPackageName, overridePackageName)) {
                mEventLog.log("overrideBoundDeviceServices - device package changed: "
                        + oldPackageName + " -> " + overridePackageName);
                setDeviceConfiguration(overridePackageName, f);
                ImsServiceInfo info = getImsServiceInfoFromCache(overridePackageName);
                if (info == null || info.featureFromMetadata) {
                    hasConfigChanged = true;
                } else {
                    // Config will change when this query completes
                    scheduleQueryForFeatures(info);
                }
            }
        }
        if (hasConfigChanged) calculateFeatureConfigurationChange();
    }

    /**
     * Schedules a query for dynamic ImsService features.
     */
    private void scheduleQueryForFeatures(ImsServiceInfo service, int delayMs) {
        // if not current device/carrier service, don't perform query. If this changes, this method
        // will be called again.
        if (!isDeviceService(service) && getSlotsForActiveCarrierService(service).isEmpty()) {
            Log.i(TAG, "scheduleQueryForFeatures: skipping query for ImsService that is not"
                    + " set as carrier/device ImsService.");
            return;
        }
        Message msg = Message.obtain(mHandler, HANDLER_START_DYNAMIC_FEATURE_QUERY, service);
        if (mHandler.hasMessages(HANDLER_START_DYNAMIC_FEATURE_QUERY, service)) {
            Log.d(TAG, "scheduleQueryForFeatures: dynamic query for " + service.name
                    + " already scheduled");
            return;
        }
        Log.d(TAG, "scheduleQueryForFeatures: starting dynamic query for " + service.name
                + " in " + delayMs + "ms.");
        mHandler.sendMessageDelayed(msg, delayMs);
    }

    private void scheduleQueryForFeatures(ComponentName name, int delayMs) {
        ImsServiceInfo service = getImsServiceInfoFromCache(name.getPackageName());
        if (service == null) {
            Log.w(TAG, "scheduleQueryForFeatures: Couldn't find cached info for name: " + name);
            return;
        }
        scheduleQueryForFeatures(service, delayMs);
    }

    private void scheduleQueryForFeatures(ImsServiceInfo service) {
        scheduleQueryForFeatures(service, 0);
    }

    /**
     * Schedules the processing of a completed query.
     */
    private void handleFeaturesChanged(ComponentName name,
            Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = name;
        args.arg2 = features;
        mHandler.obtainMessage(HANDLER_DYNAMIC_FEATURE_CHANGE, args).sendToTarget();
    }

    // Starts a dynamic query. Called from handler ONLY.
    private void startDynamicQuery(ImsServiceInfo service) {
        mEventLog.log("startDynamicQuery - starting query for " + service);
        boolean queryStarted = mFeatureQueryManager.startQuery(service.name,
                service.controllerFactory.getServiceInterface());
        if (!queryStarted) {
            Log.w(TAG, "startDynamicQuery: service could not connect. Retrying after delay.");
            mEventLog.log("startDynamicQuery - query failed. Retrying in "
                    + DELAY_DYNAMIC_QUERY_MS + " mS");
            scheduleQueryForFeatures(service, DELAY_DYNAMIC_QUERY_MS);
        } else {
            Log.d(TAG, "startDynamicQuery: Service queried, waiting for response.");
        }
    }

    // process complete dynamic query. Called from handler ONLY.
    private void dynamicQueryComplete(ComponentName name,
            Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
        ImsServiceInfo service = getImsServiceInfoFromCache(name.getPackageName());
        if (service == null) {
            Log.w(TAG, "dynamicQueryComplete: Couldn't find cached info for name: "
                    + name);
            return;
        }
        mEventLog.log("dynamicQueryComplete: for package " + name + ", features: "
                + printFeatures(service.getSupportedFeatures()) + " -> " + printFeatures(features));
        sanitizeFeatureConfig(features);
        // Add features to service
        service.replaceFeatures(features);
        calculateFeatureConfigurationChange();
    }

    /**
     * Ensure the feature includes MMTEL when it supports EMERGENCY_MMTEL, if not, remove.
     */
    private void sanitizeFeatureConfig(Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
        Set<ImsFeatureConfiguration.FeatureSlotPair> emergencyMmtelFeatures = features.stream()
                .filter(feature -> feature.featureType == ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .collect(Collectors.toSet());
        for (ImsFeatureConfiguration.FeatureSlotPair feature : emergencyMmtelFeatures) {
            if (!features.contains(new ImsFeatureConfiguration.FeatureSlotPair(feature.slotId,
                    ImsFeature.FEATURE_MMTEL))) {
                features.remove(feature);
            }
        }
    }

    // Calculate the new configuration for the bound ImsServices.
    // Should ONLY be called from the handler.
    private void calculateFeatureConfigurationChange() {
        for (ImsServiceInfo info : mInstalledServicesCache.values()) {
            Set<ImsFeatureConfiguration.FeatureSlotPair> features = calculateFeaturesToCreate(info);
            if (shouldFeaturesCauseBind(features)) {
                bindImsServiceWithFeatures(info, features);
            } else {
                unbindImsService(info);
            }
        }
    }

    private static String printFeatures(Set<ImsFeatureConfiguration.FeatureSlotPair> features) {
        StringBuilder featureString = new StringBuilder();
        featureString.append(" features: [");
        if (features != null) {
            for (ImsFeatureConfiguration.FeatureSlotPair feature : features) {
                featureString.append("{");
                featureString.append(feature.slotId);
                featureString.append(",");
                featureString.append(ImsFeature.FEATURE_LOG_MAP.get(feature.featureType));
                featureString.append("}");
            }
            featureString.append("]");
        }
        return featureString.toString();
    }

    /**
     * Returns the ImsServiceInfo that matches the provided packageName. Visible for testing
     * the ImsService caching functionality.
     */
    @VisibleForTesting
    public ImsServiceInfo getImsServiceInfoFromCache(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        ImsServiceInfo infoFilter = getInfoByPackageName(mInstalledServicesCache, packageName);
        if (infoFilter != null) {
            return infoFilter;
        } else {
            return null;
        }
    }

    // Return the ImsServiceInfo specified for the package name. If the package name is null,
    // get all packages that support ImsServices.
    private List<ImsServiceInfo> getImsServiceInfo(String packageName) {
        List<ImsServiceInfo> infos = new ArrayList<>();
        // Search for Current ImsService implementations
        infos.addAll(searchForImsServices(packageName, mImsServiceControllerFactory));
        // Search for compat ImsService Implementations
        infos.addAll(searchForImsServices(packageName, mImsServiceControllerFactoryCompat));
        return infos;
    }

    private List<ImsServiceInfo> searchForImsServices(String packageName,
            ImsServiceControllerFactory controllerFactory) {
        List<ImsServiceInfo> infos = new ArrayList<>();

        Intent serviceIntent = new Intent(controllerFactory.getServiceInterface());
        serviceIntent.setPackage(packageName);

        PackageManager packageManager = mContext.getPackageManager();
        for (ResolveInfo entry : packageManager.queryIntentServicesAsUser(
                serviceIntent,
                PackageManager.GET_META_DATA,
                UserHandle.getUserHandleForUid(UserHandle.myUserId()))) {
            ServiceInfo serviceInfo = entry.serviceInfo;

            if (serviceInfo != null) {
                ImsServiceInfo info = new ImsServiceInfo(mNumSlots);
                info.name = new ComponentName(serviceInfo.packageName, serviceInfo.name);
                info.controllerFactory = controllerFactory;

                // we will allow the manifest method of declaring manifest features in two cases:
                // 1) it is the device overlay "default" ImsService, where the features do not
                // change (the new method can still be used if the default does not define manifest
                // entries).
                // 2) using the "compat" ImsService, which only supports manifest query.
                if (isDeviceService(info)
                        || mImsServiceControllerFactoryCompat == controllerFactory) {
                    if (serviceInfo.metaData != null) {
                        if (serviceInfo.metaData.getBoolean(METADATA_MMTEL_FEATURE, false)) {
                            info.addFeatureForAllSlots(ImsFeature.FEATURE_MMTEL);
                            // only allow FEATURE_EMERGENCY_MMTEL if FEATURE_MMTEL is defined.
                            if (serviceInfo.metaData.getBoolean(METADATA_EMERGENCY_MMTEL_FEATURE,
                                    false)) {
                                info.addFeatureForAllSlots(ImsFeature.FEATURE_EMERGENCY_MMTEL);
                            }
                        }
                        if (serviceInfo.metaData.getBoolean(METADATA_RCS_FEATURE, false)) {
                            info.addFeatureForAllSlots(ImsFeature.FEATURE_RCS);
                        }
                    }
                    // Only dynamic query if we are not a compat version of ImsService and the
                    // default service.
                    if (mImsServiceControllerFactoryCompat != controllerFactory
                            && info.getSupportedFeatures().isEmpty()) {
                        // metadata empty, try dynamic query instead
                        info.featureFromMetadata = false;
                    }
                } else {
                    // We are a carrier service and not using the compat version of ImsService.
                    info.featureFromMetadata = false;
                }
                Log.i(TAG, "service name: " + info.name + ", manifest query: "
                        + info.featureFromMetadata);
                // Check manifest permission to be sure that the service declares the correct
                // permissions. Overridden if the METADATA_OVERRIDE_PERM_CHECK metadata is set to
                // true.
                // NOTE: METADATA_OVERRIDE_PERM_CHECK should only be set for testing.
                if (TextUtils.equals(serviceInfo.permission, Manifest.permission.BIND_IMS_SERVICE)
                        || serviceInfo.metaData.getBoolean(METADATA_OVERRIDE_PERM_CHECK, false)) {
                    infos.add(info);
                } else {
                    Log.w(TAG, "ImsService is not protected with BIND_IMS_SERVICE permission: "
                            + info.name);
                }
            }
        }
        return infos;
    }

    // Dump is called on the main thread, since ImsResolver Handler is also handled on main thread,
    // we shouldn't need to worry about concurrent access of private params.
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("ImsResolver:");
        pw.increaseIndent();
        pw.println("Configurations:");
        pw.increaseIndent();
        pw.println("Device:");
        pw.increaseIndent();
        for (Integer i : mDeviceServices.keySet()) {
            pw.println(ImsFeature.FEATURE_LOG_MAP.get(i) + " -> " + mDeviceServices.get(i));
        }
        pw.decreaseIndent();
        pw.println("Carrier: ");
        pw.increaseIndent();
        for (int i = 0; i < mNumSlots; i++) {
            for (int j = 0; j < MmTelFeature.FEATURE_MAX; j++) {
                pw.print("slot=");
                pw.print(i);
                pw.print(", feature=");
                pw.print(ImsFeature.FEATURE_LOG_MAP.getOrDefault(j, "?"));
                pw.println(": ");
                pw.increaseIndent();
                String name = getCarrierConfiguration(i, j);
                pw.println(TextUtils.isEmpty(name) ? "none" : name);
                pw.decreaseIndent();
            }
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
        pw.println("Bound Features:");
        pw.increaseIndent();
        for (int i = 0; i < mNumSlots; i++) {
            for (int j = 0; j < MmTelFeature.FEATURE_MAX; j++) {
                pw.print("slot=");
                pw.print(i);
                pw.print(", feature=");
                pw.print(ImsFeature.FEATURE_LOG_MAP.getOrDefault(j, "?"));
                pw.println(": ");
                pw.increaseIndent();
                ImsServiceController c = getImsServiceController(i, j);
                pw.println(c == null ? "none" : c);
                pw.decreaseIndent();
            }
        }
        pw.decreaseIndent();
        pw.println("Cached ImsServices:");
        pw.increaseIndent();
        for (ImsServiceInfo i : mInstalledServicesCache.values()) {
            pw.println(i);
        }
        pw.decreaseIndent();
        pw.println("Active controllers:");
        pw.increaseIndent();
        for (ImsServiceController c : mActiveControllers.values()) {
            pw.println(c);
            pw.increaseIndent();
            c.dump(pw);
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
        pw.println("Event Log:");
        pw.increaseIndent();
        mEventLog.dump(pw);
        pw.decreaseIndent();
    }
}
