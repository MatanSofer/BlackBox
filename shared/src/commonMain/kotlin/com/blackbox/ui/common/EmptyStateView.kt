package com.blackbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens

/**
 * Full-screen empty state view.
 *
 * Displays a centered illustration (icon), title, descriptive message,
 * and an optional call-to-action button. Used across all screens when
 * there is no data to display.
 *
 * @param title The empty state title (e.g., "No Data Yet").
 * @param message The context-specific description explaining why there is no data.
 * @param modifier Optional [Modifier] for the container.
 * @param actionLabel Optional label for the CTA button.
 * @param onAction Optional callback for the CTA button. If null, the button is hidden.
 */
@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconXl),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Dimens.SpacingXl))

            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateViewPreview() {
    BlackBoxTheme {
        EmptyStateView(
            title = "No Data Yet",
            message = "BlackBox hasn't collected any data yet. Check back later.",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateViewWithActionPreview() {
    BlackBoxTheme {
        EmptyStateView(
            title = "No Data Yet",
            message = "Start recording to see your data here.",
            actionLabel = "Start Recording",
            onAction = {},
        )
    }
}
