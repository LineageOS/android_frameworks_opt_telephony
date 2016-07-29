/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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
 */

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

package com.mediatek.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Entry of preferred network list in UICC card. ex: EF_PLMNSEL
 * {@hide}
 * @internal
 */
public class NetworkInfoWithAcT implements Parcelable {

    String operatorAlphaName;
    String operatorNumeric;

    int nAct;
    int nPriority; // priority is the index of the plmn in the list.


/**
 * Get Operator alpha name ex: Vodafone
 * @internal
 */
    public String
    getOperatorAlphaName() {
        return operatorAlphaName;
    }

/**
 * Get member Operator PLMN ID ex: 53001
 * @internal
 */
    public String
    getOperatorNumeric() {
        return operatorNumeric;
    }

/**
 * Get access techonolgy of the PLMN. It's a bitmap value.  <bit3, bit2,bit1,bit0>  =>  < E-UTRAN_Act ,UTRAN_Act,GSM_Compact_Act ,Gsm_Act >
 * @internal
 */
    public int
    getAccessTechnology() {
        return nAct;
    }

/**
 * Get priority, index of the PLMN in the list
 * @internal
 */
    public int
    getPriority() {
        return nPriority;
    }

/**
 * Set Operator alpha name ex: Vodafone
 *
 * @internal
 */
    public void
    setOperatorAlphaName(String operatorAlphaName) {
        this.operatorAlphaName = operatorAlphaName;
    }

/**
 * Set member Operator PLMN ID ex: 53001
 * @internal
 */
    public void
    setOperatorNumeric(String operatorNumeric) {
        this.operatorNumeric = operatorNumeric;
    }

/**
 * Set access techonolgy of the PLMN. It's a bitmap value.  <bit3, bit2,bit1,bit0>  =>  < E-UTRAN_Act ,UTRAN_Act,GSM_Compact_Act ,Gsm_Act >
 *
 * @internal
 */
    public void
    setAccessTechnology(int nAct) {
        this.nAct = nAct;
    }

/**
 * Set priority, index of the PLMN in the list
 * @internal
 */
    public void
    setPriority(int nIndex) {
        this.nPriority = nIndex;
    }

    public NetworkInfoWithAcT(String operatorAlphaLong,
                String operatorNumeric,
                int nAct,
                int nPriority) {

        this.operatorAlphaName = operatorAlphaLong;
        this.operatorNumeric = operatorNumeric;
        this.nAct = nAct;
        this.nPriority = nPriority;
    }

    public String toString() {
        return "NetworkInfoWithAcT " + operatorAlphaName
                + "/" + operatorNumeric
                + "/" + nAct
                + "/" + nPriority;
    }

    /**
     * Parcelable interface implemented below.
     * This is a simple effort to make NetworkInfo parcelable rather than
     * trying to make the conventional containing object (AsyncResult),
     * implement parcelable.  This functionality is needed for the
     * NetworkQueryService to fix 1128695.
     */

    public int describeContents() {
        return 0;
    }

    /**
     * Implement the Parcelable interface.
     * Method to serialize a NetworkInfo object.
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(operatorAlphaName);
        dest.writeString(operatorNumeric);
        dest.writeInt(nAct);
        dest.writeInt(nPriority);
    }

    /**
     * Implement the Parcelable interface
     * Method to deserialize a NetworkInfo object, or an array thereof.
     */
    public static final Creator<NetworkInfoWithAcT> CREATOR =
        new Creator<NetworkInfoWithAcT>() {
            public NetworkInfoWithAcT createFromParcel(Parcel in) {
                NetworkInfoWithAcT netInfoWithAct = new NetworkInfoWithAcT(
                        in.readString(), /*operatorAlphaLong*/
                        in.readString(), /*operatorNumeric*/
                        in.readInt(), /*operatorNumeric*/
                        in.readInt()); /*state*/
                return netInfoWithAct;
            }

            public NetworkInfoWithAcT[] newArray(int size) {
                return new NetworkInfoWithAcT[size];
            }
        };
}
