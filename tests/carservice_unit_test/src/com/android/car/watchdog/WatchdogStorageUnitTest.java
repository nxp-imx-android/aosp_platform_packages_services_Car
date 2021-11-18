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

package com.android.car.watchdog;

import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_NEVER;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_NO;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_YES;

import static com.android.car.watchdog.WatchdogStorage.RETENTION_PERIOD;
import static com.android.car.watchdog.WatchdogStorage.STATS_TEMPORAL_UNIT;
import static com.android.car.watchdog.WatchdogStorage.WatchdogDbHelper.DATABASE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.automotive.watchdog.PerStateBytes;
import android.car.builtin.util.Slogf;
import android.car.watchdog.IoOveruseStats;
import android.content.Context;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>This class contains unit tests for the {@link WatchdogStorage}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WatchdogStorageUnitTest {
    private static final String TAG = WatchdogStorageUnitTest.class.getSimpleName();

    private Context mContext;
    private WatchdogStorage mService;
    private File mDatabaseFile;

    private final TestTimeSource mTimeSource = new TestTimeSource();

    @Before
    public void setUp() throws Exception {
        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        mContext = InstrumentationRegistry.getTargetContext().createDeviceProtectedStorageContext();
        mDatabaseFile = mContext.createDeviceProtectedStorageContext()
                .getDatabasePath(DATABASE_NAME);
        mService = new WatchdogStorage(mContext, /* useDataSystemCarDir= */ false, mTimeSource);
    }

    @After
    public void tearDown() {
        mService.release();
        if (!mDatabaseFile.delete()) {
            Slogf.e(TAG, "Failed to delete the database file: %s", mDatabaseFile.getAbsolutePath());
        }
    }

    @Test
    public void testSaveUserPackageSettings() throws Exception {
        List<WatchdogStorage.UserPackageSettingsEntry> expected = sampleSettings();

        assertThat(mService.saveUserPackageSettings(expected)).isTrue();

        assertWithMessage("User package settings").that(mService.getUserPackageSettings())
                .containsExactlyElementsIn(expected);
    }

    @Test
    public void testOverwriteUserPackageSettings() throws Exception {
        List<WatchdogStorage.UserPackageSettingsEntry> expected = Arrays.asList(
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "system_package.non_critical.A", KILLABLE_STATE_YES),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "system_package.non_critical.B", KILLABLE_STATE_NO));

        assertThat(mService.saveUserPackageSettings(expected)).isTrue();

        expected = Arrays.asList(
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "system_package.non_critical.A", KILLABLE_STATE_NEVER),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "system_package.non_critical.B", KILLABLE_STATE_NO));

        assertThat(mService.saveUserPackageSettings(expected)).isTrue();

        assertWithMessage("User package settings after overwrite")
                .that(mService.getUserPackageSettings()).containsExactlyElementsIn(expected);
    }

    @Test
    public void testSaveAndGetIoOveruseStats() throws Exception {
        injectSampleUserPackageSettings();
        /* Start time aligned to the beginning of the day. */
        long startTime = mTimeSource.getCurrentDate().toEpochSecond();

        assertWithMessage("Saved I/O usage stats successfully")
                .that(mService.saveIoUsageStats(sampleStatsForDate(startTime, /* duration= */ 60)))
                .isTrue();

        long expectedDuration = mTimeSource.getCurrentDateTime().toEpochSecond() - startTime;
        List<WatchdogStorage.IoUsageStatsEntry> expected = sampleStatsForDate(
                startTime, expectedDuration);

        IoUsageStatsEntrySubject.assertThat(mService.getTodayIoUsageStats())
                .containsExactlyElementsIn(expected);
    }

    @Test
    public void testSaveAndGetIoOveruseStatsWithOffsettedStartTime() throws Exception {
        injectSampleUserPackageSettings();
        /* Start time in the middle of the day. */
        long startTime = mTimeSource.getCurrentDate().plusHours(12).toEpochSecond();
        List<WatchdogStorage.IoUsageStatsEntry> entries = sampleStatsForDate(
                startTime, /* duration= */ 60);

        assertWithMessage("Saved I/O usage stats successfully")
                .that(mService.saveIoUsageStats(entries)).isTrue();

        long expectedStartTime = mTimeSource.getCurrentDate().toEpochSecond();
        long expectedDuration =
                mTimeSource.getCurrentDateTime().toEpochSecond() - expectedStartTime;
        List<WatchdogStorage.IoUsageStatsEntry> expected = sampleStatsForDate(
                expectedStartTime, expectedDuration);

        IoUsageStatsEntrySubject.assertThat(mService.getTodayIoUsageStats())
                .containsExactlyElementsIn(expected);
    }

    @Test
    public void testOverwriteIoOveruseStats() throws Exception {
        injectSampleUserPackageSettings();
        long startTime = mTimeSource.getCurrentDate().toEpochSecond();
        long duration = mTimeSource.getCurrentDateTime().toEpochSecond() - startTime;

        List<WatchdogStorage.IoUsageStatsEntry> statsBeforeOverwrite = Collections.singletonList(
                constructIoUsageStatsEntry(
                        /* userId= */ 100, "system_package.non_critical.A", startTime, duration,
                        /* remainingWriteBytes= */
                        CarWatchdogServiceUnitTest.constructPerStateBytes(200, 300, 400),
                        /* writtenBytes= */
                        CarWatchdogServiceUnitTest.constructPerStateBytes(1000, 2000, 3000),
                        /* forgivenWriteBytes= */
                        CarWatchdogServiceUnitTest.constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 2, /* forgivenOveruses= */ 0,
                        /* totalTimesKilled= */ 1));

        assertWithMessage("Saved I/O usage stats successfully")
                .that(mService.saveIoUsageStats(statsBeforeOverwrite)).isTrue();

        IoUsageStatsEntrySubject.assertWithMessage(
                mService.getTodayIoUsageStats(), "I/O usage stats fetched from database")
                .containsExactlyElementsIn(statsBeforeOverwrite);

        List<WatchdogStorage.IoUsageStatsEntry> statsAfterOverwrite = Collections.singletonList(
                constructIoUsageStatsEntry(
                        /* userId= */ 100, "system_package.non_critical.A", startTime, duration,
                        /* remainingWriteBytes= */
                        CarWatchdogServiceUnitTest.constructPerStateBytes(400, 600, 800),
                        /* writtenBytes= */
                        CarWatchdogServiceUnitTest.constructPerStateBytes(2000, 3000, 4000),
                        /* forgivenWriteBytes= */
                        CarWatchdogServiceUnitTest.constructPerStateBytes(1200, 2300, 3400),
                        /* totalOveruses= */ 4, /* forgivenOveruses= */ 2,
                        /* totalTimesKilled= */ 2));

        assertWithMessage("Saved I/O usage stats successfully")
                .that(mService.saveIoUsageStats(statsAfterOverwrite)).isTrue();

        IoUsageStatsEntrySubject.assertWithMessage(
                mService.getTodayIoUsageStats(), "Cached in memory I/O usage stats")
                .containsExactlyElementsIn(statsBeforeOverwrite);

        mService.release();
        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        mService = new WatchdogStorage(mContext, /* useDataSystemCarDir= */ false, mTimeSource);

        assertWithMessage("User packages settings").that(mService.getUserPackageSettings())
                .isNotEmpty();

        IoUsageStatsEntrySubject.assertWithMessage(mService.getTodayIoUsageStats(),
                "I/O usage stats fetched from database after restart")
                .containsExactlyElementsIn(statsAfterOverwrite);
    }

    @Test
    public void testSaveIoOveruseStatsOutsideRetentionPeriod() throws Exception {
        injectSampleUserPackageSettings();
        int retentionDaysAgo = RETENTION_PERIOD.getDays();

        assertWithMessage("Saved I/O usage stats successfully")
                .that(mService.saveIoUsageStats(sampleStatsBetweenDates(
                        /* includingStartDaysAgo= */ retentionDaysAgo,
                        /* excludingEndDaysAgo= */ retentionDaysAgo + 1))).isTrue();

        assertWithMessage("Didn't fetch I/O overuse stats outside retention period")
                .that(mService.getHistoricalIoOveruseStats(
                        /* userId= */ 100, "system_package.non_critical.A", retentionDaysAgo))
                .isNull();
    }

    @Test
    public void testGetHistoricalIoOveruseStats() throws Exception {
        injectSampleUserPackageSettings();

        assertThat(mService.saveIoUsageStats(sampleStatsBetweenDates(
                /* includingStartDaysAgo= */ 0, /* excludingEndDaysAgo= */ 5))).isTrue();

        IoOveruseStats actual  = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "system_package.non_critical.A", /* numDaysAgo= */ 7);

        assertWithMessage("Fetched I/O overuse stats").that(actual).isNotNull();

        /*
         * Returned stats shouldn't include stats for the current date as WatchdogPerfHandler fills
         * the current day's stats.
         */
        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        long startTime = currentDate.minus(4, STATS_TEMPORAL_UNIT).toEpochSecond();
        long duration = currentDate.toEpochSecond() - startTime;
        IoOveruseStats expected = new IoOveruseStats.Builder(startTime, duration)
                .setTotalOveruses(8).setTotalTimesKilled(4).setTotalBytesWritten(25_200).build();

        IoOveruseStatsSubject.assertWithMessage(
                "Fetched stats only for 4 days. Expected stats (%s) equals actual stats (%s)",
                expected.toString(), actual.toString()).that(actual)
                .isEqualTo(expected);
    }

    @Test
    public void testGetHistoricalIoOveruseStatsWithNoRecentStats() throws Exception {
        injectSampleUserPackageSettings();

        assertThat(mService.saveIoUsageStats(sampleStatsBetweenDates(
                /* includingStartDaysAgo= */ 3, /* excludingEndDaysAgo= */ 5))).isTrue();

        IoOveruseStats actual  = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "system_package.non_critical.A", /* numDaysAgo= */ 7);

        assertWithMessage("Fetched I/O overuse stats").that(actual).isNotNull();

        /*
         * Returned stats shouldn't include stats for the current date as WatchdogPerfHandler fills
         * the current day's stats.
         */
        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        long startTime = currentDate.minus(4, STATS_TEMPORAL_UNIT).toEpochSecond();
        long duration = currentDate.toEpochSecond() - startTime;
        IoOveruseStats expected = new IoOveruseStats.Builder(startTime, duration)
                .setTotalOveruses(4).setTotalTimesKilled(2).setTotalBytesWritten(12_600).build();

        IoOveruseStatsSubject.assertWithMessage(
                "Fetched stats only for 2 days. Expected stats (%s) equals actual stats (%s)",
                expected.toString(), actual.toString()).that(actual)
                .isEqualTo(expected);
    }

    @Test
    public void testGetDailySystemIoUsageSummaries() throws Exception {
        injectSampleUserPackageSettings();
        List<WatchdogStorage.IoUsageStatsEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 30; ++i) {
            entries.addAll(sampleStatsBetweenDates(/* includingStartDaysAgo= */ i,
                    /* excludingEndDaysAgo= */ i + 1, /* writtenBytesMultiplier= */ i));
        }

        assertWithMessage("Save I/O usage stats").that(mService.saveIoUsageStats(entries)).isTrue();

        ZonedDateTime currentDate = mTimeSource.getCurrentDate();

        List<AtomsProto.CarWatchdogDailyIoUsageSummary> actual =
                mService.getDailySystemIoUsageSummaries(
                        /* includingStartEpochSeconds= */ currentDate.minusDays(15).toEpochSecond(),
                        /* excludingEndEpochSeconds= */ currentDate.minusDays(7).toEpochSecond());

        List<AtomsProto.CarWatchdogDailyIoUsageSummary> expected = new ArrayList<>();
        for (int i = 15; i > 7; --i) {
            expected.add(CarWatchdogServiceUnitTest
                    .constructCarWatchdogDailyIoUsageSummary(/* fgWrBytes= */ 10402L * i,
                            /* bgWrBytes= */ 14402L * i, /* gmWrBytes= */ 18402L * i,
                            /* overuseCount= */ 6));
        }

        assertWithMessage("Daily system I/O usage summary stats").that(actual).isEqualTo(expected);
    }

    @Test
    public void testGetDailySystemIoUsageSummariesWithoutStats() throws Exception {
        injectSampleUserPackageSettings();
        List<WatchdogStorage.IoUsageStatsEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 7; ++i) {
            entries.addAll(sampleStatsBetweenDates(/* includingStartDaysAgo= */ i,
                    /* excludingEndDaysAgo= */ i + 1, /* writtenBytesMultiplier= */ i));
        }

        assertWithMessage("Save I/O usage stats").that(mService.saveIoUsageStats(entries)).isTrue();

        ZonedDateTime currentDate = mTimeSource.getCurrentDate();

        List<AtomsProto.CarWatchdogDailyIoUsageSummary> actual =
                mService.getDailySystemIoUsageSummaries(
                        /* includingStartEpochSeconds= */ currentDate.minusDays(15).toEpochSecond(),
                        /* excludingEndEpochSeconds= */ currentDate.minusDays(7).toEpochSecond());

        assertWithMessage("Daily system I/O usage summary stats").that(actual).isNull();
    }

    @Test
    public void testGetTopUsersDailyIoUsageSummaries() throws Exception {
        injectSampleUserPackageSettings();
        List<WatchdogStorage.IoUsageStatsEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 30; ++i) {
            entries.addAll(sampleStatsBetweenDates(/* includingStartDaysAgo= */ i,
                    /* excludingEndDaysAgo= */ i + 1, /* writtenBytesMultiplier= */ i));
        }

        assertWithMessage("Save I/O usage stats").that(mService.saveIoUsageStats(entries)).isTrue();

        ZonedDateTime currentDate = mTimeSource.getCurrentDate();

        List<WatchdogStorage.UserPackageDailySummaries> actual =
                mService.getTopUsersDailyIoUsageSummaries(/* numTopUsers= */ 3,
                        /* minTotalWrittenBytes= */ 600_000,
                        /* includingStartEpochSeconds= */ currentDate.minusDays(15).toEpochSecond(),
                        /* excludingEndEpochSeconds= */ currentDate.minusDays(7).toEpochSecond());

        List<AtomsProto.CarWatchdogDailyIoUsageSummary> user101VendorPkgSummaries =
                new ArrayList<>();
        List<AtomsProto.CarWatchdogDailyIoUsageSummary> user100VendorPkgSummaries =
                new ArrayList<>();
        for (int i = 15; i > 7; --i) {
            user101VendorPkgSummaries.add(CarWatchdogServiceUnitTest
                    .constructCarWatchdogDailyIoUsageSummary(/* fgWrBytes= */ 4101L * i,
                            /* bgWrBytes= */ 5101L * i, /* gmWrBytes= */ 6101L * i,
                            /* overuseCount= */ 1));
            user100VendorPkgSummaries.add(CarWatchdogServiceUnitTest
                    .constructCarWatchdogDailyIoUsageSummary(/* fgWrBytes= */ 4100L * i,
                            /* bgWrBytes= */ 5100L * i, /* gmWrBytes= */ 6100L * i,
                            /* overuseCount= */ 1));
        }
        List<WatchdogStorage.UserPackageDailySummaries> expected = Arrays.asList(
                new WatchdogStorage.UserPackageDailySummaries(/* userId= */ 101,
                        /* packageName= */ "vendor_package.critical.C", user101VendorPkgSummaries),
                new WatchdogStorage.UserPackageDailySummaries(/* userId= */ 100,
                        /* packageName= */ "vendor_package.critical.C", user100VendorPkgSummaries));

        assertWithMessage("Top users daily I/O usage summaries").that(actual).isEqualTo(expected);
    }

    @Test
    public void testGetTopUsersDailyIoUsageSummariesWithoutStats() throws Exception {
        injectSampleUserPackageSettings();
        List<WatchdogStorage.IoUsageStatsEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 7; ++i) {
            entries.addAll(sampleStatsBetweenDates(/* includingStartDaysAgo= */ i,
                    /* excludingEndDaysAgo= */ i + 1, /* writtenBytesMultiplier= */ i));
        }

        assertWithMessage("Save I/O usage stats").that(mService.saveIoUsageStats(entries)).isTrue();

        ZonedDateTime currentDate = mTimeSource.getCurrentDate();

        List<WatchdogStorage.UserPackageDailySummaries> actual =
                mService.getTopUsersDailyIoUsageSummaries(/* numTopUsers= */ 3,
                        /* minTotalWrittenBytes= */ 600_000,
                        /* includingStartEpochSeconds= */ currentDate.minusDays(15).toEpochSecond(),
                        /* excludingEndEpochSeconds= */ currentDate.minusDays(7).toEpochSecond());

        assertWithMessage("Top users daily I/O usage summaries").that(actual).isNull();
    }

    @Test
    public void testDeleteUserPackage() throws Exception {
        ArrayList<WatchdogStorage.UserPackageSettingsEntry> settingsEntries = sampleSettings();
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = sampleStatsForToday();

        assertThat(mService.saveUserPackageSettings(settingsEntries)).isTrue();
        assertThat(mService.saveIoUsageStats(ioUsageStatsEntries)).isTrue();

        int deleteUserId = 100;
        String deletePackageName = "system_package.non_critical.A";

        mService.deleteUserPackage(deleteUserId, deletePackageName);

        settingsEntries.removeIf(
                (s) -> s.userId == deleteUserId && s.packageName.equals(deletePackageName));

        assertWithMessage("User package settings after deleting a user package")
                .that(mService.getUserPackageSettings()).containsExactlyElementsIn(settingsEntries);

        ioUsageStatsEntries.removeIf(
                (e) -> e.userId == deleteUserId && e.packageName.equals(deletePackageName));

        IoUsageStatsEntrySubject.assertThat(mService.getTodayIoUsageStats())
                .containsExactlyElementsIn(ioUsageStatsEntries);
    }

    @Test
    public void testDeleteUserPackageWithNonexistentPackage() throws Exception {
        injectSampleUserPackageSettings();
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = sampleStatsForToday();

        assertThat(mService.saveIoUsageStats(ioUsageStatsEntries)).isTrue();

        int deleteUserId = 100;
        String deletePackageName = "system_package.non_existent.A";

        mService.deleteUserPackage(deleteUserId, deletePackageName);

        assertWithMessage("User package settings").that(mService.getUserPackageSettings())
                .containsExactlyElementsIn(sampleSettings());

        ioUsageStatsEntries.removeIf(
                (e) -> e.userId == deleteUserId && e.packageName.equals(deletePackageName));

        IoUsageStatsEntrySubject.assertThat(mService.getTodayIoUsageStats())
                .containsExactlyElementsIn(ioUsageStatsEntries);
    }

    @Test
    public void testDeleteUserPackageWithHistoricalIoOveruseStats()
            throws Exception {
        ArrayList<WatchdogStorage.UserPackageSettingsEntry> settingsEntries = sampleSettings();

        assertThat(mService.saveUserPackageSettings(settingsEntries)).isTrue();
        assertThat(mService.saveIoUsageStats(sampleStatsBetweenDates(
                /* includingStartDaysAgo= */ 1, /* excludingEndDaysAgo= */ 6))).isTrue();

        int deleteUserId = 100;
        String deletePackageName = "system_package.non_critical.A";

        mService.deleteUserPackage(deleteUserId, deletePackageName);

        settingsEntries.removeIf(
                (s) -> s.userId == deleteUserId && s.packageName.equals(deletePackageName));

        assertWithMessage("User package settings after deleting user package with historical stats")
                .that(mService.getUserPackageSettings()).containsExactlyElementsIn(settingsEntries);

        IoOveruseStats actual = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "system_package.non_critical.A", /* numDaysAgo= */ 7);

        assertWithMessage("Fetched historical I/O overuse stats").that(actual).isNull();
    }

    @Test
    public void testSyncUsers() throws Exception {
        List<WatchdogStorage.UserPackageSettingsEntry> settingsEntries = sampleSettings();
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = sampleStatsForToday();

        assertThat(mService.saveUserPackageSettings(settingsEntries)).isTrue();
        assertThat(mService.saveIoUsageStats(ioUsageStatsEntries)).isTrue();

        mService.syncUsers(/* aliveUserIds= */ new int[] {101});

        settingsEntries.removeIf((s) -> s.userId == 100);
        ioUsageStatsEntries.removeIf((e) -> e.userId == 100);

        assertWithMessage("User package settings after syncing alive users")
                .that(mService.getUserPackageSettings()).containsExactlyElementsIn(settingsEntries);

        IoUsageStatsEntrySubject.assertThat(mService.getTodayIoUsageStats())
                .containsExactlyElementsIn(ioUsageStatsEntries);
    }

    @Test
    public void testSyncUsersWithHistoricalIoOveruseStats() throws Exception {
        List<WatchdogStorage.UserPackageSettingsEntry> settingsEntries = sampleSettings();

        assertThat(mService.saveUserPackageSettings(settingsEntries)).isTrue();
        assertThat(mService.saveIoUsageStats(sampleStatsBetweenDates(
                /* includingStartDaysAgo= */ 1, /* excludingEndDaysAgo= */ 6))).isTrue();

        mService.syncUsers(/* aliveUserIds= */ new int[] {101});

        settingsEntries.removeIf((s) -> s.userId == 100);

        assertWithMessage("User package settings after syncing alive users with historical stats")
                .that(mService.getUserPackageSettings()).containsExactlyElementsIn(settingsEntries);

        IoOveruseStats actualSystemPackage = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "system_package.non_critical.A", /* numDaysAgo= */ 7);
        IoOveruseStats actualVendorPackage = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "vendor_package.critical.C", /* numDaysAgo= */ 7);

        assertWithMessage("Fetched system I/O overuse stats for deleted user")
                .that(actualSystemPackage).isNull();
        assertWithMessage("Fetched vendor I/O overuse stats for deleted user")
                .that(actualVendorPackage).isNull();
    }

    @Test
    public void testSyncUsersWithNoDataForDeletedUser() throws Exception {
        List<WatchdogStorage.UserPackageSettingsEntry> settingsEntries = sampleSettings();
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = sampleStatsForToday();

        assertThat(mService.saveUserPackageSettings(settingsEntries)).isTrue();
        assertThat(mService.saveIoUsageStats(ioUsageStatsEntries)).isTrue();

        mService.syncUsers(/* aliveUserIds= */ new int[] {100, 101});

        assertWithMessage("User package settings after syncing users")
                .that(mService.getUserPackageSettings()).containsExactlyElementsIn(settingsEntries);
        IoUsageStatsEntrySubject.assertThat(mService.getTodayIoUsageStats())
                .containsExactlyElementsIn(ioUsageStatsEntries);
    }

    @Test
    public void testTruncateStatsOutsideRetentionPeriodOnDateChange() throws Exception {
        injectSampleUserPackageSettings();
        mTimeSource.updateNow(/* numDaysAgo= */ 1);

        assertThat(mService.saveIoUsageStats(sampleStatsBetweenDates(
                /* includingStartDaysAgo= */ 0, /* excludingEndDaysAgo= */ 40),
                /* shouldCheckRetention= */ false)).isTrue();

        IoOveruseStats actual  = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "system_package.non_critical.A", /* numDaysAgo= */ 40);

        assertWithMessage("Fetched I/O overuse stats").that(actual).isNotNull();

        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        long startTime = currentDate.minus(39, STATS_TEMPORAL_UNIT).toEpochSecond();
        long duration = currentDate.toEpochSecond() - startTime;
        IoOveruseStats expected = new IoOveruseStats.Builder(startTime, duration)
                .setTotalOveruses(78).setTotalTimesKilled(39).setTotalBytesWritten(245_700).build();

        IoOveruseStatsSubject.assertWithMessage(
                "Fetched stats only for 39 days. Expected stats (%s) equals actual stats (%s)",
                expected.toString(), actual.toString()).that(actual)
                .isEqualTo(expected);

        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        mService.shrinkDatabase();

        actual = mService.getHistoricalIoOveruseStats(
                /* userId= */ 100, "system_package.non_critical.A", /* numDaysAgo= */ 40);

        assertWithMessage("Fetched I/O overuse stats").that(actual).isNotNull();

        currentDate = mTimeSource.getCurrentDate();
        startTime = currentDate.minus(RETENTION_PERIOD.minusDays(1)).toEpochSecond();
        duration = currentDate.toEpochSecond() - startTime;
        expected = new IoOveruseStats.Builder(startTime, duration)
                .setTotalOveruses(58).setTotalTimesKilled(29).setTotalBytesWritten(182_700).build();

        IoOveruseStatsSubject.assertWithMessage("Fetched stats only within retention period. "
                        + "Expected stats (%s) equals actual stats (%s)",
                expected.toString(), actual.toString()).that(actual).isEqualTo(expected);
    }

    @Test
    public void testForgiveHistoricalOveruses() throws Exception {
        injectSampleUserPackageSettings();

        assertThat(mService.saveIoUsageStats(sampleStatsBetweenDates(/* includingStartDaysAgo= */ 1,
                /* excludingEndDaysAgo= */ 3))).isTrue();

        List<WatchdogStorage.NotForgivenOverusesEntry> expectedOveruses = Arrays.asList(
                new WatchdogStorage.NotForgivenOverusesEntry(100, "system_package.non_critical.A",
                        2),
                new WatchdogStorage.NotForgivenOverusesEntry(101, "system_package.non_critical.A",
                        2),
                new WatchdogStorage.NotForgivenOverusesEntry(100, "vendor_package.critical.C", 2),
                new WatchdogStorage.NotForgivenOverusesEntry(101, "vendor_package.critical.C", 2));

        assertWithMessage("Not forgiven historical overuses before forgiving")
                .that(mService.getNotForgivenHistoricalIoOveruses(/* numDaysAgo= */ 7))
                .containsExactlyElementsIn(expectedOveruses);

        SparseArray<List<String>> packagesToForgiveByUserId = new SparseArray<>();
        packagesToForgiveByUserId.put(100,
                Collections.singletonList("system_package.non_critical.A"));
        packagesToForgiveByUserId.put(101, Collections.singletonList("vendor_package.critical.C"));

        mService.forgiveHistoricalOveruses(packagesToForgiveByUserId, /* numDaysAgo= */ 7);

        expectedOveruses = Arrays.asList(
                new WatchdogStorage.NotForgivenOverusesEntry(101, "system_package.non_critical.A",
                        2),
                new WatchdogStorage.NotForgivenOverusesEntry(100, "vendor_package.critical.C", 2));

        assertWithMessage("Not forgiven historical overuses after forgiving")
                .that(mService.getNotForgivenHistoricalIoOveruses(/* numDaysAgo= */ 7))
                .containsExactlyElementsIn(expectedOveruses);
    }

    private void injectSampleUserPackageSettings() throws Exception {
        List<WatchdogStorage.UserPackageSettingsEntry> expected = sampleSettings();

        assertThat(mService.saveUserPackageSettings(expected)).isTrue();
    }

    private static ArrayList<WatchdogStorage.UserPackageSettingsEntry> sampleSettings() {
        return new ArrayList<>(Arrays.asList(
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "system_package.non_critical.A", KILLABLE_STATE_YES),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "system_package.non_critical.B", KILLABLE_STATE_NO),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 100, "vendor_package.critical.C", KILLABLE_STATE_NEVER),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 101, "system_package.non_critical.A", KILLABLE_STATE_NO),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 101, "system_package.non_critical.B", KILLABLE_STATE_YES),
                new WatchdogStorage.UserPackageSettingsEntry(
                        /* userId= */ 101, "vendor_package.critical.C", KILLABLE_STATE_NEVER)));
    }

    private ArrayList<WatchdogStorage.IoUsageStatsEntry> sampleStatsBetweenDates(
            int includingStartDaysAgo, int excludingEndDaysAgo) {
        return sampleStatsBetweenDates(includingStartDaysAgo, excludingEndDaysAgo,
                /* writtenBytesMultiplier= */ 1);
    }

    private ArrayList<WatchdogStorage.IoUsageStatsEntry> sampleStatsBetweenDates(
            int includingStartDaysAgo, int excludingEndDaysAgo, int writtenBytesMultiplier) {
        ZonedDateTime currentDate = mTimeSource.getCurrentDate();
        ArrayList<WatchdogStorage.IoUsageStatsEntry> entries = new ArrayList<>();
        for (int i = includingStartDaysAgo; i < excludingEndDaysAgo; ++i) {
            entries.addAll(
                    sampleStatsForDate(currentDate.minus(i, STATS_TEMPORAL_UNIT).toEpochSecond(),
                            STATS_TEMPORAL_UNIT.getDuration().toSeconds(), writtenBytesMultiplier));
        }
        return entries;
    }

    private ArrayList<WatchdogStorage.IoUsageStatsEntry> sampleStatsForToday() {
        long statsDateEpoch = mTimeSource.getCurrentDate().toEpochSecond();
        long duration = mTimeSource.getCurrentDateTime().toEpochSecond() - statsDateEpoch;
        return sampleStatsForDate(statsDateEpoch, duration, /* writtenBytesMultiplier= */ 1);
    }

    private static ArrayList<WatchdogStorage.IoUsageStatsEntry> sampleStatsForDate(
            long statsDateEpoch, long duration) {
        return sampleStatsForDate(statsDateEpoch, duration, /* writtenBytesMultiplier= */ 1);
    }

    private static ArrayList<WatchdogStorage.IoUsageStatsEntry> sampleStatsForDate(
            long statsDateEpoch, long duration, int writtenBytesMultiplier) {
        ArrayList<WatchdogStorage.IoUsageStatsEntry> entries = new ArrayList<>();
        for (int i = 100; i <= 101; ++i) {
            entries.add(constructIoUsageStatsEntry(
                    /* userId= */ i, "system_package.non_critical.A", statsDateEpoch, duration,
                    /* remainingWriteBytes= */
                    CarWatchdogServiceUnitTest.constructPerStateBytes(200L, 300L, 400L),
                    /* writtenBytes= */
                    CarWatchdogServiceUnitTest.constructPerStateBytes(
                            (1000L + i) * writtenBytesMultiplier,
                            (2000L + i) * writtenBytesMultiplier,
                            (3000L + i) * writtenBytesMultiplier),
                    /* forgivenWriteBytes= */
                    CarWatchdogServiceUnitTest.constructPerStateBytes(100L, 100L, 100L),
                    /* totalOveruses= */ 2, /* forgivenOveruses= */ 1, /* totalTimesKilled= */ 1));
            entries.add(constructIoUsageStatsEntry(
                    /* userId= */ i, "vendor_package.critical.C", statsDateEpoch, duration,
                    /* remainingWriteBytes= */
                    CarWatchdogServiceUnitTest.constructPerStateBytes(500L, 600L, 700L),
                    /* writtenBytes= */
                    CarWatchdogServiceUnitTest.constructPerStateBytes(
                            (4000L + i) * writtenBytesMultiplier,
                            (5000L + i) * writtenBytesMultiplier,
                            (6000L + i) * writtenBytesMultiplier),
                    /* forgivenWriteBytes= */
                    CarWatchdogServiceUnitTest.constructPerStateBytes(200L, 200L, 200L),
                    /* totalOveruses= */ 1, /* forgivenOveruses= */ 0, /* totalTimesKilled= */ 0));
        }
        return entries;
    }

    static WatchdogStorage.IoUsageStatsEntry constructIoUsageStatsEntry(
            int userId, String packageName, long startTime, long duration,
            PerStateBytes remainingWriteBytes, PerStateBytes writtenBytes,
            PerStateBytes forgivenWriteBytes, int totalOveruses, int forgivenOveruses,
            int totalTimesKilled) {
        WatchdogPerfHandler.PackageIoUsage ioUsage = new WatchdogPerfHandler.PackageIoUsage(
                constructInternalIoOveruseStats(startTime, duration, remainingWriteBytes,
                        writtenBytes, totalOveruses), forgivenWriteBytes, forgivenOveruses,
                totalTimesKilled);
        return new WatchdogStorage.IoUsageStatsEntry(userId, packageName, ioUsage);
    }

    private static android.automotive.watchdog.IoOveruseStats constructInternalIoOveruseStats(
            long startTime, long duration, PerStateBytes remainingWriteBytes,
            PerStateBytes writtenBytes, int totalOveruses) {
        android.automotive.watchdog.IoOveruseStats stats =
                new android.automotive.watchdog.IoOveruseStats();
        stats.startTime = startTime;
        stats.durationInSeconds = duration;
        stats.remainingWriteBytes = remainingWriteBytes;
        stats.writtenBytes = writtenBytes;
        stats.totalOveruses = totalOveruses;
        return stats;
    }

    private static final class TestTimeSource extends TimeSource {
        private static final Instant TEST_DATE_TIME = Instant.parse("2021-11-12T13:14:15.16Z");
        private Instant mNow;
        TestTimeSource() {
            mNow = TEST_DATE_TIME;
        }

        @Override
        public Instant now() {
            /* Return the same time, so the tests are deterministic. */
            return mNow;
        }

        @Override
        public String toString() {
            return "Mocked date to " + now();
        }

        void updateNow(int numDaysAgo) {
            mNow = TEST_DATE_TIME.minus(numDaysAgo, ChronoUnit.DAYS);
        }
    };
}
