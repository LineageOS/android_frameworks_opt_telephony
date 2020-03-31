/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.telephony.CarrierConfigManager.EXTRA_SLOT_INDEX;
import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY;
import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.EXTRA_SIM_STATE;
import static android.telephony.TelephonyManager.SIM_STATE_ABSENT;
import static android.telephony.TelephonyManager.SIM_STATE_LOADED;
import static android.telephony.TelephonyManager.SIM_STATE_NOT_READY;
import static android.telephony.TelephonyManager.SIM_STATE_UNKNOWN;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.UserManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.Log;

import com.android.internal.telephony.uicc.IccUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CarrierPrivilegesTracker will track the Carrier Privileges for a specific {@link Phone}.
 * Registered Telephony entities will receive notifications when the UIDs with these privileges
 * change.
 */
public class CarrierPrivilegesTracker extends Handler {
    private static final String TAG = CarrierPrivilegesTracker.class.getSimpleName();

    private static final String SHA_1 = "SHA-1";
    private static final String SHA_256 = "SHA-256";

    /**
     * Action to register a Registrant with this Tracker.
     * obj: Registrant that will be notified of Carrier Privileged UID changes.
     */
    private static final int ACTION_REGISTER_LISTENER = 1;

    /**
     * Action to unregister a Registrant with this Tracker.
     * obj: Handler used by the Registrant that will be removed.
     */
    private static final int ACTION_UNREGISTER_LISTENER = 2;

    /**
     * Action for tracking when Carrier Configs are updated.
     * arg1: Subscription Id for the Carrier Configs update being broadcast
     * arg2: Slot Index for the Carrier Configs update being broadcast
     */
    private static final int ACTION_CARRIER_CONFIG_CERTS_UPDATED = 3;

    /**
     * Action for tracking when the Phone's SIM state changes.
     * arg1: slotId that this Action applies to
     * arg2: simState reported by this Broadcast
     */
    private static final int ACTION_SIM_STATE_UPDATED = 4;

    /**
     * Action for tracking when a package is installed or replaced on the device.
     * obj: String package name that was installed or replaced on the device.
     */
    private static final int ACTION_PACKAGE_ADDED_OR_REPLACED = 5;

    private final Context mContext;
    private final Phone mPhone;
    private final CarrierConfigManager mCarrierConfigManager;
    private final PackageManager mPackageManager;
    private final UserManager mUserManager;
    private final TelephonyManager mTelephonyManager;
    private final RegistrantList mRegistrantList;
    private final Set<String> mCarrierConfigCerts;

    // TODO(b/151981841): Use Set<UiccAccessRule> to also check for package names loaded from SIM
    private final Set<String> mUiccCerts;

    // Map of PackageName -> Certificate hashes for that Package
    private final Map<String, Set<String>> mInstalledPackageCerts;

    // Map of PackageName -> UIDs for that Package
    private final Map<String, Set<Integer>> mCachedUids;
    private int[] mPrivilegedUids;

