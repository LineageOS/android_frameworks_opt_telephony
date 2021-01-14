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

import android.annotation.Nullable;
import android.content.Context;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.nano.PersistAtomsProto.CarrierIdMismatch;
import com.android.internal.telephony.nano.PersistAtomsProto.IncomingSms;
import com.android.internal.telephony.nano.PersistAtomsProto.OutgoingSms;
import com.android.internal.telephony.nano.PersistAtomsProto.PersistAtoms;
import com.android.internal.telephony.nano.PersistAtomsProto.RawVoiceCallRatUsage;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallSession;
import com.android.telephony.Rlog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Stores and aggregates metrics that should not be pulled at arbitrary frequency.
 *
 * <p>NOTE: while this class checks timestamp against {@code minIntervalMillis}, it is {@link
 * MetricsCollector}'s responsibility to ensure {@code minIntervalMillis} is set correctly.
 */
public class PersistAtomsStorage {
    private static final String TAG = PersistAtomsStorage.class.getSimpleName();

    /** Name of the file where cached statistics are saved to. */
    private static final String FILENAME = "persist_atoms.pb";

    /** Maximum number of call sessions to store during pulls. */
    private static final int MAX_NUM_CALL_SESSIONS = 50;

    /**
     * Maximum number of SMS to store during pulls. Incoming messages and outgoing messages are
     * counted separately.
     */
    private static final int MAX_NUM_SMS = 25;

    /**
     * Maximum number of carrier ID mismatch events stored on the device to avoid sending
     * duplicated metrics.
     */
    private static final int MAX_CARRIER_ID_MISMATCH = 40;

    /** Stores persist atoms and persist states of the puller. */
    @VisibleForTesting protected final PersistAtoms mAtoms;

    /** Aggregates RAT duration and call count. */
    private final VoiceCallRatTracker mVoiceCallRatTracker;

    private final Context mContext;
    private static final SecureRandom sRandom = new SecureRandom();

    public PersistAtomsStorage(Context context) {
        mContext = context;
        mAtoms = loadAtomsFromFile();
        mVoiceCallRatTracker = VoiceCallRatTracker.fromProto(mAtoms.rawVoiceCallRatUsage);
    }

    /** Adds a call to the storage. */
    public synchronized void addVoiceCallSession(VoiceCallSession call) {
        mAtoms.voiceCallSession =
                insertAtRandomPlace(mAtoms.voiceCallSession, call, MAX_NUM_CALL_SESSIONS);
        saveAtomsToFile();
    }

    /** Adds RAT usages to the storage when a call session ends. */
    public synchronized void addVoiceCallRatUsage(VoiceCallRatTracker ratUsages) {
        mVoiceCallRatTracker.mergeWith(ratUsages);
        mAtoms.rawVoiceCallRatUsage = mVoiceCallRatTracker.toProto();
        saveAtomsToFile();
    }

    /** Adds an incoming SMS to the storage. */
    public synchronized void addIncomingSms(IncomingSms sms) {
        mAtoms.incomingSms = insertAtRandomPlace(mAtoms.incomingSms, sms, MAX_NUM_SMS);
        saveAtomsToFile();

        // To be removed
        Rlog.d(TAG, "Add new incoming SMS atom: " + sms.toString());
    }

    /** Adds an outgoing SMS to the storage. */
    public synchronized void addOutgoingSms(OutgoingSms sms) {
        // Update the retry id, if needed, so that it's unique and larger than all
        // previous ones. (this algorithm ignores the fact that some SMS atoms might
        // be dropped due to limit in size of the array).
        for (OutgoingSms storedSms : mAtoms.outgoingSms) {
            if (storedSms.messageId == sms.messageId
                    && storedSms.retryId >= sms.retryId) {
                sms.retryId = storedSms.retryId + 1;
            }
        }

        mAtoms.outgoingSms = insertAtRandomPlace(mAtoms.outgoingSms, sms, MAX_NUM_SMS);
        saveAtomsToFile();

        // To be removed
        Rlog.d(TAG, "Add new outgoing SMS atom: " + sms.toString());
    }

    /** Inserts a new element in a random position in an array with a maximum size. */
    private static <T> T[] insertAtRandomPlace(T[] storage, T instance, int maxLength) {
        T[] result;
        int newLength = storage.length + 1;
        if (newLength > maxLength) {
            // will evict one previous call randomly instead of making the array larger
            newLength = maxLength;
            result = storage;
        } else {
            result = Arrays.copyOf(storage, newLength);
        }
        int insertAt = 0;
        if (newLength > 1) {
            // shuffle when each element is added, or randomly replace a previous element instead
            // if maxLength is reached (entry at the last index is evicted).
            insertAt = sRandom.nextInt(newLength);
            result[newLength - 1] = result[insertAt];
        }
        result[insertAt] = instance;
        return result;
    }

