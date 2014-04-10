/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import java.lang.NullPointerException;
import java.lang.ArrayIndexOutOfBoundsException;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneSubInfoProxy;

public class PhoneSubInfoController extends IPhoneSubInfo.Stub {
    private static final String TAG = "PhoneSubInfoController";
    private Phone[] mPhone;

    public PhoneSubInfoController(Phone[] phone) {
        mPhone = phone;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }

    // Use first phoneId to return unique value always
    public String getDeviceId() {
        return getDeviceIdForSubscriber(getFirstPhoneSubId());
    }

    public String getDeviceIdForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceId();
        } else {
            Rlog.e(TAG,"getDeviceId phoneSubInfoProxy is null" +
                      " for Subscription:" + subId);
            return null;
        }
    }

    public String getImeiForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getImei();
        } else {
            Rlog.e(TAG,"getDeviceId phoneSubInfoProxy is null" +
                    " for Subscription:" + subId);
            return null;
        }
    }

    public String getDeviceSvn() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getFirstPhoneSubId());
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceSvn();
        } else {
            Rlog.e(TAG,"getDeviceSvn phoneSubInfoProxy is null");
            return null;
        }
    }

    public String getSubscriberId() {
        return getSubscriberIdForSubscriber(getDefaultSubId());
    }

    public String getSubscriberIdForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getSubscriberId();
        } else {
            Rlog.e(TAG,"getSubscriberId phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    public String getIccSerialNumber() {
        return getIccSerialNumberForSubscriber(getDefaultSubId());
    }

    public String getIccSerialNumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIccSerialNumber();
        } else {
            Rlog.e(TAG,"getIccSerialNumber phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getLine1Number() {
        return getLine1NumberForSubscriber(getDefaultSubId());
    }

    public String getLine1NumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1Number();
        } else {
            Rlog.e(TAG,"getLine1Number phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getLine1AlphaTag() {
        return getLine1AlphaTagForSubscriber(getDefaultSubId());
    }

    public String getLine1AlphaTagForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1AlphaTag();
        } else {
            Rlog.e(TAG,"getLine1AlphaTag phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getMsisdn() {
        return getMsisdnForSubscriber(getDefaultSubId());
    }

    public String getMsisdnForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getMsisdn();
        } else {
            Rlog.e(TAG,"getMsisdn phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getVoiceMailNumber() {
        return getVoiceMailNumberForSubscriber(getDefaultVoiceSubId());
    }

    public String getVoiceMailNumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailNumber();
        } else {
            Rlog.e(TAG,"getVoiceMailNumber phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    public String getCompleteVoiceMailNumber() {
        return getCompleteVoiceMailNumberForSubscriber(getDefaultVoiceSubId());
    }

    public String getCompleteVoiceMailNumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getCompleteVoiceMailNumber();
        } else {
            Rlog.e(TAG,"getCompleteVoiceMailNumber phoneSubInfoProxy" +
                      " is null for Subscription:" + subId);
            return null;
        }
    }

    public String getVoiceMailAlphaTag() {
        return getVoiceMailAlphaTagForSubscriber(getDefaultVoiceSubId());
    }

    public String getVoiceMailAlphaTagForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailAlphaTag();
        } else {
            Rlog.e(TAG,"getVoiceMailAlphaTag phoneSubInfoProxy is" +
                      " null for Subscription:" + subId);
            return null;
        }
    }

    /**
     * get Phone sub info proxy object based on subId.
     **/
    private PhoneSubInfoProxy getPhoneSubInfoProxy(long subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            phoneId = 0;
        }

        try {
            return ((PhoneProxy)mPhone[phoneId]).getPhoneSubInfoProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subId :" + subId);
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subId :" + subId);
            e.printStackTrace();
            return null;
        }
    }

    private long getDefaultSubId() {
        return  SubscriptionController.getInstance().getDefaultSubId();
    }

    private long getDefaultVoiceSubId() {
        return  SubscriptionController.getInstance().getDefaultVoiceSubId();
    }

    private long getFirstPhoneSubId() {
        // get subId from first Phone/slot Id(i.e 0)
        long[] subId = SubscriptionController.getInstance().getSubId(PhoneConstants.PHONE_ID1);
        return  subId[0];
    }

    public String getIsimImpi() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubId());
        return phoneSubInfoProxy.getIsimImpi();
    }

    public String getIsimDomain() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubId());
        return phoneSubInfoProxy.getIsimDomain();
    }

    public String[] getIsimImpu() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubId());
        return phoneSubInfoProxy.getIsimImpu();
    }

    public String getIsimIst() throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubId());
        return phoneSubInfoProxy.getIsimIst();
    }

    public String[] getIsimPcscf() throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubId());
        return phoneSubInfoProxy.getIsimPcscf();
    }

    public String getIsimChallengeResponse(String nonce) throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubId());
        return phoneSubInfoProxy.getIsimChallengeResponse(nonce);
    }

    public String getIccSimChallengeResponse(long subId, int appType, String data)
            throws RemoteException {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        return phoneSubInfoProxy.getIccSimChallengeResponse(subId, appType, data);
    }

     public String getGroupIdLevel1() {
         return getGroupIdLevel1ForSubscriber(getDefaultSubId());
     }

     public String getGroupIdLevel1ForSubscriber(long subId) {
         PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
         if (phoneSubInfoProxy != null) {
             return phoneSubInfoProxy.getGroupIdLevel1();
         } else {
             Rlog.e(TAG,"getGroupIdLevel1 phoneSubInfoProxy is" +
                       " null for Subscription:" + subId);
             return null;
         }
     }
}
