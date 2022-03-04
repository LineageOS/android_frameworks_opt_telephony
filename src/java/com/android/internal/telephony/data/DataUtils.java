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

import android.annotation.CurrentTimeMillisLong;
import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.RadioAccessNetworkType;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.ApnSetting.ApnType;
import android.telephony.ims.feature.ImsFeature;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * This class contains all the utility methods used by telephony data stack.
 */
public class DataUtils {
    /** The time format for converting time to readable string. */
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /**
     * Get the network capability from the string.
     *
     * @param capabilityString The capability in string format
     * @return The network capability.
     */
    public static @NetCapability int getNetworkCapabilityFromString(
            @NonNull String capabilityString) {
        switch (capabilityString.toUpperCase(Locale.ROOT)) {
            case "MMS": return NetworkCapabilities.NET_CAPABILITY_MMS;
            case "SUPL": return NetworkCapabilities.NET_CAPABILITY_SUPL;
            case "DUN": return NetworkCapabilities.NET_CAPABILITY_DUN;
            case "FOTA": return NetworkCapabilities.NET_CAPABILITY_FOTA;
            case "IMS": return NetworkCapabilities.NET_CAPABILITY_IMS;
            case "CBS": return NetworkCapabilities.NET_CAPABILITY_CBS;
            case "XCAP": return NetworkCapabilities.NET_CAPABILITY_XCAP;
            case "EIMS": return NetworkCapabilities.NET_CAPABILITY_EIMS;
            case "INTERNET": return NetworkCapabilities.NET_CAPABILITY_INTERNET;
            case "MCX": return NetworkCapabilities.NET_CAPABILITY_MCX;
            case "ENTERPRISE": return NetworkCapabilities.NET_CAPABILITY_ENTERPRISE;
            // Only add APN type capabilities here. This should be only used by the priority
            // configuration.
            default:
                return -1;
        }
    }

    /**
     * Convert a network capability to string.
     *
     * This is for debugging and logging purposes only.
     *
     * @param netCap Network capability.
     * @return Network capability in string format.
     */
    public static @NonNull String networkCapabilityToString(@NetCapability int netCap) {
        switch (netCap) {
            case NetworkCapabilities.NET_CAPABILITY_MMS:                  return "MMS";
            case NetworkCapabilities.NET_CAPABILITY_SUPL:                 return "SUPL";
            case NetworkCapabilities.NET_CAPABILITY_DUN:                  return "DUN";
            case NetworkCapabilities.NET_CAPABILITY_FOTA:                 return "FOTA";
            case NetworkCapabilities.NET_CAPABILITY_IMS:                  return "IMS";
            case NetworkCapabilities.NET_CAPABILITY_CBS:                  return "CBS";
            case NetworkCapabilities.NET_CAPABILITY_WIFI_P2P:             return "WIFI_P2P";
            case NetworkCapabilities.NET_CAPABILITY_IA:                   return "IA";
            case NetworkCapabilities.NET_CAPABILITY_RCS:                  return "RCS";
            case NetworkCapabilities.NET_CAPABILITY_XCAP:                 return "XCAP";
            case NetworkCapabilities.NET_CAPABILITY_EIMS:                 return "EIMS";
            case NetworkCapabilities.NET_CAPABILITY_NOT_METERED:          return "NOT_METERED";
            case NetworkCapabilities.NET_CAPABILITY_INTERNET:             return "INTERNET";
            case NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED:       return "NOT_RESTRICTED";
            case NetworkCapabilities.NET_CAPABILITY_TRUSTED:              return "TRUSTED";
            case NetworkCapabilities.NET_CAPABILITY_NOT_VPN:              return "NOT_VPN";
            case NetworkCapabilities.NET_CAPABILITY_VALIDATED:            return "VALIDATED";
            case NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL:       return "CAPTIVE_PORTAL";
            case NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING:          return "NOT_ROAMING";
            case NetworkCapabilities.NET_CAPABILITY_FOREGROUND:           return "FOREGROUND";
            case NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED:        return "NOT_CONGESTED";
            case NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED:        return "NOT_SUSPENDED";
            case NetworkCapabilities.NET_CAPABILITY_OEM_PAID:             return "OEM_PAID";
            case NetworkCapabilities.NET_CAPABILITY_MCX:                  return "MCX";
            case NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY:
                return "PARTIAL_CONNECTIVITY";
            case NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED:
                return "TEMPORARILY_NOT_METERED";
            case NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE:          return "OEM_PRIVATE";
            case NetworkCapabilities.NET_CAPABILITY_VEHICLE_INTERNAL:     return "VEHICLE_INTERNAL";
            case NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED:      return "NOT_VCN_MANAGED";
            case NetworkCapabilities.NET_CAPABILITY_ENTERPRISE:           return "ENTERPRISE";
            case NetworkCapabilities.NET_CAPABILITY_VSIM:                 return "VSIM";
            case NetworkCapabilities.NET_CAPABILITY_BIP:                  return "BIP";
            case NetworkCapabilities.NET_CAPABILITY_HEAD_UNIT:            return "HEAD_UNIT";
            default:
                return "Unknown(" + Integer.toString(netCap) + ")";
        }
    }

    /**
     * Convert network capabilities to string.
     *
     * This is for debugging and logging purposes only.
     *
     * @param netCaps Network capabilities.
     * @return Network capabilities in string format.
     */
    public static @NonNull String networkCapabilitiesToString(@NetCapability int[] netCaps) {
        if (netCaps == null) return "";
        return "[" + Arrays.stream(netCaps)
                .mapToObj(DataUtils::networkCapabilityToString)
                .collect(Collectors.joining("|")) + "]";
    }

