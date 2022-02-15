/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.telephony.Rlog;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.TrafficDescriptor.OsAppId;

import org.junit.Test;

import java.math.BigInteger;
import java.util.UUID;

public class TrafficDescriptorTest {

    @Test
    public void testEnterpriseOsAppId() {
        for (int i = 1; i <= 5; i++) {
            OsAppId osAppId = new OsAppId(OsAppId.ANDROID_OS_ID, "ENTERPRISE", i);
            byte[] rawBytes = osAppId.getBytes();
            Rlog.d("TrafficDescriptorTest", "rawBytes=" + new BigInteger(1, rawBytes).toString(16)
                    + ", osAppId=" + osAppId);
            assertThat(new OsAppId(rawBytes)).isEqualTo(osAppId);
            assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
            assertThat(osAppId.getAppId()).isEqualTo("ENTERPRISE");
            assertThat(osAppId.getDifferentiator()).isEqualTo(i);
        }
    }

    @Test
    public void testUrllcOsAppId() {
        OsAppId osAppId = new OsAppId(OsAppId.ANDROID_OS_ID, "PRIORITIZE_LATENCY", 1);
        byte[] rawBytes = osAppId.getBytes();
        Rlog.d("TrafficDescriptorTest", "rawBytes=" + new BigInteger(1, rawBytes).toString(16)
                + ", osAppId=" + osAppId);
        assertThat(new OsAppId(rawBytes)).isEqualTo(osAppId);
        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("PRIORITIZE_LATENCY");
        assertThat(osAppId.getDifferentiator()).isEqualTo(1);
    }

    @Test
    public void testEmbbOsAppId() {
        OsAppId osAppId = new OsAppId(OsAppId.ANDROID_OS_ID, "PRIORITIZE_BANDWIDTH", 1);
        byte[] rawBytes = osAppId.getBytes();
        Rlog.d("TrafficDescriptorTest", "rawBytes=" + new BigInteger(1, rawBytes).toString(16)
                + ", osAppId=" + osAppId);
        assertThat(new OsAppId(rawBytes)).isEqualTo(osAppId);
        assertThat(osAppId.getOsId()).isEqualTo(OsAppId.ANDROID_OS_ID);
        assertThat(osAppId.getAppId()).isEqualTo("PRIORITIZE_BANDWIDTH");
        assertThat(osAppId.getDifferentiator()).isEqualTo(1);
    }

    @Test
    public void testInvalidOsId() {
        OsAppId osAppId = new OsAppId(UUID.fromString("91b7f6fb-5069-4e29-af83-50e942e9b1c3"),
                "ENTERPRISE", 1);
        // IllegalArgumentException is expected when OS id is not Android
        assertThrows(IllegalArgumentException.class,
                () -> new TrafficDescriptor.Builder()
                        .setDataNetworkName("DNN")
                        .setOsAppId(osAppId.getBytes())
                        .build());
    }

    @Test
    public void testInvalidAppId() {
        OsAppId osAppId = new OsAppId(OsAppId.ANDROID_OS_ID, "FOO", 1);
        // IllegalArgumentException is expected when App id is not in the allowed this.
        assertThrows(IllegalArgumentException.class,
                () -> new TrafficDescriptor.Builder()
                        .setDataNetworkName("DNN")
                        .setOsAppId(osAppId.getBytes())
                        .build());
    }

    @Test
    public void testInvalidDifferentiator() {
        // IllegalArgumentException is expected when App id is not in the allowed this.
        assertThrows(IllegalArgumentException.class,
                () -> new OsAppId(OsAppId.ANDROID_OS_ID, "ENTERPRISE", 0));
    }
}
