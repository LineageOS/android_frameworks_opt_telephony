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

package android.telephony.ims;

import android.os.RemoteException;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsCallSessionImplBase;
import android.telephony.ims.stub.ImsEcbmImplBase;
import android.telephony.ims.stub.ImsMultiEndpointImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.ImsUtImplBase;

public class TestMmTelFeature extends MmTelFeature {

    public boolean queryConfigurationResult = false;
    public int setCapabilitiesResult = ImsFeature.CAPABILITY_SUCCESS;
    public CapabilityChangeRequest lastRequest;
    public boolean isUtInterfaceCalled = false;

    public void incomingCall(ImsCallSessionImplBase c) throws RemoteException {
        notifyIncomingCall(c, null);
    }

    @Override
    public ImsCallProfile createCallProfile(int callSessionType, int callType) {
        return super.createCallProfile(callSessionType, callType);
    }

    @Override
    public ImsCallSessionImplBase createCallSession(ImsCallProfile profile) {
        return super.createCallSession(profile);
    }

    @Override
    public ImsUtImplBase getUt() {
        isUtInterfaceCalled = true;
        return super.getUt();
    }

    @Override
    public ImsEcbmImplBase getEcbm() {
        return super.getEcbm();
    }

    @Override
    public ImsMultiEndpointImplBase getMultiEndpoint() {
        return super.getMultiEndpoint();
    }

    @Override
    public boolean queryCapabilityConfiguration(@MmTelCapabilities.MmTelCapability int capability,
            @ImsRegistrationImplBase.ImsRegistrationTech int radioTech) {
        // Base implementation - Override to provide functionality
        return queryConfigurationResult;
    }

    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {
        lastRequest = request;
        if (setCapabilitiesResult != ImsFeature.CAPABILITY_SUCCESS) {
            // Take the first value to enable and return it as an error.
            CapabilityChangeRequest.CapabilityPair capPair = request.getCapabilitiesToEnable()
                    .get(0);
            c.onChangeCapabilityConfigurationError(capPair.getCapability(), capPair.getRadioTech(),
                    ImsFeature.CAPABILITY_ERROR_GENERIC);
        }
    }

    public void sendSetFeatureState(int state) {
        setFeatureState(state);
    }

    @Override
    public void onFeatureRemoved() {
    }

    @Override
    public void onFeatureReady() {
    }
}
