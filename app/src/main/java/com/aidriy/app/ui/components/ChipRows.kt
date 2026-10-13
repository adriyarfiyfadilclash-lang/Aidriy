@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.aidriy.app.R
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.ui.theme.Spacing

/** One option in a single-choice chip row. */
data class ChipOption<T>(val value: T, val label: String, val testTag: String? = null)

/**
 * Single-choice filter (scope, status). Only a handful of options, so a plain scrollable Row:
 * every chip stays composed and reachable by accessibility services.
 */
@Composable
fun <T> ChoiceChipRow(
    options: List<ChipOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                label = { Text(option.label) },
                colors = brandFilterChipColors(),
                modifier = option.testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/** Tag filter: "Semua tag" plus each tag in use. Hidden by callers when there are no tags. */
@Composable
fun TagFilterRow(
    tags: List<Tag>,
    selectedTagId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedTagId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.tags_filter_all)) },
                colors = brandFilterChipColors(),
            )
        }
        items(tags, key = { it.id }) { tag ->
            FilterChip(
                selected = tag.id == selectedTagId,
                onClick = { onSelect(if (tag.id == selectedTagId) null else tag.id) },
                label = { Text("#${tag.name}") },
                colors = brandFilterChipColors(),
            )
        }
    }
}

@Composable
private fun brandFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
