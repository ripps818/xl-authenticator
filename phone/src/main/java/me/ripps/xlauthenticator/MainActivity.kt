package me.ripps.xlauthenticator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val resumeCount = mutableLongStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecretStore(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    App(store, resumeCount.longValue)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh anything that may have changed in system settings (exact-alarm permission).
        resumeCount.longValue++
    }

    @Composable
    private fun App(store: SecretStore, resumes: Long) {
        val scope = rememberCoroutineScope()
        var hasSecret by remember { mutableStateOf(store.hasSecret) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
        var status by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis() / 1000
                delay(1000)
            }
        }

        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("XL Authenticator", style = MaterialTheme.typography.headlineSmall)

            if (hasSecret) {
                val code = remember(now / Totp.PERIOD_SECONDS, hasSecret) { store.currentCode() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        code?.chunked(3)?.joinToString(" ") ?: "Key unreadable",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 44.sp,
                    )
                    Spacer(Modifier.size(16.dp))
                    val remaining = Totp.secondsRemaining(now)
                    CircularProgressIndicator(
                        progress = { remaining / Totp.PERIOD_SECONDS.toFloat() },
                        modifier = Modifier.size(32.dp),
                    )
                }
                Button(
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sending = true
                        status = "Sending…"
                        scope.launch {
                            val reply = withContext(Dispatchers.IO) { PhoneListenerService.sendCode(store) }
                            status = describe(reply)
                            sending = false
                        }
                    },
                ) { Text("Send to PC") }
                if (status.isNotEmpty()) Text(status)
            }

            HorizontalDivider()
            SecretSection(store, hasSecret) { hasSecret = it }
            HorizontalDivider()
            IpSection(store)
            HorizontalDivider()
            AutoPromptSection(store, resumes)
        }
    }

    @Composable
    private fun SecretSection(store: SecretStore, hasSecret: Boolean, onChange: (Boolean) -> Unit) {
        var editing by remember { mutableStateOf(!hasSecret) }
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Text("2FA key", style = MaterialTheme.typography.titleMedium)
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saved and encrypted on this phone.", Modifier.weight(1f))
                TextButton(onClick = { editing = true }) { Text("Replace") }
            }
            return
        }
        Text(
            "Paste the key Square Enix shows when you set up the software authenticator, " +
                "or an otpauth:// link.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = { Text("Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val secret = Totp.parseSecret(input)
                if (secret == null) {
                    error = "That doesn't look like a valid key."
                } else {
                    store.setSecret(secret)
                    input = ""
                    editing = false
                    onChange(true)
                }
            }) { Text("Save") }
            if (hasSecret) OutlinedButton(onClick = { editing = false; input = "" }) { Text("Cancel") }
        }
    }

    @Composable
    private fun IpSection(store: SecretStore) {
        var input by remember { mutableStateOf(store.ips.joinToString("; ")) }
        val saved = SecretStore.parseIps(input) == store.ips
        Text("PC address", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("IP of the PC running XIVLauncher") },
            supportingText = { Text("Separate several with ;") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(enabled = !saved, onClick = {
            store.ips = SecretStore.parseIps(input)
            input = store.ips.joinToString("; ")
            if (store.autoPrompt) AutoPrompt.enable(this)
        }) { Text(if (saved) "Saved" else "Save") }
    }

    @Composable
    private fun AutoPromptSection(store: SecretStore, resumes: Long) {
        var enabled by remember { mutableStateOf(store.autoPrompt) }
        var statsTick by remember { mutableLongStateOf(0) }
        val exactAllowed = remember(resumes) { AutoPrompt.canScheduleExact(this) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Prompt on login", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When XIVLauncher asks for a code, your watch buzzes and offers to send it. " +
                        "Only checks while this phone is on the PC's network. Tapping Send on the " +
                        "watch always works, even with this off.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                store.autoPrompt = it
                if (it) AutoPrompt.enable(this@MainActivity) else AutoPrompt.disable(this@MainActivity)
            })
        }
        if (!enabled) return

        if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                "Allow alarms & reminders so checks run on time. Without it, Android may delay " +
                    "them by up to a few seconds more.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }) { Text("Allow alarms") }
        }

        LaunchedEffect(Unit) {
            while (true) {
                statsTick++
                delay(5000)
            }
        }
        val stats = remember(statsTick) { AutoPrompt.stats(this) }
        Text("Battery stats", style = MaterialTheme.typography.titleSmall)
        Text(
            buildString {
                append("Checks: ${stats.probes}, prompts: ${stats.prompts}\n")
                append("Time spent checking: ${"%.1f".format(stats.busyMs / 1000.0)} s\n")
                append("Last check: ")
                append(
                    if (stats.lastProbe == 0L) "never"
                    else DateUtils.getRelativeTimeSpanString(stats.lastProbe).toString() +
                        " (${stats.lastResult?.let(::describeProbe)})"
                )
            },
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { AutoPrompt.clearStats(this); statsTick++ }) { Text("Reset stats") }
    }

    private fun describe(reply: String) = when (reply) {
        WatchLink.RESULT_SENT -> "Sent."
        WatchLink.RESULT_NOT_WAITING -> "The PC is on, but XIVLauncher isn't asking for a code."
        WatchLink.RESULT_UNREACHABLE -> "Couldn't reach the PC. Check the IP and that you're on the same network."
        else -> "Set the key and PC address first."
    }

    private fun describeProbe(name: String) = when (name) {
        Sender.Result.ACCEPTED.name -> "launcher waiting"
        Sender.Result.NOT_WAITING.name -> "PC on, launcher not waiting"
        else -> "PC not answering"
    }
}
