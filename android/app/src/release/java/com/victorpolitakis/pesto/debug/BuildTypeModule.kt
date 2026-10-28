package com.victorpolitakis.pesto.debug

import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.audio.AudioRecordCapture
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release build: the real microphone. The overlay method comes from Settings, as in debug (Δ12). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BuildTypeModule {
    @Binds abstract fun audio(impl: AudioRecordCapture): AudioCapture
}
