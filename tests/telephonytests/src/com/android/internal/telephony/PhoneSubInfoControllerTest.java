/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.telephony.TelephonyManager.APPTYPE_ISIM;
import static android.telephony.TelephonyManager.APPTYPE_USIM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

import android.app.AppOpsManager;
import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccPort;
import com.android.internal.telephony.uicc.UiccProfile;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

public class PhoneSubInfoControllerTest extends TelephonyTest {
    private static final String FEATURE_ID = "myfeatureId";
    private static final String PSI_SMSC_TEL1 = "tel:+91123456789";
    private static final String PSI_SMSC_SIP1 = "sip:+1234567890@abc.pc.operetor1.com;user=phone";
    private static final String PSI_SMSC_TEL2 = "tel:+91987654321";
    private static final String PSI_SMSC_SIP2 = "sip:+19876543210@dcf.pc.operetor2.com;user=phone";

    private PhoneSubInfoController mPhoneSubInfoControllerUT;
    private AppOpsManager mAppOsMgr;
    private PackageManager mPm;

    // Mocked classes
    GsmCdmaPhone mSecondPhone;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSecondPhone = mock(GsmCdmaPhone.class);
        PropertyInvalidatedCache.disableForTestMode();
        /* mPhone -> PhoneId: 0 -> SubId:0
           mSecondPhone -> PhoneId:1 -> SubId: 1*/
        doReturn(0).when(mSubscriptionManagerService).getPhoneId(eq(0));
        doReturn(1).when(mSubscriptionManagerService).getPhoneId(eq(1));
        doReturn(2).when(mTelephonyManager).getPhoneCount();
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(true).when(mSubscriptionManagerService).isActiveSubId(0, TAG, FEATURE_ID);
        doReturn(true).when(mSubscriptionManagerService).isActiveSubId(1, TAG, FEATURE_ID);
        doReturn(new int[]{0, 1}).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionIdList();

        doReturn(mContext).when(mSecondPhone).getContext();

