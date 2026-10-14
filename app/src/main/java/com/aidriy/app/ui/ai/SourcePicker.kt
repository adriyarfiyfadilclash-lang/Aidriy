@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aidriy.app.AidriyApplication
import com.aidriy.app.R
import com.aidriy.app.domain.model.NoteFilter
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import com.aidriy.app.ui.common.formatRelative
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.flow.catch

/** Lets the user pick exactly which note or document the AI may read. */
@Composable
fun SourcePickerSheet(
    kind: SuggestionInput,
    onPicked: (SourceRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AidriyApplication).container
    var query by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(if (kind == SuggestionInput.DOCUMENT) R.string.ai_pick_document else R.string.ai_pick_note),
                style = MaterialTheme.typography.titleMedium,
            )
            if (kind == SuggestionInput.NOTE) {
                SearchField(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.notes_search_hint))
                val notesFlow = remember(query) {
                    container.noteRepository.observeNotes(NoteFilter(query = query)).catch { emit(emptyList()) }
                }
                val notes by notesFlow.collectAsState(initial = emptyList())
                if (notes.isEmpty()) {
                    Text(stringResource(R.string.notes_empty_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(notes, key = { it.id }) { note ->
                        RecordCard(
                            title = note.title,
                            preview = note.content,
                            meta = formatRelative(note.updatedAt),
                            onClick = { onPicked(SourceRef(SourceType.NOTE, note.id, note.title.ifBlank { "Catatan" })) },
                            leading = { AccentTile(Icons.Outlined.Description, AidriyColors.accentFor(note.id)) },
                            modifier = Modifier.testTag("picker_item"),
                        )
                    }
                }
            } else {
                val files by remember { container.fileRepository.observeReadableFiles().catch { emit(emptyList()) } }
                    .collectAsState(initial = emptyList())
                if (files.isEmpty()) {
                    Text(
                        stringResource(R.string.ai_no_documents),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xxl),
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(files, key = { it.id }) { file ->
                        RecordCard(
                            title = file.name,
                            preview = file.extractedText.orEmpty(),
                            meta = formatRelative(file.createdAt),
                            onClick = { onPicked(SourceRef(SourceType.FILE, file.id, file.name)) },
                            leading = { AccentTile(Icons.Outlined.Description, AidriyColors.accent(AccentKey.RED)) },
                            modifier = Modifier.testTag("picker_item"),
                        )
                    }
                }
            }
        }
    }
}
