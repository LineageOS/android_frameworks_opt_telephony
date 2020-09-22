/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
 */

package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.HandlerThread;
import com.android.internal.telephony.TelephonyTest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;


public class RuimRecordsTest extends TelephonyTest {

    private RuimRecords mRuimRecords;

    private class RuimRecordsTestHandler extends HandlerThread {
        private RuimRecordsTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mRuimRecords = new RuimRecords(mUiccCardApplication3gpp2, mContext, mSimulatedCommands);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        new RuimRecordsTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCsimImsiLoaded() {
        RuimRecords.EfCsimImsimLoaded mImsiLoaded = mRuimRecords.new EfCsimImsimLoaded();
        AsyncResult ar = new AsyncResult(null, null, null);
        mImsiLoaded.onRecordLoaded(ar);
        String mccmnc = mRuimRecords.getRUIMOperatorNumeric();
        assertNull(mccmnc);

        byte[] byteArray = new byte[]{0, 19, 3, 75, 68, 88, 99, (byte)128, (byte)209, 0};
        AsyncResult ar2 = new AsyncResult(null, byteArray, null);
        mImsiLoaded.onRecordLoaded(ar2);
        mccmnc = mRuimRecords.getRUIMOperatorNumeric();
        assertNotNull(mccmnc);
        assertEquals("310008", mccmnc);
    }

    @Test
    public void testCsimImsiDecode() {
        RuimRecords.EfCsimImsimLoaded efCsimImsimLoaded = mRuimRecords.new EfCsimImsimLoaded();

        // mcc + mnc + min
        byte[] byteArray = new byte[]{0, 19, 3, 75, 68, 88, 99, (byte)128, (byte)209, 0};
        String imsi = efCsimImsimLoaded.decodeImsi(byteArray);

        assertEquals("310008984641186", imsi);
    }
}
