/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

public class CarrierActionAgentTest extends TelephonyTest {
    private CarrierActionAgent mCarrierActionAgentUT;
    private FakeContentResolver mFakeContentResolver;
    private FakeContentProvider mFakeContentProvider;
    private static int DATA_CARRIER_ACTION_EVENT = 0;
    private static int RADIO_CARRIER_ACTION_EVENT = 1;
    @Mock
    private Handler mDataActionHandler;
    @Mock
    private Handler mRadioActionHandler;

    private class FakeContentResolver extends MockContentResolver {
        @Override
        public void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) {
            super.notifyChange(uri, observer, syncToNetwork);
            logd("onChanged(uri=" + uri + ")" + observer);
            if (observer != null) {
                observer.dispatchChange(false, uri);
            } else {
                mCarrierActionAgentUT.getContentObserver().dispatchChange(false, uri);
            }
        }
    }

    private class FakeContentProvider extends MockContentProvider {
        private int mExpectedValue;
        public void simulateChange(Uri uri) {
            mFakeContentResolver.notifyChange(uri, null);
        }
        @Override
        public Bundle call(String method, String request, Bundle args) {
            Bundle result = new Bundle();
            if (Settings.CALL_METHOD_GET_GLOBAL.equals(method)) {
                result.putString(Settings.NameValueTable.VALUE, Integer.toString(mExpectedValue));
            } else {
                mExpectedValue = Integer.parseInt(args.getString(Settings.NameValueTable.VALUE));
            }
            return result;
        }
    }

    private class CarrierActionAgentHandler extends HandlerThread {

        private CarrierActionAgentHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mCarrierActionAgentUT = new CarrierActionAgent(mPhone);
            mCarrierActionAgentUT.registerForCarrierAction(
                    CarrierActionAgent.CARRIER_ACTION_SET_METERED_APNS_ENABLED, mDataActionHandler,
                    DATA_CARRIER_ACTION_EVENT, null, false);
            mCarrierActionAgentUT.registerForCarrierAction(
                    CarrierActionAgent.CARRIER_ACTION_SET_RADIO_ENABLED, mRadioActionHandler,
                    RADIO_CARRIER_ACTION_EVENT, null, false);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("CarrierActionAgentTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mFakeContentResolver = new FakeContentResolver();
        mFakeContentProvider = new FakeContentProvider();
        mFakeContentResolver.addProvider(Settings.AUTHORITY, mFakeContentProvider);
        doReturn(mFakeContentResolver).when(mContext).getContentResolver();
        new CarrierActionAgentHandler(getClass().getSimpleName()).start();
        waitUntilReady();
        logd("CarrierActionAgentTest -Setup!");
    }

    @Test
    @SmallTest
    public void testCarrierActionResetOnAPM() {
        Settings.Global.putInt(mFakeContentResolver, Settings.Global.AIRPLANE_MODE_ON, 1);
        mFakeContentProvider.simulateChange(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON));
        waitForMs(50);
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);

        verify(mDataActionHandler).sendMessageAtTime(message.capture(), anyLong());
        assertEquals(DATA_CARRIER_ACTION_EVENT, message.getValue().what);

        verify(mRadioActionHandler).sendMessageAtTime(message.capture(), anyLong());
        assertEquals(RADIO_CARRIER_ACTION_EVENT, message.getValue().what);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
