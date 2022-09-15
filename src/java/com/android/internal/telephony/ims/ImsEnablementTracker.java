/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.ims;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsServiceController;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

/**
 * This class will abstract away all the new enablement logic and take the reset/enable/disable
 * IMS commands as inputs.
 * The IMS commands will call enableIms or disableIms to match the enablement state only when
 * it changes.
 */
public class ImsEnablementTracker {
    private static final String LOG_TAG = "ImsEnablementTracker";
    private static final long REQUEST_THROTTLE_TIME_MS = 1 * 1000; // 1 seconds

    private static final int COMMAND_NONE_MSG = 0;
    // Indicate that the enableIms command has been received.
    private static final int COMMAND_ENABLE_MSG = 1;
    // Indicate that the disableIms command has been received.
    private static final int COMMAND_DISABLE_MSG = 2;
    // Indicate that the resetIms command has been received.
    private static final int COMMAND_RESET_MSG = 3;
    // Indicate that the internal enable message with delay has been received.
    @VisibleForTesting
    protected static final int COMMAND_ENABLING_DONE = 4;
    // Indicate that the internal disable message with delay has been received.
    @VisibleForTesting
    protected static final int COMMAND_DISABLING_DONE = 5;
    // Indicate that the internal reset message with delay has been received.
    @VisibleForTesting
    protected static final int COMMAND_RESETTING_DONE = 6;
    // The ImsServiceController binder is connected.
    private static final int COMMAND_CONNECTED_MSG = 7;
    // The ImsServiceController binder is disconnected.
    private static final int COMMAND_DISCONNECTED_MSG = 8;

    @VisibleForTesting
    protected static final int STATE_IMS_DISCONNECTED = 0;
    @VisibleForTesting
    protected static final int STATE_IMS_DEFAULT = 1;
    @VisibleForTesting
    protected static final int STATE_IMS_ENABLED = 2;
    @VisibleForTesting
    protected static final int STATE_IMS_DISABLING = 3;
    @VisibleForTesting
    protected static final int STATE_IMS_DISABLED = 4;
    @VisibleForTesting
    protected static final int STATE_IMS_ENABLING = 5;
    @VisibleForTesting
    protected static final int STATE_IMS_RESETTING = 6;

    protected final Object mLock = new Object();
    private IImsServiceController mIImsServiceController;
    private long mLastImsOperationTimeMs = 0L;

    private final ImsEnablementTrackerStateMachine mEnablementStateMachine;

