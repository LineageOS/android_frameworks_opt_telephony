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
import android.content.Intent;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.IDeviceIdleController;
import android.os.Message;
import android.os.RegistrantList;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.test.SimulatedCommandsVerifier;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.HashMap;

public abstract class TelephonyTest {
    protected static String TAG;

    @Mock
    protected Phone mPhone;
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

    protected SimulatedCommands mSimulatedCommands;
    protected ContextFixture mContextFixture;
    protected Object mLock = new Object();
    protected boolean mReady;

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

        //Use reflection to mock singleton
        Field field = CallManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, mCallManager);

        //Use reflection to mock singleton
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        //Use reflection to mock singleton
        field = CdmaSubscriptionSourceManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mCdmaSSM);

        //Use reflection to mock singleton
        field = ImsManager.class.getDeclaredField("sImsManagerInstances");
        field.setAccessible(true);
        field.set(null, mImsManagerInstances);

        //Use reflection to mock singleton
        field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSubscriptionController);

        field = CdmaSubscriptionSourceManager.class.getDeclaredField(
                "mCdmaSubscriptionSourceChangedRegistrants");
        field.setAccessible(true);
        field.set(mCdmaSSM, mRegistrantList);

        field = SimulatedCommandsVerifier.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSimulatedCommandsVerifier);

        mSimulatedCommands = new SimulatedCommands();
        mContextFixture = new ContextFixture();
        mPhone.mCi = mSimulatedCommands;

        field = TelephonyManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mContextFixture.getTestDouble().
                getSystemService(Context.TELEPHONY_SERVICE));

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
        doReturn(true).when(mImsManagerInstances).containsKey(anyInt());
        doReturn(mIDeviceIdleController).when(mTelephonyComponentFactory).
                getIDeviceIdleController();
        doReturn(mContextFixture.getTestDouble()).when(mPhone).getContext();

        setReady(false);
    }

    protected static void logd(String s) {
        Log.d(TAG, s);
    }
}