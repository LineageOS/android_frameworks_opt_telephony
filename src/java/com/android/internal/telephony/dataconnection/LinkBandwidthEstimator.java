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

package com.android.internal.telephony.dataconnection;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Message;
import android.os.OutcomeReceiver;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.ModemActivityInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.LocalLog;
import android.util.Pair;
import android.view.Display;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyFacade;
import com.android.internal.util.IndentingPrintWriter;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;

/**
 * Link Bandwidth Estimator based on the byte counts in TrafficStats and the time reported in modem
 * activity.
 */
public class LinkBandwidthEstimator extends Handler {
    private static final String TAG = LinkBandwidthEstimator.class.getSimpleName();
    private static final boolean DBG = false;
    @VisibleForTesting
    static final int MSG_SCREEN_STATE_CHANGED = 1;
    @VisibleForTesting
    static final int MSG_TRAFFIC_STATS_POLL = 2;
    @VisibleForTesting
    static final int MSG_MODEM_ACTIVITY_RETURNED = 3;
    @VisibleForTesting
    static final int MSG_DEFAULT_NETWORK_CHANGED = 4;
    @VisibleForTesting
    static final int MSG_SIGNAL_STRENGTH_CHANGED = 5;
    @VisibleForTesting
    static final int MSG_NR_FREQUENCY_CHANGED = 6;
    @VisibleForTesting
    static final int MSG_NR_STATE_CHANGED = 7;
    static final int MSG_CARRIER_CONFIG_LINK_BANDWIDTHS_CHANGED = 8;

    // TODO: move the following parameters to xml file
    private static final int TRAFFIC_STATS_POLL_INTERVAL_MS = 1_000;
    private static final int MODEM_POLL_MIN_INTERVAL_MS = 5_000;
    private static final int TRAFFIC_MODEM_POLL_BYTE_RATIO = 8;
    private static final int TRAFFIC_POLL_BYTE_THRESHOLD_MAX = 20_000;
    private static final int BYTE_DELTA_ACC_THRESHOLD_MAX_KB = 5_000;
    private static final int MODEM_POLL_TIME_DELTA_MAX_MS = 15_000;
    private static final int FILTER_UPDATE_MAX_INTERVAL_MS = 5_100;
    // The large time constant used in BW filter
    private static final int TIME_CONSTANT_LARGE_SEC = 30;
    // The small time constant used in BW filter
    private static final int TIME_CONSTANT_SMALL_SEC = 6;
    // If RSSI changes by more than the below value, update BW filter with small time constant
    private static final int RSSI_DELTA_THRESHOLD_DB = 6;
    // The up-scaling factor of filter coefficient.
    private static final int FILTER_SCALE = 128;
    // Force weight to 0 if the elapsed time is above LARGE_TIME_DECAY_RATIO * time constant
    private static final int LARGE_TIME_DECAY_RATIO = 4;
    // Modem Tx time may contain Rx time as defined in HAL. To work around the issue, if Tx time
    // over Rx time ratio is above the following value, use Tx time + Rx time as Rx time.
    private static final int TX_OVER_RX_TIME_RATIO_THRESHOLD_NUM = 3;
    private static final int TX_OVER_RX_TIME_RATIO_THRESHOLD_DEN = 2;
    // Default Link bandwidth value if the RAT entry is not found in carrier config table.
    private static final int DEFAULT_LINK_BAND_WIDTH_KBPS = 14;
    // If Tx or Rx link bandwidth change is above the following value, send the BW update
    private static final int BW_UPDATE_THRESHOLD_PERCENT = 40;

    // To be used in link bandwidth estimation, each TrafficStats poll sample needs to be above
    // a predefine threshold.
    // For RAT with carrier config above HIGH_BANDWIDTH_THRESHOLD_KBPS, it uses the following table.
    // For others RATs, the thresholds are derived from the default carrier config BW values.
    // The following table is defined per signal level, int [NUM_SIGNAL_LEVEL].
    static final int HIGH_BANDWIDTH_THRESHOLD_KBPS = 5000;
    static final int[] LINK_BANDWIDTH_BYTE_DELTA_THRESHOLD_KB = {250, 500, 750, 1000, 1000};
    // Used to derive byte count threshold from avg BW
    static final int AVG_BW_TO_LOW_BW_RATIO = 4;

    // To be used in the long term avg, each count needs to be above the following value
    static final int BW_STATS_COUNT_THRESHOLD = 5;
    static final int NUM_SIGNAL_LEVEL = 5;
    static final int LINK_TX = 0;
    static final int LINK_RX = 1;
    private static final int NUM_LINK_DIRECTION = 2;

