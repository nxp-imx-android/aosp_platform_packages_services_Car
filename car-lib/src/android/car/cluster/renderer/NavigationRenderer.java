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
package android.car.cluster.renderer;

import android.annotation.SystemApi;
import android.annotation.UiThread;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.os.Bundle;

/**
 * Contains methods specified for Navigation App renderer in instrument cluster.
 *
 * @hide
 */
@SystemApi
@UiThread
public abstract class NavigationRenderer {
    /**
     * Returns properties of instrument cluster for navigation.
     */
    public abstract CarNavigationInstrumentCluster getNavigationProperties();

    /**
     * Called when an event is fired to change the navigation state.
     */
    public abstract void onEvent(int eventType, Bundle bundle);
}
