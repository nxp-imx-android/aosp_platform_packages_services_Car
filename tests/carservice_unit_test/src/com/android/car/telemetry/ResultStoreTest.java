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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.PersistableBundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class ResultStoreTest {
    private static final PersistableBundle TEST_INTERIM_BUNDLE = new PersistableBundle();
    private static final PersistableBundle TEST_FINAL_BUNDLE = new PersistableBundle();

    private File mTestRootDir;
    private File mTestInterimResultDir;
    private File mTestFinalResultDir;
    private ResultStore mResultStore;

    @Mock
    private Handler mMockHandler;
    @Mock
    private ResultStore.FinalResultCallback mMockFinalResultCallback;
    @Captor
    private ArgumentCaptor<PersistableBundle> mBundleCaptor;


    @Before
    public void setUp() throws Exception {
        // execute all handler posts immediately
        when(mMockHandler.post(any())).thenAnswer(i -> {
            ((Runnable) i.getArguments()[0]).run();
            return true;
        });
        TEST_INTERIM_BUNDLE.putString("test key", "interim value");
        TEST_FINAL_BUNDLE.putString("test key", "final value");

        mTestRootDir = Files.createTempDirectory("car_telemetry_test").toFile();
        mTestInterimResultDir = new File(mTestRootDir, ResultStore.INTERIM_RESULT_DIR);
        mTestFinalResultDir = new File(mTestRootDir, ResultStore.FINAL_RESULT_DIR);

        mResultStore = new ResultStore(mMockHandler, mMockHandler, mTestRootDir);
    }

    @Test
    public void testConstructor_shouldCreateResultsFolder() {
        // constructor is called in setUp()
        assertThat(mTestInterimResultDir.exists()).isTrue();
        assertThat(mTestFinalResultDir.exists()).isTrue();
    }

    @Test
    public void testConstructor_shouldLoadInterimResultsIntoMemory() throws Exception {
        String testInterimFileName = "test_file_1";
        writeBundleToFile(mTestInterimResultDir, testInterimFileName, TEST_INTERIM_BUNDLE);

        mResultStore = new ResultStore(mMockHandler, mMockHandler, mTestRootDir);

        // should compare value instead of reference
        assertThat(mResultStore.getInterimResult(testInterimFileName).toString())
                .isEqualTo(TEST_INTERIM_BUNDLE.toString());
    }

    @Test
    public void testShutdown_shouldWriteResultsToFileAndCheckContent() throws Exception {
        String testInterimFileName = "test_file_1";
        String testFinalFileName = "test_file_2";
        writeBundleToFile(mTestInterimResultDir, testInterimFileName, TEST_INTERIM_BUNDLE);
        writeBundleToFile(mTestFinalResultDir, testFinalFileName, TEST_FINAL_BUNDLE);

        mResultStore.shutdown();

        assertThat(new File(mTestInterimResultDir, testInterimFileName).exists()).isTrue();
        assertThat(new File(mTestFinalResultDir, testFinalFileName).exists()).isTrue();
        // the content check will need to be modified when data encryption is implemented
        PersistableBundle interimData =
                readBundleFromFile(mTestInterimResultDir, testInterimFileName);
        assertThat(interimData.toString()).isEqualTo(TEST_INTERIM_BUNDLE.toString());
        PersistableBundle finalData = readBundleFromFile(mTestFinalResultDir, testFinalFileName);
        assertThat(finalData.toString()).isEqualTo(TEST_FINAL_BUNDLE.toString());
    }

    @Test
    public void testShutdown_shouldRemoveStaleData() throws Exception {
        File staleTestFile1 = new File(mTestInterimResultDir, "stale_test_file_1");
        File staleTestFile2 = new File(mTestFinalResultDir, "stale_test_file_2");
        File activeTestFile3 = new File(mTestInterimResultDir, "active_test_file_3");
        writeBundleToFile(staleTestFile1, TEST_INTERIM_BUNDLE);
        writeBundleToFile(staleTestFile2, TEST_FINAL_BUNDLE);
        writeBundleToFile(activeTestFile3, TEST_INTERIM_BUNDLE);
        long currTimeMs = System.currentTimeMillis();
        staleTestFile1.setLastModified(0L); // stale
        staleTestFile2.setLastModified(
                currTimeMs - TimeUnit.MILLISECONDS.convert(31, TimeUnit.DAYS)); // stale
        activeTestFile3.setLastModified(
                currTimeMs - TimeUnit.MILLISECONDS.convert(29, TimeUnit.DAYS)); // active

        mResultStore.shutdown();

        assertThat(staleTestFile1.exists()).isFalse();
        assertThat(staleTestFile2.exists()).isFalse();
        assertThat(activeTestFile3.exists()).isTrue();
    }

    @Test
    public void testGetFinalResult_whenNoData_shouldReceiveNull() throws Exception {
        String metricsConfigName = "my_metrics_config";

        mResultStore.getFinalResult(metricsConfigName, true, mMockFinalResultCallback);

        verify(mMockFinalResultCallback).onFinalResult(eq(metricsConfigName),
                mBundleCaptor.capture());
        assertThat(mBundleCaptor.getValue()).isNull();
    }

    @Test
    public void testGetFinalResult_whenDataCorrupt_shouldReceiveNull() throws Exception {
        String metricsConfigName = "my_metrics_config";
        Files.write(new File(mTestFinalResultDir, metricsConfigName).toPath(),
                "not a bundle".getBytes(StandardCharsets.UTF_8));

        mResultStore.getFinalResult(metricsConfigName, true, mMockFinalResultCallback);

        verify(mMockFinalResultCallback).onFinalResult(eq(metricsConfigName),
                mBundleCaptor.capture());
        assertThat(mBundleCaptor.getValue()).isNull();
    }

    @Test
    public void testGetFinalResult_whenDeleteFlagTrue_shouldDeleteData() throws Exception {
        String testFinalFileName = "my_metrics_config";
        writeBundleToFile(mTestFinalResultDir, testFinalFileName, TEST_FINAL_BUNDLE);

        mResultStore.getFinalResult(testFinalFileName, true, mMockFinalResultCallback);

        verify(mMockFinalResultCallback).onFinalResult(eq(testFinalFileName),
                mBundleCaptor.capture());
        // should compare value instead of reference
        assertThat(mBundleCaptor.getValue().toString()).isEqualTo(TEST_FINAL_BUNDLE.toString());
        assertThat(new File(mTestFinalResultDir, testFinalFileName).exists()).isFalse();
    }

    @Test
    public void testGetFinalResult_whenDeleteFlagFalse_shouldNotDeleteData() throws Exception {
        String testFinalFileName = "my_metrics_config";
        writeBundleToFile(mTestFinalResultDir, testFinalFileName, TEST_FINAL_BUNDLE);

        mResultStore.getFinalResult(testFinalFileName, false, mMockFinalResultCallback);

        verify(mMockFinalResultCallback).onFinalResult(eq(testFinalFileName),
                mBundleCaptor.capture());
        // should compare value instead of reference
        assertThat(mBundleCaptor.getValue().toString()).isEqualTo(TEST_FINAL_BUNDLE.toString());
        assertThat(new File(mTestFinalResultDir, testFinalFileName).exists()).isTrue();
    }

    @Test
    public void testPutFinalResult_shouldRemoveInterimResultFromMemory() throws Exception {
        String metricsConfigName = "my_metrics_config";
        mResultStore.putInterimResult(metricsConfigName, TEST_INTERIM_BUNDLE);

        mResultStore.putFinalResult(metricsConfigName, TEST_FINAL_BUNDLE);

        assertThat(mResultStore.getInterimResult(metricsConfigName)).isNull();
    }

    @Test
    public void testPutInterimResultAndShutdown_shouldReplaceExistingFile() throws Exception {
        String newKey = "new key";
        String newValue = "new value";
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);
        TEST_INTERIM_BUNDLE.putString(newKey, newValue);

        mResultStore.putInterimResult(metricsConfigName, TEST_INTERIM_BUNDLE);
        mResultStore.shutdown();

        PersistableBundle bundle = readBundleFromFile(mTestInterimResultDir, metricsConfigName);
        assertThat(bundle.getString(newKey)).isEqualTo(newValue);
        assertThat(bundle.toString()).isEqualTo(TEST_INTERIM_BUNDLE.toString());
    }

    @Test
    public void testPutInterimResultAndShutdown_shouldWriteDirtyResultsOnly() throws Exception {
        File fileFoo = new File(mTestInterimResultDir, "foo");
        File fileBar = new File(mTestInterimResultDir, "bar");
        writeBundleToFile(fileFoo, TEST_INTERIM_BUNDLE);
        writeBundleToFile(fileBar, TEST_INTERIM_BUNDLE);
        mResultStore = new ResultStore(mMockHandler, mMockHandler, mTestRootDir); // re-load data
        PersistableBundle newData = new PersistableBundle();
        newData.putDouble("pi", 3.1415926);

        mResultStore.putInterimResult("bar", newData); // make bar dirty
        fileFoo.delete(); // delete the clean file from the file system
        mResultStore.shutdown(); // write dirty data

        // foo is a clean file that should not be written in shutdown
        assertThat(fileFoo.exists()).isFalse();
        assertThat(readBundleFromFile(fileBar).toString()).isEqualTo(newData.toString());
    }

    @Test
    public void testPutFinalResultAndShutdown_shouldRemoveInterimResultFile() throws Exception {
        String metricsConfigName = "my_metrics_config";
        writeBundleToFile(mTestInterimResultDir, metricsConfigName, TEST_INTERIM_BUNDLE);

        mResultStore.putFinalResult(metricsConfigName, TEST_FINAL_BUNDLE);
        mResultStore.shutdown();

        assertThat(new File(mTestInterimResultDir, metricsConfigName).exists()).isFalse();
        assertThat(new File(mTestFinalResultDir, metricsConfigName).exists()).isTrue();
    }

    private void writeBundleToFile(
            File dir, String fileName, PersistableBundle persistableBundle) throws Exception {
        writeBundleToFile(new File(dir, fileName), persistableBundle);
    }

    /**
     * Writes a persistable bundle to the result directory with the given directory and file name,
     * and verifies that it was successfully written.
     */
    private void writeBundleToFile(
            File file, PersistableBundle persistableBundle) throws Exception {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            persistableBundle.writeToStream(byteArrayOutputStream);
            Files.write(file.toPath(), byteArrayOutputStream.toByteArray());
        }
        assertWithMessage("bundle is not written to the result directory")
                .that(file.exists()).isTrue();
    }

    private PersistableBundle readBundleFromFile(File dir, String fileName) throws Exception {
        return readBundleFromFile(new File(dir, fileName));
    }

    /** Reads a persistable bundle from the given path. */
    private PersistableBundle readBundleFromFile(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            return PersistableBundle.readFromStream(fis);
        }
    }
}
