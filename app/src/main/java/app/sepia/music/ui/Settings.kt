package app.sepia.music.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import app.sepia.music.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.sepia.music.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.material.icons.rounded.Notifications
@Composable
private fun SettingBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val p = Graph.prefs
    val cs = MaterialTheme.colorScheme
    val act = LocalActions.current
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    var autoplay by remember { mutableStateOf(p.autoplay) }
    var quality by remember { mutableStateOf(p.quality) }
    var wifi by remember { mutableStateOf(p.wifiOnlyDownloads) }
    var piped by remember { mutableStateOf(p.pipedUrl) }
    var cacheMb by remember { mutableStateOf(p.cacheMb) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 60.dp)) {
            SettingBlock("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Match phone", AppTheme.mode == "system", { AppTheme.set("system") })
                    Pill("Light", AppTheme.mode == "light", { AppTheme.set("light") })
                    Pill("Dark", AppTheme.mode == "dark", { AppTheme.set("dark") })
                }
            }
            SettingBlock("Player style") {
                Text(
                    "Choose how the full-screen player looks.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                val styles = listOf(
                    "default" to "Sepia", "apple" to "Apple Music", "wavy" to "Wavy",
                    "ytm" to "YT Music", "spotify" to "Spotify"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    styles.take(2).forEach { (key, label) ->
                        Pill(label, AppTheme.playerStyle == key, { AppTheme.choosePlayerStyle(key) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    styles.drop(2).forEach { (key, label) ->
                        Pill(label, AppTheme.playerStyle == key, { AppTheme.choosePlayerStyle(key) })
                    }
                }
            }
            SettingBlock("Playback") {
                Text("Streaming quality", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Best", quality == "high", {
                        quality = "high"
                        p.quality = "high"
                    })
                    Pill("Data saver", quality == "low", {
                        quality = "low"
                        p.quality = "low"
                    })
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Autoplay similar songs", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "When your queue ends, keep playing songs like the last one",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoplay, onCheckedChange = {
                        autoplay = it
                        p.autoplay = it
                    })
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Can't see the music controls in your notifications? Allow notifications for Sepia.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ActionButton("Notification settings", Icons.Rounded.Notifications, false) {
                    try {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    } catch (e: Exception) {
                        act.toast("Open Android Settings > Apps > Sepia > Notifications")
                    }
                }
            }
            SettingBlock("Downloads") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Saves your mobile data",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    Switch(checked = wifi, onCheckedChange = {
                        wifi = it
                        p.wifiOnlyDownloads = it
                    })
                }
            }
            SettingBlock("Storage") {
                Text("Stream cache size", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Streamed songs are kept so they replay instantly. Applies next time you open the app.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(256, 512, 1024, 2048).forEach { mb ->
                        Pill(if (mb >= 1024) "${mb / 1024} GB" else "$mb MB", cacheMb == mb, {
                            cacheMb = mb
                            p.cacheMb = mb
                        })
                    }
                }
                Spacer(Modifier.height(14.dp))
                ActionButton("Clear cache", Icons.Rounded.Delete, false) {
                    Graph.scope.launch(Dispatchers.IO) {
                        for (key in Graph.cache.keys.toList()) runCatching { Graph.cache.removeResource(key) }
                    }
                    act.toast("Cache cleared")
                }
            }
            SettingBlock("Custom stream server (optional)") {
                Text(
                    "If a song ever refuses to play, Sepia can ask a Piped-compatible server for the audio. " +
                        "Paste its address here.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = piped,
                    onValueChange = {
                        piped = it
                        p.pipedUrl = it
                    },
                    singleLine = true,
                    placeholder = { Text("https://your-piped-api.example") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingBlock("About") {
                Text(
                    "Sepia is free and open source, with no ads. It finds and streams music through the " +
                        "NewPipe extractor (GPL-3.0). Music belongs to its artists \u2014 please use downloads " +
                        "for personal listening only.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("Version 1.2.0", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Text("Made by", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(
                    "@_ur_._adii_",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.primary,
                    modifier = Modifier
                        .bounceClick { runCatching { uri.openUri("https://instagram.com/_ur_._adii_") } }
                        .padding(vertical = 4.dp)
                )
                Text(
                    "Tap to follow on Instagram",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            SettingBlock("Support Sepia") {
                Text(
                    "Sepia is free and has no ads. If you enjoy it, you can support its development with any amount.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Image(
                    painter = painterResource(R.drawable.upi_qr),
                    contentDescription = "UPI QR code",
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(18.dp))
                )
                Spacer(Modifier.height(10.dp))
                Text("UPI ID", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("aditayverma03@fam", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Copy ID", Icons.Rounded.ContentCopy, false) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("UPI ID", "aditayverma03@fam"))
                        act.toast("UPI ID copied")
                    }
                    ActionButton("Open UPI app", Icons.Rounded.Favorite, true) {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=aditayverma03@fam&cu=INR"))
                            )
                        } catch (e: Exception) {
                            act.toast("No UPI app found. Scan the QR code instead.")
                        }
                    }
                }
            }
        }
    }
}
