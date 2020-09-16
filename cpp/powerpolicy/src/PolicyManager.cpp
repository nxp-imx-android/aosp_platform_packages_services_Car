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

#define LOG_TAG "carpowerpolicyd"

#include "PolicyManager.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <binder/Enums.h>

#include <tinyxml2.h>

#include <cstring>
#include <unordered_set>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using android::base::Error;
using android::base::Result;
using android::base::StringAppendF;
using android::base::StringPrintf;
using android::base::WriteStringToFd;
using tinyxml2::XML_SUCCESS;
using tinyxml2::XMLDocument;
using tinyxml2::XMLElement;

namespace {

// Vendor power policy filename.
constexpr const char* kVendorPolicyFile = "/vendor/etc/power_policy.xml";

// Tags and attributes in vendor power policy XML file.
constexpr const char* kTagRoot = "powerPolicy";
constexpr const char* kTagPolicyGroups = "policyGroups";
constexpr const char* kTagPolicyGroup = "policyGroup";
constexpr const char* kTagDefaultPolicy = "defaultPolicy";
constexpr const char* kTagNoDefaultPolicy = "noDefaultPolicy";
constexpr const char* kTagPolicies = "policies";
constexpr const char* kTagPolicy = "policy";
constexpr const char* kTagOtherComponents = "otherComponents";
constexpr const char* kTagComponent = "component";
constexpr const char* kTagSystemPolicyOverrides = "systemPolicyOverrides";
constexpr const char* kAttrBehavior = "behavior";
constexpr const char* kAttrId = "id";
constexpr const char* kAttrState = "state";

// Power states.
constexpr const char* kPowerStateOn = "on";
constexpr const char* kPowerStateOff = "off";
constexpr const char* kPowerStateUntouched = "untouched";

// Power transitions that a power policy can be applied with.
constexpr const char* kPowerTransitionWaitForVhal = "WaitForVHAL";
constexpr const char* kPowerTransitionOn = "On";
constexpr const char* kPowerTransitionShutdownStart = "ShutdownStart";
constexpr const char* kPowerTransitionDeepSleepEntry = "DeepSleepEntry";

const PowerComponent INVALID_POWER_COMPONENT = static_cast<PowerComponent>(-1);

constexpr const char* kPowerComponentPrefix = "POWER_COMPONENT_";

// System power policy definition: ID, enabled components, and disabled components.
constexpr const char* kSystemPolicyId = "system_power_policy_no_user_interaction";
const std::vector<PowerComponent> kSystemPolicyEnabledComponents =
        {PowerComponent::WIFI, PowerComponent::CELLULAR, PowerComponent::ETHERNET,
         PowerComponent::TRUSTED_DEVICE_DETECTION};
const std::vector<PowerComponent> kSystemPolicyDisabledComponents =
        {PowerComponent::AUDIO,
         PowerComponent::MEDIA,
         PowerComponent::DISPLAY_MAIN,
         PowerComponent::DISPLAY_CLUSTER,
         PowerComponent::DISPLAY_FRONT_PASSENGER,
         PowerComponent::DISPLAY_REAR_PASSENGER,
         PowerComponent::BLUETOOTH,
         PowerComponent::PROJECTION,
         PowerComponent::NFC,
         PowerComponent::INPUT,
         PowerComponent::VOICE_INTERACTION,
         PowerComponent::VISUAL_INTERACTION};
const std::vector<PowerComponent> kSystemPolicyConfigurableComponents =
        {PowerComponent::BLUETOOTH, PowerComponent::NFC, PowerComponent::TRUSTED_DEVICE_DETECTION};

void iterateAllPowerComponents(const std::function<bool(PowerComponent)>& processor) {
    for (const auto component : enum_range<PowerComponent>()) {
        if (!processor(component)) {
            break;
        }
    }
}

PowerComponent toPowerComponent(const char* id) {
    std::string componentId = id;
    size_t lenPrefix = strlen(kPowerComponentPrefix);
    if (componentId.substr(0, lenPrefix) != kPowerComponentPrefix) {
        return INVALID_POWER_COMPONENT;
    }
    componentId = componentId.substr(lenPrefix, std::string::npos);
    PowerComponent matchedComponent = INVALID_POWER_COMPONENT;
    iterateAllPowerComponents([componentId, &matchedComponent](PowerComponent component) {
        if (componentId == toString(component)) {
            matchedComponent = component;
            return false;
        }
        return true;
    });
    return matchedComponent;
}

const char* safePtrPrint(const char* ptr) {
    return ptr == nullptr ? "nullptr" : ptr;
}

bool isValidPowerTransition(const char* transition) {
    return !strcmp(transition, kPowerTransitionWaitForVhal) ||
            !strcmp(transition, kPowerTransitionOn) ||
            !strcmp(transition, kPowerTransitionDeepSleepEntry) ||
            !strcmp(transition, kPowerTransitionShutdownStart);
}

void logXmlError(const std::string& errMsg) {
    ALOGW("Proceed without registered policies: %s", errMsg.c_str());
}

bool readComponents(const XMLElement* pPolicy, CarPowerPolicyPtr policy,
                    std::unordered_set<PowerComponent>* visited) {
    for (const XMLElement* pComponent = pPolicy->FirstChildElement(kTagComponent);
         pComponent != nullptr; pComponent = pComponent->NextSiblingElement(kTagComponent)) {
        const char* id;
        if (pComponent->QueryStringAttribute(kAttrId, &id) != XML_SUCCESS) {
            logXmlError(StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                     kTagComponent));
            return false;
        }
        PowerComponent componentId = toPowerComponent(id);
        if (componentId == INVALID_POWER_COMPONENT) {
            logXmlError(StringPrintf("XML configuration has invalid value(%s) in |%s| attribute of "
                                     "|%s| tag",
                                     safePtrPrint(id), kAttrId, kTagComponent)
                                .c_str());
            return false;
        }
        if (visited->count(componentId) > 0) {
            logXmlError(StringPrintf("XML configuration has duplicated component(%s) in |%s| "
                                     "attribute of |%s| tag",
                                     toString(componentId).c_str(), kAttrId, kTagComponent)
                                .c_str());
            return false;
        }
        visited->insert(componentId);
        const char* powerState = pComponent->GetText();
        if (!strcmp(powerState, kPowerStateOn)) {
            policy->enabledComponents.push_back(componentId);
        } else if (!strcmp(powerState, kPowerStateOff)) {
            policy->disabledComponents.push_back(componentId);
        } else {
            logXmlError(StringPrintf("XML configuration has invalid value(%s) in |%s| tag",
                                     safePtrPrint(powerState), kTagComponent));
            return false;
        }
    }
    return true;
}

