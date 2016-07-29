/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */


package com.mediatek.internal.telephony;

/**
 * To pass IMS call context to CS modem.
 * {@hide}
 */
public class SrvccCallContext {
    /* Between 1 and 7 */
    private int mCallId;

    /*
       0: voice call
       1: video call
       2: emergency call
       3: voice conference call
       other values are reserved for future extend
     */
    private int mCallMode;

    /*
       0: MO call
       1: MT call
     */
    private int mCallDirection;

    /*
       0: Early
       1: Early with media (already attached speech in Alerting state)
       2: Active
       3: On Hold
     */
    private int mCallState;

    /*
       0x0000: unspecified
       0x0001: police
       0x0002: ambulance
       0x0004: fire brigade
       0x0008: marine guard
       0x0010: mountain rescue
       0x0020: gas
       0x0040: animal
       0x0080: physician
       0x0100: poison
       0xFFFF: not emergency call
     */
    private int mEccCategory;



    /*
      1: URI format
      2: BCD format
    */
    private int mNumberType;

    private String mNumber;

    private String mName;

    /*
      <CLI validity>: integer type. This parameter can provide details why <number>
      does not contain a calling party BCD number (refer 3GPP TS 24.008 [8] subclause
      10.5.4.30). The parameter is not present for MO call
      types.
      0 CLI valid
      1 CLI has been withheld by the originator (refer 3GPP TS 24.008 [8] table 10.5.135a/
        3GPP TS 24.008 code "Reject by user")
      2 CLI is not available due to interworking problems or limitations of originating
        network (refer 3GPP TS 24.008 [8] table 10.5.135a/3GPP TS 24.008 code
        "Interaction with other service")
      3 CLI is not available due to calling party being of type payphone
        (refer 3GPP TS 24.008 [8] table 10.5.135a/3GPP TS 24.008 code "Coin line/payphone")
      4 CLI is not available due to other reasons (refer 3GPP TS 24.008 [8] table 10.5.135a/
        3GPP TS 24.008 code "Unavailable")    */
    private int mCliValidity;

    /**
     * Constructor function.
     *
     * @param callId call id.
     * @param callMode call mode.
     * @param callDirection call direction.
     * @param callState call state.
     * @param eccCategory emergency category.
     * @param numberType number type.
     * @param phoneNumber phone number.
     * @param name Name information.
     * @param cliValidity CLI validity.
     *
     *
     */
    public SrvccCallContext(int callId,
                            int callMode,
                            int callDirection,
                            int callState,
                            int eccCategory,
                            int numberType,
                            String phoneNumber,
                            String name,
                            int cliValidity) {
        mCallId = callId;
        mCallMode = callMode;
        mCallDirection = callDirection;
        mCallState = callState;
        mEccCategory = eccCategory;
        mNumberType = numberType;
        mNumber = phoneNumber;
        mName = name;
        mCliValidity = cliValidity;
    }

    /**
     * Set call id.
     *
     * @param callId call id.
     *
     */
    public void setCallId(int callId) {
          mCallId = callId;
    }

    /**
     * Set call mode.
     *
     * @param callMode call mode.
     *
     */
    public void setCallMode(int callMode) {
          mCallMode = callMode;
    }

    /**
     * Set call direction.
     *
     * @param callDirection call direction.
     *
     */
    public void setCallDirection(int callDirection) {
          mCallDirection = callDirection;
    }

    /**
     * Set call state.
     *
     * @param callState call state.
     *
     */
    public void setCallState(int callState) {
          mCallState = callState;
    }

    /**
     * Set emergency category.
     *
     * @param eccCategory emergency category.
     *
     */
    public void setEccCategory(int eccCategory) {
          mEccCategory = eccCategory;
    }

    /**
     * Set number type.
     *
     * @param numberType number type.
     *
     */
    public void setNumberType(int numberType) {
          mNumberType = numberType;
    }

    /**
     * Set number.
     *
     * @param phoneNumber phone number.
     *
     */
    public void setCallState(String phoneNumber) {
          mNumber = phoneNumber;
    }

    /**
     * Set name.
     *
     * @param name Name information.
     *
     */
    public void setName(String name) {
          mName = name;
    }

    /**
     * Set CLI validity.
     *
     * @param cliValidity CLI validity.
     *
     */
    public void setCliValidity(int cliValidity) {
          mCliValidity = cliValidity;
    }

    /**
     * Retrieve call id.
     *
     * @return call id.
     *
     */
    public int getCallId() {
          return mCallId;
    }

    /**
     * Retrieve call mode.
     *
     * @return call mode.
     *
     */
    public int getCallMode() {
          return mCallMode;
    }

    /**
     * Retrieve call direction.
     *
     * @return call direction.
     *
     */
    public int getCallDirection() {
          return mCallDirection;
    }

    /**
     * Retrieve call state.
     *
     * @return call state.
     *
     */
    public int getCallState() {
          return mCallState;
    }

    /**
     * Retrieve emergency category.
     *
     * @return emergency category.
     *
     */
    public int getEccCategory() {
          return mEccCategory;
    }

    /**
     * Retrieve number type.
     *
     * @return number type.
     *
     */
    public int getNumberType() {
          return mNumberType;
    }

    /**
     * Retrieve phone number.
     *
     * @return phone number.
     *
     */
    public String getNumber() {
          return mNumber;
    }

    /**
     * Retrieve name.
     *
     * @return name.
     *
     */
    public String getName() {
          return mName;
    }

    /**
     * Retrieve CLI validity.
     *
     * @return CLI validity.
     *
     */
    public int getCliValidity() {
          return mCliValidity;
    }
}