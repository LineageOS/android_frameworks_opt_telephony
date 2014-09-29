/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2011, 2012 The CyanogenMod Project
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

package com.android.internal.telephony;

import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.BitwiseInputStream.AccessException;

public class VirginMobileDecoder {
    /**
     * If we have VirginMobile MMS, decode it
     * Then, update mUserData & mMessageRef fields
     * If not VirginMobile MMS, do nothing
     */
    public static void decodeMMS(SmsMessageBase sms) {
        BitwiseInputStream inStream;
        byte[] userData = null;
        int ref = 0;
        int i1 = 0;
        int desiredBitLength = 0;
        try {
            inStream = new BitwiseInputStream(sms.getUserData());
            inStream.skip(20);
            final int j = inStream.read(8) << 8;
            final int k = inStream.read(8);
            ref = j | k;
            inStream.skip(12);
            i1 = inStream.read(8) + -2;
            inStream.skip(13);
            byte abyte1[] = new byte[i1];
            for (int j1 = 0; j1 < i1; j1++) {
                abyte1[j1] = 0;
            }
            desiredBitLength = i1 * 8;
            if (inStream.available() < desiredBitLength) {
                 return;
            }
            userData = inStream.readByteArray(desiredBitLength);
        } catch (AccessException e) {
            return;
        }
        sms.mUserData = userData;
        sms.mMessageRef = ref;
    }
}
