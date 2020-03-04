#
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

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := samples

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := MultiDisplayTest

LOCAL_DEX_PREOPT := false

LOCAL_JAVA_LIBRARIES += android.car

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.annotation_annotation \
    androidx.lifecycle_lifecycle-livedata \
    androidx.lifecycle_lifecycle-viewmodel \
    androidx.fragment_fragment \
    androidx.recyclerview_recyclerview

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
