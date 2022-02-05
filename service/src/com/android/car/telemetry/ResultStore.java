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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.telemetry.MetricsConfigKey;
import android.os.PersistableBundle;
import android.util.ArrayMap;
import android.util.AtomicFile;

import com.android.car.CarLog;
import com.android.car.telemetry.util.IoUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.utils.Slogf;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Disk storage for interim and final metrics statistics.
 * All methods in this class should be invoked from the telemetry thread.
 */
public class ResultStore {

    private static final long STALE_THRESHOLD_MILLIS =
            TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS);
    @VisibleForTesting
    static final String INTERIM_RESULT_DIR = "interim";
    @VisibleForTesting
    static final String ERROR_RESULT_DIR = "error";
    @VisibleForTesting
    static final String FINAL_RESULT_DIR = "final";

    /** Map keys are MetricsConfig names, which are also the file names in disk. */
    private final Map<String, InterimResult> mInterimResultCache = new ArrayMap<>();

    private final File mInterimResultDirectory;
    private final File mErrorResultDirectory;
    private final File mFinalResultDirectory;

    ResultStore(@NonNull File rootDirectory) {
        mInterimResultDirectory = new File(rootDirectory, INTERIM_RESULT_DIR);
        mErrorResultDirectory = new File(rootDirectory, ERROR_RESULT_DIR);
        mFinalResultDirectory = new File(rootDirectory, FINAL_RESULT_DIR);
        mInterimResultDirectory.mkdirs();
        mErrorResultDirectory.mkdirs();
        mFinalResultDirectory.mkdirs();
        // load results into memory to reduce the frequency of disk access
        loadInterimResultsIntoMemory();
    }

    /** Reads interim results into memory for faster access. */
    private void loadInterimResultsIntoMemory() {
        File[] files = mInterimResultDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                PersistableBundle interimResultBundle = IoUtils.readBundle(file);
                mInterimResultCache.put(file.getName(), new InterimResult(interimResultBundle));
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read from disk.", e);
                // TODO(b/197153560): record failure
            }
        }
    }

    /**
     * Retrieves interim metrics for the given
     * {@link com.android.car.telemetry.TelemetryProto.MetricsConfig}.
     */
    @Nullable
    public PersistableBundle getInterimResult(@NonNull String metricsConfigName) {
        if (!mInterimResultCache.containsKey(metricsConfigName)) {
            return null;
        }
        return mInterimResultCache.get(metricsConfigName).getBundle();
    }

    /**
     * Retrieves final metrics for the given
     * {@link com.android.car.telemetry.TelemetryProto.MetricsConfig}.
     *
     * @param metricsConfigName name of the MetricsConfig.
     * @param deleteResult      if true, the final result will be deleted from disk.
     * @return the final result as PersistableBundle if exists, null otherwise
     */
    @Nullable
    public PersistableBundle getFinalResult(
            @NonNull String metricsConfigName, boolean deleteResult) {
        File file = new File(mFinalResultDirectory, metricsConfigName);
        // if no final result exists for this metrics config, return immediately
        if (!file.exists()) {
            return null;
        }
        try {
            PersistableBundle result = IoUtils.readBundle(file);
            if (deleteResult) {
                file.delete();
            }
            return result;
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read from disk.", e);
            // TODO(b/197153560): record failure
        }
        return null;
    }

    /**
     * Retrieves all final results, mapped to each config name.
     *
     * @return the final results mapped to config names.
     */
    public Map<String, PersistableBundle> getFinalResults() {
        Map<String, PersistableBundle> results = new ArrayMap<>();
        File[] files = mFinalResultDirectory.listFiles();
        if (files == null) {
            return results;
        }
        for (File file : files) {
            try {
                PersistableBundle finalResultBundle = IoUtils.readBundle(file);
                results.put(file.getName(), finalResultBundle);
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read from disk.", e);
                // TODO(b/197153560): record failure
            }
        }
        return results;
    }

    /**
     * Returns the error result produced by the metrics config if exists, null otherwise.
     *
     * @param metricsConfigName name of the MetricsConfig.
     * @param deleteResult      if true, the error file will be deleted from disk.
     * @return the error result if exists, null otherwise.
     */
    @Nullable
    public TelemetryProto.TelemetryError getErrorResult(
            @NonNull String metricsConfigName, boolean deleteResult) {
        File file = new File(mErrorResultDirectory, metricsConfigName);
        // if no error exists for this metrics config, return immediately
        if (!file.exists()) {
            return null;
        }
        try {
            TelemetryProto.TelemetryError result =
                    TelemetryProto.TelemetryError.parseFrom(new AtomicFile(file).readFully());
            if (deleteResult) {
                file.delete();
            }
            return result;
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to get error result from disk.", e);
            // TODO(b/197153560): record failure
        }
        return null;
    }

    /**
     * Retrieves all errors, mapped to each config name.
     *
     * @return the map of errors to each config.
     */
    @NonNull
    public Map<String, TelemetryProto.TelemetryError> getErrorResults() {
        Map<String, TelemetryProto.TelemetryError> errors = new ArrayMap<>();
        File[] files = mErrorResultDirectory.listFiles();
        if (files == null) {
            return errors;
        }
        for (File file : files) {
            try {
                TelemetryProto.TelemetryError error =
                        TelemetryProto.TelemetryError.parseFrom(new AtomicFile(file).readFully());
                errors.put(file.getName(), error);
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to read errors from disk.", e);
                // TODO(b/197153560): record failure
            }
        }
        return errors;
    }

    /**
     * Stores interim metrics results in memory for the given
     * {@link com.android.car.telemetry.TelemetryProto.MetricsConfig}.
     */
    public void putInterimResult(
            @NonNull String metricsConfigName, @NonNull PersistableBundle result) {
        mInterimResultCache.put(metricsConfigName, new InterimResult(result, /* dirty = */ true));
    }

    /**
     * Stores final metrics in memory for the given
     * {@link com.android.car.telemetry.TelemetryProto.MetricsConfig}.
     */
    public void putFinalResult(
            @NonNull String metricsConfigName, @NonNull PersistableBundle result) {
        try {
            IoUtils.writeBundle(mFinalResultDirectory, metricsConfigName, result);
            IoUtils.deleteSilently(mInterimResultDirectory, metricsConfigName);
            mInterimResultCache.remove(metricsConfigName);
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write result to file", e);
            // TODO(b/197153560): record failure
        }
    }

    /** Stores the error object produced by the script. */
    public void putErrorResult(
            @NonNull String metricsConfigName, @NonNull TelemetryProto.TelemetryError error) {
        try {
            IoUtils.writeProto(mErrorResultDirectory, metricsConfigName, error);
            IoUtils.deleteSilently(mInterimResultDirectory, metricsConfigName);
            mInterimResultCache.remove(metricsConfigName);
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write result to file", e);
            // TODO(b/197153560): record failure
        }
    }

    /**
     * Deletes script result associated with the given config name. If result does not exist, this
     * method does not do anything.
     */
    public void removeResult(@NonNull MetricsConfigKey key) {
        String metricsConfigName = key.getName();
        mInterimResultCache.remove(metricsConfigName);
        IoUtils.deleteSilently(mInterimResultDirectory, metricsConfigName);
        IoUtils.deleteSilently(mFinalResultDirectory, metricsConfigName);
        IoUtils.deleteSilently(mErrorResultDirectory, metricsConfigName);
    }

    /** Deletes all interim and final results stored in disk. */
    public void removeAllResults() {
        mInterimResultCache.clear();
        IoUtils.deleteAllSilently(mInterimResultDirectory);
        IoUtils.deleteAllSilently(mFinalResultDirectory);
        IoUtils.deleteAllSilently(mErrorResultDirectory);
    }

    /** Persists data to disk and deletes stale data. */
    public void flushToDisk() {
        writeInterimResultsToFile();
        IoUtils.deleteOldFiles(STALE_THRESHOLD_MILLIS,
                mInterimResultDirectory, mFinalResultDirectory);
    }

    /** Writes dirty interim results to disk. */
    private void writeInterimResultsToFile() {
        mInterimResultCache.forEach((metricsConfigName, interimResult) -> {
            // only write dirty data
            if (!interimResult.isDirty()) {
                return;
            }
            try {
                IoUtils.writeBundle(
                        mInterimResultDirectory, metricsConfigName, interimResult.getBundle());
            } catch (IOException e) {
                Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write result to file", e);
                // TODO(b/197153560): record failure
            }
        });
    }

    /** Wrapper around a result and whether the result should be written to disk. */
    static final class InterimResult {
        private final PersistableBundle mBundle;
        private final boolean mDirty;

        InterimResult(@NonNull PersistableBundle bundle) {
            mBundle = bundle;
            mDirty = false;
        }

        InterimResult(@NonNull PersistableBundle bundle, boolean dirty) {
            mBundle = bundle;
            mDirty = dirty;
        }

        @NonNull
        PersistableBundle getBundle() {
            return mBundle;
        }

        boolean isDirty() {
            return mDirty;
        }
    }
}
