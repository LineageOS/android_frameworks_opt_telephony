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

import android.os.Parcel;
import android.os.Parcelable;

public class CallForwardInfoP implements Parcelable {

    public int             status;      /*1 = active, 0 = not active */
    public int             reason;      /* from TS 27.007 7.11 "reason" */
    public int             serviceClass; /*Sum of CommandsInterface.SERVICE_CLASS */
    public int             toa;         /* "type" from TS 27.007 7.11 */
    public String          number;      /* "number" from TS 27.007 7.11 */
    public int             timeSeconds; /* for CF no reply only */

    @Override
    public int describeContents() {
        return 0;
    }

    public CallForwardInfoP() {
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(status);
        out.writeInt(reason);
        out.writeInt(toa);
        out.writeString(number);
        out.writeInt(timeSeconds);
        out.writeInt(serviceClass);
    }

    public static final Parcelable.Creator<CallForwardInfoP> CREATOR = new
            Parcelable.Creator<CallForwardInfoP>() {

                @Override
                public CallForwardInfoP createFromParcel(Parcel in) {
                    return new CallForwardInfoP(in);
                }

                @Override
                public CallForwardInfoP[] newArray(int size) {
                    return new CallForwardInfoP[size];
                }
            };

    public CallForwardInfoP(Parcel in) {
        status = in.readInt();
        reason = in.readInt();
        toa = in.readInt();
        number = in.readString();
        timeSeconds = in.readInt();
        serviceClass = in.readInt();
    }
}
