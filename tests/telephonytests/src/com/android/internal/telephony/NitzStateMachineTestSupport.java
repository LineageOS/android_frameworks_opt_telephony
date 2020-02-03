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

package com.android.internal.telephony;

import static org.junit.Assert.fail;

import android.app.timedetector.PhoneTimeSuggestion;
import android.app.timezonedetector.PhoneTimeZoneSuggestion;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.os.TimestampedValue;

import com.android.internal.telephony.NitzStateMachine.DeviceState;

/**
 * An assortment of methods and classes for testing {@link NitzStateMachine} implementations.
 */
public final class NitzStateMachineTestSupport {

    // Values used to when initializing device state but where the value isn't important.
    public static final long ARBITRARY_SYSTEM_CLOCK_TIME = createUtcTime(1977, 1, 1, 12, 0, 0);
    public static final long ARBITRARY_REALTIME_MILLIS = 123456789L;
    // This zone isn't used in any of the scenarios below.
    public static final String ARBITRARY_TIME_ZONE_ID = "Europe/Paris";
    public static final String ARBITRARY_DEBUG_INFO = "Test debug info";

    // A country with a single zone : the zone can be guessed from the country.
    // The UK uses UTC for part of the year so it is not good for detecting bogus NITZ signals.
    public static final Scenario UNITED_KINGDOM_SCENARIO = new Scenario.Builder()
            .setTimeZone("Europe/London")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("gb")
            .buildFrozen();

    public static final String UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID = "Europe/London";

    // The US is a country that has multiple zones, but there is only one matching time zone at the
    // time in this scenario: the zone cannot be guessed from the country alone, but can be guessed
    // from the country + NITZ. The US never uses UTC so it can be used for testing bogus (zero'd
    // values) NITZ signals.
    public static final Scenario UNIQUE_US_ZONE_SCENARIO1 = new Scenario.Builder()
            .setTimeZone("America/Los_Angeles")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();

    // An alternative US scenario which also provides a unique time zone answer.
    public static final Scenario UNIQUE_US_ZONE_SCENARIO2 = new Scenario.Builder()
            .setTimeZone("America/Chicago")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();

    // A non-unique US scenario: the offset information is ambiguous between America/Phoenix and
    // America/Denver during winter.
    public static final Scenario NON_UNIQUE_US_ZONE_SCENARIO = new Scenario.Builder()
            .setTimeZone("America/Denver")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();
    public static final String[] NON_UNIQUE_US_ZONE_SCENARIO_ZONES =
            { "America/Denver", "America/Phoenix" };

    public static final String US_COUNTRY_DEFAULT_ZONE_ID = "America/New_York";

    // New Zealand is a country with multiple zones, but the default zone has the "boost" modifier
    // which means that NITZ isn't required to find the zone.
    public static final Scenario NEW_ZEALAND_DEFAULT_SCENARIO = new Scenario.Builder()
            .setTimeZone("Pacific/Auckland")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("nz")
            .buildFrozen();
    public static final Scenario NEW_ZEALAND_OTHER_SCENARIO = new Scenario.Builder()
            .setTimeZone("Pacific/Chatham")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("nz")
            .buildFrozen();

    public static final String NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID = "Pacific/Auckland";

    // A country with a single zone: the zone can be guessed from the country alone. CZ never uses
    // UTC so it can be used for testing bogus NITZ signal handling.
    public static final Scenario CZECHIA_SCENARIO = new Scenario.Builder()
            .setTimeZone("Europe/Prague")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("cz")
            .buildFrozen();

    public static final String CZECHIA_COUNTRY_DEFAULT_ZONE_ID = "Europe/Prague";

    /**
     * A scenario used during tests. Describes a fictional reality.
     */
    public static class Scenario {

        private final boolean mFrozen;
        private TimeZone mZone;
        private String mNetworkCountryIsoCode;
        private long mActualTimeMillis;

        public Scenario(boolean frozen, long timeMillis, String zoneId, String countryIsoCode) {
            mFrozen = frozen;
            mActualTimeMillis = timeMillis;
            mZone = zone(zoneId);
            mNetworkCountryIsoCode = countryIsoCode;
        }

        /** Creates an NITZ signal to match the scenario. */
        public TimestampedValue<NitzData> createNitzSignal(long elapsedRealtimeClock) {
            return new TimestampedValue<>(elapsedRealtimeClock, createNitzData());
        }

        /** Creates an NITZ signal to match the scenario. */
        public NitzData createNitzData() {
            int[] offsets = new int[2];
            mZone.getOffset(mActualTimeMillis, false /* local */, offsets);
            int zoneOffsetMillis = offsets[0] + offsets[1];
            return NitzData.createForTests(
                    zoneOffsetMillis, offsets[1], mActualTimeMillis,
                    null /* emulatorHostTimeZone */);
        }

