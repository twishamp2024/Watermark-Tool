package com.animecliplabs.clipmark.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.ui.batch.BatchScreen
import com.animecliplabs.clipmark.ui.batch.BatchViewModel
import com.animecliplabs.clipmark.ui.common.LocalAppContainer
import com.animecliplabs.clipmark.ui.editor.EditorScreen
import com.animecliplabs.clipmark.ui.editor.EditorViewModel
import com.animecliplabs.clipmark.ui.history.HistoryScreen
import com.animecliplabs.clipmark.ui.history.HistoryViewModel
import com.animecliplabs.clipmark.ui.home.HomeScreen
import com.animecliplabs.clipmark.ui.home.HomeViewModel
import com.animecliplabs.clipmark.ui.presets.PresetsScreen
import com.animecliplabs.clipmark.ui.presets.PresetsViewModel
import com.animecliplabs.clipmark.ui.settings.SettingsScreen
import com.animecliplabs.clipmark.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
/** [presetId] set → edit that preset on the sample image; [newPreset] → design a new one. */
@Serializable data class EditorRoute(val presetId: String? = null, val newPreset: Boolean = false)
@Serializable data object BatchRoute
@Serializable data object PresetsRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

sealed interface AppEvent {
    /** Photos arrived via the share sheet. */
    data object OpenEditor : AppEvent
    /** The user tapped a batch notification. */
    data object OpenBatch : AppEvent
}

@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: CreationExtras.(AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun ClipMarkNavHost(events: Flow<AppEvent>) {
    val nav = rememberNavController()

    LaunchedEffect(nav) {
        events.collect { event ->
            when (event) {
                AppEvent.OpenEditor -> nav.navigate(EditorRoute()) { popUpTo(HomeRoute) }
                AppEvent.OpenBatch -> nav.navigate(BatchRoute) { popUpTo(HomeRoute); launchSingleTop = true }
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideInHorizontally(tween(280)) { it / 6 } + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(200)) },
    ) {
        composable<HomeRoute> { entry ->
            val openPicker by entry.savedStateHandle.getStateFlow(HomeScreenKeys.OPEN_PICKER, false).collectAsState()
            HomeScreen(
                viewModel = containerViewModel { HomeViewModel(it) },
                openPickerRequested = openPicker,
                onPickerRequestConsumed = { entry.savedStateHandle[HomeScreenKeys.OPEN_PICKER] = false },
                onOpenEditor = { nav.navigate(EditorRoute()) },
                onEditPreset = { id -> nav.navigate(EditorRoute(presetId = id)) },
                onNewPreset = { nav.navigate(EditorRoute(newPreset = true)) },
                onOpenBatch = { nav.navigate(BatchRoute) { launchSingleTop = true } },
                onOpenPresets = { nav.navigate(PresetsRoute) },
                onOpenHistory = { nav.navigate(HistoryRoute) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(
                viewModel = containerViewModel { EditorViewModel(it, createSavedStateHandle()) },
                onBack = { nav.popBackStack() },
                onBatchStarted = { nav.navigate(BatchRoute) { popUpTo(HomeRoute) } },
            )
        }
        composable<BatchRoute> {
            BatchScreen(
                viewModel = containerViewModel { BatchViewModel(it) },
                onClose = { nav.popBackStack(HomeRoute, inclusive = false) },
                onProcessMore = {
                    nav.popBackStack(HomeRoute, inclusive = false)
                    nav.currentBackStackEntry?.savedStateHandle?.set(HomeScreenKeys.OPEN_PICKER, true)
                },
            )
        }
        composable<PresetsRoute> {
            PresetsScreen(
                viewModel = containerViewModel { PresetsViewModel(it) },
                onBack = { nav.popBackStack() },
                onEditPreset = { id -> nav.navigate(EditorRoute(presetId = id)) },
                onNewPreset = { nav.navigate(EditorRoute(newPreset = true)) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(viewModel = containerViewModel { HistoryViewModel(it) }, onBack = { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(viewModel = containerViewModel { SettingsViewModel(it) }, onBack = { nav.popBackStack() })
        }
    }
}

object HomeScreenKeys {
    const val OPEN_PICKER = "open_picker"
}