    /**
     * Provides Ims Enablement Tracker State Machine responsible for ims enable/disable command
     * interactions with Ims service controller binder.
     * The enable/disable/reset ims commands have a time interval of at least 1 second between
     * processing each command.
     * For example, the enableIms command is received and the binder's enableIms is called.
     * After that, if the disableIms command is received, the binder's disableIms will be
     * called after 1 second.
     * A time of 1 second uses {@link Handler#sendMessageDelayed(Message, long)},
     * and the enabled, disabled and reset states are responsible for waiting for
     * that delay message.
     */
    class ImsEnablementTrackerStateMachine extends StateMachine {
        /**
         * The initial state of this class and waiting for an ims commands.
         */
        @VisibleForTesting
        public final Default mDefault;
        /**
         * Indicates that {@link IImsServiceController#enableIms(int, int)} has been called and
         * waiting for an ims commands.
         * Common transitions are to
         * {@link #mDisabling} state when the disable command is received
         * or {@link #mResetting} state when the reset command is received.
         * or {@link #mDisconnected} if the binder is disconnected.
         */
        @VisibleForTesting
        public final Enabled mEnabled;
        /**
         * Indicates that the state waiting for a disableIms message.
         * Common transitions are to
         * {@link #mEnabled} when the enable command is received.
         * or {@link #mResetting} when the reset command is received.
         * or {@link #mDisabled} the previous binder API call has passed 1 second, and if
         * {@link IImsServiceController#disableIms(int, int)} called.
         * or {@link #mDisabling} received a disableIms message and the previous binder API call
         * has not passed 1 second.Then send a disableIms message with delay.
         * or {@link #mDisconnected} if the binder is disconnected.
         */
        @VisibleForTesting
        public final Disabling mDisabling;
        /**
         * Indicates that {@link IImsServiceController#disableIms(int, int)} has been called and
         * waiting for an ims commands.
         * Common transitions are to
         * {@link #mEnabling} state when the enable command is received.
         * or {@link #mDisconnected} if the binder is disconnected.
         */
        @VisibleForTesting
        public final Disabled mDisabled;
        /**
         * Indicates that the state waiting for an enableIms message.
         * Common transitions are to
         * {@link #mEnabled} the previous binder API call has passed 1 second, and
         * {@link IImsServiceController#enableIms(int, int)} called.
         * or {@link #mDisabled} when the disable command is received.
         * or {@link #mEnabling} received an enableIms message and the previous binder API call
         * has not passed 1 second.Then send an enableIms message with delay.
         * or {@link #mDisconnected} if the binder is disconnected.
         */
        @VisibleForTesting
        public final Enabling mEnabling;
        /**
         * Indicates that the state waiting for a resetIms message.
         * Common transitions are to
         * {@link #mDisabling} state when the disable command is received
         * or {@link #mResetting} received a resetIms message and the previous binder API call
         * has not passed 1 second.Then send a resetIms message with delay.
         * or {@link #mEnabling} when the resetIms message is received and if
         * {@link IImsServiceController#disableIms(int, int)} call is successful. And send an enable
         * message with delay.
         * or {@link #mDisconnected} if the binder is disconnected.
         */
        @VisibleForTesting
        public final Resetting mResetting;
        /**
         * Indicates that {@link IImsServiceController} has not been set.
         * Common transition is to
         * {@link #mDefault} state when the binder is set.
         * or {@link #mDisabling} If the disable command is received while the binder is
         * disconnected
         * or {@link #mEnabling} If the enable command is received while the binder is
         * disconnected
         */
        @VisibleForTesting
        public final Disconnected mDisconnected;

        @VisibleForTesting
        public int mSlotId;
        @VisibleForTesting
        public int mSubId;

        ImsEnablementTrackerStateMachine(String name, Looper looper) {
            super(name, looper);
            mDefault = new Default();
            mEnabled = new Enabled();
            mDisabling = new Disabling();
            mDisabled = new Disabled();
            mEnabling = new Enabling();
            mResetting = new Resetting();
            mDisconnected = new Disconnected();

            addState(mDefault);
            addState(mEnabled);
            addState(mDisabling);
            addState(mDisabled);
            addState(mEnabling);
            addState(mResetting);
            addState(mDisconnected);
            setInitialState(mDisconnected);
        }

        public void clearAllMessage() {
            Log.d(LOG_TAG, "clearAllMessage");
            removeMessages(COMMAND_ENABLE_MSG);
            removeMessages(COMMAND_DISABLE_MSG);
            removeMessages(COMMAND_RESET_MSG);
            removeMessages(COMMAND_ENABLING_DONE);
            removeMessages(COMMAND_DISABLING_DONE);
            removeMessages(COMMAND_RESETTING_DONE);
        }

        public void serviceBinderConnected() {
            clearAllMessage();
            sendMessage(COMMAND_CONNECTED_MSG);
        }

        public void serviceBinderDisconnected() {
            clearAllMessage();
            sendMessage(COMMAND_DISCONNECTED_MSG);
        }

        @VisibleForTesting
        public void setState(int state) {
            if (state == mDefault.mStateNo) {
                mEnablementStateMachine.transitionTo(mDefault);
            } else if (state == mEnabled.mStateNo) {
                mEnablementStateMachine.transitionTo(mEnabled);
            } else if (state == mDisabling.mStateNo) {
                mEnablementStateMachine.transitionTo(mDisabling);
            } else if (state == mDisabled.mStateNo) {
                mEnablementStateMachine.transitionTo(mDisabled);
            } else if (state == mEnabling.mStateNo) {
                mEnablementStateMachine.transitionTo(mEnabling);
            } else if (state == mResetting.mStateNo) {
                mEnablementStateMachine.transitionTo(mResetting);
            }
        }