bool readOtherComponents(const XMLElement* pPolicy, CarPowerPolicyPtr policy,
                         const std::unordered_set<PowerComponent>& visited) {
    const char* otherComponentBehavior = kPowerStateUntouched;
    const XMLElement* pElement = pPolicy->FirstChildElement(kTagOtherComponents);
    if (pElement != nullptr) {
        if (pElement->QueryStringAttribute(kAttrBehavior, &otherComponentBehavior) != XML_SUCCESS) {
            logXmlError(StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrBehavior,
                                     kTagOtherComponents));
            return false;
        }
    }
    if (!strcmp(otherComponentBehavior, kPowerStateOn)) {
        iterateAllPowerComponents([&visited, &policy](PowerComponent component) {
            if (visited.count(component) == 0) {
                policy->enabledComponents.push_back(component);
            }
            return true;
        });
    } else if (!strcmp(otherComponentBehavior, kPowerStateOff)) {
        iterateAllPowerComponents([&visited, &policy](PowerComponent component) {
            if (visited.count(component) == 0) {
                policy->disabledComponents.push_back(component);
            }
            return true;
        });
    } else if (!strcmp(otherComponentBehavior, kPowerStateUntouched)) {
        // Do nothing
    } else {
        logXmlError(StringPrintf("XML configuration has invalid value(%s) in |%s| attribute of "
                                 "|%s| tag",
                                 safePtrPrint(otherComponentBehavior), kAttrBehavior,
                                 kTagOtherComponents));
        return false;
    }
    return true;
}

