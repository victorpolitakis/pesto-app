package com.victorpolitakis.pesto.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingStore by preferencesDataStore("pairing")

/**
 * The token of this device (Δ24), from the pairing. The connection of a
 * dictation opens synchronously, so the store keeps the token in memory.
 */
interface DeviceTokenStore {
    /** The token in memory. Null: this device is not paired. */
    fun current(): String?

    /** After the first read of the store. */
    suspend fun isPaired(): Boolean

    val paired: Flow<Boolean>

    suspend fun save(token: String)

    /** Unpairing, or a token the backend refused (revoked). */
    suspend fun clear()
}

/**
 * The token encrypted (AES-GCM) with a key of the Android Keystore, which never
 * leaves the phone, in the DataStore file `pairing`. Uninstalling loses it, and
 * the phone pairs again (expected). The token is never logged.
 */
@Singleton
class KeystoreTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @AppScope scope: CoroutineScope,
    private val log: PestoLog,
) : DeviceTokenStore {
    private val token = MutableStateFlow<String?>(null)
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                token.value = context.pairingStore.data.first()[KEY]?.let(::decrypt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A key the system lost, or a file it cannot read: the phone pairs again.
                log.event("device token unreadable: ${e.javaClass.simpleName}")
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    override fun current(): String? {
        // Only the first dictation after the process started can come before the read.
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return token.value
    }

    override suspend fun isPaired(): Boolean {
        loaded.await()
        return token.value != null
    }

    override val paired: Flow<Boolean> = flow {
        loaded.await()
        emitAll(token.map { it != null })
    }.distinctUntilChanged()

    override suspend fun save(token: String) {
        loaded.await()
        val sealed = withContext(Dispatchers.IO) { encrypt(token) }
        context.pairingStore.edit { it[KEY] = sealed }
        this.token.value = token
    }

    override suspend fun clear() {
        loaded.await()
        context.pairingStore.edit { it.remove(KEY) }
        token.value = null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Base64 of: the length of the IV, the IV, the ciphertext with its tag. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(byteArrayOf(iv.size.toByte()) + iv + sealed)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.getDecoder().decode(stored)
        val ivSize = bytes[0].toInt()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 1, ivSize))
        }
        return String(cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize), Charsets.UTF_8)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "pesto_device_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val KEY = stringPreferencesKey("device_token")
    }
}
