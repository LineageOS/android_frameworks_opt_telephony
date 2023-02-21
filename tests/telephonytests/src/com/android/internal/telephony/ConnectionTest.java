/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Looper;

import com.android.internal.telephony.emergency.EmergencyNumberTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test verifying the methods of the connection class.
 */
public class ConnectionTest extends TelephonyTest {

    private static final int TEST_PHONE_TYPE = 1;

    // Mocked classes
    protected Call mCall;
    protected EmergencyNumberTracker mEmergencyNumberTracker2;
    protected Connection.PhoneFactoryProxy mPhoneFactoryProxy;
    protected Connection mTestConnection;

    private class TestConnection extends Connection {

        private TestConnection(int phoneType) {
            super(phoneType);
        }

        @Override
        public Call getCall() {
            return mCall;
        }

        @Override
        public long getDisconnectTime() {
            return 0;
        }

        @Override
        public long getHoldDurationMillis() {
            return 0;
        }

        @Override
        public String getVendorDisconnectCause() {
            return "";
        }

        @Override
        public void deflect(String number) throws CallStateException {}

        @Override
        public void transfer(String number, boolean isConfirmationRequired)
                throws CallStateException {}

        @Override
        public void consultativeTransfer(Connection other) throws CallStateException {}

        @Override
        public void hangup() throws CallStateException {}

        @Override
        public void separate() throws CallStateException {}

        @Override
        public void proceedAfterWaitChar() {}

        @Override
        public void proceedAfterWildChar(String str) {}

        @Override
        public void cancelPostDial() {}

        @Override
        public int getNumberPresentation() {
            return 0;
        }

        @Override
        public UUSInfo getUUSInfo() {
            return null;
        }

        @Override
        public int getPreciseDisconnectCause() {
            return 0;
        }

        @Override
        public boolean isMultiparty() {
            return false;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mCall = mock(Call.class);
        doReturn(mPhone).when(mCall).getPhone();
        doReturn(mPhone).when(mCT).getPhone();
        replaceInstance(Handler.class, "mLooper", mCT, Looper.getMainLooper());

        mEmergencyNumberTracker2 = mock(EmergencyNumberTracker.class);
        doReturn(mEmergencyNumberTracker2).when(mPhone2).getEmergencyNumberTracker();

        mTestConnection = new TestConnection(TEST_PHONE_TYPE);
        mPhoneFactoryProxy = mock(Connection.PhoneFactoryProxy.class);
        mTestConnection.setPhoneFactoryProxy(mPhoneFactoryProxy);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testMigrateFrom() {
        Connection connection1 = new TestConnection(TEST_PHONE_TYPE);
        Connection connection2 = new TestConnection(TEST_PHONE_TYPE);

        // Verify Emergency Call info is migrated
        assertFalse(connection1.isEmergencyCall());
        assertNull(connection1.getEmergencyNumberInfo());
        assertFalse(connection1.hasKnownUserIntentEmergency());

        connection2.setEmergencyCallInfo(mPhone.getCallTracker());
        connection2.setHasKnownUserIntentEmergency(true);
        connection1.migrateFrom(connection2);

        assertTrue(connection1.isEmergencyCall());
        assertEquals(getTestEmergencyNumber(), connection1.getEmergencyNumberInfo());
        assertTrue(connection1.hasKnownUserIntentEmergency());

        // TODO Verify more fields during the migration
    }

    @Test
    public void testEmergencyCallParameters() {
        Connection connection = new TestConnection(TEST_PHONE_TYPE);
        connection.setEmergencyCallInfo(mPhone.getCallTracker());
        assertTrue(connection.isEmergencyCall());
        assertEquals(getTestEmergencyNumber(), connection.getEmergencyNumberInfo());
        connection.setHasKnownUserIntentEmergency(true);
        assertTrue(connection.hasKnownUserIntentEmergency());
    }

    @Test
    public void testSetEmergencyCallInfo() {
        //Replicate Dual-SIM:
        Phone [] phones = {mPhone, mPhone2};
        when(mPhoneFactoryProxy.getPhones()).thenReturn(phones);
        doReturn(1).when(mPhone).getPhoneId();
        doReturn(2).when(mPhone2).getPhoneId();

        //Replicate behavior when a number is an emergency number
        // on the secondary SIM but not on the default SIM:
        when(mPhone.getEmergencyNumberTracker().getEmergencyNumber(any())).thenReturn(null);
        when(mEmergencyNumberTracker2.getEmergencyNumber(any()))
                .thenReturn(getTestEmergencyNumber());

        //Ensure the connection is considered as an emergency call:
        mTestConnection.setEmergencyCallInfo(mCT);
        assertTrue(mTestConnection.isEmergencyCall());
    }

    // TODO Verify more methods in Connection
}
