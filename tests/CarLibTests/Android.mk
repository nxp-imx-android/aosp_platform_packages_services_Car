# Copyright (C) 2019 The Android Open Source Project
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
#

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CarLibTests

LOCAL_MODULE_TAGS := tests
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JAVA_LIBRARIES := \
    android.car \
    android.test.runner \
    android.test.base \

LOCAL_STATIC_JAVA_LIBRARIES := \
    junit \
    android.car.testapi \
    androidx.test.rules \
    androidx.test.core \
    mockito-target-minus-junit4 \
    com.android.car.test.utils \
    truth-prebuilt \

include $(BUILD_PACKAGE)
