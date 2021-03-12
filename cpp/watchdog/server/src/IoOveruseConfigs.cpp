/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "carwatchdogd"

#include "IoOveruseConfigs.h"

#include "PackageInfoResolver.h"

#include <android-base/strings.h>
#include <utils/String8.h>

#include <inttypes.h>

#include <limits>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::String16;
using ::android::String8;
using ::android::automotive::watchdog::PerStateBytes;
using ::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::android::automotive::watchdog::internal::ComponentType;
using ::android::automotive::watchdog::internal::IoOveruseAlertThreshold;
using ::android::automotive::watchdog::internal::IoOveruseConfiguration;
using ::android::automotive::watchdog::internal::PackageInfo;
using ::android::automotive::watchdog::internal::PerStateIoOveruseThreshold;
using ::android::automotive::watchdog::internal::UidType;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StartsWith;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::binder::Status;

namespace {

// Enum to filter the updatable I/O overuse configs by each component.
enum IoOveruseConfigEnum {
    COMPONENT_SPECIFIC_GENERIC_THRESHOLDS = 1 << 0,
    COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS = 1 << 1,
    COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES = 1 << 2,
    PER_CATEGORY_THRESHOLDS = 1 << 3,
    VENDOR_PACKAGE_PREFIXES = 1 << 4,
    SYSTEM_WIDE_ALERT_THRESHOLDS = 1 << 5,
};

const int32_t kSystemComponentUpdatableConfigs = COMPONENT_SPECIFIC_GENERIC_THRESHOLDS |
        COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS | COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES |
        SYSTEM_WIDE_ALERT_THRESHOLDS;
const int32_t kVendorComponentUpdatableConfigs = COMPONENT_SPECIFIC_GENERIC_THRESHOLDS |
        COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS | COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES |
        PER_CATEGORY_THRESHOLDS | VENDOR_PACKAGE_PREFIXES;
const int32_t kThirdPartyComponentUpdatableConfigs = COMPONENT_SPECIFIC_GENERIC_THRESHOLDS;

bool isZeroValueThresholds(const PerStateIoOveruseThreshold& thresholds) {
    return thresholds.perStateWriteBytes.foregroundBytes == 0 &&
            thresholds.perStateWriteBytes.backgroundBytes == 0 &&
            thresholds.perStateWriteBytes.garageModeBytes == 0;
}

std::string toString(const PerStateIoOveruseThreshold& thresholds) {
    return StringPrintf("name=%s, foregroundBytes=%" PRId64 ", backgroundBytes=%" PRId64
                        ", garageModeBytes=%" PRId64,
                        String8(thresholds.name).c_str(),
                        thresholds.perStateWriteBytes.foregroundBytes,
                        thresholds.perStateWriteBytes.backgroundBytes,
                        thresholds.perStateWriteBytes.garageModeBytes);
}

Result<void> containsValidThresholds(const PerStateIoOveruseThreshold& thresholds) {
    if (thresholds.name.size() == 0) {
        return Error() << "Doesn't contain threshold name";
    }

    if (isZeroValueThresholds(thresholds)) {
        return Error() << "Zero value thresholds for " << thresholds.name;
    }

    if (thresholds.perStateWriteBytes.foregroundBytes == 0 ||
        thresholds.perStateWriteBytes.backgroundBytes == 0 ||
        thresholds.perStateWriteBytes.garageModeBytes == 0) {
        return Error() << "Some thresholds are zero: " << toString(thresholds);
    }
    return {};
}

Result<void> containsValidThreshold(const IoOveruseAlertThreshold& threshold) {
    if (threshold.durationInSeconds == 0) {
        return Error() << "Duration must be greater than zero";
    }
    if (threshold.writtenBytesPerSecond == 0) {
        return Error() << "Written bytes/second must be greater than zero";
    }
    return {};
}

ApplicationCategoryType toApplicationCategoryType(const std::string& value) {
    if (value == "MAPS") {
        return ApplicationCategoryType::MAPS;
    }
    if (value == "MEDIA") {
        return ApplicationCategoryType::MEDIA;
    }
    return ApplicationCategoryType::OTHERS;
}

Result<void> isValidIoOveruseConfiguration(const ComponentType componentType,
                                           const int32_t updatableConfigsFilter,
                                           const IoOveruseConfiguration& updateConfig) {
    if (auto result = containsValidThresholds(updateConfig.componentLevelThresholds);
        (updatableConfigsFilter & IoOveruseConfigEnum::COMPONENT_SPECIFIC_GENERIC_THRESHOLDS) &&
        !result.ok()) {
        return Error() << "Invalid " << toString(componentType)
                       << " component level generic thresholds: " << result.error();
    }
    const auto containsValidSystemWideThresholds = [&]() -> bool {
        if (updateConfig.systemWideThresholds.empty()) {
            return false;
        }
        for (const auto& threshold : updateConfig.systemWideThresholds) {
            if (auto result = containsValidThreshold(threshold); !result.ok()) {
                return false;
            }
        }
        return true;
    };
    if ((updatableConfigsFilter & IoOveruseConfigEnum::SYSTEM_WIDE_ALERT_THRESHOLDS) &&
        !containsValidSystemWideThresholds()) {
        return Error() << "Invalid system-wide alert threshold provided in "
                       << toString(componentType) << " config";
    }
    return {};
}

}  // namespace

