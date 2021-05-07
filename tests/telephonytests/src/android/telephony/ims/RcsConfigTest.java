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
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.ims.RcsConfig;
import android.telephony.ims.RcsConfig.Characteristic;
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

    private static final String TEST_RCS_CONFIG = "<?xml version=\"1.0\"?>\n"
            + "<wap-provisioningdoc version=\"1.1\">\n"
            + "\t<characteristic type=\"APPLICATION\">\n"
            + "\t\t<parm name=\"AppID\" value=\"urn:oma:mo:ext-3gpp-ims:1.0\"/>\n"
            + "\t\t<characteristic type=\"3GPP_IMS\">\n"
            + "\t\t\t<parm name=\"AppID\" value=\"ap2001\"/>\n"
            + "\t\t\t<parm name=\"Name\" value=\"RCS IMS Settings\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"GSMA\">\n"
            + "\t\t\t\t\t<parm name=\"AppRef\" value=\"IMS-Setting\"/>\n"
            + "\t\t\t\t\t<parm name=\"rcsVolteSingleRegistration\" value=\"1\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t\t<characteristic type=\"SERVICES\">\n"
            + "\t\t\t<parm name=\"SupportedRCSProfileVersions\" value=\"UP_2.3\"/>\n"
            + "\t\t\t<parm name=\"ChatAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"GroupChatAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"ftAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"standaloneMsgAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"geolocPushAuth\" value=\"1\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"DataOff\">\n"
            + "\t\t\t\t\t<parm name=\"rcsMessagingDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"fileTransferDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"mmsDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"syncDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<characteristic type=\"Ext\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t\t<characteristic type=\"PRESENCE\">\n"
            + "\t\t\t<parm name=\"client-obj-datalimit\" value=\"8192\"/>\n"
            + "\t\t\t<parm name=\"content-serveruri\" value=\"X\"/>\n"
            + "\t\t\t<parm name=\"source-throttlepublish\" value=\"32\"/>\n"
            + "\t\t\t<parm name=\"max-number-ofsubscriptions-inpresence-list\" value=\"8\"/>\n"
            + "\t\t\t<parm name=\"service-uritemplate\" value=\"X\"/>\n"
            + "\t\t\t<parm name=\"RLS-URI\" value=\"X\"/>\n"
            + "\t\t</characteristic>\n"
            + "\t\t<characteristic type=\"MESSAGING\">\n"
            + "\t\t\t<characteristic type=\"StandaloneMsg\">\n"
            + "\t\t\t\t<parm name=\"MaxSize\" value=\"8192\"/>\n"
            + "\t\t\t\t<parm name=\"SwitchoverSize\" value=\"1024\"/>\n"
            + "\t\t\t\t<parm name=\"exploder-uri\" value=\"X\"/>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t\t<characteristic type=\"Chat\">\n"
            + "\t\t\t\t<parm name=\"max_adhoc_group_size\" value=\"60\"/>\n"
            + "\t\t\t\t<parm name=\"conf-fcty-uri\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"AutAccept\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"AutAcceptGroupChat\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"TimerIdle\" value=\"120\"/>\n"
            + "\t\t\t\t<parm name=\"MaxSize\" value=\"16384\"/>\n"
            + "\t\t\t\t<parm name=\"ChatRevokeTimer\" value=\"0\"/>\n"
            + "\t\t\t\t<parm name=\"reconnectGuardTimer\" value=\"0\"/>\n"
            + "\t\t\t\t<parm name=\"cfsTrigger\" value=\"1\"/>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t\t<parm name=\"max1ToManyRecipients\" value=\"8\"/>\n"
            + "\t\t\t<parm name=\"1toManySelectedTech\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"displayNotificationSwitch\" value=\"0\"/>\n"
            + "\t\t\t<parm name=\"contentCompressSize\" value=\"1024\"/>\n"
            + "\t\t\t<characteristic type=\"FileTransfer\">\n"
            + "\t\t\t\t<parm name=\"ftWarnSize\" value=\"0\"/>\n"
            + "\t\t\t\t<parm name=\"MaxSizeFileTr\" value=\"65536\"/>\n"
            + "\t\t\t\t<parm name=\"ftAutAccept\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"ftHTTPCSURI\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"ftHTTPDLURI\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"ftHTTPCSUser\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"ftHTTPCSPwd\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"ftHTTPFallback\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\" ftMax1ToManyRecipients\" value=\"0\"/>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t\t<characteristic type=\"Chatbot\">\n"
            + "\t\t\t\t<parm name=\"ChatbotDirectory\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"BotinfoFQDNRoot\" value=\"X\"/>\n"
            + "\t\t\t\t<part name=\"SpecificChatbotsList\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"IdentityInEnrichedSearch\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"PrivacyDisable\" value=\"0\"/>\n"
            + "\t\t\t\t<parm name=\"ChatbotMsgTech\" value=\"1\"/>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t\t<characteristic type=\"MessageStore\">\n"
            + "\t\t\t\t<parm name=\"MsgStoreUrl\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"MsgStoreNotifUrl\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"MsgStoreAuth\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"MsgStoreUserName\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"MsgStoreUserPwd\" value=\"X\"/>\n"
            + "\t\t\t\t<parm name=\"EventRpting\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"AuthArchive\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"SMSStore\" value=\"1\"/>\n"
            + "\t\t\t\t<parm name=\"MMSStore\" value=\"1\"/>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t\t<characteristic type=\"Ext\"/>\n"
            + "\t\t</characteristic>\n"
            + "\t</characteristic>\n"
            + "</wap-provisioningdoc>\n";

    private static final String[][] TEST_CONFIG_VALUES = {{"rcsVolteSingleRegistration", "1"},
        {"SupportedRCSProfileVersions", "UP_2.3"}, {"ChatAuth", "1"}, {"GroupChatAuth", "1"},
        {"ftAuth", "1"}, {"standaloneMsgAuth", "1"}, {"geolocPushAuth", "1"},
        {"rcsMessagingDataOff", "1"}, {"fileTransferDataOff", "1"}, {"mmsDataOff", "1"},
        {"syncDataOff", "1"}};

    private static final String[] VALID_CHARACTERISTICS = {"APPLICATION", "3GPP_IMS", "Ext",
        "GSMA",  "SERVICES", "DaTAOFF", "PRESENCE", "MESSAGING", "Chat", "FileTransfer",
        "Chatbot", "MessageSTORE"};
    private static final String[] INVALID_CHARACTERISTICS = {"APP2LICATION", "3GPPIMS", "Exte",
        "GSMf",  "SERVICE", "DaTAOn", "PRESENCE2", "MESSAG", "Ch", "File", "STORE"};
    private static final String[][] SUB_CHARACTERISTICS = {
        {"APPLICATION", "3GPP_IMS", "Ext", "GSMA"},
        {"APPLICATION", "SERVICES", "Ext", "DataOff", "Ext"}};
    private static final String[][] SAME_PARMS_DIFF_CHARS_VALUE_MAP = {
        {"MaxSize", "Chat", "16384"}, {"MaxSize", "StandaloneMsg", "8192"}};

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
    public void testGetCharacteristic() {
        RcsConfig config = new RcsConfig(TEST_RCS_CONFIG.getBytes());

        for (int i = 0; i < VALID_CHARACTERISTICS.length; i++) {
            assertNotNull(config.getCharacteristic(VALID_CHARACTERISTICS[i]));
        }

        for (int i = 0; i < INVALID_CHARACTERISTICS.length; i++) {
            assertNull(config.getCharacteristic(INVALID_CHARACTERISTICS[i]));
        }
    }

    @Test
    @SmallTest
    public void testSetAndMoveCharacteristic() {
        RcsConfig config = new RcsConfig(TEST_RCS_CONFIG.getBytes());

        for (String[] sub : SUB_CHARACTERISTICS) {
            Characteristic[] cl = new Characteristic[sub.length];
            int c = 0;
            for (String cur : sub) {
                cl[c] = config.getCharacteristic(cur);
                assertNotNull(cl[c]);
                config.setCurrentCharacteristic(cl[c]);
                c++;
            }

            while (c > 0) {
                assertEquals(cl[--c], config.getCurrentCharacteristic());
                config.moveToParent();
            }

            assertEquals(config.getRoot(), config.getCurrentCharacteristic());
        }
    }

    @Test
    @SmallTest
    public void testGetDuplicateParmInDifferentCharacteristics() {
        RcsConfig config = new RcsConfig(TEST_RCS_CONFIG.getBytes());
        for (String[] sub : SAME_PARMS_DIFF_CHARS_VALUE_MAP) {
            config.moveToRoot();
            if (!sub[1].isEmpty()) {
                config.setCurrentCharacteristic(config.getCharacteristic(sub[1]));
            }

            String value = config.getString(sub[0], "");

            assertEquals(value, sub[2]);
        }
    }

    @Test
    @SmallTest
    public void testIsRcsVolteSingleRegistrationSupported() {
        String[] vals = new String[]{"0", "1", "2"};
        boolean[] expectedResHome = new boolean[]{false, true, true};
        boolean[] expectedResRoaming = new boolean[]{false, true, false};
        for (int i = 0; i < vals.length; i++) {
            String xml = "\t\t\t\t<characteristic type=\"GSMA\">\n"
                    + "\t\t\t\t\t<parm name=\"rcsVolteSingleRegistration\" value=\""
                    + vals[i] + "\"/>\n" + "\t\t\t\t</characteristic>\n";
            RcsConfig config = new RcsConfig(xml.getBytes());
            assertEquals(config.isRcsVolteSingleRegistrationSupported(false), expectedResHome[i]);
            assertEquals(config.isRcsVolteSingleRegistrationSupported(true),
                    expectedResRoaming[i]);
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
