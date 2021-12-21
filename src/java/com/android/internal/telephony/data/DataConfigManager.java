/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringDef;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RegistrantList;
import android.telephony.Annotation.ApnType;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.IndentingPrintWriter;

import com.android.internal.R;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.DataNetworkController.HandoverRule;
import com.android.internal.telephony.data.DataRetryManager.DataRetryRule;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DataConfigManager is the source of all data related configuration from carrier config and
 * resource overlay. DataConfigManager is created to reduce the excessive access to the
 * {@link CarrierConfigManager}. All the data config will be loaded once and stored here.
 */
public class DataConfigManager extends Handler {
    /** Event for carrier config changed. */
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 1;

    /** Indicates the bandwidth estimation source is from the modem. */
    private static final String BANDWIDTH_SOURCE_MODEM_STRING_VALUE = "modem";

    /** Indicates the bandwidth estimation source is from the static carrier config. */
    private static final String BANDWIDTH_SOURCE_CARRIER_CONFIG_STRING_VALUE = "carrier_config";

    /** Indicates the bandwidth estimation source is from {@link LinkBandwidthEstimator}. */
    private static final String BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR_STRING_VALUE =
            "bandwidth_estimator";

    /** Default downlink and uplink bandwidth value in kbps. */
    private static final int DEFAULT_BANDWIDTH = 14;

    /** Network type GPRS. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_GPRS = "GPRS";

    /** Network type EDGE. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_EDGE = "EDGE";

    /** Network type UMTS. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_UMTS = "UMTS";

    /** Network type CDMA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_CDMA = "CDMA";

    /** Network type 1xRTT. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_1xRTT = "1xRTT";

    /** Network type EvDo Rev 0. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_EVDO_0 = "EvDo_0";

    /** Network type EvDo Rev A. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_EVDO_A = "EvDo_A";

    /** Network type HSDPA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_HSDPA = "HSDPA";

    /** Network type HSUPA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_HSUPA = "HSUPA";

    /** Network type HSPA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_HSPA = "HSPA";

    /** Network type EvDo Rev B. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_EVDO_B = "EvDo_B";

    /** Network type eHRPD. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_EHRPD = "eHRPD";

    /** Network type iDEN. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_IDEN = "iDEN";

    /** Network type LTE. Should not be used outside of DataConfigManager. */
    // TODO: Public only for use by DcTracker. This should be private once DcTracker is removed.
    public static final String DATA_CONFIG_NETWORK_TYPE_LTE = "LTE";

    /** Network type HSPA+. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_HSPAP = "HSPA+";

    /** Network type GSM. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_GSM = "GSM";

    /** Network type IWLAN. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_IWLAN = "IWLAN";

    /** Network type TD_SCDMA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_TD_SCDMA = "TD_SCDMA";

    /** Network type LTE_CA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_LTE_CA = "LTE_CA";

    /** Network type NR_NSA. Should not be used outside of DataConfigManager. */
    // TODO: Public only for use by DcTracker. This should be private once DcTracker is removed.
    public static final String DATA_CONFIG_NETWORK_TYPE_NR_NSA = "NR_NSA";

    /** Network type NR_NSA_MMWAVE. Should not be used outside of DataConfigManager. */
    // TODO: Public only for use by DcTracker. This should be private once DcTracker is removed.
    public static final String DATA_CONFIG_NETWORK_TYPE_NR_NSA_MMWAVE = "NR_NSA_MMWAVE";

    /** Network type NR_SA. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_NR_SA = "NR_SA";

    /** Network type NR_SA_MMWAVE. Should not be used outside of DataConfigManager. */
    private static final String DATA_CONFIG_NETWORK_TYPE_NR_SA_MMWAVE = "NR_SA_MMWAVE";

