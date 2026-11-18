/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.biometrics.sensors.face.aidl;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.biometrics.face.BaseFrame;
import android.hardware.biometrics.face.EnrollmentFrame;
import android.hardware.face.FaceEnrollStages;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@Presubmit
@SmallTest
public class AidlConversionUtilsTest {
    @Test
    public void samsungPoseStages_areTranslatedOnlyWhenEnabled() {
        for (int raw : new int[]{100, 101, 110, 111}) {
            EnrollmentFrame frame = frame(raw);
            int expected = raw == 100 || raw == 110
                    ? FaceEnrollStages.HOLD_STILL_IN_CENTER
                    : FaceEnrollStages.ENROLLING_MOVEMENT_1;
            assertThat(AidlConversionUtils.toFrameworkEnrollmentFrame(frame, true).getStage())
                    .isEqualTo(expected);
            assertThat(AidlConversionUtils.toFrameworkEnrollmentFrame(frame).getStage())
                    .isEqualTo(FaceEnrollStages.UNKNOWN);
        }
    }

    @Test
    public void standardStages_areUnchangedWhenSamsungMappingEnabled() {
        for (int stage = 0; stage <= 6; stage++) {
            EnrollmentFrame frame = frame(stage);
            assertThat(AidlConversionUtils.toFrameworkEnrollmentFrame(frame, true).getStage())
                    .isEqualTo(AidlConversionUtils.toFrameworkEnrollmentFrame(frame).getStage());
        }
    }

    private static EnrollmentFrame frame(int stage) {
        EnrollmentFrame frame = new EnrollmentFrame();
        frame.stage = (byte) stage;
        frame.data = new BaseFrame();
        return frame;
    }
}
