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

package com.android.internal.telephony.dataconnection;

import android.annotation.NonNull;
import android.net.LinkAddress;
import android.net.QosSession;
import android.telephony.TelephonyProtoEnums;
import android.telephony.data.EpsBearerQosSessionAttributes;
import android.telephony.data.EpsQos;
import android.telephony.data.NrQos;
import android.telephony.data.NrQosSessionAttributes;
import android.telephony.data.QosBearerFilter;
import android.telephony.data.QosBearerSession;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.telephony.Rlog;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches filters with qos sessions and send corresponding available and lost events.
 *
 * Note: This class is <b>NOT</b> thread-safe
 *
 * {@hide}
 */
public class QosCallbackTracker {
    private static final int DedicatedBearerEvent_STATE_NONE = 0;
    private static final int DedicatedBearerEvent_STATE_ADDED = 1;
    private static final int DedicatedBearerEvent_STATE_MODIFIED = 2;
    private static final int DedicatedBearerEvent_STATE_DELETED = 3;

    @NonNull private final String mTag;
    @NonNull private final DcNetworkAgent mDcNetworkAgent;
    @NonNull private final Map<Integer, QosBearerSession> mQosBearerSessions;
    @NonNull private final RcsStats mRcsStats;

    // We perform an exact match on the address
    @NonNull private final Map<Integer, IFilter> mCallbacksToFilter;

    @NonNull private final int mPhoneID;

    /**
     * Construct a new tracker
     * @param dcNetworkAgent the network agent to send events to
     */
    public QosCallbackTracker(@NonNull final DcNetworkAgent dcNetworkAgent,
            @NonNull final int phoneID) {
        mQosBearerSessions = new HashMap<>();
        mCallbacksToFilter = new HashMap<>();
        mDcNetworkAgent = dcNetworkAgent;
        mPhoneID = phoneID;
        mRcsStats = RcsStats.getInstance();
        mTag = "QosCallbackTracker" + "-" + mDcNetworkAgent.getNetwork().getNetId();
    }

    /**
     * Construct a new tracker
     * @param dcNetworkAgent the network agent to send events to
     * @param rcsStats metrics package to store QoS event info
     */
    @VisibleForTesting
    public QosCallbackTracker(@NonNull final DcNetworkAgent dcNetworkAgent,
            @NonNull final int phoneID, @NonNull final RcsStats rcsStats) {
        mQosBearerSessions = new HashMap<>();
        mCallbacksToFilter = new HashMap<>();
        mDcNetworkAgent = dcNetworkAgent;
        mPhoneID = phoneID;
        mRcsStats = rcsStats;
        mTag = "QosCallbackTracker" + "-" + mDcNetworkAgent.getNetwork().getNetId();
    }

    /**
     * Add new filter that is to receive events
     *
     * @param callbackId the associated callback id
     * @param filter provides the matching logic
     */
    public void addFilter(final int callbackId, final IFilter filter) {
        logd("addFilter: callbackId=" + callbackId);
        // Called from mDcNetworkAgent
        mCallbacksToFilter.put(callbackId, filter);

        //On first change. Check all sessions and send.
        for (final QosBearerSession session : mQosBearerSessions.values()) {
            if (doFiltersMatch(session, filter)) {
                sendSessionAvailable(callbackId, session, filter);

                notifyMetricDedicatedBearerListenerAdded(callbackId, session);
            }
        }
    }

    /**
     * Remove the filter with the associated callback id
     *
     * @param callbackId the qos callback id
     */
    public void removeFilter(final int callbackId) {
        logd("removeFilter: callbackId=" + callbackId);
        mCallbacksToFilter.remove(callbackId);
        notifyMetricDedicatedBearerListenerRemoved(callbackId);
    }

