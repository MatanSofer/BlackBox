package com.blackbox.ui.common

/**
 * Structured UI error types for display in the presentation layer.
 *
 * Maps domain errors to specific UI treatment patterns.
 * Each variant dictates how the error should be rendered.
 */
sealed interface UiError {

    /** The error message to display. */
    val message: UiText

    /** Brief error shown in a snackbar. */
    data class Snackbar(override val message: UiText) : UiError

    /** Full-screen error with optional retry action. */
    data class FullScreen(
        val title: UiText,
        override val message: UiText,
        val retryable: Boolean = false,
    ) : UiError

    /** Inline error shown below a specific field or section. */
    data class Inline(override val message: UiText) : UiError
}
