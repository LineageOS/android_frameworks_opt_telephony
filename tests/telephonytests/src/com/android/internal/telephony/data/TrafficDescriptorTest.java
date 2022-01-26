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

import android.telephony.Rlog;
import android.telephony.data.TrafficDescriptor.OsAppId;

import org.junit.Test;

import java.math.BigInteger;

public class TrafficDescriptorTest {

    @Test
    public void testOsAppId() {
        for (int i = 1; i <= 5; i++) {
            OsAppId osAppId = new OsAppId(OsAppId.ANDROID_OS_ID, "ENTERPRISE", i);
            byte[] rawBytes = osAppId.getBytes();
            Rlog.d("TrafficDescriptorTest", "rawBytes=" + new BigInteger(1, rawBytes).toString(16)
                    + ", osAppId=" + osAppId);
            assertThat(new OsAppId(rawBytes)).isEqualTo(osAppId);
        }
    }
}
