package com.blackbox.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.search_hint
import blackbox.shared.generated.resources.search_recent
import blackbox.shared.generated.resources.search_suggestions
import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.ParsedQuery
import com.blackbox.domain.model.query.QueryIntent
import com.blackbox.domain.model.query.QueryResult
import com.blackbox.domain.model.query.TimeRange
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import com.blackbox.ui.theme.neonGlowBackground
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Search screen — cyberpunk query terminal.
 *
 * Implements a two-phase result display:
 * - Phase 1: local engine result appears immediately with an AI loading indicator.
 * - Phase 2: when AI responds, its answer replaces the answer area in a prominent
 *   NeonMagenta card; the local result moves below as a secondary reference.
 *   If AI fails, a subtle fallback banner is shown below the local result.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchContent(
    state: SearchContract.State,
    onAction: (SearchContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        // Terminal header
        Text(
            text = "BLACKBOX QUERY TERMINAL",
            style = MaterialTheme.typography.labelLarge,
            color = BlackBoxColors.NeonGreen,
            modifier = Modifier.padding(bottom = Dimens.SpacingMd),
        )

        // Search input
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(SearchContract.Action.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(Res.string.search_hint),
                    color = BlackBoxColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = BlackBoxColors.NeonGreen)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onAction(SearchContract.Action.ClearResults) }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = BlackBoxColors.TextMuted)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAction(SearchContract.Action.SubmitQuery) }),
            singleLine = true,
            shape = MaterialTheme.shapes.extraSmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BlackBoxColors.NeonGreen,
                unfocusedBorderColor = BlackBoxColors.OutlineNeon,
                focusedTextColor = BlackBoxColors.TextPrimary,
                unfocusedTextColor = BlackBoxColors.TextPrimary,
                cursorColor = BlackBoxColors.NeonGreen,
                focusedContainerColor = BlackBoxColors.SurfaceVariant,
                unfocusedContainerColor = BlackBoxColors.SurfaceVariant,
            ),
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        when {
            // ── Phase 1 spinner — local engine processing ──────────────────────
            state.isLoading -> LoadingIndicator()

            // ── Error ──────────────────────────────────────────────────────────
            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction(SearchContract.Action.SubmitQuery) },
            )

            // ── Results area ───────────────────────────────────────────────────
            state.result != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ── AI response card (Phase 2 — prominent) ─────────────────
                    AnimatedVisibility(
                        visible = state.isAiMode && state.aiResponse != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        if (state.aiResponse != null) {
                            AiResponseCard(text = state.aiResponse)
                            Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                        }
                    }

                    // ── AI loading card (between Phase 1 result and Phase 2) ───
                    AnimatedVisibility(visible = state.isAiLoading) {
                        AiLoadingCard()
                        Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                    }

                    // ── Local engine result card ───────────────────────────────
                    LocalResultCard(
                        result = state.result,
                        isSecondary = state.isAiMode,
                    )

                    // ── Fallback banner (AI failed) ────────────────────────────
                    if (!state.isAiMode && !state.isAiLoading && state.aiFallbackReason != null) {
                        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                        FallbackBanner(reason = state.aiFallbackReason)
                    }

                    // ── Suggested follow-ups ───────────────────────────────────
                    if (state.suggestedFollowUps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.SpacingLg))
                        Text(
                            text = stringResource(Res.string.search_suggestions),
                            style = MaterialTheme.typography.labelMedium,
                            color = BlackBoxColors.ElectricCyan,
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                        ) {
                            state.suggestedFollowUps.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BlackBoxColors.ElectricCyan,
                                    modifier = Modifier
                                        .neonBorder(color = BlackBoxColors.ElectricCyan, cornerRadius = 4.dp)
                                        .background(BlackBoxColors.ElectricCyanFaint)
                                        .padding(horizontal = Dimens.SpacingSm, vertical = Dimens.SpacingXs)
                                        .clickable { onAction(SearchContract.Action.SuggestionClicked(suggestion)) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Empty / recent queries ─────────────────────────────────────────
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.recentQueries.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.search_recent),
                            style = MaterialTheme.typography.labelMedium,
                            color = BlackBoxColors.NeonGreen,
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                        state.recentQueries.forEach { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAction(SearchContract.Action.RecentQueryClicked(query)) }
                                    .padding(vertical = Dimens.SpacingXs)
                                    .neonBorder(color = BlackBoxColors.OutlineNeon, cornerRadius = 2.dp)
                                    .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
                            ) {
                                Text(
                                    text = "> $query",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BlackBoxColors.TextPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        }
                    } else {
                        EmptyStateView(
                            title = "ASK BLACKBOX",
                            message = "Type a question like \"Where was I yesterday?\" or \"How many steps last week?\"",
                        )
                    }
                }
            }
        }
    }
}

