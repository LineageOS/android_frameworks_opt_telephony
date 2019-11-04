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
package com.android.internal.telephony.metrics;

import android.os.BatteryStatsManager;
import android.os.connectivity.CellularBatteryStats;
import android.text.format.DateUtils;

import com.android.internal.telephony.nano.TelephonyProto.ModemPowerStats;

/**
 * ModemPowerMetrics holds the modem power metrics and converts them to ModemPowerStats proto buf.
 * This proto buf is included in the Telephony proto buf.
 */
public class ModemPowerMetrics {

    /* BatteryStatsManager API */
    private BatteryStatsManager mBatteryStatsManager;

    public ModemPowerMetrics(BatteryStatsManager batteryStatsManager) {
        mBatteryStatsManager = batteryStatsManager;
    }

    /**
     * Build ModemPowerStats proto
     * @return ModemPowerStats
     */
    public ModemPowerStats buildProto() {
        ModemPowerStats m = new ModemPowerStats();
        CellularBatteryStats stats = getStats();
        if (stats != null) {
            m.loggingDurationMs = stats.getLoggingDurationMillis();
            m.energyConsumedMah = stats.getEnergyConsumedMaMillis()
                / ((double) DateUtils.HOUR_IN_MILLIS);
            m.numPacketsTx = stats.getNumPacketsTx();
            m.cellularKernelActiveTimeMs = stats.getKernelActiveTimeMillis();
            if (stats.getTimeInRxSignalStrengthLevelMicros() != null
                    && stats.getTimeInRxSignalStrengthLevelMicros().length > 0) {
                m.timeInVeryPoorRxSignalLevelMs = stats.getTimeInRxSignalStrengthLevelMicros()[0];
            }
            m.sleepTimeMs = stats.getSleepTimeMillis();
            m.idleTimeMs = stats.getIdleTimeMillis();
            m.rxTimeMs = stats.getRxTimeMillis();
            long[] t = stats.getTxTimeMillis();
            m.txTimeMs = new long[t.length];
            System.arraycopy(t, 0, m.txTimeMs, 0, t.length);
            m.numBytesTx = stats.getNumBytesTx();
            m.numPacketsRx = stats.getNumPacketsRx();
            m.numBytesRx = stats.getNumBytesRx();
            long[] tr = stats.getTimeInRatMicros();
            m.timeInRatMs = new long[tr.length];
            System.arraycopy(tr, 0, m.timeInRatMs, 0, tr.length);
            long[] trx = stats.getTimeInRxSignalStrengthLevelMicros();
            m.timeInRxSignalStrengthLevelMs = new long[trx.length];
            System.arraycopy(trx, 0, m.timeInRxSignalStrengthLevelMs, 0, trx.length);
            m.monitoredRailEnergyConsumedMah = stats.getMonitoredRailChargeConsumedMaMillis()
                / ((double) DateUtils.HOUR_IN_MILLIS);
        }
        return m;
    }

    /**
     * Get cellular stats from BatteryStatsManager
     * @return CellularBatteryStats
     */
    private CellularBatteryStats getStats() {
        if (mBatteryStatsManager == null) {
            return null;
        }
        return mBatteryStatsManager.getCellularBatteryStats();
    }
}
