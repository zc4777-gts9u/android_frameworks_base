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

package com.android.server.biometrics.sensors.face.aidl;

import android.annotation.NonNull;
import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Slog;
import android.hardware.biometrics.face.ISession;
import android.hardware.biometrics.face.V1_0.IBiometricsFace;

import com.android.server.biometrics.sensors.face.hidl.HidlToAidlSessionAdapter;

import java.util.function.Supplier;

/**
 * A holder for an AIDL {@link ISession} with additional metadata about the current user
 * and the backend.
 */
public class AidlSession {

    private static final String TAG = "FaceAidlSession";
    private static final String SAMSUNG_SESSION_DESCRIPTOR =
            "vendor.samsung.hardware.biometrics.face.ISehSession";
    // Verified against CYG1 ISehSession V3: setRotation(int) is transaction 11
    // and returns an int status after the standard Binder exception header.
    private static final int SAMSUNG_SET_ROTATION = IBinder.FIRST_CALL_TRANSACTION + 10;

    private IBinder mSamsungSession;
    private boolean mSamsungExtensionChecked;
    private int mSamsungRotation = -1;

    private final int mHalInterfaceVersion;
    @NonNull
    private final ISession mSession;
    private final int mUserId;
    @NonNull private final AidlResponseHandler mAidlResponseHandler;

    public AidlSession(int halInterfaceVersion, @NonNull ISession session, int userId,
            AidlResponseHandler aidlResponseHandler) {
        mHalInterfaceVersion = halInterfaceVersion;
        mSession = session;
        mUserId = userId;
        mAidlResponseHandler = aidlResponseHandler;
    }

    public AidlSession(Context context, Supplier<IBiometricsFace> session, int userId,
            AidlResponseHandler aidlResponseHandler) {
        mSession = new HidlToAidlSessionAdapter(context, session, userId, aidlResponseHandler);
        mHalInterfaceVersion = 0;
        mUserId = userId;
        mAidlResponseHandler = aidlResponseHandler;
    }

    @NonNull public ISession getSession() {
        return mSession;
    }

    void setSamsungSession(IBinder vendorSession) {
        mSamsungSession = vendorSession;
        mSamsungExtensionChecked = true;
        mSamsungRotation = -1;
    }

    /** Synchronizes the optional Samsung camera rotation before a camera operation. */
    void updateVendorRotation(Context context, int displayRotation) {
        final int sensorOrientation = context.getResources().getInteger(
                com.android.internal.R.integer.config_faceAuthVendorSensorOrientation);
        if (mHalInterfaceVersion == 0 || sensorOrientation < 0
                || displayRotation < 0 || displayRotation > 3) {
            return;
        }
        // Front-facing sensor orientation relative to the current display.
        final int rotation = (sensorOrientation + displayRotation * 90) % 360;
        if (rotation == mSamsungRotation) {
            return;
        }
        try {
            if (!mSamsungExtensionChecked) {
                final IBinder extension = mSession.asBinder().getExtension();
                if (extension != null
                        && SAMSUNG_SESSION_DESCRIPTOR.equals(extension.getInterfaceDescriptor())) {
                    mSamsungSession = extension;
                }
                mSamsungExtensionChecked = true;
            }
            if (mSamsungSession == null) {
                return;
            }
            final Parcel request = Parcel.obtain(mSamsungSession);
            final Parcel reply = Parcel.obtain();
            try {
                request.writeInterfaceToken(SAMSUNG_SESSION_DESCRIPTOR);
                request.writeInt(rotation);
                if (!mSamsungSession.transact(SAMSUNG_SET_ROTATION, request, reply, 0)) {
                    Slog.w(TAG, "Samsung face rotation transaction is unsupported");
                    return;
                }
                reply.readException();
                final int result = reply.readInt();
                if (result == 0) {
                    mSamsungRotation = rotation;
                    Slog.d(TAG, "Samsung face rotation=" + rotation
                            + ", displayRotation=" + displayRotation);
                } else {
                    Slog.w(TAG, "Samsung face rotation failed: " + result);
                }
            } finally {
                reply.recycle();
                request.recycle();
            }
        } catch (RemoteException | RuntimeException e) {
            Slog.w(TAG, "Unable to update Samsung face rotation", e);
        }
    }

    /** CYG1 ISehSession V3: resume() is transaction 7 and returns an int status. */
    boolean resumeVendorEnrollment() {
        if (mHalInterfaceVersion == 0 || mSamsungSession == null) {
            return false;
        }
        final Parcel request = Parcel.obtain(mSamsungSession);
        final Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(SAMSUNG_SESSION_DESCRIPTOR);
            if (!mSamsungSession.transact(IBinder.FIRST_CALL_TRANSACTION + 6,
                    request, reply, 0)) {
                return false;
            }
            reply.readException();
            final int result = reply.readInt();
            if (result != 0) {
                Slog.w(TAG, "Samsung face enrollment resume failed: " + result);
                return false;
            }
            Slog.d(TAG, "Resumed Samsung face enrollment after glasses guidance");
            return true;
        } catch (RemoteException | RuntimeException e) {
            Slog.w(TAG, "Unable to resume Samsung face enrollment", e);
            return false;
        } finally {
            reply.recycle();
            request.recycle();
        }
    }

    public int getUserId() {
        return mUserId;
    }

    /** The HAL callback, which should only be used in tests {@See BiometricTestSessionImpl}. */
    public AidlResponseHandler getHalSessionCallback() {
        return mAidlResponseHandler;
    }

    /**
     * If this backend implements the *WithContext methods for enroll, authenticate, and
     * detectInteraction. These variants should always be called if they are available.
     */
    public boolean hasContextMethods() {
        return mHalInterfaceVersion >= 2;
    }

    /**
     * If this backend implements enroll methods with an {@link android.view.Surface}.
     */
    public boolean supportsFaceEnrollOptions() {
        return mHalInterfaceVersion >= 4;
    }
}
