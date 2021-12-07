/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import android.util.StatsEvent;

import com.android.internal.telephony.TelephonyStatsLog;

/**
 * Wrapper of generated TelephonyStatsLog class. Can be mocked during unit test.
 *
 * <p>Methods here have the same name/arguments/return as their counterparts in TelephonyStatsLog
 * and simply call their counterparts. Do not put any involved logic here as they cannot be
 * covered by unit test.
 */
public class TelephonyStatsLogHelper {
    /** Wraps TelephonyStatsLog.buildStatsEvent(...) for atom PER_SIM_STATUS. */
    public StatsEvent buildStatsEvent(
            int atomId,
            int simSlotIndex,
            int carrierId,
            int phoneNumberSourceUicc,
            int phoneNumberSourceCarrier,
            int phoneNumberSourceIms) {
        return TelephonyStatsLog.buildStatsEvent(
                atomId,
                simSlotIndex,
                carrierId,
                phoneNumberSourceUicc,
                phoneNumberSourceCarrier,
                phoneNumberSourceIms);
    }
}
