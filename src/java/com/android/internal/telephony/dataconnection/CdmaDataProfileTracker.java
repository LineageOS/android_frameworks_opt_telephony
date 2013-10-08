/*
 * Copyright (c) 2010-2012, The Linux Foundation. All rights reserved.
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

import android.os.Registrant;
import android.os.RegistrantList;
import android.os.Handler;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.dataconnection.DataProfileOmh.DataProfileTypeModem;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;

/**
 * This is used only for OMH data profiles
 * {@hide}
 */
public final class CdmaDataProfileTracker extends Handler {
    protected final String LOG_TAG = "CDMA";

    /**
     * Property that shows the status of the OMH feature
    */
    public static final String PROPERTY_OMH_ENABLED = "persist.omh.enabled";

    private CDMAPhone mPhone;
    private CdmaSubscriptionSourceManager mCdmaSsm;

    /**
     * mDataProfilesList holds all the Data profiles for cdma
     */
    private ArrayList<DataProfile> mDataProfilesList = new ArrayList<DataProfile>();

    private static final String[] SUPPORTED_APN_TYPES = {
            PhoneConstants.APN_TYPE_DEFAULT,
            PhoneConstants.APN_TYPE_MMS,
            PhoneConstants.APN_TYPE_SUPL,
            PhoneConstants.APN_TYPE_DUN,
            PhoneConstants.APN_TYPE_HIPRI,
            PhoneConstants.APN_TYPE_FOTA,
            PhoneConstants.APN_TYPE_IMS,
            PhoneConstants.APN_TYPE_CBS };

    // if we have no active DataProfile this is null
    protected DataProfile mActiveDp;

    /*
     * Context for read profiles for OMH.
     */
    private int mOmhReadProfileContext = 0;

    /*
     * Count to track if all read profiles for OMH are completed or not.
     */
    private int mOmhReadProfileCount = 0;

    private boolean mIsOmhEnabled =
            SystemProperties.getBoolean(PROPERTY_OMH_ENABLED, false);

    // Enumerated list of DataProfile from the modem.
    ArrayList<DataProfile> mOmhDataProfilesList = new ArrayList<DataProfile>();

    // Temp. DataProfile list from the modem.
    ArrayList<DataProfile> mTempOmhDataProfilesList = new ArrayList<DataProfile>();

    // Map of the service type to its priority
    HashMap<String, Integer> mOmhServicePriorityMap;

    /* Registrant list for objects interested in modem profile related events */
    private RegistrantList mModemDataProfileRegistrants = new RegistrantList();

    private static final int EVENT_READ_MODEM_PROFILES = 0;
    private static final int EVENT_GET_DATA_CALL_PROFILE_DONE = 1;
    private static final int EVENT_LOAD_PROFILES = 2;

    /* Constructor */

    CdmaDataProfileTracker(CDMAPhone phone) {
        mPhone = phone;
        mCdmaSsm = CdmaSubscriptionSourceManager.getInstance (phone.getContext(), phone.mCi, this,
                EVENT_LOAD_PROFILES, null);

        mOmhServicePriorityMap = new HashMap<String, Integer>();

        sendMessage(obtainMessage(EVENT_LOAD_PROFILES));

        log("SUPPORT_OMH: " + mIsOmhEnabled);
    }

    /**
     * Load the CDMA profiles
     */
    void loadProfiles() {
        log("loadProfiles...");
        mDataProfilesList.clear();

        readDataProfilesFromModem();
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

    public void dispose() {
    }

    protected void finalize() {
        Log.d(LOG_TAG, "CdmaDataProfileTracker finalized");
    }

    public void registerForModemProfileReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mModemDataProfileRegistrants.add(r);
    }

