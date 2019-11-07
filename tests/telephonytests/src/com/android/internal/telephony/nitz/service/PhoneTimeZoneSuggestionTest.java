/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.internal.telephony.nitz.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.os.Parcelable;

import org.junit.Test;

public class PhoneTimeZoneSuggestionTest {
    private static final int PHONE_ID = 99999;

    @Test
    public void testEquals() {
        PhoneTimeZoneSuggestion one = new PhoneTimeZoneSuggestion(PHONE_ID);
        assertEquals(one, one);

        PhoneTimeZoneSuggestion two = new PhoneTimeZoneSuggestion(PHONE_ID);
        assertEquals(one, two);
        assertEquals(two, one);

        PhoneTimeZoneSuggestion three = new PhoneTimeZoneSuggestion(PHONE_ID + 1);
        assertNotEquals(one, three);
        assertNotEquals(three, one);

        one.setZoneId("Europe/London");
        assertNotEquals(one, two);
        two.setZoneId("Europe/Paris");
        assertNotEquals(one, two);
        one.setZoneId(two.getZoneId());
        assertEquals(one, two);

        one.setMatchType(PhoneTimeZoneSuggestion.EMULATOR_ZONE_ID);
        two.setMatchType(PhoneTimeZoneSuggestion.NETWORK_COUNTRY_ONLY);
        assertNotEquals(one, two);
        one.setMatchType(PhoneTimeZoneSuggestion.NETWORK_COUNTRY_ONLY);
        assertEquals(one, two);

        one.setQuality(PhoneTimeZoneSuggestion.SINGLE_ZONE);
        two.setQuality(PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS);
        assertNotEquals(one, two);
        one.setQuality(PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS);
        assertEquals(one, two);

        // DebugInfo must not be considered in equals().
        one.addDebugInfo("Debug info 1");
        two.addDebugInfo("Debug info 2");
        assertEquals(one, two);
    }

    @Test
    public void testParcelable() {
        PhoneTimeZoneSuggestion one = new PhoneTimeZoneSuggestion(PHONE_ID);
        assertEquals(one, roundTripParcelable(one));

        one.setZoneId("Europe/London");
        one.setMatchType(PhoneTimeZoneSuggestion.EMULATOR_ZONE_ID);
        one.setQuality(PhoneTimeZoneSuggestion.SINGLE_ZONE);
        assertEquals(one, roundTripParcelable(one));

        // DebugInfo should also be stored (but is not checked by equals()
        one.addDebugInfo("This is debug info");
        PhoneTimeZoneSuggestion two = roundTripParcelable(one);
        assertEquals(one.getDebugInfo(), two.getDebugInfo());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Parcelable> T roundTripParcelable(T one) {
        Parcel parcel = Parcel.obtain();
        parcel.writeTypedObject(one, 0);
        parcel.setDataPosition(0);

        T toReturn = (T) parcel.readTypedObject(PhoneTimeZoneSuggestion.CREATOR);
        parcel.recycle();
        return toReturn;
    }
}
