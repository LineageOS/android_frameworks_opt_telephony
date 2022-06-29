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

package com.android.internal.telephony;

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.GERAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.IWLAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.NGRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_ACR;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_ALL;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BAIC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BAOC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BIC_ROAM;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BIL;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BOIC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BOIC_EXHC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_IBS;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_OBS;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_ALL;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_ALL_CONDITONAL_FORWARDING;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFB;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFNRC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFNRY;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFU;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CW;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIP;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIR;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIP;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIR;

import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAICr;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOICxH;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_ALL;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MO;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MT;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.telephony.Rlog;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The cache of the carrier configuration
 */
public class SsDomainController {
    private static final String LOG_TAG = "SsDomainController";

    /**
     * A Helper class to carry the information indicating Ut is available or not.
     */
    public static class SuppServiceRoutingInfo {
        private final boolean mUseSsOverUt;
        private final boolean mSupportsCsfb;

        public SuppServiceRoutingInfo(boolean useSsOverUt,
                boolean isUtEnabled, boolean supportsCsfb) {
            if (useSsOverUt) {
                mUseSsOverUt = isUtEnabled;
                mSupportsCsfb = supportsCsfb;
            } else {
                mUseSsOverUt = false;
                mSupportsCsfb = true;
            }
        }

        /**
         * Returns whether Ut is available.
         */
        public boolean useSsOverUt() {
            return mUseSsOverUt;
        }

        /**
         * Returns whether CSFB is allowed.
         */
        public boolean supportsCsfb() {
            return mSupportsCsfb;
        }
    }

    public static final String SS_CW = "CW";
    public static final String SS_CLIP = "CLIP";
    public static final String SS_CLIR = "CLIR";
    public static final String SS_COLP = "COLP";
    public static final String SS_COLR = "COLR";

    // Barring list of incoming numbers
    public static final String CB_FACILITY_BIL = "BIL";
    // Barring of all anonymous incoming number
    public static final String CB_FACILITY_ACR = "ACR";

