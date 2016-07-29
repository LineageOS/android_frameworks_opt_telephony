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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;

import android.telephony.SubscriptionManager;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.MultiSimVariants;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.UiccController;

import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;
import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;

/**
 * @hide
 */
public class ExternalSimManager {
    private static final String TAG = "ExternalSimManager";

    private static final int SOCKET_OPEN_RETRY_MILLIS = 4 * 1000;

    private static ExternalSimManager sInstance = null;
    private VsimEvenHandler mEventHandler = null;
    private VsimIoThread mRilIoThread = null;
    private boolean isMdWaitingResponse = false;

    static final String[] PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };

    /*  Construction function for TelephonyManager */
    public ExternalSimManager() {
        Rlog.d(TAG, "construtor 0 parameter is called - done");
    }

    private ExternalSimManager(Context context) {
        Rlog.d(TAG, "construtor 1 parameter is called - start");

        mEventHandler = new VsimEvenHandler();

        new Thread() {
            public void run() {
                ServerTask server = new ServerTask();
                server.listenConnection(mEventHandler);
            }
        }.start();

        // Need to reset system properties when shutdown ipo to avoid receiving unexcepted
        // intetnt in case of IPO boot up.
        IntentFilter intentFilter = new IntentFilter("android.intent.action.ACTION_SHUTDOWN_IPO");
        context.registerReceiver(sReceiver, intentFilter);

        Rlog.d(TAG, "construtor is called - end");
    }

    /** @hide
     *  @return return the static instance of ExternalSimManager
     */
    public static ExternalSimManager getDefault(Context context) {
        Rlog.d(TAG, "getDefault()");
        if (sInstance == null) {
            sInstance = new ExternalSimManager(context);
        }
        return sInstance;
    }

    private static ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
    }

    // Need to reset system properties when shutdown ipo to avoid receiving unexcepted
    // intetnt in case of IPO boot up.
    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Rlog.d(TAG,"[Receiver]+");
            String action = intent.getAction();
            Rlog.d(TAG,"Action: " + action);

            if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
                // set system property to note that sim enabled
                SystemProperties.set(TelephonyProperties.PROPERTY_EXTERNAL_SIM_ENABLED, "");
                SystemProperties.set(TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED, "");
            }
            Rlog.d(TAG,"[Receiver]-");
        }
    };

    public boolean initializeService(byte[] userData) {
        Rlog.d(TAG, "initializeService() - start");

        if (SystemProperties.getInt("ro.mtk_external_sim_support", 0) == 0) {
            Rlog.d(TAG, "initializeService() - mtk_external_sim_support didn't support");
            return false;
        }

        try {
            getITelephonyEx().initializeService("osi");
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
        Rlog.d(TAG, "initialize() - end");
        return true;
    }

    public boolean finalizeService(byte[] userData) {
        Rlog.d(TAG, "finalizeService() - start");

        if (SystemProperties.getInt("ro.mtk_external_sim_support", 0) == 0) {
            Rlog.d(TAG, "initializeService() - mtk_external_sim_support didn't support");
            return false;
        }

        try {
            getITelephonyEx().finalizeService("osi");
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
        Rlog.d(TAG, "finalizeService() - end");
        return true;
    }

    /**
     * Maintain a server task to provide extenal client to connect to do
     * some external SIM operation.
     *
     */
    public class ServerTask {
        public static final String HOST_NAME = "vsim-adaptor";
        private VsimIoThread ioThread = null;

        public void listenConnection(VsimEvenHandler eventHandler) {
            Rlog.d(TAG, "listenConnection() - start");

            LocalServerSocket serverSocket = null;
            ExecutorService threadExecutor = Executors.newCachedThreadPool();

            try {
                // Create server socket
                serverSocket = new LocalServerSocket(HOST_NAME);

                while(true) {
                    // Allow multiple connection connect to server.
                    LocalSocket socket = serverSocket.accept();
                    Rlog.d(TAG, "There is a client is accpted: " + socket.toString());

                    threadExecutor.execute(new ConnectionHandler(socket, eventHandler));
                }
            } catch (IOException e) {
                Rlog.d(TAG, "listenConnection catch IOException");
                e.printStackTrace();
            } catch (Exception e) {
                Rlog.d(TAG, "listenConnection catch Exception");
                e.printStackTrace();
            } finally {
                Rlog.d(TAG, "listenConnection finally!!");
                if (threadExecutor != null )
                    threadExecutor.shutdown();
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            Rlog.d(TAG, "listenConnection() - end");
        }
    }


    /**
     * Maintain a RIL client task to connect to ril_vsim socket to communicate with rild/modem.
     *
     */
    public class RilClientTask {
        public static final String SERVER_NAME = "rild-vsim";
        LocalSocket mSocket = null;
        private VsimIoThread ioThread = null;
        private int retryCount = 0;

        // Need to connect to ril_vsim socket to communicate with rild/modem
        public void connectToServer() {
            Rlog.d(TAG, "connectToServer() - start");
            while (retryCount < 10) {
                try {
                    Rlog.d(TAG, "connectToServer() - before");

                    mSocket = new LocalSocket();
                    LocalSocketAddress addr = new LocalSocketAddress(SERVER_NAME,
                            LocalSocketAddress.Namespace.RESERVED);

                    mSocket.connect(addr);

                    Rlog.d(TAG, "connectToServer() - after");
                } catch (IOException e) {
                    Rlog.d(TAG, "connectToServer catch IOException");
                    e.printStackTrace();

                    if (mSocket != null && !mSocket.isConnected()) {
                        retryCount++;
                        try {
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException er) {
                        }
                        Rlog.d(TAG, "connectToServer retry later, retry count: " + retryCount);
                    }
                }

                if (mSocket != null && mSocket.isConnected()) {
                    Rlog.d(TAG, "connectToServer connected!");
                    break;
                }
            }
            Rlog.d(TAG, "connectToServer() - end");
        }

        public VsimIoThread getIoThread(VsimEvenHandler eventHandler) {
            if (ioThread == null) {
                try {
                    ioThread = new VsimIoThread(
                            SERVER_NAME,
                            mSocket.getInputStream(),
                            mSocket.getOutputStream(),
                            eventHandler);
                    ioThread.start();
                } catch (IOException e) {
                    Rlog.d(TAG, "getIoThread catch IOException");
                    e.printStackTrace();
                }
             }
            return ioThread;
        }
    }

    public class ConnectionHandler implements Runnable {
        private LocalSocket mSocket;
        //private RilClientTask mRilSocket;
        private VsimEvenHandler mEventHandler;
        public static final String RILD_SERVER_NAME = "rild-vsim";

        public ConnectionHandler(LocalSocket clientSocket, VsimEvenHandler eventHandler) {
            mSocket = clientSocket;
            mEventHandler = eventHandler;
        }

        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            Rlog.d(TAG, "New connection: " + mSocket.toString());

            try {
                //mRilSocket = new RilClientTask();
                //mRilSocket.connectToServer();

                VsimIoThread ioThread = new VsimIoThread(
                        ServerTask.HOST_NAME,
                        mSocket.getInputStream(),
                        mSocket.getOutputStream(),
                        mEventHandler);
                ioThread.start();

                if (mRilIoThread == null) {
                    mRilIoThread = new VsimIoThread(
                            RILD_SERVER_NAME,
                            RILD_SERVER_NAME,
                            mEventHandler);
                    mRilIoThread.start();
                }

                mEventHandler.setDataStream(ioThread, mRilIoThread);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class VsimEvent {
        public static final int DEFAULT_MAX_DATA_LENGTH = 20480;
        private int mTransactionId;
        private int mMessageId;
        private int mSlotId;
        private int mDataLen;
        private int mReadOffset;
        private byte mData[];
        private int mEventMaxDataLen = DEFAULT_MAX_DATA_LENGTH;

        /**
         * The VsimEvent constructor with specified phone Id.
         *
         * @param transactionId event serial number, use to determine a pair of request & response.
         * @param messageId message event id
         */
        public VsimEvent(int transactionId, int messageId) {
            this(transactionId, messageId, 0);
        }

        /**
         * The VsimEvent constructor with specified phone Id.
         *
         * @param transactionId event serial number, use to determine a pair of request & response.
         * @param messageId message event id
         * @param slotId the indicated slotId
         */
        public VsimEvent(int transactionId, int messageId, int slotId) {
            this(transactionId, messageId, DEFAULT_MAX_DATA_LENGTH, slotId);
        }

        /**
         * The VsimEvent constructor with specified phone Id.
         *
         * @param transactionId event serial number, use to determine a pair of request & response.
         * @param messageId message event id
         * @param length the max data length of the event
         * @param slotId the indicated slotId
         */
        public VsimEvent(int transactionId, int messageId, int length, int slotId) {
            mTransactionId = transactionId;
            mMessageId = messageId;
            mSlotId = slotId;
            mEventMaxDataLen = length;
            mData = new byte[mEventMaxDataLen];
            mDataLen = 0;
            mReadOffset = 0;
        }

        public int putInt(int value) {
            synchronized (this) {
                if (mDataLen > mEventMaxDataLen - 4) {
                    return -1;
                }

                for (int i = 0 ; i < 4 ; ++i) {
                    mData[mDataLen] = (byte) ((value >> (8 * i)) & 0xFF);
                    mDataLen++;
                }
            }
            return 0;
        }

        public int putShort(int value) {
            synchronized (this) {
                if (mDataLen > mEventMaxDataLen - 2) {
                    return -1;
                }

                for (int i = 0 ; i < 2 ; ++i) {
                    mData[mDataLen] = (byte) ((value >> (8 * i)) & 0xFF);
                    mDataLen++;
                }
            }
            return 0;
        }

        public int putByte(int value) {
            if (mDataLen > mEventMaxDataLen - 1) {
                return -1;
            }

            synchronized (this) {
                mData[mDataLen] = (byte) (value & 0xFF);
                mDataLen++;
            }
            return 0;
        }

        public int putString(String str, int len) {
            synchronized (this) {
                if (mDataLen > mEventMaxDataLen - len) {
                    return -1;
                }

                byte s[] = str.getBytes();
                if (len < str.length()) {
                    System.arraycopy(s, 0, mData, mDataLen, len);
                    mDataLen += len;
                } else {
                    int remain = len - str.length();
                    System.arraycopy(s, 0, mData, mDataLen, str.length());
                    mDataLen += str.length();
                    for (int i = 0 ; i < remain ; i++) {
                        mData[mDataLen] = 0;
                        mDataLen++;
                    }
                }
            }
            return 0;
        }

        public int putBytes(byte [] value) {
            synchronized (this) {
                int len = value.length;

                if (len > mEventMaxDataLen) {
                    return -1;
                }

                System.arraycopy(value, 0, mData, mDataLen, len);
                mDataLen += len;
            }
            return 0;
        }

        public int putCapability(int multiSim, int vsimSupported, int allowedSlots) {
            if (mDataLen > mEventMaxDataLen - (4 * 4)) {
                return -1;
            }

            putInt(1);  //valid capablity
            putInt(multiSim);
            putInt(vsimSupported);
            putInt(allowedSlots);
            return 0;
        }

        public int putPaddingCapability(){
            if (mDataLen > mEventMaxDataLen - (4 * 4)) {
                return -1;
            }

            putInt(0);  //valid capablity
            putInt(0);  //multi Sim
            putInt(0);  //vsim supported flag
            putInt(0);  //allowed sim slots
            return 0;
        }

        public int putUiccCommand(int commandLen, byte[] command) {
            if (mDataLen > mEventMaxDataLen - (4 * 3)) {
                return -1;
            }

            putInt(1);  //valid uicc request command
            putInt(commandLen);
            putBytes(command);
            return 0;
        }

        public int putPaddingUiccCommand(){
            if (mDataLen > mEventMaxDataLen - (4 * 3)) {
                return -1;
            }

            putInt(1);  //valid uicc request command
            putInt(0);  //command len
            //putBytes(command);
            return 0;
        }

        public byte [] getData() {
            byte tempData[] = new byte[mDataLen];
            System.arraycopy(mData, 0, tempData, 0, mDataLen);
            return tempData;
        }

        public int getDataLen() {
            return mDataLen;
        }

        public int getMessageId() {
            return mMessageId;
        }

        /*
         * Return slot bit mask.
         * 1 means slot0,
         * 2 means slot1,
         * 3 means slot 0 and slot 1.
         */
        public int getSlotBitMask() {
            return mSlotId;
        }

        /*
         * Return the first mapping slot of slot bit mask value.
         */
        public int getFirstSlotId() {
            int simCount = TelephonyManager.getDefault().getSimCount();
            for (int i = 0; i < simCount; i++) {
                if ((getSlotBitMask() & (1 << i)) != 0) {
                    Rlog.d(TAG, "getFirstSlotId, slotId = " + i
                            + ", slot bit mapping = " + getSlotBitMask());
                    return i;
                }
            }
            return -1;
        }

        public int getTransactionId() {
            return mTransactionId;
        }

        public int getInt() {
            int ret = 0;
            synchronized (this) {
                if (mData.length >= 4) {
                    ret = ((mData[mReadOffset + 3] & 0xff) << 24 |
                           (mData[mReadOffset + 2] & 0xff) << 16 |
                        (mData[mReadOffset + 1] & 0xff) << 8 |
                        (mData[mReadOffset] & 0xff));
                    mReadOffset += 4;
                }
            }
            return ret;
        }

        public int getShort() {
            int ret = 0;
            synchronized (this) {
                ret =  ((mData[mReadOffset + 1] & 0xff) << 8 | (mData[mReadOffset] & 0xff));
                mReadOffset += 2;
            }
            return ret;
        }

        // Notice: getByte is to get int8 type from VA, not get one byte.
        public int getByte() {
            int ret = 0;
            synchronized (this) {
                ret = (mData[mReadOffset] & 0xff);
                mReadOffset += 1;
            }
            return ret;
        }

        public byte[] getBytes(int length) {
            synchronized (this) {
                if (length > mDataLen - mReadOffset) {
                    return null;
                }

                byte[] ret = new byte[length];

                for (int i = 0 ; i < length ; i++) {
                    ret[i] = mData[mReadOffset];
                    mReadOffset++;
                }
                return ret;
            }
        }

        public String getString(int len) {
            byte buf [] = new byte[len];

            synchronized (this) {
                System.arraycopy(mData, mReadOffset, buf, 0, len);
                mReadOffset += len;
            }

            return (new String(buf)).trim();
        }
    }


    class VsimIoThread extends Thread {
        private String mName = "";
        private static final int MAX_DATA_LENGTH = (20 * 1024);
        private DataInputStream mInput = null;
        private DataOutputStream mOutput = null;
        private VsimEvenHandler mEventHandler = null;
        private LocalSocket mSocket = null;
        private String mServerName = "";

        private byte[] readBuffer = null;

        public VsimIoThread(
                String name,
                InputStream inputStream,
                OutputStream outputStream,
                VsimEvenHandler eventHandler) {
            mName = name;
            mInput = new DataInputStream(inputStream);
            mOutput = new DataOutputStream(outputStream);
            mEventHandler = eventHandler;
            log("VsimIoThread constructor is called.");
        }

        public VsimIoThread(String name, String serverName, VsimEvenHandler eventHandler) {
            mServerName = serverName;
            createClientSocket(mServerName);
            mName = name;
            mEventHandler = eventHandler;
            log("VsimIoThread constructor with creating socket is called.");
        }

        private void createClientSocket(String serverName) {
            int retryCount = 0;
            log("createClientSocket() - start");
            while (retryCount < 10) {
                try {
                    log("createClientSocket() - before, serverName: " + serverName);

                    mSocket = new LocalSocket();
                    LocalSocketAddress addr = new LocalSocketAddress(serverName,
                            LocalSocketAddress.Namespace.RESERVED);

                    mSocket.connect(addr);

                    mInput = new DataInputStream(mSocket.getInputStream());
                    mOutput = new DataOutputStream(mSocket.getOutputStream());

                    log("createClientSocket() - after, mSocket:" + mSocket.toString());
                } catch (IOException e) {
                    log("createClientSocket catch IOException");
                    e.printStackTrace();

                    if (mSocket != null && !mSocket.isConnected()) {
                        retryCount++;
                        try {
                            mSocket.close();
                            mSocket = null;
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                        log("createClientSocket retry later, retry count: " + retryCount);
                    }
                }

                if (mSocket != null && mSocket.isConnected()) {
                    log("createClientSocket connected!");
                    break;
                }
            }
            log("createClientSocket() - end");
        }

        public void closeSocket() {
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                    log("closeSocket.");
                }
            } catch (IOException e) {
                log("closeSocket IOException.");
                e.printStackTrace();
            }
        }

        public void run() {
            log("VsimIoThread running.");
            while(true) {
                try {
                    //if (mInput.available() > 0) {
                        VsimEvent event = readEvent();
                        // Need to handle the event
                        if (event != null) {
                            Message msg = new Message();
                            msg.obj = event;
                            mEventHandler.sendMessage(msg);
                        }
                    //} else if (!mSocket.isConnected()){
                    //    log("VsimIoThread mSocket is disconnected!");
                    //}
                } catch (IOException e) {
                    log("VsimIoThread IOException.");
                    e.printStackTrace();

                    // To avoid server socket is closed due to modem reset
                    try {

                        if (mSocket != null) {
                            mSocket.close();
                            mSocket = null;
                        }

                        if (!mServerName.equals("")) {
                            createClientSocket(mServerName);
                        } else {
                            // Means the client socket has been disconnected.
                            // We shall close the socket and waiting for the new connection.
                            log("Ingore exception");
                            return;
                        }
                    } catch (IOException e2) {
                        log("VsimIoThread IOException 2.");
                        e2.printStackTrace();
                    }
                } catch (Exception e) {
                    log("VsimIoThread Exception.");
                    e.printStackTrace();
                }
            }
        }

        private void writeBytes(byte [] value, int len) throws IOException {
            mOutput.write(value, 0, len);
        }

        private void writeInt(int value) throws IOException {
            for (int i = 0 ; i < 4 ; ++i) {
                mOutput.write((value >> (8 * i)) & 0xff);
            }
        }

        public int writeEvent(VsimEvent event) {
            return writeEvent(event, false);
        }

        public int writeEvent(VsimEvent event, boolean isBigEndian) {
            log("writeEvent Enter, isBigEndian:" + isBigEndian);
            int ret = -1;
            try {
                synchronized (this) {
                    if (mOutput != null) {
                        dumpEvent(event);

                        writeInt(event.getTransactionId());
                        writeInt(event.getMessageId());
                        // Platfrom slot id start from 0, so need to add 1.
                        writeInt(event.getSlotBitMask());
                        writeInt(event.getDataLen());
                        writeBytes(event.getData(), event.getDataLen());
                        mOutput.flush();
                        ret = 0;
                    } else {
                        log("mOut is null, socket is not setup");
                    }
                }
            } catch (Exception e) {
                log("writeEvent Exception");
                e.printStackTrace();
                return -1;
            }

            return ret;
        }

        /**
         * DataInputStream's readInt is Big-Endian method.
         */
        private int readInt() throws IOException {
            byte[] tempBuf = new byte[8];
            int readCount = mInput.read(tempBuf, 0, 4);
            if (readCount < 0) {
                log("readInt(), fail to read and throw exception");
                throw new IOException("fail to read");
            }
            //log("[readInt] after readFully");
            return ((tempBuf[3]) << 24 |
                    (tempBuf[2] & 0xff) << 16 |
                    (tempBuf[1] & 0xff) << 8 |
                    (tempBuf[0] & 0xff));
        }

        private VsimEvent readEvent() throws IOException {
            log("readEvent Enter");

            int transaction_id = readInt();
            int msg_id = readInt();
            int slot_id = readInt();
            int data_len = readInt();
            log("readEvent transaction_id: " + transaction_id +
                    ", msgId: " + msg_id + ", slot_id: " + slot_id + ", len: " + data_len);

            readBuffer = new byte[data_len];

            int offset = 0;
            int remaining = data_len;

            do {
                int countRead = mInput.read(readBuffer, offset, remaining);

                if (countRead < 0) {
                    log("readEvent(), fail to read and throw exception");
                    throw new IOException("fail to read");
                }

                offset += countRead;
                remaining -= countRead;
            } while (remaining > 0);

            VsimEvent event = new VsimEvent(transaction_id, msg_id, data_len, slot_id);
            event.putBytes(readBuffer);

            dumpEvent(event);
            return event;
        }

        private void dumpEvent(VsimEvent event) {
            log("dumpEvent: transaction_id: " + event.getTransactionId()
                    + ", message_id:" + event.getMessageId()
                    + ", slot_id:" + event.getSlotBitMask()
                    + ", data_len:" + event.getDataLen()
                    + ", event:" + IccUtils.bytesToHexString(event.getData()));
        }

        private void log(String s) {
            Rlog.d(TAG, "[" + mName + "] " + s);
        }
    }


    public class VsimEvenHandler extends Handler {
        private VsimIoThread mVsimAdaptorIo = null;
        private VsimIoThread mVsimRilIo = null;
        private boolean mHasNotifyEnableEvnetToModem = false;

        @Override
        public void handleMessage(Message msg) {
            dispatchCallback((VsimEvent) msg.obj);
        }

        private void setDataStream(VsimIoThread vsimAdpatorIo, VsimIoThread vsimRilIo) {
            mVsimAdaptorIo = vsimAdpatorIo;
            mVsimRilIo = vsimRilIo;
            Rlog.d(TAG, "VsimEvenHandler setDataStream done.");
        }

        private void setMdWaitingFlag(boolean isWaiting) {
            Rlog.d(TAG, "setMdWaitingFlag: " + isWaiting);
            isMdWaitingResponse = isWaiting;
        }

        private boolean getMdWaitingFlag() {
            Rlog.d(TAG, "getMdWaitingFlag: " + isMdWaitingResponse);
            return isMdWaitingResponse;
        }

        private void handleEventRequest(int type, VsimEvent event) {
            Rlog.d(TAG, "VsimEvenHandler eventHandlerByType: type[" + type + "] start");

            // Get external SIM slot id
            int slotId = event.getFirstSlotId();
            // Get if a local SIM (local SIM mean no need to download SIM data from server)
            int simType = event.getInt();
            // Response result
            int result = ExternalSimConstants.RESPONSE_RESULT_OK;

            Rlog.d(TAG, "VsimEvenHandler First slotId:" + slotId + ", simType:" + simType);

            switch (type) {
                case ExternalSimConstants.REQUEST_TYPE_ENABLE_EXTERNAL_SIM: {
                    // set result according to sub ready state
                    if (SubscriptionController.getInstance().isReady()) {
                        result = ExternalSimConstants.RESPONSE_RESULT_OK;
                    } else {
                        result = ExternalSimConstants.RESPONSE_RESULT_PLATFORM_NOT_READY;
                    }

                    // 1.set default data sub id without capablity swtich to VSIM slot
                    int subId = SubscriptionManager.getSubIdUsingPhoneId(slotId);
                    SubscriptionController ctrl = SubscriptionController.getInstance();

                    if (simType != ExternalSimConstants.SIM_TYPE_LOCAL_SIM) {
                        // MTK TODO
                        // ctrl.setDefaultDataSubIdWithoutCapabilitySwitch(subId);

                        Rlog.d(TAG, "VsimEvenHandler set default data to subId: " + subId);
                    }
                    // set system property to note that sim enabled
                    TelephonyManager.getDefault().setTelephonyProperty(
                            slotId, TelephonyProperties.PROPERTY_EXTERNAL_SIM_ENABLED, "1");
                    break;
                }
                case ExternalSimConstants.REQUEST_TYPE_DISABLE_EXTERNAL_SIM: {
                    // 1.set system property to note that sim enabled
                    TelephonyManager.getDefault().setTelephonyProperty(
                            slotId, TelephonyProperties.PROPERTY_EXTERNAL_SIM_ENABLED, "0");
                    TelephonyManager.getDefault().setTelephonyProperty(
                            slotId, TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED, "0");

                    // 2.send event to modem side, will reset in rild side
                    // FIXME: C2K project need to reset modem on both modem, so we can't
                    // reset modem on gsm rild.
                    mVsimRilIo.writeEvent(event);

                    // 3. set modem waiting flag to false to drop the following uncompleted APDU
                    //    or RESET request.
                    setMdWaitingFlag(false);

                    RadioManager.getInstance().setSilentRebootPropertyForAllModem("1");
                    // MTK TODO
                    // UiccController.getInstance().resetRadioForVsim();
                    break;
                }
                case ExternalSimConstants.REQUEST_TYPE_PLUG_IN: {
                     // 1.write shared prefrence/system property to record vsim availble event.
                    TelephonyManager.getDefault().setTelephonyProperty(
                            slotId, TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED,
                            String.valueOf(simType));
                    TelephonyManager.getDefault().setTelephonyProperty(
                            slotId, "persist.radio.external.sim",
                            String.valueOf(simType));
                    // 2.capability switch or reset modem with set VSIM on
                    SubscriptionController ctrl = SubscriptionController.getInstance();
                    int mCPhoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
                    //int mCSubId = ctrl.getSubIdUsingPhoneId(mCPhoneId);
                    //int[] mTSubId = ctrl.getSubId(slotId);
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        if (mCPhoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
                            mCPhoneId = PhoneConstants.SIM_ID_1;
                        }
                        if (mCPhoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
                            mCPhoneId = PhoneConstants.SIM_ID_2;
                        }
                    }

                    if (slotId == mCPhoneId) {
                        // Send event to rild which will reset modem in rild side.
                        Rlog.d(TAG, "VsimEvenHandler no need to do capablity switch");
                        mVsimRilIo.writeEvent(event);
                        RadioManager.getInstance().setSilentRebootPropertyForAllModem("1");
                        // MTK TODO
                        // UiccController.getInstance().resetRadioForVsim();
                    } else {
                        // set result according to sub ready state
                        Rlog.d(TAG, "VsimEvenHandler need to do capablity switch");
                        if (SubscriptionController.getInstance().isReady()) {
                            // Capbility switch will reset modem
                            int subId = SubscriptionManager.getSubIdUsingPhoneId(slotId);
                            SubscriptionController.getInstance().setDefaultDataSubId(subId);
                            result = ExternalSimConstants.RESPONSE_RESULT_OK;
                        } else {
                            result = ExternalSimConstants.RESPONSE_RESULT_PLATFORM_NOT_READY;
                        }
                    }
                    break;
                }
                case ExternalSimConstants.REQUEST_TYPE_PLUG_OUT: {
                    // 1.write shared prefrence/system property to record vsim unavailble event.
                    TelephonyManager.getDefault().setTelephonyProperty(
                            slotId, TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED, "0");
                    // 2.send event to modem side
                    mVsimRilIo.writeEvent(event);

                    // 3. set modem waiting flag to false to drop the following uncompleted APDU
                    //    or RESET request.
                    setMdWaitingFlag(false);
                    break;
                }
            }

            VsimEvent eventResponse = new VsimEvent(
                    event.getTransactionId(),
                    ExternalSimConstants.MSG_ID_EVENT_RESPONSE, event.getSlotBitMask());
            eventResponse.putInt(result);  //result
            mVsimAdaptorIo.writeEvent(eventResponse);

            Rlog.d(TAG, "VsimEvenHandler eventHandlerByType: type[" + type + "] end");
        }

        private void handleGetPlatformCapability(VsimEvent event) {
            int eventId = event.getInt();   //no-used
            int simType = event.getInt();

            VsimEvent response = new VsimEvent(
                    event.getTransactionId(),
                    ExternalSimConstants.MSG_ID_GET_PLATFORM_CAPABILITY_RESPONSE,
                    event.getSlotBitMask());
            // 1. Put result value to check platform ready
            if (SubscriptionController.getInstance().isReady()) {
                response.putInt(ExternalSimConstants.RESPONSE_RESULT_OK);
            } else {
                response.putInt(ExternalSimConstants.RESPONSE_RESULT_PLATFORM_NOT_READY);
            }

            // 2.1 Return multi-phone type, such as dsds or dsda.
            MultiSimVariants config = TelephonyManager.getDefault().getMultiSimConfiguration();
            if (config == MultiSimVariants.DSDS) {
                response.putInt(ExternalSimConstants.MULTISIM_CONFIG_DSDS);
            } else if (config == MultiSimVariants.DSDA) {
                response.putInt(ExternalSimConstants.MULTISIM_CONFIG_DSDA);
            } else if (config == MultiSimVariants.TSTS) {
                response.putInt(ExternalSimConstants.MULTISIM_CONFIG_TSTS);
            } else {
                response.putInt(ExternalSimConstants.MULTISIM_CONFIG_UNKNOWN);
            }

            // 2.2 Return external SIM support flag (refer to feature option)
            response.putInt(SystemProperties.getInt("ro.mtk_external_sim_support", 0));

            // 2.3 Return slots allow to enable external SIM.
            // The value is bit-mask, bit X means the slot (X - 1) is allowed to use external
            // SIM. For an example, value 3 is bit 1 and 2 is 1 means external SIM is allowed
            // to enable on slot 1 and slot 0.
            int simCount = TelephonyManager.getDefault().getSimCount();

            Rlog.d(TAG, "handleGetPlatformCapability simType: " + simType
                    + ", simCount: " + simCount);

            if (simType == ExternalSimConstants.SIM_TYPE_LOCAL_SIM) {
                response.putInt((1 << simCount) - 1);
            } else {
                if (config == MultiSimVariants.DSDA) {
                    int isCdmaCard = 0;
                    int isHasCard = 0;

                    for (int i = 0; i < simCount; i++) {
                        String cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[i], "");
                        if (!cardType.equals("")) {
                            isHasCard |= (1 << i);
                        }

                        if (cardType.contains("CSIM")
                                || cardType.contains("RUIM") || cardType.contains("UIM")) {
                            isCdmaCard |= (1 << i);
                        }
                    }

                    Rlog.d(TAG, "handleGetPlatformCapability isCdmaCard: " + isCdmaCard
                            + ", isHasCard: " + isHasCard);

                    if (isHasCard == 0) {
                        // DSDA project and there is no card is inserted.
                        response.putInt(0);
                    } else if (isCdmaCard == 0) {
                        // DSDA project and there is no C card is inserted.
                        response.putInt(0);
                    } else {
                        // DSDA project and there is a C card is inserted.
                        response.putInt(isCdmaCard ^ ((1 << simCount) - 1));
                    }

                } else {
                    // Non-DSDA project and it is not local SIM.
                    // In this case, we could enable external SIM.
                    response.putInt(0);
                }
            }

            // Write response event by socket
            mVsimAdaptorIo.writeEvent(response);
        }

        private void handleServiceStateRequest(VsimEvent event) {
            int result = ExternalSimConstants.RESPONSE_RESULT_OK;
            int voiceRejectCause = -1;
            int dataRejectCause = -1;

            VsimEvent response = new VsimEvent(
                    event.getTransactionId(),
                    ExternalSimConstants.MSG_ID_GET_SERVICE_STATE_RESPONSE,
                    event.getSlotBitMask());
            if (SubscriptionController.getInstance().isReady()) {
                ITelephonyEx telEx = ITelephonyEx.Stub.asInterface(
                        ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
                if (telEx == null) {
                    try {
                        int subId = SubscriptionManager.getSubIdUsingPhoneId(
                                event.getFirstSlotId());
                        Bundle bundle = telEx.getServiceState(subId);
                        ServiceState ss = ServiceState.newFromBundle(bundle);
                        Rlog.d(TAG, "handleServiceStateRequest subId: " + subId +
                                ", ss = " + ss.toString());
                        voiceRejectCause = ss.getVoiceRejectCause();
                        dataRejectCause = ss.getDataRejectCause();
                    } catch (RemoteException e) {
                        Rlog.d(TAG, "RemoteException!!");
                        result = ExternalSimConstants.RESPONSE_RESULT_GENERIC_ERROR;
                        e.printStackTrace();
                    }
                }
            } else {
                result = ExternalSimConstants.RESPONSE_RESULT_PLATFORM_NOT_READY;
            }

            //Put response result
            response.putInt(result);
            //Put voice reject cause
            response.putInt(voiceRejectCause);
            //Put data reject cause
            response.putInt(dataRejectCause);

            mVsimAdaptorIo.writeEvent(response);
        }

        /* dispatch Callback */
        private void dispatchCallback(VsimEvent event) {
            // Handler events
            int msgId = event.getMessageId();

            Rlog.d(TAG, "VsimEvenHandler handleMessage: msgId[" + msgId + "]");

            switch (msgId) {
                case ExternalSimConstants.MSG_ID_INITIALIZATION_REQUEST:
                    // Customized: allow to do neccessary initialization related to external SIM.
                    // For an example, start an indicated service or set some configuration.
                    break;

                case ExternalSimConstants.MSG_ID_FINALIZATION_REQUEST:
                    // Customized: allow to do neccessary finalization related to external SIM.
                    // For an example, stop an indicated service or set some configuration.
                    break;

                case ExternalSimConstants.MSG_ID_GET_PLATFORM_CAPABILITY_REQUEST:
                    handleGetPlatformCapability(event);
                    break;

                case ExternalSimConstants.MSG_ID_EVENT_REQUEST:
                    handleEventRequest(event.getInt(), event);
                    break;

                case ExternalSimConstants.MSG_ID_EVENT_RESPONSE:
                    // Is need to return from modem???
                    break;

                case ExternalSimConstants.MSG_ID_UICC_APDU_REQUEST: {
                    setMdWaitingFlag(true);
                    // Reguest from modem side, just adjust format and dispatch the event

                    // get system property to check if vsim started
                    String inserted =  TelephonyManager.getDefault().getTelephonyProperty(
                            event.getFirstSlotId(),
                            TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED, "0");

                    if (inserted != null && inserted.length() > 0 && !"0".equals(inserted)) {
                        mVsimAdaptorIo.writeEvent(event);
                    }
                    break;
                }
                case ExternalSimConstants.MSG_ID_UICC_APDU_RESPONSE:
                    if (getMdWaitingFlag()) {
                        // If modem waiting flag is set to flag, mean that, there might
                        // be a plug out event during waiting response.
                        // In this case, AP should drop this event to avoid modem receive
                        // unexcepted event.
                        // If the waiting flag is true,
                        // just send to modem side without parsing data.
                        mVsimRilIo.writeEvent(event);
                        setMdWaitingFlag(false);
                    }
                    break;

                case ExternalSimConstants.MSG_ID_UICC_RESET_REQUEST: {
                    setMdWaitingFlag(true);
                    // Reguest from modem side, just adjust format and dispatch the event
                    // get system property to check if vsim started
                    String inserted =  TelephonyManager.getDefault().getTelephonyProperty(
                            event.getFirstSlotId(),
                            TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED, "0");

                    if (inserted != null && inserted.length() > 0 && !"0".equals(inserted)) {
                        mVsimAdaptorIo.writeEvent(event);
                    }
                    break;
                }
                case ExternalSimConstants.MSG_ID_UICC_RESET_RESPONSE:
                    if (getMdWaitingFlag()) {
                        // If modem waiting flag is set to flag, mean that, there might
                        // be a plug out event during waiting response.
                        // In this case, AP should drop this event to avoid modem receive
                        // unexcepted event.
                        // If the waiting flag is true,
                        // just send to modem side without parsing data.
                        mVsimRilIo.writeEvent(event);
                        setMdWaitingFlag(false);
                    }
                    break;

                case ExternalSimConstants.MSG_ID_UICC_POWER_DOWN_REQUEST: {
                    // get system property to check if vsim started
                    String inserted =  TelephonyManager.getDefault().getTelephonyProperty(
                            event.getFirstSlotId(),
                            TelephonyProperties.PROPERTY_EXTERNAL_SIM_INSERTED, "0");

                    if (inserted != null && inserted.length() > 0 && !"0".equals(inserted)) {
                        mVsimAdaptorIo.writeEvent(event);
                    }
                    break;
                }
                case ExternalSimConstants.MSG_ID_UICC_POWER_DOWN_RESPONSE:
                    mVsimRilIo.writeEvent(event);
                    break;

                case ExternalSimConstants.MSG_ID_GET_SERVICE_STATE_REQUEST:
                    handleServiceStateRequest(event);
                    break;
                default:
                    Rlog.d(TAG, "VsimEvenHandler handleMessage: default");
            }
        }
    }
}