    private final Phone mPhone;
    private final TelephonyFacade mTelephonyFacade;
    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;
    private final LocalLog mLocalLog = new LocalLog(512);
    private boolean mScreenOn = false;
    private boolean mIsOnDefaultRoute = false;
    private long mLastModemPollTimeMs;

    private long mLastMobileTxBytes;
    private long mLastMobileRxBytes;
    private long mTxBytesDeltaAcc;
    private long mRxBytesDeltaAcc;

    private ModemActivityInfo mLastModemActivityInfo = null;
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListenerImpl();
    private int mSignalStrengthDbm;
    private int mSignalLevel;
    private int mDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int mTac;
    private String mPlmn = "";
    private NetworkCapabilities mNetworkCapabilities;
    private NetworkBandwidth mPlaceholderNetwork;

    private long mFilterUpdateTimeMs;

    private int mBandwidthUpdateSignalDbm = -1;
    private int mBandwidthUpdateDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private String mBandwidthUpdatePlmn = "";
    private BandwidthState mTxState = new BandwidthState(LINK_TX);
    private BandwidthState mRxState = new BandwidthState(LINK_RX);

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) { }

                @Override
                public void onDisplayRemoved(int displayId) { }

                @Override
                public void onDisplayChanged(int displayId) {
                    obtainMessage(MSG_SCREEN_STATE_CHANGED, isScreenOn()).sendToTarget();
                }
            };

    private final OutcomeReceiver<ModemActivityInfo, TelephonyManager.ModemActivityInfoException>
            mOutcomeReceiver =
            new OutcomeReceiver<ModemActivityInfo, TelephonyManager.ModemActivityInfoException>() {
                @Override
                public void onResult(ModemActivityInfo result) {
                    obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, result).sendToTarget();
                }

                @Override
                public void onError(TelephonyManager.ModemActivityInfoException e) {
                    Rlog.e(TAG, "error reading modem stats:" + e);
                    obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, null).sendToTarget();
                }
            };

    private final ConnectivityManager.NetworkCallback mDefaultNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, networkCapabilities).sendToTarget();
                }

                public void onLost(@NonNull Network network) {
                    obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, null).sendToTarget();
                }
            };

    public LinkBandwidthEstimator(Phone phone, TelephonyFacade telephonyFacade) {
        mPhone = phone;
        mTelephonyFacade = telephonyFacade;
        mTelephonyManager = phone.getContext()
                .getSystemService(TelephonyManager.class)
                .createForSubscriptionId(phone.getSubId());
        mConnectivityManager = phone.getContext().getSystemService(ConnectivityManager.class);
        DisplayManager dm = (DisplayManager) phone.getContext().getSystemService(
                Context.DISPLAY_SERVICE);
        dm.registerDisplayListener(mDisplayListener, null);
        handleScreenStateChanged(isScreenOn());
        mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback, this);
        mTelephonyManager.registerPhoneStateListener(new HandlerExecutor(this),
                mPhoneStateListener);
        mPlaceholderNetwork = new NetworkBandwidth("", "");
        registerDataServiceState();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SCREEN_STATE_CHANGED:
                handleScreenStateChanged((boolean) msg.obj);
                break;
            case MSG_TRAFFIC_STATS_POLL:
                handleTrafficStatsPoll();
                break;
            case MSG_MODEM_ACTIVITY_RETURNED:
                handleModemActivityReturned((ModemActivityInfo) msg.obj);
                break;
            case MSG_DEFAULT_NETWORK_CHANGED:
                handleDefaultNetworkChanged((NetworkCapabilities) msg.obj);
                break;
            case MSG_SIGNAL_STRENGTH_CHANGED:
                handleSignalStrengthChanged((SignalStrength) msg.obj);
                break;
            case MSG_NR_FREQUENCY_CHANGED:
                // fall through
            case MSG_NR_STATE_CHANGED:
                // fall through
            case MSG_CARRIER_CONFIG_LINK_BANDWIDTHS_CHANGED:
                checkUpdateColdStartValueResetFilter();
                break;
            default:
                Rlog.e(TAG, "invalid message " + msg.what);
                break;
        }
    }

    /**
     * @return True if one the device's screen (e.g. main screen, wifi display, HDMI display etc...)
     * is on.
     */
    private boolean isScreenOn() {
        // Note that we don't listen to Intent.SCREEN_ON and Intent.SCREEN_OFF because they are no
        // longer adequate for monitoring the screen state since they are not sent in cases where
        // the screen is turned off transiently such as due to the proximity sensor.
        final DisplayManager dm = (DisplayManager) mPhone.getContext().getSystemService(
                Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        if (displays != null) {
            for (Display display : displays) {
                // Anything other than STATE_ON is treated as screen off, such as STATE_DOZE,
                // STATE_DOZE_SUSPEND, etc...
                if (display.getState() == Display.STATE_ON) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    private void handleScreenStateChanged(boolean screenOn) {
        if (mScreenOn == screenOn) {
            return;
        }
        mScreenOn = screenOn;
        handleTrafficStatsPollConditionChanged();
    }

    private void handleDefaultNetworkChanged(NetworkCapabilities networkCapabilities) {
        mNetworkCapabilities = networkCapabilities;
        boolean isOnDefaultRoute;
        if (networkCapabilities == null) {
            isOnDefaultRoute = false;
        } else {
            isOnDefaultRoute = networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
        }
        if (mIsOnDefaultRoute == isOnDefaultRoute) {
            return;
        }
        mIsOnDefaultRoute = isOnDefaultRoute;
        handleTrafficStatsPollConditionChanged();
    }

    private void handleTrafficStatsPollConditionChanged() {
        removeMessages(MSG_TRAFFIC_STATS_POLL);
        if (mScreenOn && mIsOnDefaultRoute) {
            updateDataRatCellIdentity();
            handleTrafficStatsPoll();
        }
    }

    private void handleTrafficStatsPoll() {
        long mobileTxBytes = mTelephonyFacade.getMobileTxBytes();
        long mobileRxBytes = mTelephonyFacade.getMobileRxBytes();
        long txBytesDelta = mobileTxBytes - mLastMobileTxBytes;
        long rxBytesDelta = mobileRxBytes - mLastMobileRxBytes;
        mLastMobileTxBytes = mobileTxBytes;
        mLastMobileRxBytes = mobileRxBytes;
        mTxBytesDeltaAcc += txBytesDelta;
        mRxBytesDeltaAcc += rxBytesDelta;
        // Schedule the next traffic stats poll
        sendEmptyMessageDelayed(MSG_TRAFFIC_STATS_POLL, TRAFFIC_STATS_POLL_INTERVAL_MS);

        boolean doModemPoll = true;
        // Check if it meets the requirement to request modem activity
        long txByteDeltaThr = Math.min(mTxState.mByteDeltaAccThr / TRAFFIC_MODEM_POLL_BYTE_RATIO,
                TRAFFIC_POLL_BYTE_THRESHOLD_MAX);
        long rxByteDeltaThr = Math.min(mRxState.mByteDeltaAccThr / TRAFFIC_MODEM_POLL_BYTE_RATIO,
                TRAFFIC_POLL_BYTE_THRESHOLD_MAX);
        if (txBytesDelta < txByteDeltaThr && rxBytesDelta < rxByteDeltaThr
                && mTxBytesDeltaAcc < mTxState.mByteDeltaAccThr
                && mRxBytesDeltaAcc < mRxState.mByteDeltaAccThr) {
            doModemPoll = false;
        }

        long currTimeMs = mTelephonyFacade.getElapsedSinceBootMillis();
        long timeSinceLastModemPollMs = currTimeMs - mLastModemPollTimeMs;
        if (timeSinceLastModemPollMs < MODEM_POLL_MIN_INTERVAL_MS) {
            doModemPoll = false;
        }

        if (doModemPoll) {
            StringBuilder sb = new StringBuilder();
            logd(sb.append("TxByteDelta ").append(txBytesDelta)
                    .append(" RxByteDelta ").append(rxBytesDelta)
                    .append("TxByteDeltaAcc ").append(mTxBytesDeltaAcc)
                    .append(" RxByteDeltaAcc ").append(mRxBytesDeltaAcc)
                    .append(" trigger modem activity request").toString());
            updateDataRatCellIdentity();
            // Filter update will happen after the request
            makeRequestModemActivity();
            return;
        }

        long timeSinceLastFilterUpdateMs = currTimeMs - mFilterUpdateTimeMs;
        // Update filter
        if (timeSinceLastFilterUpdateMs >= FILTER_UPDATE_MAX_INTERVAL_MS) {
            updateDataRatCellIdentity();
            updateTxRxBandwidthFilterSendToDataConnection();
        }
    }

    private void makeRequestModemActivity() {
        mLastModemPollTimeMs = mTelephonyFacade.getElapsedSinceBootMillis();
        // TODO: add CountDown in case that onResult/OnError() never happen
        mTelephonyManager.requestModemActivityInfo(Runnable::run, mOutcomeReceiver);
    }

    private void handleModemActivityReturned(ModemActivityInfo result) {
        updateBandwidthTxRxSamples(result);
        updateTxRxBandwidthFilterSendToDataConnection();
        mLastModemActivityInfo = result;
        resetByteDeltaAcc();
    }

    private void resetByteDeltaAcc() {
        mTxBytesDeltaAcc = 0;
        mRxBytesDeltaAcc = 0;
    }

    private void updateBandwidthTxRxSamples(ModemActivityInfo modemActivityInfo) {
        mTxState.mBandwidthSampleValid = false;
        mRxState.mBandwidthSampleValid = false;
        if (mLastModemActivityInfo == null || modemActivityInfo == null
                || mNetworkCapabilities == null) {
            return;
        }

        long lastTimeMs = mLastModemActivityInfo.getTimestampMillis();
        long currTimeMs = modemActivityInfo.getTimestampMillis();
        long timeDeltaMs = currTimeMs - lastTimeMs;

        if (timeDeltaMs > MODEM_POLL_TIME_DELTA_MAX_MS || timeDeltaMs <= 0) {
            return;
        }
        ModemActivityInfo deltaInfo = mLastModemActivityInfo.getDelta(modemActivityInfo);
        long txTimeDeltaMs = getModemTxTimeMs(deltaInfo);
        long rxTimeDeltaMs = deltaInfo.getReceiveTimeMillis();

        // Check if txTimeDeltaMs / rxTimeDeltaMs > TX_OVER_RX_TIME_RATIO_THRESHOLD
        boolean isTxTimeOverRxTimeRatioLarge = (txTimeDeltaMs * TX_OVER_RX_TIME_RATIO_THRESHOLD_DEN
                > rxTimeDeltaMs * TX_OVER_RX_TIME_RATIO_THRESHOLD_NUM);
        long rxTimeBwEstMs = isTxTimeOverRxTimeRatioLarge
                ? (txTimeDeltaMs + rxTimeDeltaMs) : rxTimeDeltaMs;

        mTxState.updateBandwidthSample(mTxBytesDeltaAcc, txTimeDeltaMs);
        mRxState.updateBandwidthSample(mRxBytesDeltaAcc, rxTimeBwEstMs);

        int l2TxTputKbps = mNetworkCapabilities.getLinkUpstreamBandwidthKbps();
        int l2RxTputKbps = mNetworkCapabilities.getLinkDownstreamBandwidthKbps();

        StringBuilder sb = new StringBuilder();
        logd(sb.append(" dBm=").append(mSignalStrengthDbm)
                .append(" level=").append(mSignalLevel)
                .append(" rat=").append(getDataRatName(mDataRat))
                .append(" plmn=").append(mPlmn)
                .append(" tac=").append(mTac)
                .append(" l2TxKbps=").append(l2TxTputKbps)
                .append(" l2RxKbps=").append(l2RxTputKbps)
                .append(" txMs=").append(txTimeDeltaMs)
                .append(" rxMs=").append(rxTimeDeltaMs)
                .append(" txKB=").append(mTxBytesDeltaAcc / 1024)
                .append(" rxKB=").append(mRxBytesDeltaAcc / 1024)
                .append(" TxMbps=").append(mTxState.mBandwidthSampleKbps / 1024)
                .append(" RxMbps=").append(mRxState.mBandwidthSampleKbps / 1024)
                .append(" TxValid=").append(mTxState.mBandwidthSampleValid)
                .append(" RxValid=").append(mRxState.mBandwidthSampleValid)
                .toString());
    }

    private long getModemTxTimeMs(ModemActivityInfo modemActivity) {
        long txTimeMs = 0;
        for (int lvl = 0; lvl < ModemActivityInfo.getNumTxPowerLevels(); lvl++) {
            txTimeMs += modemActivity.getTransmitDurationMillisAtPowerLevel(lvl);
        }
        return txTimeMs;
    }

    private void updateTxRxBandwidthFilterSendToDataConnection() {
        mFilterUpdateTimeMs = mTelephonyFacade.getElapsedSinceBootMillis();
        mTxState.updateBandwidthFilter();
        mRxState.updateBandwidthFilter();

        int txDeltaKbps = Math.abs(mTxState.mLastReportedBwKbps - mTxState.mFilterKbps);
        int rxDeltaKbps = Math.abs(mRxState.mLastReportedBwKbps - mRxState.mFilterKbps);
        if ((txDeltaKbps * 100  >  BW_UPDATE_THRESHOLD_PERCENT * mTxState.mLastReportedBwKbps)
                || (rxDeltaKbps * 100  >  BW_UPDATE_THRESHOLD_PERCENT
                * mRxState.mLastReportedBwKbps)
                || mBandwidthUpdateDataRat != mDataRat
                || !mBandwidthUpdatePlmn.equals(mPlmn)) {
            sendLinkBandwidthToDataConnection(mTxState.mFilterKbps, mRxState.mFilterKbps);
            mTxState.mLastReportedBwKbps = mTxState.mFilterKbps;
            mRxState.mLastReportedBwKbps = mRxState.mFilterKbps;
        }
        mBandwidthUpdateSignalDbm = mSignalStrengthDbm;
        mBandwidthUpdateDataRat = mDataRat;
        mBandwidthUpdatePlmn = mPlmn;
    }

    private class BandwidthState {
        private final int mLink;
        int mFilterKbps;
        int mByteDeltaAccThr;
        int mBandwidthSampleKbps;
        boolean mBandwidthSampleValid;
        long mBandwidthSampleValidTimeMs;
        int mBandwidthColdStartKbps;
        int mLastReportedBwKbps;

        BandwidthState(int link) {
            mLink = link;
        }

        private void updateBandwidthSample(long bytesDelta, long timeDeltaMs) {
            if (bytesDelta < mByteDeltaAccThr) {
                return;
            }
            if (timeDeltaMs <= 0) {
                return;
            }
            int linkBandwidthKbps = (int) (bytesDelta * 8 * 1000 / timeDeltaMs / 1024);
            mBandwidthSampleValid = true;
            mBandwidthSampleKbps = linkBandwidthKbps;

            String dataRatName = getDataRatName(mDataRat);
            NetworkBandwidth network = lookupNetwork(mPlmn, dataRatName);
            // Update per RAT stats of all TAC
            network.linkBandwidthStats.increment(linkBandwidthKbps, mLink, mSignalLevel);

            // Update per TAC stats
            LocalAreaNetworkBandwidth localNetwork = lookupLocalNetwork(mPlmn, mTac, dataRatName);
            localNetwork.linkBandwidthStats.increment(linkBandwidthKbps, mLink, mSignalLevel);
        }

        private void updateBandwidthFilter() {
            int avgKbps = getAvgLinkBandwidthKbps();
            // Feed the filter with the long term avg if there is no valid BW sample so that filter
            // will gradually converge the long term avg.
            int filterInKbps = mBandwidthSampleValid ? mBandwidthSampleKbps : avgKbps;

            long currTimeMs = mTelephonyFacade.getElapsedSinceBootMillis();
            int timeDeltaSec = (int) (currTimeMs - mBandwidthSampleValidTimeMs) / 1000;

            // If the operation condition changes significantly since the last update
            // or the sample has higher BW, use a faster filter. Otherwise, use a slow filter
            int timeConstantSec;
            if (Math.abs(mBandwidthUpdateSignalDbm - mSignalStrengthDbm) > RSSI_DELTA_THRESHOLD_DB
                    || !mBandwidthUpdatePlmn.equals(mPlmn)
                    || mBandwidthUpdateDataRat != mDataRat
                    || (mBandwidthSampleValid && mBandwidthSampleKbps > avgKbps)) {
                timeConstantSec = TIME_CONSTANT_SMALL_SEC;
            } else {
                timeConstantSec = TIME_CONSTANT_LARGE_SEC;
            }
            // Update timestamp for next iteration
            if (mBandwidthSampleValid) {
                mBandwidthSampleValidTimeMs = currTimeMs;
                mBandwidthSampleValid = false;
            }

            if (filterInKbps == mFilterKbps) {
                logd(mLink + " skip filter because the same input / current = " + filterInKbps);
                return;
            }

            int alpha = timeDeltaSec > LARGE_TIME_DECAY_RATIO * timeConstantSec ? 0
                    : (int) (FILTER_SCALE * Math.exp(-1.0 * timeDeltaSec / timeConstantSec));
            mFilterKbps = alpha == 0 ? filterInKbps : ((mFilterKbps * alpha
                    + filterInKbps * FILTER_SCALE - filterInKbps * alpha) / FILTER_SCALE);
            StringBuilder sb = new StringBuilder();
            logd(sb.append(mLink)
                    .append(" lastSampleWeight=").append(alpha)
                    .append("/").append(FILTER_SCALE)
                    .append(" filterInKbps=").append(filterInKbps)
                    .append(" avgKbps=").append(avgKbps)
                    .append(" filterOutKbps=").append(mFilterKbps)
                    .toString());
        }

        private int getAvgUsedLinkBandwidthKbps() {
            // Check if current TAC/RAT/level has enough stats
            String dataRatName = getDataRatName(mDataRat);
            LinkBandwidthStats linkBandwidthStats =
                    lookupLocalNetwork(mPlmn, mTac, dataRatName).linkBandwidthStats;
            int count = linkBandwidthStats.getCount(mLink, mSignalLevel);
            if (count >= BW_STATS_COUNT_THRESHOLD) {
                return (int) (linkBandwidthStats.getValue(mLink, mSignalLevel) / count);
            }

            // Check if current RAT/level has enough stats
            linkBandwidthStats = lookupNetwork(mPlmn, dataRatName).linkBandwidthStats;
            count = linkBandwidthStats.getCount(mLink, mSignalLevel);
            if (count >= BW_STATS_COUNT_THRESHOLD) {
                return (int) (linkBandwidthStats.getValue(mLink, mSignalLevel) / count);
            }
            return -1;
        }

        /** get a long term avg value (PLMN/RAT/TAC/level dependent) or carrier config value */
        private int getAvgLinkBandwidthKbps() {
            int avgUsagKbps = getAvgUsedLinkBandwidthKbps();

            if (avgUsagKbps > 0) {
                return avgUsagKbps;
            }
            // Fall back to cold start value
            return mBandwidthColdStartKbps;
        }

        private void resetBandwidthFilter() {
            mFilterKbps = getAvgLinkBandwidthKbps();
        }

        private void updateByteCountThr() {
            // For high BW RAT cases, use predefined value + threshold derived from avg usage BW
            if (mBandwidthColdStartKbps > HIGH_BANDWIDTH_THRESHOLD_KBPS) {
                int lowBytes = calculateByteCountThreshold(getAvgUsedLinkBandwidthKbps(),
                        MODEM_POLL_MIN_INTERVAL_MS);
                // Start with a predefined value
                mByteDeltaAccThr = LINK_BANDWIDTH_BYTE_DELTA_THRESHOLD_KB[mSignalLevel] * 1024;
                if (lowBytes > 0) {
                    // Raise the threshold if the avg usage BW is high
                    mByteDeltaAccThr = Math.max(lowBytes, mByteDeltaAccThr);
                    mByteDeltaAccThr = Math.min(mByteDeltaAccThr,
                            BYTE_DELTA_ACC_THRESHOLD_MAX_KB * 1024);
                }
                return;
            }
            // For low BW RAT cases, derive the threshold from carrier config BW values
            mByteDeltaAccThr = calculateByteCountThreshold(mBandwidthColdStartKbps,
                    MODEM_POLL_MIN_INTERVAL_MS);
            // Low BW RAT threshold value should be no more than high BW one.
            mByteDeltaAccThr = Math.min(mByteDeltaAccThr,
                    LINK_BANDWIDTH_BYTE_DELTA_THRESHOLD_KB[0] * 1024);
        }

        // Calculate a byte count threshold for the given avg BW and observation window size
        private int calculateByteCountThreshold(int avgBwKbps, int durationMs) {
            return avgBwKbps / 8 * durationMs / AVG_BW_TO_LOW_BW_RATIO;
        }
    }

    /**
     * Update the byte count threshold.
     * It should be called whenever the RAT, signal level or carrier config is changed.
     * For the RAT with high BW (4G and beyond), use LINK_BANDWIDTH_BYTE_DELTA_THRESHOLD_KB table.
     * For other RATs, derive the threshold based on the carrier config avg BW values.
     */
    private void updateByteCountThr() {
        mTxState.updateByteCountThr();
        mRxState.updateByteCountThr();
        logd("ByteAccThr tx:" + mTxState.mByteDeltaAccThr + " rx:" + mRxState.mByteDeltaAccThr);
    }

    // Reset BW filter to a long term avg value (PLMN/RAT/TAC dependent) or carrier config value.
    // It should be called whenever PLMN/RAT/carrier config is changed;
    private void resetBandwidthFilter() {
        StringBuilder sb = new StringBuilder();
        logd(sb.append("Reset BW filter ")
                .append(" dBm=").append(mSignalStrengthDbm)
                .append(" level=").append(mSignalLevel)
                .append(" rat=").append(getDataRatName(mDataRat))
                .append(" plmn=").append(mPlmn)
                .append(" tac=").append(mTac)
                .toString());
        mTxState.resetBandwidthFilter();
        mRxState.resetBandwidthFilter();
    }

    private void sendLinkBandwidthToDataConnection(int linkBandwidthTxKps, int linkBandwidthRxKps) {
        DcTracker dt = mPhone.getDcTracker(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (dt == null) {
            return;
        }
        DataConnection dc = dt.getDataConnectionByApnType(PhoneConstants.APN_TYPE_DEFAULT);
        if (dc == null) {
            return;
        }
        logd("Update DC BW, tx " + linkBandwidthTxKps + " rx " + linkBandwidthRxKps);
        dc.updateLinkBandwidthEstimation(linkBandwidthTxKps, linkBandwidthRxKps);
    }

    private void handleSignalStrengthChanged(SignalStrength signalStrength) {
        if (signalStrength == null) {
            return;
        }

        updateDataRatCellIdentity();

        mSignalStrengthDbm = signalStrength.getDbm();
        mSignalLevel = signalStrength.getLevel();
        if (Math.abs(mBandwidthUpdateSignalDbm - mSignalStrengthDbm) > RSSI_DELTA_THRESHOLD_DB) {
            updateByteCountThr();
            updateTxRxBandwidthFilterSendToDataConnection();
        }
    }

    private void registerDataServiceState() {
        mPhone.getServiceStateTracker().registerForNrStateChanged(this,
                MSG_NR_STATE_CHANGED, null);
        mPhone.getServiceStateTracker().registerForNrFrequencyChanged(this,
                MSG_NR_FREQUENCY_CHANGED, null);
    }

    private String getDataRatName(int rat) {
        if (rat == TelephonyManager.NETWORK_TYPE_LTE && isNRConnected()) {
            return mPhone.getServiceState().getNrFrequencyRange()
                    == ServiceState.FREQUENCY_RANGE_MMWAVE
                    ? DctConstants.RAT_NAME_NR_NSA_MMWAVE : DctConstants.RAT_NAME_NR_NSA;
        }
        return TelephonyManager.getNetworkTypeName(rat);
    }

    // Update BW cold start values.
    // It should be called whenever the RAT could be changed.
    // return true if cold start value is changed;
    private boolean updateColdStartValueFromCarrierConfig() {
        DcTracker dt = mPhone.getDcTracker(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (dt == null) {
            return false;
        }
        String dataRatName = getDataRatName(mDataRat);
        Pair<Integer, Integer> values = dt.getLinkBandwidthsFromCarrierConfig(dataRatName);
        if (values == null) {
            Rlog.e(TAG, dataRatName + " returns null CarrierConfig BW");
            mTxState.mBandwidthColdStartKbps = DEFAULT_LINK_BAND_WIDTH_KBPS;
            mRxState.mBandwidthColdStartKbps = DEFAULT_LINK_BAND_WIDTH_KBPS;
            return true;
        }
        if (mTxState.mBandwidthColdStartKbps != values.second
                || mRxState.mBandwidthColdStartKbps != values.first) {
            mTxState.mBandwidthColdStartKbps = values.second;
            mRxState.mBandwidthColdStartKbps = values.first;
            return true;
        }
        return false;
    }

    private void checkUpdateColdStartValueResetFilter() {
        if (updateColdStartValueFromCarrierConfig()) {
            updateByteCountThr();
            resetBandwidthFilter();
            updateTxRxBandwidthFilterSendToDataConnection();
        }
    }

    /** Check if the device is connected to NR 5G Non-Standalone network. */
    private boolean isNRConnected() {
        return mPhone.getServiceState().getNrState()
                == NetworkRegistrationInfo.NR_STATE_CONNECTED;
    }

    private void updateDataRatCellIdentity() {
        boolean updatedPlmn = false;
        CellIdentity cellIdentity = mPhone.getCurrentCellIdentity();
        mTac = getTac(cellIdentity);
        String plmn;
        if (cellIdentity.getPlmn() != null) {
            plmn = cellIdentity.getPlmn();
        } else {
            if (cellIdentity.getOperatorAlphaShort() != null) {
                plmn = cellIdentity.getOperatorAlphaShort().toString();
            } else {
                plmn = "";
            }
        }
        if (mPlmn == null || !plmn.equals(mPlmn)) {
            updatedPlmn = true;
            mPlmn = plmn;
        }

        boolean updatedRat = false;
        NetworkRegistrationInfo nri = mPhone.getServiceState().getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (nri != null) {
            int dataRat = nri.getAccessNetworkTechnology();
            if (dataRat != mDataRat) {
                updatedRat = true;
                mDataRat = dataRat;
                updateColdStartValueFromCarrierConfig();
                updateByteCountThr();
            }
        }

        if (updatedPlmn || updatedRat) {
            resetBandwidthFilter();
            updateTxRxBandwidthFilterSendToDataConnection();
        }
    }

    private int getTac(@NonNull CellIdentity cellIdentity) {
        if (cellIdentity instanceof CellIdentityLte) {
            return ((CellIdentityLte) cellIdentity).getTac();
        }
        if (cellIdentity instanceof CellIdentityNr) {
            return ((CellIdentityNr) cellIdentity).getTac();
        }
        if (cellIdentity instanceof CellIdentityWcdma) {
            return ((CellIdentityWcdma) cellIdentity).getLac();
        }
        if (cellIdentity instanceof CellIdentityTdscdma) {
            return ((CellIdentityTdscdma) cellIdentity).getLac();
        }
        if (cellIdentity instanceof CellIdentityGsm) {
            return ((CellIdentityGsm) cellIdentity).getLac();
        }
        return 0;
    }

    private class PhoneStateListenerImpl extends PhoneStateListener
            implements PhoneStateListener.SignalStrengthsChangedListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, signalStrength).sendToTarget();
        }
    }

    void logd(String msg) {
        if (DBG) Rlog.d(TAG, msg);
        mLocalLog.log(msg);
    }

    // Map with NetworkKey as the key and NetworkBandwidth as the value.
    // NetworkKey is specified by the PLMN, data RAT and TAC of network.
    // If TAC is not available, default TAC value (-1) is used.
    // NetworkBandwidth represents the bandwidth related stats of each network.
    private final Map<NetworkKey, NetworkBandwidth> mNetworkMap = new ArrayMap<>();
    private static class NetworkKey {
        private final String mPlmn;
        private final String mDataRat;
        private final int mTac;
        NetworkKey(String plmn, int tac, String dataRat) {
            mPlmn = plmn;
            mTac = tac;
            mDataRat = dataRat;
        }
        NetworkKey(String plmn, String dataRat) {
            mPlmn = plmn;
            mTac = -1;
            mDataRat = dataRat;
        }
        @Override
        public boolean equals(@Nullable Object o) {
            if (o == null || !(o instanceof NetworkKey) || hashCode() != o.hashCode()) {
                return false;
            }

            if (this == o) {
                return true;
            }

            NetworkKey that = (NetworkKey) o;
            return mPlmn.equals(that.mPlmn)
                    && mTac == that.mTac
                    && mDataRat.equals(that.mDataRat);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPlmn, mDataRat, mTac);
        }

    }
    private NetworkBandwidth lookupNetwork(String plmn, String dataRat) {
        if (plmn == null) {
            return mPlaceholderNetwork;
        }
        NetworkKey key = new NetworkKey(plmn, dataRat);
        NetworkBandwidth ans = mNetworkMap.get(key);
        if (ans == null) {
            ans = new NetworkBandwidth(plmn, dataRat);
            mNetworkMap.put(key, ans);
        }
        return ans;
    }

    private static class NetworkBandwidth {
        protected final String mPlmn;
        protected final NetworkKey mKey;
        protected final String mDataRat;
        public final LinkBandwidthStats linkBandwidthStats;
        NetworkBandwidth(String plmn, String dataRat) {
            mKey = new NetworkKey(plmn, dataRat);
            mPlmn = plmn;
            mDataRat = dataRat;
            linkBandwidthStats = new LinkBandwidthStats();
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(" PLMN ").append(mPlmn)
                    .append(" RAT ").append(mDataRat)
                    .append(" Stats \n").append(linkBandwidthStats);
            return sb.toString();
        }
    }

    private LocalAreaNetworkBandwidth lookupLocalNetwork(String plmn, int tac, String dataRat) {
        NetworkKey key = new NetworkKey(plmn, tac, dataRat);
        LocalAreaNetworkBandwidth ans = (LocalAreaNetworkBandwidth) mNetworkMap.get(key);
        if (ans == null) {
            ans = new LocalAreaNetworkBandwidth(plmn, tac, dataRat);
            mNetworkMap.put(key, ans);
        }
        return ans;
    }

    private static class LocalAreaNetworkBandwidth extends NetworkBandwidth {
        private final int mTac;
        LocalAreaNetworkBandwidth(String plmn, int tac, String dataRat) {
            super(plmn, dataRat);
            mTac = tac;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(" PLMN ").append(mPlmn)
                    .append(" RAT ").append(mDataRat)
                    .append(" TAC ").append(mTac)
                    .append(" Stats \n").append(linkBandwidthStats);
            return sb.toString();
        }
    }

    private static class LinkBandwidthStats {
        // Stats per signal level
        private final long[][] mValue = new long[NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
        private final int[][] mCount = new int[NUM_LINK_DIRECTION][NUM_SIGNAL_LEVEL];
        void increment(long value, int link, int level) {
            mValue[link][level] += value;
            mCount[link][level]++;
        }
        int getCount(int link, int level) {
            return mCount[link][level];
        }
        long getValue(int link, int level) {
            return mValue[link][level];
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < NUM_LINK_DIRECTION; i++) {
                sb.append(" i = " + i);
                for (int j = 0; j < NUM_SIGNAL_LEVEL; j++) {
                    sb.append(" j = " + j);
                    sb.append(" value: " + mValue[i][j]);
                    sb.append(" count: " + mCount[i][j]);
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Dump the internal state and local logs
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, " ");
        pw.increaseIndent();
        pw.println("current PLMN " + mPlmn + " TAC " + mTac + " RAT " + getDataRatName(mDataRat));
        pw.println("all recent networks ");
        for (NetworkBandwidth network : mNetworkMap.values()) {
            pw.println(network.toString());
        }

        try {
            mLocalLog.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.decreaseIndent();
        pw.println();
        pw.flush();
    }
}
