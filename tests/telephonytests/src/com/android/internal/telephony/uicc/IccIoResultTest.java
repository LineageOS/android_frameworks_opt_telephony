/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.junit.Assert;
import org.junit.Test;

public class IccIoResultTest {

    @Test
    public void check0x90_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x90, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();

        Assert.assertTrue(resultStr != null && (!resultStr.contains("Error")));
    }

    @Test
    public void check0x91_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x91, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();

        Assert.assertTrue(resultStr != null && (!resultStr.contains("Error")));
    }

    @Test
    public void check0x9E_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x9E, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();

        Assert.assertTrue(resultStr != null && (!resultStr.contains("Error")));
    }

    @Test
    public void check0x9F_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x9F, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();

        Assert.assertTrue(resultStr != null && (!resultStr.contains("Error")));
    }

    @Test
    public void check0x94_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x94, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();

        Assert.assertTrue(resultStr != null && (resultStr.contains("no EF selected")));

        iccIoResult = new IccIoResult(0x94, 0x02, new byte[10]);
        resultStr = iccIoResult.toString();

        Assert.assertTrue(
                resultStr != null && (resultStr.contains("out f range (invalid address)")));

        iccIoResult = new IccIoResult(0x94, 0x04, new byte[10]);
        resultStr = iccIoResult.toString();

        Assert.assertTrue(
                resultStr != null && (resultStr.contains("file ID not found/pattern not found")));

        iccIoResult = new IccIoResult(0x94, 0x08, new byte[10]);
        resultStr = iccIoResult.toString();

        Assert.assertTrue(
                resultStr != null && (resultStr.contains("file is inconsistent with the command")));
    }

    @Test
    public void check0x98_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x98, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("unknown")));

        iccIoResult = new IccIoResult(0x98, 0x02, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("no CHV initialized")));

        iccIoResult = new IccIoResult(0x98, 0x04, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("access condition not fulfilled")));

        iccIoResult = new IccIoResult(0x98, 0x08, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("in contradiction with CHV status")));

        iccIoResult = new IccIoResult(0x98, 0x10, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "in contradiction with invalidation status")));

        iccIoResult = new IccIoResult(0x98, 0x40, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "unsuccessful CHV verification, no attempt left")));

        iccIoResult = new IccIoResult(0x98, 0x50, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "increase cannot be performed, Max value reached")));

        iccIoResult = new IccIoResult(0x98, 0x62, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "authentication error, application specific")));

        iccIoResult = new IccIoResult(0x98, 0x64, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "authentication error, security context not supported")));

        iccIoResult = new IccIoResult(0x98, 0x65, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("key freshness failure")));

        iccIoResult = new IccIoResult(0x98, 0x66, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "authentication error, no memory space available")));

        iccIoResult = new IccIoResult(0x98, 0x67, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "authentication error, no memory space available in EF_MUK")));
    }

    @Test
    public void check0x61_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x61, 0x20, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("more response bytes available")));
    }

    @Test
    public void check0x62_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x62, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("no information given")));

        iccIoResult = new IccIoResult(0x62, 0x81, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains(
                        "part of returned data may be corrupted")));

        iccIoResult = new IccIoResult(0x62, 0x82, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "end of file/record reached before reading Le bytes")));

        iccIoResult = new IccIoResult(0x62, 0x83, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("selected file invalidated")));

        iccIoResult = new IccIoResult(0x62, 0x84, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("selected file in termination state")));

        iccIoResult = new IccIoResult(0x62, 0xF1, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("more data available")));

        iccIoResult = new IccIoResult(0x62, 0xF2, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "more data available and proactive command pending")));

        iccIoResult = new IccIoResult(0x62, 0xF3, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("response data available")));

        iccIoResult = new IccIoResult(0x62, 0xF4, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("unknown")));
    }

    @Test
    public void check0x63_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x63, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains(
                        "command successful but after using an internal update retry routine 0 "
                                + "times")));

        iccIoResult = new IccIoResult(0x63, 0xF1, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("more data expected")));

        iccIoResult = new IccIoResult(0x63, 0xF2, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains(
                        "more data expected and proactive command pending")));

        iccIoResult = new IccIoResult(0x63, 0xF3, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("unknown")));
    }

    @Test
    public void check0x64_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x64, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("unknown")));

        iccIoResult = new IccIoResult(0x64, 0x00, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("no information given")));
    }

    @Test
    public void check0x65_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x65, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("unknown")));

        iccIoResult = new IccIoResult(0x65, 0x00, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue((resultStr != null) && (resultStr.contains(
                "no information given, state of non-volatile memory changed")));

        iccIoResult = new IccIoResult(0x65, 0x81, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("memory problem")));
    }

    @Test
    public void check0x67_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x67, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "the interpretation of this status word is command dependent")));

        iccIoResult = new IccIoResult(0x67, 0x00, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "incorrect parameter P3")));
    }

    @Test
    public void check0x68_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x68, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("unknown")));

        iccIoResult = new IccIoResult(0x68, 0x00, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("no information given")));

        iccIoResult = new IccIoResult(0x68, 0x81, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("logical channel not supported")));

        iccIoResult = new IccIoResult(0x68, 0x82, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("secure messaging not supported")));
    }

    @Test
    public void check0x69_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x69, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("unknown")));

        iccIoResult = new IccIoResult(0x69, 0x00, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("no information given")));

        iccIoResult = new IccIoResult(0x69, 0x81, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains(
                        "command incompatible with file structure")));

        iccIoResult = new IccIoResult(0x69, 0x82, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("security status not satisfied")));

        iccIoResult = new IccIoResult(0x69, 0x83, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("authentication/PIN method blocked")));

        iccIoResult = new IccIoResult(0x69, 0x84, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("referenced data invalidated")));

        iccIoResult = new IccIoResult(0x69, 0x85, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("conditions of use not satisfied")));

        iccIoResult = new IccIoResult(0x69, 0x86, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue((resultStr != null) && (resultStr.contains(
                "command not allowed (no EF selected)")));

        iccIoResult = new IccIoResult(0x69, 0x89, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue((resultStr != null) && (resultStr.contains(
                "command not allowed - secure channel - security not satisfied")));
    }

    @Test
    public void check0x6A_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x6A, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("unknown")));

        iccIoResult = new IccIoResult(0x6A, 0x80, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "incorrect parameters in the data field")));

        iccIoResult = new IccIoResult(0x6A, 0x81, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("function not supported")));

        iccIoResult = new IccIoResult(0x6A, 0x82, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("file not found")));

        iccIoResult = new IccIoResult(0x6A, 0x83, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("record not found")));

        iccIoResult = new IccIoResult(0x6A, 0x84, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("not enough memory space")));

        iccIoResult = new IccIoResult(0x6A, 0x86, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(
                (resultStr != null) && (resultStr.contains("incorrect parameters P1 to P2")));

        iccIoResult = new IccIoResult(0x6A, 0x87, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue((resultStr != null) && (resultStr.contains(
                "lc inconsistent with P1 to P2")));

        iccIoResult = new IccIoResult(0x6A, 0x88, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue((resultStr != null) && (resultStr.contains(
                "referenced data not found")));
    }

    @Test
    public void check0x6B_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x6B, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(
                resultStr != null && (resultStr.contains("incorrect parameter P1 or P2")));
    }

    @Test
    public void check0x6C_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x6C, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains("wrong length, retry with ")));
    }

    @Test
    public void check0x6D_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x6D, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "unknown instruction code given in the command")));
    }

    @Test
    public void check0x6E_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x6E, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "wrong instruction class given in the command")));
    }

    @Test
    public void check0x6F_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x6F, 0xC0, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "the interpretation of this status word is command dependent")));

        iccIoResult = new IccIoResult(0x6F, 0x00, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "technical problem with no diagnostic given")));
    }

    @Test
    public void check0x92_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x92, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "command successful but after using an internal update retry routine")));

        iccIoResult = new IccIoResult(0x92, 0x40, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "memory problem")));

        iccIoResult = new IccIoResult(0x92, 0x41, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "unknown")));
    }

    @Test
    public void check0x93_ErrorCodeParsing() {
        IccIoResult iccIoResult = new IccIoResult(0x93, 0x00, new byte[10]);
        String resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "SIM Application Toolkit is busy. Command cannot be executed"
                        + " at present, further normal commands are allowed")));

        iccIoResult = new IccIoResult(0x93, 0x41, new byte[10]);
        resultStr = iccIoResult.toString();
        Assert.assertTrue(resultStr != null && (resultStr.contains(
                "unknown")));
    }
}
