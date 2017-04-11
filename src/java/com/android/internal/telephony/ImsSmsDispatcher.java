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
 * limitations under the License
 */

package com.android.internal.telephony;

import android.os.RemoteException;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.aidl.IImsSmsListener;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.ImsSmsImplBase;
import android.telephony.ims.stub.ImsSmsImplBase.SendStatusResult;
import android.provider.Telephony.Sms.Intents;
import android.util.Pair;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.MmTelFeatureConnection;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.util.SMSDispatcherUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Responsible for communications with {@link com.android.ims.ImsManager} to send/receive messages
 * over IMS.
 * @hide
 */
public class ImsSmsDispatcher extends SMSDispatcher {
    // Initial condition for ims connection retry.
    private static final int IMS_RETRY_STARTING_TIMEOUT_MS = 500; // ms
    // Ceiling bitshift amount for service query timeout, calculated as:
    // 2^mImsServiceRetryCount * IMS_RETRY_STARTING_TIMEOUT_MS, where
    // mImsServiceRetryCount ∊ [0, CEILING_SERVICE_RETRY_COUNT].
    private static final int CEILING_SERVICE_RETRY_COUNT = 6;

    @VisibleForTesting
    public Map<Integer, SmsTracker> mTrackers = new ConcurrentHashMap<>();
    @VisibleForTesting
    public AtomicInteger mNextToken = new AtomicInteger();
    private final Object mLock = new Object();
    private volatile boolean mIsSmsCapable;
    private volatile boolean mIsImsServiceUp;
    private volatile boolean mIsRegistered;
    private volatile int mImsServiceRetryCount;

    /**
     * Default implementation of interface that calculates the ImsService retry timeout.
     * Override-able for testing.
     */
    private IRetryTimeout mRetryTimeout = () -> {
        int timeout = (1 << mImsServiceRetryCount) * IMS_RETRY_STARTING_TIMEOUT_MS;
        if (mImsServiceRetryCount <= CEILING_SERVICE_RETRY_COUNT) {
            mImsServiceRetryCount++;
        }
        return timeout;
    };

    /**
     * Listen to the IMS service state change
     *
     */
    private ImsRegistrationImplBase.Callback mRegistrationCallback =
            new ImsRegistrationImplBase.Callback() {
                @Override
                public void onRegistered(
                        @ImsRegistrationImplBase.ImsRegistrationTech int imsRadioTech) {
                    Rlog.d(TAG, "onImsConnected imsRadioTech=" + imsRadioTech);
                    synchronized (mLock) {
                        mIsRegistered = true;
                    }
                }

                @Override
                public void onRegistering(
                        @ImsRegistrationImplBase.ImsRegistrationTech int imsRadioTech) {
                    Rlog.d(TAG, "onImsProgressing imsRadioTech=" + imsRadioTech);
                    synchronized (mLock) {
                        mIsRegistered = false;
                    }
                }

                @Override
                public void onDeregistered(ImsReasonInfo info) {
                    Rlog.d(TAG, "onImsDisconnected imsReasonInfo=" + info);
                    synchronized (mLock) {
                        mIsRegistered = false;
                    }
                }
            };

    private ImsFeature.CapabilityCallback mCapabilityCallback =
            new ImsFeature.CapabilityCallback() {
                @Override
                public void onCapabilitiesStatusChanged(ImsFeature.Capabilities config) {
                    synchronized (mLock) {
                        mIsSmsCapable = config.isCapable(
                                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS);
                    }
                }
    };

    // Callback fires when ImsManager MMTel Feature changes state
    private MmTelFeatureConnection.IFeatureUpdate mNotifyStatusChangedCallback =
            new MmTelFeatureConnection.IFeatureUpdate() {
                @Override
                public void notifyStateChanged() {
                    try {
                        int status = getImsManager().getImsServiceState();
                        Rlog.d(TAG, "Status Changed: " + status);
                        switch (status) {
                            case android.telephony.ims.feature.ImsFeature.STATE_READY: {
                                synchronized (mLock) {
                                    setListeners();
                                    mIsImsServiceUp = true;
                                }
                                break;
                            }
                            case android.telephony.ims.feature.ImsFeature.STATE_INITIALIZING:
                                // fall through
                            case ImsFeature.STATE_UNAVAILABLE:
                                synchronized (mLock) {
                                    mIsImsServiceUp = false;
                                }
                                break;
                            default: {
                                Rlog.w(TAG, "Unexpected State!");
                            }
                        }
                    } catch (ImsException e) {
                        // Could not get the ImsService, retry!
                        retryGetImsService();
                    }
                }

                @Override
                public void notifyUnavailable() {
                    retryGetImsService();
                }
            };

