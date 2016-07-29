
package com.mediatek.internal.telephony;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.content.Context;

import com.android.internal.telephony.CommandsInterface;

public class NetworkManager extends Handler  {
    static final String LOG_TAG = "GSM";
    private static NetworkManager sNetworkManager;
    private Context mContext;
    private CommandsInterface[] mCi;
    private int mPhoneCount;

    protected static final int EVENT_GET_AVAILABLE_NW = 1;

    public static NetworkManager init(Context context, int phoneCount, CommandsInterface[] ci) {
        synchronized (NetworkManager.class) {
            if (sNetworkManager == null) {
                sNetworkManager = new NetworkManager(context, phoneCount, ci);
            }
            return sNetworkManager;
        }
    }

    public static NetworkManager getInstance() {
        return sNetworkManager;
    }

    private NetworkManager(Context context , int phoneCount, CommandsInterface[] ci) {

        log("Initialize NetworkManager under airplane mode phoneCount= " + phoneCount);

        mContext = context;
        mCi = ci;
        mPhoneCount = phoneCount;
    }

    /**
       * To scan all available networks. i.e. PLMN list
       * @param Message for on complete
       * @param simId Indicate which sim(slot) to query
       * @internal
       */
    public void getAvailableNetworks(long subId, Message response) {
    /*
        int activeSim = -1;
        for (int i=0; i<PhoneConstants.GEMINI_SIM_NUM;++i) {
            if (!mGeminiDataMgr.isGprsDetached(i)) {
                activeSim = i;
                break;
            }
        }

        Rlog.d(LOG_TAG, "getAvailableNetworksGemini activeSIM="+activeSim);

        if (activeSim == -1 || activeSim == simId ||
                PhoneFactory.isDualTalkMode()) {
            getPhonebyId(simId).getAvailableNetworks(response);
        } else {
            PhoneBase phoneBase = getPhoneBase(activeSim);
            if (phoneBase instanceof GSMPhone) {
                Rlog.d(LOG_TAG, "activeSim: "  + activeSim + ", simId: " + simId);
                mActiveApnTypes = getActiveApnTypesGemini(activeSim);
                mGeminiDataMgr.cleanupAllConnection(activeSim);
            }
            mGettingAvailableNetwork = true;
            Message msg = obtainMessage(EVENT_GET_AVAILABLE_NW);
            msg.arg1 = activeSim;
            msg.arg2 = simId;
            msg.obj = response;
            mGeminiDataMgr.registerForDetached(activeSim, this, EVENT_GPRS_DETACHED, msg);
        }
        */
        return;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_GET_AVAILABLE_NW:
                synchronized (this) {

                }
                break;

            default:
                log("Unhandled message with number: " + msg.what);
                break;
        }
    }

    private static void log(String s) {
        Log.d(LOG_TAG, "[NetworkManager] " + s);
    }
}


