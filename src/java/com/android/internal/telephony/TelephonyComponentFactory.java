/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.IDeviceIdleController;
import android.os.ServiceManager;
import android.telephony.Rlog;

import com.android.internal.os.BackgroundThread;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.cdma.EriManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccCardProxy;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.io.File;

/**
 * This class has one-line methods to instantiate objects only. The purpose is to make code
 * unit-test friendly and use this class as a way to do dependency injection. Instantiating objects
 * this way makes it easier to mock them in tests.
 */
public class TelephonyComponentFactory {
    protected static String LOG_TAG = "TelephonyComponentFactory";

    private static TelephonyComponentFactory sInstance;

    public static TelephonyComponentFactory getInstance() {
        if (sInstance == null) {
            String fullClsName = "com.qualcomm.qti.internal.telephony.QtiTelephonyComponentFactory";
            String libPath = "/system/framework/qti-telephony-common.jar";

            PathClassLoader classLoader = new PathClassLoader(libPath,
                    ClassLoader.getSystemClassLoader());
            Rlog.d(LOG_TAG, "classLoader = " + classLoader);

            if (fullClsName == null || fullClsName.length() == 0) {
                Rlog.d(LOG_TAG, "no customized TelephonyPlugin available, fallback to default");
                fullClsName = "com.android.internal.telephony.TelephonyComponentFactory";
            }
            Class<?> cls = null;
            try {
                cls = Class.forName(fullClsName, false, classLoader);
                Rlog.d(LOG_TAG, "cls = " + cls);
                Constructor custMethod = cls.getConstructor();
                Rlog.d(LOG_TAG, "constructor method = " + custMethod);
                sInstance = (TelephonyComponentFactory) custMethod.newInstance();
            } catch (Exception  e) {
                e.printStackTrace();
                Rlog.e(LOG_TAG, "Error loading TelephonyComponentFactory");
                sInstance = new TelephonyComponentFactory();
            }
        }
        return sInstance;
    }

    public GsmCdmaCallTracker makeGsmCdmaCallTracker(GsmCdmaPhone phone) {
        Rlog.d(LOG_TAG, "makeGsmCdmaCallTracker");
        return new GsmCdmaCallTracker(phone);
    }

    public SmsStorageMonitor makeSmsStorageMonitor(Phone phone) {
        Rlog.d(LOG_TAG, "makeSmsStorageMonitor");
        return new SmsStorageMonitor(phone);
    }

    public SmsUsageMonitor makeSmsUsageMonitor(Context context) {
        Rlog.d(LOG_TAG, "makeSmsUsageMonitor");
        return new SmsUsageMonitor(context);
    }

    public ServiceStateTracker makeServiceStateTracker(GsmCdmaPhone phone, CommandsInterface ci) {
        Rlog.d(LOG_TAG, "makeServiceStateTracker");
        return new ServiceStateTracker(phone, ci);
    }

    public SimActivationTracker makeSimActivationTracker(Phone phone) {
        return new SimActivationTracker(phone);
    }

    public DcTracker makeDcTracker(Phone phone) {
        Rlog.d(LOG_TAG, "makeDcTracker");
        return new DcTracker(phone);
    }

    public CarrierSignalAgent makeCarrierSignalAgent(Phone phone) {
        return new CarrierSignalAgent(phone);
    }

    public CarrierActionAgent makeCarrierActionAgent(Phone phone) {
        return new CarrierActionAgent(phone);
    }

    public IccPhoneBookInterfaceManager makeIccPhoneBookInterfaceManager(Phone phone) {
        Rlog.d(LOG_TAG, "makeIccPhoneBookInterfaceManager");
        return new IccPhoneBookInterfaceManager(phone);
    }

    public IccSmsInterfaceManager makeIccSmsInterfaceManager(Phone phone) {
        Rlog.d(LOG_TAG, "makeIccSmsInterfaceManager");
        return new IccSmsInterfaceManager(phone);
    }

