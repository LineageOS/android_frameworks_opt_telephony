/*
 * Copyright (c) 2010-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony.dataconnection;

import android.database.Cursor;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.dataconnection.ApnProfileOmh.ApnProfileTypeModem;
import com.android.internal.telephony.dataconnection.ApnSetting;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;

/**
 * {@hide}
 */
public final class CdmaApnProfileTracker extends Handler {
    protected final String LOG_TAG = "CDMA";

    private CDMAPhone mPhone;
    private CdmaSubscriptionSourceManager mCdmaSsm;

    /**
     * mApnProfilesList holds all the Apn profiles for cdma
     */
    private ArrayList<ApnSetting> mApnProfilesList = new ArrayList<ApnSetting>();

    private static final String[] mSupportedApnTypes = {
            PhoneConstants.APN_TYPE_DEFAULT,
            PhoneConstants.APN_TYPE_MMS,
            PhoneConstants.APN_TYPE_SUPL,
            PhoneConstants.APN_TYPE_DUN,
            PhoneConstants.APN_TYPE_HIPRI,
            PhoneConstants.APN_TYPE_FOTA,
            PhoneConstants.APN_TYPE_IMS,
            PhoneConstants.APN_TYPE_CBS };

    private static final String[] mDefaultApnTypes = {
            PhoneConstants.APN_TYPE_DEFAULT,
            PhoneConstants.APN_TYPE_MMS,
            PhoneConstants.APN_TYPE_SUPL,
            PhoneConstants.APN_TYPE_HIPRI,
            PhoneConstants.APN_TYPE_FOTA,
            PhoneConstants.APN_TYPE_IMS,
            PhoneConstants.APN_TYPE_CBS };

    // if we have no active ApnProfile this is null
    protected ApnSetting mActiveApn;

    /*
     * Context for read profiles for OMH.
     */
    private int mOmhReadProfileContext = 0;

    /*
     * Count to track if all read profiles for OMH are completed or not.
     */
    private int mOmhReadProfileCount = 0;

    // Temp. ApnProfile list from the modem.
    ArrayList<ApnSetting> mTempOmhApnProfilesList = new ArrayList<ApnSetting>();

    // Map of the service type to its priority
    HashMap<String, Integer> mOmhServicePriorityMap;

    /* Registrant list for objects interested in modem profile related events */
    private RegistrantList mModemApnProfileRegistrants = new RegistrantList();

    private static final int EVENT_READ_MODEM_PROFILES = 0;
    private static final int EVENT_GET_DATA_CALL_PROFILE_DONE = 1;
    private static final int EVENT_LOAD_PROFILES = 2;

    /* Constructor */

    CdmaApnProfileTracker(CDMAPhone phone) {
        mPhone = phone;
        mCdmaSsm = CdmaSubscriptionSourceManager.getInstance (phone.getContext(), phone.mCi, this,
                EVENT_LOAD_PROFILES, null);

        mOmhServicePriorityMap = new HashMap<String, Integer>();

        sendMessage(obtainMessage(EVENT_LOAD_PROFILES));
    }

    /**
     * Load the CDMA profiles
     */
    void loadProfiles() {
        log("loadProfiles...");
        mApnProfilesList.clear();

        readApnProfilesFromModem();
    }


    /**
     * @param types comma delimited list of data service types
     * @return array of data service types
     */
    private String[] parseTypes(String types) {
        String[] result;
        // If unset, set to DEFAULT.
        if (types == null || types.equals("")) {
            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_ALL;
        } else {
            result = types.split(",");
        }
        return result;
    }

    protected void finalize() {
        Log.d(LOG_TAG, "CdmaApnProfileTracker finalized");
    }

