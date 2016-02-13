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

import android.app.IActivityManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.telephony.SubscriptionInfo;
import com.android.internal.telephony.uicc.IccFileHandler;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.internal.telephony.uicc.IccRecords;
import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import com.android.internal.telephony.uicc.IccCardProxy;

import static org.mockito.Mockito.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.HashMap;

public class SubscriptionInfoUpdaterTest extends TelephonyTest {
    private SubscriptionInfoUpdater mSubscriptionInfoUpdaterUT;
    private IccRecords mIccRecord;
    @Mock
    private IActivityManager mActivityManager;
    @Mock
    private UserInfo mUserInfo;
    @Mock
    private SubscriptionInfo mSubInfo;
    @Mock
    private ContentProvider mContentProvider;
    @Mock
    private HashMap<String, Object> mSubscriptionContent;
    @Mock
    private IccFileHandler mIccFileHandler;

    /*Custom ContentProvider */
    private class FakeSubscriptionContentProvider extends MockContentProvider {
        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            for (String key : values.keySet()) {
                mSubscriptionContent.put(key, values.get(key));
            }
            return 1;
        }
    }

    private class SubscriptionInfoUpdaterHandlerThread extends HandlerThread {

        private SubscriptionInfoUpdaterHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mSubscriptionInfoUpdaterUT = new SubscriptionInfoUpdater(mContext, new Phone[]{mPhone},
                    new CommandsInterface[]{mSimulatedCommands});
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        replaceInstance(SubscriptionInfoUpdater.class, "mIccId", null,
                new String[SubscriptionInfoUpdater.STATUS_SIM1_INSERTED]);
        replaceInstance(SubscriptionInfoUpdater.class, "mInsertSimState", null,
                new int[SubscriptionInfoUpdater.STATUS_SIM1_INSERTED]);
        replaceInstance(SubscriptionInfoUpdater.class, "mContext", null, null);
        replaceInstance(SubscriptionInfoUpdater.class, "PROJECT_SIM_NUM", null,
                SubscriptionInfoUpdater.STATUS_SIM1_INSERTED);

        TelephonyManager mTelephonyManager = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(SubscriptionInfoUpdater.STATUS_SIM1_INSERTED)
                .when(mTelephonyManager).getSimCount();
        doReturn(SubscriptionInfoUpdater.STATUS_SIM1_INSERTED)
                .when(mTelephonyManager).getPhoneCount();

        doReturn(mActivityManager).when(mActivityManagerNative).get();
        doReturn(mUserInfo).when(mActivityManager).getCurrentUser();
        mContentProvider = new FakeSubscriptionContentProvider();
        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                SubscriptionManager.CONTENT_URI.getAuthority(),
                mContentProvider);
        mIccRecord = mIccCardProxy.getIccRecords();

        new SubscriptionInfoUpdaterHandlerThread(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSimAbsent() {
        doReturn(Arrays.asList(mSubInfo)).when(mSubscriptionController)
                .getSubInfoUsingSlotIdWithCheck(eq(0), anyBoolean(), anyString());
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_ABSENT);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 0);

        mContext.sendBroadcast(mIntent);

        waitForMs(100);
        verify(mSubscriptionContent).put(eq(SubscriptionManager.SIM_SLOT_INDEX),
                eq(SubscriptionManager.INVALID_SIM_SLOT_INDEX));

        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager).updateConfigForPhoneId(eq(0),
                eq(IccCardConstants.INTENT_VALUE_ICC_ABSENT));
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimUnknown() {
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_UNKNOWN);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 1);

        mContext.sendBroadcast(mIntent);

        waitForMs(100);
        verify(mSubscriptionContent, times(0)).put(anyString(), any());
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager).updateConfigForPhoneId(eq(1),
                eq(IccCardConstants.INTENT_VALUE_ICC_UNKNOWN));
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimError() {
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 2);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);
        verify(mSubscriptionContent, times(0)).put(anyString(), any());
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager).updateConfigForPhoneId(eq(2),
                eq(IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR));
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testWrongSimState() {
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_IMSI);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 2);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);
        verify(mSubscriptionContent, times(0)).put(anyString(), any());
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(0)).updateConfigForPhoneId(eq(2),
                eq(IccCardConstants.INTENT_VALUE_ICC_IMSI));
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimLoaded() {
        /* mock new sim got loaded and there is no sim loaded before */
        doReturn(null).when(mSubscriptionController)
                .getSubInfoUsingSlotIdWithCheck(eq(0), anyBoolean(), anyString());
        doReturn("89012604200000000000").when(mIccRecord).getIccId();
        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOADED);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 0);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(
                eq("89012604200000000000"), eq(0));
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(0),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOADED));
    }

    @Test
    @SmallTest
    public void testSimLockedWithOutIccId() {
        /* mock no IccId Info present and try to query IccId
         after IccId query, update subscriptionDB */
        doReturn(mIccFileHandler).when(mIccCardProxy).getIccFileHandler();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message msg = (Message) invocation.getArguments()[1];
                AsyncResult.forMessage(msg).result = IccUtils
                        .hexStringToBytes("89012604200000000000");
                msg.sendToTarget();
                return null;
            }
        }).when(mIccFileHandler).loadEFTransparent(anyInt(), any(Message.class));

        doReturn(Arrays.asList(mSubInfo)).when(mSubscriptionController)
                .getSubInfoUsingSlotIdWithCheck(eq(0), anyBoolean(), anyString());

        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOCKED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, "TESTING");
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 0);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);

        /* old IccId != new queried IccId */
        verify(mSubscriptionContent).put(eq(SubscriptionManager.SIM_SLOT_INDEX),
                eq(SubscriptionManager.INVALID_SIM_SLOT_INDEX));
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(
                eq("98106240020000000000"), eq(0));

        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(0),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOCKED));
    }

    @Test
    @SmallTest
    public void testSimLockWIthIccId() {
        /* no need for IccId query */
        try {
            replaceInstance(SubscriptionInfoUpdater.class, "mIccId", null,
                    new String[]{"89012604200000000000"});
        } catch (Exception ex) {
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        doReturn(mIccFileHandler).when(mIccCardProxy).getIccFileHandler();

        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOCKED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, "TESTING");
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 0);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);

        verify(mIccFileHandler, times(0)).loadEFTransparent(anyInt(), any(Message.class));
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mSubscriptionManager, times(0)).addSubscriptionInfoRecord(
                anyString(), eq(0));
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        /* broadcast is done */
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(0),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOCKED));
    }

}