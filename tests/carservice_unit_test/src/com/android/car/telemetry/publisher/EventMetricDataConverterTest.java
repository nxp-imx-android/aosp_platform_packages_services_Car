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

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured;
import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.StatsLogProto.EventMetricData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class EventMetricDataConverterTest {
    @Test
    public void testConvertEventDataList_putsCorrectDataIntoPersistableBundle()
            throws StatsConversionException {
        List<EventMetricData> eventDataList = Arrays.asList(
                EventMetricData.newBuilder()
                        .setElapsedTimestampNanos(12345678L)
                        .setAtom(Atom.newBuilder()
                                .setAppStartMemoryStateCaptured(
                                        AppStartMemoryStateCaptured.newBuilder()
                                                .setUid(1000)
                                                .setActivityName("activityName1")
                                                .setRssInBytes(1234L)))
                        .build(),
                EventMetricData.newBuilder()
                        .setElapsedTimestampNanos(23456789L)
                        .setAtom(Atom.newBuilder()
                                .setAppStartMemoryStateCaptured(
                                        AppStartMemoryStateCaptured.newBuilder()
                                                .setUid(1100)
                                                .setActivityName("activityName2")
                                                .setRssInBytes(2345L)))
                        .build()
        );
        SparseArray<AtomFieldAccessor<AppStartMemoryStateCaptured>> accessorMap =
                new AppStartMemoryStateCapturedConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = EventMetricDataConverter.convertEventDataList(eventDataList);

        assertThat(bundle.size()).isEqualTo(4);
        assertThat(bundle.getLongArray(EventMetricDataConverter.ELAPSED_TIME_NANOS))
            .asList().containsExactly(12345678L, 23456789L).inOrder();
        assertThat(bundle.getIntArray(accessorMap.get(1).getFieldName()))
            .asList().containsExactly(1000, 1100).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(accessorMap.get(3).getFieldName())))
            .containsExactly("activityName1", "activityName2").inOrder();
        assertThat(bundle.getLongArray(accessorMap.get(6).getFieldName()))
            .asList().containsExactly(1234L, 2345L).inOrder();
    }
}