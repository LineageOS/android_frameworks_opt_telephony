/*
 * Copyright (C) 2011 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Clients can enable reception of SMS-CB messages for specific ranges of
 * message identifiers (channels). This class keeps track of the currently
 * enabled message identifiers and calls abstract methods to update the
 * radio when the range of enabled message identifiers changes.
 *
 * An update is a call to {@link #startUpdate} followed by zero or more
 * calls to {@link #addRange} followed by a call to {@link #finishUpdate}.
 * Calls to {@link #enableRange} and {@link #disableRange} will perform
 * an incremental update operation if the enabled ranges have changed.
 * A full update operation (i.e. after a radio reset) can be performed
 * by a call to {@link #updateRanges}.
 *
 * Clients are identified by String (the name associated with the User ID
 * of the caller) so that a call to remove a range can be mapped to the
 * client that enabled that range (or else rejected).
 */
public abstract class IntRangeManager {

    /**
     * Initial capacity for IntRange clients array list. There will be
     * few cell broadcast listeners on a typical device, so this can be small.
     */
    private static final int INITIAL_CLIENTS_ARRAY_SIZE = 4;

    /**
     * One or more clients forming the continuous range [startId, endId].
     * <p>When a client is added, the IntRange may merge with one or more
     * adjacent IntRanges to form a single combined IntRange.
     * <p>When a client is removed, the IntRange may divide into several
     * non-contiguous IntRanges.
     */
    private class IntRange {
        int mStartId;
        int mEndId;
        // sorted by earliest start id
        final ArrayList<ClientRange> mClients;

        /**
         * Create a new IntRange with a single client.
         * @param startId the first id included in the range
         * @param endId the last id included in the range
         * @param client the client requesting the enabled range
         */
        IntRange(int startId, int endId, String client) {
            mStartId = startId;
            mEndId = endId;
            mClients = new ArrayList<ClientRange>(INITIAL_CLIENTS_ARRAY_SIZE);
            mClients.add(new ClientRange(startId, endId, client));
        }

        /**
         * Create a new IntRange for an existing ClientRange.
         * @param clientRange the initial ClientRange to add
         */
        IntRange(ClientRange clientRange) {
            mStartId = clientRange.mStartId;
            mEndId = clientRange.mEndId;
            mClients = new ArrayList<ClientRange>(INITIAL_CLIENTS_ARRAY_SIZE);
            mClients.add(clientRange);
        }

        /**
         * Create a new IntRange from an existing IntRange. This is used for
         * removing a ClientRange, because new IntRanges may need to be created
         * for any gaps that open up after the ClientRange is removed. A copy
         * is made of the elements of the original IntRange preceding the element
         * that is being removed. The following elements will be added to this
         * IntRange or to a new IntRange when a gap is found.
         * @param intRange the original IntRange to copy elements from
         * @param numElements the number of elements to copy from the original
         */
        IntRange(IntRange intRange, int numElements) {
            mStartId = intRange.mStartId;
            mEndId = intRange.mEndId;
            mClients = new ArrayList<ClientRange>(intRange.mClients.size());
            for (int i=0; i < numElements; i++) {
                mClients.add(intRange.mClients.get(i));
            }
        }

        /**
         * Insert new ClientRange in order by start id.
         * <p>If the new ClientRange is known to be sorted before or after the
         * existing ClientRanges, or at a particular index, it can be added
         * to the clients array list directly, instead of via this method.
         * <p>Note that this can be changed from linear to binary search if the
         * number of clients grows large enough that it would make a difference.
         * @param range the new ClientRange to insert
         */
        void insert(ClientRange range) {
            int len = mClients.size();
            for (int i=0; i < len; i++) {
                ClientRange nextRange = mClients.get(i);
                if (range.mStartId <= nextRange.mStartId) {
                    // ignore duplicate ranges from the same client
                    if (!range.equals(nextRange)) {
                        mClients.add(i, range);
                    }
                    return;
                }
            }
            mClients.add(range);    // append to end of list
        }
    }

    /**
     * The message id range for a single client.
     */
    private class ClientRange {
        final int mStartId;
        final int mEndId;
        final String mClient;

        ClientRange(int startId, int endId, String client) {
            mStartId = startId;
            mEndId = endId;
            mClient = client;
        }

        @Override
        public boolean equals(Object o) {
            if (o != null && o instanceof ClientRange) {
                ClientRange other = (ClientRange) o;
                return mStartId == other.mStartId &&
                        mEndId == other.mEndId &&
                        mClient.equals(other.mClient);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return (mStartId * 31 + mEndId) * 31 + mClient.hashCode();
        }
    }

    /**
     * List of integer ranges, one per client, sorted by start id.
     */
    private ArrayList<IntRange> mRanges = new ArrayList<IntRange>();

    protected IntRangeManager() {}

