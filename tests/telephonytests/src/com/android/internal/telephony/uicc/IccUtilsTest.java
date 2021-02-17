/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class IccUtilsTest {
    private static final int NUM_FPLMN = 3;
    private static final List<String> FPLMNS_SAMPLE = Arrays.asList("123456", "12345", "54321");
    private static final int DATA_LENGTH = 12;
    private static final String EMOJI = new String(Character.toChars(0x1F642));

    @Test
    public void encodeFplmns() {
        byte[] encodedFplmns = IccUtils.encodeFplmns(FPLMNS_SAMPLE, DATA_LENGTH);
        int numValidPlmns = 0;
        for (int i = 0; i < NUM_FPLMN; i++) {
            String parsed = IccUtils.bcdPlmnToString(encodedFplmns, i * IccUtils.FPLMN_BYTE_SIZE);
            assertEquals(FPLMNS_SAMPLE.get(i), parsed);
            // we count the valid (non empty) records and only increment if valid
            if (!TextUtils.isEmpty(parsed)) numValidPlmns++;
        }
        assertEquals(NUM_FPLMN, numValidPlmns);
    }

    @Test
    public void stringToAdnStringField_gsmBasic() {
        String alphaTag = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz 1234567890"
                + "!@#$%&*()_+,.?;:<>";

        byte[] result = IccUtils.stringToAdnStringField(alphaTag);
        assertThat(result.length).isEqualTo(alphaTag.length());
        assertThat(IccUtils.adnStringFieldToString(result, 0, result.length))
                .isEqualTo(alphaTag);
    }

    @Test
    public void stringToAdnStringField_nonGsm() {
        String alphaTag = "日本";

        byte[] result = IccUtils.stringToAdnStringField(alphaTag);
        assertThat(result.length).isEqualTo(alphaTag.length() * 2 + 1);
        assertThat(result[0]).isEqualTo((byte) 0x80);
        assertThat(IccUtils.adnStringFieldToString(result, 0, result.length))
                .isEqualTo(alphaTag);
    }

    @Test
    public void stringToAdnStringField_mixed() {
        String alphaTag = "ni=日;hon=本;";

        byte[] result = IccUtils.stringToAdnStringField(alphaTag);
        assertThat(result.length).isEqualTo(alphaTag.length() * 2 + 1);
        assertThat(result[0]).isEqualTo((byte) 0x80);
        assertThat(IccUtils.adnStringFieldToString(result, 0, result.length))
                .isEqualTo(alphaTag);
    }

    @Test
    public void stringToAdnStringField_gsmWithEmoji() {
        String alphaTag = ":)=" + EMOJI + ";";

        byte[] result = IccUtils.stringToAdnStringField(alphaTag);
        assertThat(result.length).isEqualTo(alphaTag.length() * 2 + 1);
        assertThat(IccUtils.adnStringFieldToString(result, 0, result.length))
                .isEqualTo(alphaTag);
    }

    @Test
    public void stringToAdnStringField_mixedWithEmoji() {
        String alphaTag = "ni=日;hon=本;:)=" + EMOJI + ";";

        byte[] result = IccUtils.stringToAdnStringField(alphaTag);
        assertThat(result.length).isEqualTo(alphaTag.length() * 2 + 1);
        assertThat(result[0]).isEqualTo((byte) 0x80);
        assertThat(IccUtils.adnStringFieldToString(result, 0, result.length))
                .isEqualTo(alphaTag);
    }
}
