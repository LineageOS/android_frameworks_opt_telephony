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
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.dataconnection.AccessNetworksManager.QualifiedNetworks;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the transport manager which manages available transports (i.e. WWAN or
 * WLAN)and determine the correct transport for {@link TelephonyNetworkFactory} to handle the data
 * requests.
 */
public class TransportManager extends Handler {
    private static final String TAG = TransportManager.class.getSimpleName();

    private static final boolean DBG = true;

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

    private final List<Integer> mAvailableTransports = new ArrayList<>();

    private final AccessNetworksManager mAccessNetworksManager;

    public TransportManager(Phone phone) {
        mPhone = phone;
        mAccessNetworksManager = new AccessNetworksManager(phone);

        mAccessNetworksManager.registerForQualifiedNetworksChanged(this,
                EVENT_QUALIFIED_NETWORKS_CHANGED);

        // WWAN should be always available.
        mAvailableTransports.add(TransportType.WWAN);

        // TODO: Add more logic to check whether we should add WLAN as a transport. For now, if
        // the device operate in non-legacy mode, then we always add WLAN as a transport.
        if (!isInLegacyMode()) {
            mAvailableTransports.add(TransportType.WLAN);
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

    private synchronized void updateAvailableNetworks(List<QualifiedNetworks> networks) {
        log("updateAvailableNetworks: " + networks);
        //TODO: Update available networks and transports.
    }

    public synchronized List<Integer> getAvailableTransports() {
        return new ArrayList<>(mAvailableTransports);
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
        pw.print("mAvailableTransports=");
        List<String> transportsStrings = new ArrayList<>();
        for (int i = 0; i < mAvailableTransports.size(); i++) {
            transportsStrings.add(TransportType.toString(mAvailableTransports.get(i)));
        }
        pw.println("[" + TextUtils.join(",", transportsStrings) + "]");
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
