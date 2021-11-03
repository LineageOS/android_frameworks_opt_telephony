/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.telephony.TelephonyManager;

import java.util.Set;

/**
 * This class is the implementation of IRadioConfigResponse interface.
 */
public class RadioConfigResponse {
    private static final String TAG = "RadioConfigResponse";

    private final HalVersion mHalVersion;

    private RadioConfigResponseAidl mRadioConfigResponseAidl = null;
    private RadioConfigResponseHidl mRadioConfigResponseHidl = null;

    public RadioConfigResponse(RadioConfig radioConfig, HalVersion halVersion) {
        mHalVersion = halVersion;

        if (mHalVersion.greaterOrEqual(RIL.RADIO_HAL_VERSION_2_0)) {
            mRadioConfigResponseAidl = new RadioConfigResponseAidl(radioConfig, halVersion);
        } else {
            mRadioConfigResponseHidl = new RadioConfigResponseHidl(radioConfig, halVersion);
        }
    }

    public android.hardware.radio.config.V1_3.IRadioConfigResponse getV1() {
        return mRadioConfigResponseHidl;
    }

    public android.hardware.radio.config.IRadioConfigResponse getV2() {
        return mRadioConfigResponseAidl;
    }

    /**
     * Returns all capabilities supported in the most recent radio hal version.
     * <p/>
     * Used in the {@link RILConstants.REQUEST_NOT_SUPPORTED} case.
     *
     * @return all capabilities
     */
    @TelephonyManager.RadioInterfaceCapability
    public Set<String> getFullCapabilitySet() {
        return RILUtils.getCaps(mHalVersion, false);
    }
}
