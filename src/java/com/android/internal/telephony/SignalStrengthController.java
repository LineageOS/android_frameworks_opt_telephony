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

package com.android.internal.telephony;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * SignalStrengthController handles signal polling request and unsolicited signal strength update.
 */
public class SignalStrengthController extends Handler {
    protected static final boolean DBG = false; /* STOPSHIP if true */
    protected static final String TAG = "SSCtr";

    private static final int EVENT_SET_SIGNAL_STRENGTH_UPDATE_REQUEST   = 1;
    private static final int EVENT_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST = 2;
    private static final int EVENT_ON_DEVICE_IDLE_STATE_CHANGED         = 3;
    private static final int EVENT_RIL_CONNECTED                        = 4;
    private static final int EVENT_RADIO_AVAILABLE                      = 5;


    private final Phone mPhone;
    private final CommandsInterface mCi;

    private final List<SignalRequestRecord> mSignalRequestRecords = new ArrayList<>();

    public SignalStrengthController(Phone phone) {
        mPhone = phone;
        mCi = mPhone.mCi;

        mCi.registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
    }

    @Override
    public void handleMessage(Message msg) {
        if (DBG) log("received event " + msg.what);

        switch (msg.what) {
            case EVENT_RIL_CONNECTED: // fall through
            case EVENT_RADIO_AVAILABLE:
                onReset();
                break;
            case EVENT_SET_SIGNAL_STRENGTH_UPDATE_REQUEST: {
                Pair<SignalRequestRecord, Message> pair =
                        (Pair<SignalRequestRecord, Message>) msg.obj;
                SignalRequestRecord record = pair.first;
                Message onCompleted = pair.second;
                AsyncResult ret = AsyncResult.forMessage(onCompleted);

                // TODO(b/177956310): Check subId to filter out old request until a better solution
                boolean dupRequest = mSignalRequestRecords.stream().anyMatch(
                        srr -> srr.mCallingUid == record.mCallingUid
                                && srr.mSubId == record.mSubId);
                if (dupRequest) {
                    ret.exception = new IllegalStateException(
                            "setSignalStrengthUpdateRequest called again with same subId");
                    onCompleted.sendToTarget();
                    break;
                }

                try {
                    record.mRequest.getLiveToken().linkToDeath(record, 0);
                } catch (RemoteException | NullPointerException ex) {
                    ret.exception = new IllegalStateException(
                            "Signal request client is already dead.");
                    onCompleted.sendToTarget();
                    break;
                }

                mSignalRequestRecords.add(record);

                updateAlwaysReportSignalStrength();
                updateReportingCriteria(getCarrierConfig());

                onCompleted.sendToTarget();
                break;
            }

            case EVENT_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST: {
                Pair<SignalRequestRecord, Message> pair =
                        (Pair<SignalRequestRecord, Message>) msg.obj;
                SignalRequestRecord record = pair.first;
                Message onCompleted = pair.second;

                // for loop with removal may cause ConcurrentModificationException
                Iterator<SignalRequestRecord> it = mSignalRequestRecords.iterator();
                while (it.hasNext()) {
                    SignalRequestRecord srr = it.next();
                    if (srr.mRequest.getLiveToken().equals(record.mRequest.getLiveToken())) {
                        it.remove();
                    }
                }

                updateAlwaysReportSignalStrength();
                updateReportingCriteria(getCarrierConfig());

                if (onCompleted != null) {
                    AsyncResult ret = AsyncResult.forMessage(onCompleted);
                    onCompleted.sendToTarget();
                }
                break;
            }

            case EVENT_ON_DEVICE_IDLE_STATE_CHANGED: {
                updateReportingCriteria(getCarrierConfig());
                break;
            }

            default:
                log("Unhandled message with number: " + msg.what);
                break;
        }
    }

