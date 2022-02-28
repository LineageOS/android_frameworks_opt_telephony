/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccPort;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimSlotStateTest extends TelephonyTest {
    // Mocked classes
    private UiccSlot mInactiveSlot;
    private UiccSlot mEmptySlot;
    private UiccSlot mPhysicalSlot;
    private UiccSlot mEsimSlot;
    private UiccCard mInactiveCard;
    private UiccCard mActiveCard;
    private UiccPort mInactivePort;
    private UiccPort mActivePort;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mInactiveSlot = mock(UiccSlot.class);
        mEmptySlot = mock(UiccSlot.class);
        mPhysicalSlot = mock(UiccSlot.class);
        mEsimSlot = mock(UiccSlot.class);
        mInactiveCard = mock(UiccCard.class);
        mActiveCard = mock(UiccCard.class);
        mInactivePort = mock(UiccPort.class);
        mActivePort = mock(UiccPort.class);

        doReturn(false).when(mInactiveSlot).isActive();

        doReturn(true).when(mEmptySlot).isActive();
        doReturn(CardState.CARDSTATE_ABSENT).when(mEmptySlot).getCardState();

        doReturn(true).when(mPhysicalSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot).getCardState();
        doReturn(false).when(mPhysicalSlot).isEuicc();

        doReturn(true).when(mEsimSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mEsimSlot).getCardState();
        doReturn(true).when(mEsimSlot).isEuicc();

        doReturn(0).when(mInactivePort).getNumApplications();
        doReturn(4).when(mActivePort).getNumApplications();

        doReturn(new UiccPort[]{mInactivePort}).when(mInactiveCard).getUiccPortList();
        doReturn(new UiccPort[]{mActivePort}).when(mActiveCard).getUiccPortList();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testEmptySlots() {
        doReturn(new UiccSlot[] {}).when(mUiccController).getUiccSlots();

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(0, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_nullSlot() {
        setupSingleSim(null);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(0, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_inactiveSlot() {
        setupSingleSim(mInactiveSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(0, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_noSimCard() {
        setupSingleSim(mEmptySlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_physicalSimCard() {
        // NOTE: card in slot can be null in this case
        setupSingleSim(mPhysicalSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_physicalSimCardInErrorState() {
        doReturn(CardState.CARDSTATE_ERROR).when(mPhysicalSlot).getCardState();
        setupSingleSim(mPhysicalSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_physicalSimCardInRestrictedState() {
        doReturn(CardState.CARDSTATE_RESTRICTED).when(mPhysicalSlot).getCardState();
        setupSingleSim(mPhysicalSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        // the metrics should not count restricted cards since they cannot be used
        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_esimCardWithNullCard() {
        doReturn(null).when(mEsimSlot).getUiccCard();
        setupSingleSim(mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_esimCardWithoutProfile() {
        doReturn(mInactiveCard).when(mEsimSlot).getUiccCard();
        setupSingleSim(mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testSingleSim_esimCardWithProfile() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        setupSingleSim(mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(1, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsdsSingleSimMode_noSimCard() {
        setupDualSim(mEmptySlot, mInactiveSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsdsSingleSimMode_physicalSimCard() {
        setupDualSim(mPhysicalSlot, mInactiveSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsdsSingleSimMode_esimCardWithoutProfile() {
        doReturn(mInactiveCard).when(mEsimSlot).getUiccCard();
        setupDualSim(mInactiveSlot, mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsdsSingleSimMode_esimCardWithProfile() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        setupDualSim(mInactiveSlot, mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(1, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(1, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsds_noActiveSimProfile() {
        doReturn(mInactiveCard).when(mEsimSlot).getUiccCard();
        setupDualSim(mEmptySlot, mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(2, state.numActiveSlots);
        assertEquals(0, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsds_physicalSimCard() {
        doReturn(mInactiveCard).when(mEsimSlot).getUiccCard();
        setupDualSim(mPhysicalSlot, mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(2, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsds_esimCardWithProfile() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        setupDualSim(mEmptySlot, mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(2, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(1, state.numActiveEsims);
        assertFalse(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsds_physicalAndEsimCardWithProfile() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        setupDualSim(mPhysicalSlot, mEsimSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(2, state.numActiveSlots);
        assertEquals(2, state.numActiveSims);
        assertEquals(1, state.numActiveEsims);
        assertTrue(isMultiSim);
    }

    @Test
    @SmallTest
    public void testDsds_dualPhysicalSimCards() {
        setupDualSim(mPhysicalSlot, mPhysicalSlot);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(2, state.numActiveSlots);
        assertEquals(2, state.numActiveSims);
        assertEquals(0, state.numActiveEsims);
        assertTrue(isMultiSim);
    }

    @Test
    public void testDsds_dualSimMEPFeature() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        doReturn(new UiccPort[]{mInactivePort, mActivePort}).when(mActiveCard).getUiccPortList();
        setupDualSim(mEmptySlot, mEsimSlot);
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(0));
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(1));

        boolean isEsim0 = SimSlotState.isEsim(0);
        boolean isEsim1 = SimSlotState.isEsim(1);

        assertTrue(isEsim0);
        assertTrue(isEsim1);

        SimSlotState state = SimSlotState.getCurrentState();
        boolean isMultiSim = SimSlotState.isMultiSim();

        assertEquals(2, state.numActiveSlots);
        assertEquals(1, state.numActiveSims);
        assertEquals(1, state.numActiveEsims);
        assertFalse(isMultiSim); // one Uicc Port does not have active sim profile
    }

    @Test
    @SmallTest
    public void isEsim_singlePhysicalSim() {
        doReturn(mPhysicalSlot).when(mUiccController).getUiccSlotForPhone(eq(0));

        boolean isEsim = SimSlotState.isEsim(0);

        assertFalse(isEsim);
    }

    @Test
    @SmallTest
    public void isEsim_singleEsim() {
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(0));

        boolean isEsim = SimSlotState.isEsim(0);

        assertTrue(isEsim);
    }

    @Test
    @SmallTest
    public void isEsim_dualSim() {
        doReturn(mPhysicalSlot).when(mUiccController).getUiccSlotForPhone(eq(0));
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(1));

        boolean isEsim0 = SimSlotState.isEsim(0);
        boolean isEsim1 = SimSlotState.isEsim(1);

        assertFalse(isEsim0);
        assertTrue(isEsim1);
    }

    private void setupSingleSim(UiccSlot slot0) {
        doReturn(new UiccSlot[] {slot0}).when(mUiccController).getUiccSlots();
        doReturn(slot0).when(mUiccController).getUiccSlot(eq(0));
    }

    private void setupDualSim(UiccSlot slot0, UiccSlot slot1) {
        doReturn(new UiccSlot[] {slot0, slot1}).when(mUiccController).getUiccSlots();
        doReturn(slot0).when(mUiccController).getUiccSlot(eq(0));
        doReturn(slot1).when(mUiccController).getUiccSlot(eq(1));
    }
}
