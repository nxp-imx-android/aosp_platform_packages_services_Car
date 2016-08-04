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
import android.support.car.CarNotConnectedException;

/**
 * @hide
 */
public class CarNavigationStatusManagerEmbedded implements CarNavigationStatusManager {

    private final android.car.navigation.CarNavigationManager mManager;

    public CarNavigationStatusManagerEmbedded(Object manager) {
        mManager = (android.car.navigation.CarNavigationManager) manager;
    }

    /**
     * @param status new instrument cluster navigation status.
     * @return true if successful.
     * @throws CarNotConnectedException
     */
    @Override
    public boolean sendNavigationStatus(int status) throws CarNotConnectedException {
        try {
            return mManager.sendNavigationStatus(status);
        } catch (android.car.CarNotConnectedException e) {
           throw new CarNotConnectedException(e);
        }
    }

    @Override
    public boolean sendNavigationTurnEvent(int event, String road, int turnAngle, int turnNumber,
            int turnSide) throws CarNotConnectedException {
        return sendNavigationTurnEvent(event, road, turnAngle, turnNumber, null, turnSide);
    }

    @Override
    public boolean sendNavigationTurnEvent(int event, String road, int turnAngle, int turnNumber,
            Bitmap image, int turnSide) throws CarNotConnectedException {
        try {
            return mManager.sendNavigationTurnEvent(event, road, turnAngle, turnNumber, image,
                    turnSide);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public boolean sendNavigationTurnDistanceEvent(int distanceMeters, int timeSeconds,
            int displayDistanceMillis, int displayDistanceUnit) throws CarNotConnectedException {
        try {
            return mManager.sendNavigationTurnDistanceEvent(distanceMeters, timeSeconds,
                    displayDistanceMillis, displayDistanceUnit);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        //nothing to do
    }

    /**
     * In this implementation we just immediately call {@code listener#onInstrumentClusterStart} as
     * we expect instrument cluster to be working all the time.
     *
     * @throws CarNotConnectedException
     */
    @Override
    public void registerListener(CarNavigationListener listener)
            throws CarNotConnectedException {

        try {
            listener.onInstrumentClusterStart(convert(mManager.getInstrumentClusterInfo()));
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void unregisterListener() {
        // Nothing to do.
    }

    private static CarNavigationInstrumentCluster convert(
            android.car.navigation.CarNavigationInstrumentCluster ic) {
        if (ic == null) {
            return null;
        }
        return new CarNavigationInstrumentCluster(ic.getMinIntervalMs(), ic.getType(),
                ic.getImageWidth(), ic.getImageHeight(), ic.getImageColorDepthBits());
    }
}
