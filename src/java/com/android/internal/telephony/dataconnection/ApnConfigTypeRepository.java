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

package com.android.internal.telephony.dataconnection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.telephony.Annotation;
import android.telephony.data.ApnSetting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Hard coded configuration of specific network types that the telephony module needs.
 * Formerly stored in network attributes within the resources file.
 */
public final class ApnConfigTypeRepository {

    private static final ApnConfigTypeRepository sDefault = new ApnConfigTypeRepository();

    private final Map<Integer, ApnConfigType> mConfigTypeMap;

    ApnConfigTypeRepository() {
        mConfigTypeMap = new HashMap<>();
        setup();
    }

    /**
     * Gets the default instance of the repository.
     * @return The singleton instance of repository.
     */
    @NonNull
    public static ApnConfigTypeRepository getDefault() {
        return sDefault;
    }

    /**
     * Gets list of apn config types.
     * @return All apn config types.
     */
    public Collection<ApnConfigType> getTypes() {
        return mConfigTypeMap.values();
    }

    /**
     * Gets the apn config type by apn type.
     * @param type The ApnType to search for.
     * @return The config type matching the given apn type.
     */
    @Nullable
    public ApnConfigType getByType(@Annotation.ApnType int type) {
        return mConfigTypeMap.get(type);
    }

    private void setup() {
        add(ApnSetting.TYPE_DEFAULT, 0);
        add(ApnSetting.TYPE_MMS, 2);
        add(ApnSetting.TYPE_SUPL, 2);
        add(ApnSetting.TYPE_DUN, 2);
        add(ApnSetting.TYPE_HIPRI, 3);
        add(ApnSetting.TYPE_FOTA, 2);
        add(ApnSetting.TYPE_IMS, 2);
        add(ApnSetting.TYPE_CBS, 2);
        add(ApnSetting.TYPE_IA, 2);
        add(ApnSetting.TYPE_EMERGENCY, 2);
        add(ApnSetting.TYPE_MCX, 3);
        add(ApnSetting.TYPE_XCAP, 3);
    }

    private void add(@Annotation.ApnType int type, int priority) {
        mConfigTypeMap.put(type, new ApnConfigType(type, priority));
    }
}
