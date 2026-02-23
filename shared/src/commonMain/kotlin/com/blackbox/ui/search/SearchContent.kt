package com.blackbox.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
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
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Search screen.
 *
 * Renders the search bar, recent queries, results, and suggestions.
 * Receives state and emits actions — no ViewModel dependency.
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
        // Search input
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(SearchContract.Action.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onAction(SearchContract.Action.ClearResults) }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { onAction(SearchContract.Action.SubmitQuery) },
            ),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        when {
            state.isLoading -> {
                LoadingIndicator()
            }

            state.error != null -> {
                ErrorView(
                    message = state.error,
                    onRetry = { onAction(SearchContract.Action.SubmitQuery) },
                )
            }

            state.result != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Result card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(Dimens.PaddingCard)) {
                            Text(
                                text = state.result.responseText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            if (state.result.data.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                                Text(
                                    text = "${state.result.data.size} records",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Suggested follow-ups
                    if (state.suggestedFollowUps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.SpacingLg))
                        Text(
                            text = stringResource(Res.string.search_suggestions),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                        ) {
                            state.suggestedFollowUps.forEach { suggestion ->
                                AssistChip(
                                    onClick = { onAction(SearchContract.Action.SuggestionClicked(suggestion)) },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // Empty state: show recent queries
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.recentQueries.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.search_recent),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingSm))
                        state.recentQueries.forEach { query ->
                            Text(
                                text = query,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAction(SearchContract.Action.RecentQueryClicked(query)) }
                                    .padding(vertical = Dimens.SpacingSm),
                            )
                        }
                    } else {
                        EmptyStateView(
                            title = "Ask BlackBox",
                            message = "Type a question like \"Where was I yesterday?\" or \"How many steps last week?\"",
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentEmptyPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentWithResultPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(
                query = "Where was I yesterday?",
                result = QueryResult(
                    parsedQuery = ParsedQuery(
                        originalText = "Where was I yesterday?",
                        normalizedText = "where was i yesterday",
                        language = Language.ENGLISH,
                        intent = QueryIntent.LOCATION_QUERY,
                        timeRange = TimeRange(0L, System.currentTimeMillis()),
                    ),
                    responseText = "Yesterday you were at Home for 8 hours, then Office for 6 hours.",
                    suggestedFollowUps = listOf("What route did I take?", "How far did I walk?"),
                ),
                suggestedFollowUps = listOf("What route did I take?", "How far did I walk?"),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentLoadingPreview() {
    BlackBoxTheme {
        SearchContent(
            state = SearchContract.State(
                query = "How many steps today?",
                isLoading = true,
            ),
            onAction = {},
        )
    }
}