    /**
     * Update the list of qos sessions and send out corresponding events
     *
     * @param sessions the new list of qos sessions
     */
    public void updateSessions(@NonNull final List<QosBearerSession> sessions) {
        logd("updateSessions: sessions size=" + sessions.size());

        int bearerState = DedicatedBearerEvent_STATE_NONE;
        final List<QosBearerSession> sessionsToAdd = new ArrayList<>();
        final Map<Integer, QosBearerSession> incomingSessions = new HashMap<>();
        for (final QosBearerSession incomingSession : sessions) {
            incomingSessions.put(incomingSession.getQosBearerSessionId(), incomingSession);

            final QosBearerSession existingSession = mQosBearerSessions.get(
                    incomingSession.getQosBearerSessionId());
            for (final int callbackId : mCallbacksToFilter.keySet()) {
                final IFilter filter = mCallbacksToFilter.get(callbackId);

                final boolean incomingSessionMatch = doFiltersMatch(incomingSession, filter);
                final boolean existingSessionMatch =
                        existingSession != null && doFiltersMatch(existingSession, filter);

                if (!existingSessionMatch && incomingSessionMatch) {
                    // The filter matches now and didn't match earlier
                    sendSessionAvailable(callbackId, incomingSession, filter);

                    bearerState = DedicatedBearerEvent_STATE_ADDED;
                }

                if (existingSessionMatch && incomingSessionMatch) {
                    // The same sessions matches the same filter, but if the qos changed,
                    // the callback still needs to be notified
                    if (!incomingSession.getQos().equals(existingSession.getQos())) {
                        sendSessionAvailable(callbackId, incomingSession, filter);
                        bearerState = DedicatedBearerEvent_STATE_MODIFIED;
                    }
                }

                notifyMetricDedicatedBearerEvent(incomingSession, filter, bearerState);
            }
            sessionsToAdd.add(incomingSession);
        }

        final List<Integer> sessionsToRemove = new ArrayList<>();
        // Find sessions that no longer exist
        for (final QosBearerSession existingSession : mQosBearerSessions.values()) {
            if (!incomingSessions.containsKey(existingSession.getQosBearerSessionId())) {
                for (final int callbackId : mCallbacksToFilter.keySet()) {
                    final IFilter filter = mCallbacksToFilter.get(callbackId);
                    // The filter matches which means it was previously available, and now is lost
                    if (doFiltersMatch(existingSession, filter)) {
                        bearerState = DedicatedBearerEvent_STATE_DELETED;
                        sendSessionLost(callbackId, existingSession);
                        notifyMetricDedicatedBearerEvent(existingSession, filter, bearerState);
                    }
                }
                sessionsToRemove.add(existingSession.getQosBearerSessionId());
            }
        }

        // Add in the new or existing sessions with updated information
        for (final QosBearerSession sessionToAdd : sessionsToAdd) {
            mQosBearerSessions.put(sessionToAdd.getQosBearerSessionId(), sessionToAdd);
        }

        // Remove any old sessions
        for (final int sessionToRemove : sessionsToRemove) {
            mQosBearerSessions.remove(sessionToRemove);
        }
    }

    private boolean doFiltersMatch(
            final QosBearerSession qosBearerSession, final IFilter filter) {
        return getMatchingQosBearerFilter(qosBearerSession, filter) != null;
    }

    private boolean matchesByLocalAddress(
        QosBearerFilter sessionFilter, final IFilter filter) {
        for (final LinkAddress qosAddress : sessionFilter.getLocalAddresses()) {
            return filter.matchesLocalAddress(qosAddress.getAddress(),
                  sessionFilter.getLocalPortRange().getStart(),
                  sessionFilter.getLocalPortRange().getEnd());
        }
        return false;
    }

    private boolean matchesByRemoteAddress(
            QosBearerFilter sessionFilter, final IFilter filter) {
        for (final LinkAddress qosAddress : sessionFilter.getRemoteAddresses()) {
            return filter.matchesRemoteAddress(qosAddress.getAddress(),
                  sessionFilter.getRemotePortRange().getStart(),
                  sessionFilter.getRemotePortRange().getEnd());
        }
        return false;
    }

    private boolean matchesByRemoteAndLocalAddress(
            QosBearerFilter sessionFilter, final IFilter filter) {
        for (final LinkAddress remoteAddress : sessionFilter.getRemoteAddresses()) {
            for (final LinkAddress localAddress : sessionFilter.getLocalAddresses()) {
                return filter.matchesRemoteAddress(remoteAddress.getAddress(),
                        sessionFilter.getRemotePortRange().getStart(),
                        sessionFilter.getRemotePortRange().getEnd())
                        && filter.matchesLocalAddress(localAddress.getAddress(),
                              sessionFilter.getLocalPortRange().getStart(),
                              sessionFilter.getLocalPortRange().getEnd());
            }
        }
        return false;
    }

