/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.
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

package org.codeaurora.ims.qtiims;

import org.codeaurora.ims.qtiims.IQtiImsInterfaceListener;

/**
 * Interface through which APP and vendor communicates.
 * {@hide}
 */
oneway interface IQtiImsInterface {
   /**
     * setCallForwardingUncondTimerOptions
     * sets a call forwarding unconditional Timer option.
     *
     * @param startHour indicates starting hour
     * @param startMinute indicates starting minute
     * @param endHour indicates ending hour
     * @param endMinute indicates ending minute
     * @param action is one of the valid call forwarding
     *        CF_ACTIONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param serviceClass is service class, that is used to set CFT
     *        SERVICE_CLASS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param dialingNumber is the target phone number to forward calls to
     * @param QtiImsInterfaceListener listener to request
     * @return void
     */
    void setCallForwardUncondTimer(int startHour, int startMinute, int endHour, int endMinute,
            int action, int reason, int serviceClass, String dialingNumber,
            IQtiImsInterfaceListener listener);

   /**
     * getCallForwardingUncondTimerOptions
     * gets a call forwarding option.
     *
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param serviceClass is service class, that is used to get CFT
     *        SERVICE_CLASS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param QtiImsInterfaceListener listener to request
     * @return void
     */
    void getCallForwardUncondTimer(int reason, int serviceClass,
            IQtiImsInterfaceListener listener);

    /**
      * Total number of packets sent or received
      *
      * @param listener, provided if caller needs to be notified for get result.
      * @return void
      *
      * @throws RemoteException if calling the IMS service results in an error.
      */
    void getPacketCount(IQtiImsInterfaceListener listener);

   /**
     * Total number of packet errors encountered
     *
     * @param listener, provided if caller needs to be notified for get result.
     * @return void
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    void getPacketErrorCount(IQtiImsInterfaceListener listener);

   /**
     * sendCallDeflectRequest
     * Deflects a incoming call to given number
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param deflectNumber indicates the target number to deflect
     * @param listener an IQtiImsInterfaceListener instance to indicate the response
     * @return void
     */
    void sendCallDeflectRequest(int phoneId, String deflectNumber,
            IQtiImsInterfaceListener listener);

   /**
     * Register for VICE dialog
     *
     * @param listener, provided if caller needs to be notified for get result.
     * @return void
     *
     */
    void registerForViceRefreshInfo(IQtiImsInterfaceListener listener);

}
