package com.example.walkrun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.walkrun.WalkRunApp
import com.example.walkrun.auth.GoogleAuth
import com.example.walkrun.auth.SignInCancelledException
import com.example.walkrun.data.Account
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(app: WalkRunApp, onSignedIn: () -> Unit) {
    val context = LocalContext.current // Activity context, required by Credential Manager
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("הליכה וריצה", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "הנתונים נשמרים במכשיר בלבד. אפשר להתחבר עם Google או להמשיך בלי חשבון.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(enabled = !busy, onClick = {
            busy = true
            error = null
            scope.launch {
                val result = GoogleAuth.signIn(context)
                val account = result.getOrNull()
                if (account != null) {
                    app.settings.saveAccount(account)
                    onSignedIn()
                } else {
                    val e = result.exceptionOrNull()
                    // Closing the Google dialog is not an error.
                    error = if (e is SignInCancelledException) null
                    else e?.message ?: "ההתחברות נכשלה."
                }
                busy = false
            }
        }) {
            Text(if (busy) "מתחבר..." else "התחברות עם Google")
        }
        Spacer(Modifier.height(8.dp))
        // The data is stored on the device only, so an account is not required.
        TextButton(enabled = !busy, onClick = {
            scope.launch {
                app.settings.saveAccount(Account.GUEST)
                onSignedIn()
            }
        }) {
            Text("המשך בלי חשבון")
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}
