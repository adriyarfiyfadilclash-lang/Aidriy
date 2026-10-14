package com.aidriy.app.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aidriy.app.R
import com.aidriy.app.domain.markdown.ImageSize
import com.aidriy.app.domain.markdown.Markdown
import com.aidriy.app.ui.components.MarkdownLite
import com.aidriy.app.ui.markdown.ImageBlock

/**
 * Edit-mode content: text blocks (Markdown with styled markers) and image blocks that can be
 * resized, captioned, moved and removed. The note is still stored as one Markdown string;
 * image blocks are the `![keterangan](berkas://id "ukuran")` lines.
 */
class BlockEditorState {

    sealed interface Segment {
        val key: Long

        data class Text(override val key: Long, val value: TextFieldValue) : Segment
        data class Image(override val key: Long, val target: String, val caption: String, val size: ImageSize) : Segment
    }

    val segments = mutableStateListOf<Segment>()
    var focusedKey by mutableStateOf<Long?>(null)
    var selectedImageKey by mutableStateOf<Long?>(null)

    /** Set after a structural change so the right text block takes focus. */
    var pendingFocus by mutableStateOf<Pair<Long, Int>?>(null)

    private var nextKey = 1L
    private var loaded: String? = null

    /** Rebuilds the blocks when the stored text changed from outside (load, undo, AI format). */
    fun syncFrom(markdown: String) {
        if (markdown == loaded) return
        segments.clear()
        val text = StringBuilder()
        var first = true
        fun flushText() {
            segments += Segment.Text(nextKey++, TextFieldValue(text.toString()))
            text.clear()
            first = true
        }
        markdown.split('\n').forEach { line ->
            val m = Markdown.imageLineRe.find(line)
            if (m != null) {
                flushText()
                segments += Segment.Image(nextKey++, m.groupValues[2], m.groupValues[1], ImageSize.from(m.groupValues[3]))
            } else {
                if (!first) text.append('\n')
                text.append(line)
                first = false
            }
        }
        flushText()
        loaded = markdown
        selectedImageKey = null
    }

    fun serialize(): String = segments.mapNotNull { seg ->
        when (seg) {
            is Segment.Text -> seg.value.text.takeIf { it.isNotEmpty() }
            is Segment.Image -> Markdown.imageLine(seg.target, seg.caption, seg.size)
        }
    }.joinToString("\n").also { loaded = it }

    private fun indexOf(key: Long) = segments.indexOfFirst { it.key == key }

    fun updateText(key: Long, value: TextFieldValue) {
        val i = indexOf(key)
        if (i >= 0) segments[i] = (segments[i] as Segment.Text).copy(value = value)
    }

    /** The text block the toolbar acts on: the focused one, else the last one. */
    fun activeText(): Segment.Text? =
        (focusedKey?.let { k -> segments.firstOrNull { it.key == k } } as? Segment.Text)
            ?: segments.lastOrNull { it is Segment.Text } as? Segment.Text

    fun applyToActive(transform: (TextFieldValue) -> TextFieldValue): Boolean {
        val seg = activeText() ?: return false
        val updated = transform(seg.value)
        updateText(seg.key, updated)
        pendingFocus = seg.key to updated.selection.end
        return true
    }

    /** Splits the active text block at the cursor and puts the image between the halves. */
    fun insertImage(target: String, caption: String, size: ImageSize = ImageSize.FULL) {
        val seg = activeText()
        if (seg == null) {
            segments += Segment.Image(nextKey++, target, caption, size)
            segments += Segment.Text(nextKey++, TextFieldValue(""))
            return
        }
        val i = indexOf(seg.key)
        val text = seg.value.text
        val cursor = seg.value.selection.end.coerceIn(0, text.length)
        val before = text.substring(0, cursor).trimEnd('\n')
        val after = text.substring(cursor).trimStart('\n')
        val afterKey = nextKey++
        segments[i] = seg.copy(value = TextFieldValue(before, TextRange(before.length)))
        segments.add(i + 1, Segment.Image(nextKey++, target, caption, size))
        segments.add(i + 2, Segment.Text(afterKey, TextFieldValue(after, TextRange(0))))
        pendingFocus = afterKey to 0
    }

    fun updateImage(key: Long, caption: String? = null, size: ImageSize? = null) {
        val i = indexOf(key)
        val seg = segments.getOrNull(i) as? Segment.Image ?: return
        segments[i] = seg.copy(caption = caption ?: seg.caption, size = size ?: seg.size)
    }

    /** Moves an image past the neighbouring text block. */
    fun moveImage(key: Long, up: Boolean) {
        val i = indexOf(key)
        if (i < 0) return
        val target = if (up) i - 1 else i + 1
        if (target !in segments.indices) return
        val seg = segments.removeAt(i)
        segments.add(target, seg)
        mergeAdjacentText()
    }

    fun deleteImage(key: Long) {
        val i = indexOf(key)
        if (i < 0) return
        segments.removeAt(i)
        selectedImageKey = null
        mergeAdjacentText()
    }

