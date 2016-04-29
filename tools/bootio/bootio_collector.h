/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef BOOTIO_COLLECTOR_H_
#define BOOTIO_COLLECTOR_H_

#include <string>
#include <android-base/macros.h>

class BootioCollector {
public:
  BootioCollector(std::string path);

  void StartDataCollection(int timeout, int samples);

  void Print();

private:
  std::string getStoragePath();

  std::string path_;

  DISALLOW_COPY_AND_ASSIGN(BootioCollector);
};

#endif  // BOOTIO_COLLECTOR_H_
