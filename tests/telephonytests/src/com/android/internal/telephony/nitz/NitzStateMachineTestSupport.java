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

import static org.junit.Assert.fail;

import android.app.timedetector.TelephonyTimeSuggestion;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.os.TimestampedValue;

import com.android.internal.telephony.NitzData;
import com.android.internal.telephony.NitzStateMachine;
import com.android.internal.telephony.NitzStateMachine.DeviceState;

/**
 * An assortment of methods and classes for testing {@link NitzStateMachine} implementations.
 */
final class NitzStateMachineTestSupport {

    // Values used to when initializing device state but where the value isn't important.
    static final long ARBITRARY_SYSTEM_CLOCK_TIME = createUtcTime(1977, 1, 1, 12, 0, 0);
    static final long ARBITRARY_REALTIME_MILLIS = 123456789L;
    static final String ARBITRARY_DEBUG_INFO = "Test debug info";

    // A country with a single zone : the zone can be guessed from the country.
    // The UK uses UTC for part of the year so it is not good for detecting bogus NITZ signals.
    static final Scenario UNITED_KINGDOM_SCENARIO = new Scenario.Builder()
            .setTimeZone("Europe/London")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("gb")
            .buildFrozen();

    static final String UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID = "Europe/London";

    // The US is a country that has multiple zones, but there is only one matching time zone at the
    // time in this scenario: the zone cannot be guessed from the country alone, but can be guessed
    // from the country + NITZ. The US never uses UTC so it can be used for testing bogus (zero'd
    // values) NITZ signals.
    static final Scenario UNIQUE_US_ZONE_SCENARIO1 = new Scenario.Builder()
            .setTimeZone("America/Los_Angeles")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();

    // An alternative US scenario which also provides a unique time zone answer.
    static final Scenario UNIQUE_US_ZONE_SCENARIO2 = new Scenario.Builder()
            .setTimeZone("America/Chicago")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();

    // A non-unique US scenario: the offset information is ambiguous between America/Phoenix and
    // America/Denver during winter.
    static final Scenario NON_UNIQUE_US_ZONE_SCENARIO = new Scenario.Builder()
            .setTimeZone("America/Denver")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();
    static final String[] NON_UNIQUE_US_ZONE_SCENARIO_ZONES =
            { "America/Denver", "America/Phoenix" };

    static final String US_COUNTRY_DEFAULT_ZONE_ID = "America/New_York";

    // New Zealand is a country with multiple zones, but the default zone has the "boost" modifier
    // which means that NITZ isn't required to find the zone.
    static final Scenario NEW_ZEALAND_DEFAULT_SCENARIO = new Scenario.Builder()
            .setTimeZone("Pacific/Auckland")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("nz")
            .buildFrozen();
    static final Scenario NEW_ZEALAND_OTHER_SCENARIO = new Scenario.Builder()
            .setTimeZone("Pacific/Chatham")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("nz")
            .buildFrozen();

    static final String NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID = "Pacific/Auckland";

    // A country with a single zone: the zone can be guessed from the country alone. CZ never uses
    // UTC so it can be used for testing bogus NITZ signal handling.
    static final Scenario CZECHIA_SCENARIO = new Scenario.Builder()
            .setTimeZone("Europe/Prague")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("cz")
            .buildFrozen();

    static final String CZECHIA_COUNTRY_DEFAULT_ZONE_ID = "Europe/Prague";

    /**
     * A scenario used during tests. Describes a fictional reality.
     */
    static class Scenario {

        private final boolean mFrozen;
        private TimeZone mZone;
        private String mNetworkCountryIsoCode;
        private long mActualTimeMillis;

        Scenario(boolean frozen, long timeMillis, String zoneId, String countryIsoCode) {
            mFrozen = frozen;
            mActualTimeMillis = timeMillis;
            mZone = zone(zoneId);
            mNetworkCountryIsoCode = countryIsoCode;
        }

        /** Creates an NITZ signal to match the scenario. */
        TimestampedValue<NitzData> createNitzSignal(long elapsedRealtimeClock) {
            return new TimestampedValue<>(elapsedRealtimeClock, createNitzData());
        }

        /** Creates an NITZ signal to match the scenario. */
        NitzData createNitzData() {
            int[] offsets = new int[2];
            mZone.getOffset(mActualTimeMillis, false /* local */, offsets);
            int zoneOffsetMillis = offsets[0] + offsets[1];
            return NitzData.createForTests(
                    zoneOffsetMillis, offsets[1], mActualTimeMillis,
                    null /* emulatorHostTimeZone */);
        }

        String getNetworkCountryIsoCode() {
            return mNetworkCountryIsoCode;
        }