    /**
     * Adds a new carrier ID mismatch event to the storage.
     *
     * @return true if the item was not present and was added to the persistent storage, false
     * otherwise.
     */
    public synchronized boolean addCarrierIdMismatch(CarrierIdMismatch carrierIdMismatch) {
        // Check if the details of the SIM cards are already present and in case return.
        for (int i = 0; i < mAtoms.carrierIdMismatch.length; i++) {
            if (mAtoms.carrierIdMismatch[i].mccMnc.equals(carrierIdMismatch.mccMnc)
                    && mAtoms.carrierIdMismatch[i].gid1.equals(carrierIdMismatch.gid1)
                    && mAtoms.carrierIdMismatch[i].spn.equals(carrierIdMismatch.spn)) {
                return false;
            }
        }
        // Add the new CarrierIdMismatch at the end of the array, so that the same atom will not be
        // sent again in future.
        if (mAtoms.carrierIdMismatch.length == MAX_CARRIER_ID_MISMATCH) {
            System.arraycopy(
                    mAtoms.carrierIdMismatch, 1,
                    mAtoms.carrierIdMismatch, 0,
                    MAX_CARRIER_ID_MISMATCH - 1);
            mAtoms.carrierIdMismatch[MAX_CARRIER_ID_MISMATCH - 1] = carrierIdMismatch;
        } else {
            int newLength = mAtoms.carrierIdMismatch.length + 1;
            mAtoms.carrierIdMismatch = Arrays.copyOf(mAtoms.carrierIdMismatch, newLength);
            mAtoms.carrierIdMismatch[newLength - 1] = carrierIdMismatch;
        }
        saveAtomsToFile();
        return true;
    }

