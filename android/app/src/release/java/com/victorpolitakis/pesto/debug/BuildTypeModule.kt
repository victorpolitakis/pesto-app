package com.victorpolitakis.pesto.debug

import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.audio.AudioRecordCapture
import com.victorpolitakis.pesto.overlay.OverlayMethod
import com.victorpolitakis.pesto.overlay.OverlayMethodProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release build: the real microphone and method 1 (Δ16). TODO(phase 3): the experimental method 2 from Settings. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BuildTypeModule {
    @Binds abstract fun audio(impl: AudioRecordCapture): AudioCapture

    companion object {
        @Provides fun overlayMethod(): OverlayMethodProvider = OverlayMethodProvider { OverlayMethod.ACCESSIBILITY }
    }
}
