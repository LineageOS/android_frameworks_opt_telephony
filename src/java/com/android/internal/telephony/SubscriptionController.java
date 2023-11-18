/*
 * Copyright (C) 2023 PixelBuildsROM
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

import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.subscription.SubscriptionManagerService;

public class SubscriptionController {
    private static final String LOG_TAG = "SubscriptionController";
    protected static SubscriptionController sInstance = null;

    public static SubscriptionController getInstance() {
        // Lazy init happens once, whenever getInstance() is invoked for the first time
        if (sInstance == null) {
            synchronized (SubscriptionController.class) {
                if (sInstance == null) {
                    Log.v(LOG_TAG, "getInstance() was invoked for the first time, "
                            + "initializing the stub SubscriptionController");
                    sInstance = new SubscriptionController();
                }
            }
        }
        return sInstance;
    }

    /**
     * @return The subscription manager service instance.
     */
    public SubscriptionManagerService getSubscriptionManagerService() {
        return SubscriptionManagerService.getInstance();
    }

    public int getSubIdUsingPhoneId(int phoneId) {
        SubscriptionManagerService subscriptionManagerService = getSubscriptionManagerService();
        int subId = subscriptionManagerService.getSubId(phoneId);
        Integer subIdObj = subId;
        if (subIdObj == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return subId;
    }
}