// ── Private sub-composables ────────────────────────────────────────────────────

/**
 * Prominent AI answer card — NeonMagenta glow, shown after Phase 2 completes.
 */
@Composable
private fun AiResponseCard(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonGlowBackground(BlackBoxColors.NeonMagentaFaint)
            .neonBorder(color = BlackBoxColors.NeonMagenta, cornerRadius = 4.dp)
            .padding(Dimens.PaddingCard),
    ) {
        Text(
            text = "⚡  AI RESPONSE",
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.NeonMagenta,
            modifier = Modifier.padding(bottom = Dimens.SpacingXs),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = BlackBoxColors.TextPrimary,
        )
    }
}

/**
 * Pulsing AI loading indicator shown between Phase 1 result and Phase 2 arrival.
 */
@Composable
private fun AiLoadingCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(color = BlackBoxColors.NeonMagenta, cornerRadius = 4.dp)
            .padding(horizontal = Dimens.PaddingCard, vertical = Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = BlackBoxColors.NeonMagenta,
            strokeWidth = 2.dp,
        )
        Text(
            text = "⚡  AI ANALYZING...",
            style = MaterialTheme.typography.labelMedium,
            color = BlackBoxColors.NeonMagenta,
        )
    }
}

/**
 * Local engine result card.
 *
 * When [isSecondary] is true (AI answer is shown above), the card uses
 * a muted ElectricCyan border and dimmed text to visually de-emphasize it.
 */
@Composable
private fun LocalResultCard(
    result: QueryResult,
    isSecondary: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSecondary) BlackBoxColors.OutlineNeon else BlackBoxColors.NeonGreen
    val glowColor  = if (isSecondary) BlackBoxColors.ElectricCyanFaint else BlackBoxColors.NeonGreenFaint
    val labelColor = if (isSecondary) BlackBoxColors.TextMuted else BlackBoxColors.NeonGreen
    val textColor  = if (isSecondary) BlackBoxColors.TextMuted else BlackBoxColors.TextPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonGlowBackground(glowColor)
            .neonBorder(color = borderColor, cornerRadius = 4.dp)
            .padding(Dimens.PaddingCard),
    ) {
        Text(
            text = if (isSecondary) "LOCAL ENGINE" else "QUERY RESULT",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(bottom = Dimens.SpacingXs),
        )
        Text(
            text = result.responseText,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontStyle = if (isSecondary) FontStyle.Italic else FontStyle.Normal,
        )
        if (result.data.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
            Text(
                text = "${result.data.size} RECORDS FOUND",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}

/**
 * Subtle one-line banner shown when AI is unavailable and the local engine is active.
 */
@Composable
private fun FallbackBanner(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.SurfaceVariant)
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Text(
            text = "LOCAL ENGINE ACTIVE",
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
        )
        Text(
            text = "·  $reason",
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val sampleResult = QueryResult(
    parsedQuery = ParsedQuery(
        originalText = "Where was I yesterday?",
        normalizedText = "where was i yesterday",
        language = Language.ENGLISH,
        intent = QueryIntent.LOCATION_QUERY,
        timeRange = TimeRange(0L, System.currentTimeMillis()),
    ),
    responseText = "Yesterday you were at Home for 8 hours, then Office for 6 hours.",
    suggestedFollowUps = listOf("What route did I take?", "How far did I walk?"),
)

@Preview(showBackground = true)
@Composable
private fun SearchContentEmptyPreview() {
    BlackBoxTheme {
        SearchContent(state = SearchContract.State(), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentLoadingPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(query = "How many steps today?", isLoading = true),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentLocalResultPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(
                query = "Where was I yesterday?",
                result = sampleResult,
                isAiLoading = true,
                suggestedFollowUps = sampleResult.suggestedFollowUps,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentAiResponsePreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(
                query = "Where was I yesterday?",
                result = sampleResult,
                isAiMode = true,
                aiResponse = "Yesterday you spent most of your day at home — about 8 hours based on your location data. You then headed to what looks like your office around 10 AM and stayed there for roughly 6 hours before returning home in the evening.",
                suggestedFollowUps = sampleResult.suggestedFollowUps,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentFallbackPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(
                query = "Where was I yesterday?",
                result = sampleResult,
                isAiMode = false,
                aiFallbackReason = "No API key configured",
            ),
            onAction = {},
        )
    }
}
