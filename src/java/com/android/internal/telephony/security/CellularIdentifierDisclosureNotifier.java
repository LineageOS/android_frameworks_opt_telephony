/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.security;

import android.telephony.CellularIdentifierDisclosure;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.Rlog;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encapsulates logic to emit notifications to the user that their cellular identifiers were
 * disclosed in the clear. Callers add CellularIdentifierDisclosure instances by calling
 * addDisclosure.
 *
 * <p>This class is thread safe and is designed to do costly work on worker threads. The intention
 * is to allow callers to add disclosures from a Looper thread without worrying about blocking for
 * IPC.
 *
 * @hide
 */
public class CellularIdentifierDisclosureNotifier {

    private static final String TAG = "CellularIdentifierDisclosureNotifier";
    private static final long DEFAULT_WINDOW_CLOSE_DURATION_IN_MINUTES = 15;
    private static CellularIdentifierDisclosureNotifier sInstance = null;
    private final long mWindowCloseDuration;
    private final TimeUnit mWindowCloseUnit;
    private final Object mEnabledLock = new Object();

    @GuardedBy("mEnabledLock")
    private boolean mEnabled = false;
    // This is a single threaded executor. This is important because we want to ensure certain
    // events are strictly serialized.
    private ScheduledExecutorService mSerializedWorkQueue;

    private AtomicInteger mDisclosureCount;

    // One should only interact with this future from within the work queue's thread.
    private ScheduledFuture<?> mWhenWindowCloses;

    public CellularIdentifierDisclosureNotifier() {
        this(Executors.newSingleThreadScheduledExecutor(), DEFAULT_WINDOW_CLOSE_DURATION_IN_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * Construct a CellularIdentifierDisclosureNotifier by injection. This should only be used for
     * testing.
     *
     * @param notificationQueue a ScheduledExecutorService that should only execute on a single
     *     thread.
     */
    @VisibleForTesting
    public CellularIdentifierDisclosureNotifier(
            ScheduledExecutorService notificationQueue,
            long windowCloseDuration,
            TimeUnit windowCloseUnit) {
        mSerializedWorkQueue = notificationQueue;
        mWindowCloseDuration = windowCloseDuration;
        mWindowCloseUnit = windowCloseUnit;
        mDisclosureCount = new AtomicInteger(0);
    }

    /**
     * Add a CellularIdentifierDisclosure to be tracked by this instance.
     * If appropriate, this will trigger a user notification.
     */
    public void addDisclosure(CellularIdentifierDisclosure disclosure) {
        Rlog.d(TAG, "Identifier disclosure reported: " + disclosure);

        synchronized (mEnabledLock) {
            if (!mEnabled) {
                Rlog.d(TAG, "Skipping disclosure because notifier was disabled.");
                return;
            }

            // Don't notify if this disclosure happened in service of an emergency. That's a user
            // initiated action that we don't want to interfere with.
            if (disclosure.isEmergency()) {
                Rlog.i(TAG, "Ignoring identifier disclosure associated with an emergency.");
                return;
            }

            // Schedule incrementAndNotify from within the lock because we're sure at this point
            // that we're enabled. This allows incrementAndNotify to avoid re-checking mEnabled
            // because we know that any actions taken on disabled will be scheduled after this
            // incrementAndNotify call.
            try {
                mSerializedWorkQueue.execute(incrementAndNotify());
            } catch (RejectedExecutionException e) {
                Rlog.e(TAG, "Failed to schedule incrementAndNotify: " + e.getMessage());
            }
        } // end mEnabledLock
    }

    /**
     * Re-enable if previously disabled. This means that {@code addDisclsoure} will start tracking
     * disclosures again and potentially emitting notifications.
     */
    public void enable() {
        synchronized (mEnabledLock) {
            Rlog.d(TAG, "enabled");
            mEnabled = true;
            try {
                mSerializedWorkQueue.execute(onEnableNotifier());
            } catch (RejectedExecutionException e) {
                Rlog.e(TAG, "Failed to schedule onEnableNotifier: " + e.getMessage());
            }
        }
    }

    /**
     * Clear all internal state and prevent further notifications until optionally re-enabled.
     * This can be used to in response to a user disabling the feature to emit notifications.
     * If {@code addDisclosure} is called while in a disabled state, disclosures will be dropped.
     */
    public void disable() {
        Rlog.d(TAG, "disabled");
        synchronized (mEnabledLock) {
            mEnabled = false;
            try {
                mSerializedWorkQueue.execute(onDisableNotifier());
            } catch (RejectedExecutionException e) {
                Rlog.e(TAG, "Failed to schedule onDisableNotifier: " + e.getMessage());
            }
        }
    }

    public boolean isEnabled() {
        synchronized (mEnabledLock) {
            return mEnabled;
        }
    }

    @VisibleForTesting
    public int getCurrentDisclosureCount() {
        return mDisclosureCount.get();
    }

    /** Get a singleton CellularIdentifierDisclosureNotifier. */
    public static synchronized CellularIdentifierDisclosureNotifier getInstance() {
        if (sInstance == null) {
            sInstance = new CellularIdentifierDisclosureNotifier();
        }

        return sInstance;
    }

    private Runnable closeWindow() {
        return () -> {
            Rlog.i(TAG,
                    "Disclosure window closing. Disclosure count was " + mDisclosureCount.get());
            mDisclosureCount.set(0);
        };
    }

    private Runnable incrementAndNotify() {
        return () -> {
            int newCount = mDisclosureCount.incrementAndGet();
            Rlog.d(TAG, "Emitting notification. New disclosure count " + newCount);

            // To reset the timer for our window, we first cancel an existing timer.
            boolean cancelled = cancelWindowCloseFuture();
            Rlog.d(TAG, "Result of attempting to cancel window closing future: " + cancelled);

            try {
                mWhenWindowCloses =
                        mSerializedWorkQueue.schedule(
                                closeWindow(), mWindowCloseDuration, mWindowCloseUnit);
            } catch (RejectedExecutionException e) {
                Rlog.e(TAG, "Failed to schedule closeWindow: " + e.getMessage());
            }
        };
    }

    private Runnable onDisableNotifier() {
        return () -> {
            mDisclosureCount.set(0);
            cancelWindowCloseFuture();
            Rlog.d(TAG, "On disable notifier");
        };
    }

    private Runnable onEnableNotifier() {
        return () -> {
            Rlog.i(TAG, "On enable notifier");
        };
    }

    /**
     * A helper to cancel the Future that is in charge of closing the disclosure window. This must
     * only be called from within the single-threaded executor. Calling this method leaves a
     * completed or cancelled future in mWhenWindowCloses.
     *
     * @return boolean indicating whether or not the Future was actually cancelled. If false, this
     * likely indicates that the disclosure window has already closed.
     */
    private boolean cancelWindowCloseFuture() {
        if (mWhenWindowCloses == null) {
            return false;
        }

        // While we choose not to interrupt a running Future (we pass `false` to the `cancel`
        // call), we shouldn't ever actually need this functionality because all the work on the
        // queue is serialized on a single thread. Nothing about the `closeWindow` call is ready
        // to handle interrupts, though, so this seems like a safer choice.
        return mWhenWindowCloses.cancel(false);
    }
}