    private final IImsSmsListener mImsSmsListener = new IImsSmsListener.Stub() {
        @Override
        public void onSendSmsResult(int token, int messageRef, @SendStatusResult int status,
                int reason) throws RemoteException {
            SmsTracker tracker = mTrackers.get(token);
            if (tracker == null) {
                throw new IllegalArgumentException("Invalid token.");
            }
            switch(reason) {
                case ImsSmsImplBase.SEND_STATUS_OK:
                    tracker.onSent(mContext);
                    break;
                case ImsSmsImplBase.SEND_STATUS_ERROR:
                    tracker.onFailed(mContext, reason, 0 /* errorCode */);
                    mTrackers.remove(token);
                    break;
                case ImsSmsImplBase.SEND_STATUS_ERROR_RETRY:
                    tracker.mRetryCount += 1;
                    sendSms(tracker);
                    break;
                case ImsSmsImplBase.SEND_STATUS_ERROR_FALLBACK:
                    fallbackToPstn(token, tracker);
                    break;
                default:
            }
        }

        @Override
        public void onSmsStatusReportReceived(int token, int messageRef, String format, byte[] pdu)
                throws RemoteException {
            Rlog.d(TAG, "Status report received.");
            SmsTracker tracker = mTrackers.get(token);
            if (tracker == null) {
                throw new RemoteException("Invalid token.");
            }
            Pair<Boolean, Boolean> result = mSmsDispatchersController.handleSmsStatusReport(
                    tracker, format, pdu);
            Rlog.d(TAG, "Status report handle result, success: " + result.first +
                    "complete: " + result.second);
            try {
                getImsManager().acknowledgeSmsReport(
                        token,
                        messageRef,
                        result.first ? ImsSmsImplBase.STATUS_REPORT_STATUS_OK
                                : ImsSmsImplBase.STATUS_REPORT_STATUS_ERROR);
            } catch (ImsException e) {
                Rlog.e(TAG, "Failed to acknowledgeSmsReport(). Error: "
                        + e.getMessage());
            }
            if (result.second) {
                mTrackers.remove(token);
            }
        }

        @Override
        public void onSmsReceived(int token, String format, byte[] pdu)
                throws RemoteException {
            Rlog.d(TAG, "SMS received.");
            mSmsDispatchersController.injectSmsPdu(pdu, format, result -> {
                Rlog.d(TAG, "SMS handled result: " + result);
                try {
                    getImsManager().acknowledgeSms(token,
                            0,
                            result == Intents.RESULT_SMS_HANDLED
                                    ? ImsSmsImplBase.STATUS_REPORT_STATUS_OK
                                    : ImsSmsImplBase.DELIVER_STATUS_ERROR);
                } catch (ImsException e) {
                    Rlog.e(TAG, "Failed to acknowledgeSms(). Error: " + e.getMessage());
                }
            });
        }
    };

