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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.HandlerThread;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;

public class ImsPhoneFactoryTest extends TelephonyTest {
    // Mocked classes
    private PhoneNotifier mPhoneNotifier;

    private ImsPhone mImsPhoneUT;
    private ImsPhoneFactoryHandler mImsPhoneFactoryHandler;

    private final Executor mExecutor = Runnable::run;

    private class ImsPhoneFactoryHandler extends HandlerThread {

        private ImsPhoneFactoryHandler(String name) {
            super(name);
        }
        @Override
        public void onLooperPrepared() {
            mImsPhoneUT = ImsPhoneFactory.makePhone(mContext, mPhoneNotifier, mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mPhoneNotifier = mock(PhoneNotifier.class);
        doReturn(mExecutor).when(mContext).getMainExecutor();

        mImsPhoneFactoryHandler = new ImsPhoneFactoryHandler(getClass().getSimpleName());
        mImsPhoneFactoryHandler.start();

        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mImsPhoneFactoryHandler.quit();
        mImsPhoneFactoryHandler.join();
        mImsPhoneFactoryHandler = null;
        mImsPhoneUT = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testMakeImsPhone() throws Exception {
        assertNotNull(mImsPhoneUT);
        assertEquals(mPhone, mImsPhoneUT.getDefaultPhone());

        mImsPhoneUT.notifyDataActivity();
        verify(mPhoneNotifier, times(1)).notifyDataActivity(eq(mImsPhoneUT));
    }
}
