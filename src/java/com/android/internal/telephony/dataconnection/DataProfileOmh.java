/*
 * Copyright (c) 2010-2013, The Linux Foundation. All rights reserved.
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

import java.util.ArrayList;

import android.os.SystemProperties;
import android.text.TextUtils;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;

public class DataProfileOmh extends DataProfile {

    /**
     *  OMH spec 3GPP2 C.S0023-D defines the application types in terms of a
     *  32-bit mask where each bit represents one application
     *
     *  Application bit and the correspondign app type is listed below:
     *  1 Unspecified (all applications use the same profile)
     *  2 MMS
     *  3 Browser
     *  4 BREW
     *  5 Java
     *  6 LBS
     *  7 Terminal (tethered mode for terminal access)
     *  8-32 Reserved for future use
     *
     *  From this list all the implemented app types are listed in the enum
     */
    enum DataProfileTypeModem {
        /* Static mapping of OMH profiles to Android Service Types */
        PROFILE_TYPE_UNSPECIFIED(0x00000001, PhoneConstants.APN_TYPE_DEFAULT),
        PROFILE_TYPE_MMS(0x00000002, PhoneConstants.APN_TYPE_MMS),
        PROFILE_TYPE_LBS(0x00000020, PhoneConstants.APN_TYPE_SUPL),
        PROFILE_TYPE_TETHERED(0x00000040, PhoneConstants.APN_TYPE_DUN);

        int id;
        String serviceType;

        private DataProfileTypeModem(int i, String serviceType) {
            this.id = i;
            this.serviceType = serviceType;
        }

        public int getid() {
            return id;
        }

        public String getDataServiceType() {
            return serviceType;
        }

        public static DataProfileTypeModem getDataProfileTypeModem(String serviceType) {

            if (TextUtils.equals(serviceType, PhoneConstants.APN_TYPE_DEFAULT)) {
                return PROFILE_TYPE_UNSPECIFIED;
            } else if (TextUtils.equals(serviceType, PhoneConstants.APN_TYPE_MMS)) {
                return PROFILE_TYPE_MMS;
            } else if (TextUtils.equals(serviceType, PhoneConstants.APN_TYPE_SUPL)) {
                return PROFILE_TYPE_LBS;
            } else if (TextUtils.equals(serviceType, PhoneConstants.APN_TYPE_DUN)) {
                return PROFILE_TYPE_TETHERED;
            } else {
                /* For all other service types, return unspecified */
                return PROFILE_TYPE_UNSPECIFIED;
            }
        }
    }

    private int DATA_PROFILE_OMH_PRIORITY_LOWEST = 255;

    private int DATA_PROFILE_OMH_PRIORITY_HIGHEST = 0;

    private DataProfileTypeModem mDataProfileModem;

    private int serviceTypeMasks = 0;

    /* Priority of this profile in the modem */
    private int mPriority = 0;

    private static String PROFILE_TYPE = "DataProfileOmh";

    public DataProfileOmh(int id, String numeric, String name, String user, String password,
            int authType, String[] types, String protocol, String roamingProtocol, int bearer) {
        super(id, numeric, name, user, password,
                authType, types, protocol, roamingProtocol, bearer);
    }

    public DataProfileOmh() {
        /**
         * Default values if the profile is being used for only selective
         * fields e.g: just profileId and Priority. use case is when rest of the
         * fields can be read and processed only by the modem
         */
        this(0, "", PROFILE_TYPE, null, null,
                RILConstants.SETUP_DATA_AUTH_PAP_CHAP, null,
                "IP", "IP", 0);

        this.mProfileId = 0;
        this.mPriority = 0;
    }

    public DataProfileOmh(int profileId, int priority) {
        this();
        this.mProfileId = profileId;
        this.mPriority = priority;
        this.types = new String[0];
    }

    @Override
    public boolean canHandleType(String serviceType) {
        return ( 0 != (serviceTypeMasks & DataProfileTypeModem.
                getDataProfileTypeModem(serviceType).getid()));
    }

    @Override
    public DataProfileType getDataProfileType() {
        return DataProfileType.PROFILE_TYPE_OMH;
    }

    @Override
    public String toShortString() {
        return "DataProfile OMH";
    }

    @Override
    public String toHash() {
        return this.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
                .append(mProfileId)
                .append(", ").append(mPriority);
        sb.append("]");
        return sb.toString();
    }

    public void setDataProfileTypeModem(DataProfileTypeModem modemProfile) {
        mDataProfileModem = modemProfile;
    }

    public DataProfileTypeModem getDataProfileTypeModem() {
        return mDataProfileModem;
    }

    public void setPriority(int priority) {
        mPriority = priority;
    }

    /* priority defined from 0..255; 0 is highest */
    public boolean isPriorityHigher(int priority) {
        return isValidPriority(priority) && (mPriority < priority);
    }

    /* priority defined from 0..255; 0 is highest */
    public boolean isPriorityLower(int priority) {
        return isValidPriority(priority) && mPriority > priority;
    }

    public boolean isValidPriority() {
        return isValidPriority(mPriority);
    }

    /* NOTE: priority values are reverse, lower number = higher priority */
    private boolean isValidPriority(int priority) {
        return priority >= DATA_PROFILE_OMH_PRIORITY_HIGHEST &&
                priority <= DATA_PROFILE_OMH_PRIORITY_LOWEST;
    }

    public int getProfileId() {
        return mProfileId;
    }

    public int getPriority() {
        return mPriority;
    }

    public String[] getServiceTypes() {
        String[] dummy = null;
        return dummy;
    }

    public void addServiceType(DataProfileTypeModem modemProfile) {
        serviceTypeMasks |= modemProfile.getid();

        // Update the types
        ArrayList<String> serviceTypes = new ArrayList<String>();
        for (DataProfileTypeModem dpt : DataProfileTypeModem.values()) {
            if (0 != (serviceTypeMasks & dpt.getid())) {
                serviceTypes.add(dpt.getDataServiceType());
            }
        }
        types = serviceTypes.toArray(new String[0]);
    }
}
