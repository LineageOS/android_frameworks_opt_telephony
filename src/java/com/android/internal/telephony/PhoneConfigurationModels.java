/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.telephony.ModemInfo;
import android.telephony.PhoneCapability;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is temporary created for CBRS phase 1 demo purpose.
 * It has two hardcoded phone capability: ssss and dsds.
 * This file should be removed once modem interface to return capabilities are ready.
 */
class PhoneConfigurationModels {
    // Hardcoded DSDS capability.
    public static final PhoneCapability DSDS_CAPABILITY;
    // Hardcoded Single SIM single standby capability.
    public static final PhoneCapability SSSS_CAPABILITY;

    static {
        ModemInfo modemInfo1 = new ModemInfo(0, 0, true, true);
        ModemInfo modemInfo2 = new ModemInfo(1, 0, false, true);

        List<ModemInfo> logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo1);
        logicalModemList.add(modemInfo2);
        DSDS_CAPABILITY = new PhoneCapability(1, 2, 0, logicalModemList);

        logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo1);
        SSSS_CAPABILITY = new PhoneCapability(1, 1, 0, logicalModemList);
    }
}