    /**
     * Enable a range for the specified client and update ranges
     * if necessary. If {@link #finishUpdate} returns failure,
     * false is returned and the range is not added.
     *
     * @param startId the first id included in the range
     * @param endId the last id included in the range
     * @param client the client requesting the enabled range
     * @return true if successful, false otherwise
     */
    public synchronized boolean enableRange(int startId, int endId, String client) {
        int len = mRanges.size();

        // empty range list: add the initial IntRange
        if (len == 0) {
            if (tryAddSingleRange(startId, endId, true)) {
                mRanges.add(new IntRange(startId, endId, client));
                return true;
            } else {
                return false;   // failed to update radio
            }
        }

        for (int startIndex = 0; startIndex < len; startIndex++) {
            IntRange range = mRanges.get(startIndex);
            if (startId < range.mStartId) {
                // test if new range completely precedes this range
                // note that [1, 4] and [5, 6] coalesce to [1, 6]
                if ((endId + 1) < range.mStartId) {
                    // insert new int range before previous first range
                    if (tryAddSingleRange(startId, endId, true)) {
                        mRanges.add(startIndex, new IntRange(startId, endId, client));
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                } else if (endId <= range.mEndId) {
                    // extend the start of this range
                    if (tryAddSingleRange(startId, range.mStartId - 1, true)) {
                        range.mStartId = startId;
                        range.mClients.add(0, new ClientRange(startId, endId, client));
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                } else {
                    // find last range that can coalesce into the new combined range
                    for (int endIndex = startIndex+1; endIndex < len; endIndex++) {
                        IntRange endRange = mRanges.get(endIndex);
                        if ((endId + 1) < endRange.mStartId) {
                            // try to add entire new range
                            if (tryAddSingleRange(startId, endId, true)) {
                                range.mStartId = startId;
                                range.mEndId = endId;
                                // insert new ClientRange before existing ranges
                                range.mClients.add(0, new ClientRange(startId, endId, client));
                                // coalesce range with following ranges up to endIndex-1
                                // remove each range after adding its elements, so the index
                                // of the next range to join is always startIndex+1.
                                // i is the index if no elements were removed: we only care
                                // about the number of loop iterations, not the value of i.
                                int joinIndex = startIndex + 1;
                                for (int i = joinIndex; i < endIndex; i++) {
                                    IntRange joinRange = mRanges.get(joinIndex);
                                    range.mClients.addAll(joinRange.mClients);
                                    mRanges.remove(joinRange);
                                }
                                return true;
                            } else {
                                return false;   // failed to update radio
                            }
                        } else if (endId <= endRange.mEndId) {
                            // add range from start id to start of last overlapping range,
                            // values from endRange.startId to endId are already enabled
                            if (tryAddSingleRange(startId, endRange.mStartId - 1, true)) {
                                range.mStartId = startId;
                                range.mEndId = endRange.mEndId;
                                // insert new ClientRange before existing ranges
                                range.mClients.add(0, new ClientRange(startId, endId, client));
                                // coalesce range with following ranges up to endIndex
                                // remove each range after adding its elements, so the index
                                // of the next range to join is always startIndex+1.
                                // i is the index if no elements were removed: we only care
                                // about the number of loop iterations, not the value of i.
                                int joinIndex = startIndex + 1;
                                for (int i = joinIndex; i <= endIndex; i++) {
                                    IntRange joinRange = mRanges.get(joinIndex);
                                    range.mClients.addAll(joinRange.mClients);
                                    mRanges.remove(joinRange);
                                }
                                return true;
                            } else {
                                return false;   // failed to update radio
                            }
                        }
                    }

                    // endId extends past all existing IntRanges: combine them all together
                    if (tryAddSingleRange(startId, endId, true)) {
                        range.mStartId = startId;
                        range.mEndId = endId;
                        // insert new ClientRange before existing ranges
                        range.mClients.add(0, new ClientRange(startId, endId, client));
                        // coalesce range with following ranges up to len-1
                        // remove each range after adding its elements, so the index
                        // of the next range to join is always startIndex+1.
                        // i is the index if no elements were removed: we only care
                        // about the number of loop iterations, not the value of i.
                        int joinIndex = startIndex + 1;
                        for (int i = joinIndex; i < len; i++) {
                            IntRange joinRange = mRanges.get(joinIndex);
                            range.mClients.addAll(joinRange.mClients);
                            mRanges.remove(joinRange);
                        }
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                }
            } else if ((startId + 1) <= range.mEndId) {
                if (endId <= range.mEndId) {
                    // completely contained in existing range; no radio changes
                    range.insert(new ClientRange(startId, endId, client));
                    return true;
                } else {
                    // find last range that can coalesce into the new combined range
                    int endIndex = startIndex;
                    for (int testIndex = startIndex+1; testIndex < len; testIndex++) {
                        IntRange testRange = mRanges.get(testIndex);
                        if ((endId + 1) < testRange.mStartId) {
                            break;
                        } else {
                            endIndex = testIndex;
                        }
                    }
                    // no adjacent IntRanges to combine
                    if (endIndex == startIndex) {
                        // add range from range.endId+1 to endId,
                        // values from startId to range.endId are already enabled
                        if (tryAddSingleRange(range.mEndId + 1, endId, true)) {
                            range.mEndId = endId;
                            range.insert(new ClientRange(startId, endId, client));
                            return true;
                        } else {
                            return false;   // failed to update radio
                        }
                    }
                    // get last range to coalesce into start range
                    IntRange endRange = mRanges.get(endIndex);
                    // Values from startId to range.endId have already been enabled.
                    // if endId > endRange.endId, then enable range from range.endId+1 to endId,
                    // else enable range from range.endId+1 to endRange.startId-1, because
                    // values from endRange.startId to endId have already been added.
                    int newRangeEndId = (endId <= endRange.mEndId) ? endRange.mStartId - 1 : endId;
                    if (tryAddSingleRange(range.mEndId + 1, newRangeEndId, true)) {
                        range.mEndId = endId;
                        // insert new ClientRange in place
                        range.insert(new ClientRange(startId, endId, client));
                        // coalesce range with following ranges up to endIndex-1
                        // remove each range after adding its elements, so the index
                        // of the next range to join is always startIndex+1 (joinIndex).
                        // i is the index if no elements had been removed: we only care
                        // about the number of loop iterations, not the value of i.
                        int joinIndex = startIndex + 1;
                        for (int i = joinIndex; i < endIndex; i++) {
                            IntRange joinRange = mRanges.get(joinIndex);
                            range.mClients.addAll(joinRange.mClients);
                            mRanges.remove(joinRange);
                        }
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                }
            }
        }

        // append new range after existing IntRanges
        if (tryAddSingleRange(startId, endId, true)) {
            mRanges.add(new IntRange(startId, endId, client));
            return true;
        } else {
            return false;   // failed to update radio
        }
    }

    /**
     * Disable a range for the specified client and update ranges
     * if necessary. If {@link #finishUpdate} returns failure,
     * false is returned and the range is not removed.
     *
     * @param startId the first id included in the range
     * @param endId the last id included in the range
     * @param client the client requesting to disable the range
     * @return true if successful, false otherwise
     */
    public synchronized boolean disableRange(int startId, int endId, String client) {
        int len = mRanges.size();

        for (int i=0; i < len; i++) {
            IntRange range = mRanges.get(i);
            if (startId < range.mStartId) {
                return false;   // not found
            } else if (endId <= range.mEndId) {
                // found the IntRange that encloses the client range, if any
                // search for it in the clients list
                ArrayList<ClientRange> clients = range.mClients;

                // handle common case of IntRange containing one ClientRange
                int crLength = clients.size();
                if (crLength == 1) {
                    ClientRange cr = clients.get(0);
                    if (cr.mStartId == startId && cr.mEndId == endId && cr.mClient.equals(client)) {
                        // disable range in radio then remove the entire IntRange
                        if (tryAddSingleRange(startId, endId, false)) {
                            mRanges.remove(i);
                            return true;
                        } else {
                            return false;   // failed to update radio
                        }
                    } else {
                        return false;   // not found
                    }
                }

                // several ClientRanges: remove one, potentially splitting into many IntRanges.
                // Save the original start and end id for the original IntRange
                // in case the radio update fails and we have to revert it. If the
                // update succeeds, we remove the client range and insert the new IntRanges.
                int largestEndId = Integer.MIN_VALUE;  // largest end identifier found
                boolean updateStarted = false;

                for (int crIndex=0; crIndex < crLength; crIndex++) {
                    ClientRange cr = clients.get(crIndex);
                    if (cr.mStartId == startId && cr.mEndId == endId && cr.mClient.equals(client)) {
                        // found the ClientRange to remove, check if it's the last in the list
                        if (crIndex == crLength - 1) {
                            if (range.mEndId == largestEndId) {
                                // no channels to remove from radio; return success
                                clients.remove(crIndex);
                                return true;
                            } else {
                                // disable the channels at the end and lower the end id
                                if (tryAddSingleRange(largestEndId + 1, range.mEndId, false)) {
                                    clients.remove(crIndex);
                                    range.mEndId = largestEndId;
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                        }

                        // copy the IntRange so that we can remove elements and modify the
                        // start and end id's in the copy, leaving the original unmodified
                        // until after the radio update succeeds
                        IntRange rangeCopy = new IntRange(range, crIndex);

                        if (crIndex == 0) {
                            // removing the first ClientRange, so we may need to increase
                            // the start id of the IntRange.
                            // We know there are at least two ClientRanges in the list,
                            // so clients.get(1) should always succeed.
                            int nextStartId = clients.get(1).mStartId;
                            if (nextStartId != range.mStartId) {
                                startUpdate();
                                updateStarted = true;
                                addRange(range.mStartId, nextStartId - 1, false);
                                rangeCopy.mStartId = nextStartId;
                            }
                            // init largestEndId
                            largestEndId = clients.get(1).mEndId;
                        }

                        // go through remaining ClientRanges, creating new IntRanges when
                        // there is a gap in the sequence. After radio update succeeds,
                        // remove the original IntRange and append newRanges to mRanges.
                        // Otherwise, leave the original IntRange in mRanges and return false.
                        ArrayList<IntRange> newRanges = new ArrayList<IntRange>();

                        IntRange currentRange = rangeCopy;
                        for (int nextIndex = crIndex + 1; nextIndex < crLength; nextIndex++) {
                            ClientRange nextCr = clients.get(nextIndex);
                            if (nextCr.mStartId > largestEndId + 1) {
                                if (!updateStarted) {
                                    startUpdate();
                                    updateStarted = true;
                                }
                                addRange(largestEndId + 1, nextCr.mStartId - 1, false);
                                currentRange.mEndId = largestEndId;
                                newRanges.add(currentRange);
                                currentRange = new IntRange(nextCr);
                            } else {
                                currentRange.mClients.add(nextCr);
                            }
                            if (nextCr.mEndId > largestEndId) {
                                largestEndId = nextCr.mEndId;
                            }
                        }

                        // remove any channels between largestEndId and endId
                        if (largestEndId < endId) {
                            if (!updateStarted) {
                                startUpdate();
                                updateStarted = true;
                            }
                            addRange(largestEndId + 1, endId, false);
                            currentRange.mEndId = largestEndId;
                        }
                        newRanges.add(currentRange);

                        if (updateStarted && !finishUpdate()) {
                            return false;   // failed to update radio
                        }

                        // replace the original IntRange with newRanges
                        mRanges.remove(i);
                        mRanges.addAll(i, newRanges);
                        return true;
                    } else {
                        // not the ClientRange to remove; save highest end ID seen so far
                        if (cr.mEndId > largestEndId) {
                            largestEndId = cr.mEndId;
                        }
                    }
                }
            }
        }

        return false;   // not found
    }

    /**
     * Perform a complete update operation (enable all ranges). Useful
     * after a radio reset. Calls {@link #startUpdate}, followed by zero or
     * more calls to {@link #addRange}, followed by {@link #finishUpdate}.
     * @return true if successful, false otherwise
     */
    public boolean updateRanges() {
        startUpdate();
        Iterator<IntRange> iterator = mRanges.iterator();
        if (iterator.hasNext()) {
            IntRange range = iterator.next();
            int start = range.mStartId;
            int end = range.mEndId;
            // accumulate ranges of [startId, endId]
            while (iterator.hasNext()) {
                IntRange nextNode = iterator.next();
                // [startIdA, endIdA], [endIdA + 1, endIdB] -> [startIdA, endIdB]
                if (nextNode.mStartId <= (end + 1)) {
                    if (nextNode.mEndId > end) {
                        end = nextNode.mEndId;
                    }
                } else {
                    addRange(start, end, true);
                    start = nextNode.mStartId;
                    end = nextNode.mEndId;
                }
            }
            // add final range
            addRange(start, end, true);
        }
        return finishUpdate();
    }

    /**
     * Enable or disable a single range of message identifiers.
     * @param startId the first id included in the range
     * @param endId the last id included in the range
     * @param selected true to enable range, false to disable range
     * @return true if successful, false otherwise
     */
    private boolean tryAddSingleRange(int startId, int endId, boolean selected) {
        startUpdate();
        addRange(startId, endId, selected);
        return finishUpdate();
    }

    /**
     * Returns whether the list of ranges is completely empty.
     * @return true if there are no enabled ranges
     */
    public boolean isEmpty() {
        return mRanges.isEmpty();
    }

    /**
     * Called when the list of enabled ranges has changed. This will be
     * followed by zero or more calls to {@link #addRange} followed by
     * a call to {@link #finishUpdate}.
     */
    protected abstract void startUpdate();

    /**
     * Called after {@link #startUpdate} to indicate a range of enabled
     * or disabled values.
     *
     * @param startId the first id included in the range
     * @param endId the last id included in the range
     * @param selected true to enable range, false to disable range
     */
    protected abstract void addRange(int startId, int endId, boolean selected);

    /**
     * Called to indicate the end of a range update started by the
     * previous call to {@link #startUpdate}.
     * @return true if successful, false otherwise
     */
    protected abstract boolean finishUpdate();
}
