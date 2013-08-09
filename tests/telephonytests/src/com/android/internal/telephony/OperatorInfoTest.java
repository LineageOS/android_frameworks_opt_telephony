/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

package com.android.internal.telephony;

import junit.framework.TestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.OperatorInfo;

/**
 * {@hide}
 */
public class OperatorInfoTest extends TestCase {

    @SmallTest
    public void testBasic() throws Exception {
        OperatorInfo info;

        // OperatorInfo with no RAT
        info = new OperatorInfo("China Mobile", "CMCC", "46000");

        assertEquals("China Mobile", info.getOperatorAlphaLong());
        assertEquals("CMCC", info.getOperatorAlphaShort());
        assertEquals("46000", info.getOperatorNumeric());
        assertEquals("", info.getRadioTech());

        // OperatorInfo with RAT
        info = new OperatorInfo("China Mobile", "CMCC", "46000+17");
        assertEquals("China Mobile", info.getOperatorAlphaLong());
        assertEquals("CMCC", info.getOperatorAlphaShort());
        assertEquals("46000", info.getOperatorNumeric());
        assertEquals("17", info.getRadioTech());
    }
}
