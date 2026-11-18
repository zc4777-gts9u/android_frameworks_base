/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.biometrics.sensors.face.aidl;

import android.content.Context;
import android.hardware.biometrics.face.IFace;
import android.hardware.biometrics.face.ISession;
import android.hardware.biometrics.face.ISessionCallback;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Slog;

/** Samsung's vendor session owns the standard session through its Binder extension. */
final class SamsungFaceSession {
    private static final String TAG = "SamsungFaceSession";
    private static final String FACE_DESCRIPTOR =
            "vendor.samsung.hardware.biometrics.face.ISehFace";
    private static final String SESSION_DESCRIPTOR =
            "vendor.samsung.hardware.biometrics.face.ISehSession";
    // CYG1 ISehFace V3: createSession(sensorId, userId, ISessionCallback).
    private static final int CREATE_SESSION = IBinder.FIRST_CALL_TRANSACTION + 1;

    final ISession session;
    final IBinder vendorSession;

    private SamsungFaceSession(ISession session, IBinder vendorSession) {
        this.session = session;
        this.vendorSession = vendorSession;
    }

    static SamsungFaceSession create(Context context, IFace hal, int sensorId, int userId,
            ISessionCallback callback) throws RemoteException {
        if (context.getResources().getInteger(
                com.android.internal.R.integer.config_faceAuthVendorSensorOrientation) < 0) {
            return null;
        }
        final IBinder extension = hal.asBinder().getExtension();
        if (extension == null || !FACE_DESCRIPTOR.equals(extension.getInterfaceDescriptor())) {
            Slog.w(TAG, "Samsung face service extension is unavailable");
            return null;
        }
        Binder.allowBlocking(extension);
        final Parcel request = Parcel.obtain(extension);
        final Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(FACE_DESCRIPTOR);
            request.writeInt(sensorId);
            request.writeInt(userId);
            request.writeStrongInterface(callback);
            if (!extension.transact(CREATE_SESSION, request, reply, 0)) {
                throw new RemoteException("Samsung face createSession is unsupported");
            }
            reply.readException();
            final IBinder vendorSession = reply.readStrongBinder();
            if (vendorSession == null
                    || !SESSION_DESCRIPTOR.equals(vendorSession.getInterfaceDescriptor())) {
                throw new RemoteException("Samsung face returned an invalid vendor session");
            }
            Binder.allowBlocking(vendorSession);
            final IBinder standardSession = vendorSession.getExtension();
            if (standardSession == null
                    || !ISession.DESCRIPTOR.equals(standardSession.getInterfaceDescriptor())) {
                throw new RemoteException("Samsung face standard session extension is missing");
            }
            Binder.allowBlocking(standardSession);
            Slog.d(TAG, "Created Samsung face session and standard session extension");
            return new SamsungFaceSession(ISession.Stub.asInterface(standardSession), vendorSession);
        } finally {
            reply.recycle();
            request.recycle();
        }
    }
}
