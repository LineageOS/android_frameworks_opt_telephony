/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.MccTable.MccMnc;
import com.android.internal.telephony.util.LocaleUtils;

import org.junit.Test;

import java.util.Locale;

public class MccTableTest {

    @SmallTest
    @Test
    public void testCountryCodeForMcc() throws Exception {
        checkMccLookupWithNoMnc("lu", 270);
        checkMccLookupWithNoMnc("gr", 202);
        checkMccLookupWithNoMnc("fk", 750);
        checkMccLookupWithNoMnc("mg", 646);
        checkMccLookupWithNoMnc("us", 314);
        checkMccLookupWithNoMnc("", 300);  // mcc not defined, hence default
        checkMccLookupWithNoMnc("", 0);    // mcc not defined, hence default
        checkMccLookupWithNoMnc("", 2000); // mcc not defined, hence default
    }

    private void checkMccLookupWithNoMnc(String expectedCountryIsoCode, int mcc) {
        assertEquals(expectedCountryIsoCode, MccTable.countryCodeForMcc(mcc));
        assertEquals(expectedCountryIsoCode, MccTable.countryCodeForMcc(mcc));
        assertEquals(expectedCountryIsoCode, MccTable.countryCodeForMcc("" + mcc));
        assertEquals(expectedCountryIsoCode,
                MccTable.geoCountryCodeForMccMnc(new MccMnc("" + mcc, "999")));
    }

    @SmallTest
    @Test
    public void testGeoCountryCodeForMccMnc() throws Exception {
        // This test is possibly fragile as this data is configurable.
        assertEquals("gu", MccTable.geoCountryCodeForMccMnc(new MccMnc("310", "370")));
    }

    @SmallTest
    @Test
    public void testLang() throws Exception {
        assertEquals("en", LocaleUtils.defaultLanguageForMcc(311));
        assertEquals("de", LocaleUtils.defaultLanguageForMcc(232));
        assertEquals("cs", LocaleUtils.defaultLanguageForMcc(230));
        assertEquals("nl", LocaleUtils.defaultLanguageForMcc(204));
        assertEquals("is", LocaleUtils.defaultLanguageForMcc(274));
        // mcc not defined, hence default
        assertEquals(null, LocaleUtils.defaultLanguageForMcc(0));
        // mcc not defined, hence default
        assertEquals(null, LocaleUtils.defaultLanguageForMcc(2000));
    }

    @SmallTest
    @Test
    public void testLang_India() throws Exception {
        assertEquals("en", LocaleUtils.defaultLanguageForMcc(404));
        assertEquals("en", LocaleUtils.defaultLanguageForMcc(405));
        assertEquals("en", LocaleUtils.defaultLanguageForMcc(406));
    }

    @SmallTest
    @Test
    public void testLocale() throws Exception {
        assertEquals(Locale.forLanguageTag("en-CA"),
                LocaleUtils.getLocaleFromMcc(getContext(), 302, null));
        assertEquals(Locale.forLanguageTag("en-GB"),
                LocaleUtils.getLocaleFromMcc(getContext(), 234, null));
        assertEquals(Locale.forLanguageTag("en-US"),
                LocaleUtils.getLocaleFromMcc(getContext(), 0, "en"));
        assertEquals(Locale.forLanguageTag("zh-HK"),
                LocaleUtils.getLocaleFromMcc(getContext(), 454, null));
        assertEquals(Locale.forLanguageTag("en-HK"),
                LocaleUtils.getLocaleFromMcc(getContext(), 454, "en"));
        assertEquals(Locale.forLanguageTag("zh-TW"),
                LocaleUtils.getLocaleFromMcc(getContext(), 466, null));
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    @SmallTest
    @Test
    public void testSmDigits() throws Exception {
        assertEquals(3, MccTable.smallestDigitsMccForMnc(312));
        assertEquals(2, MccTable.smallestDigitsMccForMnc(430));
        assertEquals(3, MccTable.smallestDigitsMccForMnc(365));
        assertEquals(2, MccTable.smallestDigitsMccForMnc(536));
        // sd not defined, hence default
        assertEquals(2, MccTable.smallestDigitsMccForMnc(352));
        // mcc not defined, hence default
        assertEquals(2, MccTable.smallestDigitsMccForMnc(0));
        // mcc not defined, hence default
        assertEquals(2, MccTable.smallestDigitsMccForMnc(2000));
    }
}