        @VisibleForTesting
        public boolean isState(int state) {
            if (state == mDefault.mStateNo) {
                return (mEnablementStateMachine.getCurrentState() == mDefault) ? true : false;
            } else if (state == mEnabled.mStateNo) {
                return (mEnablementStateMachine.getCurrentState() == mEnabled) ? true : false;
            } else if (state == mDisabling.mStateNo) {
                return (mEnablementStateMachine.getCurrentState() == mDisabling) ? true : false;
            } else if (state == mDisabled.mStateNo) {
                return (mEnablementStateMachine.getCurrentState() == mDisabled) ? true : false;
            } else if (state == mEnabling.mStateNo) {
                return (mEnablementStateMachine.getCurrentState() == mEnabling) ? true : false;
            } else if (state == mResetting.mStateNo) {
                return (mEnablementStateMachine.getCurrentState() == mResetting) ? true : false;
            }
            return false;
        }


        class Default extends State {
            public int mStateNo = STATE_IMS_DEFAULT;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Default state:enter");
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Default state:exit");
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Default state:processMessage. msg.what=" + message.what);
                switch(message.what) {
                    // When enableIms() is called, enableIms of binder is call and the state
                    // change to the enabled state.
                    case COMMAND_ENABLE_MSG:
                        sendEnableIms(message.arg1, message.arg2);
                        transitionTo(mEnabled);
                        return HANDLED;
                    // When disableIms() is called, disableIms of binder is call and the state
                    // change to the disabled state.
                    case COMMAND_DISABLE_MSG:
                        sendDisableIms(message.arg1, message.arg2);
                        transitionTo(mDisabled);
                        return HANDLED;
                    case COMMAND_DISCONNECTED_MSG:
                        transitionTo(mDisconnected);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class Enabled extends State {
            public int mStateNo = STATE_IMS_ENABLED;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Enabled state:enter");
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Enabled state:exit");
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Enabled state:processMessage. msg.what=" + message.what);
                mSlotId = message.arg1;
                mSubId = message.arg2;
                switch(message.what) {
                    // the disableIms() is called.
                    case COMMAND_DISABLE_MSG:
                        transitionTo(mDisabling);
                        return HANDLED;
                    // the resetIms() is called.
                    case COMMAND_RESET_MSG:
                        transitionTo(mResetting);
                        return HANDLED;
                    case COMMAND_DISCONNECTED_MSG:
                        transitionTo(mDisconnected);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class Disabling extends State {
            public int mStateNo = STATE_IMS_DISABLING;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Disabling state:enter");
                sendMessageDelayed(COMMAND_DISABLING_DONE, mSlotId, mSubId,
                        getRemainThrottleTime());
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Disabling state:exit");
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Disabling state:processMessage. msg.what=" + message.what);
                mSlotId = message.arg1;
                mSubId = message.arg2;
                switch(message.what) {
                    // In the enabled state, disableIms() is called, but the throttle timer has
                    // not expired, so a delay_disable message is sent.
                    // At this point enableIms() was called, so it cancels the message and just
                    // changes the state to the enabled.
                    case COMMAND_ENABLE_MSG:
                        clearAllMessage();
                        transitionTo(mEnabled);
                        return HANDLED;
                    case COMMAND_DISABLING_DONE:
                        // If the disable command is received before disableIms is processed,
                        // it will be ignored because the disable command processing is in progress.
                        removeMessages(COMMAND_DISABLE_MSG);
                        sendDisableIms(mSlotId, mSubId);
                        transitionTo(mDisabled);
                        return HANDLED;
                    case COMMAND_RESET_MSG:
                        clearAllMessage();
                        transitionTo(mResetting);
                        return HANDLED;
                    case COMMAND_DISCONNECTED_MSG:
                        transitionTo(mDisconnected);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class Disabled extends State {
            public int mStateNo = STATE_IMS_DISABLED;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Disabled state:enter");
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Disabled state:exit");
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Disabled state:processMessage. msg.what=" + message.what);
                mSlotId = message.arg1;
                mSubId = message.arg2;
                switch(message.what) {
                    case COMMAND_ENABLE_MSG:
                        transitionTo(mEnabling);
                        return HANDLED;
                    case COMMAND_DISCONNECTED_MSG:
                        transitionTo(mDisconnected);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class Enabling extends State {
            public int mStateNo = STATE_IMS_ENABLING;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Enabling state:enter");
                sendMessageDelayed(COMMAND_ENABLING_DONE, mSlotId, mSubId, getRemainThrottleTime());
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Enabling state:exit");
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Enabling state:processMessage. msg.what=" + message.what);
                mSlotId = message.arg1;
                mSubId = message.arg2;
                switch(message.what) {
                    case COMMAND_DISABLE_MSG:
                        clearAllMessage();
                        transitionTo(mDisabled);
                        return HANDLED;
                    case COMMAND_ENABLING_DONE:
                        // If the enable command is received before enableIms is processed,
                        // it will be ignored because the enable command processing is in progress.
                        removeMessages(COMMAND_ENABLE_MSG);
                        sendEnableIms(mSlotId, mSubId);
                        transitionTo(mEnabled);
                        return HANDLED;
                    case COMMAND_DISCONNECTED_MSG:
                        transitionTo(mDisconnected);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class Resetting extends State {
            public int mStateNo = STATE_IMS_RESETTING;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Resetting state:enter");
                sendMessageDelayed(COMMAND_RESETTING_DONE, mSlotId, mSubId,
                        getRemainThrottleTime());
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Resetting state:exit");
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Resetting state:processMessage. msg.what=" + message.what);
                mSlotId = message.arg1;
                mSubId = message.arg2;
                switch(message.what) {
                    case COMMAND_DISABLE_MSG:
                        clearAllMessage();
                        transitionTo(mDisabling);
                        return HANDLED;
                    case COMMAND_RESETTING_DONE:
                        // If the reset command is received before disableIms is processed,
                        // it will be ignored because the reset command processing is in progress.
                        removeMessages(COMMAND_RESET_MSG);
                        sendDisableIms(mSlotId, mSubId);
                        transitionTo(mEnabling);
                        return HANDLED;
                    case COMMAND_DISCONNECTED_MSG:
                        transitionTo(mDisconnected);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class Disconnected extends State {
            public int mStateNo = STATE_IMS_DISCONNECTED;
            private int mLastMsg = COMMAND_NONE_MSG;
            @Override
            public void enter() {
                Log.d(LOG_TAG, "Disconnected state:enter");
                clearAllMessage();
            }

            @Override
            public void exit() {
                Log.d(LOG_TAG, "Disconnected state:exit");
                mLastMsg = COMMAND_NONE_MSG;
            }

            @Override
            public boolean processMessage(Message message) {
                Log.d(LOG_TAG, "Disconnected state:processMessage. msg.what=" + message.what);
                switch(message.what) {
                    case COMMAND_CONNECTED_MSG:
                        clearAllMessage();
                        transitionTo(mDefault);
                        if (mLastMsg != COMMAND_NONE_MSG) {
                            sendMessageDelayed(mLastMsg, mSlotId, mSubId, 0);
                        }
                        return HANDLED;
                    case COMMAND_ENABLE_MSG:
                    case COMMAND_DISABLE_MSG:
                    case COMMAND_RESET_MSG:
                        mLastMsg = message.what;
                        mSlotId = message.arg1;
                        mSubId = message.arg2;
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }
    }

    public ImsEnablementTracker(Looper looper) {
        mIImsServiceController = null;
        mEnablementStateMachine = new ImsEnablementTrackerStateMachine("ImsEnablementTracker",
                looper);
        mEnablementStateMachine.start();
    }

    @VisibleForTesting
    public ImsEnablementTracker(Looper looper, IImsServiceController controller) {
        mIImsServiceController = controller;
        mEnablementStateMachine = new ImsEnablementTrackerStateMachine("ImsEnablementTracker",
                looper);
        mEnablementStateMachine.start();
        mEnablementStateMachine.sendMessage(COMMAND_CONNECTED_MSG);
    }

    @VisibleForTesting
    public Handler getHandler() {
        return mEnablementStateMachine.getHandler();
    }

    /**
     * Set the current state as an input state.
     * @param state input state.
     */
    @VisibleForTesting
    public void setState(int state) {
        mEnablementStateMachine.setState(state);
    }

    /**
     * Check that the current state and the input state are the same.
     * @param state the input state.
     * @return true if the current state and input state are the same or false.
     */
    @VisibleForTesting
    public boolean isState(int state) {
        return mEnablementStateMachine.isState(state);
    }

    /**
     * Notify ImsService to enable IMS for the framework. This will trigger IMS registration and
     * trigger ImsFeature status updates.
     * @param slotId slot id
     * @param subId subscription id
     */
    public void enableIms(int slotId, int subId) {
        Log.d(LOG_TAG, "enableIms");
        mEnablementStateMachine.sendMessage(COMMAND_ENABLE_MSG, slotId, subId);
    }

    /**
     * Notify ImsService to disable IMS for the framework. This will trigger IMS de-registration and
     * trigger ImsFeature capability status to become false.
     * @param slotId slot id
     * @param subId subscription id
     */
    public void disableIms(int slotId, int subId) {
        Log.d(LOG_TAG, "disableIms");
        mEnablementStateMachine.sendMessage(COMMAND_DISABLE_MSG, slotId, subId);
    }

    /**
     * Notify ImsService to disable IMS for the framework if current state is enabled.
     * And notify ImsService back to enable IMS for the framework.
     * @param slotId slot id
     * @param subId subscription id
     */
    public void resetIms(int slotId, int subId) {
        Log.d(LOG_TAG, "resetIms");
        mEnablementStateMachine.sendMessage(COMMAND_RESET_MSG, slotId, subId);
    }

    /**
     * Sets the IImsServiceController instance.
     */
    protected void setServiceController(IBinder serviceController) {
        synchronized (mLock) {
            mIImsServiceController = IImsServiceController.Stub.asInterface(serviceController);

            if (isServiceControllerAvailable()) {
                mEnablementStateMachine.serviceBinderConnected();
            } else {
                mEnablementStateMachine.serviceBinderDisconnected();
            }
        }
    }

    @VisibleForTesting
    protected long getLastOperationTimeMillis() {
        return mLastImsOperationTimeMs;
    }

    /**
     * Get remaining throttle time value
     * @return remaining throttle time value
     */
    @VisibleForTesting
    public long getRemainThrottleTime() {
        long remainTime = REQUEST_THROTTLE_TIME_MS - (System.currentTimeMillis()
                - getLastOperationTimeMillis());
        Log.d(LOG_TAG, "getRemainThrottleTime:" + remainTime);
        if (remainTime < 0) {
            return 0;
        }
        return remainTime;
    }

    /**
     * Send internal Message for testing.
     */
    @VisibleForTesting
    public void sendInternalMessage(int msg, int slotId, int subId) {
        mEnablementStateMachine.sendMessage(msg, slotId, subId);
    }

    /**
     * Check to see if the service controller is available.
     * @return true if available, false otherwise
     */
    private boolean isServiceControllerAvailable() {
        return mIImsServiceController != null;
    }

    private void sendEnableIms(int slotId, int subId) {
        Log.d(LOG_TAG, "sendEnableIms");
        try {
            synchronized (mLock) {
                if (isServiceControllerAvailable()) {
                    mIImsServiceController.enableIms(slotId, subId);
                    mLastImsOperationTimeMs = System.currentTimeMillis();
                }
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Couldn't enable IMS: " + e.getMessage());
        }
    }

    private void sendDisableIms(int slotId, int subId) {
        Log.d(LOG_TAG, "sendDisableIms");
        try {
            synchronized (mLock) {
                if (isServiceControllerAvailable()) {
                    mIImsServiceController.disableIms(slotId, subId);
                    mLastImsOperationTimeMs = System.currentTimeMillis();
                }
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Couldn't disable IMS: " + e.getMessage());
        }
    }
}
