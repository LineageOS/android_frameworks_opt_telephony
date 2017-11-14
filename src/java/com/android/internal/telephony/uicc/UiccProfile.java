/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * This class represents the carrier profiles in the {@link UiccCard}. Each profile contains
 * multiple {@link UiccCardApplication}, one {@link UiccCarrierPrivilegeRules} and one
 * {@link CatService}.
 *
 * Profile is related to {@link android.telephony.SubscriptionInfo} but those two concepts are
 * different. {@link android.telephony.SubscriptionInfo} contains all the subscription information
 * while Profile contains all the {@link UiccCardApplication} which will be used to fetch those
 * subscription information from the {@link UiccCard}.
 *
 * {@hide}
 */
public class UiccProfile {
    protected static final String LOG_TAG = "UiccProfile";
    protected static final boolean DBG = true;

    private static final String OPERATOR_BRAND_OVERRIDE_PREFIX = "operator_branding_";

    private final Object mLock = new Object();
    private PinState mUniversalPinState;
    private int mGsmUmtsSubscriptionAppIndex;
    private int mCdmaSubscriptionAppIndex;
    private int mImsSubscriptionAppIndex;
    private UiccCardApplication[] mUiccApplications =
        new UiccCardApplication[IccCardStatus.CARD_MAX_APPS];
    private Context mContext;
    private CommandsInterface mCi;
    private UiccCard mUiccCard; //parent
    private CatService mCatService;
    private UiccCarrierPrivilegeRules mCarrierPrivilegeRules;

    private RegistrantList mCarrierPrivilegeRegistrants = new RegistrantList();

    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 15;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 16;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 17;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 18;
    private static final int EVENT_SIM_IO_DONE = 19;
    private static final int EVENT_CARRIER_PRIVILEGES_LOADED = 20;

    private static final LocalLog sLocalLog = new LocalLog(100);

    private final int mPhoneId;

    public UiccProfile(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId,
            UiccCard uiccCard) {
        if (DBG) log("Creating profile");
        mUiccCard = uiccCard;
        mPhoneId = phoneId;
        update(c, ci, ics);
    }

    /**
     * Dispose the UiccProfile.
     */
    public void dispose() {
        synchronized (mLock) {
            if (DBG) log("Disposing profile");
            if (mCatService != null) mCatService.dispose();
            for (UiccCardApplication app : mUiccApplications) {
                if (app != null) {
                    app.dispose();
                }
            }
            mCatService = null;
            mUiccApplications = null;
            mCarrierPrivilegeRules = null;
        }
    }