    private final BroadcastReceiver mIntentReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action == null) return;

                    switch (action) {
                        case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED: {
                            Bundle extras = intent.getExtras();
                            int slotIndex = extras.getInt(EXTRA_SLOT_INDEX);
                            int subId =
                                    extras.getInt(
                                            EXTRA_SUBSCRIPTION_INDEX, INVALID_SUBSCRIPTION_ID);
                            sendMessage(
                                    obtainMessage(
                                            ACTION_CARRIER_CONFIG_CERTS_UPDATED,
                                            subId,
                                            slotIndex));
                            break;
                        }
                        case TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED: // fall through
                        case TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED: {
                            Bundle extras = intent.getExtras();
                            int simState = extras.getInt(EXTRA_SIM_STATE, SIM_STATE_UNKNOWN);
                            int slotId =
                                    extras.getInt(PhoneConstants.PHONE_KEY, INVALID_SIM_SLOT_INDEX);

                            if (simState != SIM_STATE_ABSENT
                                    && simState != SIM_STATE_NOT_READY
                                    && simState != SIM_STATE_LOADED) return;

                            sendMessage(obtainMessage(ACTION_SIM_STATE_UPDATED, slotId, simState));
                            break;
                        }
                        case Intent.ACTION_PACKAGE_ADDED: // fall through
                        case Intent.ACTION_PACKAGE_REPLACED: {
                            Uri uri = intent.getData();
                            String pkgName = (uri != null) ? uri.getSchemeSpecificPart() : null;
                            if (TextUtils.isEmpty(pkgName)) {
                                Log.e(TAG, "Failed to get package from Intent");
                                return;
                            }

                            sendMessage(obtainMessage(ACTION_PACKAGE_ADDED_OR_REPLACED, pkgName));
                            break;
                        }
                    }
                }
            };

    public CarrierPrivilegesTracker(
            @NonNull Looper looper, @NonNull Phone phone, @NonNull Context context) {
        super(looper);
        mContext = context;
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mPackageManager = context.getSystemService(PackageManager.class);
        mUserManager = context.getSystemService(UserManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mPhone = phone;

        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        mContext.registerReceiver(mIntentReceiver, filter);

        mRegistrantList = new RegistrantList();
        mCarrierConfigCerts = new ArraySet<>();
        mUiccCerts = new ArraySet<>();
        mInstalledPackageCerts = new ArrayMap<>();
        mCachedUids = new ArrayMap<>();
        mPrivilegedUids = new int[0];
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_REGISTER_LISTENER: {
                handleRegisterListener((Registrant) msg.obj);
                break;
            }
            case ACTION_UNREGISTER_LISTENER: {
                handleUnregisterListener((Handler) msg.obj);
                break;
            }
            case ACTION_CARRIER_CONFIG_CERTS_UPDATED: {
                handleCarrierConfigUpdated(msg.arg1, msg.arg2);
                break;
            }
            case ACTION_SIM_STATE_UPDATED: {
                handleSimStateChanged(msg.arg1, msg.arg2);
                break;
            }
            case ACTION_PACKAGE_ADDED_OR_REPLACED: {
                String pkgName = (String) msg.obj;
                handlePackageAddedOrReplaced(pkgName);
                break;
            }
            default: {
                Log.e(TAG, "Received unknown msg type: " + msg.what);
                break;
            }
        }
    }

    private void handleRegisterListener(Registrant registrant) {
        mRegistrantList.add(registrant);
        registrant.notifyResult(mPrivilegedUids);
    }

    private void handleUnregisterListener(Handler handler) {
        mRegistrantList.remove(handler);
    }

    private void handleCarrierConfigUpdated(int subId, int slotIndex) {
        if (slotIndex != mPhone.getPhoneId()) return;

        Set<String> updatedCarrierConfigCerts = new ArraySet<>();

        // Carrier Config broadcasts with INVALID_SUBSCRIPTION_ID when the SIM is removed. This is
        // an expected event. When this happens, clear the certificates from the previous configs.
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            PersistableBundle carrierConfigs = mCarrierConfigManager.getConfigForSubId(subId);
            if (mCarrierConfigManager.isConfigForIdentifiedCarrier(carrierConfigs)) {
                String[] carrierConfigCerts =
                        carrierConfigs.getStringArray(KEY_CARRIER_CERTIFICATE_STRING_ARRAY);

                if (carrierConfigCerts != null) {
                    for (String cert : carrierConfigCerts) {
                        updatedCarrierConfigCerts.add(cert.toUpperCase());
                    }
                }
            }
        }

        maybeUpdateCertsAndNotifyRegistrants(mCarrierConfigCerts, updatedCarrierConfigCerts);
    }

    private void handleSimStateChanged(int slotId, int simState) {
        if (slotId != mPhone.getPhoneId()) return;

        Set<String> updatedUiccCerts = new ArraySet<>();

        // Only include the UICC certs if the SIM is fully loaded
        if (simState == SIM_STATE_LOADED) {
            TelephonyManager telMan = mTelephonyManager.createForSubscriptionId(mPhone.getSubId());
            if (telMan.hasIccCard(mPhone.getPhoneId())) {
                List<String> uiccCerts = telMan.getCertsFromCarrierPrivilegeAccessRules();
                if (uiccCerts != null) {
                    for (String cert : uiccCerts) {
                        updatedUiccCerts.add(cert.toUpperCase());
                    }
                }
            }
        }

        maybeUpdateCertsAndNotifyRegistrants(mUiccCerts, updatedUiccCerts);
    }

    private void handlePackageAddedOrReplaced(String pkgName) {
        PackageInfo pkg;
        try {
            pkg = mPackageManager.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Error getting installed package: " + pkgName, e);
            return;
        }

        Set<String> certs = new ArraySet<>();
        List<Signature> signatures = UiccAccessRule.getSignatures(pkg);
        for (Signature signature : signatures) {
            byte[] sha1 = UiccAccessRule.getCertHash(signature, SHA_1);
            certs.add(IccUtils.bytesToHexString(sha1).toUpperCase());

            byte[] sha256 = UiccAccessRule.getCertHash(signature, SHA_256);
            certs.add(IccUtils.bytesToHexString(sha256).toUpperCase());
        }

        mInstalledPackageCerts.put(pkg.packageName, certs);

        try {
            mCachedUids.put(pkg.packageName, getUidsForPackage(pkg.packageName));
        } catch (NameNotFoundException exception) {
            // Didn't find package. Continue looking at other packages
            Log.e(TAG, "Unable to find uid for package: " + pkg.packageName);
        }

        maybeUpdatePrivilegedUidsAndNotifyRegistrants();
    }

    private void maybeUpdateCertsAndNotifyRegistrants(
            Set<String> currentCerts, Set<String> updatedCerts) {
        if (!currentCerts.equals(updatedCerts)) {
            currentCerts.clear();
            currentCerts.addAll(updatedCerts);

            maybeUpdatePrivilegedUidsAndNotifyRegistrants();
        }
    }

    private void maybeUpdatePrivilegedUidsAndNotifyRegistrants() {
        int[] currentPrivilegedUids = getCurrentPrivilegedUids();

        // Sort UIDs for the equality check
        Arrays.sort(currentPrivilegedUids);
        if (!Arrays.equals(mPrivilegedUids, currentPrivilegedUids)) {
            mPrivilegedUids = currentPrivilegedUids;
            mRegistrantList.notifyResult(mPrivilegedUids);
        }
    }

    private int[] getCurrentPrivilegedUids() {
        Set<Integer> privilegedUids = new ArraySet<>();
        for (Map.Entry<String, Set<String>> e : mInstalledPackageCerts.entrySet()) {
            if (isPackagePrivileged(e.getValue())) {
                try {
                    privilegedUids.addAll(getUidsForPackage(e.getKey()));
                } catch (NameNotFoundException exception) {
                    // Didn't find package. Continue looking at other packages
                    Log.e(TAG, "Unable to find uid for package: " + e.getKey());
                }
            }
        }

        IntArray result = new IntArray(privilegedUids.size());
        for (int uid : privilegedUids) {
            result.add(uid);
        }
        return result.toArray();
    }

    private boolean isPackagePrivileged(Set<String> certs) {
        for (String cert : certs) {
            if (mCarrierConfigCerts.contains(cert) || mUiccCerts.contains(cert)) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> getUidsForPackage(String pkgName) throws NameNotFoundException {
        if (mCachedUids.containsKey(pkgName)) {
            return mCachedUids.get(pkgName);
        }

        Set<Integer> uids = new ArraySet<>();
        List<UserInfo> users = mUserManager.getUsers();
        for (UserInfo user : users) {
            uids.add(
                    mPackageManager.getPackageUidAsUser(
                            pkgName, user.getUserHandle().getIdentifier()));
        }
        mCachedUids.put(pkgName, uids);
        return uids;
    }

    /**
     * Registers the given Registrant with this tracker.
     *
     * <p>After being registered, the Registrant will be notified with the current Carrier
     * Privileged UIDs for this Phone.
     *
     * @hide
     */
    public void registerCarrierPrivilegesListener(Handler h, int what, Object obj) {
        sendMessage(obtainMessage(ACTION_REGISTER_LISTENER, new Registrant(h, what, obj)));
    }

    /**
     * Unregisters the given listener with this tracker.
     *
     * @hide
     */
    public void unregisterCarrierPrivilegesListener(Handler handler) {
        sendMessage(obtainMessage(ACTION_UNREGISTER_LISTENER, handler));
    }
}