    private QosBearerFilter getFilterByPrecedence(
            QosBearerFilter qosFilter, QosBearerFilter sessionFilter) {
        // Find for the highest precedence filter, lower the value is the higher the precedence
        return qosFilter == null || sessionFilter.getPrecedence() < qosFilter.getPrecedence()
                ? sessionFilter : qosFilter;
    }

    private QosBearerFilter getMatchingQosBearerFilter(
            final QosBearerSession qosBearerSession, final IFilter filter) {
        QosBearerFilter qosFilter = null;

        for (final QosBearerFilter sessionFilter : qosBearerSession.getQosBearerFilterList()) {
           if (!sessionFilter.getLocalAddresses().isEmpty()
                   && !sessionFilter.getRemoteAddresses().isEmpty()
                   && sessionFilter.getLocalPortRange().isValid()
                   && sessionFilter.getRemotePortRange().isValid()) {
               if (matchesByRemoteAndLocalAddress(sessionFilter, filter)) {
                   qosFilter = getFilterByPrecedence(qosFilter, sessionFilter);
               }
           } else if (!sessionFilter.getRemoteAddresses().isEmpty()
                   && sessionFilter.getRemotePortRange().isValid()) {
               if (matchesByRemoteAddress(sessionFilter, filter)) {
                   qosFilter = getFilterByPrecedence(qosFilter, sessionFilter);
               }
           } else if (!sessionFilter.getLocalAddresses().isEmpty()
                   && sessionFilter.getLocalPortRange().isValid()) {
               if (matchesByLocalAddress(sessionFilter, filter)) {
                   qosFilter = getFilterByPrecedence(qosFilter, sessionFilter);
               }
           }
        }
        return qosFilter;
    }

    private void sendSessionAvailable(final int callbackId,
            @NonNull final QosBearerSession session, @NonNull IFilter filter) {
        QosBearerFilter qosBearerFilter = getMatchingQosBearerFilter(session, filter);
        List<InetSocketAddress> remoteAddresses = new ArrayList<>();
        if(qosBearerFilter.getRemoteAddresses().size() > 0) {
            remoteAddresses.add(
                  new InetSocketAddress(qosBearerFilter.getRemoteAddresses().get(0).getAddress(),
                  qosBearerFilter.getRemotePortRange().getStart()));
        }

        if (session.getQos() instanceof EpsQos) {
            EpsQos qos = (EpsQos) session.getQos();
            EpsBearerQosSessionAttributes epsBearerAttr =
                    new EpsBearerQosSessionAttributes(qos.getQci(),
                            qos.getUplinkBandwidth().getMaxBitrateKbps(),
                            qos.getDownlinkBandwidth().getMaxBitrateKbps(),
                            qos.getDownlinkBandwidth().getGuaranteedBitrateKbps(),
                            qos.getUplinkBandwidth().getGuaranteedBitrateKbps(),
                            remoteAddresses);
            mDcNetworkAgent.notifyQosSessionAvailable(
                    callbackId, session.getQosBearerSessionId(), epsBearerAttr);
        } else {
            NrQos qos = (NrQos) session.getQos();
            NrQosSessionAttributes nrQosAttr =
                    new NrQosSessionAttributes(qos.get5Qi(), qos.getQfi(),
                            qos.getUplinkBandwidth().getMaxBitrateKbps(),
                            qos.getDownlinkBandwidth().getMaxBitrateKbps(),
                            qos.getDownlinkBandwidth().getGuaranteedBitrateKbps(),
                            qos.getUplinkBandwidth().getGuaranteedBitrateKbps(),
                            qos.getAveragingWindow(), remoteAddresses);
            mDcNetworkAgent.notifyQosSessionAvailable(
                    callbackId, session.getQosBearerSessionId(), nrQosAttr);
        }

        /** added to notify to Metric for passing DedicatedBearerEstablished info */
        notifyMetricDedicatedBearerListenerBearerUpdateSession(callbackId, session);

        logd("sendSessionAvailable, callbackId=" + callbackId);
    }

