@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aidriy.app.R
import com.aidriy.app.domain.markdown.CalloutKind
import com.aidriy.app.domain.markdown.FormatOptions
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.markdown.MarkdownView
import com.aidriy.app.ui.markdown.calloutStyle
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import java.io.File

// ------------------------------------------------------------------ shared rows

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

@Composable
private fun OptionRow(icon: ImageVector, accent: AccentKey, title: String, subtitle: String?, tag: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 4.dp)
            .let { if (tag != null) it.testTag(tag) else it },
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            AccentTile(icon, AidriyColors.accent(accent), size = 40.dp, iconSize = 22.dp)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GridItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, tag: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
            .let { if (tag != null) it.testTag(tag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------ Sisipkan Gambar

@Composable
fun InsertImageSheet(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onScan: () -> Unit,
    onBerkas: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = Spacing.xl).testTag("sheet_image")) {
            SheetTitle(stringResource(R.string.insert_image_title))
            OptionRow(Icons.Outlined.Image, AccentKey.BLUE, stringResource(R.string.insert_image_gallery), stringResource(R.string.insert_image_gallery_sub), "image_gallery", onGallery)
            OptionRow(Icons.Outlined.PhotoCamera, AccentKey.TEAL, stringResource(R.string.insert_image_camera), stringResource(R.string.insert_image_camera_sub), "image_camera", onCamera)
            OptionRow(Icons.Outlined.DocumentScanner, AccentKey.PURPLE, stringResource(R.string.insert_image_scan), stringResource(R.string.insert_image_scan_sub), "image_scan", onScan)
            OptionRow(Icons.Outlined.FolderOpen, AccentKey.ORANGE, stringResource(R.string.insert_image_berkas), stringResource(R.string.insert_image_berkas_sub), "image_berkas", onBerkas)
        }
    }
}

