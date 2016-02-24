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

import android.app.PendingIntent;
import android.content.Intent;
import android.os.HandlerThread;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConfig;
import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsServiceClass;
import com.android.ims.internal.ImsCallSession;
import com.android.internal.telephony.imsphone.ImsPhoneCall;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import android.os.Bundle;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

public class ImsPhoneCallTrackerTest extends TelephonyTest {
    private ImsPhoneCallTracker mCTUT;
    private ImsConnectionStateListener mImsConnectionStateListener;
    private ImsCall.Listener mImsCallListener;
    private ImsCall mImsCall;
    private ImsCall mSecondImsCall;
    private Bundle mBundle = new Bundle();
    @Mock
    private ImsCallSession mImsCallSession;

    private class ImsCTHandlerThread extends HandlerThread {

        private ImsCTHandlerThread(String name) {
            super(name);
        }
        @Override
        public void onLooperPrepared() {
            mCTUT = new ImsPhoneCallTracker(mImsPhone);
            setReady(true);
        }
    }

    private void imsCallMocking(final ImsCall mImsCall) throws Exception {

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // trigger the listener on accept call
                if (mImsCallListener != null) {
                    mImsCallListener.onCallStarted(mImsCall);
                }
                return null;
            }
        }).when(mImsCall).accept(anyInt());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // trigger the listener on reject call
                int reasonCode = (int) invocation.getArguments()[0];
                if (mImsCallListener != null) {
                    mImsCallListener.onCallStartFailed(mImsCall, new ImsReasonInfo(reasonCode, -1));
                    mImsCallListener.onCallTerminated(mImsCall, new ImsReasonInfo(reasonCode, -1));
                }
                return null;
            }
        }).when(mImsCall).reject(anyInt());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // trigger the listener on reject call
                int reasonCode = (int) invocation.getArguments()[0];
                if (mImsCallListener != null) {
                    mImsCallListener.onCallTerminated(mImsCall, new ImsReasonInfo(reasonCode, -1));
                }
                return null;
            }
        }).when(mImsCall).terminate(anyInt());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                if (mImsCallListener != null) {
                    mImsCallListener.onCallHeld(mImsCall);
                }
                return null;
            }
        }).when(mImsCall).hold();

        doReturn(mImsCallSession).when(mImsCall).getCallSession();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        mImsCallProfile.mCallExtras = mBundle;
        mImsManagerInstances.put(mImsPhone.getPhoneId(), mImsManager);
        mImsCall = spy(new ImsCall(mContext, mImsCallProfile));
        mSecondImsCall = spy(new ImsCall(mContext, mImsCallProfile));
        imsCallMocking(mImsCall);
        imsCallMocking(mSecondImsCall);

        //cache the listener
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                mImsConnectionStateListener =
                        (ImsConnectionStateListener) invocation.getArguments()[2];
                return 0;
            }
        }).when(mImsManager).open(anyInt(), (PendingIntent) any(),
                (ImsConnectionStateListener) any());

        doAnswer(new Answer<ImsCall>() {
            @Override
            public ImsCall answer(InvocationOnMock invocation) throws Throwable {
                mImsCallListener =
                        (ImsCall.Listener) invocation.getArguments()[2];
                return mImsCall;
            }
        }).when(mImsManager).takeCall(eq(0), (Intent) any(), (ImsCall.Listener) any());

        new ImsCTHandlerThread(this.getClass().getSimpleName()).start();

        waitUntilReady();
        logd("ImsPhoneCallTracker initiated");
        /* Make sure getImsService is triggered on a separate thread */
        waitForMs(100);
    }

    @After
    public void tearDown() throws Exception {
        mCTUT = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testImsFeatureCapabilityChange() {
        int[] featureEnableArray = {-1, -1, -1, -1, -1, -1},
                featureDisableArray = {-1, -1, -1, -1, -1, -1};
        assertFalse(mCTUT.isVolteEnabled());
        assertFalse(mCTUT.isVideoCallEnabled());
        //enable VoLTE feature
        featureEnableArray[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] =
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        mImsConnectionStateListener.onFeatureCapabilityChanged(ImsServiceClass.MMTEL,
                featureEnableArray,
                featureDisableArray);
        assertTrue(mCTUT.isVolteEnabled());
        assertFalse(mCTUT.isVideoCallEnabled());
        // video call not enabled
        verify(mImsPhone, times(0)).notifyForVideoCapabilityChanged(anyBoolean());
        verify(mImsPhone, times(1)).onFeatureCapabilityChanged();
        // enable video call
        featureEnableArray[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] =
                ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE;
        mImsConnectionStateListener.onFeatureCapabilityChanged(ImsServiceClass.MMTEL,
                featureEnableArray,
                featureDisableArray);
        assertTrue(mCTUT.isVideoCallEnabled());
        verify(mImsPhone, times(1)).notifyForVideoCapabilityChanged(eq(true));
    }

    @Test
    @SmallTest
    public void testImsMTCall() {
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        // mock a MT call
        Intent mIntent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL);
        mContext.sendBroadcast(mIntent);
        verify(mImsPhone, times(1)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(1)).notifyIncomingRing();
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());
        assertTrue(mCTUT.mRingingCall.isRinging());
        assertEquals(1, mCTUT.mRingingCall.getConnections().size());
    }

    @Test
    @SmallTest
    public void testImsMTCallAccept() {
        testImsMTCall();
        assertTrue(mCTUT.mRingingCall.isRinging());
        try {
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
            verify(mImsCall, times(1)).accept(eq(ImsCallProfile.
                    getCallTypeFromVideoState(ImsCallProfile.CALL_TYPE_VOICE)));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(1, mCTUT.mForegroundCall.getConnections().size());
    }

    @Test
    @SmallTest
    public void testImsMTCallReject() {
        testImsMTCall();
        assertTrue(mCTUT.mRingingCall.isRinging());
        try {
            mCTUT.rejectCall();
            verify(mImsCall, times(1)).reject(eq(ImsReasonInfo.CODE_USER_DECLINE));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(0, mCTUT.mRingingCall.getConnections().size());
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
    }

    @Test
    @SmallTest
    public void testImsMTCallAcceptHangUp() {
        testImsMTCallAccept();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        try {
            mCTUT.hangup(mCTUT.mForegroundCall);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
    }

    @Test
    @SmallTest
    public void testImsMTCallAcceptHold() {
        testImsMTCallAccept();

        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        // mock a new MT
        try {
            doReturn(mSecondImsCall).when(mImsManager).takeCall(eq(0), (Intent) any(),
                    (ImsCall.Listener) any());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        Intent mIntent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL);
        mContext.sendBroadcast(mIntent);

        verify(mImsPhone, times(2)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(2)).notifyIncomingRing();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(ImsPhoneCall.State.WAITING, mCTUT.mRingingCall.getState());
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());

        //hold the foreground active call, accept the new ringing call
        try {
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
            verify(mImsCall, times(1)).hold();
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }

        waitForMs(100);
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(Call.State.HOLDING, mCTUT.mBackgroundCall.getState());
    }
}
