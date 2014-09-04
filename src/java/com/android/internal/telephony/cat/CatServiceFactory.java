/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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
 *
 */

package com.android.internal.telephony.cat;

import android.content.Context;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;


/**
 * Class that creates the CatServices for each card.
 *
 * {@hide}
 */
public class CatServiceFactory {

    private static CatService sCatServices[] = null;
    private static final int sSimCount = TelephonyManager.getDefault().getSimCount();

    // Protects singleton instance lazy initialization.
    private static final Object sInstanceLock = new Object();

    /**
     * Used for instantiating the Service from the Card.
     *
     * @param ci CommandsInterface object
     * @param context phone app context
     * @param ic Icc card
     * @param slotId to know the index of card
     * @return The only Service object in the system
     */
    public static CatService makeCatService(CommandsInterface ci,
            Context context, UiccCard ic, int slotId) {
        UiccCardApplication ca = null;
        IccFileHandler fh = null;

        if (sCatServices == null) {
            sCatServices = new CatService[sSimCount];
        }

        if (ci == null || context == null || ic == null) return null;

        //get first valid filehandler in the card.
        for (int i = 0; i < ic.getNumApplications(); i++) {
            ca = ic.getApplicationIndex(i);
            if (ca != null && (ca.getType() != AppType.APPTYPE_UNKNOWN)) {
                fh = ca.getIccFileHandler();
                break;
            }
        }

        synchronized (sInstanceLock) {
            if (fh == null) return null;

            if (sCatServices[slotId] == null) {
                sCatServices[slotId] = new CatService(ci, context, fh, slotId);
            }
        }
        return sCatServices[slotId];
    }

    public static CatService getCatService(int slotId) {
        return ((sCatServices == null) ? null : sCatServices[slotId]);
    }

    public static void disposeCatService (int slotId) {
        if (sCatServices != null) {
            sCatServices[slotId].dispose();
            sCatServices[slotId] = null;
        }
    }
}
