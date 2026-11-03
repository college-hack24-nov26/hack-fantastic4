package com.vision.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vision.app.ui.screens.ActiveAssistantScreen
import com.vision.app.ui.screens.HomeScreen
import com.vision.app.ui.screens.OnboardingScreen
import com.vision.app.ui.screens.PermissionsScreen
import com.vision.app.ui.screens.SplashScreen
import com.vision.app.ui.screens.VoiceSetupScreen
import com.vision.app.ui.screens.CameraPreviewScreen
import com.vision.app.ui.theme.VisionColors

internal object Destination {
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Permissions = "permissions"
    const val VoiceSetup = "voice_setup"
    const val Home = "home"
    const val Assistant = "assistant"
    const val Camera = "camera"
}

@Composable
fun VisionApp(viewModel: VisionViewModel = viewModel()) {
    val navController = rememberNavController()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    if (!state.preferencesLoaded) {
        Surface(Modifier.fillMaxSize(), color = VisionColors.Background) {}
        return
    }
    val strings = VisionStrings.forLanguage(state.language)

    NavHost(navController = navController, startDestination = Destination.Splash) {
        composable(Destination.Splash) {
            SplashScreen(strings, onGetStarted = { navController.navigate(Destination.Onboarding) })
        }
        composable(Destination.Onboarding) {
            OnboardingScreen(
                page = state.onboardingPage,
                strings = strings,
                onBack = {
                    if (!viewModel.previousOnboardingPage()) navController.popBackStack()
                },
                onNext = {
                    if (!viewModel.nextOnboardingPage()) navController.navigate(Destination.Permissions)
                },
                onSkip = { navController.navigate(Destination.Permissions) },
            )
        }
        composable(Destination.Permissions) {
            PermissionsScreen(strings, onContinue = { navController.navigate(Destination.VoiceSetup) })
        }
        composable(Destination.VoiceSetup) {
            VoiceSetupScreen(
                language = state.language,
                strings = strings,
                speechRate = state.speechRate,
                onLanguageChange = viewModel::setLanguage,
                onSpeechRateChange = viewModel::setSpeechRate,
                onContinue = {
                    if (!navController.popBackStack(Destination.Home, inclusive = false)) {
                        navController.navigate(Destination.Home) {
                            popUpTo(Destination.Splash) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Destination.Home) {
            HomeScreen(strings, state.language, viewModel::setLanguage, onSettings = { navController.navigate(Destination.VoiceSetup) }, onCamera = { navController.navigate(Destination.Camera) }, onStart = {
                viewModel.loadMockObservation()
                navController.navigate(Destination.Assistant)
            })
        }
        composable(Destination.Assistant) {
            ActiveAssistantScreen(
                observation = state.observation,
                strings = strings,
                language = state.language,
                speechRate = state.speechRate,
                onLanguageChange = viewModel::setLanguage,
                onRateChange = viewModel::setSpeechRate,
                isLoading = state.isLoading,
                onRefresh = viewModel::loadMockObservation,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.Camera) {
            CameraPreviewScreen(strings, state.language, onBack = { navController.popBackStack() })
        }
    }
}
