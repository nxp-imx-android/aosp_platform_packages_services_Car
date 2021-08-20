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

package com.android.car.telemetry;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@RunWith(JUnit4.class)
public class MetricsConfigStoreTest {
    private static final String NAME_FOO = "Foo";
    private static final String NAME_BAR = "Bar";
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_FOO =
            TelemetryProto.MetricsConfig.newBuilder().setName(NAME_FOO).build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_BAR =
            TelemetryProto.MetricsConfig.newBuilder().setName(NAME_BAR).build();

    private File mTestRootDir;
    private File mTestMetricsConfigDir;
    private MetricsConfigStore mMetricsConfigStore;

    @Before
    public void setUp() throws Exception {
        mTestRootDir = Files.createTempDirectory("car_telemetry_test").toFile();
        mTestMetricsConfigDir = new File(mTestRootDir, MetricsConfigStore.METRICS_CONFIG_DIR);

        mMetricsConfigStore = new MetricsConfigStore(mTestRootDir);
        assertThat(mTestMetricsConfigDir.exists()).isTrue();
    }

    @Test
    public void testRetrieveActiveMetricsConfigs_shouldSendConfigsToListener() throws Exception {
        writeConfigToDisk(METRICS_CONFIG_FOO);
        writeConfigToDisk(METRICS_CONFIG_BAR);

        List<TelemetryProto.MetricsConfig> result = mMetricsConfigStore.getActiveMetricsConfigs();

        assertThat(result).containsExactly(METRICS_CONFIG_FOO, METRICS_CONFIG_BAR);
    }

    @Test
    public void testAddMetricsConfig_shouldWriteConfigToDisk() throws Exception {
        boolean status = mMetricsConfigStore.addMetricsConfig(METRICS_CONFIG_FOO);

        assertThat(status).isTrue();
        assertThat(readConfigFromFile(NAME_FOO)).isEqualTo(METRICS_CONFIG_FOO);
    }

    @Test
    public void testDeleteMetricsConfig_whenNoConfig_shouldReturnFalse() throws Exception {
        boolean status = mMetricsConfigStore.deleteMetricsConfig(NAME_BAR);

        assertThat(status).isFalse();
    }

    @Test
    public void testDeleteMetricsConfig_shouldDeleteConfigFromDisk() throws Exception {
        writeConfigToDisk(METRICS_CONFIG_BAR);

        boolean status = mMetricsConfigStore.deleteMetricsConfig(NAME_BAR);

        assertThat(status).isTrue();
        assertThat(new File(mTestMetricsConfigDir, NAME_BAR).exists()).isFalse();
    }

    private void writeConfigToDisk(TelemetryProto.MetricsConfig config) throws Exception {
        File file = new File(mTestMetricsConfigDir, config.getName());
        Files.write(file.toPath(), config.toByteArray());
        assertThat(file.exists()).isTrue();
    }

    private TelemetryProto.MetricsConfig readConfigFromFile(String fileName) throws Exception {
        byte[] bytes = Files.readAllBytes(new File(mTestMetricsConfigDir, fileName).toPath());
        return TelemetryProto.MetricsConfig.parseFrom(bytes);
    }
}
