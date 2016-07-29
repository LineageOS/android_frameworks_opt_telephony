/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

import static android.Manifest.permission.READ_PHONE_STATE;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.view.WindowManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;

// import com.mediatek.internal.telephony.IccCardType.SvlteCardType;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
// import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;

import android.os.SystemProperties;

import com.android.internal.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * {@hide}
 */
public class UiccCard {
    protected static final String LOG_TAG = "UiccCard";
    protected static final boolean DBG = true;

    public static final String EXTRA_ICC_CARD_ADDED =
            "com.android.internal.telephony.uicc.ICC_CARD_ADDED";

    private static final String OPERATOR_BRAND_OVERRIDE_PREFIX = "operator_branding_";

    private static final Intent MOBILE_NETWORK_SETTINGS_MSIM
            = new Intent("com.android.settings.sim.SIM_SUB_INFO_SETTINGS");

    private final Object mLock = new Object();
    private CardState mCardState;
    private PinState mUniversalPinState;
    private int mGsmUmtsSubscriptionAppIndex;
    private int mCdmaSubscriptionAppIndex;
    private int mImsSubscriptionAppIndex;
    private UiccCardApplication[] mUiccApplications =
            new UiccCardApplication[IccCardStatus.CARD_MAX_APPS];
    private Context mContext;
    private CommandsInterface mCi;
    private CatService mCatService;
    private RadioState mLastRadioState =  RadioState.RADIO_UNAVAILABLE;
    private UiccCarrierPrivilegeRules mCarrierPrivilegeRules;
    private boolean mDefaultAppsActivated;
    private UICCConfig mUICCConfig = null;

    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mCarrierPrivilegeRegistrants = new RegistrantList();

    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARD_ADDED = 14;
    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 15;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 16;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 17;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 18;
    private static final int EVENT_SIM_IO_DONE = 19;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 20;
    private static final int EVENT_SIM_GET_ATR_DONE = 21;

    private static final LocalLog mLocalLog = new LocalLog(100);

