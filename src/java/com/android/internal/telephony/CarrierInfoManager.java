/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.telephony.ImsiEncryptionInfo;

 /**
 * This class provides methods to retreive information from the CarrierKeyProvider.
 */
public class CarrierInfoManager {
    private static final String TAG = "CarrierInfoManager";

    /**
     * Returns Carrier specific information that will be used to encrypt the IMSI and IMPI.
     * @param keyType whether the key is being used for WLAN or ePDG.
     * @return ImsiEncryptionInfo which contains the information including the public key to be
     *         used for encryption.
     */
    public static ImsiEncryptionInfo getCarrierInfoForImsiEncryption(int keyType) {
        //TODO implementation will be done in subsequent CL.
        return null;
    }

    /**
     * Sets the Carrier specific information that will be used to encrypt the IMSI and IMPI.
     * This includes the public key and the key identifier. This information will be stored in the
     * device keystore.
     * @param imsiEncryptionInfo which includes the Key Type, the Public Key
     *        {@link java.security.PublicKey} and the Key Identifier.
     *        The keyIdentifier Attribute value pair that helps a server locate
     *        the private key to decrypt the permanent identity.
     */
    public static void setCarrierInfoForImsiEncryption(ImsiEncryptionInfo imsiEncryptionInfo) {
        //TODO implementation will be done in subsequent CL.
        return;
    }
}

