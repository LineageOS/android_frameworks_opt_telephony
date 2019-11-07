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

import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.EMULATOR_ZONE_ID;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.MATCH_TYPE_NA;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_SAME_OFFSET;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.NETWORK_COUNTRY_AND_OFFSET;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.NETWORK_COUNTRY_ONLY;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.QUALITY_NA;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.SINGLE_ZONE;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.TEST_NETWORK_OFFSET_ONLY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Objects;

/**
 * A singleton, stateful time zone detection service that is aware of multiple phone devices. It
 * keeps track of the most recent suggestion from each phone and it uses the best based on a scoring
 * algorithm. If both phones provide the same score then the phone with the lowest numeric ID
 * "wins". If the situation changes and it is no longer possible to be confident about the time
 * zone, phones must submit an empty suggestion in order to "withdraw" their previous suggestion.
 *
 * <p>Ultimately, this responsibility will be moved to system server and then it will be extended /
 * rewritten to handle non-telephony time zone signals.
 */
public class TimeZoneDetectionService {

    /**
     * Used by {@link TimeZoneDetectionService} to interact with device settings. It can be faked
     * for tests.
     */
    @VisibleForTesting
    public interface Helper {

        /**
         * Callback interface for automatic detection enable/disable changes.
         */
        interface Listener {
            /**
             * Automatic time zone detection has been enabled or disabled.
             */
            void onTimeZoneDetectionChange(boolean enabled);
        }

        /**
         * Sets a listener that will be called when the automatic time / time zone detection setting
         * changes.
         */
        void setListener(Listener listener);

        /**
         * Returns true if automatic time zone detection is enabled in settings.
         */
        boolean isTimeZoneDetectionEnabled();

        /**
         * Returns true if the device has had an explicit time zone set.
         */
        boolean isTimeZoneSettingInitialized();

        /**
         * Set the device time zone from the suggestion as needed.
         */
        void setDeviceTimeZoneFromSuggestion(@NonNull PhoneTimeZoneSuggestion timeZoneSuggestion);

        /**
         * Dumps any logs held to the supplied writer.
         */
        void dumpLogs(IndentingPrintWriter ipw);

        /**
         * Dumps internal state such as field values.
         */
        void dumpState(PrintWriter pw);
    }

    static final String LOG_TAG = "TimeZoneDetectionService";
    static final boolean DBG = true;

    /**
     * The abstract score for an empty or invalid suggestion.
     *
     * Used to score suggestions where there is no zone.
     */
    @VisibleForTesting
    public static final int SCORE_NONE = 0;

    /**
     * The abstract score for a low quality suggestion.
     *
     * Used to score suggestions where:
     * The suggested zone ID is one of several possibilities, and the possibilities have different
     * offsets.
     *
     * You would have to be quite desperate to want to use this choice.
     */
    @VisibleForTesting
    public static final int SCORE_LOW = 1;

    /**
     * The abstract score for a medium quality suggestion.
     *
     * Used for:
     * The suggested zone ID is one of several possibilities but at least the possibilities have the
     * same offset. Users would get the correct time but for the wrong reason. i.e. their device may
     * switch to DST at the wrong time and (for example) their calendar events.
     */
    @VisibleForTesting
    public static final int SCORE_MEDIUM = 2;

    /**
     * The abstract score for a high quality suggestion.
     *
     * Used for:
     * The suggestion was for one zone ID and the answer was unambiguous and likely correct given
     * the info available.
     */
    @VisibleForTesting
    public static final int SCORE_HIGH = 3;

    /**
     * The abstract score for a highest quality suggestion.
     *
     * Used for:
     * Suggestions that must "win" because they constitute test or emulator zone ID.
     */
    @VisibleForTesting
    public static final int SCORE_HIGHEST = 4;

    /** The threshold at which suggestions are good enough to use to set the device's time zone. */
    @VisibleForTesting
    public static final int SCORE_USAGE_THRESHOLD = SCORE_MEDIUM;

    /** The singleton instance. */
    private static TimeZoneDetectionService sInstance;

