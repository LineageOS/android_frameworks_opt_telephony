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
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Message;
import android.os.OutcomeReceiver;
import android.telephony.CellSignalStrength;
import android.telephony.ModemActivityInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Display;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyFacade;
import com.android.telephony.Rlog;

import java.util.List;

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
    private static final int MSG_SIGNAL_STRENGTH_CHANGED = 5;

    private static final int TRAFFIC_STATS_POLL_INTERVAL_MS = 1_000;
    private static final int MODEM_POLL_BYTE_DELTA_THR = 20_000;
    private static final int MODEM_POLL_BYTE_DELTA_ACC_THR = 500_000;
    private static final int MODEM_POLL_MIN_INTERVAL_MS = 5_000;
    private static final int MODEM_POLL_TIME_DELTA_MAX_MS = 15_000;
    private static final int TX_TIME_OVER_RX_TIME_RATIO_THR_NUM = 3;
    private static final int TX_TIME_OVER_RX_TIME_RATIO_THR_DEN = 2;

    private final Phone mPhone;
    private final TelephonyFacade mTelephonyFacade;
    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;
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
    private int mNetworkType;
    private NetworkCapabilities mNetworkCapabilities;
    private int mLinkBandwidthTxKps;
    private int mLinkBandwidthRxKps;

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
        handleScreenStateChange(isScreenOn());
        mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback, this);
        mTelephonyManager.registerPhoneStateListener(new HandlerExecutor(this),
                mPhoneStateListener);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SCREEN_STATE_CHANGED:
                handleScreenStateChange((boolean) msg.obj);
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
                handleSignalStrengthChange((SignalStrength) msg.obj);
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

    private void handleScreenStateChange(boolean screenOn) {
        if (mScreenOn == screenOn) {
            return;
        }
        mScreenOn = screenOn;
        handleTrafficStatsPollConditionChanged();
    }

    private void handleDefaultNetworkChanged(NetworkCapabilities networkCapabilities) {
        mNetworkCapabilities = networkCapabilities;
        if (networkCapabilities == null) {
            mIsOnDefaultRoute = false;
        } else {
            mIsOnDefaultRoute = networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
        }
        handleTrafficStatsPollConditionChanged();
    }

    private void handleTrafficStatsPollConditionChanged() {
        if (mScreenOn && mIsOnDefaultRoute) {
            handleTrafficStatsPoll();
        } else {
            removeMessages(MSG_TRAFFIC_STATS_POLL);
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
        logd("TxByteDelta " + txBytesDelta + " RxByteDelta " + rxBytesDelta);
        logd("TxByteDeltaAcc " + mTxBytesDeltaAcc + " RxByteDeltaAcc " + mRxBytesDeltaAcc);
        // Schedule the next traffic stats poll
        sendEmptyMessageDelayed(MSG_TRAFFIC_STATS_POLL, TRAFFIC_STATS_POLL_INTERVAL_MS);

        // Check it meets the requirement to request modem activity
        if (txBytesDelta < MODEM_POLL_BYTE_DELTA_THR && rxBytesDelta < MODEM_POLL_BYTE_DELTA_THR
                && mTxBytesDeltaAcc < MODEM_POLL_BYTE_DELTA_ACC_THR
                && mRxBytesDeltaAcc < MODEM_POLL_BYTE_DELTA_ACC_THR) {
            return;
        }

        long timeSinceLastModemPoll = mTelephonyFacade.getElapsedSinceBootMillis()
                - mLastModemPollTimeMs;
        if (timeSinceLastModemPoll < MODEM_POLL_MIN_INTERVAL_MS) {
            return;
        }
        makeRequestModemActivity();
    }

    private void makeRequestModemActivity() {
        logd("modem activity requested");
        mLastModemPollTimeMs = mTelephonyFacade.getElapsedSinceBootMillis();
        mTelephonyManager.requestModemActivityInfo(Runnable::run, mOutcomeReceiver);
    }

    private void handleModemActivityReturned(ModemActivityInfo result) {
        logd("modem activity returned");
        updateBandwidthEst(result);

        mLastModemActivityInfo = result;
        resetByteDeltaAcc();
    }

    private void resetByteDeltaAcc() {
        mTxBytesDeltaAcc = 0;
        mRxBytesDeltaAcc = 0;
    }

    private void updateBandwidthEst(ModemActivityInfo modemActivityInfo) {
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
        long idleTimeDeltaMs = deltaInfo.getIdleTimeMillis();
        long sleepTimeDeltaMs = deltaInfo.getSleepTimeMillis();

        // Check if txTimeDeltaMs / rxTimeDeltaMs > TX_TIME_OVER_RX_TIME_RATIO_THR
        boolean isTxTimeOverRxTimeRatioLarge = (txTimeDeltaMs * TX_TIME_OVER_RX_TIME_RATIO_THR_DEN
                > rxTimeDeltaMs * TX_TIME_OVER_RX_TIME_RATIO_THR_NUM);
        long rxTimeBwEstMs = isTxTimeOverRxTimeRatioLarge
                ? (txTimeDeltaMs + rxTimeDeltaMs) : rxTimeDeltaMs;
        mLinkBandwidthTxKps = (int) (txTimeDeltaMs <= 0 ? 0 : mTxBytesDeltaAcc * 8 * 1000
                / txTimeDeltaMs / 1024);
        mLinkBandwidthRxKps = (int) (rxTimeBwEstMs <= 0 ? 0 : mRxBytesDeltaAcc * 8 * 1000
                / rxTimeBwEstMs / 1024);

        int l2TxTputKbps = mNetworkCapabilities.getLinkUpstreamBandwidthKbps();
        int l2RxTputKbps = mNetworkCapabilities.getLinkDownstreamBandwidthKbps();

        StringBuilder sb = new StringBuilder();
        logd(sb.append("dBm, ").append(mSignalStrengthDbm)
                .append(", ").append(mNetworkType)
                .append(", L2 txRxKbps, ").append(l2TxTputKbps)
                .append(", ").append(l2RxTputKbps)
                .append(", txRxSleepIdleMs, ").append(txTimeDeltaMs)
                .append(", ").append(rxTimeDeltaMs)
                .append(", ").append(sleepTimeDeltaMs)
                .append(", ").append(idleTimeDeltaMs)
                .append(", txRxKB, ").append(mTxBytesDeltaAcc / 1000)
                .append(", ").append(mRxBytesDeltaAcc / 1000)
                .append(", L3 txRxKbps, ").append(mLinkBandwidthTxKps)
                .append(", ").append(mLinkBandwidthRxKps)
                .toString());
    }

    private long getModemTxTimeMs(ModemActivityInfo modemActivity) {
        long txTimeMs = 0;
        for (int lvl = 0; lvl < ModemActivityInfo.getNumTxPowerLevels(); lvl++) {
            txTimeMs += modemActivity.getTransmitDurationMillisAtPowerLevel(lvl);
        }
        return txTimeMs;
    }

    private void handleSignalStrengthChange(SignalStrength signalStrength) {
        List<CellSignalStrength> cssList =
                (signalStrength == null) ? null : signalStrength.getCellSignalStrengths();

        if (cssList == null || cssList.isEmpty()) {
            return;
        }

        CellSignalStrength primaryCss = cssList.get(0);
        mSignalStrengthDbm = primaryCss.getDbm();
        mNetworkType = mTelephonyManager.getDataNetworkType();
    }

    private class PhoneStateListenerImpl extends PhoneStateListener
            implements PhoneStateListener.SignalStrengthsChangedListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, signalStrength).sendToTarget();
        }
    }

    /**
     * @return the latest Tx link bandwidth estimate in Kbps
     */
    public int getTxLinkBandwidthKbps() {
        return mLinkBandwidthTxKps;
    }

    /**
     * @return the latest Rx link bandwidth estimate in Kbps
     */
    public int getRxLinkBandwidthKbps() {
        return mLinkBandwidthRxKps;
    }

    void logd(String msg) {
        if (DBG) Rlog.d(TAG, msg);
    }
}
