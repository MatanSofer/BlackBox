package com.blackbox.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Abstraction for UI text that can come from either a raw string
 * or a localized string resource.
 *
 * Allows ViewModels to produce text without needing Compose context,
 * while still supporting full localization through string resources.
 */
sealed class UiText {

    /** A plain string value. */
    data class Raw(val value: String) : UiText()

    /** A localized string resource with optional format arguments. */
    data class Resource(val resource: StringResource, val args: List<Any> = emptyList()) : UiText()

    /**
     * Resolves the text to a displayable string within a Compose scope.
     */
    @Composable
    fun asString(): String {
        return when (this) {
            is Raw -> value
            is Resource -> {
                if (args.isEmpty()) {
                    stringResource(resource)
                } else {
                    stringResource(resource, *args.toTypedArray())
                }
            }
        }
    }
}
