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

import android.test.suitebuilder.annotation.SmallTest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GsmCdmaCallTest {

    @Mock GsmCdmaCallTracker mCallTracker;
    @Mock GsmCdmaPhone mPhone;
    @Mock GsmCdmaConnection mConnection1;
    @Mock GsmCdmaConnection mConnection2;
    @Mock DriverCall mDriverCall;

    private GsmCdmaCall mCallUnderTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(mPhone).when(mCallTracker).getPhone();
        mCallUnderTest = new GsmCdmaCall(mCallTracker);
    }

    @After
    public void tearDown() throws Exception {
        mCallUnderTest = null;
    }

    @Test @SmallTest
    public void testAttachDetach() {
        //verify mConnections has 0 connections and is in IDLE state
        assertEquals(0, mCallUnderTest.mConnections.size());
        assertEquals(Call.State.IDLE, mCallUnderTest.getState());

        //attach
        mDriverCall.state = DriverCall.State.ACTIVE;
        mCallUnderTest.attach(mConnection1, mDriverCall);

        //verify mConnections has 1 connection and is not in idle
        assertEquals(1, mCallUnderTest.mConnections.size());
        assertEquals(Call.State.ACTIVE, mCallUnderTest.getState());

        //detach
        mCallUnderTest.detach(mConnection1);

        //verify mConnections has 0 connections and is in IDLE state
        assertEquals(0, mCallUnderTest.mConnections.size());
        assertEquals(Call.State.IDLE, mCallUnderTest.getState());
    }

    @Test @SmallTest
    public void testMultiparty() {
        //verify mConnections has 0 connections and is in IDLE state
        assertEquals(0, mCallUnderTest.mConnections.size());
        assertEquals(Call.State.IDLE, mCallUnderTest.getState());

        //verify isMultiparty is false
        assertEquals(false, mCallUnderTest.isMultiparty());

        //attach
        mDriverCall.state = DriverCall.State.ACTIVE;
        mCallUnderTest.attach(mConnection1, mDriverCall);

        //verify isMultiparty is false
        assertEquals(false, mCallUnderTest.isMultiparty());

        //attach
        mCallUnderTest.attach(mConnection2, mDriverCall);

        //verify isMultiparty is true
        assertEquals(true, mCallUnderTest.isMultiparty());
    }

    @Test @SmallTest
    public void testHangup() {
        //verify hangup calls mCallTracker.hangup
        try {
            mCallUnderTest.hangup();
            verify(mCallTracker).hangup(mCallUnderTest);
        } catch (Exception e) {
            fail("Exception " + e + " not expected");
        }
    }

}