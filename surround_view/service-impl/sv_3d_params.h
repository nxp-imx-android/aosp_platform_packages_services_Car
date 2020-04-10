/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef SV_3D_PARAMS_H
#define SV_3D_PARAMS_H

#include <vector>
#include <hidl/HidlSupport.h>

using ::android::hardware::hidl_vec;

static std::vector<android::hardware::hidl_vec<float>> kRecViews = {
    {0, -0.747409, 0.664364, 0, 1, 0, -0, 0, -0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {-0.382683, -0.690516, 0.613792, 0, 0.92388, -0.286021, 0.254241, 0, 0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {-0.707107, -0.528498, 0.469776, 0, 0.707107, -0.528498, 0.469776, 0, 0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {-0.92388, -0.286021, 0.254241, 0, 0.382683, -0.690516, 0.613792, 0, 0, 0.664364, 0.747409, 0, -1.19209e-07, 1.32873, -4.52598, 1},
    {-1, 3.26703e-08, -2.90403e-08, 0, -4.37114e-08, -0.747409, 0.664364, 0, 0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {-0.92388, 0.286021, -0.254241, 0, -0.382683, -0.690516, 0.613792, 0, 0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {-0.707107, 0.528498, -0.469776, 0, -0.707107, -0.528498, 0.469776, 0, 0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {-0.382683, 0.690516, -0.613792, 0, -0.92388, -0.286021, 0.254241, 0, 0, 0.664364, 0.747409, 0, 1.19209e-07, 1.32873, -4.52598, 1},
    {8.74228e-08, 0.747409, -0.664364, 0, -1, 6.53406e-08, -5.80805e-08, 0, 0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {0.382683, 0.690516, -0.613792, 0, -0.92388, 0.286021, -0.254241, 0, 0, 0.664364, 0.747409, 0, 1.19209e-07, 1.32873, -4.52598, 1},
    {0.707107, 0.528498, -0.469776, 0, -0.707107, 0.528498, -0.469776, 0, 0, 0.664364, 0.747409, 0, 1.19209e-07, 1.32873, -4.52598, 1},
    {0.92388, 0.286021, -0.254241, 0, -0.382684, 0.690516, -0.613792, 0, 0, 0.664364, 0.747409, 0, 1.19209e-07, 1.32873, -4.52598, 1},
    {1, -8.91277e-09, 7.92246e-09, 0, 1.19249e-08, 0.747409, -0.664364, 0, -0, 0.664364, 0.747409, 0, 3.55271e-15, 1.32873, -4.52598, 1},
    {0.92388, -0.286021, 0.254241, 0, 0.382684, 0.690516, -0.613792, 0, -0, 0.664364, 0.747409, 0, -0, 1.32873, -4.52598, 1},
    {0.707107, -0.528498, 0.469776, 0, 0.707107, 0.528498, -0.469776, 0, -0, 0.664364, 0.747409, 0, -1.19209e-07, 1.32873, -4.52598, 1},
    {0.382683, -0.690516, 0.613792, 0, 0.92388, 0.286021, -0.254241, 0, -0, 0.664364, 0.747409, 0, -1.19209e-07, 1.32873, -4.52598, 1},
};

#endif // SV_3D_PARAMS_H