std::vector<CarPowerPolicyPtr> readPolicies(const XMLElement* pRoot, const char* tag,
                                            bool includeOtherComponents) {
    std::vector<CarPowerPolicyPtr> policies;
    const XMLElement* pPolicies = pRoot->FirstChildElement(tag);
    if (pPolicies == nullptr) {
        return std::vector<CarPowerPolicyPtr>();
    }
    for (const XMLElement* pPolicy = pPolicies->FirstChildElement(kTagPolicy); pPolicy != nullptr;
         pPolicy = pPolicy->NextSiblingElement(kTagPolicy)) {
        std::unordered_set<PowerComponent> visited;
        const char* policyId;
        if (pPolicy->QueryStringAttribute(kAttrId, &policyId) != XML_SUCCESS) {
            logXmlError(
                    StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId, kTagPolicy));
            return std::vector<CarPowerPolicyPtr>();
        }
        auto policy = std::make_shared<CarPowerPolicy>();
        policy->policyId = policyId;

        if (!readComponents(pPolicy, policy, &visited)) {
            return std::vector<CarPowerPolicyPtr>();
        }
        if (includeOtherComponents) {
            if (!readOtherComponents(pPolicy, policy, visited)) {
                return std::vector<CarPowerPolicyPtr>();
            }
        }
        policies.push_back(policy);
    }
    return policies;
}

Result<PolicyGroup> readPolicyGroup(
        const XMLElement* pPolicyGroup,
        const std::unordered_map<std::string, CarPowerPolicyPtr>& registeredPowerPolicies) {
    PolicyGroup policyGroup;
    for (const XMLElement* pDefaultPolicy = pPolicyGroup->FirstChildElement(kTagDefaultPolicy);
         pDefaultPolicy != nullptr;
         pDefaultPolicy = pDefaultPolicy->NextSiblingElement(kTagDefaultPolicy)) {
        const char* state;
        if (pDefaultPolicy->QueryStringAttribute(kAttrState, &state) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrState,
                                           kTagDefaultPolicy);
        }
        if (!isValidPowerTransition(state)) {
            return Error() << StringPrintf("Target state(%s) is not valid", state);
        }
        const char* policyId;
        if (pDefaultPolicy->QueryStringAttribute(kAttrId, &policyId) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                           kTagDefaultPolicy);
        }
        if (registeredPowerPolicies.count(policyId) == 0) {
            return Error() << StringPrintf("Policy(id: %s) is not registered", policyId);
        }
        policyGroup.emplace(state, policyId);
    }
    for (const XMLElement* pNoPolicy = pPolicyGroup->FirstChildElement(kTagNoDefaultPolicy);
         pNoPolicy != nullptr; pNoPolicy = pNoPolicy->NextSiblingElement(kTagNoDefaultPolicy)) {
        const char* state;
        if (pNoPolicy->QueryStringAttribute(kAttrState, &state) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrState,
                                           kTagNoDefaultPolicy);
        }
        if (!isValidPowerTransition(state)) {
            return Error() << StringPrintf("Target state(%s) is not valid", state);
        }
        if (policyGroup.count(state) > 0) {
            return Error()
                    << StringPrintf("Target state(%s) is specified both in |%s| and |%s| tags",
                                    state, kTagDefaultPolicy, kTagNoDefaultPolicy);
        }
    }
    return policyGroup;
}

