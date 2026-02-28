package com.blackbox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.settings_collectors_title
import blackbox.shared.generated.resources.settings_interval
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Settings screen.
 *
 * Renders collector toggle cards with enable/disable switches
 * and collection interval information.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun SettingsContent(
    state: SettingsContract.State,
    onAction: (SettingsContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        Text(
            text = stringResource(Res.string.settings_collectors_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        when {
            state.isLoading -> {
                LoadingIndicator()
            }

            state.error != null -> {
                ErrorView(
                    message = state.error,
                    onRetry = { onAction(SettingsContract.Action.Refresh) },
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                ) {
                    items(state.collectorSettings, key = { it.collectorType.name }) { setting ->
                        val permissionGranted = state.permissionsGranted[setting.collectorType] ?: true
                        CollectorSettingCard(
                            setting = setting,
                            permissionGranted = permissionGranted,
                            onToggle = { enabled ->
                                onAction(
                                    SettingsContract.Action.CollectorToggled(
                                        setting.collectorType,
                                        enabled,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card displaying a single collector's settings with a toggle switch.
 *
 * The toggle reflects the **effective** state: both [CollectorSetting.isEnabled]
 * (user preference) AND [permissionGranted] must be true for the switch to appear
 * on. If the user's preference is enabled but the permission is missing, the switch
 * shows off and a "Permission required" label is displayed. The ViewModel handles
 * requesting the permission when the user taps the switch.
 *
 * @param setting The collector setting to display.
 * @param permissionGranted Whether the required runtime permission is currently granted.
 * @param onToggle Callback when the toggle is changed.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun CollectorSettingCard(
    setting: CollectorSetting,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectivelyEnabled = setting.isEnabled && permissionGranted
    val needsPermission = setting.isEnabled && !permissionGranted

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingCard),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatCollectorName(setting.collectorType),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (needsPermission) {
                    Text(
                        text = "Permission required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val intervalText = if (setting.collectionIntervalMs == 0L) {
                        "Event-driven"
                    } else {
                        stringResource(
                            Res.string.settings_interval,
                            setting.collectionIntervalMs / 1000,
                        )
                    }
                    Text(
                        text = intervalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Switch(
                checked = effectivelyEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

/**
 * Formats a [CollectorType] enum into a human-readable name.
 */
private fun formatCollectorName(type: CollectorType): String {
    return type.name.replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentEmptyPreview() {
    BlackBoxTheme {
        SettingsContent(
            state = SettingsContract.State(
                collectorSettings = emptyList(),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentWithDataPreview() {
    BlackBoxTheme {
        SettingsContent(
            state = SettingsContract.State(
                collectorSettings = listOf(
                    CollectorSetting(CollectorType.LOCATION, isEnabled = true, collectionIntervalMs = 0),
                    CollectorSetting(CollectorType.ACTIVITY, isEnabled = true, collectionIntervalMs = 0),
                    CollectorSetting(CollectorType.WIFI, isEnabled = true, collectionIntervalMs = 300_000),
                    CollectorSetting(CollectorType.APP_USAGE, isEnabled = true, collectionIntervalMs = 120_000),
                    CollectorSetting(CollectorType.SCREEN_STATE, isEnabled = true, collectionIntervalMs = 0),
                    CollectorSetting(CollectorType.AUDIO_LEVEL, isEnabled = false, collectionIntervalMs = 600_000),
                    CollectorSetting(CollectorType.BATTERY, isEnabled = true, collectionIntervalMs = 0),
                    CollectorSetting(CollectorType.BAROMETER, isEnabled = false, collectionIntervalMs = 300_000),
                    CollectorSetting(CollectorType.LIGHT, isEnabled = false, collectionIntervalMs = 300_000),
                ),
            ),
            onAction = {},
        )
    }
}
