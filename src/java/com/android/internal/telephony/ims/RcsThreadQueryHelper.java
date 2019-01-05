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

import android.net.Uri;
import android.telephony.ims.RcsThreadQueryParameters;

/**
 * A helper class focused on querying RCS threads from the
 * {@link com.android.providers.telephony.RcsProvider}
 */
class RcsThreadQueryHelper {
    static final String ASCENDING = "ASCENDING";
    static final String DESCENDING = "DESCENDING";
    static final String THREAD_ID = "_id";

    static final Uri THREADS_URI = Uri.parse("content://rcs/thread");
    static final Uri PARTICIPANTS_URI = Uri.parse("content://rcs/participant");
    static final String[] THREAD_PROJECTION = new String[]{THREAD_ID};

    static String buildWhereClause(RcsThreadQueryParameters queryParameters) {
        // TODO - implement
        return null;
    }
}
