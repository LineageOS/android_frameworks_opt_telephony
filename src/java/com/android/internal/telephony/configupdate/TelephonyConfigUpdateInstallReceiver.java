/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.configupdate;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.satellite.SatelliteConfigParser;
import com.android.server.updates.ConfigUpdateInstallReceiver;

import libcore.io.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class TelephonyConfigUpdateInstallReceiver extends ConfigUpdateInstallReceiver implements
        ConfigProviderAdaptor {

    private static final String TAG = "TelephonyConfigUpdateInstallReceiver";
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected static final String UPDATE_DIR = "/data/misc/telephonyconfig";
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected static final String UPDATE_CONTENT_PATH = "telephony_config.pb";
    protected static final String UPDATE_METADATA_PATH = "metadata/";
    public static final String VERSION = "version";

    private ConcurrentHashMap<Executor, Callback> mCallbackHashMap = new ConcurrentHashMap<>();
    @NonNull
    private final Object mConfigParserLock = new Object();
    @GuardedBy("mConfigParserLock")
    private ConfigParser mConfigParser;


    public static TelephonyConfigUpdateInstallReceiver sReceiverAdaptorInstance =
            new TelephonyConfigUpdateInstallReceiver();

    /**
     * @return The singleton instance of TelephonyConfigUpdateInstallReceiver
     */
    @NonNull
    public static TelephonyConfigUpdateInstallReceiver getInstance() {
        return sReceiverAdaptorInstance;
    }

    public TelephonyConfigUpdateInstallReceiver() {
        super(UPDATE_DIR, UPDATE_CONTENT_PATH, UPDATE_METADATA_PATH, VERSION);
    }

    /**
     * @return byte array type of config data protobuffer file
     */
    @Nullable
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public byte[] getCurrentContent() {
        try {
            return IoUtils.readFileAsByteArray(updateContent.getCanonicalPath());
        } catch (IOException e) {
            Slog.i(TAG, "Failed to read current content, assuming first update!");
            return null;
        }
    }

    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public void postInstall(Context context, Intent intent) {
        Log.d(TAG, "Telephony config is updated in file partition");
        ConfigParser updatedConfigParser = getNewConfigParser(DOMAIN_SATELLITE,
                getCurrentContent());

        if (updatedConfigParser == null) {
            Log.d(TAG, "updatedConfigParser is null");
            return;
        }

        boolean isParserChanged = false;

        synchronized (getInstance().mConfigParserLock) {
            if (getInstance().mConfigParser == null) {
                getInstance().mConfigParser = updatedConfigParser;
                isParserChanged = true;
            } else {
                int updatedVersion = updatedConfigParser.mVersion;
                int previousVersion = getInstance().mConfigParser.mVersion;
                Log.d(TAG, "previous version is " + previousVersion + " | updated version is "
                        + updatedVersion);
                if (updatedVersion > previousVersion) {
                    getInstance().mConfigParser = updatedConfigParser;
                    isParserChanged = true;
                }
            }
        }

        if (isParserChanged) {
            if (getInstance().mCallbackHashMap.keySet().isEmpty()) {
                Log.d(TAG, "mCallbackHashMap.keySet().isEmpty");
                return;
            }
            Iterator<Executor> iterator =
                    getInstance().mCallbackHashMap.keySet().iterator();
            while (iterator.hasNext()) {
                Executor executor = iterator.next();
                getInstance().mCallbackHashMap.get(executor).onChanged(
                        updatedConfigParser);
            }
        }
    }

    @Nullable
    @Override
    public ConfigParser getConfigParser(String domain) {
        Log.d(TAG, "getConfigParser");
        synchronized (getInstance().mConfigParserLock) {
            if (getInstance().mConfigParser == null) {
                Log.d(TAG, "CreateNewConfigParser with domain " + domain);
                getInstance().mConfigParser = getNewConfigParser(domain, getCurrentContent());
            }
            return getInstance().mConfigParser;
        }
    }

    @Override
    public void registerCallback(@NonNull Executor executor, @NonNull Callback callback) {
        mCallbackHashMap.put(executor, callback);
    }

    @Override
    public void unregisterCallback(@NonNull Callback callback) {
        Iterator<Executor> iterator = mCallbackHashMap.keySet().iterator();
        while (iterator.hasNext()) {
            Executor executor = iterator.next();
            if (mCallbackHashMap.get(executor) == callback) {
                mCallbackHashMap.remove(executor);
                break;
            }
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public File getUpdateDir() {
        return getInstance().updateDir;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public File getUpdateContent() {
        return getInstance().updateContent;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public ConcurrentHashMap<Executor, Callback> getCallbackMap() {
        return getInstance().mCallbackHashMap;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void setCallbackMap(ConcurrentHashMap<Executor, Callback> map) {
        getInstance().mCallbackHashMap = map;
    }

    /**
     * @param data byte array type of config data
     * @return when data is null, return null otherwise return ConfigParser
     */
    @Nullable
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public ConfigParser getNewConfigParser(String domain, @Nullable byte[] data) {
        if (data == null) {
            Log.d(TAG, "content data is null");
            return null;
        }
        switch (domain) {
            case DOMAIN_SATELLITE:
                return new SatelliteConfigParser(data);
            default:
                Log.e(TAG, "DOMAIN should be specified");
                return null;
        }
    }
}
