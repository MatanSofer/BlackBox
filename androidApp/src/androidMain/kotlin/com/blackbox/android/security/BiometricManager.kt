package com.blackbox.android.security

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages biometric authentication for app access.
 *
 * Wraps Android's BiometricPrompt API to provide a coroutine-based
 * authentication flow. Supports fingerprint, face, and device
 * credential fallback.
 *
 * @property logger Logger for operation tracking.
 */
class BiometricManager(
    private val logger: BlackBoxLogger,
) {

    /**
     * Checks whether the device supports biometric authentication.
     *
     * @param activity The current activity for context.
     * @return [BiometricStatus] indicating availability.
     */
    fun checkAvailability(activity: FragmentActivity): BiometricStatus {
        val biometricManager = androidx.biometric.BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(AUTHENTICATOR_TYPES)) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricStatus.AVAILABLE

            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricStatus.NO_HARDWARE

            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricStatus.HARDWARE_UNAVAILABLE

            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricStatus.NOT_ENROLLED

            else -> BiometricStatus.UNAVAILABLE
        }
    }

    /**
     * Shows the biometric prompt and suspends until authentication completes.
     *
     * @param activity The activity to attach the prompt to.
     * @param title The prompt title.
     * @param subtitle The prompt subtitle.
     * @return [AuthResult] indicating success or failure.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "BlackBox Authentication",
        subtitle: String = "Verify your identity to access your data",
    ): AuthResult = suspendCancellableCoroutine { continuation ->
        logger.d(TAG, "Showing biometric prompt")

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                logger.d(TAG, "Authentication succeeded")
                if (continuation.isActive) {
                    continuation.resume(AuthResult.Success)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                logger.w(TAG, "Authentication error ($errorCode): $errString")
                if (continuation.isActive) {
                    continuation.resume(
                        AuthResult.Error(
                            code = errorCode,
                            message = errString.toString(),
                        ),
                    )
                }
            }

            override fun onAuthenticationFailed() {
                logger.d(TAG, "Authentication attempt failed (wrong biometric)")
                // Don't resume — the prompt stays open for retry
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATOR_TYPES)
            .build()

        prompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
            logger.d(TAG, "Authentication cancelled")
            prompt.cancelAuthentication()
        }
    }

    companion object {
        private const val TAG = "BiometricManager"

        /**
         * Allowed authenticator types: biometric strong + device credential fallback.
         */
        private const val AUTHENTICATOR_TYPES =
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    }
}

/**
 * Result of a biometric authentication attempt.
 */
sealed interface AuthResult {
    /** Authentication succeeded. */
    data object Success : AuthResult

    /** Authentication failed with an error. */
    data class Error(val code: Int, val message: String) : AuthResult
}

/**
 * Status of biometric hardware availability.
 */
enum class BiometricStatus {
    /** Biometric authentication is available and ready. */
    AVAILABLE,
    /** Device has no biometric hardware. */
    NO_HARDWARE,
    /** Biometric hardware exists but is unavailable. */
    HARDWARE_UNAVAILABLE,
    /** No biometrics enrolled on the device. */
    NOT_ENROLLED,
    /** Biometric authentication is unavailable for other reasons. */
    UNAVAILABLE,
}
