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

/**
 * Call fail causes from TS 24.008 .
 * These are mostly the cause codes we need to distinguish for the UI.
 * See 22.001 Annex F.4 for mapping of cause codes to local tones.
 *
 * CDMA call failure reasons are derived from the possible call failure scenarios described
 * in "CDMA IS2000 - Release A (C.S0005-A v6.0)" standard.
 *
 * {@hide}
 *
 */
public interface CallFailCause {
    // Unassigned/Unobtainable number
    int UNOBTAINABLE_NUMBER = 1;

    int NORMAL_CLEARING     = 16;
    // Busy Tone
    int USER_BUSY           = 17;

    // No Tone
    int NUMBER_CHANGED      = 22;
    int STATUS_ENQUIRY      = 30;
    int NORMAL_UNSPECIFIED  = 31;

    // Congestion Tone
    int NO_CIRCUIT_AVAIL    = 34;
    int TEMPORARY_FAILURE   = 41;
    int SWITCHING_CONGESTION    = 42;
    int CHANNEL_NOT_AVAIL   = 44;
    int QOS_NOT_AVAIL       = 49;
    int BEARER_NOT_AVAIL    = 58;

    // others
    int ACM_LIMIT_EXCEEDED = 68;
    int CALL_BARRED        = 240;
    int FDN_BLOCKED        = 241;

    // Stk Call Control
    int DIAL_MODIFIED_TO_USSD = 244;
    int DIAL_MODIFIED_TO_SS   = 245;
    int DIAL_MODIFIED_TO_DIAL = 246;

    // Supplementary
    int NO_ROUTE_TO_DESTINAON = 3;
    int CHANNEL_UNACCEPTABLE = 6;
    int OPERATOR_DETERMINED_BARRING = 8;
    int CALL_FAIL_NO_USER_RESPONDING = 18;
    int CALL_FAIL_NO_ANSWER_FROM_USER = 19;
    int CALL_REJECTED = 21;
    int PREEMPTION = 25;
    int NON_SELECTED_USER_CLEARING = 26;
    int CALL_FAIL_DESTINATION_OUT_OF_ORDER = 27;
    int INVALID_NUMBER = 28;
    int FACILITY_REJECTED = 29;
    int NETWORK_OUT_OF_ORDER = 38;
    int ACCESS_INFORMATION_DISCARDED = 43;
    int RESOURCES_UNAVAILABLE_OR_UNSPECIFIED = 47;
    int REQUESTED_FACILITY_NOT_SUBSCRIBED = 50;
    int INCOMING_CALLS_BARRED_WITHIN_CUG = 55;
    int BEARER_CAPABILITY_NOT_AUTHORIZED = 57;
    int SERVICE_OPTION_NOT_AVAILABLE = 63;
    int BEARER_SERVICE_NOT_IMPLEMENTED = 65;
    int REQUESTED_FACILITY_NOT_IMPLEMENTED = 69;
    int ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE = 70;
    int SERVICE_OR_OPTION_NOT_IMPLEMENTED = 79;
    int INVALID_TRANSACTION_IDENTIFIER = 81;
    int USER_NOT_MEMBER_OF_CUG = 87;
    int INCOMPATIBLE_DESTINATION = 88;
    int INVALID_TRANSIT_NW_SELECTION = 91;
    int SEMANTICALLY_INCORRECT_MESSAGE = 95;
    int INVALID_MANDATORY_INFORMATION = 96;
    int MESSAGE_TYPE_NON_IMPLEMENTED = 97;
    int MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 98;
    int INFORMATION_ELEMENT_NON_EXISTENT = 99;
    int CONDITIONAL_IE_ERROR = 100;
    int MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 101;
    int RECOVERY_ON_TIMER_EXPIRED = 102;
    int PROTOCOL_ERROR_UNSPECIFIED = 111;
    int INTERWORKING_UNSPECIFIED = 127;

    //Emergency Redial
    int EMERGENCY_TEMP_FAILURE = 325;
    int EMERGENCY_PERM_FAILURE = 326;

    int CDMA_LOCKED_UNTIL_POWER_CYCLE  = 1000;
    int CDMA_DROP                      = 1001;
    int CDMA_INTERCEPT                 = 1002;
    int CDMA_REORDER                   = 1003;
    int CDMA_SO_REJECT                 = 1004;
    int CDMA_RETRY_ORDER               = 1005;
    int CDMA_ACCESS_FAILURE            = 1006;
    int CDMA_PREEMPTED                 = 1007;

    // For non-emergency number dialed while in emergency callback mode.
    int CDMA_NOT_EMERGENCY             = 1008;

    // Access Blocked by CDMA Network.
    int CDMA_ACCESS_BLOCKED            = 1009;

    int ERROR_UNSPECIFIED = 0xffff;

}
