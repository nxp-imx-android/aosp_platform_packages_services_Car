/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

/**
 * Interface that abstracts activity manager operations
 */
public interface ActivityManagerInterface {
    /**
     * Sends a broadcast
     */
    void sendBroadcastAsUser(Intent intent, UserHandle user);

    /**
     * Default implementation of ActivityManagerInterface
     */
    class DefaultImpl implements ActivityManagerInterface {
        private final Context mContext;

        DefaultImpl(Context context) {
            mContext = context;
        }

        @Override
        public void sendBroadcastAsUser(@RequiresPermission Intent intent, UserHandle user) {
            mContext.sendBroadcastAsUser(intent, user);
        }
    }
}
