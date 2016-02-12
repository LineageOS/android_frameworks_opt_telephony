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

import android.Manifest.permission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.provider.Telephony.Sms.Intents;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.SmsApplication.SmsApplicationData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

public class SmsApplicationTest extends TelephonyTest {

    private static final String FAKE_PACKAGE_NAME = "com.android.messaging";

    private static final String FAKE_NAME = "fake messenger";

    @Before
    public void setUp() throws Exception {
        logd("+Setup!");
        super.setUp(getClass().getSimpleName());

        PackageManager pm =mContextFixture.getTestDouble().getPackageManager();
        doReturn(FAKE_NAME).when(pm).getText(anyString(), anyInt(), any(ApplicationInfo.class));

        doAnswer(new Answer<List<ResolveInfo>>() {
            @Override
            public List<ResolveInfo> answer(InvocationOnMock invocation) throws Throwable {
                Intent intent = (Intent) invocation.getArguments()[0];
                logd("fake queryBroadcastReceivers: " + intent);
                if (intent.getAction().equals(Intents.SMS_DELIVER_ACTION)) {
                    return createFakeAppList(permission.BROADCAST_SMS);
                } else if (intent.getAction().equals(Intents.WAP_PUSH_DELIVER_ACTION)) {
                    return createFakeAppList(permission.BROADCAST_WAP_PUSH);
                }
                return createFakeAppList("");
            }
        }).when(pm).queryBroadcastReceivers((Intent) any(), anyInt(), anyInt());

        doAnswer(new Answer<List<ResolveInfo>>() {
            @Override
            public List<ResolveInfo> answer(InvocationOnMock invocation) throws Throwable {
                Intent intent = (Intent) invocation.getArguments()[0];
                logd("fake queryIntentServicesAsUser: " + intent);
                if (intent.getAction().equals(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE)) {
                    return createFakeAppList(permission.SEND_RESPOND_VIA_MESSAGE);
                }
                return createFakeAppList("");
            }
        }).when(pm).queryIntentServicesAsUser((Intent) any(), anyInt(), anyInt());

        doReturn(createFakeAppList("")).when(pm).
                queryIntentActivitiesAsUser((Intent) any(), anyInt(), anyInt());

        TelephonyManager tm = (TelephonyManager) mContextFixture.getTestDouble().
                getSystemService(Context.TELEPHONY_SERVICE);

        doReturn(true).when(tm).isSmsCapable();

        logd("-Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private List<ResolveInfo> createFakeAppList(String permission) {
        final List<ResolveInfo> appList = new ArrayList<ResolveInfo>();

        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = FAKE_PACKAGE_NAME;
        info.activityInfo.name = FAKE_NAME;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.permission = permission;
        info.resolvePackageName = FAKE_PACKAGE_NAME;
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = FAKE_PACKAGE_NAME;
        info.serviceInfo.name = FAKE_NAME;
        info.serviceInfo.applicationInfo = new ApplicationInfo();
        info.serviceInfo.permission = permission;
        info.labelRes = 1;
        appList.add(info);
        return appList;
    }

    @Test
    @SmallTest
    public void testGetApplicationCollection() {

        Collection<SmsApplicationData> c =
                SmsApplication.getApplicationCollection(mContextFixture.getTestDouble());

        for (SmsApplicationData app : c) {
            assertEquals(FAKE_PACKAGE_NAME, app.mPackageName);
            assertEquals(FAKE_NAME, app.mApplicationName);
        }
    }

    @Test
    @SmallTest
    public void testGetDefaultExternalTelephonyProviderChangedApplication() {

        ComponentName name = SmsApplication.getDefaultExternalTelephonyProviderChangedApplication(
                mContextFixture.getTestDouble(), false);
        assertEquals(FAKE_PACKAGE_NAME, name.getPackageName());
        assertEquals(FAKE_NAME, name.getClassName());
    }

    @Test
    @SmallTest
    public void testGetDefaultMmsApplication() {

        TelephonyManager tm = (TelephonyManager)
                mContextFixture.getTestDouble().getSystemService(Context.TELEPHONY_SERVICE);
        assertTrue(tm.isSmsCapable());

        int id = -1;
        try {
            Method m = SmsApplication.class.getDeclaredMethod("getIncomingUserId", Context.class);
            m.setAccessible(true);
            id = (Integer) m.invoke(null, mContextFixture.getTestDouble());
            logd("testGetDefaultMmsApplication: id = " + id);
        } catch (Exception ex) {
            fail("Failed to invoke getIncomingUserId(): " + ex);
        }
        assertEquals(0, id);

        Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);
        List<ResolveInfo> smsReceivers = mContextFixture.getTestDouble().getPackageManager().
                queryBroadcastReceivers(intent, 0, id);
        assertEquals(1, smsReceivers.size());
        assertEquals(FAKE_NAME, smsReceivers.get(0).activityInfo.name);
        assertEquals(FAKE_PACKAGE_NAME, smsReceivers.get(0).activityInfo.packageName);
        assertEquals(permission.BROADCAST_SMS, smsReceivers.get(0).activityInfo.permission);

        Collection<SmsApplicationData> apps = SmsApplication.getApplicationCollection(
                mContextFixture.getTestDouble());

        assertTrue(apps.size() > 0);

        assertEquals("[mApplicationName: fake messenger mPackageName: com.android.messaging " +
                "mSmsReceiverClass: fake messenger mMmsReceiverClass: fake messenger " +
                "mRespondViaMessageClass: fake messenger mSendToClass: fake messenger " +
                "mSmsAppChangedClass: fake messenger mProviderChangedReceiverClass: " +
                "fake messenger mUid: 0]", apps.toString());

        logd("apps=" + apps.toString());


        String defApp = Settings.Secure.getStringForUser(
                mContextFixture.getTestDouble().getContentResolver(),
                Settings.Secure.SMS_DEFAULT_APPLICATION, id);
        logd("testGetDefaultMmsApplication: defApp = " + defApp);
        assertEquals(FAKE_PACKAGE_NAME, defApp);

        String ret = null;
        try {
            Method m = SmsApplication.class.getDeclaredMethod("getApplicationForPackage",
                    Collection.class, String.class);
            m.setAccessible(true);
            ret = m.invoke(null, apps, FAKE_PACKAGE_NAME).toString();
            logd(ret);
        } catch (Exception ex) {
            fail("Failed to invoke getApplicationForPackage(): " + ex);
        }

        assertEquals("mApplicationName: fake messenger mPackageName: com.android.messaging " +
                        "mSmsReceiverClass: fake messenger mMmsReceiverClass: fake messenger " +
                        "mRespondViaMessageClass: fake messenger mSendToClass: fake messenger " +
                        "mSmsAppChangedClass: fake messenger mProviderChangedReceiverClass: " +
                        "fake messenger mUid: 0",
                ret);

        assertTrue(android.os.Process.myUid() != 0);

        ComponentName name = SmsApplication.getDefaultMmsApplication(
                mContextFixture.getTestDouble(), false);
        assertEquals(FAKE_PACKAGE_NAME, name.getPackageName());
        assertEquals(FAKE_NAME, name.getClassName());
    }

