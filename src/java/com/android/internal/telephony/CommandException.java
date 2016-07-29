/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.internal.telephony.RILConstants;

import android.telephony.Rlog;

/**
 * {@hide}
 */
public class CommandException extends RuntimeException {
    private Error mError;

    public enum Error {
        INVALID_RESPONSE,
        RADIO_NOT_AVAILABLE,
        GENERIC_FAILURE,
        PASSWORD_INCORRECT,
        SIM_PIN2,
        SIM_PUK2,
        REQUEST_NOT_SUPPORTED,
        OP_NOT_ALLOWED_DURING_VOICE_CALL,
        OP_NOT_ALLOWED_BEFORE_REG_NW,
        SMS_FAIL_RETRY,
        SIM_ABSENT,
        SUBSCRIPTION_NOT_AVAILABLE,
        MODE_NOT_SUPPORTED,
        FDN_CHECK_FAILURE,
        ILLEGAL_SIM_OR_ME,
        MISSING_RESOURCE,
        NO_SUCH_ELEMENT,
        INVALID_PARAMETER,
        SUBSCRIPTION_NOT_SUPPORTED,
        DIAL_MODIFIED_TO_USSD,
        DIAL_MODIFIED_TO_SS,
        DIAL_MODIFIED_TO_DIAL,
        USSD_MODIFIED_TO_DIAL,
        USSD_MODIFIED_TO_SS,
        USSD_MODIFIED_TO_USSD,
        SS_MODIFIED_TO_DIAL,
        SS_MODIFIED_TO_USSD,
        SS_MODIFIED_TO_SS,
        // MTK
        DIAL_STRING_TOO_LONG,
        TEXT_STRING_TOO_LONG,
        SIM_MEM_FULL,
        /* M: SS part */
        CALL_BARRED,
        UT_XCAP_403_FORBIDDEN,
        UT_UNKNOWN_HOST,
        /* M: SS part end */

        BT_SAP_UNDEFINED,
        BT_SAP_NOT_ACCESSIBLE,
        BT_SAP_CARD_REMOVED,

        ADDITIONAL_NUMBER_STRING_TOO_LONG,
        ADDITIONAL_NUMBER_SAVE_FAILURE,
        ADN_LIST_NOT_EXIST,
        EMAIL_SIZE_LIMIT,
        EMAIL_NAME_TOOLONG,
        NOT_READY,
        /// M: SS Ut part
        SPECAIL_UT_COMMAND_NOT_SUPPORTED,

        /// M: ALPS02147333 call control part
        CC_CALL_HOLD_FAILED_CAUSED_BY_TERMINATED,
    }

    public CommandException(Error e) {
        super(e.toString());
        mError = e;
    }

    public CommandException(Error e, String errString) {
        super(errString);
        mError = e;
    }

