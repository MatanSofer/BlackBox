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
import androidx.compose.ui.unit.dp
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder

/**
 * Terminal-style empty state view with neon border.
 *
 * Displays a centered icon, "NO DATA FOUND" title, descriptive message,
 * and an optional call-to-action button inside a neon-bordered dark card.
 *
 * @param title The empty state title (e.g., "NO DATA FOUND").
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
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(color = BlackBoxColors.OutlineNeon, cornerRadius = 4.dp)
                .padding(Dimens.PaddingCard),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconXl),
                tint = BlackBoxColors.TextMuted,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingLg))

            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = BlackBoxColors.TextMuted,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSm))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextMuted,
                textAlign = TextAlign.Center,
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(Dimens.SpacingXl))

                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxColors.NeonGreen,
                        contentColor = BlackBoxColors.TextOnNeon,
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