    /**
     * Update the UiccProfile.
     */
    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        synchronized (mLock) {
            mUniversalPinState = ics.mUniversalPinState;
            mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            mContext = c;
            mCi = ci;

            //update applications
            if (DBG) log(ics.mApplications.length + " applications");
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] == null) {
                    //Create newly added Applications
                    if (i < ics.mApplications.length) {
                        mUiccApplications[i] = new UiccCardApplication(mUiccCard,
                                ics.mApplications[i], mContext, mCi);
                    }
                } else if (i >= ics.mApplications.length) {
                    //Delete removed applications
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                } else {
                    //Update the rest
                    mUiccApplications[i].update(ics.mApplications[i], mContext, mCi);
                }
            }

            createAndUpdateCatServiceLocked();

            log("Before privilege rules: " + mCarrierPrivilegeRules);
            if (mCarrierPrivilegeRules == null) {
                mCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(mUiccCard,
                        mHandler.obtainMessage(EVENT_CARRIER_PRIVILEGES_LOADED));
            }

            sanitizeApplicationIndexesLocked();
        }
    }

    private void createAndUpdateCatServiceLocked() {
        if (mUiccApplications.length > 0 && mUiccApplications[0] != null) {
            // Initialize or Reinitialize CatService
            if (mCatService == null) {
                mCatService = CatService.getInstance(mCi, mContext, mUiccCard, mPhoneId);
            } else {
                mCatService.update(mCi, mContext, mUiccCard);
            }
        } else {
            if (mCatService != null) {
                mCatService.dispose();
            }
            mCatService = null;
        }
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccProfile finalized");
    }

    /**
     * This function makes sure that application indexes are valid
     * and resets invalid indexes. (This should never happen, but in case
     * RIL misbehaves we need to manage situation gracefully)
     */
    private void sanitizeApplicationIndexesLocked() {
        mGsmUmtsSubscriptionAppIndex =
                checkIndexLocked(
                        mGsmUmtsSubscriptionAppIndex, AppType.APPTYPE_SIM, AppType.APPTYPE_USIM);
        mCdmaSubscriptionAppIndex =
                checkIndexLocked(
                        mCdmaSubscriptionAppIndex, AppType.APPTYPE_RUIM, AppType.APPTYPE_CSIM);
        mImsSubscriptionAppIndex =
                checkIndexLocked(mImsSubscriptionAppIndex, AppType.APPTYPE_ISIM, null);
    }

    private int checkIndexLocked(int index, AppType expectedAppType, AppType altExpectedAppType) {
        if (mUiccApplications == null || index >= mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        }

        if (index < 0) {
            // This is normal. (i.e. no application of this type)
            return -1;
        }

        if (mUiccApplications[index].getType() != expectedAppType
                && mUiccApplications[index].getType() != altExpectedAppType) {
            loge("App index " + index + " is invalid since it's not "
                    + expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }

        // Seems to be valid
        return index;
    }

    /**
     * Registers the handler when carrier privilege rules are loaded.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForCarrierPrivilegeRulesLoaded(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);

            mCarrierPrivilegeRegistrants.add(r);

            if (areCarrierPriviligeRulesLoaded()) {
                r.notifyRegistrant();
            }
        }
    }

    /**
     * Unregister for notifications when carrier privilege rules are loaded.
     *
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForCarrierPrivilegeRulesLoaded(Handler h) {
        synchronized (mLock) {
            mCarrierPrivilegeRegistrants.remove(h);
        }
    }

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_OPEN_LOGICAL_CHANNEL_DONE:
                case EVENT_CLOSE_LOGICAL_CHANNEL_DONE:
                case EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE:
                case EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE:
                case EVENT_SIM_IO_DONE:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        loglocal("Exception: " + ar.exception);
                        log("Error in SIM access with exception" + ar.exception);
                    }
                    AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
                    ((Message) ar.userObj).sendToTarget();
                    break;
                case EVENT_CARRIER_PRIVILEGES_LOADED:
                    onCarrierPriviligesLoadedMessage();
                    break;
                default:
                    loge("Unknown Event " + msg.what);
            }
        }
    };

    private boolean isPackageInstalled(String pkgName) {
        PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES);
            if (DBG) log(pkgName + " is installed.");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            if (DBG) log(pkgName + " is not installed.");
            return false;
        }
    }

    private class ClickListener implements DialogInterface.OnClickListener {
        String mPkgName;
        ClickListener(String pkgName) {
            this.mPkgName = pkgName;
        }
        @Override
        public void onClick(DialogInterface dialog, int which) {
            synchronized (mLock) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    Intent market = new Intent(Intent.ACTION_VIEW);
                    market.setData(Uri.parse("market://details?id=" + mPkgName));
                    market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(market);
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    if (DBG) log("Not now clicked for carrier app dialog.");
                }
            }
        }
    }

    private void promptInstallCarrierApp(String pkgName) {
        DialogInterface.OnClickListener listener = new ClickListener(pkgName);

        Resources r = Resources.getSystem();
        String message = r.getString(R.string.carrier_app_dialog_message);
        String buttonTxt = r.getString(R.string.carrier_app_dialog_button);
        String notNowTxt = r.getString(R.string.carrier_app_dialog_not_now);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setMessage(message)
                .setNegativeButton(notNowTxt, listener)
                .setPositiveButton(buttonTxt, listener)
                .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void onCarrierPriviligesLoadedMessage() {
        UsageStatsManager usm = (UsageStatsManager) mContext.getSystemService(
                Context.USAGE_STATS_SERVICE);
        if (usm != null) {
            usm.onCarrierPrivilegedAppsChanged();
        }
        synchronized (mLock) {
            mCarrierPrivilegeRegistrants.notifyRegistrants();
            String whitelistSetting = Settings.Global.getString(mContext.getContentResolver(),
                    Settings.Global.CARRIER_APP_WHITELIST);
            if (TextUtils.isEmpty(whitelistSetting)) {
                return;
            }
            HashSet<String> carrierAppSet = new HashSet<String>(
                    Arrays.asList(whitelistSetting.split("\\s*;\\s*")));
            if (carrierAppSet.isEmpty()) {
                return;
            }

            List<String> pkgNames = mCarrierPrivilegeRules.getPackageNames();
            for (String pkgName : pkgNames) {
                if (!TextUtils.isEmpty(pkgName) && carrierAppSet.contains(pkgName)
                        && !isPackageInstalled(pkgName)) {
                    promptInstallCarrierApp(pkgName);
                }
            }
        }
    }

    /**
     * Check whether the specified type of application exists in the profile.
     *
     * @param type UICC application type.
     */
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null && mUiccApplications[i].getType() == type) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Return the universal pin state of the profile.
     */
    public PinState getUniversalPinState() {
        synchronized (mLock) {
            return mUniversalPinState;
        }
    }

    /**
     * Return the application of the specified family.
     *
     * @param family UICC application family.
     * @return application corresponding to family or a null if no match found
     */
    public UiccCardApplication getApplication(int family) {
        synchronized (mLock) {
            int index = IccCardStatus.CARD_MAX_APPS;
            switch (family) {
                case UiccController.APP_FAM_3GPP:
                    index = mGsmUmtsSubscriptionAppIndex;
                    break;
                case UiccController.APP_FAM_3GPP2:
                    index = mCdmaSubscriptionAppIndex;
                    break;
                case UiccController.APP_FAM_IMS:
                    index = mImsSubscriptionAppIndex;
                    break;
            }
            if (index >= 0 && index < mUiccApplications.length) {
                return mUiccApplications[index];
            }
            return null;
        }
    }

    /**
     * Return the application with the index of the array.
     *
     * @param index Index of the application array.
     * @return application corresponding to index or a null if no match found
     */
    public UiccCardApplication getApplicationIndex(int index) {
        synchronized (mLock) {
            if (index >= 0 && index < mUiccApplications.length) {
                return mUiccApplications[index];
            }
            return null;
        }
    }

    /**
     * Returns the SIM application of the specified type.
     *
     * @param type ICC application type
     * (@see com.android.internal.telephony.PhoneConstants#APPTYPE_xxx)
     * @return application corresponding to type or a null if no match found
     */
    public UiccCardApplication getApplicationByType(int type) {
        synchronized (mLock) {
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null
                        && mUiccApplications[i].getType().ordinal() == type) {
                    return mUiccApplications[i];
                }
            }
            return null;
        }
    }

    /**
     * Resets the application with the input AID. Returns true if any changes were made.
     *
     * A null aid implies a card level reset - all applications must be reset.
     */
    public boolean resetAppWithAid(String aid) {
        synchronized (mLock) {
            boolean changed = false;
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null
                        && (TextUtils.isEmpty(aid) || aid.equals(mUiccApplications[i].getAid()))) {
                    // Delete removed applications
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                    changed = true;
                }
            }
            if (TextUtils.isEmpty(aid)) {
                if (mCarrierPrivilegeRules != null) {
                    mCarrierPrivilegeRules = null;
                    changed = true;
                }
                if (mCatService != null) {
                    mCatService.dispose();
                    mCatService = null;
                    changed = true;
                }
            }
            return changed;
        }
    }

    /**
     * Exposes {@link CommandsInterface#iccOpenLogicalChannel}
     */
    public void iccOpenLogicalChannel(String aid, int p2, Message response) {
        loglocal("Open Logical Channel: " + aid + " , " + p2 + " by pid:" + Binder.getCallingPid()
                + " uid:" + Binder.getCallingUid());
        mCi.iccOpenLogicalChannel(aid, p2,
                mHandler.obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccCloseLogicalChannel}
     */
    public void iccCloseLogicalChannel(int channel, Message response) {
        loglocal("Close Logical Channel: " + channel);
        mCi.iccCloseLogicalChannel(channel,
                mHandler.obtainMessage(EVENT_CLOSE_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccTransmitApduLogicalChannel}
     */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduLogicalChannel(channel, cla, command, p1, p2, p3,
                data, mHandler.obtainMessage(EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccTransmitApduBasicChannel}
     */
    public void iccTransmitApduBasicChannel(int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduBasicChannel(cla, command, p1, p2, p3,
                data, mHandler.obtainMessage(EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccIO}
     */
    public void iccExchangeSimIO(int fileID, int command, int p1, int p2, int p3,
            String pathID, Message response) {
        mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null,
                mHandler.obtainMessage(EVENT_SIM_IO_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#sendEnvelopeWithStatus}
     */
    public void sendEnvelopeWithStatus(String contents, Message response) {
        mCi.sendEnvelopeWithStatus(contents, response);
    }

    /**
     * Returns number of applications on this card
     */
    public int getNumApplications() {
        int count = 0;
        for (UiccCardApplication a : mUiccApplications) {
            if (a != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the id of the phone which is associated with this profile.
     */
    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Returns true iff carrier privileges rules are null (dont need to be loaded) or loaded.
     */
    public boolean areCarrierPriviligeRulesLoaded() {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                || carrierPrivilegeRules.areCarrierPriviligeRulesLoaded();
    }

    /**
     * Returns true if there are some carrier privilege rules loaded and specified.
     */
    public boolean hasCarrierPrivilegeRules() {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules != null && carrierPrivilegeRules.hasCarrierPrivilegeRules();
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatus(signature, packageName);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatus(packageManager, packageName);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(PackageInfo packageInfo) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatus(packageInfo);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatusForCurrentTransaction}.
     */
    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction(
                        packageManager);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPackageNamesForIntent}.
     */
    public List<String> getCarrierPackageNamesForIntent(
            PackageManager packageManager, Intent intent) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null ? null :
                carrierPrivilegeRules.getCarrierPackageNamesForIntent(
                        packageManager, intent);
    }

    /** Returns a reference to the current {@link UiccCarrierPrivilegeRules}. */
    private UiccCarrierPrivilegeRules getCarrierPrivilegeRules() {
        synchronized (mLock) {
            return mCarrierPrivilegeRules;
        }
    }

    /**
     * Sets the overridden operator brand.
     */
    public boolean setOperatorBrandOverride(String brand) {
        log("setOperatorBrandOverride: " + brand);
        log("current iccId: " + getIccId());

        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }

        SharedPreferences.Editor spEditor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        String key = OPERATOR_BRAND_OVERRIDE_PREFIX + iccId;
        if (brand == null) {
            spEditor.remove(key).commit();
        } else {
            spEditor.putString(key, brand).commit();
        }
        return true;
    }

    /**
     * Returns the overridden operator brand.
     */
    public String getOperatorBrandOverride() {
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return null;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        return sp.getString(OPERATOR_BRAND_OVERRIDE_PREFIX + iccId, null);
    }

    /**
     * Returns the iccid of the profile.
     */
    public String getIccId() {
        // ICCID should be same across all the apps.
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                IccRecords ir = app.getIccRecords();
                if (ir != null && ir.getIccId() != null) {
                    return ir.getIccId();
                }
            }
        }
        return null;
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    private void loglocal(String msg) {
        if (DBG) sLocalLog.log(msg);
    }

    /**
     * Dump
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccProfile:");
        pw.println(" mCi=" + mCi);
        pw.println(" mCatService=" + mCatService);
        for (int i = 0; i < mCarrierPrivilegeRegistrants.size(); i++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i + "]="
                    + ((Registrant) mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.println(" mUniversalPinState=" + mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + mImsSubscriptionAppIndex);
        pw.println(" mUiccApplications: length=" + mUiccApplications.length);
        for (int i = 0; i < mUiccApplications.length; i++) {
            if (mUiccApplications[i] == null) {
                pw.println("  mUiccApplications[" + i + "]=" + null);
            } else {
                pw.println("  mUiccApplications[" + i + "]="
                        + mUiccApplications[i].getType() + " " + mUiccApplications[i]);
            }
        }
        pw.println();
        // Print details of all applications
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                app.dump(fd, pw, args);
                pw.println();
            }
        }
        // Print details of all IccRecords
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                IccRecords ir = app.getIccRecords();
                if (ir != null) {
                    ir.dump(fd, pw, args);
                    pw.println();
                }
            }
        }
        // Print UiccCarrierPrivilegeRules and registrants.
        if (mCarrierPrivilegeRules == null) {
            pw.println(" mCarrierPrivilegeRules: null");
        } else {
            pw.println(" mCarrierPrivilegeRules: " + mCarrierPrivilegeRules);
            mCarrierPrivilegeRules.dump(fd, pw, args);
        }
        pw.println(" mCarrierPrivilegeRegistrants: size=" + mCarrierPrivilegeRegistrants.size());
        for (int i = 0; i < mCarrierPrivilegeRegistrants.size(); i++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i + "]="
                    + ((Registrant) mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.flush();
        pw.println("sLocalLog:");
        sLocalLog.dump(fd, pw, args);
        pw.flush();
    }
}
