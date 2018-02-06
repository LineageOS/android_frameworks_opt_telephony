/*
 * Copyright 2018 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;


public class AnswerToResetTest {

    @Test
    @SmallTest
    public void tesAnswerToRestNullString() {
        AnswerToReset atr = AnswerToReset.parseAtr(null);
        assertNull(atr);
    }

    @Test
    @SmallTest
    public void tesAnswerToRestOddLength() {
        String str = "3B02145";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNull(atr);
    }

    @Test
    @SmallTest
    public void tesAnswerToRestTooShortLength() {
        String str = "3B";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNull(atr);
    }

    @Test
    @SmallTest
    public void tesAnswerToRestNoInterfaceByteNoHistoricalByte() {
        String str = "3B00";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3B);
        assertEquals(atr.getFormatByte(), (byte) 0x00);
        assertTrue(atr.getInterfaceBytes().isEmpty());
        assertEquals(atr.getHistoricalBytes().length, 0);
        assertNull(atr.getCheckByte());
    }

    @Test
    @SmallTest
    public void tesAnswerToRestNoHistoricalByte() {
        String str = "3F909580B1FE001F4297";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x90);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xB1);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x00, null, (byte) 0x1F);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect, atr.getInterfaceBytes());

        assertEquals(atr.getHistoricalBytes().length, 0);
        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));
    }

    @Test
    @SmallTest
    public void tesAnswerToRestNoInterfaceByte() {
        String str = "3F078031A073BE211797";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x07);
        assertTrue(atr.getInterfaceBytes().isEmpty());
        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect = new byte[]{
                (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
                (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect));
        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));
    }

    @Test
    @SmallTest
    public void tesAnswerToRestSuccess() {
        String str = "3F979580B1FE001F428031A073BE211797";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x97);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xB1);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x00, null, (byte) 0x1F);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect_ib = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect_ib, atr.getInterfaceBytes());
        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect_hb = new byte[]{
                (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
                (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect_hb));
        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));
    }

    @Test
    @SmallTest
    public void tesAnswerToRestSuccessWithoutCheckByte() {
        String str = "3F979580B0FE0010428031A073BE2117";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x97);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xB0);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x00, null, (byte) 0x10);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect_ib = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect_ib, atr.getInterfaceBytes());

        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect_hb = new byte[]{
                (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
                (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect_hb));

        assertEquals(atr.getCheckByte(), null);
        assertFalse(atr.isEuiccSupported());
    }

    @Test
    @SmallTest
    public void tesAnswerToRestFailWithoutCheckByte() {
        String str = "3F979581B0FE0010428031A073BE2117";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNull(atr);
    }

    @Test
    @SmallTest
    public void tesAnswerToRestFailWithExtraByte() {
        String str = "3F979580B1FE001F428031A073BE21179718";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNull(atr);
    }

    @Test
    @SmallTest
    public void tesAnswerToRestEuiccSupported() {
        String str = "3F979580BFFE8210428031A073BE211797";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x97);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xBF);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x82, null, (byte) 0x10);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect_ib = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect_ib, atr.getInterfaceBytes());

        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect_hb = new byte[]{
                (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
                (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect_hb));

        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));

        assertTrue(atr.isEuiccSupported());
    }

    @Test
    @SmallTest
    public void tesAnswerToRestEuiccSupportedWithLowerCaseString() {
        String str = "3f979580bffe8210428031a073be211797";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x97);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xBF);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x82, null, (byte) 0x10);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect_ib = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect_ib, atr.getInterfaceBytes());

        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect_hb = new byte[]{
            (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
            (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect_hb));

        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));

        assertTrue(atr.isEuiccSupported());
    }

    @Test
    @SmallTest
    public void tesAnswerToRestEuiccNotSupportedDueToIncorrectT() {
        String str = "3F979580BEFE8210428031A073BE211797";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x97);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xBE);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x82, null, (byte) 0x10);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect_ib = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect_ib, atr.getInterfaceBytes());

        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect_hb = new byte[]{
                (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
                (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect_hb));

        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));

        assertFalse(atr.isEuiccSupported());
    }

    @Test
    @SmallTest
    public void tesAnswerToRestEuiccNotSupportedDueToIncorrectTB() {
        String str = "3F979580BFFE8110428031A073BE211797";
        AnswerToReset atr = AnswerToReset.parseAtr(str);
        assertNotNull(atr);
        assertEquals(atr.getConventionByte(), (byte) 0x3F);
        assertEquals(atr.getFormatByte(), (byte) 0x97);

        assertEquals(atr.getInterfaceBytes().size(), 4);
        AnswerToReset.InterfaceByte expect_t1 =
                new AnswerToReset.InterfaceByte((byte) 0x95, null, null, (byte) 0x80);
        AnswerToReset.InterfaceByte expect_t2 =
                new AnswerToReset.InterfaceByte(null, null, null, (byte) 0xBF);
        AnswerToReset.InterfaceByte expect_t3 =
                new AnswerToReset.InterfaceByte((byte) 0xFE, (byte) 0x81, null, (byte) 0x10);
        AnswerToReset.InterfaceByte expect_t4 =
                new AnswerToReset.InterfaceByte((byte) 0x42, null, null, null);
        ArrayList<AnswerToReset.InterfaceByte> expect_ib = new ArrayList<>(
                Arrays.asList(expect_t1, expect_t2, expect_t3, expect_t4)
        );
        assertEquals(expect_ib, atr.getInterfaceBytes());

        assertEquals(atr.getHistoricalBytes().length, 7);
        byte[] expect_hb = new byte[]{
                (byte) 0x80, (byte) 0x31, (byte) 0xA0, (byte) 0x73,
                (byte) 0xBE, (byte) 0x21, (byte) 0x17};
        assertTrue(Arrays.equals(atr.getHistoricalBytes(), expect_hb));

        assertEquals(atr.getCheckByte(), Byte.valueOf((byte) 0x97));

        assertFalse(atr.isEuiccSupported());
    }
}
