package com.hoddesk.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hoddesk.app.settings.DeskSettingsStore
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.theme.Hod

/** Pure decision: should the app ask for the phone's screen lock now? */
object AppLockPolicy {
    /** Choices offered in Settings; 0 means "as soon as the app leaves the screen". */
    val TIMEOUT_CHOICES = listOf(0, 1, 5, 15, 30)

    fun shouldLock(enabled: Boolean, deviceSecure: Boolean, lastBackgroundAt: Long?, now: Long, timeoutMinutes: Int): Boolean {
        if (!enabled || !deviceSecure || lastBackgroundAt == null) return false
        // A clock moved backwards cannot prove that little time has passed, so lock.
        if (now < lastBackgroundAt) return true
        return now - lastBackgroundAt >= timeoutMinutes.coerceAtLeast(0) * 60_000L
    }

    fun timeoutLabel(minutes: Int): String = when (minutes) {
        0 -> "Immediately"
        1 -> "After 1 minute"
        else -> "After $minutes minutes"
    }
}

/** Remembers when the app last left the screen. Kept on this phone only; never backed up. */
class AppLockState(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("hod_desk_app_lock", Context.MODE_PRIVATE)

    val deviceSecure: Boolean get() = (app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    fun markBackground(now: Long = System.currentTimeMillis()) { prefs.edit().putLong(KEY, now).commit() }
    fun clear() { prefs.edit().remove(KEY).commit() }

    fun shouldLockNow(settings: DeskSettingsStore, now: Long = System.currentTimeMillis()): Boolean =
        AppLockPolicy.shouldLock(settings.autoLockEnabled, deviceSecure, prefs.getLong(KEY, -1L).takeIf { it > 0 }, now, settings.autoLockMinutes)

    @Suppress("DEPRECATION")
    fun credentialIntent(): Intent? {
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        return if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("Psychiatry HOD Desk", "Unlock to continue") else null
    }

    private companion object { const val KEY = "last_background_at" }
}

/**
 * Covers the app with a lock screen after it has been away for the chosen time, and asks for the
 * phone's own fingerprint, face, PIN or pattern. The app underneath stays alive, so open dialogs,
 * drafts and file pickers are not lost; it simply cannot be seen or touched until unlocked.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { DeskSettingsStore(context) }
    val state = remember { AppLockState(context) }
    var locked by rememberSaveable { mutableStateOf(state.shouldLockNow(settings)) }
    var unlocking by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        unlocking = false
        if (result.resultCode == Activity.RESULT_OK) { state.clear(); locked = false }
    }
    fun requestUnlock() {
        if (unlocking) return
        val intent = state.credentialIntent()
        // The screen lock was removed from the phone: there is nothing to verify against.
        if (intent == null) { state.clear(); locked = false; return }
        unlocking = true
        runCatching { unlockLauncher.launch(intent) }.onFailure { unlocking = false }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> state.markBackground()
                Lifecycle.Event.ON_START -> if (!locked && state.shouldLockNow(settings)) locked = true
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(locked) {
        if (locked) { focus.clearFocus(force = true); requestUnlock() }
    }

    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { content() }
        if (locked) {
            BackHandler { (context as? Activity)?.moveTaskToBack(true) }
            LockScreen(onUnlock = ::requestUnlock)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val c = Hod.colors
    Box(
        Modifier.fillMaxSize().background(c.groupedBackground)
            // Swallows every touch so nothing underneath can be operated.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.tint, modifier = Modifier.size(44.dp))
            Text("HOD Desk is locked", style = Hod.type.title3, color = c.label)
            Text("Use your fingerprint, face or screen lock to continue.", style = Hod.type.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center)
            HodButton("Unlock", onUnlock, modifier = Modifier.fillMaxWidth())
        }
    }
}
