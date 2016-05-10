/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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
 * Neither the name of The Linux Foundation, Inc. nor the names of its
         contributors may be used to endorse or promote products derived
         from this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
   WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
   ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
   BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
   CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
   SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
   BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
   WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
   OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
   IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony.uicc;

import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import java.util.Arrays;


/**
 *
 * Used to store ADNs (Abbreviated Dialing Numbers).
 *
 * {@hide}
 *
 */
public class SimPhoneBookAdnRecord {
    static final String LOG_TAG = "SimPhoneBookAdnRecord";

    //***** Instance Variables
    public int mRecordIndex = 0;

    public String mAlphaTag = null;
    public String mNumber = null;

    public int mEmailCount = 0;
    public String[] mEmails = null;

    public int mAdNumCount = 0;
    public String[] mAdNumbers = null;
    //***** Constants


    //***** Instance Methods

    public int getRecordIndex() {
        return mRecordIndex;
    }

    public String getAlphaTag() {
        return mAlphaTag;
    }

    public String getNumber() {
        return mNumber;
    }

    public int getNumEmails() {
        return mEmailCount;
    }

    public String[] getEmails() {
        return mEmails;
    }

    public int getNumAdNumbers() {
        return mAdNumCount;
    }

    public String[] getAdNumbers() {
        return mAdNumbers;
    }

    public static String ConvertToPhoneNumber(String input) {
        return input == null ? null : input.replace( 'e', PhoneNumberUtils.WAIT )
                                            .replace( 'T', PhoneNumberUtils.PAUSE )
                                            .replace( '?', PhoneNumberUtils.WILD );
    }

    public static String ConvertToRecordNumber(String input) {
        return input == null ? null : input.replace( PhoneNumberUtils.WAIT, 'e' )
                                            .replace( PhoneNumberUtils.PAUSE, 'T' )
                                            .replace( PhoneNumberUtils.WILD, '?' );
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(mAlphaTag)
                && TextUtils.isEmpty(mNumber)
                && mEmails == null
                && mAdNumbers == null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("SimPhoneBookAdnRecord{").append("index =").append(mRecordIndex);

        sb.append(", name = ").append(mAlphaTag == null ? "null" : mAlphaTag);
        sb.append(", number = ").append(mNumber == null ? "null" : mNumber);

        sb.append(", email count = ").append(mEmailCount);
        sb.append(", email = ").append(Arrays.toString(mEmails));

        sb.append(", ad number count = ").append(mAdNumCount);
        sb.append(", ad number = ").append(Arrays.toString(mAdNumbers));

        sb.append("}");
        return sb.toString();
    }
}
