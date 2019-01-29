#
# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true

LOCAL_MODULE_TAGS := optional
LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.car_car \
    car-theme-lib \
    androidx.transition_transition \
    androidx.legacy_legacy-support-v4 \
    androidx-constraintlayout_constraintlayout

LOCAL_MODULE_OWNER := google
LOCAL_PACKAGE_NAME := ThemePlayground
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_STATIC_JAVA_LIBRARIES := \
         androidx-constraintlayout_constraintlayout-solver

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JAVA_LIBRARIES += android.car

include $(BUILD_PACKAGE)
