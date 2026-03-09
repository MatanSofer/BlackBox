package com.blackbox.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import com.blackbox.ui.theme.accentBorder
import com.blackbox.ui.theme.obsidianCard
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Search screen.
 *
 * Two-phase result display:
 * - Phase 1: local engine result with an AI loading chip.
 * - Phase 2: AI response in a prominent indigo card; local result moves below.
 *
 * @param state    Current UI state from the ViewModel.
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
            .padding(horizontal = Dimens.PaddingScreen),
    ) {
        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        // ── Screen title ─────────────────────────────────────────────────────
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineSmall,
            color = BlackBoxColors.TextPrimary,
        )
        Text(
            text = "Ask anything about your day",
            style = MaterialTheme.typography.bodyMedium,
            color = BlackBoxColors.TextTertiary,
            modifier = Modifier.padding(top = 2.dp, bottom = Dimens.SpacingLg),
        )

        // ── Search bar ───────────────────────────────────────────────────────
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(SearchContract.Action.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(Res.string.search_hint),
                    color = BlackBoxColors.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (state.query.isNotEmpty()) BlackBoxColors.Indigo else BlackBoxColors.TextTertiary,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onAction(SearchContract.Action.ClearResults) }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = BlackBoxColors.TextTertiary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAction(SearchContract.Action.SubmitQuery) }),
            singleLine = true,
            shape = RoundedCornerShape(Dimens.RadiusMd),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BlackBoxColors.Indigo,
                unfocusedBorderColor = BlackBoxColors.Border,
                focusedTextColor = BlackBoxColors.TextPrimary,
                unfocusedTextColor = BlackBoxColors.TextPrimary,
                cursorColor = BlackBoxColors.Indigo,
                focusedContainerColor = BlackBoxColors.SurfaceVariant,
                unfocusedContainerColor = BlackBoxColors.SurfaceVariant,
            ),
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        when {
            state.isLoading -> LoadingIndicator()

            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction(SearchContract.Action.SubmitQuery) },
            )

            state.result != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                ) {
                    // AI response (Phase 2)
                    AnimatedVisibility(
                        visible = state.isAiMode && state.aiResponse != null,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut(),
                    ) {
                        if (state.aiResponse != null) {
                            AiResponseCard(text = state.aiResponse)
                        }
                    }

                    // AI loading chip
                    AnimatedVisibility(visible = state.isAiLoading) {
                        AiLoadingChip()
                    }

                    // Local result
                    LocalResultCard(
                        result = state.result,
                        isSecondary = state.isAiMode,
                    )

                    // Fallback banner
                    if (!state.isAiMode && !state.isAiLoading && state.aiFallbackReason != null) {
                        FallbackBanner(reason = state.aiFallbackReason)
                    }

                    // Suggested follow-ups
                    if (state.suggestedFollowUps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        Text(
                            text = stringResource(Res.string.search_suggestions),
                            style = MaterialTheme.typography.labelMedium,
                            color = BlackBoxColors.TextTertiary,
                            modifier = Modifier.padding(bottom = Dimens.SpacingXs),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                        ) {
                            state.suggestedFollowUps.forEach { suggestion ->
                                SuggestionChip(
                                    text = suggestion,
                                    onClick = { onAction(SearchContract.Action.SuggestionClicked(suggestion)) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.SpacingXl))
                }
            }

            else -> {
                // Recent queries / empty state
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.recentQueries.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.search_recent),
                            style = MaterialTheme.typography.labelMedium,
                            color = BlackBoxColors.TextTertiary,
                            modifier = Modifier.padding(bottom = Dimens.SpacingSm),
                        )
                        state.recentQueries.forEach { query ->
                            RecentQueryRow(
                                query = query,
                                onClick = { onAction(SearchContract.Action.RecentQueryClicked(query)) },
                            )
                            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
                        }
                    } else {
                        EmptyStateView(
                            title = "Ask BlackBox",
                            message = "Try \"Where was I yesterday?\" or \"How many steps last week?\"",
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun AiResponseCard(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .accentBorder(color = BlackBoxColors.Indigo, cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = Dimens.SpacingXs),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BlackBoxColors.Indigo),
            )
            Spacer(modifier = Modifier.width(Dimens.SpacingXs))
            Text(
                text = "AI Answer",
                style = MaterialTheme.typography.labelMedium,
                color = BlackBoxColors.IndigoLight,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = BlackBoxColors.TextPrimary,
        )
    }
}

@Composable
private fun AiLoadingChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(BlackBoxColors.IndigoDim, RoundedCornerShape(Dimens.RadiusFull))
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = BlackBoxColors.Indigo,
            strokeWidth = 1.5.dp,
        )
        Text(
            text = "AI is thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.IndigoLight,
        )
    }
}

@Composable
private fun LocalResultCard(
    result: QueryResult,
    isSecondary: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
    ) {
        Text(
            text = if (isSecondary) "Local engine" else "Result",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSecondary) BlackBoxColors.TextTertiary else BlackBoxColors.Teal,
            modifier = Modifier.padding(bottom = Dimens.SpacingXs),
        )
        Text(
            text = result.responseText,
            style = if (isSecondary) {
                MaterialTheme.typography.bodyMedium.copy(color = BlackBoxColors.TextSecondary)
            } else {
                MaterialTheme.typography.bodyLarge.copy(color = BlackBoxColors.TextPrimary)
            },
        )
        if (result.data.isNotEmpty()) {
            Text(
                text = "${result.data.size} records found",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.TextTertiary,
                modifier = Modifier.padding(top = Dimens.SpacingXs),
            )
        }
    }
}

@Composable
private fun FallbackBanner(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.SurfaceVariant, RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Offline mode",
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
        )
        Text(
            text = "·  $reason",
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecentQueryRow(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusSm)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = BlackBoxColors.TextTertiary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = BlackBoxColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = BlackBoxColors.IndigoLight,
        modifier = modifier
            .background(BlackBoxColors.IndigoDim, RoundedCornerShape(Dimens.RadiusFull))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
    )
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
    BlackBoxTheme { SearchContent(state = SearchContract.State(), onAction = {}) }
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
                aiResponse = "Yesterday you spent most of your day at home — about 8 hours based on your location data. You then headed to your office around 10 AM and stayed for roughly 6 hours before returning home in the evening.",
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
