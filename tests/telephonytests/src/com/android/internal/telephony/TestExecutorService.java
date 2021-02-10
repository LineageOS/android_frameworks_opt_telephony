/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestExecutorService implements ScheduledExecutorService {
    private static final String TAG = "TestExecutorService";

    private class CompletedFuture<T> implements Future<T>, ScheduledFuture<T> {

        private final Callable<T> mTask;
        private final long mDelayMs;
        private Runnable mRunnable;

        CompletedFuture(Callable<T> task) {
            mTask = task;
            mDelayMs = 0;
        }

        CompletedFuture(Callable<T> task, long delayMs) {
            mTask = task;
            mDelayMs = delayMs;
        }

        CompletedFuture(Runnable task, long delayMs) {
            mRunnable = task;
            mTask = (Callable<T>) Executors.callable(task);
            mDelayMs = delayMs;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelRunnable(mRunnable);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                return mTask.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return mTask.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            if (unit == TimeUnit.MILLISECONDS) {
                return mDelayMs;
            } else {
                // not implemented
                return 0;
            }
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == null) return 1;
            if (o.getDelay(TimeUnit.MILLISECONDS) > mDelayMs) return -1;
            if (o.getDelay(TimeUnit.MILLISECONDS) < mDelayMs) return 1;
            return 0;
        }
    }

    private long mClock = 0;
    private Map<Long, Runnable> mScheduledRunnables = new HashMap<>();
    private Map<Runnable, Long> mRepeatDuration = new HashMap<>();

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return null;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return new com.android.internal.telephony.TestExecutorService.CompletedFuture<>(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Future<?> submit(Runnable task) {
        task.run();
        return new com.android.internal.telephony.TestExecutorService.CompletedFuture<>(() -> null);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        // Schedule the runnable for execution at the specified time.
        long scheduledTime = getNextExecutionTime(delay, unit);
        mScheduledRunnables.put(scheduledTime, command);

        Log.i(TAG, "schedule: runnable=" + System.identityHashCode(command) + ", time="
                + scheduledTime);

        return new com.android.internal.telephony.TestExecutorService.CompletedFuture<Runnable>(
                command, delay);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
            TimeUnit unit) {
        return scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
            long delay, TimeUnit unit) {
        // Schedule the runnable for execution at the specified time.
        long nextScheduledTime = getNextExecutionTime(delay, unit);
        mScheduledRunnables.put(nextScheduledTime, command);
        mRepeatDuration.put(command, unit.toMillis(delay));

        return new com.android.internal.telephony.TestExecutorService.CompletedFuture<Runnable>(
                command, delay);
    }

    private long getNextExecutionTime(long delay, TimeUnit unit) {
        long delayMillis = unit.toMillis(delay);
        return mClock + delayMillis;
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    /**
     * Used in unit tests, used to add a delta to the "clock" so that we can fire off scheduled
     * items and reschedule the repeats.
     * @param duration The duration (millis) to add to the clock.
     */
    public void advanceTime(long duration) {
        Map<Long, Runnable> nextRepeats = new HashMap<>();
        List<Runnable> toRun = new ArrayList<>();
        mClock += duration;
        Iterator<Map.Entry<Long, Runnable>> iterator = mScheduledRunnables.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Runnable> entry = iterator.next();
            if (mClock >= entry.getKey()) {
                toRun.add(entry.getValue());

                Runnable r = entry.getValue();
                Log.i(TAG, "advanceTime: runningRunnable=" + System.identityHashCode(r));
                // If this is a repeating scheduled item, schedule the repeat.
                if (mRepeatDuration.containsKey(r)) {
                    // schedule next execution
                    nextRepeats.put(mClock + mRepeatDuration.get(r), entry.getValue());
                }
                iterator.remove();
            }
        }

        // Update things at the end to avoid concurrent access.
        mScheduledRunnables.putAll(nextRepeats);
        toRun.forEach(r -> r.run());
    }

    /**
     * Used from a {@link CompletedFuture} as defined above to cancel a scheduled task.
     * @param r The runnable to cancel.
     */
    private void cancelRunnable(Runnable r) {
        Optional<Map.Entry<Long, Runnable>> found = mScheduledRunnables.entrySet().stream()
                .filter(e -> e.getValue() == r)
                .findFirst();
        if (found.isPresent()) {
            mScheduledRunnables.remove(found.get().getKey());
        }
        mRepeatDuration.remove(r);
        Log.i(TAG, "cancelRunnable: runnable=" + System.identityHashCode(r));
    }
}

