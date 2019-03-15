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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringDef;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.data.ApnSetting;
import android.telephony.data.ApnSetting.ApnType;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.RIL;
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
 * WLAN) and determine the correct transport for {@link TelephonyNetworkFactory} to handle the data
 * requests.
 *
 * The device can operate in the following modes, which is stored in the system properties
 * ro.telephony.iwlan_operation_mode. If the system properties is missing, then it's tied to
 * IRadio version. For 1.4 or above, it's legacy mode. For 1.3 or below, it's
 *
 * Legacy mode:
 *      Frameworks send all data requests to the default data service, which is the cellular data
 *      service. IWLAN should be still reported as a RAT on cellular network service.
 *
 * AP-assisted mode:
 *      IWLAN is handled by IWLAN data service extending {@link android.telephony.data.DataService},
 *      IWLAN network service extending {@link android.telephony.NetworkService}, and qualified
 *      network service extending {@link android.telephony.data.QualifiedNetworksService}.
 *
 *      The following settings for service package name need to be configured properly for
 *      frameworks to bind.
 *
 *      Package name of data service:
 *          The resource overlay 'config_wwan_data_service_package' or,
 *          the carrier config
 *          {@link CarrierConfigManager#KEY_CARRIER_DATA_SERVICE_WLAN_PACKAGE_OVERRIDE_STRING}.
 *          The carrier config takes precedence over the resource overlay if both exist.
 *
 *      Package name of network service
 *          The resource overlay 'config_wwan_network_service_package' or
 *          the carrier config
 *          {@link CarrierConfigManager#KEY_CARRIER_NETWORK_SERVICE_WLAN_PACKAGE_OVERRIDE_STRING}.
 *          The carrier config takes precedence over the resource overlay if both exist.
 *
 *      Package name of qualified network service
 *          The resource overlay 'config_qualified_networks_service_package' or
 *          the carrier config
 *          {@link CarrierConfigManager#
 *          KEY_CARRIER_QUALIFIED_NETWORKS_SERVICE_PACKAGE_OVERRIDE_STRING}.
 *          The carrier config takes precedence over the resource overlay if both exist.
 */
public class TransportManager extends Handler {
    private static final String TAG = TransportManager.class.getSimpleName();

    // Key is the access network, value is the transport.
    private static final Map<Integer, Integer> ACCESS_NETWORK_TRANSPORT_TYPE_MAP;

    static {
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP = new HashMap<>();
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.UNKNOWN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.GERAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.UTRAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.EUTRAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.CDMA2000,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        ACCESS_NETWORK_TRANSPORT_TYPE_MAP.put(AccessNetworkType.IWLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    private static final int EVENT_QUALIFIED_NETWORKS_CHANGED = 1;

    public static final String SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE =
            "ro.telephony.iwlan_operation_mode";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef(prefix = {"IWLAN_OPERATION_MODE_"},
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
    public static final String IWLAN_OPERATION_MODE_DEFAULT = "default";

    /**
     * IWLAN legacy mode. IWLAN is completely handled by the modem, and when the device is on
     * IWLAN, modem reports IWLAN as a RAT.
     */
    public static final String IWLAN_OPERATION_MODE_LEGACY = "legacy";

    /**
     * IWLAN application processor assisted mode. IWLAN is handled by the bound IWLAN data service
     * and network service separately.
     */
    public static final String IWLAN_OPERATION_MODE_AP_ASSISTED = "AP-assisted";

    private final Phone mPhone;

    /** The available transports. Must be one or more of AccessNetworkConstants.TransportType.XXX */
    private final int[] mAvailableTransports;

    @Nullable
    private AccessNetworksManager mAccessNetworksManager;

    /**
     * Available networks. The key is the APN type, and the value is the available network list in
     * the preferred order.
     */
    private final Map<Integer, int[]> mCurrentAvailableNetworks;

    /**
     * The current transport of the APN type. The key is the APN type, and the value is the
     * transport.
     */
    private final Map<Integer, Integer> mCurrentTransports;

    /**
     * The registrants for listening data handover needed events.
     */
    private final RegistrantList mHandoverNeededEventRegistrants;

    /**
     * Handover parameters
     */
    @VisibleForTesting
    public static final class HandoverParams {
        public final @ApnType int apnType;
        public final int targetTransport;
        HandoverParams(int apnType, int targetTransport) {
            this.apnType = apnType;
            this.targetTransport = targetTransport;
        }
    }

    public TransportManager(Phone phone) {
        mPhone = phone;
        mCurrentAvailableNetworks = new ConcurrentHashMap<>();
        mCurrentTransports = new ConcurrentHashMap<>();
        mHandoverNeededEventRegistrants = new RegistrantList();

        if (isInLegacyMode()) {
            log("operates in legacy mode.");
            // For legacy mode, WWAN is the only transport to handle all data connections, even
            // the IWLAN ones.
            mAvailableTransports = new int[]{AccessNetworkConstants.TRANSPORT_TYPE_WWAN};
        } else {
            log("operates in AP-assisted mode.");
            mAccessNetworksManager = new AccessNetworksManager(phone);
            mAccessNetworksManager.registerForQualifiedNetworksChanged(this,
                    EVENT_QUALIFIED_NETWORKS_CHANGED);
            mAvailableTransports = new int[]{AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN};
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

    private boolean isHandoverNeeded(QualifiedNetworks newNetworks) {
        int apnType = newNetworks.apnType;
        int[] newNetworkList = newNetworks.qualifiedNetworks;
        int[] currentNetworkList = mCurrentAvailableNetworks.get(apnType);

        // If the current network list is empty, but the new network list is not, then we can
        // directly setup data on the new network. If the current network list is not empty, but
        // the new network is, then we can tear down the data directly. Therefore if one of the
        // list is empty, then we don't need to do handover.
        if (ArrayUtils.isEmpty(newNetworkList) || ArrayUtils.isEmpty(currentNetworkList)) {
            return false;
        }

        // The list is networks in the preferred order. For now we only pick the first element
        // because it's the most preferred. In the future we should also consider the rest in the
        // list, for example, the first one violates carrier/user policy.
        return !ACCESS_NETWORK_TRANSPORT_TYPE_MAP.get(newNetworkList[0]).equals(
                ACCESS_NETWORK_TRANSPORT_TYPE_MAP.get(currentNetworkList[0]));
    }

    private static boolean areNetworksValid(QualifiedNetworks networks) {
        if (networks.qualifiedNetworks == null) {
            return false;
        }
        for (int network : networks.qualifiedNetworks) {
            if (!ACCESS_NETWORK_TRANSPORT_TYPE_MAP.containsKey(network)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Set the current transport of apn type.
     *
     * @apnType The APN type
     * @transport The transport. Must be WWAN or WLAN.
     */
    public void setCurrentTransport(@ApnType int apnType, int transport) {
        mCurrentTransports.put(apnType, transport);
    }

    private synchronized void updateAvailableNetworks(List<QualifiedNetworks> networksList) {
        log("updateAvailableNetworks: " + networksList);
        for (QualifiedNetworks networks : networksList) {
            if (areNetworksValid(networks)) {
                if (isHandoverNeeded(networks)) {
                    mCurrentAvailableNetworks.put(networks.apnType, networks.qualifiedNetworks);
                    // If handover is needed, perform the handover works. For now we only pick the
                    // first element because it's the most preferred. In the future we should also
                    // consider the rest in the list, for example, the first one violates
                    // carrier/user policy.
                    int targetTransport = ACCESS_NETWORK_TRANSPORT_TYPE_MAP.get(
                            networks.qualifiedNetworks[0]);
                    log("Handover needed for APN type: "
                            + ApnSetting.getApnTypeString(networks.apnType) + ", target transport: "
                            + AccessNetworkConstants.transportTypeToString(targetTransport));
                    mHandoverNeededEventRegistrants.notifyResult(
                            new HandoverParams(networks.apnType, targetTransport));

                } else {
                    // If handover is not needed, immediately update the available networks and
                    // transport.
                    log("Handover not needed for APN type: "
                            + ApnSetting.getApnTypeString(networks.apnType));
                    mCurrentAvailableNetworks.put(networks.apnType, networks.qualifiedNetworks);
                    int transport = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
                    if (!ArrayUtils.isEmpty(networks.qualifiedNetworks)
                            && ACCESS_NETWORK_TRANSPORT_TYPE_MAP.containsKey(
                                    networks.qualifiedNetworks[0])) {
                        // For now we only pick the first element because it's the most preferred.
                        // In the future we should also consider the rest in the list, for example,
                        // the first one violates carrier/user policy.
                        transport = ACCESS_NETWORK_TRANSPORT_TYPE_MAP
                                .get(networks.qualifiedNetworks[0]);
                    }
                    setCurrentTransport(networks.apnType, transport);
                }
            }
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
     * @return {@code true} if the device operates in legacy mode, otherwise {@code false}.
     */
    public boolean isInLegacyMode() {
        // Get IWLAN operation mode from the system property. If the system property is missing or
        // misconfiged the default behavior is tied to the IRadio version. For 1.4 or above, it's
        // AP-assisted mode, for 1.3 or below, it's legacy mode. For IRadio 1.3 or below, no matter
        // what the configuration is, it will always be legacy mode.
        String mode = SystemProperties.get(SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE);

        return mode.equals(IWLAN_OPERATION_MODE_LEGACY)
                || mPhone.getHalVersion().less(RIL.RADIO_HAL_VERSION_1_4);
    }

    /**
     * Get the transport based on the APN type.
     *
     * @param apnType APN type
     * @return The transport type
     */
    public int getCurrentTransport(@ApnType int apnType) {
        // In legacy mode, always route to cellular.
        if (isInLegacyMode()) {
            return AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        }

        // If we can't find the corresponding transport, always route to cellular.
        return mCurrentTransports.get(apnType) == null
                ? AccessNetworkConstants.TRANSPORT_TYPE_WWAN : mCurrentTransports.get(apnType);
    }

    /**
     * Register for data handover needed event
     *
     * @param h The handler of the event
     * @param what The id of the event
     */
    public void registerForHandoverNeededEvent(Handler h, int what) {
        if (h != null) {
            mHandoverNeededEventRegistrants.addUnique(h, what, null);
        }
    }

    /**
     * Unregister for data handover needed event
     *
     * @param h The handler
     */
    public void unregisterForHandoverNeededEvent(Handler h) {
        mHandoverNeededEventRegistrants.remove(h);
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
                .mapToObj(type -> AccessNetworkConstants.transportTypeToString(type))
                .collect(Collectors.joining(",")) + "]");
        pw.println("mCurrentAvailableNetworks=" + mCurrentAvailableNetworks);
        pw.println("mCurrentTransports=" + mCurrentTransports);
        pw.println("isInLegacy=" + isInLegacyMode());
        pw.println("IWLAN operation mode="
                + SystemProperties.get(SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE));
        if (mAccessNetworksManager != null) {
            mAccessNetworksManager.dump(fd, pw, args);
        }
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
