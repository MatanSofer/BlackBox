package com.blackbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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

/**
 * Full-screen lock screen shown when biometric lock is enabled.
 *
 * Displays a lock icon, app name, and an "Unlock" button.
 * Tapping "Unlock" triggers the platform biometric/PIN prompt.
 *
 * @param onUnlock Callback invoked when the user requests authentication.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
            modifier = Modifier.padding(Dimens.PaddingScreen),
        ) {
            // Lock icon in a rounded container
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(BlackBoxColors.SurfaceVariant, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = BlackBoxColors.Indigo,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingXs))

            Text(
                text = "BlackBox",
                style = MaterialTheme.typography.headlineMedium,
                color = BlackBoxColors.TextPrimary,
            )
            Text(
                text = "Authenticate to access\nyour personal data",
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextTertiary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSm))

            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlackBoxColors.Indigo,
                    contentColor = BlackBoxColors.TextPrimary,
                ),
                shape = RoundedCornerShape(Dimens.RadiusMd),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                )
                Text("Unlock")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
    BlackBoxTheme { LockScreen(onUnlock = {}) }
}