    @Test
    @SmallTest
    public void testGetDefaultRespondViaMessageApplication() {

        ComponentName name = SmsApplication.getDefaultRespondViaMessageApplication(
                mContextFixture.getTestDouble(), false);
        assertEquals(FAKE_PACKAGE_NAME, name.getPackageName());
        assertEquals(FAKE_NAME, name.getClassName());
    }

    @Test
    @SmallTest
    public void testGetDefaultSendToApplication() {

        ComponentName name = SmsApplication.getDefaultSendToApplication(
                mContextFixture.getTestDouble(), false);
        assertEquals(FAKE_PACKAGE_NAME, name.getPackageName());
        assertEquals(FAKE_NAME, name.getClassName());
    }

    @Test
    @SmallTest
    public void testGetDefaultSmsApplication() {

        ComponentName name = SmsApplication.getDefaultSmsApplication(
                mContextFixture.getTestDouble(), false);
        assertEquals(FAKE_PACKAGE_NAME, name.getPackageName());
        assertEquals(FAKE_NAME, name.getClassName());
    }

    @Test
    @SmallTest
    public void testIsDefaultSmsApplication() {

        assertTrue(SmsApplication.isDefaultSmsApplication(
                mContextFixture.getTestDouble(), FAKE_PACKAGE_NAME));
    }

    /*@Test
    @SmallTest
    public void testSetDefaultApplication() {
        SmsApplication.setDefaultApplication(FAKE_PACKAGE_NAME, mContextFixture.getTestDouble());
        ComponentName name = SmsApplication.getDefaultSmsApplication(
                mContextFixture.getTestDouble(), false);
        assertEquals(FAKE_PACKAGE_NAME, name.getPackageName());
        assertEquals(FAKE_NAME, name.getClassName());
    }*/

    @Test
    @SmallTest
    public void testShouldWriteMessageForPackage() {
        assertFalse(SmsApplication.shouldWriteMessageForPackage(
                FAKE_PACKAGE_NAME, mContextFixture.getTestDouble()));
    }
}