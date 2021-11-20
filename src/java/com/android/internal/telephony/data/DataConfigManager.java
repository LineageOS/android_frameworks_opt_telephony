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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RegistrantList;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.IndentingPrintWriter;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.DataRetryManager.DataRetryRule;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DataConfigManager is the source of all data related configuration from carrier config and
 * resource overlay. DataConfigManager is created to reduce the excessive access to the
 * {@link CarrierConfigManager}. All the data config will be loaded once and store here.
 */
public class DataConfigManager extends Handler {
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 1;

    private final Phone mPhone;
    private final String mLogTag;

    /** The registrants list for config update event. */
    private final RegistrantList mConfigUpdateRegistrants = new RegistrantList();

    private @NonNull final CarrierConfigManager mCarrierConfigManager;

    /** The network capability priority map */
    private final Map<Integer, Integer> mNetworkCapabilityPriorityMap = new ConcurrentHashMap<>();

    private final List<DataRetryRule> mDataRetryRules = new ArrayList<>();

    private @Nullable PersistableBundle mCarrierConfig = null;
    private @Nullable Resources mResources = null;

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

        if (mCarrierConfigManager != null) {
            mCarrierConfig = mCarrierConfigManager.getConfigForSubId(mPhone.getSubId());
        }
        mResources = SubscriptionManager.getResourcesForSubId(mPhone.getContext(),
                mPhone.getSubId());
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
        return mCarrierConfig != null
                && mCarrierConfig.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL);
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

        log("Data config updated. Config is " + (isConfigCarrierSpecific() ? "" : "not ")
                + "carrier specific.");

        mConfigUpdateRegistrants.notifyRegistrants();
    }

    /**
     * Update the network capability priority from carrier config.
     */
    private void updateNetworkCapabilityPriority() {
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
        mDataRetryRules.clear();
        String[] dataRetryRulesStrings = mCarrierConfig.getStringArray(
                CarrierConfigManager.KEY_TELEPHONY_DATA_RETRY_RULES_STRING_ARRAY);
        if (dataRetryRulesStrings != null) {
            Arrays.stream(dataRetryRulesStrings)
                    .map(DataRetryRule::new)
                    .forEach(mDataRetryRules::add);
        }
    }

    /**
     * @return The data retry rules from carrier config.
     */
    public @NonNull List<DataRetryRule> getDataRetryRules() {
        return Collections.unmodifiableList(mDataRetryRules);
    }

    /**
     * Get the TCP config string, which will be used for
     * {@link android.net.LinkProperties#setTcpBufferSizes(String)}
     *
     * @param networkType The network type. Note that {@link TelephonyManager#NETWORK_TYPE_NR} is
     * used for both 5G SA and NSA case. {@link TelephonyManager#NETWORK_TYPE_LTE_CA} can be used
     * for LTE CA even though it's not really a radio access technology.
     *
     * @return The TCP buffer configuration string.
     */
    public @NonNull String getTcpConfigString(@NetworkType int networkType) {
        // TODO: Move all TCP_BUFFER_SIZES_XXX from DataConnection to here.
        return null;
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
        pw.println("Network capability priority:");
        pw.increaseIndent();
        for (Map.Entry<Integer, Integer> entry : mNetworkCapabilityPriorityMap.entrySet()) {
            pw.print(DataUtils.networkCapabilityToString(entry.getKey()) + ":"
                    + entry.getValue() + " ");
        }
        pw.decreaseIndent();
        pw.println();
        pw.println("Data retry rules:");
        pw.increaseIndent();
        for (DataRetryRule rule : mDataRetryRules) {
            pw.println(rule);
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
