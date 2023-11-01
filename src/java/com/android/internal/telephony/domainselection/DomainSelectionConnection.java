/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.domainselection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.RadioAccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.ApnType;
import android.telephony.Annotation.DisconnectCauses;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelectionService.EmergencyScanType;
import android.telephony.DomainSelector;
import android.telephony.EmergencyRegResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.data.ApnSetting;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.telephony.IDomainSelector;
import com.android.internal.telephony.ITransportSelectorCallback;
import com.android.internal.telephony.ITransportSelectorResultCallback;
import com.android.internal.telephony.IWwanSelectorCallback;
import com.android.internal.telephony.IWwanSelectorResultCallback;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.AccessNetworksManager.QualifiedNetworks;
import com.android.internal.telephony.util.TelephonyUtils;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * Manages the information of request and the callback binder.
 */
public class DomainSelectionConnection {

    private static final boolean DBG = TelephonyUtils.IS_DEBUGGABLE;

    protected static final int EVENT_EMERGENCY_NETWORK_SCAN_RESULT = 1;
    protected static final int EVENT_QUALIFIED_NETWORKS_CHANGED = 2;

    /** Callback to receive responses from DomainSelectionConnection. */
    public interface DomainSelectionConnectionCallback {
        /**
         * Notifies that selection has terminated because there is no decision that can be made
         * or a timeout has occurred. The call should be terminated when this method is called.
         *
         * @param cause Indicates the reason.
         */
        void onSelectionTerminated(@DisconnectCauses int cause);
    }

    /**
     * A wrapper class for {@link ITransportSelectorCallback} interface.
     */
    private final class TransportSelectorCallbackAdaptor extends ITransportSelectorCallback.Stub {
        @Override
        public void onCreated(@NonNull IDomainSelector selector) {
            synchronized (mLock) {
                mDomainSelector = selector;
                if (mDisposed) {
                    try {
                        selector.cancelSelection();
                    } catch (RemoteException e) {
                        // ignore exception
                    }
                    return;
                }
                DomainSelectionConnection.this.onCreated();
            }
        }

        @Override
        public void onWlanSelected(boolean useEmergencyPdn) {
            synchronized (mLock) {
                if (mDisposed) return;
                DomainSelectionConnection.this.onWlanSelected(useEmergencyPdn);
            }
        }

        @Override
        public @NonNull IWwanSelectorCallback onWwanSelected() {
            synchronized (mLock) {
                if (mWwanSelectorCallback == null) {
                    mWwanSelectorCallback = new WwanSelectorCallbackAdaptor();
                }
                if (mDisposed) {
                    return mWwanSelectorCallback;
                }
                DomainSelectionConnection.this.onWwanSelected();
                return mWwanSelectorCallback;
            }
        }

        @Override
        public void onWwanSelectedAsync(@NonNull final ITransportSelectorResultCallback cb) {
            synchronized (mLock) {
                if (mDisposed) return;
                if (mWwanSelectorCallback == null) {
                    mWwanSelectorCallback = new WwanSelectorCallbackAdaptor();
                }
                initHandler();
                mHandler.post(() -> {
                    synchronized (mLock) {
                        if (mDisposed) return;
                        DomainSelectionConnection.this.onWwanSelected();
                    }
                    try {
                        cb.onCompleted(mWwanSelectorCallback);
                    } catch (RemoteException e) {
                        loge("onWwanSelectedAsync executor exception=" + e);
                    }
                });
            }
        }

        @Override
        public void onSelectionTerminated(int cause) {
            synchronized (mLock) {
                if (mDisposed) return;
                DomainSelectionConnection.this.onSelectionTerminated(cause);
                dispose();
            }
        }
    }

    /**
     * A wrapper class for {@link IWwanSelectorCallback} interface.
     */
    private final class WwanSelectorCallbackAdaptor extends IWwanSelectorCallback.Stub {
        @Override
        public void onRequestEmergencyNetworkScan(
                @NonNull @RadioAccessNetworkType int[] preferredNetworks,
                @EmergencyScanType int scanType, @NonNull IWwanSelectorResultCallback cb) {
            synchronized (mLock) {
                if (mDisposed) return;
                mResultCallback = cb;
                initHandler();
                mHandler.post(() -> {
                    synchronized (mLock) {
                        DomainSelectionConnection.this.onRequestEmergencyNetworkScan(
                                preferredNetworks, scanType);
                    }
                });
            }
        }

