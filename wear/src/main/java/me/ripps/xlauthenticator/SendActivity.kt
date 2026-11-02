package me.ripps.xlauthenticator

import android.app.NotificationManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asks the phone to send the code, shows the outcome with a haptic, then closes itself.
 * Opened by the app button, the tile, the complication and the login prompt.
 */
class SendActivity : ComponentActivity() {
    private val result = mutableStateOf<WatchLink.Outcome?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(WatchListenerService.PROMPT_NOTIFICATION_ID)

        setContent {
            MaterialTheme {
                val outcome by result
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val r = outcome) {
                        null -> {
                            CircularProgressIndicator()
                            Text("Sending…")
                        }
                        else -> {
                            Text(if (r.ok) "✓" else "✗", fontSize = 40.sp)
                            Text(r.message, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            val r = WatchLink.requestSend(applicationContext)
            result.value = r
            buzz(r.ok)
            delay(if (r.ok) 1500 else 3500)
            finish()
        }
    }

    private fun buzz(success: Boolean) {
        val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        val effect = if (success) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1)
        }
        vibrator.vibrate(effect)
    }
}
