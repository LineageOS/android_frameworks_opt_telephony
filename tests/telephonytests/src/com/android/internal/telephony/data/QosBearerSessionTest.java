/*
 * Copyright 2022 The Android Open Source Project
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

import static com.android.internal.telephony.data.QosCallbackTrackerTest.createEpsQos;
import static com.android.internal.telephony.data.QosCallbackTrackerTest.createIpv4QosFilter;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.data.QosBearerFilter;
import android.telephony.data.QosBearerSession;

import org.junit.Test;

import java.util.ArrayList;

public class QosBearerSessionTest {

    @Test
    public void testParcel() {
        ArrayList<QosBearerFilter> qosFilters = new ArrayList<>();
        qosFilters.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(2222, 2222), 45));
        QosBearerSession qosBearerSession = new QosBearerSession(1235,
                createEpsQos(5, 6, 7, 8), qosFilters);

        Parcel p = Parcel.obtain();
        qosBearerSession.writeToParcel(p, 0);
        p.setDataPosition(0);

        QosBearerSession qosBearerSession2 = QosBearerSession.CREATOR.createFromParcel(p);
        assertThat(qosBearerSession).isEqualTo(qosBearerSession2);
    }

    @Test
    public void testEquals() {
        ArrayList<QosBearerFilter> qosFilters = new ArrayList<>();
        qosFilters.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(2222, 2222), 45));
        QosBearerSession qosBearerSession = new QosBearerSession(1235,
                createEpsQos(5, 6, 7, 8), qosFilters);

        ArrayList<QosBearerFilter> qosFilters2 = new ArrayList<>();
        qosFilters2.add(createIpv4QosFilter("122.22.22.22",
                new QosBearerFilter.PortRange(2222, 2222), 45));
        QosBearerSession qosBearerSession2 = new QosBearerSession(1235,
                createEpsQos(5, 6, 7, 8), qosFilters2);

        assertThat(qosBearerSession).isEqualTo(qosBearerSession2);
    }
}
