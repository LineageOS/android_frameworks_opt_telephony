/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import static com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataNetworkControllerTest extends TelephonyTest {
    private DataNetworkController mDataNetworkController;
    private DataNetworkControllerTestHandler mDataNetworkControllerTestHandler;
    private PersistableBundle mCarrierConfig;

    private class DataNetworkControllerTestHandler extends HandlerThread {

        private DataNetworkControllerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mDataNetworkController = new DataNetworkController(mPhone, getLooper());
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DataNetworkControllerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mCarrierConfig = mContextFixture.getCarrierConfigBundle();
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_TELEPHONY_NETWORK_CAPABILITY_PRIORITIES_STRING_ARRAY,
                new String[] {
                        "eims:90", "supl:80", "mms:70", "xcap:70", "cbs:50", "mcx:50", "fota:50",
                        "ims:40", "dun:30", "enterprise:20", "internet:20"
                });

        mDataNetworkControllerTestHandler = new DataNetworkControllerTestHandler(
                getClass().getSimpleName());
        mDataNetworkControllerTestHandler.start();
        waitUntilReady();
        waitForLastHandlerAction(mDataNetworkControllerTestHandler.getThreadHandler());
        doReturn(mDataNetworkController).when(mPhone).getDataNetworkController();

        logd("DataNetworkControllerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    // The purpose of this test is to make sure the network request insertion/removal works as
    // expected, and make sure it is always sorted.
    @Test
    public void testNetworkRequestList() {
        NetworkRequestList networkRequestList = new NetworkRequestList();

        int[] netCaps = new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_EIMS,
                NetworkCapabilities.NET_CAPABILITY_MMS};
        for (int netCap : netCaps) {
            networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                    .addCapability(netCap)
                    .build(), mPhone));
        }

        // Check if emergency has the highest priority, then mms, then internet.
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS,
                networkRequestList.get(0).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_MMS,
                networkRequestList.get(1).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_INTERNET,
                networkRequestList.get(2).getCapabilities()[0]);

        // Add IMS
        assertTrue(networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone)));

        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS,
                networkRequestList.get(0).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_MMS,
                networkRequestList.get(1).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS,
                networkRequestList.get(2).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_INTERNET,
                networkRequestList.get(3).getCapabilities()[0]);

        // Add IMS again
        assertFalse(networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone)));
        assertEquals(4, networkRequestList.size());

        // Remove MMS
        assertTrue(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                        .build(), mPhone)));
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS,
                networkRequestList.get(0).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS,
                networkRequestList.get(1).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_INTERNET,
                networkRequestList.get(2).getCapabilities()[0]);

        // Remove EIMS
        assertTrue(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                        .build(), mPhone)));
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS,
                networkRequestList.get(0).getCapabilities()[0]);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_INTERNET,
                networkRequestList.get(1).getCapabilities()[0]);

        // Remove Internet
        assertTrue(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone)));
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS,
                networkRequestList.get(0).getCapabilities()[0]);

        // Remove XCAP (which does not exist)
        assertFalse(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP)
                        .build(), mPhone)));
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS,
                networkRequestList.get(0).getCapabilities()[0]);

        // Remove IMS
        assertTrue(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone)));
        assertEquals(0, networkRequestList.size());
    }
}
