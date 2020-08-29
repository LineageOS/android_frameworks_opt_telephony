/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SmsPermissionsTest extends TelephonyTest {
    private static final String PACKAGE = "com.example.package";
    private static final String ATTRIBUTION_TAG = null;
    private static final String MESSAGE = "msg";

    private HandlerThread mHandlerThread;

    @Mock
    private Phone mMockPhone;
    @Mock
    private Context mMockContext;
    @Mock
    private AppOpsManager mMockAppOps;

    private SmsPermissions mSmsPermissionsTest;

    private boolean mCallerHasCarrierPrivileges;
    private boolean mCallerIsDefaultSmsPackage;

    @Before
    public void setUp() throws Exception {
        super.setUp("SmsPermissionsTest");

        MockitoAnnotations.initMocks(this);
        mHandlerThread = new HandlerThread("IccSmsInterfaceManagerTest");
        mHandlerThread.start();
        final CountDownLatch initialized = new CountDownLatch(1);
        new Handler(mHandlerThread.getLooper()).post(() -> {
            mSmsPermissionsTest = new SmsPermissions(
                    mMockPhone, mMockContext, mMockAppOps) {
                @Override
                public void enforceCallerIsImsAppOrCarrierApp(String message) {
                    if (!mCallerHasCarrierPrivileges) {
                        throw new SecurityException(message);
                    }
                }

                @Override
                public boolean isCallerDefaultSmsPackage(String packageName) {
                    return mCallerIsDefaultSmsPackage;
                }
            };
            initialized.countDown();
        });
        // Wait for object to initialize.
        if (!initialized.await(30, TimeUnit.SECONDS)) {
            fail("Could not initialize IccSmsInterfaceManager");
        }
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        mHandlerThread.join();
        super.tearDown();
    }

    @Test
    public void testCheckCallingSendTextPermissions_persist_grant() {
        assertTrue(mSmsPermissionsTest.checkCallingCanSendText(
                true /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG, MESSAGE));
    }

    @Test
    public void testCheckCallingSendTextPermissions_persist_noGrant() {
        Mockito.doThrow(new SecurityException(MESSAGE)).when(mMockContext)
                .enforceCallingPermission(Manifest.permission.SEND_SMS, MESSAGE);
        try {
            mSmsPermissionsTest.checkCallingCanSendText(
                    true /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG,
                    MESSAGE);
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckCallingSendTextPermissions_persist_noAppOps() {
        Mockito.when(
                mMockAppOps.noteOp(AppOpsManager.OPSTR_SEND_SMS, Binder.getCallingUid(), PACKAGE,
                        ATTRIBUTION_TAG, null))
                .thenReturn(AppOpsManager.MODE_ERRORED);
        assertFalse(mSmsPermissionsTest.checkCallingCanSendText(
                true /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG, MESSAGE));
    }

    @Test
    public void testCheckCallingSendTextPermissions_noPersist_grantViaCarrierApp() {
        mCallerHasCarrierPrivileges = true;
        // Other permissions shouldn't matter.
        Mockito.doThrow(new SecurityException(MESSAGE)).when(mMockContext)
                .enforceCallingPermission(Manifest.permission.MODIFY_PHONE_STATE, MESSAGE);
        Mockito.doThrow(new SecurityException(MESSAGE)).when(mMockContext)
                .enforceCallingPermission(Manifest.permission.SEND_SMS, MESSAGE);
        Mockito.when(
                mMockAppOps.noteOp(AppOpsManager.OPSTR_SEND_SMS, Binder.getCallingUid(), PACKAGE,
                        ATTRIBUTION_TAG, null))
                .thenReturn(AppOpsManager.MODE_ERRORED);

        assertTrue(mSmsPermissionsTest.checkCallingCanSendText(
                false /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG, MESSAGE));
    }

    @Test
    public void testCheckCallingSendTextPermissions_noPersist_grantViaModifyAndSend() {
        assertTrue(mSmsPermissionsTest.checkCallingCanSendText(
                false /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG, MESSAGE));
    }

    @Test
    public void testCheckCallingSendTextPermissions_noPersist_noModify() {
        Mockito.doThrow(new SecurityException(MESSAGE)).when(mMockContext)
                .enforceCallingPermission(Manifest.permission.MODIFY_PHONE_STATE, MESSAGE);
        try {
            mSmsPermissionsTest.checkCallingCanSendText(
                    false /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG,
                    MESSAGE);
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckCallingSendTextPermissions_noPersist_noSendSmsPermission() {
        Mockito.doThrow(new SecurityException(MESSAGE)).when(mMockContext)
                .enforceCallingPermission(Manifest.permission.SEND_SMS, MESSAGE);
        try {
            mSmsPermissionsTest.checkCallingCanSendText(
                    false /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG,
                    MESSAGE);
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckCallingSendTextPermissions_noPersist_noAppOps() {
        Mockito.when(
                mMockAppOps.noteOp(AppOpsManager.OPSTR_SEND_SMS, Binder.getCallingUid(), PACKAGE,
                        ATTRIBUTION_TAG, null))
                .thenReturn(AppOpsManager.MODE_ERRORED);
        assertFalse(mSmsPermissionsTest.checkCallingCanSendText(
                false /* persistMessageForNonDefaultSmsApp */, PACKAGE, ATTRIBUTION_TAG, MESSAGE));
    }

    @Test
    public void testCheckCallingOrSelfCanGetSmscAddressPermissions_defaultSmsApp() {
        mCallerIsDefaultSmsPackage = true;
        // Other permissions shouldn't matter.
        Mockito.when(mMockContext.checkCallingOrSelfPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE))
                .thenReturn(PERMISSION_DENIED);
        assertTrue(mSmsPermissionsTest.checkCallingOrSelfCanGetSmscAddress(PACKAGE, MESSAGE));
    }

    @Test
    public void testCheckCallingOrSelfCanGetSmscAddressPermissions_hasReadPrivilegedPhoneState() {
        Mockito.when(mMockContext.checkCallingOrSelfPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE))
                .thenReturn(PERMISSION_GRANTED);
        assertTrue(mSmsPermissionsTest.checkCallingOrSelfCanGetSmscAddress(PACKAGE, MESSAGE));
    }

    @Test
    public void testCheckCallingOrSelfCanGetSmscAddressPermissions_noPermissions() {
        Mockito.when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(
                mTelephonyManager);
        Mockito.when(mMockContext.checkCallingOrSelfPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE))
                .thenReturn(PERMISSION_DENIED);
        try {
            mSmsPermissionsTest.checkCallingOrSelfCanGetSmscAddress(PACKAGE, MESSAGE);
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }
    @Test
    public void testCheckCallingOrSelfCanSetSmscAddressPermissions_defaultSmsApp() {
        mCallerIsDefaultSmsPackage = true;
        // Other permissions shouldn't matter.
        Mockito.when(mMockContext.checkCallingOrSelfPermission(
                    Manifest.permission.MODIFY_PHONE_STATE))
                .thenReturn(PERMISSION_DENIED);
        assertTrue(mSmsPermissionsTest.checkCallingOrSelfCanSetSmscAddress(PACKAGE, MESSAGE));
    }

    @Test
    public void testCheckCallingOrSelfCanSetSmscAddressPermissions_hasModifyPhoneState() {
        Mockito.when(mMockContext.checkCallingOrSelfPermission(
                    Manifest.permission.MODIFY_PHONE_STATE))
                .thenReturn(PERMISSION_GRANTED);
        assertTrue(mSmsPermissionsTest.checkCallingOrSelfCanSetSmscAddress(PACKAGE, MESSAGE));
    }

    @Test
    public void testCheckCallingOrSelfCanSetSmscAddressPermissions_noPermissions() {
        Mockito.when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(
                mTelephonyManager);
        Mockito.when(mMockContext.checkCallingOrSelfPermission(
                Manifest.permission.MODIFY_PHONE_STATE)).thenReturn(PERMISSION_DENIED);
        try {
            assertFalse(mSmsPermissionsTest.checkCallingOrSelfCanSetSmscAddress(PACKAGE, MESSAGE));
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testPackageNameMatchesCallingUid() {
        AppOpsManager mockAppOpsManager = mock(AppOpsManager.class);
        Mockito.when(mMockContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(
                mockAppOpsManager);

        // test matching case
        assertTrue(new SmsPermissions(mMockPhone, mMockContext, mMockAppOps)
                .packageNameMatchesCallingUid(PACKAGE));

        // test mis-match case
        SecurityException e = new SecurityException("Test exception");
        doThrow(e).when(mockAppOpsManager).checkPackage(anyInt(), anyString());
        assertFalse(new SmsPermissions(mMockPhone, mMockContext, mMockAppOps)
                .packageNameMatchesCallingUid(PACKAGE));
    }
}
