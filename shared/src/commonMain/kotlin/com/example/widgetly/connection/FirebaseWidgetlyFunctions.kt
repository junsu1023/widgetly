package com.example.widgetly.connection

import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.serialization.Serializable

/**
 * Response shapes of the deployed callables. These exist because
 * `HttpsCallableResult.data<T>()` decodes through kotlinx.serialization: the reified
 * `T` must be a concrete `@Serializable` type so the serialization compiler plugin can
 * resolve a serializer for it. `data<Map<String, Any?>>()` compiles but throws
 * `SerializationException` at runtime (no serializer for `Any`), which is why every
 * one of these calls used to fail end-to-end.
 *
 * Field names match the backend's actual returns - `functions/src/createPairingCode.ts`
 * (`{ code }`), `redeemPairingCode.ts` (`{ connectionId }`), `sendSignal.ts`
 * (`{ rateLimited }`).
 */
@Serializable
internal data class CreatePairingCodeResponse(val code: String)

@Serializable
internal data class RedeemPairingCodeResponse(val connectionId: String)

@Serializable
internal data class SendSignalResponse(val rateLimited: Boolean)

/**
 * Calls the real deployed backend callables. Request/response field names below
 * must match `functions/src/registerFcmToken.ts`, `createPairingCode.ts`,
 * `redeemPairingCode.ts`, `sendSignal.ts` exactly - this is the actual wire contract,
 * not something re-derived independently on the client.
 */
class FirebaseWidgetlyFunctions(private val functions: FirebaseFunctions) : WidgetlyFunctions {

    override suspend fun registerFcmToken(token: String, platform: String) {
        functions.httpsCallable("registerFcmToken")
            .invoke(mapOf("token" to token, "platform" to platform))
    }

    override suspend fun createPairingCode(type: ConnectionType): String {
        val result = functions.httpsCallable("createPairingCode")
            .invoke(mapOf("type" to type.wireValue))
        return result.data<CreatePairingCodeResponse>().code
    }

    override suspend fun redeemPairingCode(code: String): String {
        val result = functions.httpsCallable("redeemPairingCode")
            .invoke(mapOf("code" to code))
        return result.data<RedeemPairingCodeResponse>().connectionId
    }

    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult {
        val result = functions.httpsCallable("sendSignal")
            .invoke(mapOf("connectionId" to connectionId, "preset" to preset, "signalId" to signalId))
        return SendSignalResult(result.data<SendSignalResponse>().rateLimited)
    }
}

private val ConnectionType.wireValue: String
    get() = when (this) {
        ConnectionType.COUPLE -> "couple"
        ConnectionType.FRIEND -> "friend"
    }
