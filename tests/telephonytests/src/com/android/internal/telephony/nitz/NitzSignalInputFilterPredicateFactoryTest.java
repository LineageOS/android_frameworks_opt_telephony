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

package com.android.internal.telephony.nitz;

import static com.android.internal.telephony.nitz.NitzSignalInputFilterPredicateFactory.createBogusElapsedRealtimeCheck;
import static com.android.internal.telephony.nitz.NitzSignalInputFilterPredicateFactory.createIgnoreNitzPropertyCheck;
import static com.android.internal.telephony.nitz.NitzSignalInputFilterPredicateFactory.createRateLimitCheck;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.ARBITRARY_AGE;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.internal.telephony.NitzData;
import com.android.internal.telephony.NitzSignal;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nitz.NitzSignalInputFilterPredicateFactory.NitzSignalInputFilterPredicateImpl;
import com.android.internal.telephony.nitz.NitzSignalInputFilterPredicateFactory.TrivalentPredicate;
import com.android.internal.telephony.nitz.NitzStateMachineTestSupport.FakeDeviceState;
import com.android.internal.telephony.nitz.NitzStateMachineTestSupport.Scenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NitzSignalInputFilterPredicateFactoryTest extends TelephonyTest {

    private FakeDeviceState mFakeDeviceState;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mFakeDeviceState = new FakeDeviceState();
    }

    @After
    public void tearDown() throws Exception {
        mFakeDeviceState = null;
        super.tearDown();
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_nullSecondArgumentRejected() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        NitzSignal nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtimeMillis(), ARBITRARY_AGE);
        TrivalentPredicate[] triPredicates = {};
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(triPredicates);
        try {
            impl.mustProcessNitzSignal(nitzSignal, null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_defaultIsTrue() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        NitzSignal nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtimeMillis(), ARBITRARY_AGE);
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(new TrivalentPredicate[0]);
        assertTrue(impl.mustProcessNitzSignal(null, nitzSignal));
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_nullIsIgnored() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        NitzSignal nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtimeMillis(), ARBITRARY_AGE);
        TrivalentPredicate nullPredicate = (x, y) -> null;
        TrivalentPredicate[] triPredicates = { nullPredicate };
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(triPredicates);
        assertTrue(impl.mustProcessNitzSignal(null, nitzSignal));
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_trueIsHonored() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        NitzSignal nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtimeMillis(), ARBITRARY_AGE);
        TrivalentPredicate nullPredicate = (x, y) -> null;
        TrivalentPredicate truePredicate = (x, y) -> true;
        TrivalentPredicate exceptionPredicate = (x, y) -> {
            throw new RuntimeException();
        };
        TrivalentPredicate[] triPredicates = {
                nullPredicate,
                truePredicate,
                exceptionPredicate,
        };
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(triPredicates);
        assertTrue(impl.mustProcessNitzSignal(null, nitzSignal));
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_falseIsHonored() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        NitzSignal nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtimeMillis(), ARBITRARY_AGE);
        TrivalentPredicate nullPredicate = (x, y) -> null;
        TrivalentPredicate falsePredicate = (x, y) -> false;
        TrivalentPredicate exceptionPredicate = (x, y) -> {
            throw new RuntimeException();
        };
        TrivalentPredicate[] triPredicates = {
                nullPredicate,
                falsePredicate,
                exceptionPredicate,
        };
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(triPredicates);
        assertFalse(impl.mustProcessNitzSignal(null, nitzSignal));
    }

    @Test
    public void testTrivalentPredicate_ignoreNitzPropertyCheck() {
        TrivalentPredicate triPredicate = createIgnoreNitzPropertyCheck(mFakeDeviceState);

        mFakeDeviceState.ignoreNitz = true;
        assertFalse(triPredicate.mustProcessNitzSignal(null, null));

        mFakeDeviceState.ignoreNitz = false;
        assertNull(triPredicate.mustProcessNitzSignal(null, null));
    }

    @Test
    public void testTrivalentPredicate_bogusElapsedRealtimeCheck() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        long elapsedRealtimeMillis = mFakeDeviceState.elapsedRealtimeMillis();
        NitzSignal baseNitzSignal =
                scenario.createNitzSignal(elapsedRealtimeMillis, ARBITRARY_AGE);

        TrivalentPredicate triPredicate =
                createBogusElapsedRealtimeCheck(mContext, mFakeDeviceState);
        assertNull(triPredicate.mustProcessNitzSignal(null, baseNitzSignal));

        // Any signal that claims to be from the future must be rejected.
        {
            long receiptElapsedMillis = elapsedRealtimeMillis + 1;
            long ageMillis = 0;
            NitzSignal bogusNitzSignal = new NitzSignal(
                    receiptElapsedMillis, baseNitzSignal.getNitzData(), ageMillis);
            assertFalse(triPredicate.mustProcessNitzSignal(null, bogusNitzSignal));
        }

        // Age should be ignored: the predicate is intended to check receipt time isn't obviously
        // corrupt / fabricated to be in the future. Larger ages could imply that the NITZ was
        // received by the modem before the elapsed realtime clock started ticking, but we don't
        // currently check for that.
        {
            long receiptElapsedMillis = elapsedRealtimeMillis + 1;
            long ageMillis = 10000;
            NitzSignal bogusNitzSignal = new NitzSignal(
                    receiptElapsedMillis, baseNitzSignal.getNitzData(), ageMillis);

            assertFalse(triPredicate.mustProcessNitzSignal(null, bogusNitzSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_noOldSignalCheck() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        NitzSignal nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtimeMillis(), ARBITRARY_AGE);

        TrivalentPredicate triPredicate =
                NitzSignalInputFilterPredicateFactory.createNoOldSignalCheck();
        assertTrue(triPredicate.mustProcessNitzSignal(null, nitzSignal));
        assertNull(triPredicate.mustProcessNitzSignal(nitzSignal, nitzSignal));
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_elapsedRealtime_zeroAge() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        int nitzSpacingThreshold = mFakeDeviceState.getNitzUpdateSpacingMillis();
        // Change the other setting that can affect the predicate behavior so it is not a factor in
        // the test.
        mFakeDeviceState.setNitzUpdateDiffMillis(Integer.MAX_VALUE);

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtimeMillis();
        NitzData baseNitzData = scenario.createNitzData();
        int baseAgeMillis = 0;
        NitzSignal baseNitzSignal =
                new NitzSignal(baseElapsedRealtimeMillis, baseNitzData, baseAgeMillis);

        // Two identical signals: no spacing so the new signal should not be processed.
        assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, baseNitzSignal));

        // Two signals not spaced apart enough in receipt time: the new signal should not be
        // processed.
        {
            int timeAdjustment = nitzSpacingThreshold - 1;
            int unixTimeAdjustment = 0;
            long ageAdjustment = 0;
            NitzSignal newSignal = createAdjustedNitzSignal(
                    baseNitzSignal, timeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, newSignal));
        }

        // Two signals spaced apart: the new signal should be processed.
        {
            int timeAdjustment = nitzSpacingThreshold + 1;
            int unixTimeAdjustment = 0;
            long ageAdjustment = 0;
            NitzSignal newSignal = createAdjustedNitzSignal(
                    baseNitzSignal, timeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, newSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_elapsedRealtime_withAge() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        int nitzSpacingThreshold = 60000;
        mFakeDeviceState.setNitzUpdateSpacingMillis(nitzSpacingThreshold);

        // Change the other setting that can affect the predicate behavior so it is not a factor in
        // the test.
        mFakeDeviceState.setNitzUpdateDiffMillis(Integer.MAX_VALUE);

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        // Create a NITZ signal to be the first of two NITZ signals received.
        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtimeMillis();
        NitzData baseNitzData = scenario.createNitzData();
        int baseAgeMillis = 20000;
        NitzSignal baseNitzSignal =
                new NitzSignal(baseElapsedRealtimeMillis, baseNitzData, baseAgeMillis);

        // Two identical signals: no spacing so the new signal should not be processed.
        assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, baseNitzSignal));

        // Two signals not spaced apart enough: the new signal should not be processed.
        // The age is changed to prove it doesn't affect this check.
        {
            int elapsedRealtimeAdjustment = nitzSpacingThreshold - 1;
            int unixTimeAdjustment = 0;
            long ageAdjustment = 10000;
            NitzSignal newSignal = createAdjustedNitzSignal(
                    baseNitzSignal, elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, newSignal));
        }

        // Two signals not spaced apart enough: the new signal should not be processed.
        // The age is changed to prove it doesn't affect this check.
        {
            int elapsedRealtimeAdjustment = nitzSpacingThreshold - 1;
            int unixTimeAdjustment = 0;
            long ageAdjustment = -10000;
            NitzSignal newSignal = createAdjustedNitzSignal(
                    baseNitzSignal, elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, newSignal));
        }

        // Two signals spaced far enough apart: the new signal should be processed.
        {
            int elapsedRealtimeAdjustment = nitzSpacingThreshold + 1;
            int unixTimeAdjustment = 0;
            long ageAdjustment = 10000;
            NitzSignal newSignal = createAdjustedNitzSignal(
                    baseNitzSignal, elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, newSignal));
        }

        // Two signals spaced far enough apart: the new signal should be processed.
        {
            int elapsedRealtimeAdjustment = nitzSpacingThreshold + 1;
            int unixTimeAdjustment = 0;
            long ageAdjustment = -10000;
            NitzSignal newSignal = createAdjustedNitzSignal(
                    baseNitzSignal, elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, newSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_offsetDifference() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        int nitzSpacingThreshold = mFakeDeviceState.getNitzUpdateSpacingMillis();

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtimeMillis();
        NitzData baseNitzData = scenario.createNitzData();
        long baseAgeMillis = 0;
        NitzSignal baseNitzSignal = new NitzSignal(
                baseElapsedRealtimeMillis, baseNitzData, baseAgeMillis);

        // Create a new NitzSignal that would normally be filtered.
        int timeAdjustment = nitzSpacingThreshold - 1;
        long ageAdjustment = 0;
        NitzSignal intermediateNitzSignal = createAdjustedNitzSignal(
                baseNitzSignal, timeAdjustment, timeAdjustment, ageAdjustment);
        NitzData intermediateNitzData = intermediateNitzSignal.getNitzData();
        assertAgeAdjustedUnixEpochTimeIsIdentical(baseNitzSignal, intermediateNitzSignal);
        assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, intermediateNitzSignal));

        // Two signals spaced apart so that the second would be filtered, but they contain different
        // offset information so should be detected as "different" and processed.
        {
            // Modifying the local offset should be enough to recognize the NitzData as different.
            NitzData differentOffsetNitzData = NitzData.createForTests(
                    intermediateNitzData.getLocalOffsetMillis() + 1,
                    intermediateNitzData.getDstAdjustmentMillis(),
                    intermediateNitzData.getCurrentTimeInMillis(),
                    intermediateNitzData.getEmulatorHostTimeZone());
            NitzSignal differentOffsetSignal = new NitzSignal(
                    baseNitzSignal.getReceiptElapsedRealtimeMillis() + timeAdjustment,
                    differentOffsetNitzData,
                    baseNitzSignal.getAgeMillis());
            assertAgeAdjustedUnixEpochTimeIsIdentical(baseNitzSignal, differentOffsetSignal);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, differentOffsetSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_unixEpochTimeDifferences_withZeroAge() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        // Change the other setting that can affect the predicate behavior so it is not a factor in
        // the test.
        mFakeDeviceState.setNitzUpdateSpacingMillis(Integer.MAX_VALUE);
        int nitzUnixDiffThreshold = mFakeDeviceState.getNitzUpdateDiffMillis();

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtimeMillis();
        NitzData baseNitzData = scenario.createNitzData();
        int baseAgeMillis = 0;
        NitzSignal baseNitzSignal =
                new NitzSignal(baseElapsedRealtimeMillis, baseNitzData, baseAgeMillis);

        // Two signals spaced contain Unix epoch times that are not sufficiently different and so
        // should be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = nitzUnixDiffThreshold - 1;
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(baseNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }

        // Two signals spaced contain Unix epoch times that are not sufficiently different and so
        // should be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = -(nitzUnixDiffThreshold - 1);
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(baseNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }

        // Two signals spaced contain Unix epoch times that are sufficiently different and so should
        // not be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = nitzUnixDiffThreshold + 1;
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(baseNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }

        // Two signals spaced contain Unix epoch times that are sufficiently different and so should
        // not be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = -(nitzUnixDiffThreshold + 1);
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(baseNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_unixEpochTimeDifferences_withAge() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        // Change the other setting that can affect the predicate behavior so it is not a factor in
        // the test.
        mFakeDeviceState.setNitzUpdateSpacingMillis(Integer.MAX_VALUE);
        int nitzUnixDiffThreshold = mFakeDeviceState.getNitzUpdateDiffMillis();

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtimeMillis();
        NitzData baseNitzData = scenario.createNitzData();
        int baseAgeMillis = 20000;
        NitzSignal baseNitzSignal =
                new NitzSignal(baseElapsedRealtimeMillis, baseNitzData, baseAgeMillis);

        // This is another NitzSignal that represents the same time as baseNitzSignal, but it has
        // been cached by the modem for a different amount of time, so has different values even
        // though it encodes for the same Unix epoch time. Used to construct test signals below.
        int intermediateSignalAgeAdjustment = -10000;
        int intermediateUnixTimeAdjustment = 0;
        NitzSignal intermediateNitzSignal = createAdjustedNitzSignal(baseNitzSignal,
                intermediateSignalAgeAdjustment, intermediateUnixTimeAdjustment,
                intermediateSignalAgeAdjustment);
        assertAgeAdjustedUnixEpochTimeIsIdentical(baseNitzSignal, intermediateNitzSignal);

        // Two signals spaced contain Unix epoch times that are not sufficiently different and so
        // should be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = nitzUnixDiffThreshold - 1;
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(intermediateNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }

        // Two signals spaced contain Unix epoch times that are not sufficiently different and so
        // should be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = -(nitzUnixDiffThreshold - 1);
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(intermediateNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertFalse(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }

        // Two signals spaced contain Unix epoch times that are sufficiently different and so should
        // not be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = nitzUnixDiffThreshold + 1;
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(intermediateNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }

        // Two signals spaced contain Unix epoch times that are sufficiently different and so should
        // not be filtered.
        {
            int elapsedRealtimeAdjustment = 0;
            int unixTimeAdjustment = -(nitzUnixDiffThreshold + 1);
            long ageAdjustment = 0;
            NitzSignal nitzSignal = createAdjustedNitzSignal(intermediateNitzSignal,
                    elapsedRealtimeAdjustment, unixTimeAdjustment, ageAdjustment);
            assertTrue(triPredicate.mustProcessNitzSignal(baseNitzSignal, nitzSignal));
        }
    }

    /**
     * Creates an NITZ signal based on the supplied signal but with all the fields associated with
     * the time (receipt time, Unix epoch and age) adjusted by the specified amounts.
     */
    private static NitzSignal createAdjustedNitzSignal(
            NitzSignal baseNitzSignal, int elapsedRealtimeMillisAdjustment,
            int unixMillisAdjustment, long ageMillisAdjustment) {
        long adjustedReceiptElapsedMillis =
                baseNitzSignal.getReceiptElapsedRealtimeMillis() + elapsedRealtimeMillisAdjustment;
        NitzData adjustedNitzData =
                createAdjustedNitzData(baseNitzSignal.getNitzData(), unixMillisAdjustment);
        long adjustedAgeMillis = baseNitzSignal.getAgeMillis() + ageMillisAdjustment;
        return new NitzSignal(adjustedReceiptElapsedMillis, adjustedNitzData, adjustedAgeMillis);
    }

    /** Creates a new NitzData by adjusting the Unix epoch time in the supplied NitzData */
    private static NitzData createAdjustedNitzData(NitzData baseData, int unixMillisAdjustment) {
        return NitzData.createForTests(
                baseData.getLocalOffsetMillis(),
                baseData.getDstAdjustmentMillis(),
                baseData.getCurrentTimeInMillis() + unixMillisAdjustment,
                baseData.getEmulatorHostTimeZone());
    }

    /**
     * Used during tests to confirm that two NitzSignal test objects represent the same Unix epoch
     * time, even though their receipt times and ages may differ.
     */
    private static void assertAgeAdjustedUnixEpochTimeIsIdentical(
            NitzSignal signal1, NitzSignal signal2) {
        long referenceTimeDifference = signal2.getAgeAdjustedElapsedRealtimeMillis()
                - signal1.getAgeAdjustedElapsedRealtimeMillis();
        long unixEpochTimeDifference = signal2.getNitzData().getCurrentTimeInMillis()
                - signal1.getNitzData().getCurrentTimeInMillis();
        assertEquals(referenceTimeDifference, unixEpochTimeDifference);
    }
}