        mAppOsMgr = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mPm = mContext.getPackageManager();

        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[]{mPhone, mSecondPhone});
        mPhoneSubInfoControllerUT = new PhoneSubInfoController(mContext);

        setupMocksForTelephonyPermissions();
        // TelephonyPermissions will query the READ_DEVICE_IDENTIFIERS op from AppOpManager to
        // determine if the calling package should be granted access to device identifiers. To
        // ensure this appop does not interfere with any of the tests always return its default
        // value.
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOpNoThrow(
                eq(AppOpsManager.OPSTR_READ_DEVICE_IDENTIFIERS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));

        // Bypass calling package check.
        doReturn(Binder.getCallingUid()).when(mPm).getPackageUid(eq(TAG), anyInt());
    }

    @After
    public void tearDown() throws Exception {
        mAppOsMgr = null;
        mPm = null;
        mPhoneSubInfoControllerUT = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetDeviceId() {
        doReturn("353626073736741").when(mPhone).getDeviceId();
        doReturn("353626073736742").when(mSecondPhone).getDeviceId();

        assertEquals("353626073736741",
                mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG, FEATURE_ID));
        assertEquals("353626073736742",
                mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetDeviceIdWithOutPermission() {
        // The READ_PRIVILEGED_PHONE_STATE permission or passing a device / profile owner access
        // check is required to access device identifiers. Since neither of those are true for this
        // test each case will result in a SecurityException being thrown.
        setIdentifierAccess(false);
        doReturn("353626073736741").when(mPhone).getDeviceId();
        doReturn("353626073736742").when(mSecondPhone).getDeviceId();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceId"));
        }

        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceId"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceId"));
        }

        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceId"));
        }

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        // The READ_PRIVILEGED_PHONE_STATE permission is now required to get device identifiers.
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceId"));
        }

        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceId"));
        }
    }

    @Test
    @SmallTest
    public void testGetNai() {
        doReturn("aaa@example.com").when(mPhone).getNai();
        assertEquals("aaa@example.com",
                mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG, FEATURE_ID));

        doReturn("bbb@example.com").when(mSecondPhone).getNai();
        assertEquals("bbb@example.com",
                mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetNaiWithOutPermission() {
        // The READ_PRIVILEGED_PHONE_STATE permission, carrier privileges, or passing a device /
        // profile owner access check is required to access subscriber identifiers. Since none of
        // those are true for this test each case will result in a SecurityException being thrown.
        setIdentifierAccess(false);
        doReturn("aaa@example.com").when(mPhone).getNai();
        doReturn("bbb@example.com").when(mSecondPhone).getNai();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getNai"));
        }

        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getNai"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getNai"));
        }

        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getNai"));
        }

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getNai"));
        }

        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getNai"));
        }
    }

    @Test
    @SmallTest
    public void testGetImei() {
        doReturn("990000862471854").when(mPhone).getImei();
        assertEquals("990000862471854",
                mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG, FEATURE_ID));

        doReturn("990000862471855").when(mSecondPhone).getImei();
        assertEquals("990000862471855",
                mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetImeiWithOutPermission() {
        // The READ_PRIVILEGED_PHONE_STATE permission, carrier privileges, or passing a device /
        // profile owner access check is required to access device identifiers. Since none of
        // those are true for this test each case will result in a SecurityException being thrown.
        setIdentifierAccess(false);
        doReturn("990000862471854").when(mPhone).getImei();
        doReturn("990000862471855").when(mSecondPhone).getImei();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getImei"));
        }

        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getImei"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getImei"));
        }

        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getImei"));
        }

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getImei"));
        }

        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getImei"));
        }
    }

    @Test
    @SmallTest
    public void testGetDeviceSvn() {
        doReturn("00").when(mPhone).getDeviceSvn();
        assertEquals("00", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG, FEATURE_ID));

        doReturn("01").when(mSecondPhone).getDeviceSvn();
        assertEquals("01", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetDeviceSvnWithOutPermission() {
        doReturn("00").when(mPhone).getDeviceSvn();
        doReturn("01").when(mSecondPhone).getDeviceSvn();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceSvn"));
        }

        try {
            mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getDeviceSvn"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));

        assertNull(mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG, FEATURE_ID));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        assertEquals("00", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG, FEATURE_ID));
        assertEquals("01", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetSubscriberId() {
        //IMSI
        doReturn("310260426283121").when(mPhone).getSubscriberId();
        assertEquals("310260426283121", mPhoneSubInfoControllerUT
                .getSubscriberIdForSubscriber(0, TAG, FEATURE_ID));

        doReturn("310260426283122").when(mSecondPhone).getSubscriberId();
        assertEquals("310260426283122", mPhoneSubInfoControllerUT
                .getSubscriberIdForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetSubscriberIdWithInactiveSubId() {
        //IMSI
        assertNull(mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(2, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetSubscriberIdWithOutPermission() {
        // The READ_PRIVILEGED_PHONE_STATE permission, carrier privileges, or passing a device /
        // profile owner access check is required to access subscriber identifiers. Since none of
        // those are true for this test each case will result in a SecurityException being thrown.
        setIdentifierAccess(false);
        doReturn("310260426283121").when(mPhone).getSubscriberId();
        doReturn("310260426283122").when(mSecondPhone).getSubscriberId();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSubscriberId"));
        }

        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSubscriberId"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSubscriberId"));
        }

        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSubscriberId"));
        }

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        // The READ_PRIVILEGED_PHONE_STATE permission is now required to get device identifiers.
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSubscriberId"));
        }

        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSubscriberId"));
        }
    }

    @Test
    @SmallTest
    public void testGetIccSerialNumber() {
        //IccId
        doReturn("8991101200003204510").when(mPhone).getIccSerialNumber();
        assertEquals("8991101200003204510", mPhoneSubInfoControllerUT
                .getIccSerialNumberForSubscriber(0, TAG, FEATURE_ID));

        doReturn("8991101200003204511").when(mSecondPhone).getIccSerialNumber();
        assertEquals("8991101200003204511", mPhoneSubInfoControllerUT
                .getIccSerialNumberForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetIccSerialNumberWithOutPermission() {
        // The READ_PRIVILEGED_PHONE_STATE permission, carrier privileges, or passing a device /
        // profile owner access check is required to access subscriber identifiers. Since none of
        // those are true for this test each case will result in a SecurityException being thrown.
        setIdentifierAccess(false);
        doReturn("8991101200003204510").when(mPhone).getIccSerialNumber();
        doReturn("8991101200003204511").when(mSecondPhone).getIccSerialNumber();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getIccSerialNumber"));
        }

        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getIccSerialNumber"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getIccSerialNumber"));
        }

        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getIccSerialNumber"));
        }

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getIccSerialNumber"));
        }

        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getIccSerialNumber"));
        }
    }

    @Test
    @SmallTest
    public void testGetLine1Number() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        doReturn("+18051234567").when(mPhone).getLine1Number();
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));

        doReturn("+18052345678").when(mSecondPhone).getLine1Number();
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetLine1NumberWithOutPermissionTargetPreR() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        doReturn("+18051234567").when(mPhone).getLine1Number();
        doReturn("+18052345678").when(mSecondPhone).getLine1Number();

        /* case 1: no READ_PRIVILEGED_PHONE_STATE & READ_PHONE_STATE &
        READ_SMS and no OP_WRITE_SMS & OP_READ_SMS from appOsMgr */
        // All permission checks are handled by the LegacyPermissionManager, so this test only
        // requires three case; all permissions / appops denied, READ_PHONE_STATE permission
        // granted without the appop, and one or more of the permissions / appops granted.
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        setPhoneNumberAccess(PackageManager.PERMISSION_DENIED);
        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        /* case 2: only enable READ_PHONE_STATE permission */
        setPhoneNumberAccess(AppOpsManager.MODE_IGNORED);
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));

        /* case 3: enable READ_SMS and OP_READ_SMS */
        setPhoneNumberAccess(PackageManager.PERMISSION_GRANTED);
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetLine1NumberWithOutPermissionTargetR() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        doReturn("+18051234567").when(mPhone).getLine1Number();
        doReturn("+18052345678").when(mSecondPhone).getLine1Number();

        /* case 1: no READ_PRIVILEGED_PHONE_STATE & READ_PHONE_STATE &
        READ_SMS and no OP_WRITE_SMS & OP_READ_SMS from appOsMgr */
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        setPhoneNumberAccess(PackageManager.PERMISSION_DENIED);
        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        /* case 2: enable READ_SMS and OP_READ_SMS */
        setPhoneNumberAccess(PackageManager.PERMISSION_GRANTED);
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));
    }


    @Test
    @SmallTest
    public void testGetLine1AlphaTag() {
        doReturn("LINE1_SIM_0").when(mPhone).getLine1AlphaTag();
        assertEquals("LINE1_SIM_0", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(0, TAG, FEATURE_ID));

        doReturn("LINE1_SIM_1").when(mSecondPhone).getLine1AlphaTag();
        assertEquals("LINE1_SIM_1", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetLine1AlphaTagWithOutPermission() {
        doReturn("LINE1_SIM_0").when(mPhone).getLine1AlphaTag();
        doReturn("LINE1_SIM_1").when(mSecondPhone).getLine1AlphaTag();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getLine1AlphaTag"));
        }

        try {
            mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getLine1AlphaTag"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));

        assertNull(mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(1, TAG, FEATURE_ID));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        assertEquals("LINE1_SIM_0", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("LINE1_SIM_1", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetMsisdn() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        doReturn("+18051234567").when(mPhone).getMsisdn();
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID));

        doReturn("+18052345678").when(mSecondPhone).getMsisdn();
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetMsisdnWithOutPermissionTargetPreR() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        doReturn("+18051234567").when(mPhone).getMsisdn();
        doReturn("+18052345678").when(mSecondPhone).getMsisdn();

        /* case 1: no READ_PRIVILEGED_PHONE_STATE & READ_PHONE_STATE from appOsMgr */
        // The LegacyPermissionManager handles these checks, so set its return code to indicate
        // none of these have been granted.
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        setPhoneNumberAccess(PackageManager.PERMISSION_DENIED);
        try {
            mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        try {
            mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        /* case 2: only enable READ_PHONE_STATE permission */
        // The LegacyPermissionManager will return AppOpsManager.MODE_IGNORED if the target SDK
        // version < R and the READ_PHONE_STATE permission has been granted without the appop.
        setPhoneNumberAccess(AppOpsManager.MODE_IGNORED);
        assertNull(mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID));

        /* case 3: enable appOsMgr READ_PHONE_PERMISSION & READ_PHONE_STATE */
        setPhoneNumberAccess(PackageManager.PERMISSION_GRANTED);
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetMsisdnWithOutPermissionTargetR() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        doReturn("+18051234567").when(mPhone).getMsisdn();
        doReturn("+18052345678").when(mSecondPhone).getMsisdn();

        /* case 1: no READ_PRIVILEGED_PHONE_STATE & READ_PHONE_STATE &
        READ_SMS and no OP_WRITE_SMS & OP_READ_SMS from appOsMgr */
        // Since the LegacyPermissionManager is performing this check the service will perform
        // the READ_PHONE_STATE checks based on target SDK version; for apps targeting R+ it
        // will not check the READ_PHONE_STATE permission and appop and will only return
        // permission granted / denied.
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        setPhoneNumberAccess(PackageManager.PERMISSION_DENIED);
        try {
            mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        try {
            mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        /* case 2: enable READ_SMS and OP_READ_SMS */
        setPhoneNumberAccess(PackageManager.PERMISSION_GRANTED);
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailNumber() {
        doReturn("+18051234567").when(mPhone).getVoiceMailNumber();
        assertEquals("+18051234567", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(0, TAG, FEATURE_ID));

        doReturn("+18052345678").when(mSecondPhone).getVoiceMailNumber();
        assertEquals("+18052345678", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailNumberWithOutPermission() {
        doReturn("+18051234567").when(mPhone).getVoiceMailNumber();
        doReturn("+18052345678").when(mSecondPhone).getVoiceMailNumber();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getVoiceMailNumber"));
        }

        try {
            mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getVoiceMailNumber"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));

        assertNull(mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(1, TAG, FEATURE_ID));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        assertEquals("+18051234567", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailAlphaTag() {
        doReturn("VM_SIM_0").when(mPhone).getVoiceMailAlphaTag();
        assertEquals("VM_SIM_0", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(0, TAG, FEATURE_ID));

        doReturn("VM_SIM_1").when(mSecondPhone).getVoiceMailAlphaTag();
        assertEquals("VM_SIM_1", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(1, TAG, FEATURE_ID));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailAlphaTagWithOutPermission() {
        doReturn("VM_SIM_0").when(mPhone).getVoiceMailAlphaTag();
        doReturn("VM_SIM_1").when(mSecondPhone).getVoiceMailAlphaTag();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getVoiceMailAlphaTag"));
        }

        try {
            mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getVoiceMailAlphaTag"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));

        assertNull(mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(1, TAG, FEATURE_ID));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        assertEquals("VM_SIM_0", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("VM_SIM_1", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(1, TAG, FEATURE_ID));
    }

    private void setUpInitials() {
        UiccPort uiccPort1 = Mockito.mock(UiccPort.class);
        UiccProfile uiccProfile1 = Mockito.mock(UiccProfile.class);
        UiccCardApplication uiccCardApplication1 = Mockito.mock(UiccCardApplication.class);
        SIMRecords simRecords1 = Mockito.mock(SIMRecords.class);
        IsimUiccRecords isimUiccRecords1 = Mockito.mock(IsimUiccRecords.class);

        doReturn(uiccPort1).when(mPhone).getUiccPort();
        doReturn(uiccProfile1).when(uiccPort1).getUiccProfile();
        doReturn(uiccCardApplication1).when(uiccProfile1).getApplicationByType(anyInt());
        doReturn(simRecords1).when(uiccCardApplication1).getIccRecords();
        doReturn(isimUiccRecords1).when(uiccCardApplication1).getIccRecords();
        doReturn(PSI_SMSC_TEL1).when(simRecords1).getSmscIdentity();
        doReturn(PSI_SMSC_TEL1).when(isimUiccRecords1).getSmscIdentity();

        doReturn(mUiccPort).when(mSecondPhone).getUiccPort();
        doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
        doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
        doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();
        doReturn(mIsimUiccRecords).when(mUiccCardApplicationIms).getIccRecords();
        doReturn(PSI_SMSC_TEL2).when(mSimRecords).getSmscIdentity();
        doReturn(PSI_SMSC_TEL2).when(mIsimUiccRecords).getSmscIdentity();
    }

    @Test
    public void testGetSmscIdentityForTelUri() {
        try {
            setUpInitials();
            assertEquals(PSI_SMSC_TEL1, mPhoneSubInfoControllerUT
                    .getSmscIdentity(0, APPTYPE_ISIM).toString());
            assertEquals(PSI_SMSC_TEL1, mPhoneSubInfoControllerUT
                    .getSmscIdentity(0, APPTYPE_USIM).toString());
            assertEquals(PSI_SMSC_TEL2, mPhoneSubInfoControllerUT
                    .getSmscIdentity(1, APPTYPE_ISIM).toString());
            assertEquals(PSI_SMSC_TEL2, mPhoneSubInfoControllerUT
                    .getSmscIdentity(1, APPTYPE_USIM).toString());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGetSmscIdentityForSipUri() {
        try {
            UiccPort uiccPort1 = Mockito.mock(UiccPort.class);
            UiccProfile uiccProfile1 = Mockito.mock(UiccProfile.class);
            UiccCardApplication uiccCardApplication1 = Mockito.mock(UiccCardApplication.class);
            SIMRecords simRecords1 = Mockito.mock(SIMRecords.class);
            IsimUiccRecords isimUiccRecords1 = Mockito.mock(IsimUiccRecords.class);

            doReturn(uiccPort1).when(mPhone).getUiccPort();
            doReturn(uiccProfile1).when(uiccPort1).getUiccProfile();
            doReturn(uiccCardApplication1).when(uiccProfile1).getApplicationByType(anyInt());
            doReturn(simRecords1).when(uiccCardApplication1).getIccRecords();
            doReturn(isimUiccRecords1).when(uiccCardApplication1).getIccRecords();
            doReturn(PSI_SMSC_SIP1).when(simRecords1).getSmscIdentity();
            doReturn(PSI_SMSC_SIP1).when(isimUiccRecords1).getSmscIdentity();

            doReturn(mUiccPort).when(mSecondPhone).getUiccPort();
            doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
            doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
            doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();
            doReturn(mIsimUiccRecords).when(mUiccCardApplicationIms).getIccRecords();
            doReturn(PSI_SMSC_SIP2).when(mSimRecords).getSmscIdentity();
            doReturn(PSI_SMSC_SIP2).when(mIsimUiccRecords).getSmscIdentity();

            assertEquals(PSI_SMSC_SIP1, mPhoneSubInfoControllerUT
                    .getSmscIdentity(0, APPTYPE_ISIM).toString());
            assertEquals(PSI_SMSC_SIP1, mPhoneSubInfoControllerUT
                    .getSmscIdentity(0, APPTYPE_USIM).toString());
            assertEquals(PSI_SMSC_SIP2, mPhoneSubInfoControllerUT
                    .getSmscIdentity(1, APPTYPE_ISIM).toString());
            assertEquals(PSI_SMSC_SIP2, mPhoneSubInfoControllerUT
                    .getSmscIdentity(1, APPTYPE_USIM).toString());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGetSmscIdentityWithOutPermissions() {
        setUpInitials();

        //case 1: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getSmscIdentity(0, APPTYPE_ISIM);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSmscIdentity"));
        }

        try {
            mPhoneSubInfoControllerUT.getSmscIdentity(1, APPTYPE_ISIM);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSmscIdentity"));
        }

        try {
            mPhoneSubInfoControllerUT.getSmscIdentity(0, APPTYPE_USIM);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSmscIdentity"));
        }

        try {
            mPhoneSubInfoControllerUT.getSmscIdentity(1, APPTYPE_USIM);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSmscIdentity"));
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));

        try {
            assertEquals(PSI_SMSC_TEL1, mPhoneSubInfoControllerUT
                    .getSmscIdentity(0, APPTYPE_ISIM).toString());
            assertEquals(PSI_SMSC_TEL1, mPhoneSubInfoControllerUT
                    .getSmscIdentity(0, APPTYPE_USIM).toString());
            assertEquals(PSI_SMSC_TEL2, mPhoneSubInfoControllerUT
                    .getSmscIdentity(1, APPTYPE_ISIM).toString());
            assertEquals(PSI_SMSC_TEL2, mPhoneSubInfoControllerUT
                    .getSmscIdentity(1, APPTYPE_USIM).toString());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGetSimServiceTable() throws RemoteException {
        String refSst = "1234567";
        doReturn(mUiccPort).when(mPhone).getUiccPort();
        doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
        doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
        doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();

        doReturn(refSst).when(mSimRecords).getSimServiceTable();

        String resultSst = mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt());
        assertEquals(refSst, resultSst);
    }

    @Test
    public void testGetSimServiceTableEmpty() throws RemoteException {
        String refSst = null;
        doReturn(mUiccPort).when(mPhone).getUiccPort();
        doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
        doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
        doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();

        doReturn(refSst).when(mSimRecords).getSimServiceTable();

        String resultSst = mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt());
        assertEquals(refSst, resultSst);
    }

    @Test
    public void testGetSstWhenNoUiccPort() throws RemoteException {
            String refSst = "1234567";
            doReturn(null).when(mPhone).getUiccPort();
            doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
            doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
            doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();

            doReturn(refSst).when(mSimRecords).getSimServiceTable();

            String resultSst = mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt());
            assertEquals(null, resultSst);
    }

    @Test
    public void testGetSstWhenNoUiccProfile() throws RemoteException {
        String refSst = "1234567";
        doReturn(mUiccPort).when(mPhone).getUiccPort();
        doReturn(null).when(mUiccPort).getUiccProfile();
        doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
        doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();

        doReturn(refSst).when(mSimRecords).getSimServiceTable();

        String resultSst = mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt());
        assertEquals(null, resultSst);
    }

    @Test
    public void testGetSstWhenNoUiccApplication() throws RemoteException {
        String refSst = "1234567";
        doReturn(mUiccPort).when(mPhone).getUiccPort();
        doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
        doReturn(null).when(mUiccProfile).getApplicationByType(anyInt());
        doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();

        doReturn(refSst).when(mSimRecords).getSimServiceTable();

        String resultSst = mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt());
        assertEquals(null, resultSst);
    }

    @Test
    public void testGetSimServiceTableWithOutPermissions() throws RemoteException {
        String refSst = "1234567";
        doReturn(mUiccPort).when(mPhone).getUiccPort();
        doReturn(mUiccProfile).when(mUiccPort).getUiccProfile();
        doReturn(mUiccCardApplicationIms).when(mUiccProfile).getApplicationByType(anyInt());
        doReturn(mSimRecords).when(mUiccCardApplicationIms).getIccRecords();

        doReturn(refSst).when(mSimRecords).getSimServiceTable();

        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt());
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("getSimServiceTable"));
        }

        mContextFixture.addCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
        assertEquals(refSst, mPhoneSubInfoControllerUT.getSimServiceTable(anyInt(), anyInt()));
    }

    @Test
    public void getPrivateUserIdentity() {
        String refImpi = "1234567890@example.com";
        doReturn(mIsimUiccRecords).when(mPhone).getIsimRecords();
        doReturn(refImpi).when(mIsimUiccRecords).getIsimImpi();

        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOpNoThrow(
                eq(AppOpsManager.OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER), anyInt(), eq(TAG),
                eq(FEATURE_ID), nullable(String.class));

        String impi = mPhoneSubInfoControllerUT.getImsPrivateUserIdentity(0, TAG, FEATURE_ID);
        assertEquals(refImpi, impi);
    }

    @Test
    public void getPrivateUserIdentity_NoPermission() {
        String refImpi = "1234567890@example.com";
        doReturn(mIsimUiccRecords).when(mPhone).getIsimRecords();
        doReturn(refImpi).when(mIsimUiccRecords).getIsimImpi();

        try {
            mPhoneSubInfoControllerUT.getImsPrivateUserIdentity(0, TAG, FEATURE_ID);
            fail();
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertTrue(ex.getMessage().contains("No permissions to the caller"));
        }
    }

    @Test
    public void getPrivateUserIdentity_InValidSubIdCheck() {
        String refImpi = "1234567890@example.com";
        doReturn(mIsimUiccRecords).when(mPhone).getIsimRecords();
        doReturn(refImpi).when(mIsimUiccRecords).getIsimImpi();

        try {
            mPhoneSubInfoControllerUT.getImsPrivateUserIdentity(-1, TAG, FEATURE_ID);
            fail();
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalArgumentException);
            assertTrue(ex.getMessage().contains("Invalid SubscriptionID"));
        }
    }

    @Test
    public void getImsPublicUserIdentities() {
        String[] refImpuArray = new String[3];
        refImpuArray[0] = "012345678";
        refImpuArray[1] = "sip:test@verify.com";
        refImpuArray[2] = "tel:+91987754324";
        doReturn(mIsimUiccRecords).when(mPhone).getIsimRecords();
        doReturn(refImpuArray).when(mIsimUiccRecords).getIsimImpu();

        List<Uri> impuList = mPhoneSubInfoControllerUT.getImsPublicUserIdentities(0, TAG,
                FEATURE_ID);

        assertNotNull(impuList);
        assertEquals(refImpuArray.length, impuList.size());
        assertEquals(impuList.get(0).toString(), refImpuArray[0]);
        assertEquals(impuList.get(1).toString(), refImpuArray[1]);
        assertEquals(impuList.get(2).toString(), refImpuArray[2]);
    }

    @Test
    public void getImsPublicUserIdentities_InvalidImpu() {
        String[] refImpuArray = new String[3];
        refImpuArray[0] = null;
        refImpuArray[2] = "";
        refImpuArray[2] = "tel:+91987754324";
        doReturn(mIsimUiccRecords).when(mPhone).getIsimRecords();
        doReturn(refImpuArray).when(mIsimUiccRecords).getIsimImpu();
        List<Uri> impuList = mPhoneSubInfoControllerUT.getImsPublicUserIdentities(0, TAG,
                FEATURE_ID);
        assertNotNull(impuList);
        // Null or Empty string cannot be converted to URI
        assertEquals(refImpuArray.length - 2, impuList.size());
    }

    @Test
    public void getImsPublicUserIdentities_IsimNotLoadedError() {
        doReturn(null).when(mPhone).getIsimRecords();

        try {
            mPhoneSubInfoControllerUT.getImsPublicUserIdentities(0, TAG, FEATURE_ID);
            fail();
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalStateException);
            assertTrue(ex.getMessage().contains("ISIM is not loaded"));
        }
    }

    @Test
    public void getImsPublicUserIdentities_InValidSubIdCheck() {
        try {
            mPhoneSubInfoControllerUT.getImsPublicUserIdentities(-1, TAG, FEATURE_ID);
            fail();
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalArgumentException);
            assertTrue(ex.getMessage().contains("Invalid SubscriptionID"));
        }
    }

    @Test
    public void getImsPublicUserIdentities_NoReadPrivilegedPermission() {
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        String[] refImpuArray = new String[3];
        refImpuArray[0] = "012345678";
        refImpuArray[1] = "sip:test@verify.com";
        refImpuArray[2] = "tel:+91987754324";
        doReturn(mIsimUiccRecords).when(mPhone).getIsimRecords();
        doReturn(refImpuArray).when(mIsimUiccRecords).getIsimImpu();

        List<Uri> impuList = mPhoneSubInfoControllerUT.getImsPublicUserIdentities(0, TAG,
                FEATURE_ID);

        assertNotNull(impuList);
        assertEquals(refImpuArray.length, impuList.size());
        assertEquals(impuList.get(0).toString(), refImpuArray[0]);
        assertEquals(impuList.get(1).toString(), refImpuArray[1]);
        assertEquals(impuList.get(2).toString(), refImpuArray[2]);
        mContextFixture.addCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
    }
}