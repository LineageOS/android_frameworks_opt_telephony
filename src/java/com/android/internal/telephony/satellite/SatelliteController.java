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

import static android.telephony.CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT;
import static android.telephony.SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER;
import static android.telephony.SubscriptionManager.isValidSubscriptionId;
import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911;
import static android.telephony.satellite.SatelliteManager.KEY_NTN_SIGNAL_STRENGTH;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import android.annotation.ArrayRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.satellite.INtnSignalStrengthCallback;
import android.telephony.satellite.ISatelliteCapabilitiesCallback;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.ISatelliteStateCallback;
import android.telephony.satellite.ISatelliteTransmissionUpdateCallback;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.uwb.UwbManager;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DeviceStateMonitor;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.metrics.ControllerMetricsStats;
import com.android.internal.telephony.satellite.metrics.ProvisionMetricsStats;
import com.android.internal.telephony.satellite.metrics.SessionMetricsStats;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.internal.util.FunctionalUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Satellite controller is the backend service of
 * {@link android.telephony.satellite.SatelliteManager}.
 */
public class SatelliteController extends Handler {
    private static final String TAG = "SatelliteController";
    /** Whether enabling verbose debugging message or not. */
    private static final boolean DBG = false;
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    /** File used to store shared preferences related to satellite. */
    public static final String SATELLITE_SHARED_PREF = "satellite_shared_pref";
    /** Value to pass for the setting key SATELLITE_MODE_ENABLED, enabled = 1, disabled = 0 */
    public static final int SATELLITE_MODE_ENABLED_TRUE = 1;
    public static final int SATELLITE_MODE_ENABLED_FALSE = 0;
    public static final int INVALID_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE = -1;

    /** Message codes used in handleMessage() */
    //TODO: Move the Commands and events related to position updates to PointingAppController
    private static final int CMD_START_SATELLITE_TRANSMISSION_UPDATES = 1;
    private static final int EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE = 2;
    private static final int CMD_STOP_SATELLITE_TRANSMISSION_UPDATES = 3;
    private static final int EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE = 4;
    private static final int CMD_PROVISION_SATELLITE_SERVICE = 7;
    private static final int EVENT_PROVISION_SATELLITE_SERVICE_DONE = 8;
    private static final int CMD_DEPROVISION_SATELLITE_SERVICE = 9;
    private static final int EVENT_DEPROVISION_SATELLITE_SERVICE_DONE = 10;
    private static final int CMD_SET_SATELLITE_ENABLED = 11;
    private static final int EVENT_SET_SATELLITE_ENABLED_DONE = 12;
    private static final int CMD_IS_SATELLITE_ENABLED = 13;
    private static final int EVENT_IS_SATELLITE_ENABLED_DONE = 14;
    private static final int CMD_IS_SATELLITE_SUPPORTED = 15;
    private static final int EVENT_IS_SATELLITE_SUPPORTED_DONE = 16;
    private static final int CMD_GET_SATELLITE_CAPABILITIES = 17;
    private static final int EVENT_GET_SATELLITE_CAPABILITIES_DONE = 18;
    private static final int CMD_IS_SATELLITE_COMMUNICATION_ALLOWED = 19;
    private static final int EVENT_IS_SATELLITE_COMMUNICATION_ALLOWED_DONE = 20;
    private static final int CMD_GET_TIME_SATELLITE_NEXT_VISIBLE = 21;
    private static final int EVENT_GET_TIME_SATELLITE_NEXT_VISIBLE_DONE = 22;
    private static final int EVENT_RADIO_STATE_CHANGED = 23;
    private static final int CMD_IS_SATELLITE_PROVISIONED = 24;
    private static final int EVENT_IS_SATELLITE_PROVISIONED_DONE = 25;
    private static final int EVENT_SATELLITE_PROVISION_STATE_CHANGED = 26;
    private static final int EVENT_PENDING_DATAGRAMS = 27;
    private static final int EVENT_SATELLITE_MODEM_STATE_CHANGED = 28;
    private static final int EVENT_SET_SATELLITE_PLMN_INFO_DONE = 29;
    private static final int CMD_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE = 30;
    private static final int EVENT_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE_DONE = 31;
    private static final int CMD_REQUEST_NTN_SIGNAL_STRENGTH = 32;
    private static final int EVENT_REQUEST_NTN_SIGNAL_STRENGTH_DONE = 33;
    private static final int EVENT_NTN_SIGNAL_STRENGTH_CHANGED = 34;
    private static final int CMD_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING = 35;
    private static final int EVENT_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING_DONE = 36;
    private static final int EVENT_SERVICE_STATE_CHANGED = 37;
    private static final int EVENT_SATELLITE_CAPABILITIES_CHANGED = 38;

    @NonNull private static SatelliteController sInstance;
    @NonNull private final Context mContext;
    @NonNull private final SatelliteModemInterface mSatelliteModemInterface;
    @NonNull private SatelliteSessionController mSatelliteSessionController;
    @NonNull private final PointingAppController mPointingAppController;
    @NonNull private final DatagramController mDatagramController;
    @NonNull private final ControllerMetricsStats mControllerMetricsStats;
    @NonNull private final ProvisionMetricsStats mProvisionMetricsStats;
    @NonNull private final SubscriptionManagerService mSubscriptionManagerService;
    private final CommandsInterface mCi;
    private ContentResolver mContentResolver;
    private final DeviceStateMonitor mDSM;

    private final Object mRadioStateLock = new Object();

    /** Flags to indicate whether the respective radio is enabled */
    @GuardedBy("mRadioStateLock")
    private boolean mBTStateEnabled = false;
    @GuardedBy("mRadioStateLock")
    private boolean mNfcStateEnabled = false;
    @GuardedBy("mRadioStateLock")
    private boolean mUwbStateEnabled = false;
    @GuardedBy("mRadioStateLock")
    private boolean mWifiStateEnabled = false;

    // Flags to indicate that respective radios need to be disabled when satellite is enabled
    private boolean mDisableBTOnSatelliteEnabled = false;
    private boolean mDisableNFCOnSatelliteEnabled = false;
    private boolean mDisableUWBOnSatelliteEnabled = false;
    private boolean mDisableWifiOnSatelliteEnabled = false;

    private final Object mSatelliteEnabledRequestLock = new Object();
    @GuardedBy("mSatelliteEnabledRequestLock")
    private RequestSatelliteEnabledArgument mSatelliteEnabledRequest = null;
    /** Flag to indicate that satellite is enabled successfully
     * and waiting for all the radios to be disabled so that success can be sent to callback
     */
    @GuardedBy("mSatelliteEnabledRequestLock")
    private boolean mWaitingForRadioDisabled = false;

    private boolean mWaitingForDisableSatelliteModemResponse = false;
    private boolean mWaitingForSatelliteModemOff = false;

    private final AtomicBoolean mRegisteredForProvisionStateChangedWithSatelliteService =
            new AtomicBoolean(false);
    private final AtomicBoolean mRegisteredForPendingDatagramCountWithSatelliteService =
            new AtomicBoolean(false);
    private final AtomicBoolean mRegisteredForSatelliteModemStateChangedWithSatelliteService =
            new AtomicBoolean(false);
    private final AtomicBoolean mRegisteredForNtnSignalStrengthChanged = new AtomicBoolean(false);
    private final AtomicBoolean mRegisteredForSatelliteCapabilitiesChanged =
            new AtomicBoolean(false);
    private final AtomicBoolean mShouldReportNtnSignalStrength = new AtomicBoolean(false);
    /**
     * Map key: subId, value: callback to get error code of the provision request.
     */
    private final ConcurrentHashMap<Integer, Consumer<Integer>> mSatelliteProvisionCallbacks =
            new ConcurrentHashMap<>();

