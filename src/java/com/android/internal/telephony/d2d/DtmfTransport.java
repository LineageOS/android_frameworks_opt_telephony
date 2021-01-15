/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.d2d;

import java.util.Set;

/**
 * Implements a DTMF-based transport for use with device-to-device communication.
 *
 * TODO: This is a stub placeholder protocol for now.
 */
public class DtmfTransport implements TransportProtocol {

    private TransportProtocol.Callback mCallback;

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void startNegotiation() {
        // TODO: implement
    }

    @Override
    public void sendMessages(Set<Communicator.Message> messages) {
        // TODO: implement
    }
}