@Composable
fun BerkasImagePickerSheet(images: List<StoredFile>, onPick: (StoredFile) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.insert_image_berkas))
        if (images.isEmpty()) {
            Text(
                stringResource(R.string.insert_image_berkas_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(images, key = { it.id }) { file ->
                    AsyncImage(
                        model = File(file.localPath),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onPick(file) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

// ------------------------------------------------------------------ Lainnya

enum class MoreElement { HEADING, NUMBERED, BULLET, CHECKLIST, TABLE, QUOTE, DIVIDER, LINK, CODE, CALLOUT, DATETIME, EMOJI, DICTATE, TAGS, NOTE_LINK, TOC }

@Composable
fun MoreElementsSheet(onPick: (MoreElement) -> Unit, onDismiss: () -> Unit) {
    val items = listOf(
        Triple(MoreElement.HEADING, Icons.Outlined.Title, R.string.more_heading to R.string.more_heading_sub),
        Triple(MoreElement.DIVIDER, Icons.Outlined.HorizontalRule, R.string.more_divider to R.string.more_divider_sub),
        Triple(MoreElement.NUMBERED, Icons.Outlined.FormatListNumbered, R.string.fmt_numbers to R.string.more_numbered_sub),
        Triple(MoreElement.LINK, Icons.Outlined.Link, R.string.fmt_link to R.string.more_link_sub),
        Triple(MoreElement.BULLET, Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.fmt_bullets to R.string.more_bullet_sub),
        Triple(MoreElement.CODE, Icons.Outlined.Code, R.string.more_code to R.string.more_code_sub),
        Triple(MoreElement.CHECKLIST, Icons.Outlined.CheckBox, R.string.fmt_checklist to R.string.more_checklist_sub),
        Triple(MoreElement.CALLOUT, Icons.Outlined.Info, R.string.more_callout to R.string.more_callout_sub),
        Triple(MoreElement.TABLE, Icons.Outlined.TableChart, R.string.more_table to R.string.more_table_sub),
        Triple(MoreElement.DATETIME, Icons.Outlined.CalendarMonth, R.string.more_datetime to R.string.more_datetime_sub),
        Triple(MoreElement.QUOTE, Icons.Outlined.FormatQuote, R.string.more_quote to R.string.more_quote_sub),
        Triple(MoreElement.EMOJI, Icons.Outlined.EmojiEmotions, R.string.more_emoji to R.string.more_emoji_sub),
        Triple(MoreElement.DICTATE, Icons.Outlined.Mic, R.string.fmt_dictate to R.string.more_dictate_sub),
        Triple(MoreElement.TAGS, Icons.Outlined.Sell, R.string.tags_label to R.string.more_tags_sub),
        Triple(MoreElement.NOTE_LINK, Icons.Outlined.AddLink, R.string.more_note_link to R.string.more_note_link_sub),
        Triple(MoreElement.TOC, Icons.AutoMirrored.Outlined.Toc, R.string.md_toc to R.string.more_toc_sub),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl).testTag("sheet_more")) {
            SheetTitle(stringResource(R.string.more_title))
            items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                    row.forEach { (element, icon, labels) ->
                        Box(Modifier.weight(1f)) {
                            GridItem(icon, stringResource(labels.first), stringResource(labels.second), { onPick(element) }, "more_${element.name.lowercase()}")
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Teks

enum class TextStyleChoice { H1, H2, H3, PARAGRAPH, BOLD, ITALIC, UNDERLINE, STRIKE, HIGHLIGHT, INLINE_CODE }

@Composable
fun TextStyleSheet(onPick: (TextStyleChoice) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = Spacing.xl)) {
            SheetTitle(stringResource(R.string.text_style_title))
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    TextStyleChoice.H1 to "H1", TextStyleChoice.H2 to "H2", TextStyleChoice.H3 to "H3",
                ).forEach { (choice, label) ->
                    OutlinedButton(onClick = { onPick(choice) }, modifier = Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = { onPick(TextStyleChoice.PARAGRAPH) }, modifier = Modifier.weight(1.4f)) {
                    Text(stringResource(R.string.text_style_paragraph), maxLines = 1)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            listOf(
                Triple(TextStyleChoice.BOLD, Icons.Outlined.FormatBold, R.string.fmt_bold),
                Triple(TextStyleChoice.ITALIC, Icons.Outlined.FormatItalic, R.string.fmt_italic),
                Triple(TextStyleChoice.UNDERLINE, Icons.Outlined.FormatUnderlined, R.string.fmt_underline),
                Triple(TextStyleChoice.STRIKE, Icons.Outlined.FormatStrikethrough, R.string.fmt_strike),
                Triple(TextStyleChoice.HIGHLIGHT, Icons.Outlined.TextFields, R.string.fmt_highlight),
                Triple(TextStyleChoice.INLINE_CODE, Icons.Outlined.Code, R.string.fmt_inline_code),
            ).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                    row.forEach { (choice, icon, label) ->
                        Box(Modifier.weight(1f)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(choice) }
                                    .padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(icon, contentDescription = null)
                                Spacer(Modifier.width(Spacing.sm))
                                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small dialogs

@Composable
fun TableDialog(onInsert: (rows: Int, cols: Int) -> Unit, onDismiss: () -> Unit) {
    var rows by rememberSaveable { mutableIntStateOf(3) }
    var cols by rememberSaveable { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_table)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Stepper(stringResource(R.string.table_rows), rows, 1..20) { rows = it }
                Stepper(stringResource(R.string.table_cols), cols, 1..6) { cols = it }
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(rows, cols) }) { Text(stringResource(R.string.action_insert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−", fontSize = 20.sp) }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
fun CalloutDialog(onPick: (CalloutKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_callout)) },
        text = {
            Column {
                CalloutKind.entries.map { k -> calloutStyle(k).let { Triple(k, it.icon, it.label) } }
                    .forEach { (kind, icon, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(kind) }
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null)
                        Spacer(Modifier.width(Spacing.md))
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val EMOJI = listOf(
    "😀", "😊", "😂", "🥰", "😎", "🤔", "😴", "😢", "😡", "👍", "👎", "👏",
    "🙏", "💪", "🎉", "🔥", "✨", "⭐", "❤️", "💡", "📌", "📎", "📝", "📚",
    "✅", "❌", "⚠️", "❓", "❗", "⏰", "📅", "🗓️", "🏠", "💼", "💰", "🛒",
    "☕", "🍽️", "🏃", "🎯", "🚀", "🌱", "🌙", "☀️", "🌧️", "📷", "🎵", "💬",
)

@Composable
fun EmojiSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.more_emoji))
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            items(EMOJI) { e ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clickable { onPick(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 24.sp) }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
fun CaptionDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_caption)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace("\n", " ").take(120) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.image_caption_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ------------------------------------------------------------------ Rapikan Format (AI)

@Composable
fun FormatSheet(
    info: FormatterInfo?,
    state: FormatState,
    onPreview: (FormatOptions) -> Unit,
    onOpenAiSettings: () -> Unit,
    onCancelWork: () -> Unit,
    onDismiss: () -> Unit,
) {
    var spacing by rememberSaveable { mutableStateOf(true) }
    var headings by rememberSaveable { mutableStateOf(true) }
    var indentation by rememberSaveable { mutableStateOf(true) }
    var emphasis by rememberSaveable { mutableStateOf(false) }
    var rich by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (state != FormatState.Working) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .testTag("sheet_format"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.format_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(R.string.format_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.format_promise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.md))

            when {
                info == null || !info.configured -> {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(stringResource(R.string.format_needs_key), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(Spacing.sm))
                            Button(onClick = onOpenAiSettings) { Text(stringResource(R.string.ai_setup_action)) }
                        }
                    }
                }
                state == FormatState.Working -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Text(stringResource(R.string.format_working, info.providerName), modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancelWork) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
                else -> {
                    Text(stringResource(R.string.format_options), style = MaterialTheme.typography.titleSmall)
                    CheckRow(stringResource(R.string.format_opt_spacing), spacing) { spacing = it }
                    CheckRow(stringResource(R.string.format_opt_headings), headings) { headings = it }
                    CheckRow(stringResource(R.string.format_opt_indent), indentation) { indentation = it }
                    CheckRow(stringResource(R.string.format_opt_emphasis), emphasis) { emphasis = it }
                    CheckRow(stringResource(R.string.format_opt_rich), rich) { rich = it }
                    CheckRow(stringResource(R.string.format_opt_keep_blocks), checked = true, enabled = false) {}
                    if (state is FormatState.Failed) {
                        Text(
                            state.message.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }
                    Text(
                        stringResource(R.string.format_privacy, info.providerName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                    Button(
                        onClick = { onPreview(FormatOptions(spacing, headings, indentation, emphasis, rich)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("format_preview"),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.format_make_preview))
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Before/after preview. Applying needs a second confirmation (image 9 of the design). */
@Composable
fun FormatPreviewDialog(
    preview: FormatState.Preview,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("format_preview_screen"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Text(
                    stringResource(R.string.format_preview_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(Spacing.lg),
                )
                SecondaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.format_before)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.format_after)) }, modifier = Modifier.testTag("tab_after"))
                }
                val warning = preview.changedWords
                if (warning != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(stringResource(R.string.format_changed_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            val details = buildList {
                                if (warning.removed.isNotEmpty()) add(stringResource(R.string.format_changed_removed, warning.removed.joinToString(", ")))
                                if (warning.added.isNotEmpty()) add(stringResource(R.string.format_changed_added, warning.added.joinToString(", ")))
                                if (warning.removed.isEmpty() && warning.added.isEmpty()) add(stringResource(R.string.format_changed_tasks))
                            }
                            details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                ) {
                    MarkdownView(if (tab == 0) preview.before else preview.after)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { confirm = true },
                        colors = if (warning != null) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("format_apply"),
                    ) {
                        Text(stringResource(if (warning != null) R.string.format_apply_anyway else R.string.format_apply))
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.format_confirm_title)) },
            text = {
                Text(
                    stringResource(if (preview.changedWords != null) R.string.format_confirm_body_changed else R.string.format_confirm_body),
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    onApply()
                }, modifier = Modifier.testTag("format_confirm")) { Text(stringResource(R.string.format_apply)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
