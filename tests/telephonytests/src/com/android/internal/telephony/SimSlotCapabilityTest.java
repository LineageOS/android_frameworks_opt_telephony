/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.SimSlotCapability;

import org.junit.Test;

public class SimSlotCapabilityTest {
    @Test
    public void basicTests() {
        int physicalSlotId = 0;
        int slotType = SimSlotCapability.SLOT_TYPE_UICC;

        SimSlotCapability capability = new SimSlotCapability(physicalSlotId, slotType);
        assertEquals(physicalSlotId, capability.getPhysicalSlotIndex());
        assertEquals(slotType, capability.getSlotType());

        SimSlotCapability toCompare = new SimSlotCapability(physicalSlotId + 1,
                SimSlotCapability.SLOT_TYPE_IUICC);
        assertEquals(capability, new SimSlotCapability(physicalSlotId, slotType));
        assertNotEquals(capability, toCompare);
    }

    @Test
    public void parcelReadWrite() {
        int physicalSlotId = 0;
        int slotType = SimSlotCapability.SLOT_TYPE_EUICC;

        SimSlotCapability capability = new SimSlotCapability(physicalSlotId, slotType);

        Parcel parcel = Parcel.obtain();
        capability.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SimSlotCapability toCompare = SimSlotCapability.CREATOR.createFromParcel(parcel);

        assertEquals(physicalSlotId, capability.getPhysicalSlotIndex());
        assertEquals(slotType, capability.getSlotType());
        assertEquals(capability, toCompare);
    }
}
