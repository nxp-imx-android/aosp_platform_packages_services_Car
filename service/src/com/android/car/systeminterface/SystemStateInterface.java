/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.systeminterface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Pair;
import com.android.car.procfsinspector.ProcessInfo;
import com.android.car.procfsinspector.ProcfsInspector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Interface that abstracts system status (booted, sleeping, ...) operations
 */
public interface SystemStateInterface {
    void shutdown();
    void enterDeepSleep(int wakeupTimeSec);
    void scheduleActionForBootCompleted(Runnable action, Duration delay);

    default boolean isWakeupCausedByTimer() {
        //TODO bug: 32061842, check wake up reason and do necessary operation information should
        // come from kernel. it can be either power on or wake up for maintenance
        // power on will involve GPIO trigger from power controller
        // its own wakeup will involve timer expiration.
        return false;
    }

    default boolean isSystemSupportingDeepSleep() {
        //TODO should return by checking some kernel suspend control sysfs, bug: 32061842
        return false;
    }

    default List<ProcessInfo> getRunningProcesses() {
        return ProcfsInspector.readProcessTable();
    }

    class DefaultImpl implements SystemStateInterface {
        private final static Duration MIN_BOOT_COMPLETE_ACTION_DELAY = Duration.ofSeconds(10);

        private final Context mContext;
        private final PowerManager mPowerManager;
        private List<Pair<Runnable, Duration>> mActionsList = new ArrayList<>();
        private ScheduledExecutorService mExecutorService;
        private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                    for (Pair<Runnable, Duration> action : mActionsList) {
                        mExecutorService.schedule(action.first,
                            action.second.toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
            }
        };

        DefaultImpl(Context context) {
            mContext = context;
            mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        }

        @Override
        public void shutdown() {
            mPowerManager.shutdown(false /* no confirm*/, null, true /* true */);
        }

        @Override
        public void enterDeepSleep(int wakeupTimeSec) {
            //TODO set wake up time, bug: 32061842
            mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN,
                PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay) {
            if (MIN_BOOT_COMPLETE_ACTION_DELAY.compareTo(delay) < 0) {
                // TODO: consider adding some degree of randomness here
                delay = MIN_BOOT_COMPLETE_ACTION_DELAY;
            }
            if (mActionsList.isEmpty()) {
                final int corePoolSize = 1;
                mExecutorService = Executors.newScheduledThreadPool(corePoolSize);
                IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
                mContext.registerReceiver(mBroadcastReceiver, intentFilter);
            }
            mActionsList.add(Pair.create(action, delay));
        }

    }
}
