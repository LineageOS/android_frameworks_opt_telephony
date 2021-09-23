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

/**
 * This class is the implementation of IRadioConfigIndication interface.
 */
public class RadioConfigIndication {
    private RadioConfigIndicationHidl mRadioConfigIndicationHidl;
    private RadioConfigIndicationAidl mRadioConfigIndicationAidl;

    public RadioConfigIndication(RadioConfig radioConfig, HalVersion halVersion) {
        if (halVersion.greaterOrEqual(RIL.RADIO_HAL_VERSION_2_0)) {
            mRadioConfigIndicationAidl = new RadioConfigIndicationAidl(radioConfig);
        } else {
            mRadioConfigIndicationHidl = new RadioConfigIndicationHidl(radioConfig);
        }
    }

    public android.hardware.radio.config.V1_2.IRadioConfigIndication getV1() {
        return mRadioConfigIndicationHidl;
    }

    public android.hardware.radio.config.IRadioConfigIndication getV2() {
        return mRadioConfigIndicationAidl;
    }
}