        /** Creates a time signal to match the scenario. */
        public TimestampedValue<Long> createTimeSignal(long elapsedRealtimeClock) {
            return new TimestampedValue<>(elapsedRealtimeClock, mActualTimeMillis);
        }

        public String getNetworkCountryIsoCode() {
            return mNetworkCountryIsoCode;
        }

        public String getTimeZoneId() {
            return mZone.getID();
        }

        public TimeZone getTimeZone() {
            return mZone;
        }

        public long getActualTimeMillis() {
            return mActualTimeMillis;
        }

        public Scenario incrementTime(long timeIncrementMillis) {
            checkFrozen();
            mActualTimeMillis += timeIncrementMillis;
            return this;
        }

        public Scenario changeCountry(String timeZoneId, String networkCountryIsoCode) {
            checkFrozen();
            mZone = zone(timeZoneId);
            mNetworkCountryIsoCode = networkCountryIsoCode;
            return this;
        }

        public Scenario mutableCopy() {
            return new Scenario(
                    false /* frozen */, mActualTimeMillis, mZone.getID(), mNetworkCountryIsoCode);
        }

        private void checkFrozen() {
            if (mFrozen) {
                throw new IllegalStateException("Scenario is frozen. Copy first");
            }
        }

        public static class Builder {

            private long mActualTimeMillis;
            private String mZoneId;
            private String mCountryIsoCode;

            public Builder setActualTimeUtc(int year, int monthInYear, int day, int hourOfDay,
                    int minute, int second) {
                mActualTimeMillis = createUtcTime(year, monthInYear, day, hourOfDay, minute,
                        second);
                return this;
            }

            public Builder setTimeZone(String zoneId) {
                mZoneId = zoneId;
                return this;
            }

            public Builder setCountryIso(String isoCode) {
                mCountryIsoCode = isoCode;
                return this;
            }

            public Scenario buildFrozen() {
                return new Scenario(true /* frozen */, mActualTimeMillis, mZoneId, mCountryIsoCode);
            }
        }
    }

    /** A fake implementation of {@link DeviceState}. */
    public static class FakeDeviceState implements DeviceState {

        public boolean ignoreNitz;
        public int nitzUpdateDiffMillis;
        public int nitzUpdateSpacingMillis;
        public long elapsedRealtime;
        public long currentTimeMillis;

        public FakeDeviceState() {
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

        public void simulateTimeIncrement(int timeIncrementMillis) {
            if (timeIncrementMillis <= 0) {
                fail("elapsedRealtime clock must go forwards");
            }
            elapsedRealtime += timeIncrementMillis;
            currentTimeMillis += timeIncrementMillis;
        }

    }

    private NitzStateMachineTestSupport() {}

    public static long createUtcTime(int year, int monthInYear, int day, int hourOfDay, int minute,
            int second) {
        Calendar cal = new GregorianCalendar(zone("Etc/UTC"));
        cal.clear();
        cal.set(year, monthInYear - 1, day, hourOfDay, minute, second);
        return cal.getTimeInMillis();
    }

    public static PhoneTimeZoneSuggestion createEmptyTimeZoneSuggestion(int slotIndex) {
        return new PhoneTimeZoneSuggestion.Builder(slotIndex)
                .addDebugInfo("Test")
                .build();
    }

    public static PhoneTimeSuggestion createEmptyTimeSuggestion(int slotIndex) {
        return new PhoneTimeSuggestion.Builder(slotIndex)
                .addDebugInfo("Test")
                .build();
    }

    public static PhoneTimeSuggestion createTimeSuggestionFromNitzSignal(
            int slotIndex, TimestampedValue<NitzData> nitzSignal) {
        return new PhoneTimeSuggestion.Builder(slotIndex)
                .setUtcTime(createTimeSignalFromNitzSignal(nitzSignal))
                .addDebugInfo("Test")
                .build();
    }

    public static TimestampedValue<Long> createTimeSignalFromNitzSignal(
            TimestampedValue<NitzData> nitzSignal) {
        return new TimestampedValue<>(
                nitzSignal.getReferenceTimeMillis(),
                nitzSignal.getValue().getCurrentTimeInMillis());
    }

    public static TimeZone zone(String zoneId) {
        TimeZone timeZone = TimeZone.getFrozenTimeZone(zoneId);
        if (timeZone.getID().equals(TimeZone.UNKNOWN_ZONE_ID)) {
            fail(zoneId + " is not a valid zone");
        }
        return timeZone;
    }
}
