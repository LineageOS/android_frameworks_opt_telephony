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

package com.android.internal.telephony;

import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.os.UserHandle.SYSTEM;
import static android.telephony.CarrierConfigManager.EXTRA_SLOT_INDEX;
import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.EXTRA_SIM_STATE;
import static android.telephony.TelephonyManager.SIM_STATE_LOADED;
import static android.telephony.TelephonyManager.SIM_STATE_NOT_READY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;

import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierPrivilegesTrackerTest extends TelephonyTest {
    private static final int REGISTRANT_WHAT = 1;
    private static final int PHONE_ID = 2;
    private static final int PHONE_ID_INCORRECT = 3;
    private static final int SUB_ID = 4;

    private static final int USER_ID_1 = 5;
    private static final int USER_ID_2 = 6;

    private static final UserInfo USER_1 = new UserInfo(USER_ID_1, "" /* name */, 0 /* flags */);
    private static final UserInfo USER_2 = new UserInfo(USER_ID_2, "" /* name */, 0 /* flags */);

    private static final String PACKAGE_1 = "android.test.package1";
    private static final String PACKAGE_2 = "android.test.package2";

    private static final String CERT_1 = "11223344";
    private static final String CERT_2 = "AABBCCDD";
    private static final String CERT_3 = "FFFFFFFF";

    private static final String SHA_1 = "SHA-1";

    private static final int UID_1 = 10000001;
    private static final int UID_2 = 10000002;
    private static final int[] PRIVILEGED_UIDS = {UID_1, UID_2};

    private static final int PM_FLAGS =
            PackageManager.MATCH_DISABLED_COMPONENTS
                    | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                    | PackageManager.GET_SIGNING_CERTIFICATES;

    @Mock private Signature mSignature;

    private PersistableBundle mCarrierConfigs;
    private CarrierPrivilegesTrackerTestHandler mHandler;
    private CarrierPrivilegesTracker mCarrierPrivilegesTracker;

    @Before
    public void setUp() throws Exception {
        logd("CarrierPrivilegesTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        when(mPhone.getPhoneId()).thenReturn(PHONE_ID);
        when(mPhone.getSubId()).thenReturn(SUB_ID);

        mCarrierConfigs = new PersistableBundle();
        mHandler = new CarrierPrivilegesTrackerTestHandler();

        // set mock behavior so CarrierPrivilegeTracker initializes with no privileged UIDs
        setupCarrierConfigRules();
        setupSimLoadedRules();
        setupInstalledPackages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /** @param rules can either be "hash" or "hash:package[,package...]" */
    private void setupCarrierConfigRules(String... rules) {
        mCarrierConfigs.putStringArray(KEY_CARRIER_CERTIFICATE_STRING_ARRAY, rules);
        mCarrierConfigs.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(mCarrierConfigs);
    }

    private static String carrierConfigRuleString(String certificateHash, String... packageNames) {
        if (packageNames == null || packageNames.length == 0) {
            return certificateHash;
        }
        return certificateHash + ':' + String.join(",", packageNames);
    }

    private void setupSimLoadedRules(UiccAccessRule... certHashes) {
        when(mTelephonyManager.hasIccCard(PHONE_ID)).thenReturn(true);
        when(mUiccProfile.getCarrierPrivilegeAccessRules()).thenReturn(Arrays.asList(certHashes));
    }

    private static UiccAccessRule ruleWithHashOnly(String certificateHash) {
        return ruleWithHashAndPackage(certificateHash, null /* packageName */);
    }

    private static UiccAccessRule ruleWithHashAndPackage(
            String certificateHash, String packageName) {
        return new UiccAccessRule(
                IccUtils.hexStringToBytes(certificateHash), packageName, /* accessType= */ 0L);
    }

    private void setupInstalledPackages(PackageCertInfo... pkgCertInfos) throws Exception {
        Set<UserInfo> users = new ArraySet<>();
        List<PackageInfo> installedPackages = new ArrayList<>();
        for (PackageCertInfo pkgCertInfo : pkgCertInfos) {
            users.add(pkgCertInfo.userInfo);

            PackageInfo pkg = new PackageInfo();
            pkg.packageName = pkgCertInfo.pkgName;
            pkg.signatures = new Signature[] {new Signature(pkgCertInfo.cert)};

            when(mPackageManager.getPackageInfo(
                            eq(pkgCertInfo.pkgName), eq(GET_SIGNING_CERTIFICATES)))
                    .thenReturn(pkg);
            when(mPackageManager.getPackageUidAsUser(
                            eq(pkgCertInfo.pkgName), eq(pkgCertInfo.userInfo.id)))
                    .thenReturn(pkgCertInfo.uid);
            installedPackages.add(pkg);
        }
        when(mUserManager.getUsers()).thenReturn(new ArrayList<>(users));
        when(mPackageManager.getInstalledPackagesAsUser(eq(PM_FLAGS), eq(SYSTEM.getIdentifier())))
                .thenReturn(installedPackages);
    }

    /**
     * Creates and returns a CarrierPrivilegesTracker instance.
     *
     * <p>The initial configuration of the CarrierPrivilegesTracker will be based on the current
     * state of certificate hashes and installed packages.
     *
     * <p>See {@link #setupCarrierConfigRules}, {@link #setupSimLoadedRules}, {@link
     * #setupInstalledPackages}.
     */
    private CarrierPrivilegesTracker createCarrierPrivilegesTracker() throws Exception {
        CarrierPrivilegesTracker cpt =
                new CarrierPrivilegesTracker(mTestableLooper.getLooper(), mPhone, mContext);
        mTestableLooper.processAllMessages();

        cpt.registerCarrierPrivilegesListener(mHandler, REGISTRANT_WHAT, null);
        mTestableLooper.processAllMessages();
        mHandler.reset();

        return cpt;
    }

    private void setupCarrierPrivilegesTrackerWithCarrierConfigUids() throws Exception {
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
    }

    private void setupCarrierPrivilegesTrackerWithSimLoadedUids() throws Exception {
        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)), ruleWithHashOnly(getHash(CERT_2)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
    }

    private class CarrierPrivilegesTrackerTestHandler extends Handler {
        public int[] privilegedUids;
        public int numUidUpdates;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REGISTRANT_WHAT:
                    AsyncResult asyncResult = (AsyncResult) msg.obj;
                    privilegedUids = (int[]) asyncResult.result;
                    numUidUpdates++;
                    break;
                default:
                    fail("Unexpected msg received. what=" + msg.what);
            }
        }

        void reset() {
            privilegedUids = null;
            numUidUpdates = 0;
        }
    }

    private void verifyPrivilegedUids(@Nullable int[] expectedUids, int expectedUidUpdates) {
        assertArrayEquals(expectedUids, mHandler.privilegedUids);
        assertEquals(expectedUidUpdates, mHandler.numUidUpdates);
    }

    private void verifyRegisterListener(int[] expectedUids, int expectedUidUpdates)
            throws Exception {
        // mHandler registered in createCarrierPrivilegesTracker(), so reset it
        mHandler = new CarrierPrivilegesTrackerTestHandler();

        mCarrierPrivilegesTracker.registerCarrierPrivilegesListener(
                mHandler, REGISTRANT_WHAT, null);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(expectedUids, expectedUidUpdates);
    }

    @Test
    public void testRegisterListener() throws Exception {
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        verifyRegisterListener(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testUnregisterListener() throws Exception {
        // Start with privileges. Verify no updates received after clearing UIDs.
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        verifyRegisterListener(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
        mHandler.reset();

        mCarrierPrivilegesTracker.unregisterCarrierPrivilegesListener(mHandler);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(null /* expectedUids */, 0 /* expectedUidUpdates */);

        // Clear UIDs
        sendCarrierConfigChangedIntent(INVALID_SUBSCRIPTION_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(null /* expectedUids */, 0 /* expectedUidUpdates */);
    }

    @Test
    public void testCarrierConfigUpdated() throws Exception {
        // Start with packages installed and no certs
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testCarrierConfigUpdatedMismatchedSlotIndex() throws Exception {
        // Start with privileges. Incorrect phoneId shouldn't affect certs
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID_INCORRECT);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(null /* expectedUids */, 0 /* expectedUidUpdates */);
    }

    @Test
    public void testCarrierConfigUpdatedInvalidSubId() throws Exception {
        // Start with privileges, verify clearing certs clears UIDs
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        sendCarrierConfigChangedIntent(INVALID_SUBSCRIPTION_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testCarrierConfigUpdatedNotIdentifiedCarrier() throws Exception {
        // Start with privileges, verify clearing certs clears UIDs
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        mCarrierConfigs.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(mCarrierConfigs);

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testCarrierConfigUpdatedExplicitPackageNames() throws Exception {
        // Start with privileges specified just by wildcard certificate hashes, verify specifying
        // package names clears privileges on UIDs that don't match the updated rules.
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        // Package 1 keeps its privileges by matching the first rule; the second rule no longer
        // matches package 2.
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1), PACKAGE_1),
                carrierConfigRuleString(getHash(CERT_2), PACKAGE_1));

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[] {UID_1} /* expectedUids */, 1 /* expectedUidUpdates */);

        // Give package 2 privileges again.
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1), PACKAGE_1),
                carrierConfigRuleString(getHash(CERT_2), PACKAGE_1, PACKAGE_2));

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS /* expectedUids */, 2 /* expectedUidUpdates */);
    }

    @Test
    public void testSimCardStateChanged() throws Exception {
        // Start with packages installed and no certs
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)), ruleWithHashOnly(getHash(CERT_2)));

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testSimApplicationStateChanged() throws Exception {
        // Start with packages installed and no certs
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)), ruleWithHashOnly(getHash(CERT_2)));

        sendSimApplicationStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testSimStateChangedWithoutIccCard() throws Exception {
        // Start with privileges, verify no Icc Card clears UIDs
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        when(mTelephonyManager.hasIccCard(PHONE_ID)).thenReturn(false);

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testSimStateChangedMismatchedSlotIndex() throws Exception {
        // Start with privileges. Incorrect phoneId shouldn't affect certs
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        when(mTelephonyManager.hasIccCard(PHONE_ID)).thenReturn(false);

        sendSimCardStateChangedIntent(PHONE_ID_INCORRECT, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(null /* expectedUids */, 0 /* expectedUidUpdates */);
    }

    @Test
    public void testSimStateChangedSimStateNotReady() throws Exception {
        // Start with privileges, verify clearing certs clears UIDs
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_NOT_READY);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testSimStateChangedExplicitPackageNames() throws Exception {
        // Start with privileges specified just by wildcard certificate hashes, verify specifying
        // package names clears privileges on UIDs that don't match the updated rules.
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        // Package 1 keeps its privileges by matching the first rule; the second rule no longer
        // matches package 2.
        setupSimLoadedRules(
                ruleWithHashAndPackage(getHash(CERT_1), PACKAGE_1),
                ruleWithHashAndPackage(getHash(CERT_2), PACKAGE_1));

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[] {UID_1} /* expectedUids */, 1 /* expectedUidUpdates */);

        // Give package 2 privileges again.
        setupSimLoadedRules(
                ruleWithHashAndPackage(getHash(CERT_1), PACKAGE_1),
                ruleWithHashAndPackage(getHash(CERT_2), PACKAGE_1),
                ruleWithHashAndPackage(getHash(CERT_2), PACKAGE_2));

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS /* expectedUids */, 2 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageAdded() throws Exception {
        // Start with certs and no packages installed
        setupCarrierConfigRules(carrierConfigRuleString(getHash(CERT_1)));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        setupInstalledPackages(new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1));

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[] {UID_1}, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageAddedMultipleUsers() throws Exception {
        // Start with certs and no packages installed
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_1, CERT_2, USER_2, UID_2));

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageReplaced() throws Exception {
        // Start with certs and an unmatched package
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));
        setupInstalledPackages(new PackageCertInfo(PACKAGE_1, CERT_3, USER_1, UID_1));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_1, CERT_2, USER_2, UID_2));

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_REPLACED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageAddedOrReplacedNoSignatures() throws Exception {
        // Start with certs and packages installed
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        // Update PACKAGE_1 to have no signatures
        PackageInfo pkg = new PackageInfo();
        pkg.packageName = PACKAGE_1;
        when(mPackageManager.getPackageInfo(eq(PACKAGE_1), eq(GET_SIGNING_CERTIFICATES)))
                .thenReturn(pkg);

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[] {UID_2} /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageAddedOrReplacedSignatureChanged() throws Exception {
        // Start with certs and packages installed
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        // Update PACKAGE_1 to have a different signature
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_3, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[] {UID_2} /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageRemoved() throws Exception {
        // Start with certs and packages installed
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1)), carrierConfigRuleString(getHash(CERT_2)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_REMOVED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(new int[] {UID_2} /* expectedUids */, 1 /* expectedUidUpdates */);
    }

    @Test
    public void testPackageRemovedNoChanges() throws Exception {
        // Start with packages installed and no certs
        setupInstalledPackages(new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_REMOVED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyPrivilegedUids(null /* expectedUids */, 0 /* expectedUidUpdates */);
    }

    private void sendCarrierConfigChangedIntent(int subId, int phoneId) {
        mContext.sendBroadcast(
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)
                        .putExtra(EXTRA_SUBSCRIPTION_INDEX, subId)
                        .putExtra(EXTRA_SLOT_INDEX, phoneId));
    }

    private void sendSimCardStateChangedIntent(int phoneId, int simState) {
        mContext.sendBroadcast(
                new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)
                        .putExtra(EXTRA_SIM_STATE, simState)
                        .putExtra(PhoneConstants.PHONE_KEY, phoneId));
    }

    private void sendSimApplicationStateChangedIntent(int phoneId, int simState) {
        mContext.sendBroadcast(
                new Intent(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED)
                        .putExtra(EXTRA_SIM_STATE, simState)
                        .putExtra(PhoneConstants.PHONE_KEY, phoneId));
    }

    private void sendPackageChangedIntent(String action, String pkgName) {
        mContext.sendBroadcast(new Intent(action, new Uri.Builder().path(pkgName).build()));
    }

    /** Returns the SHA-1 hash (as a hex String) for the given hex String. */
    private static String getHash(String hexString) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance(SHA_1);
        byte[] result = sha1.digest(IccUtils.hexStringToBytes(hexString));
        return IccUtils.bytesToHexString(result);
    }

    private class PackageCertInfo {
        public final String pkgName;
        public final String cert;
        public final UserInfo userInfo;
        public final int uid;

        PackageCertInfo(String pkgName, String cert, UserInfo userInfo, int uid) {
            this.pkgName = pkgName;
            this.cert = cert;
            this.userInfo = userInfo;
            this.uid = uid;
        }
    }
}
