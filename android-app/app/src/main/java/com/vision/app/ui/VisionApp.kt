package com.vision.app.ui

import androidx.compose.runtime.Composable
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

internal object Destination {
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Permissions = "permissions"
    const val VoiceSetup = "voice_setup"
    const val Home = "home"
    const val Assistant = "assistant"
}

@Composable
fun VisionApp(viewModel: VisionViewModel = viewModel()) {
    val navController = rememberNavController()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    NavHost(navController = navController, startDestination = Destination.Splash) {
        composable(Destination.Splash) {
            SplashScreen(onGetStarted = { navController.navigate(Destination.Onboarding) })
        }
        composable(Destination.Onboarding) {
            OnboardingScreen(
                page = state.onboardingPage,
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
            PermissionsScreen(onContinue = { navController.navigate(Destination.VoiceSetup) })
        }
        composable(Destination.VoiceSetup) {
            VoiceSetupScreen(
                languageTag = state.languageTag,
                speechRate = state.speechRate,
                onLanguageChange = viewModel::setLanguage,
                onSpeechRateChange = viewModel::setSpeechRate,
                onContinue = {
                    navController.navigate(Destination.Home) {
                        popUpTo(Destination.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable(Destination.Home) {
            HomeScreen(onStart = {
                viewModel.loadMockObservation()
                navController.navigate(Destination.Assistant)
            })
        }
        composable(Destination.Assistant) {
            ActiveAssistantScreen(
                observation = state.observation,
                isLoading = state.isLoading,
                onRefresh = viewModel::loadMockObservation,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
