/*
 * Copyright 2020 The Android Open Source Project
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

package com.telephony.ims;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.os.Parcel;
import android.telephony.SubscriptionManager;
import android.telephony.ims.RcsConfig;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.FakeTelephonyProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class RcsConfigTest {

    private static final String TEST_RCS_CONFIG = "<RCSConfig>\n"
            + "\t<rcsVolteSingleRegistration>1</rcsVolteSingleRegistration>\n"
            + "\t<SERVICES>\n"
            + "\t\t<SupportedRCSProfileVersions>UP_2.0</SupportedRCSProfileVersions>\n"
            + "\t\t<ChatAuth>1</ChatAuth>\n"
            + "\t\t<GroupChatAuth>1</GroupChatAuth>\n"
            + "\t\t<ftAuth>1</ftAuth>\n"
            + "\t\t<standaloneMsgAuth>1</standaloneMsgAuth>\n"
            + "\t\t<geolocPushAuth>1</geolocPushAuth>\n"
            + "\t\t<Ext>\n"
            + "\t\t\t<DataOff>\n"
            + "\t\t\t\t<rcsMessagingDataOff>1</rcsMessagingDataOff>\n"
            + "\t\t\t\t<fileTransferDataOff>1</fileTransferDataOff>\n"
            + "\t\t\t\t<mmsDataOff>1</mmsDataOff>\n"
            + "\t\t\t\t<syncDataOff>1</syncDataOff>\n"
            + "\t\t\t</DataOff>\n"
            + "\t\t</Ext>\n"
            + "\t</SERVICES>\n"
            + "</RCSConfig>";

    private static final String[][] TEST_CONFIG_VALUES = {{"rcsVolteSingleRegistration", "1"},
        {"SupportedRCSProfileVersions", "UP_2.0"}, {"ChatAuth", "1"}, {"GroupChatAuth", "1"},
        {"ftAuth", "1"}, {"standaloneMsgAuth", "1"}, {"geolocPushAuth", "1"},
        {"rcsMessagingDataOff", "1"}, {"fileTransferDataOff", "1"}, {"mmsDataOff", "1"},
        {"syncDataOff", "1"}};

    private static final int FAKE_SUB_ID = 1;
    private MockContentResolver mContentResolver;
    private FakeTelephonyProvider mFakeTelephonyProvider;
    @Mock
    Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFakeTelephonyProvider = new FakeTelephonyProvider();
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(SubscriptionManager.CONTENT_URI.getAuthority(),
                mFakeTelephonyProvider);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        createFakeSimInfo();
    }

    @Test
    @SmallTest
    public void testLoadAndUpdateConfigForSub() {

        byte[] currentData = RcsConfig.loadRcsConfigForSub(mContext, FAKE_SUB_ID, false);

        RcsConfig.updateConfigForSub(mContext, FAKE_SUB_ID, null, false);
        byte[] updatedData = RcsConfig.loadRcsConfigForSub(mContext, FAKE_SUB_ID, false);
        assertNull(updatedData);

        RcsConfig.updateConfigForSub(mContext, FAKE_SUB_ID, TEST_RCS_CONFIG.getBytes(), false);
        updatedData = RcsConfig.loadRcsConfigForSub(mContext, FAKE_SUB_ID, false);
        assertTrue(Arrays.equals(updatedData, TEST_RCS_CONFIG.getBytes()));

        RcsConfig.updateConfigForSub(mContext, FAKE_SUB_ID, currentData, false);
        updatedData = RcsConfig.loadRcsConfigForSub(mContext, FAKE_SUB_ID, false);
        assertTrue(Arrays.equals(currentData, updatedData));
    }

    @Test
    @SmallTest
    public void testCompressAndDecompress() {
        byte[] compressedData = RcsConfig.compressGzip(TEST_RCS_CONFIG.getBytes());
        assertFalse(Arrays.equals(compressedData, TEST_RCS_CONFIG.getBytes()));
        byte[] decompressedData = RcsConfig.decompressGzip(compressedData);
        assertTrue(Arrays.equals(decompressedData, TEST_RCS_CONFIG.getBytes()));
        assertNull(RcsConfig.compressGzip(null));
        assertNull(RcsConfig.decompressGzip(null));
        byte[] emptyData = new byte[0];
        assertEquals(emptyData, RcsConfig.compressGzip(emptyData));
        assertEquals(emptyData, RcsConfig.decompressGzip(emptyData));
    }

    @Test
    @SmallTest
    public void testParseConfig() {
        RcsConfig config = new RcsConfig(TEST_RCS_CONFIG.getBytes());
        for (int i = 0; i < TEST_CONFIG_VALUES.length; i++) {
            assertEquals(config.getString(TEST_CONFIG_VALUES[i][0], null),
                    TEST_CONFIG_VALUES[i][1]);
        }
    }

    @Test
    @SmallTest
    public void testParcelUnparcel() {
        RcsConfig config = new RcsConfig(TEST_RCS_CONFIG.getBytes());
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RcsConfig config2 = RcsConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        assertTrue(config.equals(config2));
    }

    @Test
    @SmallTest
    public void testIsRcsVolteSingleRegistrationSupported() {
        String[] vals = new String[]{"0", "1", "false", "true"};
        boolean[] expectedRes = new boolean[]{false, true, false, true};
        for (int i = 0; i < vals.length; i++) {
            String xml = "<RCSConfig>\n" + "\t<rcsVolteSingleRegistration>" + vals[i]
                    + "</rcsVolteSingleRegistration>\n" + "</RCSConfig>\n";
            RcsConfig config = new RcsConfig(xml.getBytes());
            assertEquals(config.isRcsVolteSingleRegistrationSupported(), expectedRes[i]);
        }
    }

    private void createFakeSimInfo() {
        ContentValues contentValues = new ContentValues();
        final String fakeIccId = "fakeleIccId";
        final String fakeCardId = "fakeCardId";
        contentValues.put(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID, FAKE_SUB_ID);
        contentValues.put(SubscriptionManager.ICC_ID, fakeIccId);
        contentValues.put(SubscriptionManager.CARD_ID, fakeCardId);
        mContentResolver.insert(SubscriptionManager.CONTENT_URI, contentValues);
    }
}
