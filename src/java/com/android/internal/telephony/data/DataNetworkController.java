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
import android.annotation.StringDef;
import android.net.NetworkAgent;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.DataProfile;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.RIL;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * DataNetworkController in the central module of the telephony data stack. It is responsible to
 * create and manage all the mobile data networks.
 */
public class DataNetworkController extends Handler {
    private final boolean VDBG;

    public static final String SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE =
            "ro.telephony.iwlan_operation_mode";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef(prefix = {"IWLAN_OPERATION_MODE_"},
            value = {
                    IWLAN_OPERATION_MODE_DEFAULT,
                    IWLAN_OPERATION_MODE_LEGACY,
                    IWLAN_OPERATION_MODE_AP_ASSISTED})
    public @interface IwlanOperationMode {}

    /**
     * IWLAN default mode. On device that has IRadio 1.4 or above, it means
     * {@link #IWLAN_OPERATION_MODE_AP_ASSISTED}. On device that has IRadio 1.3 or below, it means
     * {@link #IWLAN_OPERATION_MODE_LEGACY}.
     */
    public static final String IWLAN_OPERATION_MODE_DEFAULT = "default";

    /**
     * IWLAN legacy mode. IWLAN is completely handled by the modem, and when the device is on
     * IWLAN, modem reports IWLAN as a RAT.
     */
    public static final String IWLAN_OPERATION_MODE_LEGACY = "legacy";

    /**
     * IWLAN application processor assisted mode. IWLAN is handled by the bound IWLAN data service
     * and network service separately.
     */
    public static final String IWLAN_OPERATION_MODE_AP_ASSISTED = "AP-assisted";

    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    /** Event for adding a network request. */
    private static final int EVENT_ADD_NETWORK_REQUEST = 2;

    /** Event for removing a network request. */
    private static final int EVENT_REMOVE_NETWORK_REQUEST = 3;

    /** Event for satisfying a single network request. */
    private static final int EVENT_SATISFY_NETWORK_REQUEST = 4;

    /** Event for setup a data network. */
    private static final int EVENT_SETUP_DATA_NETWORK = 5;



    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    private final @NonNull DataConfigManager mDataConfigManager;
    private final @NonNull DataSettingsManager mDataSettingsManager;
    private final @NonNull DataProfileManager mDataProfileManager;
    private final @NonNull DataStallMonitor mDataStallMonitor;
    private final @NonNull DataTaskManager mDataTaskManager;
    private final @NonNull SparseArray<DataServiceManager> mDataServiceManagers =
            new SparseArray<>();

    /**
     * The list of all network requests.
     */
    private final @NonNull NetworkRequestList mNetworkRequestList = new NetworkRequestList();

    /**
     * The current data network list, including the ones that are connected, connecting, or
     * disconnecting.
     */
    private final @NonNull List<DataNetwork> mDataNetworkList = new ArrayList<>();

    /**
     * Contain the last 10 data networks that were connected. This is for debugging purposes only.
     */
    private final @NonNull List<DataNetwork> mHistoricalDataNetworkList = new ArrayList<>();

    /**
     * This network request list is for unsatisfied network requests. Telephony will not attempt
     * to satisfy those network requests unless there is an environmental changes, such as airplane
     * mode changes, carrier config changes, SIM state changes, RAT or registration state changes,
     * etc....
     */
    private final @NonNull NetworkRequestList mUnsatisfiedNetworkRequestList =
            new NetworkRequestList();

    /**
     * This network request list is for actively being processed network requests. Network requests
     * in this list are all scheduled with a timer that telephony will attempt to satisfy them soon.
     * Network requests will be removed from this list once they attach to a data network, or
     * telephony decides not to retry them anymore and put them into
     * {@link #mUnsatisfiedNetworkRequestList}.
     */
    private final @NonNull NetworkRequestList mActivelyProcessedNetworkRequestList =
            new NetworkRequestList();

    /**
     * The sorted network request list by priority. The highest priority network request stays at
     * the head of the list. The highest priority is 100, the lowest is 0.
     *
     * Note this list is not thread-safe. Do not access the list from different threads.
     */
    @VisibleForTesting
    public static class NetworkRequestList extends LinkedList<TelephonyNetworkRequest> {
        /**
         * Add the network request to the list. Note that the item will be inserted to the position
         * based on the priority.
         *
         * @param newRequest The network request to be added.
         * @return {@code true} if added successfully. {@code false} if the request already exists.
         */
        @Override
        public boolean add(@NonNull TelephonyNetworkRequest newRequest) {
            int index = 0;
            while (index < size()) {
                TelephonyNetworkRequest tnr = get(index);
                if (tnr.equals(newRequest)) {
                    return false;   // Do not allow duplicate
                }
                if (newRequest.getPriority() > tnr.getPriority()) {
                    break;
                }
                index++;
            }
            add(index, newRequest);
            return true;
        }

