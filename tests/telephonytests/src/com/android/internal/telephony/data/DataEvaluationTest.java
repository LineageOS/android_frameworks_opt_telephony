/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.data;

import com.android.internal.telephony.TelephonyTest;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DataEvaluationTest extends TelephonyTest {
    DataEvaluation mDataEvaluationUT;
    @Before
    public void setUp() throws Exception {
        logd("DataEvaluationTest +Setup!");
        super.setUp(getClass().getSimpleName());
        logd("DataEvaluationTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        mDataEvaluationUT = null;
        super.tearDown();
    }

    @Test
    public void testModifyDataDisallowedReasonSet() {
        mDataEvaluationUT = new DataEvaluation(DataEvaluation.DataEvaluationReason.DATA_RETRY);
        mDataEvaluationUT.addDataDisallowedReason(
                DataEvaluation.DataDisallowedReason.DATA_DISABLED);
        mDataEvaluationUT.addDataDisallowedReason(
                DataEvaluation.DataDisallowedReason.ROAMING_DISABLED);

        assertThat(mDataEvaluationUT.getDataDisallowedReasons().size()).isEqualTo(2);

        //remove nonexistent disallowed reason
        mDataEvaluationUT.removeDataDisallowedReason(
                DataEvaluation.DataDisallowedReason.DEFAULT_DATA_UNSELECTED);
        assertThat(mDataEvaluationUT.getDataDisallowedReasons().size()).isEqualTo(2);

        mDataEvaluationUT.removeDataDisallowedReason(
                DataEvaluation.DataDisallowedReason.DATA_DISABLED);
        assertThat(mDataEvaluationUT.getDataDisallowedReasons().size()).isEqualTo(1);
    }
}
