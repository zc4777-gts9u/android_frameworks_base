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

package com.android.server.biometrics.sensors.face.aidl;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.biometrics.face.IFace;
import android.hardware.biometrics.face.ISession;
import android.hardware.biometrics.face.ISessionCallback;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.biometrics.log.BiometricContext;
import com.android.server.biometrics.log.BiometricLogger;
import com.android.server.biometrics.sensors.ClientMonitorCallback;
import com.android.server.biometrics.sensors.StartUserClient;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FaceStartUserClient extends StartUserClient<IFace, ISession> {
    private static final String TAG = "FaceStartUserClient";

    @NonNull private final ISessionCallback mSessionCallback;
    @Nullable private final Consumer<IBinder> mVendorSessionConsumer;

    public FaceStartUserClient(@NonNull Context context,
            @NonNull Supplier<IFace> lazyDaemon,
            @Nullable IBinder token, int userId, int sensorId,
            @NonNull BiometricLogger logger, @NonNull BiometricContext biometricContext,
            @NonNull ISessionCallback sessionCallback,
            @NonNull UserStartedCallback<ISession> callback) {
        this(context, lazyDaemon, token, userId, sensorId, logger, biometricContext,
                sessionCallback, callback, null);
    }

    public FaceStartUserClient(@NonNull Context context,
            @NonNull Supplier<IFace> lazyDaemon,
            @Nullable IBinder token, int userId, int sensorId,
            @NonNull BiometricLogger logger, @NonNull BiometricContext biometricContext,
            @NonNull ISessionCallback sessionCallback,
            @NonNull UserStartedCallback<ISession> callback,
            @Nullable Consumer<IBinder> vendorSessionConsumer) {
        super(context, lazyDaemon, token, userId, sensorId, logger, biometricContext, callback);
        mSessionCallback = sessionCallback;
        mVendorSessionConsumer = vendorSessionConsumer;
    }

    @Override
    public void start(@NonNull ClientMonitorCallback callback) {
        super.start(callback);
        startHalOperation();
    }

    @Override
    protected void startHalOperation() {
        try {
            final IFace hal = getFreshDaemon();
            final int version = hal.getInterfaceVersion();
            final SamsungFaceSession samsung = mVendorSessionConsumer == null ? null
                    : SamsungFaceSession.create(getContext(), hal, getSensorId(),
                            getTargetUserId(), mSessionCallback);
            final ISession newSession = samsung == null
                    ? hal.createSession(getSensorId(), getTargetUserId(), mSessionCallback)
                    : samsung.session;
            Binder.allowBlocking(newSession.asBinder());
            mUserStartedCallback.onUserStarted(getTargetUserId(), newSession, version);
            if (mVendorSessionConsumer != null) {
                mVendorSessionConsumer.accept(samsung == null ? null : samsung.vendorSession);
            }
            getCallback().onClientFinished(this, true /* success */);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Unable to create face session", e);
            getCallback().onClientFinished(this, false /* success */);
        }
    }

    @Override
    public void unableToStart() {
    }
}