        /**
         * Dump the priority queue.
         *
         * @param pw print writer.
         */
        public void dump(IndentingPrintWriter pw) {
            pw.increaseIndent();
            for (TelephonyNetworkRequest tnr : this) {
                pw.println(tnr);
            }
            pw.decreaseIndent();
        }
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataNetworkController(@NonNull Phone phone, @NonNull Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DNC-" + mPhone.getPhoneId();
        VDBG = Rlog.isLoggable(mLogTag, Log.VERBOSE);
        log("DataNetworkController created.");

        mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                new DataServiceManager(mPhone, looper, AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        if (!isIwlanLegacyMode()) {
            mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    new DataServiceManager(mPhone, looper,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        }
        mDataConfigManager = new DataConfigManager(mPhone, looper);
        mDataSettingsManager = new DataSettingsManager(mPhone, looper);
        mDataProfileManager = new DataProfileManager(mPhone, looper);
        mDataStallMonitor = new DataStallMonitor(mPhone, looper);
        mDataTaskManager = new DataTaskManager(mPhone, looper);

        registerAllEvents();
    }

    /**
     * Register for all events that data network controller is interested.
     */
    private void registerAllEvents() {
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_ADD_NETWORK_REQUEST:
                onAddNetworkRequest((TelephonyNetworkRequest) msg.obj);
                break;
            case EVENT_SATISFY_NETWORK_REQUEST:
                onSatisfyNetworkRequest((TelephonyNetworkRequest) msg.obj);
                break;
            case EVENT_REMOVE_NETWORK_REQUEST:
                onRemoveNetworkRequest((NetworkRequest) msg.obj);
                break;
            case EVENT_SETUP_DATA_NETWORK:
                onSetupDataNetwork((DataProfile) msg.obj);
                break;
            default:
                loge("Unexpected event " + msg.what);
        }
    }

    /**
     * Add a network request, which is originated from the apps. Note that add a network request
     * is not necessarily setting up a net {@link DataNetwork}.
     *
     * @param networkRequest Network request
     */
    public void addNetworkRequest(@NonNull NetworkRequest networkRequest) {
        // TODO: TelephonyNetworkRequest should be created in TelephonyNetworkFactory after
        //       DcTracker and other legacy data stacks are removed.
        sendMessage(obtainMessage(EVENT_ADD_NETWORK_REQUEST,
                new TelephonyNetworkRequest(networkRequest, mPhone)));
    }

    private void onAddNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        if (!mNetworkRequestList.add(networkRequest)) {
            loge("onAddNetworkRequest: Duplicate network request. " + networkRequest);
            return;
        }
        logv("onAddNetworkRequest: added " + networkRequest);
        sendMessage(obtainMessage(EVENT_SATISFY_NETWORK_REQUEST, networkRequest));
    }

    private void onSatisfyNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        // Check if the existing data network can satisfy this network request or not. If yes, just
        // attach the network request to the data network (even though it is connecting or
        // disconnecting).
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (networkRequest.canBeSatisfiedBy(dataNetwork.getNetworkCapabilities())) {
                if (dataNetwork.attachNetworkRequest(networkRequest)) {
                    mUnsatisfiedNetworkRequestList.remove(networkRequest);
                    mActivelyProcessedNetworkRequestList.remove(networkRequest);
                    log("Found existing data network " + dataNetwork.getLogTag() + " can satisfy"
                            + " network request " + networkRequest);
                } else {
                    log(dataNetwork.getLogTag() + " cannot be attached at this point. Put the "
                            + "network request into unsatisfied pool. " + networkRequest);
                    mUnsatisfiedNetworkRequestList.add(networkRequest);
                }
                return;
            }
        }

        // TODO: There are a more works needed to be done here.
        //   1. Check if the environment is allowed to satisfy this network request.
        //   2. Check if we can find a data profile that can satisfy this network request.

