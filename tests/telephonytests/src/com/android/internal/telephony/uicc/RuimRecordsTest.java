/* Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.internal.telephony.uicc;

import org.mockito.Mock;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import org.mockito.MockitoAnnotations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.android.internal.telephony.TelephonyTest;

import android.content.Context;
import android.os.AsyncResult;
import android.os.HandlerThread;

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
         String mccmnc = mRuimRecords.getOperatorNumeric();
         assertNull(mccmnc);

         byte[] byteArray = new byte[]{0,19,3,75,68,88,99,(byte)128,(byte)209,0};
         AsyncResult ar2 = new AsyncResult(null, byteArray, null);
         mImsiLoaded.onRecordLoaded(ar2);
         mccmnc = mRuimRecords.getOperatorNumeric();
         assertNotNull(mccmnc);
         assertEquals("31000", mccmnc);
    }
}
