/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.testing.AndroidTestingRunner;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)

public class SatelliteConfigParserTest extends TelephonyTest {

    /**
     * satelliteConfigBuilder.setVersion(4);
     *
     * carrierSupportedSatelliteServiceBuilder.setCarrierId(1);
     *
     * satelliteProviderCapabilityBuilder.setCarrierPlmn("310160");
     * satelliteProviderCapabilityBuilder.addAllowedServices(1);
     * satelliteProviderCapabilityBuilder.addAllowedServices(2);
     * satelliteProviderCapabilityBuilder.addAllowedServices(3);
     *
     * satelliteProviderCapabilityBuilder.setCarrierPlmn("310220");
     * satelliteProviderCapabilityBuilder.addAllowedServices(3);
     *
     * String test = "0123456789";
     * bigString.append(test.repeat(1));
     * satelliteRegionBuilder.setS2CellFile(ByteString.copyFrom(bigString.toString().getBytes()));
     * satelliteRegionBuilder.addCountryCodes("US");
     * satelliteRegionBuilder.setIsAllowed(true);
     */
    private String mBase64StrForPBByteArray =
            "CjYIBBIeCAESDgoGMzEwMTYwEAEQAhADEgoKBjMxMDIyMBADGhIKCjAxMjM0NTY3ODkSAlVTGAE=";
    private byte[] mBytesProtoBuffer = Base64.getDecoder().decode(mBase64StrForPBByteArray);


    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testGetAllSatellitePlmnsForCarrier() {
        List<String> compareList_cid1 = new ArrayList<>();
        compareList_cid1.add("310160");
        compareList_cid1.add("310220");
        List<String> compareList_cid_placeholder = new ArrayList<>();
        compareList_cid_placeholder.add("310260");
        compareList_cid_placeholder.add("45060");


        SatelliteConfigParser satelliteConfigParserNull = new SatelliteConfigParser((byte[]) null);
        assertNotNull(satelliteConfigParserNull);
        assertNull(satelliteConfigParserNull.getConfig());

        SatelliteConfigParser satelliteConfigParserPlaceHolder =
                new SatelliteConfigParser((byte[]) null);
        assertNotNull(satelliteConfigParserPlaceHolder);
        assertNull(satelliteConfigParserPlaceHolder.getConfig());

        SatelliteConfigParser satelliteConfigParser = new SatelliteConfigParser(mBytesProtoBuffer);

        List<String> parsedList1 = satelliteConfigParser.getConfig()
                .getAllSatellitePlmnsForCarrier(1);
        Collections.sort(compareList_cid1);
        Collections.sort(compareList_cid_placeholder);
        Collections.sort(parsedList1);

        assertEquals(compareList_cid1, parsedList1);
        assertNotEquals(compareList_cid_placeholder, parsedList1);

        List<String> parsedList2 = satelliteConfigParser.getConfig()
                .getAllSatellitePlmnsForCarrier(0);
        assertEquals(0, parsedList2.size());
    }

    @Test
    public void testGetSupportedSatelliteServices() {
        Map<String, Set<Integer>> compareMapCarrierId1 = new HashMap<>();
        Set<Integer> compareSet310160 = new HashSet<>();
        compareSet310160.add(1);
        compareSet310160.add(2);
        compareSet310160.add(3);
        compareMapCarrierId1.put("310160", compareSet310160);

        Set<Integer> compareSet310220 = new HashSet<>();
        compareSet310220.add(3);
        compareMapCarrierId1.put("310220", compareSet310220);

        SatelliteConfigParser satelliteConfigParserNull = new SatelliteConfigParser((byte[]) null);
        assertNotNull(satelliteConfigParserNull);
        assertNull(satelliteConfigParserNull.getConfig());

        SatelliteConfigParser satelliteConfigParserPlaceholder =
                new SatelliteConfigParser("test".getBytes());
        assertNotNull(satelliteConfigParserPlaceholder);
        assertNull(satelliteConfigParserPlaceholder.getConfig());

        SatelliteConfigParser satelliteConfigParser = new SatelliteConfigParser(mBytesProtoBuffer);
        Map<String, Set<Integer>> parsedMap1 = satelliteConfigParser.getConfig()
                .getSupportedSatelliteServices(0);
        Map<String, Set<Integer>> parsedMap2 = satelliteConfigParser.getConfig()
                .getSupportedSatelliteServices(1);
        assertEquals(0, parsedMap1.size());
        assertEquals(2, parsedMap2.size());
        assertEquals(compareMapCarrierId1, parsedMap2);
    }

