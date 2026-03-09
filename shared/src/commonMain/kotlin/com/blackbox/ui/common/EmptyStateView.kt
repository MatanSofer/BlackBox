package com.blackbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.obsidianCard

/**
 * Full-screen empty state with icon, title, message, and optional action button.
 *
 * @param title       Short empty-state heading.
 * @param message     Explanation of why data is absent.
 * @param modifier    Optional [Modifier].
 * @param actionLabel Optional CTA button label.
 * @param onAction    Optional CTA callback. Button hidden when null.
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
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .obsidianCard(cornerRadius = Dimens.RadiusMd)
                .padding(Dimens.SpacingXxl),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconXl),
                tint = BlackBoxColors.TextTertiary,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingLg))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BlackBoxColors.TextSecondary,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSm))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextTertiary,
                textAlign = TextAlign.Center,
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(Dimens.SpacingXl))

                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxColors.Indigo,
                        contentColor = BlackBoxColors.OnAccent,
                    ),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateViewPreview() {
    BlackBoxTheme {
        EmptyStateView(
            title = "No data yet",
            message = "BlackBox hasn't collected any data yet. Check back later.",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateViewWithActionPreview() {
    BlackBoxTheme {
        EmptyStateView(
            title = "Nothing here",
            message = "Start recording to see your data here.",
            actionLabel = "Get started",
            onAction = {},
        )
    }
}
