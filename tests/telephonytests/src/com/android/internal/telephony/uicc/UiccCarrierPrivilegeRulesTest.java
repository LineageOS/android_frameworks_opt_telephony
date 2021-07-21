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
package com.android.internal.telephony.uicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;

import android.content.pm.Signature;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.UiccAccessRule;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UiccCarrierPrivilegeRulesTest extends TelephonyTest {
    private UiccCarrierPrivilegeRules mUiccCarrierPrivilegeRules;

    private static final String ARAM = "A00000015141434C00";
    private static final String ARAD = "A00000015144414300";
    private static final String PKCS15_AID = "A000000063504B43532D3135";

    @Mock
    private UiccProfile mUiccProfile;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mUiccCarrierPrivilegeRules = null;
    }

    private void testHelper(String hexString) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[2];
                AsyncResult ar = new AsyncResult(null, new int[]{0}, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes(hexString));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyString(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();
    }

    @Test
    @SmallTest
    public void testParseRule_Normal() {
        /**
         * FF40 45
         *   E2 43
         *      E1 35
         *         C1 14 ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4
         *         CA 1D 636F6D2E676F6F676C652E616E64726F69642E617070732E6D79617070
         *      E3 0A
         *         DB 08 0000000000000001
         */
        final String hexString =
                "FF4045E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4CA1D636F6D2E676F6F676"
                        + "C652E616E64726F69642E617070732E6D79617070E30ADB080000000000000001";

        testHelper(hexString);

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(2, mUiccCarrierPrivilegeRules.getPackageNames().size());
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(0));
        Signature signature = new Signature("abcd92cbb156b280fa4e1429a6eceeb6e5c1bfe4");
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));
    }

    @Test
    @SmallTest
    public void testParseRule_With4FD0D1() {
        /**
         * FF40 34
         *   E2 32
         *      E1 1E
         *         4F 06 FF FF FF FF FF FF
         *         C1 14 B6 1B E3 4A D2 C2 0D 7A FE D8 49 3C 31 3A 13 7F 89 FA 27 65
         *      E3 10
         *         D0 01 01
         *         D1 01 01
         *         DB 08 00 00 00 00 00 00 00 01
         */
        final String hexString = "FF4034E232E11E4F06FFFFFFFFFFFFC114B61BE34AD2C20D7AFED84"
                + "93C313A137F89FA2765E310D00101D10101DB080000000000000001";

        testHelper(hexString);

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(0, mUiccCarrierPrivilegeRules.getPackageNames().size());
    }

    @Test
    @SmallTest
    public void testParseRule_With4FD0() {
        /**
         * FF40 31
         *   E2 2F
         *      E1 1E
         *         4F 06 FF FF FF FF FF FF
         *         C1 14 B6 1B E3 4A D2 C2 0D 7A FE D8 49 3C 31 3A 13 7F 89 FA 27 65
         *      E3 0D
         *         D0 01 01
         *         DB 08 00 00 00 00 00 00 00 01
         */
        final String hexString = "FF4031E22FE11E4F06FFFFFFFFFFFFC114B61BE34AD2C20D7AFED8493C313A"
                + "137F89FA2765E30DD00101DB080000000000000001";

        testHelper(hexString);

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(0, mUiccCarrierPrivilegeRules.getPackageNames().size());
    }

    @Test
    @SmallTest
    public void testParseRule_TwoMessages() {
        /**
         * FF40 68
         *   E2 39
         *      E1 2B
         *         4F 06 FFFFFFFFFFFF
         *         C1 02 B61B
         *         CA 1D 636F6D2E676F6F676C652E616E64726F69642E617070732E6D79617070
         *      E3 0A
         *         D0 01 01
         *         D1 01 01
         *         DB 02 0001
         *   E2 2B
         *      E1 23
         *         C1 02 ABCD
         *         CA 1D 636F6D2E676F6F676C652E616E64726F69642E617070732E6D79617070
         *      E3 04
         *         DB 02 0001
         */
        final String hexString =
                "FF4068E239E12B4F06FFFFFFFFFFFFC102B61BCA1D636F6D2E676F6F676C652E616E64726F69642"
                        + "E617070732E6D79617070E30AD00101D10101DB020001E22BE123C102ABCDCA1D636F"
                        + "6D2E676F6F676C652E616E64726F69642E617070732E6D79617070E304DB020001";

        testHelper(hexString);

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(4, mUiccCarrierPrivilegeRules.getPackageNames().size());
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(0));
        Signature signature1 = new Signature("b61b");
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature1,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));

        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(1));
        Signature signature2 = new Signature("abcd");
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature2,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));
    }

    @Test
    @SmallTest
    public void testParseRule_InvalidRulesWith4F00() {
        /**
         * FF40 24
         *   E2 22
         *      E1 18
         *         4F 00
         *         C1 14 75C073AFD219AEB221948E828F066E778ADFDF23
         *      E3 06
         *         D0 01 01
         *         D1 01 01
         */
        final String hexString = "FF4024E222E1184F00C11475C073AFD219AEB221948E828F066E778ADFDF23"
                + "E306D00101D10101";

        testHelper(hexString);

        assertTrue(!mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(0, mUiccCarrierPrivilegeRules.getPackageNames().size());
    }

    @Test
    @SmallTest
    public void testParseRule_InvalidRulesWithoutDB() {
        /**
         * FF40 2A
         *   E2 28
         *      E1 1E
         *         4F 06 FF FF FF FF FF FF
         *         C1 14 B6 1B E3 4A D2 C2 0D 7A FE D8 49 3C 31 3A 13 7F 89 FA 27 65
         *      E3 06
         *         D0 01 01
         *         D1 01 01
         */
        final String hexString = "FF402AE228E11E4F06FFFFFFFFFFFFC114B61BE34AD2C20D7AFED8493C313A"
                + "137F89FA2765E306D00101D10101";

        testHelper(hexString);

        assertTrue(!mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(0, mUiccCarrierPrivilegeRules.getPackageNames().size());
    }

    @Test
    @SmallTest
    public void testRetryARAM_shouldRetry() {
        AsyncResult ar1 = new AsyncResult(
                null,
                new int[]{0, 105, -123},
                new CommandException(CommandException.Error.NO_SUCH_ELEMENT));
        assertTrue(mUiccCarrierPrivilegeRules.shouldRetry(ar1, 0));

        AsyncResult ar2 = new AsyncResult(
                null,
                new int[]{0},
                new CommandException(CommandException.Error.MISSING_RESOURCE));
        assertTrue(mUiccCarrierPrivilegeRules.shouldRetry(ar2, 0));

        AsyncResult ar3 = new AsyncResult(
                null,
                new int[]{0, 105, 153},
                new CommandException(CommandException.Error.INTERNAL_ERR));
        assertTrue(mUiccCarrierPrivilegeRules.shouldRetry(ar3, 0));
    }

    @Test
    @SmallTest
    public void testRetryARAM_shouldNotRetry() {
        AsyncResult ar = new AsyncResult(
                null,
                new int[]{0, 106, -126},
                new CommandException(CommandException.Error.NO_SUCH_ELEMENT));
        assertTrue(!mUiccCarrierPrivilegeRules.shouldRetry(ar, 0));
    }

    @Test
    @SmallTest
    public void testAID_OnlyARAM() {
        final String hexString =
                "FF4045E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4CA1D636F6D2E676F6F676"
                        + "C652E616E64726F69642E617070732E6D79617070E30ADB080000000000000001";
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                if (aid.equals(ARAM)) {
                    AsyncResult ar = new AsyncResult(null, new int[]{0}, null);
                    message.obj = ar;
                    message.arg2 = 1;
                    message.sendToTarget();
                } else {
                    AsyncResult ar = new AsyncResult(null, null, null);
                    message.obj = ar;
                    message.sendToTarget();
                }
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes(hexString));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyString(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));


        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(1, mUiccCarrierPrivilegeRules.getPackageNames().size());
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(0));
        Signature signature = new Signature("abcd92cbb156b280fa4e1429a6eceeb6e5c1bfe4");
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));
    }

    @Test
    @SmallTest
    public void testAID_OnlyARAD() {
        final String hexString =
                "FF4045E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4CA1D636F6D2E676F6F676"
                        + "C652E616E64726F69642E617070732E6D79617070E30ADB080000000000000001";
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                if (aid.equals(ARAD)) {
                    AsyncResult ar = new AsyncResult(null, new int[]{0}, null);
                    message.obj = ar;
                    message.arg2 = 0;
                    message.sendToTarget();
                } else {
                    AsyncResult ar = new AsyncResult(null, null, null);
                    message.obj = ar;
                    message.arg2 = 1;
                    message.sendToTarget();
                }
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes(hexString));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyString(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(1, mUiccCarrierPrivilegeRules.getPackageNames().size());
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(0));
        Signature signature = new Signature("abcd92cbb156b280fa4e1429a6eceeb6e5c1bfe4");
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));
    }

    @Test
    @SmallTest
    public void testAID_BothARAMandARAD() {
        final String hexString =
                "FF4045E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4CA1D636F6D2E676F6F676"
                        + "C652E616E64726F69642E617070732E6D79617070E30ADB080000000000000001";
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                AsyncResult ar = new AsyncResult(null, new int[]{0}, null);
                message.obj = ar;
                if (aid.equals(ARAD)) {
                    message.arg2 = 0;
                } else {
                    message.arg2 = 1;
                }
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes(hexString));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyString(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        Signature signature = new Signature("abcd92cbb156b280fa4e1429a6eceeb6e5c1bfe4");
        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(2, mUiccCarrierPrivilegeRules.getPackageNames().size());
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(0));
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(1));
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature,
                mUiccCarrierPrivilegeRules.getPackageNames().get(1)));
    }

    @Test
    @SmallTest
    public void testAID_ARFFailed() {
        final String hexString =
                "FF4045E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4CA1D636F6D2E676F6F676"
                        + "C652E616E64726F69642E617070732E6D79617070E30ADB080000000000000001";
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                AsyncResult ar = new AsyncResult(null, null, null);
                if (aid.equals(ARAM)) {
                    message.arg2 = 1;
                } else if (aid.equals(ARAD)) {
                    message.arg2 = 0;
                } else {
                    // PKCS15
                    ar = new AsyncResult(null, null, new Throwable());
                }
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        assertTrue(!mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
    }

    @Test
    @SmallTest
    public void testAID_ARFSucceed() {
        /**
         * PKCS#15 application (AID: A0 00 00 00 63 50 4B 43 53 2D 31 35)
         *   -ODF (5031)
         *       A7 06 30 04 04 02 52 07
         *   -DODF (5207)
         *       A1 29 30 00 30 0F 0C 0D 47 50 20 53 45 20 41 63 63 20 43 74 6C A1 14 30 12
         *       06 0A 2A 86 48 86 FC 6B 81 48 01 01 30 04 04 02 42 00
         *   -EF ACMain (4200)
         *       30 10 04 08 01 02 03 04 05 06 07 08 30 04 04 02 43 00
         *   -EF ACRules (4300)
         *       30 10 A0 08 04 06 A0 00 00 01 51 01 30 04 04 02 43 10
         *   -EF ACConditions1 (4310)
         *       30 22
         *          04 20
         *             B9CFCE1C47A6AC713442718F15EF55B00B3A6D1A6D48CB46249FA8EB51465350
         *       30 22
         *          04 20
         *             4C36AF4A5BDAD97C1F3D8B283416D244496C2AC5EAFE8226079EF6F676FD1859
         */
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                AsyncResult ar = new AsyncResult(null, null, null);
                if (aid.equals(ARAM)) {
                    message.arg2 = 1;
                } else if (aid.equals(ARAD)) {
                    message.arg2 = 0;
                } else {
                    // PKCS15
                    ar = new AsyncResult(null, new int[]{2}, null);
                }
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        // Select files
        AtomicReference<String> currentFileId = new AtomicReference<>();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                currentFileId.set((String) invocation.getArguments()[6]);
                Message message = (Message) invocation.getArguments()[7];
                AsyncResult ar = new AsyncResult(null, new int[]{2}, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), eq(0x00), eq(0xA4), eq(0x00),
                eq(0x04), eq(0x02), anyString(), any(Message.class));

        // Read binary - since params are identical across files, we need to keep track of which
        // file was selected most recently and give back that content.
        Map<String, String> binaryContent =
                new HashMap<>() {
                    {
                        // ODF
                        put("5031", "A706300404025207");
                        // DODF
                        put(
                                "5207",
                                "A1293000300F0C0D4750205345204163632043746CA1143012060A2A864886FC6B"
                                        + "81480101300404024200");
                        // ACMF
                        put("4200", "301004080102030405060708300404024300");
                        // ACRF
                        put("4300", "3010A0080406FFFFFFFFFFFF300404024310");
                        // ACCF
                        put(
                                "4310",
                                "30220420B9CFCE1C47A6AC713442718F15EF55B00B3A6D1A6D48CB46249FA8EB51"
                                        + "465350302204204C36AF4A5BDAD97C1F3D8B283416D244496C2AC5EA"
                                        + "FE8226079EF6F676FD1859");
                    }
                };
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir =
                        new IccIoResult(0x90, 0x00,
                                IccUtils.hexStringToBytes(binaryContent.get(currentFileId.get())));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), eq(0x00), eq(0xB0), eq(0x00),
                eq(0x00), eq(0x00), eq(""), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(2, mUiccCarrierPrivilegeRules.getAccessRules().size());
        List<UiccAccessRule> accessRules = mUiccCarrierPrivilegeRules.getAccessRules();
        UiccAccessRule accessRule1 = new UiccAccessRule(
                IccUtils.hexStringToBytes(
                        "B9CFCE1C47A6AC713442718F15EF55B00B3A6D1A6D48CB46249FA8EB51465350"),
                "",
                0x00);
        assertTrue(accessRules.contains(accessRule1));
        UiccAccessRule accessRule2 = new UiccAccessRule(
                IccUtils.hexStringToBytes(
                        "4C36AF4A5BDAD97C1F3D8B283416D244496C2AC5EAFE8226079EF6F676FD1859"),
                "",
                0x00);
        assertTrue(accessRules.contains(accessRule2));
    }

    @Test
    @SmallTest
    public void testAID_ARFFallbackToACRF() {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                AsyncResult ar = new AsyncResult(null, null, null);
                if (aid.equals(ARAM)) {
                    message.arg2 = 1;
                } else if (aid.equals(ARAD)) {
                    message.arg2 = 0;
                } else {
                    // PKCS15
                    ar = new AsyncResult(null, new int[]{2}, null);
                }
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        // Select files
        AtomicReference<String> currentFileId = new AtomicReference<>();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                currentFileId.set((String) invocation.getArguments()[6]);
                Message message = (Message) invocation.getArguments()[7];
                AsyncResult ar = new AsyncResult(null, new int[]{2}, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), eq(0x00), eq(0xA4), eq(0x00),
                eq(0x04), eq(0x02), anyString(), any(Message.class));

        // Read binary - since params are identical across files, we need to keep track of which
        // file was selected most recently and give back that content.
        Map<String, String> binaryContent =
                new HashMap<>() {
                    {
                        // ODF fails
                        put("5031", "");
                        // ACRF
                        put("4300", "3010A0080406FFFFFFFFFFFF300404024310");
                        // ACCF
                        put(
                                "4310",
                                "30220420B9CFCE1C47A6AC713442718F15EF55B00B3A6D1A6D48CB46249FA8EB51"
                                        + "465350302204204C36AF4A5BDAD97C1F3D8B283416D244496C2AC5EA"
                                        + "FE8226079EF6F676FD1859");
                    }
                };
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir =
                        new IccIoResult(0x90, 0x00,
                                IccUtils.hexStringToBytes(binaryContent.get(currentFileId.get())));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), eq(0x00), eq(0xB0), eq(0x00),
                eq(0x00), eq(0x00), eq(""), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(2, mUiccCarrierPrivilegeRules.getAccessRules().size());
        List<UiccAccessRule> accessRules = mUiccCarrierPrivilegeRules.getAccessRules();
        UiccAccessRule accessRule1 = new UiccAccessRule(
                IccUtils.hexStringToBytes(
                        "B9CFCE1C47A6AC713442718F15EF55B00B3A6D1A6D48CB46249FA8EB51465350"),
                "",
                0x00);
        assertTrue(accessRules.contains(accessRule1));
        UiccAccessRule accessRule2 = new UiccAccessRule(
                IccUtils.hexStringToBytes(
                        "4C36AF4A5BDAD97C1F3D8B283416D244496C2AC5EAFE8226079EF6F676FD1859"),
                "",
                0x00);
        assertTrue(accessRules.contains(accessRule2));
    }

    private static final int P2 = 0x40;
    private static final int P2_EXTENDED_DATA = 0x60;

    @Test
    @SmallTest
    public void testAID_RetransmitLogicalChannel() {
        final String hexString1 =
                "FF4045E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4CA1D636F6D2E676F6F676"
                        + "C652E616E64726F69642E617070732E6D79617070E30A";

        final String hexString2 =
                "DB080000000000000001";
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String aid = (String) invocation.getArguments()[0];
                Message message = (Message) invocation.getArguments()[2];
                if (aid.equals(ARAD)) {
                    AsyncResult ar = new AsyncResult(null, new int[]{0}, null);
                    message.obj = ar;
                    message.arg2 = 0;
                    message.sendToTarget();
                } else {
                    AsyncResult ar = new AsyncResult(null, null, null);
                    message.obj = ar;
                    message.arg2 = 1;
                    message.sendToTarget();
                }
                return null;
            }
        }).when(mUiccProfile).iccOpenLogicalChannel(anyString(), anyInt(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir = new IccIoResult(0x90, 0x00,
                        IccUtils.hexStringToBytes(hexString1));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), anyInt(), anyInt(), anyInt(),
                eq(P2), anyInt(), anyString(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[7];
                IccIoResult iir = new IccIoResult(0x90, 0x00,
                        IccUtils.hexStringToBytes(hexString2));
                AsyncResult ar = new AsyncResult(null, iir, null);
                message.obj = ar;
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccTransmitApduLogicalChannel(anyInt(), anyInt(), anyInt(), anyInt(),
                eq(P2_EXTENDED_DATA), anyInt(), anyString(), any(Message.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[1];
                message.sendToTarget();
                return null;
            }
        }).when(mUiccProfile).iccCloseLogicalChannel(anyInt(), any(Message.class));

        mUiccCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccProfile, null);
        processAllMessages();

        assertTrue(mUiccCarrierPrivilegeRules.hasCarrierPrivilegeRules());
        assertEquals(1, mUiccCarrierPrivilegeRules.getPackageNames().size());
        assertEquals("com.google.android.apps.myapp",
                mUiccCarrierPrivilegeRules.getPackageNames().get(0));
        Signature signature = new Signature("abcd92cbb156b280fa4e1429a6eceeb6e5c1bfe4");
        assertEquals(0, mUiccCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature,
                mUiccCarrierPrivilegeRules.getPackageNames().get(0)));
    }
}
