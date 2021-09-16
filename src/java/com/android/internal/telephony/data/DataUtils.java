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
import android.net.NetworkCapabilities;

/**
 * This class contains all the utility methods used by telephony data stack.
 */
public class DataUtils {
    /**
     * Get the network capability from the string.
     *
     * @param capabilityString The capability in string format
     * @return The network capability.
     */
    public static int getNetworkCapabilityFromString(@NonNull String capabilityString) {
        switch (capabilityString) {
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
     * Convert capabilities to string.
     *
     * This is for debugging and logging purposes only.
     *
     * @param netCap Network capability
     * @return Network capability in string format
     */
    public static String networkCapabilityToString(int netCap) {
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
}
