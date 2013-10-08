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

import org.codeaurora.ims.csvt.ICsvtServiceListener;
import android.os.Message;

interface ICsvtService {

    /**
     * Initiate a new CSVT connection. This happens asynchronously, so you
     * cannot assume the audio path is connected (or a call index has been
     * assigned) until PhoneStateChanged notification has occurred.
     */
     void dial( String number );

    /**
     * Hang up the foreground call. Reject occurs asynchronously,
     * and final notification occurs via PhoneStateChanged callback.
     */
    void hangup();

    /**
     * Answers a ringing.
     * Answering occurs asynchronously, and final notification occurs via
     * PhoneStateChanged callback.
     */
    void acceptCall();

    /**
     * Reject (ignore) a ringing call. In GSM, this means UDUB
     * (User Determined User Busy). Reject occurs asynchronously,
     * and final notification occurs via  PhoneStateChanged callback.
     */
    void rejectCall();

    /**
     * Reject (ignore) a ringing call and sends Incompatible Destination
     * fail cause to the remote party. Reject occurs asynchronously,
     * and final notification occurs via  PhoneStateChanged callback.
     */
    void fallBack();

    /**
     * Checks if there is an active or ringing CSVT call.
     * @return true if there is an active or ringing CSVT call.
     */
    boolean isIdle();

    /**
    * Checks if there is an active Csvt call.
    * @return true if there is an active Csvt call.
    */
    boolean isActive();

    /**
     * Checks if all non-CSVT calls are idle.
     * @return true if all non-CSVT calls are idle.
     */
    boolean isNonCsvtIdle();

    /**
     * getCallForwardingOptions
     * Call Forwarding options are returned via
     * ICsvtServiceListener.onCallForwardingOptions callback.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param onComplete a callback message when the action is completed.
     *        onComplete.arg1 is set to zero (0) if the action is completed
     *        successfully.
     * @see   ICsvtServiceListener.onCallForwardingOptions
     */
    void getCallForwardingOption(int commandInterfaceCFReason,
                                 in Message onComplete);

    /**
     * setCallForwardingOptions
     * sets a call forwarding option.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param commandInterfaceCFAction is one of the valid call forwarding
     *        CF_ACTIONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param dialingNumber is the target phone number to forward calls to
     * @param timerSeconds is used by CFNRy to indicate the timeout before
     *        forwarding is attempted.
     * @param onComplete a callback message when the action is completed.
     *        onComplete.arg1 is set to zero (0) if the action is completed
     *        successfully.
     */
    void setCallForwardingOption(int commandInterfaceCFReason,
                                 int commandInterfaceCFAction,
                                 String dialingNumber,
                                 int timerSeconds,
                                 in Message onComplete);
    /**
     * getCallWaiting
     * gets call waiting activation state. The call waiting activation state
     * is returned via ICsvtServiceListener.onCallWaiting callback.
     *
     * @param onComplete a callback message when the action is completed.
     *        onComplete.arg1 is set to zero (0) if the action completed
     *        successfully.
     * @see   ICsvtServiceListener.onCallWaiting
     */
    void getCallWaiting(in Message onComplete);

    /**
     * setCallWaiting
     * sets a call forwarding option.
     *
     * @param enable is a boolean representing the state that you are
     *        requesting, true for enabled, false for disabled.
     * @param onComplete a callback message when the action is completed.
     *        onComplete.arg1 is set to zero (0) if the action is completed
     *        successfully.
     */
    void setCallWaiting(boolean enable, in Message onComplete);

    void registerListener(ICsvtServiceListener l);
    void unregisterListener(ICsvtServiceListener l);
}
