/* Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES O
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

package com.android.internal.telephony;

public class CallModify {
    // Keep this error codes in sync with error codes defined in
    // imsIF.proto file.
    public static int E_SUCCESS = 0;
    public static int E_CANCELLED = 7;
    public static int E_UNUSED = 16;

    public int call_index;

    public CallDetails call_details;

    public int error;

    public CallModify() {
        this(new CallDetails(), 0);
    }

    public CallModify(CallDetails callDetails, int callIndex) {
        this(callDetails, callIndex, E_SUCCESS);
    }

    public CallModify(CallDetails callDetails, int callIndex, int err) {
        call_details = callDetails;
        call_index = callIndex;
        error = err;
    }

    public void setCallDetails(CallDetails calldetails) {
        call_details = new CallDetails(calldetails);
    }

    /**
     * @return true if the message is sent to notify about the error.
     */
    public boolean error() {
        return this.error != E_UNUSED && this.error != E_SUCCESS;
    }

    /**
     * @return string representation.
     */
    @Override
    public String toString() {
        return (" " + call_index
                + " " + call_details
                + " " + error);
    }
}
