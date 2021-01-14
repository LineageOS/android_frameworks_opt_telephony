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
import com.android.internal.telephony.nano.PersistAtomsProto.CellularDataServiceSwitch;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularServiceState;
import com.android.internal.telephony.nano.PersistAtomsProto.DataCallSession;
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
import java.util.stream.IntStream;

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

    /** Maximum number of call sessions to store between pulls. */
    private static final int MAX_NUM_CALL_SESSIONS = 50;

    /**
     * Maximum number of SMS to store between pulls. Incoming messages and outgoing messages are
     * counted separately.
     */
    private static final int MAX_NUM_SMS = 25;

    /**
     * Maximum number of carrier ID mismatch events stored on the device to avoid sending duplicated
     * metrics.
     */
    private static final int MAX_CARRIER_ID_MISMATCH = 40;

    /** Maximum number of data call sessions to store during pulls. */
    private static final int MAX_NUM_DATA_CALL_SESSIONS = 15;

    /** Maximum number of service states to store between pulls. */
    private static final int MAX_NUM_CELLULAR_SERVICE_STATES = 50;

    /** Maximum number of data service switches to store between pulls. */
    private static final int MAX_NUM_CELLULAR_DATA_SERVICE_SWITCHES = 50;

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
            if (storedSms.messageId == sms.messageId && storedSms.retryId >= sms.retryId) {
                sms.retryId = storedSms.retryId + 1;
            }
        }

        mAtoms.outgoingSms = insertAtRandomPlace(mAtoms.outgoingSms, sms, MAX_NUM_SMS);
        saveAtomsToFile();

        // To be removed
        Rlog.d(TAG, "Add new outgoing SMS atom: " + sms.toString());
    }

    /** Adds a service state to the storage, together with data service switch if any. */
    public synchronized void addCellularServiceStateAndCellularDataServiceSwitch(
            CellularServiceState state, @Nullable CellularDataServiceSwitch serviceSwitch) {
        CellularServiceState existingState = find(state);
        if (existingState != null) {
            existingState.totalTimeMillis += state.totalTimeMillis;
            existingState.lastUsedMillis = getWallTimeMillis();
        } else {
            state.lastUsedMillis = getWallTimeMillis();
            mAtoms.cellularServiceState =
                    insertAtRandomPlace(
                            mAtoms.cellularServiceState, state, MAX_NUM_CELLULAR_SERVICE_STATES);
        }

        if (serviceSwitch != null) {
            CellularDataServiceSwitch existingSwitch = find(serviceSwitch);
            if (existingSwitch != null) {
                existingSwitch.switchCount += serviceSwitch.switchCount;
                existingSwitch.lastUsedMillis = getWallTimeMillis();
            } else {
                serviceSwitch.lastUsedMillis = getWallTimeMillis();
                mAtoms.cellularDataServiceSwitch =
                        insertAtRandomPlace(
                                mAtoms.cellularDataServiceSwitch,
                                serviceSwitch,
                                MAX_NUM_CELLULAR_DATA_SERVICE_SWITCHES);
            }
        }

        saveAtomsToFile();
    }

    /** Adds a data call session to the storage. */
    public synchronized void addDataCallSession(DataCallSession dataCall) {
        mAtoms.dataCallSession =
                insertAtRandomPlace(mAtoms.dataCallSession, dataCall, MAX_NUM_DATA_CALL_SESSIONS);
        saveAtomsToFile();
    }

    /**
     * Adds a new carrier ID mismatch event to the storage.
     *
     * @return true if the item was not present and was added to the persistent storage, false
     *     otherwise.
     */
    public synchronized boolean addCarrierIdMismatch(CarrierIdMismatch carrierIdMismatch) {
        // Check if the details of the SIM cards are already present and in case return.
        if (find(carrierIdMismatch) != null) {
            return false;
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
     * Returns and clears the incoming SMS if last pulled longer than {@code minIntervalMillis} ago,
     * otherwise returns {@code null}.
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
     * Returns and clears the outgoing SMS if last pulled longer than {@code minIntervalMillis} ago,
     * otherwise returns {@code null}.
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

    /**
     * Returns and clears the data call session if last pulled longer than {@code minIntervalMillis}
     * ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized DataCallSession[] getDataCallSessions(long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.dataCallSessionPullTimestampMillis > minIntervalMillis) {
            mAtoms.dataCallSessionPullTimestampMillis = getWallTimeMillis();
            DataCallSession[] previousDataCallSession = mAtoms.dataCallSession;
            mAtoms.dataCallSession = new DataCallSession[0];
            saveAtomsToFile();
            return previousDataCallSession;
        } else {
            return null;
        }
    }

    /**
     * Returns and clears the service state durations if last pulled longer than {@code
     * minIntervalMillis} ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized CellularServiceState[] getCellularServiceStates(long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.cellularServiceStatePullTimestampMillis
                > minIntervalMillis) {
            mAtoms.cellularServiceStatePullTimestampMillis = getWallTimeMillis();
            CellularServiceState[] previousStates = mAtoms.cellularServiceState;
            Arrays.stream(previousStates).forEach(state -> state.lastUsedMillis = 0L);
            mAtoms.cellularServiceState = new CellularServiceState[0];
            saveAtomsToFile();
            return previousStates;
        } else {
            return null;
        }
    }

    /**
     * Returns and clears the service state durations if last pulled longer than {@code
     * minIntervalMillis} ago, otherwise returns {@code null}.
     */
    @Nullable
    public synchronized CellularDataServiceSwitch[] getCellularDataServiceSwitches(
            long minIntervalMillis) {
        if (getWallTimeMillis() - mAtoms.cellularDataServiceSwitchPullTimestampMillis
                > minIntervalMillis) {
            mAtoms.cellularDataServiceSwitchPullTimestampMillis = getWallTimeMillis();
            CellularDataServiceSwitch[] previousSwitches = mAtoms.cellularDataServiceSwitch;
            Arrays.stream(previousSwitches)
                    .forEach(serviceSwitch -> serviceSwitch.lastUsedMillis = 0L);
            mAtoms.cellularDataServiceSwitch = new CellularDataServiceSwitch[0];
            saveAtomsToFile();
            return previousSwitches;
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
                atomsFromFile.incomingSms = Arrays.copyOf(atomsFromFile.incomingSms, MAX_NUM_SMS);
            }
            if (atomsFromFile.outgoingSms == null) {
                atomsFromFile.outgoingSms = new OutgoingSms[0];
            }
            if (atomsFromFile.outgoingSms.length > MAX_NUM_SMS) {
                atomsFromFile.outgoingSms = Arrays.copyOf(atomsFromFile.outgoingSms, MAX_NUM_SMS);
            }
            if (atomsFromFile.carrierIdMismatch == null) {
                atomsFromFile.carrierIdMismatch = new CarrierIdMismatch[0];
            }
            if (atomsFromFile.carrierIdMismatch.length > MAX_CARRIER_ID_MISMATCH) {
                atomsFromFile.carrierIdMismatch =
                        Arrays.copyOf(atomsFromFile.carrierIdMismatch, MAX_CARRIER_ID_MISMATCH);
            }
            if (atomsFromFile.dataCallSession == null) {
                atomsFromFile.dataCallSession = new DataCallSession[0];
            }
            if (atomsFromFile.dataCallSession.length > MAX_NUM_DATA_CALL_SESSIONS) {
                atomsFromFile.dataCallSession =
                        Arrays.copyOf(atomsFromFile.dataCallSession, MAX_NUM_DATA_CALL_SESSIONS);
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
            if (atomsFromFile.dataCallSessionPullTimestampMillis == 0L) {
                atomsFromFile.dataCallSessionPullTimestampMillis = getWallTimeMillis();
            }
            if (atomsFromFile.cellularServiceStatePullTimestampMillis == 0L) {
                atomsFromFile.cellularServiceStatePullTimestampMillis = getWallTimeMillis();
            }
            if (atomsFromFile.cellularDataServiceSwitchPullTimestampMillis == 0L) {
                atomsFromFile.cellularDataServiceSwitchPullTimestampMillis = getWallTimeMillis();
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

    /**
     * Returns the service state that has the same dimension values with the given one, or {@code
     * null} if it does not exist.
     */
    private @Nullable CellularServiceState find(CellularServiceState key) {
        for (CellularServiceState state : mAtoms.cellularServiceState) {
            if (state.voiceRat == key.voiceRat
                    && state.dataRat == key.dataRat
                    && state.voiceRoamingType == key.voiceRoamingType
                    && state.dataRoamingType == key.dataRoamingType
                    && state.isEndc == key.isEndc
                    && state.simSlotIndex == key.simSlotIndex
                    && state.isMultiSim == key.isMultiSim
                    && state.carrierId == key.carrierId) {
                return state;
            }
        }
        return null;
    }

    /**
     * Returns the data service switch that has the same dimension values with the given one, or
     * {@code null} if it does not exist.
     */
    private @Nullable CellularDataServiceSwitch find(CellularDataServiceSwitch key) {
        for (CellularDataServiceSwitch serviceSwitch : mAtoms.cellularDataServiceSwitch) {
            if (serviceSwitch.ratFrom == key.ratFrom
                    && serviceSwitch.ratTo == key.ratTo
                    && serviceSwitch.simSlotIndex == key.simSlotIndex
                    && serviceSwitch.isMultiSim == key.isMultiSim
                    && serviceSwitch.carrierId == key.carrierId) {
                return serviceSwitch;
            }
        }
        return null;
    }

    /**
     * Returns the carrier ID mismatch event that has the same dimension values with the given one,
     * or {@code null} if it does not exist.
     */
    private @Nullable CarrierIdMismatch find(CarrierIdMismatch key) {
        for (CarrierIdMismatch mismatch : mAtoms.carrierIdMismatch) {
            if (mismatch.mccMnc.equals(key.mccMnc)
                    && mismatch.gid1.equals(key.gid1)
                    && mismatch.spn.equals(key.spn)) {
                return mismatch;
            }
        }
        return null;
    }

    /**
     * Inserts a new element in a random position in an array with a maximum size, replacing the
     * least recent item if possible.
     */
    private static <T> T[] insertAtRandomPlace(T[] storage, T instance, int maxLength) {
        final int newLength = storage.length + 1;
        final boolean arrayFull = (newLength > maxLength);
        T[] result = Arrays.copyOf(storage, arrayFull ? maxLength : newLength);
        if (newLength == 1) {
            result[0] = instance;
        } else if (arrayFull) {
            result[findItemToEvict(storage)] = instance;
        } else {
            // insert at random place (by moving the item at the random place to the end)
            int insertAt = sRandom.nextInt(newLength);
            result[newLength - 1] = result[insertAt];
            result[insertAt] = instance;
        }
        return result;
    }

    /** Returns index of the item suitable for eviction when the array is full. */
    private static <T> int findItemToEvict(T[] array) {
        if (array instanceof CellularServiceState[]) {
            CellularServiceState[] arr = (CellularServiceState[]) array;
            return IntStream.range(0, arr.length)
                    .reduce((i, j) -> arr[i].lastUsedMillis < arr[j].lastUsedMillis ? i : j)
                    .getAsInt();
        }

        if (array instanceof CellularDataServiceSwitch[]) {
            CellularDataServiceSwitch[] arr = (CellularDataServiceSwitch[]) array;
            return IntStream.range(0, arr.length)
                    .reduce((i, j) -> arr[i].lastUsedMillis < arr[j].lastUsedMillis ? i : j)
                    .getAsInt();
        }

        return sRandom.nextInt(array.length);
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
        atoms.dataCallSessionPullTimestampMillis = currentTime;
        atoms.cellularServiceStatePullTimestampMillis = currentTime;
        atoms.cellularDataServiceSwitchPullTimestampMillis = currentTime;
        Rlog.d(TAG, "created new PersistAtoms");
        return atoms;
    }

    @VisibleForTesting
    protected long getWallTimeMillis() {
        // Epoch time in UTC, preserved across reboots, but can be adjusted e.g. by the user or NTP
        return System.currentTimeMillis();
    }
}
