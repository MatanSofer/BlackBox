package com.blackbox.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.onboarding_back
import blackbox.shared.generated.resources.onboarding_get_started
import blackbox.shared.generated.resources.onboarding_next
import blackbox.shared.generated.resources.onboarding_p1_desc
import blackbox.shared.generated.resources.onboarding_p1_title
import blackbox.shared.generated.resources.onboarding_p2_desc
import blackbox.shared.generated.resources.onboarding_p2_title
import blackbox.shared.generated.resources.onboarding_p3_desc
import blackbox.shared.generated.resources.onboarding_p3_title
import blackbox.shared.generated.resources.onboarding_skip
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.PulseRingIndicator
import com.blackbox.ui.theme.indigoTealGradient
import com.blackbox.ui.theme.obsidianCard
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Onboarding screen — clean Obsidian initialization flow.
 *
 * Three-page onboarding: welcome → permissions → ready.
 * Page indicators are pill-shaped with animated active state.
 *
 * @param state    Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun OnboardingContent(
    state: OnboardingContract.State,
    onAction: (OnboardingContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxColors.Background)
            .padding(Dimens.PaddingScreen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Page content
        when (state.currentPage) {
            0 -> WelcomePage()
            1 -> PermissionsPage(state, onAction)
            2 -> ReadyPage()
        }

        Spacer(modifier = Modifier.weight(1f))

        // Pill page indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(state.totalPages) { index ->
                val isActive = index == state.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = Dimens.SpacingXs)
                        .size(width = if (isActive) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) BlackBoxColors.Indigo else BlackBoxColors.Border,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.currentPage > 0) {
                OutlinedButton(
                    onClick = { onAction(OnboardingContract.Action.PreviousPage) },
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = BlackBoxColors.Border,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BlackBoxColors.TextSecondary,
                    ),
                ) {
                    Text(stringResource(Res.string.onboarding_back))
                }
            } else {
                Spacer(modifier = Modifier.width(Dimens.SpacingXl))
            }

            if (state.currentPage == state.totalPages - 1) {
                Button(
                    onClick = { onAction(OnboardingContract.Action.Complete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxColors.Indigo,
                        contentColor = BlackBoxColors.OnAccent,
                    ),
                ) {
                    Text(stringResource(Res.string.onboarding_get_started))
                }
            } else if (state.currentPage == 1) {
                Row {
                    TextButton(
                        onClick = { onAction(OnboardingContract.Action.SkipPermissions) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = BlackBoxColors.TextTertiary,
                        ),
                    ) {
                        Text(stringResource(Res.string.onboarding_skip))
                    }
                    Spacer(modifier = Modifier.width(Dimens.SpacingSm))
                    Button(
                        onClick = { onAction(OnboardingContract.Action.NextPage) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlackBoxColors.Indigo,
                            contentColor = BlackBoxColors.OnAccent,
                        ),
                    ) {
                        Text(stringResource(Res.string.onboarding_next))
                    }
                }
            } else {
                Button(
                    onClick = { onAction(OnboardingContract.Action.NextPage) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxColors.Indigo,
                        contentColor = BlackBoxColors.OnAccent,
                    ),
                ) {
                    Text(stringResource(Res.string.onboarding_next))
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))
    }
}

// ── Page 1: Welcome ────────────────────────────────────────────────────────────

/**
 * Welcome page — gradient "BlackBox" wordmark with tagline.
 */
@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Logo mark: layered rings
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(BlackBoxColors.IndigoDim, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(BlackBoxColors.Indigo.copy(alpha = 0.15f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(BlackBoxColors.Indigo, CircleShape),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        // Gradient title
        Text(
            text = "BlackBox",
            style = TextStyle(
                brush = indigoTealGradient(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_p1_title),
            style = MaterialTheme.typography.titleMedium,
            color = BlackBoxColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        Text(
            text = stringResource(Res.string.onboarding_p1_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = BlackBoxColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Page 2: Permissions ────────────────────────────────────────────────────────

/**
 * Permissions page — clean permission cards with granted/pending status.
 */
@Composable
private fun PermissionsPage(
    state: OnboardingContract.State,
    onAction: (OnboardingContract.Action) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = BlackBoxColors.Indigo,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        Text(
            text = stringResource(Res.string.onboarding_p2_title),
            style = MaterialTheme.typography.headlineSmall,
            color = BlackBoxColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_p2_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = BlackBoxColors.TextTertiary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        PermissionRow(label = "Location", granted = state.locationGranted)
        PermissionRow(label = "Activity Recognition", granted = state.activityGranted)
        PermissionRow(label = "Notifications", granted = state.notificationGranted)
        UsageAccessRow(
            granted = state.usageAccessGranted,
            requested = state.usageAccessRequested,
            onOpen = { onAction(OnboardingContract.Action.OpenUsageAccessSettings) },
            onCheck = { onAction(OnboardingContract.Action.CheckUsageAccess) },
        )
    }
}

/**
 * Single permission row card.
 *
 * @param label   The permission display name.
 * @param granted Whether the permission is currently granted.
 */
@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingXs)
            .obsidianCard(
                cornerRadius = Dimens.RadiusMd,
                borderColor = if (granted) BlackBoxColors.Success.copy(alpha = 0.3f) else BlackBoxColors.Border,
            )
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.IconMd)
                .background(
                    if (granted) BlackBoxColors.Success.copy(alpha = 0.15f) else BlackBoxColors.SurfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSm),
                tint = if (granted) BlackBoxColors.Success else BlackBoxColors.TextTertiary,
            )
        }

        Spacer(modifier = Modifier.width(Dimens.SpacingMd))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BlackBoxColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .background(
                    if (granted) BlackBoxColors.Success.copy(alpha = 0.12f) else BlackBoxColors.SurfaceVariant,
                    RoundedCornerShape(Dimens.RadiusFull),
                )
                .padding(horizontal = Dimens.SpacingSm, vertical = 2.dp),
        ) {
            Text(
                text = if (granted) "Granted" else "Pending",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) BlackBoxColors.Success else BlackBoxColors.TextTertiary,
            )
        }
    }
}

