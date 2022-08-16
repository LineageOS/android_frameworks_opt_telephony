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

package com.android.internal.telephony.data;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.Network;
import android.telephony.data.EpsBearerQosSessionAttributes;
import android.telephony.data.EpsQos;
import android.telephony.data.Qos;
import android.telephony.data.QosBearerFilter;
import android.telephony.data.QosBearerSession;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.metrics.RcsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class QosCallbackTrackerTest extends TelephonyTest {

    class Filter implements QosCallbackTracker.IFilter {
        InetSocketAddress mLocalAddress;
        InetSocketAddress mRemoteAddress;

        Filter(@NonNull final InetSocketAddress localAddress) {
            this.mLocalAddress = localAddress;
            this.mRemoteAddress = null;
        }

        Filter(@NonNull final InetSocketAddress localAddress,
                @NonNull final InetSocketAddress remoteAddress) {
            this.mLocalAddress = localAddress;
            this.mRemoteAddress = remoteAddress;
        }

        public boolean matchesLocalAddress(final @NonNull InetAddress address,
                final int startPort, final int endPort) {
            return startPort <= mLocalAddress.getPort()
                    && endPort >= mLocalAddress.getPort()
                    && mLocalAddress.getAddress().equals(address);
        }

        public boolean matchesRemoteAddress(final @NonNull InetAddress address,
                final int startPort, final int endPort) {
            return mRemoteAddress != null
                    && startPort <= mRemoteAddress.getPort()
                    && endPort >= mRemoteAddress.getPort()
                    && mRemoteAddress.getAddress().equals(address);
        }
    }

    // Mocked classes
    private Phone mPhone;
    private TelephonyNetworkAgent mNetworkAgent;
    private Network mNetwork;
    private RcsStats mRcsStats;

    private QosCallbackTracker mQosCallbackTracker;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mPhone = mock(Phone.class);
        mNetworkAgent = mock(TelephonyNetworkAgent.class);
        mNetwork = mock(Network.class);
        mRcsStats = mock(RcsStats.class);
        doReturn(mNetwork).when(mNetworkAgent).getNetwork();
        doReturn(100).when(mNetwork).getNetId();
        doReturn(0).when(mPhone).getPhoneId();
        mQosCallbackTracker = new QosCallbackTracker(mNetworkAgent, mPhone);
        replaceInstance(QosCallbackTracker.class, "mRcsStats", mQosCallbackTracker, mRcsStats);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mQosCallbackTracker = null;
        super.tearDown();
    }

    public static EpsQos createEpsQos(int dlMbr, int ulMbr, int dlGbr, int ulGbr) {
        // Build android.hardware.radio.V1_6.EpsQos
        android.hardware.radio.V1_6.EpsQos halEpsQos = new android.hardware.radio.V1_6.EpsQos();
        halEpsQos.qci = 4;
        halEpsQos.downlink.maxBitrateKbps = dlMbr;
        halEpsQos.downlink.guaranteedBitrateKbps = dlGbr;
        halEpsQos.uplink.maxBitrateKbps = ulMbr;
        halEpsQos.uplink.guaranteedBitrateKbps = ulGbr;

        return new EpsQos(
                new Qos.QosBandwidth(dlMbr, dlGbr), new Qos.QosBandwidth(ulMbr, ulGbr), 4);
    }

    public static QosBearerFilter createIpv4QosFilter(String localAddress,
            QosBearerFilter.PortRange localPort, int precedence) {
        return new QosBearerFilter(
                Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(localAddress), 32)),
                new ArrayList<>(), localPort, null, QosBearerFilter.QOS_PROTOCOL_TCP,
                7, 987, 678, QosBearerFilter.QOS_FILTER_DIRECTION_BIDIRECTIONAL, precedence);
    }

    private static QosBearerFilter createIpv4QosFilter(String localAddress, String remoteAddress,
            QosBearerFilter.PortRange localPort, QosBearerFilter.PortRange remotePort,
            int precedence) {
        return new QosBearerFilter(
                Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(localAddress), 32)),
                Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(remoteAddress), 32)),
                localPort, remotePort,
                QosBearerFilter.QOS_PROTOCOL_TCP, 7, 987, 678,
                QosBearerFilter.QOS_FILTER_DIRECTION_BIDIRECTIONAL, precedence);
    }

    @Test
    @SmallTest
    public void testAddFilterBeforeUpdateSessions() throws Exception {
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222));
        mQosCallbackTracker.addFilter(1, filter);

        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55",
                new QosBearerFilter.PortRange(2222, 2222), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, never()).notifyQosSessionAvailable(eq(1),
                eq(1234), any(EpsBearerQosSessionAttributes.class));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(2222, 2222), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

    }


    @Test
    @SmallTest
    public void testAddFilterAfterUpdateSessions() throws Exception {
        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55",
                new QosBearerFilter.PortRange(2222, 2222), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(2222, 2222), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);

        // Add filter after updateSessions
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222));
        mQosCallbackTracker.addFilter(1, filter);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

    }

    @Test
    @SmallTest
    public void testRemoveFilter() throws Exception {
        // Add filter
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222));
        mQosCallbackTracker.addFilter(1, filter);

        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55",
                new QosBearerFilter.PortRange(2222, 2222), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, never()).notifyQosSessionAvailable(eq(1),
                eq(1234), any(EpsBearerQosSessionAttributes.class));

        // Remove the filter
        mQosCallbackTracker.removeFilter(1);

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(2222, 2222), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        // Verify that notifyQosSessionAvailable is not invoked as the filter is already removed
        verify(mNetworkAgent, never()).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

    }

    @Test
    @SmallTest
    public void testSessionLost() throws Exception {
        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        // Add filter after updateSessions
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));
        mQosCallbackTracker.addFilter(1, filter);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

        // Remove the matching QosBearerFilter
        qosSessions.remove(1);
        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionLost(eq(1), eq(1235), eq(1));
    }

    @Test
    @SmallTest
    public void testModifiedQos() throws Exception {
        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);

        // Add filter after updateSessions
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));
        mQosCallbackTracker.addFilter(1, filter);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

        reset(mNetworkAgent);

        // Update the QOS
        qosSessions.remove(1);
        qosSessions.add(new QosBearerSession(1235, createEpsQos(10, 12, 14, 16), qosFilters2));
        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));
    }

    @Test
    @SmallTest
    public void testUnmodifiedQos() throws Exception {
        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);

        // Add filter after updateSessions
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));
        mQosCallbackTracker.addFilter(1, filter);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

        reset(mNetworkAgent);

        // Update the same QOS
        qosSessions.remove(1);
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));
        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, never()).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));
    }

    @Test
    @SmallTest
    public void testEmptyQosSessions() throws Exception {
        // Add filter
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("155.55.55.55"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));
        mQosCallbackTracker.addFilter(1, filter);

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);

        // Add filter after updateSessions
        Filter filter2 = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));
        mQosCallbackTracker.addFilter(2, filter2);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1234), any(EpsBearerQosSessionAttributes.class));
        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(2),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

        // Update empty QOS sessions list
        mQosCallbackTracker.updateSessions(new ArrayList<>());
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionLost(eq(1), eq(1234), eq(1));
        verify(mNetworkAgent, times(1)).notifyQosSessionLost(eq(2), eq(1235), eq(1));
    }

    @Test
    @SmallTest
    public void testMultipleQosSessions() throws Exception {
        // Add filter 1
        Filter filter1 = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("155.55.55.55"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));
        mQosCallbackTracker.addFilter(1, filter1);

        // Add filter 2
        Filter filter2 = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("177.77.77.77"), 2227));
        mQosCallbackTracker.addFilter(2, filter2);

        // QosBearerFilter 1
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));

        // QosBearerFilter 2
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22", "177.77.77.77",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2227), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(7, 8, 9, 10), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(1),
                eq(1234), any(EpsBearerQosSessionAttributes.class));
        verify(mNetworkAgent, times(1)).notifyQosSessionAvailable(eq(2),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

        // Update empty QOS sessions list
        mQosCallbackTracker.updateSessions(new ArrayList<>());
        processAllMessages();

        verify(mNetworkAgent, times(1)).notifyQosSessionLost(eq(1), eq(1234), eq(1));
        verify(mNetworkAgent, times(1)).notifyQosSessionLost(eq(2), eq(1235), eq(1));
    }

    @Test
    @SmallTest
    public void testQosSessionWithInvalidPortRange() throws Exception {
        // Non-matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55",
                new QosBearerFilter.PortRange(0, 0), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));

        // Matching QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(-1, 1), 45));
        qosSessions.add(new QosBearerSession(1235, createEpsQos(5, 6, 7, 8), qosFilters2));

        mQosCallbackTracker.updateSessions(qosSessions);

        // Add filter after updateSessions
        Filter filter = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("122.22.22.22"), 2222));
        mQosCallbackTracker.addFilter(1, filter);
        processAllMessages();

        verify(mNetworkAgent, never()).notifyQosSessionAvailable(eq(1),
                eq(1235), any(EpsBearerQosSessionAttributes.class));

    }

    @Test
    @SmallTest
    public void testQosMetrics() throws Exception {
        final int callbackId = 1;
        final int slotId = mPhone.getPhoneId();
        // Add filter before update session
        Filter filter1 = new Filter(new InetSocketAddress(
                InetAddresses.parseNumericAddress("155.55.55.55"), 2222),
                new InetSocketAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 2223));

        mQosCallbackTracker.addFilter(callbackId, filter1);
        processAllMessages();
        verify(mRcsStats, never()).onImsDedicatedBearerListenerAdded(eq(callbackId), eq(slotId),
                anyInt(), anyInt());

        // QosBearerFilter
        ArrayList<QosBearerFilter> qosFilters1 = new ArrayList<>();
        qosFilters1.add(createIpv4QosFilter("155.55.55.55", "144.44.44.44",
                new QosBearerFilter.PortRange(2222, 2222),
                new QosBearerFilter.PortRange(2223, 2223), 45));

        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosSessions.add(new QosBearerSession(1234, createEpsQos(5, 6, 7, 8), qosFilters1));
        mQosCallbackTracker.updateSessions(qosSessions);
        processAllMessages();

        verify(mRcsStats, times(1))
                .onImsDedicatedBearerListenerUpdateSession(
                        eq(callbackId), eq(slotId), anyInt(), anyInt(), eq(true));

        mQosCallbackTracker.addFilter(callbackId, filter1);
        processAllMessages();
        verify(mRcsStats, times(1)).onImsDedicatedBearerListenerAdded(
                anyInt(), anyInt(), anyInt(), anyInt());

        mQosCallbackTracker.removeFilter(callbackId);
        processAllMessages();
        verify(mRcsStats, times(1))
                .onImsDedicatedBearerListenerRemoved(callbackId);
    }
}

