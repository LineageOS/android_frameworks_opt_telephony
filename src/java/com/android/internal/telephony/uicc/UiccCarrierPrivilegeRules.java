/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.content.pm.Signature;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccUtils;

import java.io.ByteArrayInputStream;
import java.lang.IllegalArgumentException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Class that reads and stores the carrier privileged rules from the UICC.
 *
 * The rules are read when the class is created, hence it should only be created
 * after the UICC can be read. And it should be deleted when a UICC is changed.
 *
 * The spec for the rules:
 *     TODO: Put link here.
 *
 *
 * TODO: Notifications.
 *
 * {@hide}
 */
public class UiccCarrierPrivilegeRules extends Handler {
    private static final String LOG_TAG = "UiccCarrierPrivilegeRules";

    // TODO: These are temporary values. Put real ones here.
    private static final String AID = "A0000000015141434D";
    private static final int CLA = 0x80;
    private static final int COMMAND = 0xCA;
    private static final int P1 = 0xFF;
    private static final int P2 = 0x40;
    private static final int P3 = 0x00;
    private static final String DATA = "";

    // Values from the data standard.
    private static final String TAG_ALL_REF_AR_DO = "FF40";
    private static final String TAG_REF_AR_DO = "E2";
    private static final String TAG_REF_DO = "E1";
    private static final String TAG_AR_DO = "E3";
    private static final String TAG_DEVICE_APP_ID_REF_DO = "C1";
    private static final String TAG_PERM_AR_DO = "DB";
    private static final String TAG_PKG_REF_DO = "CA";

    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 1;
    private static final int EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE = 2;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 3;

    // State of the object.
    private static final int STATE_LOADING  = 0;
    private static final int STATE_LOADED   = 1;
    private static final int STATE_ERROR    = 2;

    // Describes a single rule.
    private static class AccessRule {
        public byte[] certificateHash;
        public String packageName;
        public long accessType;   // This bit is not currently used, but reserved for future use.

        AccessRule(byte[] certificateHash, String packageName, long accessType) {
            this.certificateHash = certificateHash;
            this.packageName = packageName;
            this.accessType = accessType;
        }

        boolean matches(byte[] certHash, String packageName) {
          return certHash != null && Arrays.equals(this.certificateHash, certHash) &&
                (this.packageName == null || this.packageName.equals(packageName));
        }

        @Override
        public String toString() {
            return "cert: " + certificateHash + " pkg: " + packageName +
                " access: " + accessType;
        }
    }

    // Used for parsing the data from the UICC.
    private static class TLV {
        private String tag;
        private Integer length;
        private String value;

        public TLV(String tag) {
            this.tag = tag;
        }

        public String parse(String data, boolean shouldConsumeAll) {
            if (!data.startsWith(tag)) {
                throw new IllegalArgumentException("Tags don't match.");
            }
            int index = tag.length();
            if (index + 2 > data.length()) {
                throw new IllegalArgumentException("No length.");
            }
            length = new Integer(2 * Integer.parseInt(
                    data.substring(index, index + 2), 16));
            index += 2;

            int remainingLength = data.length() - (index + length);
            if (remainingLength < 0) {
                throw new IllegalArgumentException("Not enough data.");
            }
            if (shouldConsumeAll && (remainingLength != 0)) {
                throw new IllegalArgumentException("Did not consume all.");
            }
            value = data.substring(index, index + length);

            Rlog.e(LOG_TAG, "Got TLV: " + tag + "," + length + "," + value);

            return data.substring(index + length);
        }
    }

    private UiccCard mUiccCard;  // Parent
    private AtomicInteger mState;
    private List<AccessRule> mAccessRules;