std::unordered_map<std::string, PolicyGroup> readPolicyGroups(
        const XMLElement* pRoot,
        const std::unordered_map<std::string, CarPowerPolicyPtr>& registeredPowerPolicies) {
    const XMLElement* pPolicyGroups = pRoot->FirstChildElement(kTagPolicyGroups);
    if (pPolicyGroups == nullptr) {
        return std::unordered_map<std::string, PolicyGroup>();
    }
    std::unordered_map<std::string, PolicyGroup> policyGroups;

    for (const XMLElement* pPolicyGroup = pPolicyGroups->FirstChildElement(kTagPolicyGroup);
         pPolicyGroup != nullptr;
         pPolicyGroup = pPolicyGroup->NextSiblingElement(kTagPolicyGroup)) {
        const char* policyGroupId;
        if (pPolicyGroup->QueryStringAttribute(kAttrId, &policyGroupId) != XML_SUCCESS) {
            logXmlError(StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                     kTagPolicyGroup));
            return std::unordered_map<std::string, PolicyGroup>();
        }
        const auto& policyGroup = readPolicyGroup(pPolicyGroup, registeredPowerPolicies);
        if (!policyGroup.ok()) {
            logXmlError(policyGroup.error().message());
            return std::unordered_map<std::string, PolicyGroup>();
        }
        policyGroups.emplace(policyGroupId, *policyGroup);
    }
    return policyGroups;
}

std::vector<CarPowerPolicyPtr> readSystemPolicyOverrides(const XMLElement* pRoot) {
    return readPolicies(pRoot, kTagSystemPolicyOverrides, false);
}

bool isConfigurableComponent(PowerComponent component) {
    for (auto configurableComponent : kSystemPolicyConfigurableComponents) {
        if (component == configurableComponent) {
            return true;
        }
    }
    return false;
}

bool configureComponents(const std::vector<PowerComponent>& configComponents,
                         std::vector<PowerComponent>* systemComponents) {
    for (const auto component : configComponents) {
        if (!isConfigurableComponent(component)) {
            ALOGW("Component(%s) is not configurable in system power policy.",
                  toString(component).c_str());
            return false;
        }
        auto it = std::find(systemComponents->begin(), systemComponents->end(), component);
        if (it == systemComponents->end()) {
            systemComponents->push_back(component);
        }
    }
    return true;
}

}  // namespace

std::string toString(const std::vector<PowerComponent>& components) {
    size_t size = components.size();
    if (size == 0) {
        return "none";
    }
    std::string filterStr = toString(components[0]);
    for (int i = 1; i < size; i++) {
        StringAppendF(&filterStr, ", %s", toString(components[i]).c_str());
    }
    return filterStr;
}

std::string toString(const CarPowerPolicy& policy) {
    return StringPrintf("%s(enabledComponents: %s, disabledComponents: %s)",
                        policy.policyId.c_str(), toString(policy.enabledComponents).c_str(),
                        toString(policy.disabledComponents).c_str());
}

void PolicyManager::init() {
    readPowerPolicyConfiguration();
}

CarPowerPolicyPtr PolicyManager::getPowerPolicy(const std::string& policyId) {
    if (mRegisteredPowerPolicies.count(policyId) == 0) {
        ALOGW("Policy(id: %s) is not found", policyId.c_str());
        return nullptr;
    }
    return mRegisteredPowerPolicies.at(policyId);
}

CarPowerPolicyPtr PolicyManager::getDefaultPowerPolicyForTransition(
        const std::string& powerTransition) {
    if (mPolicyGroups.count(mCurrentPolicyGroupId) == 0) {
        ALOGW("The current power policy group is not set");
        return nullptr;
    }
    PolicyGroup policyGroup = mPolicyGroups.at(mCurrentPolicyGroupId);
    if (policyGroup.count(powerTransition) == 0) {
        ALOGW("Policy for %s is not found", powerTransition.c_str());
        return nullptr;
    }
    return mRegisteredPowerPolicies.at(policyGroup.at(powerTransition));
}

