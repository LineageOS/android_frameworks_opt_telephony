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
 * limitations under the License
 */

package com.android.internal.telephony.imsphone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsExternalCallState;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the {@link ImsExternalCallTracker}.
 */
public class ImsExternalCallTrackerTest {
    private static final Uri TEST_ADDRESS = Uri.parse("tel:6505551212");
    private static final int CALL_ID = 1;

    ImsExternalCallTracker mTracker;

    // Mocked classes
    ImsPhone mImsPhone;
    ImsPullCall mImsPullCall;
    ImsExternalCallTracker.ImsCallNotify mCallNotifier;

    @Before
    public void setUp() throws Exception {
        mImsPhone = mock(ImsPhone.class);
        mImsPullCall = mock(ImsPullCall.class);
        mCallNotifier = mock(ImsExternalCallTracker.ImsCallNotify.class);

        mTracker = new ImsExternalCallTracker(mImsPhone, mImsPullCall, mCallNotifier,
            Runnable::run);
    }

    @After
    public void tearDown() throws Exception {
        mTracker = null;
    }

    @FlakyTest
    @Test
    @Ignore
    public void testAddExternalCall() {
        List<ImsExternalCallState> dep = new ArrayList<>();
        dep.add(
                new ImsExternalCallState(CALL_ID,
                        TEST_ADDRESS,
                        false /* isPullable */,
                        ImsExternalCallState.CALL_STATE_CONFIRMED,
                        ImsCallProfile.CALL_TYPE_VOICE,
                        false /* isHeld */));

        mTracker.refreshExternalCallState(dep);

        ArgumentCaptor<Connection> connectionArgumentCaptor = ArgumentCaptor.forClass(
                Connection.class);
        verify(mCallNotifier).notifyUnknownConnection(connectionArgumentCaptor.capture());


        Connection connection = connectionArgumentCaptor.getValue();
        assert(connection instanceof ImsExternalConnection);
    }

    @FlakyTest
    @Test
    @Ignore
    public void testRemoveExternalCall() {
        testAddExternalCall();

        ImsExternalConnection connection = (ImsExternalConnection)
                mTracker.getConnectionById(CALL_ID);

        List<ImsExternalCallState> dep = new ArrayList<>();
        mTracker.refreshExternalCallState(dep);
        verify(mCallNotifier).notifyPreciseCallStateChanged();
        assertEquals(connection.getState(), Call.State.DISCONNECTED);
        assertNull(mTracker.getConnectionById(CALL_ID));
    }
}
