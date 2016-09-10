/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.car;

/**
 * Enables applications to set and listen for the current application focus (such as active
 * navigation). Typically, only one such application should be running at a time. When another
 * application gets ownership of a given APP_FOCUS_TYPE_*, the old app should stop using the
 * feature represented by the focus type.
 */
public abstract class CarAppFocusManager implements CarManagerBase {
    /**
     * Receives notifications when app focus changes.
     */
    public interface AppFocusChangeListener {
        /**
         * Indicates the application focus has changed. The {@link CarAppFocusManager} instance
         * causing the change does not get this notification.
         * @param appType application type for which status changed
         * @param active returns {@code true} if active
         */
        void onAppFocusChange(int appType, boolean active);
    }

    /**
     * Receives notifications when the application has lost ownership.
     */
    public interface AppFocusOwnershipChangeListener {
        /**
         * Lost ownership for the focus, which occurs when another app has set the focus.
         * The app losing focus should stop the action associated with the focus.
         * For example, a navigation app running active navigation should stop navigation
         * upon getting this for {@link CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION}.
         * @param appType
         */
        void onAppFocusOwnershipLoss(int appType);
    }

    /**
     * Represents navigation focus.
     */
    public static final int APP_FOCUS_TYPE_NAVIGATION = 1;
    /**
     * Represents voice command focus.
     * @hide
     */
    public static final int APP_FOCUS_TYPE_VOICE_COMMAND = 2;
    /**
     * Update this after adding a new app type.
     * @hide
     */
    public static final int APP_FOCUS_TYPE_MAX = 2;

    /**
     * A failed focus change request.
     */
    public static final int APP_FOCUS_REQUEST_FAILED = 0;
    /**
     * A successful focus change request.
     */
    public static final int APP_FOCUS_REQUEST_GRANTED = 1;

    /**
     * Register listener to monitor app focus changes.
     * Multiple listeners can be registered for a single focus and the same listener can be used
     * for multiple focuses.
     * @param appType Application type to get notification for.
     * @param listener Listener to register for focus events.
     * @throws CarNotConnectedException
     */
    public abstract void addFocusListener(int appType, AppFocusChangeListener listener)
            throws CarNotConnectedException;

    /**
     * Unregister listener for app type and stop listening to focus change events.
     * @param appType Application type to get notification for.
     * @param listener Listener to unregister from focus events.
     * @throws CarNotConnectedException
     */
    public abstract void removeFocusListener(int appType, AppFocusChangeListener listener)
            throws CarNotConnectedException;

    /**
     * Unregister listener for all app types and stop listening to focus change events.
     * @param listener Listener to unregister from focus events.
     * @throws CarNotConnectedException
     */
    public abstract void removeFocusListener(AppFocusChangeListener listener)
            throws CarNotConnectedException;

    /**
     * Check if the current process owns the given focus.
     * @param appType Application type.
     * @param listener Listener that was used to request ownership.
     * @return Returns {@code true} if current listener owns focus for application type.
     * @throws CarNotConnectedException
     */
    public abstract boolean isOwningFocus(int appType, AppFocusOwnershipChangeListener listener)
            throws CarNotConnectedException;

    /**
     * Request application focus. By requesting this, the app gains the focus for this appType.
     * {@link AppFocusOwnershipChangeListener#onAppFocusOwnershipLoss(int)} will be sent to the app
     * that currently holds focus. The foreground app will have higher priority; other apps cannot
     * set the same focus while owner is in foreground.
     *
     * @param appType Application type to request focus for.
     * @param ownershipListener Ownership listener to request app focus for. Cannot be null.
     *
     * @return {@link #APP_FOCUS_REQUEST_FAILED} or {@link #APP_FOCUS_REQUEST_GRANTED}
     * @throws IllegalStateException if listener was not registered.
     * @throws SecurityException if owner cannot be changed.
     * @throws CarNotConnectedException
     */
    public abstract int requestAppFocus(int appType,
            AppFocusOwnershipChangeListener ownershipListener)
            throws IllegalStateException, SecurityException, CarNotConnectedException;

    /**
     * Abandon the given focus (mark it as inactive).
     * @param ownershipListener Ownership listener to abandon app focus for. Cannot be null.
     * @param appType Application type to abandon focus for.
     * @throws CarNotConnectedException
     */
    public abstract void abandonAppFocus(AppFocusOwnershipChangeListener ownershipListener,
            int appType) throws CarNotConnectedException;

    /**
     * Abandon all focuses (mark them as inactive).
     * @param ownershipListener Ownership listener to abandon focus for. Cannot be null.
     * @throws CarNotConnectedException
     */
    public abstract void abandonAppFocus(AppFocusOwnershipChangeListener ownershipListener)
            throws CarNotConnectedException;
}
