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
 */package android.automotive.computepipe.runner;

/**
 * Types of termination options
 */
@VintfStability
@Backing(type="int")
enum PipeTerminationConfigTerminationType {
    /**
     * run indefinetely until client stop.
     */
    CLIENT_STOP = 0,
    /**
     * run for minimum number of valid output packets
     */
    MIN_PACKET_COUNT,
    /**
     * run for fixed maximum duration, graph may still produce some packets
     * post run time, because of delayed signal.
     */
    MAX_RUN_TIME,
    /**
     * run until specified event.
     */
    EVENT,
}
