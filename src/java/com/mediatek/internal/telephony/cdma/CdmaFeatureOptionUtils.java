/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.mediatek.internal.telephony.cdma;

import android.os.SystemProperties;
import com.android.internal.telephony.PhoneConstants;
// import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
// import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

/**
 * The utilities class of CDMA related feature option definitions.
 * @hide
 */
public class CdmaFeatureOptionUtils {

    // EVDO dual talk support system property
    public static final String EVDO_DT_SUPPORT = "ril.evdo.dtsupport";
    // SVLTE support system property
    public static final String MTK_SVLTE_SUPPORT = "ro.mtk_svlte_support";
    // SRLTE support system property
    public static final String MTK_SRLTE_SUPPORT = "ro.mtk_srlte_support";
    // IRAT support system property
    public static final String MTK_IRAT_SUPPORT = "ro.c2k.irat.support";
    // MTK C2K support
    public static final String MTK_C2K_SUPPORT = "ro.mtk_c2k_support";
    // C2K World Phone Solution2
    public static final String MTK_C2KWP_P2_SUPPORT = "ro.mtk.c2k.slot2.support";
    // C2K World Phone Solution2 Sim Switch
    public static final String MTK_C2KWP_SIMSWITCH_SUPPORT = "ro.mtk.c2kwp.simswitch.support";
    // C2K OM World Phone Network Selection Type
    public static final String MTK_C2K_OM_NW_SEL_TYPE = "ro.mtk_c2k_om_nw_sel_type";
    // CT C 6M support
    public static final String MTK_CT6M_SUPPORT = "ro.ct6m_support";
    // CT LTE TDD TEST ONLY
    private static final String MTK_CT_LTE_TDD_TEST = "persist.sys.forcttddtest";
    // Flight mode power off modem support
    public static final String MTK_FLIGHT_MODE_POWER_OFF_MODEM_SUPPORT
            = "ro.mtk_flight_mode_power_off_md";

    // Feature support.
    public static final String SUPPORT_YES = "1";

    /**
     * Check if EVDO_DT_SUPPORT feature option support is true.
     * @return true if SVLTE is enabled
     */
    public static boolean isEvdoDTSupport() {
        if (SystemProperties.get(EVDO_DT_SUPPORT).equals(SUPPORT_YES)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if CDMA LTE Dual connection(SVLTE) support is true.
     * @return true if SVLTE is enabled
     */
    public static boolean isCdmaLteDcSupport() {
        if (SystemProperties.get(MTK_SVLTE_SUPPORT).equals(SUPPORT_YES) ||
                SystemProperties.get(MTK_SRLTE_SUPPORT).equals(SUPPORT_YES)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if CDMA LTE Dual connection(SVLTE) support is true.
     * @return true if SVLTE is enabled
     */
    public static boolean isSvlteSupport() {
        return SystemProperties.get(MTK_SVLTE_SUPPORT).equals(SUPPORT_YES);
    }

    /**
     * Check if CDMA LTE Dual connection(SRLTE) support is true.
     * @return true if SRLTE is enabled
     */
    public static boolean isSrlteSupport() {
        return SystemProperties.get(MTK_SRLTE_SUPPORT).equals(SUPPORT_YES);
    }

    /**
     * Check if CDMA IRAT feature is supported.
     * @return True if C2K IRAT feature is supported.
     */
    public static boolean isCdmaIratSupport() {
        return SystemProperties.get(MTK_IRAT_SUPPORT).equals(SUPPORT_YES);
    }

    /**
     * Check if MTK C2K feature is supported.
     * @return True if MTK C2K feature is supported.
     */
    public static boolean isMtkC2KSupport() {
        return SystemProperties.get(MTK_C2K_SUPPORT).equals(SUPPORT_YES);
    }

    /**
      * Get cdma slot NO.
      * @return static int
      */
    public static int getExternalModemSlot() {
        // xen0n: MTK TODO
        /*
        if (isCdmaLteDcSupport()) {
            return SvlteModeController.getCdmaSocketSlotId();
        }
        */
        return SystemProperties.getInt("ril.external.md", 0) - 1;
    }

    /**
     * Check if C2K World Phone solution2 is supported.
     * @return True if supported.
     */
    public static boolean isC2KWorldPhoneP2Support() {
        return SystemProperties.get(MTK_C2KWP_P2_SUPPORT).equals(SUPPORT_YES);
    }

    /**
     * Check if C2K World Phone solution2 Sim Switch is supported.
     * @return True if supported.
     */
    public static boolean isC2KWpSimSwitchSupport() {
        return SystemProperties.get(MTK_C2KWP_SIMSWITCH_SUPPORT).equals(SUPPORT_YES);
    }


    /**
     * Get the C2K OM Network Selection Type.
     * @return type value
     */
    public static int getC2KOMNetworkSelectionType() {
        return SystemProperties.getInt(MTK_C2K_OM_NW_SEL_TYPE, 0);
    }

    /**
     * Get the CT 6M Support
     */
    public static boolean isCT6MSupport() {
        return SUPPORT_YES.equals(SystemProperties.get(MTK_CT6M_SUPPORT, "0"));
    }

    /**
     * Get the CT LTE TDD test Support
     */
    public static boolean isCTLteTddTestSupport() {
        // xen0n: MTK TODO
        return false;
        /*
        return isCdmaLteDcSupport()
                && (SUPPORT_YES.equals(SystemProperties.get(MTK_CT_LTE_TDD_TEST, "0")))
                && (SvlteUiccUtils.getInstance().isUsimOnly(PhoneConstants.SIM_ID_1));
        */
    }

    /**
     * Check if flight mode power off mode feature is support.
     * @return Ture if supported.
     */
    public static boolean isFlightModePowerOffModemSupport() {
        return SystemProperties.get(MTK_FLIGHT_MODE_POWER_OFF_MODEM_SUPPORT)
                .equals(SUPPORT_YES);
    }
}
