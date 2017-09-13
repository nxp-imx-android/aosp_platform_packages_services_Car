#!/usr/bin/env python3
#
# Copyright (C) 2017 The Android Open Source Project
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

# This script generates vhal_consts_x_y.py files for use in vhal_emulator
# They are generated from corresponding data in Vehicle HAL types.hal files
# To run, invoke at a shell by saying:
# $ packages/services/Car/tools/emulator/vhal_const_generate.py
# The script will automatically locate itself and the required HAL files and will write next
# to itself vhal_consts_x.y.py for any version of Vehicle HAL that it knows about
# Those files can then be used with vhal_emulator.py as per that script's documentation

from __future__ import print_function

import datetime

def printHeader(dest):
    year = datetime.datetime.now().year
    print("# Copyright (C) %s The Android Open Source Project" % year, file=dest)
    print("#", file=dest)
    print("# Licensed under the Apache License, Version 2.0 (the \"License\");", file=dest)
    print("# you may not use this file except in compliance with the License.", file=dest)
    print("# You may obtain a copy of the License at", file=dest)
    print("#", file=dest)
    print("#      http://www.apache.org/licenses/LICENSE-2.0", file=dest)
    print("#", file=dest)
    print("# Unless required by applicable law or agreed to in writing, software", file=dest)
    print("# distributed under the License is distributed on an \"AS IS\" BASIS,", file=dest)
    print("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.", file=dest)
    print("# See the License for the specific language governing permissions and", file=dest)
    print("# limitations under the License.", file=dest)
    print("#", file=dest)
    print("# DO NOT EDIT MANUALLY", file=dest)
    print("# This file was autogenerated by vhal_const_generate.py", file=dest)

def printEnum(doc, name, dest, postprocess=lambda x: x):
    # Construct a value name prefix from the group name
    valueNamePrefix = name.upper() + '_'

    enum_object = doc['enums'][name]
    print("\n# %s" % name, file=dest)
    for case in enum_object.cases:
        print('%s%s = %s' % (valueNamePrefix, case.name,
            postprocess(case.value.resolve(enum_object, doc))),
            file=dest)

import os, os.path
import sys

script_directory = os.path.join(os.path.dirname(os.path.abspath(__file__)))
parent_location = os.path.abspath(os.path.join(script_directory, '..'))
sys.path.append(parent_location)

# hidl_parser depends on a custom Python package that requires installation
# give user guidance should the import fail
try:
    from hidl_parser import parser
except ImportError as e:
    isPly = False
    pipTool = "pip%s" % ("3" if sys.version_info > (3,0) else "")
    if hasattr(e, 'name'):
        if e.name == 'ply': isPly = True
    elif hasattr(e, 'message'):
        if e.message.endswith('ply'): isPly = True
    if isPly:
        print('could not import ply.')
        print('ply is available as part of an Android checkout in external/ply')
        print('or it can be installed via "sudo %s install ply"' % pipTool)
        sys.exit(1)
    else:
        raise e

android_build_top = os.environ.get("ANDROID_BUILD_TOP", None)
if android_build_top is not None:
    vhal_location = os.path.join(android_build_top, 'hardware','interfaces','automotive','vehicle')
else:
    vhal_location = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
        '..','..','..','..','..','hardware','interfaces','automotive','vehicle'
    ))
if not(os.path.exists(vhal_location) and os.path.isdir(vhal_location)):
    print("Vehicle HAL was not found at %s. lunch may provide a correct environment, or files moved" % vhal_location)
    sys.exit(1)

def generateHal20():
    vhal_20_file = os.path.join(vhal_location, '2.0', 'types.hal')
    if not(os.path.exists(vhal_20_file)):
        print("Vehicle HAL was not found at %s. lunch may provide a correct environment, or files moved" % vhal_location)
        sys.exit(1)

    print("Generating constants from Vehicle HAL 2.0 (%s)" % (vhal_20_file))
    vhal_20_doc = parser.parse(vhal_20_file)

    vhal_20_file = open(os.path.join(script_directory, 'vhal_consts_2_0.py'), 'w')

    printHeader(vhal_20_file)

    for group in vhal_20_doc['enums']:
        print(group)
        printEnum(vhal_20_doc, group, vhal_20_file, lambda x : hex(x))

    print("\n# Create a container of value_type constants to be used by vhal_emulator", file=vhal_20_file)
    print("class vhal_types_2_0:", file=vhal_20_file)
    print("    TYPE_STRING  = [VEHICLEPROPERTYTYPE_STRING]", file=vhal_20_file)
    print("    TYPE_BYTES   = [VEHICLEPROPERTYTYPE_BYTES]", file=vhal_20_file)
    print("    TYPE_INT32   = [VEHICLEPROPERTYTYPE_BOOLEAN,", file=vhal_20_file)
    print("                    VEHICLEPROPERTYTYPE_INT32]", file=vhal_20_file)
    print("    TYPE_INT64   = [VEHICLEPROPERTYTYPE_INT64]", file=vhal_20_file)
    print("    TYPE_FLOAT   = [VEHICLEPROPERTYTYPE_FLOAT]", file=vhal_20_file)
    print("    TYPE_INT32S  = [VEHICLEPROPERTYTYPE_INT32_VEC]", file=vhal_20_file)
    print("    TYPE_FLOATS  = [VEHICLEPROPERTYTYPE_FLOAT_VEC]", file=vhal_20_file)
    print("    TYPE_COMPLEX = [VEHICLEPROPERTYTYPE_COMPLEX]", file=vhal_20_file)

generateHal20()