    public ImsSmsDispatcher(Phone phone, SmsDispatchersController smsDispatchersController) {
        super(phone, smsDispatchersController);

        mImsServiceRetryCount = 0;
        // Send a message to connect to the Ims Service and open a connection through
        // getImsService().
        sendEmptyMessage(EVENT_GET_IMS_SERVICE);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_GET_IMS_SERVICE:
                try {
                    getImsService();
                } catch (ImsException e) {
                    Rlog.e(TAG, "setListeners: " + e);
                    retryGetImsService();
                }
                break;
            default:
                super.handleMessage(msg);
        }
    }

    private void getImsService() throws ImsException {
        Rlog.d(TAG, "getImsService");
        // Adding to set, will be safe adding multiple times. If the ImsService is not active yet,
        // this method will throw an ImsException.
        getImsManager().addNotifyStatusChangedCallbackIfAvailable(mNotifyStatusChangedCallback);
        // Wait for ImsService.STATE_READY to start listening for SMS.
        // Call the callback right away for compatibility with older devices that do not use states.
        mNotifyStatusChangedCallback.notifyStateChanged();
    }

    private void setListeners() throws ImsException {
        getImsManager().addRegistrationCallback(mRegistrationCallback);
        getImsManager().addCapabilitiesCallback(mCapabilityCallback);
        getImsManager().setSmsListener(mImsSmsListener);
        getImsManager().onSmsReady();
        mImsServiceRetryCount = 0;
    }

    private void retryGetImsService() {
        // The binder connection is already up. Do not try to get it again.
        if (getImsManager().isServiceAvailable()) {
            return;
        }
        // remove callback so we do not receive updates from old MmTelFeatureConnection when switching
        // between ImsServices.
        getImsManager().removeNotifyStatusChangedCallback(mNotifyStatusChangedCallback);
        // Exponential backoff during retry, limited to 32 seconds.
        Rlog.e(TAG, "getImsService: Retrying getting ImsService...");
        removeMessages(EVENT_GET_IMS_SERVICE);
        sendEmptyMessageDelayed(EVENT_GET_IMS_SERVICE, mRetryTimeout.get());
    }

    public boolean isAvailable() {
        synchronized (mLock) {
            return mIsImsServiceUp && mIsRegistered && mIsSmsCapable;
        }
    }

    @Override
    protected String getFormat() {
        try {
            return getImsManager().getSmsFormat();
        } catch (ImsException e) {
            Rlog.e(TAG, "Failed to get sms format. Error: " + e.getMessage());
            return SmsConstants.FORMAT_UNKNOWN;
        }
    }

    @Override
    protected boolean shouldBlockSms() {
        return SMSDispatcherUtil.shouldBlockSms(isCdmaMo(), mPhone);
    }

    @Override
    protected SmsMessageBase.SubmitPduBase getSubmitPdu(String scAddr, String destAddr,
            String message, boolean statusReportRequested, SmsHeader smsHeader, int priority,
            int validityPeriod) {
        return SMSDispatcherUtil.getSubmitPdu(isCdmaMo(), scAddr, destAddr, message,
                statusReportRequested, smsHeader, priority, validityPeriod);
    }

    @Override
    protected SmsMessageBase.SubmitPduBase getSubmitPdu(String scAddr, String destAddr,
            int destPort, byte[] message, boolean statusReportRequested) {
        return SMSDispatcherUtil.getSubmitPdu(isCdmaMo(), scAddr, destAddr, destPort, message,
                statusReportRequested);
    }

    @Override
    protected TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SMSDispatcherUtil.calculateLength(isCdmaMo(), messageBody, use7bitOnly);
    }

    @Override
    public void sendSms(SmsTracker tracker) {
        Rlog.d(TAG, "sendSms: "
                + " mRetryCount=" + tracker.mRetryCount
                + " mMessageRef=" + tracker.mMessageRef
                + " SS=" + mPhone.getServiceState().getState());

        HashMap<String, Object> map = tracker.getData();

        byte[] pdu = (byte[]) map.get(MAP_KEY_PDU);
        byte smsc[] = (byte[]) map.get(MAP_KEY_SMSC);
        boolean isRetry = tracker.mRetryCount > 0;

        if (SmsConstants.FORMAT_3GPP.equals(getFormat()) && tracker.mRetryCount > 0) {
            // per TS 23.040 Section 9.2.3.6:  If TP-MTI SMS-SUBMIT (0x01) type
            //   TP-RD (bit 2) is 1 for retry
            //   and TP-MR is set to previously failed sms TP-MR
            if (((0x01 & pdu[0]) == 0x01)) {
                pdu[0] |= 0x04; // TP-RD
                pdu[1] = (byte) tracker.mMessageRef; // TP-MR
            }
        }

        int token = mNextToken.incrementAndGet();
        mTrackers.put(token, tracker);
        try {
            getImsManager().sendSms(
                    token,
                    tracker.mMessageRef,
                    getFormat(),
                    smsc != null ? new String(smsc) : null,
                    isRetry,
                    pdu);
        } catch (ImsException e) {
            Rlog.e(TAG, "sendSms failed. Falling back to PSTN. Error: " + e.getMessage());
            fallbackToPstn(token, tracker);
        }
    }

    private ImsManager getImsManager() {
        return ImsManager.getInstance(mContext, mPhone.getPhoneId());
    }

    @VisibleForTesting
    public void fallbackToPstn(int token, SmsTracker tracker) {
        mSmsDispatchersController.sendRetrySms(tracker);
        mTrackers.remove(token);
    }

    @Override
    protected boolean isCdmaMo() {
        return mSmsDispatchersController.isCdmaFormat(getFormat());
    }

    @VisibleForTesting
    public interface IRetryTimeout {
        int get();
    }
}
