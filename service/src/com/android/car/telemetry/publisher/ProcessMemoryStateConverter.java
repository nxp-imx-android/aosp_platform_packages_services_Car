/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.telemetry.publisher;

import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessMemoryState;

/**
 * Atom data converter for atoms of type {@link ProcessMemoryState}.
 */
public class ProcessMemoryStateConverter extends AbstractAtomConverter<ProcessMemoryState> {
    private static final SparseArray<AtomFieldAccessor<ProcessMemoryState>> sAtomFieldAccessorMap =
            new SparseArray<>();
    static {
        sAtomFieldAccessorMap.append(1, new AtomFieldAccessor<>(
                "uid",
                a -> a.hasUid(),
                a -> a.getUid()
        ));
        sAtomFieldAccessorMap.append(2, new AtomFieldAccessor<>(
                "process_name",
                a -> a.hasProcessName(),
                a -> a.getProcessName()
        ));
        sAtomFieldAccessorMap.append(3, new AtomFieldAccessor<>(
                "oom_adj_score",
                a -> a.hasOomAdjScore(),
                a -> a.getOomAdjScore()
        ));
        sAtomFieldAccessorMap.append(4, new AtomFieldAccessor<>(
                "page_fault",
                a -> a.hasPageFault(),
                a -> a.getPageFault()
        ));
        sAtomFieldAccessorMap.append(5, new AtomFieldAccessor<>(
                "page_major_fault",
                a -> a.hasPageMajorFault(),
                a -> a.getPageMajorFault()
        ));
        sAtomFieldAccessorMap.append(6, new AtomFieldAccessor<>(
                "rss_in_bytes",
                a -> a.hasRssInBytes(),
                a -> a.getRssInBytes()
        ));
        sAtomFieldAccessorMap.append(7, new AtomFieldAccessor<>(
                "cache_in_bytes",
                a -> a.hasCacheInBytes(),
                a -> a.getCacheInBytes()
        ));
        sAtomFieldAccessorMap.append(8, new AtomFieldAccessor<>(
                "swap_in_bytes",
                a -> a.hasSwapInBytes(),
                a -> a.getSwapInBytes()
        ));
    }

    ProcessMemoryStateConverter() {
        super();
    }

    @Override
    SparseArray<AtomFieldAccessor<ProcessMemoryState>> getAtomFieldAccessorMap() {
        return sAtomFieldAccessorMap;
    }

    @Override
    ProcessMemoryState getAtomData(Atom atom) {
        return atom.getProcessMemoryState();
    }

    @Override
    String getAtomDataClassName() {
        return ProcessMemoryState.class.getSimpleName();
    }
}