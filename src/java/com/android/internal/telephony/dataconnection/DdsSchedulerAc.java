/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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
 *IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony.dataconnection;

import com.android.internal.util.Protocol;
import com.android.internal.util.AsyncChannel;

import android.telephony.Rlog;

import android.net.NetworkRequest;
import android.os.Message;

public class DdsSchedulerAc extends AsyncChannel {

    private final String TAG = "DdsSchedulerAc";

    private DdsScheduler mScheduler;
    public static final int BASE = Protocol.BASE_DDS_SCHEDULER;
    public static final int REQ_DDS_ALLOCATION = BASE + 0;
    public static final int REQ_DDS_FREE = BASE + 1;
    public static final int EVENT_ON_DEMAND_DDS_SWITCH_DONE = BASE + 2;
    public static final int EVENT_ON_DEMAND_PS_ATTACH_DONE = BASE + 3;
    public static final int EVENT_MODEM_DATA_CAPABILITY_UPDATE = BASE + 4;
    public static final int EVENT_ADD_REQUEST = BASE + 5;
    public static final int EVENT_REMOVE_REQUEST = BASE + 6;

    public DdsSchedulerAc() {
    }

    public void allocateDds(NetworkRequest req) {
        Rlog.d(TAG, "EVENT_ADD_REQUEST = " + req);
        sendMessage(EVENT_ADD_REQUEST, req);
    }

    public void freeDds(NetworkRequest req) {
        Rlog.d(TAG, "EVENT_REMOVE_REQUEST = " + req);
        sendMessage(EVENT_REMOVE_REQUEST, req);
    }
}
