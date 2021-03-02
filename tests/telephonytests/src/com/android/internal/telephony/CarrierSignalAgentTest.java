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

import static android.telephony.TelephonyManager.ACTION_CARRIER_SIGNAL_PCO_VALUE;
import static android.telephony.TelephonyManager.ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierSignalAgentTest extends TelephonyTest {

    private CarrierSignalAgent mCarrierSignalAgentUT;
    private PersistableBundle mBundle;
    private static final String PCO_RECEIVER = "pak/PCO_RECEIVER";
    private static final String DC_ERROR_RECEIVER = "pak/DC_ERROR_RECEIVER";
    private static final String LEGACY_RECEIVER = "old.pkg/LEGACY_RECEIVER";
    private static final String PCO_PACKAGE = "pak";

    private static final Intent FAKE_PCO_INTENT;
    private static final Intent FAKE_REDIRECTED_INTENT;
    private static final Intent FAKE_NETWORK_FAILED_INTENT;
    private static final Intent FAKE_RESET_INTENT;
    private static final Intent FAKE_DEFAULT_NETWORK_INTENT;
    static {
        FAKE_PCO_INTENT = new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE);
        FAKE_PCO_INTENT.putExtra(TelephonyManager.EXTRA_APN_TYPE, ApnSetting.TYPE_MMS);
        FAKE_PCO_INTENT.putExtra(TelephonyManager.EXTRA_APN_PROTOCOL, ApnSetting.PROTOCOL_IP);
        FAKE_PCO_INTENT.putExtra(TelephonyManager.EXTRA_PCO_ID, 500);
        FAKE_PCO_INTENT.putExtra(TelephonyManager.EXTRA_PCO_VALUE, new byte[]{1, 2, 3});

        FAKE_REDIRECTED_INTENT = new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_REDIRECTED);
        FAKE_REDIRECTED_INTENT.putExtra(TelephonyManager.EXTRA_APN_TYPE, ApnSetting.TYPE_MMS);
        FAKE_REDIRECTED_INTENT.putExtra(TelephonyManager.EXTRA_REDIRECTION_URL, "example.com");

        FAKE_NETWORK_FAILED_INTENT = new Intent(ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED);
        FAKE_NETWORK_FAILED_INTENT.putExtra(TelephonyManager.EXTRA_APN_TYPE, ApnSetting.TYPE_MMS);
        FAKE_NETWORK_FAILED_INTENT.putExtra(TelephonyManager.EXTRA_DATA_FAIL_CAUSE,
                DataFailCause.UNKNOWN_PDP_CONTEXT);

        FAKE_RESET_INTENT = new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_RESET);

        FAKE_DEFAULT_NETWORK_INTENT =
                new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE);
        FAKE_DEFAULT_NETWORK_INTENT.putExtra(
                TelephonyManager.EXTRA_DEFAULT_NETWORK_AVAILABLE, true);
    }

    @Mock
    ResolveInfo mResolveInfo;

    @Before
    public void setUp() throws Exception {
        logd("CarrierSignalAgentTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        mCarrierSignalAgentUT = new CarrierSignalAgent(mPhone);

        ComponentName legacyReceiverComponent = ComponentName.unflattenFromString(LEGACY_RECEIVER);
        ApplicationInfo fakeLegacyApplicationInfo = new ApplicationInfo();
        fakeLegacyApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;

        ApplicationInfo fakeApplicationInfo = new ApplicationInfo();
        fakeApplicationInfo.targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;

        when(mContext.getPackageManager().getApplicationInfo(
                nullable(String.class), anyInt()))
                .thenReturn(fakeApplicationInfo);
        when(mContext.getPackageManager().getApplicationInfo(
                eq(legacyReceiverComponent.getPackageName()), anyInt()))
                .thenReturn(fakeLegacyApplicationInfo);

        processAllMessages();
        logd("CarrierSignalAgentTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testNotifyManifestReceivers() throws Exception {
        // Broadcast count
        int count = 0;
        Intent intent = new Intent(FAKE_PCO_INTENT);
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{PCO_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_PCO_VALUE,
                        DC_ERROR_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_PCO_VALUE,
                        LEGACY_RECEIVER + ":" + TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE
                });

        // Verify no broadcast has been sent without carrier config
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(intent);
        ArgumentCaptor<Intent> mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());

        // Trigger carrier config reloading
        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();
        count++;

        // Verify no broadcast has been sent due to no manifest receivers
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(intent);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());

        // Verify broadcast has been sent to two different registered manifest receivers
        doReturn(new ArrayList<>(Arrays.asList(mResolveInfo)))
                .when(mPackageManager).queryBroadcastReceivers((Intent) any(), anyInt());
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(intent);
        count += 3;
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());

        logd(mCaptorIntent.getAllValues().toString());
        Map<String, Intent> componentToIntent = mCaptorIntent.getAllValues().stream()
                .collect(Collectors.toMap((i) ->
                        i.getComponent() == null ? "null" : i.getComponent().flattenToString(),
                        Function.identity()));
        Intent dcErrorIntent = componentToIntent.get(DC_ERROR_RECEIVER);
        assertNotNull(dcErrorIntent);
        assertEquals(ACTION_CARRIER_SIGNAL_PCO_VALUE, dcErrorIntent.getAction());

        Intent pcoReceiverIntent = componentToIntent.get(PCO_RECEIVER);
        assertNotNull(pcoReceiverIntent);
        assertEquals(ACTION_CARRIER_SIGNAL_PCO_VALUE, pcoReceiverIntent.getAction());

        Intent legacyReceiverIntent = componentToIntent.get(LEGACY_RECEIVER);
        assertNotNull(legacyReceiverIntent);
        assertEquals(TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE,
                legacyReceiverIntent.getAction());
    }

    @Test
    @SmallTest
    public void testLegacyConversionSupport() {
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{LEGACY_RECEIVER + ":" + String.join(",",
                        TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE,
                        TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED,
                        TelephonyIntents.ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED,
                        TelephonyIntents.ACTION_CARRIER_SIGNAL_RESET,
                        TelephonyIntents.ACTION_CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE)
                });

        // Trigger carrier config reloading
        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();

        // Verify broadcast has been sent to two different registered manifest receivers
        doReturn(new ArrayList<>(Arrays.asList(mResolveInfo)))
                .when(mPackageManager).queryBroadcastReceivers((Intent) any(), anyInt());

        int broadcastCount = 1;
        {
            mCarrierSignalAgentUT.notifyCarrierSignalReceivers(new Intent(FAKE_PCO_INTENT));
            broadcastCount++;
            ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(mContext, times(broadcastCount)).sendBroadcast(intentCaptor.capture());
            Intent intent = intentCaptor.getValue();
            assertTrue(intent.hasExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX));

            assertEquals(TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE, intent.getAction());
            verifyLegacyApnTypes(intent, FAKE_PCO_INTENT);
            assertEquals(FAKE_PCO_INTENT.getIntExtra(
                    TelephonyManager.EXTRA_PCO_ID, Integer.MAX_VALUE),
                    intent.getIntExtra(TelephonyIntents.EXTRA_PCO_ID, Integer.MIN_VALUE));
            assertArrayEquals(FAKE_PCO_INTENT.getByteArrayExtra(
                    TelephonyManager.EXTRA_PCO_VALUE),
                    intent.getByteArrayExtra(TelephonyIntents.EXTRA_PCO_VALUE));
        }

        {
            mCarrierSignalAgentUT.notifyCarrierSignalReceivers(new Intent(FAKE_REDIRECTED_INTENT));
            broadcastCount++;
            ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(mContext, times(broadcastCount)).sendBroadcast(intentCaptor.capture());
            Intent intent = intentCaptor.getValue();
            assertTrue(intent.hasExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX));

            assertEquals(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED, intent.getAction());
            verifyLegacyApnTypes(intent, FAKE_REDIRECTED_INTENT);
            assertEquals(
                    FAKE_REDIRECTED_INTENT.getStringExtra(TelephonyManager.EXTRA_REDIRECTION_URL),
                    intent.getStringExtra(TelephonyIntents.EXTRA_REDIRECTION_URL));
        }

        {
            mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                    new Intent(FAKE_NETWORK_FAILED_INTENT));
            broadcastCount++;
            ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(mContext, times(broadcastCount)).sendBroadcast(intentCaptor.capture());
            Intent intent = intentCaptor.getValue();
            assertTrue(intent.hasExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX));

            assertEquals(TelephonyIntents.ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED,
                    intent.getAction());
            verifyLegacyApnTypes(intent, FAKE_REDIRECTED_INTENT);
            assertEquals(
                    FAKE_NETWORK_FAILED_INTENT.getIntExtra(
                            TelephonyManager.EXTRA_DATA_FAIL_CAUSE, Integer.MAX_VALUE),
                    intent.getIntExtra(TelephonyIntents.EXTRA_ERROR_CODE, Integer.MIN_VALUE));
        }

        {
            mCarrierSignalAgentUT.notifyCarrierSignalReceivers(new Intent(FAKE_RESET_INTENT));
            broadcastCount++;
            ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(mContext, times(broadcastCount)).sendBroadcast(intentCaptor.capture());
            Intent intent = intentCaptor.getValue();
            assertTrue(intent.hasExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX));

            assertEquals(TelephonyIntents.ACTION_CARRIER_SIGNAL_RESET, intent.getAction());
        }

        {
            mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                    new Intent(FAKE_DEFAULT_NETWORK_INTENT));
            broadcastCount++;
            ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(mContext, times(broadcastCount)).sendBroadcast(intentCaptor.capture());
            Intent intent = intentCaptor.getValue();
            assertTrue(intent.hasExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX));

            assertEquals(TelephonyIntents.ACTION_CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE,
                    intent.getAction());

            assertEquals(
                    FAKE_DEFAULT_NETWORK_INTENT.getBooleanExtra(
                            TelephonyManager.EXTRA_DEFAULT_NETWORK_AVAILABLE, false),
                    intent.getBooleanExtra(TelephonyIntents.EXTRA_DEFAULT_NETWORK_AVAILABLE, true));
        }
    }

    private void verifyLegacyApnTypes(Intent legacyIntent, Intent originalIntent) {
        int apnType = originalIntent.getIntExtra(TelephonyManager.EXTRA_APN_TYPE,
                Integer.MAX_VALUE);
        String apnTypeString = ApnSetting.getApnTypeString(apnType);
        assertNotEquals("Unknown", apnTypeString);

        assertEquals(apnTypeString,
                legacyIntent.getStringExtra(TelephonyIntents.EXTRA_APN_TYPE));
        assertEquals(apnType,
                legacyIntent.getIntExtra(TelephonyIntents.EXTRA_APN_TYPE_INT, Integer.MIN_VALUE));
    }

    @Test
    @SmallTest
    public void testNotifyRuntimeReceivers() throws Exception {
        // Broadcast count
        int count = 0;
        Intent intent = new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE);
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_NO_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{PCO_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_PCO_VALUE});

        // Verify no broadcast without carrier configs
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(intent);
        ArgumentCaptor<Intent> mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());

        // Trigger carrier config reloading
        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();
        count++;

        // Verify broadcast has been sent to registered components
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(intent);
        verify(mContext, times(++count)).sendBroadcast(mCaptorIntent.capture());
        assertEquals(ACTION_CARRIER_SIGNAL_PCO_VALUE,
                mCaptorIntent.getValue().getAction());
        assertEquals(PCO_PACKAGE, mCaptorIntent.getValue().getPackage());

        // Verify no broadcast has been sent to manifest receivers (bad config)
        doReturn(new ArrayList<>(Arrays.asList(mResolveInfo)))
                .when(mPackageManager).queryBroadcastReceivers((Intent) any(), anyInt());
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(intent);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());
    }

    @Test
    @SmallTest
    public void testNotify() {
        // Broadcast count
        int count = 0;
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{ PCO_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_PCO_VALUE });
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_NO_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{ PCO_RECEIVER + ":"
                        + ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED });
        // Only wake signal is declared in the manifest
        doReturn(new ArrayList<>(Arrays.asList(mResolveInfo)))
                .when(mPackageManager).queryBroadcastReceivers(
                argThat(o -> Objects.equals(o.getAction(), ACTION_CARRIER_SIGNAL_PCO_VALUE)),
                anyInt());

        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();
        count++;

        // Wake signal for PAK_PCO_RECEIVER
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE));
        ArgumentCaptor<Intent> mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(++count)).sendBroadcast(mCaptorIntent.capture());
        assertEquals(ACTION_CARRIER_SIGNAL_PCO_VALUE, mCaptorIntent.getValue().getAction());
        assertEquals(PCO_RECEIVER, mCaptorIntent.getValue().getComponent().flattenToString());

        // No wake signal for PAK_PCO_RECEIVER
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                new Intent(ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED));
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(++count)).sendBroadcast(mCaptorIntent.capture());
        assertEquals(ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED,
                mCaptorIntent.getValue().getAction());
        assertEquals(PCO_PACKAGE, mCaptorIntent.getValue().getPackage());

        // Both wake and no-wake signals are declared in the manifest
        doReturn(new ArrayList<>(Arrays.asList(mResolveInfo)))
                .when(mPackageManager).queryBroadcastReceivers((Intent) any(), anyInt());
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE));
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(++count)).sendBroadcast(mCaptorIntent.capture());
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                new Intent(ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED));
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());

        // Neither wake nor no-wake signals are declared in the manifest
        doReturn(new ArrayList<>()).when(mPackageManager).queryBroadcastReceivers((Intent) any(),
                anyInt());
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                new Intent(ACTION_CARRIER_SIGNAL_PCO_VALUE));
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(count)).sendBroadcast(mCaptorIntent.capture());
        mCarrierSignalAgentUT.notifyCarrierSignalReceivers(
                new Intent(ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED));
        mCaptorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(++count)).sendBroadcast(mCaptorIntent.capture());
    }


    @Test
    @SmallTest
    public void testCarrierConfigChange() {
        // default config value
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{ PCO_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_PCO_VALUE + ","
                        + ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED });
        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();
        // verify no reset action on initial config load
        verify(mCarrierActionAgent, times(0)).sendMessageAtTime(any(Message.class), anyLong());

        // new carrier config with different receiver intent order
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{ PCO_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED
                        + "," + ACTION_CARRIER_SIGNAL_PCO_VALUE});
        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();
        // verify no reset action for the same config (different order)
        verify(mCarrierActionAgent, times(0)).sendMessageAtTime(any(Message.class), anyLong());

        // new different config value
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                new String[]{ DC_ERROR_RECEIVER + ":" + ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED
                        + "," + ACTION_CARRIER_SIGNAL_PCO_VALUE});
        mContext.sendBroadcast(new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        processAllMessages();
        // verify there is no reset action
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mCarrierActionAgent, times(1))
                .sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(CarrierActionAgent.CARRIER_ACTION_RESET,
                messageArgumentCaptor.getValue().what);
    }
}
