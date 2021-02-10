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
 * limitations under the License.
 */

package android.telephony.ims;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.telephony.AccessNetworkConstants;
import android.telephony.BinderCacheManager;
import android.telephony.ims.aidl.IImsRegistrationCallback;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class ImsMmTelManagerTests extends TelephonyTest {

    @Mock ITelephony mMockTelephonyInterface;
    @Mock BinderCacheManager<ITelephony> mBinderCache;

    public class LocalCallback extends ImsMmTelManager.RegistrationCallback {
        int mRegResult = -1;

        @Override
        public void onRegistered(int imsRadioTech) {
            mRegResult = imsRadioTech;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("ImsMmTelManagerTests");
        doReturn(mMockTelephonyInterface).when(mBinderCache).getBinder();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Ensure that LTE-> WWAN and IWLAN-> WLAN map correctly as well as ensure that wacky values
     * result in a -1 result.
     */
    @SmallTest
    @Test
    public void testDeprecatedCallbackValues() throws Exception {
        LocalCallback cb = new LocalCallback();
        ImsMmTelManager managerUT = new ImsMmTelManager(0, mBinderCache);
        managerUT.registerImsRegistrationCallback(Runnable::run, cb);

        // Capture the RegistrationCallback that was registered.
        ArgumentCaptor<IImsRegistrationCallback> callbackCaptor =
                ArgumentCaptor.forClass(IImsRegistrationCallback.class);
        verify(mMockTelephonyInterface).registerImsRegistrationCallback(anyInt(),
                callbackCaptor.capture());

        IImsRegistrationCallback cbBinder = callbackCaptor.getValue();
        // Ensure the transport types are correct
        cbBinder.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).build());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, cb.mRegResult);
        cbBinder.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).build());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, cb.mRegResult);
        cbBinder.onRegistered(new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_NONE).build());
        assertEquals(-1, cb.mRegResult);
        // Wacky value
        cbBinder.onRegistered(new ImsRegistrationAttributes.Builder(0xDEADBEEF).build());
        assertEquals(-1, cb.mRegResult);
    }
}
