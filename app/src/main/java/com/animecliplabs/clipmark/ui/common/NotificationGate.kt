package com.animecliplabs.clipmark.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * Asks for notification permission once, at the moment it becomes meaningful (the first batch),
 * then runs the action whatever the answer. Never nags again.
 */
@Composable
fun rememberNotificationGate(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    val settings = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val asked by settings.askedForNotifications.collectAsState(initial = true)
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    return { action ->
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            scope.launch { settings.markAskedForNotifications() }
            pending = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
}