    @StringDef(prefix = {"DATA_CONFIG_NETWORK_TYPE_"}, value = {
            DATA_CONFIG_NETWORK_TYPE_GPRS,
            DATA_CONFIG_NETWORK_TYPE_EDGE,
            DATA_CONFIG_NETWORK_TYPE_UMTS,
            DATA_CONFIG_NETWORK_TYPE_CDMA,
            DATA_CONFIG_NETWORK_TYPE_1xRTT,
            DATA_CONFIG_NETWORK_TYPE_EVDO_0,
            DATA_CONFIG_NETWORK_TYPE_EVDO_A,
            DATA_CONFIG_NETWORK_TYPE_HSDPA,
            DATA_CONFIG_NETWORK_TYPE_HSUPA,
            DATA_CONFIG_NETWORK_TYPE_HSPA,
            DATA_CONFIG_NETWORK_TYPE_EVDO_B,
            DATA_CONFIG_NETWORK_TYPE_EHRPD,
            DATA_CONFIG_NETWORK_TYPE_IDEN,
            DATA_CONFIG_NETWORK_TYPE_LTE,
            DATA_CONFIG_NETWORK_TYPE_HSPAP,
            DATA_CONFIG_NETWORK_TYPE_GSM,
            DATA_CONFIG_NETWORK_TYPE_IWLAN,
            DATA_CONFIG_NETWORK_TYPE_TD_SCDMA,
            DATA_CONFIG_NETWORK_TYPE_LTE_CA,
            DATA_CONFIG_NETWORK_TYPE_NR_NSA,
            DATA_CONFIG_NETWORK_TYPE_NR_NSA_MMWAVE,
            DATA_CONFIG_NETWORK_TYPE_NR_SA,
            DATA_CONFIG_NETWORK_TYPE_NR_SA_MMWAVE,
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface DataConfigNetworkType {}

    private @NonNull final Phone mPhone;
    private @NonNull final String mLogTag;

    /** The registrants list for config update event. */
    private @NonNull final RegistrantList mConfigUpdateRegistrants = new RegistrantList();

    private @NonNull final CarrierConfigManager mCarrierConfigManager;
    private @NonNull PersistableBundle mCarrierConfig = null;
    private @NonNull Resources mResources = null;

    /** The network capability priority map */
    private @NonNull final Map<Integer, Integer> mNetworkCapabilityPriorityMap =
            new ConcurrentHashMap<>();
    /** The data retry rules */
    private @NonNull final List<DataRetryRule> mDataRetryRules = new ArrayList<>();
    /** The metered APN types for home network */
    private @NonNull final @ApnType Set<Integer> mMeteredApnTypes = new HashSet<>();
    /** The metered APN types for roaming network */
    private @NonNull final @ApnType Set<Integer> mRoamingMeteredApnTypes = new HashSet<>();
    /** The network types that only support single data networks */
    private @NonNull final @NetworkType List<Integer> mSingleDataNetworkTypeList =
            new ArrayList<>();
    /** The network types that support temporarily not metered */
    private @NonNull final @DataConfigNetworkType Set<String> mUnmeteredNetworkTypes =
            new HashSet<>();
    /** The network types that support temporarily not metered when roaming */
    private @NonNull final @DataConfigNetworkType Set<String> mRoamingUnmeteredNetworkTypes =
            new HashSet<>();
    /** A map of network types to the downlink and uplink bandwidth values for that network type */
    private @NonNull final @DataConfigNetworkType Map<String, DataNetwork.NetworkBandwidth>
            mBandwidthMap = new ConcurrentHashMap<>();
    /** A map of network types to the TCP buffer sizes for that network type */
    private @NonNull final @DataConfigNetworkType Map<String, String> mTcpBufferSizeMap =
            new ConcurrentHashMap<>();
    /** Rules for handover between IWLAN and cellular network. */
    private @NonNull final List<HandoverRule> mHandoverRuleList = new ArrayList<>();

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataConfigManager(@NonNull Phone phone, @NonNull Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DCM-" + mPhone.getPhoneId();
        log("DataConfigManager created.");

        mCarrierConfigManager = mPhone.getContext().getSystemService(CarrierConfigManager.class);

        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mPhone.getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                    if (mPhone.getPhoneId() == intent.getIntExtra(
                            CarrierConfigManager.EXTRA_SLOT_INDEX,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX)) {
                        sendEmptyMessage(EVENT_CARRIER_CONFIG_CHANGED);
                    }
                }
            }
        }, filter, null, mPhone);

        // Must be called to set mCarrierConfig and mResources to non-null values
        updateConfig();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_CARRIER_CONFIG_CHANGED:
                log("EVENT_CARRIER_CONFIG_CHANGED");
                updateConfig();
                break;
            default:
                loge("Unexpected message " + msg.what);
        }
    }

    /**
     * @return {@code true} if the configuration is carrier specific. {@code false} if the
     * configuration is the default (i.e. SIM not inserted).
     */
    public boolean isConfigCarrierSpecific() {
        return mCarrierConfig.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL);
    }

    /**
     * Update the configuration.
     */
    private void updateConfig() {
        if (mCarrierConfigManager != null) {
            mCarrierConfig = mCarrierConfigManager.getConfigForSubId(mPhone.getSubId());
        }
        if (mCarrierConfig == null) {
            mCarrierConfig = CarrierConfigManager.getDefaultConfig();
        }
        mResources = SubscriptionManager.getResourcesForSubId(mPhone.getContext(),
                mPhone.getSubId());

        updateNetworkCapabilityPriority();
        updateDataRetryRules();
        updateMeteredApnTypes();
        updateSingleDataNetworkTypeList();
        updateUnmeteredNetworkTypes();
        updateBandwidths();
        updateTcpBuffers();
        updateHandoverRules();

        log("Data config updated. Config is " + (isConfigCarrierSpecific() ? "" : "not ")
                + "carrier specific.");

        mConfigUpdateRegistrants.notifyRegistrants();
    }

    /**
     * Update the network capability priority from carrier config.
     */
    private void updateNetworkCapabilityPriority() {
        synchronized (this) {
            mNetworkCapabilityPriorityMap.clear();
            String[] capabilityPriorityStrings = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_TELEPHONY_NETWORK_CAPABILITY_PRIORITIES_STRING_ARRAY);
            if (capabilityPriorityStrings != null) {
                for (String capabilityPriorityString : capabilityPriorityStrings) {
                    capabilityPriorityString = capabilityPriorityString.trim().toUpperCase();
                    String[] tokens = capabilityPriorityString.split(":");
                    if (tokens.length != 2) {
                        loge("Invalid config \"" + capabilityPriorityString + "\"");
                        continue;
                    }

                    int netCap = DataUtils.getNetworkCapabilityFromString(tokens[0]);
                    if (netCap < 0) {
                        loge("Invalid config \"" + capabilityPriorityString + "\"");
                        continue;
                    }

                    int priority = Integer.parseInt(tokens[1]);
                    mNetworkCapabilityPriorityMap.put(netCap, priority);
                }
            }
        }
    }

    /**
     * Get the priority of a network capability.
     *
     * @param capability The network capability
     * @return The priority range from 0 ~ 100. 100 is the highest priority.
     */
    public int getNetworkCapabilityPriority(@NetCapability int capability) {
        if (mNetworkCapabilityPriorityMap.containsKey(capability)) {
            return mNetworkCapabilityPriorityMap.get(capability);
        }
        return 0;
    }

    /**
     * Update the data retry rules from the carrier config.
     */
    private void updateDataRetryRules() {
        synchronized (this) {
            mDataRetryRules.clear();
            String[] dataRetryRulesStrings = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_TELEPHONY_DATA_RETRY_RULES_STRING_ARRAY);
            if (dataRetryRulesStrings != null) {
                for (String ruleString : dataRetryRulesStrings) {
                    try {
                        mDataRetryRules.add(new DataRetryRule(ruleString));
                    } catch (IllegalArgumentException e) {
                        loge("updateDataRetryRules: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * @return The data retry rules from carrier config.
     */
    public @NonNull List<DataRetryRule> getDataRetryRules() {
        return Collections.unmodifiableList(mDataRetryRules);
    }

    /**
     * @return Whether data roaming is enabled by default in carrier config.
     */
    public boolean isDataRoamingEnabledByDefault() {
        return mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_CARRIER_DEFAULT_DATA_ROAMING_ENABLED_BOOL);
    }

    /**
     * Update the home and roaming metered APN types from the carrier config.
     */
    private void updateMeteredApnTypes() {
        synchronized (this) {
            mMeteredApnTypes.clear();
            String[] meteredApnTypes = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS);
            if (meteredApnTypes != null) {
                Arrays.stream(meteredApnTypes)
                        .map(ApnSetting::getApnTypeInt)
                        .forEach(mMeteredApnTypes::add);
            }
            mRoamingMeteredApnTypes.clear();
            String[] roamingMeteredApns = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS);
            if (roamingMeteredApns != null) {
                Arrays.stream(roamingMeteredApns)
                        .map(ApnSetting::getApnTypeInt)
                        .forEach(mRoamingMeteredApnTypes::add);
            }
        }
    }

    /**
     * @return The metered APN types when connected to a home network
     */
    public @NonNull @ApnType Set<Integer> getMeteredApnTypes() {
        return Collections.unmodifiableSet(mMeteredApnTypes);
    }

    /**
     * @return The metered APN types when roaming
     */
    public @NonNull @ApnType Set<Integer> getMeteredApnTypesWhenRoaming() {
        return Collections.unmodifiableSet(mRoamingMeteredApnTypes);
    }

    /**
     * @return Whether to use data activity for RRC detection
     */
    public boolean shouldUseDataActivityForRrcDetection() {
        return mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL);
    }

    /**
     * Update the network types for only single data networks from the carrier config.
     */
    private void updateSingleDataNetworkTypeList() {
        synchronized (this) {
            mSingleDataNetworkTypeList.clear();
            int[] singleDataNetworkTypeList = mCarrierConfig.getIntArray(
                    CarrierConfigManager.KEY_ONLY_SINGLE_DC_ALLOWED_INT_ARRAY);
            if (singleDataNetworkTypeList != null) {
                Arrays.stream(singleDataNetworkTypeList)
                        .forEach(mSingleDataNetworkTypeList::add);
            }
        }
    }

    /**
     * @return The list of {@link NetworkType} that only supports single data networks
     */
    public @NonNull @NetworkType List<Integer> getNetworkTypesOnlySupportSingleDataNetwork() {
        return Collections.unmodifiableList(mSingleDataNetworkTypeList);
    }

    /**
     * @return Whether {@link NetworkCapabilities#NET_CAPABILITY_TEMPORARILY_NOT_METERED}
     * is supported by the carrier.
     */
    public boolean isTempNotMeteredSupportedByCarrier() {
        return mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_NETWORK_TEMP_NOT_METERED_SUPPORTED_BOOL);
    }

    /**
     * Update the network types that are temporarily not metered from the carrier config.
     */
    private void updateUnmeteredNetworkTypes() {
        synchronized (this) {
            mUnmeteredNetworkTypes.clear();
            String[] unmeteredNetworkTypes = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_UNMETERED_NETWORK_TYPES_STRING_ARRAY);
            if (unmeteredNetworkTypes != null) {
                mUnmeteredNetworkTypes.addAll(Arrays.asList(unmeteredNetworkTypes));
            }
            mRoamingUnmeteredNetworkTypes.clear();
            String[] roamingUnmeteredNetworkTypes = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_ROAMING_UNMETERED_NETWORK_TYPES_STRING_ARRAY);
            if (roamingUnmeteredNetworkTypes != null) {
                mRoamingUnmeteredNetworkTypes.addAll(Arrays.asList(roamingUnmeteredNetworkTypes));
            }
        }
    }

    /**
     * Get whether the network type is unmetered from the carrier configs.
     *
     * @param networkType The network type to check meteredness for
     * @param serviceState The service state, used to determine NR state
     * @return Whether the carrier considers the given network type unmetered
     */
    public boolean isNetworkTypeUnmetered(@NetworkType int networkType,
            @NonNull ServiceState serviceState) {
        String dataConfigNetworkType = getDataConfigNetworkType(networkType, serviceState);
        return serviceState.getDataRoaming()
                ? mRoamingUnmeteredNetworkTypes.contains(dataConfigNetworkType)
                : mUnmeteredNetworkTypes.contains(dataConfigNetworkType);
    }

    /**
     * Update the downlink and uplink bandwidth values from the carrier config.
     */
    private void updateBandwidths() {
        synchronized (this) {
            mBandwidthMap.clear();
            String[] bandwidths = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_BANDWIDTH_STRING_ARRAY);
            boolean useLte = mCarrierConfig.getBoolean(CarrierConfigManager
                    .KEY_BANDWIDTH_NR_NSA_USE_LTE_VALUE_FOR_UPLINK_BOOL);
            if (bandwidths != null) {
                for (String bandwidth : bandwidths) {
                    // split1[0] = network type as string
                    // split1[1] = downlink,uplink
                    String[] split1 = bandwidth.split(":");
                    if (split1.length != 2) {
                        loge("Invalid bandwidth: " + bandwidth);
                        continue;
                    }
                    // split2[0] = downlink bandwidth in kbps
                    // split2[1] = uplink bandwidth in kbps
                    String[] split2 = split1[1].split(",");
                    if (split2.length != 2) {
                        loge("Invalid bandwidth values: " + Arrays.toString(split2));
                        continue;
                    }
                    int downlink, uplink;
                    try {
                        downlink = Integer.parseInt(split2[0]);
                        uplink = Integer.parseInt(split2[1]);
                    } catch (NumberFormatException e) {
                        loge("Exception parsing bandwidth values for network type " + split1[0]
                                + ": " + e);
                        continue;
                    }
                    if (useLte && split1[0].startsWith("NR")) {
                        // We can get it directly from mBandwidthMap because LTE is defined before
                        // the NR values in CarrierConfigManager#KEY_BANDWIDTH_STRING_ARRAY.
                        uplink = mBandwidthMap.get(DATA_CONFIG_NETWORK_TYPE_LTE)
                                .uplinkBandwidthKbps;
                    }
                    mBandwidthMap.put(split1[0],
                            new DataNetwork.NetworkBandwidth(downlink, uplink));
                }
            }
        }
    }

    /**
     * Get the bandwidth estimate from the carrier config.
     *
     * @param networkType The network type to get the bandwidth for
     * @param serviceState The service state, used to determine NR state
     * @return The pre-configured bandwidth estimate from carrier config.
     */
    public @NonNull DataNetwork.NetworkBandwidth getBandwidthForNetworkType(
            @NetworkType int networkType, @NonNull ServiceState serviceState) {
        DataNetwork.NetworkBandwidth bandwidth = mBandwidthMap.get(
                getDataConfigNetworkType(networkType, serviceState));
        if (bandwidth != null) {
            return bandwidth;
        }
        return new DataNetwork.NetworkBandwidth(DEFAULT_BANDWIDTH, DEFAULT_BANDWIDTH);
    }

    /**
     * @return Whether data throttling should be reset when the TAC changes from the carrier config.
     */
    public boolean shouldResetDataThrottlingWhenTacChanges() {
        return mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_UNTHROTTLE_DATA_RETRY_WHEN_TAC_CHANGES_BOOL);
    }

    /**
     * @return The data service package override string from the carrier config.
     */
    public String getDataServicePackageName() {
        return mCarrierConfig.getString(
                CarrierConfigManager.KEY_CARRIER_DATA_SERVICE_WWAN_PACKAGE_OVERRIDE_STRING);
    }

    /**
     * @return The default MTU value in bytes from the carrier config.
     */
    public int getDefaultMtu() {
        return mCarrierConfig.getInt(CarrierConfigManager.KEY_DEFAULT_MTU_INT);
    }

    /**
     * Update the TCP buffer sizes from the carrier config.
     */
    private void updateTcpBuffers() {
        synchronized (this) {
            mTcpBufferSizeMap.clear();
            String[] buffers = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_TCP_BUFFERS_STRING_ARRAY);
            if (buffers != null) {
                for (String buffer : buffers) {
                    // split[0] = network type as string
                    // split[1] = rmem_min,rmem_def,rmem_max,wmem_min,wmem_def,wmem_max
                    String[] split = buffer.split(":");
                    if (split.length != 2) {
                        loge("Invalid TCP buffer sizes: " + buffer);
                        continue;
                    }
                    if (split[1].split(",").length != 6) {
                        loge("Invalid TCP buffer sizes for " + split[0] + ": " + split[1]);
                        continue;
                    }
                    mTcpBufferSizeMap.put(split[0], split[1]);
                }
            }
        }
    }

    /**
     * Get the TCP config string, used by {@link LinkProperties#setTcpBufferSizes(String)}.
     * The config string will have the following form, with values in bytes:
     * "read_min,read_default,read_max,write_min,write_default,write_max"
     *
     * @param networkType The network type. Note that {@link TelephonyManager#NETWORK_TYPE_LTE_CA}
     *                    can be used for LTE CA even though it's not a radio access technology.
     * @param serviceState The service state, used to determine NR state.
     * @return The TCP configuration string for the given network type or null if unavailable.
     */
    public @Nullable String getTcpConfigString(@NetworkType int networkType,
            @NonNull ServiceState serviceState) {
        return mTcpBufferSizeMap.get(getDataConfigNetworkType(networkType, serviceState));
    }

    /**
     * @return The delay in millisecond for IMS graceful tear down. If IMS/RCS de-registration
     * does not complete within the window, the data network will be torn down after timeout.
     */
    public long getImsDeregistrationDelay() {
        return mResources.getInteger(R.integer.config_delay_for_ims_dereg_millis);
    }

    /**
     * @return {@code true} if PDN should persist when IWLAN data service restarted/crashed.
     * {@code false} will cause all data networks on IWLAN torn down if IWLAN data service crashes.
     */
    public boolean shouldPersistIwlanDataNetworksWhenDataServiceRestarted() {
        return mResources.getBoolean(com.android.internal.R.bool
                .config_wlan_data_service_conn_persistence_on_restart);
    }

    /**
     * @return The bandwidth estimation source.
     */
    public @DataNetwork.BandwidthEstimationSource int getBandwidthEstimateSource() {
        String source = mResources.getString(
                com.android.internal.R.string.config_bandwidthEstimateSource);
        switch (source) {
            case BANDWIDTH_SOURCE_MODEM_STRING_VALUE:
                return DataNetwork.BANDWIDTH_SOURCE_MODEM;
            case BANDWIDTH_SOURCE_CARRIER_CONFIG_STRING_VALUE:
                return DataNetwork.BANDWIDTH_SOURCE_CARRIER_CONFIG;
            case BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR_STRING_VALUE:
                return DataNetwork.BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR;
            default:
                loge("Invalid bandwidth estimation source config: " + source);
                return DataNetwork.BANDWIDTH_SOURCE_UNKNOWN;
        }
    }

    /**
     * Get the data config network type based on the given network type and service state
     *
     * @param networkType The network type
     * @param serviceState The service state, used to determine NR state
     * @return The equivalent data config network type
     */
    public static @NonNull @DataConfigNetworkType String getDataConfigNetworkType(
            @NetworkType int networkType, @NonNull ServiceState serviceState) {
        // TODO: Make method private once DataConnection is removed
        if ((networkType == TelephonyManager.NETWORK_TYPE_LTE
                || networkType == TelephonyManager.NETWORK_TYPE_LTE_CA)
                && (serviceState.getNrState() == NetworkRegistrationInfo.NR_STATE_CONNECTED)) {
            return serviceState.getNrFrequencyRange() == ServiceState.FREQUENCY_RANGE_MMWAVE
                    ? DATA_CONFIG_NETWORK_TYPE_NR_NSA_MMWAVE : DATA_CONFIG_NETWORK_TYPE_NR_NSA;
        } else if (networkType == TelephonyManager.NETWORK_TYPE_NR
                && serviceState.getNrFrequencyRange() == ServiceState.FREQUENCY_RANGE_MMWAVE) {
            return DATA_CONFIG_NETWORK_TYPE_NR_SA_MMWAVE;
        }
        return networkTypeToDataConfigNetworkType(networkType);
    }

    /** Update handover rules from carrier config. */
    private void updateHandoverRules() {
        synchronized (this) {
            mHandoverRuleList.clear();
            String[] handoverRulesStrings = mCarrierConfig.getStringArray(
                    CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY);
            if (handoverRulesStrings != null) {
                for (String ruleString : handoverRulesStrings) {
                    try {
                        mHandoverRuleList.add(new HandoverRule(ruleString));
                    } catch (IllegalArgumentException e) {
                        loge("updateHandoverRules: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * @return Get rules for handover between IWLAN and cellular networks.
     *
     * @see CarrierConfigManager#KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY
     */
    public @NonNull List<HandoverRule> getHandoverRules() {
        return Collections.unmodifiableList(mHandoverRuleList);
    }

    /**
     * Get the data config network type for the given network type
     *
     * @param networkType The network type
     * @return The equivalent data config network type
     */
    private static @NonNull @DataConfigNetworkType String networkTypeToDataConfigNetworkType(
            @NetworkType int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return DATA_CONFIG_NETWORK_TYPE_GPRS;
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return DATA_CONFIG_NETWORK_TYPE_EDGE;
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return DATA_CONFIG_NETWORK_TYPE_UMTS;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return DATA_CONFIG_NETWORK_TYPE_HSDPA;
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return DATA_CONFIG_NETWORK_TYPE_HSUPA;
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return DATA_CONFIG_NETWORK_TYPE_HSPA;
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return DATA_CONFIG_NETWORK_TYPE_CDMA;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return DATA_CONFIG_NETWORK_TYPE_EVDO_0;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return DATA_CONFIG_NETWORK_TYPE_EVDO_A;
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return DATA_CONFIG_NETWORK_TYPE_EVDO_B;
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return DATA_CONFIG_NETWORK_TYPE_1xRTT;
            case TelephonyManager.NETWORK_TYPE_LTE:
                return DATA_CONFIG_NETWORK_TYPE_LTE;
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return DATA_CONFIG_NETWORK_TYPE_EHRPD;
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return DATA_CONFIG_NETWORK_TYPE_IDEN;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return DATA_CONFIG_NETWORK_TYPE_HSPAP;
            case TelephonyManager.NETWORK_TYPE_GSM:
                return DATA_CONFIG_NETWORK_TYPE_GSM;
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return DATA_CONFIG_NETWORK_TYPE_TD_SCDMA;
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return DATA_CONFIG_NETWORK_TYPE_IWLAN;
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                return DATA_CONFIG_NETWORK_TYPE_LTE_CA;
            case TelephonyManager.NETWORK_TYPE_NR:
                return DATA_CONFIG_NETWORK_TYPE_NR_SA;
            default:
                return "";
        }
    }

    /**
     * Registration point for subscription info ready
     *
     * @param h handler to notify
     * @param what what code of message when delivered
     */
    public void registerForConfigUpdate(Handler h, int what) {
        mConfigUpdateRegistrants.addUnique(h, what, null);
    }

    /**
     *
     * @param h The original handler passed in {@link #registerForConfigUpdate(Handler, int)}.
     */
    public void unregisterForConfigUpdate(Handler h) {
        mConfigUpdateRegistrants.remove(h);
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(mLogTag, s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(mLogTag, s);
    }

    /**
     * Dump the state of DataConfigManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataConfigManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("isConfigCarrierSpecific=" + isConfigCarrierSpecific());
        pw.println("Network capability priority:");
        pw.increaseIndent();
        mNetworkCapabilityPriorityMap.forEach((key, value) -> pw.print(
                DataUtils.networkCapabilityToString(key) + ":" + value + " "));
        pw.decreaseIndent();
        pw.println();
        pw.println("Data retry rules:");
        pw.increaseIndent();
        mDataRetryRules.forEach(pw::println);
        pw.decreaseIndent();
        pw.println("Metered APN types=" + mMeteredApnTypes.stream()
                .map(ApnSetting::getApnTypeString).collect(Collectors.joining(",")));
        pw.println("Roaming metered APN types=" + mRoamingMeteredApnTypes.stream()
                .map(ApnSetting::getApnTypeString).collect(Collectors.joining(",")));
        pw.println("Single data network types=" + mSingleDataNetworkTypeList.stream()
                .map(TelephonyManager::getNetworkTypeName).collect(Collectors.joining(",")));
        pw.println("Unmetered network types=" + String.join(",", mUnmeteredNetworkTypes));
        pw.println("Roaming unmetered network types="
                + String.join(",", mRoamingUnmeteredNetworkTypes));
        pw.println("Bandwidths:");
        pw.increaseIndent();
        mBandwidthMap.forEach((key, value) -> pw.println(key + ":" + value));
        pw.decreaseIndent();
        pw.println("shouldUseDataActivityForRrcDetection="
                + shouldUseDataActivityForRrcDetection());
        pw.println("isTempNotMeteredSupportedByCarrier=" + isTempNotMeteredSupportedByCarrier());
        pw.println("shouldResetDataThrottlingWhenTacChanges="
                + shouldResetDataThrottlingWhenTacChanges());
        pw.println("Data service package name=" + getDataServicePackageName());
        pw.println("Default MTU=" + getDefaultMtu());
        pw.println("TCP buffer sizes:");
        pw.increaseIndent();
        mTcpBufferSizeMap.forEach((key, value) -> pw.println(key + ":" + value));
        pw.decreaseIndent();
        pw.println("getImsDeregistrationDelay=" + getImsDeregistrationDelay());
        pw.println("shouldPersistIwlanDataNetworksWhenDataServiceRestarted="
                + shouldPersistIwlanDataNetworksWhenDataServiceRestarted());
        pw.println("Bandwidth estimation source=" + mResources.getString(
                com.android.internal.R.string.config_bandwidthEstimateSource));
        pw.decreaseIndent();
    }
}
