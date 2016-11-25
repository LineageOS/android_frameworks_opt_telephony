/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;


public class OperatorSimInfo {

    private final String GENERIC_SIM_DRAWABLE_TAG = "generic_sim_drawable_name";
    private final String OPERATOR_MCC_MNC_ARRAY_TAG = "mcc_mnc_list";
    private final String OPERATOR_DISPLAY_NAME_TAG = "operator_display_name";
    private final String OPERATOR_SIM_DRAWABLE_TAG = "operator_sim_drawable_name";
    private final String PRE_PACKAGE_NAME = "com.android.customsiminfo.res";
    private final String TAG = "OperatorSimInfo";

    private String[] mccMncList;
    private String mOperatorDisplayName;

    private int mSize = 0;

    private Drawable mOperatorDrawableIcon;
    private Drawable mGenericSimDrawableIcon;

    private boolean mIsCustomSimFeatureEnabled = false;
    private Context mContext;
    private Context prePackageContext = null;

    public OperatorSimInfo(Context context) {
        mContext = context;
        try {
            if(isOperatorFeatureEnabled()) {
                prePackageContext = context.createPackageContext(PRE_PACKAGE_NAME,
                        Context.CONTEXT_IGNORE_SECURITY);
            }
        } catch (Exception e) {
            Log.e(TAG, "Create Res Apk Failed");
        }
        if (prePackageContext != null) {
            getMccMncListFromApp();
            getOperatorLabelFromApp();
            getOperatorDrawableFromApp();
            getGenericDrawableFromApp();
        }
    }

    // getting Operator MCC-MNC list from app
    private void getMccMncListFromApp() {
         final int arrayResId = prePackageContext.getResources().getIdentifier(
                    OPERATOR_MCC_MNC_ARRAY_TAG, "array", PRE_PACKAGE_NAME);
            mccMncList = prePackageContext.getResources().getStringArray(arrayResId);
            mSize = mccMncList.length;
    }

    // gettings Operator-Sim label name from app
    private void getOperatorLabelFromApp() {
        try {
            final int labelResId = prePackageContext.getResources().getIdentifier(
                    OPERATOR_DISPLAY_NAME_TAG, "string", PRE_PACKAGE_NAME);
            mOperatorDisplayName = prePackageContext.getResources().
                    getString(labelResId);
        } catch (Exception textException) {
            Log.e(TAG, "Operator label not found");
        }

    }

    // getting Operator-Sim drawable icon
    private void getOperatorDrawableFromApp() {
        try {
            final int drawableStringResId = prePackageContext.getResources().
                    getIdentifier(OPERATOR_SIM_DRAWABLE_TAG, "string", PRE_PACKAGE_NAME);
            String drawableNameString = prePackageContext.getResources().
                    getString(drawableStringResId);
            final int drawableResId = prePackageContext.getResources().getIdentifier(
                    drawableNameString, "drawable", PRE_PACKAGE_NAME);
            mOperatorDrawableIcon = prePackageContext.getResources().
                    getDrawable(drawableResId);
        } catch (Exception resException) {
            Log.e(TAG, "Operator icon not found");
        }

    }

    // getting Generic-Sim drawable icon
    private void getGenericDrawableFromApp() {
        try {
            final int genericDrawableStringResId = prePackageContext.getResources().
                         getIdentifier(GENERIC_SIM_DRAWABLE_TAG, "string", PRE_PACKAGE_NAME);
            String genericDrawableNameString = prePackageContext.getResources().
                         getString(genericDrawableStringResId);
            final int genericSimDrawableResId = prePackageContext.getResources().getIdentifier(
                         genericDrawableNameString, "drawable", PRE_PACKAGE_NAME);
            mGenericSimDrawableIcon = prePackageContext.getResources().
                         getDrawable(genericSimDrawableResId);
       } catch (Exception genericResException) {
           Log.e(TAG, "Generic icon not found");
       }

    }

    public boolean isSimTypeOperator(int slotIndex) {
        String mccMncString = null;
        int mccMnc = -1;
        mccMncString = TelephonyManager.getDefault().
                getSimOperatorNumericForPhone(slotIndex);
        boolean isSimSlotOperator = false;
        if (!TextUtils.isEmpty(mccMncString)) {
            mccMnc = Integer.parseInt(mccMncString);
            if (mccMncList != null && mSize > 0) {
                for (int i =0; i< mSize; i++) {
                    int operatorMccMnc = Integer.parseInt(mccMncList[i]);
                    if (operatorMccMnc == mccMnc) {
                        isSimSlotOperator = true;
                        break;
                    }
                }
                return isSimSlotOperator;
            }
        }
        return isSimSlotOperator;
    }

    public boolean isSimTypeOperatorForMccMnc(int currentMccMnc) {
        boolean isSimSlotOperator = false;
        if (mccMncList != null && mSize > 0) {
            for (int i =0; i< mSize; i++) {
                int operatorMccMnc = Integer.parseInt(mccMncList[i]);
                if (operatorMccMnc == currentMccMnc) {
                    isSimSlotOperator = true;
                    break;
                }
            }
            return isSimSlotOperator;
        }
        return isSimSlotOperator;
    }

    public String getOperatorDisplayName() {
        return mOperatorDisplayName;
    }

    public Drawable getOperatorDrawable() {
        return mOperatorDrawableIcon;
    }

    public Drawable getGenericSimDrawable() {
        return mGenericSimDrawableIcon;
    }

    public boolean isOperatorFeatureEnabled() {
       return mContext.getResources().getBoolean(
               com.android.internal.R.bool.operator_custom_sim_icon);
    }

    public String getOperatorNameForSubId(int subId) {
        String label = TelephonyManager.getDefault().getSimOperator(subId);
        return label;
    }

}
