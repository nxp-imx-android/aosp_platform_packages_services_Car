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
package android.support.car.navigation;

import android.graphics.Bitmap;
import android.support.annotation.IntDef;
import android.support.car.Car;
import android.support.car.CarAppFocusManager;
import android.support.car.CarManagerBase;
import android.support.car.CarNotConnectedException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * APIs for providing navigation status to the instrument cluster. For cars that have a navigation
 * display built into the instrument cluster, a navigation application should also provide
 * turn-by-turn information to the cluster through this manager.
 * <p/>
 * Navigation applications should first call {@link CarAppFocusManager#requestAppFocus(int,
 * CarAppFocusManager.OnAppFocusOwnershipLostListener)} and request {@link
 * CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION}.
 * <p/>
 * After navigation focus is granted, applications should call {@code
 * sendNavigationStatus(STATUS_ACTIVE);} to initialize the cluster and let it know the app will be
 * sending turn events. Then, for each turn of the turn-by-turn guidance, the app calls {@link
 * #sendNavigationTurnEvent(int, String, int, int, int)}; this sends image data to the cluster
 * (and is why that data is not sent in subsequent turn distance events). To update the distance
 * and time to the next turn, the app should make periodic calls to {@link
 * #sendNavigationTurnDistanceEvent(int, int, int, int)}.
 * <p/>
 * Calling {@code sendNavigationStatus(STATUS_INACTIVE);} when the route is completed allows the
 * car to use the cluster panel for other data (such as media, weather, etc.) and is what a well
 * behaved app is expected to do.
 */
public interface CarNavigationStatusManager extends CarManagerBase {

    /**
     * Listener for navigation related events. Callbacks are called in the Looper context.
     */
    public interface CarNavigationCallback {
        /**
         * Instrument Cluster started in navigation mode.
         * @param manager The manager the callback is attached to.  Useful if the app wished to
         * unregister.
         * @param instrumentCluster An object describing the configuration and state of the car's
         * navigation instrument cluster.
         */
        void onInstrumentClusterStarted(CarNavigationStatusManager manager,
                CarNavigationInstrumentCluster instrumentCluster);

        /**
         * Instrument cluster ended.
         * @param manager The manager the callback is attached to.  Useful if the app wished to
         * unregister.
         */
        void onInstrumentClusterStopped(CarNavigationStatusManager manager);
    }

    /* Navigation statuses */
    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_INACTIVE = 2;

    /** @hide */
    @IntDef({
        STATUS_UNAVAILABLE,
        STATUS_ACTIVE,
        STATUS_INACTIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {}

    /* Turn Types */
    /** Turn is of an unknown type.*/
    public static final int TURN_UNKNOWN = 0;
    /** Starting point of the navigation. */
    public static final int TURN_DEPART = 1;
    /** No turn, but the street name changes. */
    public static final int TURN_NAME_CHANGE = 2;
    /** Slight turn. */
    public static final int TURN_SLIGHT_TURN = 3;
    /** Regular turn. */
    public static final int TURN_TURN = 4;
    /** Sharp turn. */
    public static final int TURN_SHARP_TURN = 5;
    /** U-turn. */
    public static final int TURN_U_TURN = 6;
    /** On ramp. */
    public static final int TURN_ON_RAMP = 7;
    /** Off ramp. */
    public static final int TURN_OFF_RAMP = 8;
    /** Road forks (diverges). */
    public static final int TURN_FORK = 9;
    /** Road merges. */
    public static final int TURN_MERGE = 10;
    /** Roundabout entrance on which the route ends. Instruction says "Enter roundabout". */
    public static final int TURN_ROUNDABOUT_ENTER = 11;
    /** Roundabout exit. */
    public static final int TURN_ROUNDABOUT_EXIT = 12;
    /**
     * Roundabout entrance and exit. For example, "At the roundabout, take Nth exit." Be sure to
     * specify the "turnNumber" parameter when using this type.
     */
    public static final int TURN_ROUNDABOUT_ENTER_AND_EXIT = 13;
    /** Potentially confusing intersection where the user should steer straight. */
    public static final int TURN_STRAIGHT = 14;
    /** You're on a boat! */
    public static final int TURN_FERRY_BOAT = 16;
    /** Train ferries for vehicles. */
    public static final int TURN_FERRY_TRAIN = 17;
    /** You have arrived. */
    public static final int TURN_DESTINATION = 19;

    /** @hide */
    @IntDef({
        TURN_UNKNOWN,
        TURN_DEPART,
        TURN_NAME_CHANGE,
        TURN_SLIGHT_TURN,
        TURN_TURN,
        TURN_SHARP_TURN,
        TURN_U_TURN,
        TURN_ON_RAMP,
        TURN_OFF_RAMP,
        TURN_FORK,
        TURN_MERGE,
        TURN_ROUNDABOUT_ENTER,
        TURN_ROUNDABOUT_EXIT,
        TURN_ROUNDABOUT_ENTER_AND_EXIT,
        TURN_STRAIGHT,
        TURN_FERRY_BOAT,
        TURN_FERRY_TRAIN,
        TURN_DESTINATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TurnEvent {}

    /* Turn Side */
    /** Turn is on the left side of the vehicle. */
    public static final int TURN_SIDE_LEFT = 1;
    /** Turn is on the right side of the vehicle. */
    public static final int TURN_SIDE_RIGHT = 2;
    /** Turn side is unspecified. */
    public static final int TURN_SIDE_UNSPECIFIED = 3;

    /** @hide */
    @IntDef({
        TURN_SIDE_LEFT,
        TURN_SIDE_RIGHT,
        TURN_SIDE_UNSPECIFIED
    })
    public @interface TurnSide {}

    /*
     * Distance units for use in {@link #sendNavigationTurnDistanceEvent(int, int, int, int)}.
     */
    /** Distance is specified in meters. */
    public static final int DISTANCE_METERS = 1;
    /** Distance is specified in kilometers. */
    public static final int DISTANCE_KILOMETERS = 2;
    /** Distance is specified in miles. */
    public static final int DISTANCE_MILES = 3;
    /** Distance is specified in feet. */
    public static final int DISTANCE_FEET = 4;
    /** Distance is specified in yards. */
    public static final int DISTANCE_YARDS = 5;

    /** @hide */
    @IntDef({
        DISTANCE_METERS,
        DISTANCE_KILOMETERS,
        DISTANCE_MILES,
        DISTANCE_FEET,
        DISTANCE_YARDS
    })
    public @interface DistanceUnit {}

    /**
     * Inform the instrument cluster if navigation is active or not.
     * @param status New instrument cluster navigation status, one of the STATUS_* constants in
     * this class.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    void sendNavigationStatus(@Status int status) throws CarNotConnectedException;

    /**
     * Send a Navigation Next Step event to the car.
     * <p/>
     * Roundabout Example: In a roundabout with four, evenly spaced exits, the
     * first exit is turnNumber=1, turnAngle=90; the second exit is turnNumber=2,
     * turnAngle=180; the third exit is turnNumber=3, turnAngle=270. turnNumber and turnAngle are
     * counted in the direction of travel around the roundabout (clockwise for roads where the car
     * drives on the left side of the road, such as Australia; counter-clockwise for roads
     * where the car drives on the right side of the road, such as the USA).
     *
     * @param turnEvent Turn event type ({@link #TURN_TURN}, {@link #TURN_U_TURN}, {@link
     * #TURN_ROUNDABOUT_ENTER_AND_EXIT}, etc).
     * @param eventName Name of the turn event like road name to turn. For example "Charleston road"
     *        in "Turn right to Charleston road"
     * @param turnAngle Turn angle in degrees between the roundabout entry and exit (0..359). Used
     * only for event type {@link #TURN_ROUNDABOUT_ENTER_AND_EXIT}. -1 if unused.
     * @param turnNumber Turn number, counting from the roundabout entry to the exit. Used only
     * for event type {@link #TURN_ROUNDABOUT_ENTER_AND_EXIT}. -1 if unused.
     * @param turnSide Turn side ({@link #TURN_SIDE_LEFT}, {@link #TURN_SIDE_RIGHT} or {@link
     * #TURN_SIDE_UNSPECIFIED}).
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    void sendNavigationTurnEvent(@TurnEvent int tuenEvent, CharSequence eventName, int turnAngle,
            int turnNumber, @TurnSide int turnSide) throws CarNotConnectedException;

    /**
     * Same as the public version ({@link #sendNavigationTurnEvent(int, String, int, int, Bitmap,
     * int)}) except a custom image can be sent to the cluster. See documentation for that method.
     *
     * @param image image to be shown in the instrument cluster. Null if instrument cluster type is
     * {@link CarNavigationInstrumentCluster.ClusterType#IMAGE_CODES_ONLY}, or if the image
     * parameters are malformed (length or width non-positive, or illegal imageColorDepthBits) in
     * the initial NavigationStatusService call.
     *
     * @hide only first party applications may send a custom image to the cluster.
     */
    void sendNavigationTurnEvent(@TurnEvent int turnEvent, CharSequence eventName, int turnAngle,
            int turnNumber, Bitmap image, @TurnSide int turnSide) throws CarNotConnectedException;

    /**
     * Send a Navigation Next Step Distance event to the car.
     *
     * @param distanceMeters Distance to next event in meters.
     * @param timeSeconds Time to next event in seconds.
     * @param displayDistanceMillis Distance to the next event formatted as it will be displayed by
     * the calling app, in milli-units. For example, 1.25 should be supplied as 1250.
     * @param displayDistanceUnit Unit type to use on of the DISTANCE_* types defined in this
     * file.
     * @return Returns {@code true} if successful.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    void sendNavigationTurnDistanceEvent(int distanceMeters, int timeSeconds,
            int displayDistanceMillis, int displayDistanceUnit) throws CarNotConnectedException;

    /**
     * @param callback {@link CarNavigationCallback} to be registered, replacing any existing
     * listeners.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    void addListener(CarNavigationCallback callback) throws CarNotConnectedException;

    /**
     * Unregister the {@link CarNavigationCallback} associated with this instance.
     */
    void removeListener();
}
