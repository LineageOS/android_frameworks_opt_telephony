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

package com.android.internal.telephony.gsm;

/**
 * Call fail causes from TS 24.008 .
 * These are mostly the cause codes we need to distinguish for the UI.
 * See 22.001 Annex F.4 for mapping of cause codes to local tones.
 *
 * {@hide}
 *
 */
public interface CallFailCause {
    // Unassigned/Unobtainable number
    static final int UNOBTAINABLE_NUMBER = 1;

    static final int NORMAL_CLEARING     = 16;
    // Busy Tone
    static final int USER_BUSY           = 17;

    // No Tone
    static final int NUMBER_CHANGED      = 22;
    static final int STATUS_ENQUIRY      = 30;
    static final int NORMAL_UNSPECIFIED  = 31;

    // Congestion Tone
    static final int NO_CIRCUIT_AVAIL    = 34;
    static final int TEMPORARY_FAILURE   = 41;
    static final int SWITCHING_CONGESTION    = 42;
    static final int CHANNEL_NOT_AVAIL   = 44;
    static final int QOS_NOT_AVAIL       = 49;
    static final int BEARER_NOT_AVAIL    = 58;

    // others
    static final int ACM_LIMIT_EXCEEDED = 68;
    static final int CALL_BARRED        = 240;
    static final int FDN_BLOCKED        = 241;

    // Stk Call Control
    static final int DIAL_MODIFIED_TO_USSD = 244;
    static final int DIAL_MODIFIED_TO_SS = 245;
    static final int DIAL_MODIFIED_TO_DIAL = 246;

    // Supplementary
    static final int NO_ROUTE_TO_DESTINAON = 3;
    static final int CHANNEL_UNACCEPTABLE = 6;
    static final int OPERATOR_DETERMINED_BARRING = 8;
    static final int CALL_FAIL_NO_USER_RESPONDING = 18;
    static final int CALL_FAIL_NO_ANSWER_FROM_USER = 19;
    static final int CALL_REJECTED = 21;
    static final int PREEMPTION = 25;
    static final int NON_SELECTED_USER_CLEARING = 26;
    static final int CALL_FAIL_DESTINATION_OUT_OF_ORDER = 27;
    static final int INVALID_NUMBER = 28;
    static final int FACILITY_REJECTED = 29;
    static final int NETWORK_OUT_OF_ORDER = 38;
    static final int ACCESS_INFORMATION_DISCARDED = 43;
    static final int RESOURCES_UNAVAILABLE_OR_UNSPECIFIED = 47;
    static final int REQUESTED_FACILITY_NOT_SUBSCRIBED = 50;
    static final int INCOMING_CALLS_BARRED_WITHIN_CUG = 55;
    static final int BEARER_CAPABILITY_NOT_AUTHORIZED = 57;
    static final int SERVICE_OPTION_NOT_AVAILABLE = 63;
    static final int BEARER_SERVICE_NOT_IMPLEMENTED = 65;
    static final int REQUESTED_FACILITY_NOT_IMPLEMENTED = 69;
    static final int ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE = 70;
    static final int SERVICE_OR_OPTION_NOT_IMPLEMENTED = 79;
    static final int INVALID_TRANSACTION_IDENTIFIER = 81;
    static final int USER_NOT_MEMBER_OF_CUG = 87;
    static final int INCOMPATIBLE_DESTINATION = 88;
    static final int INVALID_TRANSIT_NW_SELECTION = 91;
    static final int SEMANTICALLY_INCORRECT_MESSAGE = 95;
    static final int INVALID_MANDATORY_INFORMATION = 96;
    static final int MESSAGE_TYPE_NON_IMPLEMENTED = 97;
    static final int MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 98;
    static final int INFORMATION_ELEMENT_NON_EXISTENT = 99;
    static final int CONDITIONAL_IE_ERROR = 100;
    static final int MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 101;
    static final int RECOVERY_ON_TIMER_EXPIRED = 102;
    static final int PROTOCOL_ERROR_UNSPECIFIED = 111;
    static final int INTERWORKING_UNSPECIFIED = 127;

    //Emergency Redial
    static final int EMERGENCY_TEMP_FAILURE = 325;
    static final int EMERGENCY_PERM_FAILURE = 326;

    static final int ERROR_UNSPECIFIED = 0xffff;
}
