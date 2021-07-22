/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.ModemInfo;
import android.telephony.PhoneCapability;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PhoneCapabilityTest {
    @Test
    @SmallTest
    public void basicTests() throws Exception {
        int maxActiveVoiceCalls = 1;
        int maxActiveData = 2;
        ModemInfo modemInfo = new ModemInfo(1, 2, true, false);
        List<ModemInfo> logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo);
        int[] deviceNrCapabilities = new int[]{PhoneCapability.DEVICE_NR_CAPABILITY_SA};

        PhoneCapability capability = new PhoneCapability(maxActiveVoiceCalls, maxActiveData,
                logicalModemList, false, deviceNrCapabilities);

        assertEquals(maxActiveVoiceCalls, capability.getMaxActiveVoiceSubscriptions());
        assertEquals(maxActiveData, capability.getMaxActiveDataSubscriptions());
        assertEquals(1, capability.getLogicalModemList().size());
        assertEquals(modemInfo, capability.getLogicalModemList().get(0));
        assertArrayEquals(deviceNrCapabilities, capability.getDeviceNrCapabilities());

        PhoneCapability toCompare = new PhoneCapability(maxActiveVoiceCalls + 1, maxActiveData - 1,
                logicalModemList, false, deviceNrCapabilities);
        assertEquals(capability,
                new PhoneCapability(maxActiveVoiceCalls, maxActiveData, logicalModemList,
                        false, deviceNrCapabilities));
        assertNotEquals(capability, toCompare);
    }

    @Test
    @SmallTest
    public void parcelReadWrite() throws Exception {
        int maxActiveVoiceCalls = 1;
        int maxActiveData = 2;
        ModemInfo modemInfo = new ModemInfo(1, 2, true, false);
        List<ModemInfo> logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo);
        int[] deviceNrCapabilities = new int[]{};

        PhoneCapability capability = new PhoneCapability(maxActiveVoiceCalls, maxActiveData,
                logicalModemList, false, deviceNrCapabilities);

        Parcel parcel = Parcel.obtain();
        capability.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PhoneCapability toCompare = PhoneCapability.CREATOR.createFromParcel(parcel);

        assertEquals(maxActiveVoiceCalls, toCompare.getMaxActiveVoiceSubscriptions());
        assertEquals(maxActiveData, toCompare.getMaxActiveDataSubscriptions());
        assertEquals(1, toCompare.getLogicalModemList().size());
        assertEquals(modemInfo, toCompare.getLogicalModemList().get(0));
        assertArrayEquals(deviceNrCapabilities, toCompare.getDeviceNrCapabilities());
        assertEquals(capability, toCompare);
    }
}
