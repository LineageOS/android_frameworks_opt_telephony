/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Parcel;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierRestrictionRules;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;

/** Unit tests for {@link CarrierRestrictionRules}. */

public class CarrierRestrictionRulesTest extends AndroidTestCase {

    private static final String MCC1 = "110";
    private static final String MNC1 = "210";
    private static final String MCC2 = "120";
    private static final String MNC2 = "220";
    private static final String MCC1_WILDCHAR = "3??";
    private static final String MNC1_WILDCHAR = "???";
    private static final String MCC2_WILDCHAR = "31?";
    private static final String MNC2_WILDCHAR = "?0";
    private static final String GID1 = "80";

    @SmallTest
    public void testBuilderAllowedAndExcludedCarriers() {
        ArrayList<CarrierIdentifier> allowedCarriers = new ArrayList<>();
        allowedCarriers.add(new CarrierIdentifier(MCC1, MNC1, null, null, null, null));
        allowedCarriers.add(new CarrierIdentifier(MCC2, MNC2, null, null, null, null));

        ArrayList<CarrierIdentifier> excludedCarriers = new ArrayList<>();
        excludedCarriers.add(new CarrierIdentifier(MCC2, MNC2, null, null, GID1, null));

        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder()
                .setAllowedCarriers(allowedCarriers)
                .setExcludedCarriers(excludedCarriers)
                .build();

        assertEquals(false, rules.isAllCarriersAllowed());
        assertTrue(rules.getAllowedCarriers().equals(allowedCarriers));
        assertTrue(rules.getExcludedCarriers().equals(excludedCarriers));
        assertEquals(CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_NOT_ALLOWED,
                rules.getDefaultCarrierRestriction());
    }

    @SmallTest
    public void testBuilderEmptyLists() {
        ArrayList<CarrierIdentifier> emptyCarriers = new ArrayList<>();

        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder().build();

        assertEquals(false, rules.isAllCarriersAllowed());
        assertTrue(rules.getAllowedCarriers().equals(emptyCarriers));
        assertTrue(rules.getExcludedCarriers().equals(emptyCarriers));
        assertEquals(CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_NOT_ALLOWED,
                rules.getDefaultCarrierRestriction());
    }

    @SmallTest
    public void testBuilderWildCharacter() {
        ArrayList<CarrierIdentifier> allowedCarriers = new ArrayList<>();
        allowedCarriers.add(new CarrierIdentifier(MCC1_WILDCHAR, MNC1_WILDCHAR, null, null,
                null, null));

        ArrayList<CarrierIdentifier> excludedCarriers = new ArrayList<>();
        excludedCarriers.add(new CarrierIdentifier(MCC2_WILDCHAR, MNC2_WILDCHAR, null, null,
                GID1, null));

        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder()
                .setAllowedCarriers(allowedCarriers)
                .setExcludedCarriers(excludedCarriers)
                .build();

        assertEquals(false, rules.isAllCarriersAllowed());
        assertTrue(rules.getAllowedCarriers().equals(allowedCarriers));
        assertTrue(rules.getExcludedCarriers().equals(excludedCarriers));
        assertEquals(CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_NOT_ALLOWED,
                rules.getDefaultCarrierRestriction());
    }

    @SmallTest
    public void testBuilderDefaultAllowed() {
        ArrayList<CarrierIdentifier> allowedCarriers = new ArrayList<>();
        allowedCarriers.add(new CarrierIdentifier(MCC1, MNC1, null, null, null, null));
        allowedCarriers.add(new CarrierIdentifier(MCC2, MNC2, null, null, null, null));

        ArrayList<CarrierIdentifier> excludedCarriers = new ArrayList<>();
        allowedCarriers.add(new CarrierIdentifier(MCC2, MNC2, null, null, GID1, null));

        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder()
                .setAllowedCarriers(allowedCarriers)
                .setExcludedCarriers(excludedCarriers)
                .setDefaultCarrierRestriction(
                    CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_ALLOWED)
                .build();

        assertEquals(false, rules.isAllCarriersAllowed());
        assertTrue(rules.getAllowedCarriers().equals(allowedCarriers));
        assertTrue(rules.getExcludedCarriers().equals(excludedCarriers));
        assertEquals(CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_ALLOWED,
                rules.getDefaultCarrierRestriction());
    }

    @SmallTest
    public void testBuilderAllCarriersAllowed() {
        ArrayList<CarrierIdentifier> allowedCarriers = new ArrayList<>();
        ArrayList<CarrierIdentifier> excludedCarriers = new ArrayList<>();

        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder()
                .setAllCarriersAllowed()
                .build();

        assertEquals(true, rules.isAllCarriersAllowed());
        assertTrue(rules.getAllowedCarriers().equals(allowedCarriers));
        assertTrue(rules.getExcludedCarriers().equals(excludedCarriers));
        assertEquals(CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_ALLOWED,
                rules.getDefaultCarrierRestriction());
    }

    @SmallTest
    public void testParcel() {
        ArrayList<CarrierIdentifier> allowedCarriers = new ArrayList<>();
        allowedCarriers.add(new CarrierIdentifier(MCC1, MNC1, null, null, null, null));
        allowedCarriers.add(new CarrierIdentifier(MCC2, MNC2, null, null, null, null));

        ArrayList<CarrierIdentifier> excludedCarriers = new ArrayList<>();
        excludedCarriers.add(new CarrierIdentifier(MCC2, MNC2, null, null, GID1, null));

        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder()
                .setAllowedCarriers(allowedCarriers)
                .setExcludedCarriers(excludedCarriers)
                .setDefaultCarrierRestriction(
                    CarrierRestrictionRules.CARRIER_RESTRICTION_DEFAULT_NOT_ALLOWED)
                .setMultiSimPolicy(
                    CarrierRestrictionRules.MULTISIM_POLICY_ONE_VALID_SIM_MUST_BE_PRESENT)
                .build();

        Parcel p = Parcel.obtain();
        rules.writeToParcel(p, 0);
        p.setDataPosition(0);

        CarrierRestrictionRules newRules = CarrierRestrictionRules.CREATOR.createFromParcel(p);

        assertEquals(false, rules.isAllCarriersAllowed());
        assertTrue(allowedCarriers.equals(newRules.getAllowedCarriers()));
        assertTrue(excludedCarriers.equals(newRules.getExcludedCarriers()));
        assertEquals(rules.getDefaultCarrierRestriction(),
                newRules.getDefaultCarrierRestriction());
        assertEquals(rules.getMultiSimPolicy(),
                CarrierRestrictionRules.MULTISIM_POLICY_ONE_VALID_SIM_MUST_BE_PRESENT);
    }

    @SmallTest
    public void testDefaultMultiSimPolicy() {
        CarrierRestrictionRules rules = CarrierRestrictionRules.newBuilder().build();

        assertEquals(CarrierRestrictionRules.MULTISIM_POLICY_NONE, rules.getMultiSimPolicy());
    }
}
