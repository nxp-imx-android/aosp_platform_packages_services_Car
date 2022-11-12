/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.oem;

import android.annotation.CallSuper;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.Service;
import android.car.CarVersion;
import android.car.annotation.ApiRequirements;
import android.car.builtin.util.Slogf;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;

// TODO(b/241294844): Expose Slogf as system API and use it here.
/**
 * This code will be running as part of the OEM Service. This provides basic implementation for OEM
 * Service. OEMs should extend this class and override relevant methods.
 *
 * @hide
 */
@SystemApi
public abstract class OemCarService extends Service {

    private static final String TAG = OemCarService.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String PERMISSION_BIND_OEM_CAR_SERVICE =
            "android.car.permission.BIND_OEM_CAR_SERVICE";

    // OEM Service components
    // Note: Change the size as more components are added.
    @GuardedBy("mLock")
    private final ArrayMap<Class<?>, OemCarServiceComponent> mOemCarServiceComponents =
            new ArrayMap<Class<?>, OemCarServiceComponent>(1);

    private final Object mLock = new Object();

    private final IOemCarService mInterface = new IOemCarService.Stub() {
        // Component services
        @Override
        public IOemCarAudioFocusService getOemAudioFocusService() {
            assertPermission();
            synchronized (mLock) {
                return (IOemCarAudioFocusService) mOemCarServiceComponents
                        .getOrDefault(IOemCarAudioFocusService.class, null);
            }
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            assertPermission();
            OemCarService.this.dump(fd, writer, args);
        }

        @Override
        public void onCarServiceReady(IOemCarServiceCallback callback) throws RemoteException {
            assertPermission();
            OemCarService.this.onCarServiceReady();
            callback.sendOemCarServiceReady();
        }

        @Override
        public boolean isOemServiceReady() {
            assertPermission();
            return OemCarService.this.isOemServiceReady();
        }

        @Override
        public CarVersion getSupportedCarVersion() {
            assertPermission();
            return OemCarService.this.getSupportedCarVersion();
        }

        @Override
        public String getAllStackTraces() {
            assertPermission();
            Map<Thread, StackTraceElement[]> tracesMap = Thread.getAllStackTraces();
            StringBuilder sb = new StringBuilder();
            sb.append("OemService stack trace:\n");
            int i = 0;
            for (Map.Entry<Thread, StackTraceElement[]> entry : tracesMap.entrySet()) {
                sb.append("Thread: ").append(i++).append('\n');
                StackTraceElement[] stack = entry.getValue();
                for (int j = 0; j < stack.length; j++) {
                    sb.append(stack[j].toString()).append('\n');
                }
            }

            return sb.toString();
        }

        private void assertPermission() {
            if (checkCallingPermission(
                    PERMISSION_BIND_OEM_CAR_SERVICE) != PackageManager.PERMISSION_GRANTED) {
                String errorMsg = "Caller with uid:" + Binder.getCallingUid()
                        + " doesn't have permission " + PERMISSION_BIND_OEM_CAR_SERVICE;
                Slogf.e(TAG, errorMsg);
                throw new SecurityException(errorMsg);
            }
        }
    };

    /**
     * {@inheritDoc}
     * <p>
     * OEM should override this method and do the initialization. OEM should also call super as this
     * method would call {@link OemCarServiceComponent#init()} for each component implemented by
     * OEM.
     * <p>
     * Car Service will not be available at the time of this initialization. If the OEM needs
     * anything from CarService, they should wait for the CarServiceReady() call. It is expected
     * that most of the initialization will finish in this call.
     */
    @Override
    @CallSuper
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void onCreate() {
        if (DBG) {
            Slogf.d(TAG, "OnCreate");
        }
        super.onCreate();

        // Create all components
        OemCarAudioFocusService oemCarAudioFocusService = getOemAudioFocusService();
        synchronized (mLock) {
            if (oemCarAudioFocusService != null) {
                mOemCarServiceComponents.put(IOemCarAudioFocusService.class,
                        new OemCarAudioFocusServiceImpl(oemCarAudioFocusService));
            }

            // Initialize them
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                mOemCarServiceComponents.valueAt(i).init();
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * OEM should override this method and do all the resources deallocation. OEM should also call
     * super as this method would call {@link OemCarServiceComponent#release()} for each component
     * implemented by OEM.
     */
    @Override
    @CallSuper
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void onDestroy() {
        if (DBG) {
            Slogf.d(TAG, "OnDestroy");
        }
        super.onDestroy();

        // Destroy all components and release the resources
        synchronized (mLock) {
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                mOemCarServiceComponents.valueAt(i).release();
            }
        }
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public final int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (DBG) {
            Slogf.d(TAG, "onStartCommand");
        }
        return START_STICKY;
    }

    @NonNull
    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public final IBinder onBind(@Nullable Intent intent) {
        if (DBG) {
            Slogf.d(TAG, "onBind");
        }
        return mInterface.asBinder();
    }

    /**
     * Gets Audio Focus Service implemented by OEM Service.
     *
     * @return audio focus service if implemented by OEM service, else return {@code null}.
     */
    @Nullable
    @SuppressWarnings("[OnNameExpected]")
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public OemCarAudioFocusService getOemAudioFocusService() {
        if (DBG) {
            Slogf.d(TAG, "getOemUserService");
        }
        return null;
    }

    /**
     * Dumps OEM Car Service.
     * <p>
     * OEM should override this method to dump. OEM should also call super as this method would call
     * {@link OemCarServiceComponent#dump(FileDescriptor, PrintWriter, String[])} for each component
     * implemented by OEM.
     */
    @CallSuper
    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void dump(@Nullable FileDescriptor fd, @Nullable PrintWriter writer,
            @Nullable String[] args) {
        writer.println("**** Dump OemCarService ****");
        synchronized (mLock) {
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                mOemCarServiceComponents.valueAt(i).dump(writer, args);
            }
        }
    }

    /**
     * Checks if OEM service is ready. OEM service must be ready within certain time.
     */
    @SuppressWarnings("[OnNameExpected]")
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public abstract boolean isOemServiceReady();

    /**
     * Checks the supported CarVersion by the OEM service.
     */
    @SuppressWarnings("[OnNameExpected]")
    @NonNull
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public abstract CarVersion getSupportedCarVersion();

    /**
     * Informs OEM service that CarService is now ready for communication.
     * <p>
     * OEM should override this method and do the necessary initialization depending on CarService.
     * OEM should also call super as this method would call
     * {@link OemCarServiceComponent#onCarServiceReady()} for each component implemented by OEM.
     */
    @CallSuper
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void onCarServiceReady() {
        if (DBG) {
            Slogf.d(TAG, "onCarServiceReady");
        }

        synchronized (mLock) {
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                mOemCarServiceComponents.valueAt(i).onCarServiceReady();
            }
        }
    }
}
