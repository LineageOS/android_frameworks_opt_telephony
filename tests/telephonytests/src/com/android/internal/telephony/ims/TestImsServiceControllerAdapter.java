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

package com.android.internal.telephony.ims;

import android.os.RemoteException;

import com.android.ims.internal.IImsServiceController;

import static org.mockito.Mockito.spy;

/**
 * Test base implementation of the ImsServiceController
 */

public class TestImsServiceControllerAdapter {

    public class ImsServiceControllerBinder extends IImsServiceController.Stub {

        @Override
        public void createImsFeature(int subId, int feature) throws RemoteException {
            TestImsServiceControllerAdapter.this.createImsFeature(subId, feature);
        }

        @Override
        public void removeImsFeature(int subId, int feature) throws RemoteException {
            TestImsServiceControllerAdapter.this.removeImsFeature(subId, feature);
        }
    }

    private ImsServiceControllerBinder mBinder;

    public IImsServiceController getBinder() {
        if (mBinder == null) {
            mBinder = spy(new ImsServiceControllerBinder());
        }

        return mBinder;
    }

    public void createImsFeature(int subId, int feature) throws RemoteException {
    }

    public void removeImsFeature(int subId, int feature) throws RemoteException {
    }
}