/**
 * Row for the Usage Access special permission.
 *
 * Unlike normal runtime permissions, Usage Access must be granted manually
 * via a system Settings screen. Shows an "Open Settings" button before the
 * user has attempted it, and a "I've enabled it" check button after they return.
 *
 * @param granted   Whether the permission is currently granted.
 * @param requested Whether the user has already opened the Settings screen.
 * @param onOpen    Called when the user taps "Open Settings".
 * @param onCheck   Called when the user taps "I've enabled it".
 */
@Composable
private fun UsageAccessRow(
    granted: Boolean,
    requested: Boolean,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingXs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .obsidianCard(
                    cornerRadius = Dimens.RadiusMd,
                    borderColor = if (granted) BlackBoxColors.Success.copy(alpha = 0.3f) else BlackBoxColors.Border,
                )
                .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.IconMd)
                    .background(
                        if (granted) BlackBoxColors.Success.copy(alpha = 0.15f) else BlackBoxColors.SurfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.Check else Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.IconSm),
                    tint = if (granted) BlackBoxColors.Success else BlackBoxColors.TextTertiary,
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SpacingMd))

            Text(
                text = "App Usage Tracking",
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )

            when {
                granted -> Box(
                    modifier = Modifier
                        .background(BlackBoxColors.Success.copy(alpha = 0.12f), RoundedCornerShape(Dimens.RadiusFull))
                        .padding(horizontal = Dimens.SpacingSm, vertical = 2.dp),
                ) {
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.labelSmall,
                        color = BlackBoxColors.Success,
                    )
                }
                !requested -> FilledTonalButton(
                    onClick = onOpen,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BlackBoxColors.IndigoDim,
                        contentColor = BlackBoxColors.IndigoLight,
                    ),
                ) {
                    Text("Open Settings", style = MaterialTheme.typography.labelSmall)
                }
                else -> FilledTonalButton(
                    onClick = onCheck,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BlackBoxColors.TealDim,
                        contentColor = BlackBoxColors.Teal,
                    ),
                ) {
                    Text("I've enabled it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (!granted) {
            Text(
                text = "Required to track which apps you use. Not enabled by default.",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.TextTertiary,
                modifier = Modifier.padding(start = Dimens.IconMd + Dimens.SpacingMd, top = Dimens.SpacingXxs),
            )
        }
    }
}

// ── Page 3: Ready ──────────────────────────────────────────────────────────────

/**
 * Final page — animated ring with "All set" confirmation.
 */
@Composable
private fun ReadyPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PulseRingIndicator(
            color = BlackBoxColors.Indigo,
            size = 88.dp,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        Text(
            text = "All set",
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(BlackBoxColors.Indigo, BlackBoxColors.Teal),
                ),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_p3_title),
            style = MaterialTheme.typography.titleMedium,
            color = BlackBoxColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        Text(
            text = stringResource(Res.string.onboarding_p3_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = BlackBoxColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun OnboardingWelcomePreview() {
    BlackBoxTheme {
        OnboardingContent(
            state = OnboardingContract.State(currentPage = 0),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPermissionsPreview() {
    BlackBoxTheme {
        OnboardingContent(
            state = OnboardingContract.State(
                currentPage = 1,
                locationGranted = true,
                activityGranted = false,
                notificationGranted = false,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingReadyPreview() {
    BlackBoxTheme {
        OnboardingContent(
            state = OnboardingContract.State(currentPage = 2),
            onAction = {},
        )
    }
}