        // Can't find any way to satisfy the network request. Add it to the unsatisfied pool. We'll
        // deal with it later.
        log("Add network request to the unsatisfied list. " + networkRequest);
        mUnsatisfiedNetworkRequestList.add(networkRequest);
    }

    /**
     * Remove a network request, which is originated from the apps. Note that remove a network
     * will not result in tearing down the network. The tear down request directly comes from
     * {@link com.android.server.ConnectivityService} through
     * {@link NetworkAgent#onNetworkUnwanted()}.
     *
     * @param networkRequest Network request
     */
    // TODO: TelephonyNetworkRequest should be used after DcTracker and other legacy data stacks are
    //  removed.
    public void removeNetworkRequest(@NonNull NetworkRequest networkRequest) {
        sendMessage(obtainMessage(EVENT_REMOVE_NETWORK_REQUEST, networkRequest));
    }

    private void onRemoveNetworkRequest(@NonNull NetworkRequest networkRequest) {
        // TODO: TelephonyNetworkRequest should be used after DcTracker and other legacy data stacks
        //  are removed.
        // temp solution: find the original telephony network request.
        TelephonyNetworkRequest tnr = mNetworkRequestList.stream()
                .filter(nr -> nr.getNativeNetworkRequest().equals(networkRequest))
                .findFirst()
                .orElse(null);
        if (tnr == null) {
            loge("onRemoveNetworkRequest: Can't find original network request. "
                    + networkRequest);
            return;
        }

        if (!mNetworkRequestList.remove(tnr)) {
            loge("onRemoveNetworkRequest: Network request does not exist. " + tnr);
            return;
        }
        logv("onRemoveNetworkRequest: Removed " + tnr);

        mUnsatisfiedNetworkRequestList.remove(tnr);
        mActivelyProcessedNetworkRequestList.remove(tnr);

        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.detachNetworkRequest(tnr)) {
                return;
            }
        }
    }

    /**
     * Called when data config was updated.
     */
    private void onDataConfigUpdated() {
        updateNetworkRequestsPriority();
    }

    /**
     * Update each network request's priority.
     */
    private void updateNetworkRequestsPriority() {
        for (TelephonyNetworkRequest networkRequest : mNetworkRequestList) {
            networkRequest.updatePriority();
        }
    }

    /**
     * Handle setup data network event.
     *
     * @param dataProfile The data profile to setup the data network.
     */
    private void onSetupDataNetwork(@NonNull DataProfile dataProfile) {
        log("onSetupDataNetwork: dataProfile=" + dataProfile);
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.getDataProfile().equals(dataProfile)) {
                log("onSetupDataNetwork: Found existing data network " + dataNetwork.getLogTag()
                        + " has the same data profile.");
                return;
            }
        }
    }

    /**
     * @return Data config manager instance.
     */
    public @NonNull DataConfigManager getDataConfigManager() {
        return mDataConfigManager;
    }

    /**
     * @return {@code true} if the device operates in IWLAN legacy mode, otherwise {@code false}. In
     * legacy mode, IWLAN registration state is reported through cellular
     * {@link android.telephony.NetworkRegistrationInfo}.
     */
    public boolean isIwlanLegacyMode() {
        // Get IWLAN operation mode from the system property. If the system property is configured
        // to default or not configured, the mode is tied to IRadio version. For 1.4 or above, it's
        // AP-assisted mode, for 1.3 or below, it's legacy mode.
        String mode = SystemProperties.get(SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE);

        if (mode.equals(IWLAN_OPERATION_MODE_AP_ASSISTED)) {
            return false;
        } else if (mode.equals(IWLAN_OPERATION_MODE_LEGACY)) {
            return true;
        }

        return mPhone.getHalVersion().less(RIL.RADIO_HAL_VERSION_1_4);
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(mLogTag, s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(mLogTag, s);
    }

    /**
     * Log verbose messages.
     * @param s debug messages.
     */
    private void logv(@NonNull String s) {
        if (VDBG) Rlog.v(mLogTag, s);
    }

    /**
     * Log debug messages and also log into the local log.
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of DataNetworkController
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataNetworkController.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("IWLAN operation mode="
                + SystemProperties.get(SYSTEM_PROPERTIES_IWLAN_OPERATION_MODE));
        pw.println("Current data networks:");
        pw.increaseIndent();
        for (DataNetwork dn : mDataNetworkList) {
            dn.dump(fd, printWriter, args);
        }
        pw.decreaseIndent();
        pw.println("Historical connected data networks:");
        pw.increaseIndent();
        for (DataNetwork dn: mHistoricalDataNetworkList) {
            // Do not print networks which is already in current network list.
            if (!mDataNetworkList.contains(dn)) {
                dn.dump(fd, printWriter, args);
            }
        }
        pw.decreaseIndent();
        pw.println("Unsatisfied network requests in priority order.");
        mUnsatisfiedNetworkRequestList.dump(pw);
        pw.println("Actively processed network requests in priority order.");
        mActivelyProcessedNetworkRequestList.dump(pw);


        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println("-------------------------------------");
        mDataProfileManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataTaskManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataSettingsManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataStallMonitor.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataConfigManager.dump(fd, pw, args);

        pw.decreaseIndent();
    }
}