Result<void> ComponentSpecificConfig::updatePerPackageThresholds(
        const std::vector<PerStateIoOveruseThreshold>& thresholds,
        const std::function<void(const std::string&)>& maybeAppendVendorPackagePrefixes) {
    mPerPackageThresholds.clear();
    if (thresholds.empty()) {
        return Error() << "\tNo per-package thresholds provided so clearing it\n";
    }
    std::string errorMsgs;
    for (const auto& packageThreshold : thresholds) {
        std::string packageName = std::string(String8(packageThreshold.name));
        if (packageName.empty()) {
            StringAppendF(&errorMsgs, "\tSkipping per-package threshold without package name\n");
            continue;
        }
        maybeAppendVendorPackagePrefixes(packageName);
        if (auto result = containsValidThresholds(packageThreshold); !result.ok()) {
            StringAppendF(&errorMsgs,
                          "\tSkipping invalid package specific thresholds for package %s: %s\n",
                          packageName.c_str(), result.error().message().c_str());
            continue;
        }
        if (const auto& it = mPerPackageThresholds.find(packageName);
            it != mPerPackageThresholds.end()) {
            StringAppendF(&errorMsgs, "\tDuplicate threshold received for package '%s'\n",
                          packageName.c_str());
        }
        mPerPackageThresholds[packageName] = packageThreshold;
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

Result<void> ComponentSpecificConfig::updateSafeToKillPackages(
        const std::vector<String16>& packages,
        const std::function<void(const std::string&)>& maybeAppendVendorPackagePrefixes) {
    mSafeToKillPackages.clear();
    if (packages.empty()) {
        return Error() << "\tNo safe-to-kill packages provided so clearing it\n";
    }
    std::string errorMsgs;
    for (const auto& packageNameStr16 : packages) {
        std::string packageName = std::string(String8(packageNameStr16));
        if (packageName.empty()) {
            StringAppendF(&errorMsgs, "\tSkipping empty safe-to-kill package name");
            continue;
        }
        maybeAppendVendorPackagePrefixes(packageName);
        mSafeToKillPackages.insert(packageName);
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

size_t IoOveruseConfigs::AlertThresholdHashByDuration::operator()(
        const IoOveruseAlertThreshold& threshold) const {
    return std::hash<std::string>{}(std::to_string(threshold.durationInSeconds));
}

bool IoOveruseConfigs::AlertThresholdEqualByDuration::operator()(
        const IoOveruseAlertThreshold& l, const IoOveruseAlertThreshold& r) const {
    return l.durationInSeconds == r.durationInSeconds;
}

Result<void> IoOveruseConfigs::updatePerCategoryThresholds(
        const std::vector<PerStateIoOveruseThreshold>& thresholds) {
    mPerCategoryThresholds.clear();
    if (thresholds.empty()) {
        return Error() << "\tNo per-category thresholds provided so clearing it\n";
    }
    std::string errorMsgs;
    for (const auto& categoryThreshold : thresholds) {
        if (auto result = containsValidThresholds(categoryThreshold); !result.ok()) {
            StringAppendF(&errorMsgs, "\tInvalid category specific thresholds: %s\n",
                          result.error().message().c_str());
            continue;
        }
        std::string name = std::string(String8(categoryThreshold.name));
        if (auto category = toApplicationCategoryType(name);
            category == ApplicationCategoryType::OTHERS) {
            StringAppendF(&errorMsgs, "\tInvalid application category %s\n", name.c_str());
        } else {
            if (const auto& it = mPerCategoryThresholds.find(category);
                it != mPerCategoryThresholds.end()) {
                StringAppendF(&errorMsgs, "\tDuplicate threshold received for category: '%s'\n",
                              name.c_str());
            }
            mPerCategoryThresholds[category] = categoryThreshold;
        }
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

Result<void> IoOveruseConfigs::updateAlertThresholds(
        const std::vector<IoOveruseAlertThreshold>& thresholds) {
    mAlertThresholds.clear();
    std::string errorMsgs;
    for (const auto& alertThreshold : thresholds) {
        if (auto result = containsValidThreshold(alertThreshold); !result.ok()) {
            StringAppendF(&errorMsgs, "\tInvalid system-wide alert threshold: %s\n",
                          result.error().message().c_str());
            continue;
        }
        if (const auto& it = mAlertThresholds.find(alertThreshold); it != mAlertThresholds.end()) {
            StringAppendF(&errorMsgs,
                          "\tDuplicate threshold received for duration %" PRId64
                          ". Overwriting previous threshold with %" PRId64
                          " written bytes per second \n",
                          alertThreshold.durationInSeconds, it->writtenBytesPerSecond);
        }
        mAlertThresholds.emplace(alertThreshold);
    }
    return errorMsgs.empty() ? Result<void>{} : Error() << errorMsgs;
}

Result<void> IoOveruseConfigs::update(const ComponentType componentType,
                                      const IoOveruseConfiguration& updateConfig) {
    const std::string componentTypeStr = toString(componentType);
    if (auto configComponentTypeStr = String8(updateConfig.componentLevelThresholds.name).string();
        configComponentTypeStr != componentTypeStr) {
        return Error(Status::EX_ILLEGAL_ARGUMENT)
                << "Invalid config: Config's component name '" << configComponentTypeStr
                << "' != component name in update request '" << componentTypeStr << "'";
    }
    ComponentSpecificConfig* targetComponentConfig;
    int32_t updatableConfigsFilter = 0;
    switch (componentType) {
        case ComponentType::SYSTEM:
            targetComponentConfig = &mSystemConfig;
            updatableConfigsFilter = kSystemComponentUpdatableConfigs;
            break;
        case ComponentType::VENDOR:
            targetComponentConfig = &mVendorConfig;
            updatableConfigsFilter = kVendorComponentUpdatableConfigs;
            break;
        case ComponentType::THIRD_PARTY:
            targetComponentConfig = &mThirdPartyConfig;
            updatableConfigsFilter = kThirdPartyComponentUpdatableConfigs;
            break;
        default:
            return Error(Status::EX_ILLEGAL_ARGUMENT)
                    << "Invalid component type " << componentTypeStr;
    }
    if (auto result =
                isValidIoOveruseConfiguration(componentType, updatableConfigsFilter, updateConfig);
        !result.ok()) {
        return Error(Status::EX_ILLEGAL_ARGUMENT) << result.error();
    }

    if ((updatableConfigsFilter & IoOveruseConfigEnum::COMPONENT_SPECIFIC_GENERIC_THRESHOLDS)) {
        targetComponentConfig->mGeneric = updateConfig.componentLevelThresholds;
    }

    std::string nonUpdatableConfigMsgs;
    if (updatableConfigsFilter & IoOveruseConfigEnum::VENDOR_PACKAGE_PREFIXES) {
        mVendorPackagePrefixes.clear();
        for (const auto& prefixStr16 : updateConfig.vendorPackagePrefixes) {
            if (auto prefix = std::string(String8(prefixStr16)); !prefix.empty()) {
                mVendorPackagePrefixes.insert(prefix);
            }
        }
    } else if (!updateConfig.vendorPackagePrefixes.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%svendor packages prefixes",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    std::string errorMsgs;
    const auto maybeAppendVendorPackagePrefixes =
            [& componentType = std::as_const(componentType),
             &vendorPackagePrefixes = mVendorPackagePrefixes](const std::string& packageName) {
                if (componentType != ComponentType::VENDOR) {
                    return;
                }
                for (const auto& prefix : vendorPackagePrefixes) {
                    if (StartsWith(packageName, prefix)) {
                        return;
                    }
                }
                vendorPackagePrefixes.insert(packageName);
            };

    if (updatableConfigsFilter & IoOveruseConfigEnum::COMPONENT_SPECIFIC_PER_PACKAGE_THRESHOLDS) {
        if (auto result =
                    targetComponentConfig
                            ->updatePerPackageThresholds(updateConfig.packageSpecificThresholds,
                                                         maybeAppendVendorPackagePrefixes);
            !result.ok()) {
            StringAppendF(&errorMsgs, "%s", result.error().message().c_str());
        }
    } else if (!updateConfig.packageSpecificThresholds.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%sper-package thresholds",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & IoOveruseConfigEnum::COMPONENT_SPECIFIC_SAFE_TO_KILL_PACKAGES) {
        if (auto result = targetComponentConfig
                                  ->updateSafeToKillPackages(updateConfig.safeToKillPackages,
                                                             maybeAppendVendorPackagePrefixes);
            !result.ok()) {
            StringAppendF(&errorMsgs, "%s", result.error().message().c_str());
        }
    } else if (!updateConfig.safeToKillPackages.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%ssafe-to-kill list",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & IoOveruseConfigEnum::PER_CATEGORY_THRESHOLDS) {
        if (auto result = updatePerCategoryThresholds(updateConfig.categorySpecificThresholds);
            !result.ok()) {
            StringAppendF(&errorMsgs, "%s", result.error().message().c_str());
        }
    } else if (!updateConfig.categorySpecificThresholds.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%scategory specific thresholds",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (updatableConfigsFilter & IoOveruseConfigEnum::SYSTEM_WIDE_ALERT_THRESHOLDS) {
        if (auto result = updateAlertThresholds(updateConfig.systemWideThresholds); !result.ok()) {
            StringAppendF(&errorMsgs, "%s", result.error().message().c_str());
        }
    } else if (!updateConfig.systemWideThresholds.empty()) {
        StringAppendF(&nonUpdatableConfigMsgs, "%ssystem-wide alert thresholds",
                      !nonUpdatableConfigMsgs.empty() ? ", " : "");
    }

    if (!nonUpdatableConfigMsgs.empty()) {
        StringAppendF(&errorMsgs, "\tReceived values for non-updatable configs: %s\n",
                      nonUpdatableConfigMsgs.c_str());
    }
    if (!errorMsgs.empty()) {
        ALOGE("Invalid I/O overuse configs received for %s component:\n%s",
              componentTypeStr.c_str(), errorMsgs.c_str());
    }
    return {};
}

PerStateBytes IoOveruseConfigs::fetchThreshold(const PackageInfo& packageInfo) const {
    const std::string packageName = std::string(String8(packageInfo.packageIdentifier.name));
    switch (packageInfo.componentType) {
        case ComponentType::SYSTEM:
            if (const auto it = mSystemConfig.mPerPackageThresholds.find(packageName);
                it != mSystemConfig.mPerPackageThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            if (const auto it = mPerCategoryThresholds.find(packageInfo.appCategoryType);
                it != mPerCategoryThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            return mSystemConfig.mGeneric.perStateWriteBytes;
        case ComponentType::VENDOR:
            if (const auto it = mVendorConfig.mPerPackageThresholds.find(packageName);
                it != mVendorConfig.mPerPackageThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            if (const auto it = mPerCategoryThresholds.find(packageInfo.appCategoryType);
                it != mPerCategoryThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            return mVendorConfig.mGeneric.perStateWriteBytes;
        case ComponentType::THIRD_PARTY:
            if (const auto it = mPerCategoryThresholds.find(packageInfo.appCategoryType);
                it != mPerCategoryThresholds.end()) {
                return it->second.perStateWriteBytes;
            }
            return mThirdPartyConfig.mGeneric.perStateWriteBytes;
        default:
            ALOGW("Returning default threshold for %s",
                  packageInfo.packageIdentifier.toString().c_str());
            return defaultThreshold().perStateWriteBytes;
    }
}

bool IoOveruseConfigs::isSafeToKill(const PackageInfo& packageInfo) const {
    if (packageInfo.uidType == UidType::NATIVE) {
        // Native packages can't be disabled so don't kill them on I/O overuse.
        return false;
    }
    const std::string packageName = std::string(String8(packageInfo.packageIdentifier.name));
    switch (packageInfo.componentType) {
        case ComponentType::SYSTEM:
            return mSystemConfig.mSafeToKillPackages.find(packageName) !=
                    mSystemConfig.mSafeToKillPackages.end();
        case ComponentType::VENDOR:
            return mVendorConfig.mSafeToKillPackages.find(packageName) !=
                    mVendorConfig.mSafeToKillPackages.end();
        default:
            return true;
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
