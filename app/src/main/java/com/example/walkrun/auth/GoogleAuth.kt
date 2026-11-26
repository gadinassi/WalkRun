package com.example.walkrun.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.walkrun.BuildConfig
import com.example.walkrun.data.Account
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.cancellation.CancellationException

/** The user closed the Google sign-in dialog. This is not an error. */
class SignInCancelledException : Exception()

/** Sign-in failed. [message] is in Hebrew and can be shown to the user. */
class SignInException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Google sign-in with Credential Manager.
 * NOTE: The ID token is not verified on a server. The app has no backend.
 * Sign-in identifies the user locally only.
 */
object GoogleAuth {
    private const val TAG = "GoogleAuth"

    /**
     * [activityContext] must be an Activity context.
     * A failure is [SignInCancelledException] or [SignInException].
     */
    suspend fun signIn(activityContext: Context): Result<Account> {
        return try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(Account(name = google.displayName ?: google.id, email = google.id))
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                Result.failure(SignInException("ההתחברות עם Google נכשלה. אפשר להמשיך בלי חשבון."))
            }
        } catch (e: CancellationException) {
            throw e // The screen was closed. Do not hide coroutine cancellation.
        } catch (e: GetCredentialCancellationException) {
            Result.failure(SignInCancelledException())
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No credential", e)
            Result.failure(
                SignInException("לא נמצא חשבון Google מתאים במכשיר. אפשר להמשיך בלי חשבון.", e)
            )
        } catch (e: GetCredentialException) {
            // Often: the OAuth client ID is not set, or the SHA-1 does not match. See README.
            Log.w(TAG, "Sign-in failed", e)
            Result.failure(SignInException("ההתחברות עם Google נכשלה. אפשר להמשיך בלי חשבון.", e))
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed", e)
            Result.failure(SignInException("ההתחברות עם Google נכשלה. אפשר להמשיך בלי חשבון.", e))
        }
    }

    suspend fun signOut(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
