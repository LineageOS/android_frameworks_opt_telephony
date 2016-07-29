/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.mediatek.telephony;

/**
 * @hide
 */
public class ExternalSimConstants {
    // External SIM to platform
    public static final int MSG_ID_INITIALIZATION_REQUEST           = 1;
    public static final int MSG_ID_GET_PLATFORM_CAPABILITY_REQUEST  = 2;
    public static final int MSG_ID_EVENT_REQUEST                    = 3;
    public static final int MSG_ID_UICC_RESET_RESPONSE              = 4;
    public static final int MSG_ID_UICC_APDU_RESPONSE               = 5;
    public static final int MSG_ID_UICC_POWER_DOWN_RESPONSE         = 6;
    public static final int MSG_ID_GET_SERVICE_STATE_REQUEST        = 7;
    public static final int MSG_ID_FINALIZATION_REQUEST             = 8;

    // Type id of MSG_ID_EVENT_REQUEST event
    public static final int REQUEST_TYPE_ENABLE_EXTERNAL_SIM        = 1;
    public static final int REQUEST_TYPE_DISABLE_EXTERNAL_SIM       = 2;
    public static final int REQUEST_TYPE_PLUG_OUT                   = 3;
    public static final int REQUEST_TYPE_PLUG_IN                    = 4;

    // SIM type
    public static final int SIM_TYPE_LOCAL_SIM  = 1;
    public static final int SIM_TYPE_REMOTE_SIM = 2;

    // Response result
    public static final int RESPONSE_RESULT_OK                  = 0;
    public static final int RESPONSE_RESULT_GENERIC_ERROR       = -1;
    public static final int RESPONSE_RESULT_PLATFORM_NOT_READY  = -2;

    // Platform to external SIM
    public static final int MSG_ID_INITIALIZATION_RESPONSE          = 1001;
    public static final int MSG_ID_GET_PLATFORM_CAPABILITY_RESPONSE = 1002;
    public static final int MSG_ID_EVENT_RESPONSE                   = 1003;
    public static final int MSG_ID_UICC_RESET_REQUEST               = 1004;
    public static final int MSG_ID_UICC_APDU_REQUEST                = 1005;
    public static final int MSG_ID_UICC_POWER_DOWN_REQUEST          = 1006;
    public static final int MSG_ID_GET_SERVICE_STATE_RESPONSE       = 1007;
    public static final int MSG_ID_FINALIZATION_RESPONSE            = 1008;

    // Platfrom capability parameters
    public static final int MULTISIM_CONFIG_UNKNOWN = 0;
    public static final int MULTISIM_CONFIG_DSDS = 1;
    public static final int MULTISIM_CONFIG_DSDA = 2;
    public static final int MULTISIM_CONFIG_TSTS = 3;
}


