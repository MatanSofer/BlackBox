package com.blackbox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.settings_collectors_title
import blackbox.shared.generated.resources.settings_interval
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.model.settings.RetentionPeriod
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.obsidianCard
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Settings screen — Obsidian collector control panel.
 *
 * Renders collector toggle cards with clean dark surfaces, indigo toggles,
 * and teal/rose accents per section.
 *
 * @param state    Current UI state from the ViewModel.
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
            .padding(horizontal = Dimens.PaddingScreen),
    ) {
        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = BlackBoxColors.TextPrimary,
        )
        Text(
            text = "Manage collectors and data preferences",
            style = MaterialTheme.typography.bodyMedium,
            color = BlackBoxColors.TextTertiary,
            modifier = Modifier.padding(top = Dimens.SpacingXxs, bottom = Dimens.SpacingLg),
        )

        when {
            state.isLoading -> LoadingIndicator()

            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction(SettingsContract.Action.Refresh) },
            )

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                ) {
                    item {
                        SectionHeader(stringResource(Res.string.settings_collectors_title))
                        Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                    }

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
                        SectionHeader("Display")
                        Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        DataInspectionCard(
                            isRawDataViewEnabled = state.isRawDataViewEnabled,
                            onToggle = { enabled ->
                                onAction(SettingsContract.Action.RawDataViewToggled(enabled))
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                        SectionHeader("Security")
                        Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        BiometricLockCard(
                            isEnabled = state.isBiometricLockEnabled,
                            onToggle = { enabled ->
                                onAction(SettingsContract.Action.BiometricLockToggled(enabled))
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                        SectionHeader("Data Retention")
                        Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        RetentionPeriodCard(
                            retentionPeriod = state.retentionPeriod,
                            onPeriodSelected = { period ->
                                onAction(SettingsContract.Action.RetentionPeriodChanged(period))
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                        SectionHeader("Debug")
                        Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        DbDumpCard(
                            isLoading = state.isDumpLoading,
                            onDump = { onAction(SettingsContract.Action.DumpDbRecords) },
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingXl))
                    }
                }
            }
        }
    }

    // DB dump dialog
    if (state.dbDumpText != null) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsContract.Action.DismissDbDump) },
            title = {
                Text(
                    text = "DB Records",
                    style = MaterialTheme.typography.titleMedium,
                    color = BlackBoxColors.TextPrimary,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = state.dbDumpText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                        ),
                        color = BlackBoxColors.TextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsContract.Action.DismissDbDump) }) {
                    Text("Close", color = BlackBoxColors.Indigo)
                }
            },
            containerColor = BlackBoxColors.Surface,
        )
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = BlackBoxColors.TextTertiary,
        modifier = Modifier.padding(start = Dimens.SpacingXs),
    )
}

/**
 * Obsidian card displaying a single collector's settings with an indigo toggle.
 *
 * When the permission is not granted, shows an amber "Permission required" label
 * and the switch is forced off regardless of the stored preference.
 *
 * @param setting          The collector setting to display.
 * @param permissionGranted Whether the required runtime permission is granted.
 * @param onToggle         Callback when the toggle is changed.
 * @param modifier         Optional [Modifier].
 */
@Composable
private fun CollectorSettingCard(
    setting: CollectorSetting,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectivelyEnabled = setting.isEnabled && permissionGranted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(horizontal = Dimens.PaddingCard, vertical = Dimens.SpacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatCollectorName(setting.collectorType),
                style = MaterialTheme.typography.bodyLarge,
                color = if (effectivelyEnabled) BlackBoxColors.TextPrimary else BlackBoxColors.TextSecondary,
            )

            if (!permissionGranted) {
                Text(
                    text = "Permission required",
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.Warning,
                )
            } else {
                val intervalText = if (setting.collectionIntervalMs == 0L) {
                    "Event-driven"
                } else {
                    stringResource(Res.string.settings_interval, setting.collectionIntervalMs / 1000)
                }
                Text(
                    text = intervalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextTertiary,
                )
            }
        }

        Switch(
            checked = effectivelyEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BlackBoxColors.OnAccent,
                checkedTrackColor = BlackBoxColors.Indigo,
                uncheckedThumbColor = BlackBoxColors.TextTertiary,
                uncheckedTrackColor = BlackBoxColors.SurfaceVariant,
                uncheckedBorderColor = BlackBoxColors.Border,
            ),
        )
    }
}

/**
 * Card for the Display section — toggles raw collector data in the Timeline.
 *
 * @param isRawDataViewEnabled Current state of the toggle.
 * @param onToggle             Callback when the toggle is changed.
 * @param modifier             Optional [Modifier].
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
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(horizontal = Dimens.PaddingCard, vertical = Dimens.SpacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Show all collectors in Timeline",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRawDataViewEnabled) BlackBoxColors.TextPrimary else BlackBoxColors.TextSecondary,
            )
            Text(
                text = "Displays raw records from all collectors. Useful for verifying data collection.",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        }

        Switch(
            checked = isRawDataViewEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BlackBoxColors.OnAccent,
                checkedTrackColor = BlackBoxColors.Teal,
                uncheckedThumbColor = BlackBoxColors.TextTertiary,
                uncheckedTrackColor = BlackBoxColors.SurfaceVariant,
                uncheckedBorderColor = BlackBoxColors.Border,
            ),
        )
    }
}

/**
 * Card for the Security section — toggles biometric / device-credential lock on app launch.
 *
 * When enabled, the app requires fingerprint, face, or PIN authentication before showing content.
 *
 * @param isEnabled Current state of the biometric lock.
 * @param onToggle  Callback when the toggle is changed.
 * @param modifier  Optional [Modifier].
 */
