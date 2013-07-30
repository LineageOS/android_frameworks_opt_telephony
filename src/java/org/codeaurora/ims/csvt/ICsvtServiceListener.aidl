/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
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

package org.codeaurora.ims.csvt;

import org.codeaurora.ims.csvt.CallForwardInfoP;

/**
 * Listener interface for the clients of CSVT Service
 *
 * {@hide}
 */
oneway interface ICsvtServiceListener{

    /**
     * Called when phone state changed.
     * @param state, see TelephonyManager
     */
    void onPhoneStateChanged(int state);

    /**
     * Called to notify about video call status.
     * @param result, result cause
     */
    void onCallStatus(int status);

    /**
     * Called to notify about call waiting activation state.
     * @param enabled, if true call waiting is activated, false otherwise.
     * @see ICsvtService.getCallWaiting
     */
    void onCallWaiting( boolean enabled );

    /**
     * Called to notify about call forwarding options.
     * @param fi, Call Forwarding options.
     */
    void onCallForwardingOptions( in List<CallForwardInfoP> fi );
}
