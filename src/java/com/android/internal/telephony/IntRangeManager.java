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
        int startId;
        int endId;
        // sorted by earliest start id
        final ArrayList<ClientRange> clients;

        /**
         * Create a new IntRange with a single client.
         * @param startId the first id included in the range
         * @param endId the last id included in the range
         * @param client the client requesting the enabled range
         */
        IntRange(int startId, int endId, String client) {
            this.startId = startId;
            this.endId = endId;
            clients = new ArrayList<ClientRange>(INITIAL_CLIENTS_ARRAY_SIZE);
            clients.add(new ClientRange(startId, endId, client));
        }

        /**
         * Create a new IntRange for an existing ClientRange.
         * @param clientRange the initial ClientRange to add
         */
        IntRange(ClientRange clientRange) {
            startId = clientRange.startId;
            endId = clientRange.endId;
            clients = new ArrayList<ClientRange>(INITIAL_CLIENTS_ARRAY_SIZE);
            clients.add(clientRange);
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
            this.startId = intRange.startId;
            this.endId = intRange.endId;
            this.clients = new ArrayList<ClientRange>(intRange.clients.size());
            for (int i=0; i < numElements; i++) {
                this.clients.add(intRange.clients.get(i));
            }
        }

        /**
         * Insert new ClientRange in order by start id, then by end id
         * <p>If the new ClientRange is known to be sorted before or after the
         * existing ClientRanges, or at a particular index, it can be added
         * to the clients array list directly, instead of via this method.
         * <p>Note that this can be changed from linear to binary search if the
         * number of clients grows large enough that it would make a difference.
         * @param range the new ClientRange to insert
         */
        void insert(ClientRange range) {
            int len = clients.size();
            int insert = -1;
            for (int i=0; i < len; i++) {
                ClientRange nextRange = clients.get(i);
                if (range.startId <= nextRange.startId) {
                    // ignore duplicate ranges from the same client
                    if (!range.equals(nextRange)) {
                        // check if same startId, then order by endId
                        if (range.startId == nextRange.startId && range.endId > nextRange.endId) {
                            insert = i + 1;
                            if (insert < len) {
                                // there may be more client following with same startId
                                // new [1, 5] existing [1, 2] [1, 4] [1, 7]
                                continue;
                            }
                            break;
                        }
                        clients.add(i, range);
                    }
                    return;
                }
            }
            if (insert != -1 && insert < len) {
                clients.add(insert, range);
                return;
            }
            clients.add(range);    // append to end of list
        }
    }

    /**
     * The message id range for a single client.
     */
    private class ClientRange {
        final int startId;
        final int endId;
        final String client;

        ClientRange(int startId, int endId, String client) {
            this.startId = startId;
            this.endId = endId;
            this.client = client;
        }

        @Override
        public boolean equals(Object o) {
            if (o != null && o instanceof ClientRange) {
                ClientRange other = (ClientRange) o;
                return startId == other.startId &&
                        endId == other.endId &&
                        client.equals(other.client);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return (startId * 31 + endId) * 31 + client.hashCode();
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
            if (tryAddRanges(startId, endId, true)) {
                mRanges.add(new IntRange(startId, endId, client));
                return true;
            } else {
                return false;   // failed to update radio
            }
        }

        for (int startIndex = 0; startIndex < len; startIndex++) {
            IntRange range = mRanges.get(startIndex);
            if ((startId) >= range.startId && (endId) <= range.endId) {
                // exact same range:  new [1, 1] existing [1, 1]
                // range already enclosed in existing: new [3, 3], [1,3]
                // no radio update necessary.
                // duplicate "client" check is done in insert, attempt to insert.
                range.insert(new ClientRange(startId, endId, client));
                return true;
            } else if ((startId - 1) == range.endId) {
                // new [3, x] existing [1, 2]  OR new [2, 2] existing [1, 1]
                // found missing link? check if next range can be joined
                int newRangeEndId = endId;
                IntRange nextRange = null;
                if ((startIndex + 1) < len) {
                    nextRange = mRanges.get(startIndex + 1);
                    if ((nextRange.startId - 1) <= endId) {
                        // new [3, x] existing [1, 2] [5, 7] OR  new [2 , 2] existing [1, 1] [3, 5]
                        if (endId <= nextRange.endId) {
                            // new [3, 6] existing [1, 2] [5, 7]
                            newRangeEndId = nextRange.startId - 1; // need to enable [3, 4]
                        }
                    } else {
                        // mark nextRange to be joined as null.
                        nextRange = null;
                    }
                }
                if (tryAddRanges(startId, newRangeEndId, true)) {
                    range.endId = endId;
                    range.insert(new ClientRange(startId, endId, client));

                    // found missing link? check if next range can be joined
                    if (nextRange != null) {
                        if (range.endId < nextRange.endId) {
                            // new [3, 6] existing [1, 2] [5, 10]
                            range.endId = nextRange.endId;
                        }
                        range.clients.addAll(nextRange.clients);
                        mRanges.remove(nextRange);
                    }
                    return true;
                } else {
                    return false;   // failed to update radio
                }
            } else if (startId < range.startId) {
                // new [1, x] , existing [5, y]
                // test if new range completely precedes this range
                // note that [1, 4] and [5, 6] coalesce to [1, 6]
                if ((endId + 1) < range.startId) {
                    // new [1, 3] existing [5, 6] non contiguous case
                    // insert new int range before previous first range
                    if (tryAddRanges(startId, endId, true)) {
                        mRanges.add(startIndex, new IntRange(startId, endId, client));
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                } else if (endId <= range.endId) {
                    // new [1, 4] existing [5, 6]  or  new [1, 1] existing [2, 2]
                    // extend the start of this range
                    if (tryAddRanges(startId, range.startId - 1, true)) {
                        range.startId = startId;
                        range.clients.add(0, new ClientRange(startId, endId, client));
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                } else {
                    // find last range that can coalesce into the new combined range
                    for (int endIndex = startIndex+1; endIndex < len; endIndex++) {
                        IntRange endRange = mRanges.get(endIndex);
                        if ((endId + 1) < endRange.startId) {
                            // new [1, 10] existing [2, 3] [14, 15]
                            // try to add entire new range
                            if (tryAddRanges(startId, endId, true)) {
                                range.startId = startId;
                                range.endId = endId;
                                // insert new ClientRange before existing ranges
                                range.clients.add(0, new ClientRange(startId, endId, client));
                                // coalesce range with following ranges up to endIndex-1
                                // remove each range after adding its elements, so the index
                                // of the next range to join is always startIndex+1.
                                // i is the index if no elements were removed: we only care
                                // about the number of loop iterations, not the value of i.
                                int joinIndex = startIndex + 1;
                                for (int i = joinIndex; i < endIndex; i++) {
                                    // new [1, 10] existing [2, 3] [5, 6] [14, 15]
                                    IntRange joinRange = mRanges.get(joinIndex);
                                    range.clients.addAll(joinRange.clients);
                                    mRanges.remove(joinRange);
                                }
                                return true;
                            } else {
                                return false;   // failed to update radio
                            }
                        } else if (endId <= endRange.endId) {
                            // new [1, 10] existing [2, 3] [5, 15]
                            // add range from start id to start of last overlapping range,
                            // values from endRange.startId to endId are already enabled
                            if (tryAddRanges(startId, endRange.startId - 1, true)) {
                                range.startId = startId;
                                range.endId = endRange.endId;
                                // insert new ClientRange before existing ranges
                                range.clients.add(0, new ClientRange(startId, endId, client));
                                // coalesce range with following ranges up to endIndex
                                // remove each range after adding its elements, so the index
                                // of the next range to join is always startIndex+1.
                                // i is the index if no elements were removed: we only care
                                // about the number of loop iterations, not the value of i.
                                int joinIndex = startIndex + 1;
                                for (int i = joinIndex; i <= endIndex; i++) {
                                    IntRange joinRange = mRanges.get(joinIndex);
                                    range.clients.addAll(joinRange.clients);
                                    mRanges.remove(joinRange);
                                }
                                return true;
                            } else {
                                return false;   // failed to update radio
                            }
                        }
                    }

                    // new [1, 10] existing [2, 3]
                    // endId extends past all existing IntRanges: combine them all together
                    if (tryAddRanges(startId, endId, true)) {
                        range.startId = startId;
                        range.endId = endId;
                        // insert new ClientRange before existing ranges
                        range.clients.add(0, new ClientRange(startId, endId, client));
                        // coalesce range with following ranges up to len-1
                        // remove each range after adding its elements, so the index
                        // of the next range to join is always startIndex+1.
                        // i is the index if no elements were removed: we only care
                        // about the number of loop iterations, not the value of i.
                        int joinIndex = startIndex + 1;
                        for (int i = joinIndex; i < len; i++) {
                            // new [1, 10] existing [2, 3] [5, 6]
                            IntRange joinRange = mRanges.get(joinIndex);
                            range.clients.addAll(joinRange.clients);
                            mRanges.remove(joinRange);
                        }
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                }
            } else if ((startId + 1) <= range.endId) {
                // new [2, x] existing [1, 4]
                if (endId <= range.endId) {
                    // new [2, 3] existing [1, 4]
                    // completely contained in existing range; no radio changes
                    range.insert(new ClientRange(startId, endId, client));
                    return true;
                } else {
                    // new [2, 5] existing [1, 4]
                    // find last range that can coalesce into the new combined range
                    int endIndex = startIndex;
                    for (int testIndex = startIndex+1; testIndex < len; testIndex++) {
                        IntRange testRange = mRanges.get(testIndex);
                        if ((endId + 1) < testRange.startId) {
                            break;
                        } else {
                            endIndex = testIndex;
                        }
                    }
                    // no adjacent IntRanges to combine
                    if (endIndex == startIndex) {
                        // new [2, 5] existing [1, 4]
                        // add range from range.endId+1 to endId,
                        // values from startId to range.endId are already enabled
                        if (tryAddRanges(range.endId + 1, endId, true)) {
                            range.endId = endId;
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
                    int newRangeEndId = (endId <= endRange.endId) ? endRange.startId - 1 : endId;
                    // new [2, 10] existing [1, 4] [7, 8] OR
                    // new [2, 10] existing [1, 4] [7, 15]
                    if (tryAddRanges(range.endId + 1, newRangeEndId, true)) {
                        newRangeEndId = (endId <= endRange.endId) ? endRange.endId : endId;
                        range.endId = newRangeEndId;
                        // insert new ClientRange in place
                        range.insert(new ClientRange(startId, endId, client));
                        // coalesce range with following ranges up to endIndex
                        // remove each range after adding its elements, so the index
                        // of the next range to join is always startIndex+1 (joinIndex).
                        // i is the index if no elements had been removed: we only care
                        // about the number of loop iterations, not the value of i.
                        int joinIndex = startIndex + 1;
                        for (int i = joinIndex; i <= endIndex; i++) {
                            IntRange joinRange = mRanges.get(joinIndex);
                            range.clients.addAll(joinRange.clients);
                            mRanges.remove(joinRange);
                        }
                        return true;
                    } else {
                        return false;   // failed to update radio
                    }
                }
            }
        }

        // new [5, 6], existing [1, 3]
        // append new range after existing IntRanges
        if (tryAddRanges(startId, endId, true)) {
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
            if (startId < range.startId) {
                return false;   // not found
            } else if (endId <= range.endId) {
                // found the IntRange that encloses the client range, if any
                // search for it in the clients list
                ArrayList<ClientRange> clients = range.clients;

                // handle common case of IntRange containing one ClientRange
                int crLength = clients.size();
                if (crLength == 1) {
                    ClientRange cr = clients.get(0);
                    if (cr.startId == startId && cr.endId == endId && cr.client.equals(client)) {
                        // mRange contains only what's enabled.
                        // remove the range from mRange then update the radio
                        mRanges.remove(i);
                        if (updateRanges()) {
                            return true;
                        } else {
                            // failed to update radio.  insert back the range
                            mRanges.add(i, range);
                            return false;
                        }
                    } else {
                        return false;   // not found
                    }
                }

                // several ClientRanges: remove one, potentially splitting into many IntRanges.
                // Save the original start and end id for the original IntRange
                // in case the radio update fails and we have to revert it. If the
                // update succeeds, we remove the client range and insert the new IntRanges.
                // clients are ordered by startId then by endId, so client with largest endId
                // can be anywhere.  Need to loop thru to find largestEndId.
                int largestEndId = Integer.MIN_VALUE;  // largest end identifier found
                boolean updateStarted = false;

                // crlength >= 2
                for (int crIndex=0; crIndex < crLength; crIndex++) {
                    ClientRange cr = clients.get(crIndex);
                    if (cr.startId == startId && cr.endId == endId && cr.client.equals(client)) {
                        // found the ClientRange to remove, check if it's the last in the list
                        if (crIndex == crLength - 1) {
                            if (range.endId == largestEndId) {
                                // remove [2, 5] from [1, 7] [2, 5]
                                // no channels to remove from radio; return success
                                clients.remove(crIndex);
                                return true;
                            } else {
                                // disable the channels at the end and lower the end id
                                clients.remove(crIndex);
                                range.endId = largestEndId;
                                if (updateRanges()) {
                                    return true;
                                } else {
                                    clients.add(crIndex, cr);
                                    range.endId = cr.endId;
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
                            // because check for just one ClientRangees case is already handled
                            // so clients.get(1) should always succeed.
                            int nextStartId = clients.get(1).startId;
                            if (nextStartId != range.startId) {
                                updateStarted = true;
                                rangeCopy.startId = nextStartId;
                            }
                            // init largestEndId
                            largestEndId = clients.get(1).endId;
                        }

                        // go through remaining ClientRanges, creating new IntRanges when
                        // there is a gap in the sequence. After radio update succeeds,
                        // remove the original IntRange and append newRanges to mRanges.
                        // Otherwise, leave the original IntRange in mRanges and return false.
                        ArrayList<IntRange> newRanges = new ArrayList<IntRange>();

                        IntRange currentRange = rangeCopy;
                        for (int nextIndex = crIndex + 1; nextIndex < crLength; nextIndex++) {
                            ClientRange nextCr = clients.get(nextIndex);
                            if (nextCr.startId > largestEndId + 1) {
                                updateStarted = true;
                                currentRange.endId = largestEndId;
                                newRanges.add(currentRange);
                                currentRange = new IntRange(nextCr);
                            } else {
                                if (currentRange.endId < nextCr.endId) {
                                    currentRange.endId = nextCr.endId;
                                }
                                currentRange.clients.add(nextCr);
                            }
                            if (nextCr.endId > largestEndId) {
                                largestEndId = nextCr.endId;
                            }
                        }

                        // remove any channels between largestEndId and endId
                        if (largestEndId < endId) {
                            updateStarted = true;
                            currentRange.endId = largestEndId;
                        }
                        newRanges.add(currentRange);

                        // replace the original IntRange with newRanges
                        mRanges.remove(i);
                        mRanges.addAll(i, newRanges);
                        if (updateStarted && !updateRanges()) {
                            // failed to update radio.  revert back mRange.
                            mRanges.removeAll(newRanges);
                            mRanges.add(i, range);
                            return false;
                        }

                        return true;
                    } else {
                        // not the ClientRange to remove; save highest end ID seen so far
                        if (cr.endId > largestEndId) {
                            largestEndId = cr.endId;
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

        populateAllRanges();
        return finishUpdate();
    }

    /**
     * Enable or disable a single range of message identifiers.
     * @param startId the first id included in the range
     * @param endId the last id included in the range
     * @param selected true to enable range, false to disable range
     * @return true if successful, false otherwise
     */
    protected boolean tryAddRanges(int startId, int endId, boolean selected) {

        startUpdate();
        populateAllRanges();
        // This is the new range to be enabled
        addRange(startId, endId, selected); // adds to mConfigList
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
     * Called when attempting to add a single range of message identifiers
     * Populate all ranges of message identifiers.
     */
    private void populateAllRanges() {
        Iterator<IntRange> itr = mRanges.iterator();
        // Populate all ranges from mRanges
        while (itr.hasNext()) {
            IntRange currRange = (IntRange) itr.next();
            addRange(currRange.startId, currRange.endId, true);
        }
    }

    /**
     * Called when attempting to add a single range of message identifiers
     * Populate all ranges of message identifiers using clients' ranges.
     */
    private void populateAllClientRanges() {
        int len = mRanges.size();
        for (int i = 0; i < len; i++) {
            IntRange range = mRanges.get(i);

            int clientLen = range.clients.size();
            for (int j=0; j < clientLen; j++) {
                ClientRange nextRange = range.clients.get(j);
                addRange(nextRange.startId, nextRange.endId, true);
            }
        }
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