    /**
     * Convert the validation status to string.
     *
     * @param status The validation status.
     * @return The validation status in string format.
     */
    public static @NonNull String validationStatusToString(@ValidationStatus int status) {
        switch (status) {
            case NetworkAgent.VALIDATION_STATUS_VALID: return "VALID";
            case NetworkAgent.VALIDATION_STATUS_NOT_VALID: return "INVALID";
            default: return "UNKNOWN(" + status + ")";
        }
    }

    /**
     * Convert network capability into APN type.
     *
     * @param networkCapability Network capability.
     * @return APN type.
     */
    public static @ApnType int networkCapabilityToApnType(@NetCapability int networkCapability) {
        switch (networkCapability) {
            case NetworkCapabilities.NET_CAPABILITY_MMS:
                return ApnSetting.TYPE_MMS;
            case NetworkCapabilities.NET_CAPABILITY_SUPL:
                return ApnSetting.TYPE_SUPL;
            case NetworkCapabilities.NET_CAPABILITY_DUN:
                return ApnSetting.TYPE_DUN;
            case NetworkCapabilities.NET_CAPABILITY_FOTA:
                return ApnSetting.TYPE_FOTA;
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                return ApnSetting.TYPE_IMS;
            case NetworkCapabilities.NET_CAPABILITY_CBS:
                return ApnSetting.TYPE_CBS;
            case NetworkCapabilities.NET_CAPABILITY_XCAP:
                return ApnSetting.TYPE_XCAP;
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                return ApnSetting.TYPE_EMERGENCY;
            case NetworkCapabilities.NET_CAPABILITY_INTERNET:
                return ApnSetting.TYPE_DEFAULT;
            case NetworkCapabilities.NET_CAPABILITY_MCX:
                return ApnSetting.TYPE_MCX;
            case NetworkCapabilities.NET_CAPABILITY_IA:
                return ApnSetting.TYPE_IA;
            default:
                return ApnSetting.TYPE_NONE;
        }
    }

    /**
     * Convert APN type to capability.
     *
     * @param apnType APN type.
     * @return Network capability.
     */
    public static @NetCapability int apnTypeToNetworkCapability(@ApnType int apnType) {
        switch (apnType) {
            case ApnSetting.TYPE_MMS:
                return NetworkCapabilities.NET_CAPABILITY_MMS;
            case ApnSetting.TYPE_SUPL:
                return NetworkCapabilities.NET_CAPABILITY_SUPL;
            case ApnSetting.TYPE_DUN:
                return NetworkCapabilities.NET_CAPABILITY_DUN;
            case ApnSetting.TYPE_FOTA:
                return NetworkCapabilities.NET_CAPABILITY_FOTA;
            case ApnSetting.TYPE_IMS:
                return NetworkCapabilities.NET_CAPABILITY_IMS;
            case ApnSetting.TYPE_CBS:
                return NetworkCapabilities.NET_CAPABILITY_CBS;
            case ApnSetting.TYPE_XCAP:
                return NetworkCapabilities.NET_CAPABILITY_XCAP;
            case ApnSetting.TYPE_EMERGENCY:
                return NetworkCapabilities.NET_CAPABILITY_EIMS;
            case ApnSetting.TYPE_DEFAULT:
                return NetworkCapabilities.NET_CAPABILITY_INTERNET;
            case ApnSetting.TYPE_MCX:
                return NetworkCapabilities.NET_CAPABILITY_MCX;
            case ApnSetting.TYPE_IA:
                return NetworkCapabilities.NET_CAPABILITY_IA;
            // Do not add TYPE_VSIM, TYPE_BIP, TYPE_HIPRI
            // TODO: Add ENTERPRISE here if needed.
            default:
                return -1;
        }
    }

    /**
     * Convert network type to access network type.
     *
     * @param networkType The network type.
     * @return The access network type.
     */
    public static @RadioAccessNetworkType int networkTypeToAccessNetworkType(
            @NetworkType int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return AccessNetworkConstants.AccessNetworkType.GERAN;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return AccessNetworkConstants.AccessNetworkType.UTRAN;
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return AccessNetworkConstants.AccessNetworkType.CDMA2000;
            case TelephonyManager.NETWORK_TYPE_LTE:
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                return AccessNetworkConstants.AccessNetworkType.EUTRAN;
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return AccessNetworkConstants.AccessNetworkType.IWLAN;
            case TelephonyManager.NETWORK_TYPE_NR:
                return AccessNetworkConstants.AccessNetworkType.NGRAN;
            default:
                return AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        }
    }

    /**
     * Convert the elapsed time to the current time with readable time format.
     *
     * @param elapsedTime The elapsed time retrieved from {@link SystemClock#elapsedRealtime()}.
     * @return The string format time.
     */
    public static @NonNull String elapsedTimeToString(@ElapsedRealtimeLong long elapsedTime) {
        return (elapsedTime != 0) ? systemTimeToString(System.currentTimeMillis()
                - SystemClock.elapsedRealtime() + elapsedTime) : "never";
    }

    /**
     * Convert the system time to the human readable format.
     *
     * @param systemTime The system time retrieved from {@link System#currentTimeMillis()}.
     * @return The string format time.
     */
    public static @NonNull String systemTimeToString(@CurrentTimeMillisLong long systemTime) {
        return (systemTime != 0) ? TIME_FORMAT.format(systemTime) : "never";
    }

    /**
     * Convert the IMS feature to string.
     *
     * @param imsFeature IMS feature.
     * @return IMS feature in string format.
     */
    public static @NonNull String imsFeatureToString(@ImsFeature.FeatureType int imsFeature) {
        switch (imsFeature) {
            case ImsFeature.FEATURE_MMTEL: return "MMTEL";
            case ImsFeature.FEATURE_RCS: return "RCS";
            default:
                return "Unknown(" + imsFeature + ")";
        }
    }
}