    /**
     * Returns the singleton instance, constructing as needed with the supplied context.
     */
    public static synchronized TimeZoneDetectionService getInstance(Context context) {
        if (sInstance == null) {
            Helper timeZoneDetectionServiceHelper = new TimeZoneDetectionServiceHelperImpl(context);
            sInstance = new TimeZoneDetectionService(timeZoneDetectionServiceHelper);
        }
        return sInstance;
    }

    private static final int KEEP_SUGGESTION_HISTORY_SIZE = 30;

    /**
     * A mapping from phoneId to a linked list of time zone suggestions (the head being the latest).
     * We typically expect one or two entries in this Map: devices will have a small number
     * of telephony devices and phoneIds are assumed to be stable. The LinkedList associated with
     * the ID will not exceed {@link #KEEP_SUGGESTION_HISTORY_SIZE} in size.
     */
    @GuardedBy("this")
    private ArrayMap<Integer, LinkedList<QualifiedPhoneTimeZoneSuggestion>> mSuggestionByPhoneId =
            new ArrayMap<>();

    /**
     * The most recent best guess of time zone from all phones. Can be {@code null} to indicate
     * there would be no current suggestion.
     */
    @GuardedBy("this")
    @Nullable
    private QualifiedPhoneTimeZoneSuggestion mCurrentSuggestion;

    // Dependencies and log state.
    private final Helper mTimeZoneDetectionServiceHelper;

    @VisibleForTesting
    public TimeZoneDetectionService(Helper timeZoneDetectionServiceHelper) {
        mTimeZoneDetectionServiceHelper = timeZoneDetectionServiceHelper;
        mTimeZoneDetectionServiceHelper.setListener(enabled -> {
            if (enabled) {
                handleAutoTimeZoneEnabled();
            }
        });
    }

    /**
     * Suggests a time zone for the device, or withdraws a previous suggestion if
     * {@link PhoneTimeZoneSuggestion#getZoneId()} is {@code null}. The suggestion is scoped to a
     * specific {@link PhoneTimeZoneSuggestion#getPhoneId() phone}.
     * See {@link PhoneTimeZoneSuggestion} for an explanation of the metadata associated with a
     * suggestion. The service uses suggestions to decide whether to modify the device's time zone
     * setting and what to set it to.
     */
    public synchronized void suggestPhoneTimeZone(@NonNull PhoneTimeZoneSuggestion newSuggestion) {
        if (DBG) {
            Log.d(LOG_TAG, "suggestPhoneTimeZone: newSuggestion=" + newSuggestion);
        }
        Objects.requireNonNull(newSuggestion);

        int score = scoreSuggestion(newSuggestion);
        QualifiedPhoneTimeZoneSuggestion scoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(newSuggestion, score);

        // Record the suggestion against the correct phoneId.
        LinkedList<QualifiedPhoneTimeZoneSuggestion> suggestions =
                mSuggestionByPhoneId.get(newSuggestion.getPhoneId());
        if (suggestions == null) {
            suggestions = new LinkedList<>();
            mSuggestionByPhoneId.put(newSuggestion.getPhoneId(), suggestions);
        }
        suggestions.addFirst(scoredSuggestion);
        if (suggestions.size() > KEEP_SUGGESTION_HISTORY_SIZE) {
            suggestions.removeLast();
        }

        // Now run the competition between the phones' suggestions.
        doTimeZoneDetection();
    }

    private static int scoreSuggestion(@NonNull PhoneTimeZoneSuggestion suggestion) {
        int score;
        if (suggestion.getZoneId() == null || !isValid(suggestion)) {
            score = SCORE_NONE;
        } else if (suggestion.getMatchType() == TEST_NETWORK_OFFSET_ONLY
                || suggestion.getMatchType() == EMULATOR_ZONE_ID) {
            // Handle emulator / test cases : These suggestions should always just be used.
            score = SCORE_HIGHEST;
        } else if (suggestion.getQuality() == SINGLE_ZONE) {
            score = SCORE_HIGH;
        } else if (suggestion.getQuality() == MULTIPLE_ZONES_WITH_SAME_OFFSET) {
            // The suggestion may be wrong, but at least the offset should be correct.
            score = SCORE_MEDIUM;
        } else if (suggestion.getQuality() == MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS) {
            // The suggestion has a good chance of being wrong.
            score = SCORE_LOW;
        } else {
            throw new AssertionError();
        }
        return score;
    }

