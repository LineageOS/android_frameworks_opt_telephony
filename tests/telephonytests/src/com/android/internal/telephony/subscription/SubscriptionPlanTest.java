/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.subscription;

import static android.telephony.SubscriptionPlan.SUBSCRIPTION_STATUS_ACTIVE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telephony.SubscriptionPlan;
import android.testing.AndroidTestingRunner;

import com.android.internal.telephony.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Period;
import java.time.ZonedDateTime;

@RunWith(AndroidTestingRunner.class)
public class SubscriptionPlanTest {
    private static final ZonedDateTime ZONED_DATE_TIME_START =
            ZonedDateTime.parse("2007-03-14T00:00:00.000Z");

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public void testBuilderExpirationDateSetsCorrectly() {
        ZonedDateTime endDate = ZonedDateTime.parse("2024-11-07T00:00:00.000Z");

        SubscriptionPlan planNonRecurring = SubscriptionPlan.Builder
                .createNonrecurring(ZONED_DATE_TIME_START, endDate)
                .setTitle("unit test")
                .build();
        SubscriptionPlan planRecurring = SubscriptionPlan.Builder
                .createRecurring(ZONED_DATE_TIME_START, Period.ofMonths(1))
                .setTitle("unit test")
                .build();

        assertThat(planNonRecurring.getPlanEndDate()).isEqualTo(endDate);
        assertNull(planRecurring.getPlanEndDate());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public void testBuilderValidSubscriptionStatusSetsCorrectly() {
        @SubscriptionPlan.SubscriptionStatus int status = SUBSCRIPTION_STATUS_ACTIVE;

        SubscriptionPlan plan = SubscriptionPlan.Builder
                .createRecurring(ZONED_DATE_TIME_START, Period.ofMonths(1))
                .setSubscriptionStatus(status)
                .setTitle("unit test")
                .build();

        assertThat(plan.getSubscriptionStatus()).isEqualTo(status);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public void testBuilderInvalidSubscriptionStatusThrowsError() {
        int minInvalid = -1;
        int maxInvalid = 5;

        assertThrows(IllegalArgumentException.class, () -> {
            SubscriptionPlan.Builder
                    .createRecurring(ZONED_DATE_TIME_START, Period.ofMonths(1))
                    .setSubscriptionStatus(minInvalid)
                    .setTitle("unit test")
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            SubscriptionPlan.Builder
                    .createRecurring(ZONED_DATE_TIME_START, Period.ofMonths(1))
                    .setSubscriptionStatus(maxInvalid)
                    .setTitle("unit test")
                    .build();
        });
    }
}
