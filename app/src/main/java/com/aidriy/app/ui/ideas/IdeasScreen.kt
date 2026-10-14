package com.aidriy.app.ui.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.ui.common.ListUiState
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.ChipOption
import com.aidriy.app.ui.components.ChoiceChipRow
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.InlineError
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.components.SortMenu
import com.aidriy.app.ui.components.TagFilterRow
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.Spacing

@Composable
fun IdeasRoute(
    onBack: () -> Unit,
    onOpenIdea: (String) -> Unit,
    viewModel: IdeasViewModel = viewModel(factory = IdeasViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val options by viewModel.listOptions.collectAsStateWithLifecycle()
    val tags by viewModel.availableTags.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    IdeasScreen(
        onBack = onBack,
        uiState = uiState,
        query = viewModel.query,
        captureText = viewModel.captureText,
        captureError = viewModel.captureError,
        isCapturing = viewModel.isCapturing,
        options = options,
        tags = tags,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onCaptureTextChange = viewModel::onCaptureTextChange,
        onCapture = viewModel::capture,
        onStatusChange = viewModel::setStatus,
        onSortChange = viewModel::setSort,
        onTagChange = viewModel::setTag,
        onRetry = viewModel::retry,
        onOpenIdea = onOpenIdea,
    )
}

@Composable
fun IdeasScreen(
    onBack: () -> Unit,
    uiState: ListUiState<Idea>,
    query: String,
    captureText: String,
    captureError: UiText?,
    isCapturing: Boolean,
    options: IdeaListOptions,
    tags: List<Tag>,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onCaptureTextChange: (String) -> Unit,
    onCapture: () -> Unit,
    onStatusChange: (IdeaStatus?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onTagChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onOpenIdea: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_ideas"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                AppHeaderBar(
                title = stringResource(R.string.nav_ideas),
                navigation = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
            }
            item {
                QuickCaptureCard(
                    text = captureText,
                    error = captureError,
                    isCapturing = isCapturing,
                    onTextChange = onCaptureTextChange,
                    onCapture = onCapture,
                )
            }
            item {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.ideas_search_hint),
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .testTag("ideas_search"),
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChipRow(
                        options = listOf(
                            ChipOption<IdeaStatus?>(null, stringResource(R.string.ideas_status_all), "status_all"),
                        ) + IdeaStatus.entries.map { status ->
                            ChipOption<IdeaStatus?>(status, stringResource(status.labelRes()), "status_${status.storageKey}")
                        },
                        selected = options.status,
                        onSelect = onStatusChange,
                        modifier = Modifier.weight(1f),
                    )
                    SortMenu(sort = options.sort, onSortChange = onSortChange)
                }
            }
            if (tags.isNotEmpty()) {
                item { TagFilterRow(tags = tags, selectedTagId = options.tagId, onSelect = onTagChange) }
            }

            when (uiState) {
                ListUiState.Loading -> item { LoadingState() }
                is ListUiState.Error -> item { ErrorState(uiState.message.asString(), onRetry) }
                is ListUiState.Empty -> item {
                    if (uiState.isFiltered) {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.no_results_title),
                            body = stringResource(R.string.no_results_body),
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Lightbulb,
                            title = stringResource(R.string.ideas_empty_title),
                            body = stringResource(R.string.ideas_empty_body),
                        )
                    }
                }
                is ListUiState.Success -> items(uiState.items, key = { it.id }) { idea ->
                    RecordCard(
                        title = idea.title,
                        preview = idea.content,
                        meta = if (options.sort == SortOrder.CREATED) {
                            stringResource(R.string.meta_created, formatTimestamp(idea.createdAt))
                        } else {
                            stringResource(R.string.meta_updated, formatTimestamp(idea.updatedAt))
                        },
                        badge = stringResource(idea.status.labelRes()),
                        tags = idea.tags,
                        onClick = { onOpenIdea(idea.id) },
                        modifier = Modifier.testTag("idea_item"),
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Minimal-interaction capture: type one line, press Enter or the send button. */
@Composable
private fun QuickCaptureCard(
    text: String,
    error: UiText?,
    isCapturing: Boolean,
    onTextChange: (String) -> Unit,
    onCapture: () -> Unit,
) {
    AidriyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("idea_capture_input"),
                placeholder = { Text(stringResource(R.string.ideas_capture_hint)) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onCapture() }),
                trailingIcon = {
                    IconButton(
                        onClick = onCapture,
                        enabled = text.isNotBlank() && !isCapturing,
                        modifier = Modifier.testTag("idea_capture_send"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.ideas_capture_action),
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium,
            )
            if (error != null) InlineError(error.asString())
        }
    }
}