@Composable
private fun BiometricLockCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(horizontal = Dimens.PaddingCard, vertical = Dimens.SpacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Biometric Lock",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEnabled) BlackBoxColors.TextPrimary else BlackBoxColors.TextSecondary,
            )
            Text(
                text = "Require fingerprint, face, or PIN to open the app.",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BlackBoxColors.OnAccent,
                checkedTrackColor = BlackBoxColors.Rose,
                uncheckedThumbColor = BlackBoxColors.TextTertiary,
                uncheckedTrackColor = BlackBoxColors.SurfaceVariant,
                uncheckedBorderColor = BlackBoxColors.Border,
            ),
        )
    }
}

/**
 * Card for the Data Retention section — lets the user choose how long raw records
 * are kept before CleanupWorker deletes them.
 *
 * @param retentionPeriod  Currently selected retention period.
 * @param onPeriodSelected Callback when the user picks a new period.
 * @param modifier         Optional [Modifier].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionPeriodCard(
    retentionPeriod: RetentionPeriod,
    onPeriodSelected: (RetentionPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
    ) {
        Text(
            text = "Raw record lifespan",
            style = MaterialTheme.typography.bodyLarge,
            color = BlackBoxColors.TextPrimary,
        )
        Text(
            text = "Older records are deleted automatically. Daily summaries are kept forever.",
            style = MaterialTheme.typography.bodySmall,
            color = BlackBoxColors.TextTertiary,
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextField(
                value = retentionPeriodLabel(retentionPeriod),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = BlackBoxColors.Indigo),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BlackBoxColors.SurfaceVariant,
                    unfocusedContainerColor = BlackBoxColors.SurfaceVariant,
                    focusedIndicatorColor = BlackBoxColors.Indigo,
                    unfocusedIndicatorColor = BlackBoxColors.Border,
                    focusedTrailingIconColor = BlackBoxColors.Indigo,
                    unfocusedTrailingIconColor = BlackBoxColors.TextTertiary,
                    focusedTextColor = BlackBoxColors.Indigo,
                    unfocusedTextColor = BlackBoxColors.Indigo,
                    cursorColor = Color.Transparent,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = BlackBoxColors.SurfaceElevated,
            ) {
                RetentionPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = retentionPeriodLabel(period),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (period == retentionPeriod) BlackBoxColors.Indigo else BlackBoxColors.TextPrimary,
                            )
                        },
                        onClick = {
                            onPeriodSelected(period)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Card for the Debug section — triggers a full DB dump formatted as a text block.
 *
 * Shows a spinner while the query is running and a "Dump" button otherwise.
 *
 * @param isLoading Whether the dump query is currently running.
 * @param onDump    Callback when the user taps the button.
 * @param modifier  Optional [Modifier].
 */
@Composable
private fun DbDumpCard(
    isLoading: Boolean,
    onDump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(horizontal = Dimens.PaddingCard, vertical = Dimens.SpacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "View DB records",
                style = MaterialTheme.typography.bodyLarge,
                color = BlackBoxColors.TextPrimary,
            )
            Text(
                text = "Last ${com.blackbox.domain.usecase.settings.DumpDbRecordsUseCase.MAX_PER_COLLECTOR} records per collector — also logged to logcat [BB_DB_DUMP]",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.IconMd),
                color = BlackBoxColors.Rose,
                strokeWidth = Dimens.ElevationSm,
            )
        } else {
            TextButton(onClick = onDump) {
                Text(
                    text = "Dump",
                    color = BlackBoxColors.Rose,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Maps a [RetentionPeriod] to a human-readable label. */
private fun retentionPeriodLabel(period: RetentionPeriod): String = when (period) {
    RetentionPeriod.THREE_MONTHS -> "3 Months"
    RetentionPeriod.SIX_MONTHS -> "6 Months"
    RetentionPeriod.ONE_YEAR -> "1 Year"
    RetentionPeriod.TWO_YEARS -> "2 Years"
    RetentionPeriod.UNLIMITED -> "Unlimited"
}

/** Formats a [CollectorType] enum into a human-readable name. */
private fun formatCollectorName(type: CollectorType): String =
    type.name.replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SettingsContentEmptyPreview() {
    BlackBoxTheme {
        SettingsContent(
            state = SettingsContract.State(collectorSettings = emptyList()),
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
                retentionPeriod = RetentionPeriod.ONE_YEAR,
            ),
            onAction = {},
        )
    }
}
