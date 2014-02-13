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

import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.PreciseCallState;
import android.telephony.DisconnectCause;

import com.android.internal.telephony.ITelephonyRegistry;

import java.util.List;

/**
 * broadcast intents
 */
public class DefaultPhoneNotifier implements PhoneNotifier {

    private ITelephonyRegistry mRegistry;

    /*package*/
    DefaultPhoneNotifier() {
        mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    "telephony.registry"));
    }

    @Override
    public void notifyPhoneState(Phone sender) {
        Call ringingCall = sender.getRingingCall();
        String incomingNumber = "";
        if (ringingCall != null && ringingCall.getEarliestConnection() != null){
            incomingNumber = ringingCall.getEarliestConnection().getAddress();
        }
        try {
            mRegistry.notifyCallState(convertCallState(sender.getState()), incomingNumber);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyServiceState(Phone sender) {
        ServiceState ss = sender.getServiceState();
        if (ss == null) {
            ss = new ServiceState();
            ss.setStateOutOfService();
        }
        try {
            mRegistry.notifyServiceState(ss);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifySignalStrength(Phone sender) {
        try {
            mRegistry.notifySignalStrength(sender.getSignalStrength());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyMessageWaitingChanged(Phone sender) {
        try {
            mRegistry.notifyMessageWaitingChanged(sender.getMessageWaitingIndicator());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyCallForwardingChanged(Phone sender) {
        try {
            mRegistry.notifyCallForwardingChanged(sender.getCallForwardingIndicator());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyDataActivity(Phone sender) {
        try {
            mRegistry.notifyDataActivity(convertDataActivityState(sender.getDataActivityState()));
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyDataConnection(Phone sender, String reason, String apnType,
            PhoneConstants.DataState state) {
        doNotifyDataConnection(sender, reason, apnType, state);
    }

    private void doNotifyDataConnection(Phone sender, String reason, String apnType,
            PhoneConstants.DataState state) {
        // TODO
        // use apnType as the key to which connection we're talking about.
        // pass apnType back up to fetch particular for this one.
        TelephonyManager telephony = TelephonyManager.getDefault();
        LinkProperties linkProperties = null;
        LinkCapabilities linkCapabilities = null;
        boolean roaming = false;

        if (state == PhoneConstants.DataState.CONNECTED) {
            linkProperties = sender.getLinkProperties(apnType);
            linkCapabilities = sender.getLinkCapabilities(apnType);
        }
        ServiceState ss = sender.getServiceState();
        if (ss != null) roaming = ss.getRoaming();

        try {
            mRegistry.notifyDataConnection(
                    convertDataState(state),
                    sender.isDataConnectivityPossible(apnType), reason,
                    sender.getActiveApnHost(apnType),
                    apnType,
                    linkProperties,
                    linkCapabilities,
                    ((telephony!=null) ? telephony.getNetworkType() :
                    TelephonyManager.NETWORK_TYPE_UNKNOWN),
                    roaming);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyDataConnectionFailed(Phone sender, String reason, String apnType) {
        try {
            mRegistry.notifyDataConnectionFailed(reason, apnType);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyCellLocation(Phone sender) {
        Bundle data = new Bundle();
        sender.getCellLocation().fillInNotifierBundle(data);
        try {
            mRegistry.notifyCellLocation(data);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    @Override
    public void notifyCellInfo(Phone sender, List<CellInfo> cellInfo) {
        try {
            mRegistry.notifyCellInfo(cellInfo);
        } catch (RemoteException ex) {

        }
    }

    @Override
    public void notifyOtaspChanged(Phone sender, int otaspMode) {
        try {
            mRegistry.notifyOtaspChanged(otaspMode);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyPreciseCallState(Phone sender) {
        Call ringingCall = sender.getRingingCall();
        Call foregroundCall = sender.getForegroundCall();
        Call backgroundCall = sender.getBackgroundCall();
        if (ringingCall != null && foregroundCall != null && backgroundCall != null) {
            try {
                mRegistry.notifyPreciseCallState(
                        convertPreciseCallState(ringingCall.getState()),
                        convertPreciseCallState(foregroundCall.getState()),
                        convertPreciseCallState(backgroundCall.getState()));
            } catch (RemoteException ex) {
                // system process is dead
            }
        }
    }

    public void notifyDisconnectCause(Connection.DisconnectCause cause, int preciseCause) {
        try {
            mRegistry.notifyDisconnectCause(convertDisconnectCause(cause), preciseCause);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyPreciseDataConnectionFailed(Phone sender, String reason, String apnType,
            String apn, String failCause) {
        try {
            mRegistry.notifyPreciseDataConnectionFailed(reason, apnType, apn, failCause);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Convert the {@link PhoneConstants.State} enum into the TelephonyManager.CALL_STATE_*
     * constants for the public API.
     */
    public static int convertCallState(PhoneConstants.State state) {
        switch (state) {
            case RINGING:
                return TelephonyManager.CALL_STATE_RINGING;
            case OFFHOOK:
                return TelephonyManager.CALL_STATE_OFFHOOK;
            default:
                return TelephonyManager.CALL_STATE_IDLE;
        }
    }

    /**
     * Convert the TelephonyManager.CALL_STATE_* constants into the
     * {@link PhoneConstants.State} enum for the public API.
     */
    public static PhoneConstants.State convertCallState(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                return PhoneConstants.State.RINGING;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return PhoneConstants.State.OFFHOOK;
            default:
                return PhoneConstants.State.IDLE;
        }
    }

    /**
     * Convert the {@link PhoneConstants.DataState} enum into the TelephonyManager.DATA_* constants
     * for the public API.
     */
    public static int convertDataState(PhoneConstants.DataState state) {
        switch (state) {
            case CONNECTING:
                return TelephonyManager.DATA_CONNECTING;
            case CONNECTED:
                return TelephonyManager.DATA_CONNECTED;
            case SUSPENDED:
                return TelephonyManager.DATA_SUSPENDED;
            default:
                return TelephonyManager.DATA_DISCONNECTED;
        }
    }

    /**
     * Convert the TelephonyManager.DATA_* constants into {@link PhoneConstants.DataState} enum
     * for the public API.
     */
    public static PhoneConstants.DataState convertDataState(int state) {
        switch (state) {
            case TelephonyManager.DATA_CONNECTING:
                return PhoneConstants.DataState.CONNECTING;
            case TelephonyManager.DATA_CONNECTED:
                return PhoneConstants.DataState.CONNECTED;
            case TelephonyManager.DATA_SUSPENDED:
                return PhoneConstants.DataState.SUSPENDED;
            default:
                return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    /**
     * Convert the {@link Phone.DataActivityState} enum into the TelephonyManager.DATA_* constants
     * for the public API.
     */
    public static int convertDataActivityState(Phone.DataActivityState state) {
        switch (state) {
            case DATAIN:
                return TelephonyManager.DATA_ACTIVITY_IN;
            case DATAOUT:
                return TelephonyManager.DATA_ACTIVITY_OUT;
            case DATAINANDOUT:
                return TelephonyManager.DATA_ACTIVITY_INOUT;
            case DORMANT:
                return TelephonyManager.DATA_ACTIVITY_DORMANT;
            default:
                return TelephonyManager.DATA_ACTIVITY_NONE;
        }
    }

    /**
     * Convert the TelephonyManager.DATA_* constants into the {@link Phone.DataActivityState} enum
     * for the public API.
     */
    public static Phone.DataActivityState convertDataActivityState(int state) {
        switch (state) {
            case TelephonyManager.DATA_ACTIVITY_IN:
                return Phone.DataActivityState.DATAIN;
            case TelephonyManager.DATA_ACTIVITY_OUT:
                return Phone.DataActivityState.DATAOUT;
            case TelephonyManager.DATA_ACTIVITY_INOUT:
                return Phone.DataActivityState.DATAINANDOUT;
            case TelephonyManager.DATA_ACTIVITY_DORMANT:
                return Phone.DataActivityState.DORMANT;
            default:
                return Phone.DataActivityState.NONE;
        }
    }

    /**
     * Convert the {@link State} enum into the PreciseCallState.PRECISE_CALL_STATE_* constants
     * for the public API.
     */
    public static int convertPreciseCallState(Call.State state) {
        switch (state) {
            case ACTIVE:
                return PreciseCallState.PRECISE_CALL_STATE_ACTIVE;
            case HOLDING:
                return PreciseCallState.PRECISE_CALL_STATE_HOLDING;
            case DIALING:
                return PreciseCallState.PRECISE_CALL_STATE_DIALING;
            case ALERTING:
                return PreciseCallState.PRECISE_CALL_STATE_ALERTING;
            case INCOMING:
                return PreciseCallState.PRECISE_CALL_STATE_INCOMING;
            case WAITING:
                return PreciseCallState.PRECISE_CALL_STATE_WAITING;
            case DISCONNECTED:
                return PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED;
            case DISCONNECTING:
                return PreciseCallState.PRECISE_CALL_STATE_DISCONNECTING;
            default:
                return PreciseCallState.PRECISE_CALL_STATE_IDLE;
        }
    }

    /**
     * Convert the Call.State.* constants into the {@link State} enum
     * for the public API.
     */
    public static Call.State convertPreciseCallState(int state) {
        switch (state) {
            case PreciseCallState.PRECISE_CALL_STATE_ACTIVE:
                return Call.State.ACTIVE;
            case PreciseCallState.PRECISE_CALL_STATE_HOLDING:
                return Call.State.HOLDING;
            case PreciseCallState.PRECISE_CALL_STATE_DIALING:
                return Call.State.DIALING;
            case PreciseCallState.PRECISE_CALL_STATE_ALERTING:
                return Call.State.ALERTING;
            case PreciseCallState.PRECISE_CALL_STATE_INCOMING:
                return Call.State.INCOMING;
            case PreciseCallState.PRECISE_CALL_STATE_WAITING:
                return Call.State.WAITING;
            case PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED:
                return Call.State.DISCONNECTED;
            case PreciseCallState.PRECISE_CALL_STATE_DISCONNECTING:
                return Call.State.DISCONNECTING;
            default:
                return Call.State.IDLE;
        }
    }

    /**
     * Convert the {@link DisconnectCause} enum into the DisconnectCause.*
     * constants for the public API.
     */
    public static int convertDisconnectCause(Connection.DisconnectCause cause) {
        switch (cause) {
            case NOT_DISCONNECTED:
                return DisconnectCause.NOT_DISCONNECTED;
            case INCOMING_MISSED:
                return DisconnectCause.INCOMING_MISSED;
            case NORMAL:
                return DisconnectCause.NORMAL;
            case LOCAL:
                return DisconnectCause.LOCAL;
            case BUSY:
                return DisconnectCause.BUSY;
            case CONGESTION:
                return DisconnectCause.CONGESTION;
            case MMI:
                return DisconnectCause.MMI;
            case INVALID_NUMBER:
                return DisconnectCause.INVALID_NUMBER;
            case NUMBER_UNREACHABLE:
                return DisconnectCause.NUMBER_UNREACHABLE;
            case SERVER_UNREACHABLE:
                return DisconnectCause.SERVER_UNREACHABLE;
            case INVALID_CREDENTIALS:
                return DisconnectCause.INVALID_CREDENTIALS;
            case OUT_OF_NETWORK:
                return DisconnectCause.OUT_OF_NETWORK;
            case SERVER_ERROR:
                return DisconnectCause.SERVER_ERROR;
            case TIMED_OUT:
                return DisconnectCause.TIMED_OUT;
            case LOST_SIGNAL:
                return DisconnectCause.LOST_SIGNAL;
            case LIMIT_EXCEEDED:
                return DisconnectCause.LIMIT_EXCEEDED;
            case INCOMING_REJECTED:
                return DisconnectCause.INCOMING_REJECTED;
            case POWER_OFF:
                return DisconnectCause.POWER_OFF;
            case OUT_OF_SERVICE:
                return DisconnectCause.OUT_OF_SERVICE;
            case ICC_ERROR:
                return DisconnectCause.ICC_ERROR;
            case CALL_BARRED:
                return DisconnectCause.CALL_BARRED;
            case FDN_BLOCKED:
                return DisconnectCause.FDN_BLOCKED;
            case CS_RESTRICTED:
                return DisconnectCause.CS_RESTRICTED;
            case CS_RESTRICTED_NORMAL:
                return DisconnectCause.CS_RESTRICTED_NORMAL;
            case CS_RESTRICTED_EMERGENCY:
                return DisconnectCause.CS_RESTRICTED_EMERGENCY;
            case UNOBTAINABLE_NUMBER:
                return DisconnectCause.UNOBTAINABLE_NUMBER;
            case CDMA_LOCKED_UNTIL_POWER_CYCLE:
                return DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE;
            case CDMA_DROP:
                return DisconnectCause.CDMA_DROP;
            case CDMA_INTERCEPT:
                return DisconnectCause.CDMA_INTERCEPT;
            case CDMA_REORDER:
                return DisconnectCause.CDMA_REORDER;
            case CDMA_SO_REJECT:
                return DisconnectCause.CDMA_SO_REJECT;
            case CDMA_RETRY_ORDER:
                return DisconnectCause.CDMA_RETRY_ORDER;
            case CDMA_ACCESS_FAILURE:
                return DisconnectCause.CDMA_ACCESS_FAILURE;
            case CDMA_PREEMPTED:
                return DisconnectCause.CDMA_PREEMPTED;
            case CDMA_NOT_EMERGENCY:
                return DisconnectCause.CDMA_NOT_EMERGENCY;
            case CDMA_ACCESS_BLOCKED:
                return DisconnectCause.CDMA_ACCESS_BLOCKED;
            default:
                return DisconnectCause.ERROR_UNSPECIFIED;
        }
    }

    /**
     * Convert the DisconnectCause.* constants into the {@link DisconnectCause}
     * enum for the public API.
     */
    public static Connection.DisconnectCause convertDisconnectCause(int disconnectCause) {
        switch (disconnectCause) {
            case DisconnectCause.NOT_DISCONNECTED:
                return Connection.DisconnectCause.NOT_DISCONNECTED;
            case DisconnectCause.INCOMING_MISSED:
                return Connection.DisconnectCause.INCOMING_MISSED;
            case DisconnectCause.NORMAL:
                return Connection.DisconnectCause.NORMAL;
            case DisconnectCause.LOCAL:
                return Connection.DisconnectCause.LOCAL;
            case DisconnectCause.BUSY:
                return Connection.DisconnectCause.BUSY;
            case DisconnectCause.CONGESTION:
                return Connection.DisconnectCause.CONGESTION;
            case DisconnectCause.MMI:
                return Connection.DisconnectCause.MMI;
            case DisconnectCause.INVALID_NUMBER:
                return Connection.DisconnectCause.INVALID_NUMBER;
            case DisconnectCause.NUMBER_UNREACHABLE:
                return Connection.DisconnectCause.NUMBER_UNREACHABLE;
            case DisconnectCause.SERVER_UNREACHABLE:
                return Connection.DisconnectCause.SERVER_UNREACHABLE;
            case DisconnectCause.INVALID_CREDENTIALS:
                return Connection.DisconnectCause.INVALID_CREDENTIALS;
            case DisconnectCause.OUT_OF_NETWORK:
                return Connection.DisconnectCause.OUT_OF_NETWORK;
            case DisconnectCause.SERVER_ERROR:
                return Connection.DisconnectCause.SERVER_ERROR;
            case DisconnectCause.TIMED_OUT:
                return Connection.DisconnectCause.TIMED_OUT;
            case DisconnectCause.LOST_SIGNAL:
                return Connection.DisconnectCause.LOST_SIGNAL;
            case DisconnectCause.LIMIT_EXCEEDED:
                return Connection.DisconnectCause.LIMIT_EXCEEDED;
            case DisconnectCause.INCOMING_REJECTED:
                return Connection.DisconnectCause.INCOMING_REJECTED;
            case DisconnectCause.POWER_OFF:
                return Connection.DisconnectCause.POWER_OFF;
            case DisconnectCause.OUT_OF_SERVICE:
                return Connection.DisconnectCause.OUT_OF_SERVICE;
            case DisconnectCause.ICC_ERROR:
                return Connection.DisconnectCause.ICC_ERROR;
            case DisconnectCause.CALL_BARRED:
                return Connection.DisconnectCause.CALL_BARRED;
            case DisconnectCause.FDN_BLOCKED:
                return Connection.DisconnectCause.FDN_BLOCKED;
            case DisconnectCause.CS_RESTRICTED:
                return Connection.DisconnectCause.CS_RESTRICTED;
            case DisconnectCause.CS_RESTRICTED_NORMAL:
                return Connection.DisconnectCause.CS_RESTRICTED_NORMAL;
            case DisconnectCause.CS_RESTRICTED_EMERGENCY:
                return Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY;
            case DisconnectCause.UNOBTAINABLE_NUMBER:
                return Connection.DisconnectCause.UNOBTAINABLE_NUMBER;
            case DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE:
                return Connection.DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE;
            case DisconnectCause.CDMA_DROP:
                return Connection.DisconnectCause.CDMA_DROP;
            case DisconnectCause.CDMA_INTERCEPT:
                return Connection.DisconnectCause.CDMA_INTERCEPT;
            case DisconnectCause.CDMA_REORDER:
                return Connection.DisconnectCause.CDMA_REORDER;
            case DisconnectCause.CDMA_SO_REJECT:
                return Connection.DisconnectCause.CDMA_SO_REJECT;
            case DisconnectCause.CDMA_RETRY_ORDER:
                return Connection.DisconnectCause.CDMA_RETRY_ORDER;
            case DisconnectCause.CDMA_ACCESS_FAILURE:
                return Connection.DisconnectCause.CDMA_ACCESS_FAILURE;
            case DisconnectCause.CDMA_PREEMPTED:
                return Connection.DisconnectCause.CDMA_PREEMPTED;
            case DisconnectCause.CDMA_NOT_EMERGENCY:
                return Connection.DisconnectCause.CDMA_NOT_EMERGENCY;
            case DisconnectCause.CDMA_ACCESS_BLOCKED:
                return Connection.DisconnectCause.CDMA_ACCESS_BLOCKED;
            default:
                return Connection.DisconnectCause.ERROR_UNSPECIFIED;
        }
    }
}