    /**
     * Network callback used to determine whether Wi-Fi is connected or not.
     */
    private ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    logi("Network available: " + network);
                    updateWifiForUt(true);
                }

                @Override
                public void onLost(Network network) {
                    logi("Network lost: " + network);
                    updateWifiForUt(false);
                }

                @Override
                public void onUnavailable() {
                    logi("Network unavailable");
                    updateWifiForUt(false);
                }
            };

    private final GsmCdmaPhone mPhone;

    private final HashSet<String> mCbOverUtSupported = new HashSet<>();
    private final HashSet<Integer> mCfOverUtSupported = new HashSet<>();
    private final HashSet<String> mSsOverUtSupported = new HashSet<>();
    private boolean mUtSupported = false;
    private boolean mCsfbSupported = true;

    private boolean mUtRequiresImsRegistration = false;
    private boolean mUtAvailableWhenPsDataOff = false;
    private boolean mUtAvailableWhenRoaming = false;
    private Set<Integer> mUtAvailableRats = new HashSet<>();
    private boolean mWiFiAvailable = false;
    private boolean mIsMonitoringConnectivity = false;
    /** true if Ims service handles the terminal-based service by itself. */
    private boolean mOemHandlesTerminalBasedService = false;
    private boolean mSupportsTerminalBasedCallWaiting = false;
    private boolean mSupportsTerminalBasedClir = false;

    public SsDomainController(GsmCdmaPhone phone) {
        mPhone = phone;
    }

    /**
     * Cache the configurations
     */
    public void updateSsOverUtConfig(PersistableBundle b) {
        if (b == null) {
            b = CarrierConfigManager.getDefaultConfig();
        }

        boolean supportsCsfb = b.getBoolean(
                CarrierConfigManager.ImsSs.KEY_USE_CSFB_ON_XCAP_OVER_UT_FAILURE_BOOL);
        boolean requiresImsRegistration = b.getBoolean(
                CarrierConfigManager.ImsSs.KEY_UT_REQUIRES_IMS_REGISTRATION_BOOL);
        boolean availableWhenPsDataOff = b.getBoolean(
                CarrierConfigManager.ImsSs.KEY_UT_SUPPORTED_WHEN_PS_DATA_OFF_BOOL);
        boolean availableWhenRoaming = b.getBoolean(
                CarrierConfigManager.ImsSs.KEY_UT_SUPPORTED_WHEN_ROAMING_BOOL);

        boolean supportsUt = b.getBoolean(
                CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL);
        int[] services = b.getIntArray(
                CarrierConfigManager.ImsSs.KEY_UT_SERVER_BASED_SERVICES_INT_ARRAY);

        int[] utRats = b.getIntArray(
                CarrierConfigManager.ImsSs.KEY_XCAP_OVER_UT_SUPPORTED_RATS_INT_ARRAY);

        int[] tbServices = b.getIntArray(
                CarrierConfigManager.ImsSs.KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY);

        updateSsOverUtConfig(supportsUt, supportsCsfb, requiresImsRegistration,
                availableWhenPsDataOff, availableWhenRoaming, services, utRats, tbServices);
    }

    private void updateSsOverUtConfig(boolean supportsUt, boolean supportsCsfb,
            boolean requiresImsRegistration, boolean availableWhenPsDataOff,
            boolean availableWhenRoaming, int[] services, int[] utRats, int[] tbServices) {

        mUtSupported = supportsUt;
        mCsfbSupported = supportsCsfb;
        mUtRequiresImsRegistration = requiresImsRegistration;
        mUtAvailableWhenPsDataOff = availableWhenPsDataOff;
        mUtAvailableWhenRoaming = availableWhenRoaming;

        mSupportsTerminalBasedCallWaiting = false;
        mSupportsTerminalBasedClir = false;
        if (tbServices != null) {
            for (int tbService : tbServices) {
                if (tbService == SUPPLEMENTARY_SERVICE_CW) {
                    mSupportsTerminalBasedCallWaiting = true;
                }
                if (tbService == SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIR) {
                    mSupportsTerminalBasedClir = true;
                }
            }
        }
        logi("updateSsOverUtConfig terminal-based cw=" + mSupportsTerminalBasedCallWaiting
                + ", clir=" + mSupportsTerminalBasedClir);

        mCbOverUtSupported.clear();
        mCfOverUtSupported.clear();
        mSsOverUtSupported.clear();
        mUtAvailableRats.clear();

        if (!mUtSupported) {
            logd("updateSsOverUtConfig Ut is not supported");
            unregisterForConnectivityChanges();
            return;
        }

        if (services != null) {
            for (int service : services) {
                updateConfig(service);
            }
        }

        if (utRats != null) {
            mUtAvailableRats = Arrays.stream(utRats).boxed().collect(Collectors.toSet());
        }

        if (mUtAvailableRats.contains(IWLAN)) {
            registerForConnectivityChanges();
        } else {
            unregisterForConnectivityChanges();
        }

        logi("updateSsOverUtConfig supportsUt=" + mUtSupported
                + ", csfb=" + mCsfbSupported
                + ", regRequire=" + mUtRequiresImsRegistration
                + ", whenPsDataOff=" + mUtAvailableWhenPsDataOff
                + ", whenRoaming=" + mUtAvailableWhenRoaming
                + ", cbOverUtSupported=" + mCbOverUtSupported
                + ", cfOverUtSupported=" + mCfOverUtSupported
                + ", ssOverUtSupported=" + mSsOverUtSupported
                + ", utAvailableRats=" + mUtAvailableRats
                + ", including IWLAN=" + mUtAvailableRats.contains(IWLAN));
    }

    private void updateConfig(int service) {
        switch(service) {
            case SUPPLEMENTARY_SERVICE_CW: mSsOverUtSupported.add(SS_CW); return;

            case SUPPLEMENTARY_SERVICE_CF_ALL: mCfOverUtSupported.add(CF_REASON_ALL); return;
            case SUPPLEMENTARY_SERVICE_CF_CFU:
                mCfOverUtSupported.add(CF_REASON_UNCONDITIONAL);
                return;
            case SUPPLEMENTARY_SERVICE_CF_ALL_CONDITONAL_FORWARDING:
                mCfOverUtSupported.add(CF_REASON_ALL_CONDITIONAL);
                return;
            case SUPPLEMENTARY_SERVICE_CF_CFB: mCfOverUtSupported.add(CF_REASON_BUSY); return;
            case SUPPLEMENTARY_SERVICE_CF_CFNRY: mCfOverUtSupported.add(CF_REASON_NO_REPLY); return;
            case SUPPLEMENTARY_SERVICE_CF_CFNRC:
                mCfOverUtSupported.add(CF_REASON_NOT_REACHABLE);
                return;

            case SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIP: mSsOverUtSupported.add(SS_CLIP); return;
            case SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIP: mSsOverUtSupported.add(SS_COLP); return;
            case SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIR: mSsOverUtSupported.add(SS_CLIR); return;
            case SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIR: mSsOverUtSupported.add(SS_COLR); return;

            case SUPPLEMENTARY_SERVICE_CB_BAOC: mCbOverUtSupported.add(CB_FACILITY_BAOC); return;
            case SUPPLEMENTARY_SERVICE_CB_BOIC: mCbOverUtSupported.add(CB_FACILITY_BAOIC); return;
            case SUPPLEMENTARY_SERVICE_CB_BOIC_EXHC:
                mCbOverUtSupported.add(CB_FACILITY_BAOICxH);
                return;
            case SUPPLEMENTARY_SERVICE_CB_BAIC: mCbOverUtSupported.add(CB_FACILITY_BAIC); return;
            case SUPPLEMENTARY_SERVICE_CB_BIC_ROAM:
                mCbOverUtSupported.add(CB_FACILITY_BAICr);
                return;
            case SUPPLEMENTARY_SERVICE_CB_ACR: mCbOverUtSupported.add(CB_FACILITY_ACR); return;
            case SUPPLEMENTARY_SERVICE_CB_BIL: mCbOverUtSupported.add(CB_FACILITY_BIL); return;
            case SUPPLEMENTARY_SERVICE_CB_ALL: mCbOverUtSupported.add(CB_FACILITY_BA_ALL); return;
            case SUPPLEMENTARY_SERVICE_CB_OBS: mCbOverUtSupported.add(CB_FACILITY_BA_MO); return;
            case SUPPLEMENTARY_SERVICE_CB_IBS: mCbOverUtSupported.add(CB_FACILITY_BA_MT); return;

            default:
                break;
        }
    }

    /**
     * Determines whether Ut service is available or not.
     *
     * @return {@code true} if Ut service is available
     */
    @VisibleForTesting
    public boolean isUtEnabled() {
        Phone imsPhone = mPhone.getImsPhone();
        if (imsPhone == null) {
            logd("isUtEnabled: called for GsmCdma");
            return false;
        }

        if (!mUtSupported) {
            logd("isUtEnabled: not supported");
            return false;
        }

        if (mUtRequiresImsRegistration
                && imsPhone.getServiceState().getState() != ServiceState.STATE_IN_SERVICE) {
            logd("isUtEnabled: not registered");
            return false;
        }

        if (isUtAvailableOnAnyTransport()) {
            return imsPhone.isUtEnabled();
        }

        return false;
    }

    private boolean isMobileDataEnabled() {
        boolean enabled;
        int state = Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                Settings.Global.MOBILE_DATA, -1);
        if (state == -1) {
            logi("isMobileDataEnabled MOBILE_DATA not found");
            enabled = "true".equalsIgnoreCase(
                    SystemProperties.get("ro.com.android.mobiledata", "true"));
        } else {
            enabled = (state != 0);
        }
        logi("isMobileDataEnabled enabled=" + enabled);
        return enabled;
    }

    private boolean isUtAvailableOnAnyTransport() {
        if (mUtAvailableWhenPsDataOff || isMobileDataEnabled()) {
            if (isUtAvailableOverCellular()) {
                logi("isUtAvailableOnAnyTransport found cellular");
                return true;
            }
        }

        logi("isUtAvailableOnAnyTransport wifiConnected=" + mWiFiAvailable);
        if (mWiFiAvailable) {
            if (mUtAvailableRats.contains(IWLAN)) {
                logi("isUtAvailableOnAnyTransport found wifi");
                return true;
            }
            logi("isUtAvailableOnAnyTransport wifi not support Ut");
        }

        logi("isUtAvailableOnAnyTransport no transport");
        return false;
    }

    private boolean isUtAvailableOverCellular() {
        NetworkRegistrationInfo nri = mPhone.getServiceState().getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (nri != null && nri.isRegistered()) {
            if (!mUtAvailableWhenRoaming && nri.isRoaming()) {
                logi("isUtAvailableOverCellular not available in roaming");
                return false;
            }

            int networkType = nri.getAccessNetworkTechnology();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_NR:
                    if (mUtAvailableRats.contains(NGRAN)) return true;
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    if (mUtAvailableRats.contains(EUTRAN)) return true;
                    break;

                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    if (mUtAvailableRats.contains(UTRAN)) return true;
                    break;
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GSM:
                    if (mUtAvailableRats.contains(GERAN)) return true;
                    break;
                default:
                    break;
            }
        }

        logi("isUtAvailableOverCellular no cellular");
        return false;
    }

    /**
     * Updates the Wi-Fi connection state.
     */
    @VisibleForTesting
    public void updateWifiForUt(boolean available) {
        mWiFiAvailable = available;
    }

    /**
     * Registers for changes to network connectivity.
     */
    private void registerForConnectivityChanges() {
        if (mIsMonitoringConnectivity) {
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) mPhone.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            logi("registerForConnectivityChanges");
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            cm.registerNetworkCallback(builder.build(), mNetworkCallback);
            mIsMonitoringConnectivity = true;
        }
    }

    /**
     * Unregisters for connectivity changes.
     */
    private void unregisterForConnectivityChanges() {
        if (!mIsMonitoringConnectivity) {
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) mPhone.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            logi("unregisterForConnectivityChanges");
            cm.unregisterNetworkCallback(mNetworkCallback);
            mIsMonitoringConnectivity = false;
        }
    }

    /**
     * Returns whether Ut is available for the given Call Barring service.
     */
    @VisibleForTesting
    public boolean useCbOverUt(String facility) {
        if (!mUtSupported) {
            logd("useCbOverUt: Ut not supported");
            return false;
        }

        return mCbOverUtSupported.contains(facility);
    }

    /**
     * Returns whether Ut is available for the given Call Forwarding service.
     */
    @VisibleForTesting
    public boolean useCfOverUt(int reason) {
        if (!mUtSupported) {
            logd("useCfOverUt: Ut not supported");
            return false;
        }

        return mCfOverUtSupported.contains(reason);
    }

    /**
     * Returns whether Ut is available for the given supplementary service.
     */
    @VisibleForTesting
    public boolean useSsOverUt(String service) {
        if (!mUtSupported) {
            logd("useSsOverUt: Ut not supported");
            return false;
        }

        return mSsOverUtSupported.contains(service);
    }

    /**
     * Returns whether CSFB is supported for supplementary services.
     */
    public boolean supportCsfb() {
        if (!mUtSupported) {
            logd("supportsCsfb: Ut not supported");
            return true;
        }

        return mCsfbSupported;
    }

    /**
     * Returns SuppServiceRoutingInfo instance for the given Call Barring service.
     * Only for ImsPhoneMmiCode.
     */
    public SuppServiceRoutingInfo getSuppServiceRoutingInfoForCb(String facility) {
        return new SuppServiceRoutingInfo(useCbOverUt(facility), isUtEnabled(), supportCsfb());
    }

    /**
     * Returns SuppServiceRoutingInfo instance for the given Call Forwarding service.
     * Only for ImsPhoneMmiCode.
     */
    public SuppServiceRoutingInfo getSuppServiceRoutingInfoForCf(int reason) {
        return new SuppServiceRoutingInfo(useCfOverUt(reason), isUtEnabled(), supportCsfb());
    }

    /**
     * Returns SuppServiceRoutingInfo instance for the given supplementary service.
     * Only for ImsPhoneMmiCode.
     */
    public SuppServiceRoutingInfo getSuppServiceRoutingInfoForSs(String service) {
        if ((SS_CW.equals(service) && getOemHandlesTerminalBasedCallWaiting())
                || (SS_CLIR.equals(service) && getOemHandlesTerminalBasedClir())) {
            // Ims service handles the terminal based service by itself.
            // Use legacy implementation. Forward the request to Ims service if Ut is available.
            Phone imsPhone = mPhone.getImsPhone();
            boolean isUtEnabled = (imsPhone != null) && imsPhone.isUtEnabled();
            return new SuppServiceRoutingInfo(true, isUtEnabled, true);
        }
        return new SuppServiceRoutingInfo(useSsOverUt(service), isUtEnabled(), supportCsfb());
    }

    /**
     * Returns SuppServiceRoutingInfo instance for a service will be served by Ut interface.
     * Only for ImsPhoneMmiCode.
     */
    public SuppServiceRoutingInfo getSsRoutingOverUt() {
        return new SuppServiceRoutingInfo(true, isUtEnabled(), true);
    }

    /**
     * Set the carrier configuration for test.
     * Test purpose only.
     */
    @VisibleForTesting
    public void updateCarrierConfigForTest(boolean supportsUt, boolean supportsCsfb,
            boolean requiresImsRegistration, boolean availableWhenPsDataOff,
            boolean availableWhenRoaming, int[] services, int[] utRats, int[] tbServices) {
        logi("updateCarrierConfigForTest supportsUt=" + supportsUt
                +  ", csfb=" + supportsCsfb
                + ", reg=" + requiresImsRegistration
                + ", whenPsDataOff=" + availableWhenPsDataOff
                + ", whenRoaming=" + availableWhenRoaming
                + ", services=" + Arrays.toString(services)
                + ", rats=" + Arrays.toString(utRats)
                + ", tbServices=" + Arrays.toString(tbServices));

        updateSsOverUtConfig(supportsUt, supportsCsfb, requiresImsRegistration,
                availableWhenPsDataOff, availableWhenRoaming, services, utRats, tbServices);
    }

    /**
     * @param state true if Ims service handles the terminal-based service by itself.
     *              Otherwise, false.
     */
    public void setOemHandlesTerminalBasedService(boolean state) {
        logi("setOemHandlesTerminalBasedService " + state);
        mOemHandlesTerminalBasedService = state;
    }

    /**
     * Returns whether the carrier supports the terminal-based call waiting service
     * and Ims service handles it by itself.
     */
    public boolean getOemHandlesTerminalBasedCallWaiting() {
        logi("getOemHandlesTerminalBasedCallWaiting "
                + mSupportsTerminalBasedCallWaiting + ", " + mOemHandlesTerminalBasedService);
        return mSupportsTerminalBasedCallWaiting && mOemHandlesTerminalBasedService;
    }

    /**
     * Returns whether the carrier supports the terminal-based CLIR
     * and Ims service handles it by itself.
     */
    public boolean getOemHandlesTerminalBasedClir() {
        logi("getOemHandlesTerminalBasedClir "
                + mSupportsTerminalBasedClir + ", " + mOemHandlesTerminalBasedService);
        return mSupportsTerminalBasedClir && mOemHandlesTerminalBasedService;
    }

    /**
     * Dump this instance into a readable format for dumpsys usage.
     */
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.increaseIndent();
        pw.println("SsDomainController:");
        pw.println(" mUtSupported=" + mUtSupported);
        pw.println(" mCsfbSupported=" + mCsfbSupported);
        pw.println(" mCbOverUtSupported=" + mCbOverUtSupported);
        pw.println(" mCfOverUtSupported=" + mCfOverUtSupported);
        pw.println(" mSsOverUtSupported=" + mSsOverUtSupported);
        pw.println(" mUtRequiresImsRegistration=" + mUtRequiresImsRegistration);
        pw.println(" mUtAvailableWhenPsDataOff=" + mUtAvailableWhenPsDataOff);
        pw.println(" mUtAvailableWhenRoaming=" + mUtAvailableWhenRoaming);
        pw.println(" mUtAvailableRats=" + mUtAvailableRats);
        pw.println(" mWiFiAvailable=" + mWiFiAvailable);
        pw.println(" mOemHandlesTerminalBasedService=" + mOemHandlesTerminalBasedService);
        pw.println(" mSupportsTerminalBasedCallWaiting=" + mSupportsTerminalBasedCallWaiting);
        pw.println(" mSupportsTerminalBasedClir=" + mSupportsTerminalBasedClir);
        pw.decreaseIndent();
    }

    private void logi(String msg) {
        Rlog.i(LOG_TAG, "[" + mPhone.getPhoneId() + "] " + msg);
    }

    private void logd(String msg) {
        Rlog.d(LOG_TAG, "[" + mPhone.getPhoneId() + "] " + msg);
    }
}
