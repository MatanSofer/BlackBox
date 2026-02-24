package com.blackbox.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.ui.BlackBoxApp
import com.blackbox.ui.theme.BlackBoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Single activity for the BlackBox app.
 *
 * Sets up edge-to-edge display, applies [BlackBoxTheme], and delegates
 * to the shared [BlackBoxApp] composable for all UI. Checks onboarding
 * completion state to determine the start destination.
 *
 * Uses a nullable loading state to avoid flashing the main screen
 * before the onboarding check completes.
 */
class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        var isOnboardingComplete: Boolean? by mutableStateOf(null)

        lifecycleScope.launch {
            isOnboardingComplete = withContext(Dispatchers.IO) {
                settingsRepository.isOnboardingComplete()
            }
        }

        setContent {
            BlackBoxTheme {
                BlackBoxApp(isOnboardingComplete = isOnboardingComplete)
            }
        }
    }
}
