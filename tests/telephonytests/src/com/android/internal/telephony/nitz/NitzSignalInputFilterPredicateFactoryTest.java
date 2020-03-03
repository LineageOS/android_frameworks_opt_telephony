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
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.TimestampedValue;

import com.android.internal.telephony.NitzData;
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
        super.setUp("NitzSignalInputFilterPredicateFactoryTest");
        mFakeDeviceState = new FakeDeviceState();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_nullSecondArgumentRejected() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
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
        TimestampedValue<NitzData> nitzSignal = scenario
                .createNitzSignal(mFakeDeviceState.elapsedRealtime());
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(new TrivalentPredicate[0]);
        assertTrue(impl.mustProcessNitzSignal(null, nitzSignal));
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_nullIsIgnored() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        TrivalentPredicate nullPredicate = (x, y) -> null;
        TrivalentPredicate[] triPredicates = { nullPredicate };
        NitzSignalInputFilterPredicateImpl impl =
                new NitzSignalInputFilterPredicateImpl(triPredicates);
        assertTrue(impl.mustProcessNitzSignal(null, nitzSignal));
    }

    @Test
    public void testNitzSignalInputFilterPredicateImpl_trueIsHonored() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
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
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
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
        long elapsedRealtimeClock = mFakeDeviceState.elapsedRealtime();
        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal(elapsedRealtimeClock);

        TrivalentPredicate triPredicate =
                createBogusElapsedRealtimeCheck(mContext, mFakeDeviceState);
        assertNull(triPredicate.mustProcessNitzSignal(null, nitzSignal));

        // Any signal that claims to be from the future must be rejected.
        TimestampedValue<NitzData> bogusNitzSignal = new TimestampedValue<>(
                elapsedRealtimeClock + 1, nitzSignal.getValue());
        assertFalse(triPredicate.mustProcessNitzSignal(null, bogusNitzSignal));
    }

    @Test
    public void testTrivalentPredicate_noOldSignalCheck() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        TrivalentPredicate triPredicate =
                NitzSignalInputFilterPredicateFactory.createNoOldSignalCheck();
        assertTrue(triPredicate.mustProcessNitzSignal(null, nitzSignal));
        assertNull(triPredicate.mustProcessNitzSignal(nitzSignal, nitzSignal));
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_elapsedRealtime() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        int nitzSpacingThreshold = mFakeDeviceState.getNitzUpdateSpacingMillis();
        NitzData baseNitzData = scenario.createNitzData();

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtime();
        TimestampedValue<NitzData> baseSignal =
                new TimestampedValue<>(baseElapsedRealtimeMillis, baseNitzData);

        // Two identical signals: no spacing so the new signal should not be processed.
        {
            assertFalse(triPredicate.mustProcessNitzSignal(baseSignal, baseSignal));
        }

        // Two signals not spaced apart enough: the new signal should not processed.
        {
            int elapsedTimeIncrement = nitzSpacingThreshold - 1;
            TimestampedValue<NitzData> newSignal =
                    createIncrementedNitzSignal(baseSignal, elapsedTimeIncrement);
            assertFalse(triPredicate.mustProcessNitzSignal(baseSignal, newSignal));
        }

        // Two signals spaced apart: the new signal should be processed.
        {
            int elapsedTimeIncrement = nitzSpacingThreshold + 1;
            TimestampedValue<NitzData> newSignal =
                    createIncrementedNitzSignal(baseSignal, elapsedTimeIncrement);
            assertTrue(triPredicate.mustProcessNitzSignal(baseSignal, newSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_offsetDifference() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        int nitzSpacingThreshold = mFakeDeviceState.getNitzUpdateSpacingMillis();
        NitzData baseNitzData = scenario.createNitzData();

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtime();
        TimestampedValue<NitzData> baseSignal =
                new TimestampedValue<>(baseElapsedRealtimeMillis, baseNitzData);

        // Create a new NitzSignal that should be filtered.
        int elapsedTimeIncrement = nitzSpacingThreshold - 1;
        TimestampedValue<NitzData> intermediateNitzSignal =
                createIncrementedNitzSignal(baseSignal, elapsedTimeIncrement);
        NitzData intermediateNitzData = intermediateNitzSignal.getValue();
        assertFalse(triPredicate.mustProcessNitzSignal(baseSignal, intermediateNitzSignal));

        // Two signals spaced apart so that the second would be filtered, but they contain different
        // offset information so should be detected as "different" and processed.
        {
            // Modifying the local offset should be enough to recognize the NitzData as different.
            NitzData differentOffsetNitzData = NitzData.createForTests(
                    intermediateNitzData.getLocalOffsetMillis() + 1,
                    intermediateNitzData.getDstAdjustmentMillis(),
                    intermediateNitzData.getCurrentTimeInMillis(),
                    intermediateNitzData.getEmulatorHostTimeZone());
            TimestampedValue<NitzData> differentOffsetSignal = new TimestampedValue<>(
                    baseSignal.getReferenceTimeMillis() + elapsedTimeIncrement,
                    differentOffsetNitzData);
            assertTrue(triPredicate.mustProcessNitzSignal(baseSignal, differentOffsetSignal));
        }
    }

    @Test
    public void testTrivalentPredicate_rateLimitCheck_utcTimeDifferences() {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        int nitzSpacingThreshold = mFakeDeviceState.getNitzUpdateSpacingMillis();
        int nitzUtcDiffThreshold = mFakeDeviceState.getNitzUpdateDiffMillis();
        NitzData baseNitzData = scenario.createNitzData();

        TrivalentPredicate triPredicate = createRateLimitCheck(mFakeDeviceState);

        long baseElapsedRealtimeMillis = mFakeDeviceState.elapsedRealtime();
        TimestampedValue<NitzData> baseSignal =
                new TimestampedValue<>(baseElapsedRealtimeMillis, baseNitzData);

        // Create a new NitzSignal that should be filtered.
        int elapsedTimeIncrement = nitzSpacingThreshold - 1;
        TimestampedValue<NitzData> intermediateSignal =
                createIncrementedNitzSignal(baseSignal, elapsedTimeIncrement);
        NitzData intermediateNitzData = intermediateSignal.getValue();
        assertFalse(triPredicate.mustProcessNitzSignal(baseSignal, intermediateSignal));

        // Two signals spaced apart so that the second would normally be filtered and it contains
        // a UTC time that is not sufficiently different.
        {
            NitzData incrementedUtcTimeNitzData = NitzData.createForTests(
                    intermediateNitzData.getLocalOffsetMillis(),
                    intermediateNitzData.getDstAdjustmentMillis(),
                    intermediateNitzData.getCurrentTimeInMillis() + nitzUtcDiffThreshold - 1,
                    intermediateNitzData.getEmulatorHostTimeZone());

            TimestampedValue<NitzData> incrementedNitzSignal = new TimestampedValue<>(
                    intermediateSignal.getReferenceTimeMillis(), incrementedUtcTimeNitzData);
            assertFalse(triPredicate.mustProcessNitzSignal(baseSignal, incrementedNitzSignal));
        }

        // Two signals spaced apart so that the second would normally be filtered but it contains
        // a UTC time that is sufficiently different.
        {
            NitzData incrementedUtcTimeNitzData = NitzData.createForTests(
                    intermediateNitzData.getLocalOffsetMillis(),
                    intermediateNitzData.getDstAdjustmentMillis(),
                    intermediateNitzData.getCurrentTimeInMillis() + nitzUtcDiffThreshold + 1,
                    intermediateNitzData.getEmulatorHostTimeZone());

            TimestampedValue<NitzData> incrementedNitzSignal = new TimestampedValue<>(
                    intermediateSignal.getReferenceTimeMillis(), incrementedUtcTimeNitzData);
            assertTrue(triPredicate.mustProcessNitzSignal(baseSignal, incrementedNitzSignal));
        }

        // Two signals spaced apart so that the second would normally be filtered and it contains
        // a UTC time that is not sufficiently different.
        {
            NitzData decrementedUtcTimeNitzData = NitzData.createForTests(
                    intermediateNitzData.getLocalOffsetMillis(),
                    intermediateNitzData.getDstAdjustmentMillis(),
                    intermediateNitzData.getCurrentTimeInMillis() - nitzUtcDiffThreshold + 1,
                    intermediateNitzData.getEmulatorHostTimeZone());

            TimestampedValue<NitzData> decrementedNitzSignal = new TimestampedValue<>(
                    intermediateSignal.getReferenceTimeMillis(), decrementedUtcTimeNitzData);
            assertFalse(triPredicate.mustProcessNitzSignal(baseSignal, decrementedNitzSignal));
        }

        // Two signals spaced apart so that the second would normally be filtered but it contains
        // a UTC time that is sufficiently different.
        {
            NitzData decrementedUtcTimeNitzData = NitzData.createForTests(
                    intermediateNitzData.getLocalOffsetMillis(),
                    intermediateNitzData.getDstAdjustmentMillis(),
                    intermediateNitzData.getCurrentTimeInMillis() + nitzUtcDiffThreshold + 1,
                    intermediateNitzData.getEmulatorHostTimeZone());

            TimestampedValue<NitzData> decrementedNitzSignal = new TimestampedValue<>(
                    intermediateSignal.getReferenceTimeMillis(), decrementedUtcTimeNitzData);
            assertTrue(triPredicate.mustProcessNitzSignal(baseSignal, decrementedNitzSignal));
        }
    }

    /**
     * Creates an NITZ signal based on the the supplied signal but with all the fields related to
     * elapsed time incremented by the specified number of milliseconds.
     */
    private static TimestampedValue<NitzData> createIncrementedNitzSignal(
            TimestampedValue<NitzData> baseSignal, int incrementMillis) {
        NitzData baseData = baseSignal.getValue();
        return new TimestampedValue<>(baseSignal.getReferenceTimeMillis() + incrementMillis,
                NitzData.createForTests(
                        baseData.getLocalOffsetMillis(),
                        baseData.getDstAdjustmentMillis(),
                        baseData.getCurrentTimeInMillis() + incrementMillis,
                        baseData.getEmulatorHostTimeZone()));
    }
}
