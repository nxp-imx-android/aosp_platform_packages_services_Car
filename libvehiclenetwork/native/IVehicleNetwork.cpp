/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "VehicleNetwork"

#include <memory>

#include <binder/IPCThreadState.h>
#include <private/android_filesystem_config.h>

#include <utils/Log.h>

#include <IVehicleNetwork.h>
#include <VehicleNetworkProto.pb.h>

#include "BinderUtil.h"
#include "VehicleNetworkProtoUtil.h"

namespace android {

enum {
    LIST_PROPERTIES = IBinder::FIRST_CALL_TRANSACTION,
    SET_PROPERTY,
    GET_PROPERTY,
    SUBSCRIBE,
    UNSUBSCRIBE,
};

// ----------------------------------------------------------------------------

const char IVehicleNetwork::SERVICE_NAME[] = "com.android.car.vehiclenetwork.IVehicleNetwork";

// ----------------------------------------------------------------------------

class BpVehicleNetwork : public BpInterface<IVehicleNetwork> {
public:
    BpVehicleNetwork(const sp<IBinder> & impl)
        : BpInterface<IVehicleNetwork>(impl) {
    }

    virtual sp<VehiclePropertiesHolder> listProperties(int32_t property) {
        sp<VehiclePropertiesHolder> holder;
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleNetwork::getInterfaceDescriptor());
        data.writeInt32(property);
        status_t status = remote()->transact(LIST_PROPERTIES, data, &reply);
        if (status == NO_ERROR) {
            reply.readExceptionCode(); // for compatibility with java
            if (reply.readInt32() == 0) { // no result
                return holder;
            }
            ReadableBlobHolder blob(new Parcel::ReadableBlob());
            if (blob.blob == NULL) {
                ALOGE("listProperties, no memory");
                return holder;
            }
            int32_t size = reply.readInt32();
            status = reply.readBlob(size, blob.blob);
            if (status != NO_ERROR) {
                ALOGE("listProperties, cannot read blob %d", status);
                return holder;
            }
            //TODO make this more memory efficient
            std::unique_ptr<VehiclePropConfigs> configs(new VehiclePropConfigs());
            if (configs.get() == NULL) {
                return holder;
            }
            if(!configs->ParseFromArray(blob.blob->data(), size)) {
                ALOGE("listProperties, cannot parse reply");
                return holder;
            }
            vehicle_prop_config_t* configArray = NULL;
            int32_t numConfigs;
            status = VehicleNetworkProtoUtil::fromVehiclePropConfigs(*configs.get(), &configArray,
                    &numConfigs);
            if (status != NO_ERROR) {
                ALOGE("listProperties, cannot convert VehiclePropConfigs %d", status);
                return holder;
            }
            holder = new VehiclePropertiesHolder(configArray, numConfigs);
        }
        return holder;
    }

    virtual status_t setProperty(const vehicle_prop_value_t& value) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleNetwork::getInterfaceDescriptor());
        data.writeInt32(1); // 0 means no value. For compatibility with aidl based code.
        std::unique_ptr<VehiclePropValue> v(new VehiclePropValue());
        ASSERT_OR_HANDLE_NO_MEMORY(v.get(), return NO_MEMORY);
        VehicleNetworkProtoUtil::toVehiclePropValue(value, *v.get());
        int size = v->ByteSize();
        WritableBlobHolder blob(new Parcel::WritableBlob());
        ASSERT_OR_HANDLE_NO_MEMORY(blob.blob, return NO_MEMORY);
        data.writeInt32(size);
        data.writeBlob(size, false, blob.blob);
        v->SerializeToArray(blob.blob->data(), size);
        status_t status = remote()->transact(SET_PROPERTY, data, &reply);
        return status;
    }

    virtual status_t getProperty(vehicle_prop_value_t* value) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleNetwork::getInterfaceDescriptor());
        // only needs to send property itself.
        data.writeInt32(value->prop);
        status_t status = remote()->transact(GET_PROPERTY, data, &reply);
        if (status == NO_ERROR) {
            reply.readExceptionCode(); // for compatibility with java
            if (reply.readInt32() == 0) { // no result
                return BAD_VALUE;
            }
            ReadableBlobHolder blob(new Parcel::ReadableBlob());
            ASSERT_OR_HANDLE_NO_MEMORY(blob.blob, return NO_MEMORY);
            int32_t size = reply.readInt32();
            status = reply.readBlob(size, blob.blob);
            if (status != NO_ERROR) {
                ALOGE("getProperty, cannot read blob");
                return status;
            }
            std::unique_ptr<VehiclePropValue> v(new VehiclePropValue());
            ASSERT_OR_HANDLE_NO_MEMORY(v.get(), return NO_MEMORY);
            if (!v->ParseFromArray(blob.blob->data(), size)) {
                ALOGE("getProperty, cannot parse reply");
                return BAD_VALUE;
            }
            status = VehicleNetworkProtoUtil::fromVehiclePropValue(*v.get(), *value);
        }
        return status;
    }

    virtual status_t subscribe(const sp<IVehicleNetworkListener> &listener, int32_t property,
                float sampleRate) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleNetwork::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(listener));
        data.writeInt32(property);
        data.writeFloat(sampleRate);
        status_t status = remote()->transact(SUBSCRIBE, data, &reply);
        return status;
    }

    virtual void unsubscribe(const sp<IVehicleNetworkListener> &listener, int32_t property) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleNetwork::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(listener));
        data.writeInt32(property);
        status_t status = remote()->transact(UNSUBSCRIBE, data, &reply);
        if (status != NO_ERROR) {
            ALOGI("unsubscribing property %d failed %d", property, status);
        }
    }
};

