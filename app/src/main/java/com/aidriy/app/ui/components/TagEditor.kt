@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.aidriy.app.R
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.ui.theme.Spacing

/**
 * Assign and remove tags on a record. Existing tags are offered as suggestions so they are
 * reused instead of duplicated. Validation happens in the ViewModel; [onAdd] returns true
 * when the tag was accepted (the input is then cleared).
 */
@Composable
fun TagEditor(
    tags: List<String>,
    allTags: List<Tag>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val assigned = tags.map(TagNames::normalize).toSet()
    val typed = TagNames.normalize(input)
    val suggestions = allTags
        .filter { TagNames.normalize(it.name) !in assigned }
        .filter { typed.isEmpty() || TagNames.normalize(it.name).startsWith(typed) }
        .take(8)

    fun submit(name: String) {
        if (name.isBlank()) return
        if (onAdd(name)) input = ""
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.tags_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                tags.forEach { tag ->
                    val removeLabel = stringResource(R.string.tag_remove, tag)
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = removeLabel,
                                modifier = Modifier.size(InputChipDefaults.AvatarSize),
                            )
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tag_input"),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.tag_add_hint)) },
            trailingIcon = {
                IconButton(onClick = { submit(input) }, enabled = input.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tag_add))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit(input) }),
            isError = errorMessage != null,
            shape = MaterialTheme.shapes.medium,
        )
        if (errorMessage != null) InlineError(errorMessage)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                suggestions.forEach { tag ->
                    SuggestionChip(
                        onClick = { submit(tag.name) },
                        label = { Text("#${tag.name}") },
                    )
                }
            }
        }
    }
}
