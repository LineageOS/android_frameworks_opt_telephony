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
import static android.Manifest.permission.READ_SMS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;

import android.app.AppOpsManager;
import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.os.Build;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class PhoneSubInfoControllerTest extends TelephonyTest {
    private static final String FEATURE_ID = "myfeatureId";

    private PhoneSubInfoController mPhoneSubInfoControllerUT;
    private AppOpsManager mAppOsMgr;

    @Mock
    GsmCdmaPhone mSecondPhone;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        PropertyInvalidatedCache.disableForTestMode();
        /* mPhone -> PhoneId: 0 -> SubId:0
           mSecondPhone -> PhoneId:1 -> SubId: 1*/
        doReturn(0).when(mSubscriptionController).getPhoneId(eq(0));
        doReturn(1).when(mSubscriptionController).getPhoneId(eq(1));
        doReturn(2).when(mTelephonyManager).getPhoneCount();
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(true).when(mSubscriptionController).isActiveSubId(0, TAG, FEATURE_ID);
        doReturn(true).when(mSubscriptionController).isActiveSubId(1, TAG, FEATURE_ID);
        doReturn(new int[]{0, 1}).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionIdList();

        mServiceManagerMockedServices.put("isub", mSubscriptionController);
        doReturn(mSubscriptionController).when(mSubscriptionController)
                .queryLocalInterface(anyString());
        doReturn(mContext).when(mSecondPhone).getContext();

        mAppOsMgr = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);

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
    }

    @After
    public void tearDown() throws Exception {
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
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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

        /* case 2: only enable WRITE_SMS permission */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));

        /* case 3: only enable READ_PRIVILEGED_PHONE_STATE */
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        mContextFixture.addCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));

        /* case 4: only enable READ_PHONE_STATE permission */
        mContextFixture.removeCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));

        /* case 5: enable appOsMgr READ_PHONE_PERMISSION & READ_PHONE_STATE */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        assertEquals("+18051234567",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID));
        assertEquals("+18052345678",
                mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID));

        /* case 6: only enable READ_SMS; without the appop should throw SecurityException */
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        mContextFixture.removeCallingOrSelfPermission(READ_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(READ_SMS);
        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG, FEATURE_ID);
            Assert.fail("expected SecurityException thrown");
        } catch (SecurityException expected) {
        }
        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG, FEATURE_ID);
            Assert.fail("expected SecurityException thrown");
        } catch (SecurityException expected) {
        }

        /* case 7: enable READ_SMS and OP_READ_SMS */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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

        /* case 2: enable READ_PHONE_STATE permission */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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

        /* case 3: enable READ_SMS and OP_READ_SMS */
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        mContextFixture.addCallingOrSelfPermission(READ_SMS);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        assertNull(mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG, FEATURE_ID));
        assertNull(mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG, FEATURE_ID));

        /* case 3: enable appOsMgr READ_PHONE_PERMISSION & READ_PHONE_STATE */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_WRITE_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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

        /* case 3: enable READ_SMS and OP_READ_SMS */
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
        mContextFixture.addCallingOrSelfPermission(READ_SMS);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OPSTR_READ_SMS), anyInt(), eq(TAG), eq(FEATURE_ID),
                nullable(String.class));
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
}