    private fun mergeAdjacentText() {
        var i = 0
        while (i < segments.size - 1) {
            val a = segments[i]
            val b = segments[i + 1]
            if (a is Segment.Text && b is Segment.Text) {
                val joined = listOf(a.value.text, b.value.text).filter { it.isNotEmpty() }.joinToString("\n")
                segments[i] = a.copy(value = TextFieldValue(joined, TextRange(joined.length)))
                segments.removeAt(i + 1)
            } else {
                i++
            }
        }
        if (segments.lastOrNull() !is Segment.Text) segments += Segment.Text(nextKey++, TextFieldValue(""))
        if (segments.firstOrNull() !is Segment.Text) segments.add(0, Segment.Text(nextKey++, TextFieldValue("")))
    }

    fun imageCount(): Int = segments.count { it is Segment.Image }
}

@Composable
fun BlockEditor(
    state: BlockEditorState,
    placeholder: String,
    onChanged: (String) -> Unit,
    onEditCaption: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val linkColor = MaterialTheme.colorScheme.primary
    val transformation = remember(markerColor, linkColor) { MarkdownLite.visualTransformation(markerColor, linkColor) }

    LaunchedEffect(state.pendingFocus) {
        val (key, cursor) = state.pendingFocus ?: return@LaunchedEffect
        val seg = state.segments.firstOrNull { it.key == key } as? BlockEditorState.Segment.Text
        if (seg != null) {
            state.updateText(key, seg.value.copy(selection = TextRange(cursor.coerceIn(0, seg.value.text.length))))
            runCatching { requesters[key]?.requestFocus() }
        }
        state.pendingFocus = null
    }

    Column(modifier = modifier.testTag("editor_content_blocks"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val onlyOneText = state.segments.size == 1
        state.segments.forEachIndexed { index, seg ->
            when (seg) {
                is BlockEditorState.Segment.Text -> {
                    val requester = requesters.getOrPut(seg.key) { FocusRequester() }
                    // Empty blocks between images stay small but tappable.
                    val isFirstText = state.segments.indexOfFirst { it is BlockEditorState.Segment.Text } == index
                    TextField(
                        value = seg.value,
                        onValueChange = { value ->
                            val changed = value.text != seg.value.text
                            state.updateText(seg.key, value)
                            if (changed) onChanged(state.serialize())
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = if (isFirstText && (onlyOneText || seg.value.text.isEmpty() && index == 0)) {
                            { Text(placeholder, style = MaterialTheme.typography.bodyLarge) }
                        } else {
                            null
                        },
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        minLines = if (onlyOneText) 10 else 1,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) state.focusedKey = seg.key }
                            .let { if (isFirstText) it.testTag("editor_content") else it },
                    )
                }
                is BlockEditorState.Segment.Image -> EditableImage(
                    seg = seg,
                    selected = state.selectedImageKey == seg.key,
                    onSelect = { state.selectedImageKey = if (state.selectedImageKey == seg.key) null else seg.key },
                    onResize = {
                        state.updateImage(seg.key, size = seg.size.next())
                        onChanged(state.serialize())
                    },
                    onCaption = { onEditCaption(seg.key, seg.caption) },
                    onMove = { up ->
                        state.moveImage(seg.key, up)
                        onChanged(state.serialize())
                    },
                    onDelete = {
                        state.deleteImage(seg.key)
                        onChanged(state.serialize())
                    },
                )
            }
        }
    }
}

@Composable
private fun EditableImage(
    seg: BlockEditorState.Segment.Image,
    selected: Boolean,
    onSelect: () -> Unit,
    onResize: () -> Unit,
    onCaption: () -> Unit,
    onMove: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("editor_image"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.border(2.dp, colors.primary, MaterialTheme.shapes.medium) else Modifier)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
                .padding(4.dp),
        ) {
            ImageBlock(
                target = seg.target,
                caption = seg.caption,
                widthFraction = seg.size.fraction,
                onOpenBerkas = { onSelect() },
            )
        }
        if (selected) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colors.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onResize, modifier = Modifier.testTag("image_resize")) {
                        Icon(Icons.Outlined.AspectRatio, contentDescription = stringResource(R.string.image_resize, sizeLabel(seg.size.next())))
                    }
                    IconButton(onClick = onCaption) {
                        Icon(Icons.AutoMirrored.Outlined.ShortText, contentDescription = stringResource(R.string.image_caption))
                    }
                    IconButton(onClick = { onMove(true) }) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.image_move_up))
                    }
                    IconButton(onClick = { onMove(false) }) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.image_move_down))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("image_delete")) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.image_delete), tint = colors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun sizeLabel(size: ImageSize): String = stringResource(
    when (size) {
        ImageSize.SMALL -> R.string.image_size_small
        ImageSize.MEDIUM -> R.string.image_size_medium
        ImageSize.FULL -> R.string.image_size_full
    },
)
