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

package android.telephony.ims.internal;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.os.RemoteException;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.internal.aidl.IImsMmTelFeature;
import android.telephony.ims.internal.feature.ImsFeature;
import android.telephony.ims.internal.feature.MmTelFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.ImsCallSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class MmTelFeatureTests {

    private static final int TEST_CAPABILITY = 1;
    private static final int TEST_RADIO_TECH = 0;

    private TestMmTelFeature mFeature;
    private IImsMmTelFeature mFeatureBinder;
    private ImsFeature.CapabilityCallback mCapabilityCallback;
    private MmTelFeature.Listener mListener;

    @Before
    public void setup() throws RemoteException {
        mFeature = new TestMmTelFeature();
        mFeatureBinder = mFeature.getBinder();
        mCapabilityCallback = spy(new ImsFeature.CapabilityCallback());
        mListener = spy(new MmTelFeature.Listener());
        mFeatureBinder.setListener(mListener);
    }

    @After
    public void tearDown() {
        mFeature = null;
        mFeatureBinder = null;
    }

    @SmallTest
    @Test
    public void testQueryCapabilityConfiguration() throws Exception {
        mFeature.queryConfigurationResult = true;

        mFeatureBinder.queryCapabilityConfiguration(TEST_CAPABILITY, TEST_RADIO_TECH,
                mCapabilityCallback);

        verify(mCapabilityCallback).onQueryCapabilityConfiguration(eq(TEST_CAPABILITY),
                eq(TEST_RADIO_TECH), eq(true));
    }

    @SmallTest
    @Test
    public void testNewIncomingCall() throws Exception {
        IImsCallSession sessionBinder = Mockito.mock(IImsCallSession.class);
        ImsCallSession session = new ImsCallSession(sessionBinder);

        mFeature.incomingCall(session);
        ArgumentCaptor<ImsCallSession> captor = ArgumentCaptor.forClass(ImsCallSession.class);
        verify(mListener).onIncomingCall(captor.capture());

        assertEquals(sessionBinder, captor.getValue().getSession());
    }
}
