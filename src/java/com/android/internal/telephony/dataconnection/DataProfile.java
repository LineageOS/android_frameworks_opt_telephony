/*
 * Copyright (c) 2010-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

package com.android.internal.telephony.dataconnection;
import com.android.internal.telephony.RILConstants;

public abstract class DataProfile {

    protected final static String LOG_TAG = "DataProfile";

    public final int id;
    public final String apn;
    public final String user;
    public final String password;
    public final int    authType;
    public final String protocol;
    public final String roamingProtocol;
    public final String numeric;

    public String carrier;
    public String proxy;
    public String port;
    public String mmsc;
    public String mmsProxy;
    public String mmsPort;

    /* ID of the profile in the modem */
    protected int mProfileId = 0;

    /**
     * Current status of APN
     * true : enabled APN, false : disabled APN.
     */
    public boolean carrierEnabled;

    /**
     * Radio Access Technology info
     * To check what values can hold, refer to ServiceState.java.
     * This should be spread to other technologies,
     * but currently only used for LTE(14) and EHRPD(13).
     */
    public final int bearer;

    public String[] types;

    public enum DataProfileType {
        PROFILE_TYPE_APN(0),
        PROFILE_TYPE_CDMA(1),
        PROFILE_TYPE_OMH(2);

        int id;

        private DataProfileType(int i) {
            this.id = i;
        }

        public int getid() {
            return id;
        }
    }

    public boolean mTetheredCallOn = false;

    private DataConnection mDc = null;

    public DataProfile (int id, String numeric, String apn, String user, String password,
            int authType, String[] types, String protocol, String roamingProtocol, int bearer) {
        this.id = id;
        this.numeric = numeric;
        this.apn = apn;
        this.types = types;
        this.user = user;
        this.password = password;
        this.authType = authType;
        this.protocol = protocol;
        this.roamingProtocol = roamingProtocol;
        this.bearer = bearer;
    }

    /* package */ boolean isActive() {
      return mDc != null;
    }

    /* package */void setAsActive(DataConnection dc) {
        mDc = dc;
    }

    /* package */void setAsInactive() {
        mDc = null;
    }

    public String[] getServiceTypes() {
        return types.clone();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[DataProfile] ")
        .append(", ").append(id)
        .append(", ").append(numeric)
        .append(", ").append(apn)
        .append(", ").append(authType).append(", ");
        for (int i = 0; i < types.length; i++) {
            sb.append(types[i]);
            if (i < types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(protocol);
        sb.append(", ").append(roamingProtocol);
        sb.append(", ").append(bearer);
        return sb.toString();
    }

    public void setProfileId(int profileId) {
        mProfileId = profileId;
    }

    /* some way to identify this data profile uniquely */
    public abstract String toHash();

    public abstract String toShortString();

    public abstract boolean canHandleType(String type);

    public abstract DataProfileType getDataProfileType();

    public abstract int getProfileId();
}
