/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

package com.android.internal.telephony.dataconnection;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccRecords;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.lang.Iterable;
import java.util.Arrays;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class ApnSettingParameterizedTest extends TelephonyTest {

    private String mIccId;
    private String mGid;
    private ApnSetting mApnSetting;
    private boolean mIsMatchExpected;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Parameters
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            /*Validate that mvno data is matched for current iccid*/
            {
                "testGid",
                "a100",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                        0, new String[]{"mms"},
                        "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "iccid", "a100"),
                true
            },

            /*Validate that iccid is matched from comma separated list*/
            {
                "testGid",
                "a100",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                    0, new String[]{"mms"},
                    "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "iccid", "a100,a200"),
                true
            },

            /*Validate that iccid is matched from comma separated list, irrespective of order*/
            {
                "testGid",
                "a200",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                        0, new String[]{"mms"},
                        "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "iccid", "a100,a200"),
                true
            },

            /*Validate that mvno is not matched if mvno data is missing in apn settings*/
            {
                "testGid",
                "a200",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                        0, new String[]{"mms"},
                        "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "", ""),
                false
            },

            /*Validate that mvno is not matched if mvno data(iccid) is different in apn settings*/
            {
                "testGid",
                "a200",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                        0, new String[]{"mms"},
                        "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "iccid", "a300,a400"),
                false
            },

            /*Validate that mvno is matched if mvno data(gid) is matched in apn settings*/
            {
                "testGid",
                "a100",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                        0, new String[]{"mms"},
                        "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "gid", "testGid"),
                true
            },

            /*Validate that mvno is not matched if mvno data(gid) is different in apn settings*/
            {
                "testGid",
                "",
                new ApnSetting(-1, "12346", "Name1", "apn1", "", "", "", "", "", "", "",
                        0, new String[]{"mms"},
                        "IPV6", "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "gid", "wrongGid"),
                false
            }
        });
    }

    public ApnSettingParameterizedTest(String gid, String iccId, ApnSetting apn,
            boolean isMatchExpected) {
        mGid = gid;
        mIccId = iccId;
        mApnSetting = apn;
        mIsMatchExpected = isMatchExpected;
    }

    @Test
    @SmallTest
    public void testMvnoMathesForIccId() throws Exception {
        IccRecords r = mPhone.getIccCard().getIccRecords();
        doReturn(mGid).when(mSimRecords).getGid1();
        doReturn(mIccId).when(mSimRecords).getIccId();

        assertThat(mIsMatchExpected, is(equalTo(ApnSetting.mvnoMatches(r,
                            mApnSetting.mvnoType,
                            mApnSetting.mvnoMatchData))));

    }
}