    /**
     * Map key: binder of the callback, value: callback to receive provision state changed events.
     */
    private final ConcurrentHashMap<IBinder, ISatelliteProvisionStateCallback>
            mSatelliteProvisionStateChangedListeners = new ConcurrentHashMap<>();
    /**
     * Map key: binder of the callback, value: callback to receive non-terrestrial signal strength
     * state changed events.
     */
    private final ConcurrentHashMap<IBinder, INtnSignalStrengthCallback>
            mNtnSignalStrengthChangedListeners = new ConcurrentHashMap<>();
    /**
     * Map key: binder of the callback, value: callback to receive satellite capabilities changed
     * events.
     */
    private final ConcurrentHashMap<IBinder, ISatelliteCapabilitiesCallback>
            mSatelliteCapabilitiesChangedListeners = new ConcurrentHashMap<>();
    private final Object mIsSatelliteSupportedLock = new Object();
    @GuardedBy("mIsSatelliteSupportedLock")
    private Boolean mIsSatelliteSupported = null;
    private boolean mIsDemoModeEnabled = false;
    private final Object mIsSatelliteEnabledLock = new Object();
    @GuardedBy("mIsSatelliteEnabledLock")
    private Boolean mIsSatelliteEnabled = null;
    private boolean mIsRadioOn = false;
    private final Object mIsSatelliteProvisionedLock = new Object();
    @GuardedBy("mIsSatelliteProvisionedLock")
    private Boolean mIsSatelliteProvisioned = null;
    private final Object mSatelliteCapabilitiesLock = new Object();
    @GuardedBy("mSatelliteCapabilitiesLock")
    private SatelliteCapabilities mSatelliteCapabilities;
    private final Object mNeedsSatellitePointingLock = new Object();
    @GuardedBy("mNeedsSatellitePointingLock")
    private boolean mNeedsSatellitePointing = false;
    private final Object mNtnSignalsStrengthLock = new Object();
    @GuardedBy("mNtnSignalsStrengthLock")
    private NtnSignalStrength mNtnSignalStrength =
            new NtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE);
    /** Key: subId, value: (key: PLMN, value: set of
     * {@link android.telephony.NetworkRegistrationInfo.ServiceType})
     */
    @GuardedBy("mSupportedSatelliteServicesLock")
    @NonNull private final Map<Integer, Map<String, Set<Integer>>>
            mSatelliteServicesSupportedByCarriers = new HashMap<>();
    @NonNull private final Object mSupportedSatelliteServicesLock = new Object();
    @NonNull private final List<String> mSatellitePlmnListFromOverlayConfig;
    @NonNull private final CarrierConfigManager mCarrierConfigManager;
    @NonNull private final CarrierConfigManager.CarrierConfigChangeListener
            mCarrierConfigChangeListener;
    @NonNull private final Object mCarrierConfigArrayLock = new Object();
    @GuardedBy("mCarrierConfigArrayLock")
    @NonNull private final SparseArray<PersistableBundle> mCarrierConfigArray = new SparseArray<>();
    @GuardedBy("mIsSatelliteEnabledLock")
    /** Key: Subscription ID, value: set of restriction reasons for satellite communication.*/
    @NonNull private final Map<Integer, Set<Integer>> mSatelliteAttachRestrictionForCarrierArray =
            new HashMap<>();
    @GuardedBy("mIsSatelliteEnabledLock")
    /** Key: Subscription ID, value: the actual satellite enabled state in the modem -
     * {@code true} for enabled and {@code false} for disabled. */
    @NonNull private final Map<Integer, Boolean> mIsSatelliteAttachEnabledForCarrierArrayPerSub =
            new HashMap<>();
    @NonNull private final FeatureFlags mFeatureFlags;
    @NonNull private final Object mSatelliteConnectedLock = new Object();
    /** Key: Subscription ID; Value: Last satellite connected time */
    @GuardedBy("mSatelliteConnectedLock")
    @NonNull private final SparseArray<Long> mLastSatelliteDisconnectedTimesMillis =
            new SparseArray<>();
    /**
     * Key: Subscription ID; Value: {@code true} if satellite was just connected,
     * {@code false} otherwise.
     */
    @GuardedBy("mSatelliteConnectedLock")
    @NonNull private final SparseBooleanArray
            mWasSatelliteConnectedViaCarrier = new SparseBooleanArray();

    @GuardedBy("mSatelliteConnectedLock")
    @NonNull private final SparseBooleanArray
            mIsSatelliteConnectedViaCarrierHysteresisTimeExpired = new SparseBooleanArray();

    /**
     * This is used for testing only. When mEnforcedEmergencyCallToSatelliteHandoverType is valid,
     * Telephony will ignore the IMS registration status and cellular availability, and always send
     * the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE to Dialer.
     */
    private int mEnforcedEmergencyCallToSatelliteHandoverType =
            INVALID_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
    private int mDelayInSendingEventDisplayEmergencyMessage = 0;

    /**
     * @return The singleton instance of SatelliteController.
     */
    public static SatelliteController getInstance() {
        if (sInstance == null) {
            loge("SatelliteController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the SatelliteController singleton instance.
     * @param context The Context to use to create the SatelliteController.
     * @param featureFlags The feature flag.
     */
    public static void make(@NonNull Context context, @NonNull FeatureFlags featureFlags) {
        if (sInstance == null) {
            HandlerThread satelliteThread = new HandlerThread(TAG);
            satelliteThread.start();
            sInstance = new SatelliteController(context, satelliteThread.getLooper(), featureFlags);
        }
    }

    /**
     * Create a SatelliteController to act as a backend service of
     * {@link android.telephony.satellite.SatelliteManager}
     *
     * @param context The Context for the SatelliteController.
     * @param looper The looper for the handler. It does not run on main thread.
     * @param featureFlags The feature flag.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public SatelliteController(
            @NonNull Context context, @NonNull Looper looper, @NonNull FeatureFlags featureFlags) {
        super(looper);

        mContext = context;
        mFeatureFlags = featureFlags;
        Phone phone = SatelliteServiceUtils.getPhone();
        mCi = phone.mCi;
        mDSM = phone.getDeviceStateMonitor();
        // Create the SatelliteModemInterface singleton, which is used to manage connections
        // to the satellite service and HAL interface.
        mSatelliteModemInterface = SatelliteModemInterface.make(mContext, this);

        // Create the PointingUIController singleton,
        // which is used to manage interactions with PointingUI app.
        mPointingAppController = PointingAppController.make(mContext);

        // Create the SatelliteControllerMetrics to report controller metrics
        // should be called before making DatagramController
        mControllerMetricsStats = ControllerMetricsStats.make(mContext);
        mProvisionMetricsStats = ProvisionMetricsStats.getOrCreateInstance();
        mSubscriptionManagerService = SubscriptionManagerService.getInstance();

        // Create the DatagramController singleton,
        // which is used to send and receive satellite datagrams.
        mDatagramController = DatagramController.make(mContext, looper, mPointingAppController);

        requestIsSatelliteSupported(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                new ResultReceiver(this) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        logd("requestIsSatelliteSupported: resultCode=" + resultCode);
                    }
                });
        mCi.registerForRadioStateChanged(this, EVENT_RADIO_STATE_CHANGED, null);
        mIsRadioOn = phone.isRadioOn();
        registerForSatelliteProvisionStateChanged();
        registerForPendingDatagramCount();
        registerForSatelliteModemStateChanged();
        registerForServiceStateChanged();
        mContentResolver = mContext.getContentResolver();
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);

        initializeSatelliteModeRadios();

        ContentObserver satelliteModeRadiosContentObserver = new ContentObserver(this) {
            @Override
            public void onChange(boolean selfChange) {
                initializeSatelliteModeRadios();
            }
        };
        if (mContentResolver != null) {
            mContentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.SATELLITE_MODE_RADIOS),
                    false, satelliteModeRadiosContentObserver);
        }

        mSatellitePlmnListFromOverlayConfig = readSatellitePlmnsFromOverlayConfig();
        updateSupportedSatelliteServicesForActiveSubscriptions();
        mCarrierConfigChangeListener =
                (slotIndex, subId, carrierId, specificCarrierId) ->
                        handleCarrierConfigChanged(slotIndex, subId, carrierId, specificCarrierId);
        mCarrierConfigManager.registerCarrierConfigChangeListener(
                        new HandlerExecutor(new Handler(looper)), mCarrierConfigChangeListener);
        mDSM.registerForSignalStrengthReportDecision(this, CMD_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING,
                null);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected void initializeSatelliteModeRadios() {
        if (mContentResolver != null) {
            BTWifiNFCStateReceiver bTWifiNFCSateReceiver = new BTWifiNFCStateReceiver();
            UwbAdapterStateCallback uwbAdapterStateCallback = new UwbAdapterStateCallback();
            IntentFilter radioStateIntentFilter = new IntentFilter();

            synchronized (mRadioStateLock) {
                // Initialize radio states to default value
                mDisableBTOnSatelliteEnabled = false;
                mDisableNFCOnSatelliteEnabled = false;
                mDisableWifiOnSatelliteEnabled = false;
                mDisableUWBOnSatelliteEnabled = false;

                mBTStateEnabled = false;
                mNfcStateEnabled = false;
                mWifiStateEnabled = false;
                mUwbStateEnabled = false;

                // Read satellite mode radios from settings
                String satelliteModeRadios = Settings.Global.getString(mContentResolver,
                        Settings.Global.SATELLITE_MODE_RADIOS);
                if (satelliteModeRadios == null) {
                    loge("initializeSatelliteModeRadios: satelliteModeRadios is null");
                    return;
                }
                logd("Radios To be checked when satellite is on: " + satelliteModeRadios);

                if (satelliteModeRadios.contains(Settings.Global.RADIO_BLUETOOTH)) {
                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (bluetoothAdapter != null) {
                        mDisableBTOnSatelliteEnabled = true;
                        mBTStateEnabled = bluetoothAdapter.isEnabled();
                        radioStateIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                    }
                }

                if (satelliteModeRadios.contains(Settings.Global.RADIO_NFC)) {
                    Context applicationContext = mContext.getApplicationContext();
                    NfcAdapter nfcAdapter = null;
                    if (applicationContext != null) {
                        nfcAdapter = NfcAdapter.getDefaultAdapter(mContext.getApplicationContext());
                    }
                    if (nfcAdapter != null) {
                        mDisableNFCOnSatelliteEnabled = true;
                        mNfcStateEnabled = nfcAdapter.isEnabled();
                        radioStateIntentFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
                    }
                }

                if (satelliteModeRadios.contains(Settings.Global.RADIO_WIFI)) {
                    WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
                    if (wifiManager != null) {
                        mDisableWifiOnSatelliteEnabled = true;
                        mWifiStateEnabled = wifiManager.isWifiEnabled();
                        radioStateIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
                    }
                }
                mContext.registerReceiver(bTWifiNFCSateReceiver, radioStateIntentFilter);

                if (satelliteModeRadios.contains(Settings.Global.RADIO_UWB)) {
                    UwbManager uwbManager = mContext.getSystemService(UwbManager.class);
                    if (uwbManager != null) {
                        mDisableUWBOnSatelliteEnabled = true;
                        mUwbStateEnabled = uwbManager.isUwbEnabled();
                        final long identity = Binder.clearCallingIdentity();
                        try {
                            uwbManager.registerAdapterStateCallback(mContext.getMainExecutor(),
                                    uwbAdapterStateCallback);
                        } finally {
                            Binder.restoreCallingIdentity(identity);
                        }
                    }
                }

                logd("mDisableBTOnSatelliteEnabled: " + mDisableBTOnSatelliteEnabled
                        + " mDisableNFCOnSatelliteEnabled: " + mDisableNFCOnSatelliteEnabled
                        + " mDisableWifiOnSatelliteEnabled: " + mDisableWifiOnSatelliteEnabled
                        + " mDisableUWBOnSatelliteEnabled: " + mDisableUWBOnSatelliteEnabled);

                logd("mBTStateEnabled: " + mBTStateEnabled
                        + " mNfcStateEnabled: " + mNfcStateEnabled
                        + " mWifiStateEnabled: " + mWifiStateEnabled
                        + " mUwbStateEnabled: " + mUwbStateEnabled);
            }
        }
    }

    protected class UwbAdapterStateCallback implements UwbManager.AdapterStateCallback {

        public String toString(int state) {
            switch (state) {
                case UwbManager.AdapterStateCallback.STATE_DISABLED:
                    return "Disabled";

                case UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE:
                    return "Inactive";

                case UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE:
                    return "Active";

                default:
                    return "";
            }
        }

        @Override
        public void onStateChanged(int state, int reason) {
            logd("UwbAdapterStateCallback#onStateChanged() called, state = " + toString(state));
            logd("Adapter state changed reason " + String.valueOf(reason));
            synchronized (mRadioStateLock) {
                if (state == UwbManager.AdapterStateCallback.STATE_DISABLED) {
                    mUwbStateEnabled = false;
                    evaluateToSendSatelliteEnabledSuccess();
                } else {
                    mUwbStateEnabled = true;
                }
                logd("mUwbStateEnabled: " + mUwbStateEnabled);
            }
        }
    }

    protected class BTWifiNFCStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                logd("BTWifiNFCStateReceiver NULL action for intent " + intent);
                return;
            }

            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    synchronized (mRadioStateLock) {
                        boolean currentBTStateEnabled = mBTStateEnabled;
                        if (btState == BluetoothAdapter.STATE_OFF) {
                            mBTStateEnabled = false;
                            evaluateToSendSatelliteEnabledSuccess();
                        } else if (btState == BluetoothAdapter.STATE_ON) {
                            mBTStateEnabled = true;
                        }
                        if (currentBTStateEnabled != mBTStateEnabled) {
                            logd("mBTStateEnabled=" + mBTStateEnabled);
                        }
                    }
                    break;

                case NfcAdapter.ACTION_ADAPTER_STATE_CHANGED:
                    int nfcState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1);
                    synchronized (mRadioStateLock) {
                        boolean currentNfcStateEnabled = mNfcStateEnabled;
                        if (nfcState == NfcAdapter.STATE_ON) {
                            mNfcStateEnabled = true;
                        } else if (nfcState == NfcAdapter.STATE_OFF) {
                            mNfcStateEnabled = false;
                            evaluateToSendSatelliteEnabledSuccess();
                        }
                        if (currentNfcStateEnabled != mNfcStateEnabled) {
                            logd("mNfcStateEnabled=" + mNfcStateEnabled);
                        }
                    }
                    break;

                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN);
                    synchronized (mRadioStateLock) {
                        boolean currentWifiStateEnabled = mWifiStateEnabled;
                        if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                            mWifiStateEnabled = true;
                        } else if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                            mWifiStateEnabled = false;
                            evaluateToSendSatelliteEnabledSuccess();
                        }
                        if (currentWifiStateEnabled != mWifiStateEnabled) {
                            logd("mWifiStateEnabled=" + mWifiStateEnabled);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static final class SatelliteControllerHandlerRequest {
        /** The argument to use for the request */
        public @NonNull Object argument;
        /** The caller needs to specify the phone to be used for the request */
        public @NonNull Phone phone;
        /** The result of the request that is run on the main thread */
        public @Nullable Object result;

        SatelliteControllerHandlerRequest(Object argument, Phone phone) {
            this.argument = argument;
            this.phone = phone;
        }
    }

    private static final class RequestSatelliteEnabledArgument {
        public boolean enableSatellite;
        public boolean enableDemoMode;
        @NonNull public Consumer<Integer> callback;

        RequestSatelliteEnabledArgument(boolean enableSatellite, boolean enableDemoMode,
                Consumer<Integer> callback) {
            this.enableSatellite = enableSatellite;
            this.enableDemoMode = enableDemoMode;
            this.callback = callback;
        }
    }

    private static final class RequestHandleSatelliteAttachRestrictionForCarrierArgument {
        public int subId;
        @SatelliteManager.SatelliteCommunicationRestrictionReason
        public int reason;
        @NonNull public Consumer<Integer> callback;

        RequestHandleSatelliteAttachRestrictionForCarrierArgument(int subId,
                @SatelliteManager.SatelliteCommunicationRestrictionReason int reason,
                Consumer<Integer> callback) {
            this.subId = subId;
            this.reason = reason;
            this.callback = callback;
        }
    }

    private static final class ProvisionSatelliteServiceArgument {
        @NonNull public String token;
        @NonNull public byte[] provisionData;
        @NonNull public Consumer<Integer> callback;
        public int subId;

        ProvisionSatelliteServiceArgument(String token, byte[] provisionData,
                Consumer<Integer> callback, int subId) {
            this.token = token;
            this.provisionData = provisionData;
            this.callback = callback;
            this.subId = subId;
        }
    }

    /**
     * Arguments to send to SatelliteTransmissionUpdate registrants
     */
    public static final class SatelliteTransmissionUpdateArgument {
        @NonNull public Consumer<Integer> errorCallback;
        @NonNull public ISatelliteTransmissionUpdateCallback callback;
        public int subId;

        SatelliteTransmissionUpdateArgument(Consumer<Integer> errorCallback,
                ISatelliteTransmissionUpdateCallback callback, int subId) {
            this.errorCallback = errorCallback;
            this.callback = callback;
            this.subId = subId;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        SatelliteControllerHandlerRequest request;
        Message onCompleted;
        AsyncResult ar;

        switch(msg.what) {
            case CMD_START_SATELLITE_TRANSMISSION_UPDATES: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE, request);
                mPointingAppController.startSatelliteTransmissionUpdates(onCompleted);
                break;
            }

            case EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE: {
                handleStartSatelliteTransmissionUpdatesDone((AsyncResult) msg.obj);
                break;
            }

            case CMD_STOP_SATELLITE_TRANSMISSION_UPDATES: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE, request);
                mPointingAppController.stopSatelliteTransmissionUpdates(onCompleted);
                break;
            }

            case EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =  SatelliteServiceUtils.getSatelliteError(ar,
                        "stopSatelliteTransmissionUpdates");
                ((Consumer<Integer>) request.argument).accept(error);
                break;
            }

            case CMD_PROVISION_SATELLITE_SERVICE: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                ProvisionSatelliteServiceArgument argument =
                        (ProvisionSatelliteServiceArgument) request.argument;
                if (mSatelliteProvisionCallbacks.containsKey(argument.subId)) {
                    argument.callback.accept(
                            SatelliteManager.SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS);
                    notifyRequester(request);
                    break;
                }
                mSatelliteProvisionCallbacks.put(argument.subId, argument.callback);
                onCompleted = obtainMessage(EVENT_PROVISION_SATELLITE_SERVICE_DONE, request);
                // Log the current time for provision triggered
                mProvisionMetricsStats.setProvisioningStartTime();
                mSatelliteModemInterface.provisionSatelliteService(argument.token,
                        argument.provisionData, onCompleted);
                break;
            }

            case EVENT_PROVISION_SATELLITE_SERVICE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int errorCode =  SatelliteServiceUtils.getSatelliteError(ar,
                        "provisionSatelliteService");
                handleEventProvisionSatelliteServiceDone(
                        (ProvisionSatelliteServiceArgument) request.argument, errorCode);
                notifyRequester(request);
                break;
            }

            case CMD_DEPROVISION_SATELLITE_SERVICE: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                ProvisionSatelliteServiceArgument argument =
                        (ProvisionSatelliteServiceArgument) request.argument;
                onCompleted = obtainMessage(EVENT_DEPROVISION_SATELLITE_SERVICE_DONE, request);
                if (argument.callback != null) {
                    mProvisionMetricsStats.setProvisioningStartTime();
                }
                mSatelliteModemInterface.deprovisionSatelliteService(argument.token, onCompleted);
                break;
            }

            case EVENT_DEPROVISION_SATELLITE_SERVICE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int errorCode =  SatelliteServiceUtils.getSatelliteError(ar,
                        "deprovisionSatelliteService");
                handleEventDeprovisionSatelliteServiceDone(
                        (ProvisionSatelliteServiceArgument) request.argument, errorCode);
                break;
            }

            case CMD_SET_SATELLITE_ENABLED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                handleSatelliteEnabled(request);
                break;
            }

            case EVENT_SET_SATELLITE_ENABLED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                RequestSatelliteEnabledArgument argument =
                        (RequestSatelliteEnabledArgument) request.argument;
                int error =  SatelliteServiceUtils.getSatelliteError(ar, "setSatelliteEnabled");
                logd("EVENT_SET_SATELLITE_ENABLED_DONE = " + error);

                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (argument.enableSatellite) {
                        synchronized (mSatelliteEnabledRequestLock) {
                            mWaitingForRadioDisabled = true;
                        }
                        setSettingsKeyForSatelliteMode(SATELLITE_MODE_ENABLED_TRUE);

                        /**
                         * TODO for NTN-based satellites: Check if satellite is acquired.
                         */
                        if (mNeedsSatellitePointing) {
                            mPointingAppController.startPointingUI(false);
                        }
                        evaluateToSendSatelliteEnabledSuccess();
                    } else {
                        /**
                         * Unregister Importance Listener for Pointing UI
                         * when Satellite is disabled
                         */
                        if (mNeedsSatellitePointing) {
                            mPointingAppController.removeListenerForPointingUI();
                        }
                        synchronized (mSatelliteEnabledRequestLock) {
                            if (mSatelliteEnabledRequest != null &&
                                    mSatelliteEnabledRequest.enableSatellite == true &&
                                    argument.enableSatellite == false && mWaitingForRadioDisabled) {
                                // Previous mSatelliteEnabledRequest is successful but waiting for
                                // all radios to be turned off.
                                mSatelliteEnabledRequest.callback.accept(
                                        SATELLITE_RESULT_SUCCESS);
                            }
                        }

                        synchronized (mIsSatelliteEnabledLock) {
                            if (!mWaitingForSatelliteModemOff) {
                                moveSatelliteToOffStateAndCleanUpResources(
                                        SATELLITE_RESULT_SUCCESS,
                                        argument.callback);
                            } else {
                                logd("Wait for satellite modem off before updating satellite"
                                        + " modem state");
                            }
                            mWaitingForDisableSatelliteModemResponse = false;
                        }
                    }
                } else {
                    synchronized (mSatelliteEnabledRequestLock) {
                        if (mSatelliteEnabledRequest != null &&
                                mSatelliteEnabledRequest.enableSatellite == true &&
                                argument.enableSatellite == false && mWaitingForRadioDisabled) {
                            // Previous mSatelliteEnabledRequest is successful but waiting for
                            // all radios to be turned off.
                            mSatelliteEnabledRequest.callback.accept(
                                    SATELLITE_RESULT_SUCCESS);
                        }
                    }
                    resetSatelliteEnabledRequest();

                    // If Satellite enable/disable request returned Error, no need to wait for radio
                    argument.callback.accept(error);
                }

                if (argument.enableSatellite) {
                    if (error == SATELLITE_RESULT_SUCCESS) {
                        mControllerMetricsStats.onSatelliteEnabled();
                        mControllerMetricsStats.reportServiceEnablementSuccessCount();
                    } else {
                        mControllerMetricsStats.reportServiceEnablementFailCount();
                    }
                    SessionMetricsStats.getInstance()
                            .setInitializationResult(error)
                            .setRadioTechnology(getSupportedNtnRadioTechnology())
                            .reportSessionMetrics();
                } else {
                    mControllerMetricsStats.onSatelliteDisabled();
                    synchronized (mIsSatelliteEnabledLock) {
                        mWaitingForDisableSatelliteModemResponse = false;
                    }
                }
                break;
            }

            case CMD_IS_SATELLITE_ENABLED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_IS_SATELLITE_ENABLED_DONE, request);
                mSatelliteModemInterface.requestIsSatelliteEnabled(onCompleted);
                break;
            }

            case EVENT_IS_SATELLITE_ENABLED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =  SatelliteServiceUtils.getSatelliteError(ar,
                        "isSatelliteEnabled");
                Bundle bundle = new Bundle();
                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (ar.result == null) {
                        loge("isSatelliteEnabled: result is null");
                        error = SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
                    } else {
                        boolean enabled = ((int[]) ar.result)[0] == 1;
                        if (DBG) logd("isSatelliteEnabled: " + enabled);
                        bundle.putBoolean(SatelliteManager.KEY_SATELLITE_ENABLED, enabled);
                        updateSatelliteEnabledState(enabled, "EVENT_IS_SATELLITE_ENABLED_DONE");
                    }
                } else if (error == SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED) {
                    updateSatelliteSupportedStateWhenSatelliteServiceConnected(false);
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_IS_SATELLITE_SUPPORTED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_IS_SATELLITE_SUPPORTED_DONE, request);
                mSatelliteModemInterface.requestIsSatelliteSupported(onCompleted);
                break;
            }

            case EVENT_IS_SATELLITE_SUPPORTED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =  SatelliteServiceUtils.getSatelliteError(ar, "isSatelliteSupported");
                Bundle bundle = new Bundle();
                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (ar.result == null) {
                        loge("isSatelliteSupported: result is null");
                        error = SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
                    } else {
                        boolean supported = (boolean) ar.result;
                        if (DBG) logd("isSatelliteSupported: " + supported);
                        bundle.putBoolean(SatelliteManager.KEY_SATELLITE_SUPPORTED, supported);
                        updateSatelliteSupportedStateWhenSatelliteServiceConnected(supported);
                    }
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_GET_SATELLITE_CAPABILITIES: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_GET_SATELLITE_CAPABILITIES_DONE, request);
                mSatelliteModemInterface.requestSatelliteCapabilities(onCompleted);
                break;
            }

            case EVENT_GET_SATELLITE_CAPABILITIES_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =  SatelliteServiceUtils.getSatelliteError(ar,
                        "getSatelliteCapabilities");
                Bundle bundle = new Bundle();
                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (ar.result == null) {
                        loge("getSatelliteCapabilities: result is null");
                        error = SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
                    } else {
                        SatelliteCapabilities capabilities = (SatelliteCapabilities) ar.result;
                        synchronized (mNeedsSatellitePointingLock) {
                            mNeedsSatellitePointing = capabilities.isPointingRequired();
                        }
                        if (DBG) logd("getSatelliteCapabilities: " + capabilities);
                        bundle.putParcelable(SatelliteManager.KEY_SATELLITE_CAPABILITIES,
                                capabilities);
                        synchronized (mSatelliteCapabilitiesLock) {
                            mSatelliteCapabilities = capabilities;
                        }
                    }
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_IS_SATELLITE_COMMUNICATION_ALLOWED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_IS_SATELLITE_COMMUNICATION_ALLOWED_DONE, request);
                mSatelliteModemInterface
                        .requestIsSatelliteCommunicationAllowedForCurrentLocation(onCompleted);
                break;
            }

            case EVENT_IS_SATELLITE_COMMUNICATION_ALLOWED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =  SatelliteServiceUtils.getSatelliteError(ar,
                        "isSatelliteCommunicationAllowedForCurrentLocation");
                Bundle bundle = new Bundle();
                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (ar.result == null) {
                        loge("isSatelliteCommunicationAllowedForCurrentLocation: result is null");
                        error = SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
                    } else {
                        boolean communicationAllowed = (boolean) ar.result;
                        if (DBG) {
                            logd("isSatelliteCommunicationAllowedForCurrentLocation: "
                                    + communicationAllowed);
                        }
                        bundle.putBoolean(SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED,
                                communicationAllowed);
                    }
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_GET_TIME_SATELLITE_NEXT_VISIBLE: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_GET_TIME_SATELLITE_NEXT_VISIBLE_DONE,
                        request);
                mSatelliteModemInterface.requestTimeForNextSatelliteVisibility(onCompleted);
                break;
            }

            case EVENT_GET_TIME_SATELLITE_NEXT_VISIBLE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = SatelliteServiceUtils.getSatelliteError(ar,
                        "requestTimeForNextSatelliteVisibility");
                Bundle bundle = new Bundle();
                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (ar.result == null) {
                        loge("requestTimeForNextSatelliteVisibility: result is null");
                        error = SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
                    } else {
                        int nextVisibilityDuration = ((int[]) ar.result)[0];
                        if (DBG) {
                            logd("requestTimeForNextSatelliteVisibility: " +
                                    nextVisibilityDuration);
                        }
                        bundle.putInt(SatelliteManager.KEY_SATELLITE_NEXT_VISIBILITY,
                                nextVisibilityDuration);
                    }
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case EVENT_RADIO_STATE_CHANGED: {
                if (mCi.getRadioState() == TelephonyManager.RADIO_POWER_ON) {
                    mIsRadioOn = true;
                    if (!mSatelliteModemInterface.isSatelliteServiceSupported()) {
                        synchronized (mIsSatelliteSupportedLock) {
                            if (mIsSatelliteSupported == null) {
                                ResultReceiver receiver = new ResultReceiver(this) {
                                    @Override
                                    protected void onReceiveResult(
                                            int resultCode, Bundle resultData) {
                                        logd("requestIsSatelliteSupported: resultCode="
                                                + resultCode);
                                    }
                                };
                                requestIsSatelliteSupported(
                                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, receiver);
                            }
                        }
                    }
                }
                break;
            }

            case CMD_IS_SATELLITE_PROVISIONED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_IS_SATELLITE_PROVISIONED_DONE, request);
                mSatelliteModemInterface.requestIsSatelliteProvisioned(onCompleted);
                break;
            }

            case EVENT_IS_SATELLITE_PROVISIONED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =  SatelliteServiceUtils.getSatelliteError(ar,
                        "isSatelliteProvisioned");
                Bundle bundle = new Bundle();
                if (error == SATELLITE_RESULT_SUCCESS) {
                    if (ar.result == null) {
                        loge("isSatelliteProvisioned: result is null");
                        error = SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
                    } else {
                        boolean provisioned = ((int[]) ar.result)[0] == 1;
                        if (DBG) logd("isSatelliteProvisioned: " + provisioned);
                        bundle.putBoolean(SatelliteManager.KEY_SATELLITE_PROVISIONED, provisioned);
                        synchronized (mIsSatelliteProvisionedLock) {
                            mIsSatelliteProvisioned = provisioned;
                        }
                    }
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case EVENT_SATELLITE_PROVISION_STATE_CHANGED:
                ar = (AsyncResult) msg.obj;
                if (ar.result == null) {
                    loge("EVENT_SATELLITE_PROVISION_STATE_CHANGED: result is null");
                } else {
                    handleEventSatelliteProvisionStateChanged((boolean) ar.result);
                }
                break;

            case EVENT_PENDING_DATAGRAMS:
                logd("Received EVENT_PENDING_DATAGRAMS");
                IIntegerConsumer internalCallback = new IIntegerConsumer.Stub() {
                    @Override
                    public void accept(int result) {
                        logd("pollPendingSatelliteDatagram result: " + result);
                    }
                };
                pollPendingSatelliteDatagrams(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, internalCallback);
                break;

            case EVENT_SATELLITE_MODEM_STATE_CHANGED:
                ar = (AsyncResult) msg.obj;
                if (ar.result == null) {
                    loge("EVENT_SATELLITE_MODEM_STATE_CHANGED: result is null");
                } else {
                    handleEventSatelliteModemStateChanged((int) ar.result);
                }
                break;

            case EVENT_SET_SATELLITE_PLMN_INFO_DONE:
                handleSetSatellitePlmnInfoDoneEvent(msg);
                break;

            case CMD_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE: {
                logd("CMD_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE");
                request = (SatelliteControllerHandlerRequest) msg.obj;
                handleRequestSatelliteAttachRestrictionForCarrierCmd(request);
                break;
            }

            case EVENT_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                RequestHandleSatelliteAttachRestrictionForCarrierArgument argument =
                        (RequestHandleSatelliteAttachRestrictionForCarrierArgument)
                                request.argument;
                int subId = argument.subId;
                int error =  SatelliteServiceUtils.getSatelliteError(ar,
                        "requestSetSatelliteEnabledForCarrier");

                synchronized (mIsSatelliteEnabledLock) {
                    if (error == SATELLITE_RESULT_SUCCESS) {
                        boolean enableSatellite = mSatelliteAttachRestrictionForCarrierArray
                                .getOrDefault(argument.subId, Collections.emptySet()).isEmpty();
                        mIsSatelliteAttachEnabledForCarrierArrayPerSub.put(subId, enableSatellite);
                    } else {
                        mIsSatelliteAttachEnabledForCarrierArrayPerSub.remove(subId);
                    }
                }

                argument.callback.accept(error);
                break;
            }

            case CMD_REQUEST_NTN_SIGNAL_STRENGTH: {
                logd("CMD_REQUEST_NTN_SIGNAL_STRENGTH");
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_REQUEST_NTN_SIGNAL_STRENGTH_DONE, request);
                mSatelliteModemInterface.requestNtnSignalStrength(onCompleted);
                break;
            }

            case EVENT_REQUEST_NTN_SIGNAL_STRENGTH_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                ResultReceiver result = (ResultReceiver) request.argument;
                int errorCode =  SatelliteServiceUtils.getSatelliteError(ar,
                        "requestNtnSignalStrength");
                if (errorCode == SATELLITE_RESULT_SUCCESS) {
                    NtnSignalStrength ntnSignalStrength = (NtnSignalStrength) ar.result;
                    if (ntnSignalStrength != null) {
                        synchronized (mNtnSignalsStrengthLock) {
                            mNtnSignalStrength = ntnSignalStrength;
                        }
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(KEY_NTN_SIGNAL_STRENGTH, ntnSignalStrength);
                        result.send(SATELLITE_RESULT_SUCCESS, bundle);
                    } else {
                        synchronized (mNtnSignalsStrengthLock) {
                            if (mNtnSignalStrength.getLevel() != NTN_SIGNAL_STRENGTH_NONE) {
                                mNtnSignalStrength = new NtnSignalStrength(
                                        NTN_SIGNAL_STRENGTH_NONE);
                            }
                        }
                        loge("EVENT_REQUEST_NTN_SIGNAL_STRENGTH_DONE: ntnSignalStrength is null");
                        result.send(SatelliteManager.SATELLITE_RESULT_REQUEST_FAILED, null);
                    }
                } else {
                    synchronized (mNtnSignalsStrengthLock) {
                        if (mNtnSignalStrength.getLevel() != NTN_SIGNAL_STRENGTH_NONE) {
                            mNtnSignalStrength = new NtnSignalStrength(NTN_SIGNAL_STRENGTH_NONE);
                        }
                    }
                    result.send(errorCode, null);
                }
                break;
            }

            case EVENT_NTN_SIGNAL_STRENGTH_CHANGED: {
                ar = (AsyncResult) msg.obj;
                if (ar.result == null) {
                    loge("EVENT_NTN_SIGNAL_STRENGTH_CHANGED: result is null");
                } else {
                    handleEventNtnSignalStrengthChanged((NtnSignalStrength) ar.result);
                }
                break;
            }

            case CMD_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING: {
                ar = (AsyncResult) msg.obj;
                boolean shouldReport = (boolean) ar.result;
                if (DBG) {
                    logd("CMD_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING: shouldReport=" + shouldReport);
                }
                request = new SatelliteControllerHandlerRequest(shouldReport,
                        SatelliteServiceUtils.getPhone());
                if (SATELLITE_RESULT_SUCCESS != evaluateOemSatelliteRequestAllowed(true)) {
                    return;
                }
                if (mShouldReportNtnSignalStrength.get() == shouldReport) {
                    if (DBG) {
                        logd("CMD_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING : modem state matches the "
                                + "expected state, return.");
                    }
                    return;
                }
                onCompleted = obtainMessage(EVENT_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING_DONE,
                        request);
                if (shouldReport) {
                    mSatelliteModemInterface.startSendingNtnSignalStrength(onCompleted);
                } else {
                    mSatelliteModemInterface.stopSendingNtnSignalStrength(onCompleted);
                }
                break;
            }

            case EVENT_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                boolean shouldReport = (boolean) request.argument;
                int errorCode =  SatelliteServiceUtils.getSatelliteError(ar,
                        "EVENT_UPDATE_NTN_SIGNAL_STRENGTH_REPORTING_DONE");
                if (errorCode == SATELLITE_RESULT_SUCCESS) {
                    mShouldReportNtnSignalStrength.set(shouldReport);
                } else {
                    loge(((boolean) request.argument ? "startSendingNtnSignalStrength"
                            : "stopSendingNtnSignalStrength") + "returns " + errorCode);
                }
                break;
            }

            case EVENT_SERVICE_STATE_CHANGED: {
                handleEventServiceStateChanged();
                break;
            }

            case EVENT_SATELLITE_CAPABILITIES_CHANGED: {
                ar = (AsyncResult) msg.obj;
                if (ar.result == null) {
                    loge("EVENT_SATELLITE_CAPABILITIES_CHANGED: result is null");
                } else {
                    handleEventSatelliteCapabilitiesChanged((SatelliteCapabilities) ar.result);
                }
                break;
            }

            default:
                Log.w(TAG, "SatelliteControllerHandler: unexpected message code: " +
                        msg.what);
                break;
        }
    }

    private void notifyRequester(SatelliteControllerHandlerRequest request) {
        synchronized (request) {
            request.notifyAll();
        }
    }

    /**
     * Request to enable or disable the satellite modem and demo mode. If the satellite modem is
     * enabled, this will also disable the cellular modem, and if the satellite modem is disabled,
     * this will also re-enable the cellular modem.
     *
     * @param subId The subId of the subscription to set satellite enabled for.
     * @param enableSatellite {@code true} to enable the satellite modem and
     *                        {@code false} to disable.
     * @param enableDemoMode {@code true} to enable demo mode and {@code false} to disable.
     * @param callback The callback to get the error code of the request.
     */
    public void requestSatelliteEnabled(int subId, boolean enableSatellite, boolean enableDemoMode,
            @NonNull IIntegerConsumer callback) {
        logd("requestSatelliteEnabled subId: " + subId + " enableSatellite: " + enableSatellite
                + " enableDemoMode: " + enableDemoMode);
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            sendErrorAndReportSessionMetrics(error, result);
            return;
        }

        if (enableSatellite) {
            if (!mIsRadioOn) {
                loge("Radio is not on, can not enable satellite");
                sendErrorAndReportSessionMetrics(
                        SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE, result);
                return;
            }
        } else {
            /* if disable satellite, always assume demo is also disabled */
            enableDemoMode = false;
        }

        synchronized (mIsSatelliteEnabledLock) {
            if (mIsSatelliteEnabled != null) {
                if (mIsSatelliteEnabled == enableSatellite) {
                    if (enableDemoMode != mIsDemoModeEnabled) {
                        loge("Received invalid demo mode while satellite session is enabled"
                                + " enableDemoMode = " + enableDemoMode);
                        sendErrorAndReportSessionMetrics(
                                SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS, result);
                        return;
                    } else {
                        logd("Enable request matches with current state"
                                + " enableSatellite = " + enableSatellite);
                        sendErrorAndReportSessionMetrics(
                                SatelliteManager.SATELLITE_RESULT_SUCCESS, result);
                        return;
                    }
                }
            }
        }

        RequestSatelliteEnabledArgument request =
                new RequestSatelliteEnabledArgument(enableSatellite, enableDemoMode, result);
        /**
         * Multiple satellite enabled requests are handled as below:
         * 1. If there are no ongoing requests, store current request in mSatelliteEnabledRequest
         * 2. If there is a ongoing request, then:
         *      1. ongoing request = enable, current request = enable: return IN_PROGRESS error
         *      2. ongoing request = disable, current request = disable: return IN_PROGRESS error
         *      3. ongoing request = disable, current request = enable: return
         *      SATELLITE_RESULT_ERROR error
         *      4. ongoing request = enable, current request = disable: send request to modem
         */
        synchronized (mSatelliteEnabledRequestLock) {
            if (mSatelliteEnabledRequest == null) {
                mSatelliteEnabledRequest = request;
            } else if (mSatelliteEnabledRequest.enableSatellite == request.enableSatellite) {
                logd("requestSatelliteEnabled enableSatellite: " + enableSatellite
                        + " is already in progress.");
                sendErrorAndReportSessionMetrics(
                        SatelliteManager.SATELLITE_RESULT_REQUEST_IN_PROGRESS, result);
                return;
            } else if (mSatelliteEnabledRequest.enableSatellite == false
                    && request.enableSatellite == true) {
                logd("requestSatelliteEnabled enableSatellite: " + enableSatellite + " cannot be "
                        + "processed. Disable satellite is already in progress.");
                sendErrorAndReportSessionMetrics(
                        SatelliteManager.SATELLITE_RESULT_ERROR, result);
                return;
            }
        }

        sendRequestAsync(CMD_SET_SATELLITE_ENABLED, request, null);
    }

    /**
     * Request to get whether the satellite modem is enabled.
     *
     * @param subId The subId of the subscription to check whether satellite is enabled for.
     * @param result The result receiver that returns whether the satellite modem is enabled
     *               if the request is successful or an error code if the request failed.
     */
    public void requestIsSatelliteEnabled(int subId, @NonNull ResultReceiver result) {
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        synchronized (mIsSatelliteEnabledLock) {
            if (mIsSatelliteEnabled != null) {
                /* We have already successfully queried the satellite modem. */
                Bundle bundle = new Bundle();
                bundle.putBoolean(SatelliteManager.KEY_SATELLITE_ENABLED, mIsSatelliteEnabled);
                result.send(SATELLITE_RESULT_SUCCESS, bundle);
                return;
            }
        }

        sendRequestAsync(CMD_IS_SATELLITE_ENABLED, result, null);
    }

    /**
     * Get whether the satellite modem is enabled.
     * This will return the cached value instead of querying the satellite modem.
     *
     * @return {@code true} if the satellite modem is enabled and {@code false} otherwise.
     */
    public boolean isSatelliteEnabled() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("isSatelliteEnabled: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        if (mIsSatelliteEnabled == null) return false;
        return mIsSatelliteEnabled;
    }

    /**
     * Request to get whether the satellite service demo mode is enabled.
     *
     * @param subId The subId of the subscription to check whether the satellite demo mode
     *              is enabled for.
     * @param result The result receiver that returns whether the satellite demo mode is enabled
     *               if the request is successful or an error code if the request failed.
     */
    public void requestIsDemoModeEnabled(int subId, @NonNull ResultReceiver result) {
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        final Bundle bundle = new Bundle();
        bundle.putBoolean(SatelliteManager.KEY_DEMO_MODE_ENABLED, mIsDemoModeEnabled);
        result.send(SATELLITE_RESULT_SUCCESS, bundle);
    }

    /**
     * Get whether the satellite service demo mode is enabled.
     *
     * @return {@code true} if the satellite demo mode is enabled and {@code false} otherwise.
     */
    public boolean isDemoModeEnabled() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("isDemoModeEnabled: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        return mIsDemoModeEnabled;
    }

    /**
     * Request to get whether the satellite service is supported on the device.
     *
     * @param subId The subId of the subscription to check satellite service support for.
     * @param result The result receiver that returns whether the satellite service is supported on
     *               the device if the request is successful or an error code if the request failed.
     */
    public void requestIsSatelliteSupported(int subId, @NonNull ResultReceiver result) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("requestIsSatelliteSupported: oemEnabledSatelliteFlag is disabled");
            result.send(SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED, null);
            return;
        }
        synchronized (mIsSatelliteSupportedLock) {
            if (mIsSatelliteSupported != null) {
                /* We have already successfully queried the satellite modem. */
                Bundle bundle = new Bundle();
                bundle.putBoolean(SatelliteManager.KEY_SATELLITE_SUPPORTED, mIsSatelliteSupported);
                result.send(SATELLITE_RESULT_SUCCESS, bundle);
                return;
            }
        }

        sendRequestAsync(CMD_IS_SATELLITE_SUPPORTED, result, null);
    }

    /**
     * Request to get the {@link SatelliteCapabilities} of the satellite service.
     *
     * @param subId The subId of the subscription to get the satellite capabilities for.
     * @param result The result receiver that returns the {@link SatelliteCapabilities}
     *               if the request is successful or an error code if the request failed.
     */
    public void requestSatelliteCapabilities(int subId, @NonNull ResultReceiver result) {
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        synchronized (mSatelliteCapabilitiesLock) {
            if (mSatelliteCapabilities != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(SatelliteManager.KEY_SATELLITE_CAPABILITIES,
                        mSatelliteCapabilities);
                result.send(SATELLITE_RESULT_SUCCESS, bundle);
                return;
            }
        }

        sendRequestAsync(CMD_GET_SATELLITE_CAPABILITIES, result, null);
    }

    /**
     * Start receiving satellite transmission updates.
     * This can be called by the pointing UI when the user starts pointing to the satellite.
     * Modem should continue to report the pointing input as the device or satellite moves.
     *
     * @param subId The subId of the subscription to start satellite transmission updates for.
     * @param errorCallback The callback to get the error code of the request.
     * @param callback The callback to notify of satellite transmission updates.
     */
    public void startSatelliteTransmissionUpdates(int subId,
            @NonNull IIntegerConsumer errorCallback,
            @NonNull ISatelliteTransmissionUpdateCallback callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(errorCallback::accept);
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.accept(error);
            return;
        }

        final int validSubId = SatelliteServiceUtils.getValidSatelliteSubId(subId, mContext);
        mPointingAppController.registerForSatelliteTransmissionUpdates(validSubId, callback);
        sendRequestAsync(CMD_START_SATELLITE_TRANSMISSION_UPDATES,
                new SatelliteTransmissionUpdateArgument(result, callback, validSubId), null);
    }

    /**
     * Stop receiving satellite transmission updates.
     * This can be called by the pointing UI when the user stops pointing to the satellite.
     *
     * @param subId The subId of the subscription to stop satellite transmission updates for.
     * @param errorCallback The callback to get the error code of the request.
     * @param callback The callback that was passed to {@link #startSatelliteTransmissionUpdates(
     *                 int, IIntegerConsumer, ISatelliteTransmissionUpdateCallback)}.
     */
    public void stopSatelliteTransmissionUpdates(int subId, @NonNull IIntegerConsumer errorCallback,
            @NonNull ISatelliteTransmissionUpdateCallback callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(errorCallback::accept);
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.accept(error);
            return;
        }

        final int validSubId = SatelliteServiceUtils.getValidSatelliteSubId(subId, mContext);
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(
                validSubId, result, callback);

        // Even if handler is null - which means there are no listeners, the modem command to stop
        // satellite transmission updates might have failed. The callers might want to retry
        // sending the command. Thus, we always need to send this command to the modem.
        sendRequestAsync(CMD_STOP_SATELLITE_TRANSMISSION_UPDATES, result, null);
    }

    /**
     * Register the subscription with a satellite provider.
     * This is needed to register the subscription if the provider allows dynamic registration.
     *
     * @param subId The subId of the subscription to be provisioned.
     * @param token The token to be used as a unique identifier for provisioning with satellite
     *              gateway.
     * @param provisionData Data from the provisioning app that can be used by provisioning server
     * @param callback The callback to get the error code of the request.
     *
     * @return The signal transport used by the caller to cancel the provision request,
     *         or {@code null} if the request failed.
     */
    @Nullable public ICancellationSignal provisionSatelliteService(int subId,
            @NonNull String token, @NonNull byte[] provisionData,
            @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.accept(error);
            return null;
        }

        final int validSubId = SatelliteServiceUtils.getValidSatelliteSubId(subId, mContext);
        if (mSatelliteProvisionCallbacks.containsKey(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS);
            return null;
        }

        Boolean satelliteProvisioned = isSatelliteViaOemProvisioned();
        if (satelliteProvisioned != null && satelliteProvisioned) {
            result.accept(SATELLITE_RESULT_SUCCESS);
            return null;
        }

        sendRequestAsync(CMD_PROVISION_SATELLITE_SERVICE,
                new ProvisionSatelliteServiceArgument(token, provisionData, result, validSubId),
                null);

        ICancellationSignal cancelTransport = CancellationSignal.createTransport();
        CancellationSignal.fromTransport(cancelTransport).setOnCancelListener(() -> {
            sendRequestAsync(CMD_DEPROVISION_SATELLITE_SERVICE,
                    new ProvisionSatelliteServiceArgument(token, provisionData, null,
                            validSubId), null);
            mProvisionMetricsStats.setIsCanceled(true);
        });
        return cancelTransport;
    }

    /**
     * Unregister the device/subscription with the satellite provider.
     * This is needed if the provider allows dynamic registration. Once deprovisioned,
     * {@link android.telephony.satellite.SatelliteProvisionStateCallback
     * #onSatelliteProvisionStateChanged(boolean)}
     * should report as deprovisioned.
     *
     * @param subId The subId of the subscription to be deprovisioned.
     * @param token The token of the device/subscription to be deprovisioned.
     * @param callback The callback to get the error code of the request.
     */
    public void deprovisionSatelliteService(int subId,
            @NonNull String token, @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.accept(error);
            return;
        }

        Boolean satelliteProvisioned = isSatelliteViaOemProvisioned();
        if (satelliteProvisioned == null) {
            result.accept(SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
            return;
        }
        if (!satelliteProvisioned) {
            result.accept(SATELLITE_RESULT_SUCCESS);
            return;
        }

        final int validSubId = SatelliteServiceUtils.getValidSatelliteSubId(subId, mContext);
        sendRequestAsync(CMD_DEPROVISION_SATELLITE_SERVICE,
                new ProvisionSatelliteServiceArgument(token, null, result, validSubId),
                null);
    }

    /**
     * Registers for the satellite provision state changed.
     *
     * @param subId The subId of the subscription to register for provision state changed.
     * @param callback The callback to handle the satellite provision state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     */
    @SatelliteManager.SatelliteResult public int registerForSatelliteProvisionStateChanged(
            int subId, @NonNull ISatelliteProvisionStateCallback callback) {
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            return error;
        }

        mSatelliteProvisionStateChangedListeners.put(callback.asBinder(), callback);
        return SATELLITE_RESULT_SUCCESS;
    }

    /**
     * Unregisters for the satellite provision state changed.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for provision state changed.
     * @param callback The callback that was passed to
     * {@link #registerForSatelliteProvisionStateChanged(int, ISatelliteProvisionStateCallback)}.
     */
    public void unregisterForSatelliteProvisionStateChanged(
            int subId, @NonNull ISatelliteProvisionStateCallback callback) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("unregisterForSatelliteProvisionStateChanged: "
                    + "oemEnabledSatelliteFlag is disabled");
            return;
        }
        mSatelliteProvisionStateChangedListeners.remove(callback.asBinder());
    }

    /**
     * Request to get whether the device is provisioned with a satellite provider.
     *
     * @param subId The subId of the subscription to get whether the device is provisioned for.
     * @param result The result receiver that returns whether the device is provisioned with a
     *               satellite provider if the request is successful or an error code if the
     *               request failed.
     */
    public void requestIsSatelliteProvisioned(int subId, @NonNull ResultReceiver result) {
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        synchronized (mIsSatelliteProvisionedLock) {
            if (mIsSatelliteProvisioned != null) {
                Bundle bundle = new Bundle();
                bundle.putBoolean(SatelliteManager.KEY_SATELLITE_PROVISIONED,
                        mIsSatelliteProvisioned);
                result.send(SATELLITE_RESULT_SUCCESS, bundle);
                return;
            }
        }

        sendRequestAsync(CMD_IS_SATELLITE_PROVISIONED, result, null);
    }

    /**
     * Registers for modem state changed from satellite modem.
     *
     * @param subId The subId of the subscription to register for satellite modem state changed.
     * @param callback The callback to handle the satellite modem state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     */
    @SatelliteManager.SatelliteResult public int registerForSatelliteModemStateChanged(int subId,
            @NonNull ISatelliteStateCallback callback) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("registerForSatelliteModemStateChanged: oemEnabledSatelliteFlag is disabled");
            return SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
        }
        if (mSatelliteSessionController != null) {
            mSatelliteSessionController.registerForSatelliteModemStateChanged(callback);
        } else {
            loge("registerForSatelliteModemStateChanged: mSatelliteSessionController"
                    + " is not initialized yet");
            return SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
        }
        return SATELLITE_RESULT_SUCCESS;
    }

    /**
     * Unregisters for modem state changed from satellite modem.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for satellite modem state changed.
     * @param callback The callback that was passed to
     *                 {@link #registerForSatelliteModemStateChanged(int, ISatelliteStateCallback)}.
     */
    public void unregisterForSatelliteModemStateChanged(int subId,
            @NonNull ISatelliteStateCallback callback) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("unregisterForSatelliteModemStateChanged: oemEnabledSatelliteFlag is disabled");
            return;
        }
        if (mSatelliteSessionController != null) {
            mSatelliteSessionController.unregisterForSatelliteModemStateChanged(callback);
        } else {
            loge("registerForSatelliteModemStateChanged: mSatelliteSessionController"
                    + " is not initialized yet");
        }
    }

    /**
     * Register to receive incoming datagrams over satellite.
     *
     * @param subId The subId of the subscription to register for incoming satellite datagrams.
     * @param callback The callback to handle incoming datagrams over satellite.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     */
    @SatelliteManager.SatelliteResult public int registerForSatelliteDatagram(int subId,
            @NonNull ISatelliteDatagramCallback callback) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("registerForSatelliteDatagram: oemEnabledSatelliteFlag is disabled");
            return SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
        }
        if (!mSatelliteModemInterface.isSatelliteServiceSupported()) {
            return SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
        }
        logd("registerForSatelliteDatagram: callback=" + callback);
        return mDatagramController.registerForSatelliteDatagram(subId, callback);
    }

    /**
     * Unregister to stop receiving incoming datagrams over satellite.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for incoming satellite datagrams.
     * @param callback The callback that was passed to
     *                 {@link #registerForSatelliteDatagram(int, ISatelliteDatagramCallback)}.
     */
    public void unregisterForSatelliteDatagram(int subId,
            @NonNull ISatelliteDatagramCallback callback) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("unregisterForSatelliteDatagram: oemEnabledSatelliteFlag is disabled");
            return;
        }
        if (!mSatelliteModemInterface.isSatelliteServiceSupported()) {
            return;
        }
        logd("unregisterForSatelliteDatagram: callback=" + callback);
        mDatagramController.unregisterForSatelliteDatagram(subId, callback);
    }

    /**
     * Poll pending satellite datagrams over satellite.
     *
     * This method requests modem to check if there are any pending datagrams to be received over
     * satellite. If there are any incoming datagrams, they will be received via
     * {@link android.telephony.satellite.SatelliteDatagramCallback#onSatelliteDatagramReceived(
     * long, SatelliteDatagram, int, Consumer)}
     *
     * @param subId The subId of the subscription used for receiving datagrams.
     * @param callback The callback to get {@link SatelliteManager.SatelliteResult} of the request.
     */
    public void pollPendingSatelliteDatagrams(int subId, @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.accept(error);
            return;
        }

        final int validSubId = SatelliteServiceUtils.getValidSatelliteSubId(subId, mContext);
        mDatagramController.pollPendingSatelliteDatagrams(validSubId, result);
    }

    /**
     * Send datagram over satellite.
     *
     * Gateway encodes SOS message or location sharing message into a datagram and passes it as
     * input to this method. Datagram received here will be passed down to modem without any
     * encoding or encryption.
     *
     * @param subId The subId of the subscription to send satellite datagrams for.
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param datagram encoded gateway datagram which is encrypted by the caller.
     *                 Datagram will be passed down to modem without any encoding or encryption.
     * @param needFullScreenPointingUI this is used to indicate pointingUI app to open in
     *                                 full screen mode.
     * @param callback The callback to get {@link SatelliteManager.SatelliteResult} of the request.
     */
    public void sendSatelliteDatagram(int subId, @SatelliteManager.DatagramType int datagramType,
            SatelliteDatagram datagram, boolean needFullScreenPointingUI,
            @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.accept(error);
            return;
        }

        /**
         * TODO for NTN-based satellites: Check if satellite is acquired.
         */
        if (mNeedsSatellitePointing) {
            mPointingAppController.startPointingUI(needFullScreenPointingUI);
        }

        final int validSubId = SatelliteServiceUtils.getValidSatelliteSubId(subId, mContext);
        mDatagramController.sendSatelliteDatagram(validSubId, datagramType, datagram,
                needFullScreenPointingUI, result);
    }

    /**
     * Request to get whether satellite communication is allowed for the current location.
     *
     * @param subId The subId of the subscription to check whether satellite communication is
     *              allowed for the current location for.
     * @param result The result receiver that returns whether satellite communication is allowed
     *               for the current location if the request is successful or an error code
     *               if the request failed.
     */
    public void requestIsSatelliteCommunicationAllowedForCurrentLocation(int subId,
            @NonNull ResultReceiver result) {
        int error = evaluateOemSatelliteRequestAllowed(false);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        sendRequestAsync(CMD_IS_SATELLITE_COMMUNICATION_ALLOWED, result, null);
    }

    /**
     * Request to get the time after which the satellite will be visible.
     *
     * @param subId The subId to get the time after which the satellite will be visible for.
     * @param result The result receiver that returns the time after which the satellite will
     *               be visible if the request is successful or an error code if the request failed.
     */
    public void requestTimeForNextSatelliteVisibility(int subId, @NonNull ResultReceiver result) {
        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        sendRequestAsync(CMD_GET_TIME_SATELLITE_NEXT_VISIBLE, result, null);
    }

    /**
     * Inform whether the device is aligned with satellite for demo mode.
     *
     * @param subId The subId of the subscription.
     * @param isAligned {@true} means device is aligned with the satellite, otherwise {@false}.
     */
    public void setDeviceAlignedWithSatellite(@NonNull int subId, @NonNull boolean isAligned) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setDeviceAlignedWithSatellite: oemEnabledSatelliteFlag is disabled");
            return;
        }
        mDatagramController.setDeviceAlignedWithSatellite(isAligned);
    }

    /**
     * Add a restriction reason for disallowing carrier supported satellite plmn scan and attach
     * by modem. After updating restriction list, evaluate if satellite should be enabled/disabled,
     * and request modem to enable/disable satellite accordingly if the desired state does not match
     * the current state.
     *
     * @param subId The subId of the subscription to request for.
     * @param reason Reason for disallowing satellite communication for carrier.
     * @param callback The callback to get the result of the request.
     */
    public void addSatelliteAttachRestrictionForCarrier(int subId,
            @SatelliteManager.SatelliteCommunicationRestrictionReason int reason,
            @NonNull IIntegerConsumer callback) {
        if (DBG) logd("addSatelliteAttachRestrictionForCarrier(" + subId + ", " + reason + ")");
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            result.accept(SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
            logd("addSatelliteAttachRestrictionForCarrier: carrierEnabledSatelliteFlag is "
                    + "disabled");
            return;
        }

        synchronized (mIsSatelliteEnabledLock) {
            if (mSatelliteAttachRestrictionForCarrierArray.getOrDefault(
                    subId, Collections.emptySet()).isEmpty()) {
                mSatelliteAttachRestrictionForCarrierArray.put(subId, new HashSet<>());
            } else if (mSatelliteAttachRestrictionForCarrierArray.get(subId).contains(reason)) {
                result.accept(SATELLITE_RESULT_SUCCESS);
                return;
            }
            mSatelliteAttachRestrictionForCarrierArray.get(subId).add(reason);
        }
        RequestHandleSatelliteAttachRestrictionForCarrierArgument request =
                new RequestHandleSatelliteAttachRestrictionForCarrierArgument(subId, reason,
                        result);
        sendRequestAsync(CMD_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE, request,
                SatelliteServiceUtils.getPhone(subId));
    }

    /**
     * Remove a restriction reason for disallowing carrier supported satellite plmn scan and attach
     * by modem. After updating restriction list, evaluate if satellite should be enabled/disabled,
     * and request modem to enable/disable satellite accordingly if the desired state does not match
     * the current state.
     *
     * @param subId The subId of the subscription to request for.
     * @param reason Reason for disallowing satellite communication.
     * @param callback The callback to get the result of the request.
     */
    public void removeSatelliteAttachRestrictionForCarrier(int subId,
            @SatelliteManager.SatelliteCommunicationRestrictionReason int reason,
            @NonNull IIntegerConsumer callback) {
        if (DBG) logd("removeSatelliteAttachRestrictionForCarrier(" + subId + ", " + reason + ")");
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            result.accept(SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
            logd("removeSatelliteAttachRestrictionForCarrier: carrierEnabledSatelliteFlag is "
                    + "disabled");
            return;
        }

        synchronized (mIsSatelliteEnabledLock) {
            if (mSatelliteAttachRestrictionForCarrierArray.getOrDefault(
                    subId, Collections.emptySet()).isEmpty()
                    || !mSatelliteAttachRestrictionForCarrierArray.get(subId).contains(reason)) {
                result.accept(SATELLITE_RESULT_SUCCESS);
                return;
            }
            mSatelliteAttachRestrictionForCarrierArray.get(subId).remove(reason);
        }
        RequestHandleSatelliteAttachRestrictionForCarrierArgument request =
                new RequestHandleSatelliteAttachRestrictionForCarrierArgument(subId, reason,
                        result);
        sendRequestAsync(CMD_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE, request,
                SatelliteServiceUtils.getPhone(subId));
    }

    /**
     * Get reasons for disallowing satellite communication, as requested by
     * {@link #addSatelliteAttachRestrictionForCarrier(int, int, IIntegerConsumer)}.
     *
     * @param subId The subId of the subscription to request for.
     *
     * @return Set of reasons for disallowing satellite attach for carrier.
     */
    @NonNull public Set<Integer> getSatelliteAttachRestrictionReasonsForCarrier(int subId) {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("getSatelliteAttachRestrictionReasonsForCarrier: carrierEnabledSatelliteFlag is "
                    + "disabled");
            return new HashSet<>();
        }
        synchronized (mIsSatelliteEnabledLock) {
            Set<Integer> resultSet =
                    mSatelliteAttachRestrictionForCarrierArray.get(subId);
            if (resultSet == null) {
                return new HashSet<>();
            }
            return new HashSet<>(resultSet);
        }
    }

    /**
     * Request to get the signal strength of the satellite connection.
     *
     * @param subId The subId of the subscription to request for.
     * @param result Result receiver to get the error code of the request and the current signal
     * strength of the satellite connection.
     */
    public void requestNtnSignalStrength(int subId, @NonNull ResultReceiver result) {
        if (DBG) logd("requestNtnSignalStrength()");

        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) {
            result.send(error, null);
            return;
        }

        /* In case cache is available, it is not needed to request non-terrestrial signal strength
        to modem */
        synchronized (mNtnSignalsStrengthLock) {
            if (mNtnSignalStrength.getLevel() != NTN_SIGNAL_STRENGTH_NONE) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(KEY_NTN_SIGNAL_STRENGTH, mNtnSignalStrength);
                result.send(SATELLITE_RESULT_SUCCESS, bundle);
                return;
            }
        }

        Phone phone = SatelliteServiceUtils.getPhone();
        sendRequestAsync(CMD_REQUEST_NTN_SIGNAL_STRENGTH, result, phone);
    }

    /**
     * Registers for NTN signal strength changed from satellite modem. If the registration operation
     * is not successful, a {@link ServiceSpecificException} that contains
     * {@link SatelliteManager.SatelliteResult} will be thrown.
     *
     * @param subId The id of the subscription to request for.
     * @param callback The callback to handle the NTN signal strength changed event. If the
     * operation is successful, {@link INtnSignalStrengthCallback#onNtnSignalStrengthChanged(
     * NtnSignalStrength)} will return an instance of {@link NtnSignalStrength} with a value of
     * {@link NtnSignalStrength.NtnSignalStrengthLevel} when the signal strength of non-terrestrial
     * network has changed.
     *
     * @throws ServiceSpecificException If the callback registration operation fails.
     */
    public void registerForNtnSignalStrengthChanged(int subId,
            @NonNull INtnSignalStrengthCallback callback) throws RemoteException {
        if (DBG) logd("registerForNtnSignalStrengthChanged()");

        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error == SATELLITE_RESULT_SUCCESS) {
            mNtnSignalStrengthChangedListeners.put(callback.asBinder(), callback);
        } else {
            throw new ServiceSpecificException(error);
        }
    }

    /**
     * Unregisters for NTN signal strength changed from satellite modem.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The id of the subscription to unregister for listening NTN signal strength
     * changed event.
     * @param callback The callback that was passed to
     * {@link #registerForNtnSignalStrengthChanged(int, INtnSignalStrengthCallback)}
     */
    public void unregisterForNtnSignalStrengthChanged(
            int subId, @NonNull INtnSignalStrengthCallback callback) {
        if (DBG) logd("unregisterForNtnSignalStrengthChanged()");

        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error == SATELLITE_RESULT_SUCCESS) {
            mNtnSignalStrengthChangedListeners.remove(callback.asBinder());
        }
    }

    /**
     * Registers for satellite capabilities change event from the satellite service.
     *
     * @param subId The id of the subscription to request for.
     * @param callback The callback to handle the satellite capabilities changed event.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     */
    @SatelliteManager.SatelliteResult public int registerForSatelliteCapabilitiesChanged(
            int subId, @NonNull ISatelliteCapabilitiesCallback callback) {
        if (DBG) logd("registerForSatelliteCapabilitiesChanged()");

        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error != SATELLITE_RESULT_SUCCESS) return error;

        mSatelliteCapabilitiesChangedListeners.put(callback.asBinder(), callback);
        return SATELLITE_RESULT_SUCCESS;
    }

    /**
     * Unregisters for satellite capabilities change event from the satellite service.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The id of the subscription to unregister for listening satellite capabilities
     * changed event.
     * @param callback The callback that was passed to
     * {@link #registerForSatelliteCapabilitiesChanged(int, ISatelliteCapabilitiesCallback)}
     */
    public void unregisterForSatelliteCapabilitiesChanged(
            int subId, @NonNull ISatelliteCapabilitiesCallback callback) {
        if (DBG) logd("unregisterForSatelliteCapabilitiesChanged()");

        int error = evaluateOemSatelliteRequestAllowed(true);
        if (error == SATELLITE_RESULT_SUCCESS) {
            mSatelliteCapabilitiesChangedListeners.remove(callback.asBinder());
        }
    }

    /**
     * This API can be used by only CTS to update satellite vendor service package name.
     *
     * @param servicePackageName The package name of the satellite vendor service.
     * @return {@code true} if the satellite vendor service is set successfully,
     * {@code false} otherwise.
     */
    public boolean setSatelliteServicePackageName(@Nullable String servicePackageName) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setSatelliteServicePackageName: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        if (!isMockModemAllowed()) {
            logd("setSatelliteServicePackageName: mock modem not allowed");
            return false;
        }

        // Cached states need to be cleared whenever switching satellite vendor services.
        logd("setSatelliteServicePackageName: Resetting cached states");
        synchronized (mIsSatelliteSupportedLock) {
            mIsSatelliteSupported = null;
        }
        synchronized (mIsSatelliteProvisionedLock) {
            mIsSatelliteProvisioned = null;
        }
        synchronized (mIsSatelliteEnabledLock) {
            mIsSatelliteEnabled = null;
        }
        synchronized (mSatelliteCapabilitiesLock) {
            mSatelliteCapabilities = null;
        }
        mSatelliteModemInterface.setSatelliteServicePackageName(servicePackageName);
        return true;
    }

    /**
     * This API can be used by only CTS to update the timeout duration in milliseconds that
     * satellite should stay at listening mode to wait for the next incoming page before disabling
     * listening mode.
     *
     * @param timeoutMillis The timeout duration in millisecond.
     * @return {@code true} if the timeout duration is set successfully, {@code false} otherwise.
     */
    public boolean setSatelliteListeningTimeoutDuration(long timeoutMillis) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setSatelliteListeningTimeoutDuration: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        if (mSatelliteSessionController == null) {
            loge("mSatelliteSessionController is not initialized yet");
            return false;
        }
        return mSatelliteSessionController.setSatelliteListeningTimeoutDuration(timeoutMillis);
    }

    /**
     * This API can be used by only CTS to update the timeout duration in milliseconds whether
     * the device is aligned with the satellite for demo mode
     *
     * @param timeoutMillis The timeout duration in millisecond.
     * @return {@code true} if the timeout duration is set successfully, {@code false} otherwise.
     */
    public boolean setSatelliteDeviceAlignedTimeoutDuration(long timeoutMillis) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setSatelliteDeviceAlignedTimeoutDuration: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        return mDatagramController.setSatelliteDeviceAlignedTimeoutDuration(timeoutMillis);
    }

    /**
     * This API can be used by only CTS to update satellite gateway service package name.
     *
     * @param servicePackageName The package name of the satellite gateway service.
     * @return {@code true} if the satellite gateway service is set successfully,
     * {@code false} otherwise.
     */
    public boolean setSatelliteGatewayServicePackageName(@Nullable String servicePackageName) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setSatelliteGatewayServicePackageName: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        if (mSatelliteSessionController == null) {
            loge("mSatelliteSessionController is not initialized yet");
            return false;
        }
        return mSatelliteSessionController.setSatelliteGatewayServicePackageName(
                servicePackageName);
    }

    /**
     * This API can be used by only CTS to update satellite pointing UI app package and class names.
     *
     * @param packageName The package name of the satellite pointing UI app.
     * @param className The class name of the satellite pointing UI app.
     * @return {@code true} if the satellite pointing UI app package and class is set successfully,
     * {@code false} otherwise.
     */
    public boolean setSatellitePointingUiClassName(
            @Nullable String packageName, @Nullable String className) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setSatellitePointingUiClassName: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        return mPointingAppController.setSatellitePointingUiClassName(packageName, className);
    }

    /**
     * This API can be used in only testing to override connectivity status in monitoring emergency
     * calls and sending EVENT_DISPLAY_EMERGENCY_MESSAGE to Dialer.
     *
     * @param handoverType The type of handover from emergency call to satellite messaging. Use one
     *                     of the following values to enable the override:
     *                     0 - EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS
     *                     1 - EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911
     *                     To disable the override, use -1 for handoverType.
     * @param delaySeconds The event EVENT_DISPLAY_EMERGENCY_MESSAGE will be sent to Dialer
     *                     delaySeconds after the emergency call starts.
     * @return {@code true} if the handover type is set successfully, {@code false} otherwise.
     */
    public boolean setEmergencyCallToSatelliteHandoverType(int handoverType, int delaySeconds) {
        if (!isMockModemAllowed()) {
            loge("setEmergencyCallToSatelliteHandoverType: mock modem not allowed");
            return false;
        }
        if (isHandoverTypeValid(handoverType)) {
            mEnforcedEmergencyCallToSatelliteHandoverType = handoverType;
            mDelayInSendingEventDisplayEmergencyMessage = delaySeconds > 0 ? delaySeconds : 0;
        } else {
            mEnforcedEmergencyCallToSatelliteHandoverType =
                    INVALID_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
            mDelayInSendingEventDisplayEmergencyMessage = 0;
        }
        return true;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected int getEnforcedEmergencyCallToSatelliteHandoverType() {
        return mEnforcedEmergencyCallToSatelliteHandoverType;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected int getDelayInSendingEventDisplayEmergencyMessage() {
        return mDelayInSendingEventDisplayEmergencyMessage;
    }

    private boolean isHandoverTypeValid(int handoverType) {
        if (handoverType == EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS
                || handoverType == EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911) {
            return true;
        }
        return false;
    }

    /**
     * This function is used by {@link SatelliteModemInterface} to notify
     * {@link SatelliteController} that the satellite vendor service was just connected.
     * <p>
     * {@link SatelliteController} will send requests to satellite modem to check whether it support
     * satellite and whether it is provisioned. {@link SatelliteController} will use these cached
     * values to serve requests from its clients.
     * <p>
     * Because satellite vendor service might have just come back from a crash, we need to disable
     * the satellite modem so that resources will be cleaned up and internal states will be reset.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onSatelliteServiceConnected() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("onSatelliteServiceConnected: oemEnabledSatelliteFlag is disabled");
            return;
        }
        if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
            synchronized (mIsSatelliteSupportedLock) {
                if (mIsSatelliteSupported == null) {
                    ResultReceiver receiver = new ResultReceiver(this) {
                        @Override
                        protected void onReceiveResult(
                                int resultCode, Bundle resultData) {
                            logd("requestIsSatelliteSupported: resultCode="
                                    + resultCode);
                        }
                    };
                    requestIsSatelliteSupported(
                            SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, receiver);
                }
            }
        } else {
            logd("onSatelliteServiceConnected: Satellite vendor service is not supported."
                    + " Ignored the event");
        }
    }

    /**
     * This function is used by {@link com.android.internal.telephony.ServiceStateTracker} to notify
     * {@link SatelliteController} that it has received a request to power off the cellular radio
     * modem. {@link SatelliteController} will then power off the satellite modem.
     */
    public void onCellularRadioPowerOffRequested() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("onCellularRadioPowerOffRequested: oemEnabledSatelliteFlag is disabled");
            return;
        }

        mIsRadioOn = false;
        requestSatelliteEnabled(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                false /* enableSatellite */, false /* enableDemoMode */,
                new IIntegerConsumer.Stub() {
                    @Override
                    public void accept(int result) {
                        logd("onRadioPowerOffRequested: requestSatelliteEnabled result=" + result);
                    }
                });
    }

    /**
     * @return {@code true} if satellite is supported via OEM on the device,
     * {@code  false} otherwise.
     */
    public boolean isSatelliteSupportedViaOem() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("isSatelliteSupported: oemEnabledSatelliteFlag is disabled");
            return false;
        }
        Boolean supported = isSatelliteSupportedViaOemInternal();
        return (supported != null ? supported : false);
    }

    /**
     * @param subId Subscription ID.
     * @return The list of satellite PLMNs used for connecting to satellite networks.
     */
    @NonNull
    public List<String> getSatellitePlmnList(int subId) {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("getSatellitePlmnList: carrierEnabledSatelliteFlag is disabled");
            return new ArrayList<>();
        }
        synchronized (mSupportedSatelliteServicesLock) {
            if (mSatelliteServicesSupportedByCarriers.containsKey(subId)) {
                return new ArrayList<>(mSatelliteServicesSupportedByCarriers.get(subId).keySet());
            } else {
                return new ArrayList<>();
            }
        }
    }

    /**
     * @param subId Subscription ID.
     * @param plmn The satellite plmn.
     * @return The list of services supported by the carrier associated with the {@code subId} for
     * the satellite network {@code plmn}.
     */
    @NonNull
    public List<Integer> getSupportedSatelliteServices(int subId, String plmn) {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("getSupportedSatelliteServices: carrierEnabledSatelliteFlag is disabled");
            return new ArrayList<>();
        }
        synchronized (mSupportedSatelliteServicesLock) {
            if (mSatelliteServicesSupportedByCarriers.containsKey(subId)) {
                Map<String, Set<Integer>> supportedServices =
                        mSatelliteServicesSupportedByCarriers.get(subId);
                if (supportedServices != null && supportedServices.containsKey(plmn)) {
                    return new ArrayList<>(supportedServices.get(plmn));
                } else {
                    loge("getSupportedSatelliteServices: subId=" + subId + ", supportedServices "
                            + "does not contain key plmn=" + plmn);
                }
            } else {
                loge("getSupportedSatelliteServices: mSatelliteServicesSupportedByCarriers does "
                        + "not contain key subId=" + subId);
            }
            return new ArrayList<>();
        }
    }

    /**
     * Check whether satellite modem has to attach to a satellite network before sending/receiving
     * datagrams.
     *
     * @return {@code true} if satellite attach is required, {@code false} otherwise.
     */
    public boolean isSatelliteAttachRequired() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("isSatelliteAttachRequired: oemEnabledSatelliteFlag is disabled");
            return false;
        }

        synchronized (mSatelliteCapabilitiesLock) {
            if (mSatelliteCapabilities == null) {
                loge("isSatelliteAttachRequired: mSatelliteCapabilities is null");
                return false;
            }
            if (mSatelliteCapabilities.getSupportedRadioTechnologies().contains(
                    SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN)) {
                return true;
            }
            return false;
        }
    }

    /**
     * @return {@code true} if satellite is supported via carrier by any subscription on the device,
     * {@code false} otherwise.
     */
    public boolean isSatelliteSupportedViaCarrier() {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("isSatelliteSupportedViaCarrier: carrierEnabledSatelliteFlag is disabled");
            return false;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            if (isSatelliteSupportedViaCarrier(phone.getSubId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} if any subscription on the device is connected to satellite,
     * {@code false} otherwise.
     */
    private boolean isUsingNonTerrestrialNetworkViaCarrier() {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("isUsingNonTerrestrialNetwork: carrierEnabledSatelliteFlag is disabled");
            return false;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            ServiceState serviceState = phone.getServiceState();
            if (serviceState != null && serviceState.isUsingNonTerrestrialNetwork()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} if the device is connected to satellite via any carrier within the
     * {@link CarrierConfigManager#KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT}
     * duration, {@code false} otherwise.
     */
    public boolean isSatelliteConnectedViaCarrierWithinHysteresisTime() {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("isSatelliteConnectedViaCarrierWithinHysteresisTime: carrierEnabledSatelliteFlag"
                    + " is disabled");
            return false;
        }
        if (isUsingNonTerrestrialNetworkViaCarrier()) {
            return true;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            if (isSatelliteSupportedViaCarrier(phone.getSubId())) {
                synchronized (mSatelliteConnectedLock) {
                    Boolean isHysteresisTimeExpired =
                            mIsSatelliteConnectedViaCarrierHysteresisTimeExpired.get(
                                    phone.getSubId());
                    if (isHysteresisTimeExpired != null && isHysteresisTimeExpired) {
                        continue;
                    }

                    Long lastDisconnectedTime =
                            mLastSatelliteDisconnectedTimesMillis.get(phone.getSubId());
                    long satelliteConnectionHysteresisTime =
                            getSatelliteConnectionHysteresisTimeMillis(phone.getSubId());
                    if (lastDisconnectedTime != null
                            && (getElapsedRealtime() - lastDisconnectedTime)
                            <= satelliteConnectionHysteresisTime) {
                        return true;
                    } else {
                        mIsSatelliteConnectedViaCarrierHysteresisTimeExpired.put(
                                phone.getSubId(), true);
                    }
                }
            }
        }
        return false;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected long getElapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * If we have not successfully queried the satellite modem for its satellite service support,
     * we will retry the query one more time. Otherwise, we will return the cached result.
     */
    private Boolean isSatelliteSupportedViaOemInternal() {
        synchronized (mIsSatelliteSupportedLock) {
            if (mIsSatelliteSupported != null) {
                /* We have already successfully queried the satellite modem. */
                return mIsSatelliteSupported;
            }
        }
        /**
         * We have not successfully checked whether the modem supports satellite service.
         * Thus, we need to retry it now.
         */
        requestIsSatelliteSupported(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                new ResultReceiver(this) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        logd("requestIsSatelliteSupported: resultCode=" + resultCode);
                    }
                });
        return null;
    }

    private void handleEventProvisionSatelliteServiceDone(
            @NonNull ProvisionSatelliteServiceArgument arg,
            @SatelliteManager.SatelliteResult int result) {
        logd("handleEventProvisionSatelliteServiceDone: result="
                + result + ", subId=" + arg.subId);

        Consumer<Integer> callback = mSatelliteProvisionCallbacks.remove(arg.subId);
        if (callback == null) {
            loge("handleEventProvisionSatelliteServiceDone: callback is null for subId="
                    + arg.subId);
            mProvisionMetricsStats
                    .setResultCode(SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE)
                    .setIsProvisionRequest(true)
                    .reportProvisionMetrics();
            mControllerMetricsStats.reportProvisionCount(
                    SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
            return;
        }
        callback.accept(result);
    }

    private void handleEventDeprovisionSatelliteServiceDone(
            @NonNull ProvisionSatelliteServiceArgument arg,
            @SatelliteManager.SatelliteResult int result) {
        if (arg == null) {
            loge("handleEventDeprovisionSatelliteServiceDone: arg is null");
            return;
        }
        logd("handleEventDeprovisionSatelliteServiceDone: result="
                + result + ", subId=" + arg.subId);

        if (arg.callback != null) {
            arg.callback.accept(result);
            mProvisionMetricsStats.setResultCode(result)
                    .setIsProvisionRequest(false)
                    .reportProvisionMetrics();
            mControllerMetricsStats.reportDeprovisionCount(result);
        }
    }

    private void handleStartSatelliteTransmissionUpdatesDone(@NonNull AsyncResult ar) {
        SatelliteControllerHandlerRequest request = (SatelliteControllerHandlerRequest) ar.userObj;
        SatelliteTransmissionUpdateArgument arg =
                (SatelliteTransmissionUpdateArgument) request.argument;
        int errorCode =  SatelliteServiceUtils.getSatelliteError(ar,
                "handleStartSatelliteTransmissionUpdatesDone");
        arg.errorCallback.accept(errorCode);

        if (errorCode != SATELLITE_RESULT_SUCCESS) {
            mPointingAppController.setStartedSatelliteTransmissionUpdates(false);
            // We need to remove the callback from our listener list since the caller might not call
            // stopSatelliteTransmissionUpdates to unregister the callback in case of failure.
            mPointingAppController.unregisterForSatelliteTransmissionUpdates(arg.subId,
                    arg.errorCallback, arg.callback);
        } else {
            mPointingAppController.setStartedSatelliteTransmissionUpdates(true);
        }
    }

    /**
     * Posts the specified command to be executed on the main thread and returns immediately.
     *
     * @param command command to be executed on the main thread
     * @param argument additional parameters required to perform of the operation
     * @param phone phone object used to perform the operation.
     */
    private void sendRequestAsync(int command, @NonNull Object argument, @Nullable Phone phone) {
        SatelliteControllerHandlerRequest request = new SatelliteControllerHandlerRequest(
                argument, phone);
        Message msg = this.obtainMessage(command, request);
        msg.sendToTarget();
    }

    /**
     * Posts the specified command to be executed on the main thread. As this is a synchronous
     * request, it waits until the request is complete and then return the result.
     *
     * @param command command to be executed on the main thread
     * @param argument additional parameters required to perform of the operation
     * @param phone phone object used to perform the operation.
     * @return result of the operation
     */
    private @Nullable Object sendRequest(int command, @NonNull Object argument,
            @Nullable Phone phone) {
        if (Looper.myLooper() == this.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread");
        }

        SatelliteControllerHandlerRequest request = new SatelliteControllerHandlerRequest(
                argument, phone);
        Message msg = this.obtainMessage(command, request);
        msg.sendToTarget();

        synchronized (request) {
            while(request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                    // Do nothing, go back and wait until the request is complete.
                }
            }
        }
        return request.result;
    }

    /**
     * Check if satellite is provisioned for a subscription on the device.
     * @return true if satellite is provisioned on the given subscription else return false.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected Boolean isSatelliteViaOemProvisioned() {
        synchronized (mIsSatelliteProvisionedLock) {
            if (mIsSatelliteProvisioned != null) {
                return mIsSatelliteProvisioned;
            }
        }

        requestIsSatelliteProvisioned(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                new ResultReceiver(this) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        logd("requestIsSatelliteProvisioned: resultCode=" + resultCode);
                    }
                });
        return null;
    }

    private void handleSatelliteEnabled(SatelliteControllerHandlerRequest request) {
        RequestSatelliteEnabledArgument argument =
                (RequestSatelliteEnabledArgument) request.argument;
        if (!argument.enableSatellite && mSatelliteModemInterface.isSatelliteServiceSupported()) {
            synchronized (mIsSatelliteEnabledLock) {
                mWaitingForDisableSatelliteModemResponse = true;
                mWaitingForSatelliteModemOff = true;
            }
        }

        Message onCompleted = obtainMessage(EVENT_SET_SATELLITE_ENABLED_DONE, request);
        mSatelliteModemInterface.requestSatelliteEnabled(argument.enableSatellite,
                argument.enableDemoMode, onCompleted);
    }

    private void handleRequestSatelliteAttachRestrictionForCarrierCmd(
            SatelliteControllerHandlerRequest request) {
        RequestHandleSatelliteAttachRestrictionForCarrierArgument argument =
                (RequestHandleSatelliteAttachRestrictionForCarrierArgument) request.argument;

        if (argument.reason == SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER) {
            if (!persistSatelliteAttachEnabledForCarrierSetting(argument.subId)) {
                argument.callback.accept(SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
                return;
            }
        }

        evaluateEnablingSatelliteForCarrier(argument.subId, argument.reason, argument.callback);
    }

    private void updateSatelliteSupportedStateWhenSatelliteServiceConnected(boolean supported) {
        synchronized (mIsSatelliteSupportedLock) {
            mIsSatelliteSupported = supported;
        }
        mSatelliteSessionController = SatelliteSessionController.make(
                mContext, getLooper(), supported);
        if (supported) {
            registerForSatelliteProvisionStateChanged();
            registerForPendingDatagramCount();
            registerForSatelliteModemStateChanged();
            registerForNtnSignalStrengthChanged();
            registerForSatelliteCapabilitiesChanged();

            requestIsSatelliteProvisioned(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                    new ResultReceiver(this) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            logd("requestIsSatelliteProvisioned: resultCode=" + resultCode);
                            requestSatelliteEnabled(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                                    false, false,
                                    new IIntegerConsumer.Stub() {
                                        @Override
                                        public void accept(int result) {
                                            logd("requestSatelliteEnabled: result=" + result);
                                        }
                                    });
                        }
                    });
            requestSatelliteCapabilities(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                    new ResultReceiver(this) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            logd("requestSatelliteCapabilities: resultCode=" + resultCode);
                        }
                    });
        }
    }

    private void updateSatelliteEnabledState(boolean enabled, String caller) {
        synchronized (mIsSatelliteEnabledLock) {
            mIsSatelliteEnabled = enabled;
        }
        if (mSatelliteSessionController != null) {
            mSatelliteSessionController.onSatelliteEnabledStateChanged(enabled);
            mSatelliteSessionController.setDemoMode(mIsDemoModeEnabled);
        } else {
            loge(caller + ": mSatelliteSessionController is not initialized yet");
        }
    }

    private void registerForSatelliteProvisionStateChanged() {
        if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
            if (!mRegisteredForProvisionStateChangedWithSatelliteService.get()) {
                mSatelliteModemInterface.registerForSatelliteProvisionStateChanged(
                        this, EVENT_SATELLITE_PROVISION_STATE_CHANGED, null);
                mRegisteredForProvisionStateChangedWithSatelliteService.set(true);
            }
        }
    }

    private void registerForPendingDatagramCount() {
        if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
            if (!mRegisteredForPendingDatagramCountWithSatelliteService.get()) {
                mSatelliteModemInterface.registerForPendingDatagrams(
                        this, EVENT_PENDING_DATAGRAMS, null);
                mRegisteredForPendingDatagramCountWithSatelliteService.set(true);
            }
        }
    }

    private void registerForSatelliteModemStateChanged() {
        if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
            if (!mRegisteredForSatelliteModemStateChangedWithSatelliteService.get()) {
                mSatelliteModemInterface.registerForSatelliteModemStateChanged(
                        this, EVENT_SATELLITE_MODEM_STATE_CHANGED, null);
                mRegisteredForSatelliteModemStateChangedWithSatelliteService.set(true);
            }
        }
    }

    private void registerForNtnSignalStrengthChanged() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("registerForNtnSignalStrengthChanged: oemEnabledSatelliteFlag is disabled");
            return;
        }

        if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
            if (!mRegisteredForNtnSignalStrengthChanged.get()) {
                mSatelliteModemInterface.registerForNtnSignalStrengthChanged(
                        this, EVENT_NTN_SIGNAL_STRENGTH_CHANGED, null);
                mRegisteredForNtnSignalStrengthChanged.set(true);
            }
        }
    }

    private void registerForSatelliteCapabilitiesChanged() {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("registerForSatelliteCapabilitiesChanged: oemEnabledSatelliteFlag is disabled");
            return;
        }

        if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
            if (!mRegisteredForSatelliteCapabilitiesChanged.get()) {
                mSatelliteModemInterface.registerForSatelliteCapabilitiesChanged(
                        this, EVENT_SATELLITE_CAPABILITIES_CHANGED, null);
                mRegisteredForSatelliteCapabilitiesChanged.set(true);
            }
        }
    }

    private void handleEventSatelliteProvisionStateChanged(boolean provisioned) {
        logd("handleSatelliteProvisionStateChangedEvent: provisioned=" + provisioned);

        synchronized (mIsSatelliteProvisionedLock) {
            mIsSatelliteProvisioned = provisioned;
        }

        List<ISatelliteProvisionStateCallback> deadCallersList = new ArrayList<>();
        mSatelliteProvisionStateChangedListeners.values().forEach(listener -> {
            try {
                listener.onSatelliteProvisionStateChanged(provisioned);
            } catch (RemoteException e) {
                logd("handleSatelliteProvisionStateChangedEvent RemoteException: " + e);
                deadCallersList.add(listener);
            }
        });
        deadCallersList.forEach(listener -> {
            mSatelliteProvisionStateChangedListeners.remove(listener.asBinder());
        });
    }

    private void handleEventSatelliteModemStateChanged(
            @SatelliteManager.SatelliteModemState int state) {
        logd("handleEventSatelliteModemStateChanged: state=" + state);
        if (state == SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE
                || state == SatelliteManager.SATELLITE_MODEM_STATE_OFF) {
            synchronized (mIsSatelliteEnabledLock) {
                if ((state == SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE)
                        || ((mIsSatelliteEnabled == null || isSatelliteEnabled())
                        && !mWaitingForDisableSatelliteModemResponse)) {
                    int error = (state == SatelliteManager.SATELLITE_MODEM_STATE_OFF)
                            ? SATELLITE_RESULT_SUCCESS
                            : SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE;
                    Consumer<Integer> callback = null;
                    synchronized (mSatelliteEnabledRequestLock) {
                        if (mSatelliteEnabledRequest != null) {
                            callback = mSatelliteEnabledRequest.callback;
                        }
                    }
                    moveSatelliteToOffStateAndCleanUpResources(error, callback);
                } else {
                    logd("Either waiting for the response of disabling satellite modem or the event"
                            + " should be ignored because isSatelliteEnabled="
                            + isSatelliteEnabled()
                            + ", mIsSatelliteEnabled=" + mIsSatelliteEnabled);
                }
                mWaitingForSatelliteModemOff = false;
            }
        } else {
            if (mSatelliteSessionController != null) {
                mSatelliteSessionController.onSatelliteModemStateChanged(state);
            } else {
                loge("handleEventSatelliteModemStateChanged: mSatelliteSessionController is null");
            }
        }
    }

    private void handleEventNtnSignalStrengthChanged(NtnSignalStrength ntnSignalStrength) {
        logd("handleEventNtnSignalStrengthChanged: ntnSignalStrength=" + ntnSignalStrength);
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("handleEventNtnSignalStrengthChanged: oemEnabledSatelliteFlag is disabled");
            return;
        }

        synchronized (mNtnSignalsStrengthLock) {
            mNtnSignalStrength = ntnSignalStrength;
        }

        List<INtnSignalStrengthCallback> deadCallersList = new ArrayList<>();
        mNtnSignalStrengthChangedListeners.values().forEach(listener -> {
            try {
                listener.onNtnSignalStrengthChanged(ntnSignalStrength);
            } catch (RemoteException e) {
                logd("handleEventNtnSignalStrengthChanged RemoteException: " + e);
                deadCallersList.add(listener);
            }
        });
        deadCallersList.forEach(listener -> {
            mNtnSignalStrengthChangedListeners.remove(listener.asBinder());
        });
    }

    private void handleEventSatelliteCapabilitiesChanged(SatelliteCapabilities capabilities) {
        logd("handleEventSatelliteCapabilitiesChanged()");
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("handleEventSatelliteCapabilitiesChanged: oemEnabledSatelliteFlag is disabled");
            return;
        }

        synchronized (mSatelliteCapabilitiesLock) {
            mSatelliteCapabilities = capabilities;
        }

        List<ISatelliteCapabilitiesCallback> deadCallersList = new ArrayList<>();
        mSatelliteCapabilitiesChangedListeners.values().forEach(listener -> {
            try {
                listener.onSatelliteCapabilitiesChanged(capabilities);
            } catch (RemoteException e) {
                logd("handleEventSatelliteCapabilitiesChanged RemoteException: " + e);
                deadCallersList.add(listener);
            }
        });
        deadCallersList.forEach(listener -> {
            mSatelliteCapabilitiesChangedListeners.remove(listener.asBinder());
        });
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected void setSettingsKeyForSatelliteMode(int val) {
        logd("setSettingsKeyForSatelliteMode val: " + val);
        Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.SATELLITE_MODE_ENABLED, val);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected boolean areAllRadiosDisabled() {
        synchronized (mRadioStateLock) {
            if ((mDisableBTOnSatelliteEnabled && mBTStateEnabled)
                    || (mDisableNFCOnSatelliteEnabled && mNfcStateEnabled)
                    || (mDisableWifiOnSatelliteEnabled && mWifiStateEnabled)
                    || (mDisableUWBOnSatelliteEnabled && mUwbStateEnabled)) {
                logd("All radios are not disabled yet.");
                return false;
            }
            logd("All radios are disabled.");
            return true;
        }
    }

    private void evaluateToSendSatelliteEnabledSuccess() {
        logd("evaluateToSendSatelliteEnabledSuccess");
        synchronized (mSatelliteEnabledRequestLock) {
            if (areAllRadiosDisabled() && (mSatelliteEnabledRequest != null)
                    && mWaitingForRadioDisabled) {
                logd("Sending success to callback that sent enable satellite request");
                setDemoModeEnabled(mSatelliteEnabledRequest.enableDemoMode);
                synchronized (mIsSatelliteEnabledLock) {
                    mIsSatelliteEnabled = mSatelliteEnabledRequest.enableSatellite;
                }
                mSatelliteEnabledRequest.callback.accept(SATELLITE_RESULT_SUCCESS);
                updateSatelliteEnabledState(
                        mSatelliteEnabledRequest.enableSatellite,
                        "EVENT_SET_SATELLITE_ENABLED_DONE");
                mSatelliteEnabledRequest = null;
                mWaitingForRadioDisabled = false;
            }
        }
    }

    private void resetSatelliteEnabledRequest() {
        logd("resetSatelliteEnabledRequest");
        synchronized (mSatelliteEnabledRequestLock) {
            mSatelliteEnabledRequest = null;
            mWaitingForRadioDisabled = false;
        }
    }

    private void moveSatelliteToOffStateAndCleanUpResources(
            @SatelliteManager.SatelliteResult int error, @Nullable Consumer<Integer> callback) {
        logd("moveSatelliteToOffStateAndCleanUpResources");
        synchronized (mIsSatelliteEnabledLock) {
            resetSatelliteEnabledRequest();
            setDemoModeEnabled(false);
            mIsSatelliteEnabled = false;
            setSettingsKeyForSatelliteMode(SATELLITE_MODE_ENABLED_FALSE);
            if (callback != null) callback.accept(error);
            updateSatelliteEnabledState(
                    false, "moveSatelliteToOffStateAndCleanUpResources");
        }
    }

    private void setDemoModeEnabled(boolean enabled) {
        mIsDemoModeEnabled = enabled;
        mDatagramController.setDemoMode(mIsDemoModeEnabled);
    }

    private boolean isMockModemAllowed() {
        return (DEBUG || SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false));
    }

    private void configureSatellitePlmnForCarrier(int subId) {
        logd("configureSatellitePlmnForCarrier");
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("configureSatellitePlmnForCarrier: carrierEnabledSatelliteFlag is disabled");
            return;
        }
        synchronized (mSupportedSatelliteServicesLock) {
            List<String> carrierPlmnList;
            if (mSatelliteServicesSupportedByCarriers.containsKey(subId)) {
                carrierPlmnList =
                        mSatelliteServicesSupportedByCarriers.get(subId).keySet().stream().toList();
            } else {
                carrierPlmnList = new ArrayList<>();
            }
            int slotId = SubscriptionManager.getSlotIndex(subId);
            mSatelliteModemInterface.setSatellitePlmn(slotId, carrierPlmnList,
                    SatelliteServiceUtils.mergeStrLists(
                            carrierPlmnList, mSatellitePlmnListFromOverlayConfig),
                    obtainMessage(EVENT_SET_SATELLITE_PLMN_INFO_DONE));
        }
    }

    private void handleSetSatellitePlmnInfoDoneEvent(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        SatelliteServiceUtils.getSatelliteError(ar, "handleSetSatellitePlmnInfoCmd");
    }

    private void updateSupportedSatelliteServicesForActiveSubscriptions() {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("updateSupportedSatelliteServicesForActiveSubscriptions: "
                    + "carrierEnabledSatelliteFlag is disabled");
            return;
        }

        synchronized (mSupportedSatelliteServicesLock) {
            mSatelliteServicesSupportedByCarriers.clear();
            int[] activeSubIds = mSubscriptionManagerService.getActiveSubIdList(true);
            if (activeSubIds != null) {
                for (int subId : activeSubIds) {
                    updateSupportedSatelliteServices(subId);
                }
            } else {
                loge("updateSupportedSatelliteServicesForActiveSubscriptions: "
                        + "activeSubIds is null");
            }
        }
    }

    private void updateSupportedSatelliteServices(int subId) {
        synchronized (mSupportedSatelliteServicesLock) {
            mSatelliteServicesSupportedByCarriers.put(
                    subId, readSupportedSatelliteServicesFromCarrierConfig(subId));
        }
    }

    @NonNull
    private List<String> readSatellitePlmnsFromOverlayConfig() {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            logd("readSatellitePlmnsFromOverlayConfig: carrierEnabledSatelliteFlag is disabled");
            return new ArrayList<>();
        }

        String[] devicePlmns = readStringArrayFromOverlayConfig(
                R.array.config_satellite_providers);
        return Arrays.stream(devicePlmns).toList();
    }

    @NonNull
    private Map<String, Set<Integer>> readSupportedSatelliteServicesFromCarrierConfig(int subId) {
        synchronized (mCarrierConfigArrayLock) {
            PersistableBundle config = mCarrierConfigArray.get(subId);
            if (config == null) {
                config = getConfigForSubId(subId);
                mCarrierConfigArray.put(subId, config);
            }
            return SatelliteServiceUtils.parseSupportedSatelliteServices(
                    config.getPersistableBundle(
                            KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE));
        }
    }

    @NonNull private PersistableBundle getConfigForSubId(int subId) {
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(subId,
                KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT);
        if (config == null || config.isEmpty()) {
            config = CarrierConfigManager.getDefaultConfig();
        }
        return config;
    }

    private void handleCarrierConfigChanged(int slotIndex, int subId, int carrierId,
            int specificCarrierId) {
        logd("handleCarrierConfigChanged(): slotIndex(" + slotIndex + "), subId("
                + subId + "), carrierId(" + carrierId + "), specificCarrierId("
                + specificCarrierId + ")");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }

        updateCarrierConfig(subId);
        updateSupportedSatelliteServicesForActiveSubscriptions();
        configureSatellitePlmnForCarrier(subId);

        synchronized (mIsSatelliteEnabledLock) {
            mSatelliteAttachRestrictionForCarrierArray.clear();
            mIsSatelliteAttachEnabledForCarrierArrayPerSub.clear();
        }

        setSatelliteAttachEnabledForCarrierOnSimLoaded(subId);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected void updateCarrierConfig(int subId) {
        synchronized (mCarrierConfigArrayLock) {
            mCarrierConfigArray.put(subId, getConfigForSubId(subId));
        }
    }

    /**
     * When a SIM is loaded, we need to check if users has enabled satellite attach for the carrier
     * associated with the SIM, and evaluate if satellite should be enabled for the carrier.
     *
     * @param subId Subscription ID.
     */
    private void setSatelliteAttachEnabledForCarrierOnSimLoaded(int subId) {
        synchronized (mIsSatelliteEnabledLock) {
            if (isSatelliteAttachEnabledForCarrierByUser(subId)
                    && !mIsSatelliteAttachEnabledForCarrierArrayPerSub.getOrDefault(subId,
                    false)) {
                evaluateEnablingSatelliteForCarrier(subId,
                        SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER, null);
            }
        }
    }

    @NonNull
    private String[] readStringArrayFromOverlayConfig(@ArrayRes int id) {
        String[] strArray = null;
        try {
            strArray = mContext.getResources().getStringArray(id);
        } catch (Resources.NotFoundException ex) {
            loge("readStringArrayFromOverlayConfig: id= " + id + ", ex=" + ex);
        }
        if (strArray == null) {
            strArray = new String[0];
        }
        return strArray;
    }

    private boolean isSatelliteSupportedViaCarrier(int subId) {
        return getConfigForSubId(subId)
                .getBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL);
    }

    /**
     * Check if satellite attach is enabled by user for the carrier associated with the
     * {@code subId}.
     *
     * @param subId Subscription ID.
     *
     * @return Returns {@code true} if satellite attach for carrier is enabled by user,
     * {@code false} otherwise.
     */
    private boolean isSatelliteAttachEnabledForCarrierByUser(int subId) {
        synchronized (mIsSatelliteEnabledLock) {
            Set<Integer> cachedRestrictionSet =
                    mSatelliteAttachRestrictionForCarrierArray.get(subId);
            if (cachedRestrictionSet != null) {
                return !cachedRestrictionSet.contains(
                        SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER);
            } else {
                logd("isSatelliteAttachEnabledForCarrierByUser() no correspondent cache, "
                        + "load from persist storage");
                try {
                    String enabled =
                            mSubscriptionManagerService.getSubscriptionProperty(subId,
                                    SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                                    mContext.getOpPackageName(), mContext.getAttributionTag());

                    if (enabled == null) {
                        loge("isSatelliteAttachEnabledForCarrierByUser: invalid subId, subId="
                                + subId);
                        return false;
                    }

                    if (enabled.isEmpty()) {
                        loge("isSatelliteAttachEnabledForCarrierByUser: no data for subId(" + subId
                                + ")");
                        return false;
                    }

                    synchronized (mIsSatelliteEnabledLock) {
                        boolean result = enabled.equals("1");
                        if (!result) {
                            mSatelliteAttachRestrictionForCarrierArray.put(subId, new HashSet<>());
                            mSatelliteAttachRestrictionForCarrierArray.get(subId).add(
                                    SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER);
                        }
                        return result;
                    }
                } catch (IllegalArgumentException | SecurityException ex) {
                    loge("isSatelliteAttachEnabledForCarrierByUser: ex=" + ex);
                    return false;
                }
            }
        }
    }

    /**
     * Check whether there is any reason to restrict satellite communication for the carrier
     * associated with the {@code subId}.
     *
     * @param subId Subscription ID
     * @return {@code true} when there is at least on reason, {@code false} otherwise.
     */
    private boolean hasReasonToRestrictSatelliteCommunicationForCarrier(int subId) {
        synchronized (mIsSatelliteEnabledLock) {
            return !mSatelliteAttachRestrictionForCarrierArray
                    .getOrDefault(subId, Collections.emptySet()).isEmpty();
        }
    }

    /**
     * Save user setting for enabling satellite attach for the carrier associated with the
     * {@code subId} to persistent storage.
     *
     * @param subId Subscription ID.
     *
     * @return {@code true} if persist successful, {@code false} otherwise.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected boolean persistSatelliteAttachEnabledForCarrierSetting(int subId) {
        logd("persistSatelliteAttachEnabledForCarrierSetting");
        if (!isValidSubscriptionId(subId)) {
            loge("persistSatelliteAttachEnabledForCarrierSetting: subId is not valid,"
                    + " subId=" + subId);
            return false;
        }

        synchronized (mIsSatelliteEnabledLock) {
            try {
                mSubscriptionManagerService.setSubscriptionProperty(subId,
                        SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                        mSatelliteAttachRestrictionForCarrierArray.get(subId)
                                .contains(SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER)
                                ? "0" : "1");
            } catch (IllegalArgumentException | SecurityException ex) {
                loge("persistSatelliteAttachEnabledForCarrierSetting, ex=" + ex);
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluate whether satellite attach for carrier should be restricted.
     *
     * @param subId Subscription Id to evaluate for.
     * @return {@code true} satellite attach is restricted, {@code false} otherwise.
     */
    private boolean isSatelliteRestrictedForCarrier(int subId) {
        return !isSatelliteAttachEnabledForCarrierByUser(subId)
                || hasReasonToRestrictSatelliteCommunicationForCarrier(subId);
    }

    /**
     * Check whether satellite is enabled for carrier at modem.
     *
     * @param subId Subscription ID to check for.
     * @return {@code true} if satellite modem is enabled, {@code false} otherwise.
     */
    private boolean isSatelliteEnabledForCarrierAtModem(int subId) {
        synchronized (mIsSatelliteEnabledLock) {
            return mIsSatelliteAttachEnabledForCarrierArrayPerSub.getOrDefault(subId, false);
        }
    }

    /**
     * Evaluate whether satellite modem for carrier should be enabled or not.
     * <p>
     * Satellite will be enabled only when the following conditions are met:
     * <ul>
     * <li>Users want to enable it.</li>
     * <li>There is no satellite communication restriction, which is added by
     * {@link #addSatelliteAttachRestrictionForCarrier(int, int, IIntegerConsumer)}</li>
     * <li>The carrier config {@link
     * android.telephony.CarrierConfigManager#KEY_SATELLITE_ATTACH_SUPPORTED_BOOL} is set to
     * {@code true}.</li>
     * </ul>
     *
     * @param subId Subscription Id for evaluate for.
     * @param callback The callback for getting the result of enabling satellite.
     */
    private void evaluateEnablingSatelliteForCarrier(int subId, int reason,
            @Nullable Consumer<Integer> callback) {
        if (callback == null) {
            callback = errorCode -> logd("evaluateEnablingSatelliteForCarrier: "
                    + "SetSatelliteAttachEnableForCarrier error code =" + errorCode);
        }

        if (!isSatelliteSupportedViaCarrier(subId)) {
            logd("Satellite for carrier is not supported. Only user setting is stored");
            callback.accept(SATELLITE_RESULT_SUCCESS);
            return;
        }

        /* Request to enable or disable the satellite in the cellular modem only when the desired
        state and the current state are different. */
        boolean isSatelliteExpectedToBeEnabled = !isSatelliteRestrictedForCarrier(subId);
        if (isSatelliteExpectedToBeEnabled != isSatelliteEnabledForCarrierAtModem(subId)) {
            if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                int simSlot = SubscriptionManager.getSlotIndex(subId);
                RequestHandleSatelliteAttachRestrictionForCarrierArgument argument =
                        new RequestHandleSatelliteAttachRestrictionForCarrierArgument(subId,
                                reason, callback);
                SatelliteControllerHandlerRequest request =
                        new SatelliteControllerHandlerRequest(argument,
                                SatelliteServiceUtils.getPhone(subId));
                Message onCompleted = obtainMessage(
                        EVENT_EVALUATE_SATELLITE_ATTACH_RESTRICTION_CHANGE_DONE, request);
                mSatelliteModemInterface.requestSetSatelliteEnabledForCarrier(simSlot,
                        isSatelliteExpectedToBeEnabled, onCompleted);
            } else {
                callback.accept(SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE);
            }
        } else {
            callback.accept(SATELLITE_RESULT_SUCCESS);
        }
    }

    @SatelliteManager.SatelliteResult private int evaluateOemSatelliteRequestAllowed(
            boolean isProvisionRequired) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("oemEnabledSatelliteFlag is disabled");
            return SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
        }
        if (!mSatelliteModemInterface.isSatelliteServiceSupported()) {
            return SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
        }

        Boolean satelliteSupported = isSatelliteSupportedViaOemInternal();
        if (satelliteSupported == null) {
            return SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
        }
        if (!satelliteSupported) {
            return SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
        }

        if (isProvisionRequired) {
            Boolean satelliteProvisioned = isSatelliteViaOemProvisioned();
            if (satelliteProvisioned == null) {
                return SatelliteManager.SATELLITE_RESULT_INVALID_TELEPHONY_STATE;
            }
            if (!satelliteProvisioned) {
                return SatelliteManager.SATELLITE_RESULT_SERVICE_NOT_PROVISIONED;
            }
        }

        return SATELLITE_RESULT_SUCCESS;
    }

    /**
     * Returns the non-terrestrial network radio technology that the satellite modem currently
     * supports. If multiple technologies are available, returns the first supported technology.
     */
    @VisibleForTesting
    protected @SatelliteManager.NTRadioTechnology int getSupportedNtnRadioTechnology() {
        synchronized (mSatelliteCapabilitiesLock) {
            if (mSatelliteCapabilities != null) {
                return mSatelliteCapabilities.getSupportedRadioTechnologies()
                        .stream().findFirst().orElse(SatelliteManager.NT_RADIO_TECHNOLOGY_UNKNOWN);
            }
            return SatelliteManager.NT_RADIO_TECHNOLOGY_UNKNOWN;
        }
    }

    private void sendErrorAndReportSessionMetrics(@SatelliteManager.SatelliteResult int error,
            Consumer<Integer> result) {
        result.accept(error);
        SessionMetricsStats.getInstance()
                .setInitializationResult(error)
                .setRadioTechnology(getSupportedNtnRadioTechnology())
                .reportSessionMetrics();
    }

    private void registerForServiceStateChanged() {
        if (!mFeatureFlags.carrierEnabledSatelliteFlag()) {
            return;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            phone.registerForServiceStateChanged(this, EVENT_SERVICE_STATE_CHANGED, null);
        }
    }

    private void handleEventServiceStateChanged() {
        handleServiceStateForSatelliteConnectionViaCarrier();
    }

    private void handleServiceStateForSatelliteConnectionViaCarrier() {
        for (Phone phone : PhoneFactory.getPhones()) {
            ServiceState serviceState = phone.getServiceState();
            if (serviceState != null) {
                synchronized (mSatelliteConnectedLock) {
                    if (serviceState.isUsingNonTerrestrialNetwork()) {
                        mWasSatelliteConnectedViaCarrier.put(phone.getSubId(), true);
                        mIsSatelliteConnectedViaCarrierHysteresisTimeExpired.put(
                                phone.getSubId(), false);
                    } else {
                        Boolean connected = mWasSatelliteConnectedViaCarrier.get(phone.getSubId());
                        if (connected != null && connected) {
                            // The device just got disconnected from a satellite network.
                            mLastSatelliteDisconnectedTimesMillis.put(
                                    phone.getSubId(), getElapsedRealtime());
                            mIsSatelliteConnectedViaCarrierHysteresisTimeExpired.put(
                                    phone.getSubId(), false);
                        }
                        mWasSatelliteConnectedViaCarrier.put(phone.getSubId(), false);
                    }
                }
            }
        }
    }

    private long getSatelliteConnectionHysteresisTimeMillis(int subId) {
        synchronized (mCarrierConfigArrayLock) {
            PersistableBundle config = mCarrierConfigArray.get(subId);
            if (config == null) {
                config = getConfigForSubId(subId);
                mCarrierConfigArray.put(subId, config);
            }
            return (config.getInt(
                    KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT) * 1000L);
        }
    }

    /**
     * This API can be used by only CTS to override the cached value for the device overlay config
     * value : config_send_satellite_datagram_to_modem_in_demo_mode, which determines whether
     * outgoing satellite datagrams should be sent to modem in demo mode.
     *
     * @param shouldSendToModemInDemoMode Whether send datagram in demo mode should be sent to
     * satellite modem or not.
     *
     * @return {@code true} if the operation is successful, {@code false} otherwise.
     */
    public boolean setShouldSendDatagramToModemInDemoMode(boolean shouldSendToModemInDemoMode) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            logd("setShouldSendDatagramToModemInDemoMode: oemEnabledSatelliteFlag is disabled");
            return false;
        }

        if (!isMockModemAllowed()) {
            logd("setShouldSendDatagramToModemInDemoMode: mock modem not allowed.");
            return false;
        }

        mDatagramController.setShouldSendDatagramToModemInDemoMode(shouldSendToModemInDemoMode);
        return true;
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
