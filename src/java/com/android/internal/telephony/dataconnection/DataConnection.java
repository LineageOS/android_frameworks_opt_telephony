/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.os.Message;
import android.telephony.Rlog;
import android.util.Patterns;
import android.text.TextUtils;

import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RetryManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * {@hide}
 */
public class DataConnection extends DataConnectionBase {

    //***** Instance Variables
    protected int mProfileId = RILConstants.DATA_PROFILE_DEFAULT;
    //***** Constructor
    private DataConnection(PhoneBase phone, String name, int id, RetryManager rm,
            DataConnectionTrackerBase dct) {
        super(phone, name, id, rm, dct);
    }

    /**
     * Create the connection object
     *
     * @param phone the Phone
     * @param id the connection id
     * @param rm the RetryManager
     * @return DataConnection that was created.
     */
    static DataConnection makeDataConnection(PhoneBase phone, int id, RetryManager rm,
            DataConnectionTrackerBase dct) {
        DataConnection gsmDc = new DataConnection(phone,
                "DC-" + mCount.incrementAndGet(), id, rm, dct);
        gsmDc.start();
        if (DBG) gsmDc.log("Made " + gsmDc.getName());
        return gsmDc;
    }

    /**
     * Begin setting up a data connection, calls setupDataCall
     * and the ConnectionParams will be returned with the
     * EVENT_SETUP_DATA_CONNECTION_DONE AsyncResul.userObj.
     *
     * @param cp is the connection parameters
     */
    @Override
    protected
    void onConnect(ConnectionParams cp) {
        mApn = cp.mApnContext.getApnSetting();

        if (DBG) log("onConnect: carrier='" + mApn.carrier
                + "' APN='" + mApn.apn
                + "' proxy='" + mApn.proxy + "' port='" + mApn.port + "'");

        mCreateTime = -1;
        mLastFailTime = -1;
        mLastFailCause = FailCause.NONE;

        // msg.obj will be returned in AsyncResult.userObj;
        Message msg = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
        msg.obj = cp;

        int authType = mApn.authType;
        if (authType == -1) {
            authType = TextUtils.isEmpty(mApn.user) ? RILConstants.SETUP_DATA_AUTH_NONE
                    : RILConstants.SETUP_DATA_AUTH_PAP_CHAP;
        }

        String protocol;
        if (mPhone.getServiceState().getRoaming()) {
            protocol = mApn.roamingProtocol;
        } else {
            protocol = mApn.protocol;
        }

        mPhone.mCi.setupDataCall(
                Integer.toString(getRilRadioTechnology()),
                Integer.toString(mProfileId),
                mApn.apn, mApn.user, mApn.password,
                Integer.toString(authType),
                protocol, msg);
    }

    public void setProfileId(int profileId) {
        mProfileId = profileId;
    }

    public int getProfileId() {
        return mProfileId;
    }

    /** Doesn't print mApnList of ApnContext's which would be recursive */
    @Override
    public String toStringSimple() {
        return getName() + ": State=" + getCurrentState().getName() +
                " apnSetting=" + mApn + " RefCount=" + mApnList.size() +
                " cid=" + mCid + " create=" + mCreateTime + " lastFail=" + mLastFailTime +
                " lastFailCause=" + mLastFailCause;
    }

    @Override
    public String toString() {
        return "{" + toStringSimple() + " mApnList=" + mApnList + "}";
    }

    @Override
    protected boolean isDnsOk(String[] domainNameServers) {
        if (NULL_IP.equals(domainNameServers[0]) && NULL_IP.equals(domainNameServers[1])
                && !mPhone.isDnsCheckDisabled()) {
            // Work around a race condition where QMI does not fill in DNS:
            // Deactivate PDP and let DataConnectionTracker retry.
            // Do not apply the race condition workaround for MMS APN
            // if Proxy is an IP-address.
            // Otherwise, the default APN will not be restored anymore.
            if (!mApn.types[0].equals(PhoneConstants.APN_TYPE_MMS)
                || !isIpAddress(mApn.mmsProxy)) {
                log(String.format(
                        "isDnsOk: return false apn.types[0]=%s APN_TYPE_MMS=%s isIpAddress(%s)=%s",
                        mApn.types[0], PhoneConstants.APN_TYPE_MMS, mApn.mmsProxy,
                        isIpAddress(mApn.mmsProxy)));
                return false;
            }
        }
        return true;
    }

    @Override
    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    private boolean isIpAddress(String address) {
        if (address == null) return false;

        return Patterns.IP_ADDRESS.matcher(address).matches();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DataConnection extends:");
        super.dump(fd, pw, args);
        pw.println(" mProfileId=" + mProfileId);
    }
}
