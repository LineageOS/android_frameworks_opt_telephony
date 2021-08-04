/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.IBinder;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipDelegateStateCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.stub.SipDelegate;
import android.telephony.ims.stub.SipTransportImplBase;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class SipTransportImplBaseTest {

    private static final int TEST_SUB_ID = 1;

    private static class TestSipTransport extends SipTransportImplBase {

        private static class SipDelegateContainer {
            public final int subId;
            public final DelegateRequest delegateRequest;
            public final DelegateStateCallback delegateStateCallback;
            public final DelegateMessageCallback delegateMessageCallback;
            public final SipDelegate sipDelegate;

            SipDelegateContainer(int subId, DelegateRequest request,
                    DelegateStateCallback dc, DelegateMessageCallback mc, SipDelegate delegate) {
                this.subId = subId;
                delegateRequest = request;
                delegateStateCallback = dc;
                delegateMessageCallback = mc;
                sipDelegate = delegate;
            }
        }

        private final Set<SipDelegateContainer> mDelegates = new ArraySet<>();

        TestSipTransport(Executor executor) {
            super(executor);
        }

        @Override
        public void createSipDelegate(int subscriptionId, DelegateRequest request,
                DelegateStateCallback dc, DelegateMessageCallback mc) {
            SipDelegate mockDelegate = mock(SipDelegate.class);
            SipDelegateContainer container = new SipDelegateContainer(subscriptionId, request, dc,
                    mc, mockDelegate);
            mDelegates.add(container);
            dc.onCreated(mockDelegate, Collections.emptySet());
        }

        @Override
        public void destroySipDelegate(SipDelegate delegate, int reason) {
            mDelegates.removeIf(candidate -> {
                if (delegate.equals(candidate.sipDelegate)) {
                    candidate.delegateStateCallback.onDestroyed(reason);
                    return true;
                }
                return false;
            });
        }

        public boolean isTrackedDelegateSetEmpty() {
            return mDelegates.isEmpty();
        }
    }

    @Test
    public void createDestroyDelegate() throws Exception {
        // Set up the executor to simply run inline
        TestSipTransport t = new TestSipTransport(Runnable::run);

        ISipDelegateStateCallback stateCb = mock(ISipDelegateStateCallback.class);
        IBinder stateBinder = mock(IBinder.class);
        doReturn(stateBinder).when(stateCb).asBinder();
        ISipDelegateMessageCallback messageCb = mock(ISipDelegateMessageCallback.class);

        ISipDelegate delegate = createSipDelegate(t, stateCb, messageCb);
        assertFalse(t.isTrackedDelegateSetEmpty());
        ArgumentCaptor<IBinder.DeathRecipient> captor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(stateBinder).linkToDeath(captor.capture(), anyInt());
        assertNotNull(captor.getValue());

        destroySipDelegate(t, delegate,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(stateBinder).unlinkToDeath(eq(captor.getValue()), anyInt());
        verify(stateCb).onDestroyed(
                eq(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP));
    }


    @Test
    public void testPhoneProcessCrash() throws Exception {
        // Set up the executor to simply run inline
        TestSipTransport t = new TestSipTransport(Runnable::run);

        ISipDelegateStateCallback stateCb = mock(ISipDelegateStateCallback.class);
        IBinder stateBinder = mock(IBinder.class);
        doReturn(stateBinder).when(stateCb).asBinder();
        ISipDelegateMessageCallback messageCb = mock(ISipDelegateMessageCallback.class);

        createSipDelegate(t, stateCb, messageCb);
        assertFalse(t.isTrackedDelegateSetEmpty());
        ArgumentCaptor<IBinder.DeathRecipient> captor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(stateBinder).linkToDeath(captor.capture(), anyInt());
        assertNotNull(captor.getValue());
        IBinder.DeathRecipient recipient = captor.getValue();

        // simulate phone process crash
        recipient.binderDied(stateBinder);
        verify(stateCb).onDestroyed(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        assertTrue(t.isTrackedDelegateSetEmpty());
    }

    private ISipDelegate createSipDelegate(TestSipTransport transport,
            ISipDelegateStateCallback stateCb,
            ISipDelegateMessageCallback messageCb) throws Exception {
        ISipTransport transportBinder = transport.getBinder();
        transportBinder.createSipDelegate(TEST_SUB_ID, new DelegateRequest(Collections.emptySet()),
                stateCb, messageCb);
        ArgumentCaptor<ISipDelegate> captor = ArgumentCaptor.forClass(ISipDelegate.class);
        verify(stateCb).onCreated(captor.capture(), anyList());
        assertNotNull(captor.getValue());
        return captor.getValue();
    }

    private void destroySipDelegate(TestSipTransport transport, ISipDelegate delegate,
            int reason) throws Exception {
        ISipTransport transportBinder = transport.getBinder();
        transportBinder.destroySipDelegate(delegate, reason);
    }
}
