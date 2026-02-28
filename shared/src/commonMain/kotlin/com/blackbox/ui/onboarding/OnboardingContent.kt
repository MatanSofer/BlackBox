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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Onboarding screen.
 *
 * Renders a multi-page onboarding flow with page indicators,
 * permission status, and navigation buttons.
 *
 * @param state Current UI state from the ViewModel.
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

        // Page indicator dots
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(state.totalPages) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = Dimens.SpacingXs)
                        .size(if (index == state.currentPage) Dimens.SpacingSm else Dimens.SpacingXs)
                        .clip(CircleShape)
                        .background(
                            if (index == state.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
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
                OutlinedButton(onClick = { onAction(OnboardingContract.Action.PreviousPage) }) {
                    Text(stringResource(Res.string.onboarding_back))
                }
            } else {
                Spacer(modifier = Modifier.width(Dimens.SpacingXl))
            }

            if (state.currentPage == state.totalPages - 1) {
                Button(onClick = { onAction(OnboardingContract.Action.Complete) }) {
                    Text(stringResource(Res.string.onboarding_get_started))
                }
            } else if (state.currentPage == 1) {
                // Permissions page — show skip and next
                Row {
                    TextButton(onClick = { onAction(OnboardingContract.Action.SkipPermissions) }) {
                        Text(stringResource(Res.string.onboarding_skip))
                    }
                    Spacer(modifier = Modifier.width(Dimens.SpacingSm))
                    Button(onClick = { onAction(OnboardingContract.Action.NextPage) }) {
                        Text(stringResource(Res.string.onboarding_next))
                    }
                }
            } else {
                Button(onClick = { onAction(OnboardingContract.Action.NextPage) }) {
                    Text(stringResource(Res.string.onboarding_next))
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))
    }
}

/**
 * Welcome page introducing BlackBox.
 */
@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconXl * 2),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        Text(
            text = stringResource(Res.string.onboarding_p1_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        Text(
            text = stringResource(Res.string.onboarding_p1_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Permissions page showing required permissions and their status.
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
            modifier = Modifier.size(Dimens.IconXl * 2),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        Text(
            text = stringResource(Res.string.onboarding_p2_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        Text(
            text = stringResource(Res.string.onboarding_p2_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * Single permission row with granted/pending status.
 */
@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconMd),
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(modifier = Modifier.width(Dimens.SpacingMd))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (granted) "Granted" else "Pending",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Row for the Usage Access special permission.
 *
 * Unlike normal runtime permissions, Usage Access must be granted manually
 * via a system Settings screen. Shows an "Open Settings" button before the
 * user has attempted it, and a "I've enabled it" check button after they
 * return from Settings.
 *
 * @param granted Whether the permission is currently granted.
 * @param requested Whether the user has already opened the Settings screen.
 * @param onOpen Called when the user taps "Open Settings".
 * @param onCheck Called when the user taps "I've enabled it" to re-verify.
 */
@Composable
private fun UsageAccessRow(
    granted: Boolean,
    requested: Boolean,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconMd),
                tint = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(modifier = Modifier.width(Dimens.SpacingMd))

            Text(
                text = "App Usage Tracking",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            if (granted) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (!requested) {
                FilledTonalButton(onClick = onOpen) {
                    Text("Open Settings", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                FilledTonalButton(onClick = onCheck) {
                    Text("I've enabled it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (!granted) {
            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
            Text(
                text = "Required to track which apps you use. Not enabled by default.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.IconMd + Dimens.SpacingMd),
            )
        }
    }
}

/**
 * Final page — ready to start using BlackBox.
 */
@Composable
private fun ReadyPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconXl * 2),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        Text(
            text = stringResource(Res.string.onboarding_p3_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        Text(
            text = stringResource(Res.string.onboarding_p3_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

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
