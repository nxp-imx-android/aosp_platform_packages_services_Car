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

package android.support.car.app.menu;

import android.os.Bundle;

import java.util.List;

interface ISubscriptionCallbacks {
        int getVersion() = 0;
        void onChildrenLoaded(String parentId, in List<Bundle> items) = 1;
        void onError(String id) = 2;
        void onChildChanged(String parentId, in Bundle bundle) = 3;
}
