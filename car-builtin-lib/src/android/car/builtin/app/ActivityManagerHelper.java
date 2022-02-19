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

package android.car.builtin.app;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.car.builtin.util.Slogf;
import android.os.Bundle;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Provide access to {@code android.app.IActivityManager} calls.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ActivityManagerHelper {

    /** Invalid task ID. */
    public static final int INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID;

    private static final String TAG = "CAR.AM";  // CarLog.TAG_AM

    private static Object sLock = new Object();

    private IActivityManager mAm;

    @GuardedBy("sLock")
    private static ActivityManagerHelper sActivityManagerBuiltIn;

    private ActivityManagerHelper() {
        mAm = ActivityManager.getService();
    }

    /**
     * Get instance of {@code IActivityManagerBuiltIn}
     */
    @NonNull
    public static ActivityManagerHelper getInstance() {
        synchronized (sLock) {
            if (sActivityManagerBuiltIn == null) {
                sActivityManagerBuiltIn = new ActivityManagerHelper();
            }

            return sActivityManagerBuiltIn;
        }
    }

    /**
     * See {@code android.app.IActivityManager.startUserInBackground}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public boolean startUserInBackground(@UserIdInt int userId) {
        return runRemotely(() -> mAm.startUserInBackground(userId),
                "error while startUserInBackground %d", userId);
    }

    /**
     * See {@code android.app.IActivityManager.startUserInForegroundWithListener}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public boolean startUserInForeground(@UserIdInt int userId) {
        return runRemotely(
                () -> mAm.startUserInForegroundWithListener(userId, /* listener= */ null),
                "error while startUserInForeground %d", userId);
    }

    /**
     * See {@code android.app.IActivityManager.stopUserWithDelayedLocking}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public int stopUserWithDelayedLocking(@UserIdInt int userId, boolean force) {
        return runRemotely(
                () -> mAm.stopUserWithDelayedLocking(userId, force, /* callback= */ null),
                "error while stopUserWithDelayedLocking %d", userId);
    }

    /**
     * Check {@code android.app.IActivityManager.unlockUser}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public boolean unlockUser(@UserIdInt int userId) {
        return runRemotely(() -> mAm.unlockUser(userId,
                /* token= */ null, /* secret= */ null, /* listener= */ null),
                "error while unlocking user %d", userId);
    }

    /**
     * Stops all task for the user.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    public void stopAllTasksForUser(@UserIdInt int userId) {
        try {
            for (RootTaskInfo info : mAm.getAllRootTaskInfos()) {
                for (int i = 0; i < info.childTaskIds.length; i++) {
                    if (info.childTaskUserIds[i] == userId) {
                        int taskId = info.childTaskIds[i];
                        if (!mAm.removeTask(taskId)) {
                            Slogf.w(TAG, "could not remove task " + taskId);
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            throw logAndReThrow(e, "could not get stack info for user %d", userId);
        }
    }

    /**
     * Creates an ActivityOptions from the Bundle generated from ActivityOptions.
     */
    @NonNull
    public static ActivityOptions createActivityOptions(@NonNull Bundle bOptions) {
        return new ActivityOptions(bOptions);
    }

    private <T> T runRemotely(Callable<T> callable, String format, Object...args) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw logAndReThrow(e, format, args);
        }
    }

    private RuntimeException logAndReThrow(Exception e, String format, Object...args) {
        String msg = String.format(format, args);
        Slogf.e(TAG, msg, e);
        return new IllegalStateException(msg, e);
    }

    private final Object mLock = new Object();

    /**
     * Makes the root task of the given taskId focused.
     */
    public void setFocusedRootTask(int taskId) {
        try {
            mAm.setFocusedRootTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to setFocusedRootTask", e);
        }
    }

    /**
     * Removes the given task.
     */
    public boolean removeTask(int taskId) {
        try {
            return mAm.removeTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to removeTask", e);
        }
        return false;
    }

    public interface ProcessObserverCallback {
        default void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        }
        default void onProcessDied(int pid, int uid) {}
    }

    @GuardedBy("mLock")
    private ArrayList<ProcessObserverCallback> mProcessObserverCallbacks = new ArrayList<>();

    private final IProcessObserver.Stub mProcessObserver = new IProcessObserver.Stub() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities)
                throws RemoteException {
            List<ProcessObserverCallback> callbacks;
            synchronized (mLock) {
                callbacks = mProcessObserverCallbacks;
            }
            for (int i = 0, size = callbacks.size(); i < size; ++i) {
                callbacks.get(i).onForegroundActivitiesChanged(pid, uid, foregroundActivities);
            }
        }

        @Override
        public void onForegroundServicesChanged(int pid, int uid, int fgServiceTypes)
                throws RemoteException {
            // Not used
        }

        @Override
        public void onProcessDied(int pid, int uid) throws RemoteException {
            List<ProcessObserverCallback> callbacks;
            synchronized (mLock) {
                callbacks = mProcessObserverCallbacks;
            }
            for (int i = 0, size = callbacks.size(); i < size; ++i) {
                callbacks.get(i).onProcessDied(pid, uid);
            }
        }
    };

    /**
     * Registers a callback to be invoked when the process states are changed.
     * @param callback a callback to register
     */
    public void registerProcessObserverCallback(ProcessObserverCallback callback) {
        synchronized (mLock) {
            if (mProcessObserverCallbacks.isEmpty()) {
                try {
                    mAm.registerProcessObserver(mProcessObserver);
                } catch (RemoteException e) {
                    Slogf.e(TAG, "Failed to register ProcessObserver", e);
                    throw new RuntimeException(e);
                }
            }
            // Make a copy of callbacks not to affect on-going callbacks.
            mProcessObserverCallbacks =
                    (ArrayList<ProcessObserverCallback>) mProcessObserverCallbacks.clone();
            mProcessObserverCallbacks.add(callback);
        }
    }

    /**
     * Unregisters the given callback.
     * @param callback a callback to unregister
     */
    public void unregisterProcessObserverCallback(ProcessObserverCallback callback) {
        synchronized (mLock) {
            // Make a copy of callbacks not to affect on-going callbacks.
            mProcessObserverCallbacks =
                    (ArrayList<ProcessObserverCallback>) mProcessObserverCallbacks.clone();
            mProcessObserverCallbacks.remove(callback);
            if (mProcessObserverCallbacks.isEmpty()) {
                try {
                    mAm.unregisterProcessObserver(mProcessObserver);
                } catch (RemoteException e) {
                    Slogf.e(TAG, "Failed to unregister listener", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Same as {@link ActivityManager#checkComponentPermission(String, int, int, boolean).
     */
    public static int checkComponentPermission(@NonNull String permission, int uid, int owningUid,
            boolean exported) {
        return ActivityManager.checkComponentPermission(permission, uid, owningUid, exported);
    }
}