    public static CommandException
    fromRilErrno(int ril_errno) {
        switch(ril_errno) {
            case RILConstants.SUCCESS:                       return null;
            case RILConstants.RIL_ERRNO_INVALID_RESPONSE:
                return new CommandException(Error.INVALID_RESPONSE);
            case RILConstants.RADIO_NOT_AVAILABLE:
                return new CommandException(Error.RADIO_NOT_AVAILABLE);
            case RILConstants.GENERIC_FAILURE:
                return new CommandException(Error.GENERIC_FAILURE);
            case RILConstants.PASSWORD_INCORRECT:
                return new CommandException(Error.PASSWORD_INCORRECT);
            case RILConstants.SIM_PIN2:
                return new CommandException(Error.SIM_PIN2);
            case RILConstants.SIM_PUK2:
                return new CommandException(Error.SIM_PUK2);
            case RILConstants.REQUEST_NOT_SUPPORTED:
                return new CommandException(Error.REQUEST_NOT_SUPPORTED);
            case RILConstants.OP_NOT_ALLOWED_DURING_VOICE_CALL:
                return new CommandException(Error.OP_NOT_ALLOWED_DURING_VOICE_CALL);
            case RILConstants.OP_NOT_ALLOWED_BEFORE_REG_NW:
                return new CommandException(Error.OP_NOT_ALLOWED_BEFORE_REG_NW);
            case RILConstants.SMS_SEND_FAIL_RETRY:
                return new CommandException(Error.SMS_FAIL_RETRY);
            case RILConstants.SIM_ABSENT:
                return new CommandException(Error.SIM_ABSENT);
            case RILConstants.SUBSCRIPTION_NOT_AVAILABLE:
                return new CommandException(Error.SUBSCRIPTION_NOT_AVAILABLE);
            case RILConstants.MODE_NOT_SUPPORTED:
                return new CommandException(Error.MODE_NOT_SUPPORTED);
            case RILConstants.FDN_CHECK_FAILURE:
                return new CommandException(Error.FDN_CHECK_FAILURE);
            case RILConstants.ILLEGAL_SIM_OR_ME:
                return new CommandException(Error.ILLEGAL_SIM_OR_ME);
            case RILConstants.MISSING_RESOURCE:
                return new CommandException(Error.MISSING_RESOURCE);
            case RILConstants.NO_SUCH_ELEMENT:
                return new CommandException(Error.NO_SUCH_ELEMENT);
            case RILConstants.INVALID_PARAMETER:
                 return new CommandException(Error.INVALID_PARAMETER);
            case RILConstants.SUBSCRIPTION_NOT_SUPPORTED:
                return new CommandException(Error.SUBSCRIPTION_NOT_SUPPORTED);
            case RILConstants.DIAL_MODIFIED_TO_USSD:
                return new CommandException(Error.DIAL_MODIFIED_TO_USSD);
            case RILConstants.DIAL_MODIFIED_TO_SS:
                return new CommandException(Error.DIAL_MODIFIED_TO_SS);
            case RILConstants.DIAL_MODIFIED_TO_DIAL:
                return new CommandException(Error.DIAL_MODIFIED_TO_DIAL);
            case RILConstants.USSD_MODIFIED_TO_DIAL:
                return new CommandException(Error.USSD_MODIFIED_TO_DIAL);
            case RILConstants.USSD_MODIFIED_TO_SS:
                return new CommandException(Error.USSD_MODIFIED_TO_SS);
            case RILConstants.USSD_MODIFIED_TO_USSD:
                return new CommandException(Error.USSD_MODIFIED_TO_USSD);
            case RILConstants.SS_MODIFIED_TO_DIAL:
                return new CommandException(Error.SS_MODIFIED_TO_DIAL);
            case RILConstants.SS_MODIFIED_TO_USSD:
                return new CommandException(Error.SS_MODIFIED_TO_USSD);
            case RILConstants.SS_MODIFIED_TO_SS:
                return new CommandException(Error.SS_MODIFIED_TO_SS);
            // MTK
            case RILConstants.DIAL_STRING_TOO_LONG:
                return new CommandException(Error.DIAL_STRING_TOO_LONG);
            case RILConstants.TEXT_STRING_TOO_LONG:
                return new CommandException(Error.TEXT_STRING_TOO_LONG);
            case RILConstants.SIM_MEM_FULL:
                return new CommandException(Error.SIM_MEM_FULL);
            case RILConstants.ADDITIONAL_NUMBER_STRING_TOO_LONG:
                return new CommandException(Error.ADDITIONAL_NUMBER_STRING_TOO_LONG);
            case RILConstants.ADDITIONAL_NUMBER_SAVE_FAILURE:
                return new CommandException(Error.ADDITIONAL_NUMBER_SAVE_FAILURE);
            case RILConstants.ADN_LIST_NOT_EXIST:
                return new CommandException(Error.ADN_LIST_NOT_EXIST);
            case RILConstants.EMAIL_SIZE_LIMIT:
                return new CommandException(Error.EMAIL_SIZE_LIMIT);
            case RILConstants.EMAIL_NAME_TOOLONG:
                return new CommandException(Error.EMAIL_NAME_TOOLONG);
            case RILConstants.BT_SAP_UNDEFINED:
                return new CommandException(Error.BT_SAP_UNDEFINED);
            case RILConstants.BT_SAP_NOT_ACCESSIBLE:
                return new CommandException(Error.BT_SAP_NOT_ACCESSIBLE);
            case RILConstants.BT_SAP_CARD_REMOVED:
                return new CommandException(Error.BT_SAP_CARD_REMOVED);
            /// M: ALPS02147333 Hold call failed caused by call terminated
            case RILConstants.CC_CALL_HOLD_FAILED_CAUSED_BY_TERMINATED:
                return new CommandException(Error.CC_CALL_HOLD_FAILED_CAUSED_BY_TERMINATED);
            default:
                Rlog.e("GSM", "Unrecognized RIL errno " + ril_errno);
                return new CommandException(Error.INVALID_RESPONSE);
        }
    }

    public Error getCommandError() {
        return mError;
    }



}
