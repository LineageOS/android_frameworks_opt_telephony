/*
 * Copyright (C) 2009 Qualcomm Innovation Center, Inc.  All Rights Reserved.
 * Copyright (C) 2009 The Android Open Source Project
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

import android.os.SystemProperties;
import android.text.TextUtils;

/**
 * This is RIL_Data_Call_Response_v5 from ril.h
 */
public class DataCallResponse {
    private final boolean DBG = true;
    private final String LOG_TAG = "DataCallResponse";

    public final int status;
    public final int suggestedRetryTime;
    public final int cid;
    public final int active;
    public final String type;
    public final String ifname;
    public final String [] addresses;
    public final String [] dnses;
    public final String[] gateways;
    public final String [] pcscf;
    public final int mtu;

    public DataCallResponse(int status, int suggestedRetryTime, int cid, int active, String type,
                            String ifname, String addresses, String dnses, String gateways,
                            String pcscf, int mtu) {
        this.status = status;
        this.suggestedRetryTime = suggestedRetryTime;
        this.cid = cid;
        this.active = active;
        this.type = (type == null) ? "" : type;
        this.ifname = (ifname == null) ? "" : ifname;
        if ((status == DcFailCause.NONE.getErrorCode()) && TextUtils.isEmpty(ifname)) {
            throw new RuntimeException("DataCallResponse, no ifname");
        }
        this.addresses = TextUtils.isEmpty(addresses) ? new String[0] : addresses.split(" ");
        this.dnses = TextUtils.isEmpty(dnses) ? new String[0] : dnses.split(" ");

        String[] myGateways = TextUtils.isEmpty(gateways)
                ? new String[0] : gateways.split(" ");

        // set gateways
        if (myGateways.length == 0) {
            String propertyPrefix = "net." + this.ifname + ".";
            String sysGateways = SystemProperties.get(propertyPrefix + "gw");
            if (sysGateways != null) {
                myGateways = sysGateways.split(" ");
            } else {
                myGateways = new String[0];
            }
        }
        this.gateways = myGateways;

        this.pcscf = TextUtils.isEmpty(pcscf) ? new String[0] : pcscf.split(" ");
        this.mtu = mtu;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("DataCallResponse: {")
           .append(" status=").append(status)
           .append(" retry=").append(suggestedRetryTime)
           .append(" cid=").append(cid)
           .append(" active=").append(active)
           .append(" type=").append(type)
           .append(" ifname=").append(ifname)
           .append(" mtu=").append(mtu)
           .append(" addresses=[");
        for (String addr : addresses) {
            sb.append(addr);
            sb.append(",");
        }
        if (addresses.length > 0) sb.deleteCharAt(sb.length()-1);
        sb.append("] dnses=[");
        for (String addr : dnses) {
            sb.append(addr);
            sb.append(",");
        }
        if (dnses.length > 0) sb.deleteCharAt(sb.length()-1);
        sb.append("] gateways=[");
        for (String addr : gateways) {
            sb.append(addr);
            sb.append(",");
        }
        if (gateways.length > 0) sb.deleteCharAt(sb.length()-1);
        sb.append("] pcscf=[");
        for (String addr : pcscf) {
            sb.append(addr);
            sb.append(",");
        }
        if (pcscf.length > 0) sb.deleteCharAt(sb.length()-1);
        sb.append("]}");
        return sb.toString();
    }
}