    @Test
    public void testGetDeviceSatelliteCountryCodes() {
        List<String> compareList_countryCodes = new ArrayList<>();
        compareList_countryCodes.add("US");
        Collections.sort(compareList_countryCodes);

        List<String> compareList_countryCodes_placeholder = new ArrayList<>();
        compareList_countryCodes_placeholder.add("US");
        compareList_countryCodes_placeholder.add("IN");
        Collections.sort(compareList_countryCodes_placeholder);

        SatelliteConfigParser satelliteConfigParserNull = new SatelliteConfigParser((byte[]) null);
        assertNotNull(satelliteConfigParserNull);
        assertNull(satelliteConfigParserNull.getConfig());

        SatelliteConfigParser satelliteConfigParser = new SatelliteConfigParser(mBytesProtoBuffer);
        List<String> tempList = satelliteConfigParser.getConfig().getDeviceSatelliteCountryCodes();
        List<String> parsedList = new ArrayList<>(tempList);
        Collections.sort(parsedList);

        assertEquals(compareList_countryCodes, parsedList);
        assertNotEquals(compareList_countryCodes_placeholder, parsedList);
    }

    @Test
    public void testGetSatelliteS2CellFile() {
        final String filePath = "/data/user_de/0/com.android.phone/app_satellite/s2_cell_file";
        Path targetSatS2FilePath = Paths.get(filePath);

        SatelliteConfigParser mockedSatelliteConfigParserNull = spy(
                new SatelliteConfigParser((byte[]) null));
        assertNotNull(mockedSatelliteConfigParserNull);
        assertNull(mockedSatelliteConfigParserNull.getConfig());

        SatelliteConfigParser mockedSatelliteConfigParserPlaceholder = spy(
                new SatelliteConfigParser("test".getBytes()));
        assertNotNull(mockedSatelliteConfigParserPlaceholder);
        assertNull(mockedSatelliteConfigParserPlaceholder.getConfig());

        SatelliteConfigParser mockedSatelliteConfigParser =
                spy(new SatelliteConfigParser(mBytesProtoBuffer));
        SatelliteConfig mockedSatelliteConfig = Mockito.mock(SatelliteConfig.class);
        doReturn(targetSatS2FilePath).when(mockedSatelliteConfig).getSatelliteS2CellFile(any());
        doReturn(mockedSatelliteConfig).when(mockedSatelliteConfigParser).getConfig();
//        assertNotNull(mockedSatelliteConfigParser.getConfig());
//        doReturn(false).when(mockedSatelliteConfigParser).getConfig().isFileExist(any());
//        doReturn(targetSatS2FilePath).when(mockedSatelliteConfigParser).getConfig()
//                .copySatS2FileToPhoneDirectory(any(), any());
        assertEquals(targetSatS2FilePath,
                mockedSatelliteConfigParser.getConfig().getSatelliteS2CellFile(mContext));
    }

    @Test
    public void testGetSatelliteAccessAllow() {
        SatelliteConfigParser satelliteConfigParserNull = new SatelliteConfigParser((byte[]) null);
        assertNotNull(satelliteConfigParserNull);
        assertNull(satelliteConfigParserNull.getConfig());

        SatelliteConfigParser satelliteConfigParserPlaceholder =
                new SatelliteConfigParser("test".getBytes());
        assertNotNull(satelliteConfigParserPlaceholder);
        assertNull(satelliteConfigParserPlaceholder.getConfig());

        SatelliteConfigParser satelliteConfigParser = new SatelliteConfigParser(mBytesProtoBuffer);
        assertNotNull(satelliteConfigParser);
        assertNotNull(satelliteConfigParser.getConfig());
        assertTrue(satelliteConfigParser.getConfig().isSatelliteDataForAllowedRegion());
    }
}
