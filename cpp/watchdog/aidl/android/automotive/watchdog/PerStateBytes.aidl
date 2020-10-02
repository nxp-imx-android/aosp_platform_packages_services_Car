/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.automotive.watchdog;

/**
 * Structure that describes the number of bytes attribute to each state of the application and
 * system.
 */
@VintfStability
parcelable PerStateBytes {
  /**
   * Number of bytes attributed to the application foreground mode.
   */
  long applicationForegroundBytes;

  /**
   * Number of bytes attributed to the application background mode.
   */
  long applicationBackgroundBytes;

  /**
   * Number of bytes attributed to the system garage mode.
   */
  long systemGarageModeBytes;
}
