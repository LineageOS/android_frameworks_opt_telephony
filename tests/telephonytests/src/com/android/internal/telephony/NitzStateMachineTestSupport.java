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

import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.util.TimestampedValue;

import com.android.internal.telephony.NitzStateMachine.DeviceState;

/**
 * An assortment of methods and classes for testing {@link NitzStateMachine} implementations.
 */
final class NitzStateMachineTestSupport {

    /**
     * A scenario used during tests. Describes a fictional reality.
     */
    static class Scenario {

        private final boolean mFrozen;
        private TimeZone mZone;
        private String mNetworkCountryIsoCode;
        private long mElapsedRealtimeMillis;
        private long mActualTimeMillis;

        Scenario(boolean frozen, long elapsedRealtimeMillis, long timeMillis, String zoneId,
                String countryIsoCode) {
            mFrozen = frozen;
            mActualTimeMillis = timeMillis;
            mElapsedRealtimeMillis = elapsedRealtimeMillis;
            mZone = zone(zoneId);
            mNetworkCountryIsoCode = countryIsoCode;
        }

        /** Creates an NITZ signal to match the scenario. */
        TimestampedValue<NitzData> createNitzSignal() {
            int[] offsets = new int[2];
            mZone.getOffset(mActualTimeMillis, false /* local */, offsets);
            int zoneOffsetMillis = offsets[0] + offsets[1];
            NitzData nitzData = NitzData.createForTests(
                    zoneOffsetMillis, offsets[1], mActualTimeMillis,
                    null /* emulatorHostTimeZone */);
            return new TimestampedValue<>(mElapsedRealtimeMillis, nitzData);
        }

        /** Creates a time signal to match the scenario. */
        TimestampedValue<Long> createTimeSignal() {
            return new TimestampedValue<>(mElapsedRealtimeMillis, mActualTimeMillis);
        }

        long getDeviceRealTimeMillis() {
            return mElapsedRealtimeMillis;
        }

        String getNetworkCountryIsoCode() {
            return mNetworkCountryIsoCode;
        }

        String getTimeZoneId() {
            return mZone.getID();
        }

        long getActualTimeMillis() {
            return mActualTimeMillis;
        }

        Scenario incrementTime(long timeIncrementMillis) {
            checkFrozen();
            mElapsedRealtimeMillis += timeIncrementMillis;
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
                    false /* frozen */, mElapsedRealtimeMillis, mActualTimeMillis, mZone.getID(),
                    mNetworkCountryIsoCode);
        }

        private void checkFrozen() {
            if (mFrozen) {
                throw new IllegalStateException("Scenario is frozen. Copy first");
            }
        }

        static class Builder {

            private long mInitialDeviceRealtimeMillis;
            private long mActualTimeMillis;
            private String mZoneId;
            private String mCountryIsoCode;

            Builder setDeviceRealtimeMillis(long realtimeMillis) {
                mInitialDeviceRealtimeMillis = realtimeMillis;
                return this;
            }

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
                return new Scenario(
                        true /* frozen */, mInitialDeviceRealtimeMillis, mActualTimeMillis, mZoneId,
                        mCountryIsoCode);
            }
        }
    }

    /** A fake implementation of {@link DeviceState}. */
    static class FakeDeviceState implements DeviceState {

        public boolean ignoreNitz;
        public int nitzUpdateDiffMillis;
        public int nitzUpdateSpacingMillis;
        public String networkCountryIsoForPhone;
        public long elapsedRealtime;
        public long currentTimeMillis;

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
        public String getNetworkCountryIsoForPhone() {
            return networkCountryIsoForPhone;
        }

        @Override
        public long elapsedRealtime() {
            return elapsedRealtime;
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
        }

    }

    private NitzStateMachineTestSupport() {}

    static long createUtcTime(int year, int monthInYear, int day, int hourOfDay, int minute,
            int second) {
        Calendar cal = new GregorianCalendar(zone("Etc/UTC"));
        cal.clear();
        cal.set(year, monthInYear - 1, day, hourOfDay, minute, second);
        return cal.getTimeInMillis();
    }

    static TimestampedValue<Long> createTimeSignalFromNitzSignal(
            TimestampedValue<NitzData> nitzSignal) {
        return new TimestampedValue<>(
                nitzSignal.getReferenceTimeMillis(),
                nitzSignal.getValue().getCurrentTimeInMillis());
    }

    static TimeZone zone(String zoneId) {
        TimeZone timeZone = TimeZone.getFrozenTimeZone(zoneId);
        if (timeZone.getID().equals(TimeZone.UNKNOWN_ZONE_ID)) {
            fail(zoneId + " is not a valid zone");
        }
        return timeZone;
    }
}
