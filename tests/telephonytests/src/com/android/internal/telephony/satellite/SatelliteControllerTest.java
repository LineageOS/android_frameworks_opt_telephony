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

import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY;
import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY;
import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED;
import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_OPEN;
import static android.hardware.devicestate.feature.flags.Flags.FLAG_DEVICE_STATE_PROPERTY_MIGRATION;
import static android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC;
import static android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_NOTIFICATION_HYSTERESIS_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_EMERGENCY_CALL_TO_SATELLITE_T911_HANDOVER_TIMEOUT_MILLIS_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_NIDD_APN_NAME_STRING;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_P2P_SMS_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_TURN_OFF_SESSION_FOR_EMERGENCY_CALL_BOOL;
import static android.telephony.CarrierConfigManager.SATELLITE_DATA_SUPPORT_ALL;
import static android.telephony.CarrierConfigManager.SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_DATA;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_SMS;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_VOICE;
import static android.telephony.SubscriptionManager.SATELLITE_ENTITLEMENT_STATUS;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_GREAT;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_POOR;
import static android.telephony.satellite.SatelliteManager.KEY_DEMO_MODE_ENABLED;
import static android.telephony.satellite.SatelliteManager.KEY_DEPROVISION_SATELLITE_TOKENS;
import static android.telephony.satellite.SatelliteManager.KEY_EMERGENCY_MODE_ENABLED;
import static android.telephony.satellite.SatelliteManager.KEY_NTN_SIGNAL_STRENGTH;
import static android.telephony.satellite.SatelliteManager.KEY_PROVISION_SATELLITE_TOKENS;
import static android.telephony.satellite.SatelliteManager.KEY_REQUEST_PROVISION_SUBSCRIBER_ID_TOKEN;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_CAPABILITIES;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_ENABLED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_NEXT_VISIBILITY;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_PROVISIONED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.METADATA_SATELLITE_MANUAL_CONNECT_P2P_SUPPORT;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_EMTC_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_PROPRIETARY;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_OFF;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_DISABLE_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_EMERGENCY_CALL_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NO_RESOURCES;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SERVICE_NOT_PROVISIONED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.satellite.SatelliteController.DEFAULT_CARRIER_EMERGENCY_CALL_WAIT_FOR_CONNECTION_TIMEOUT_MILLIS;
import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_DATA_PLAN_METERED;
import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_DATA_PLAN_UNMETERED;
import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_MODE_ENABLED_FALSE;
import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_MODE_ENABLED_TRUE;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceState;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellSignalStrength;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.EarfcnRange;
import android.telephony.satellite.INtnSignalStrengthCallback;
import android.telephony.satellite.ISatelliteCapabilitiesCallback;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.ISatelliteModemStateCallback;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.ISatelliteSupportedStateCallback;
import android.telephony.satellite.ISatelliteTransmissionUpdateCallback;
import android.telephony.satellite.ISelectedNbIotSatelliteSubscriptionCallback;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteInfo;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteManager.SatelliteException;
import android.telephony.satellite.SatelliteModemEnableRequestAttributes;
import android.telephony.satellite.SatellitePosition;
import android.telephony.satellite.SatelliteSubscriberInfo;
import android.telephony.satellite.SatelliteSubscriberProvisionStatus;
import android.telephony.satellite.SatelliteSubscriptionInfo;
import android.telephony.satellite.SystemSelectionSpecifier;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.IntArray;
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
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;
import com.android.internal.telephony.subscription.SubscriptionManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private static final int TEST_WAIT_FOR_CELLULAR_MODEM_OFF_TIMEOUT_MILLIS =
            (int) TimeUnit.SECONDS.toMillis(60);


    private static final String SATELLITE_PLMN = "00103";
    private List<Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener>>
            mCarrierConfigChangedListenerList = new ArrayList<>();

    private TestSatelliteController mSatelliteControllerUT;
    private TestSharedPreferences mSharedPreferences;
    private PersistableBundle mCarrierConfigBundle;
    private ServiceState mServiceState2;

    private SubscriptionInfo testSubscriptionInfo;
    private SubscriptionInfo testSubscriptionInfo2;

    @Mock private SatelliteController mMockSatelliteController;
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
    @Mock private CellSignalStrength mCellSignalStrength;
    @Mock private SatelliteConfig mMockConfig;
    @Mock private DemoSimulator mMockDemoSimulator;
    @Mock private Resources mResources;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private SubscriptionInfo mSubscriptionInfo;
    @Mock private PackageManager mMockPManager;

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
    final int mCarrierId = 0;
    final String mImsi = "1234567890123";
    final String mNiddApn = "testApn";
    final String mMsisdn = "0987654321";
    final String mSubscriberId = mImsi.substring(0, 6) + mMsisdn;
    final String mIccId = "1000000000000001";
    final String mIccId2 = "2000000000000002";
    final String mImsi2 = "2345678901234";
    final String mMsisdn2 = "9876543210";
    final String mSubscriberId2 = mIccId2;

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

    private boolean mRequestIsEmergency = false;
    private ResultReceiver mRequestIsEmergencyReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            logd("requestIsEmergencyReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_EMERGENCY_MODE_ENABLED)) {
                    mRequestIsEmergency = resultData.getBoolean(
                            KEY_EMERGENCY_MODE_ENABLED);
                } else {
                    loge("KEY_EMERGENCY_MODE_ENABLED does not exist.");

                }
            }
        }
    };

    private int mQueriedSystemSelectionChannelUpdatedResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mSystemSelectionChannelUpdatedSemaphore = new Semaphore(0);
    private ResultReceiver mSystemSelectionChannelUpdatedReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedSystemSelectionChannelUpdatedResultCode = resultCode;
            try {
                mSystemSelectionChannelUpdatedSemaphore.release();
            } catch (Exception ex) {
                fail("mSystemSelectionChannelUpdatedReceiver: Got exception in releasing "
                        + "semaphore, ex="
                        + ex);
            }
        }
    };

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
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
        replaceInstance(DemoSimulator.class, "sInstance", null, mMockDemoSimulator);

        doNothing().when(mMockSatelliteController).moveSatelliteToOffStateAndCleanUpResources(
                SATELLITE_RESULT_REQUEST_ABORTED);
        mServiceState2 = mock(ServiceState.class);
        when(mPhone.getServiceState()).thenReturn(mServiceState);
        when(mPhone.getSubId()).thenReturn(SUB_ID);
        when(mPhone.getPhoneId()).thenReturn(0);
        when(mPhone.getSignalStrengthController()).thenReturn(mSignalStrengthController);
        when(mPhone2.getServiceState()).thenReturn(mServiceState2);
        when(mPhone2.getSubId()).thenReturn(SUB_ID1);
        when(mPhone2.getPhoneId()).thenReturn(1);
        when(mPhone2.getSignalStrengthController()).thenReturn(mSignalStrengthController);

        mContextFixture.putStringArrayResource(
                R.array.config_satellite_providers,
                EMPTY_STRING_ARRAY);
        mContextFixture.putIntResource(
                R.integer.config_wait_for_satellite_enabling_response_timeout_millis,
                TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS);
        mContextFixture.putIntResource(
                R.integer.config_satellite_wait_for_cellular_modem_off_timeout_millis,
                TEST_WAIT_FOR_CELLULAR_MODEM_OFF_TIMEOUT_MILLIS);
        mContextFixture.putIntArrayResource(
                R.array.config_foldedDeviceStates,
                new int[0]);
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
        doNothing().when(mMockSatelliteSessionController).cleanUpResource();
        doNothing().when(mMockControllerMetricsStats).onSatelliteEnabled();
        doNothing().when(mMockControllerMetricsStats).reportServiceEnablementSuccessCount();
        doNothing().when(mMockControllerMetricsStats).reportServiceEnablementFailCount();
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setInitializationResult(anyInt());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setSatelliteTechnology(anyInt());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setTerminationResult(anyInt());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setInitializationProcessingTime(anyLong());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setTerminationProcessingTime(anyLong());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setSessionDurationSec(anyInt());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setIsDemoMode(anyBoolean());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setCarrierId(anyInt());
        doReturn(mMockSessionMetricsStats)
                .when(mMockSessionMetricsStats).setIsNtnOnlyCarrier(anyBoolean());
        doNothing().when(mMockSessionMetricsStats).reportSessionMetrics();

        doReturn(mMockProvisionMetricsStats).when(mMockProvisionMetricsStats)
                .setResultCode(anyInt());
        doReturn(mMockProvisionMetricsStats).when(mMockProvisionMetricsStats)
                .setIsProvisionRequest(anyBoolean());
        doReturn(mMockProvisionMetricsStats).when(mMockProvisionMetricsStats)
                .setCarrierId(anyInt());
        doReturn(mMockProvisionMetricsStats).when(mMockProvisionMetricsStats)
                .setIsNtnOnlyCarrier(anyBoolean());
        doNothing().when(mMockProvisionMetricsStats).reportProvisionMetrics();
        doNothing().when(mMockControllerMetricsStats).reportDeprovisionCount(anyInt());
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        doReturn(mSST).when(mPhone).getServiceStateTracker();
        doReturn(mSST).when(mPhone2).getServiceStateTracker();
        doReturn(mServiceState).when(mSST).getServiceState();
        doReturn(Context.NOTIFICATION_SERVICE).when(mContext).getSystemServiceName(
                NotificationManager.class);
        doReturn(mMockNotificationManager).when(mContext).getSystemService(
                Context.NOTIFICATION_SERVICE);
        mSatelliteControllerUT =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
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
        doReturn(mSubscriptionInfo).when(mMockSubscriptionManagerService).getSubscriptionInfo(
                anyInt());
        doReturn("").when(mSubscriptionInfo).getIccId();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        mSatelliteControllerUT = null;
        super.tearDown();
    }

    @Test
    public void testShouldTurnOffCarrierSatelliteForEmergencyCall() throws Exception {
        DatagramController datagramController = mock(DatagramController.class);
        replaceInstance(SatelliteController.class, "mDatagramController",
                mSatelliteControllerUT, datagramController);

        // Verify should turn off satellite
        mCarrierConfigBundle.putBoolean(
                KEY_SATELLITE_ROAMING_TURN_OFF_SESSION_FOR_EMERGENCY_CALL_BOOL, true);
        doReturn(false).when(datagramController).isEmergencyCommunicationEstablished();
        invokeCarrierConfigChanged();
        mSatelliteControllerUT.setSatellitePhone(1);
        processAllMessages();

        assertTrue(mSatelliteControllerUT.shouldTurnOffCarrierSatelliteForEmergencyCall());

        // Verify should NOT turn off satellite
        mCarrierConfigBundle.putBoolean(
                KEY_SATELLITE_ROAMING_TURN_OFF_SESSION_FOR_EMERGENCY_CALL_BOOL, false);
        doReturn(true).when(datagramController).isEmergencyCommunicationEstablished();
        invokeCarrierConfigChanged();
        mSatelliteControllerUT.setSatellitePhone(1);
        processAllMessages();

        assertFalse(mSatelliteControllerUT.shouldTurnOffCarrierSatelliteForEmergencyCall());
    }

    @Test
    public void testRequestTimeForNextSatelliteVisibility() {
        mSatelliteVisibilityTimeSemaphore.drainPermits();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
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
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestTimeForNextSatelliteVisibility(mSatelliteNextVisibilityTime,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        setUpResponseForRequestTimeForNextSatelliteVisibility(mSatelliteNextVisibilityTime,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteVisibilityTimeResultCode);
        assertEquals(mSatelliteNextVisibilityTime, mQueriedSatelliteVisibilityTime);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        setUpNullResponseForRequestTimeForNextSatelliteVisibility(
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
                mSatelliteVisibilityTimeReceiver);
        processAllMessages();
        assertTrue(waitForRequestTimeForNextSatelliteVisibilityResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteVisibilityTimeResultCode);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        setUpNullResponseForRequestTimeForNextSatelliteVisibility(
                SATELLITE_RESULT_INVALID_MODEM_STATE);
        mSatelliteControllerUT.requestTimeForNextSatelliteVisibility(
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
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // Radio is off during TN -> NTN image switch, SatelliteController should not set radio
        // state to OFF
        setRadioPower(false);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // Turn on radio
        setRadioPower(true);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // APM is triggered
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertTrue(mSatelliteControllerUT.isRadioOffRequested());
        assertTrue(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // SatelliteController should set the radio state to OFF
        setRadioPower(false);
        processAllMessages();
        assertFalse(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // Turn on radio
        setRadioPower(true);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // APM is triggered
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertTrue(mSatelliteControllerUT.isRadioOffRequested());
        assertTrue(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // Modem fails to power off radio. APM is disabled
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(true);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // APM is triggered
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertTrue(mSatelliteControllerUT.isRadioOffRequested());
        assertTrue(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // The timer WaitForCellularModemOff time out
        moveTimeForward(TEST_WAIT_FOR_CELLULAR_MODEM_OFF_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // APM is triggered
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertTrue(mSatelliteControllerUT.isRadioOffRequested());
        assertTrue(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());

        // Modem failed to power off the radio
        mSatelliteControllerUT.onPowerOffCellularRadioFailed();
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isRadioOn());
        assertFalse(mSatelliteControllerUT.isRadioOffRequested());
        assertFalse(mSatelliteControllerUT.isWaitForCellularModemOffTimerStarted());
    }

    @Test
    public void testRadioPowerOff() {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        NetworkRegistrationInfo satelliteNri = new NetworkRegistrationInfo.Builder()
                .setIsNonTerrestrialNetwork(true)
                .setAvailableServices(List.of(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                .build();
        mCarrierConfigBundle.putInt(KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT, 1 * 60);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        when(mServiceState.getNetworkRegistrationInfoList()).thenReturn(List.of(satelliteNri));
        when(mServiceState.isUsingNonTerrestrialNetwork()).thenReturn(true);
        sendServiceStateChangedEvent();
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertEquals(List.of(SERVICE_TYPE_DATA),
                mSatelliteControllerUT.getCapabilitiesForCarrierRoamingSatelliteMode(mPhone));

        when(mServiceState.isUsingNonTerrestrialNetwork()).thenReturn(false);
        setRadioPower(false);
        processAllMessages();
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertEquals(new ArrayList<>(),
                mSatelliteControllerUT.getCapabilitiesForCarrierRoamingSatelliteMode(mPhone));
    }

    @Test
    public void testRequestSatelliteEnabled() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.satelliteStateChangeListener()).thenReturn(true);
        mIsSatelliteEnabledSemaphore.drainPermits();

        // Fail to enable satellite when SatelliteController is not fully loaded yet.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        // Fail to enable satellite when the device does not support satellite.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        // Fail to enable satellite when the device is not provisioned yet.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        resetSatelliteControllerUT();
        verify(mMockSatelliteSessionController, times(1)).onSatelliteEnabledStateChanged(eq(false));
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));

        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Fail to enable satellite when the emergency call is in progress
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mTelecomManager).isInEmergencyCall();
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_EMERGENCY_CALL_IN_PROGRESS,
                (long) mIIntegerConsumerResults.get(0));
        doReturn(false).when(mTelecomManager).isInEmergencyCall();

        // Successfully enable satellite
        reset(mTelephonyRegistryManager);
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, true, mIIntegerConsumer);
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        verify(mMockSatelliteSessionController, times(1)).onEmergencyModeChanged(eq(true));
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertTrue(mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled);
        assertEquals(
                SATELLITE_MODE_ENABLED_TRUE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteSessionController, times(1)).onSatelliteEnabledStateChanged(eq(true));
        verify(mMockSatelliteSessionController, times(2)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(2)).setDemoMode(eq(false));
        verify(mMockControllerMetricsStats, times(1)).onSatelliteEnabled();
        verify(mMockControllerMetricsStats, times(1)).reportServiceEnablementSuccessCount();
        verify(mTelephonyRegistryManager).notifySatelliteStateChanged(eq(true));

        // Successfully disable satellite when radio is turned off.
        reset(mTelephonyRegistryManager);
        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockDatagramController);
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        mSatelliteControllerUT.isSatelliteBeingDisabled = true;
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        setRadioPower(false);
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        processAllMessages();
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_OFF, null);
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verify(mMockSatelliteSessionController, times(1)).onEmergencyModeChanged(eq(false));
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertTrue(mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled);
        assertEquals(
                SATELLITE_MODE_ENABLED_FALSE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteSessionController, times(2)).onSatelliteEnabledStateChanged(eq(false));
        verify(mMockSatelliteSessionController, times(2)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(2)).setDemoMode(eq(false));
        verify(mMockControllerMetricsStats, times(1)).onSatelliteDisabled();
        mSatelliteControllerUT.isSatelliteBeingDisabled = false;
        verify(mTelephonyRegistryManager, atLeastOnce()).notifySatelliteStateChanged(eq(false));

        // Fail to enable satellite when radio is off.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        // Radio is not on, can not enable satellite
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));

        setRadioPower(true);
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Fail to enable satellite with an error response from modem when radio is on.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        clearInvocations(mMockPointingAppController);
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false,
                SATELLITE_RESULT_INVALID_MODEM_STATE);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verify(mMockPointingAppController, never()).startPointingUI(anyBoolean(), anyBoolean(),
                anyBoolean());
        assertFalse(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertFalse(mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled);
        verify(mMockControllerMetricsStats, times(1)).reportServiceEnablementFailCount();

        // Successfully enable satellite when radio is on.
        reset(mTelephonyRegistryManager);
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertTrue(mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled);
        assertEquals(SATELLITE_MODE_ENABLED_TRUE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteSessionController, times(1)).onSatelliteEnabledStateChanged(eq(true));
        verify(mMockSatelliteSessionController, times(3)).setDemoMode(eq(false));
        verify(mMockDatagramController, times(3)).setDemoMode(eq(false));
        verify(mMockControllerMetricsStats, times(2)).onSatelliteEnabled();
        verify(mMockControllerMetricsStats, times(2)).reportServiceEnablementSuccessCount();
        verify(mTelephonyRegistryManager).notifySatelliteStateChanged(eq(true));

        // Successfully enable satellite when it is already enabled.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Fail to enable satellite with a different demo mode when it is already enabled.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteEnabled(true, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_ARGUMENTS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Successfully disable satellite.
        reset(mTelephonyRegistryManager);
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verify(mTelephonyRegistryManager, atLeastOnce()).notifySatelliteStateChanged(eq(false));

        // Disable satellite when satellite is already disabled.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Disable satellite with a different demo mode when satellite is already disabled.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteEnabled(false, true, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Send a second request while the first request in progress
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpNoResponseForRequestSatelliteEnabled(true, false, false);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_REQUEST_IN_PROGRESS, (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(false);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        // The enable request should be aborted when satellite modem move to OFF state.
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_REQUEST_ABORTED, (long) mIIntegerConsumerResults.get(0));

        // Successfully enable satellite
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Move to satellite-disabling in progress.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpNoResponseForRequestSatelliteEnabled(false, false, false);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));

        // Disable is in progress. Thus, a new request to enable satellite will be rejected.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        mSatelliteControllerUT.isSatelliteBeingDisabled = true;
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_DISABLE_IN_PROGRESS, (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        resetSatelliteControllerUTToOffAndProvisionedState();
        mSatelliteControllerUT.isSatelliteBeingDisabled = false;

        /**
         * Make areAllRadiosDisabled return false and move mWaitingForRadioDisabled to true, which
         * will lead to no response for requestSatelliteEnabled.
         */
        mSatelliteControllerUT.allRadiosDisabled = false;
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));

        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        // We should receive 2 callbacks for the above 2 requests.
        assertTrue(waitForIIntegerConsumerResult(2));
        // Successful result for disable request
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        // The enable request should be aborted after getting the successful confirmation of the
        // disable request.
        assertEquals(SATELLITE_RESULT_REQUEST_ABORTED, (long) mIIntegerConsumerResults.get(1));

        resetSatelliteControllerUTToOffAndProvisionedState();

        // Repeat the same test as above but with error response from modem for the second request
        mSatelliteControllerUT.allRadiosDisabled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        // No response for the enable request because all radios are not disabled yet
        assertFalse(waitForIIntegerConsumerResult(1));

        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_NO_RESOURCES);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        // We should receive result for the disable request.
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NO_RESOURCES, (long) mIIntegerConsumerResults.get(0));
        mSatelliteControllerUT.allRadiosDisabled = true;

        resetSatelliteControllerUTToOnAndProvisionedState();
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        processAllMessages();
        // Satellite should not be powered off since the feature flag oemEnabledSatelliteFlag is
        // disabled
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Successfully disable satellite.
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Fail to enable satellite when radio is being powered off.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.onSetCellularRadioPowerStateRequested(false);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        // Radio is being powered off, can not enable satellite
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, (long) mIIntegerConsumerResults.get(0));

        // Modem failed to power off
        mSatelliteControllerUT.onPowerOffCellularRadioFailed();

        // Successfully enable satellite when radio is on.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Clean up all previous resources
        processAllFutureMessages();
        mIIntegerConsumerSemaphore.drainPermits();

        // Successfully disable satellite.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Move to satellite-enabling in progress.
        setUpNoResponseForRequestSatelliteEnabled(true, false, false);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));

        // Successfully disable satellite.
        mIIntegerConsumerResults.clear();
        mIIntegerConsumerSemaphore.drainPermits();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(2));
        // Should get success result for the disable request
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        // The enable request should be aborted
        assertEquals(SATELLITE_RESULT_REQUEST_ABORTED, (long) mIIntegerConsumerResults.get(1));
        // All timers waiting for enablement response should be stopped
        assertFalse(mSatelliteControllerUT.isAnyWaitForSatelliteEnablingResponseTimerStarted());
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testGetRequestIsEmergency() {
        mIsSatelliteEnabledSemaphore.drainPermits();
        doReturn(true).when(mFeatureFlags).carrierRoamingNbIotNtn();

        // Successfully enable satellite
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);

        // Set provisioned state
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        // Set response for enabling request
        setUpResponseForRequestSatelliteEnabled(true, false, true/*emergency*/,
                SATELLITE_RESULT_SUCCESS);
        // Request satellite enabling for emergency
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, true /*isEmergency*/,
                mIIntegerConsumer);
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        processAllMessages();

        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Verify satellite enabled for emergency
        assertTrue(mSatelliteControllerUT.getRequestIsEmergency());
        mSatelliteControllerUT.requestIsEmergencyModeEnabled(mRequestIsEmergencyReceiver);
        assertTrue(mRequestIsEmergency);
    }

    @Test
    public void testRequestSatelliteCapabilities() {
        mSatelliteCapabilitiesSemaphore.drainPermits();
        mSatelliteControllerUT.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteCapabilitiesResultCode);

        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedSatelliteCapabilitiesResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSatelliteCapabilities(mSatelliteCapabilities,
                SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
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
        mSatelliteControllerUT.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                mQueriedSatelliteCapabilitiesResultCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpNullResponseForRequestSatelliteCapabilities(SATELLITE_RESULT_INVALID_MODEM_STATE);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_MODEM_STATE, mQueriedSatelliteCapabilitiesResultCode);
    }

    @Test
    public void testStartSatelliteTransmissionUpdates() {
        mIIntegerConsumerSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        setUpResponseForStartSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(mIIntegerConsumer,
                mStartTransmissionUpdateCallback);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        setUpResponseForStartSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(mIIntegerConsumer,
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
        mSatelliteControllerUT.startSatelliteTransmissionUpdates(mIIntegerConsumer,
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
        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        provisionSatelliteService();
        setUpResponseForStopSatelliteTransmissionUpdates(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(mIIntegerConsumer,
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
        mSatelliteControllerUT.stopSatelliteTransmissionUpdates(mIIntegerConsumer,
                mStopTransmissionUpdateCallback);
        verify(mMockPointingAppController, times(2)).unregisterForSatelliteTransmissionUpdates(
                anyInt(), any(), eq(mStopTransmissionUpdateCallback));
        processAllMessages();
        verify(mMockPointingAppController, times(2)).stopSatelliteTransmissionUpdates(
                any(Message.class));
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
    }

    @Test
    public void testRequestIsDemoModeEnabled() {
        mIsDemoModeEnabledSemaphore.drainPermits();
        resetSatelliteControllerUT();
        mSatelliteControllerUT.requestIsDemoModeEnabled(mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsDemoModeEnabled(mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsDemoModeEnabled(mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsDemoModeEnabled(mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED, mQueriedIsDemoModeEnabledResultCode);
        assertFalse(mQueriedIsDemoModeEnabled);

        resetSatelliteControllerUT();
        boolean isDemoModeEnabled = mSatelliteControllerUT.isDemoModeEnabled();
        provisionSatelliteService();
        mSatelliteControllerUT.requestIsDemoModeEnabled(mIsDemoModeEnabledReceiver);
        assertTrue(waitForRequestIsDemoModeEnabledResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedIsDemoModeEnabledResultCode);
        assertEquals(isDemoModeEnabled, mQueriedIsDemoModeEnabled);
    }

    @Test
    public void testIsSatelliteEnabled() {
        logd("testIsSatelliteEnabled: starting");
        assertFalse(mSatelliteControllerUT.isSatelliteEnabledOrBeingEnabled());
        setUpResponseForRequestIsSatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        mIsSatelliteEnabledSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteEnabled(mIsSatelliteEnabledReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteEnabledResult(1));
        assertEquals(
                SATELLITE_RESULT_INVALID_TELEPHONY_STATE, mQueriedIsSatelliteEnabledResultCode);


        logd("testIsSatelliteEnabled: setUpResponseForRequestIsSatelliteSupported");
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        logd("testIsSatelliteEnabled: verifySatelliteSupported");
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestIsSatelliteEnabled(mIsSatelliteEnabledReceiver);
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedIsSatelliteEnabledResultCode);
        assertEquals(mSatelliteControllerUT.isSatelliteEnabledOrBeingEnabled(),
                mQueriedIsSatelliteEnabled);
    }

    @Test
    public void testOnSatelliteServiceConnected() {
        verifySatelliteSupported(false, SATELLITE_RESULT_RADIO_NOT_AVAILABLE);
        verifySatelliteEnabled(false, SATELLITE_RESULT_INVALID_TELEPHONY_STATE);

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);

        setUpResponseForRequestIsSatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.onSatelliteServiceConnected();
        processAllMessages();

        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testRegisterForSatelliteModemStateChanged() {
        ISatelliteModemStateCallback callback = new ISatelliteModemStateCallback.Stub() {
            @Override
            public void onSatelliteModemStateChanged(int state) {
                logd("onSatelliteModemStateChanged: state=" + state);
            }

            @Override
            public void onEmergencyModeChanged(boolean isEmergency) {
                logd("onEmergencyModeChanged: emergency=" + isEmergency);
            }

            @Override
            public void onRegistrationFailure(int causeCode) {
                logd("onRegistrationFailure: causeCode=" + causeCode);
            }

            @Override
            public void onTerrestrialNetworkAvailableChanged(boolean isAvailable) {
                logd("onTerrestrialNetworkAvailableChanged: isAvailable=" + isAvailable);
            }
        };
        int errorCode = mSatelliteControllerUT.registerForSatelliteModemStateChanged(callback);
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, errorCode);
        verify(mMockSatelliteSessionController, never())
                .registerForSatelliteModemStateChanged(callback);

        resetSatelliteControllerUTToSupportedAndProvisionedState();
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);

        errorCode = mSatelliteControllerUT.registerForSatelliteModemStateChanged(callback);
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

            @Override
            public void onEmergencyModeChanged(boolean isEmergency) {
                logd("onEmergencyModeChanged: emergency=" + isEmergency);
            }

            @Override
            public void onRegistrationFailure(int causeCode) {
                logd("onRegistrationFailure: causeCode=" + causeCode);
            }

            @Override
            public void onTerrestrialNetworkAvailableChanged(boolean isAvailable) {
                logd("onTerrestrialNetworkAvailableChanged: isAvailable=" + isAvailable);
            }
        };
        mSatelliteControllerUT.unregisterForModemStateChanged(callback);
        verify(mMockSatelliteSessionController, never())
                .unregisterForSatelliteModemStateChanged(callback);

        resetSatelliteControllerUTToSupportedAndProvisionedState();
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        mSatelliteControllerUT.unregisterForModemStateChanged(callback);
        verify(mMockSatelliteSessionController).unregisterForSatelliteModemStateChanged(callback);
    }

    @Test
    public void testRegisterForSatelliteProvisionStateChanged() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
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

                    @Override
                    public void onSatelliteSubscriptionProvisionStateChanged(
                            List<SatelliteSubscriberProvisionStatus> status) {
                        logd("onSatelliteSubscriptionProvisionStateChanged: " + status);
                    }
                };
        int errorCode = mSatelliteControllerUT.registerForSatelliteProvisionStateChanged(callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForSatelliteProvisionStateChanged(callback);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteProvisionStateChanged"));
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);

        try {
            setSatelliteSubscriberTesting(true);
        } catch (Exception ex) {
            fail("provisionSatelliteService.setSatelliteSubscriberTesting: ex=" + ex);
        }
        doReturn(true).when(mMockSubscriptionManagerService).isSatelliteProvisionedForNonIpDatagram(
                anyInt());

        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        CancellationSignal cancellationSignal = new CancellationSignal();
        ICancellationSignal cancelRemote = null;
        mIIntegerConsumerResults.clear();
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteProvisionStateChanged"));
        mSatelliteControllerUT.unregisterForSatelliteProvisionStateChanged(callback);
        semaphore.drainPermits();
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
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
        when(mMockDatagramController.registerForSatelliteDatagram(anyInt(), eq(callback)))
                .thenReturn(SATELLITE_RESULT_SUCCESS);
        int errorCode = mSatelliteControllerUT.registerForIncomingDatagram(callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);
        verify(mMockDatagramController).registerForSatelliteDatagram(anyInt(), eq(callback));
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
                .unregisterForSatelliteDatagram(anyInt(), eq(callback));
        mSatelliteControllerUT.unregisterForIncomingDatagram(callback);
        verify(mMockDatagramController).unregisterForSatelliteDatagram(anyInt(), eq(callback));
    }

    @Test
    public void testSendSatelliteDatagram() {
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        int[] sosDatagramTypes = {SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                SatelliteManager.DATAGRAM_TYPE_LAST_SOS_MESSAGE_STILL_NEED_HELP,
                SatelliteManager.DATAGRAM_TYPE_LAST_SOS_MESSAGE_NO_HELP_NEEDED};
        for (int datagramType : sosDatagramTypes) {
            mSatelliteControllerUT =
                    new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
            mIIntegerConsumerSemaphore.drainPermits();
            mIIntegerConsumerResults.clear();
            clearInvocations(mMockDatagramController);
            clearInvocations(mMockPointingAppController);

            mSatelliteControllerUT.sendDatagram(datagramType, datagram, true,
                    mIIntegerConsumer);
            processAllMessages();
            assertTrue(waitForIIntegerConsumerResult(1));
            assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                    (long) mIIntegerConsumerResults.get(0));
            verify(mMockDatagramController, never()).sendSatelliteDatagram(anyInt(),
                    eq(datagramType), eq(datagram), eq(true), any());

            mIIntegerConsumerResults.clear();
            setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
            verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
            setProvisionedState(false);
            processAllMessages();
            verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
            mSatelliteControllerUT.sendDatagram(datagramType, datagram, true,
                    mIIntegerConsumer);
            processAllMessages();
            assertTrue(waitForIIntegerConsumerResult(1));
            assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                    (long) mIIntegerConsumerResults.get(0));
            verify(mMockDatagramController, never()).sendSatelliteDatagram(anyInt(),
                    eq(datagramType), eq(datagram), eq(true), any());

            mIIntegerConsumerResults.clear();
            setProvisionedState(true);
            processAllMessages();
            verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
            mSatelliteControllerUT.sendDatagram(datagramType, datagram, true,
                    mIIntegerConsumer);
            processAllMessages();
            assertFalse(waitForIIntegerConsumerResult(1));
            verify(mMockDatagramController, times(1)).sendSatelliteDatagram(anyInt(),
                    eq(datagramType), eq(datagram), eq(true), any());
            verify(mMockPointingAppController, times(1)).startPointingUI(eq(true), anyBoolean(),
                    anyBoolean());
        }
    }

    @Test
    public void testPollPendingSatelliteDatagrams() {
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.pollPendingDatagrams(mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockDatagramController, never()).pollPendingSatelliteDatagrams(anyInt(), any());

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(false);
        processAllMessages();
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.pollPendingDatagrams(mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_NOT_PROVISIONED,
                (long) mIIntegerConsumerResults.get(0));
        verify(mMockDatagramController, never()).pollPendingSatelliteDatagrams(anyInt(), any());

        mIIntegerConsumerResults.clear();
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.pollPendingDatagrams(mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockDatagramController, times(1)).pollPendingSatelliteDatagrams(anyInt(), any());
    }

    @Test
    public void testProvisionSatelliteService() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(false);

        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        CancellationSignal cancellationSignal = new CancellationSignal();
        ICancellationSignal cancelRemote = null;
        mIIntegerConsumerResults.clear();
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
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
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
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
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertNotNull(cancelRemote);
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Send provision request again after the device is successfully provisioned
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        assertNull(cancelRemote);

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_NEXT_SATELLITE_TOKEN, testProvisionData, mIIntegerConsumer);
        cancellationSignal.setRemote(cancelRemote);
        cancellationSignal.cancel();
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
                TEST_NEXT_SATELLITE_TOKEN,
                testProvisionData, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS,
                (long) mIIntegerConsumerResults.get(0));
    }

    @Test
    public void testDeprovisionSatelliteService() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(false);
        mIIntegerConsumerSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.deprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE,
                (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.deprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));

        resetSatelliteControllerUT();
        provisionSatelliteService();
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.deprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(null);
        mIIntegerConsumerResults.clear();
        mSatelliteControllerUT.deprovisionSatelliteService(
                TEST_SATELLITE_TOKEN, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteProvisioned(false, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testSupportedSatelliteServices() throws Exception {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        List<String> satellitePlmnList = mSatelliteControllerUT.getSatellitePlmnsForCarrier(
                SUB_ID);
        assertEquals(EMPTY_STRING_ARRAY.length, satellitePlmnList.size());
        List<Integer> supportedSatelliteServices =
                mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(SUB_ID, "00101");
        assertTrue(supportedSatelliteServices.isEmpty());

        String[] satelliteProviderStrArray = {"00101", "00102"};
        mContextFixture.putStringArrayResource(
                R.array.config_satellite_providers, satelliteProviderStrArray);
        int[] expectedSupportedServices2 = {2};
        int[] expectedSupportedServices3 = {1, 3};
        int[] defaultSupportedServices = {5, 6};
        PersistableBundle carrierSupportedSatelliteServicesPerProvider = new PersistableBundle();
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00102", expectedSupportedServices2);
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00103", expectedSupportedServices3);
        String[] expectedSupportedSatellitePlmns = {"00102", "00103"};
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        mCarrierConfigBundle.putIntArray(
                CarrierConfigManager.KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY,
                defaultSupportedServices);
        TestSatelliteController testSatelliteController =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);

        satellitePlmnList = testSatelliteController.getSatellitePlmnsForCarrier(SUB_ID);
        assertTrue(satellitePlmnList.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00101");
        assertTrue(supportedSatelliteServices.isEmpty());

        // Add entitlement provided PLMNs.
        setEntitlementPlmnList(testSatelliteController, SUB_ID,
                Arrays.asList("00102", "00104", "00105"));
        // Carrier config changed with carrierEnabledSatelliteFlag disabled
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00102");
        assertTrue(supportedSatelliteServices.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00103");
        assertTrue(supportedSatelliteServices.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00104");
        assertTrue(supportedSatelliteServices.isEmpty());
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00105");
        assertTrue(supportedSatelliteServices.isEmpty());

        // Trigger carrier config changed with carrierEnabledSatelliteFlag enabled
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);
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
                mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(SUB_ID, "00102");
        // "00101" should return carrier config assigned value, though it is in allowed list.
        assertTrue(Arrays.equals(expectedSupportedServices2,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        supportedSatelliteServices =
                mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(SUB_ID, "00103");
        assertTrue(Arrays.equals(expectedSupportedServices3,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        // "00104", and "00105" should return default supported service.
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00104");
        assertTrue(Arrays.equals(defaultSupportedServices,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00105");
        assertTrue(Arrays.equals(defaultSupportedServices,
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
        // "00102" and "00103" should return default supported service for SUB_ID.
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00102");
        assertTrue(Arrays.equals(defaultSupportedServices,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00103");
        assertTrue(Arrays.equals(defaultSupportedServices,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        // "00104", and "00105" should return default supported service for SUB_ID.
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00104");
        assertTrue(Arrays.equals(defaultSupportedServices,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID, "00105");
        assertTrue(Arrays.equals(defaultSupportedServices,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID1, "00102");
        assertNotNull(supportedSatelliteServices);
        assertTrue(Arrays.equals(expectedSupportedServices2,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID1, "00103");
        assertTrue(Arrays.equals(expectedSupportedServices3,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        /* "00104", and "00105" should return default supported service. */
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID1, "00104");
        assertTrue(Arrays.equals(defaultSupportedServices,
                supportedSatelliteServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        supportedSatelliteServices =
                testSatelliteController.getSupportedSatelliteServicesForPlmn(SUB_ID1, "00105");
        assertTrue(Arrays.equals(defaultSupportedServices,
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
        satelliteController.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
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
        satelliteController.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
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
        satelliteController.requestSatelliteCapabilities(mSatelliteCapabilitiesReceiver);
        processAllMessages();
        assertTrue(waitForRequestSatelliteCapabilitiesResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteCapabilitiesResultCode);
        assertEquals(mSatelliteCapabilities, mQueriedSatelliteCapabilities);
        assertTrue(
                mQueriedSatelliteCapabilities.getSupportedRadioTechnologies().contains(
                        satelliteController.getSupportedNtnRadioTechnology()));
        assertEquals(mQueriedSatelliteCapabilities.getMaxBytesPerOutgoingDatagram(), 255);
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
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);
        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockDatagramController);
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_UNAVAILABLE, null);
        processAllMessages();
        verify(mMockSatelliteSessionController, times(1)).onSatelliteModemStateChanged(
                eq(SATELLITE_MODEM_STATE_OFF));

        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockDatagramController);
        mSatelliteControllerUT.isSatelliteBeingDisabled = true;
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_NOT_CONNECTED, null);
        processAllMessages();
        verify(mMockSatelliteSessionController, times(1)).onSatelliteModemStateChanged(
                SATELLITE_MODEM_STATE_NOT_CONNECTED);

        clearInvocations(mMockSatelliteSessionController);
        mSatelliteControllerUT.isSatelliteBeingDisabled = false;
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_NOT_CONNECTED, null);
        processAllMessages();
        verify(mMockSatelliteSessionController, never()).onSatelliteModemStateChanged(
                SATELLITE_MODEM_STATE_NOT_CONNECTED);
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
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForNtnSignalStrengthChanged"));
        assertEquals(expectedLevel, signalStrength[0].getLevel());

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

        mSatelliteControllerUT.unregisterForNtnSignalStrengthChanged(callback);
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

        // but it is ignored because satellite is disabled
        setUpResponseForRequestIsSatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        sendCmdStartSendingNtnSignalStrengthChangedEvent(true);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .startSendingNtnSignalStrength(any(Message.class));

        // after satellite is enabled, startSendingNtnSignalStrength() is requested normally
        resetSatelliteControllerUT();
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        provisionSatelliteService();
        setUpResponseForStartSendingNtnSignalStrength(expectedResult);
        setUpResponseForRequestIsSatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        processAllMessages();
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
    public void testCarrierEnabledSatelliteConnectionHysteresisTime() throws Exception {
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        assertFalse(mSatelliteControllerUT
                        .isSatelliteConnectedViaCarrierWithinHysteresisTime().first);

        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mServiceState2.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigBundle.putInt(KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT, 1 * 60);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(mSignalStrength).when(mPhone2).getSignalStrength();
        List<CellSignalStrength> cellSignalStrengthList = new ArrayList<>();
        cellSignalStrengthList.add(mCellSignalStrength);
        doReturn(cellSignalStrengthList).when(mSignalStrength).getCellSignalStrengths();
        processAllMessages();
        mSatelliteControllerUT.elapsedRealtime = 0;
        assertFalse(mSatelliteControllerUT
                        .isSatelliteConnectedViaCarrierWithinHysteresisTime().first);
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone2));

        when(mServiceState.isUsingNonTerrestrialNetwork()).thenReturn(false);
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(false);
        sendServiceStateChangedEvent();
        processAllMessages();
        assertFalse(mSatelliteControllerUT
                        .isSatelliteConnectedViaCarrierWithinHysteresisTime().first);
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone2));
        verify(mPhone, times(1)).notifyCarrierRoamingNtnModeChanged(eq(false));
        verify(mPhone2, times(1)).notifyCarrierRoamingNtnModeChanged(eq(false));
        clearInvocations(mPhone);
        clearInvocations(mPhone2);

        // Last satellite connected time of Phone2 should be 0
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(true);
        sendServiceStateChangedEvent();
        processAllMessages();
        // 2 minutes later and hysteresis timeout is 1 minute
        mSatelliteControllerUT.elapsedRealtime = 2 * 60 * 1000;
        // But Phone2 is connected to NTN right now
        assertTrue(mSatelliteControllerUT
                       .isSatelliteConnectedViaCarrierWithinHysteresisTime().first);
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertTrue(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone2));
        verify(mPhone, times(0)).notifyCarrierRoamingNtnModeChanged(eq(false));
        verify(mPhone2, times(1)).notifyCarrierRoamingNtnModeChanged(eq(true));
        clearInvocations(mPhone);
        clearInvocations(mPhone2);

        // Last satellite disconnected time of Phone2 should be 2 * 60 * 1000
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(false);
        sendServiceStateChangedEvent();
        processAllMessages();
        // Current time (2) - last disconnected time (2) < hysteresis timeout (1)
        assertTrue(mSatelliteControllerUT
                       .isSatelliteConnectedViaCarrierWithinHysteresisTime().first);
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertTrue(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone2));
        verify(mPhone, times(0)).notifyCarrierRoamingNtnModeChanged(eq(false));
        verify(mPhone2, times(0)).notifyCarrierRoamingNtnModeChanged(anyBoolean());
        clearInvocations(mPhone);
        clearInvocations(mPhone2);

        // Current time (4) - last disconnected time (2) > hysteresis timeout (1)
        mSatelliteControllerUT.elapsedRealtime = 4 * 60 * 1000;
        moveTimeForward(2 * 60 * 1000);
        processAllMessages();
        assertFalse(mSatelliteControllerUT
                        .isSatelliteConnectedViaCarrierWithinHysteresisTime().first);
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone));
        assertFalse(mSatelliteControllerUT.isInSatelliteModeForCarrierRoaming(mPhone2));
        verify(mPhone, times(0)).notifyCarrierRoamingNtnModeChanged(eq(false));
        verify(mPhone2, times(1)).notifyCarrierRoamingNtnModeChanged(eq(false));
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

        int errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(callback);
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, errorCode);

        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(callback);
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, errorCode);

        resetSatelliteControllerUT();
        provisionSatelliteService();
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(callback);
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

        mSatelliteControllerUT.unregisterForCapabilitiesChanged(callback);
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

        int errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);

        setUpResponseForRequestIsSatelliteSupported(false,
                SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_NOT_SUPPORTED);
        errorCode = mSatelliteControllerUT.registerForCapabilitiesChanged(callback);
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
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), mIIntegerConsumer);
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
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), mIIntegerConsumer);
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
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        // If the entitlement plmn list, the carrier plmn list, the overlay config plmn list and
        // the barred plmn list are empty, verify not passing to the modem.
        reset(mMockSatelliteModemInterface);
        List<String> entitlementPlmnList = new ArrayList<>();
        List<String> barredPlmnList = new ArrayList<>();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));

        // If the entitlement plmn list and the overlay config plmn list are available and the
        // carrier plmn list and the barred plmn list are empty, verify passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", "00103"}).toList();
        List<String> mergedPlmnList = entitlementPlmnList;
        overlayConfigPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00104"}).toList();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        verifyPassingToModemAfterQueryCompleted(entitlementPlmnList, mergedPlmnList,
                overlayConfigPlmnList, barredPlmnList);

        // If the entitlement plmn list, the overlay config plmn list and the carrier plmn list
        // are available and the barred plmn list is empty, verify passing to the modem.
        reset(mMockSatelliteModemInterface);
        Map<Integer, Map<String, Set<Integer>>>
                satelliteServicesSupportedByCarriers = new HashMap<>();
        List<String> carrierConfigPlmnList = Arrays.stream(new String[]{"00105", "00106"}).toList();
        Map<String, Set<Integer>> plmnAndService = new HashMap<>();
        plmnAndService.put(carrierConfigPlmnList.get(0), new HashSet<>(Arrays.asList(3, 5)));
        plmnAndService.put(carrierConfigPlmnList.get(1), new HashSet<>(Arrays.asList(3)));
        satelliteServicesSupportedByCarriers.put(SUB_ID, plmnAndService);
        replaceInstance(SatelliteController.class, "mSatelliteServicesSupportedByCarriers",
                mSatelliteControllerUT, satelliteServicesSupportedByCarriers);
        verifyPassingToModemAfterQueryCompleted(entitlementPlmnList, mergedPlmnList,
                overlayConfigPlmnList, barredPlmnList);

        // If the entitlement plmn list is empty and the overlay config plmn list and the carrier
        // plmn list are available, verify passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = new ArrayList<>();
        mergedPlmnList = carrierConfigPlmnList;
        verifyPassingToModemAfterQueryCompleted(entitlementPlmnList, mergedPlmnList,
                overlayConfigPlmnList, barredPlmnList);

        // If the entitlement plmn list is empty and the overlay config plmn list, the carrier
        // plmn list and the barred plmn list are available, verify passing to the modem.
        reset(mMockSatelliteModemInterface);
        barredPlmnList = Arrays.stream(new String[]{"00105", "00107"}).toList();
        verifyPassingToModemAfterQueryCompleted(entitlementPlmnList, mergedPlmnList,
                overlayConfigPlmnList, barredPlmnList);

        // If the entitlement plmn list is null and the overlay config plmn list and the carrier
        // plmn list are available, verify passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = null;
        mergedPlmnList = carrierConfigPlmnList;
        verifyPassingToModemAfterQueryCompleted(entitlementPlmnList, mergedPlmnList,
                overlayConfigPlmnList, barredPlmnList);

        // If the entitlement plmn list is invalid, verify not passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", ""}).toList();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));

        // If the entitlement plmn list is invalid, verify not passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", "123456789"}).toList();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));

        // If the entitlement plmn list is invalid, verify not passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", "12"}).toList();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));

        // If the entitlement plmn list is invalid, verify not passing to the modem.
        reset(mMockSatelliteModemInterface);
        entitlementPlmnList = Arrays.stream(new String[]{"00101", "00102", "1234"}).toList();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));
    }

    private void verifyPassingToModemAfterQueryCompleted(List<String> entitlementPlmnList,
            List<String> mergedPlmnList, List<String> overlayConfigPlmnList,
            List<String> barredPlmnList) {
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);

        List<String> plmnListPerCarrier = mSatelliteControllerUT.getSatellitePlmnsForCarrier(
                SUB_ID);
        List<String> allSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                plmnListPerCarrier, overlayConfigPlmnList, barredPlmnList);

        assertEquals(mergedPlmnList, plmnListPerCarrier);
        if (overlayConfigPlmnList.isEmpty()) {
            assertEquals(plmnListPerCarrier, allSatellitePlmnList);
        }
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
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "31016");
        assertEquals(new ArrayList<>(), servicesPerPlmn);

        // Verify whether the carrier config plmn list is returned with conditions below
        // the config data plmn list : empty
        // the carrier config plmn list : exist with services {{2}, {1, 3}, {2}}
        setConfigData(new ArrayList<>());
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00101");
        assertEquals(Arrays.asList(2).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00102");
        assertEquals(Arrays.asList(1, 3).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00104");
        assertEquals(Arrays.asList(2).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());

        // Verify whether the carrier config plmn list is returned with conditions below
        // the config data plmn list : exist with services {{1}, {3}, {5}}
        // the carrier config plmn list : exist with services {{2}, {1, 3}, {2}}
        setConfigData(Arrays.asList("00101", "00102", "31024"));
        setCarrierConfigDataPlmnList(Arrays.asList("00101", "00102", "00104"));
        invokeCarrierConfigChanged();
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00101");
        assertEquals(Arrays.asList(1).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00102");
        assertEquals(Arrays.asList(3).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00104");
        assertEquals(new ArrayList<>(), servicesPerPlmn.stream().sorted().toList());
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "31024");
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

    private void setEntitlementPlmnList(SatelliteController targetClass, int subId,
            List<String> plmnList) throws Exception {
        SparseArray<List<String>> entitlementPlmnListPerCarrier = new SparseArray<>();
        if (!plmnList.isEmpty()) {
            entitlementPlmnListPerCarrier.clear();
            entitlementPlmnListPerCarrier.put(subId, plmnList);
        }
        replaceInstance(SatelliteController.class, "mEntitlementPlmnListPerCarrier",
                targetClass, entitlementPlmnListPerCarrier);
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
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
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
                new ArrayList<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);

        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID));
        assertEquals(false, satelliteEnabledPerCarrier.get(SUB_ID1));

        // Change SUB_ID1's EntitlementStatus to true
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID1, true, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), mIIntegerConsumer);

        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID));
        assertEquals(true, satelliteEnabledPerCarrier.get(SUB_ID1));

        // Change SUB_ID's EntitlementStatus to false
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), mIIntegerConsumer);

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

        // If the Satellite entitlement plmn list read from the DB is empty list and carrier
        // config plmn list also is empty , check whether an empty list is returned when calling
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
    public void testHandleEventServiceStateChanged() {
        mContextFixture.putBooleanResource(
            R.bool.config_satellite_should_notify_availability, true);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mCarrierConfigBundle.putInt(KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT,
                CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC);
        invokeCarrierConfigChanged();

        // Do nothing when the satellite is not connected
        doReturn(false).when(mServiceState).isUsingNonTerrestrialNetwork();
        sendServiceStateChangedEvent();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        processAllMessages();
        assertFalse(mSharedPreferences.getBoolean(SATELLITE_SYSTEM_NOTIFICATION_DONE_KEY, false));
        verify(mMockNotificationManager, never()).notifyAsUser(anyString(), anyInt(), any(), any());

        // Check sending a system notification when the satellite is connected
        doReturn(true).when(mServiceState).isUsingNonTerrestrialNetwork();
        sendServiceStateChangedEvent();
        processAllMessages();
        verify(mMockNotificationManager, times(1)).notifyAsUser(anyString(), anyInt(), any(),
                any());
        // Just by showing notification we do not update the pref file , only once user interact
        // only we will update the pref value to true.
        assertFalse(mSharedPreferences.getBoolean(SATELLITE_SYSTEM_NOTIFICATION_DONE_KEY, false));

        // Check don't display again after displayed already a system notification.
        sendServiceStateChangedEvent();
        processAllMessages();
        verify(mMockNotificationManager, times(2)).notifyAsUser(anyString(), anyInt(), any(),
                any());
    }

    @Test
    public void testRequestSatelliteEnabled_timeout() {
        mIsSatelliteEnabledSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // Successfully disable satellite
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        // Time out to enable satellite
        ArgumentCaptor<SatelliteModemEnableRequestAttributes> enableSatelliteRequest =
                ArgumentCaptor.forClass(SatelliteModemEnableRequestAttributes.class);
        ArgumentCaptor<Message> enableSatelliteResponse = ArgumentCaptor.forClass(Message.class);
        mIIntegerConsumerResults.clear();
        setUpNoResponseForRequestSatelliteEnabled(true, false, false);
        clearInvocations(mMockSatelliteModemInterface);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockSatelliteModemInterface).requestSatelliteEnabled(
                enableSatelliteRequest.capture(),
                enableSatelliteResponse.capture());
        SatelliteModemEnableRequestAttributes request = enableSatelliteRequest.getValue();
        assertTrue(request.isEnabled());
        assertFalse(request.isDemoMode());
        assertFalse(request.isEmergencyMode());

        clearInvocations(mMockSatelliteModemInterface);
        moveTimeForward(TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_MODEM_TIMEOUT, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));
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
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Time out to disable satellite
        ArgumentCaptor<Message> disableSatelliteResponse = ArgumentCaptor.forClass(Message.class);
        mIIntegerConsumerResults.clear();
        clearInvocations(mMockSatelliteModemInterface);
        setUpNoResponseForRequestSatelliteEnabled(false, false, false);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertFalse(waitForIIntegerConsumerResult(1));
        verify(mMockSatelliteModemInterface).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class),
                disableSatelliteResponse.capture());

        clearInvocations(mMockSatelliteModemInterface);
        moveTimeForward(TEST_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_MODEM_TIMEOUT, (long) mIIntegerConsumerResults.get(0));
        verify(mMockSatelliteModemInterface, never()).requestSatelliteEnabled(
                any(SatelliteModemEnableRequestAttributes.class), any(Message.class));
        // Satellite should state at enabled state since satellite disable request failed
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Send the response for the above request to disable satellite. SatelliteController should
        // ignore the event
        response = disableSatelliteResponse.getValue();
        AsyncResult.forMessage(response, null, null);
        response.sendToTarget();
        processAllMessages();
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testUpdateNtnSignalStrentghReportWithFeatureFlagEnabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        mIsSatelliteEnabledSemaphore.drainPermits();
        mIIntegerConsumerResults.clear();
        resetSatelliteControllerUT();

        // Successfully provisioned
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(true);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);

        // startSendingNtnSignalStrength should be invoked when satellite is enabled
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertTrue(mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled);
        assertEquals(
                SATELLITE_MODE_ENABLED_TRUE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteModemInterface, times(1)).startSendingNtnSignalStrength(
                any(Message.class));

        // Ignore request ntn signal strength for redundant enable request
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        verify(mMockSatelliteModemInterface, never()).startSendingNtnSignalStrength(
                any(Message.class));

        // stopSendingNtnSignalStrength should be invoked when satellite is successfully off.
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verify(mMockSatelliteModemInterface, times(1)).stopSendingNtnSignalStrength(
                any(Message.class));

        // Ignore redundant request for stop reporting ntn signal strength.
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(false, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
        verify(mMockSatelliteModemInterface, never()).stopSendingNtnSignalStrength(
                any(Message.class));

        // startSendingNtnSignalStrength is invoked when satellite is enabled again.
        mIIntegerConsumerResults.clear();
        reset(mMockSatelliteModemInterface);
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled = false;
        mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled = false;
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);
        assertTrue(mSatelliteControllerUT.setSettingsKeyForSatelliteModeCalled);
        assertTrue(mSatelliteControllerUT.setSettingsKeyToAllowDeviceRotationCalled);
        assertEquals(
                SATELLITE_MODE_ENABLED_TRUE, mSatelliteControllerUT.satelliteModeSettingValue);
        verify(mMockSatelliteModemInterface, times(1)).startSendingNtnSignalStrength(
                any(Message.class));
    }

    @Test
    public void testRegisterForSatelliteSupportedStateChanged_WithFeatureFlagEnabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        Semaphore semaphore = new Semaphore(0);
        final boolean[] isSupported  = new boolean[1];
        ISatelliteSupportedStateCallback callback =
                new ISatelliteSupportedStateCallback.Stub() {
                    @Override
                    public void onSatelliteSupportedStateChanged(boolean supported) {
                        logd("onSatelliteSupportedStateChanged: supported=" + supported);
                        isSupported[0] = supported;
                        try {
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSatelliteSupportedStateChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };

        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        int errorCode = mSatelliteControllerUT.registerForSatelliteSupportedStateChanged(callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);

        sendSatelliteSupportedStateChangedEvent(true, null);
        processAllMessages();
        // Verify redundant report is ignored
        assertFalse(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);

        // Verify updated state is reported
        sendSatelliteSupportedStateChangedEvent(false, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
        assertEquals(false, isSupported[0]);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);

        // Verify redundant report is ignored
        sendSatelliteSupportedStateChangedEvent(false, null);
        processAllMessages();
        assertFalse(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);

        // Verify updated state is reported
        sendSatelliteSupportedStateChangedEvent(true, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
        assertEquals(true, isSupported[0]);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);

        // Successfully enable satellite
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
        processAllMessages();
        assertTrue(waitForIIntegerConsumerResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, (long) mIIntegerConsumerResults.get(0));
        verifySatelliteEnabled(true, SATELLITE_RESULT_SUCCESS);

        // Send satellite is not supported state from modem to disable satellite
        setUpResponseForRequestSatelliteEnabled(false, false, false, SATELLITE_RESULT_SUCCESS);
        sendSatelliteSupportedStateChangedEvent(false, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
        assertEquals(false, isSupported[0]);

        // It is needed to set satellite as support to check whether satellite is enabled or not
        sendSatelliteSupportedStateChangedEvent(true, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
        assertEquals(true, isSupported[0]);
        // Verify satellite was disabled
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);

        mSatelliteControllerUT.unregisterForSatelliteSupportedStateChanged(callback);
        sendSatelliteSupportedStateChangedEvent(true, null);
        processAllMessages();
        assertFalse(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSupportedStateChanged"));
    }

    @Test
    public void testRegisterForSatelliteSupportedStateChanged_WithFeatureFlagDisabled() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);

        Semaphore semaphore = new Semaphore(0);
        ISatelliteSupportedStateCallback callback =
                new ISatelliteSupportedStateCallback.Stub() {
                    @Override
                    public void onSatelliteSupportedStateChanged(boolean supported) {
                        logd("onSatelliteSupportedStateChanged: supported=" + supported);
                        try {
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSatelliteSupportedStateChanged: Got exception in releasing "
                                    + "semaphore, ex=" + ex);
                        }
                    }
                };
        int errorCode = mSatelliteControllerUT.registerForSatelliteSupportedStateChanged(
                callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);
    }

    @Test
    public void testRegisterForSelectedNbIotSatelliteSubscriptionChanged_WithFeatureFlagEnabled() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        Semaphore semaphore = new Semaphore(0);
        final int[] selectedSubIds = new int[1];
        ISelectedNbIotSatelliteSubscriptionCallback callback =
                new ISelectedNbIotSatelliteSubscriptionCallback.Stub() {
                    @Override
                    public void onSelectedNbIotSatelliteSubscriptionChanged(int selectedSubId) {
                        logd("onSelectedNbIotSatelliteSubscriptionChanged: selectedSubId="
                                + selectedSubId);
                        try {
                            selectedSubIds[0] = selectedSubId;
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSelectedNbIotSatelliteSubscriptionChanged: Got exception in "
                                    + "releasing semaphore, ex=" + ex);
                        }
                    }
                };

        int errorCode = mSatelliteControllerUT.registerForSelectedNbIotSatelliteSubscriptionChanged(
                callback);
        assertEquals(SATELLITE_RESULT_INVALID_TELEPHONY_STATE, errorCode);

        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForSelectedNbIotSatelliteSubscriptionChanged(
                callback);
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, errorCode);

        // Register the callback and verify that the event is reported.
        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteProvisioned(true,SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        errorCode = mSatelliteControllerUT.registerForSelectedNbIotSatelliteSubscriptionChanged(
                callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);
        int expectedSubId = 1;
        sendSelectedNbIotSatelliteSubscriptionChangedEvent(expectedSubId, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSelectedNbIotSatelliteSubscriptionChanged"));
        assertEquals(expectedSubId, selectedSubIds[0]);

        // Unregister the callback and verify that the event is not reported.
        mSatelliteControllerUT.unregisterForSelectedNbIotSatelliteSubscriptionChanged(callback);
        sendSelectedNbIotSatelliteSubscriptionChangedEvent(2, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 0, "testRegisterForSelectedNbIotSatelliteSubscriptionChanged"));
    }

    @Test
    public void testRegisterForSelectedNbIotSatelliteSubscriptionChanged_WithFeatureFlagDisabled() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(false);

        Semaphore semaphore = new Semaphore(0);
        final int[] selectedSubIds = new int[1];
        ISelectedNbIotSatelliteSubscriptionCallback callback =
                new ISelectedNbIotSatelliteSubscriptionCallback.Stub() {
                    @Override
                    public void onSelectedNbIotSatelliteSubscriptionChanged(int selectedSubId) {
                        logd("onSelectedNbIotSatelliteSubscriptionChanged: selectedSubId="
                                + selectedSubId);
                        try {
                            selectedSubIds[0] = selectedSubId;
                            semaphore.release();
                        } catch (Exception ex) {
                            loge("onSelectedNbIotSatelliteSubscriptionChanged: Got exception in "
                                    + "releasing semaphore, ex=" + ex);
                        }
                    }
                };

        int errorCode = mSatelliteControllerUT.registerForSelectedNbIotSatelliteSubscriptionChanged(
                callback);
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, errorCode);

        // Verify that the event is not reported.
        sendSelectedNbIotSatelliteSubscriptionChangedEvent(1, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 0, "testRegisterForSelectedNbIotSatelliteSubscriptionChanged"));


    }

    @Test
    public void testIsSatelliteEmergencyMessagingSupportedViaCarrier() {
        // Carrier-enabled flag is off
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        assertFalse(mSatelliteControllerUT.isSatelliteEmergencyMessagingSupportedViaCarrier());

        // Carrier-enabled flag is on and satellite attach is not supported
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        assertFalse(mSatelliteControllerUT.isSatelliteEmergencyMessagingSupportedViaCarrier());

        // Trigger carrier config changed to enable satellite attach
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        assertFalse(mSatelliteControllerUT.isSatelliteEmergencyMessagingSupportedViaCarrier());

        // Trigger carrier config changed to enable satellite attach & emergency messaging
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isSatelliteEmergencyMessagingSupportedViaCarrier());
    }

    @Test
    public void testGetCarrierEmergencyCallWaitForConnectionTimeoutMillis() {
        // Carrier-enabled flag is off
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(false);
        assertEquals(DEFAULT_CARRIER_EMERGENCY_CALL_WAIT_FOR_CONNECTION_TIMEOUT_MILLIS,
                mSatelliteControllerUT.getCarrierEmergencyCallWaitForConnectionTimeoutMillis());

        // Carrier-enabled flag is on
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        assertEquals(DEFAULT_CARRIER_EMERGENCY_CALL_WAIT_FOR_CONNECTION_TIMEOUT_MILLIS,
                mSatelliteControllerUT.getCarrierEmergencyCallWaitForConnectionTimeoutMillis());

        // Trigger carrier config changed to enable satellite attach
        int timeoutMillisForCarrier1 = 1000;
        PersistableBundle carrierConfigBundle1 = new PersistableBundle();
        carrierConfigBundle1.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        carrierConfigBundle1.putBoolean(
                CarrierConfigManager.KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL, true);
        carrierConfigBundle1.putInt(
                KEY_EMERGENCY_CALL_TO_SATELLITE_T911_HANDOVER_TIMEOUT_MILLIS_INT,
                timeoutMillisForCarrier1);
        doReturn(carrierConfigBundle1)
                .when(mCarrierConfigManager).getConfigForSubId(eq(SUB_ID), anyVararg());

        int timeoutMillisForCarrier2 = 2000;
        PersistableBundle carrierConfigBundle2 = new PersistableBundle();
        carrierConfigBundle2.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        carrierConfigBundle2.putBoolean(
                CarrierConfigManager.KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL, true);
        carrierConfigBundle2.putInt(
                KEY_EMERGENCY_CALL_TO_SATELLITE_T911_HANDOVER_TIMEOUT_MILLIS_INT,
                timeoutMillisForCarrier2);
        doReturn(carrierConfigBundle2)
                .when(mCarrierConfigManager).getConfigForSubId(eq(SUB_ID1), anyVararg());

        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();

        // Both phones are not in satellite mode for carrier roaming, and thus the max timeout
        // duration - timeoutMillisForCarrier2 - is used
        assertEquals(timeoutMillisForCarrier2,
                mSatelliteControllerUT.getCarrierEmergencyCallWaitForConnectionTimeoutMillis());

        // Phone 1 is in satellite mode for carrier roaming
        when(mServiceState.isUsingNonTerrestrialNetwork()).thenReturn(true);
        assertEquals(timeoutMillisForCarrier1,
                mSatelliteControllerUT.getCarrierEmergencyCallWaitForConnectionTimeoutMillis());

        // Both phones are in satellite mode for carrier roaming. The timeout duration of the first
        // phone will be selected
        when(mServiceState2.isUsingNonTerrestrialNetwork()).thenReturn(true);
        assertEquals(timeoutMillisForCarrier1,
                mSatelliteControllerUT.getCarrierEmergencyCallWaitForConnectionTimeoutMillis());
    }

    @Test
    public void testIsCarrierRoamingNtnEligible() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(false);
        assertFalse(mSatelliteControllerUT.isCarrierRoamingNtnEligible(null));

        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mServiceState2.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        mSatelliteControllerUT.mIsApplicationSupportsP2P = true;
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putInt(KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT, 1);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ROAMING_P2P_SMS_SUPPORTED_BOOL, true);
        int[] supportedServices2 = {2};
        int[] supportedServices3 = {1, 3};
        PersistableBundle carrierSupportedSatelliteServicesPerProvider = new PersistableBundle();
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00102", supportedServices2);
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00103", supportedServices3);
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        mSatelliteControllerUT.setSatellitePhone(1);
        mSatelliteControllerUT.setSelectedSatelliteSubId(SUB_ID);
        mSatelliteControllerUT.isSatelliteProvisioned = true;
        mSatelliteControllerUT.setIsSatelliteAllowedState(true);
        processAllMessages();

        assertTrue(mSatelliteControllerUT.isCarrierRoamingNtnEligible(mPhone));

        when(mServiceState.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        when(mServiceState2.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        processAllMessages();
        assertFalse(mSatelliteControllerUT.isCarrierRoamingNtnEligible(mPhone));

        when(mServiceState.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        when(mServiceState2.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        mSatelliteControllerUT.overrideCarrierRoamingNtnEligibilityChanged(true, false);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isCarrierRoamingNtnEligible(mPhone));
    }

    @Test
    public void testOverrideCarrierRoamingNtNEligibilityChange() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mSatelliteControllerUT.overrideCarrierRoamingNtnEligibilityChanged(true, false);
        verify(mPhone, times(1)).notifyCarrierRoamingNtnEligibleStateChanged(eq(true));
        clearInvocations(mPhone);

        mSatelliteControllerUT.overrideCarrierRoamingNtnEligibilityChanged(false, false);
        verify(mPhone, times(1)).notifyCarrierRoamingNtnEligibleStateChanged(eq(false));
        clearInvocations(mPhone);

        mSatelliteControllerUT.overrideCarrierRoamingNtnEligibilityChanged(false, true);
        verify(mPhone, times(0)).notifyCarrierRoamingNtnEligibleStateChanged(eq(true));
        clearInvocations(mPhone);
    }

    @Test
    public void testNotifyNtnEligibilityHysteresisTimedOut() {
        mContextFixture.putBooleanResource(
            R.bool.config_satellite_should_notify_availability, true);
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        when(mServiceState2.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        mSatelliteControllerUT.mIsApplicationSupportsP2P = true;
        mSatelliteControllerUT.setIsSatelliteSupported(true);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putInt(KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT,
            CARRIER_ROAMING_NTN_CONNECT_MANUAL);
        mCarrierConfigBundle.putInt(
                KEY_CARRIER_SUPPORTED_SATELLITE_NOTIFICATION_HYSTERESIS_SEC_INT, 1 * 60);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ROAMING_P2P_SMS_SUPPORTED_BOOL, true);
        int[] supportedServices2 = {2};
        int[] supportedServices3 = {1, 3};
        PersistableBundle carrierSupportedSatelliteServicesPerProvider = new PersistableBundle();
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00102", supportedServices2);
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                "00103", supportedServices3);
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        mSatelliteControllerUT.setSatellitePhone(1);
        mSatelliteControllerUT.setSelectedSatelliteSubId(SUB_ID);
        mSatelliteControllerUT.isSatelliteProvisioned = true;
        mSatelliteControllerUT.isSatelliteAllowedCallback = null;
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.setIsSatelliteAllowedState(true);
        processAllMessages();
        mSatelliteControllerUT.elapsedRealtime = 0;
        assertTrue(mSatelliteControllerUT.isCarrierRoamingNtnEligible(mPhone));
        verify(mPhone, times(0)).notifyCarrierRoamingNtnEligibleStateChanged(eq(true));
        verify(mPhone2, times(0)).notifyCarrierRoamingNtnEligibleStateChanged(anyBoolean());
        clearInvocations(mPhone);

        // 2 minutes later and hysteresis timeout is 1 minute
        mSatelliteControllerUT.elapsedRealtime = 2 * 60 * 1000;
        moveTimeForward(2 * 60 * 1000);
        processAllMessages();
        assertTrue(mSatelliteControllerUT.isCarrierRoamingNtnEligible(mPhone));
        verify(mPhone, times(1)).notifyCarrierRoamingNtnEligibleStateChanged(eq(true));
        verify(mPhone2, times(0)).notifyCarrierRoamingNtnEligibleStateChanged(anyBoolean());
        verify(mMockNotificationManager, times(1)).notifyAsUser(anyString(), anyInt(), any(),
                any());
        clearInvocations(mPhone);

        when(mServiceState.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        sendServiceStateChangedEvent();
        processAllMessages();
        assertFalse(mSatelliteControllerUT.isCarrierRoamingNtnEligible(mPhone));
        verify(mPhone, times(1)).notifyCarrierRoamingNtnEligibleStateChanged(eq(false));
        verify(mPhone2, times(0)).notifyCarrierRoamingNtnEligibleStateChanged(anyBoolean());
    }

    @Test
    public void testNotifyCarrierRoamingNtnSignalStrengthChanged() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        sendSignalStrengthChangedEvent(mPhone.getPhoneId());
        processAllMessages();
        ArgumentCaptor<NtnSignalStrength> captor = ArgumentCaptor.forClass(NtnSignalStrength.class);
        verify(mPhone, times(1)).notifyCarrierRoamingNtnSignalStrengthChanged(
                captor.capture());
        NtnSignalStrength actualSignalStrength = captor.getValue();
        assertEquals(NTN_SIGNAL_STRENGTH_NONE, actualSignalStrength.getLevel());
        clearInvocations(mPhone);

        when(mSignalStrength.getLevel()).thenReturn(SignalStrength.SIGNAL_STRENGTH_GOOD);
        when(mPhone.getSignalStrength()).thenReturn(mSignalStrength);
        mCarrierConfigBundle.putInt(KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT, 1 * 60);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        processAllMessages();
        when(mServiceState.isUsingNonTerrestrialNetwork()).thenReturn(true);
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        sendServiceStateChangedEvent();
        processAllMessages();
        captor = ArgumentCaptor.forClass(NtnSignalStrength.class);
        verify(mPhone, times(1)).notifyCarrierRoamingNtnSignalStrengthChanged(
                captor.capture());
        actualSignalStrength = captor.getValue();
        assertEquals(NTN_SIGNAL_STRENGTH_GOOD, actualSignalStrength.getLevel());
        clearInvocations(mPhone);
    }

    @Test
    public void testGetWwanIsInService() {
        when(mServiceState.getNetworkRegistrationInfoListForTransportType(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(new ArrayList<>());
        assertFalse(mSatelliteControllerUT.getWwanIsInService(mServiceState));

        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        when(mServiceState.getNetworkRegistrationInfoListForTransportType(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(List.of(nri));
        assertTrue(mSatelliteControllerUT.getWwanIsInService(mServiceState));

        nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        when(mServiceState.getNetworkRegistrationInfoListForTransportType(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(List.of(nri));
        assertTrue(mSatelliteControllerUT.getWwanIsInService(mServiceState));

        nri = new NetworkRegistrationInfo.Builder().setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .build();
        when(mServiceState.getNetworkRegistrationInfoListForTransportType(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(List.of(nri));
        assertFalse(mSatelliteControllerUT.getWwanIsInService(mServiceState));
    }

    @Test
    public void testRegistrationFailureCallback() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        Semaphore semaphore = new Semaphore(0);
        final int[] resultErrorCode = new int[1];
        ISatelliteModemStateCallback callback = new ISatelliteModemStateCallback.Stub() {
            @Override
            public void onSatelliteModemStateChanged(int state) {
                logd("onSatelliteModemStateChanged: state=" + state);
            }

            @Override
            public void onEmergencyModeChanged(boolean isEmergency) {
                logd("onEmergencyModeChanged: emergency=" + isEmergency);
            }

            @Override
            public void onRegistrationFailure(int causeCode) {
                logd("onRegistrationFailure: causeCode=" + causeCode);
                resultErrorCode[0] = causeCode;
                semaphore.release();
            }

            @Override
            public void onTerrestrialNetworkAvailableChanged(boolean isAvailable) {
                logd("onTerrestrialNetworkAvailableChanged: isAvailable=" + isAvailable);
            }
        };
        resetSatelliteControllerUTToSupportedAndProvisionedState();
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);

        int RegisterErrorCode = mSatelliteControllerUT.registerForSatelliteModemStateChanged(
                callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, RegisterErrorCode);
        verify(mMockSatelliteSessionController).registerForSatelliteModemStateChanged(callback);

        int expectedErrorCode = 100;
        mIIntegerConsumerResults.clear();
        sendSatelliteRegistrationFailureEvent(100, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegistrationFailureCallback"));
        assertEquals(expectedErrorCode, resultErrorCode[0]);
    }

    @RequiresFlagsDisabled(FLAG_DEVICE_STATE_PROPERTY_MIGRATION)
    @Test
    public void testDetermineIsFoldable_overlayConfigurationValues() {
        // isFoldable should return false with the base configuration.
        assertFalse(mSatelliteControllerUT.isFoldable(mContext,
                mSatelliteControllerUT.getSupportedDeviceStates()));

        mContextFixture.putIntArrayResource(R.array.config_foldedDeviceStates, new int[2]);
        assertTrue(mSatelliteControllerUT.isFoldable(mContext,
                mSatelliteControllerUT.getSupportedDeviceStates()));
    }

    @RequiresFlagsEnabled(FLAG_DEVICE_STATE_PROPERTY_MIGRATION)
    @Test
    public void testDetermineIsFoldable_deviceStateManager() {
        // isFoldable should return false with the base configuration.
        assertFalse(mSatelliteControllerUT.isFoldable(mContext,
                mSatelliteControllerUT.getSupportedDeviceStates()));

        DeviceState foldedDeviceState = new DeviceState(new DeviceState.Configuration.Builder(
                0 /* identifier */, "FOLDED")
                .setSystemProperties(Set.of(PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY))
                .setPhysicalProperties(
                        Set.of(PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED))
                .build());
        DeviceState unfoldedDeviceState = new DeviceState(new DeviceState.Configuration.Builder(
                1 /* identifier */, "UNFOLDED")
                .setSystemProperties(Set.of(PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY))
                .setPhysicalProperties(
                        Set.of(PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_OPEN))
                .build());
        List<DeviceState> foldableDeviceStateList = List.of(foldedDeviceState, unfoldedDeviceState);
        assertTrue(mSatelliteControllerUT.isFoldable(mContext, foldableDeviceStateList));
    }

    @Test
    public void testTerrestrialNetworkAvailableChangedCallback() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        Semaphore semaphore = new Semaphore(0);
        final int[] receivedScanResult = new int[1];
        ISatelliteModemStateCallback callback = new ISatelliteModemStateCallback.Stub() {
            @Override
            public void onSatelliteModemStateChanged(int state) {
                logd("onSatelliteModemStateChanged: state=" + state);
            }

            @Override
            public void onEmergencyModeChanged(boolean isEmergency) {
                logd("onEmergencyModeChanged: emergency=" + isEmergency);
            }

            @Override
            public void onRegistrationFailure(int causeCode) {
                logd("onRegistrationFailure: causeCode=" + causeCode);
            }

            @Override
            public void onTerrestrialNetworkAvailableChanged(boolean isAvailable) {
                logd("onTerrestrialNetworkAvailableChanged: isAvailable=" + isAvailable);
                receivedScanResult[0] = isAvailable ? 1 : 0;
                semaphore.release();
            }
        };
        resetSatelliteControllerUTToSupportedAndProvisionedState();
        mSatelliteControllerUT.setSatelliteSessionController(mMockSatelliteSessionController);

        int RegisterErrorCode = mSatelliteControllerUT.registerForSatelliteModemStateChanged(
                callback);
        assertEquals(SATELLITE_RESULT_SUCCESS, RegisterErrorCode);
        verify(mMockSatelliteSessionController).registerForSatelliteModemStateChanged(callback);

        int expectedErrorCode = 1;
        mIIntegerConsumerResults.clear();
        sendTerrestrialNetworkAvailableChangedEvent(true, null);
        processAllMessages();
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegistrationFailureCallback"));
        assertEquals(expectedErrorCode, receivedScanResult[0]);
    }

    private boolean mProvisionState = false;
    private int mProvisionSateResultCode = -1;
    private Semaphore mProvisionSateSemaphore = new Semaphore(0);
    private ResultReceiver mProvisionSatelliteReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mProvisionSateResultCode = resultCode;
            logd("mProvisionSatelliteReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_PROVISION_SATELLITE_TOKENS)) {
                    mProvisionState = resultData.getBoolean(KEY_PROVISION_SATELLITE_TOKENS);
                    logd("mProvisionSatelliteReceiver: mProvisionState=" + mProvisionState);
                } else {
                    loge("KEY_PROVISION_SATELLITE_TOKENS does not exist.");
                    mProvisionState = false;
                }
            } else {
                mProvisionState = false;
            }
            try {
                mProvisionSateSemaphore.release();
            } catch (Exception ex) {
                loge("mProvisionSatelliteReceiver: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private List<SatelliteSubscriberProvisionStatus>
            mRequestSatelliteSubscriberProvisionStatusResultList = new ArrayList<>();
    private int mRequestSatelliteSubscriberProvisionStatusResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mRequestSatelliteSubscriberProvisionStatusSemaphore = new Semaphore(0);
    private ResultReceiver mRequestSatelliteSubscriberProvisionStatusReceiver = new ResultReceiver(
            null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mRequestSatelliteSubscriberProvisionStatusResultCode = resultCode;
            logd("mRequestSatelliteSubscriberProvisionStatusReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_REQUEST_PROVISION_SUBSCRIBER_ID_TOKEN)) {
                    mRequestSatelliteSubscriberProvisionStatusResultList =
                            resultData.getParcelableArrayList(
                                    KEY_REQUEST_PROVISION_SUBSCRIBER_ID_TOKEN,
                                    SatelliteSubscriberProvisionStatus.class);
                } else {
                    loge("KEY_REQUEST_PROVISION_SUBSCRIBER_ID_TOKEN does not exist.");
                    mRequestSatelliteSubscriberProvisionStatusResultList = new ArrayList<>();
                }
            } else {
                mRequestSatelliteSubscriberProvisionStatusResultList = new ArrayList<>();
            }
            try {
                mRequestSatelliteSubscriberProvisionStatusSemaphore.release();
            } catch (Exception ex) {
                loge("mRequestSatelliteSubscriberProvisionStatusReceiver: Got exception in "
                        + "releasing "
                        + "semaphore, ex=" + ex);
            }
        }
    };

    @Test
    public void testRequestSatelliteSubscriberProvisionStatus() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        verifyRequestSatelliteSubscriberProvisionStatus();
    }

    private void verifyRequestSatelliteSubscriberProvisionStatus() throws Exception {
        setSatelliteSubscriberTesting(true);
        List<SatelliteSubscriberInfo> list = getExpectedSatelliteSubscriberInfoList();
        mCarrierConfigBundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mCarrierConfigBundle.putString(KEY_SATELLITE_NIDD_APN_NAME_STRING, mNiddApn);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ESOS_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        moveTimeForward(TimeUnit.MINUTES.toMillis(1));
        processAllMessages();

        // Verify that calling requestSatelliteSubscriberProvisionStatus returns the expected
        // list of SatelliteSubscriberProvisionStatus.
        mSatelliteControllerUT.requestSatelliteSubscriberProvisionStatus(
                mRequestSatelliteSubscriberProvisionStatusReceiver);
        moveTimeForward(TimeUnit.MINUTES.toMillis(1));
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS,
                mRequestSatelliteSubscriberProvisionStatusResultCode);
        assertEquals(list.get(0), mRequestSatelliteSubscriberProvisionStatusResultList.get(
                0).getSatelliteSubscriberInfo());
    }

    @Test
    public void testProvisionSatellite() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        verifyRequestSatelliteSubscriberProvisionStatus();
        List<SatelliteSubscriberInfo> inputList = getExpectedSatelliteSubscriberInfoList();
        verifyProvisionSatellite(inputList);
    }

    private void verifyProvisionSatellite(List<SatelliteSubscriberInfo> inputList) {
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[1];
            AsyncResult.forMessage(message, null, new SatelliteException(SATELLITE_RESULT_SUCCESS));
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).updateSatelliteSubscription(anyString(), any());

        mSatelliteControllerUT.provisionSatellite(inputList, mProvisionSatelliteReceiver);
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS, mProvisionSateResultCode);
        assertTrue(mProvisionState);
    }

    @Test
    public void testRegisterForSatelliteSubscriptionProvisionStateChanged() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        Semaphore semaphore = new Semaphore(0);
        SatelliteSubscriberProvisionStatus[] resultArray =
                new SatelliteSubscriberProvisionStatus[2];
        ISatelliteProvisionStateCallback callback = new ISatelliteProvisionStateCallback.Stub() {
            @Override
            public void onSatelliteProvisionStateChanged(boolean provisioned) {
                logd("onSatelliteProvisionStateChanged: provisioned=" + provisioned);
            }

            @Override
            public void onSatelliteSubscriptionProvisionStateChanged(
                    List<SatelliteSubscriberProvisionStatus> satelliteSubscriberProvisionStatus) {
                logd("onSatelliteSubscriptionProvisionStateChanged: "
                        + satelliteSubscriberProvisionStatus);
                for (int i = 0; i < satelliteSubscriberProvisionStatus.size(); i++) {
                    resultArray[i] = satelliteSubscriberProvisionStatus.get(i);
                }
                try {
                    semaphore.release();
                } catch (Exception ex) {
                    loge("onSatelliteSubscriptionProvisionStateChanged: Got exception in releasing "
                            + "semaphore, ex=" + ex);
                }
            }
        };

        TestSubscriptionManager testSubscriptionManager = new TestSubscriptionManager();
        doAnswer(invocation -> {
            testSubscriptionManager.setIsSatelliteProvisionedForNonIpDatagram(
                    invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(mMockSubscriptionManagerService).setIsSatelliteProvisionedForNonIpDatagram(anyInt(),
                anyBoolean());
        doAnswer(invocation -> testSubscriptionManager.isSatelliteProvisionedForNonIpDatagram(
                invocation.getArgument(0))).when(
                mMockSubscriptionManagerService).isSatelliteProvisionedForNonIpDatagram(anyInt());

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        int errorCode = mSatelliteControllerUT.registerForSatelliteProvisionStateChanged(callback);
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS, errorCode);

        verifyRequestSatelliteSubscriberProvisionStatus();

        // Verify that onSatelliteSubscriptionProvisionStateChanged is called when requesting
        // provisioning for the first time.
        List<SatelliteSubscriberInfo> list = getExpectedSatelliteSubscriberInfoList();
        List<SatelliteSubscriberInfo> inputList = new ArrayList<>();
        inputList.add(list.get(0));
        verifyProvisionSatellite(inputList);

        verify(mMockSatelliteModemInterface, times(1)).updateSatelliteSubscription(anyString(),
                any());
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSubscriptionProvisionStateChanged"));
        assertTrue(resultArray[0].isProvisioned());
        assertEquals(mSubscriberId, resultArray[0].getSatelliteSubscriberInfo().getSubscriberId());

        // Request provisioning with SatelliteSubscriberInfo that has not been provisioned
        // before, and verify that onSatelliteSubscriptionProvisionStateChanged is called.
        inputList = new ArrayList<>();
        inputList.add(list.get(1));
        verifyProvisionSatellite(inputList);

        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSubscriptionProvisionStateChanged"));
        assertTrue(resultArray[1].isProvisioned());
        assertEquals(mSubscriberId2, resultArray[1].getSatelliteSubscriberInfo().getSubscriberId());

        // Request provisioning with the same SatelliteSubscriberInfo that was previously
        // requested, and verify that onSatelliteSubscriptionProvisionStateChanged is not called.
        verifyProvisionSatellite(inputList);

        assertFalse(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSubscriptionProvisionStateChanged"));

        // Request deprovision for subscriberID 2, verify that subscriberID 2 is set to
        // deprovision and that subscriberID 1 is set to provision.
        verifyDeprovisionSatellite(inputList);
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSubscriptionProvisionStateChanged"));
        assertFalse(resultArray[1].isProvisioned());
        assertEquals(mSubscriberId2, resultArray[1].getSatelliteSubscriberInfo().getSubscriberId());
        assertTrue(resultArray[0].isProvisioned());
        assertEquals(mSubscriberId, resultArray[0].getSatelliteSubscriberInfo().getSubscriberId());

        // Request deprovision for subscriberID 1, verify that subscriberID 1 is set to deprovision.
        inputList = new ArrayList<>();
        inputList.add(list.get(0));
        verifyDeprovisionSatellite(inputList);
        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSubscriptionProvisionStateChanged"));
        assertFalse(resultArray[1].isProvisioned());
        assertEquals(mSubscriberId2, resultArray[1].getSatelliteSubscriberInfo().getSubscriberId());
        assertFalse(resultArray[0].isProvisioned());
        assertEquals(mSubscriberId, resultArray[0].getSatelliteSubscriberInfo().getSubscriberId());

        // Request provision for subscriberID 2, verify that subscriberID 2 is set to provision.
        inputList = new ArrayList<>();
        inputList.add(list.get(1));
        verifyProvisionSatellite(inputList);

        assertTrue(waitForForEvents(
                semaphore, 1, "testRegisterForSatelliteSubscriptionProvisionStateChanged"));
        assertTrue(resultArray[1].isProvisioned());
        assertEquals(mSubscriberId2, resultArray[1].getSatelliteSubscriberInfo().getSubscriberId());
        assertFalse(resultArray[0].isProvisioned());
        assertEquals(mSubscriberId, resultArray[0].getSatelliteSubscriberInfo().getSubscriberId());
    }

    private boolean mDeprovisionDone = false;
    private int mDeprovisionSateResultCode = -1;
    private Semaphore mDeprovisionSateSemaphore = new Semaphore(0);
    private ResultReceiver mDeprovisionSatelliteReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mDeprovisionSateResultCode = resultCode;
            logd("DeprovisionSatelliteReceiver: resultCode=" + resultCode);
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_DEPROVISION_SATELLITE_TOKENS)) {
                    mDeprovisionDone = resultData.getBoolean(KEY_DEPROVISION_SATELLITE_TOKENS);
                    logd("DeprovisionSatelliteReceiver: deprovision=" + mDeprovisionDone);
                } else {
                    loge("KEY_DEPROVISION_SATELLITE_TOKENS does not exist.");
                    mDeprovisionDone = false;
                }
            } else {
                mDeprovisionDone = false;
            }
            try {
                mDeprovisionSateSemaphore.release();
            } catch (Exception ex) {
                loge("DeprovisionSatelliteReceiver: Got exception in releasing semaphore " + ex);
            }
        }
    };

    @Test
    public void testDeprovisionSatellite() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        verifyRequestSatelliteSubscriberProvisionStatus();
        List<SatelliteSubscriberInfo> inputList = getExpectedSatelliteSubscriberInfoList();
        verifyProvisionSatellite(inputList);
        verifyDeprovisionSatellite(inputList);
    }

    private void verifyDeprovisionSatellite(List<SatelliteSubscriberInfo> inputList) {
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[1];
            AsyncResult.forMessage(message, null, new SatelliteException(SATELLITE_RESULT_SUCCESS));
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).updateSatelliteSubscription(anyString(), any());

        mSatelliteControllerUT.deprovisionSatellite(inputList, mDeprovisionSatelliteReceiver);
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS, mDeprovisionSateResultCode);
        assertTrue(mDeprovisionDone);
    }

    private void setSatelliteSubscriberTesting(boolean sameCarrier) throws Exception {
        doReturn("123").when(mContext).getAttributionTag();
        final int carrierId_subID = 0;
        final int carrierId_subID1 = sameCarrier ? 0 : 1;
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo.Builder()
                .setId(SUB_ID).setIccId(mIccId).setSimSlotIndex(0).setOnlyNonTerrestrialNetwork(
                        false).setSatelliteESOSSupported(true).setCarrierId(
                            carrierId_subID).build();
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo.Builder()
                .setId(SUB_ID1).setIccId(mIccId2).setSimSlotIndex(1).setOnlyNonTerrestrialNetwork(
                        true).setSatelliteESOSSupported(false).setCarrierId(
                            carrierId_subID1).build();
        List<SubscriptionInfo> allSubInfos = new ArrayList<>();
        allSubInfos.add(subscriptionInfo);
        allSubInfos.add(subscriptionInfo2);
        testSubscriptionInfo = subscriptionInfo;
        testSubscriptionInfo2 = subscriptionInfo2;
        doReturn(allSubInfos).when(mMockSubscriptionManagerService).getAllSubInfoList(
                anyString(), anyString());
        SubscriptionInfoInternal subInfoInternal =
                new SubscriptionInfoInternal.Builder().setCarrierId(
                    carrierId_subID).setImsi(mImsi).setIccId(mIccId).build();
        SubscriptionInfoInternal subInfoInternal2 =
                new SubscriptionInfoInternal.Builder().setCarrierId(
                    carrierId_subID1).setImsi(mImsi2).setIccId(mIccId2).build();
        doReturn(subscriptionInfo).when(mMockSubscriptionManagerService).getSubscriptionInfo(
                eq(SUB_ID));
        doReturn(subscriptionInfo2).when(mMockSubscriptionManagerService).getSubscriptionInfo(
                eq(SUB_ID1));
        Field field = SatelliteController.class.getDeclaredField("mInjectSubscriptionManager");
        field.setAccessible(true);
        field.set(mSatelliteControllerUT, mSubscriptionManager);
        doReturn(mMsisdn).when(mSubscriptionManager).getPhoneNumber(eq(SUB_ID));
        doReturn(mMsisdn2).when(mSubscriptionManager).getPhoneNumber(eq(SUB_ID1));
        Field provisionedSubscriberIdField = SatelliteController.class.getDeclaredField(
                "mProvisionedSubscriberId");
        provisionedSubscriberIdField.setAccessible(true);
        provisionedSubscriberIdField.set(mSatelliteControllerUT, new HashMap<>());
        Field subscriberIdPerSubField = SatelliteController.class.getDeclaredField(
                "mSubscriberIdPerSub");
        subscriberIdPerSubField.setAccessible(true);
        subscriberIdPerSubField.set(mSatelliteControllerUT, new HashMap<>());
        Field lastConfiguredIccIdField = SatelliteController.class.getDeclaredField(
                "mLastConfiguredIccId");
        lastConfiguredIccIdField.setAccessible(true);
        lastConfiguredIccIdField.set(mSatelliteControllerUT, null);
        doReturn(subInfoInternal).when(mMockSubscriptionManagerService).getSubscriptionInfoInternal(
                eq(SUB_ID));
        doReturn(subInfoInternal2).when(
                mMockSubscriptionManagerService).getSubscriptionInfoInternal(eq(SUB_ID1));
        doReturn(mResources).when(mContext).getResources();
        doReturn("package").when(mResources).getString(
                eq(R.string.config_satellite_gateway_service_package));
        doReturn("className").when(mResources).getString(
                eq(R.string.config_satellite_carrier_roaming_esos_provisioned_class));
    }

    private List<SatelliteSubscriberInfo> getExpectedSatelliteSubscriberInfoList() {
        List<SatelliteSubscriberInfo> list = new ArrayList<>();
        list.add(new SatelliteSubscriberInfo.Builder().setSubscriberId(mSubscriberId).setCarrierId(
                mCarrierId).setNiddApn(mNiddApn).setSubId(SUB_ID).setSubscriberIdType(
                SatelliteSubscriberInfo.IMSI_MSISDN).build());
        list.add(new SatelliteSubscriberInfo.Builder().setSubscriberId(mSubscriberId2).setCarrierId(
                mCarrierId).setNiddApn(mNiddApn).setSubId(SUB_ID1).setSubscriberIdType(
                SatelliteSubscriberInfo.ICCID).build());
        return list;
    }

    @Test
    public void testCheckForSubscriberIdChange_noChanged() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        String imsi = "012345";
        String oldMsisdn = "1234567890";
        String newMsisdn = "1234567890";
        List<SubscriptionInfo> allSubInfos = new ArrayList<>();
        Optional<String> getSubscriberId;
        SubscriptionInfoInternal subInfoInternal =
                new SubscriptionInfoInternal.Builder().setCarrierId(0)
                        .setImsi(imsi).build();

        when(mSubscriptionInfo.getSubscriptionId()).thenReturn(SUB_ID);
        allSubInfos.add(mSubscriptionInfo);
        doReturn(" ").when(mContext).getOpPackageName();
        doReturn(" ").when(mContext).getAttributionTag();
        when(mMockSubscriptionManagerService.getAllSubInfoList(anyString(), anyString()))
                .thenReturn(allSubInfos);
        when(mSubscriptionInfo.isSatelliteESOSSupported()).thenReturn(true);
        when(mMockSubscriptionManagerService.getSubscriptionInfoInternal(SUB_ID))
                .thenReturn(subInfoInternal);

        try {
            Field field = SatelliteController.class.getDeclaredField("mInjectSubscriptionManager");
            field.setAccessible(true);
            field.set(mSatelliteControllerUT, mSubscriptionManager);
        } catch (Exception e) {
            loge("Exception InjectSubscriptionManager e: " + e);
        }
        when(mSubscriptionManager.getPhoneNumber(SUB_ID)).thenReturn(newMsisdn);
        when(mSubscriptionInfo.isOnlyNonTerrestrialNetwork()).thenReturn(false);
        mSatelliteControllerUT.subscriberIdPerSub().put(imsi + oldMsisdn, SUB_ID);

        getSubscriberId = mSatelliteControllerUT.subscriberIdPerSub().entrySet().stream()
                .filter(entry -> entry.getValue().equals(SUB_ID))
                .map(Map.Entry::getKey).findFirst();
        assertEquals(imsi + newMsisdn, getSubscriberId.get());

        setComponentName();
        mSatelliteControllerUT.subsInfoListPerPriority().computeIfAbsent(
                        getKeyPriority(mSubscriptionInfo), k -> new ArrayList<>())
                .add(mSubscriptionInfo);
        mSatelliteControllerUT.evaluateESOSProfilesPrioritizationTest();
        // Verify that broadcast has not been sent.
        verify(mContext, times(0)).sendBroadcast(any(Intent.class));
    }

    @Test
    public void testCheckForSubscriberIdChange_changed() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mCarrierConfigBundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        List<SubscriptionInfo> allSubInfos = new ArrayList<>();

        String imsi = "012345";
        String oldMsisdn = "1234567890";
        String newMsisdn = "4567891234";

        Optional<String> getSubscriberId;
        SubscriptionInfoInternal subInfoInternal =
                new SubscriptionInfoInternal.Builder().setCarrierId(0).setImsi(imsi).build();

        when(mSubscriptionInfo.getSubscriptionId()).thenReturn(SUB_ID);
        allSubInfos.add(mSubscriptionInfo);
        doReturn(" ").when(mContext).getOpPackageName();
        doReturn(" ").when(mContext).getAttributionTag();
        when(mMockSubscriptionManagerService.getAllSubInfoList(anyString(), anyString()))
                .thenReturn(allSubInfos);

        when(mSubscriptionInfo.isSatelliteESOSSupported()).thenReturn(true);
        when(mSubscriptionInfo.isActive()).thenReturn(true);
        when(mMockSubscriptionManagerService.getSubscriptionInfoInternal(SUB_ID))
                .thenReturn(subInfoInternal);

        try {
            Field field = SatelliteController.class.getDeclaredField("mInjectSubscriptionManager");
            field.setAccessible(true);
            field.set(mSatelliteControllerUT, mSubscriptionManager);
        } catch (Exception e) {
            loge("Exception InjectSubscriptionManager e: " + e);
        }
        when(mSubscriptionManager.getPhoneNumber(SUB_ID)).thenReturn(newMsisdn);
        when(mSubscriptionInfo.isOnlyNonTerrestrialNetwork()).thenReturn(false);
        mSatelliteControllerUT.subscriberIdPerSub().put(imsi + oldMsisdn, SUB_ID);

        getSubscriberId = mSatelliteControllerUT.subscriberIdPerSub().entrySet().stream()
                .filter(entry -> entry.getValue().equals(SUB_ID))
                .map(Map.Entry::getKey).findFirst();
        assertNotEquals(imsi + newMsisdn, getSubscriberId.get());

        setComponentName();
        mSatelliteControllerUT.subsInfoListPerPriority().computeIfAbsent(
                        getKeyPriority(mSubscriptionInfo), k -> new ArrayList<>())
                .add(mSubscriptionInfo);
        mSatelliteControllerUT.evaluateESOSProfilesPrioritizationTest();
        // Verify that broadcast has been sent.
        verify(mContext, times(1)).sendBroadcast(any(Intent.class));
    }

    @Test
    public void testRegisterForSatelliteCommunicationAllowedStateChanged() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mContextFixture.putIntArrayResource(
                R.array.config_verizon_satellite_enabled_tagids,
                new int[]{1001});
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getIntArray(
                R.array.config_verizon_satellite_enabled_tagids)).thenReturn(new int[]{1001});
        // carrierID is same as SUBID for this test
        final int carrierSubId = SUB_ID;
        final int oemSubId = SUB_ID1;
        final String carrierSubscriberId = mSubscriberId;
        final String oemSubscriberId = mSubscriberId2;
        mCarrierConfigBundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        setSatelliteSubscriberTesting(false);
        invokeCarrierConfigChanged();

        Field provisionedSubscriberIdField = SatelliteController.class.getDeclaredField(
                "mProvisionedSubscriberId");
        provisionedSubscriberIdField.setAccessible(true);
        Map<String, Boolean> testProvisionedSubscriberId = new HashMap<>();;
        testProvisionedSubscriberId.put(carrierSubscriberId, true);
        testProvisionedSubscriberId.put(oemSubscriberId, true);
        provisionedSubscriberIdField.set(mSatelliteControllerUT, testProvisionedSubscriberId);

        Field currentLocationTagIdsField = SatelliteController.class.getDeclaredField(
                "mCurrentLocationTagIds");
        currentLocationTagIdsField.setAccessible(true);

        setComponentName();
        mSatelliteControllerUT.setIsSatelliteAllowedState(true);

        mSatelliteControllerUT.registerForSatelliteCommunicationAllowedStateChanged();

        // Test satelliteAccessConfigCallback.onSuccess
        // with current location NOT supporting carrier satellite
        // OEM satellite subscription should be selected
        currentLocationTagIdsField.set(mSatelliteControllerUT, Arrays.asList(100));

        mSatelliteControllerUT.subsInfoListPerPriority().computeIfAbsent(
                        getKeyPriority(testSubscriptionInfo), k -> new ArrayList<>())
                .add(testSubscriptionInfo);
        mSatelliteControllerUT.subsInfoListPerPriority().computeIfAbsent(
                        getKeyPriority(testSubscriptionInfo2), k -> new ArrayList<>())
                .add(testSubscriptionInfo2);

        mSatelliteControllerUT.evaluateESOSProfilesPrioritizationTest();
        processAllMessages();
        assertEquals(oemSubId, mSatelliteControllerUT.getSelectedSatelliteSubId());

        // Test satelliteAccessConfigCallback.onSuccess
        // with current location supporting carrier satellite
        // Carrier satellite subscription should be selected
        currentLocationTagIdsField.set(mSatelliteControllerUT, Arrays.asList(1001, 100));

        mSatelliteControllerUT.subsInfoListPerPriority().computeIfAbsent(
                        getKeyPriority(testSubscriptionInfo), k -> new ArrayList<>())
                .add(testSubscriptionInfo);
        mSatelliteControllerUT.subsInfoListPerPriority().computeIfAbsent(
                        getKeyPriority(testSubscriptionInfo2), k -> new ArrayList<>())
                .add(testSubscriptionInfo2);

        mSatelliteControllerUT.evaluateESOSProfilesPrioritizationTest();
        processAllMessages();
        assertEquals(carrierSubId, mSatelliteControllerUT.getSelectedSatelliteSubId());
    }


    @Test
    public void testProvisionStatusPerSubscriberIdGetFromDb() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        setSatelliteSubscriberTesting(true);
        // Check if the cache is not updated when the value read from the database is false.
        verifyProvisionStatusPerSubscriberIdGetFromDb(false);

        // Check if the cache is updated when the value read from the database is true.
        verifyProvisionStatusPerSubscriberIdGetFromDb(true);
    }

    @Test
    public void testProvisionStatusPerSubscriberIdStoreToDb() throws Exception {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        setSatelliteSubscriberTesting(true);
        // Check if the cache is not updated when the value read from the database is false.
        verifyProvisionStatusPerSubscriberIdGetFromDb(false);

        List<SatelliteSubscriberInfo> inputList = getExpectedSatelliteSubscriberInfoList();
        verifyProvisionSatellite(inputList);
        verify(mMockSubscriptionManagerService).setIsSatelliteProvisionedForNonIpDatagram(
                eq(SUB_ID), eq(true));
    }

    @Test
    public void testIsCarrierRoamingNtnAvailableServicesForManualConnect() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        // CARRIER_ROAMING_NTN_CONNECT_MANUAL: 1
        mCarrierConfigBundle.putInt(KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT, 1);

        mSatelliteControllerUT.setSatellitePhone(1);
        processAllMessages();
        when(mContext.getPackageManager()).thenReturn(mMockPManager);
        try {
            when(mMockPManager.getApplicationInfo(anyString(),
                    anyInt())).thenReturn(getApplicationInfo());
        } catch (PackageManager.NameNotFoundException e) {
            logd("NameNotFoundException");
        }
        assertTrue(mSatelliteControllerUT
                .isP2PSmsDisallowedOnCarrierRoamingNtn(/*subId*/ SUB_ID));
    }

    @Test
    public void testIsCarrierRoamingNtnAvailableServicesForAutomaticConnect() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        // CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC: 0
        mCarrierConfigBundle.putInt(KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT, 0);

        mSatelliteControllerUT.setSatellitePhone(1);
        processAllMessages();
        when(mContext.getPackageManager()).thenReturn(mMockPManager);
        try {
            when(mMockPManager.getApplicationInfo(anyString(),
                    anyInt())).thenReturn(getApplicationInfo());
        } catch (PackageManager.NameNotFoundException e) {
            logd("NameNotFoundException");
        }
        // If it is automatic connection case, it is not support the callback.
        assertFalse(mSatelliteControllerUT
                .isP2PSmsDisallowedOnCarrierRoamingNtn(/*subId*/ SUB_ID));
    }

    ApplicationInfo getApplicationInfo() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.metaData = new Bundle();
        applicationInfo.metaData.putBoolean(
                METADATA_SATELLITE_MANUAL_CONNECT_P2P_SUPPORT, true);
        return applicationInfo;
    }

    @Test
    public void testRegisterApplicationStateChanged() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, false);
        when(mMockSubscriptionManagerService.getActiveSubIdList(true))
                .thenReturn(new int[]{SUB_ID1});

        ArgumentCaptor<IntentFilter> intentFilterCaptor =
                ArgumentCaptor.forClass(IntentFilter.class);
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), intentFilterCaptor.capture(),
                anyInt());

        BroadcastReceiver receiver = receiverCaptor.getValue();
        mSatelliteControllerUT =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        assertFalse(mSatelliteControllerUT.isApplicationUpdated);
        Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
        intent.setData(Uri.parse("com.example.app"));
        receiver.onReceive(mContext, intent);
        CountDownLatch latch1 = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            latch1.countDown();
        }, 100);
        try {
            latch1.await();
        } catch (InterruptedException e) {
        }
        assertTrue(mSatelliteControllerUT.isApplicationUpdated);
        mSatelliteControllerUT =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        assertFalse(mSatelliteControllerUT.isApplicationUpdated);
        intent = new Intent(Intent.ACTION_PACKAGE_REPLACED);
        intent.setData(Uri.parse("com.example.app"));
        receiver.onReceive(mContext, intent);
        CountDownLatch latch2 = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            latch2.countDown();
        }, 100);
        try {
            latch2.await();
        } catch (InterruptedException e) {
        }
        assertTrue(mSatelliteControllerUT.isApplicationUpdated);
        mSatelliteControllerUT =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        assertFalse(mSatelliteControllerUT.isApplicationUpdated);
        intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.setData(Uri.parse("com.example.app"));
        receiver.onReceive(mContext, intent);
        CountDownLatch latch3 = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            latch3.countDown();
        }, 100);
        try {
            latch3.await();
        } catch (InterruptedException e) {
        }
        assertTrue(mSatelliteControllerUT.isApplicationUpdated);
        mSatelliteControllerUT =
                new TestSatelliteController(mContext, Looper.myLooper(), mFeatureFlags);
        assertFalse(mSatelliteControllerUT.isApplicationUpdated);
        intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
        intent.setData(Uri.parse("com.example.different"));
        receiver.onReceive(mContext, intent);
        CountDownLatch latch4 = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            latch4.countDown();
        }, 100);
        try {
            latch4.await();
        } catch (InterruptedException e) {
        }
        assertFalse(mSatelliteControllerUT.isApplicationUpdated);
    }

    @Test
    public void testUpdateSystemSelectionChannels() {
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        String mccmnc = "123455";
        int[] bands1 = {200, 201, 202};
        IntArray intArraybands1 = new IntArray(3);
        intArraybands1.addAll(bands1);
        int[] earfcns1 = {300, 301, 310, 311};
        IntArray intArrayEarfcns1 = new IntArray(4);
        intArrayEarfcns1.addAll(earfcns1);
        String seed1 = "test-seed-satellite1";
        UUID uuid1 = UUID.nameUUIDFromBytes(seed1.getBytes());
        SatellitePosition satellitePosition1 = new SatellitePosition(0, 35876);
        EarfcnRange earfcnRange1 = new EarfcnRange(301, 300);
        EarfcnRange earfcnRange2 = new EarfcnRange(311, 310);
        List<EarfcnRange> earfcnRangeList1 = new ArrayList<>(
                Arrays.asList(earfcnRange1, earfcnRange2));
        SatelliteInfo satelliteInfo1 = new SatelliteInfo(uuid1, satellitePosition1, Arrays.stream(
                bands1).boxed().collect(Collectors.toList()), earfcnRangeList1);
        int[] tagIds = {1, 2, 3};
        IntArray intArrayTagIds = new IntArray(3);
        intArrayTagIds.addAll(tagIds);
        SystemSelectionSpecifier systemSelectionSpecifier1 = new SystemSelectionSpecifier(mccmnc,
                intArraybands1, intArrayEarfcns1, new SatelliteInfo[]{satelliteInfo1},
                intArrayTagIds);

        setUpResponseForUpdateSystemSelectionChannels(SATELLITE_RESULT_ERROR);
        mSatelliteControllerUT.updateSystemSelectionChannels(
                new ArrayList<>(List.of(systemSelectionSpecifier1)),
                mSystemSelectionChannelUpdatedReceiver);
        processAllMessages();
        assertTrue(waitForRequestUpdateSystemSelectionChannelResult(1));
        assertEquals(SATELLITE_RESULT_ERROR, mQueriedSystemSelectionChannelUpdatedResultCode);

        // Verify whether callback receives expected result
        setUpResponseForUpdateSystemSelectionChannels(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.updateSystemSelectionChannels(
                new ArrayList<>(List.of(systemSelectionSpecifier1)),
                mSystemSelectionChannelUpdatedReceiver);
        processAllMessages();
        assertTrue(waitForRequestUpdateSystemSelectionChannelResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSystemSelectionChannelUpdatedResultCode);

        // Verify whether SatelliteModemInterface API was invoked and data is valid, when single
        // data was provided.
        ArgumentCaptor<List<SystemSelectionSpecifier>> systemSelectionSpecifierListCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mMockSatelliteModemInterface, times(2)).updateSystemSelectionChannels(
                systemSelectionSpecifierListCaptor.capture(), any(Message.class));
        List<SystemSelectionSpecifier> capturedList = systemSelectionSpecifierListCaptor.getValue();
        SystemSelectionSpecifier systemSelectionSpecifier = capturedList.getFirst();

        assertEquals(mccmnc, systemSelectionSpecifier.getMccMnc());
        int[] actualBandsArray = systemSelectionSpecifier.getBands();
        assertArrayEquals(bands1, actualBandsArray);
        int[] actualEarfcnsArray = systemSelectionSpecifier.getEarfcns();
        assertArrayEquals(earfcns1, actualEarfcnsArray);
        assertArrayEquals(new SatelliteInfo[]{satelliteInfo1},
                systemSelectionSpecifier.getSatelliteInfos().toArray(new SatelliteInfo[0]));
        int[] actualTagIdArray = systemSelectionSpecifier.getTagIds();
        assertArrayEquals(tagIds, actualTagIdArray);

        // Verify whether SatelliteModemInterface API was invoked and data is valid, when list
        // of data was provided.
        int[] bands2 = {210, 211, 212};
        IntArray intArraybands2 = new IntArray(3);
        intArraybands2.addAll(bands2);
        int[] earfcns2 = {320, 321, 330, 331};
        IntArray intArrayEarfcns2 = new IntArray(4);
        intArrayEarfcns2.addAll(earfcns2);
        String seed2 = "test-seed-satellite2";
        UUID uuid2 = UUID.nameUUIDFromBytes(seed2.getBytes());
        SatellitePosition satellitePosition2 = new SatellitePosition(120, 35876);
        EarfcnRange earfcnRange3 = new EarfcnRange(321, 320);
        EarfcnRange earfcnRange4 = new EarfcnRange(331, 330);
        List<EarfcnRange> earfcnRangeList2 = new ArrayList<>(
                Arrays.asList(earfcnRange3, earfcnRange4));
        SatelliteInfo satelliteInfo2 = new SatelliteInfo(uuid2, satellitePosition2, Arrays.stream(
                bands1).boxed().collect(Collectors.toList()), earfcnRangeList2);
        SystemSelectionSpecifier systemSelectionSpecifier2 = new SystemSelectionSpecifier(mccmnc,
                intArraybands2, intArrayEarfcns2, new SatelliteInfo[]{satelliteInfo2},
                intArrayTagIds);

        // Verify whether callback receives expected result
        setUpResponseForUpdateSystemSelectionChannels(SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.updateSystemSelectionChannels(
                new ArrayList<>(List.of(systemSelectionSpecifier1, systemSelectionSpecifier2)),
                mSystemSelectionChannelUpdatedReceiver);
        processAllMessages();
        assertTrue(waitForRequestUpdateSystemSelectionChannelResult(1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSystemSelectionChannelUpdatedResultCode);

        // Verify whether SatelliteModemInterface API was invoked and data is valid,
        verify(mMockSatelliteModemInterface, times(3)).updateSystemSelectionChannels(
                systemSelectionSpecifierListCaptor.capture(), any(Message.class));
        capturedList = systemSelectionSpecifierListCaptor.getValue();
        SystemSelectionSpecifier capturedSystemSelectionSpecifier1 = capturedList.getFirst();
        SystemSelectionSpecifier capturedSystemSelectionSpecifier2 = capturedList.get(1);

        // Verify first SystemSelectionSpecifier
        assertEquals(mccmnc, systemSelectionSpecifier.getMccMnc());
        actualBandsArray = capturedSystemSelectionSpecifier1.getBands();
        assertArrayEquals(bands1, actualBandsArray);
        actualEarfcnsArray = capturedSystemSelectionSpecifier1.getEarfcns();
        assertArrayEquals(earfcns1, actualEarfcnsArray);
        assertArrayEquals(new SatelliteInfo[]{satelliteInfo1},
                capturedSystemSelectionSpecifier1.getSatelliteInfos().toArray(
                        new SatelliteInfo[0]));
        actualTagIdArray = capturedSystemSelectionSpecifier1.getTagIds();
        assertArrayEquals(tagIds, actualTagIdArray);

        // Verify second SystemSelectionSpecifier
        assertEquals(mccmnc, systemSelectionSpecifier.getMccMnc());
        actualBandsArray = capturedSystemSelectionSpecifier2.getBands();
        assertArrayEquals(bands2, actualBandsArray);
        actualEarfcnsArray = capturedSystemSelectionSpecifier2.getEarfcns();
        assertArrayEquals(earfcns2, actualEarfcnsArray);
        assertArrayEquals(new SatelliteInfo[]{satelliteInfo2},
                capturedSystemSelectionSpecifier2.getSatelliteInfos().toArray(
                        new SatelliteInfo[0]));
        actualTagIdArray = capturedSystemSelectionSpecifier2.getTagIds();
        assertArrayEquals(tagIds, actualTagIdArray);
    }

    private void verifyProvisionStatusPerSubscriberIdGetFromDb(boolean provision) {
        doReturn(provision).when(
                mMockSubscriptionManagerService).isSatelliteProvisionedForNonIpDatagram(anyInt());
        mCarrierConfigBundle.putString(KEY_SATELLITE_NIDD_APN_NAME_STRING, mNiddApn);
        mCarrierConfigBundle.putBoolean(KEY_SATELLITE_ESOS_SUPPORTED_BOOL, true);
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        moveTimeForward(TimeUnit.MINUTES.toMillis(1));
        processAllMessages();
        mSatelliteControllerUT.requestSatelliteSubscriberProvisionStatus(
                mRequestSatelliteSubscriberProvisionStatusReceiver);
        moveTimeForward(TimeUnit.MINUTES.toMillis(1));
        processAllMessages();
        assertEquals(SATELLITE_RESULT_SUCCESS,
                mRequestSatelliteSubscriberProvisionStatusResultCode);
        assertEquals(provision,
                mRequestSatelliteSubscriberProvisionStatusResultList.get(0).isProvisioned());
    }

    private void setComponentName() {
        when(mSatelliteControllerUT.getStringFromOverlayConfigTest(
                R.string.config_satellite_gateway_service_package))
                .thenReturn("com.example.package");
        when(mSatelliteControllerUT.getStringFromOverlayConfigTest(
                R.string.config_satellite_carrier_roaming_esos_provisioned_class))
                .thenReturn("com.example.class");
    }

    private int getKeyPriority(SubscriptionInfo subscriptionInfo) {
        boolean isActive = subscriptionInfo.isActive();
        boolean isNtnOnly = subscriptionInfo.isOnlyNonTerrestrialNetwork();
        boolean isESOSSupported = subscriptionInfo.isSatelliteESOSSupported();
        boolean isCarrierSatelliteHigherPriority =
                mSatelliteControllerUT.isCarrierSatelliteHigherPriorityTest(
                        subscriptionInfo);

        int keyPriority;
        if (isESOSSupported && isActive && isCarrierSatelliteHigherPriority) {
            keyPriority = 1;
        } else if (isNtnOnly) {
            keyPriority = 2;
        } else if (isESOSSupported) {
            keyPriority = 3;
        } else {
            keyPriority = -1;
        }
        return keyPriority;
    }

    private void resetSatelliteControllerUTEnabledState() {
        logd("resetSatelliteControllerUTEnabledState");
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_RADIO_NOT_AVAILABLE);
        doNothing().when(mMockSatelliteModemInterface)
                .setSatelliteServicePackageName(anyString());
        mSatelliteControllerUT.setSatelliteServicePackageName("TestSatelliteService", null);
        processAllMessages();

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(false);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(true);
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
        mSatelliteControllerUT.setSatelliteServicePackageName("TestSatelliteService", null);
        processAllMessages();
    }

    private void resetSatelliteControllerUTToSupportedAndProvisionedState() {
        resetSatelliteControllerUT();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        verifySatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setProvisionedState(true);
        processAllMessages();
        verifySatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
    }

    private void resetSatelliteControllerUTToOffAndProvisionedState() {
        resetSatelliteControllerUTToSupportedAndProvisionedState();
        // Clean up pending resources and move satellite controller to OFF state.
        sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_UNAVAILABLE, null);
        mSatelliteControllerUT.moveSatelliteToOffStateAndCleanUpResources(
                SATELLITE_RESULT_REQUEST_ABORTED);
        processAllMessages();
        verifySatelliteEnabled(false, SATELLITE_RESULT_SUCCESS);
    }

    private void resetSatelliteControllerUTToOnAndProvisionedState() {
        resetSatelliteControllerUTToOffAndProvisionedState();
        setRadioPower(true);
        processAllMessages();

        mIIntegerConsumerResults.clear();
        setUpResponseForRequestSatelliteEnabled(true, false, false, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.requestSatelliteEnabled(true, false, false, mIIntegerConsumer);
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
        mSatelliteControllerUT.isSatelliteEnabledOrBeingEnabled = isSatelliteEnabled;
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
        mSatelliteControllerUT.setSatelliteProvisioned(isSatelliteProvisioned);
    }

    private void setUpResponseForRequestSatelliteEnabled(
            boolean enabled, boolean demoMode, boolean emergency,
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            if (exception == null && !enabled) {
                sendSatelliteModemStateChangedEvent(SATELLITE_MODEM_STATE_OFF, null);
            }
            Message message = (Message) invocation.getArguments()[1];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface)
                .requestSatelliteEnabled(any(SatelliteModemEnableRequestAttributes.class),
                        any(Message.class));
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

    private void setUpNoResponseForRequestSatelliteEnabled(boolean enabled, boolean demoMode,
            boolean emergency) {
        doNothing().when(mMockSatelliteModemInterface)
                .requestSatelliteEnabled(eq(new SatelliteModemEnableRequestAttributes(
                                enabled, demoMode, emergency,
                                new SatelliteSubscriptionInfo("", "")
                        )),
                        any(Message.class));
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

    private void setUpResponseForUpdateSystemSelectionChannels(
            @SatelliteManager.SatelliteResult int error) {
        SatelliteException exception = (error == SATELLITE_RESULT_SUCCESS)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[1];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).updateSystemSelectionChannels(anyList(),
                any(Message.class));
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

    private boolean waitForRequestUpdateSystemSelectionChannelResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSystemSelectionChannelUpdatedSemaphore.tryAcquire(TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    logd("Timeout to receive "
                            + "updateSystemSelectionChannel()"
                            + " callback");
                    return false;
                }
            } catch (Exception ex) {
                logd("updateSystemSelectionChannel: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private void verifySatelliteSupported(boolean supported, int expectedErrorCode) {
        mSatelliteSupportSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteSupported(mSatelliteSupportReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteSupportedResult(1));
        assertEquals(expectedErrorCode, mQueriedSatelliteSupportedResultCode);
        assertEquals(supported, mQueriedSatelliteSupported);
    }

    private void verifySatelliteSupported(TestSatelliteController satelliteController,
            boolean supported, int expectedErrorCode) {
        mSatelliteSupportSemaphore.drainPermits();
        satelliteController.requestIsSatelliteSupported(mSatelliteSupportReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteSupportedResult(1));
        assertEquals(expectedErrorCode, mQueriedSatelliteSupportedResultCode);
        assertEquals(supported, mQueriedSatelliteSupported);
    }

    private void verifySatelliteEnabled(boolean enabled, int expectedErrorCode) {
        mIsSatelliteEnabledSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteEnabled(mIsSatelliteEnabledReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteEnabledResult(1));
        assertEquals(expectedErrorCode, mQueriedIsSatelliteEnabledResultCode);
        assertEquals(enabled, mQueriedIsSatelliteEnabled);
    }

    private void verifySatelliteProvisioned(boolean provisioned, int expectedErrorCode) {
        mIsSatelliteProvisionedSemaphore.drainPermits();
        mSatelliteControllerUT.requestIsSatelliteProvisioned(mIsSatelliteProvisionedReceiver);
        processAllMessages();
        assertTrue(waitForRequestIsSatelliteProvisionedResult(1));
        assertEquals(expectedErrorCode, mQueriedIsSatelliteProvisionedResultCode);
        assertEquals(provisioned, mQueriedIsSatelliteProvisioned);
    }

    private void verifyRequestNtnSignalStrength(
            @NtnSignalStrength.NtnSignalStrengthLevel int signalStrengthLevel,
            int expectedErrorCode) {
        mRequestNtnSignalStrengthSemaphore.drainPermits();
        mSatelliteControllerUT.requestNtnSignalStrength(mRequestNtnSignalStrengthReceiver);
        processAllMessages();
        assertTrue(waitForRequestNtnSignalStrengthResult(1));
        assertEquals(expectedErrorCode, mQueriedNtnSignalStrengthResultCode);
        assertEquals(signalStrengthLevel, mQueriedNtnSignalStrengthLevel);
    }

    private void setProvisionedState(@Nullable Boolean provisioned) {
        mSatelliteControllerUT.setSatelliteProvisioned(provisioned);
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

    private void sendSignalStrengthChangedEvent(int phoneId) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                57 /* EVENT_SIGNAL_STRENGTH_CHANGED */);
        msg.obj = new AsyncResult(phoneId, null, null);
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

    private void sendSatelliteSupportedStateChangedEvent(boolean supported, Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                41 /* EVENT_SATELLITE_SUPPORTED_STATE_CHANGED */);
        msg.obj = new AsyncResult(null, supported, exception);
        msg.sendToTarget();
    }

    private void sendSatelliteRegistrationFailureEvent(int errorCode, Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                54 /* EVENT_SATELLITE_REGISTRATION_FAILURE */);
        msg.obj = new AsyncResult(null, errorCode, exception);
        msg.sendToTarget();
    }

    private void sendTerrestrialNetworkAvailableChangedEvent(boolean isAvailable,
            Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                55 /* EVENT_TERRESTRIAL_NETWORK_AVAILABLE_CHANGED */);
        msg.obj = new AsyncResult(null, isAvailable, exception);
        msg.sendToTarget();
    }

    private void sendSelectedNbIotSatelliteSubscriptionChangedEvent(int selectedSubId,
            Throwable exception) {
        Message msg = mSatelliteControllerUT.obtainMessage(
                60 /* EVENT_SELECTED_NB_IOT_SATELLITE_SUBSCRIPTION_CHANGED */);
        msg.obj = new AsyncResult(null, selectedSubId, exception);
        msg.sendToTarget();
    }

    private void sendCmdEvaluateCarrierRoamingNtnEligibilityChange() {
        mSatelliteControllerUT.obtainMessage(
                61 /* CMD_EVALUATE_CARRIER_ROAMING_NTN_ELIGIBILITY_CHANGE */).sendToTarget();
    }

    private void setRadioPower(boolean on) {
        mSimulatedCommands.setRadioPower(on, false, false, null);
    }

    private void verifyRegisterForNtnSignalStrengthChanged(int subId,
            INtnSignalStrengthCallback callback, int expectedError) {
        if (expectedError == SATELLITE_RESULT_SUCCESS) {
            try {
                mSatelliteControllerUT.registerForNtnSignalStrengthChanged(callback);
            } catch (RemoteException ex) {
                throw new AssertionError();
            }
        } else {
            RemoteException ex = assertThrows(RemoteException.class,
                    () -> mSatelliteControllerUT.registerForNtnSignalStrengthChanged(callback));
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

        try {
            setSatelliteSubscriberTesting(true);
        } catch (Exception ex) {
            fail("provisionSatelliteService.setSatelliteSubscriberTesting: ex=" + ex);
        }
        doReturn(true).when(mMockSubscriptionManagerService).isSatelliteProvisionedForNonIpDatagram(
                anyInt());

        cancelRemote = mSatelliteControllerUT.provisionSatelliteService(
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
        mSatelliteControllerUT.deprovisionSatelliteService(
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

    private class TestSatelliteController extends SatelliteController {
        public boolean setSettingsKeyForSatelliteModeCalled = false;
        public boolean allRadiosDisabled = true;
        public long elapsedRealtime = 0;
        public int satelliteModeSettingValue = SATELLITE_MODE_ENABLED_FALSE;
        public boolean setSettingsKeyToAllowDeviceRotationCalled = false;
        public OutcomeReceiver<Boolean, SatelliteException> isSatelliteAllowedCallback = null;
        public static boolean isApplicationUpdated;
        public String packageName = "com.example.app";
        public boolean isSatelliteBeingDisabled = false;
        public boolean mIsApplicationSupportsP2P = false;
        public int selectedSatelliteSubId = -1;
        public boolean isSatelliteProvisioned;
        public boolean isSatelliteEnabledOrBeingEnabled = false;

        TestSatelliteController(
                Context context, Looper looper, @NonNull FeatureFlags featureFlags) {
            super(context, looper, featureFlags);
            logd("Constructing TestSatelliteController");
            isApplicationUpdated = false;
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
        protected void setSettingsKeyToAllowDeviceRotation(int val) {
            logd("setSettingsKeyToAllowDeviceRotation: val=" + val);
            setSettingsKeyToAllowDeviceRotationCalled = true;
        }

        @Override
        protected boolean areAllRadiosDisabled() {
            logd("areAllRadiosDisabled: " + allRadiosDisabled);
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

        @Override
        protected void registerForSatelliteCommunicationAllowedStateChanged() {
            logd("registerForSatelliteCommunicationAllowedStateChanged");
        }

        void setSatelliteSessionController(SatelliteSessionController satelliteSessionController) {
            mSatelliteSessionController = satelliteSessionController;
        }

        @Override
        protected void setSatellitePhone(int subId) {
            logd("setSatellitePhone");
            synchronized (mSatellitePhoneLock) {
                mSatellitePhone = mPhone;
            }
        }

        @Override
        protected void setSelectedSatelliteSubId(int subId) {
            logd("setSelectedSatelliteSubId: subId=" + subId);
            synchronized (mSatelliteTokenProvisionedLock) {
                mSelectedSatelliteSubId = subId;
            }
        }

        @Override
        protected boolean isSubscriptionProvisioned(int subId) {
            return isSatelliteProvisioned;
        }

        @Override
        protected List<DeviceState> getSupportedDeviceStates() {
            return List.of(new DeviceState(new DeviceState.Configuration.Builder(0 /* identifier */,
                    "DEFAULT" /* name */).build()));
        }

        @Override
        public boolean isSatelliteBeingDisabled() {
            return isSatelliteBeingDisabled;
        }

        @Override
        public boolean isSatelliteEnabledOrBeingEnabled() {
            return isSatelliteEnabledOrBeingEnabled;
        }

        protected String getConfigSatelliteGatewayServicePackage() {
            String packageName = "com.example.app";
            return packageName;
        }

        @Override
        protected void handleCarrierRoamingNtnAvailableServicesChanged(int subId) {
            isApplicationUpdated = true;
        }

        @Override
        public boolean isApplicationSupportsP2P(String packageName) {
            return mIsApplicationSupportsP2P;
        }

        @Override
        public int[] getSupportedServicesOnCarrierRoamingNtn(int subId) {
            return new int[]{3, 5};
        }


        void setSatelliteProvisioned(@Nullable Boolean isProvisioned) {
            synchronized (mDeviceProvisionLock) {
                mIsDeviceProvisioned = isProvisioned;
            }
        }

        void setIsSatelliteSupported(@Nullable Boolean isSatelliteSupported) {
            synchronized (mIsSatelliteSupportedLock) {
                mIsSatelliteSupported = isSatelliteSupported;
            }
        }

        public boolean isRadioOn() {
            synchronized (mIsRadioOnLock) {
                return mIsRadioOn;
            }
        }

        public boolean isRadioOffRequested() {
            synchronized (mIsRadioOnLock) {
                return mRadioOffRequested;
            }
        }

        public boolean isWaitForCellularModemOffTimerStarted() {
            return hasMessages(EVENT_WAIT_FOR_CELLULAR_MODEM_OFF_TIMED_OUT);
        }

        public Map<String, Integer> subscriberIdPerSub() {
            synchronized (mSatelliteTokenProvisionedLock) {
                return mSubscriberIdPerSub;
            }
        }

        public Map<Integer, List<SubscriptionInfo>> subsInfoListPerPriority() {
            synchronized (mSatelliteTokenProvisionedLock) {
                return mSubsInfoListPerPriority;
            }
        }

        public void evaluateESOSProfilesPrioritizationTest() {
            evaluateESOSProfilesPrioritization();
        }

        public boolean isCarrierSatelliteHigherPriorityTest(SubscriptionInfo info) {
            return isCarrierSatelliteHigherPriority(info);
        }

        public String getStringFromOverlayConfigTest(int resourceId) {
            return getStringFromOverlayConfig(resourceId);
        }

        public boolean isAnyWaitForSatelliteEnablingResponseTimerStarted() {
            return hasMessages(EVENT_WAIT_FOR_SATELLITE_ENABLING_RESPONSE_TIMED_OUT);
        }

        public int getResultReceiverTotalCount() {
            synchronized (mResultReceiverTotalCountLock) {
                return mResultReceiverTotalCount;
            }
        }

        public HashMap<String, Integer> getResultReceiverCountPerMethodMap() {
            synchronized (mResultReceiverTotalCountLock) {
                return mResultReceiverCountPerMethodMap;
            }
        }

        public void setIsSatelliteAllowedState(boolean isAllowed) {
            synchronized(mSatelliteAccessConfigLock) {
                mSatelliteAccessAllowed = isAllowed;
            }
        }
    }

    @Test
    public void testLoggingCodeForResultReceiverCount() throws Exception {
        final String callerSC =  "SC:ResultReceiver";
        final String callerSAC =  "SAC:ResultReceiver";

        doReturn(false).when(mFeatureFlags).carrierRoamingNbIotNtn();

        mSatelliteControllerUT.incrementResultReceiverCount(callerSC);
        assertEquals(0, mSatelliteControllerUT.getResultReceiverTotalCount());
        mSatelliteControllerUT.decrementResultReceiverCount(callerSC);
        assertEquals(0, mSatelliteControllerUT.getResultReceiverTotalCount());

        doReturn(true).when(mFeatureFlags).carrierRoamingNbIotNtn();

        mSatelliteControllerUT.incrementResultReceiverCount(callerSC);
        assertEquals(1, mSatelliteControllerUT.getResultReceiverTotalCount());
        assertEquals(1, mSatelliteControllerUT.getResultReceiverCountPerMethodMap().size());
        assertEquals(1, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSC)).orElse(0));
        assertEquals(0, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSAC)).orElse(0));

        mSatelliteControllerUT.incrementResultReceiverCount(callerSC);
        assertEquals(2, mSatelliteControllerUT.getResultReceiverTotalCount());
        assertEquals(1, mSatelliteControllerUT.getResultReceiverCountPerMethodMap().size());
        assertEquals(2, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSC)).orElse(0));
        assertEquals(0, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSAC)).orElse(0));

        mSatelliteControllerUT.incrementResultReceiverCount(callerSAC);
        assertEquals(3, mSatelliteControllerUT.getResultReceiverTotalCount());
        assertEquals(2, mSatelliteControllerUT.getResultReceiverCountPerMethodMap().size());
        assertEquals(2, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSC)).orElse(0));
        assertEquals(1, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSAC)).orElse(0));

        mSatelliteControllerUT.decrementResultReceiverCount(callerSC);
        assertEquals(2, mSatelliteControllerUT.getResultReceiverTotalCount());
        assertEquals(2, mSatelliteControllerUT.getResultReceiverCountPerMethodMap().size());
        assertEquals(1, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSC)).orElse(0));
        assertEquals(1, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSAC)).orElse(0));

        mSatelliteControllerUT.decrementResultReceiverCount(callerSC);
        assertEquals(1, mSatelliteControllerUT.getResultReceiverTotalCount());
        assertEquals(2, mSatelliteControllerUT.getResultReceiverCountPerMethodMap().size());
        assertEquals(0, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSC)).orElse(0));
        assertEquals(1, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSAC)).orElse(0));

        mSatelliteControllerUT.decrementResultReceiverCount(callerSAC);
        assertEquals(0, mSatelliteControllerUT.getResultReceiverTotalCount());
        assertEquals(2, mSatelliteControllerUT.getResultReceiverCountPerMethodMap().size());
        assertEquals(0, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSC)).orElse(0));
        assertEquals(0, (int) Optional.ofNullable(mSatelliteControllerUT
                .getResultReceiverCountPerMethodMap().get(callerSAC)).orElse(0));
    }

    @Test
    public void testSetNtnSmsSupportedByMessagesApp() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mSatelliteControllerUT.setNtnSmsSupportedByMessagesApp(true);
        assertTrue(mSharedPreferences.getBoolean(
                SatelliteController.NTN_SMS_SUPPORTED_BY_MESSAGES_APP_KEY, false));
    }

    private static class TestSubscriptionManager {
        public Map<Integer, Boolean> mSatelliteProvisionedForNonIpDatagram = new HashMap<>();

        public void resetProvisionMapForNonIpDatagram() {
            mSatelliteProvisionedForNonIpDatagram.clear();
        }

        public void setIsSatelliteProvisionedForNonIpDatagram(int subId, boolean provisioned) {
            mSatelliteProvisionedForNonIpDatagram.put(subId, provisioned);
        }

        public boolean isSatelliteProvisionedForNonIpDatagram(int subId) {
            Boolean isProvisioned = mSatelliteProvisionedForNonIpDatagram.get(subId);
            return isProvisioned != null ? isProvisioned : false;
        }
    }

    @Test
    public void testGetSatelliteDataPlanForPlmn_WithEntitlement() throws Exception {
        logd("testGetSatelliteDataPlanForPlmn_WithEntitlement");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        List<String> entitlementPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00103", "00104"})
                        .toList();
        List<String> barredPlmnList = new ArrayList<>();
        Map<String, Integer> dataPlanListMap = Map.of(
                "00101", SATELLITE_DATA_PLAN_METERED,
                "00103", SATELLITE_DATA_PLAN_UNMETERED);
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, dataPlanListMap, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);

        int dataPlanForPlmn;
        dataPlanForPlmn = mSatelliteControllerUT.getSatelliteDataPlanForPlmn(SUB_ID, "00101");
        assertEquals(SATELLITE_DATA_PLAN_METERED, dataPlanForPlmn);

        dataPlanForPlmn = mSatelliteControllerUT.getSatelliteDataPlanForPlmn(SUB_ID, "00103");
        assertEquals(SATELLITE_DATA_PLAN_UNMETERED, dataPlanForPlmn);
    }

    @Test
    public void testGetSatelliteDataPlanForPlmn_WithoutEntitlement() throws Exception {
        logd("testGetSatelliteDataPlanForPlmn_WithoutEntitlement");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        List<String> entitlementPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00103", "00104"})
                        .toList();
        List<String> barredPlmnList = new ArrayList<>();
        Map<String, Integer> dataPlanListMap = new HashMap<>();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, dataPlanListMap, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);

        int dataPlanForPlmn = mSatelliteControllerUT.getSatelliteDataPlanForPlmn(SUB_ID, "00101");
        assertEquals(SATELLITE_DATA_PLAN_METERED, dataPlanForPlmn);
    }

    @Test
    public void TestGetSupportedSatelliteServicesForPlmn_WithEntitlement() throws Exception {
        logd("TestGetSupportedSatelliteServicesForPlmn_WithEntitlement");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        List<String> entitlementPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00103", "00104"})
                        .toList();
        List<String> barredPlmnList = new ArrayList<>();
        Map<String, List<Integer>> serviceTypeListMap = Map.of(
                "00101", List.of(SERVICE_TYPE_DATA, SERVICE_TYPE_SMS),
                "00102", List.of(SERVICE_TYPE_VOICE, SERVICE_TYPE_SMS),
                "00103", List.of(SERVICE_TYPE_DATA, SERVICE_TYPE_VOICE, SERVICE_TYPE_SMS));
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), serviceTypeListMap,
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);

        List<Integer> allowedServiceForPlmn;
        allowedServiceForPlmn = mSatelliteControllerUT
                .getSupportedSatelliteServicesForPlmn(SUB_ID, "00101");
        assertEquals(List.of(SERVICE_TYPE_DATA, SERVICE_TYPE_SMS), allowedServiceForPlmn);

        allowedServiceForPlmn = mSatelliteControllerUT
                .getSupportedSatelliteServicesForPlmn(SUB_ID, "00102");
        assertEquals(List.of(SERVICE_TYPE_VOICE, SERVICE_TYPE_SMS), allowedServiceForPlmn);

        allowedServiceForPlmn = mSatelliteControllerUT
                .getSupportedSatelliteServicesForPlmn(SUB_ID, "00103");
        assertEquals(List.of(SERVICE_TYPE_DATA, SERVICE_TYPE_VOICE, SERVICE_TYPE_SMS),
                allowedServiceForPlmn);
    }

    @Test
    public void TestGetSupportedSatelliteServicesForPlmn_WithoutEntitlement() throws Exception {
        logd("TestGetSupportedSatelliteServicesForPlmn_WithoutAllowedServices");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        List<String> entitlementPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00103", "00104"})
                        .toList();
        List<String> barredPlmnList = new ArrayList<>();
        Map<String, Integer> dataPlanListMap =  new HashMap<>();
        Map<String, List<Integer>> allowedServiceListMap = new HashMap<>();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, dataPlanListMap, allowedServiceListMap,
                new HashMap<>(), new HashMap<>(), mIIntegerConsumer);

        // Verify whether the carrier config plmn list is returned with conditions below
        // the config data plmn list : empty
        // the carrier config plmn list : exist with services {{2}}
        setConfigData(new ArrayList<>());
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);
        PersistableBundle carrierSupportedSatelliteServicesPerProvider =
                new PersistableBundle();
        List<String> carrierConfigPlmnList = List.of("00101");
        carrierSupportedSatelliteServicesPerProvider.putIntArray(
                carrierConfigPlmnList.get(0), new int[]{2});
        mCarrierConfigBundle.putPersistableBundle(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                carrierSupportedSatelliteServicesPerProvider);
        invokeCarrierConfigChanged();

        List<Integer> servicesPerPlmn;
        servicesPerPlmn = mSatelliteControllerUT.getSupportedSatelliteServicesForPlmn(
                SUB_ID, "00101");
        assertEquals(Arrays.asList(2).stream().sorted().toList(),
                servicesPerPlmn.stream().sorted().toList());
    }

    @Test
    public void testGetSupportedSatelliteDataModeForPlmn_WithEntitlement() throws Exception {
        logd("testGetSupportedSatelliteDataModeForPlmn_WithEntitlement");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        List<String> entitlementPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00103", "00104"})
                        .toList();
        List<String> barredPlmnList = new ArrayList<>();
        Map<String, Integer> dataServicePolicyMap = Map.of(
                "00101", SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED,
                "00102", SATELLITE_DATA_SUPPORT_ALL
        );
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                dataServicePolicyMap, new HashMap<>(), mIIntegerConsumer);

        int dataSupportModeForPlmn;
        dataSupportModeForPlmn = mSatelliteControllerUT
                .getSatelliteDataServicePolicyForPlmn(SUB_ID, "00101");
        assertEquals(SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED, dataSupportModeForPlmn);

        dataSupportModeForPlmn = mSatelliteControllerUT
                .getSatelliteDataServicePolicyForPlmn(SUB_ID, "00102");
        assertEquals(SATELLITE_DATA_SUPPORT_ALL, dataSupportModeForPlmn);

    }

    @Test
    public void testGetSupportedSatelliteDataModeForPlmn_WithoutEntitlement() throws Exception {
        logd("testGetSupportedSatelliteDataModeForPlmn_WithoutEntitlement");
        when(mFeatureFlags.carrierEnabledSatelliteFlag()).thenReturn(true);

        replaceInstance(SatelliteController.class, "mMergedPlmnListPerCarrier",
                mSatelliteControllerUT, new SparseArray<>());
        List<String> overlayConfigPlmnList = new ArrayList<>();
        replaceInstance(SatelliteController.class, "mSatellitePlmnListFromOverlayConfig",
                mSatelliteControllerUT, overlayConfigPlmnList);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        mCarrierConfigBundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                true);

        List<String> entitlementPlmnList =
                Arrays.stream(new String[]{"00101", "00102", "00103", "00104"})
                        .toList();
        List<String> barredPlmnList = new ArrayList<>();
        Map<String, Integer> dataServicePolicyMap = new HashMap<>();
        mSatelliteControllerUT.onSatelliteEntitlementStatusUpdated(SUB_ID, false,
                entitlementPlmnList, barredPlmnList, new HashMap<>(), new HashMap<>(),
                dataServicePolicyMap, new HashMap<>(), mIIntegerConsumer);

        mCarrierConfigBundle.putInt(
                CarrierConfigManager.KEY_SATELLITE_DATA_SUPPORT_MODE_INT,
                SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED);
        int dataSupportModeForPlmn = mSatelliteControllerUT
                .getSatelliteDataServicePolicyForPlmn(SUB_ID, "00101");
        assertEquals(SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED, dataSupportModeForPlmn);
    }

    @Test
    public void testEvaluateCarrierRoamingNtnEligibilityChange_inSatelliteMode() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        mSatelliteControllerUT.isSatelliteEnabledOrBeingEnabled = true;
        mSatelliteControllerUT.setSatellitePhone(1);
        mSatelliteControllerUT.setSelectedSatelliteSubId(SUB_ID);
        mSatelliteControllerUT.isSatelliteProvisioned = true;
        mSatelliteControllerUT.isSatelliteAllowedCallback = null;
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        mSatelliteControllerUT.setIsSatelliteAllowedState(true);
        sendCmdEvaluateCarrierRoamingNtnEligibilityChange();
        processAllMessages();
        verify(mPhone, times(0)).notifyCarrierRoamingNtnEligibleStateChanged(anyBoolean());
    }
}
