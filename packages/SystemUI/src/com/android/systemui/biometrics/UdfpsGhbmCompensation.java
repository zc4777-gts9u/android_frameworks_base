/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.biometrics;

/** Maps the requested brightness to a dim color outside a global-HBM fingerprint target. */
public final class UdfpsGhbmCompensation {
    private final float[] mNits;
    private final int mBacklightMaximum;
    private final int mTargetBacklight;
    private final boolean mValid;

    public UdfpsGhbmCompensation(String[] nits, int backlightMaximum, int targetBacklight) {
        mNits = new float[nits.length];
        mBacklightMaximum = backlightMaximum;
        mTargetBacklight = targetBacklight;
        boolean valid = backlightMaximum > 0 && targetBacklight > 0
                && targetBacklight <= backlightMaximum && nits.length == backlightMaximum + 1;
        for (int i = 0; i < nits.length; i++) {
            try {
                mNits[i] = Float.parseFloat(nits[i]);
                valid &= Float.isFinite(mNits[i]) && mNits[i] > 0
                        && (i == 0 || mNits[i] >= mNits[i - 1]);
            } catch (NumberFormatException e) {
                valid = false;
            }
        }
        mValid = valid;
    }

    /** Null means illumination must not be posted with an uncalibrated compensation buffer. */
    public Integer getDimColor(float brightness) {
        if (!mValid || !Float.isFinite(brightness) || brightness < 0) return null;
        float backlight = Math.min(brightness, 1f) * mBacklightMaximum;
        int lower = (int) backlight;
        int upper = Math.min(lower + 1, mBacklightMaximum);
        float currentNits = mNits[lower]
                + (mNits[upper] - mNits[lower]) * (backlight - lower);
        float ratio = Math.min(currentNits / mNits[mTargetBacklight], 1f);
        int alpha = Math.round(255f * (1f - (float) Math.pow(ratio, 1d / 2.2d)));
        return alpha << 24;
    }
}