    private void sendSessionLost(final int callbackId, @NonNull final QosBearerSession session) {
        mDcNetworkAgent.notifyQosSessionLost(callbackId, session.getQosBearerSessionId(),
                session.getQos() instanceof EpsQos ?
                QosSession.TYPE_EPS_BEARER : QosSession.TYPE_NR_BEARER);
        logd("sendSessionLost, callbackId=" + callbackId);
    }

    private void notifyMetricDedicatedBearerListenerAdded(
            final int callbackId, final QosBearerSession session) {

        final int slotId = mPhoneID;
        final int rat = getRatInfoFromSessionInfo(session);
        final int qci = getQCIFromSessionInfo(session);

        mRcsStats.onImsDedicatedBearerListenerAdded(callbackId, slotId, rat, qci);
    }

    private void notifyMetricDedicatedBearerListenerBearerUpdateSession(
            final int callbackId, final QosBearerSession session) {

        final int slotId = mPhoneID;
        final int rat = getRatInfoFromSessionInfo(session);
        final int qci = getQCIFromSessionInfo(session);

        mRcsStats.onImsDedicatedBearerListenerUpdateSession(callbackId, slotId, rat, qci, true);
    }

    private void notifyMetricDedicatedBearerListenerRemoved(final int callbackId) {
        mRcsStats.onImsDedicatedBearerListenerRemoved(callbackId);
    }

    private int getQCIFromSessionInfo(final QosBearerSession session) {
        if (session.getQos() instanceof EpsQos) {
            return ((EpsQos) session.getQos()).getQci();
        } else if (session.getQos() instanceof NrQos) {
            return ((NrQos) session.getQos()).get5Qi();
        }

        return 0;
    }

    private int getRatInfoFromSessionInfo(final QosBearerSession session) {
        if (session.getQos() instanceof EpsQos) {
            return TelephonyProtoEnums.NETWORK_TYPE_LTE;
        } else if (session.getQos() instanceof NrQos) {
            return TelephonyProtoEnums.NETWORK_TYPE_NR;
        }

        return 0;
    }

    private void notifyMetricDedicatedBearerEvent(final QosBearerSession session,
            final IFilter filter, final int bearerState) {

        final int slotId = mPhoneID;
        int ratAtEnd = 0;
        int qci = 0;
        boolean localConnectionInfoReceived = false;
        boolean remoteConnectionInfoReceived = false;


        QosBearerFilter qosBearerFilter = getMatchingQosBearerFilter(session, filter);
        if (session.getQos() instanceof EpsQos) {
            ratAtEnd = TelephonyProtoEnums.NETWORK_TYPE_LTE;
            qci = ((EpsQos) session.getQos()).getQci();
        } else if (session.getQos() instanceof NrQos) {
            ratAtEnd = TelephonyProtoEnums.NETWORK_TYPE_NR;
            qci = ((NrQos) session.getQos()).get5Qi();
        } else {
            return;
        }

        if (qosBearerFilter != null) {
            if (!qosBearerFilter.getLocalAddresses().isEmpty()
                    && qosBearerFilter.getLocalPortRange().isValid()) {
                localConnectionInfoReceived = true;
            }
            if (!qosBearerFilter.getRemoteAddresses().isEmpty()
                    && qosBearerFilter.getRemotePortRange().isValid()) {
                remoteConnectionInfoReceived = true;
            }
        }

        mRcsStats.onImsDedicatedBearerEvent(slotId, ratAtEnd, qci, bearerState,
                localConnectionInfoReceived, remoteConnectionInfoReceived, true);
    }

    public interface IFilter {
        public boolean matchesLocalAddress(InetAddress address, int startPort, int endPort);
        public boolean matchesRemoteAddress(InetAddress address, int startPort, int endPort);
    }

    /**
     * Log with debug level
     *
     * @param s is string log
     */
    private void logd(String s) {
        Rlog.d(mTag, s);
    }
}