    private int mPhoneId;

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics) {
        if (DBG) log("Creating");
        mCardState = ics.mCardState;
        update(c, ci, ics);
    }

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        mCardState = ics.mCardState;
        mPhoneId = phoneId;
        update(c, ci, ics);
    }

    protected UiccCard() {
    }

    public void dispose() {
        synchronized (mLock) {
            if (DBG) log("Disposing card");
            if (mCatService != null) mCatService.dispose();
            for (UiccCardApplication app : mUiccApplications) {
                if (app != null) {
                    app.dispose();
                }
            }
            mCatService = null;
            mUiccApplications = null;
            mCarrierPrivilegeRules = null;
            mUICCConfig = null;

            // MTK
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && mIsSvlte) {
                mSvlteCi.unregisterForEusimReady(mHandler);
                mCi.unregisterForCdmaCardType(mHandler);
            }
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        update(c, ci, ics, true);
    }

    // with MTK extension
    public void update(Context c, CommandsInterface ci, IccCardStatus ics, boolean isUpdateSimInfo) {
        synchronized (mLock) {
            CardState oldState = mCardState;
            mCardState = ics.mCardState;
            mUniversalPinState = ics.mUniversalPinState;
            // MTK SVLTE
            // mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            // mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            mContext = c;
            mCi = ci;

            // MTK-START
            // check the UiccCard type.
            int targetIndex = -1;
            log("update mIsSvlte=" + mIsSvlte);
            if (mIsSvlte) {
                IccCardApplicationStatus.AppType appType =
                        IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN;
                for (int i = 0; i < ics.mApplications.length; i++) {
                    if (ics.mApplications[i] != null &&
                            (ics.mApplications[i].app_type ==
                                 IccCardApplicationStatus.AppType.APPTYPE_CSIM ||
                             ics.mApplications[i].app_type ==
                                 IccCardApplicationStatus.AppType.APPTYPE_RUIM ||
                             ics.mApplications[i].app_type ==
                                 IccCardApplicationStatus.AppType.APPTYPE_SIM ||
                             ics.mApplications[i].app_type ==
                                 IccCardApplicationStatus.AppType.APPTYPE_USIM)) {
                        appType = ics.mApplications[i].app_type;
                        break;
                    }
                }
                log("update appType=" + appType);
                if (appType == IccCardApplicationStatus.AppType.APPTYPE_CSIM ||
                    appType == IccCardApplicationStatus.AppType.APPTYPE_RUIM) {
                    mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
                    targetIndex = mCdmaSubscriptionAppIndex;
                    //Reset gsm index if to conflict with cdma index
                    if (targetIndex == mGsmUmtsSubscriptionAppIndex) {
                        mGsmUmtsSubscriptionAppIndex = -1;
                        log("reset mGsmUmtsSubscriptionAppIndex to "
                            + mGsmUmtsSubscriptionAppIndex);
                    }
                } else if (appType == IccCardApplicationStatus.AppType.APPTYPE_SIM
                        || appType == IccCardApplicationStatus.AppType.APPTYPE_USIM) {
                    mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
                    targetIndex = mGsmUmtsSubscriptionAppIndex;
                    //Reset cdma index if to conflict with gsm index
                    if (targetIndex == mCdmaSubscriptionAppIndex) {
                        mCdmaSubscriptionAppIndex = -1;
                        log("reset mCdmaSubscriptionAppIndex to "
                            + mCdmaSubscriptionAppIndex);
                    }
                } else {
                    loge("update, but appType: " + appType);
                }
            } else {
                mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
                mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            }

            log("update targetIndex=" + targetIndex +
                    "  mGsmUmtsSubscriptionAppIndex=" + mGsmUmtsSubscriptionAppIndex +
                    "  mCdmaSubscriptionAppIndex=" + mCdmaSubscriptionAppIndex +
                    "  mUiccApplications.length=" + mUiccApplications.length);
            // MTK-END

            //update applications
            if (mUICCConfig == null)
                mUICCConfig = new UICCConfig();
            if (DBG) log(ics.mApplications.length + " applications");
            for ( int i = 0; i < mUiccApplications.length; i++) {
                // MTK-START
                if (targetIndex != i && targetIndex >= 0) {
                    continue;
                }
                log("mUiccApplications[i]=" + mUiccApplications[i]);
                // MTK-END
                if (mUiccApplications[i] == null) {
                    //Create newly added Applications
                    if (i < ics.mApplications.length) {
                        mUiccApplications[i] = new UiccCardApplication(this,
                                ics.mApplications[i], mContext,
                                (mIsSvlte && targetIndex == mGsmUmtsSubscriptionAppIndex) ? mSvlteCi : mCi);
                        log("new mUiccApplications[" + i + "]");
                    }
                } else if (i >= ics.mApplications.length) {
                    //Delete removed applications
                    // MTK
                    if (mUiccApplications[i] != null) {
                        mUiccApplications[i].dispose();
                        mUiccApplications[i] = null;
                        log("dispose mUiccApplications[" + i + "]");
                    }
                } else {
                    //Update the rest
                    // MTK
                    if (mUiccApplications[i] != null) {
                        mUiccApplications[i].update(ics.mApplications[i], mContext,
                                (mIsSvlte && targetIndex == mGsmUmtsSubscriptionAppIndex) ?  mSvlteCi : mCi);
                        log("update mUiccApplications[" + i + "]");
                    }
                }
            }

            // MTK
            if (mIsSvlte) {
                if (targetIndex == mGsmUmtsSubscriptionAppIndex) {
                    createAndUpdateCatService(mSvlteCi);
                } else {
                    //no need to create catservice for csim and ruim.
                }
            } else {
                createAndUpdateCatService(mCi);
            }

            // Reload the carrier privilege rules if necessary.
            log("Before privilege rules: " + mCarrierPrivilegeRules + " : " + mCardState);
            if (mCarrierPrivilegeRules == null && mCardState == CardState.CARDSTATE_PRESENT) {
                mCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(this,
                        mHandler.obtainMessage(EVENT_CARRIER_PRIVILIGES_LOADED));
            } else if (mCarrierPrivilegeRules != null && mCardState != CardState.CARDSTATE_PRESENT) {
                mCarrierPrivilegeRules = null;
            }

            sanitizeApplicationIndexes();

            RadioState radioState = mCi.getRadioState();
            if (DBG) log("update: radioState=" + radioState + " mLastRadioState="
                    + mLastRadioState /* MTK */ + " isUpdateSimInfo= " + isUpdateSimInfo);
            // No notifications while radio is off or we just powering up
            if (isUpdateSimInfo) {  // MTK
            if (radioState == RadioState.RADIO_ON && mLastRadioState == RadioState.RADIO_ON) {
                if (oldState != CardState.CARDSTATE_ABSENT &&
                        mCardState == CardState.CARDSTATE_ABSENT) {
                    if (DBG) log("update: notify card removed");
                    mAbsentRegistrants.notifyRegistrants();
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_REMOVED, null));
                } else if (oldState == CardState.CARDSTATE_ABSENT &&
                        mCardState != CardState.CARDSTATE_ABSENT) {
                    if (DBG) log("update: notify card added");
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_ADDED, null));
                }
            }
            }  // MTK
            if (mCi.needsOldRilFeature("simactivation")) {
                if (mCardState == CardState.CARDSTATE_PRESENT) {
                    if (!mDefaultAppsActivated) {
                        activateDefaultApps();
                        mDefaultAppsActivated = true;
                    }
                } else {
                    // SIM removed, reset activation flag to make sure
                    // to re-run the activation at the next insertion
                    mDefaultAppsActivated = false;
                }
            }

            mLastRadioState = radioState;
        }
    }

    // MTK
    // protected void createAndUpdateCatService() {
    protected void createAndUpdateCatService(CommandsInterface ci) {
        if (mUiccApplications.length > 0 && mUiccApplications[0] != null) {
            // Initialize or Reinitialize CatService
            if (mCatService == null) {
                mCatService = CatService.getInstance(ci /* mCi */, mContext, this, mPhoneId);
            } else {
                ((CatService)mCatService).update(ci /* mCi */, mContext, this);
            }
        } else {
            if (mCatService != null) {
                mCatService.dispose();
            }
            mCatService = null;
        }
    }

    public CatService getCatService() {
        return mCatService;
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccCard finalized");
    }

    /**
     * This function makes sure that application indexes are valid
     * and resets invalid indexes. (This should never happen, but in case
     * RIL misbehaves we need to manage situation gracefully)
     */
    private void sanitizeApplicationIndexes() {
        mGsmUmtsSubscriptionAppIndex =
                checkIndex(mGsmUmtsSubscriptionAppIndex, AppType.APPTYPE_SIM, AppType.APPTYPE_USIM);
        mCdmaSubscriptionAppIndex =
                checkIndex(mCdmaSubscriptionAppIndex, AppType.APPTYPE_RUIM, AppType.APPTYPE_CSIM);
        mImsSubscriptionAppIndex =
                checkIndex(mImsSubscriptionAppIndex, AppType.APPTYPE_ISIM, null);

        // MTK
        if (DBG) {
            log("sanitizeApplicationIndexes  GSM index= " + mGsmUmtsSubscriptionAppIndex +
                    "  CDMA index = " + mCdmaSubscriptionAppIndex + "  IMS index = "
                    + mImsSubscriptionAppIndex);
        }
    }

    private int checkIndex(int index, AppType expectedAppType, AppType altExpectedAppType) {
        if (mUiccApplications == null || index >= mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        }

        if (index < 0) {
            // This is normal. (i.e. no application of this type)
            return -1;
        }

        // MTK-START
        if (mUiccApplications[index] == null) {
            loge("App index " + index + " is null since there are no applications");
            return -1;
        }

        log("checkIndex mUiccApplications[" + index + "].getType()= "
            + mUiccApplications[index].getType());
        // MTK-END

        if (mUiccApplications[index].getType() != expectedAppType &&
            mUiccApplications[index].getType() != altExpectedAppType) {
            loge("App index " + index + " is invalid since it's not " +
                    expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }

        // Seems to be valid
        return index;
    }

    private void activateDefaultApps() {
        int gsmIndex = mGsmUmtsSubscriptionAppIndex;
        int cdmaIndex = mCdmaSubscriptionAppIndex;

        if (gsmIndex < 0 || cdmaIndex < 0) {
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] == null) {
                    continue;
                }

                AppType appType = mUiccApplications[i].getType();
                if (gsmIndex < 0
                        && (appType == AppType.APPTYPE_USIM || appType == AppType.APPTYPE_SIM)) {
                    gsmIndex = i;
                } else if (cdmaIndex < 0 &&
                        (appType == AppType.APPTYPE_CSIM || appType == AppType.APPTYPE_RUIM)) {
                    cdmaIndex = i;
                }
            }
        }
        if (gsmIndex >= 0) {
            mCi.setUiccSubscription(gsmIndex, true, null);
        }
        if (cdmaIndex >= 0) {
            mCi.setUiccSubscription(cdmaIndex, true, null);
        }
    }

    /**
     * Notifies handler of any transition into State.ABSENT
     */
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mAbsentRegistrants.add(r);

            if (mCardState == CardState.CARDSTATE_ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (mLock) {
            mAbsentRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler when carrier privilege rules are loaded.
     */
    public void registerForCarrierPrivilegeRulesLoaded(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mCarrierPrivilegeRegistrants.add(r);

            if (areCarrierPriviligeRulesLoaded()) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForCarrierPrivilegeRulesLoaded(Handler h) {
        synchronized (mLock) {
            mCarrierPrivilegeRegistrants.remove(h);
        }
    }

    private void onIccSwap(boolean isAdded) {

        boolean isHotSwapSupported = mContext.getResources().getBoolean(
                R.bool.config_hotswapCapable);

        if (isHotSwapSupported) {
            log("onIccSwap: isHotSwapSupported is true, don't prompt for rebooting");
            // If an Icc card is being removed, it may be the default data/voice/messaging
            // subscription holder. We need to notify the user that they may have to configure
            // their defaults again. Relevant only in MSIM scenario
            if (isAdded && (TelephonyManager.getDefault().getPhoneCount() > 1)) {
                notifyOfPotentialConfigurationNeeded();
            }
            return;
        }
        log("onIccSwap: isHotSwapSupported is false, prompt for rebooting");

        promptForRestart(isAdded);
    }

    private void promptForRestart(boolean isAdded) {
        synchronized (mLock) {
            final Resources res = mContext.getResources();
            final String dialogComponent = res.getString(
                    R.string.config_iccHotswapPromptForRestartDialogComponent);
            if (dialogComponent != null) {
                Intent intent = new Intent().setComponent(ComponentName.unflattenFromString(
                        dialogComponent)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_ICC_CARD_ADDED, isAdded);
                try {
                    mContext.startActivity(intent);
                    return;
                } catch (ActivityNotFoundException e) {
                    loge("Unable to find ICC hotswap prompt for restart activity: " + e);
                }
            }

            // TODO: Here we assume the device can't handle SIM hot-swap
            //      and has to reboot. We may want to add a property,
            //      e.g. REBOOT_ON_SIM_SWAP, to indicate if modem support
            //      hot-swap.
            DialogInterface.OnClickListener listener = null;


            // TODO: SimRecords is not reset while SIM ABSENT (only reset while
            //       Radio_off_or_not_available). Have to reset in both both
            //       added or removed situation.
            listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    synchronized (mLock) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            if (DBG) log("Reboot due to SIM swap");
                            PowerManager pm = (PowerManager) mContext
                                    .getSystemService(Context.POWER_SERVICE);
                            pm.reboot("SIM is added.");
                        }
                    }
                }

            };

            Resources r = Resources.getSystem();

            String title = (isAdded) ? r.getString(R.string.sim_added_title) :
                r.getString(R.string.sim_removed_title);
            String message = (isAdded) ? r.getString(R.string.sim_added_message) :
                r.getString(R.string.sim_removed_message);
            String buttonTxt = r.getString(R.string.sim_restart_button);

            AlertDialog dialog = new AlertDialog.Builder(mContext)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonTxt, listener)
            .create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        }
    }

    private void notifyOfPotentialConfigurationNeeded() {
        INotificationManager inm = NotificationManager.getService();
        if (inm == null) {
            return;
        }

        Resources r = Resources.getSystem();

        Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(com.android.internal.R.drawable.stat_notify_disabled_data)
                .setColor(r.getColor(com.android.internal.R.color.system_notification_accent_color))
                .setContentTitle(r.getString(
                        com.android.internal.R.string.uicc_hot_swapped_event_title))
                .setContentText(r.getString(
                        com.android.internal.R.string.uicc_hot_swapped_event_text))
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(PendingIntent.getActivityAsUser(mContext, 0,
                        MOBILE_NETWORK_SETTINGS_MSIM, PendingIntent.FLAG_CANCEL_CURRENT, null,
                        new UserHandle(0)))
                        .build();

        // Since this is coming from android's phone process, manually enqueue this notification
        try {
            int[] outId = new int[1];
            inm.enqueueNotificationWithTag("android", "android", null,
                    com.android.internal.R.string.uicc_hot_swapped_event_title,
                    notification, outId, ActivityManager.getCurrentUser());
        } catch (RuntimeException | RemoteException e) {
            log(e.toString());
        }
    }

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){
            switch (msg.what) {
                case EVENT_CARD_REMOVED:
                    onIccSwap(false);
                    break;
                case EVENT_CARD_ADDED:
                    onIccSwap(true);
                    break;
                case EVENT_OPEN_LOGICAL_CHANNEL_DONE:
                case EVENT_CLOSE_LOGICAL_CHANNEL_DONE:
                case EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE:
                case EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE:
                case EVENT_SIM_IO_DONE:
                case EVENT_SIM_GET_ATR_DONE:
                // MTK-START
                case EVENT_GET_ATR_DONE:
                case EVENT_OPEN_CHANNEL_WITH_SW_DONE:
                // MTK-END
                    AsyncResult ar = (AsyncResult)msg.obj;
                    if (ar.exception != null) {
                        loglocal("Exception: " + ar.exception);
                        log("Error in SIM access with exception" + ar.exception);
                    }
                    AsyncResult.forMessage((Message)ar.userObj, ar.result, ar.exception);
                    ((Message)ar.userObj).sendToTarget();
                    break;
                case EVENT_CARRIER_PRIVILIGES_LOADED:
                    onCarrierPriviligesLoadedMessage();
                    break;
                // MTK-START
                case EVENT_CDMA_CARD_IMSI_DONE:
                    log("Handler EVENT_CDMA_CARD_IMSI_DONE mIsSvlte=" + mIsSvlte);
                    if (mIsSvlte && mUiccApplications != null) {
                        if (mGsmUmtsSubscriptionAppIndex >= 0
                            && mUiccApplications[mGsmUmtsSubscriptionAppIndex] != null
                            && mCdmaSubscriptionAppIndex >= 0
                            && mUiccApplications[mCdmaSubscriptionAppIndex] != null) {
                            mCsimRecords =
                                mUiccApplications[mCdmaSubscriptionAppIndex].getIccRecords();
                            mUsimRecords =
                                mUiccApplications[mGsmUmtsSubscriptionAppIndex].getIccRecords();
                            if ((mUsimRecords != null) || (mCsimRecords != null)) {
                                if ((mUsimRecords.getIMSI() != null)
                                    && (mUsimRecords.getIMSI() != mCdmaUsimImsi)
                                    && (mCsimRecords.getIMSI() != null)
                                    && (mCsimRecords.getIMSI() != mCdmaCsimImsi)) {
                                    mCdmaUsimImsi = mUsimRecords.getIMSI();
                                    mCdmaCsimImsi = mCsimRecords.getIMSI();
                                    broadcastCdmaCardImsiIntent();
                                }
                            }
                        }
                    }
                    break;
                case EVENT_CDMA_CARD_TYPE:
                    if (DBG) {
                        log("handleMessgage (EVENT_CDMA_CARD_TYPE)");
                    }
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        int[] resultType = (int[]) ar.result;
                        if (resultType != null) {
                            loge("SvlteCardType: TODO!");
                            /*
                            mSvlteCardType = SvlteCardType.getCardTypeFromInt(resultType[0]);
                            if (mSvlteCardType.isValidCardType()) {
                                broadcastSvlteCardTypeChanged(mPhoneId, mSvlteCardType.getValue());
                            } else {
                                log("invalid cardType=" + resultType[0]);
                            }
                            */
                        }
                    }
                    break;
                case EVENT_C2K_WP_CARD_TYPE_READY:
                    if (DBG) {
                        log("handleMessgage (EVENT_C2K_WP_CARD_TYPE_READY)");
                    }
                    loge("SvlteCardType: TODO!");
                    /*
                    mSvlteCardType = SvlteCardType.transformCardTypeFromString(getIccCardType());
                    if (mSvlteCardType.isValidCardType()) {
                        broadcastSvlteCardTypeChanged(mPhoneId, mSvlteCardType.getValue());
                    }
                    */
                    break;
                 // MTK-END
                default:
                    loge("Unknown Event " + msg.what);
            }
        }
    };

    private void onCarrierPriviligesLoadedMessage() {
        synchronized (mLock) {
            mCarrierPrivilegeRegistrants.notifyRegistrants();
        }
    }

    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            for (int i = 0 ; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null && mUiccApplications[i].getType() == type) {
                    return true;
                }
            }
            return false;
        }
    }

    public CardState getCardState() {
        synchronized (mLock) {
            return mCardState;
        }
    }

    public PinState getUniversalPinState() {
        synchronized (mLock) {
            return mUniversalPinState;
        }
    }

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
     * @param type ICC application type (@see com.android.internal.telephony.PhoneConstants#APPTYPE_xxx)
     * @return application corresponding to type or a null if no match found
     */
    public UiccCardApplication getApplicationByType(int type) {
        synchronized (mLock) {
            for (int i = 0 ; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null &&
                        mUiccApplications[i].getType().ordinal() == type) {
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
                if (mUiccApplications[i] != null &&
                    (aid == null || aid.equals(mUiccApplications[i].getAid()))) {
                    // Delete removed applications
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                    changed = true;
                }
            }
            return changed;
        }
        // TODO: For a card level notification, we should delete the CarrierPrivilegeRules and the
        // CAT service.
    }

    /**
     * Exposes {@link CommandsInterface.iccOpenLogicalChannel}
     */
    public void iccOpenLogicalChannel(String AID, Message response) {
        loglocal("Open Logical Channel: " + AID + " by pid:" + Binder.getCallingPid()
                + " uid:" + Binder.getCallingUid());
        mCi.iccOpenLogicalChannel(AID,
                mHandler.obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, response));
    }

    public void iccOpenLogicalChannel(String AID, byte p2, Message response) {
        loglocal("Open Logical Channel: " + AID + " , " + p2 + " by pid:" + Binder.getCallingPid()
                + " uid:" + Binder.getCallingUid());
        mCi.iccOpenLogicalChannel(AID,
                p2, mHandler.obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface.iccCloseLogicalChannel}
     */
    public void iccCloseLogicalChannel(int channel, Message response) {
        loglocal("Close Logical Channel: " + channel);
        mCi.iccCloseLogicalChannel(channel,
                mHandler.obtainMessage(EVENT_CLOSE_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface.iccTransmitApduLogicalChannel}
     */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduLogicalChannel(channel, cla, command, p1, p2, p3,
                data, mHandler.obtainMessage(EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface.iccTransmitApduBasicChannel}
     */
    public void iccTransmitApduBasicChannel(int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduBasicChannel(cla, command, p1, p2, p3,
                data, mHandler.obtainMessage(EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface.iccIO}
     */
    public void iccExchangeSimIO(int fileID, int command, int p1, int p2, int p3,
            String pathID, Message response) {
        mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null,
                mHandler.obtainMessage(EVENT_SIM_IO_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface.getAtr}
     */
    public void getAtr(Message response) {
        mCi.getAtr(mHandler.obtainMessage(EVENT_SIM_GET_ATR_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface.sendEnvelopeWithStatus}
     */
    public void sendEnvelopeWithStatus(String contents, Message response) {
        mCi.sendEnvelopeWithStatus(contents, response);
    }

    /* Returns number of applications on this card */
    public int getNumApplications() {
        int count = 0;
        for (UiccCardApplication a : mUiccApplications) {
            if (a != null) {
                count++;
            }
        }
        return count;
    }

    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Returns true iff carrier priveleges rules are null (dont need to be loaded) or loaded.
     */
    public boolean areCarrierPriviligeRulesLoaded() {
        return mCarrierPrivilegeRules == null
            || mCarrierPrivilegeRules.areCarrierPriviligeRulesLoaded();
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules.getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        return mCarrierPrivilegeRules == null ?
            TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
            mCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature, packageName);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules.getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        return mCarrierPrivilegeRules == null ?
            TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
            mCarrierPrivilegeRules.getCarrierPrivilegeStatus(packageManager, packageName);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction}.
     */
    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        return mCarrierPrivilegeRules == null ?
            TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
            mCarrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction(packageManager);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules.getCarrierPackageNamesForIntent}.
     */
    public List<String> getCarrierPackageNamesForIntent(
            PackageManager packageManager, Intent intent) {
        return mCarrierPrivilegeRules == null ? null :
            mCarrierPrivilegeRules.getCarrierPackageNamesForIntent(
                    packageManager, intent);
    }

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

    public String getOperatorBrandOverride() {
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return null;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        return sp.getString(OPERATOR_BRAND_OVERRIDE_PREFIX + iccId, null);
    }

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

    public UICCConfig getUICCConfig() {
        return mUICCConfig;
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg /* MTK */  + " (phoneId " + mPhoneId + ")");
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg /* MTK */  + " (phoneId " + mPhoneId + ")");
    }

    private void loglocal(String msg) {
        if (DBG) mLocalLog.log(msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccCard:");
        pw.println(" mCi=" + mCi);
        pw.println(" mLastRadioState=" + mLastRadioState);
        pw.println(" mCatService=" + mCatService);
        pw.println(" mAbsentRegistrants: size=" + mAbsentRegistrants.size());
        for (int i = 0; i < mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]="
                    + ((Registrant)mAbsentRegistrants.get(i)).getHandler());
        }
        for (int i = 0; i < mCarrierPrivilegeRegistrants.size(); i++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i + "]="
                    + ((Registrant)mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.println(" mCardState=" + mCardState);
        pw.println(" mUniversalPinState=" + mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + mImsSubscriptionAppIndex);
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
                    + ((Registrant)mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.flush();
        pw.println("mLocalLog:");
        mLocalLog.dump(fd, pw, args);
        pw.flush();
    }

    // MTK

    private static final int EVENT_GET_ATR_DONE = 100;
    private static final int EVENT_OPEN_CHANNEL_WITH_SW_DONE = 101;
    private static final int EVENT_CDMA_CARD_IMSI_DONE = 102;
    private static final int EVENT_CDMA_CARD_TYPE = 103;
    private static final int EVENT_C2K_WP_CARD_TYPE_READY = 104;

    static final String[] UICCCARD_PROPERTY_RIL_UICC_TYPE = {
        "gsm.ril.uicctype",
        "gsm.ril.uicctype.2",
        "gsm.ril.uicctype.3",
        "gsm.ril.uicctype.4",
    };

    private String mIccType = null; /* Add for USIM detect */
    private CommandsInterface mSvlteCi; /* Add for C2K SVLTE */
    private boolean mIsSvlte = false;
    private static final String[]  PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };

    private IccRecords mUsimRecords = null;
    private IccRecords mCsimRecords = null;
    private String mCdmaCsimImsi = null;
    private String mCdmaUsimImsi = null;
    private boolean mCsimRigisterDone = false;
    private boolean mUsimRigisterDone = false;

    // private SvlteCardType mSvlteCardType = SvlteCardType.UNKNOW_CARD;

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId, boolean isUpdateSiminfo) {
        if (DBG) log("Creating simId " + phoneId + ",isUpdateSiminfo" + isUpdateSiminfo);
        mCardState = ics.mCardState;
        mPhoneId = phoneId;
        update(c, ci, ics, isUpdateSiminfo);
    }

    public void exchangeSimIo(int fileID, int command,
                                           int p1, int p2, int p3, String pathID, String data, String pin2, Message onComplete) {
        mCi.iccIO(command, fileID, pathID, p1, p2, p3, data, pin2,
              mHandler.obtainMessage(EVENT_SIM_IO_DONE, onComplete));
    }

    public void iccGetAtr(Message onComplete) {
        mCi.iccGetATR(mHandler.obtainMessage(EVENT_GET_ATR_DONE, onComplete));
    }

    public String getIccCardType() {
        //int slot = -1;
        //if (SubscriptionController.getInstance() != null) {
        //    slot = SubscriptionController.getInstance().getSlotId(
        //            SubscriptionController.getInstance().getSubIdUsingPhoneId(
        //            mPhoneId));
        //    mIccType = SystemProperties.get(UICCCARD_PROPERTY_RIL_UICC_TYPE[slot]);
        //}
        mIccType = SystemProperties.get(UICCCARD_PROPERTY_RIL_UICC_TYPE[mPhoneId]);
        if (DBG) log("getIccCardType(): iccType = " + mIccType + ", slot " + mPhoneId);
        return mIccType;
    }

    public String[] getFullIccCardType() {
        return SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[mPhoneId]).split(",");
    }

    // MTK-START
    /**
     * Request to get SVLTE UICC card type.
     *
     * @return index for UICC card type
     *
     */
    public int getSvlteCardType() {
        // MTK TODO
        /*
        if (DBG) {
            log("getSvlteCardType(): mSvlteCardType = " + mSvlteCardType.getValue()
                    + ", slot " + mPhoneId);
        }
        return mSvlteCardType.getValue();
        */
        loge("getSvlteCardType: TODO!");
        return 0;
    }

    private void broadcastSvlteCardTypeChanged(int slotId, int cardType) {
        Intent i = new Intent(TelephonyIntents.ACTION_SVLTE_CARD_TYPE);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        i.putExtra(PhoneConstants.SLOT_KEY, slotId);
        i.putExtra(TelephonyIntents.INTENT_KEY_SVLTE_CARD_TYPE, cardType);
        log("Broadcasting intent ACTION_SVLTE_CARD_TYPE, slotId " +
                slotId + ", cardType " + cardType);
        ActivityManagerNative.broadcastStickyIntent(i, READ_PHONE_STATE,
                UserHandle.USER_ALL);
    }
    // MTK-END

    public void iccOpenChannelWithSw(String AID, Message onComplete) {
        mCi.iccOpenChannelWithSw(AID,
            mHandler.obtainMessage(EVENT_OPEN_CHANNEL_WITH_SW_DONE, onComplete));
    }

    //For C2K SVLTE
    /**
     * UiccCard for SVLTE.
     * @param c  Context
     * @param ci CommandsInterface
     * @param ics IccCardStatus
     * @param slotId Card slot id
     * @param svlteCi CommandsInterface
     */
    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId,
        CommandsInterface svlteCi) {
        if (DBG) {
            log("Creating phoneId " + phoneId + ",svlteCi" + svlteCi);
        }
        // MTK-START
        // MTK TODO
        /*
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mIsSvlte = true;
            mSvlteCi = svlteCi;
            UiccController.getInstance().registerForC2KWPCardTypeReady(mHandler,
                EVENT_C2K_WP_CARD_TYPE_READY, null);
            ci.registerForCdmaCardType(mHandler, EVENT_CDMA_CARD_TYPE, null);
        }
        */
        // MTK-END
        mCardState = ics.mCardState;
        mPhoneId = phoneId;
        update(c, ci, ics);
    }
    /**
     * Set LTE flag.
     * @param isSvlte svlte flag
    */
    /*
    public void setSvlteFlag(boolean isSvlte) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mIsSvlte = isSvlte;
        } else {
            log("setSvlteFlag Error");
        }
        log("setSvlteFlag mIsSvlte:" + mIsSvlte);
    }
    */
    //Update LTE UiccApplication
    /*public void update(Context c, CommandsInterface ci, IccCardStatus ics,
        CommandsInterface svlteCi) {
        log("update svlteCi");
        mSvlteCi = svlteCi;
        update(c, ci, ics);
    }*/
    /*
    private void configModemRemoteSimAccess() {
        String cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[0]);
        Rlog.d(LOG_TAG, "configModemRemoteSimAccess cardType=" + cardType);
        String appType[] = cardType.split(",");
        int type = 0;
        for (int i = 0; i < appType.length; i++) {
            if ("USIM".equals(appType[i]) || "SIM".equals(appType[i])) {
                Rlog.d(LOG_TAG, "UiccCard cardType: contain USIM/SIM");
                type |= 0x01;
                continue;
            } else if ("CSIM".equals(appType[i]) || "RUIM".equals(appType[i])) {
                Rlog.d(LOG_TAG, "UiccCard cardType: contain CSIM/RUIM");
                type |= 0x02;
                continue;
            }
        }
        switch (type) {
            case 0:
                // no card
                mCi.configModemStatus(2, 1, null);
                if (mSvlteCi != null) {
                    mSvlteCi.configModemStatus(2, 1, null);
                }
                break;
            case 1:
                // GSM only card
                mCi.configModemStatus(2, 1, null);
                if (mSvlteCi != null) {
                    mSvlteCi.configModemStatus(2, 1, null);
                }
                break;
            case 2:
                // UIM only card
                mCi.configModemStatus(1, 1, null);
                if (mSvlteCi != null) {
                    mSvlteCi.configModemStatus(1, 1, null);
                }
                break;
            case 3:
                // LTE card
                mCi.configModemStatus(2, 1, null);
                if (mSvlteCi != null) {
                    mSvlteCi.configModemStatus(2, 1, null);
                }
                break;
            default:
                break;
            }
    }
    */

    /**
    * This funtion is to register for card imsi done.
    * @param index int uicc card index
    */
    public void registerCdmaCardImsiDone(int index) {
        log("registerCdmaCardImsiDone: index =" + index
            + " mCdmaSubscriptionAppIndex=" + mCdmaSubscriptionAppIndex
            + " mGsmUmtsSubscriptionAppIndex=" + mGsmUmtsSubscriptionAppIndex);
        if ((!mCsimRigisterDone) && (index != UiccController.INDEX_SVLTE)) {
            if (mCdmaSubscriptionAppIndex >= 0
                && mUiccApplications[mCdmaSubscriptionAppIndex] != null) {
                mCsimRecords = mUiccApplications[mCdmaSubscriptionAppIndex].getIccRecords();
                if (mCsimRecords != null) {
                    mCsimRecords.registerForImsiReady(mHandler, EVENT_CDMA_CARD_IMSI_DONE, null);
                    mCsimRigisterDone = true;
                    log("registerCdmaCardImsiDone: index != UiccController.INDEX_SVLTE");
                }
            }
        } else if ((!mUsimRigisterDone) && (index == UiccController.INDEX_SVLTE)) {
            if (mGsmUmtsSubscriptionAppIndex >= 0
                && mUiccApplications[mGsmUmtsSubscriptionAppIndex] != null) {
                mUsimRecords = mUiccApplications[mGsmUmtsSubscriptionAppIndex].getIccRecords();
                if (mUsimRecords != null) {
                    mUsimRecords.registerForImsiReady(mHandler, EVENT_CDMA_CARD_IMSI_DONE, null);
                    mUsimRigisterDone = true;
                    log("registerCdmaCardImsiDone: index == UiccController.INDEX_SVLTE");
                }
            }
        }
    }

    private void broadcastCdmaCardImsiIntent() {
        // MTK TODO
        /*
        Intent intent = new Intent(TelephonyIntents.ACTION_CDMA_CARD_IMSI);
        intent.putExtra(TelephonyIntents.INTENT_KEY_CDMA_CARD_CSIM_IMSI, mCdmaCsimImsi);
        intent.putExtra(TelephonyIntents.INTENT_KEY_CDMA_CARD_USIM_IMSI, mCdmaUsimImsi);
        intent.putExtra(TelephonyIntents.INTENT_KEY_SVLTE_MODE_SLOT_ID,
            SvlteModeController.getActiveSvlteModeSlotId());
        log("Broadcasting intent broadcastCdmaCardImsiIntent mCdmaCsimImsi=" + mCdmaCsimImsi
            + " mCdmaUsimImsi=" + mCdmaUsimImsi + " getActiveSvlteModeSlotId() = " +
            SvlteModeController.getActiveSvlteModeSlotId());
        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE, UserHandle.USER_ALL);
        */
    }
    // MTK-END
}