    private static boolean isValid(@NonNull PhoneTimeZoneSuggestion suggestion) {
        int quality = suggestion.getQuality();
        int matchType = suggestion.getMatchType();
        if (suggestion.getZoneId() == null) {
            return quality == QUALITY_NA && matchType == MATCH_TYPE_NA;
        } else {
            boolean qualityValid = quality == SINGLE_ZONE
                    || quality == MULTIPLE_ZONES_WITH_SAME_OFFSET
                    || quality == MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS;
            boolean matchTypeValid = matchType == NETWORK_COUNTRY_ONLY
                    || matchType == NETWORK_COUNTRY_AND_OFFSET
                    || matchType == EMULATOR_ZONE_ID
                    || matchType == TEST_NETWORK_OFFSET_ONLY;
            return qualityValid && matchTypeValid;
        }
    }

    /**
     * Finds the best available time zone suggestion from all phones. If it is high-enough quality
     * and automatic time zone detection is enabled then it will be set on the device. The outcome
     * can be that this service becomes / remains un-opinionated and nothing is set.
     */
    @GuardedBy("this")
    private void doTimeZoneDetection() {
        QualifiedPhoneTimeZoneSuggestion bestSuggestion = findBestSuggestion();
        boolean timeZoneDetectionEnabled =
                mTimeZoneDetectionServiceHelper.isTimeZoneDetectionEnabled();

        // Work out what to do with the best suggestion.
        if (bestSuggestion == null) {
            // There is no suggestion. Become un-opinionated.
            if (DBG) {
                Log.d(LOG_TAG, "doTimeZoneDetection: No good suggestion."
                        + " bestSuggestion=null"
                        + ", timeZoneDetectionEnabled=" + timeZoneDetectionEnabled);
            }
            mCurrentSuggestion = null;
            return;
        }

        // Special case handling for uninitialized devices. This should only happen once.
        String newZoneId = bestSuggestion.suggestion.getZoneId();
        if (newZoneId != null && !mTimeZoneDetectionServiceHelper.isTimeZoneSettingInitialized()) {
            Log.i(LOG_TAG, "doTimeZoneDetection: Device has no time zone set so might set the"
                    + " device to the best available suggestion."
                    + " bestSuggestion=" + bestSuggestion
                    + ", timeZoneDetectionEnabled=" + timeZoneDetectionEnabled);

            mCurrentSuggestion = bestSuggestion;
            if (timeZoneDetectionEnabled) {
                mTimeZoneDetectionServiceHelper.setDeviceTimeZoneFromSuggestion(
                        bestSuggestion.suggestion);
            }
            return;
        }

        boolean suggestionGoodEnough = bestSuggestion.score >= SCORE_USAGE_THRESHOLD;
        if (!suggestionGoodEnough) {
            if (DBG) {
                Log.d(LOG_TAG, "doTimeZoneDetection: Suggestion not good enough."
                        + " bestSuggestion=" + bestSuggestion);
            }
            mCurrentSuggestion = null;
            return;
        }

        // Paranoia: Every suggestion above the SCORE_USAGE_THRESHOLD should have a non-null time
        // zone ID.
        if (newZoneId == null) {
            Log.w(LOG_TAG, "Empty zone suggestion scored higher than expected. This is an error:"
                    + " bestSuggestion=" + bestSuggestion);
            mCurrentSuggestion = null;
            return;
        }

        // There is a good suggestion. Store the suggestion and set the device time zone if
        // settings allow.
        mCurrentSuggestion = bestSuggestion;

        // Only set the device time zone if time zone detection is enabled.
        if (!timeZoneDetectionEnabled) {
            if (DBG) {
                Log.d(LOG_TAG, "doTimeZoneDetection: Not setting the time zone because time zone"
                        + " detection is disabled."
                        + " bestSuggestion=" + bestSuggestion);
            }
            return;
        }
        mTimeZoneDetectionServiceHelper.setDeviceTimeZoneFromSuggestion(bestSuggestion.suggestion);
    }