CarPowerPolicyPtr PolicyManager::getSystemPowerPolicy() {
    return mSystemPowerPolicy;
}

Result<void> PolicyManager::dump(int fd, const Vector<String16>& /*args*/) {
    const char* indent = "  ";
    const char* doubleIndent = "    ";
    const char* tripleIndent = "      ";

    WriteStringToFd(StringPrintf("%sRegistered power policies:%s\n", indent,
                                 mRegisteredPowerPolicies.size() ? "" : " none"),
                    fd);
    for (auto& it : mRegisteredPowerPolicies) {
        WriteStringToFd(StringPrintf("%s- %s\n", doubleIndent, toString(*it.second).c_str()), fd);
    }
    WriteStringToFd(StringPrintf("%sCurrent power policy group ID: %s\n", indent,
                                 mCurrentPolicyGroupId.empty() ? "not set"
                                                               : mCurrentPolicyGroupId.c_str()),
                    fd);
    WriteStringToFd(StringPrintf("%sPower policy groups: %s\n", indent,
                                 mPolicyGroups.size() ? "" : " none"),
                    fd);
    for (auto& itGroup : mPolicyGroups) {
        WriteStringToFd(StringPrintf("%s%s\n", doubleIndent, itGroup.first.c_str()), fd);
        for (auto& itMapping : itGroup.second) {
            WriteStringToFd(StringPrintf("%s- %s --> %s\n", tripleIndent, itMapping.first.c_str(),
                                         itMapping.second.c_str()),
                            fd);
        }
    }
    WriteStringToFd(StringPrintf("%sSystem power policy: %s\n", indent,
                                 toString(*mSystemPowerPolicy).c_str()),
                    fd);
    return {};
}

void PolicyManager::readPowerPolicyConfiguration() {
    XMLDocument xmlDoc;
    xmlDoc.LoadFile(kVendorPolicyFile);
    if (xmlDoc.ErrorID() != XML_SUCCESS) {
        logXmlError(StringPrintf("Failed to read and/or parse %s", kVendorPolicyFile));
        return;
    }
    const XMLElement* pRootElement = xmlDoc.RootElement();
    if (!pRootElement || strcmp(pRootElement->Name(), kTagRoot)) {
        logXmlError(StringPrintf("XML file is not in the required format"));
        return;
    }
    mRegisteredPowerPolicies.clear();
    const auto& registeredPolicies = readPolicies(pRootElement, kTagPolicies, true);
    for (auto policy : registeredPolicies) {
        mRegisteredPowerPolicies.emplace(policy->policyId, policy);
    }
    mPolicyGroups.clear();
    mPolicyGroups = readPolicyGroups(pRootElement, mRegisteredPowerPolicies);
    const auto& systemPolicyOverrides = readSystemPolicyOverrides(pRootElement);
    reconstructSystemPolicies(systemPolicyOverrides);
}

void PolicyManager::reconstructSystemPolicies(
        const std::vector<CarPowerPolicyPtr>& policyOverrides) {
    CarPowerPolicyPtr systemPolicy = std::make_shared<CarPowerPolicy>();
    systemPolicy->policyId = kSystemPolicyId;
    systemPolicy->enabledComponents = kSystemPolicyEnabledComponents;
    systemPolicy->disabledComponents = kSystemPolicyDisabledComponents;

    for (auto policy : policyOverrides) {
        if (policy->policyId != kSystemPolicyId) {
            ALOGW("System power policy(%s) is not supported.", policy->policyId.c_str());
            return;
        }
        if (!configureComponents(policy->enabledComponents, &systemPolicy->enabledComponents) ||
            !configureComponents(policy->disabledComponents, &systemPolicy->disabledComponents)) {
            return;
        }
    }
    mSystemPowerPolicy = std::move(systemPolicy);
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
