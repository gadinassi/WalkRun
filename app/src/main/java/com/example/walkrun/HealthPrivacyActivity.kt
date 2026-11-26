package com.example.walkrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.walkrun.ui.WalkRunTheme

/**
 * Required by Health Connect: explains what the app does with health data.
 * Opened from the Health Connect permission screen.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalkRunTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.safeDrawingPadding().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("WalkRun ו-Health Connect", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "WalkRun כותבת ל-Health Connect רק את האימונים שהיא מקליטה: " +
                                    "סוג האימון (הליכה או ריצה), זמן, מרחק, צעדים וקלוריות. " +
                                    "האפליקציה לא קוראת נתוני בריאות אחרים ולא שולחת אותם לשום מקום."
                            )
                            Text("אפשר לבטל את ההרשאה בכל רגע בהגדרות Health Connect.")
                            Button(onClick = { finish() }) { Text("סגירה") }
                        }
                    }
                }
            }
        }
    }
}
