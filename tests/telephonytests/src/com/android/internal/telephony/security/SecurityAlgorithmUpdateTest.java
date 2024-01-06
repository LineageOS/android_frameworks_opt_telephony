/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.security;

import static android.telephony.SecurityAlgorithmUpdate.CONNECTION_EVENT_PS_SIGNALLING_3G;
import static android.telephony.SecurityAlgorithmUpdate.CONNECTION_EVENT_VOLTE_SIP;
import static android.telephony.SecurityAlgorithmUpdate.SECURITY_ALGORITHM_AUTH_HMAC_SHA2_256_128;
import static android.telephony.SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2;
import static android.telephony.SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96;
import static android.telephony.SecurityAlgorithmUpdate.SECURITY_ALGORITHM_UEA1;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.radio.network.ConnectionEvent;
import android.hardware.radio.network.SecurityAlgorithm;
import android.os.Parcel;
import android.telephony.SecurityAlgorithmUpdate;

import com.android.internal.telephony.RILUtils;

import org.junit.Test;

public final class SecurityAlgorithmUpdateTest {

    @Test
    public void testEqualsAndHash() {
        SecurityAlgorithmUpdate update = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_VOLTE_SIP, SECURITY_ALGORITHM_EEA2,
                SECURITY_ALGORITHM_HMAC_SHA1_96, false);
        SecurityAlgorithmUpdate sameUpdate = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_VOLTE_SIP, SECURITY_ALGORITHM_EEA2,
                SECURITY_ALGORITHM_HMAC_SHA1_96, false);

        assertThat(update).isEqualTo(sameUpdate);
        assertThat(update.hashCode()).isEqualTo(sameUpdate.hashCode());
    }

    @Test
    public void testNotEqualsAndHash() {
        SecurityAlgorithmUpdate update = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_VOLTE_SIP, SECURITY_ALGORITHM_EEA2,
                SECURITY_ALGORITHM_HMAC_SHA1_96, false);
        SecurityAlgorithmUpdate sameUpdate = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_VOLTE_SIP, SECURITY_ALGORITHM_EEA2,
                SECURITY_ALGORITHM_HMAC_SHA1_96, true);

        assertThat(update).isNotEqualTo(sameUpdate);
        assertThat(update.hashCode()).isNotEqualTo(sameUpdate.hashCode());
    }

    @Test
    public void testGetters() {
        SecurityAlgorithmUpdate update = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_VOLTE_SIP, SECURITY_ALGORITHM_EEA2,
                SECURITY_ALGORITHM_HMAC_SHA1_96, false);

        assertThat(update.getConnectionEvent()).isEqualTo(CONNECTION_EVENT_VOLTE_SIP);
        assertThat(update.getEncryption()).isEqualTo(SECURITY_ALGORITHM_EEA2);
        assertThat(update.getIntegrity()).isEqualTo(SECURITY_ALGORITHM_HMAC_SHA1_96);
        assertThat(update.isUnprotectedEmergency()).isFalse();
    }

    @Test
    public void testParcel() {
        SecurityAlgorithmUpdate update = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_VOLTE_SIP, SECURITY_ALGORITHM_EEA2,
                SECURITY_ALGORITHM_HMAC_SHA1_96, false);

        Parcel p = Parcel.obtain();
        update.writeToParcel(p, 0);
        p.setDataPosition(0);

        SecurityAlgorithmUpdate fromParcel = SecurityAlgorithmUpdate.CREATOR.createFromParcel(p);
        assertThat(fromParcel).isEqualTo(update);
    }

    @Test
    public void testConvertSecurityAlgorithmUpdate() {
        android.hardware.radio.network.SecurityAlgorithmUpdate aidlUpdate =
                new android.hardware.radio.network.SecurityAlgorithmUpdate();
        aidlUpdate.connectionEvent = ConnectionEvent.PS_SIGNALLING_3G;
        aidlUpdate.encryption = SecurityAlgorithm.UEA1;
        aidlUpdate.integrity = SecurityAlgorithm.AUTH_HMAC_SHA2_256_128;
        aidlUpdate.isUnprotectedEmergency = true;

        assertThat(RILUtils.convertSecurityAlgorithmUpdate(aidlUpdate))
                .isEqualTo(
                        new SecurityAlgorithmUpdate(
                                CONNECTION_EVENT_PS_SIGNALLING_3G,
                                SECURITY_ALGORITHM_UEA1,
                                SECURITY_ALGORITHM_AUTH_HMAC_SHA2_256_128,
                                true));
    }
}