    public void unregisterForModemProfileReady(Handler h) {
        mModemDataProfileRegistrants.remove(h);
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
                onReadDataProfilesFromModem();
                break;

            case EVENT_GET_DATA_CALL_PROFILE_DONE:
                onGetDataCallProfileDone((AsyncResult) msg.obj, (int)msg.arg1);
                break;

            default:
                // handle the message in the super class DataConnectionTracker
                super.handleMessage(msg);
                break;
        }
    }

    /*
     * Trigger modem read for data profiles
     */
    private void readDataProfilesFromModem() {
        if (mIsOmhEnabled) {
            sendMessage(obtainMessage(EVENT_READ_MODEM_PROFILES));
        } else {
            log("OMH is disabled, ignoring request!");
        }
    }

    /*
     * Reads all the data profiles from the modem
     */
    private void onReadDataProfilesFromModem() {
        log("OMH: onReadDataProfilesFromModem()");
        mOmhReadProfileContext++;

        mOmhReadProfileCount = 0; // Reset the count and list(s)
        /* Clear out the modem profiles lists (main and temp) which were read/saved */
        mOmhDataProfilesList.clear();
        mTempOmhDataProfilesList.clear();
        mOmhServicePriorityMap.clear();

        // For all the service types known in modem, read the data profies
        for (DataProfileTypeModem p : DataProfileTypeModem.values()) {
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

        // DataProfile list from the modem for a given SERVICE_TYPE. These may
        // be from RUIM in case of OMH
        ArrayList<DataProfile> dataProfileListModem = new ArrayList<DataProfile>();
        dataProfileListModem = (ArrayList<DataProfile>)ar.result;

        DataProfileTypeModem modemProfile = (DataProfileTypeModem)ar.userObj;

        mOmhReadProfileCount--;

        if (dataProfileListModem != null && dataProfileListModem.size() > 0) {
            String serviceType;

            /* For the modem service type, get the android DataServiceType */
            serviceType = modemProfile.getDataServiceType();

            log("OMH: # profiles returned from modem:" + dataProfileListModem.size()
                    + " for " + serviceType);

            mOmhServicePriorityMap.put(serviceType,
                    omhListGetArbitratedPriority(dataProfileListModem, serviceType));

            for (DataProfile dp : dataProfileListModem) {

                /* Store the modem profile type in the data profile */
                ((DataProfileOmh)dp).setDataProfileTypeModem(modemProfile);

                /* Look through mTempOmhDataProfilesList for existing profile id's
                 * before adding it. This implies that the (similar) profile with same
                 * priority already exists.
                 */
                DataProfileOmh omhDuplicatedp = getDuplicateProfile(dp);
                if (null == omhDuplicatedp) {
                    mTempOmhDataProfilesList.add(dp);
                    ((DataProfileOmh)dp).addServiceType(DataProfileTypeModem.
                            getDataProfileTypeModem(serviceType));
                } else {
                    /*  To share the already established data connection
                     * (say between SUPL and DUN) in cases such as below:
                     *  Ex:- SUPL+DUN [profile id 201, priority 1]
                     *  'dp' instance is found at this point. Add the non-provisioned
                     *   service type to this 'dp' instance
                     */
                    log("OMH: Duplicate Profile " + omhDuplicatedp);
                    ((DataProfileOmh)omhDuplicatedp).addServiceType(DataProfileTypeModem.
                            getDataProfileTypeModem(serviceType));
                }
            }
        }

        //(Re)Load APN List
        if(mOmhReadProfileCount == 0) {
            log("OMH: Modem omh profile read complete.");
            addServiceTypeToUnSpecified();
            mDataProfilesList.addAll(mTempOmhDataProfilesList);
            mModemDataProfileRegistrants.notifyRegistrants();
        }

        return;
    }

    /*
     * returns the object 'OMH dataProfile' if a match with the same profile id
     * exists in the enumerated list of OMH profile list
     */
    private DataProfileOmh getDuplicateProfile(DataProfile dp) {
        for (DataProfile dataProfile : mTempOmhDataProfilesList) {
            if (((DataProfileOmh)dp).getProfileId() ==
                ((DataProfileOmh)dataProfile).getProfileId()){
                return (DataProfileOmh)dataProfile;
            }
        }
        return null;
    }

    public DataProfile getDataProfile(String serviceType) {
        log("getDataProfile: serviceType="+serviceType);
        DataProfile profile = null;

        // Go through all the profiles to find one
        for (DataProfile dp: mDataProfilesList) {
            if (dp.canHandleType(serviceType)) {
                if (mIsOmhEnabled &&
                    dp.getDataProfileType() != DataProfile.DataProfileType.PROFILE_TYPE_OMH) {
                    // OMH enabled - Keep looking for OMH profile
                    continue;
                }
                profile = dp;
                break;
            }
        }

        if (profile == null) {
            log("getDataProfile: OMH profile not found for "+serviceType);

            for (DataProfile dp: mDataProfilesList) {
                if (dp.canHandleType(serviceType)) {
                    profile = dp;
                    break;
                }
            }

            log("getDataProfile: using hardcoded profile "+profile);

        }

        log("getDataProfile: return profile="+profile);
        return profile;
    }

    /* For all the OMH service types not present in the card, add them to the
     * UNSPECIFIED/DEFAULT data profile.
     */
    private void addServiceTypeToUnSpecified() {
        for (String apntype : SUPPORTED_APN_TYPES) {
            if(!mOmhServicePriorityMap.containsKey(apntype)) {

                // ServiceType :apntype is not provisioned in the card,
                // Look through the profiles read from the card to locate
                // the UNSPECIFIED profile and add the service type to it.
                for (DataProfile dp : mTempOmhDataProfilesList) {
                    if (((DataProfileOmh)dp).getDataProfileTypeModem() ==
                                DataProfileTypeModem.PROFILE_TYPE_UNSPECIFIED) {
                        ((DataProfileOmh)dp).addServiceType(DataProfileTypeModem.
                                getDataProfileTypeModem(apntype));
                        log("OMH: Service Type added to UNSPECIFIED is : " +
                                DataProfileTypeModem.getDataProfileTypeModem(apntype));
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
            ArrayList<DataProfile> dataProfileListModem,
            String serviceType) {
        DataProfile profile = null;

        for (DataProfile dp : dataProfileListModem) {
            if (!((DataProfileOmh) dp).isValidPriority()) {
                log("[OMH] Invalid priority... skipping");
                continue;
            }

            if (profile == null) {
                profile = dp; // first hit
            } else {
                if (serviceType == PhoneConstants.APN_TYPE_SUPL) {
                    // Choose the profile with lower priority
                    profile = ((DataProfileOmh) dp).isPriorityLower(((DataProfileOmh) profile)
                            .getPriority()) ? dp : profile;
                } else {
                    // Choose the profile with higher priority
                    profile = ((DataProfileOmh) dp).isPriorityHigher(((DataProfileOmh) profile)
                            .getPriority()) ? dp : profile;
                }
            }
        }
        return ((DataProfileOmh) profile).getPriority();
    }

    public void clearActiveDataProfile() {
        mActiveDp = null;
    }

    public boolean isApnTypeActive(String type) {
        return mActiveDp != null && mActiveDp.canHandleType(type);
    }

    public boolean isOmhEnabled() {
        return mIsOmhEnabled;
    }

    protected boolean isApnTypeAvailable(String type) {
        for (String s : SUPPORTED_APN_TYPES) {
            if (TextUtils.equals(type, s)) {
                return true;
            }
        }
        return false;
    }

    protected String[] getActiveApnTypes() {
        String[] result;
        if (mActiveDp != null) {
            result = mActiveDp.getServiceTypes();
        } else {
            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_DEFAULT;
        }
        return result;
    }

    protected void log(String s) {
        Log.d(LOG_TAG, "[CdmaDataProfileTracker] " + s);
    }

    protected void loge(String s) {
        Log.e(LOG_TAG, "[CdmaDataProfileTracker] " + s);
    }
}