    public UiccCarrierPrivilegeRules(UiccCard uiccCard) {
        Rlog.d(LOG_TAG, "Creating UiccCarrierPrivilegeRules");
        mUiccCard = uiccCard;
        mState = new AtomicInteger(STATE_LOADING);

        // Start loading the rules.
        mUiccCard.iccOpenLogicalChannel(AID,
            obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, null));
    }

    /**
     * Returns true if the certificate and packageName has carrier privileges.
     *
     * @param signature The signature of the certificate.
     * @param packageName name of the package.
     * @return Access status.
     */
    public int hasCarrierPrivileges(Signature signature, String packageName) {
        Rlog.d(LOG_TAG, "hasCarrierPrivileges: " + signature + " : " + packageName);
        int state = mState.get();
        if (state == STATE_LOADING) {
            Rlog.d(LOG_TAG, "Rules not loaded.");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        } else if (state == STATE_ERROR) {
            Rlog.d(LOG_TAG, "Error loading rules.");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_ERROR_LOADING_RULES;
        }

        byte[] certHash = getCertHash(signature);
        if (certHash == null) {
          return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        }
        Rlog.e(LOG_TAG, "Checking: " + IccUtils.bytesToHexString(certHash) + " : " + packageName);

        for (AccessRule ar : mAccessRules) {
            if (ar.matches(certHash, packageName)) {
                return TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
            }
        }

        Rlog.d(LOG_TAG, "No matching rule found. Returning false.");
        return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {

          case EVENT_OPEN_LOGICAL_CHANNEL_DONE:
              Rlog.d(LOG_TAG, "EVENT_OPEN_LOGICAL_CHANNEL_DONE");
              ar = (AsyncResult) msg.obj;
              if (ar.exception == null && ar.result != null) {
                  int channelId = ((int[]) ar.result)[0];
                  mUiccCard.iccTransmitApduLogicalChannel(channelId, CLA, COMMAND, P1, P2, P3, DATA,
                      obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE, new Integer(channelId)));
              } else {
                  Rlog.e(LOG_TAG, "Error opening channel");
                  mState.set(STATE_ERROR);
              }
              break;

          case EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE:
              Rlog.d(LOG_TAG, "EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE");
              ar = (AsyncResult) msg.obj;
              if (ar.exception == null && ar.result != null) {
                  IccIoResult response = (IccIoResult) ar.result;
                  if (response.payload != null && response.sw1 == 0x90 && response.sw2 == 0x00) {
                      try {
                          mAccessRules = parseRules(IccUtils.bytesToHexString(response.payload));
                          mState.set(STATE_LOADED);
                      } catch (IllegalArgumentException ex) {
                          Rlog.e(LOG_TAG, "Error parsing rules: " + ex);
                          mState.set(STATE_ERROR);
                      }
                   } else {
                      Rlog.e(LOG_TAG, "Invalid response: payload=" + response.payload +
                              " sw1=" + response.sw1 + " sw2=" + response.sw2);
                   }
              } else {
                  Rlog.e(LOG_TAG, "Error reading value from SIM.");
                  mState.set(STATE_ERROR);
              }

              int channelId = (Integer) ar.userObj;
              mUiccCard.iccCloseLogicalChannel(channelId, obtainMessage(
                      EVENT_CLOSE_LOGICAL_CHANNEL_DONE));
              break;

          case EVENT_CLOSE_LOGICAL_CHANNEL_DONE:
              Rlog.d(LOG_TAG, "EVENT_CLOSE_LOGICAL_CHANNEL_DONE");
              break;

          default:
              Rlog.e(LOG_TAG, "Unknown event " + msg.what);
        }
    }

    /*
     * Parses the rules from the input string.
     */
    private static List<AccessRule> parseRules(String rules) {
        rules = rules.toUpperCase(Locale.US);
        Rlog.d(LOG_TAG, "Got rules: " + rules);

        /*
         * Rules format.
         *   ALL_REF_AR_DO = TAG_ALL_REF_AR_DO + len + [REF_AR_DO]xn
         *   REF_AR_DO = TAG_REF_AR_DO + len + REF-DO | AR-DO
         */
        TLV allRefArDo = new TLV(TAG_ALL_REF_AR_DO);
        allRefArDo.parse(rules, true);

        String arDos = allRefArDo.value;
        List<AccessRule> accessRules = new ArrayList<AccessRule>();
        while (!arDos.isEmpty()) {
            TLV refArDo = new TLV(TAG_REF_AR_DO);
            arDos = refArDo.parse(arDos, false);
            accessRules.add(parseRefArdo(refArDo.value));
        }
        return accessRules;
    }

    /*
     * Parses a single rule.
     */
    private static AccessRule parseRefArdo(String rule) {
        Rlog.d(LOG_TAG, "Got rule: " + rule);

        /*
         *   REF_AR_DO = TAG_REF_AR_DO + len + REF-DO | AR-DO
         *   REF_DO = TAG_REF_DO + len + DEVICE_APP_ID_REF_DO | PKG_REF_DO
         *   AR_DO = TAG_AR_DO + len + PERM_AR_DO
         *   DEVICE_APP_ID_REF_DO = TAG_DEVICE_APP_ID_REF_DO + 20 | 0 + 20 byte hash (padded with FF)
         *   PKG_REF_DO = TAG_PKG_REF_DO + 20 + 20 byte hash of package name (padded with FF)
         *   PERM_AR_DO = TAG_PERM_AR_DO + 8 + 8 bytes
         */

        String certificateHash = null;
        String packageName = null;
        long accessType = 0;

        while (!rule.isEmpty()) {
            if (rule.startsWith(TAG_REF_DO)) {
                TLV refDo = new TLV(TAG_REF_DO);
                rule = refDo.parse(rule, false);

                if (refDo.value.startsWith(TAG_DEVICE_APP_ID_REF_DO)) {
                    TLV deviceDo = new TLV(TAG_DEVICE_APP_ID_REF_DO);
                    deviceDo.parse(refDo.value, true);
                    certificateHash = deviceDo.value;
                } else if (refDo.value.startsWith(TAG_PKG_REF_DO)) {
                    TLV pkgDo = new TLV(TAG_PKG_REF_DO);
                    pkgDo.parse(refDo.value, true);
                    packageName = pkgDo.value;
                } else {
                    throw new IllegalArgumentException(
                        "Invalid REF_DO value tag: " + refDo.value);
                }
            } else if (rule.startsWith(TAG_AR_DO)) {
                TLV arDo = new TLV(TAG_AR_DO);
                rule = arDo.parse(rule, false);

                TLV permDo = new TLV(TAG_PERM_AR_DO);
                permDo.parse(arDo.value, true);
                Rlog.e(LOG_TAG, permDo.value);
            } else  {
                // TODO: Mayabe just throw away rules that are not parseable.
                throw new RuntimeException("Invalid Rule type");
            }
        }

        Rlog.e(LOG_TAG, "Adding: " + certificateHash + " : " + packageName + " : " + accessType);

        AccessRule accessRule = new AccessRule(IccUtils.hexStringToBytes(certificateHash),
            packageName, accessType);
        Rlog.e(LOG_TAG, "Parsed rule: " + accessRule);
        return accessRule;
    }

    /*
     * Converts a Signature into a Certificate hash usable for comparison.
     */
    private static byte[] getCertHash(Signature signature) {
        // TODO: Is the following sufficient.
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                    new ByteArrayInputStream(signature.toByteArray()));

            MessageDigest md = MessageDigest.getInstance("SHA");
            return md.digest(cert.getEncoded());
        } catch (CertificateException ex) {
            Rlog.e(LOG_TAG, "CertificateException: " + ex);
        } catch (NoSuchAlgorithmException ex) {
            Rlog.e(LOG_TAG, "NoSuchAlgorithmException: " + ex);
        }

        Rlog.e(LOG_TAG, "Cannot compute cert hash");
        return null;
    }
}