IMPLEMENT_META_INTERFACE(VehicleNetwork, IVehicleNetwork::SERVICE_NAME);

// ----------------------------------------------------------------------

static bool isSystemUser() {
    uid_t uid =  IPCThreadState::self()->getCallingUid();
    switch (uid) {
        // This list will be expanded. Only those UIDs are allowed to access vehicle network
        // for now. There can be per property based UID check built-in as well.
        case AID_ROOT:
        case AID_SYSTEM:
        case AID_AUDIO: {
            return true;
        } break;
        default: {
            ALOGE("non-system user tried access, uid %d", uid);
        } break;
    }
    return false;
}

status_t BnVehicleNetwork::onTransact(uint32_t code, const Parcel& data, Parcel* reply,
        uint32_t flags) {
    if (!isSystemUser()) {
        return PERMISSION_DENIED;
    }
    status_t r;
    switch (code) {
        case LIST_PROPERTIES: {
            CHECK_INTERFACE(IVehicleNetwork, data, reply);
            int32_t property = data.readInt32();
            sp<VehiclePropertiesHolder> holder = listProperties(property);
            if (holder.get() == NULL) { // given property not found
                BinderUtil::fillObjectResultReply(reply, false /* isValid */);
                return NO_ERROR;
            }
            std::unique_ptr<VehiclePropConfigs> configs(new VehiclePropConfigs());
            ASSERT_OR_HANDLE_NO_MEMORY(configs.get(), return NO_MEMORY);
            VehicleNetworkProtoUtil::toVehiclePropConfigs(holder->getData(),
                    holder->getNumConfigs(), *configs.get());
            int size = configs->ByteSize();
            WritableBlobHolder blob(new Parcel::WritableBlob());
            ASSERT_OR_HANDLE_NO_MEMORY(blob.blob, return NO_MEMORY);
            BinderUtil::fillObjectResultReply(reply, true);
            reply->writeInt32(size);
            reply->writeBlob(size, false, blob.blob);
            configs->SerializeToArray(blob.blob->data(), size);
            return NO_ERROR;
        } break;
        case SET_PROPERTY: {
            CHECK_INTERFACE(IVehicleNetwork, data, reply);
            if (data.readInt32() == 0) { // java side allows passing null with this.
                return BAD_VALUE;
            }
            ScopedVehiclePropValue value;
            ReadableBlobHolder blob(new Parcel::ReadableBlob());
            ASSERT_OR_HANDLE_NO_MEMORY(blob.blob, return NO_MEMORY);
            int32_t size = data.readInt32();
            r = data.readBlob(size, blob.blob);
            if (r != NO_ERROR) {
                ALOGE("setProperty:service, cannot read blob");
                return r;
            }
            std::unique_ptr<VehiclePropValue> v(new VehiclePropValue());
            ASSERT_OR_HANDLE_NO_MEMORY(v.get(), return NO_MEMORY);
            if (!v->ParseFromArray(blob.blob->data(), size)) {
                ALOGE("setProperty:service, cannot parse data");
                return BAD_VALUE;
            }
            r = VehicleNetworkProtoUtil::fromVehiclePropValue(*v.get(), value.value);
            if (r != NO_ERROR) {
                ALOGE("setProperty:service, cannot convert data");
                return BAD_VALUE;
            }
            r = setProperty(value.value);
            BinderUtil::fillNoResultReply(reply);
            return r;
        } break;
        case GET_PROPERTY: {
            CHECK_INTERFACE(IVehicleNetwork, data, reply);
            ScopedVehiclePropValue value;
            value.value.prop = data.readInt32();
            r = getProperty(&(value.value));
            if (r == NO_ERROR) {
                BinderUtil::fillObjectResultReply(reply, true);
                std::unique_ptr<VehiclePropValue> v(new VehiclePropValue());
                ASSERT_OR_HANDLE_NO_MEMORY(v.get(), return NO_MEMORY);
                VehicleNetworkProtoUtil::toVehiclePropValue(value.value, *v.get());
                int size = v->ByteSize();
                WritableBlobHolder blob(new Parcel::WritableBlob());
                ASSERT_OR_HANDLE_NO_MEMORY(blob.blob, return NO_MEMORY);
                reply->writeInt32(size);
                reply->writeBlob(size, false, blob.blob);
                v->SerializeToArray(blob.blob->data(), size);
            }
            return r;
        } break;
        case SUBSCRIBE: {
            CHECK_INTERFACE(IVehicleNetwork, data, reply);
            sp<IVehicleNetworkListener> listener =
                    interface_cast<IVehicleNetworkListener>(data.readStrongBinder());
            int32_t property = data.readInt32();
            float sampleRate = data.readFloat();
            r = subscribe(listener, property, sampleRate);
            BinderUtil::fillNoResultReply(reply);
            return r;
        } break;
        case UNSUBSCRIBE: {
            CHECK_INTERFACE(IVehicleNetwork, data, reply);
            sp<IVehicleNetworkListener> listener =
                    interface_cast<IVehicleNetworkListener>(data.readStrongBinder());
            int32_t property = data.readInt32();
            unsubscribe(listener, property);
            BinderUtil::fillNoResultReply(reply);
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

}; // namespace android
