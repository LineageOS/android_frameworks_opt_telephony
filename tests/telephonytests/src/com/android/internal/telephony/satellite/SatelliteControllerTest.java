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

package com.android.internal.telephony.satellite;

import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT;
import static android.telephony.SubscriptionManager.SATELLITE_ENTITLEMENT_STATUS;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_GREAT;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_POOR;
import static android.telephony.satellite.SatelliteManager.KEY_DEMO_MODE_ENABLED;
import static android.telephony.satellite.SatelliteManager.KEY_NTN_SIGNAL_STRENGTH;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_CAPABILITIES;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_ENABLED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_NEXT_VISIBILITY;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_PROVISIONED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_EMTC_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_PROPRIETARY;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_OFF;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NOT_AUTHORIZED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NO_RESOURCES;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SERVICE_NOT_PROVISIONED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_MODE_ENABLED_FALSE;
import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_MODE_ENABLED_TRUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.satellite.INtnSignalStrengthCallback;
import android.telephony.satellite.ISatelliteCapabilitiesCallback;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.ISatelliteModemStateCallback;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.ISatelliteTransmissionUpdateCallback;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteManager.SatelliteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.internal.R;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.IVoidConsumer;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.configupdate.ConfigProviderAdaptor;
import com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.metrics.ControllerMetricsStats;
import com.android.internal.telephony.satellite.metrics.ProvisionMetricsStats;
import com.android.internal.telephony.satellite.metrics.SessionMetricsStats;
import com.android.internal.telephony.subscription.SubscriptionManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SatelliteControllerTest extends TelephonyTest {
    private static final String TAG = "SatelliteControllerTest";

    private static final long TIMEOUT = 500;
    private static final int SUB_ID = 0;
    private static final int SUB_ID1 = 1;
    private static final int MAX_BYTES_PER_OUT_GOING_DATAGRAM = 339;
    private static final String TEST_SATELLITE_TOKEN = "TEST_SATELLITE_TOKEN";
    private static final String TEST_NEXT_SATELLITE_TOKEN = "TEST_NEXT_SATELLITE_TOKEN";
    private static final String[] EMPTY_STRING_ARRAY = {};
    private static final List<String> EMPTY_STRING_LIST = new ArrayList<>();
    private static final String SATELLITE_SYSTEM_NOTIFICATION_DONE_KEY =
            "satellite_system_notification_done_key";
    private static final int[] ACTIVE_SUB_IDS = {SUB_ID};
    private static final int TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS =
            (int) TimeUnit.SECONDS.toMillis(60);
    private List<Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener>>
            mCarrierConfigChangedListenerList = new ArrayList<>();

    private TestSatelliteController mSatelliteControllerUT;
    private TestSharedPreferences mSharedPreferences;
    private PersistableBundle mCarrierConfigBundle;
    private ServiceState mServiceState2;

    @Mock private DatagramController mMockDatagramController;
    @Mock private SatelliteModemInterface mMockSatelliteModemInterface;
    @Mock private SatelliteSessionController mMockSatelliteSessionController;
    @Mock private PointingAppController mMockPointingAppController;
    @Mock private ControllerMetricsStats mMockControllerMetricsStats;
    @Mock private ProvisionMetricsStats mMockProvisionMetricsStats;
    @Mock private SessionMetricsStats mMockSessionMetricsStats;
    @Mock private SubscriptionManagerService mMockSubscriptionManagerService;
    @Mock private NotificationManager mMockNotificationManager;
    private List<Integer> mIIntegerConsumerResults =  new ArrayList<>();
    @Mock private ISatelliteTransmissionUpdateCallback mStartTransmissionUpdateCallback;
    @Mock private ISatelliteTransmissionUpdateCallback mStopTransmissionUpdateCallback;
    @Mock private FeatureFlags mFeatureFlags;
    @Mock private TelephonyConfigUpdateInstallReceiver mMockTelephonyConfigUpdateInstallReceiver;
    @Mock private SatelliteConfigParser mMockConfigParser;
    @Mock private SatelliteConfig mMockConfig;

    private Semaphore mIIntegerConsumerSemaphore = new Semaphore(0);
    private IIntegerConsumer mIIntegerConsumer = new IIntegerConsumer.Stub() {
        @Override
        public void accept(int result) {
            logd("mIIntegerConsumer: result=" + result);
            mIIntegerConsumerResults.add(result);
            try {
                mIIntegerConsumerSemaphore.release();
            } catch (Exception ex) {
                loge("mIIntegerConsumer: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private boolean mIsSatelliteServiceSupported = true;
    private boolean mIsPointingRequired = true;
    private Set<Integer> mSupportedRadioTechnologies = new HashSet<>(Arrays.asList(
            NT_RADIO_TECHNOLOGY_NR_NTN,
            NT_RADIO_TECHNOLOGY_EMTC_NTN,
            NT_RADIO_TECHNOLOGY_NB_IOT_NTN,
            NT_RADIO_TECHNOLOGY_PROPRIETARY));
    private SatelliteCapabilities mSatelliteCapabilities = new SatelliteCapabilities(
            mSupportedRadioTechnologies, mIsPointingRequired, MAX_BYTES_PER_OUT_GOING_DATAGRAM,
            new HashMap<>());
    private SatelliteCapabilities mEmptySatelliteCapabilities = new SatelliteCapabilities(
            new HashSet<>(), mIsPointingRequired, MAX_BYTES_PER_OUT_GOING_DATAGRAM,
            new HashMap<>());
    private Semaphore mSatelliteCapabilitiesSemaphore = new Semaphore(0);
    private SatelliteCapabilities mQueriedSatelliteCapabilities = null;
    private int mQueriedSatelliteCapabilitiesResultCode = SATELLITE_RESULT_SUCCESS;
    private ResultReceiver mSatelliteCapabilitiesReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedSatelliteCapabilitiesResultCode = resultCode;
            logd("mSatelliteCapabilitiesReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_CAPABILITIES)) {
                    mQueriedSatelliteCapabilities = resultData.getParcelable(
                            KEY_SATELLITE_CAPABILITIES, SatelliteCapabilities.class);
                } else {
                    loge("KEY_SATELLITE_SUPPORTED does not exist.");
                    mQueriedSatelliteCapabilities = null;
                }
            } else {
                mQueriedSatelliteCapabilities = null;
            }
            try {
                mSatelliteCapabilitiesSemaphore.release();
            } catch (Exception ex) {
                loge("mSatelliteCapabilitiesReceiver: Got exception in releasing semaphore, ex="
                        + ex);
            }
        }
    };

    private boolean mQueriedSatelliteSupported = false;
    private int mQueriedSatelliteSupportedResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mSatelliteSupportSemaphore = new Semaphore(0);
    private ResultReceiver mSatelliteSupportReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedSatelliteSupportedResultCode = resultCode;
            logd("mSatelliteSupportReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_SUPPORTED)) {
                    mQueriedSatelliteSupported = resultData.getBoolean(KEY_SATELLITE_SUPPORTED);
                } else {
                    loge("KEY_SATELLITE_SUPPORTED does not exist.");
                    mQueriedSatelliteSupported = false;
                }
            } else {
                mQueriedSatelliteSupported = false;
            }
            try {
                mSatelliteSupportSemaphore.release();
            } catch (Exception ex) {
                loge("mSatelliteSupportReceiver: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private boolean mQueriedIsSatelliteEnabled = false;
    private int mQueriedIsSatelliteEnabledResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mIsSatelliteEnabledSemaphore = new Semaphore(0);
    private ResultReceiver mIsSatelliteEnabledReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            logd("mIsSatelliteEnabledReceiver: resultCode=" + resultCode);
            mQueriedIsSatelliteEnabledResultCode = resultCode;
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_ENABLED)) {
                    mQueriedIsSatelliteEnabled = resultData.getBoolean(KEY_SATELLITE_ENABLED);
                } else {
                    loge("KEY_SATELLITE_ENABLED does not exist.");
                    mQueriedIsSatelliteEnabled = false;
                }
            } else {
                mQueriedIsSatelliteEnabled = false;
            }
            try {
                mIsSatelliteEnabledSemaphore.release();
            } catch (Exception ex) {
                loge("mIsSatelliteEnabledReceiver: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private boolean mQueriedIsDemoModeEnabled = false;
    private int mQueriedIsDemoModeEnabledResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mIsDemoModeEnabledSemaphore = new Semaphore(0);
    private ResultReceiver mIsDemoModeEnabledReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedIsDemoModeEnabledResultCode = resultCode;
            logd("mIsDemoModeEnabledReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_DEMO_MODE_ENABLED)) {
                    mQueriedIsDemoModeEnabled = resultData.getBoolean(KEY_DEMO_MODE_ENABLED);
                } else {
                    loge("KEY_DEMO_MODE_ENABLED does not exist.");
                    mQueriedIsDemoModeEnabled = false;
                }
            } else {
                mQueriedIsDemoModeEnabled = false;
            }
            try {
                mIsDemoModeEnabledSemaphore.release();
            } catch (Exception ex) {
                loge("mIsDemoModeEnabledReceiver: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private boolean mQueriedIsSatelliteProvisioned = false;
    private int mQueriedIsSatelliteProvisionedResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mIsSatelliteProvisionedSemaphore = new Semaphore(0);
    private ResultReceiver mIsSatelliteProvisionedReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedIsSatelliteProvisionedResultCode = resultCode;
            logd("mIsSatelliteProvisionedReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_PROVISIONED)) {
                    mQueriedIsSatelliteProvisioned =
                            resultData.getBoolean(KEY_SATELLITE_PROVISIONED);
                } else {
                    loge("KEY_SATELLITE_PROVISIONED does not exist.");
                    mQueriedIsSatelliteProvisioned = false;
                }
            } else {
                mQueriedIsSatelliteProvisioned = false;
            }
            try {
                mIsSatelliteProvisionedSemaphore.release();
            } catch (Exception ex) {
                loge("mIsSatelliteProvisionedReceiver: Got exception in releasing semaphore ex="
                        + ex);
            }
        }
    };

    private boolean mQueriedSatelliteAllowed = false;
    private int mQueriedSatelliteAllowedResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mSatelliteAllowedSemaphore = new Semaphore(0);
    private ResultReceiver mSatelliteAllowedReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedSatelliteAllowedResultCode = resultCode;
            logd("mSatelliteAllowedReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_COMMUNICATION_ALLOWED)) {
                    mQueriedSatelliteAllowed = resultData.getBoolean(
                            KEY_SATELLITE_COMMUNICATION_ALLOWED);
                } else {
                    loge("KEY_SATELLITE_COMMUNICATION_ALLOWED does not exist.");
                    mQueriedSatelliteAllowed = false;
                }
            } else {
                mQueriedSatelliteAllowed = false;
            }
            try {
                mSatelliteAllowedSemaphore.release();
            } catch (Exception ex) {
                loge("mSatelliteAllowedReceiver: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private int mQueriedSatelliteVisibilityTime = -1;
    private int mSatelliteNextVisibilityTime = 3600;
    private int mQueriedSatelliteVisibilityTimeResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mSatelliteVisibilityTimeSemaphore = new Semaphore(0);
    private ResultReceiver mSatelliteVisibilityTimeReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedSatelliteVisibilityTimeResultCode = resultCode;
            logd("mSatelliteVisibilityTimeReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_NEXT_VISIBILITY)) {
                    mQueriedSatelliteVisibilityTime = resultData.getInt(
                            KEY_SATELLITE_NEXT_VISIBILITY);
                } else {
                    loge("KEY_SATELLITE_NEXT_VISIBILITY does not exist.");
                    mQueriedSatelliteVisibilityTime = -1;
                }
            } else {
                mQueriedSatelliteVisibilityTime = -1;
            }
            try {
                mSatelliteVisibilityTimeSemaphore.release();
            } catch (Exception ex) {
                loge("mSatelliteVisibilityTimeReceiver: Got exception in releasing semaphore, ex="
                        + ex);
            }
        }
    };

    private @NtnSignalStrength.NtnSignalStrengthLevel int mQueriedNtnSignalStrengthLevel =
            NTN_SIGNAL_STRENGTH_NONE;
    private int mQueriedNtnSignalStrengthResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mRequestNtnSignalStrengthSemaphore = new Semaphore(0);
    private ResultReceiver mRequestNtnSignalStrengthReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedNtnSignalStrengthResultCode = resultCode;
            logd("KEY_NTN_SIGNAL_STRENGTH: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_NTN_SIGNAL_STRENGTH)) {
                    NtnSignalStrength result = resultData.getParcelable(KEY_NTN_SIGNAL_STRENGTH);
                    logd("result.getLevel()=" + result.getLevel());
                    mQueriedNtnSignalStrengthLevel = result.getLevel();
                } else {
                    loge("KEY_NTN_SIGNAL_STRENGTH does not exist.");
                    mQueriedNtnSignalStrengthLevel = NTN_SIGNAL_STRENGTH_NONE;
                }
            } else {
                mQueriedNtnSignalStrengthLevel = NTN_SIGNAL_STRENGTH_NONE;
            }
            try {
                mRequestNtnSignalStrengthSemaphore.release();
            } catch (Exception ex) {
                loge("mRequestNtnSignalStrengthReceiver: Got exception in releasing semaphore, ex="
                        + ex);
            }
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(DatagramController.class, "sInstance", null,
                mMockDatagramController);
        replaceInstance(SatelliteModemInterface.class, "sInstance", null,
                mMockSatelliteModemInterface);
        replaceInstance(SatelliteSessionController.class, "sInstance", null,
                mMockSatelliteSessionController);
        replaceInstance(PointingAppController.class, "sInstance", null,
                mMockPointingAppController);
        replaceInstance(ControllerMetricsStats.class, "sInstance", null,
                mMockControllerMetricsStats);
        replaceInstance(ProvisionMetricsStats.class, "sInstance", null,
                mMockProvisionMetricsStats);
        replaceInstance(SessionMetricsStats.class, "sInstance", null,
                mMockSessionMetricsStats);
        replaceInstance(SubscriptionManagerService.class, "sInstance", null,
                mMockSubscriptionManagerService);
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[]{mPhone, mPhone2});
        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, mMockTelephonyConfigUpdateInstallReceiver);

        mServiceState2 = Mockito.mock(ServiceState.class);
        when(mPhone.getServiceState()).thenReturn(mServiceState);
        when(mPhone.getSubId()).thenReturn(SUB_ID);
        when(mPhone2.getServiceState()).thenReturn(mServiceState2);
        when(mPhone2.getSubId()).thenReturn(SUB_ID1);

        mContextFixture.putStringArrayResource(
                R.array.config_satellite_providers,
                EMPTY_STRING_ARRAY);
        mContextFixture.putIntResource(
                R.integer.config_wait_for_satellite_enabling_response_timeout_millis,
                TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS);
        doReturn(ACTIVE_SUB_IDS).when(mMockSubscriptionManagerService).getActiveSubIdList(true);

        mCarrierConfigBundle = mContextFixture.getCarrierConfigBundle();
        doReturn(mCarrierConfigBundle)
                .when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyVararg());
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(0);
            CarrierConfigManager.CarrierConfigChangeListener listener = invocation.getArgument(1);
            mCarrierConfigChangedListenerList.add(new Pair<>(executor, listener));
            return null;
        }).when(mCarrierConfigManager).registerCarrierConfigChangeListener(
                any(Executor.class),
                any(CarrierConfigManager.CarrierConfigChangeListener.class));

        mSharedPreferences = new TestSharedPreferences();
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        doReturn(mIsSatelliteServiceSupported)
                .when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestSatelliteCapabilities(
                mSatelliteCapabilities, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_RADIO_NOT_AVAILABLE);
        doNothing().when(mMockDatagramController).setDemoMode(anyBoolean());
        doNothing().when(mMockSatelliteSessionController)
                .onSatelliteEnabledStateChanged(anyBoolean());
        doNothing().when(mMockSatelliteSessionController).onSatelliteModemStateChanged(anyInt());
        doNothing().when(mMockSatelliteSessionController).setDemoMode(anyBoolean());
        doNothing().when(mMockControllerMetricsStats).onSatelliteEnabled();
        doNothing().when(mMockControllerMetricsStats).reportServiceEnablementSuccessCount();
        doNothing().when(mMockControllerMetricsStats).reportServiceEnablementFailCount();
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setInitializationResult(anyInt());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setRadioTechnology(anyInt());
        doNothing().when(mMockSessionMetricsStats).reportSessionMetrics();

        doReturn(mMockProvisionMetricsStats).when(mMockProvisionMetricsStats)
                .setResultCode(anyInt());
        doReturn(mMockProvisionMetricsStats).when(mMockProvisionMetricsStats)
                .setIsProvisionRequest(eq(false));
        doNothing().when(mMockProvisionMetricsStats).reportProvisionMetrics();
        doNothing().when(mMockControllerMetricsStats).reportDeprovisionCount(anyInt());
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        doReturn(mSST).when(mPhone).getServiceStateTracker();
        doReturn(mSST).when(mPhone2).getServiceStateTracker();
        doReturn(mServiceState).when(mSST).getServiceState();
        doReturn(Context.NOTIFICATION_SERVICE).when(mContext).getSystemServiceName(
                NotificationManager.class);
        doReturn(mMockNotificationManager).when(mContext).getSystemService(
                Context.NOTIFICATION_SERVICE);
        mSatelliteControllerUT =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        verify(mMockSatelliteModemInterface).registerForSatelliteProvisionStateChanged(
                any(Handler.class),
                eq(26) /* EVENT_SATELLITE_PROVISION_STATE_CHANGED */,
                eq(null));
        verify(mMockSatelliteModemInterface).registerForPendingDatagrams(
                any(Handler.class),
                eq(27) /* EVENT_PENDING_DATAGRAMS */,
                eq(null));
        verify(mMockSatelliteModemInterface).registerForSatelliteModemStateChanged(
                any(Handler.class),
                eq(28) /* EVENT_SATELLITE_MODEM_STATE_CHANGED */,
                eq(null));

        doReturn(mMockConfigParser).when(mMockTelephonyConfigUpdateInstallReceiver)
                .getConfigParser(ConfigProviderAdaptor.DOMAIN_SATELLITE);
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        mSatelliteControllerUT = null;
        super.tearDown();
    }

    @Test
    public void testRequestIsSatelliteCommunicationAllowedForCurrentLocation() {
        mSatelliteAllowedSemaphore.drainPermits();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsSatelliteCommunicationAllowedForCurrentLocation(SUB_ID,
                mSatelliteAllowedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedSatelliteAllowedResultCode);

        resetSatelliteControllerUT();
        mSatelliteControllerUT.requestIsSatelliteCommunicationAllowedForCurrentLocation(SUB_ID,
                mSatelliteAllowedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedSatelliteAllowedResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteAllowedForCurrentLocation(true,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsSatelliteCommunicationAllowedForCurrentLocation(SUB_ID,
                mSatelliteAllowedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteAllowedResultCode);
        assertTrue(mQueriedSatelliteAllowed);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpNullResponseForRequestIsSatelliteAllowedForCurrentLocation(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsSatelliteCommunicationAllowedForCurrentLocation(SUB_ID,
                mSatelliteAllowedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedSatelliteAllowedResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpNullResponseForRequestIsSatelliteAllowedForCurrentLocation(
                SATELLITE_RESULT_INVALID_MODEM_STATE);
        mSatelliteControllerUT.requestIsSatelliteCommunicationAllowedForCurrentLocation(SUB_ID,
                mSatelliteAllowedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, mQueriedSatelliteAllowedResultCode);
    }

    @Test
    public void testRequestTimeForNextSatelliteVisibility() {
        mSatelliteVisibilityTimeSemaphore.drainPermits();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestTimeForNextSatelliteVisibility(mSatelliteNextVisibilityTime,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestTimeForNextSatelliteVisibility(mSatelliteNextVisibilityTime,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        setUpResponseForRequestTimeForNextSatelliteVisibility(mSatelliteNextVisibilityTime,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteVisibilityTimeResultCode);
        assertEquals(mSatelliteNextVisibilityTime, mQueriedSatelliteVisibilityTime);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        setUpNullResponseForRequestTimeForNextSatelliteVisibility(
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        setUpNullResponseForRequestTimeForNextSatelliteVisibility(
                SATELLITE_RESULT_INVALID_MODEM_STATE);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(SUB_ID,
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE,
                mQueriedSatelliteVisibilityTimeResultCode);
    }

    @Test
    public void testRadioStateChanged() {
        mIsSatelliteEnabledSemaphore.drainPermits();

        when(mMockSatelliteModemInterface.isSatelliteServiceConnected()).thenReturn(false);
        setRadioPower(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .requestIsSatelliteSupported(any(Message.class));

        setRadioPower(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .requestIsSatelliteSupported(any(Message.class));

        when(mMockSatelliteModemInterface.isSatelliteServiceConnected()).thenReturn(true);
        setRadioPower(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .requestIsSatelliteSupported(any(Message.class));

        setRadioPower(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(2))
                .requestIsSatelliteSupported(any(Message.class));

        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        setRadioPower(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(3))
                .requestIsSatelliteSupported(any(Message.class));

        setRadioPower(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(4))
                .requestIsSatelliteSupported(any(Message.class));

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setRadioPower(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(5))
                .requestIsSatelliteSupported(any(Message.class));

        setRadioPower(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(5))
                .requestIsSatelliteSupported(any(Message.class));
    }

    @Test
    public void testRequestSatelliteEnabled() {
        mIsSatelliteEnabledSemaphore.drainPermits();

        // Fail to enable satellite when SatelliteController is not fully loaded yet.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        // Fail to enable satellite when the device does not support satellite.
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        // Fail to enable satellite when the device is not provisioned yet.
        mIIntegerConsumerResults.clear();
        resetSatelliteControllerUT();
        verify(mMockSatelliteSessionController, times(1)).onSatelliteEnabledStateChanged(eq(false));
        verify(mMockSatelliteSessionController, times(1)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(1)).setDemoMode(eq(false));
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));

        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Successfully enable satellite
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertEquals(
                SATELLITE_MODE_ENABLED_TRUE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteSessionController, times(1)).onSatelliteEnabledStateChanged(eq(true));
        verify(mMockSatelliteSessionController, times(2)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(2)).setDemoMode(eq(false));
        verify(mMockControllerMetricsStats, times(1)).onSatelliteEnabled();
        verify(mMockControllerMetricsStats, times(1)).reportServiceEnablementSuccessCount();

        // Successfully disable satellite when radio is turned off.
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        setUpResponseForRequestSatelliteEnabled(false, false, SATELLITE_RESULT_SUCCESS);
        setRadioPower(false);
        mSatelliteControllerUT.onCellularRadioPowerOffRequested();
        processAllMessages();
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_OFF, null);
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertEquals(
                SATELLITE_MODE_ENABLED_FALSE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteSessionController, times(2)).onSatelliteEnabledStateChanged(eq(false));
        verify(mMockSatelliteSessionController, times(3)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(3)).setDemoMode(eq(false));
        verify(mMockControllerMetricsStats, times(1)).onSatelliteDisabled();

        // Fail to enable satellite when radio is off.
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        // Radio is not on, can not enable satellite
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));

        setRadioPower(true);
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Fail to enable satellite with an error response from modem when radio is on.
        mIIntegerConsumerResults.clear();
        clearInvocations(mMockPointingAppController);
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_INVALID_MODEM_STATE);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verify(mMockPointingAppController, never()).startPointingUI(anyBoolean());
        assertFalse(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        verify(mMockControllerMetricsStats, times(1)).reportServiceEnablementFailCount();

        // Successfully enable satellite when radio is on.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertEquals(SATELLITE_MODE_ENABLED_TRUE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteSessionController, times(2)).onSatelliteEnabledStateChanged(eq(true));
        verify(mMockSatelliteSessionController, times(4)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(4)).setDemoMode(eq(false));
        verify(mMockControllerMetricsStats, times(2)).onSatelliteEnabled();
        verify(mMockControllerMetricsStats, times(2)).reportServiceEnablementSuccessCount();
        verify(mMockSessionMetricsStats, times(7)).setInitializationResult(anyInt());
        verify(mMockSessionMetricsStats, times(7)).setRadioTechnology(anyInt());
        verify(mMockSessionMetricsStats, times(7)).reportSessionMetrics();

        // Successfully enable satellite when it is already enabled.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Fail to enable satellite with a different demo mode when it is already enabled.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, true, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_ARGUMENTS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Successfully disable satellite.
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Disable satellite when satellite is already disabled.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Disable satellite with a different demo mode when satellite is already disabled.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, true, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Send a second request while the first request in progress
        mIIntegerConsumerResults.clear();
        setUpNoResponseForRequestSatelliteEnabled(true, false);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_REQUEST_IN_PROGRESS, (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        resetSatelliteControllerUTToSupportedAndProvisionedState();
        // Should receive callback for the above request when satellite modem is turned off.
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));

        // Move to satellite-disabling in progress.
        setUpNoResponseForRequestSatelliteEnabled(false, false);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));

        // Disable is in progress. Thus, a new request to enable satellite will be rejected.
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_ERROR, (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        resetSatelliteControllerUTToOffAndProvisionedState();
        // Should receive callback for the above request when satellite modem is turned off.
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));

        /**
         * Make areAllRadiosDisabled return false and move mWaitingForRadioDisabled to true, which
         * will lead to no response for requestSatelliteEnabled.
         */
        mSatelliteControllerUT.allRadiosDisabled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));

        resetSatelliteControllerUTEnabledState();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        // We should receive 2 callbacks for the above 2 requests.
        assertTrue(waitForIIntegerConsumerResult(2));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(1));

        resetSatelliteControllerUTToOffAndProvisionedState();

        // Repeat the same test as above but with error response from modem for the second request
        mSatelliteControllerUT.allRadiosDisabled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));

        resetSatelliteControllerUTEnabledState();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(false, false, SATELLITE_RESULT_NO_RESOURCES);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        // We should receive 2 callbacks for the above 2 requests.
        assertTrue(waitForIIntegerConsumerResult(2));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertEquals(SATELLITE_RESULT_NO_RESOURCES, (long) mIIntegerConsumerResults.get(1));
        mSatelliteControllerUT.allRadiosDisabled = true;

        resetSatelliteControllerUTToOnAndProvisionedState();
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);
        mSatelliteControllerUT.onCellularRadioPowerOffRequested();
        processAllMessages();
        // Satellite should not be powered off since the feature flag oemEnabledSatelliteFlag is
        // disabled
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testRequestSatelliteCapabilities() {
        mSatelliteCapabilitiesSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteCapabilitiesResultCode);

        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedSatelliteCapabilitiesResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSatelliteCapabilities(mSatelliteCapabilities,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteCapabilitiesResultCode);
        assertEquals(mSatelliteCapabilities, mQueriedSatelliteCapabilities);
        assertTrue(
                mQueriedSatelliteCapabilities.getSupportedRadioTechnologies().contains(
                        mSatelliteControllerUT.getSupportedNtnRadioTechnology()));

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpNullResponseForRequestSatelliteCapabilities(SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteCapabilitiesResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpNullResponseForRequestSatelliteCapabilities(SATELLITE_RESULT_INVALID_MODEM_STATE);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, mQueriedSatelliteCapabilitiesResultCode);
    }

    @Test
    public void testStartSatelliteTransmissionUpdates() {
        mIIntegerConsumerSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForStartSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForStartSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        verify(mMockPointingAppController).registerForSatelliteTransmissionUpdates(anyInt(),
                eq(mStartTransmissionUpdateCallback));
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verify(mMockPointingAppController).startSatelliteTransmissionUpdates(any(Message.class));
        verify(mMockPointingAppController).setStartedSatelliteTransmissionUpdates(eq(true));

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForStartSatelliteTransmissionUpdates(SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockPointingAppController).unregisterForSatelliteTransmissionUpdates(anyInt(),
                any(), eq(mStartTransmissionUpdateCallback));
        verify(mMockPointingAppController).setStartedSatelliteTransmissionUpdates(eq(false));
    }

    @Test
    public void testStopSatelliteTransmissionUpdates() {
        mIIntegerConsumerSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForStopSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        provisionSatelliteService();
        setUpResponseForStopSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        verify(mMockPointingAppController).unregisterForSatelliteTransmissionUpdates(anyInt(),
                any(), eq(mStopTransmissionUpdateCallback));
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verify(mMockPointingAppController).stopSatelliteTransmissionUpdates(any(Message.class));

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForStopSatelliteTransmissionUpdates(SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(SUB_ID, mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
    }

    @Test
    public void testRequestIsDemoModeEnabled() {
        mIsDemoModeEnabledSemaphore.drainPermits();
        resetSatelliteControllerUT();
        mSatelliteControllerUT.requestIsDemoModeEnabled(SUB_ID, mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsDemoModeEnabled(SUB_ID, mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsDemoModeEnabled(SUB_ID, mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsDemoModeEnabled(SUB_ID, mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        boolean isDemoModeEnabled = mSatelliteControllerUT.isDemoModeEnabled();
        provisionSatelliteService();
        mSatelliteControllerUT.requestIsDemoModeEnabled(SUB_ID, mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedIsDemoModeEnabledResultCode);
        assertEquals(isDemoModeEnabled, mQueriedIsDemoModeEnabled);
    }

    @Test
    public void testIsSatelliteEnabled() {
        setUpResponseForRequestIsSatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        assertFalse(mSatelliteControllerUT.isSatelliteEnabled());
        mIsSatelliteEnabledSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteEnabled(SUB_ID, mIsSatelliteEnabledReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteEnabledResult(1));
        assertEquals(
                SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedIsSatelliteEnabledResultCode);


        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsSatelliteEnabled(SUB_ID, mIsSatelliteEnabledReceiver);
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedIsSatelliteEnabledResultCode);
        assertEquals(mSatelliteControllerUT.isSatelliteEnabled(), mQueriedIsSatelliteEnabled);
    }

    @Test
    public void testOnSatelliteServiceConnected() {
        verifySatelliteSupported(false, SATELLITE_RESULT_RADIO_NOT_AVAILABLE);
        verifySatelliteEnabled(false, SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_INVALID_TELEPHONY_STATE);

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSatelliteEnabled(false, false, SATELLITE_RESULT_SUCCESS);

        setUpResponseForRequestIsSatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.onSatelliteServiceConnected();
        processAllMessages();

        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testRegisterForSatelliteModemStateChanged() {
        ISatelliteModemStateCallback callback = new ISatelliteModemStateCallback.Stub() {
            @Override
            public void onSatelliteModemStateChanged(int state) {
                logd("onSatelliteModemStateChanged: state=" + state);
            }
        };
        int errorCode = mSatelliteControllerUT.registerForSatelliteModemStateChanged(
                SUB_ID, callback);
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, errorCode);
        verify(mMockSatelliteSessionController, never())
                .registerForSatelliteModemStateChanged(callback);

        resetSatelliteControllerUTToSupportedAndProvisionedState();

        errorCode = mSatelliteControllerUT.registerForSatelliteModemStateChanged(
                SUB_ID, callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);
        verify(mMockSatelliteSessionController).registerForSatelliteModemStateChanged(callback);
    }

    @Test
    public void testUnregisterForSatelliteModemStateChanged() {
        ISatelliteModemStateCallback callback = new ISatelliteModemStateCallback.Stub() {
            @Override
            public void onSatelliteModemStateChanged(int state) {
                logd("onSatelliteModemStateChanged: state=" + state);
            }
        };
        mSatelliteControllerUT.unregisterForModemStateChanged(SUB_ID, callback);
        verify(mMockSatelliteSessionController, never())
                .unregisterForSatelliteModemStateChanged(callback);

        resetSatelliteControllerUTToSupportedAndProvisionedState();

        mSatelliteControllerUT.unregisterForModemStateChanged(SUB_ID, callback);
        verify(mMockSatelliteSessionController).unregisterForSatelliteModemStateChanged(callback);
    }

    @Test
    public void testRegisterForSatelliteProvisionStateChanged() {
        Semaphore semaphore = new Semaphore(0);
        ISatelliteProvisionStateCallback callback =
                new ISatelliteProvisionStateCallback.Stub() {
                    @Override
                    public void onSatelliteProvisionStateChanged(boolean provisioned) {
                        logd("onSatelliteProvisionStateChanged: provisioned=" + provisioned);
                        try {
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSatelliteProvisionStateChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };
        int errorCode = mSatelliteControllerUT.registerForSatelliteProvisionStateChanged(
                SUB_ID, callback);
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, errorCode);

        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForSatelliteProvisionStateChanged(
                SUB_ID, callback);
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, errorCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForSatelliteProvisionStateChanged(
                SUB_ID, callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);

        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteProvisionStateChanged"));

        mSatelliteControllerUT.unregisterForSatelliteProvisionStateChanged(SUB_ID, callback);
        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        assertFalse(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteProvisionStateChanged"));
    }

    @Test
    public void testRegisterForSatelliteDatagram() {
        ISatelliteDatagramCallback callback =
                new ISatelliteDatagramCallback.Stub() {
                    @Override
                    public void onSatelliteDatagramReceived(long datagramId,
                            @NonNull SatelliteDatagram datagram, int pendingCount,
                            @NonNull IVoidConsumer internalAck) {
                        logd("onSatelliteDatagramReceived");
                    }
                };
        when(mMockDatagramController.registerForSatelliteDatagram(eq(SUB_ID), eq(callback)))
                .thenReturn(SATELLITE_RESULT_SUCCESS);
        int errorCode = mSatelliteControllerUT.registerForIncomingDatagram(SUB_ID, callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);
        verify(mMockDatagramController).registerForSatelliteDatagram(eq(SUB_ID), eq(callback));
    }

    @Test
    public void testUnregisterForSatelliteDatagram() {
        ISatelliteDatagramCallback callback =
                new ISatelliteDatagramCallback.Stub() {
                    @Override
                    public void onSatelliteDatagramReceived(long datagramId,
                            @NonNull SatelliteDatagram datagram, int pendingCount,
                            @NonNull IVoidConsumer internalAck) {
                        logd("onSatelliteDatagramReceived");
                    }
                };
        doNothing().when(mMockDatagramController)
                .unregisterForSatelliteDatagram(eq(SUB_ID), eq(callback));
        mSatelliteControllerUT.unregisterForIncomingDatagram(SUB_ID, callback);
        verify(mMockDatagramController).unregisterForSatelliteDatagram(eq(SUB_ID), eq(callback));
    }

    @Test
    public void testSendSatelliteDatagram() {
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.sendDatagram(SUB_ID,
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockDatagramController, never()).sendSatelliteDatagram(anyInt(),
                eq(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE), eq(datagram), eq(true),
                any());

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        sendProvisionedStateChangedEvent(false, null);
        processAllMessages();
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.sendDatagram(SUB_ID,
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockDatagramController, never()).sendSatelliteDatagram(anyInt(),
                eq(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE), eq(datagram), eq(true),
                any());

        mIIntegerConsumerResults.clear();
        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.sendDatagram(SUB_ID,
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockDatagramController, times(1)).sendSatelliteDatagram(anyInt(),
                eq(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE), eq(datagram), eq(true),
                any());
        verify(mMockPointingAppController, times(1)).startPointingUI(eq(true));
    }

    @Test
    public void testPollPendingSatelliteDatagrams() {
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.pollPendingDatagrams(SUB_ID, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockDatagramController, never()).pollPendingSatelliteDatagrams(anyInt(), any());

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        sendProvisionedStateChangedEvent(false, null);
        processAllMessages();
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.pollPendingDatagrams(SUB_ID, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockDatagramController, never()).pollPendingSatelliteDatagrams(anyInt(), any());

        mIIntegerConsumerResults.clear();
        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.pollPendingDatagrams(SUB_ID, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockDatagramController, times(1)).pollPendingSatelliteDatagrams(anyInt(), any());
    }

    @Test
    public void testProvisionSatelliteService() {
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        CancellationSignal cancellationSignal = new CancellationSignal();
        ICancellationSignal cancelRemote = null;
        mIIntegerConsumerResults.clear();
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
        assertNull(cancelRemote);

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));
        assertNull(cancelRemote);

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForProvisionSatelliteService(TEST_SATELLITE_TOKEN, testProvisionData,
                SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertNotNull(cancelRemote);
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Send provision request again after the device is successfully provisioned
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertNull(cancelRemote);

        // Vendor service does not support the request requestIsSatelliteProvisioned. Telephony will
        // make decision itself
        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(
                false, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Vendor service does not support the requests requestIsSatelliteProvisioned and
        // provisionSatelliteService. Telephony will make decision itself
        deprovisionSatelliteService();
        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(
                false, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForProvisionSatelliteService(TEST_SATELLITE_TOKEN, testProvisionData,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertNotNull(cancelRemote);
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForProvisionSatelliteService(TEST_SATELLITE_TOKEN, testProvisionData,
                SATELLITE_RESULT_NOT_AUTHORIZED);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_AUTHORIZED, (long) mIIntegerConsumerResults.get(0));
        assertNotNull(cancelRemote);

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForProvisionSatelliteService(TEST_NEXT_SATELLITE_TOKEN, testProvisionData,
                SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_NEXT_SATELLITE_TOKEN, testProvisionData, mIIntegerConsumer);
        cancellationSignal.setRemote(cancelRemote);
        cancellationSignal.cancel();
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface).deprovisionSatelliteService(
                eq(TEST_NEXT_SATELLITE_TOKEN), any(Message.class));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpNoResponseForProvisionSatelliteService(TEST_SATELLITE_TOKEN);
        setUpResponseForProvisionSatelliteService(TEST_NEXT_SATELLITE_TOKEN, testProvisionData,
                SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_NEXT_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS,
                (long) mIIntegerConsumerResults.get(0));
    }

    @Test
    public void testDeprovisionSatelliteService() {
        mIIntegerConsumerSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForDeprovisionSatelliteService(TEST_SATELLITE_TOKEN, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForDeprovisionSatelliteService(TEST_SATELLITE_TOKEN, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForDeprovisionSatelliteService(TEST_SATELLITE_TOKEN, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);

        // Vendor service does not support deprovisionSatelliteService
        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForDeprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForDeprovisionSatelliteService(TEST_SATELLITE_TOKEN,
                SATELLITE_RESULT_INVALID_MODEM_STATE);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testSupportedSatelliteServices() {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        List<String> satellitePlmnList = mSatelliteControllerUT.getSatellitePlmnsForCarrier(
                SUB_ID);
        assertEquals(EMPTY_STRING_ARRAY.length, satellitePlmnList.size());
        List<Integer> supportedSatelliteServices =
                mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00101");
        assertTrue(supportedSatelliteServices.isEmpty());

        String[] satelliteProviderStrArray = {"00101", "00102"};
        mContextFixture.putStringArrayResource(
                R.array.config_satellite_providers, satelliteProviderStrArray);
        int[] expectedSupportedServices2 = {2};
        int[] expectedSupportedServices3 = {1, 3};
        PersistableBundle carrierSupportedSatelliteServicesPerProvider = new PersistableBundle();
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00102", expectedSupportedServices2);
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00103", expectedSupportedServices3);
        String[] expectedSupportedSatellitePlmns = {"00102", "00103"};
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        TestSatelliteController testSatelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);

        satellitePlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        assertTrue(satellitePlmnList.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID, "00101");
        assertTrue(supportedSatelliteServices.isEmpty());

        // Carrier config changed with carrierEnabledSatelliteFlag disabled
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID, "00102");
        assertTrue(supportedSatelliteServices.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID, "00103");
        assertTrue(supportedSatelliteServices.isEmpty());

        // Trigger carrier config changed with carrierEnabledSatelliteFlag enabled
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        satellitePlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        assertTrue(Arrays.equals(
                expectedSupportedSatellitePlmns, satellitePlmnList.stream().toArray()));
        supportedSatelliteServices =
                mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00102");
        assertTrue(Arrays.equals(expectedSupportedServices2,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        supportedSatelliteServices =
                mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00103");
        assertTrue(Arrays.equals(expectedSupportedServices3,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        // Subscriptions changed
        int[] newActiveSubIds = {SUB_ID1};
        doReturn(newActiveSubIds).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        satellitePlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        assertTrue(satellitePlmnList.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID, "00102");
        assertTrue(supportedSatelliteServices.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID, "00103");
        assertTrue(supportedSatelliteServices.isEmpty());


        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID1, "00102");
        assertNotNull(supportedSatelliteServices);
        assertTrue(Arrays.equals(expectedSupportedServices2,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServices(SUB_ID1, "00103");
        assertTrue(Arrays.equals(expectedSupportedServices3,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
    }

    @Test
    public void testConfigureSatellitePlmnOnCarrierConfigChanged() {
        logd("testConfigureSatellitePlmnOnCarrierConfigChanged");

        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        String[] satelliteProviderStrArray =
                {"00101", "00102", "00103", "00104", "00105"};
        List<String> satellitePlmnListFromOverlayConfig =
                Arrays.stream(satelliteProviderStrArray).toList();
        mContextFixture.putStringArrayResource(
                R.array.config_satellite_providers, satelliteProviderStrArray);

        /* Initially, the radio state is ON. In the constructor, satelliteController registers for
         the radio state changed events and immediately gets the radio state changed event as ON. */
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mCarrierConfigChangedListenerList.clear();
        TestSatelliteController testSatelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        processAllMessages();
        List<String> carrierPlmnList = testSatelliteController.getSatellitePlmnsForCarrier(
                SUB_ID);
        verify(mMockSatelliteModemInterface, never()).setSatellitePlmn(
                anyInt(), anyList(), anyList(), any(Message.class));
        assertTrue(carrierPlmnList.isEmpty());
        reset(mMockSatelliteModemInterface);

        // Test setSatellitePlmn() when Carrier Config change event triggered.
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);
        int[] supportedServices2 = {2};
        int[] supportedServices3 = {1, 3};
        PersistableBundle carrierSupportedSatelliteServicesPerProvider = new PersistableBundle();
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00102", supportedServices2);
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00103", supportedServices3);
        List<String> expectedCarrierPlmnList = Arrays.asList("00102", "00103");
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        carrierPlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        verify(mMockSatelliteModemInterface, never()).setSatellitePlmn(
                anyInt(), anyList(), anyList(), any(Message.class));
        assertTrue(carrierPlmnList.isEmpty());
        reset(mMockSatelliteModemInterface);

        // Reset TestSatelliteController so that device satellite PLMNs is loaded when
        // carrierEnabledSatelliteFlag is enabled.
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigChangedListenerList.clear();
        testSatelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);

        // Trigger carrier config changed with carrierEnabledSatelliteFlag enabled and empty
        // carrier supported satellite services.
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                new PersistableBundle());
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        carrierPlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        assertTrue(carrierPlmnList.isEmpty());
        List<String> allSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                carrierPlmnList, satellitePlmnListFromOverlayConfig);
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(EMPTY_STRING_LIST), eq(allSatellitePlmnList), any(Message.class));
        reset(mMockSatelliteModemInterface);

        // Trigger carrier config changed with carrierEnabledSatelliteFlag enabled and non-empty
        // carrier supported satellite services.
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        carrierPlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        allSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                carrierPlmnList, satellitePlmnListFromOverlayConfig);
        assertEquals(expectedCarrierPlmnList, carrierPlmnList);
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(carrierPlmnList), eq(allSatellitePlmnList), any(Message.class));
        reset(mMockSatelliteModemInterface);

        /* setSatellitePlmn() is called regardless whether satellite attach for carrier is
           supported. */
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                false);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(carrierPlmnList), eq(allSatellitePlmnList), any(Message.class));
        reset(mMockSatelliteModemInterface);

        // Test empty config_satellite_providers and empty carrier PLMN list
        mCarrierConfigChangedListenerList.clear();
        mContextFixture.putStringArrayResource(
                R.array.config_satellite_providers, EMPTY_STRING_ARRAY);
        testSatelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                new PersistableBundle());
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        carrierPlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        assertTrue(carrierPlmnList.isEmpty());
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(EMPTY_STRING_LIST), eq(EMPTY_STRING_LIST), any(Message.class));
        reset(mMockSatelliteModemInterface);
    }

    @Test
    public void testSatelliteCommunicationRestriction() {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        // Remove restriction reason if exist
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(false, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mSatelliteControllerUT.removeAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        mSatelliteControllerUT.removeAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(2));

        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(1));

        Set<Integer> restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(!restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER));
        assertTrue(!restrictionSet.contains(
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION));

        // Add satellite attach restriction reason by user
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(false, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mSatelliteControllerUT.addAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface, never())
                .requestSetSatelliteEnabledForCarrier(anyInt(), anyBoolean(), any(Message.class));
        assertTrue(waitForIIntegerConsumerResult(1));
        restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER));

        // remove satellite restriction reason by user
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(true, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mSatelliteControllerUT.removeAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(!restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER));
        verify(mMockSatelliteModemInterface, times(1))
                .requestSetSatelliteEnabledForCarrier(anyInt(), anyBoolean(), any(Message.class));

        // Add satellite attach restriction reason by user
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(false, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mSatelliteControllerUT.addAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER));
        verify(mMockSatelliteModemInterface, times(1))
                .requestSetSatelliteEnabledForCarrier(anyInt(), eq(false), any(Message.class));

        // add satellite attach restriction reason by geolocation
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.addAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION, mIIntegerConsumer);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION));
        verify(mMockSatelliteModemInterface, never())
                .requestSetSatelliteEnabledForCarrier(anyInt(), anyBoolean(), any(Message.class));

        // remove satellite attach restriction reason by geolocation
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.removeAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION, mIIntegerConsumer);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(!restrictionSet.contains(
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION));
        verify(mMockSatelliteModemInterface, never())
                .requestSetSatelliteEnabledForCarrier(anyInt(), anyBoolean(), any(Message.class));

        // remove satellite restriction reason by user
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.removeAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(!restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER));
        verify(mMockSatelliteModemInterface, times(1))
                .requestSetSatelliteEnabledForCarrier(anyInt(), eq(true), any(Message.class));
        reset(mMockSatelliteModemInterface);

        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);

        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.removeAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));
        verifyZeroInteractions(mMockSatelliteModemInterface);

        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.addAttachRestrictionForCarrier(SUB_ID,
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));
        verifyZeroInteractions(mMockSatelliteModemInterface);

        Set<Integer> satelliteRestrictionReasons =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertTrue(satelliteRestrictionReasons.isEmpty());
    }

    @Test
    public void testIsSatelliteAttachRequired() {
        TestSatelliteController satelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        mSatelliteCapabilitiesSemaphore.drainPermits();
        satelliteController.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(
                SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedSatelliteCapabilitiesResultCode);
        assertFalse(satelliteController.isSatelliteAttachRequired());

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSatelliteCapabilities(
                mEmptySatelliteCapabilities, SATELLITE_RESULT_SUCCESS);
        satelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        verifySatelliteSupported(satelliteController, true, SATELLITE_RESULT_SUCCESS);
        mSatelliteCapabilitiesSemaphore.drainPermits();
        satelliteController.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteCapabilitiesResultCode);
        assertEquals(mEmptySatelliteCapabilities, mQueriedSatelliteCapabilities);
        assertEquals(SatelliteManager.NT_RADIO_TECHNOLOGY_UNKNOWN,
                mSatelliteControllerUT.getSupportedNtnRadioTechnology());

        assertFalse(satelliteController.isSatelliteAttachRequired());

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSatelliteCapabilities(
                mSatelliteCapabilities, SATELLITE_RESULT_SUCCESS);
        satelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        verifySatelliteSupported(satelliteController, true, SATELLITE_RESULT_SUCCESS);
        mSatelliteCapabilitiesSemaphore.drainPermits();
        satelliteController.requestSatelliteCapabilities(SUB_ID, mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteCapabilitiesResultCode);
        assertEquals(mSatelliteCapabilities, mQueriedSatelliteCapabilities);
        assertTrue(
                mQueriedSatelliteCapabilities.getSupportedRadioTechnologies().contains(
                        satelliteController.getSupportedNtnRadioTechnology()));
        assertTrue(satelliteController.isSatelliteAttachRequired());

        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);
        assertFalse(satelliteController.isSatelliteAttachRequired());
    }

    @Test
    public void testSatelliteModemStateChanged() {
        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockDatagramController);
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_CONNECTED, null);
        processAllMessages();
        verify(mMockSatelliteSessionController, times(0)).onSatelliteModemStateChanged(
                SATELLITE_MODEM_STATE_CONNECTED);

        resetSatelliteControllerUTToSupportedAndProvisionedState();
        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockDatagramController);
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_UNAVAILABLE, null);
        processAllMessages();
        verify(mMockSatelliteSessionController, times(1)).onSatelliteEnabledStateChanged(eq(false));
        verify(mMockSatelliteSessionController, times(1)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(1)).setDemoMode(eq(false));

        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockDatagramController);
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_CONNECTED, null);
        processAllMessages();
        verify(mMockSatelliteSessionController, times(1)).onSatelliteModemStateChanged(
                SATELLITE_MODEM_STATE_CONNECTED);
    }

    @Test
    public void testRequestNtnSignalStrengthWithFeatureFlagEnabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        resetSatelliteControllerUT();

        mRequestNtnSignalStrengthSemaphore.drainPermits();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);

        @NtnSignalStrength.NtnSignalStrengthLevel int expectedLevel = NTN_SIGNAL_STRENGTH_GREAT;
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        /* In case request is not successful, result should be NTN_SIGNAL_STRENGTH_NONE */
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE, SATELLITE_RESULT_NOT_SUPPORTED);

        resetSatelliteControllerUT();
        provisionSatelliteService();

        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        // reset cache to NTN_SIGNAL_STRENGTH_NONE
        sendNtnSignalStrengthChangedEvent(NTN_SIGNAL_STRENGTH_NONE, null);
        processAllMessages();
        expectedLevel = NTN_SIGNAL_STRENGTH_POOR;
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testRequestNtnSignalStrengthWithFeatureFlagDisabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);

        resetSatelliteControllerUT();
        mRequestNtnSignalStrengthSemaphore.drainPermits();
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();

        @NtnSignalStrength.NtnSignalStrengthLevel int expectedLevel = NTN_SIGNAL_STRENGTH_GREAT;
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        expectedLevel = NTN_SIGNAL_STRENGTH_POOR;
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_MODEM_ERROR);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
    }

    @Test
    public void testRegisterForNtnSignalStrengthChangedWithFeatureFlagEnabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        Semaphore semaphore = new Semaphore(0);
        final NtnSignalStrength[] signalStrength = new NtnSignalStrength[1];
        INtnSignalStrengthCallback callback =
                new INtnSignalStrengthCallback.Stub() {
                    @Override
                    public void onNtnSignalStrengthChanged(NtnSignalStrength ntnSignalStrength) {
                        logd("onNtnSignalStrengthChanged: ntnSignalStrength="
                                + ntnSignalStrength);
                        try {
                            signalStrength[0] = ntnSignalStrength;
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onNtnSignalStrengthChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };

        verifyRegisterForNtnSignalStrengthChanged(SUB_ID, callback,
                SATELLITE_RESULT_INVALID_TELEPHONY_STATE);

        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifyRegisterForNtnSignalStrengthChanged(SUB_ID, callback,
                SATELLITE_RESULT_NOT_SUPPORTED);

        @NtnSignalStrength.NtnSignalStrengthLevel int expectedLevel = NTN_SIGNAL_STRENGTH_NONE;
        verifyRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_NOT_SUPPORTED);

        resetSatelliteControllerUT();
        setUpResponseForRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);
        provisionSatelliteService();
        verifyRegisterForNtnSignalStrengthChanged(SUB_ID, callback,
                SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(expectedLevel, SATELLITE_RESULT_SUCCESS);

        expectedLevel = NTN_SIGNAL_STRENGTH_GOOD;
        sendNtnSignalStrengthChangedEvent(expectedLevel, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForNtnSignalStrengthChanged"));
        assertEquals(expectedLevel, signalStrength[0].getLevel());
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_GOOD, SATELLITE_RESULT_SUCCESS);

        expectedLevel = NTN_SIGNAL_STRENGTH_POOR;
        sendNtnSignalStrengthChangedEvent(expectedLevel, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForNtnSignalStrengthChanged"));
        assertEquals(expectedLevel, signalStrength[0].getLevel());
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_POOR, SATELLITE_RESULT_SUCCESS);

        mSatelliteControllerUT.unregisterForNtnSignalStrengthChanged(SUB_ID, callback);
        sendNtnSignalStrengthChangedEvent(NTN_SIGNAL_STRENGTH_GREAT, null);
        processAllMessages();
        assertFalse(waitForForEvents(
                semaphore, 1, "testRegisterForNtnSignalStrengthChanged"));
        /* Even if all listeners are unregistered, the cache is updated with the latest value when a
         new value event occurs. */
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_GREAT, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testRegisterForNtnSignalStrengthChangedWithFeatureFlagDisabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);

        Semaphore semaphore = new Semaphore(0);
        final NtnSignalStrength[] signalStrength = new NtnSignalStrength[1];
        INtnSignalStrengthCallback callback =
                new INtnSignalStrengthCallback.Stub() {
                    @Override
                    public void onNtnSignalStrengthChanged(NtnSignalStrength ntnSignalStrength) {
                        logd("onNtnSignalStrengthChanged: ntnSignalStrength="
                                + ntnSignalStrength);
                        try {
                            signalStrength[0] = ntnSignalStrength;
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onNtnSignalStrengthChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };

        verifyRegisterForNtnSignalStrengthChanged(SUB_ID, callback,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        verifyRegisterForNtnSignalStrengthChanged(SUB_ID, callback,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        setUpResponseForRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_SUCCESS);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        verifyRegisterForNtnSignalStrengthChanged(SUB_ID, callback,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        verifyRequestNtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE,
                SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        @NtnSignalStrength.NtnSignalStrengthLevel int expectedNtnSignalStrengthLevel =
                NTN_SIGNAL_STRENGTH_GOOD;
        sendNtnSignalStrengthChangedEvent(expectedNtnSignalStrengthLevel, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 0, "testRegisterForNtnSignalStrengthChanged"));
    }

    @Test
    public void testSendingNtnSignalStrengthWithFeatureEnabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        int expectedResult = SATELLITE_RESULT_SUCCESS;
        // startSendingNtnSignalStrength() is requested when screen on event comes.
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        provisionSatelliteService();
        setUpResponseForStartSendingNtnSignalStrength(expectedResult);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .startSendingNtnSignalStrength(any(Message.class));

        // requested again but ignored as expected and current state are matched.
        setUpResponseForStartSendingNtnSignalStrength(expectedResult);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .startSendingNtnSignalStrength(any(Message.class));

        // stopSendingNtnSignalStrength() is requested when screen off event comes.
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopSendingNtnSignalStrength(expectedResult);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .stopSendingNtnSignalStrength(any(Message.class));

        // requested again but ignored as expected and current state are matched.
        setUpResponseForStopSendingNtnSignalStrength(expectedResult);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .stopSendingNtnSignalStrength(any(Message.class));

        // startSendingNtnSignalStrength() is requested but received fail from the service.
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStartSendingNtnSignalStrength(SATELLITE_RESULT_INVALID_MODEM_STATE);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .startSendingNtnSignalStrength(any(Message.class));

        /* stopSendingNtnSignalStrength() is ignored because startSendingNtnSignalStrength has
           failed thus current state is stopSendingNtnSignalStrength */
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopSendingNtnSignalStrength(SATELLITE_RESULT_NO_RESOURCES);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .stopSendingNtnSignalStrength(any(Message.class));

        // startSendingNtnSignalStrength() is requested and modem state is changed
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStartSendingNtnSignalStrength(SATELLITE_RESULT_SUCCESS);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .startSendingNtnSignalStrength(any(Message.class));

        // stopSendingNtnSignalStrength() is failed as modem returns error
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopSendingNtnSignalStrength(SATELLITE_RESULT_NO_RESOURCES);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .stopSendingNtnSignalStrength(any(Message.class));

        // request stopSendingNtnSignalStrength() again and returns success
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopSendingNtnSignalStrength(SATELLITE_RESULT_SUCCESS);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, times(1))
                .stopSendingNtnSignalStrength(any(Message.class));
    }

    @Test
    public void testSendingNtnSignalStrengthWithFeatureDisabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);

        int expectedResult = SATELLITE_RESULT_SUCCESS;
        // startSendingNtnSignalStrength() is requested when screen on event comes.
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestIsSatelliteSupported(true, expectedResult);
        setUpResponseForRequestIsSatelliteProvisioned(true, expectedResult);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        setUpResponseForStartSendingNtnSignalStrength(expectedResult);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .startSendingNtnSignalStrength(any(Message.class));

        // stopSendingNtnSignalStrength() is requested when screen off event comes.
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopSendingNtnSignalStrength(expectedResult);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(false);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .stopSendingNtnSignalStrength(any(Message.class));
    }

    @Test
    public void testIsSatelliteSupportedViaCarrier() {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        assertFalse(mSatelliteControllerUT.isSatelliteSupportedViaCarrier());

        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        assertFalse(mSatelliteControllerUT.isSatelliteSupportedViaCarrier());

        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isSatelliteSupportedViaCarrier());
    }

    @Test
    public void testCarrierEnabledSatelliteConnectionHysteresisTime() {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        assertFalse(mSatelliteControllerUT.isSatelliteConnectedViaCarrierWithinHysteresisTime());

        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigBundle.putInt(KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT, 1 * 60);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        mSatelliteControllerUT.elapsedRealtime = 0;
        assertFalse(mSatelliteControllerUT.isSatelliteConnectedViaCarrierWithinHysteresisTime());

        when(mServiceState.isUsingNonTerrestrialNetwork()).thenReturn(false);
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(false);
        sendServiceStateChangedEvent();
        processAllMessages();
        assertFalse(mSatelliteControllerUT.isSatelliteConnectedViaCarrierWithinHysteresisTime());

        // Last satellite connected time of Phone2 should be 0
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(true);
        sendServiceStateChangedEvent();
        processAllMessages();
        // 2 minutes later and hysteresis timeout is 1 minute
        mSatelliteControllerUT.elapsedRealtime = 2 * 60 * 1000;
        // But Phone2 is connected to NTN right now
        assertTrue(mSatelliteControllerUT.isSatelliteConnectedViaCarrierWithinHysteresisTime());

        // Last satellite disconnected time of Phone2 should be 2 * 60 * 1000
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(false);
        sendServiceStateChangedEvent();
        processAllMessages();
        // Current time (2) - last disconnected time (2) < hysteresis timeout (1)
        assertTrue(mSatelliteControllerUT.isSatelliteConnectedViaCarrierWithinHysteresisTime());

        // Current time (4) - last disconnected time (2) > hysteresis timeout (1)
        mSatelliteControllerUT.elapsedRealtime = 4 * 60 * 1000;
        assertFalse(mSatelliteControllerUT.isSatelliteConnectedViaCarrierWithinHysteresisTime());
    }

    @Test
    public void testRegisterForSatelliteCapabilitiesChangedWithFeatureFlagEnabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        Semaphore semaphore = new Semaphore(0);
        final SatelliteCapabilities[] satelliteCapabilities = new SatelliteCapabilities[1];
        ISatelliteCapabilitiesCallback callback =
                new ISatelliteCapabilitiesCallback.Stub() {
                    @Override
                    public void onSatelliteCapabilitiesChanged(SatelliteCapabilities capabilities) {
                        logd("onSatelliteCapabilitiesChanged: " + capabilities);
                        try {
                            satelliteCapabilities[0] = capabilities;
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSatelliteCapabilitiesChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };

        int errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(SUB_ID,
                callback);
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, errorCode);

        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(SUB_ID,
                callback);
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, errorCode);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(SUB_ID,
                callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);
        SatelliteCapabilities expectedCapabilities = mSatelliteCapabilities;
        sendSatelliteCapabilitiesChangedEvent(expectedCapabilities, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteCapabilitiesChanged"));
        assertTrue(expectedCapabilities.equals(satelliteCapabilities[0]));

        expectedCapabilities = mEmptySatelliteCapabilities;
        sendSatelliteCapabilitiesChangedEvent(expectedCapabilities, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteCapabilitiesChanged"));
        assertTrue(expectedCapabilities.equals(satelliteCapabilities[0]));

        mSatelliteControllerUT.unregisterForCapabilitiesChanged(SUB_ID, callback);
        expectedCapabilities = mSatelliteCapabilities;
        sendSatelliteCapabilitiesChangedEvent(expectedCapabilities, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 0, "testRegisterForSatelliteCapabilitiesChanged"));
    }

    @Test
    public void testRegisterForSatelliteCapabilitiesChangedWithFeatureFlagDisabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);

        Semaphore semaphore = new Semaphore(0);
        final SatelliteCapabilities[] satelliteCapabilities = new SatelliteCapabilities[1];
        ISatelliteCapabilitiesCallback callback =
                new ISatelliteCapabilitiesCallback.Stub() {
                    @Override
                    public void onSatelliteCapabilitiesChanged(SatelliteCapabilities capabilities) {
                        logd("onSatelliteCapabilitiesChanged: " + capabilities);
                        try {
                            satelliteCapabilities[0] = capabilities;
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSatelliteCapabilitiesChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };

        int errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(SUB_ID,
                callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);

        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(SUB_ID,
                callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(SUB_ID,
                callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);

        SatelliteCapabilities expectedCapabilities = mSatelliteCapabilities;
        sendSatelliteCapabilitiesChangedEvent(expectedCapabilities, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 0, "testRegisterForSatelliteCapabilitiesChanged"));
    }

    @Test
    public void testSatelliteCommunicationRestrictionForEntitlement() throws Exception {
        logd("testSatelliteCommunicationRestrictionForEntitlement");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);
        SparseBooleanArray satelliteEnabledPerCarrier = new SparseBooleanArray();
        replaceInstance(SatelliteController.class, "mSatelliteEntitlementStatusPerCarrier",
                mSatelliteControllerUT, satelliteEnabledPerCarrier);

        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        setUpResponseForRequestSetSatelliteEnabledForCarrier(true, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        Map<Integer, Set<Integer>> satelliteAttachRestrictionForCarrierArray = new HashMap<>();
        satelliteAttachRestrictionForCarrierArray.put(SUB_ID, new HashSet<>());
        satelliteAttachRestrictionForCarrierArray.get(SUB_ID).add(
                SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT);
        replaceInstance(SatelliteController.class, "mSatelliteAttachRestrictionForCarrierArray",
                mSatelliteControllerUT, satelliteAttachRestrictionForCarrierArray);

        // Verify call the requestSetSatelliteEnabledForCarrier to enable the satellite when
        // satellite service is enabled by entitlement server.
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, true, new ArrayList<>(),
                mIIntegerConsumer);
        processAllMessages();

        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface, times(1))
                .requestSetSatelliteEnabledForCarrier(anyInt(), eq(true), any(Message.class));

        // Verify call the requestSetSatelliteEnabledForCarrier to disable the satellite when
        // satellite service is disabled by entitlement server.
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        Map<Integer, Boolean> enabledForCarrierArrayPerSub = new HashMap<>();
        enabledForCarrierArrayPerSub.put(SUB_ID, true);
        replaceInstance(SatelliteController.class, "mIsSatelliteAttachEnabledForCarrierArrayPerSub",
                mSatelliteControllerUT, enabledForCarrierArrayPerSub);
        doReturn(mIsSatelliteServiceSupported)
                .when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestSetSatelliteEnabledForCarrier(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false, new ArrayList<>(),
                mIIntegerConsumer);
        processAllMessages();

        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface, times(1))
                .requestSetSatelliteEnabledForCarrier(anyInt(), eq(false), any(Message.class));
    }

    @Test
    public void testPassSatellitePlmnToModemAfterUpdateSatelliteEntitlementStatus()
            throws Exception {
        logd("testPassSatellitePlmnToModemAfterUpdateSatelliteEntitlementStatus");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList =  new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);

        // If the PlmnListPerCarrier and the overlay config plmn list are empty verify passing to
        // the modem.
        List<String> entitlementPlmnList = new ArrayList<>();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, mIIntegerConsumer);

        List<String> plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(
                SUB_ID);
        List<String> allSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                plmnListPerCarrier, overlayConfigPlmnList);

        assertEquals(new ArrayList<>(), plmnListPerCarrier);
        assertEquals(new ArrayList<>(), allSatellitePlmnList);
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(plmnListPerCarrier), eq(allSatellitePlmnList), any(Message.class));

        // If the PlmnListPerCarrier and the overlay config plmn list are exist verify passing
        // the modem.
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", "00103"}).toList();
        overlayConfigPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00104"}).toList();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);

        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, true,
                entitlementPlmnList, mIIntegerConsumer);

        plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID);
        allSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                plmnListPerCarrier, overlayConfigPlmnList);

        assertEquals(entitlementPlmnList, plmnListPerCarrier);
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(plmnListPerCarrier), eq(allSatellitePlmnList), any(Message.class));

        // If the PlmnListPerCarrier and the overlay config plmn list are exist verify passing
        // the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", "00103"}).toList();
        Map<Integer, Map<String, Set<Integer>>>
                satelliteServicesSupportedByCarriers = new HashMap<>();
        List<String> carrierConfigPlmnList = Arrays.stream(new String[]{"00105", "00106"}).toList();
        Map<String, Set<Integer>> plmnAndService = new HashMap<>();
        plmnAndService.put(carrierConfigPlmnList.get(0), new HashSet<>(Arrays.asList(3, 5)));
        plmnAndService.put(carrierConfigPlmnList.get(1), new HashSet<>(Arrays.asList(3)));
        satelliteServicesSupportedByCarriers.put(SUB_ID, plmnAndService);
        replaceInstance(SatelliteController.class, "mSatelliteServicesSupportedByCarriers",
                mSatelliteControllerUT, satelliteServicesSupportedByCarriers);
        overlayConfigPlmnList = Arrays.stream(new String[]{"00101", "00102", "00104"}).toList();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        List<String> mergedPlmnList = entitlementPlmnList;

        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, true,
                entitlementPlmnList, mIIntegerConsumer);

        plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID);
        allSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                plmnListPerCarrier, overlayConfigPlmnList);

        assertEquals(mergedPlmnList, plmnListPerCarrier);
        verify(mMockSatelliteModemInterface, times(1)).setSatellitePlmn(anyInt(),
                eq(plmnListPerCarrier), eq(allSatellitePlmnList), any(Message.class));
    }

    private void setConfigData(List<String> plmnList) {
        doReturn(plmnList).when(mMockConfig).getAllSatellitePlmnsForCarrier(anyInt());
        doReturn(mMockConfig).when(mMockConfigParser).getConfig();

        Map<String, List<Integer>> servicePerPlmn = new HashMap<>();
        List<List<Integer>> serviceLists = Arrays.asList(
                Arrays.asList(1),
                Arrays.asList(3),
                Arrays.asList(5)
        );
        for (int i = 0; i < plmnList.size(); i++) {
            servicePerPlmn.put(plmnList.get(i), serviceLists.get(i));
        }
        doReturn(servicePerPlmn).when(mMockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mMockConfig).when(mMockConfigParser).getConfig();
    }

    @Test
    public void testUpdateSupportedSatelliteServices() throws Exception {
        logd("testUpdateSupportedSatelliteServices");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        replaceInstance(SatelliteController.class, "mSatelliteServicesSupportedByCarriers",
                mSatelliteControllerUT, new HashMap<>());
        List<Integer> servicesPerPlmn;

        // verify whether an empty list is returned with conditions below
        // the config data plmn list : empty
        // the carrier config plmn list : empty
        setConfigData(new ArrayList<>());
        setCarrierConfigDataPlmnList(new ArrayList<>());
        invokeCarrierConfigChanged();
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "31016");
        assertEquals(new ArrayList<>(), servicesPerPlmn);

        // Verify whether the carrier config plmn list is returned with conditions below
        // the config data plmn list : empty
        // the carrier config plmn list : exist with services {{2}, {1, 3}, {2}}
        setConfigData(new ArrayList<>());
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00101");
        assertEquals(Arrays.asList(2).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00102");
        assertEquals(Arrays.asList(1, 3).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00104");
        assertEquals(Arrays.asList(2).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());

        // Verify whether the carrier config plmn list is returned with conditions below
        // the config data plmn list : exist with services {{1}, {3}, {5}}
        // the carrier config plmn list : exist with services {{2}, {1, 3}, {2}}
        setConfigData(Arrays.asList("00101", "00102", "31024"));
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00101");
        assertEquals(Arrays.asList(1).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00102");
        assertEquals(Arrays.asList(3).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "00104");
        assertEquals(new ArrayList<>(), servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServices(SUB_ID, "31024");
        assertEquals(Arrays.asList(5).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
    }
    private void setEntitlementPlmnList(List<String> plmnList) throws Exception {
        SparseArray<List<String>> entitlementPlmnListPerCarrier = new SparseArray<>();
        if (!plmnList.isEmpty()) {
            entitlementPlmnListPerCarrier.clear();
            entitlementPlmnListPerCarrier.put(SUB_ID, plmnList);
        }
        replaceInstance(SatelliteController.class, "mEntitlementPlmnListPerCarrier",
                mSatelliteControllerUT, entitlementPlmnListPerCarrier);
    }

    private void setConfigDataPlmnList(List<String> plmnList) {
        doReturn(plmnList).when(mMockConfig).getAllSatellitePlmnsForCarrier(anyInt());
        doReturn(mMockConfig).when(mMockConfigParser).getConfig();
    }

    private void setCarrierConfigDataPlmnList(List<String> plmnList) {
        if (!plmnList.isEmpty()) {
            mCarrierConfigBundle.putBoolean(
                    CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                    true);
            PersistableBundle carrierSupportedSatelliteServicesPerProvider =
                    new PersistableBundle();
            List<String> carrierConfigPlmnList = plmnList;
            carrierSupportedSatelliteServicesPerProvider.putIntArray(
                    carrierConfigPlmnList.get(0), new int[]{2});
            carrierSupportedSatelliteServicesPerProvider.putIntArray(
                    carrierConfigPlmnList.get(1), new int[]{1, 3});
            carrierSupportedSatelliteServicesPerProvider.putIntArray(
                    carrierConfigPlmnList.get(2), new int[]{2});
            mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                            .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                    carrierSupportedSatelliteServicesPerProvider);
        } else {
            mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                            .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                    new PersistableBundle());
        }
    }

    private void invokeCarrierConfigChanged() {
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
    }

    @Test
    public void testUpdatePlmnListPerCarrier() throws Exception {
        logd("testUpdatePlmnListPerCarrier");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> plmnListPerCarrier;

        // verify whether an empty list is returned with conditions below
        // the entitlement plmn list : empty
        // the config data plmn list : empty
        // the carrier config plmn list : empty
        setEntitlementPlmnList(new ArrayList<>());
        setConfigDataPlmnList(new ArrayList<>());
        setCarrierConfigDataPlmnList(new ArrayList<>());
        invokeCarrierConfigChanged();
        plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID);
        assertEquals(new ArrayList<>(), plmnListPerCarrier.stream().sorted().toList());

        // Verify whether the carrier config plmn list is returned with conditions below
        // the entitlement plmn list : empty
        // the config data plmn list : empty
        // the carrier config plmn list : exist
        setEntitlementPlmnList(new ArrayList<>());
        setConfigDataPlmnList(new ArrayList<>());
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID);
        assertEquals(Arrays.asList("00101", "00102", "00104").stream().sorted().toList(),
                plmnListPerCarrier.stream().sorted().toList());

        // Verify whether config data plmn list is returned with conditions below
        // the entitlement plmn list : empty
        // the config data plmn list : exist
        // the carrier config plmn list : exist
        setEntitlementPlmnList(new ArrayList<>());
        setConfigDataPlmnList(Arrays.asList("11111", "22222", "33333"));
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID);
        assertEquals(Arrays.asList("11111", "22222", "33333").stream().sorted().toList(),
                plmnListPerCarrier.stream().sorted().toList());

        // Verify whether the entitlement plmn list is returned with conditions below
        // the entitlement plmn list : exist
        // the config data plmn list : exist
        // the carrier config plmn list : exist
        setEntitlementPlmnList(Arrays.asList("99090", "88080", "77070"));
        setConfigDataPlmnList(Arrays.asList("11111", "22222", "33333"));
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID);
        assertEquals(Arrays.asList("99090", "88080", "77070").stream().sorted().toList(),
                plmnListPerCarrier.stream().sorted().toList());
    }

    @Test
    public void testEntitlementStatus() throws Exception {
        logd("testEntitlementStatus");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        SparseBooleanArray satelliteEnabledPerCarrier = new SparseBooleanArray();
        replaceInstance(SatelliteController.class, "mSatelliteEntitlementStatusPerCarrier",
                mSatelliteControllerUT, satelliteEnabledPerCarrier);

        // Change SUB_ID's EntitlementStatus to true
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, true, new ArrayList<>(),
                mIIntegerConsumer);

        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID));
        assertEquals(false, satelliteEnabledPerCarrier.get(SUB_ID1));

        // Change SUB_ID1's EntitlementStatus to true
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID1, true, new ArrayList<>(),
                mIIntegerConsumer);

        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID));
        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID1));

        // Change SUB_ID's EntitlementStatus to false
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false, new ArrayList<>(),
                mIIntegerConsumer);

        assertEquals(false, satelliteEnabledPerCarrier.get(SUB_ID));
        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID1));
    }

    @Test
    public void testUpdateRestrictReasonForEntitlementPerCarrier() throws Exception {
        logd("testUpdateRestrictReasonForEntitlementPerCarrier");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        // Verify that the entitlement restriction reason is added before the entitlement query,
        // When the Satellite entitlement status value read from DB is disabled.
        doReturn("").when(mContext).getOpPackageName();
        doReturn("").when(mContext).getAttributionTag();
        doReturn("0").when(mMockSubscriptionManagerService).getSubscriptionProperty(anyInt(),
                eq(SATELLITE_ENTITLEMENT_STATUS), anyString(), anyString());
        doReturn(new ArrayList<>()).when(
                mMockSubscriptionManagerService).getSatelliteEntitlementPlmnList(anyInt());
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        Set<Integer> restrictionSet =
                mSatelliteControllerUT.getAttachRestrictionReasonsForCarrier(SUB_ID);
        assertEquals(1, restrictionSet.size());
        assertTrue(restrictionSet.contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT));
    }

    @Test
    public void testUpdateEntitlementPlmnListPerCarrier() throws Exception {
        logd("testUpdateEntitlementPlmnListPerCarrier");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        // If the Satellite entitlement plmn list read from the DB is empty and carrier config
        // plmn list also is empty , check whether an empty list is returned when calling
        // getSatellitePlmnsForCarrier before the entitlement query.
        doReturn(new ArrayList<>()).when(
                mMockSubscriptionManagerService).getSatelliteEntitlementPlmnList(anyInt());
        replaceInstance(SatelliteController.class, "mEntitlementPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        replaceInstance(SatelliteController.class, "mSatelliteServicesSupportedByCarriers",
                mSatelliteControllerUT, new HashMap<>());
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        assertEquals(new ArrayList<>(), mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID));

        // If the Satellite entitlement plmn list read from the DB is valid and carrier config
        // plmn list is empty, check whether valid entitlement plmn list is returned
        // when calling getSatellitePlmnsForCarrier before the entitlement query.
        replaceInstance(SatelliteController.class, "mEntitlementPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> expectedSatelliteEntitlementPlmnList = Arrays.asList("123456,12560");
        doReturn(expectedSatelliteEntitlementPlmnList).when(
                mMockSubscriptionManagerService).getSatelliteEntitlementPlmnList(anyInt());
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        assertEquals(expectedSatelliteEntitlementPlmnList,
                mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID));

        // If the Satellite entitlement plmn list read from the DB is valid and carrier config
        // plmn list is valid, check whether valid entitlement plmn list is returned when
        // calling getSatellitePlmnsForCarrier before the entitlement query.
        replaceInstance(SatelliteController.class, "mEntitlementPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        PersistableBundle carrierSupportedSatelliteServicesPerProvider = new PersistableBundle();
        List<String> carrierConfigPlmnList = Arrays.asList("00102", "00103", "00105");
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                carrierConfigPlmnList.get(0), new int[]{2});
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                carrierConfigPlmnList.get(1), new int[]{1, 3});
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                carrierConfigPlmnList.get(2), new int[]{2});
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        assertEquals(expectedSatelliteEntitlementPlmnList,
                mSatelliteControllerUT.getSatellitePlmnsForCarrier(SUB_ID));

        // If the Satellite entitlement plmn list read from the DB is empty and carrier config
        // plmn list is valid, check whether valid carrier config plmn list is returned when
        // calling getSatellitePlmnsForCarrier before the entitlement query.
        replaceInstance(SatelliteController.class, "mEntitlementPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        doReturn(new ArrayList<>()).when(
                mMockSubscriptionManagerService).getSatelliteEntitlementPlmnList(anyInt());
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        assertEquals(carrierConfigPlmnList.stream().sorted().toList(),
                mSatelliteControllerUT.getSatellitePlmnsForCarrier(
                        SUB_ID).stream().sorted().toList());
    }

    @Test
    public void testHandleEventServiceStateChanged() throws Exception {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        // Do nothing when the satellite is not connected
        doReturn(false).when(mServiceState).isUsingNonTerrestrialNetwork();
        sendServiceStateChangedEvent();
        processAllMessages();
        assertEquals(false,
                mSharedPreferences.getBoolean(SATELLITE_SYSTEM_NOTIFICATION_DONE_KEY, false));
        verify(mMockNotificationManager, never()).notifyAsUser(anyString(), anyInt(), any(), any());

        // Check sending a system notification when the satellite is connected
        doReturn(true).when(mServiceState).isUsingNonTerrestrialNetwork();
        sendServiceStateChangedEvent();
        processAllMessages();
        verify(mMockNotificationManager, times(1)).notifyAsUser(anyString(), anyInt(), any(),
                any());
        assertEquals(true,
                mSharedPreferences.getBoolean(SATELLITE_SYSTEM_NOTIFICATION_DONE_KEY, false));

        // Check don't display again after displayed already a system notification.
        sendServiceStateChangedEvent();
        processAllMessages();
        verify(mMockNotificationManager, times(1)).notifyAsUser(anyString(), anyInt(), any(),
                any());
    }

    @Test
    public void testRequestSatelliteEnabled_timeout() {
        mIsSatelliteEnabledSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Successfully disable satellite
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Time out to enable satellite
        ArgumentCaptor<Message> enableSatelliteResponse = ArgumentCaptor.forClass(Message.class);
        mIIntegerConsumerResults.clear();
        setUpNoResponseForRequestSatelliteEnabled(true, false);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockSatelliteModemInterface).requestSatelliteEnabled(eq(true), eq(false),
                enableSatelliteResponse.capture());

        clearInvocations(mMockSatelliteModemInterface);
        moveTimeForward(TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_MODEM_TIMEOUT, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface).requestSatelliteEnabled(eq(false), eq(false), any(
                Message.class));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Send the response for the above request to enable satellite. SatelliteController should
        // ignore the event
        Message response = enableSatelliteResponse.getValue();
        AsyncResult.forMessage(response, null, null);
        response.sendToTarget();
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Successfully enable satellite
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Time out to disable satellite
        ArgumentCaptor<Message> disableSatelliteResponse = ArgumentCaptor.forClass(Message.class);
        mIIntegerConsumerResults.clear();
        clearInvocations(mMockSatelliteModemInterface);
        setUpNoResponseForRequestSatelliteEnabled(false, false);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockSatelliteModemInterface).requestSatelliteEnabled(eq(false), eq(false),
                disableSatelliteResponse.capture());

        clearInvocations(mMockSatelliteModemInterface);
        moveTimeForward(TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_MODEM_TIMEOUT, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(anyBoolean(),
                anyBoolean(), any(Message.class));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Send the response for the above request to disable satellite. SatelliteController should
        // ignore the event
        response = disableSatelliteResponse.getValue();
        AsyncResult.forMessage(response, null, null);
        response.sendToTarget();
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
    }

    private void resetSatelliteControllerUTEnabledState() {
        logd("resetSatelliteControllerUTEnabledState");
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_RADIO_NOT_AVAILABLE);
        doNothing().when(mMockSatelliteModemInterface)
                .setSatelliteServicePackageName(anyString());
        mSatelliteControllerUT.setSatelliteServicePackageName("TestSatelliteService");
        processAllMessages();

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
    }

    private void resetSatelliteControllerUT() {
        logd("resetSatelliteControllerUT");
        // Trigger cleanUpResources
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_UNAVAILABLE, null);
        processAllMessages();

        // Reset all cached states
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_RADIO_NOT_AVAILABLE);
        doNothing().when(mMockSatelliteModemInterface)
                .setSatelliteServicePackageName(anyString());
        mSatelliteControllerUT.setSatelliteServicePackageName("TestSatelliteService");
        processAllMessages();
    }

    private void resetSatelliteControllerUTToSupportedAndProvisionedState() {
        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        sendProvisionedStateChangedEvent(true, null);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
    }

    private void resetSatelliteControllerUTToOffAndProvisionedState() {
        resetSatelliteControllerUTToSupportedAndProvisionedState();
        // Clean up pending resources and move satellite controller to OFF state.
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_UNAVAILABLE, null);
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
    }

    private void resetSatelliteControllerUTToOnAndProvisionedState() {
        resetSatelliteControllerUTToOffAndProvisionedState();
        setRadioPower(true);
        processAllMessages();

        setUpResponseForRequestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(SUB_ID, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
    }

    private void setUpResponseForRequestIsSatelliteEnabled(boolean isSatelliteEnabled,
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            int[] enabled = new int[] {isSatelliteEnabled ? 1 : 0};
            AsyncResult.forMessage(message, enabled, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).requestIsSatelliteEnabled(any(Message.class));
    }

    private void setUpResponseForRequestIsSatelliteSupported(
            boolean isSatelliteSupported, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, isSatelliteSupported, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).requestIsSatelliteSupported(any(Message.class));
    }

    private void setUpResponseForRequestIsSatelliteAllowedForCurrentLocation(
            boolean isSatelliteAllowed, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, isSatelliteAllowed, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestIsSatelliteCommunicationAllowedForCurrentLocation(any(Message.class));
    }

    private void setUpNullResponseForRequestIsSatelliteAllowedForCurrentLocation(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestIsSatelliteCommunicationAllowedForCurrentLocation(any(Message.class));
    }

    private void setUpResponseForRequestTimeForNextSatelliteVisibility(
            int satelliteVisibilityTime, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        int[] visibilityTime = new int[]{satelliteVisibilityTime};
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, visibilityTime, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestTimeForNextSatelliteVisibility(any(Message.class));
    }

    private void setUpNullResponseForRequestTimeForNextSatelliteVisibility(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestTimeForNextSatelliteVisibility(any(Message.class));
    }

    private void setUpResponseForRequestIsSatelliteProvisioned(
            boolean isSatelliteProvisioned, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        int[] provisioned = new int[]{isSatelliteProvisioned ? 1 : 0};
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, provisioned, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).requestIsSatelliteProvisioned(any(Message.class));
    }

    private void setUpResponseForRequestSatelliteEnabled(
            boolean enabled, boolean demoMode, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            if (exception == null && !enabled) {
                sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_OFF, null);
            }
            Message message = (Message) invocation.getArguments()[2];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestSatelliteEnabled(eq(enabled), eq(demoMode), any(Message.class));
    }

    private void setUpResponseForRequestSetSatelliteEnabledForCarrier(
            boolean enabled, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[2];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestSetSatelliteEnabledForCarrier(anyInt(), eq(enabled), any(Message.class));
    }

    private void setUpNoResponseForRequestSatelliteEnabled(boolean enabled, boolean demoMode) {
        doNothing().when(mMockSatelliteModemInterface)
                .requestSatelliteEnabled(eq(enabled), eq(demoMode), any(Message.class));
    }

    private void setUpResponseForProvisionSatelliteService(
            String token, byte[] provisionData, @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[2];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .provisionSatelliteService(eq(token), any(byte[].class), any(Message.class));
    }

    private void setUpNoResponseForProvisionSatelliteService(String token) {
        doNothing().when(mMockSatelliteModemInterface)
                .provisionSatelliteService(eq(token), any(), any(Message.class));
    }

    private void setUpResponseForDeprovisionSatelliteService(String token,
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[1];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .deprovisionSatelliteService(eq(token), any(Message.class));
    }

    private void setUpResponseForRequestSatelliteCapabilities(
            SatelliteCapabilities satelliteCapabilities,
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, satelliteCapabilities, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).requestSatelliteCapabilities(any(Message.class));
    }

    private void setUpResponseForRequestNtnSignalStrength(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel,
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, new NtnSignalStrength(ntnSignalStrengthLevel),
                    exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).requestNtnSignalStrength(any(Message.class));
    }

    private boolean waitForForEvents(
            Semaphore semaphore, int expectedNumberOfEvents, String caller) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(caller + ": Timeout to receive the expected event");
                    return false;
                }
            } catch (Exception ex) {
                loge(caller + ": Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private void setUpNullResponseForRequestSatelliteCapabilities(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).requestSatelliteCapabilities(any(Message.class));
    }

    private void setUpResponseForStartSatelliteTransmissionUpdates(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockPointingAppController).startSatelliteTransmissionUpdates(any(Message.class));
    }

    private void setUpResponseForStopSatelliteTransmissionUpdates(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockPointingAppController).stopSatelliteTransmissionUpdates(any(Message.class));
    }

    private void setUpResponseForStartSendingNtnSignalStrength(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).startSendingNtnSignalStrength(any(Message.class));
    }

    private void setUpResponseForStopSendingNtnSignalStrength(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).stopSendingNtnSignalStrength(any(Message.class));
    }

    private boolean waitForRequestIsSatelliteSupportedResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSatelliteSupportSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive requestIsSatelliteSupported() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestIsSatelliteSupportedResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestIsSatelliteAllowedForCurrentLocationResult(
            int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSatelliteAllowedSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive "
                            + "requestIsCommunicationAllowedForCurrentLocation()"
                            + " callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestIsSatelliteSupportedResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestTimeForNextSatelliteVisibilityResult(
            int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSatelliteVisibilityTimeSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive "
                            + "requestTimeForNextSatelliteVisibility() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestTimeForNextSatelliteVisibilityResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestIsSatelliteEnabledResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mIsSatelliteEnabledSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive requestIsSatelliteEnabled() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestIsSatelliteEnabledResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestIsSatelliteProvisionedResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mIsSatelliteProvisionedSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive requestIsSatelliteProvisioned() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestIsSatelliteProvisionedResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestSatelliteCapabilitiesResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSatelliteCapabilitiesSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive requestSatelliteCapabilities() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestSatelliteCapabilitiesResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestIsDemoModeEnabledResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mIsDemoModeEnabledSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive requestIsDemoModeEnabled() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForRequestIsDemoModeEnabled: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForRequestNtnSignalStrengthResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRequestNtnSignalStrengthSemaphore.tryAcquire(TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive requestNtnSignalStrength() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("requestNtnSignalStrength: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForIIntegerConsumerResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mIIntegerConsumerSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive IIntegerConsumer() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForIIntegerConsumerResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private void verifySatelliteSupported(boolean supported, int expectedErrorCode) {
        mSatelliteSupportSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteSupported(SUB_ID, mSatelliteSupportReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteSupportedResult(1));
        assertEquals(expectedErrorCode, mQueriedSatelliteSupportedResultCode);
        assertEquals(supported, mQueriedSatelliteSupported);
    }

    private void verifySatelliteSupported(TestSatelliteController satelliteController,
            boolean supported, int expectedErrorCode) {
        mSatelliteSupportSemaphore.drainPermits();
        satelliteController.requestIsSatelliteSupported(SUB_ID, mSatelliteSupportReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteSupportedResult(1));
        assertEquals(expectedErrorCode, mQueriedSatelliteSupportedResultCode);
        assertEquals(supported, mQueriedSatelliteSupported);
    }

    private void verifySatelliteEnabled(boolean enabled, int expectedErrorCode) {
        mIsSatelliteEnabledSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteEnabled(SUB_ID, mIsSatelliteEnabledReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteEnabledResult(1));
        assertEquals(expectedErrorCode, mQueriedIsSatelliteEnabledResultCode);
        assertEquals(enabled, mQueriedIsSatelliteEnabled);
    }

    private void verifySatelliteProvisioned(boolean provisioned, int expectedErrorCode) {
        mIsSatelliteProvisionedSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteProvisioned(
                SUB_ID, mIsSatelliteProvisionedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteProvisionedResult(1));
        assertEquals(expectedErrorCode, mQueriedIsSatelliteProvisionedResultCode);
        assertEquals(provisioned, mQueriedIsSatelliteProvisioned);
    }

    private void verifyRequestNtnSignalStrength(
            @NtnSignalStrength.NtnSignalStrengthLevel int signalStrengthLevel,
            int expectedErrorCode) {
        mRequestNtnSignalStrengthSemaphore.drainPermits();
        mSatelliteControllerUT.requestNtnSignalStrength(SUB_ID, mRequestNtnSignalStrengthReceiver);
        processAllMessages();
        assertTrue(waitForRequestNtnSignalStrengthResult(1));
        assertEquals(expectedErrorCode, mQueriedNtnSignalStrengthResultCode);
        assertEquals(signalStrengthLevel, mQueriedNtnSignalStrengthLevel);
    }

    private void sendProvisionedStateChangedEvent(boolean provisioned, Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                26 /* EVENT_SATELLITE_PROVISION_STATE_CHANGED */);
        msg.obj = new AsyncResult(null, provisioned, exception);
        msg.sendToTarget();
    }

    private void sendSatelliteModemStateChangedEvent(int state, Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                28 /* EVENT_SATELLITE_MODEM_STATE_CHANGED */);
        msg.obj = new AsyncResult(null, state, exception);
        msg.sendToTarget();
    }

    private void sendNtnSignalStrengthChangedEvent(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel,
            Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                34 /* EVENT_NTN_SIGNAL_STRENGTH_CHANGED */);
        msg.obj = new AsyncResult(null, new NtnSignalStrength(ntnSignalStrengthLevel),
                exception);
        msg.sendToTarget();
    }

    private void sendCmdStartSendingNtnSignalStrengthChangedEvent(boolean shouldReport) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                35 /* CMD_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING */);
        msg.obj = new AsyncResult(null, shouldReport, null);
        msg.sendToTarget();
    }

    private void sendStartSendingNtnSignalStrengthChangedEvent(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel,
            Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                36 /* EVENT_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING_DONE */);
        msg.obj = new AsyncResult(null, new NtnSignalStrength(ntnSignalStrengthLevel),
                exception);
        msg.sendToTarget();
    }

    private void sendServiceStateChangedEvent() {
        mSatelliteControllerUT.obtainMessage(37 /* EVENT_SERVICE_STATE_CHANGED */).sendToTarget();
    }

    private void sendSatelliteCapabilitiesChangedEvent(SatelliteCapabilities capabilities,
            Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                38 /* EVENT_SATELLITE_CAPABILITIES_CHANGED */);
        msg.obj = new AsyncResult(null, capabilities, exception);
        msg.sendToTarget();
    }

    private void setRadioPower(boolean on) {
        mSimulatedCommands.setRadioPower(on, false, false, null);
    }

    private void verifyRegisterForNtnSignalStrengthChanged(int subId,
            INtnSignalStrengthCallback callback, int expectedError) {
        if (expectedError == SATELLITE_RESULT_SUCCESS) {
            try {
                mSatelliteControllerUT.registerForNtnSignalStrengthChanged(subId, callback);
            } catch (RemoteException ex) {
                throw new AssertionError();
            }
        } else {
            RemoteException ex = assertThrows(RemoteException.class,
                    () -> mSatelliteControllerUT.registerForNtnSignalStrengthChanged(subId,
                            callback));
            assertTrue("The cause is not IllegalStateException",
                    ex.getCause() instanceof IllegalStateException);
        }
    }

    private void provisionSatelliteService() {
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        ICancellationSignal cancelRemote;
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForProvisionSatelliteService(TEST_SATELLITE_TOKEN, testProvisionData,
                SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertNotNull(cancelRemote);
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
    }

    private void deprovisionSatelliteService() {
        mIIntegerConsumerResults.clear();
        setUpResponseForDeprovisionSatelliteService(TEST_SATELLITE_TOKEN, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(SUB_ID,
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
    }

    private static void loge(String message) {
        Rlog.e(TAG, message);
    }

    private static class TestSharedPreferences
            implements SharedPreferences, SharedPreferences.Editor {
        private HashMap<String, Object> mValues = new HashMap<String, Object>();

        public int getValueCount() {
            return mValues.size();
        }

        @Override
        public Editor edit() {
            return this;
        }

        @Override
        public boolean contains(String key) {
            return mValues.containsKey(key);
        }

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<String, Object>(mValues);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            if (mValues.containsKey(key)) {
                return ((Boolean) mValues.get(key)).booleanValue();
            }
            return defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            if (mValues.containsKey(key)) {
                return ((Float) mValues.get(key)).floatValue();
            }
            return defValue;
        }

        @Override
        public int getInt(String key, int defValue) {
            if (mValues.containsKey(key)) {
                return ((Integer) mValues.get(key)).intValue();
            }
            return defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            if (mValues.containsKey(key)) {
                return ((Long) mValues.get(key)).longValue();
            }
            return defValue;
        }

        @Override
        public String getString(String key, String defValue) {
            if (mValues.containsKey(key)) return (String) mValues.get(key);
            else return defValue;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            if (mValues.containsKey(key)) {
                return (Set<String>) mValues.get(key);
            }
            return defValues;
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            mValues.put(key, Boolean.valueOf(value));
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            mValues.put(key, value);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            mValues.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            mValues.put(key, value);
            return this;
        }

        @Override
        public Editor putString(String key, String value) {
            mValues.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            mValues.put(key, values);
            return this;
        }

        @Override
        public Editor remove(String key) {
            mValues.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            mValues.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }

    private static class TestSatelliteController extends SatelliteController {
        public boolean setSettingsKeyForSatelliteModeCalled = false;
        public boolean allRadiosDisabled = true;
        public long elapsedRealtime = 0;
        public int satelliteModeSettingValue = SATELLITE_MODE_ENABLED_FALSE;

        TestSatelliteController(
                Context context, Looper looper, @NonNull FeatureFlags featureFlags) {
            super(context, looper, featureFlags);
            logd("Constructing TestSatelliteController");
        }

        @Override
        protected void initializeSatelliteModeRadios() {
            logd("initializeSatelliteModeRadios");
        }

        @Override
        protected void setSettingsKeyForSatelliteMode(int val) {
            logd("setSettingsKeyForSatelliteMode: val=" + val);
            satelliteModeSettingValue = val;
            setSettingsKeyForSatelliteModeCalled = true;
        }

        @Override
        protected boolean areAllRadiosDisabled() {
            return allRadiosDisabled;
        }

        @Override
        protected int getSupportedNtnRadioTechnology() {
            int ntRadioTechnology = super.getSupportedNtnRadioTechnology();
            logd("getCurrentNtnRadioTechnology: val=" + ntRadioTechnology);
            return ntRadioTechnology;
        }

        @Override
        protected long getElapsedRealtime() {
            return elapsedRealtime;
        }
    }
}
