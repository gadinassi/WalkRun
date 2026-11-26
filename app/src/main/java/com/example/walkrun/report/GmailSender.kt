package com.example.walkrun.report

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sends the report from the user's own Gmail to the same address, with the Gmail API.
 * Only the "gmail.send" scope: the app can send, it cannot read the mailbox.
 */
object GmailSender {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.send"
    private const val SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
    private const val TAG = "GmailSender"

    sealed interface Auth {
        data class Token(val value: String) : Auth
        /** The user must approve in a Google window (first time, or after the approval was removed). */
        data class NeedsApproval(val intent: PendingIntent) : Auth
    }

    private fun request(email: String) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .setAccount(Account(email, "com.google"))
        .build()

    /** Without UI. When approval was given before, Google returns a token directly. */
    suspend fun authorize(context: Context, email: String): Auth {
        val result = Identity.getAuthorizationClient(context).authorize(request(email)).await()
        return toAuth(result)
    }

    /** Reads the result of the approval window. */
    fun fromApprovalResult(context: Context, data: Intent?): Auth? = runCatching {
        toAuth(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    }.onFailure { Log.w(TAG, "Approval result", it) }.getOrNull()

    private fun toAuth(r: AuthorizationResult): Auth {
        val pending = r.pendingIntent
        return if (r.hasResolution() && pending != null) Auth.NeedsApproval(pending)
        else Auth.Token(r.accessToken ?: error("No access token"))
    }

    /** Throws on failure, with the HTTP status and the Google error text. */
    suspend fun send(token: String, to: String, email: Reports.Email) = withContext(Dispatchers.IO) {
        val raw = Base64.encodeToString(mime(to, email), Base64.URL_SAFE or Base64.NO_WRAP)
        val conn = (URL(SEND_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            conn.outputStream.use { it.write(JSONObject().put("raw", raw).toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GmailException(code, err.take(500))
            }
        } finally {
            conn.disconnect()
        }
    }

    class GmailException(val code: Int, body: String) : Exception("Gmail API $code: $body")

    /** RFC 2822 message: UTF-8 text part and PNG attachments, all base64. */
    private fun mime(to: String, email: Reports.Email): ByteArray {
        val boundary = "walkrun-" + UUID.randomUUID()
        val sb = StringBuilder()
        fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.DEFAULT) // 76-char lines
        sb.append("To: ").append(to).append("\r\n")
        sb.append("Subject: =?UTF-8?B?").append(Base64.encodeToString(email.subject.toByteArray(), Base64.NO_WRAP)).append("?=\r\n")
        sb.append("MIME-Version: 1.0\r\n")
        sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n")
        sb.append("Content-Transfer-Encoding: base64\r\n\r\n")
        sb.append(b64(email.body.toByteArray())).append("\r\n")
        for (f: File in email.images) {
            sb.append("--").append(boundary).append("\r\n")
            sb.append("Content-Type: image/png; name=\"").append(f.name).append("\"\r\n")
            sb.append("Content-Disposition: attachment; filename=\"").append(f.name).append("\"\r\n")
            sb.append("Content-Transfer-Encoding: base64\r\n\r\n")
            sb.append(b64(f.readBytes())).append("\r\n")
        }
        sb.append("--").append(boundary).append("--\r\n")
        return sb.toString().toByteArray()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}
