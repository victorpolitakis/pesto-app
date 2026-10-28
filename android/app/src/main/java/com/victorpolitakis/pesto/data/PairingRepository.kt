package com.victorpolitakis.pesto.data

import com.victorpolitakis.pesto.backend.BackendApi
import com.victorpolitakis.pesto.backend.PairResult
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The name the backend lists for this phone (`pesto-server devices`), e.g. «POCO X7 Pro». */
fun interface DeviceName {
    fun get(): String
}

/**
 * The connection with the backend (Δ24): the one-time code of
 * `pesto-server pair` becomes the token of this phone. C3 asks for it last
 * (Requirement.PAIRING), and Settings show it and let the phone unpair.
 */
@Singleton
class PairingRepository @Inject constructor(
    private val api: BackendApi,
    private val tokens: DeviceTokenStore,
    private val deviceName: DeviceName,
    private val log: PestoLog,
) {
    val paired: Flow<Boolean> get() = tokens.paired

    suspend fun isPaired(): Boolean = tokens.isPaired()

    /** [code]: the 8 digits, spaces allowed. The token goes only to the store, never to the log. */
    suspend fun pair(code: String): PairResult {
        val result = api.pair(code.filter { it.isDigit() }, deviceName.get())
        if (result is PairResult.Paired) tokens.save(result.token)
        log.event("pairing result=${if (result is PairResult.Failed) "Failed ${result.reason}" else result}")
        return result
    }

    /** Forgets the token on this phone. The backend keeps the device until `pesto-server revoke`. */
    suspend fun unpair() = tokens.clear()

    suspend fun backendAnswers(): Boolean = api.health()
}
