/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.testing.TestableLooper;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

/** Test for {@link TelephonyCountryDetector} */
@RunWith(AndroidJUnit4.class)
public class TelephonyCountryDetectorTest extends TelephonyTest {
    private static final String TAG = "TelephonyCountryDetectorTest";

    @Mock
    ServiceStateTracker mSST2;
    @Mock
    LocaleTracker mMockLocaleTracker;
    @Mock
    LocaleTracker mMockLocaleTracker2;
    @Mock Location mMockLocation;
    @Mock Network mMockNetwork;

    @Captor
    private ArgumentCaptor<LocationListener> mLocationListenerCaptor;
    @Captor
    private ArgumentCaptor<ConnectivityManager.NetworkCallback> mNetworkCallbackCaptor;

    private LocaleTracker[] mLocaleTrackers;
    private Looper mLooper;
    private NetworkCapabilities mNetworkCapabilities;
    private TestTelephonyCountryDetector mCountryDetectorUT;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        HandlerThread handlerThread = new HandlerThread("CountryDetectorTest");
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mTestableLooper = new TestableLooper(mLooper);

        mLocaleTrackers = new LocaleTracker[]{mMockLocaleTracker, mMockLocaleTracker2};
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone, mPhone2});
        when(mPhone.getServiceStateTracker()).thenReturn(mSST);
        when(mPhone.getPhoneId()).thenReturn(0);
        when(mSST.getLocaleTracker()).thenReturn(mMockLocaleTracker);
        when(mMockLocaleTracker.getCurrentCountry()).thenReturn("");
        when(mPhone2.getServiceStateTracker()).thenReturn(mSST2);
        when(mPhone2.getPhoneId()).thenReturn(1);
        when(mSST2.getLocaleTracker()).thenReturn(mMockLocaleTracker2);
        when(mMockLocaleTracker2.getCurrentCountry()).thenReturn("");

        when(mConnectivityManager.getActiveNetwork()).thenReturn(mMockNetwork);
        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.setTransportType(NetworkCapabilities.TRANSPORT_WIFI, true);
        mNetworkCapabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET, true);
        when(mConnectivityManager.getNetworkCapabilities(any(Network.class)))
                .thenReturn(mNetworkCapabilities);

        when(mLocationManager.getProviders(true)).thenReturn(Arrays.asList("TEST_PROVIDER"));

        mCountryDetectorUT = new TestTelephonyCountryDetector(
                mLooper, mContext, mLocationManager, mConnectivityManager);
        if (isGeoCoderImplemented()) {
            verify(mLocationManager).requestLocationUpdates(anyString(), anyLong(), anyFloat(),
                    mLocationListenerCaptor.capture());
            verify(mLocationManager).getProviders(true);
            verify(mLocationManager).getLastKnownLocation(anyString());
        }
        verify(mConnectivityManager).registerNetworkCallback(
                any(NetworkRequest.class), mNetworkCallbackCaptor.capture());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        Log.d(TAG, "tearDown");
    }

    @Test
    public void testGetInstance() {
        clearInvocations(mLocationManager);
        clearInvocations(mConnectivityManager);
        when(mMockLocaleTracker.getCurrentCountry()).thenReturn("US");
        TelephonyCountryDetector inst1 = TelephonyCountryDetector.getInstance(mContext);
        TelephonyCountryDetector inst2 = TelephonyCountryDetector.getInstance(mContext);
        assertEquals(inst1, inst2);
        if (isGeoCoderImplemented()) {
            verify(mLocationManager, never()).requestLocationUpdates(anyString(), anyLong(),
                    anyFloat(), any(LocationListener.class));
        }
        verify(mConnectivityManager).registerNetworkCallback(
                any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void testGetCurrentNetworkCountryIso() {
        // No ServiceStateTracker
        when(mPhone.getServiceStateTracker()).thenReturn(null);
        when(mPhone2.getServiceStateTracker()).thenReturn(null);
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().isEmpty());

        // No LocaleTracker
        when(mPhone.getServiceStateTracker()).thenReturn(mSST);
        when(mSST.getLocaleTracker()).thenReturn(null);
        when(mPhone2.getServiceStateTracker()).thenReturn(mSST2);
        when(mSST2.getLocaleTracker()).thenReturn(null);
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().isEmpty());

        // LocaleTracker returns invalid country
        when(mSST.getLocaleTracker()).thenReturn(mMockLocaleTracker);
        when(mMockLocaleTracker.getCurrentCountry()).thenReturn("1234");
        when(mSST2.getLocaleTracker()).thenReturn(mMockLocaleTracker2);
        when(mMockLocaleTracker2.getCurrentCountry()).thenReturn("");
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().isEmpty());

        // LocaleTracker of phone 2 returns valid country
        when(mMockLocaleTracker2.getCurrentCountry()).thenReturn("US");
        assertEquals(1, mCountryDetectorUT.getCurrentNetworkCountryIso().size());
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().contains("US"));

        // Phone 1 is also in US
        when(mMockLocaleTracker.getCurrentCountry()).thenReturn("US");
        assertEquals(1, mCountryDetectorUT.getCurrentNetworkCountryIso().size());
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().contains("US"));

        // Phone 1 is in US and Phone 2 is in CA
        when(mMockLocaleTracker.getCurrentCountry()).thenReturn("CA");
        assertEquals(2, mCountryDetectorUT.getCurrentNetworkCountryIso().size());
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().contains("US"));
        assertTrue(mCountryDetectorUT.getCurrentNetworkCountryIso().contains("CA"));
    }

    @Test
    public void testCachedNetworkCountryCodeUpdate() {
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().isEmpty());

        // Update network country code of Phone 1
        sendNetworkCountryCodeChanged("US", mPhone);
        assertEquals(1, mCountryDetectorUT.getCachedNetworkCountryIsoInfo().size());
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("US"));
        assertEquals(0, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("US"));

        // Move time forwards and update network country code of Phone 2
        mCountryDetectorUT.elapsedRealtimeNanos = 2;
        sendNetworkCountryCodeChanged("US", mPhone2);
        assertEquals(1, mCountryDetectorUT.getCachedNetworkCountryIsoInfo().size());
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("US"));
        assertEquals(2, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("US"));

        // Move time forwards and update network country code of Phone 1
        mCountryDetectorUT.elapsedRealtimeNanos = 3;
        sendNetworkCountryCodeChanged("CA", mPhone);
        assertEquals(2, mCountryDetectorUT.getCachedNetworkCountryIsoInfo().size());
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("US"));
        assertEquals(2, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("US"));
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("CA"));
        assertEquals(3, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("CA"));

        // Move time forwards and update network country code of Phone 2
        mCountryDetectorUT.elapsedRealtimeNanos = 4;
        sendNetworkCountryCodeChanged("CA", mPhone2);
        assertEquals(1, mCountryDetectorUT.getCachedNetworkCountryIsoInfo().size());
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("CA"));
        assertEquals(4, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("CA"));

        // Move time forwards and update network country code of Phone 1
        mCountryDetectorUT.elapsedRealtimeNanos = 5;
        sendNetworkCountryCodeChanged("", mPhone);
        assertEquals(1, mCountryDetectorUT.getCachedNetworkCountryIsoInfo().size());
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("CA"));
        assertEquals(5, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("CA"));

        // Move time forwards and update network country code of Phone 2
        mCountryDetectorUT.elapsedRealtimeNanos = 6;
        sendNetworkCountryCodeChanged("", mPhone2);
        assertEquals(1, mCountryDetectorUT.getCachedNetworkCountryIsoInfo().size());
        assertTrue(mCountryDetectorUT.getCachedNetworkCountryIsoInfo().containsKey("CA"));
        assertEquals(6, (long) mCountryDetectorUT.getCachedNetworkCountryIsoInfo().get("CA"));
    }

    @Test
    public void testCachedLocationCountryCodeUpdate() {
        if (!isGeoCoderImplemented()) {
            logd("Skip the test because GeoCoder is not implemented on the device");
            return;
        }
        assertNull(mCountryDetectorUT.getCachedLocationCountryIsoInfo().first);
        assertEquals(0, (long) mCountryDetectorUT.getCachedLocationCountryIsoInfo().second);
        assertFalse(mCountryDetectorUT.queryCountryCodeForLocationTriggered);

        // Move time forwards and update location-based country code
        mCountryDetectorUT.elapsedRealtimeNanos = 2;
        mCountryDetectorUT.queryCountryCodeForLocationTriggered = false;
        sendLocationUpdate();
        assertTrue(mCountryDetectorUT.queryCountryCodeForLocationTriggered);
        sendLocationBasedCountryCodeChanged("CA", 1);
        mTestableLooper.processAllMessages();
        assertEquals("CA", mCountryDetectorUT.getCachedLocationCountryIsoInfo().first);
        assertEquals(1, (long) mCountryDetectorUT.getCachedLocationCountryIsoInfo().second);
    }

    @Test
    public void testRegisterForLocationUpdates() {
        if (!isGeoCoderImplemented()) {
            logd("Skip the test because GeoCoder is not implemented on the device");
            return;
        }

        // Network country code is available
        clearInvocations(mLocationManager);
        sendNetworkCountryCodeChanged("US", mPhone);
        verify(mLocationManager).removeUpdates(any(LocationListener.class));

        // Network country code is not available and Wi-fi is available
        clearInvocations(mLocationManager);
        sendNetworkCountryCodeChanged("", mPhone);
        verify(mLocationManager).requestLocationUpdates(anyString(), anyLong(), anyFloat(),
                any(LocationListener.class));

        // Wi-fi is not available
        clearInvocations(mLocationManager);
        mNetworkCapabilities.setTransportType(NetworkCapabilities.TRANSPORT_WIFI, false);
        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
        mTestableLooper.processAllMessages();
        verify(mLocationManager, never()).removeUpdates(any(LocationListener.class));

        // Wi-fi becomes available
        clearInvocations(mLocationManager);
        mNetworkCapabilities.setTransportType(NetworkCapabilities.TRANSPORT_WIFI, true);
        mNetworkCallbackCaptor.getValue().onAvailable(mMockNetwork);
        mTestableLooper.processAllMessages();
        // Location updates were already requested
        verify(mLocationManager, never()).requestLocationUpdates(anyString(), anyLong(), anyFloat(),
                any(LocationListener.class));

        // Make Wi-fi not available and reset the quota
        clearInvocations(mLocationManager);
        mNetworkCapabilities.setTransportType(NetworkCapabilities.TRANSPORT_WIFI, false);
        mTestableLooper.moveTimeForward(
                TestTelephonyCountryDetector.getLocationUpdateRequestQuotaResetTimeoutMillis());
        mTestableLooper.processAllMessages();
        verify(mLocationManager).removeUpdates(any(LocationListener.class));

        // Wi-fi becomes available
        clearInvocations(mLocationManager);
        mNetworkCapabilities.setTransportType(NetworkCapabilities.TRANSPORT_WIFI, true);
        mNetworkCallbackCaptor.getValue().onAvailable(mMockNetwork);
        mTestableLooper.processAllMessages();
        verify(mLocationManager).requestLocationUpdates(anyString(), anyLong(), anyFloat(),
                any(LocationListener.class));

        // Reset the quota
        clearInvocations(mLocationManager);
        mTestableLooper.moveTimeForward(
                TestTelephonyCountryDetector.getLocationUpdateRequestQuotaResetTimeoutMillis());
        mTestableLooper.processAllMessages();
        verify(mLocationManager, never()).removeUpdates(any(LocationListener.class));
        verify(mLocationManager, never()).requestLocationUpdates(anyString(), anyLong(), anyFloat(),
                any(LocationListener.class));

        // Wi-fi becomes not available
        clearInvocations(mLocationManager);
        mNetworkCapabilities.setTransportType(NetworkCapabilities.TRANSPORT_WIFI, false);
        mNetworkCallbackCaptor.getValue().onUnavailable();
        mTestableLooper.processAllMessages();
        verify(mLocationManager).removeUpdates(any(LocationListener.class));
    }

    private static boolean isGeoCoderImplemented() {
        return Geocoder.isPresent();
    }

    private void sendLocationUpdate() {
        mLocationListenerCaptor.getValue().onLocationChanged(mMockLocation);
        mTestableLooper.processAllMessages();
    }

    private void sendLocationBasedCountryCodeChanged(String countryCode, long locationUpdatedTime) {
        Message message = mCountryDetectorUT.obtainMessage(
                2 /* EVENT_LOCATION_COUNTRY_CODE_CHANGED */,
                new Pair<>(countryCode, locationUpdatedTime));
        message.sendToTarget();
        mTestableLooper.processAllMessages();
    }

    private void sendNetworkCountryCodeChanged(String countryCode, @NonNull Phone phone) {
        when(mLocaleTrackers[phone.getPhoneId()].getCurrentCountry()).thenReturn(countryCode);
        mCountryDetectorUT.onNetworkCountryCodeChanged(phone, countryCode);
        mTestableLooper.processAllMessages();
    }

    private static class TestTelephonyCountryDetector extends TelephonyCountryDetector {
        public boolean queryCountryCodeForLocationTriggered = false;
        public long elapsedRealtimeNanos = 0;

        /**
         * Create the singleton instance of {@link TelephonyCountryDetector}.
         *
         * @param looper           The looper to run the {@link TelephonyCountryDetector} instance.
         * @param context          The context associated with the instance.
         * @param locationManager  The LocationManager instance.
         */
        TestTelephonyCountryDetector(Looper looper, Context context,
                LocationManager locationManager, ConnectivityManager connectivityManager) {
            super(looper, context, locationManager, connectivityManager);
        }

        @Override
        protected void queryCountryCodeForLocation(@NonNull Location location) {
            queryCountryCodeForLocationTriggered = true;
        }

        @Override
        protected long getElapsedRealtimeNanos() {
            return elapsedRealtimeNanos;
        }

        public static long getLocationUpdateRequestQuotaResetTimeoutMillis() {
            return WAIT_FOR_LOCATION_UPDATE_REQUEST_QUOTA_RESET_TIMEOUT_MILLIS;
        }
    }
}
