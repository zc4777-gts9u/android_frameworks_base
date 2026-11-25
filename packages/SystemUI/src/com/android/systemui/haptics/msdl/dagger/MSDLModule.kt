/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.haptics.msdl.dagger

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.VibrationEffect
import android.os.Vibrator
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.Module
import dagger.Provides

@Module
object MSDLModule {
    @SuppressLint("NonInjectedService")
    @Provides
    @SysUISingleton
    fun provideMSDLPlayer(vibrator: Vibrator?, @Main resources: Resources): MSDLPlayer {
        // Slow DC motors cannot reproduce the stock fallback's short pulses.
        // Use device-calibrated HAL effects only when primitives are unsupported.
        val fallbackEffects = buildMap {
            val unlockEffectId = resources.getInteger(R.integer.config_msdlUnlockHapticEffect)
            if (unlockEffectId >= 0) {
                put(MSDLToken.UNLOCK, VibrationEffect.createPredefined(unlockEffectId))
            }

            val switchEffectId = resources.getInteger(R.integer.config_msdlSwitchHapticEffect)
            if (switchEffectId >= 0) {
                val switchEffect = VibrationEffect.createPredefined(switchEffectId)
                put(MSDLToken.SWITCH_ON, switchEffect)
                put(MSDLToken.SWITCH_OFF, switchEffect)
            }
        }
        return MSDLPlayer.createPlayer(vibrator, fallbackEffects = fallbackEffects)
    }
}
