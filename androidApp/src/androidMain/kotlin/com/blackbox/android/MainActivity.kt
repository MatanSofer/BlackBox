package com.blackbox.android

import android.Manifest
import android.content.Intent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.blackbox.android.security.AuthResult
import com.blackbox.android.security.BiometricManager
import com.blackbox.android.service.BlackBoxService
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
 * Starts [BlackBoxService] as soon as onboarding is confirmed complete,
 * so data collection begins immediately and continues in the background
 * even after the user closes the app.
 *
 * Uses a nullable loading state to avoid flashing the main screen
 * before the onboarding check completes.
 *
 * Permission launchers are registered here (Android-specific API) and
 * passed as plain lambdas into the shared composable hierarchy. Results
 * are detected automatically via the ON_RESUME lifecycle observer in
 * [com.blackbox.ui.onboarding.OnboardingScreen].
 */
class MainActivity : FragmentActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private val biometricManager: BiometricManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() to intercept the system splash.
        var isOnboardingComplete: Boolean? by mutableStateOf(null)
        var isAuthenticated: Boolean? by mutableStateOf(null)
        installSplashScreen().setKeepOnScreenCondition { isOnboardingComplete == null }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val complete = withContext(Dispatchers.IO) {
                settingsRepository.isOnboardingComplete()
            }
            val biometricEnabled = if (complete) withContext(Dispatchers.IO) {
                settingsRepository.isBiometricLockEnabled()
            } else false

            isOnboardingComplete = complete
            if (!biometricEnabled) {
                isAuthenticated = true
            } else {
                isAuthenticated = false
                // Auto-trigger the prompt immediately on launch
                triggerBiometricAuth { isAuthenticated = true }
            }

            if (complete) {
                startForegroundService(BlackBoxService.newIntent(this@MainActivity))
            }
        }

        setContent {
            // Permission launchers — results are handled by the ON_RESUME observer
            // in OnboardingScreen / SettingsScreen rather than in these callbacks,
            // so no result threading through the composable hierarchy is needed.
            val locationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { /* result picked up on resume */ }

            val singlePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* result picked up on resume */ }

            BlackBoxTheme {
                BlackBoxApp(
                    isOnboardingComplete = isOnboardingComplete,
                    isAuthenticated = isAuthenticated,
                    onRequestAuthentication = {
                        lifecycleScope.launch {
                            triggerBiometricAuth { isAuthenticated = true }
                        }
                    },
                    onStartService = {
                        startForegroundService(BlackBoxService.newIntent(this@MainActivity))
                    },
                    onOpenUsageAccessSettings = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onRequestLocationPermission = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onRequestActivityPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            singlePermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            singlePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenAppSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            },
                        )
                    },
                )
            }
        }
    }

    /** Shows the biometric / device-credential prompt and invokes [onSuccess] if it passes. */
    private suspend fun triggerBiometricAuth(onSuccess: () -> Unit) {
        val result = biometricManager.authenticate(this@MainActivity)
        if (result is AuthResult.Success) {
            onSuccess()
        }
    }
}
