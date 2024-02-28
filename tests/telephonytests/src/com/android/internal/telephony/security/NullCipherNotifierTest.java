/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.NULL_CIPHER_STATE_ENCRYPTED;
import static com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.NULL_CIPHER_STATE_NOTIFY_ENCRYPTED;
import static com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.telephony.SecurityAlgorithmUpdate;

import com.android.internal.telephony.TestExecutorService;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class NullCipherNotifierTest {

    private static final int SUB_ID = 3425;
    private static final List<Integer> NON_TRANSPORT_LAYER_EVENTS =
            List.of(SecurityAlgorithmUpdate.CONNECTION_EVENT_VOLTE_SIP,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VOLTE_SIP_SOS,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VOLTE_RTP,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VOLTE_RTP_SOS,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VONR_SIP,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VONR_SIP_SOS,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VONR_RTP,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_VONR_RTP_SOS);
    private static final List<Integer> TRANSPORT_LAYER_EVENTS =
            List.of(SecurityAlgorithmUpdate.CONNECTION_EVENT_CS_SIGNALLING_GSM,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_PS_SIGNALLING_GPRS,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_CS_SIGNALLING_3G,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_PS_SIGNALLING_3G,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_NAS_SIGNALLING_LTE,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_AS_SIGNALLING_LTE,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_NAS_SIGNALLING_5G,
                    SecurityAlgorithmUpdate.CONNECTION_EVENT_AS_SIGNALLING_5G);
    private static final List<Integer> NULL_CIPHERS =
            List.of(SecurityAlgorithmUpdate.SECURITY_ALGORITHM_A50,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_GEA0,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_UEA0,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA0,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_NEA0,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_IMS_NULL,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_SIP_NULL,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_SRTP_NULL,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_OTHER);
    private static final List<Integer> NON_NULL_CIPHERS =
            List.of(SecurityAlgorithmUpdate.SECURITY_ALGORITHM_A51,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_A52,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_A53,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_A54,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_GEA1,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_GEA2,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_GEA3,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_GEA4,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_GEA5,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_UEA1,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_UEA2,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA1,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA3,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_NEA1,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_NEA2,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_NEA3,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_SIP_NO_IPSEC_CONFIG,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_AES_GCM,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_AES_GMAC,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_AES_CBC,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_DES_EDE3_CBC,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_AES_EDE3_CBC,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_MD5_96,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_RTP,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_SRTP_AES_COUNTER,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_SRTP_AES_F8,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_SRTP_HMAC_SHA1,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_ENCR_AES_GCM_16,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_ENCR_AES_CBC,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_AUTH_HMAC_SHA2_256_128,
                    SecurityAlgorithmUpdate.SECURITY_ALGORITHM_ORYX);

    private CellularNetworkSecuritySafetySource mSafetySource;
    private Context mContext;
    private TestExecutorService mExecutor = new TestExecutorService();

    @Before
    public void setUp() {
        mSafetySource = mock(CellularNetworkSecuritySafetySource.class);
    }

    @Test
    public void initializeNotifier_notifierAndSafetySourceDisabled() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        assertThat(notifier.isEnabled()).isFalse();
        verify(mSafetySource, never()).setNullCipherIssueEnabled(any(), anyBoolean());
    }

    @Test
    public void enable_enablesSafetySource() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        notifier.enable(mContext);

        assertThat(notifier.isEnabled()).isTrue();
        verify(mSafetySource, times(1)).setNullCipherIssueEnabled(eq(mContext), eq(true));
    }

    @Test
    public void disable_disablesSafetySource() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);
        notifier.enable(mContext);

        notifier.disable(mContext);

        assertThat(notifier.isEnabled()).isFalse();
        verify(mSafetySource, times(1)).setNullCipherIssueEnabled(eq(mContext), eq(false));
    }

    @Test
    public void onSecurityAlgorithmUpdate_enabled_unprotectedEmergency_noSafetySourceUpdate() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        notifier.onSecurityAlgorithmUpdate(
                mContext,
                SUB_ID,
                new SecurityAlgorithmUpdate(
                        SecurityAlgorithmUpdate.CONNECTION_EVENT_AS_SIGNALLING_5G,
                        SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                        SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                        /* isUnprotectedEmergency= */ true));

        verify(mSafetySource, never()).setNullCipherState(any(), anyInt(), anyInt());
    }

    @Test
    public void onSecurityAlgorithmUpdate_enabled_nonTransportLayerEvent_noSafetySourceUpdate() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        for (int connectionEvent : NON_TRANSPORT_LAYER_EVENTS) {
            clearInvocations(mSafetySource);
            notifier.onSecurityAlgorithmUpdate(
                    mContext,
                    SUB_ID,
                    new SecurityAlgorithmUpdate(
                            connectionEvent,
                            SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                            SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                            /* isUnprotectedEmergency= */ false));

            verify(mSafetySource, never().description("Connection event: " + connectionEvent))
                    .setNullCipherState(any(), anyInt(), anyInt());
        }
    }

    @Test
    public void onUpdate_enabled_transportLayerEvent_encryptionNullCipher_notifyNonEncrypted() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        for (int connectionEvent : TRANSPORT_LAYER_EVENTS) {
            for (int encryptionAlgorithm : NULL_CIPHERS) {
                clearInvocations(mSafetySource);
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                encryptionAlgorithm,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                                /* isUnprotectedEmergency= */ false));

                verify(
                        mSafetySource,
                        times(1).description(
                                "Connection event: " + connectionEvent
                                        + " Encryption algorithm: " + encryptionAlgorithm))
                        .setNullCipherState(
                                eq(mContext),
                                eq(SUB_ID),
                                eq(NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED));
            }
        }
    }

    @Test
    public void onUpdate_enabled_transportLayerEvent_integrityNullCipher_notifyNonEncrypted() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        for (int connectionEvent : TRANSPORT_LAYER_EVENTS) {
            for (int integrityAlgorithm : NULL_CIPHERS) {
                clearInvocations(mSafetySource);
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                                integrityAlgorithm,
                                /* isUnprotectedEmergency= */ false));

                verify(
                        mSafetySource,
                        times(1).description(
                                "Connection event: " + connectionEvent
                                        + " Integrity algorithm: " + integrityAlgorithm))
                        .setNullCipherState(
                                eq(mContext),
                                eq(SUB_ID),
                                eq(NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED));
            }
        }
    }

    @Test
    public void onUpdate_enabled_transportLayerEvent_encryptionNonNullCipher_encrypted() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        for (int connectionEvent : TRANSPORT_LAYER_EVENTS) {
            for (int encryptionAlgorithm : NON_NULL_CIPHERS) {
                clearInvocations(mSafetySource);
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                encryptionAlgorithm,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                                /* isUnprotectedEmergency= */ false));

                verify(
                        mSafetySource,
                        times(1).description(
                                "Connection event: " + connectionEvent
                                        + " Encryption algorithm: " + encryptionAlgorithm))
                        .setNullCipherState(
                                eq(mContext),
                                eq(SUB_ID),
                                eq(NULL_CIPHER_STATE_ENCRYPTED));
            }
        }
    }

    @Test
    public void onUpdate_enabled_transportLayerEvent_integrityNonNullCipher_encrypted() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        for (int connectionEvent : TRANSPORT_LAYER_EVENTS) {
            for (int integrityAlgorithm : NON_NULL_CIPHERS) {
                clearInvocations(mSafetySource);
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                                integrityAlgorithm,
                                /* isUnprotectedEmergency= */ false));

                verify(
                        mSafetySource,
                        times(1).description(
                                "Connection event: " + connectionEvent
                                        + " Integrity algorithm: " + integrityAlgorithm))
                        .setNullCipherState(
                                eq(mContext),
                                eq(SUB_ID),
                                eq(NULL_CIPHER_STATE_ENCRYPTED));
            }
        }
    }

    @Test
    public void onUpdate_enabled_transportLayerEvent_encryptionNonNullCipher_notifyEncrypted() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);

        for (int connectionEvent : TRANSPORT_LAYER_EVENTS) {
            for (int encryptionAlgorithm : NON_NULL_CIPHERS) {
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                NULL_CIPHERS.get(0),
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                                /* isUnprotectedEmergency= */ false));

                clearInvocations(mSafetySource);
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                encryptionAlgorithm,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_HMAC_SHA1_96,
                                /* isUnprotectedEmergency= */ false));

                verify(
                        mSafetySource,
                        times(1).description(
                                "Connection event: " + connectionEvent
                                        + " Encryption algorithm: " + encryptionAlgorithm))
                        .setNullCipherState(
                                eq(mContext),
                                eq(SUB_ID),
                                eq(NULL_CIPHER_STATE_NOTIFY_ENCRYPTED));
            }
        }
    }

    @Test
    public void onUpdate_enabled_transportLayerEvent_integrityNonNullCipher_notifyEncrypted() {
        NullCipherNotifier notifier = new NullCipherNotifier(mExecutor, mSafetySource);
        notifier.enable(mContext);

        for (int connectionEvent : TRANSPORT_LAYER_EVENTS) {
            for (int integrityAlgorithm : NON_NULL_CIPHERS) {
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                                NULL_CIPHERS.get(0),
                                /* isUnprotectedEmergency= */ false));

                clearInvocations(mSafetySource);
                notifier.onSecurityAlgorithmUpdate(
                        mContext,
                        SUB_ID,
                        new SecurityAlgorithmUpdate(
                                connectionEvent,
                                SecurityAlgorithmUpdate.SECURITY_ALGORITHM_EEA2,
                                integrityAlgorithm,
                                /* isUnprotectedEmergency= */ false));

                verify(
                        mSafetySource,
                        times(1).description(
                                "Connection event: " + connectionEvent
                                        + " Integrity algorithm: " + integrityAlgorithm))
                        .setNullCipherState(
                                eq(mContext),
                                eq(SUB_ID),
                                eq(NULL_CIPHER_STATE_NOTIFY_ENCRYPTED));
            }
        }
    }
}
