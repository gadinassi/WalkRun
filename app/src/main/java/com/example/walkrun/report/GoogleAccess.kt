package com.example.walkrun.report

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Access tokens for one Google API scope (Gmail send, Drive app folder), with the Play services authorization. */
object GoogleAccess {
    sealed interface Auth {
        data class Token(val value: String) : Auth
        /** The user must approve in a Google window (first time, or after the approval was removed). */
        data class NeedsApproval(val intent: PendingIntent) : Auth
    }

    private fun request(email: String, scope: String) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(scope)))
        .setAccount(Account(email, "com.google"))
        .build()

    /** Without UI. When approval was given before, Google returns a token directly. */
    suspend fun authorize(context: Context, email: String, scope: String): Auth =
        toAuth(Identity.getAuthorizationClient(context).authorize(request(email, scope)).await())

    /** Reads the result of the approval window. */
    fun fromApprovalResult(context: Context, data: Intent?): Auth? = runCatching {
        toAuth(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    }.onFailure { Log.w("GoogleAccess", "Approval result", it) }.getOrNull()

    private fun toAuth(r: AuthorizationResult): Auth {
        val pending = r.pendingIntent
        return if (r.hasResolution() && pending != null) Auth.NeedsApproval(pending)
        else Auth.Token(r.accessToken ?: error("No access token"))
    }

    class HttpException(val code: Int, body: String) : Exception("HTTP $code: $body")

    /** Throws [HttpException] for a status that is not 2xx, with the start of the error body. */
    fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw HttpException(code, err.take(500))
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}