    public IccCardProxy makeIccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        Rlog.d(LOG_TAG, "makeIccCardProxy");
        return new IccCardProxy(context, ci, phoneId);
    }

    public EriManager makeEriManager(Phone phone, Context context, int eriFileSource) {
        Rlog.d(LOG_TAG, "makeEriManager");
        return new EriManager(phone, context, eriFileSource);
    }

    public WspTypeDecoder makeWspTypeDecoder(byte[] pdu) {
        Rlog.d(LOG_TAG, "makeWspTypeDecoder");
        return new WspTypeDecoder(pdu);
    }

    /**
     * Create a tracker for a single-part SMS.
     */
    public InboundSmsTracker makeInboundSmsTracker(byte[] pdu, long timestamp, int destPort,
            boolean is3gpp2, boolean is3gpp2WapPdu, String address, String displayAddr,
            String messageBody) {
        Rlog.d(LOG_TAG, "makeInboundSmsTracker");
        return new InboundSmsTracker(pdu, timestamp, destPort, is3gpp2, is3gpp2WapPdu, address,
                displayAddr, messageBody);
    }

    /**
     * Create a tracker for a multi-part SMS.
     */
    public InboundSmsTracker makeInboundSmsTracker(byte[] pdu, long timestamp, int destPort,
            boolean is3gpp2, String address, String displayAddr, int referenceNumber, int sequenceNumber,
            int messageCount, boolean is3gpp2WapPdu, String messageBody) {
        Rlog.d(LOG_TAG, "makeInboundSmsTracker");
        return new InboundSmsTracker(pdu, timestamp, destPort, is3gpp2, address, displayAddr,
                referenceNumber, sequenceNumber, messageCount, is3gpp2WapPdu, messageBody);
    }

    /**
     * Create a tracker from a row of raw table
     */
    public InboundSmsTracker makeInboundSmsTracker(Cursor cursor, boolean isCurrentFormat3gpp2) {
        return new InboundSmsTracker(cursor, isCurrentFormat3gpp2);
    }

    public ImsPhoneCallTracker makeImsPhoneCallTracker(ImsPhone imsPhone) {
        Rlog.d(LOG_TAG, "makeImsPhoneCallTracker");
        return new ImsPhoneCallTracker(imsPhone);
    }

    public ImsExternalCallTracker makeImsExternalCallTracker(ImsPhone imsPhone) {

        return new ImsExternalCallTracker(imsPhone);
    }

    /**
     * Create an AppSmsManager for per-app SMS message.
     */
    public AppSmsManager makeAppSmsManager(Context context) {
        return new AppSmsManager(context);
    }

    public DeviceStateMonitor makeDeviceStateMonitor(Phone phone) {
        return new DeviceStateMonitor(phone);
    }

    public CdmaSubscriptionSourceManager
    getCdmaSubscriptionSourceManagerInstance(Context context, CommandsInterface ci, Handler h,
                                             int what, Object obj) {
        Rlog.d(LOG_TAG, "getCdmaSubscriptionSourceManagerInstance");
        return CdmaSubscriptionSourceManager.getInstance(context, ci, h, what, obj);
    }

    public IDeviceIdleController getIDeviceIdleController() {
        Rlog.d(LOG_TAG, "getIDeviceIdleController");
        return IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
    }

    public Phone makePhone(Context context, CommandsInterface ci, PhoneNotifier notifier,
            int phoneId, int precisePhoneType,
            TelephonyComponentFactory telephonyComponentFactory) {
        Rlog.d(LOG_TAG, "makePhone");
        Phone phone = null;
        if (precisePhoneType == PhoneConstants.PHONE_TYPE_GSM) {
            phone = new GsmCdmaPhone(context,
                ci, notifier, phoneId,
                PhoneConstants.PHONE_TYPE_GSM,
                telephonyComponentFactory);
        } else if (precisePhoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                phone = new GsmCdmaPhone(context,
                ci, notifier, phoneId,
                PhoneConstants.PHONE_TYPE_CDMA_LTE,
                telephonyComponentFactory);
        }
        return phone;
    }

    public SubscriptionController initSubscriptionController(Context c, CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "initSubscriptionController");
        return SubscriptionController.init(c, ci);
    }

    public SubscriptionInfoUpdater makeSubscriptionInfoUpdater(Context context, Phone[] phones,
            CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "makeSubscriptionInfoUpdater");
        return new SubscriptionInfoUpdater(BackgroundThread.get().getLooper(), context, phones, ci);
    }
}
