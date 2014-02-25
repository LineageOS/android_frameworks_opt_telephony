/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

public class ServiceStatus {
    public boolean isValid = false;
    public int type; /* Refer "enum CallType" from imsIF.proto file
                      * for possible types
                      */
    public int status; /*
                        * Overall Status (eg. enabled, disabled)
                        * best case for this type across all
                        * access techs
                        * Refer "enum StatusType" from imsIF.proto file
                        * for possible status values
                        */
    public byte[] userdata;
    public StatusForAccessTech[] accessTechStatus;
    public static class StatusForAccessTech {
        public int networkMode; /* Refer "enum RadioTechType" from
                                 * imsIF.proto file for possible
                                 * networkMode values
                                 */
        public int status; /* Refer "enum StatusType" from imsIF.proto
                            * file for possible status values
                            */
        public int restrictCause;
        public int registered; /* Refer "enum RegState" from imsIF.proto
                                * file for possible values
                                */

        /**
         * @return string representation.
         */
        @Override
        public String toString() {
            return " mode = " + networkMode + " Status = " + status + " restrictCause = "
                    + restrictCause + " registered = " + registered;
        }
    }
}
