/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
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
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.codeaurora.ims.qtiims;

import android.os.Parcel;
import android.os.Parcelable;

/*
 * This file contains all the api's through which
 * information received in Vice Dialog can be
 * queried
 */

/**
 * Parcelable object to handle VICE Dialog Information
 * @hide
 */

public class QtiViceInfo implements Parcelable {

    private String mViceInfoAsString = null;

    public QtiViceInfo() {
    }

    public QtiViceInfo(Parcel in) {
        readFromParcel(in);
    }

    public void setViceDialogInfoAsString(String value) {
        mViceInfoAsString = value;
    }

    public String getViceDialogInfoAsString() {
        return mViceInfoAsString;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private void readFromParcel(Parcel in) {

    }

    @Override
    public void writeToParcel(Parcel out, int flags) {

    }

    public static final Creator<QtiViceInfo> CREATOR =
            new Creator<QtiViceInfo>() {
        @Override
        public QtiViceInfo createFromParcel(Parcel in) {
            return new QtiViceInfo(in);
        }

        @Override
        public QtiViceInfo[] newArray(int size) {
            return new QtiViceInfo[size];
        }
    };
}
