#!/usr/bin/env python
#
# Copyright (C) 2015 The Android Open Source Project
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
"""Generate Java code from vehicle.h"""
import os
import re
import sys

JAVA_HEADER = \
"""
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

//Autogenerated from vehicle.h. Do not modify manually.

package com.android.car.vehiclenetwork;

public class VehicleNetworkConsts {
"""

JAVA_TRAIL = \
"""
}
"""

RE_PROPERTY_PATTERN = r'/\*\*(.*?)\*/\n\#define\s+VEHICLE_PROPERTY_(\S+)\s+(\S+)'
RE_ENUM_PATTERN = r'enum\s+(\S+)\s+\{\S*(.*?)\}'
RE_ENUM_ENTRY_PATTERN = r'(\S+)\s*=\s*(\S+)'

class PropertyInfo(object):
  def __init__(self, value, name):
    self.value = value
    self.name = name
    self.type = ""
    self.changeMode = ""
    self.access = ""
    self.unit = ""
    self.startEnd = 0 # _START/END property

  def __str__(self):
    r = ["value:" + self.value]
    r.append("name:" + self.name)
    if self.type != "":
      r.append("type:" + self.type)
    if self.changeMode != "":
      r.append("changeMode:" + self.changeMode)
    if self.access != "":
      r.append("access:" + self.access)
    if self.unit != "":
      r.append("unit:" + self.unit)
    return " ".join(r)

class EnumInfo(object):
  def __init__(self, name):
    self.name = name
    self.enums = [] #(name, value) tuple
  def addEntry(self, name, value):
    self.enums.append((name, value))
  def __str__(self):
    r = [self.name + "\n"]
    for e in self.enums:
      r.append("  " + e[0] + ":" + e[1] + "\n")
    return ''.join(r)

def toJavaStyleName(name):
  # do not convert if 1st letter is already upper
  if name[0].isupper():
    return name
  words = name.split("_")
  #print words
  for i in range(len(words)):
    w = words[i]
    w = w[0].upper() + w[1:]
    words[i] = w
  return ''.join(words)

JAVA_INT_DEF = "public static final int "
def printProperties(props):
  for p in props:
    print JAVA_INT_DEF + p.name + " = " + p.value + ";"

  #now impement getVehicleValueType
  print \
"""public static int getVehicleValueType(int property) {
switch (property) {"""
  for p in props:
    if p.type != "":
      print "case " + p.name + ": return VehicleValueType." + p.type + ";"
  print \
"""default: return VehicleValueType.VEHICLE_VALUE_TYPE_SHOUD_NOT_USE;
}
}
"""
  #now implement getVehiclePropertyName
  print \
"""public static String getVehiclePropertyName(int property) {
switch (property) {"""
  for p in props:
    if (p.startEnd == 0):
      print "case " + p.name + ': return "' + p.name +     '";'
  print \
"""default: return "UNKNOWN_PROPERTY";
}
}
"""
  #now implement getVehicleChangeMode
  print \
"""public static int[] getVehicleChangeMode(int property) {
switch (property) {"""
  for p in props:
    if p.changeMode != "":
      modes = p.changeMode.split('|')
      modesString = []
      for m in modes:
        modesString.append("VehiclePropChangeMode." + m)
      print "case " + p.name + ": return new int[] { " + " , ".join(modesString) + " };"
  print \
"""default: return null;
}
}
"""
  #now implement getVehicleAccess
  print \
"""public static int getVehicleAccess(int property) {
switch (property) {"""
  for p in props:
    if p.access != "":
      print "case " + p.name + ": return VehiclePropAccess." + p.access + ";"
  print \
"""default: return 0;
}
}
"""

def printEnum(e):
  print "public static class " + toJavaStyleName(e.name) + " {"
  for entry in e.enums:
    print JAVA_INT_DEF + entry[0] + " = " + entry[1] + ";"
  #now implement enumToString
  print \
"""public static String enumToString(int v) {
switch(v) {"""
  valueStore = []
  for entry in e.enums:
    # handling enum with the same value. Print only 1st one.
    if valueStore.count(entry[1]) == 0:
      valueStore.append(entry[1])
      print "case " + entry[0] + ': return "' + entry[0] + '";'
  print \
"""default: return "UNKNOWN";
}
}
}
"""

def printEnums(enums):
  for e in enums:
    printEnum(e)

def main(argv):
  vehicle_h_path = os.path.dirname(os.path.abspath(__file__)) + "/../../../../../hardware/libhardware/include/hardware/vehicle.h"
  #print vehicle_h_path
  f = open(vehicle_h_path, 'r')
  text = f.read()
  f.close()
  vehicle_internal_h_path = os.path.dirname(os.path.abspath(__file__)) + "/../include/vehicle-internal.h"
  f = open(vehicle_internal_h_path, 'r')
  text = text + f.read()
  f.close()

  props = []
  property_re = re.compile(RE_PROPERTY_PATTERN, re.MULTILINE | re.DOTALL)
  for match in property_re.finditer(text):
    words = match.group(1).split()
    name = "VEHICLE_PROPERTY_" + match.group(2)
    value = match.group(3)
    if (value[0] == "(" and value[-1] == ")"):
      value = value[1:-1]
    prop = PropertyInfo(value, name)
    i = 0
    while i < len(words):
      if words[i] == "@value_type":
        i += 1
        prop.type = words[i]
      elif words[i] == "@change_mode":
        i += 1
        prop.changeMode = words[i]
      elif words[i] == "@access":
        i += 1
        prop.access = words[i]
      elif words[i] == "@unit":
        i += 1
        prop.unit = words[i]
      elif words[i] == "@range_start" or words[i] == "@range_end":
        prop.startEnd = 1
      i += 1
    props.append(prop)
    #for p in props:
    #  print p

  enums = []
  enum_re = re.compile(RE_ENUM_PATTERN, re.MULTILINE | re.DOTALL)
  enum_entry_re = re.compile(RE_ENUM_ENTRY_PATTERN, re.MULTILINE)
  for match in enum_re.finditer(text):
    name = match.group(1)
    info = EnumInfo(name)
    for match_entry in enum_entry_re.finditer(match.group(2)):
      valueName = match_entry.group(1)
      value = match_entry.group(2)
      #print valueName, value
      if value[-1] == ',':
        value = value[:-1]
      info.addEntry(valueName, value)
    enums.append(info)
  #for e in enums:
  #  print e
  print JAVA_HEADER
  printProperties(props)
  printEnums(enums)
  print JAVA_TRAIL
if __name__ == '__main__':
  main(sys.argv)

