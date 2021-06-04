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

import static junit.framework.Assert.assertEquals;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.data.ApnSetting;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ApnConfigTypeRepositoryTest {

    PersistableBundle mCarrierConfig;

    @Before
    public void setUp() throws Exception {
        mCarrierConfig = new PersistableBundle();
    }

    @Test
    public void testReturnsDefaultsWhenCarrierConfigNull() {
        ApnConfigTypeRepository repository = new ApnConfigTypeRepository(null);
        checkDefaults(repository);
    }

    @Test
    public void testReturnsDefaultsWhenCarrierConfigApnContextKeyReturnsNull() {
        mCarrierConfig.putStringArray(CarrierConfigManager.KEY_APN_PRIORITY_STRING_ARRAY,
                null);

        ApnConfigTypeRepository repository = new ApnConfigTypeRepository(mCarrierConfig);
        checkDefaults(repository);
    }

    @Test
    public void testReturnsDefaultsWhenCarrierConfigHasInvalidTypes() {

        List<String> apnConfigStringArray = new ArrayList<>();
        apnConfigStringArray.add("xcap,cbs:3");
        apnConfigStringArray.add("default:0a");

        mCarrierConfig.putStringArray(CarrierConfigManager.KEY_APN_PRIORITY_STRING_ARRAY,
                apnConfigStringArray.toArray(new String[0]));

        ApnConfigTypeRepository repository = new ApnConfigTypeRepository(mCarrierConfig);
        checkDefaults(repository);
    }

    @Test
    public void testReturnsCarrierConfigOverride() {
        List<String> apnConfigStringArray = new ArrayList<>();
        //Shouldn't match or override any keys
        apnConfigStringArray.add("xcap,cbs:3");

        //Priorities must be integers
        apnConfigStringArray.add("default:10a");

        //Key isn't case sensitive, which means that this priority should be taken
        apnConfigStringArray.add("fotA:10");

        mCarrierConfig.putStringArray(CarrierConfigManager.KEY_APN_PRIORITY_STRING_ARRAY,
                apnConfigStringArray.toArray(new String[0]));

        ApnConfigTypeRepository repository = new ApnConfigTypeRepository(mCarrierConfig);
        assertEquals(10, repository.getByType(ApnSetting.TYPE_FOTA).getPriority());
        checkDefaults(repository);
    }

    private void checkDefaults(ApnConfigTypeRepository repository) {
        assertEquals(0, repository.getByType(ApnSetting.TYPE_ENTERPRISE).getPriority());
        assertEquals(1, repository.getByType(ApnSetting.TYPE_DEFAULT).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_MMS).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_SUPL).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_DUN).getPriority());
        assertEquals(3, repository.getByType(ApnSetting.TYPE_HIPRI).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_IMS).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_CBS).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_IA).getPriority());
        assertEquals(2, repository.getByType(ApnSetting.TYPE_EMERGENCY).getPriority());
        assertEquals(3, repository.getByType(ApnSetting.TYPE_MCX).getPriority());
        assertEquals(3, repository.getByType(ApnSetting.TYPE_XCAP).getPriority());
    }
}