        @Override
        public void onDomainSelected(@NetworkRegistrationInfo.Domain int domain,
                boolean useEmergencyPdn) {
            synchronized (mLock) {
                if (mDisposed) return;
                DomainSelectionConnection.this.onDomainSelected(domain, useEmergencyPdn);
            }
        }

        @Override
        public void onCancel() {
            synchronized (mLock) {
                if (mDisposed || mHandler == null) return;
                mHandler.post(() -> {
                    DomainSelectionConnection.this.onCancel();
                });
            }
        }
    }

    protected final class DomainSelectionConnectionHandler extends Handler {
        DomainSelectionConnectionHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_EMERGENCY_NETWORK_SCAN_RESULT:
                    ar = (AsyncResult) msg.obj;
                    EmergencyRegResult regResult = (EmergencyRegResult) ar.result;
                    if (DBG) logd("EVENT_EMERGENCY_NETWORK_SCAN_RESULT result=" + regResult);
                    synchronized (mLock) {
                        mIsWaitingForScanResult = false;
                        if (mResultCallback != null) {
                            try {
                                mResultCallback.onComplete(regResult);
                            } catch (RemoteException e) {
                                loge("EVENT_EMERGENCY_NETWORK_SCAN_RESULT exception=" + e);
                            }
                        }
                    }
                    break;
                case EVENT_QUALIFIED_NETWORKS_CHANGED:
                    ar = (AsyncResult) msg.obj;
                    if (ar == null || ar.result == null) {
                        loge("handleMessage EVENT_QUALIFIED_NETWORKS_CHANGED null result");
                        break;
                    }
                    onQualifiedNetworksChanged((List<QualifiedNetworks>) ar.result);
                    break;
                default:
                    loge("handleMessage unexpected msg=" + msg.what);
                    break;
            }
        }
    }

    protected String mTag = "DomainSelectionConnection";

    private boolean mDisposed = false;
    private final Object mLock = new Object();
    private final LocalLog mLocalLog = new LocalLog(30);
    private final @NonNull ITransportSelectorCallback mTransportSelectorCallback;

    /**
     * Controls the communication between {@link DomainSelectionConnection} and
     * {@link DomainSelectionService}.
     */
    private final @NonNull DomainSelectionController mController;
    /** Indicates whether the requested service is for emergency services. */
    private final boolean mIsEmergency;

    /** Interface to receive the request to trigger emergency network scan and selected domain. */
    private @Nullable IWwanSelectorCallback mWwanSelectorCallback;
    /** Interface to return the result of emergency network scan. */
    private @Nullable IWwanSelectorResultCallback mResultCallback;
    /** Interface to the {@link DomainSelector} created for this service. */
    private @Nullable IDomainSelector mDomainSelector;

    /** The slot requested this connection. */
    protected @NonNull Phone mPhone;
    /** The requested domain selector type. */
    private @DomainSelectionService.SelectorType int mSelectorType;

    /** The attributes required to determine the domain. */
    private @Nullable DomainSelectionService.SelectionAttributes mSelectionAttributes;

    private final @NonNull Looper mLooper;
    protected @Nullable DomainSelectionConnectionHandler mHandler;
    private boolean mRegisteredRegistrant;
    private boolean mIsWaitingForScanResult;

    private @NonNull AndroidFuture<Integer> mOnComplete;

    /**
     * Creates an instance.
     *
     * @param phone For which this service is requested.
     * @param selectorType Indicates the type of the requested service.
     * @param isEmergency Indicates whether this request is for emergency service.
     * @param controller The controller to communicate with the domain selection service.
     */
    public DomainSelectionConnection(@NonNull Phone phone,
            @DomainSelectionService.SelectorType int selectorType, boolean isEmergency,
            @NonNull DomainSelectionController controller) {
        mController = controller;
        mPhone = phone;
        mSelectorType = selectorType;
        mIsEmergency = isEmergency;
        mLooper = Looper.getMainLooper();

        mTransportSelectorCallback = new TransportSelectorCallbackAdaptor();
        mOnComplete = new AndroidFuture<>();
    }

    /**
     * Returns the attributes required to determine the domain for a telephony service.
     *
     * @return The attributes required to determine the domain.
     */
    public @Nullable DomainSelectionService.SelectionAttributes getSelectionAttributes() {
        return mSelectionAttributes;
    }

    /**
     * Returns the callback binder interface.
     *
     * @return The {@link ITransportSelectorCallback} interface.
     */
    public @Nullable ITransportSelectorCallback getTransportSelectorCallback() {
        return mTransportSelectorCallback;
    }

    /**
     * Returns the callback binder interface to handle the emergency scan result.
     *
     * @return The {@link IWwanSelectorResultCallback} interface.
     */
    public @Nullable IWwanSelectorResultCallback getEmergencyRegResultCallback() {
        return mResultCallback;
    }

    /**
     * Returns the {@link CompletableFuture} to receive the selected domain.
     *
     * @return The callback to receive response.
     */
    public @NonNull CompletableFuture<Integer> getCompletableFuture() {
        return mOnComplete;
    }

    /**
     * Returs the {@link Phone} which requested this connection.
     *
     * @return The {@link Phone} instance.
     */
    public @NonNull Phone getPhone() {
        return mPhone;
    }

    /**
     * Requests the domain selection servic to select a domain.
     *
     * @param attr The attributes required to determine the domain.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public void selectDomain(@NonNull DomainSelectionService.SelectionAttributes attr) {
        mSelectionAttributes = attr;
        mController.selectDomain(attr, mTransportSelectorCallback);
    }

    /**
     * Notifies that {@link DomainSelector} instance has been created for the selection request.
     */
    public void onCreated() {
        // Can be overridden if required
    }

    /**
     * Notifies that WLAN transport has been selected.
     */
    public void onWlanSelected() {
        // Can be overridden.
    }

    /**
     * Notifies that WLAN transport has been selected.
     *
     * @param useEmergencyPdn Indicates whether Wi-Fi emergency services use emergency PDN or not.
     */
    public void onWlanSelected(boolean useEmergencyPdn) {
        // Can be overridden.
        onWlanSelected();
    }

    /**
     * Notifies that WWAN transport has been selected.
     */
    public void onWwanSelected() {
        // Can be overridden.
    }

    /**
     * Notifies that selection has terminated because there is no decision that can be made
     * or a timeout has occurred. The call should be terminated when this method is called.
     *
     * @param cause Indicates the reason.
     */
    public void onSelectionTerminated(@DisconnectCauses int cause) {
        // Can be overridden.
    }

    /**
     * Requests the emergency network scan.
     *
     * @param preferredNetworks The ordered list of preferred networks to scan.
     * @param scanType Indicates the scan preference, such as full service or limited service.
     */
    public void onRequestEmergencyNetworkScan(
            @NonNull @RadioAccessNetworkType int[] preferredNetworks,
            @EmergencyScanType int scanType) {
        // Can be overridden if required
        synchronized (mLock) {
            if (mDisposed || mHandler == null) return;

            if (!mRegisteredRegistrant) {
                mPhone.registerForEmergencyNetworkScan(mHandler,
                        EVENT_EMERGENCY_NETWORK_SCAN_RESULT, null);
                mRegisteredRegistrant = true;
            }
            mIsWaitingForScanResult = true;
            mPhone.triggerEmergencyNetworkScan(preferredNetworks, scanType, null);
        }
    }

    /**
     * Notifies the domain selected.
     *
     * @param domain The selected domain.
     */
    public void onDomainSelected(@NetworkRegistrationInfo.Domain int domain) {
        // Can be overridden if required
        CompletableFuture<Integer> future = getCompletableFuture();
        future.complete(domain);
    }

    /**
     * Notifies the domain selected.
     *
     * @param domain The selected domain.
     * @param useEmergencyPdn Indicates whether emergency services use emergency PDN or not.
     */
    public void onDomainSelected(@NetworkRegistrationInfo.Domain int domain,
            boolean useEmergencyPdn) {
        // Can be overridden if required
        onDomainSelected(domain);
    }

    /**
     * Notifies that the emergency network scan is canceled.
     */
    public void onCancel() {
        // Can be overridden if required
        onCancel(false);
    }

    private void onCancel(boolean resetScan) {
        if (mIsWaitingForScanResult) {
            mIsWaitingForScanResult = false;
            mPhone.cancelEmergencyNetworkScan(resetScan, null);
        }
    }

    /**
     * Cancels an ongoing selection operation. It is up to the {@link DomainSelectionService}
     * to clean up all ongoing operations with the framework.
     */
    public void cancelSelection() {
        synchronized (mLock) {
            try {
                if (mDomainSelector != null) {
                    mDomainSelector.cancelSelection();
                }
            } catch (RemoteException e) {
                loge("cancelSelection exception=" + e);
            } finally {
                dispose();
            }
        }
    }

    /**
     * Requests the domain selection service to reselect a domain.
     *
     * @param attr The attributes required to determine the domain.
     * @return The callback to receive the response.
     */
    public @NonNull CompletableFuture<Integer> reselectDomain(
            @NonNull DomainSelectionService.SelectionAttributes attr) {
        synchronized (mLock) {
            mSelectionAttributes = attr;
            mOnComplete = new AndroidFuture<>();
            try {
                if (mDomainSelector != null) {
                    mDomainSelector.reselectDomain(attr);
                }
            } catch (RemoteException e) {
                loge("reselectDomain exception=" + e);
            } finally {
                return mOnComplete;
            }
        }
    }

    /**
     * Finishes the selection procedure and cleans everything up.
     */
    public void finishSelection() {
        synchronized (mLock) {
            try {
                if (mDomainSelector != null) {
                    mDomainSelector.finishSelection();
                }
            } catch (RemoteException e) {
                loge("finishSelection exception=" + e);
            } finally {
                dispose();
            }
        }
    }

    /** Indicates that the service connection has been removed. */
    public void onServiceDisconnected() {
        // Can be overridden.
        dispose();
    }

    private void dispose() {
        mDisposed = true;
        if (mRegisteredRegistrant) {
            mPhone.unregisterForEmergencyNetworkScan(mHandler);
            mRegisteredRegistrant = false;
        }
        onCancel(true);
        mController.removeConnection(this);
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
        mHandler = null;
    }

    protected void initHandler() {
        if (mHandler == null) mHandler = new DomainSelectionConnectionHandler(mLooper);
    }

    /**
     * Notifies the change of qualified networks.
     */
    protected void onQualifiedNetworksChanged(List<QualifiedNetworks> networksList) {
        if (mIsEmergency
                && (mSelectorType == DomainSelectionService.SELECTOR_TYPE_CALLING)) {
            // DomainSelectionConnection for emergency calls shall override this.
            throw new IllegalStateException("DomainSelectionConnection for emergency calls"
                    + " should override onQualifiedNetworksChanged()");
        }
    }

    /**
     * Get the  preferred transport.
     *
     * @param apnType APN type.
     * @return The preferred transport.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public int getPreferredTransport(@ApnType int apnType,
            List<QualifiedNetworks> networksList) {
        for (QualifiedNetworks networks : networksList) {
            if (networks.qualifiedNetworks.length > 0) {
                if (networks.apnType == apnType) {
                    return getTransportFromAccessNetwork(networks.qualifiedNetworks[0]);
                }
            }
        }

        loge("getPreferredTransport no network found for " + ApnSetting.getApnTypeString(apnType));
        return AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    }

    private static @TransportType int getTransportFromAccessNetwork(int accessNetwork) {
        return accessNetwork == AccessNetworkType.IWLAN
                ? AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                : AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    }

    /**
     * Dumps local log.
     */
    public void dump(@NonNull PrintWriter printWriter) {
        mLocalLog.dump(printWriter);
    }

    protected void logd(String msg) {
        Log.d(mTag, msg);
    }

    protected void logi(String msg) {
        Log.i(mTag, msg);
        mLocalLog.log(msg);
    }

    protected void loge(String msg) {
        Log.e(mTag, msg);
        mLocalLog.log(msg);
    }
}