        String getTimeZoneId() {
            return mZone.getID();
        }

        TimeZone getTimeZone() {
            return mZone;
        }

        Scenario incrementTime(long timeIncrementMillis) {
            checkFrozen();
            mActualTimeMillis += timeIncrementMillis;
            return this;
        }

        Scenario changeCountry(String timeZoneId, String networkCountryIsoCode) {
            checkFrozen();
            mZone = zone(timeZoneId);
            mNetworkCountryIsoCode = networkCountryIsoCode;
            return this;
        }

        Scenario mutableCopy() {
            return new Scenario(
                    false /* frozen */, mActualTimeMillis, mZone.getID(), mNetworkCountryIsoCode);
        }

        private void checkFrozen() {
            if (mFrozen) {
                throw new IllegalStateException("Scenario is frozen. Copy first");
            }
        }

        static class Builder {

            private long mActualTimeMillis;
            private String mZoneId;
            private String mCountryIsoCode;

            Builder setActualTimeUtc(int year, int monthInYear, int day, int hourOfDay,
                    int minute, int second) {
                mActualTimeMillis = createUtcTime(year, monthInYear, day, hourOfDay, minute,
                        second);
                return this;
            }

            Builder setTimeZone(String zoneId) {
                mZoneId = zoneId;
                return this;
            }

            Builder setCountryIso(String isoCode) {
                mCountryIsoCode = isoCode;
                return this;
            }

            Scenario buildFrozen() {
                return new Scenario(true /* frozen */, mActualTimeMillis, mZoneId, mCountryIsoCode);
            }
        }
    }

    /** A fake implementation of {@link DeviceState}. */
    static class FakeDeviceState implements DeviceState {

        public boolean ignoreNitz;
        public int nitzUpdateDiffMillis;
        public int nitzUpdateSpacingMillis;
        public long elapsedRealtime;
        public long currentTimeMillis;

        FakeDeviceState() {
            // Set sensible defaults fake device state.
            ignoreNitz = false;
            nitzUpdateDiffMillis = 2000;
            nitzUpdateSpacingMillis = 1000 * 60 * 10;
            elapsedRealtime = ARBITRARY_REALTIME_MILLIS;
        }

        @Override
        public int getNitzUpdateSpacingMillis() {
            return nitzUpdateSpacingMillis;
        }

        @Override
        public int getNitzUpdateDiffMillis() {
            return nitzUpdateDiffMillis;
        }

        @Override
        public boolean getIgnoreNitz() {
            return ignoreNitz;
        }

        @Override
        public long elapsedRealtime() {
            return elapsedRealtime;
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
        }

        void simulateTimeIncrement(int timeIncrementMillis) {
            if (timeIncrementMillis <= 0) {
                fail("elapsedRealtime clock must go forwards");
            }
            elapsedRealtime += timeIncrementMillis;
            currentTimeMillis += timeIncrementMillis;
        }

    }

    private NitzStateMachineTestSupport() {}

    private static long createUtcTime(int year, int monthInYear, int day, int hourOfDay, int minute,
            int second) {
        Calendar cal = new GregorianCalendar(zone("Etc/UTC"));
        cal.clear();
        cal.set(year, monthInYear - 1, day, hourOfDay, minute, second);
        return cal.getTimeInMillis();
    }

    static TelephonyTimeZoneSuggestion createEmptyTimeZoneSuggestion(int slotIndex) {
        return new TelephonyTimeZoneSuggestion.Builder(slotIndex)
                .addDebugInfo("Test")
                .build();
    }

    static TelephonyTimeSuggestion createEmptyTimeSuggestion(int slotIndex) {
        return new TelephonyTimeSuggestion.Builder(slotIndex)
                .addDebugInfo("Test")
                .build();
    }

    static TelephonyTimeSuggestion createTimeSuggestionFromNitzSignal(
            int slotIndex, TimestampedValue<NitzData> nitzSignal) {
        return new TelephonyTimeSuggestion.Builder(slotIndex)
                .setUtcTime(createTimeSignalFromNitzSignal(nitzSignal))
                .addDebugInfo("Test")
                .build();
    }

    private static TimestampedValue<Long> createTimeSignalFromNitzSignal(
            TimestampedValue<NitzData> nitzSignal) {
        return new TimestampedValue<>(
                nitzSignal.getReferenceTimeMillis(),
                nitzSignal.getValue().getCurrentTimeInMillis());
    }

    private static TimeZone zone(String zoneId) {
        TimeZone timeZone = TimeZone.getFrozenTimeZone(zoneId);
        if (timeZone.getID().equals(TimeZone.UNKNOWN_ZONE_ID)) {
            fail(zoneId + " is not a valid zone");
        }
        return timeZone;
    }
}