    public void registerForModemProfileReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mModemApnProfileRegistrants.add(r);
    }

    public void unregisterForModemProfileReady(Handler h) {
        mModemApnProfileRegistrants.remove(h);
    }

    public void handleMessage (Message msg) {

        if (!mPhone.mIsTheCurrentActivePhone) {
            Log.d(LOG_TAG, "Ignore CDMA msgs since CDMA phone is inactive");
            return;
        }

        switch (msg.what) {
            case EVENT_LOAD_PROFILES:
                loadProfiles();
                break;
            case EVENT_READ_MODEM_PROFILES:
                onReadApnProfilesFromModem();
                break;

            case EVENT_GET_DATA_CALL_PROFILE_DONE:
                onGetDataCallProfileDone((AsyncResult) msg.obj, (int)msg.arg1);
                break;

            default:
                break;
        }
    }

    /*
     * Trigger modem read for data profiles
     */
    private void readApnProfilesFromModem() {
        sendMessage(obtainMessage(EVENT_READ_MODEM_PROFILES));
    }

    /*
     * Reads all the data profiles from the modem
     */
    private void onReadApnProfilesFromModem() {
        log("OMH: onReadApnProfilesFromModem()");
        mOmhReadProfileContext++;

        mOmhReadProfileCount = 0; // Reset the count and list(s)
        /* Clear out the modem profiles lists (main and temp) which were read/saved */
        mTempOmhApnProfilesList.clear();
        mOmhServicePriorityMap.clear();

        // For all the service types known in modem, read the data profies
        for (ApnProfileTypeModem p : ApnProfileTypeModem.values()) {
            log("OMH: Reading profiles for:" + p.getid());
            mOmhReadProfileCount++;
            mPhone.mCi.getDataCallProfile(p.getid(),
                            obtainMessage(EVENT_GET_DATA_CALL_PROFILE_DONE, //what
                            mOmhReadProfileContext, //arg1
                            0 , //arg2  -- ignore
                            p));//userObj
        }

    }

    /*
     * Process the response for the RIL request GET_DATA_CALL_PROFILE.
     * Save the profile details received.
     */
    private void onGetDataCallProfileDone(AsyncResult ar, int context) {
        if (context != mOmhReadProfileContext) {
            //we have other onReadOmhDataprofiles() on the way.
            return;
        }

        if (ar.exception != null) {
            log("OMH: Exception in onGetDataCallProfileDone:" + ar.exception);
            mOmhReadProfileCount--;
            return;
        }

        // ApnProfile list from the modem for a given SERVICE_TYPE. These may
        // be from RUIM in case of OMH
        ArrayList<ApnSetting> dataProfileListModem = (ArrayList<ApnSetting>)ar.result;

        ApnProfileTypeModem modemProfile = (ApnProfileTypeModem)ar.userObj;

        mOmhReadProfileCount--;

        if (dataProfileListModem != null && dataProfileListModem.size() > 0) {
            String serviceType;

            /* For the modem service type, get the android DataServiceType */
            serviceType = modemProfile.getDataServiceType();

            log("OMH: # profiles returned from modem:" + dataProfileListModem.size()
                    + " for " + serviceType);

            mOmhServicePriorityMap.put(serviceType,
                    omhListGetArbitratedPriority(dataProfileListModem, serviceType));

            for (ApnSetting apn : dataProfileListModem) {

                /* Store the modem profile type in the data profile */
                ((ApnProfileOmh)apn).setApnProfileTypeModem(modemProfile);

                /* Look through mTempOmhApnProfilesList for existing profile id's
                 * before adding it. This implies that the (similar) profile with same
                 * priority already exists.
                 */
                ApnProfileOmh omhDuplicateDp = getDuplicateProfile(apn);
                if (null == omhDuplicateDp) {
                    mTempOmhApnProfilesList.add(apn);
                    ((ApnProfileOmh)apn).addServiceType(ApnProfileTypeModem.
                            getApnProfileTypeModem(serviceType));
                } else {
                    /*  To share the already established data connection
                     * (say between SUPL and DUN) in cases such as below:
                     *  Ex:- SUPL+DUN [profile id 201, priority 1]
                     *  'apn' instance is found at this point. Add the non-provisioned
                     *   service type to this 'apn' instance
                     */
                    log("OMH: Duplicate Profile " + omhDuplicateDp);
                    omhDuplicateDp.addServiceType(ApnProfileTypeModem.
                            getApnProfileTypeModem(serviceType));
                }
            }
        }

        //(Re)Load APN List
        if (mOmhReadProfileCount == 0) {
            log("OMH: Modem omh profile read complete.");
            addServiceTypeToUnSpecified();
            mApnProfilesList.addAll(mTempOmhApnProfilesList);
            mModemApnProfileRegistrants.notifyRegistrants();
        }

        return;
    }

    /*
     * returns the object 'OMH dataProfile' if a match with the same profile id
     * exists in the enumerated list of OMH profile list
     */
    private ApnProfileOmh getDuplicateProfile(ApnSetting apn) {
        for (ApnSetting dataProfile : mTempOmhApnProfilesList) {
            if (((ApnProfileOmh)apn).getProfileId() ==
                ((ApnProfileOmh)dataProfile).getProfileId()){
                return (ApnProfileOmh)dataProfile;
            }
        }
        return null;
    }

    public ApnSetting getApnProfile(String serviceType) {
        log("getApnProfile: serviceType="+serviceType);
        ApnSetting profile = null;

        // Go through all the profiles to find one
        for (ApnSetting apn: mApnProfilesList) {
            if (apn.canHandleType(serviceType)) {
                profile = apn;
                break;
            }
        }

        log("getApnProfile: return profile=" + profile);
        return profile;
    }

    public ArrayList<ApnSetting> getOmhApnProfilesList() {
        log("getOmhApnProfilesList:" + mApnProfilesList);
        return mApnProfilesList;
    }

    /* For all the OMH service types not present in the card, add them to the
     * UNSPECIFIED/DEFAULT data profile.
     */
    private void addServiceTypeToUnSpecified() {
        for (String apntype : mSupportedApnTypes) {
            if(!mOmhServicePriorityMap.containsKey(apntype)) {

                // ServiceType :apntype is not provisioned in the card,
                // Look through the profiles read from the card to locate
                // the UNSPECIFIED profile and add the service type to it.
                for (ApnSetting apn : mTempOmhApnProfilesList) {
                    if (((ApnProfileOmh)apn).getApnProfileTypeModem() ==
                                ApnProfileTypeModem.PROFILE_TYPE_UNSPECIFIED) {
                        ((ApnProfileOmh)apn).addServiceType(ApnProfileTypeModem.
                                getApnProfileTypeModem(apntype));
                        log("OMH: Service Type added to UNSPECIFIED is : " +
                                ApnProfileTypeModem.getApnProfileTypeModem(apntype));
                        break;
                    }
                }
            }
        }
    }

    /*
     * Retrieves the highest priority for all APP types except SUPL. Note that
     * for SUPL, retrieve the least priority among its profiles.
     */
    private int omhListGetArbitratedPriority(
            ArrayList<ApnSetting> dataProfileListModem,
            String serviceType) {
        ApnSetting profile = null;

        for (ApnSetting apn : dataProfileListModem) {
            if (!((ApnProfileOmh) apn).isValidPriority()) {
                log("[OMH] Invalid priority... skipping");
                continue;
            }

            if (profile == null) {
                profile = apn; // first hit
            } else {
                if (serviceType == PhoneConstants.APN_TYPE_SUPL) {
                    // Choose the profile with lower priority
                    profile = ((ApnProfileOmh) apn).isPriorityLower(((ApnProfileOmh) profile)
                            .getPriority()) ? apn : profile;
                } else {
                    // Choose the profile with higher priority
                    profile = ((ApnProfileOmh) apn).isPriorityHigher(((ApnProfileOmh) profile)
                            .getPriority()) ? apn : profile;
                }
            }
        }
        return ((ApnProfileOmh) profile).getPriority();
    }

    public void clearActiveApnProfile() {
        mActiveApn = null;
    }

    public boolean isApnTypeActive(String type) {
        return mActiveApn != null && mActiveApn.canHandleType(type);
    }

    protected boolean isApnTypeAvailable(String type) {
        for (String s : mSupportedApnTypes) {
            if (TextUtils.equals(type, s)) {
                return true;
            }
        }
        return false;
    }

    protected void log(String s) {
        Log.d(LOG_TAG, "[CdmaApnProfileTracker] " + s);
    }

    protected void loge(String s) {
        Log.e(LOG_TAG, "[CdmaApnProfileTracker] " + s);
    }
}

