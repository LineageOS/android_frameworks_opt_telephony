/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.data.ApnSetting.ApnType;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.dataconnection.AccessNetworksManager.QualifiedNetworks;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This class represents the transport manager which manages available transports (i.e. WWAN or
 * WLAN)and determine the correct transport for {@link TelephonyNetworkFactory} to handle the data
 * requests.
 */
public class TransportManager extends Handler {
    private static final String TAG = TransportManager.class.getSimpleName();

    private static final Map<Integer, Integer> ACCESS_NETWORK_TRANSPORT_TYPE_MAP;

    static {
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP = new HashMap<>();
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.UNKNOWN, TransportType.WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.GERAN, TransportType.WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.UTRAN, TransportType.WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.EUTRAN, TransportType.WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.CDMA2000, TransportType.WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.IWLAN, TransportType.WLAN);
    }

    private static final int EVENT_QUALIFIED_NETWORKS_CHANGED = 1;

    public static final String SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE =
            "ro.telephony.iwlan_operation_mode";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"IWLAN_OPERATION_MODE_"},
            value = {
                    IWLAN_OPERATION_MODE_DEFAULT,
                    IWLAN_OPERATION_MODE_LEGACY,
                    IWLAN_OPERATION_MODE_AP_ASSISTED})
    public @interface IwlanOperationMode {}

    /**
     * IWLAN default mode. On device that has IRadio 1.3 or above, it means
     * {@link #IWLAN_OPERATION_MODE_AP_ASSISTED}. On device that has IRadio 1.2 or below, it means
     * {@link #IWLAN_OPERATION_MODE_LEGACY}.
     */
    public static final int IWLAN_OPERATION_MODE_DEFAULT = 0;

    /**
     * IWLAN legacy mode. IWLAN is completely handled by the modem, and when the device is on
     * IWLAN, modem reports IWLAN as a RAT.
     */
    public static final int IWLAN_OPERATION_MODE_LEGACY = 1;

    /**
     * IWLAN application processor assisted mode. IWLAN is handled by the bound IWLAN data service
     * and network service separately.
     */
    public static final int IWLAN_OPERATION_MODE_AP_ASSISTED = 2;

    private final Phone mPhone;

    /** The available transports. Must be one or more of AccessNetworkConstants.TransportType.XXX */
    private final int[] mAvailableTransports;

    private final AccessNetworksManager mAccessNetworksManager;

    /**
     * Available networks. The key is the APN type, and the value is the available network list in
     * the preferred order.
     */
    private final Map<Integer, int[]> mCurrentAvailableNetworks;

    public TransportManager(Phone phone) {
        mPhone = phone;
        mAccessNetworksManager = new AccessNetworksManager(phone);

        mAccessNetworksManager.registerForQualifiedNetworksChanged(this,
                EVENT_QUALIFIED_NETWORKS_CHANGED);

        mCurrentAvailableNetworks = new ConcurrentHashMap<>();

        if (isInLegacyMode()) {
            // For legacy mode, WWAN is the only transport to handle all data connections, even
            // the IWLAN ones.
            mAvailableTransports = new int[]{TransportType.WWAN};
        } else {
            mAvailableTransports = new int[]{TransportType.WWAN, TransportType.WLAN};
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_QUALIFIED_NETWORKS_CHANGED:
                AsyncResult ar = (AsyncResult) msg.obj;
                List<QualifiedNetworks> networks = (List<QualifiedNetworks>) ar.result;
                updateAvailableNetworks(networks);
                break;
            default:
                loge("Unexpected event " + msg.what);
                break;
        }
    }

    private synchronized void updateAvailableNetworks(List<QualifiedNetworks> networksList) {
        log("updateAvailableNetworks: " + networksList);
        for (QualifiedNetworks networks : networksList) {
            mCurrentAvailableNetworks.put(networks.apnType, networks.qualifiedNetworks);
        }
    }

    /**
     * @return The available transports. Note that on legacy devices, the only available transport
     * would be WWAN only. If the device is configured as AP-assisted mode, the available transport
     * will always be WWAN and WLAN (even if the device is not camped on IWLAN).
     * See {@link #isInLegacyMode()} for mode details.
     */
    public synchronized @NonNull int[] getAvailableTransports() {
        return mAvailableTransports;
    }

    /**
     * @return True if in IWLAN legacy mode. Operating in legacy mode means telephony will send
     * all data requests to the default data service, which is the cellular data service.
     * AP-assisted mode requires properly configuring the resource overlay
     * 'config_wwan_data_service_package' (or the carrier config
     * {@link CarrierConfigManager#KEY_CARRIER_DATA_SERVICE_WLAN_PACKAGE_OVERRIDE_STRING }) to
     * the IWLAN data service package, 'config_wwan_network_service_package' (or the carrier config
     * {@link CarrierConfigManager#KEY_CARRIER_NETWORK_SERVICE_WLAN_PACKAGE_OVERRIDE_STRING })
     * to the IWLAN network service package, and 'config_qualified_networks_service_package' (or the
     * carrier config
     * {@link CarrierConfigManager#KEY_CARRIER_QUALIFIED_NETWORKS_SERVICE_PACKAGE_OVERRIDE_STRING})
     * to the qualified networks service package.
     */
    public boolean isInLegacyMode() {
        return (mPhone.mCi.getIwlanOperationMode() == IWLAN_OPERATION_MODE_LEGACY);
    }

    /**
     * Get the transport based on the APN type
     *
     * @param apnType APN type
     * @return The transport type
     */
    public int getCurrentTransport(@ApnType int apnType) {
        // In legacy mode, always route to cellular.
        if (isInLegacyMode()) {
            return TransportType.WWAN;
        }

        // If we can't find the available networks, always route to cellular.
        if (!mCurrentAvailableNetworks.containsKey(apnType)) {
            return TransportType.WWAN;
        }

        int[] availableNetworks = mCurrentAvailableNetworks.get(apnType);

        // If the available networks list is empty, route to cellular.
        if (ArrayUtils.isEmpty(availableNetworks)) {
            return TransportType.WWAN;
        }

        // TODO: For now we choose the first network because it's the most preferred one. In the
        // the future we should validate it to make sure this network does not violate carrier
        // preference and user preference.
        return ACCESS_NETWORK_TRANSPORT_TYPE_MAP.get(availableNetworks[0]);
    }

    /**
     * Dump the state of transport manager
     *
     * @param fd File descriptor
     * @param printwriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printwriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printwriter, "  ");
        pw.println("TransportManager:");
        pw.increaseIndent();
        pw.println("mAvailableTransports=[" + Arrays.stream(mAvailableTransports)
                .mapToObj(type -> TransportType.toString(type))
                .collect(Collectors.joining(",")) + "]");
        pw.println("isInLegacy=" + isInLegacyMode());
        pw.println("IWLAN operation mode=" + mPhone.mCi.getIwlanOperationMode());
        mAccessNetworksManager.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.flush();
    }

    private void log(String s) {
        Rlog.d(TAG, s);
    }

    private void loge(String s) {
        Rlog.e(TAG, s);
    }
}
