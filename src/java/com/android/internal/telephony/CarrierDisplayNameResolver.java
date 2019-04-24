/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.telephony.ServiceState;

import com.android.internal.telephony.uicc.IccRecords.CarrierNameDisplayConditionBitmask;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** Interface for carrier display name resolver. */
public interface CarrierDisplayNameResolver {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"EF_SOURCE_"}, value = {
            EF_SOURCE_CARRIER_API,
            EF_SOURCE_CARRIER_CONFIG,
            EF_SOURCE_USIM,
            EF_SOURCE_SIM,
            EF_SOURCE_CSIM,
            EF_SOURCE_RUIM,
            EF_SOURCE_VOICE_OPERATOR_SIGNALLING,
            EF_SOURCE_DATA_OPERATOR_SIGNALLING,
            EF_SOURCE_MODEM_CONFIG})
    @interface EFSource {}

    int EF_SOURCE_DEFAULT = 0;
    int EF_SOURCE_CARRIER_CONFIG = 1;
    int EF_SOURCE_CARRIER_API = 2;
    int EF_SOURCE_USIM = 3;
    int EF_SOURCE_SIM = 4;
    int EF_SOURCE_CSIM = 5;
    int EF_SOURCE_RUIM = 6;
    int EF_SOURCE_VOICE_OPERATOR_SIGNALLING = 7;
    int EF_SOURCE_DATA_OPERATOR_SIGNALLING = 8;
    int EF_SOURCE_MODEM_CONFIG = 9;

    /**
     * Update the service provider name for the registered PLMN.
     *
     * Reference: 3GPP TS 131.102 Section 4.2.12 EF_SPN.
     *
     * @param source the source of where the service provider name come from.
     * @param spn the service provider name for the registered PLMN.
     */
    void updateServiceProviderName(@EFSource int source, @NonNull String spn);

    /**
     * Update the display condition of service provider name and PLMN network name. The display
     * condition has two bits(lsb). Service provider name display is required if the first bit
     * is set to 1. PLMN network name display is required if the second bit is set to 1.
     *
     * @see {@link IccRecords#CARRIER_NAME_DISPLAY_CONDITION_BITMASK_PLMN}
     * @see {@link IccRecords#CARRIER_NAME_DISPLAY_CONDITION_BITMASK_SPN}
     *
     * Reference: 3GPP TS 131.102 Section 4.2.12 EF_SPN
     *
     * @param source where the spn display condition come from.
     * @param spnDisplayCondition the spn display condition.
     */
    void updateServiceProviderNameDisplayCondition(
            @EFSource int source,
            @CarrierNameDisplayConditionBitmask int spnDisplayCondition);

    /**
     * Update the service provider display information. This is a list of PLMNs in which the
     * service provider name shall be displayed.
     *
     * Reference: 3GPP TS 131.102 Section 4.2.66 EF_SPDI
     *
     * @param source the source of where the service provider display information come from.
     * @param spdi a list of numeric version PLMNs.
     */
    void updateServiceProviderDisplayInformation(
            @EFSource int source, @NonNull List<String> spdi);

    /**
     * Update a list of full and short form versions of the network name for the registered
     * PLMN.
     *
     * Reference: 3GPP TS 131.102 Section 4.2.58 EF_PNN.
     *
     * @param source the source of where the PLMN network name come from.
     * @param pnnList a list of full name and short name of PLMN network name.
     */
    void updatePlmnNetworkNameList(
            @EFSource int source, @NonNull List<PlmnNetworkName> pnnList);

    /**
     * Update the equivalent HPLMN list.
     *
     * Reference: 3GPP TS 31.102 v15.2.0 Section 4.2.84 EF_EHPLMN
     * Reference: 3GPP TS 23.122 v15.6.0 Section 1.2 Equivalent HPLMN list
     *
     * @param source the source of where the ehplmn come from.
     * @param ehplmns a string list contains the equivalent HPLMN.
     */
    void updateEhplmnList(@EFSource int source, @NonNull List<String> ehplmns);

    /**
     * Update a list of operator PLMN information.
     *
     * Reference: 3GPP TS 131.102 Section 4.2.59 EF_OPL.
     *
     * @param source the source of where the OPL information come from.
     * @param oplList a list of operator PLMN information.
     */
    void updateOperatorPlmnList(
            @EFSource int source, @NonNull List<OperatorPlmnInfo> oplList);

    /**
     * Update service state.
     * @param serviceState service state.
     */
    void updateServiceState(@NonNull ServiceState serviceState);

    /**
     * Update the numeric version name of home PLMN.
     * @param homePlmnNumeric numeric version name of home PLMN.
     */
    void updateHomePlmnNumeric(@NonNull String homePlmnNumeric);

    /**
     * Check if display PLMN network name is required.
     *
     * @return {@code True} if display PLMN network name is required.
     */
    boolean shouldShowPlmnNetworkName();

    /**
     * Check if display service provider name is required.
     *
     * @return {@code True} if display service provider name is required.
     */
    boolean shouldShowServiceProviderName();

    /**
     * Get the plmn network name of the current registered PLMN.
     *
     * @return the full version name if is present, or return the short version name if it's
     * present and the full one is not present. If neither full name nor short name is present,
     * the numeric version name will be returned.
     */
    @NonNull
    String getPlmnNetworkName();

    /**
     * Get the service provider name of the current registered PLMN.
     *
     * @return the service provider name.
     */
    @NonNull
    String getServiceProviderName();
}

