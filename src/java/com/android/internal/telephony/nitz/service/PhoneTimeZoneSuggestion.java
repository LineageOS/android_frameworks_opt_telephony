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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A suggested time zone from a Phone-based signal, e.g. from MCC and NITZ information.
 */
public final class PhoneTimeZoneSuggestion implements Parcelable {

    public static final Creator<PhoneTimeZoneSuggestion> CREATOR =
            new Creator<PhoneTimeZoneSuggestion>() {
                public PhoneTimeZoneSuggestion createFromParcel(Parcel in) {
                    return PhoneTimeZoneSuggestion.createFromParcel(in);
                }

                public PhoneTimeZoneSuggestion[] newArray(int size) {
                    return new PhoneTimeZoneSuggestion[size];
                }
            };

    /**
     * Creates an empty time zone suggestion, i.e. one that will cancel previous suggestions with
     * the same {@code phoneId}.
     */
    @NonNull
    public static PhoneTimeZoneSuggestion createEmptySuggestion(
            int phoneId, @NonNull String debugInfo) {
        PhoneTimeZoneSuggestion timeZoneSuggestion = new PhoneTimeZoneSuggestion(phoneId);
        timeZoneSuggestion.addDebugInfo(debugInfo);
        return timeZoneSuggestion;
    }

    @IntDef({ MATCH_TYPE_NA, NETWORK_COUNTRY_ONLY, NETWORK_COUNTRY_AND_OFFSET, EMULATOR_ZONE_ID,
            TEST_NETWORK_OFFSET_ONLY })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MatchType {}

    /** Used when match type is not applicable. */
    public static final int MATCH_TYPE_NA = 0;

    /**
     * Only the network country is known.
     */
    public static final int NETWORK_COUNTRY_ONLY = 2;

    /**
     * Both the network county and offset were known.
     */
    public static final int NETWORK_COUNTRY_AND_OFFSET = 3;

    /**
     * The device is running in an emulator and an NITZ signal was simulated containing an
     * Android extension with an explicit Olson ID.
     */
    public static final int EMULATOR_ZONE_ID = 4;

    /**
     * The phone is most likely running in a test network not associated with a country (this is
     * distinct from the country just not being known yet).
     * Historically, Android has just picked an arbitrary time zone with the correct offset when
     * on a test network.
     */
    public static final int TEST_NETWORK_OFFSET_ONLY = 5;

    @IntDef({ QUALITY_NA, SINGLE_ZONE, MULTIPLE_ZONES_WITH_SAME_OFFSET,
            MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Quality {}

    /** Used when quality is not applicable. */
    public static final int QUALITY_NA = 0;

    /** There is only one answer */
    public static final int SINGLE_ZONE = 1;

    /**
     * There are multiple answers, but they all shared the same offset / DST state at the time
     * the suggestion was created. i.e. it might be the wrong zone but the user won't notice
     * immediately if it is wrong.
     */
    public static final int MULTIPLE_ZONES_WITH_SAME_OFFSET = 2;

    /**
     * There are multiple answers with different offsets. The one given is just one possible.
     */
    public static final int MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS = 3;

    /**
     * The ID of the phone this suggestion is associated with. For multiple-sim devices this
     * helps to establish origin so filtering / stickiness can be implemented.
     */
    private final int mPhoneId;

    /**
     * The suggestion. {@code null} means there is no current suggestion and any previous suggestion
     * should be forgotten.
     */
    private String mZoneId;

    /**
     * The type of "match" used to establish the time zone.
     */
    @MatchType
    private int mMatchType;

    /**
     * A measure of the quality of the time zone suggestion, i.e. how confident one could be in
     * it.
     */
    @Quality
    private int mQuality;

    /**
     * Free-form debug information about how the signal was derived. Used for debug only,
     * intentionally not used in equals(), etc.
     */
    private List<String> mDebugInfo;

    public PhoneTimeZoneSuggestion(int phoneId) {
        this.mPhoneId = phoneId;
    }

    @SuppressWarnings("unchecked")
    private static PhoneTimeZoneSuggestion createFromParcel(Parcel in) {
        int phoneId = in.readInt();
        PhoneTimeZoneSuggestion phoneTimeZoneSuggestion = new PhoneTimeZoneSuggestion(phoneId);
        phoneTimeZoneSuggestion.mZoneId = in.readString();
        phoneTimeZoneSuggestion.mMatchType = in.readInt();
        phoneTimeZoneSuggestion.mQuality = in.readInt();
        phoneTimeZoneSuggestion.mDebugInfo =
                (List<String>) in.readArrayList(PhoneTimeZoneSuggestion.class.getClassLoader());
        return phoneTimeZoneSuggestion;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPhoneId);
        dest.writeString(mZoneId);
        dest.writeInt(mMatchType);
        dest.writeInt(mQuality);
        dest.writeList(mDebugInfo);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getPhoneId() {
        return mPhoneId;
    }

    @Nullable
    public String getZoneId() {
        return mZoneId;
    }

    public void setZoneId(@Nullable String zoneId) {
        this.mZoneId = zoneId;
    }


    @MatchType
    public int getMatchType() {
        return mMatchType;
    }

    public void setMatchType(@MatchType int matchType) {
        this.mMatchType = matchType;
    }
    @Quality
    public int getQuality() {
        return mQuality;
    }

    public void setQuality(@Quality int quality) {
        this.mQuality = quality;
    }

    public List<String> getDebugInfo() {
        return Collections.unmodifiableList(mDebugInfo);
    }

    /**
     * Associates information with the instance that can be useful for debugging / logging. The
     * information is present in {@link #toString()} but is not considered for
     * {@link #equals(Object)} and {@link #hashCode()}.
     */
    public void addDebugInfo(String... debugInfos) {
        if (mDebugInfo == null) {
            mDebugInfo = new ArrayList<>();
        }
        mDebugInfo.addAll(Arrays.asList(debugInfos));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PhoneTimeZoneSuggestion that = (PhoneTimeZoneSuggestion) o;
        return mPhoneId == that.mPhoneId
                && mMatchType == that.mMatchType
                && mQuality == that.mQuality
                && Objects.equals(mZoneId, that.mZoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPhoneId, mZoneId, mMatchType, mQuality);
    }

    @Override
    public String toString() {
        return "PhoneTimeZoneSuggestion{"
                + "mPhoneId=" + mPhoneId
                + ", mZoneId='" + mZoneId + '\''
                + ", mMatchType=" + mMatchType
                + ", mQuality=" + mQuality
                + ", mDebugInfo=" + mDebugInfo
                + '}';
    }
}
