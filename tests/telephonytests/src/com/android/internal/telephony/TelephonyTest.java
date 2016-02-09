/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.os.Handler;
import android.os.IDeviceIdleController;
import android.os.RegistrantList;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.ImsManager;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.test.SimulatedCommandsVerifier;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.HashMap;

public abstract class TelephonyTest {
    protected static String TAG;

    @Mock
    protected GsmCdmaPhone mPhone;
    @Mock
    protected ServiceStateTracker mSST;
    @Mock
    protected GsmCdmaCallTracker mCT;
    @Mock
    protected UiccController mUiccController;
    @Mock
    protected IccCardProxy mIccCardProxy;
    @Mock
    protected CallManager mCallManager;
    @Mock
    protected PhoneNotifier mNotifier;
    @Mock
    protected TelephonyComponentFactory mTelephonyComponentFactory;
    @Mock
    protected CdmaSubscriptionSourceManager mCdmaSSM;
    @Mock
    protected RegistrantList mRegistrantList;
    @Mock
    protected IccPhoneBookInterfaceManager mIccPhoneBookIntManager;
    @Mock
    protected HashMap<Integer, ImsManager> mImsManagerInstances;
    @Mock
    protected DcTracker mDcTracker;
    @Mock
    protected GsmCdmaCall mGsmCdmaCall;
    @Mock
    protected SubscriptionController mSubscriptionController;
    @Mock
    protected ServiceState mServiceState;
    @Mock
    protected SimulatedCommandsVerifier mSimulatedCommandsVerifier;
    @Mock
    protected IDeviceIdleController mIDeviceIdleController;
    @Mock
    protected InboundSmsHandler mInboundSmsHandler;
    @Mock
    protected WspTypeDecoder mWspTypeDecoder;
    @Mock
    protected SparseArray<TelephonyEventLog> mTelephonyEventLogInstances;
    @Mock
    protected TelephonyEventLog mTelephonyEventLog;
    @Mock
    protected UiccCardApplication mUiccCardApplication;
    @Mock
    protected IccRecords mIccRecords;

    protected SimulatedCommands mSimulatedCommands;
    protected ContextFixture mContextFixture;
    protected Context mContext;
    private Object mLock = new Object();
    private boolean mReady;

    private Object mOrigCallManager;
    private Object mOrigTelephonyComponentFactory;
    private Object mOrigUiccController;
    private Object mOrigCdmaSSM;
    private Object mOrigImsManagerInstances;
    private Object mOrigSubscriptionController;
    private Object mOrigTelephonyEventLogInstances;
    private Object mOrigTelephonyManager;

    protected void waitUntilReady() {
        while(true) {
            synchronized (mLock) {
                if (mReady) {
                    break;
                }
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
        }
    }

    protected void setUp(String tag) throws Exception {
        TAG = tag;
        MockitoAnnotations.initMocks(this);
        //Use reflection to mock singletons
        Field field = CallManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        mOrigCallManager = field.get(null);
        field.set(null, mCallManager);

        field = TelephonyComponentFactory.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        mOrigTelephonyComponentFactory = field.get(null);
        field.set(null, mTelephonyComponentFactory);

        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        mOrigUiccController = field.get(null);
        field.set(null, mUiccController);

        field = CdmaSubscriptionSourceManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        mOrigCdmaSSM = field.get(null);
        field.set(null, mCdmaSSM);

        field = ImsManager.class.getDeclaredField("sImsManagerInstances");
        field.setAccessible(true);
        mOrigImsManagerInstances = field.get(null);
        field.set(null, mImsManagerInstances);

        field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        mOrigSubscriptionController = field.get(null);
        field.set(null, mSubscriptionController);

        field = TelephonyEventLog.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        mOrigTelephonyEventLogInstances = field.get(null);
        field.set(null, mTelephonyEventLogInstances);

        field = CdmaSubscriptionSourceManager.class.getDeclaredField(
                "mCdmaSubscriptionSourceChangedRegistrants");
        field.setAccessible(true);
        field.set(mCdmaSSM, mRegistrantList);

        field = SimulatedCommandsVerifier.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSimulatedCommandsVerifier);

        mSimulatedCommands = new SimulatedCommands();
        mContextFixture = new ContextFixture();
        mContext = mContextFixture.getTestDouble();
        mPhone.mCi = mSimulatedCommands;

        field = TelephonyManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        mOrigTelephonyManager = field.get(null);
        field.set(null, mContext.getSystemService(Context.TELEPHONY_SERVICE));

        doReturn(mSST).when(mTelephonyComponentFactory).
                makeServiceStateTracker(any(GsmCdmaPhone.class), any(CommandsInterface.class));
        doReturn(mIccCardProxy).when(mTelephonyComponentFactory).
                makeIccCardProxy(any(Context.class), any(CommandsInterface.class), anyInt());
        doReturn(mCT).when(mTelephonyComponentFactory).
                makeGsmCdmaCallTracker(any(GsmCdmaPhone.class));
        doReturn(mIccPhoneBookIntManager).when(mTelephonyComponentFactory).
                makeIccPhoneBookInterfaceManager(any(Phone.class));
        doReturn(mDcTracker).when(mTelephonyComponentFactory).
                makeDcTracker(any(Phone.class));
        doReturn(mIDeviceIdleController).when(mTelephonyComponentFactory).
                getIDeviceIdleController();
        doReturn(mWspTypeDecoder).when(mTelephonyComponentFactory).
                makeWspTypeDecoder(any(byte[].class));
        doReturn(mCdmaSSM).when(mTelephonyComponentFactory).
                getCdmaSubscriptionSourceManagerInstance(any(Context.class),
                        any(CommandsInterface.class), any(Handler.class),
                        anyInt(), any(Object.class));

        doReturn(mContext).when(mPhone).getContext();
        doReturn(true).when(mPhone).getUnitTestMode();

        doReturn(true).when(mImsManagerInstances).containsKey(anyInt());

        doReturn(mPhone).when(mInboundSmsHandler).getPhone();

        doReturn(mTelephonyEventLog).when(mTelephonyEventLogInstances).get(anyInt());

        doReturn(mUiccCardApplication).when(mUiccController).getUiccCardApplication(anyInt(),
                anyInt());

        doReturn(mIccRecords).when(mUiccCardApplication).getIccRecords();

        setReady(false);
    }

    protected void tearDown() throws Exception {
        //Reset fields that were set using reflection
        Field field = CallManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, mOrigCallManager);

        field = TelephonyComponentFactory.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mOrigTelephonyComponentFactory);

        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mOrigUiccController);

        field = CdmaSubscriptionSourceManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mOrigCdmaSSM);

        field = ImsManager.class.getDeclaredField("sImsManagerInstances");
        field.setAccessible(true);
        field.set(null, mOrigImsManagerInstances);

        field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mOrigSubscriptionController);

        field = TelephonyEventLog.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        field.set(null, mOrigTelephonyEventLogInstances);

        field = TelephonyManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mOrigTelephonyManager);
    }

    protected static void logd(String s) {
        Log.d(TAG, s);
    }
}