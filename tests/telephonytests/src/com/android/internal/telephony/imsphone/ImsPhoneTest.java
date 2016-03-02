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

package com.android.internal.telephony.imsphone;

import android.os.HandlerThread;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsStreamMediaProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ImsPhoneTest extends TelephonyTest {
    @Mock
    ImsPhoneCall mForegroundCall;
    @Mock
    ImsPhoneCall mBackgroundCall;
    @Mock
    ImsPhoneCall mRingingCall;

    private ImsPhone mImsPhoneUT;

    private class ImsPhoneTestHandler extends HandlerThread {

        private ImsPhoneTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mImsPhoneUT = new ImsPhone(mContext, mNotifier, mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mImsCT.mForegroundCall = mForegroundCall;
        mImsCT.mBackgroundCall = mBackgroundCall;
        mImsCT.mRingingCall = mRingingCall;
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.IDLE).when(mBackgroundCall).getState();
        doReturn(Call.State.IDLE).when(mRingingCall).getState();

        new ImsPhoneTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mImsPhoneUT = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallDeflection() {
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("0"));
        try {
            verify(mImsCT).rejectCall();
        } catch (Exception e) {
            logd("Unexpected exception: " + e);
        }
    }
}