    @GuardedBy("this")
    @Nullable
    private QualifiedPhoneTimeZoneSuggestion findBestSuggestion() {
        QualifiedPhoneTimeZoneSuggestion bestSuggestion = null;

        // Iterate over the latest QualifiedPhoneTimeZoneSuggestion objects received for each phone
        // and find the best. Note that we deliberately do not look at age: the caller can
        // rate-limit so age is not a strong indicator of confidence. Instead, the callers are
        // expected to withdraw suggestions they no longer have confidence in.
        for (int i = 0; i < mSuggestionByPhoneId.size(); i++) {
            LinkedList<QualifiedPhoneTimeZoneSuggestion> phoneSuggestions =
                    mSuggestionByPhoneId.valueAt(i);
            if (phoneSuggestions == null) {
                // Unexpected
                continue;
            }
            QualifiedPhoneTimeZoneSuggestion candidateSuggestion = phoneSuggestions.getFirst();
            if (candidateSuggestion == null) {
                // Unexpected
                continue;
            }

            if (bestSuggestion == null) {
                bestSuggestion = candidateSuggestion;
            } else if (candidateSuggestion.score > bestSuggestion.score) {
                bestSuggestion = candidateSuggestion;
            } else if (candidateSuggestion.score == bestSuggestion.score) {
                // Tie! Use the suggestion with the lowest phoneId.
                int candidatePhoneId = candidateSuggestion.suggestion.getPhoneId();
                int bestPhoneId = bestSuggestion.suggestion.getPhoneId();
                if (candidatePhoneId < bestPhoneId) {
                    bestSuggestion = candidateSuggestion;
                }
            }
        }
        return bestSuggestion;
    }

    /**
     * Returns the current best suggestion. Not intended for general use: it is used during tests
     * to check service behavior.
     */
    @VisibleForTesting
    @Nullable
    public synchronized QualifiedPhoneTimeZoneSuggestion findBestSuggestionForTests() {
        return findBestSuggestion();
    }

    private synchronized void handleAutoTimeZoneEnabled() {
        if (DBG) {
            Log.d(LOG_TAG, "handleAutoTimeEnabled() called");
        }
        // When the user enabled time zone detection, run the time zone detection and change the
        // device time zone if possible.
        doTimeZoneDetection();
    }

    /**
     * Dumps any logs held to the supplied writer.
     */
    public void dumpLogs(IndentingPrintWriter ipw) {
        mTimeZoneDetectionServiceHelper.dumpLogs(ipw);
    }

    /**
     * Dumps internal state such as field values.
     */
    public void dumpState(PrintWriter pw) {
        pw.println(" TimeZoneDetectionService.mCurrentSuggestion=" + mCurrentSuggestion);
        pw.println(" TimeZoneDetectionService.mSuggestionsByPhoneId=" + mSuggestionByPhoneId);
        mTimeZoneDetectionServiceHelper.dumpState(pw);
        pw.flush();
    }

    /**
     * A method used to inspect service state during tests. Not intended for general use.
     */
    @VisibleForTesting
    public synchronized QualifiedPhoneTimeZoneSuggestion getLatestPhoneSuggestion(int phoneId) {
        LinkedList<QualifiedPhoneTimeZoneSuggestion> suggestions =
                mSuggestionByPhoneId.get(phoneId);
        if (suggestions == null) {
            return null;
        }
        return suggestions.getFirst();
    }

    /**
     * A {@link PhoneTimeZoneSuggestion} with additional qualifying metadata.
     */
    @VisibleForTesting
    public static class QualifiedPhoneTimeZoneSuggestion {

        @VisibleForTesting
        public final PhoneTimeZoneSuggestion suggestion;

        /**
         * The score the suggestion has been given. This can be used to rank against other
         * suggestions of the same type.
         */
        @VisibleForTesting
        public final int score;

        @VisibleForTesting
        public QualifiedPhoneTimeZoneSuggestion(PhoneTimeZoneSuggestion suggestion, int score) {
            this.suggestion = suggestion;
            this.score = score;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QualifiedPhoneTimeZoneSuggestion that = (QualifiedPhoneTimeZoneSuggestion) o;
            return score == that.score
                    && suggestion.equals(that.suggestion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(score, suggestion);
        }

        @Override
        public String toString() {
            return "QualifiedPhoneTimeZoneSuggestion{"
                    + "suggestion=" + suggestion
                    + ", score=" + score
                    + '}';
        }
    }
}