    /**
     * Set a new request to update the signal strength thresholds.
     */
    public void setSignalStrengthUpdateRequest(int subId, int callingUid,
            SignalStrengthUpdateRequest request, @NonNull Message onCompleted) {
        SignalRequestRecord record = new SignalRequestRecord(subId, callingUid, request);
        sendMessage(obtainMessage(EVENT_SET_SIGNAL_STRENGTH_UPDATE_REQUEST,
                new Pair<SignalRequestRecord, Message>(record, onCompleted)));
    }

    /**
     * Clear the previously set request.
     */
    public void clearSignalStrengthUpdateRequest(int subId, int callingUid,
            SignalStrengthUpdateRequest request, @Nullable Message onCompleted) {
        SignalRequestRecord record = new SignalRequestRecord(subId, callingUid, request);
        sendMessage(obtainMessage(EVENT_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST,
                new Pair<SignalRequestRecord, Message>(record, onCompleted)));
    }

    /**
     * Update signal strength reporting criteria from the carrier config
     */
    @VisibleForTesting
    public void updateReportingCriteria(PersistableBundle config) {
        int lteMeasurementEnabled = config.getInt(CarrierConfigManager
                .KEY_PARAMETERS_USED_FOR_LTE_SIGNAL_BAR_INT, CellSignalStrengthLte.USE_RSRP);
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                config.getIntArray(CarrierConfigManager.KEY_LTE_RSRP_THRESHOLDS_INT_ARRAY),
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                (lteMeasurementEnabled & CellSignalStrengthLte.USE_RSRP) != 0);
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
                config.getIntArray(CarrierConfigManager.KEY_WCDMA_RSCP_THRESHOLDS_INT_ARRAY),
                AccessNetworkConstants.AccessNetworkType.UTRAN, true);
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                config.getIntArray(CarrierConfigManager.KEY_GSM_RSSI_THRESHOLDS_INT_ARRAY),
                AccessNetworkConstants.AccessNetworkType.GERAN, true);

        if (mPhone.getHalVersion().greaterOrEqual(RIL.RADIO_HAL_VERSION_1_5)) {
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                    config.getIntArray(CarrierConfigManager.KEY_LTE_RSRQ_THRESHOLDS_INT_ARRAY),
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    (lteMeasurementEnabled & CellSignalStrengthLte.USE_RSRQ) != 0);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                    config.getIntArray(CarrierConfigManager.KEY_LTE_RSSNR_THRESHOLDS_INT_ARRAY),
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    (lteMeasurementEnabled & CellSignalStrengthLte.USE_RSSNR) != 0);

            int measurementEnabled = config.getInt(CarrierConfigManager
                    .KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT, CellSignalStrengthNr.USE_SSRSRP);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                    config.getIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY),
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    (measurementEnabled & CellSignalStrengthNr.USE_SSRSRP) != 0);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                    config.getIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY),
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    (measurementEnabled & CellSignalStrengthNr.USE_SSRSRQ) != 0);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                    config.getIntArray(CarrierConfigManager.KEY_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY),
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    (measurementEnabled & CellSignalStrengthNr.USE_SSSINR) != 0);
        }
    }

    /**
     * Print the SignalStrengthController states into the given stream.
     *
     * @param fd The raw file descriptor that the dump is being sent to.
     * @param pw A PrintWriter to which the dump is to be set.
     * @param args Additional arguments to the dump request.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.increaseIndent();
        pw.println("mSignalRequestRecords: " + mSignalRequestRecords);
        ipw.decreaseIndent();
        ipw.flush();
    }

    /**
     * Align all the qualified thresholds set from applications to the {@code systemThresholds}
     * and consolidate a new thresholds array, follow rules below:
     * 1. All threshold values (whose interval is guaranteed to be larger than hysteresis) in
     *    {@code systemThresholds} will keep as it.
     * 2. Any threshold from apps that has interval less than hysteresis from any threshold in
     *    {@code systemThresholds} will be removed.
     * 3. The target thresholds will be {@code systemThresholds} + all qualified thresholds from
     *    apps, sorted in ascending order.
     */
    @VisibleForTesting
    public int[] getConsolidatedSignalThresholds(int ran, int measurement,
            int[] systemThresholds, int hysteresis) {

        // TreeSet with comparator that will filter element with interval less than hysteresis
        // from any current element
        Set<Integer> target = new TreeSet<>((x, y) -> {
            if (y >= x - hysteresis && y <= x + hysteresis) {
                return 0;
            }
            return Integer.compare(x, y);
        });

        for (int systemThreshold : systemThresholds) {
            target.add(systemThreshold);
        }

        final boolean isDeviceIdle = mPhone.isDeviceIdle();
        final int curSubId = mPhone.getSubId();
        // The total number of record is small (10~15 tops). With each request has at most 5
        // SignalThresholdInfo which has at most 8 thresholds arrays. So the nested loop should
        // not be a concern here.
        for (SignalRequestRecord record : mSignalRequestRecords) {
            if (curSubId != record.mSubId
                    || (isDeviceIdle && !record.mRequest.isReportingRequestedWhileIdle())) {
                continue;
            }
            for (SignalThresholdInfo info : record.mRequest.getSignalThresholdInfos()) {
                if (isRanAndSignalMeasurementTypeMatch(ran, measurement, info)) {
                    for (int appThreshold : info.getThresholds()) {
                        target.add(appThreshold);
                    }
                }
            }
        }

        int[] targetArray = new int[target.size()];
        int i = 0;
        for (int element : target) {
            targetArray[i++] = element;
        }
        return targetArray;
    }

    /**
     * Return true if system thresholds should be honored when consolidating.
     */
    @VisibleForTesting
    public boolean shouldHonorSystemThresholds() {
        if (!mPhone.isDeviceIdle()) {
            return true;
        }

        final int curSubId = mPhone.getSubId();
        return mSignalRequestRecords.stream().anyMatch(
                srr -> curSubId == srr.mSubId
                        && srr.mRequest.isSystemThresholdReportingRequestedWhileIdle());
    }

    void onDeviceIdleStateChanged(boolean isDeviceIdle) {
        sendMessage(obtainMessage(EVENT_ON_DEVICE_IDLE_STATE_CHANGED, isDeviceIdle));
    }

    /**
     * Return true if signal threshold should be enabled due to the apps requests.
     */
    @VisibleForTesting
    public boolean shouldEnableSignalThresholdForAppRequest(
            @AccessNetworkConstants.RadioAccessNetworkType int ran,
            @SignalThresholdInfo.SignalMeasurementType int measurement,
            int subId,
            boolean isDeviceIdle) {
        for (SignalRequestRecord record : mSignalRequestRecords) {
            if (subId != record.mSubId) {
                continue;
            }
            for (SignalThresholdInfo info : record.mRequest.getSignalThresholdInfos()) {
                if (isRanAndSignalMeasurementTypeMatch(ran, measurement, info)
                        && (!isDeviceIdle || isSignalReportRequestedWhileIdle(record.mRequest))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setDefaultSignalStrengthReportingCriteria() {
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                AccessNetworkThresholds.GERAN, AccessNetworkConstants.AccessNetworkType.GERAN,
                true);
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
                AccessNetworkThresholds.UTRAN, AccessNetworkConstants.AccessNetworkType.UTRAN,
                true);
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                AccessNetworkThresholds.EUTRAN_RSRP,
                AccessNetworkConstants.AccessNetworkType.EUTRAN, true);
        mPhone.setSignalStrengthReportingCriteria(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                AccessNetworkThresholds.CDMA2000, AccessNetworkConstants.AccessNetworkType.CDMA2000,
                true);
        if (mPhone.getHalVersion().greaterOrEqual(RIL.RADIO_HAL_VERSION_1_5)) {
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                    AccessNetworkThresholds.EUTRAN_RSRQ,
                    AccessNetworkConstants.AccessNetworkType.EUTRAN, false);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                    AccessNetworkThresholds.EUTRAN_RSSNR,
                    AccessNetworkConstants.AccessNetworkType.EUTRAN, true);

            // Defaultly we only need SSRSRP for NGRAN signal criteria reporting
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                    AccessNetworkThresholds.NGRAN_RSRSRP,
                    AccessNetworkConstants.AccessNetworkType.NGRAN, true);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                    AccessNetworkThresholds.NGRAN_RSRSRQ,
                    AccessNetworkConstants.AccessNetworkType.NGRAN, false);
            mPhone.setSignalStrengthReportingCriteria(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                    AccessNetworkThresholds.NGRAN_SSSINR,
                    AccessNetworkConstants.AccessNetworkType.NGRAN, false);
        }
    }

    /**
     * dBm thresholds that correspond to changes in signal strength indications.
     */
    private static final class AccessNetworkThresholds {

        /**
         * List of dBm thresholds for GERAN {@link AccessNetworkConstants.AccessNetworkType}.
         *
         * Calculated from GSM asu level thresholds - TS 27.007 Sec 8.5
         */
        public static final int[] GERAN = new int[]{
                -109,
                -103,
                -97,
                -89,
        };

        /**
         * List of default dBm thresholds for UTRAN
         * {@link AccessNetworkConstants.AccessNetworkType}.
         *
         * These thresholds are taken from the WCDMA RSCP defaults in {@link CarrierConfigManager}.
         * See TS 27.007 Sec 8.69.
         */
        public static final int[] UTRAN = new int[]{
                -114, /* SIGNAL_STRENGTH_POOR */
                -104, /* SIGNAL_STRENGTH_MODERATE */
                -94,  /* SIGNAL_STRENGTH_GOOD */
                -84   /* SIGNAL_STRENGTH_GREAT */
        };

        /**
         * List of default dBm RSRP thresholds for EUTRAN
         * {@link AccessNetworkConstants.AccessNetworkType}.
         *
         * These thresholds are taken from the LTE RSRP defaults in {@link CarrierConfigManager}.
         */
        public static final int[] EUTRAN_RSRP = new int[]{
                -128, /* SIGNAL_STRENGTH_POOR */
                -118, /* SIGNAL_STRENGTH_MODERATE */
                -108, /* SIGNAL_STRENGTH_GOOD */
                -98,  /* SIGNAL_STRENGTH_GREAT */
        };

        /**
         * List of default dB RSRQ thresholds for EUTRAN
         * {@link AccessNetworkConstants.AccessNetworkType}.
         *
         * These thresholds are taken from the LTE RSRQ defaults in {@link CarrierConfigManager}.
         */
        public static final int[] EUTRAN_RSRQ = new int[]{
                -20,  /* SIGNAL_STRENGTH_POOR */
                -17,  /* SIGNAL_STRENGTH_MODERATE */
                -14,  /* SIGNAL_STRENGTH_GOOD */
                -11   /* SIGNAL_STRENGTH_GREAT */
        };

        /**
         * List of default dB RSSNR thresholds for EUTRAN
         * {@link AccessNetworkConstants.AccessNetworkType}.
         *
         * These thresholds are taken from the LTE RSSNR defaults in {@link CarrierConfigManager}.
         */
        public static final int[] EUTRAN_RSSNR = new int[]{
                -3,  /* SIGNAL_STRENGTH_POOR */
                1,   /* SIGNAL_STRENGTH_MODERATE */
                5,   /* SIGNAL_STRENGTH_GOOD */
                13   /* SIGNAL_STRENGTH_GREAT */
        };

        /**
         * List of dBm thresholds for CDMA2000 {@link AccessNetworkConstants.AccessNetworkType}.
         *
         * These correspond to EVDO level thresholds.
         */
        public static final int[] CDMA2000 = new int[]{
                -105,
                -90,
                -75,
                -65
        };

        /**
         * List of dB thresholds for NGRAN {@link AccessNetworkConstants.AccessNetworkType} RSRSRP
         */
        public static final int[] NGRAN_RSRSRP = new int[]{
                -110, /* SIGNAL_STRENGTH_POOR */
                -90, /* SIGNAL_STRENGTH_MODERATE */
                -80, /* SIGNAL_STRENGTH_GOOD */
                -65,  /* SIGNAL_STRENGTH_GREAT */
        };

        /**
         * List of dB thresholds for NGRAN {@link AccessNetworkConstants.AccessNetworkType} RSRSRP
         */
        public static final int[] NGRAN_RSRSRQ = new int[]{
                -31, /* SIGNAL_STRENGTH_POOR */
                -19, /* SIGNAL_STRENGTH_MODERATE */
                -7, /* SIGNAL_STRENGTH_GOOD */
                6  /* SIGNAL_STRENGTH_GREAT */
        };

        /**
         * List of dB thresholds for NGRAN {@link AccessNetworkConstants.AccessNetworkType} SSSINR
         */
        public static final int[] NGRAN_SSSINR = new int[]{
                -5, /* SIGNAL_STRENGTH_POOR */
                5, /* SIGNAL_STRENGTH_MODERATE */
                15, /* SIGNAL_STRENGTH_GOOD */
                30  /* SIGNAL_STRENGTH_GREAT */
        };
    }

    private static boolean isRanAndSignalMeasurementTypeMatch(
            @AccessNetworkConstants.RadioAccessNetworkType int ran,
            @SignalThresholdInfo.SignalMeasurementType int measurement,
            SignalThresholdInfo info) {
        return ran == info.getRadioAccessNetworkType()
                && measurement == info.getSignalMeasurementType();
    }

    private static boolean isSignalReportRequestedWhileIdle(SignalStrengthUpdateRequest request) {
        return request.isSystemThresholdReportingRequestedWhileIdle()
                || request.isReportingRequestedWhileIdle();
    }

    /**
     * Gets the carrier configuration values for a particular subscription.
     *
     * @return A {@link PersistableBundle} containing the config for the given subId,
     *         or default values for an invalid subId.
     */
    @NonNull
    private PersistableBundle getCarrierConfig() {
        CarrierConfigManager configManager = (CarrierConfigManager) mPhone.getContext()
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            // If an invalid subId is used, this bundle will contain default values.
            PersistableBundle config = configManager.getConfigForSubId(mPhone.getSubId());
            if (config != null) {
                return config;
            }
        }
        // Return static default defined in CarrierConfigManager.
        return CarrierConfigManager.getDefaultConfig();
    }

    private class SignalRequestRecord implements IBinder.DeathRecipient {
        final int mSubId; // subId the request originally applied to
        final int mCallingUid;
        final SignalStrengthUpdateRequest mRequest;

        SignalRequestRecord(int subId, int uid, @NonNull SignalStrengthUpdateRequest request) {
            this.mCallingUid = uid;
            this.mSubId = subId;
            this.mRequest = request;
        }

        @Override
        public void binderDied() {
            clearSignalStrengthUpdateRequest(mSubId, mCallingUid, mRequest, null /*onCompleted*/);
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer("SignalRequestRecord {");
            sb.append("mSubId=").append(mSubId);
            sb.append(" mCallingUid=").append(mCallingUid);
            sb.append(" mRequest=").append(mRequest).append("}");
            return sb.toString();
        }
    }

    private void updateAlwaysReportSignalStrength() {
        final int curSubId = mPhone.getSubId();
        boolean alwaysReport = mSignalRequestRecords.stream().anyMatch(
                srr -> srr.mSubId == curSubId && isSignalReportRequestedWhileIdle(srr.mRequest));

        // TODO(b/177924721): TM#setAlwaysReportSignalStrength will be removed and we will not
        // worry about unset flag which was set by other client.
        mPhone.setAlwaysReportSignalStrength(alwaysReport);
    }

    /**
     * Called when RIL is connected during boot up or after modem restart. Set the default criteria
     * so that modem can start with default state before updated criteria is ready.
     */
    private void onReset() {
        setDefaultSignalStrengthReportingCriteria();
    }

    private void log(String msg) {
        if (DBG) Rlog.d(TAG, msg);
    }
}
