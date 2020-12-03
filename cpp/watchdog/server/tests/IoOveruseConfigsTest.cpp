/*
 * Copyright 2020 The Android Open Source Project
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

#include "IoOveruseConfigs.h"

#include <android-base/strings.h>
#include <gmock/gmock.h>

#include <inttypes.h>

namespace android {
namespace automotive {
namespace watchdog {

using android::automotive::watchdog::internal::ApplicationCategoryType;
using android::automotive::watchdog::internal::ComponentType;
using android::automotive::watchdog::internal::IoOveruseAlertThreshold;
using android::automotive::watchdog::internal::IoOveruseConfiguration;
using android::automotive::watchdog::internal::PerStateIoOveruseThreshold;
using android::base::Join;
using android::base::Result;
using android::base::StringAppendF;
using android::base::StringPrintf;

namespace {

bool isEqual(const ComponentSpecificConfig& l, const ComponentSpecificConfig& r) {
    return l.generic == r.generic && l.perPackageThresholds == r.perPackageThresholds &&
            l.safeToKillPackages == r.safeToKillPackages;
}

bool isEqual(const IoOveruseConfigs& l, const IoOveruseConfigs& r) {
    return isEqual(l.systemConfig, r.systemConfig) && isEqual(l.vendorConfig, r.vendorConfig) &&
            isEqual(l.thirdPartyConfig, r.thirdPartyConfig) &&
            l.perCategoryThresholds == r.perCategoryThresholds &&
            l.vendorPackagePrefixes == r.vendorPackagePrefixes &&
            l.alertThresholds == r.alertThresholds;
}

std::string toString(const PerStateIoOveruseThreshold& thresholds) {
    return StringPrintf("name=%s, foregroundBytes=%" PRId64 ", backgroundBytes=%" PRId64
                        ", garageModeBytes=%" PRId64,
                        String8(thresholds.name).c_str(),
                        thresholds.perStateWriteBytes.applicationForegroundBytes,
                        thresholds.perStateWriteBytes.applicationBackgroundBytes,
                        thresholds.perStateWriteBytes.systemGarageModeBytes);
}

std::string toString(const ComponentSpecificConfig& config) {
    std::string output;
    StringAppendF(&output, "\tComponent-level threshold: {%s}\n", toString(config.generic).c_str());
    StringAppendF(&output, "\tPackage specific thresholds:\n");
    for (const auto& it : config.perPackageThresholds) {
        StringAppendF(&output, "\t%s\n", toString(it.second).c_str());
    }
    StringAppendF(&output, "\tSafe-to-kill packages: '%s'",
                  Join(config.safeToKillPackages, ",").c_str());
    return output;
}

std::string toString(const IoOveruseAlertThreshold& threshold) {
    return StringPrintf("aggregateDurationSecs=%" PRId64 ", triggerDurationSecs=%" PRId64
                        ", writtenBytes=%" PRId64,
                        threshold.aggregateDurationSecs, threshold.triggerDurationSecs,
                        threshold.writtenBytes);
}

std::string toString(const IoOveruseConfigs& configs) {
    std::string output;
    StringAppendF(&output, "System component config:\n%s\n",
                  toString(configs.systemConfig).c_str());
    StringAppendF(&output, "Vendor component config:\n%s\n",
                  toString(configs.vendorConfig).c_str());
    StringAppendF(&output, "Third-party component config:\n%s\n",
                  toString(configs.thirdPartyConfig).c_str());
    StringAppendF(&output, "Category specific thresholds:\n");
    for (const auto& it : configs.perCategoryThresholds) {
        StringAppendF(&output, "\t%s\n", toString(it.second).c_str());
    }
    StringAppendF(&output, "Vendor package regex: '%s'\n",
                  Join(configs.vendorPackagePrefixes, ",").c_str());
    StringAppendF(&output, "System-wide I/O overuse alert thresholds:\n");
    for (const auto& it : configs.alertThresholds) {
        StringAppendF(&output, "\t%s\n", toString(it).c_str());
    }
    return output;
}

PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(std::string name, int64_t fgBytes,
                                                        int64_t bgBytes, int64_t garageModeBytes) {
    PerStateIoOveruseThreshold threshold;
    threshold.name = String16(String8(name.c_str()));
    threshold.perStateWriteBytes.applicationForegroundBytes = fgBytes;
    threshold.perStateWriteBytes.applicationBackgroundBytes = bgBytes;
    threshold.perStateWriteBytes.systemGarageModeBytes = garageModeBytes;
    return threshold;
}

PerStateIoOveruseThreshold toPerStateIoOveruseThreshold(ComponentType type, int64_t fgBytes,
                                                        int64_t bgBytes, int64_t garageModeBytes) {
    return toPerStateIoOveruseThreshold(toString(type), fgBytes, bgBytes, garageModeBytes);
}

IoOveruseAlertThreshold toIoOveruseAlertThreshold(int64_t aggregateDurationSecs,
                                                  int64_t triggerDurationSecs,
                                                  int64_t writtenBytes) {
    IoOveruseAlertThreshold threshold;
    threshold.aggregateDurationSecs = aggregateDurationSecs;
    threshold.triggerDurationSecs = triggerDurationSecs;
    threshold.writtenBytes = writtenBytes;
    return threshold;
}

std::vector<android::String16> toString16Vector(std::vector<std::string> values) {
    std::vector<android::String16> output;
    for (const auto v : values) {
        output.emplace_back(String16(String8(v.c_str())));
    }
    return output;
}

}  // namespace

TEST(IoOveruseConfigsTest, TestUpdateWithValidConfigs) {
    IoOveruseConfiguration systemComponentConfig;
    systemComponentConfig.componentLevelThresholds =
            toPerStateIoOveruseThreshold(ComponentType::SYSTEM, 200, 100, 500);
    systemComponentConfig.packageSpecificThresholds =
            {toPerStateIoOveruseThreshold("systemPackageA", 600, 400, 1000),
             toPerStateIoOveruseThreshold("systemPackageB", 1200, 800, 1500)};
    systemComponentConfig.safeToKillPackages = toString16Vector({"systemPackageA"});
    systemComponentConfig.systemWideThresholds = {toIoOveruseAlertThreshold(5, 20, 200),
                                                  toIoOveruseAlertThreshold(30, 600, 40000)};

    IoOveruseConfiguration vendorComponentConfig;
    vendorComponentConfig.componentLevelThresholds =
            toPerStateIoOveruseThreshold(ComponentType::VENDOR, 100, 50, 900);
    vendorComponentConfig.packageSpecificThresholds =
            {toPerStateIoOveruseThreshold("vendorPackageA", 800, 300, 500),
             toPerStateIoOveruseThreshold("vendorPkgB", 1600, 600, 1000)};
    vendorComponentConfig.safeToKillPackages = toString16Vector({"vendorPackageA"});
    vendorComponentConfig.vendorPackagePrefixes = toString16Vector({"vendorPackage", "vendorPkg"});
    vendorComponentConfig.categorySpecificThresholds = {toPerStateIoOveruseThreshold("MAPS", 600,
                                                                                     400, 1000),
                                                        toPerStateIoOveruseThreshold("MEDIA", 1200,
                                                                                     800, 1500)};

    IoOveruseConfiguration thirdPartyComponentConfig;
    thirdPartyComponentConfig.componentLevelThresholds =
            toPerStateIoOveruseThreshold(ComponentType::THIRD_PARTY, 300, 150, 1900);

    const IoOveruseConfigs expected = {
            .systemConfig =
                    {.generic = systemComponentConfig.componentLevelThresholds,
                     .perPackageThresholds =
                             {{"systemPackageA",
                               toPerStateIoOveruseThreshold("systemPackageA", 600, 400, 1000)},
                              {"systemPackageB",
                               toPerStateIoOveruseThreshold("systemPackageB", 1200, 800, 1500)}},
                     .safeToKillPackages = {"systemPackageA"}},
            .vendorConfig =
                    {.generic = vendorComponentConfig.componentLevelThresholds,
                     .perPackageThresholds =
                             {{"vendorPackageA",
                               toPerStateIoOveruseThreshold("vendorPackageA", 800, 300, 500)},
                              {"vendorPkgB",
                               toPerStateIoOveruseThreshold("vendorPkgB", 1600, 600, 1000)}},
                     .safeToKillPackages = {"vendorPackageA"}},
            .thirdPartyConfig = {.generic = thirdPartyComponentConfig.componentLevelThresholds},
            .perCategoryThresholds = {{ApplicationCategoryType::MAPS,
                                       toPerStateIoOveruseThreshold("MAPS", 600, 400, 1000)},
                                      {ApplicationCategoryType::MEDIA,
                                       toPerStateIoOveruseThreshold("MEDIA", 1200, 800, 1500)}},
            .vendorPackagePrefixes = {"vendorPackage", "vendorPkg"},
            .alertThresholds = {toIoOveruseAlertThreshold(5, 20, 200),
                                toIoOveruseAlertThreshold(30, 600, 40000)},
    };

    IoOveruseConfigs actual;
    ASSERT_RESULT_OK(actual.update(ComponentType::SYSTEM, systemComponentConfig));
    ASSERT_RESULT_OK(actual.update(ComponentType::VENDOR, vendorComponentConfig));
    ASSERT_RESULT_OK(actual.update(ComponentType::THIRD_PARTY, thirdPartyComponentConfig));
    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);
}

TEST(IoOveruseConfigsTest, TestFailsUpdateOnInvalidComponentName) {
    IoOveruseConfiguration config;
    config.componentLevelThresholds = toPerStateIoOveruseThreshold("random name", 200, 100, 500);
    const IoOveruseConfigs expected = {};

    IoOveruseConfigs actual;
    ASSERT_FALSE(actual.update(ComponentType::SYSTEM, config).ok());

    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);

    ASSERT_FALSE(actual.update(ComponentType::VENDOR, config).ok());

    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);

    ASSERT_FALSE(actual.update(ComponentType::THIRD_PARTY, config).ok());

    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);
}

TEST(IoOveruseConfigsTest, TestIgnoresNonUpdatableConfigsBySystemComponent) {
    IoOveruseConfiguration config;
    config.componentLevelThresholds =
            toPerStateIoOveruseThreshold(ComponentType::SYSTEM, 200, 100, 500);
    config.packageSpecificThresholds = {toPerStateIoOveruseThreshold("systemPackageA", 600, 400,
                                                                     1000),
                                        toPerStateIoOveruseThreshold("systemPackageB", 1200, 800,
                                                                     1500)};
    config.safeToKillPackages = toString16Vector({"systemPackageA"});
    config.vendorPackagePrefixes = toString16Vector({"vendorPackage"});
    config.categorySpecificThresholds = {toPerStateIoOveruseThreshold("MAPS", 600, 400, 1000),
                                         toPerStateIoOveruseThreshold("MEDIA", 1200, 800, 1500)};
    config.systemWideThresholds = {toIoOveruseAlertThreshold(5, 20, 200),
                                   toIoOveruseAlertThreshold(30, 600, 40000)};
    const IoOveruseConfigs expected = {
            .systemConfig =
                    {.generic = config.componentLevelThresholds,
                     .perPackageThresholds =
                             {{"systemPackageA",
                               toPerStateIoOveruseThreshold("systemPackageA", 600, 400, 1000)},
                              {"systemPackageB",
                               toPerStateIoOveruseThreshold("systemPackageB", 1200, 800, 1500)}},
                     .safeToKillPackages = {"systemPackageA"}},
            .alertThresholds = {toIoOveruseAlertThreshold(5, 20, 200),
                                toIoOveruseAlertThreshold(30, 600, 40000)},
    };
    IoOveruseConfigs actual;
    ASSERT_RESULT_OK(actual.update(ComponentType::SYSTEM, config));
    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);
}

TEST(IoOveruseConfigsTest, TestIgnoresNonUpdatableConfigsByVendorComponent) {
    IoOveruseConfiguration config;
    config.componentLevelThresholds =
            toPerStateIoOveruseThreshold(ComponentType::VENDOR, 100, 50, 900);
    config.packageSpecificThresholds =
            {toPerStateIoOveruseThreshold("vendorPackageA", 800, 300, 500),
             toPerStateIoOveruseThreshold("systemPackageB", 1600, 600, 1000),
             toPerStateIoOveruseThreshold("vendorPackageC", 2000, 700, 1100)};
    config.safeToKillPackages = toString16Vector({"vendorPackageA"});
    config.vendorPackagePrefixes = toString16Vector({"vendorPackage"});
    config.categorySpecificThresholds = {toPerStateIoOveruseThreshold("MAPS", 600, 400, 1000),
                                         toPerStateIoOveruseThreshold("MEDIA", 1200, 800, 1500)};
    config.systemWideThresholds = {toIoOveruseAlertThreshold(5, 20, 200),
                                   toIoOveruseAlertThreshold(30, 600, 40000)};
    const IoOveruseConfigs expected = {
            .vendorConfig =
                    {.generic = config.componentLevelThresholds,
                     .perPackageThresholds =
                             {{"vendorPackageA",
                               toPerStateIoOveruseThreshold("vendorPackageA", 800, 300, 500)},
                              {"vendorPackageC",
                               toPerStateIoOveruseThreshold("vendorPackageC", 2000, 700, 1100)}},
                     .safeToKillPackages = {"vendorPackageA"}},
            .perCategoryThresholds = {{ApplicationCategoryType::MAPS,
                                       toPerStateIoOveruseThreshold("MAPS", 600, 400, 1000)},
                                      {ApplicationCategoryType::MEDIA,
                                       toPerStateIoOveruseThreshold("MEDIA", 1200, 800, 1500)}},
            .vendorPackagePrefixes = {"vendorPackage"},
    };
    IoOveruseConfigs actual;
    ASSERT_RESULT_OK(actual.update(ComponentType::VENDOR, config));
    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);
}

TEST(IoOveruseConfigsTest, TestIgnoresNonUpdatableConfigsByThirdPartyComponent) {
    IoOveruseConfiguration config;
    config.componentLevelThresholds =
            toPerStateIoOveruseThreshold(ComponentType::THIRD_PARTY, 300, 150, 1900);
    config.packageSpecificThresholds = {toPerStateIoOveruseThreshold("vendorPackageA", 800, 300,
                                                                     500),
                                        toPerStateIoOveruseThreshold("systemPackageB", 1600, 600,
                                                                     1000)};
    config.safeToKillPackages = toString16Vector({"vendorPackageA", "systemPackageC"});
    config.vendorPackagePrefixes = toString16Vector({"vendorPackage"});
    config.categorySpecificThresholds = {toPerStateIoOveruseThreshold("MAPS", 600, 400, 1000),
                                         toPerStateIoOveruseThreshold("MEDIA", 1200, 800, 1500)};
    config.systemWideThresholds = {toIoOveruseAlertThreshold(5, 20, 200),
                                   toIoOveruseAlertThreshold(30, 600, 40000)};
    const IoOveruseConfigs expected = {
            .thirdPartyConfig = {.generic = config.componentLevelThresholds},
    };
    IoOveruseConfigs actual;
    ASSERT_RESULT_OK(actual.update(ComponentType::THIRD_PARTY, config));
    ASSERT_TRUE(isEqual(actual, expected)) << "Expected:\n"
                                           << toString(expected) << "\nActual:\n"
                                           << toString(actual);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
