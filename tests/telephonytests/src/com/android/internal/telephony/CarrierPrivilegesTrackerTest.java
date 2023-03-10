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

import static android.os.UserHandle.SYSTEM;
import static android.telephony.CarrierConfigManager.EXTRA_SLOT_INDEX;
import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
import static android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
import static android.telephony.TelephonyManager.EXTRA_SIM_STATE;
import static android.telephony.TelephonyManager.SIM_STATE_ABSENT;
import static android.telephony.TelephonyManager.SIM_STATE_LOADED;
import static android.telephony.TelephonyManager.SIM_STATE_NOT_READY;
import static android.telephony.TelephonyManager.SIM_STATE_READY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Process;
import android.service.carrier.CarrierService;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.Pair;

import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private static final String PACKAGE_3 = "android.test.package3";
    private static final String PACKAGE_4 = "android.test.package4";
    private static final String PACKAGE_5 = "android.test.package5";
    private static final String PACKAGE_6 = "android.test.package6";
    private static final String PACKAGE_7 = "android.test.package7";
    private static final String PACKAGE_8 = "android.test.package8";
    private static final Set<String> PRIVILEGED_PACKAGES = Set.of(PACKAGE_1, PACKAGE_2);

    private static final String CERT_1 = "11223344";
    private static final String CERT_2 = "AABBCCDD";
    private static final String CERT_3 = "FFFFFFFF";

    private static final String SHA_1 = "SHA-1";

    private static final int UID_1 = 10000001;
    private static final int UID_2 = 10000002;
    private static final int UID_3 = 10000003;
    private static final int[] PRIVILEGED_UIDS = {UID_1, UID_2};
    private static final Set<Integer> PRIVILEGED_UIDS_SET = Set.of(UID_1, UID_2);

    private static final int PM_FLAGS =
            PackageManager.GET_SIGNING_CERTIFICATES
                    | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                    | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS;

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
        mHandler = null;
        mCarrierPrivilegesTracker = null;
        mCarrierConfigs =  null;
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
                    eq(pkgCertInfo.pkgName), eq(PM_FLAGS)))
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
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_3, CERT_3, USER_1, UID_3));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
    }

    private void setupCarrierPrivilegesTrackerWithSimLoadedUids() throws Exception {
        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)), ruleWithHashOnly(getHash(CERT_2)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_3, CERT_3, USER_1, UID_3));
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

    private void verifyCurrentState(Set<String> expectedPackageNames, int[] expectedUids) {
        assertEquals(
                expectedPackageNames, mCarrierPrivilegesTracker.getPackagesWithCarrierPrivileges());
        for (String packageName : expectedPackageNames) {
            assertEquals(
                    CARRIER_PRIVILEGE_STATUS_HAS_ACCESS,
                    mCarrierPrivilegesTracker.getCarrierPrivilegeStatusForPackage(packageName));
        }
        for (int uid : expectedUids) {
            assertEquals(
                    CARRIER_PRIVILEGE_STATUS_HAS_ACCESS,
                    mCarrierPrivilegesTracker.getCarrierPrivilegeStatusForUid(uid));
        }
    }

    private void verifyRegistrantUpdates(@Nullable int[] expectedUids, int expectedUidUpdates) {
        assertArrayEquals(expectedUids, mHandler.privilegedUids);
        assertEquals(expectedUidUpdates, mHandler.numUidUpdates);
    }

    private void verifyCarrierPrivilegesChangedUpdates(
            List<Pair<Set<String>, Set<Integer>>> expectedUpdates) {
        if (expectedUpdates.isEmpty()) {
            verify(mTelephonyRegistryManager, never())
                    .notifyCarrierPrivilegesChanged(anyInt(), any(), any());
        } else {
            InOrder inOrder = inOrder(mTelephonyRegistryManager);
            for (Pair<Set<String>, Set<Integer>> expectedUpdate : expectedUpdates) {
                // By looking at TelephonyRegistryManager, we can see the full flow as it evolves.
                inOrder.verify(mTelephonyRegistryManager)
                        .notifyCarrierPrivilegesChanged(
                                eq(PHONE_ID),
                                eq(expectedUpdate.first),
                                eq(expectedUpdate.second));
            }
        }
    }

    private void verifyCarrierServicesChangedUpdates(List<Pair<String, Integer>> expectedUpdates) {
        if (expectedUpdates.isEmpty()) {
            verify(mTelephonyRegistryManager, never())
                    .notifyCarrierPrivilegesChanged(anyInt(), any(), any());
        } else {
            InOrder inOrder = inOrder(mTelephonyRegistryManager);
            for (Pair<String, Integer> expectedUpdate : expectedUpdates) {
                // By looking at TelephonyRegistryManager, we can see the full flow as
                // it evolves.
                inOrder.verify(mTelephonyRegistryManager)
                        .notifyCarrierServiceChanged(
                                eq(PHONE_ID), eq(expectedUpdate.first), eq(expectedUpdate.second));
            }
        }
    }

    @Test
    public void testRegisterListener() throws Exception {
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
        // mHandler registered in createCarrierPrivilegesTracker(), so reset it
        mHandler = new CarrierPrivilegesTrackerTestHandler();

        mCarrierPrivilegesTracker.registerCarrierPrivilegesListener(
                mHandler, REGISTRANT_WHAT, null);
        mTestableLooper.processAllMessages();

        // No updates triggered, but the registrant gets an empty update.
        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(List.of());
    }

    @Test
    public void testUnregisterListener() throws Exception {
        // Start with privileges. Verify no updates received after clearing UIDs.
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();
        // mHandler registered in createCarrierPrivilegesTracker(), so reset it
        mHandler = new CarrierPrivilegesTrackerTestHandler();

        mCarrierPrivilegesTracker.registerCarrierPrivilegesListener(
                mHandler, REGISTRANT_WHAT, null);
        mTestableLooper.processAllMessages();

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS /* expectedUids */, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
        mHandler.reset();
        reset(mTelephonyRegistryManager);

        mCarrierPrivilegesTracker.unregisterCarrierPrivilegesListener(mHandler);
        mTestableLooper.processAllMessages();

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(null /* expectedUids */, 0 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(List.of());

        // Clear UIDs
        sendCarrierConfigChangedIntent(INVALID_SUBSCRIPTION_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(null /* expectedUids */, 0 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(List.of(new Pair<>(Set.of(), Set.of())));
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

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    @Test
    public void testCarrierConfigUpdatedMismatchedSlotIndex() throws Exception {
        // Start with privileges. Incorrect phoneId shouldn't affect certs
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID_INCORRECT);
        mTestableLooper.processAllMessages();

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(null /* expectedUids */, 0 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    @Test
    public void testCarrierConfigUpdatedInvalidSubId() throws Exception {
        // Start with privileges, verify clearing certs clears UIDs
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        sendCarrierConfigChangedIntent(INVALID_SUBSCRIPTION_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(
                        new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET),
                        new Pair<>(Set.of(), Set.of())));
    }

    @Test
    public void testCarrierConfigUpdatedNotIdentifiedCarrier() throws Exception {
        // Start with privileges, verify clearing certs clears UIDs
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        mCarrierConfigs.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(mCarrierConfigs);

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0] /* expectedUids */, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(List.of(new Pair<>(Set.of(), Set.of())));
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

        verifyCurrentState(Set.of(PACKAGE_1), new int[] {UID_1});
        verifyRegistrantUpdates(new int[] {UID_1}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), Set.of(UID_1))));

        // Give package 2 privileges again.
        setupCarrierConfigRules(
                carrierConfigRuleString(getHash(CERT_1), PACKAGE_1),
                carrierConfigRuleString(getHash(CERT_2), PACKAGE_1, PACKAGE_2));

        sendCarrierConfigChangedIntent(SUB_ID, PHONE_ID);
        mTestableLooper.processAllMessages();

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 2 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
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

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
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

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    @Test
    public void testSimStateChangedWithoutIccCard() throws Exception {
        // Start with privileges, verify no Icc Card clears UIDs
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        when(mTelephonyManager.hasIccCard(PHONE_ID)).thenReturn(false);

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0], 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(
                        new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET),
                        new Pair<>(Set.of(), Set.of())));
    }

    @Test
    public void testSimStateChangedMismatchedSlotIndex() throws Exception {
        // Start with privileges. Incorrect phoneId shouldn't affect certs
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        when(mTelephonyManager.hasIccCard(PHONE_ID)).thenReturn(false);

        sendSimCardStateChangedIntent(PHONE_ID_INCORRECT, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(null /* expectedUids */, 0 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    // TODO(b/232273884): turn UT case on when grace period is on
    @Ignore
    public void testSimStateChangedSimStateNotReady() throws Exception {
        // Start with privileges, verify clearing certs clears UIDs
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_NOT_READY);
        mTestableLooper.processAllMessages();

        // Immediately check current state, nothing should change
        verifyCurrentState(Set.of(PACKAGE_1, PACKAGE_2), new int[]{UID_1, UID_2});

        // Wait for 30 seconds
        moveTimeForward(TimeUnit.SECONDS.toMillis(30));
        mTestableLooper.processAllMessages();

        // Check again, the carrier privileges should be emptied
        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0], 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(
                        new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET),
                        new Pair<>(Set.of(), Set.of())));
    }

    // TODO(b/232273884): turn UT case on when grace period is on
    @Ignore
    public void testSimStateChangedSimStateAbsentThenLoadedWithSameRules() throws Exception {
        // Start with privileges
        setupCarrierPrivilegesTrackerWithSimLoadedUids();
        // CPT initialization process may trigger notification, remove the interfere here
        reset(mTelephonyRegistryManager);

        // SIM is removed
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_ABSENT);
        mTestableLooper.processAllMessages();


        // Wait for 20 seconds and the same SIM is inserted
        moveTimeForward(TimeUnit.SECONDS.toMillis(20));
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();
        // Wait for another 20 seconds
        moveTimeForward(TimeUnit.SECONDS.toMillis(20));

        // verify all carrier privileges should remain, no CP change notified
        verifyCurrentState(Set.of(PACKAGE_1, PACKAGE_2), new int[]{UID_1, UID_2});
        verifyRegistrantUpdates(null /* expectedUidUpdates */, 0 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(List.of());
    }

    @Test
    public void testSimStateChangedSimStateAbsentForever() throws Exception {
        // Start with privileges
        setupCarrierPrivilegesTrackerWithSimLoadedUids();
        // CPT initialization process may trigger notification, remove the interfere here
        reset(mTelephonyRegistryManager);

        // SIM is removed
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_ABSENT);
        mTestableLooper.processAllMessages();

        // Wait for 30 seconds
        moveTimeForward(TimeUnit.SECONDS.toMillis(30));
        mTestableLooper.processAllMessages();

        // verify the carrier privileges should be emptied
        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0], 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(), Set.of())));
    }

    @Test
    public void testSimStateChangedSimStateNotReadyForever() throws Exception {
        // Start with privileges
        setupCarrierPrivilegesTrackerWithSimLoadedUids();
        // CPT initialization process may trigger notification, remove the interfere here
        reset(mTelephonyRegistryManager);

        // eSIM profile disabled and leave in state SIM_STATE_NOT_READY
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_NOT_READY);
        mTestableLooper.processAllMessages();

        // Wait for 30 seconds
        moveTimeForward(TimeUnit.SECONDS.toMillis(30));
        mTestableLooper.processAllMessages();

        // verify the carrier privileges should be emptied
        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0], 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(), Set.of())));
    }

    // TODO(b/232273884): turn UT case on when grace period is on
    @Ignore
    public void testSimStateChangedSimStateAbsentThenLoadedWithUpdatedRules() throws Exception {
        // Start with privileges
        setupCarrierPrivilegesTrackerWithSimLoadedUids();
        reset(mTelephonyRegistryManager);

        // SIM is removed
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_ABSENT);
        mTestableLooper.processAllMessages();


        // Wait for 20 seconds and a different SIM is inserted
        moveTimeForward(TimeUnit.SECONDS.toMillis(20));
        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)));
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();
        // Wait for another 20 seconds
        moveTimeForward(TimeUnit.SECONDS.toMillis(20));

        // Carrier privileges should be updated and CP change should be notified
        verifyCurrentState(Set.of(PACKAGE_1), new int[] {UID_1});
        verifyRegistrantUpdates(new int[] {UID_1}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), Set.of(UID_1))));
    }

    @Test
    public void testSimStateChangedSimStateReadyThenLoaded() throws Exception {
        // Start with privileges (from carrier config)
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        ResolveInfo pkg1ResolveInfo = new ResolveInfoBuilder().setActivity(PACKAGE_1).build();
        ResolveInfo pkg2ResolveInfo = new ResolveInfoBuilder().setActivity(PACKAGE_2).build();
        when(mPackageManager.queryBroadcastReceivers(any(), anyInt())).thenReturn(
                List.of(pkg1ResolveInfo, pkg2ResolveInfo));

        // SIM is READY
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_READY);
        mTestableLooper.processAllMessages();

        assertEquals(Collections.emptyList(),
                mCarrierPrivilegesTracker.getCarrierPackageNamesForIntent(
                        new Intent(CarrierService.CARRIER_SERVICE_INTERFACE)));
        assertEquals(CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED,
                mCarrierPrivilegesTracker.getCarrierPrivilegeStatusForUid(UID_1));
        assertEquals(Collections.EMPTY_SET,
                mCarrierPrivilegesTracker.getPackagesWithCarrierPrivileges());
        assertEquals(CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED,
                mCarrierPrivilegesTracker.getCarrierPrivilegeStatusForPackage(PACKAGE_1));

        // SIM is LOADED
        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        assertEquals(List.of(PACKAGE_1, PACKAGE_2),
                mCarrierPrivilegesTracker.getCarrierPackageNamesForIntent(
                        new Intent(CarrierService.CARRIER_SERVICE_INTERFACE)));
        assertEquals(CARRIER_PRIVILEGE_STATUS_HAS_ACCESS,
                mCarrierPrivilegesTracker.getCarrierPrivilegeStatusForUid(UID_1));
        assertEquals(PRIVILEGED_PACKAGES,
                mCarrierPrivilegesTracker.getPackagesWithCarrierPrivileges());
        assertEquals(CARRIER_PRIVILEGE_STATUS_HAS_ACCESS,
                mCarrierPrivilegesTracker.getCarrierPrivilegeStatusForPackage(PACKAGE_1));
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

        verifyCurrentState(Set.of(PACKAGE_1), new int[] {UID_1});
        verifyRegistrantUpdates(new int[] {UID_1}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), Set.of(UID_1))));

        // Give package 2 privileges again.
        setupSimLoadedRules(
                ruleWithHashAndPackage(getHash(CERT_1), PACKAGE_1),
                ruleWithHashAndPackage(getHash(CERT_2), PACKAGE_1),
                ruleWithHashAndPackage(getHash(CERT_2), PACKAGE_2));

        sendSimCardStateChangedIntent(PHONE_ID, SIM_STATE_LOADED);
        mTestableLooper.processAllMessages();

        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 2 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    @Test
    public void testPackageAdded() throws Exception {
        // Start with certs and no packages installed
        setupCarrierConfigRules(carrierConfigRuleString(getHash(CERT_1)));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        setupInstalledPackages(new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1));

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(PACKAGE_1), new int[] {UID_1});
        verifyRegistrantUpdates(new int[] {UID_1}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), Set.of(UID_1))));
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

        verifyCurrentState(Set.of(PACKAGE_1), PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), PRIVILEGED_UIDS_SET)));
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

        verifyCurrentState(Set.of(PACKAGE_1), PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), PRIVILEGED_UIDS_SET)));
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
        when(mPackageManager.getPackageInfo(eq(PACKAGE_1), eq(PM_FLAGS)))
                .thenReturn(pkg);

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(PACKAGE_2), new int[] {UID_2});
        verifyRegistrantUpdates(new int[] {UID_2}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_2), Set.of(UID_2))));
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

        verifyCurrentState(Set.of(PACKAGE_2), new int[] {UID_2});
        verifyRegistrantUpdates(new int[] {UID_2}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_2), Set.of(UID_2))));
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

        verifyCurrentState(Set.of(PACKAGE_2), new int[] {UID_2});
        verifyRegistrantUpdates(new int[] {UID_2}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_2), Set.of(UID_2))));
    }

    @Test
    public void testPackageRemovedNoChanges() throws Exception {
        // Start with packages installed and no certs
        setupInstalledPackages(new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1));
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        sendPackageChangedIntent(Intent.ACTION_PACKAGE_REMOVED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(null /* expectedUidUpdates */, 0 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(List.of());
    }

    @Test
    public void testPackageDisabledAndThenEnabled() throws Exception {
        // Start with certs and packages installed
        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)));
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2));
        when(mPackageManager.getPackageUid(eq(PACKAGE_1), anyInt())).thenReturn(UID_1);
        when(mPackageManager.getPackageUid(eq(PACKAGE_2), anyInt())).thenReturn(UID_2);
        ResolveInfo resolveInfoPkg1 = new ResolveInfoBuilder().setService(PACKAGE_1).build();
        doReturn(List.of(resolveInfoPkg1))
                .when(mPackageManager)
                .queryIntentServices(any(), anyInt());
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();

        // Package_1 is disabled
        when(mPackageManager.getApplicationEnabledSetting(eq(PACKAGE_1))).thenReturn(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
        doReturn(List.of()).when(
                mPackageManager).queryIntentServices(any(), anyInt());
        sendPackageChangedIntent(Intent.ACTION_PACKAGE_CHANGED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(), new int[0]);
        verifyCarrierPrivilegesChangedUpdates(List.of(new Pair<>(Set.of(), Set.of())));
        verifyCarrierServicesChangedUpdates(List.of(new Pair<>(null, -1)));

        // Package_1 is re-enabled
        when(mPackageManager.getApplicationEnabledSetting(eq(PACKAGE_1))).thenReturn(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        doReturn(List.of(resolveInfoPkg1)).when(
                mPackageManager).queryIntentServices(any(), anyInt());
        sendPackageChangedIntent(Intent.ACTION_PACKAGE_CHANGED, PACKAGE_1);
        mTestableLooper.processAllMessages();

        verifyCurrentState(Set.of(PACKAGE_1), new int[] {UID_1});
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(Set.of(PACKAGE_1), Set.of(UID_1))));
        verifyCarrierServicesChangedUpdates(List.of(new Pair<>(PACKAGE_1, UID_1)));
    }

    @Test
    public void testSetCarrierTestOverrideWithEmptyRule() throws Exception {
        // Start with PACKAGE_1 & PACKAGE_2 installed and have privileges from CarrierConfig
        setupCarrierPrivilegesTrackerWithCarrierConfigUids();

        // Set test override with EMPTY rule
        mCarrierPrivilegesTracker.setTestOverrideCarrierPrivilegeRules("");
        mTestableLooper.processAllMessages();

        // Expect no package will have privilege at last
        verifyCurrentState(Set.of(), new int[0]);
        verifyRegistrantUpdates(new int[0], 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(
                        new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET),
                        new Pair<>(Set.of(), Set.of())));

        // Set test override with null rule to revoke previous test override
        mCarrierPrivilegesTracker.setTestOverrideCarrierPrivilegeRules(null);
        mTestableLooper.processAllMessages();

        // Expect all privileges from Carrier Config come back
        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 2 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    @Test
    public void testSetCarrierTestOverrideWithNonEmptyRule() throws Exception {
        // Start with PACKAGE_1 & PACKAGE_2 installed and have privileges from UICC
        setupCarrierPrivilegesTrackerWithSimLoadedUids();

        // Set test override with non-EMPTY rule (PACKAGE_3)
        mCarrierPrivilegesTracker.setTestOverrideCarrierPrivilegeRules(getHash(CERT_3));
        mTestableLooper.processAllMessages();

        // Expect only PACKAGE_3 will have privilege at last
        verifyCurrentState(Set.of(PACKAGE_3), new int[]{UID_3});
        verifyRegistrantUpdates(new int[]{UID_3}, 1 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(
                        new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET),
                        new Pair<>(Set.of(PACKAGE_3), Set.of(UID_3))));


        // Set test override with null rule to revoke previous test override
        mCarrierPrivilegesTracker.setTestOverrideCarrierPrivilegeRules(null);
        mTestableLooper.processAllMessages();

        // Expect all privileges from UICC come back
        verifyCurrentState(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS);
        verifyRegistrantUpdates(PRIVILEGED_UIDS, 2 /* expectedUidUpdates */);
        verifyCarrierPrivilegesChangedUpdates(
                List.of(new Pair<>(PRIVILEGED_PACKAGES, PRIVILEGED_UIDS_SET)));
    }

    @Test
    public void testGetCarrierPackageNameForIntent() throws Exception {
        // Only packages with CERT_1 have carrier privileges
        setupCarrierConfigRules(carrierConfigRuleString(getHash(CERT_1)));
        // Setup all odd packages privileged, even packages not
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_3, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_4, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_5, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_6, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_7, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_8, CERT_2, USER_1, UID_2));

        ResolveInfo privilegeBroadcast = new ResolveInfoBuilder().setActivity(PACKAGE_1).build();
        ResolveInfo noPrivilegeBroadcast = new ResolveInfoBuilder().setActivity(PACKAGE_2).build();
        when(mPackageManager.queryBroadcastReceivers(any(), anyInt())).thenReturn(
                List.of(privilegeBroadcast, noPrivilegeBroadcast));

        ResolveInfo privilegeActivity = new ResolveInfoBuilder().setActivity(PACKAGE_3).build();
        ResolveInfo noPrivilegeActivity = new ResolveInfoBuilder().setActivity(PACKAGE_4).build();
        when(mPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(
                List.of(privilegeActivity, noPrivilegeActivity));

        ResolveInfo privilegeService = new ResolveInfoBuilder().setService(PACKAGE_5).build();
        ResolveInfo noPrivilegeService = new ResolveInfoBuilder().setService(PACKAGE_6).build();
        // Use doReturn instead of when/thenReturn which has NPE with unknown reason
        doReturn(List.of(privilegeService, noPrivilegeService)).when(
                mPackageManager).queryIntentServices(any(), anyInt());

        ResolveInfo privilegeProvider = new ResolveInfoBuilder().setProvider(PACKAGE_7).build();
        ResolveInfo noPrivilegeProvider = new ResolveInfoBuilder().setProvider(PACKAGE_8).build();
        when(mPackageManager.queryIntentContentProviders(any(), anyInt())).thenReturn(
                List.of(privilegeProvider, noPrivilegeProvider));

        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
        Intent intent = new Intent(CarrierService.CARRIER_SERVICE_INTERFACE);
        List<String> carrierPackageNames =
                mCarrierPrivilegesTracker.getCarrierPackageNamesForIntent(intent);
        mTestableLooper.processAllMessages();

        // Order of the result packages doesn't matter. Comparing the Set instead of the List
        assertEquals(Set.of(PACKAGE_1, PACKAGE_3, PACKAGE_5, PACKAGE_7),
                new HashSet<>(carrierPackageNames));
    }

    @Test
    public void testGetCarrierService_haveCarrierServiceWithSimCarrierPrivileges()
            throws Exception {
        // Package 1 has SIM loaded rules, making it eligible for carrier service bindings
        setupSimLoadedRules(ruleWithHashOnly(getHash(CERT_1)));
        // Package 2 has only carrier-config based rules, which is insufficient for carrier services
        setupCarrierConfigRules(carrierConfigRuleString(getHash(CERT_2)));
        // Setup all odd packages privileged, even packages not
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_3, CERT_1, USER_1, UID_1));
        // Two declared CarrierService, only PACKAGE_1 has carrier privileges
        ResolveInfo privilegeService = new ResolveInfoBuilder().setService(PACKAGE_1).build();
        ResolveInfo noPrivilegeService = new ResolveInfoBuilder().setService(PACKAGE_2).build();
        // Use doReturn instead of when/thenReturn which has NPE with unknown reason
        doReturn(List.of(privilegeService, noPrivilegeService)).when(
                mPackageManager).queryIntentServices(any(), anyInt());
        when(mPackageManager.getPackageUid(eq(PACKAGE_1), anyInt())).thenReturn(UID_1);
        when(mPackageManager.getPackageUid(eq(PACKAGE_2), anyInt())).thenReturn(UID_2);
        when(mPackageManager.getPackageUid(eq(PACKAGE_3), anyInt())).thenReturn(UID_1);

        // Get CS package name for the first time
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
        String carrierServicePackageName = mCarrierPrivilegesTracker.getCarrierServicePackageName();
        int carrierServiceUid = mCarrierPrivilegesTracker.getCarrierServicePackageUid();
        mTestableLooper.processAllMessages();

        // Package manager should be queried from
        verify(mPackageManager).queryIntentServices(any(), anyInt());
        assertEquals(PACKAGE_1, carrierServicePackageName);
        assertEquals(UID_1, carrierServiceUid);

        reset(mPackageManager);
        // Get CS again
        carrierServicePackageName = mCarrierPrivilegesTracker.getCarrierServicePackageName();
        carrierServiceUid = mCarrierPrivilegesTracker.getCarrierServicePackageUid();
        mTestableLooper.processAllMessages();

        // It should return the same result, but didn't query package manager
        verify(mPackageManager, never()).queryIntentServices(any(), anyInt());
        assertEquals(PACKAGE_1, carrierServicePackageName);
        assertEquals(UID_1, carrierServiceUid);
    }

    @Test
    public void testGetCarrierService_haveCarrierServiceWithoutSimCarrierPrivileges()
            throws Exception {
        // Package 1 has no carrier privileges, package 2 has carrier-config based privileges, but
        // no matching certificate on the SIM.
        setupCarrierConfigRules(carrierConfigRuleString(getHash(CERT_2)));
        // Setup all odd packages privileged, even packages not
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_3, CERT_1, USER_1, UID_1));
        // Two declared CarrierService, only PACKAGE_1 has carrier privileges
        ResolveInfo service1 = new ResolveInfoBuilder().setService(PACKAGE_1).build();
        ResolveInfo service2 = new ResolveInfoBuilder().setService(PACKAGE_2).build();
        // Use doReturn instead of when/thenReturn which has NPE with unknown reason
        doReturn(List.of(service1, service2))
                .when(mPackageManager)
                .queryIntentServices(any(), anyInt());
        when(mPackageManager.getPackageUid(eq(PACKAGE_1), anyInt())).thenReturn(UID_1);
        when(mPackageManager.getPackageUid(eq(PACKAGE_2), anyInt())).thenReturn(UID_2);
        when(mPackageManager.getPackageUid(eq(PACKAGE_3), anyInt())).thenReturn(UID_1);

        // Verify that neither carrier service (no privileges, or carrier-config based privileges)
        // are accepted.
        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
        String carrierServicePackageName = mCarrierPrivilegesTracker.getCarrierServicePackageName();
        int carrierServiceUid = mCarrierPrivilegesTracker.getCarrierServicePackageUid();
        mTestableLooper.processAllMessages();

        verify(mPackageManager).queryIntentServices(any(), anyInt());
        assertNull(carrierServicePackageName);
        assertEquals(Process.INVALID_UID, carrierServiceUid);
    }

    @Test
    public void testGetCarrierService_haveNoCarrierService() throws Exception {
        // Only packages with CERT_1 have carrier privileges
        setupCarrierConfigRules(carrierConfigRuleString(getHash(CERT_1)));
        // Setup all odd packages privileged, even packages not
        setupInstalledPackages(
                new PackageCertInfo(PACKAGE_1, CERT_1, USER_1, UID_1),
                new PackageCertInfo(PACKAGE_2, CERT_2, USER_1, UID_2),
                new PackageCertInfo(PACKAGE_3, CERT_1, USER_1, UID_1));
        // No CarrierService declared at all
        // Use doReturn instead of when/thenReturn which has NPE with unknown reason
        doReturn(List.of()).when(
                mPackageManager).queryIntentServices(any(), anyInt());
        when(mPackageManager.getPackageUid(eq(PACKAGE_1), anyInt())).thenReturn(UID_1);
        when(mPackageManager.getPackageUid(eq(PACKAGE_2), anyInt())).thenReturn(UID_2);
        when(mPackageManager.getPackageUid(eq(PACKAGE_3), anyInt())).thenReturn(UID_1);

        mCarrierPrivilegesTracker = createCarrierPrivilegesTracker();
        String carrierServicePackageName = mCarrierPrivilegesTracker.getCarrierServicePackageName();
        int carrierServiceUid = mCarrierPrivilegesTracker.getCarrierServicePackageUid();
        mTestableLooper.processAllMessages();

        assertNull(carrierServicePackageName);
        assertEquals(Process.INVALID_UID, carrierServiceUid);
        verify(mPackageManager).queryIntentServices(any(), anyInt());
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

    /**
     * Utility class to build {@link ResolveInfo} for testing.
     */
    private static final class ResolveInfoBuilder {
        private ActivityInfo mActivityInfo;
        private ServiceInfo mServiceInfo;
        private ProviderInfo mProviderInfo;

        public ResolveInfoBuilder setActivity(String packageName) {
            mActivityInfo = new ActivityInfo();
            mActivityInfo.packageName = packageName;
            return this;
        }

        public ResolveInfoBuilder setService(String packageName) {
            mServiceInfo = new ServiceInfo();
            mServiceInfo.packageName = packageName;
            return this;
        }

        public ResolveInfoBuilder setProvider(String packageName) {
            mProviderInfo = new ProviderInfo();
            mProviderInfo.packageName = packageName;
            return this;
        }

        public ResolveInfo build() {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = mActivityInfo;
            resolveInfo.serviceInfo = mServiceInfo;
            resolveInfo.providerInfo = mProviderInfo;
            return resolveInfo;
        }
    }
}
