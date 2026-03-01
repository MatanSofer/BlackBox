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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.settings_collectors_title
import blackbox.shared.generated.resources.settings_interval
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Settings screen — cyberpunk collector control panel.
 *
 * Renders collector toggle cards with neon borders, monospace labels,
 * neon green switches, and magenta permission warnings.
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
            text = stringResource(Res.string.settings_collectors_title).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = BlackBoxColors.NeonGreen,
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

                    item {
                        Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                        Text(
                            text = "DATA INSPECTION",
                            style = MaterialTheme.typography.labelLarge,
                            color = BlackBoxColors.NeonGreen,
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                        DataInspectionCard(
                            isRawDataViewEnabled = state.isRawDataViewEnabled,
                            onToggle = { enabled ->
                                onAction(SettingsContract.Action.RawDataViewToggled(enabled))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cyberpunk card displaying a single collector's settings with a neon toggle switch.
 *
 * The toggle reflects the **effective** state: both [CollectorSetting.isEnabled]
 * (user preference) AND [permissionGranted] must be true for the switch to appear
 * on. If the user's preference is enabled but the permission is missing, the switch
 * shows off and a neon magenta "Permission required" label is displayed.
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
    // Show "PERMISSION REQUIRED" whenever the permission is not granted,
    // regardless of enabled state — so users see upfront what is needed.
    val needsPermission = !permissionGranted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(
                color = if (effectivelyEnabled) BlackBoxColors.OutlineNeon else BlackBoxColors.OutlineFaint,
                cornerRadius = 4.dp,
            )
            .padding(Dimens.PaddingCard),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatCollectorName(setting.collectorType).uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (effectivelyEnabled) BlackBoxColors.TextPrimary else BlackBoxColors.TextMuted,
            )

            if (needsPermission) {
                Text(
                    text = "PERMISSION REQUIRED",
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.NeonMagenta,
                )
            } else {
                val intervalText = if (setting.collectionIntervalMs == 0L) {
                    "EVENT-DRIVEN"
                } else {
                    stringResource(
                        Res.string.settings_interval,
                        setting.collectionIntervalMs / 1000,
                    ).uppercase()
                }
                Text(
                    text = intervalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }

        Switch(
            checked = effectivelyEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BlackBoxColors.TextOnNeon,
                checkedTrackColor = BlackBoxColors.NeonGreen,
                uncheckedThumbColor = BlackBoxColors.TextMuted,
                uncheckedTrackColor = BlackBoxColors.SurfaceVariant,
                uncheckedBorderColor = BlackBoxColors.OutlineNeon,
            ),
        )
    }
}

/**
 * Card for the Data Inspection section — toggles raw collector data in the Timeline.
 *
 * Unlike collector cards this has no permission implications; it is purely
 * a display preference. A subtitle explains the purpose to orient developers
 * and power users.
 *
 * @param isRawDataViewEnabled Current state of the toggle.
 * @param onToggle Callback when the toggle is changed.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun DataInspectionCard(
    isRawDataViewEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(
                color = if (isRawDataViewEnabled) BlackBoxColors.ElectricCyan else BlackBoxColors.OutlineFaint,
                cornerRadius = 4.dp,
            )
            .padding(Dimens.PaddingCard),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SHOW ALL COLLECTORS IN TIMELINE",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRawDataViewEnabled) BlackBoxColors.TextPrimary else BlackBoxColors.TextMuted,
            )
            Text(
                text = "Displays raw records from all 10 collectors. Use to verify data collection is working.",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextMuted,
            )
        }

        Switch(
            checked = isRawDataViewEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BlackBoxColors.TextOnNeon,
                checkedTrackColor = BlackBoxColors.ElectricCyan,
                uncheckedThumbColor = BlackBoxColors.TextMuted,
                uncheckedTrackColor = BlackBoxColors.SurfaceVariant,
                uncheckedBorderColor = BlackBoxColors.OutlineNeon,
            ),
        )
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
                isRawDataViewEnabled = true,
            ),
            onAction = {},
        )
    }
}
