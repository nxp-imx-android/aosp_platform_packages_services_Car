/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car;

import static android.car.telemetry.CarTelemetryManager.STATUS_METRICS_CONFIG_ALREADY_EXISTS;
import static android.car.telemetry.CarTelemetryManager.STATUS_METRICS_CONFIG_PARSE_FAILED;
import static android.car.telemetry.CarTelemetryManager.STATUS_METRICS_CONFIG_SUCCESS;
import static android.car.telemetry.CarTelemetryManager.STATUS_METRICS_CONFIG_VERSION_TOO_OLD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.car.Car;
import android.car.telemetry.CarTelemetryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.telemetry.CarTelemetryService;
import com.android.car.telemetry.TelemetryProto;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Test the public entry points for the CarTelemetryManager. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarTelemetryManagerTest extends MockedCarTestBase {
    private static final long TIMEOUT_MS = 5_000L;
    private static final String TAG = CarTelemetryManagerTest.class.getSimpleName();
    private static final byte[] INVALID_METRICS_CONFIG = "bad config".getBytes();
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final String CONFIG_NAME = "my_metrics_config";
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName(CONFIG_NAME).setVersion(1).setScript("no-op").build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_V2 =
            METRICS_CONFIG_V1.toBuilder().setVersion(2).build();

    private final AddMetricsConfigCallbackImpl mAddMetricsConfigCallback =
            new AddMetricsConfigCallbackImpl();
    private final FakeCarTelemetryResultsListener mListener = new FakeCarTelemetryResultsListener();
    private final HandlerThread mTelemetryThread =
            CarServiceUtils.getHandlerThread(CarTelemetryService.class.getSimpleName());
    private final Handler mHandler = new Handler(mTelemetryThread.getLooper());

    private CarTelemetryManager mCarTelemetryManager;
    private CountDownLatch mIdleHandlerLatch = new CountDownLatch(1);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(getCar().isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE));

        mTelemetryThread.getLooper().getQueue().addIdleHandler(() -> {
            mIdleHandlerLatch.countDown();
            return true;
        });

        Log.i(TAG, "attempting to get CAR_TELEMETRY_SERVICE");
        mCarTelemetryManager = (CarTelemetryManager) getCar().getCarManager(
                Car.CAR_TELEMETRY_SERVICE);
        mCarTelemetryManager.setListener(DIRECT_EXECUTOR, mListener);
    }

    @Test
    public void testSetClearListener() {
        mCarTelemetryManager.clearListener();
        mCarTelemetryManager.setListener(DIRECT_EXECUTOR, mListener);

        // setListener multiple times should fail
        assertThrows(IllegalStateException.class,
                () -> mCarTelemetryManager.setListener(DIRECT_EXECUTOR, mListener));
    }

    @Test
    public void testApiInvocationWithoutSettingListener() {
        mCarTelemetryManager.clearListener();

        assertThrows(IllegalStateException.class,
                () -> mCarTelemetryManager.removeAllMetricsConfigs());
        assertThrows(IllegalStateException.class,
                () -> mCarTelemetryManager.sendFinishedReports(CONFIG_NAME));
        assertThrows(IllegalStateException.class,
                () -> mCarTelemetryManager.sendAllFinishedReports());
    }

    @Test
    public void testAddMetricsConfig() throws Exception {
        // invalid config, should fail
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, INVALID_METRICS_CONFIG, DIRECT_EXECUTOR,
                mAddMetricsConfigCallback);
        waitForHandlerThreadToFinish();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_METRICS_CONFIG_PARSE_FAILED);

        // new valid config, should succeed
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V1.toByteArray(),
                DIRECT_EXECUTOR, mAddMetricsConfigCallback);
        waitForHandlerThreadToFinish();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_METRICS_CONFIG_SUCCESS);

        // duplicate config, should fail
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V1.toByteArray(),
                DIRECT_EXECUTOR, mAddMetricsConfigCallback);
        waitForHandlerThreadToFinish();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_METRICS_CONFIG_ALREADY_EXISTS);

        // newer version of the config should replace older version
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V2.toByteArray(),
                DIRECT_EXECUTOR, mAddMetricsConfigCallback);
        waitForHandlerThreadToFinish();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_METRICS_CONFIG_SUCCESS);

        // older version of the config should not be accepted
        mCarTelemetryManager.addMetricsConfig(CONFIG_NAME, METRICS_CONFIG_V1.toByteArray(),
                DIRECT_EXECUTOR, mAddMetricsConfigCallback);
        waitForHandlerThreadToFinish();
        assertThat(mAddMetricsConfigCallback.mAddConfigStatusMap.get(CONFIG_NAME)).isEqualTo(
                STATUS_METRICS_CONFIG_VERSION_TOO_OLD);
    }

    private void waitForHandlerThreadToFinish() throws Exception {
        assertWithMessage("handler not idle in %sms", TIMEOUT_MS)
                .that(mIdleHandlerLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        mIdleHandlerLatch = new CountDownLatch(1); // reset idle handler condition
        mHandler.runWithScissors(() -> {
        }, TIMEOUT_MS);
    }

    private static final class AddMetricsConfigCallbackImpl
            implements CarTelemetryManager.AddMetricsConfigCallback {

        private Map<String, Integer> mAddConfigStatusMap = new ArrayMap<>();

        @Override
        public void onAddMetricsConfigStatus(@NonNull String metricsConfigName, int statusCode) {
            mAddConfigStatusMap.put(metricsConfigName, statusCode);
        }
    }

    private static final class FakeCarTelemetryResultsListener
            implements CarTelemetryManager.CarTelemetryResultsListener {
        @Override
        public void onResult(@NonNull String metricsConfigName, @NonNull byte[] result) {
        }

        @Override
        public void onError(@NonNull String metricsConfigName, @NonNull byte[] error) {
        }
    }
}
