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

package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Looper;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import com.android.internal.R;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maintain the Apn context
 */
public class ApnContext extends NetworkAgent {

    public final String LOG_TAG;

    protected static final boolean DBG = true;

    private final Context mContext;

    private final String mApnType;

    private DctConstants.State mState;

    private ArrayList<ApnSetting> mWaitingApns = null;

    public final int priority;

    /** A zero indicates that all waiting APNs had a permanent error */
    private AtomicInteger mWaitingApnsPermanentFailureCountDown;

    private ApnSetting mApnSetting;

    DcAsyncChannel mDcAc;


    PendingIntent mReconnectAlarmIntent;

    /**
     * user/app requested connection on this APN
     */
    AtomicBoolean mDataEnabled;

    /**
     * carrier requirements met
     */
    AtomicBoolean mDependencyMet;

    private final DcTracker mDcTracker;

    public final NetworkInfo networkInfo;

    public ApnContext(Looper looper, Context context, String apnType, String logTag,
            NetworkConfig config, DcTracker tracker, int netType) {
        super(looper, context, logTag);
        mDcTracker = tracker;
        mContext = context;
        mApnType = apnType;
        mState = DctConstants.State.IDLE;
        mDataEnabled = new AtomicBoolean(false);
        mDependencyMet = new AtomicBoolean(config.dependencyMet);
        mWaitingApnsPermanentFailureCountDown = new AtomicInteger(0);
        priority = config.priority;
        LOG_TAG = logTag;
        // TODO - is there a better way to get the network type/name?
        networkInfo = new NetworkInfo(netType, TelephonyManager.getDefault().getNetworkType(),
                "Cellular", TelephonyManager.getDefault().getNetworkTypeName());
        setReason(Phone.REASON_DATA_ENABLED);
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        switch (netType) {
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
            case ConnectivityManager.TYPE_MOBILE: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_MMS: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_SUPL: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_DUN: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
                nc.removeNetworkCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_FOTA: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
                nc.removeNetworkCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_IMS: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
                nc.removeNetworkCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_CBS: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_CBS);
                nc.removeNetworkCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                break;
            }
            case ConnectivityManager.TYPE_MOBILE_IA: {
                nc.addNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IA);
                nc.removeNetworkCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                break;
            }
        }
        // updated per rat, later
        nc.setLinkUpstreamBandwidthKbps(10 * 1024);
        nc.setLinkDownstreamBandwidthKbps(20 * 1024);
        sendNetworkCapabilities(nc);
    }

    public String getApnType() {
        return mApnType;
    }

    public synchronized DcAsyncChannel getDcAc() {
        return mDcAc;
    }

    public synchronized void setDataConnectionAc(DcAsyncChannel dcac) {
        if (DBG) {
            log("setDataConnectionAc: old dcac=" + mDcAc + " new dcac=" + dcac
                    + " this=" + this);
        }
        mDcAc = dcac;
    }

    public synchronized PendingIntent getReconnectIntent() {
        return mReconnectAlarmIntent;
    }

    public synchronized void setReconnectIntent(PendingIntent intent) {
        mReconnectAlarmIntent = intent;
    }

    public synchronized ApnSetting getApnSetting() {
        log("getApnSetting: apnSetting=" + mApnSetting);
        return mApnSetting;
    }

    public synchronized void setApnSetting(ApnSetting apnSetting) {
        log("setApnSetting: apnSetting=" + apnSetting);
        mApnSetting = apnSetting;
        boolean prov = false;
        if (mApnSetting != null && mApnSetting.apn != null) {
            String provisioningApn = mContext.getResources()
                    .getString(R.string.mobile_provisioning_apn);
            prov = mApnSetting.apn.equals(provisioningApn);
        }
        networkInfo.setIsConnectedToProvisioningNetwork(prov);
    }

    public synchronized void setWaitingApns(ArrayList<ApnSetting> waitingApns) {
        mWaitingApns = waitingApns;
        mWaitingApnsPermanentFailureCountDown.set(mWaitingApns.size());
    }

    public int getWaitingApnsPermFailCount() {
        return mWaitingApnsPermanentFailureCountDown.get();
    }

    public void decWaitingApnsPermFailCount() {
        mWaitingApnsPermanentFailureCountDown.decrementAndGet();
    }

    public synchronized ApnSetting getNextWaitingApn() {
        ArrayList<ApnSetting> list = mWaitingApns;
        ApnSetting apn = null;

        if (list != null) {
            if (!list.isEmpty()) {
                apn = list.get(0);
            }
        }
        return apn;
    }

    public synchronized void removeWaitingApn(ApnSetting apn) {
        if (mWaitingApns != null) {
            mWaitingApns.remove(apn);
        }
    }

    public synchronized ArrayList<ApnSetting> getWaitingApns() {
        return mWaitingApns;
    }

    public synchronized void setState(DctConstants.State s) {
        if (DBG) {
            log("setState: " + s + ", previous state:" + mState);
        }

        mState = s;

        if (mState == DctConstants.State.FAILED) {
            if (mWaitingApns != null) {
                mWaitingApns.clear(); // when teardown the connection and set to IDLE
            }
        }
    }

    public synchronized DctConstants.State getState() {
        return mState;
    }

    public boolean isDisconnected() {
        DctConstants.State currentState = getState();
        return ((currentState == DctConstants.State.IDLE) ||
                    currentState == DctConstants.State.FAILED);
    }

    public synchronized void setReason(String reason) {
        if (DBG) {
            log("set reason as " + reason + ",current state " + mState);
        }
        networkInfo.setDetailedState(networkInfo.getDetailedState(), reason,
                networkInfo.getExtraInfo());
    }

    public synchronized String getReason() {
        return networkInfo.getReason();
    }

    public boolean isReady() {
        return mDataEnabled.get() && mDependencyMet.get();
    }

    public boolean isConnectable() {
        return isReady() && ((mState == DctConstants.State.IDLE)
                                || (mState == DctConstants.State.SCANNING)
                                || (mState == DctConstants.State.RETRYING)
                                || (mState == DctConstants.State.FAILED));
    }

    public boolean isConnectedOrConnecting() {
        return isReady() && ((mState == DctConstants.State.CONNECTED)
                                || (mState == DctConstants.State.CONNECTING)
                                || (mState == DctConstants.State.SCANNING)
                                || (mState == DctConstants.State.RETRYING));
    }

    public void setEnabled(boolean enabled) {
        if (DBG) {
            log("set enabled as " + enabled + ", current state is " + mDataEnabled.get());
        }
        mDataEnabled.set(enabled);
    }

    public boolean isEnabled() {
        return mDataEnabled.get();
    }

    public void setDependencyMet(boolean met) {
        if (DBG) {
            log("set mDependencyMet as " + met + " current state is " + mDependencyMet.get());
        }
        mDependencyMet.set(met);
    }

    public boolean getDependencyMet() {
       return mDependencyMet.get();
    }

    public boolean isProvisioningApn() {
        return networkInfo.isConnectedToProvisioningNetwork();
    }

    @Override
    protected void connect() {
        if (DBG) log("connect");
        mDcTracker.setEnabled(mDcTracker.apnTypeToId(mApnType), true);
    }

    @Override
    protected void disconnect() {
        if (DBG) log("disconnect");
        mDcTracker.setEnabled(mDcTracker.apnTypeToId(mApnType), false);
    }

    public void sendNetworkInfo() {
        sendNetworkInfo(networkInfo);
    }

    public void sendRat(int rilRat) {
        int up, down; // kbps
        switch (rilRat) {
            case TelephonyManager.NETWORK_TYPE_GPRS: up = 80; down = 80; break;
            case TelephonyManager.NETWORK_TYPE_EDGE: up = 59; down = 236; break;
            case TelephonyManager.NETWORK_TYPE_UMTS: up = 384; down = 384; break;
            case TelephonyManager.NETWORK_TYPE_CDMA: up = 14; down = 14; break;
            case TelephonyManager.NETWORK_TYPE_EVDO_0: up = 153; down = 2457; break;
            case TelephonyManager.NETWORK_TYPE_EVDO_A: up = 1843; down = 3174; break;
            case TelephonyManager.NETWORK_TYPE_1xRTT: up = 100; down = 100; break;
            case TelephonyManager.NETWORK_TYPE_HSDPA: up = 2048; down = 14336; break;
            case TelephonyManager.NETWORK_TYPE_HSUPA: up = 5898; down = 14336; break;
            case TelephonyManager.NETWORK_TYPE_HSPA: up = 5898; down = 14336; break;
            case TelephonyManager.NETWORK_TYPE_IDEN: up = 14; down = 14; break;
            case TelephonyManager.NETWORK_TYPE_EVDO_B: up = 1843; down = 5017; break;
            case TelephonyManager.NETWORK_TYPE_LTE: up = 51200; down = 102400; break;
            case TelephonyManager.NETWORK_TYPE_EHRPD: up = 153; down = 2516; break;
            case TelephonyManager.NETWORK_TYPE_HSPAP: up = 11264; down = 43008; break;
            default:
                return;  // unknown
        }
        synchronized (this) {
            NetworkCapabilities nc = getNetworkCapabilities();
            nc.setLinkUpstreamBandwidthKbps(up);
            nc.setLinkDownstreamBandwidthKbps(down);
            sendNetworkCapabilities(nc);
        }
    }

    @Override
    public synchronized String toString() {
        // We don't print mDataConnection because its recursive.
        return "{mApnType=" + mApnType + " mState=" + getState() + " mWaitingApns={" + mWaitingApns +
                "} mWaitingApnsPermanentFailureCountDown=" + mWaitingApnsPermanentFailureCountDown +
                " mApnSetting={" + mApnSetting + "} mReason=" + networkInfo.getReason() +
                " mDataEnabled=" + mDataEnabled + " mDependencyMet=" + mDependencyMet + "}";
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[ApnContext:" + mApnType + "] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ApnContext: " + this.toString());
    }
}