    /**
     * Stores the version of the carrier ID matching table.
     *
     * @return true if the version is newer than last available version, false otherwise.
     */
    public synchronized boolean setCarrierIdTableVersion(int carrierIdTableVersion) {
        if (mAtoms.carrierIdTableVersion < carrierIdTableVersion) {
            mAtoms.carrierIdTableVersion = carrierIdTableVersion;
            saveAtomsToFile();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns and clears the voice call sessions if last pulled longer than {@code
     * minIntervalMillis} ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized VoiceCallSession[] getVoiceCallSessions(long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.voiceCallSessionPullTimestampMillis > minIntervalMillis) {
            mAtoms.voiceCallSessionPullTimestampMillis = getWallTimeMillis();
            VoiceCallSession[] previousCalls = mAtoms.voiceCallSession;
            mAtoms.voiceCallSession = new VoiceCallSession[0];
            saveAtomsToFile();
            return previousCalls;
        } else {
            return null;
        }
    }

    /**
     * Returns and clears the voice call RAT usages if last pulled longer than {@code
     * minIntervalMillis} ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized RawVoiceCallRatUsage[] getVoiceCallRatUsages(long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.rawVoiceCallRatUsagePullTimestampMillis
                > minIntervalMillis) {
            mAtoms.rawVoiceCallRatUsagePullTimestampMillis = getWallTimeMillis();
            RawVoiceCallRatUsage[] previousUsages = mAtoms.rawVoiceCallRatUsage;
            mVoiceCallRatTracker.clear();
            mAtoms.rawVoiceCallRatUsage = new RawVoiceCallRatUsage[0];
            saveAtomsToFile();
            return previousUsages;
        } else {
            return null;
        }
    }

    /**
     * Returns and clears the incoming SMS if last pulled longer than {@code
     * minIntervalMillis} ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized IncomingSms[] getIncomingSms(long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.incomingSmsPullTimestampMillis > minIntervalMillis) {
            mAtoms.incomingSmsPullTimestampMillis = getWallTimeMillis();
            IncomingSms[] previousIncomingSms = mAtoms.incomingSms;
            mAtoms.incomingSms = new IncomingSms[0];
            saveAtomsToFile();
            return previousIncomingSms;
        } else {
            return null;
        }
    }

    /**
     * Returns and clears the outgoing SMS if last pulled longer than {@code
     * minIntervalMillis} ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized OutgoingSms[] getOutgoingSms(long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.outgoingSmsPullTimestampMillis > minIntervalMillis) {
            mAtoms.outgoingSmsPullTimestampMillis = getWallTimeMillis();
            OutgoingSms[] previousOutgoingSms = mAtoms.outgoingSms;
            mAtoms.outgoingSms = new OutgoingSms[0];
            saveAtomsToFile();
            return previousOutgoingSms;
        } else {
            return null;
        }
    }

    /** Loads {@link PersistAtoms} from a file in private storage. */
    private PersistAtoms loadAtomsFromFile() {
        try {
            PersistAtoms atomsFromFile =
                    PersistAtoms.parseFrom(
                            Files.readAllBytes(mContext.getFileStreamPath(FILENAME).toPath()));
            // check all the fields in case of situations such as OTA or crash during saving
            if (atomsFromFile.rawVoiceCallRatUsage == null) {
                atomsFromFile.rawVoiceCallRatUsage = new RawVoiceCallRatUsage[0];
            }
            if (atomsFromFile.voiceCallSession == null) {
                atomsFromFile.voiceCallSession = new VoiceCallSession[0];
            }
            if (atomsFromFile.voiceCallSession.length > MAX_NUM_CALL_SESSIONS) {
                atomsFromFile.voiceCallSession =
                        Arrays.copyOf(atomsFromFile.voiceCallSession, MAX_NUM_CALL_SESSIONS);
            }
            if (atomsFromFile.incomingSms == null) {
                atomsFromFile.incomingSms = new IncomingSms[0];
            }
            if (atomsFromFile.incomingSms.length > MAX_NUM_SMS) {
                atomsFromFile.incomingSms =
                        Arrays.copyOf(atomsFromFile.incomingSms, MAX_NUM_SMS);
            }
            if (atomsFromFile.outgoingSms == null) {
                atomsFromFile.outgoingSms = new OutgoingSms[0];
            }
            if (atomsFromFile.outgoingSms.length > MAX_NUM_SMS) {
                atomsFromFile.outgoingSms =
                        Arrays.copyOf(atomsFromFile.outgoingSms, MAX_NUM_SMS);
            }
            if (atomsFromFile.carrierIdMismatch == null) {
                atomsFromFile.carrierIdMismatch = new CarrierIdMismatch[0];
            }
            if (atomsFromFile.carrierIdMismatch.length > MAX_CARRIER_ID_MISMATCH) {
                atomsFromFile.carrierIdMismatch =
                        Arrays.copyOf(atomsFromFile.carrierIdMismatch, MAX_CARRIER_ID_MISMATCH);
            }
            // out of caution, set timestamps to now if they are missing
            if (atomsFromFile.rawVoiceCallRatUsagePullTimestampMillis == 0L) {
                atomsFromFile.rawVoiceCallRatUsagePullTimestampMillis = getWallTimeMillis();
            }
            if (atomsFromFile.voiceCallSessionPullTimestampMillis == 0L) {
                atomsFromFile.voiceCallSessionPullTimestampMillis = getWallTimeMillis();
            }
            if (atomsFromFile.incomingSmsPullTimestampMillis == 0L) {
                atomsFromFile.incomingSmsPullTimestampMillis = getWallTimeMillis();
            }
            if (atomsFromFile.outgoingSmsPullTimestampMillis == 0L) {
                atomsFromFile.outgoingSmsPullTimestampMillis = getWallTimeMillis();
            }
            return atomsFromFile;
        } catch (IOException | NullPointerException e) {
            Rlog.e(TAG, "cannot load/parse PersistAtoms", e);
            return makeNewPersistAtoms();
        }
    }

    /** Saves a copy of {@link PersistAtoms} to a file in private storage. */
    private void saveAtomsToFile() {
        try (FileOutputStream stream = mContext.openFileOutput(FILENAME, Context.MODE_PRIVATE)) {
            stream.write(PersistAtoms.toByteArray(mAtoms));
        } catch (IOException e) {
            Rlog.e(TAG, "cannot save PersistAtoms", e);
        }
    }

    /** Returns an empty PersistAtoms with pull timestamp set to current time. */
    private PersistAtoms makeNewPersistAtoms() {
        PersistAtoms atoms = new PersistAtoms();
        // allow pulling only after some time so data are sufficiently aggregated
        long currentTime = getWallTimeMillis();
        atoms.rawVoiceCallRatUsagePullTimestampMillis = currentTime;
        atoms.voiceCallSessionPullTimestampMillis = currentTime;
        atoms.incomingSmsPullTimestampMillis = currentTime;
        atoms.outgoingSmsPullTimestampMillis = currentTime;
        atoms.carrierIdTableVersion = TelephonyManager.UNKNOWN_CARRIER_ID_LIST_VERSION;
        Rlog.d(TAG, "created new PersistAtoms");
        return atoms;
    }

    @VisibleForTesting
    protected long getWallTimeMillis() {
        // Epoch time in UTC, preserved across reboots, but can be adjusted e.g. by the user or NTP
        return System.currentTimeMillis();
    }
}
