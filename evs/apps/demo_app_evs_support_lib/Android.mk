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

##################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    evs_app_support_lib.cpp \

LOCAL_SHARED_LIBRARIES := \
    liblog \
    libutils \
    libui \
    libhidlbase \
    libhardware \
    android.hardware.automotive.evs@1.0 \
    android.hardware.automotive.vehicle@2.0 \
    libevssupport

LOCAL_STATIC_LIBRARIES := \
    libjsoncpp \

LOCAL_C_INCLUDES += packages/services/Car/evs/support_library

LOCAL_STRIP_MODULE := keep_symbols

LOCAL_INIT_RC := evs_app_support_lib.rc

LOCAL_MODULE:= evs_app_support_lib
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -DLOG_TAG=\"EvsAppSupportLib\"
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES
LOCAL_CFLAGS += -Wall -Werror -Wunused -Wunreachable-code

include $(BUILD_EXECUTABLE)
