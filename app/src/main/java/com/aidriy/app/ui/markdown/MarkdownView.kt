package com.aidriy.app.ui.markdown

import android.widget.Toast
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.aidriy.app.domain.markdown.CodeHighlighter
import com.aidriy.app.domain.markdown.CodeTokenKind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aidriy.app.R
import com.aidriy.app.domain.markdown.CalloutKind
import com.aidriy.app.domain.markdown.ColumnAlign
import com.aidriy.app.domain.markdown.ListKind
import com.aidriy.app.domain.markdown.Markdown
import com.aidriy.app.domain.markdown.MarkdownInline
import com.aidriy.app.domain.markdown.MdBlock
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import java.io.File

/** Looks up a Berkas file for `berkas://` images and links. Provided by the screen. */
val LocalBerkasLookup = staticCompositionLocalOf<suspend (String) -> StoredFile?> { { null } }

@Composable
fun ProvideBerkasLookup(lookup: suspend (String) -> StoredFile?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBerkasLookup provides lookup, content = content)
}

/** How links behave inside one rendered Markdown text. */
private class LinkHandler(val canOpen: (String) -> Boolean, val open: (String) -> Unit)

/**
 * Renders note Markdown. Tasks are tappable when [onToggleTask] is set (it receives the source
 * line). Links open in the browser; `berkas://` links and images open the Berkas viewer;
 * [[Judul]] links open that note when [onOpenNote] is set (otherwise they are shown as text).
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    onToggleTask: ((Int) -> Unit)? = null,
    onOpenBerkas: (String) -> Unit = {},
    onOpenNote: ((String) -> Unit)? = null,
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val uriHandler = LocalUriHandler.current
    val links = LinkHandler(
        canOpen = { url -> Markdown.noteTitle(url) == null || onOpenNote != null },
        open = { url ->
            val berkas = Markdown.berkasId(url)
            val note = Markdown.noteTitle(url)
            when {
                berkas != null -> onOpenBerkas(berkas)
                note != null -> onOpenNote?.invoke(note)
                url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:") ->
                    runCatching { uriHandler.openUri(url) }
            }
        },
    )
    Column(modifier = modifier.testTag("markdown_view"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> BlockView(block, onToggleTask, links, onOpenBerkas, depth = 0) }
    }
}

/** Quotes and callouts hold Markdown themselves; nesting deeper than this is shown as text. */
private const val MAX_DEPTH = 4

@Composable
private fun NestedBlocks(text: String, links: LinkHandler, onOpenBerkas: (String) -> Unit, depth: Int, style: TextStyle) {
    if (depth >= MAX_DEPTH) {
        InlineText(text, style, links)
        return
    }
    val blocks = remember(text) { Markdown.parse(text) }
    CompositionLocalProvider(LocalBodyStyle provides style) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tasks inside quotes are not toggled: their source lines are relative to the quote.
            blocks.forEach { BlockView(it, null, links, onOpenBerkas, depth + 1) }
        }
    }
}

/** Body text style; quotes and callouts provide their own for nested blocks. */
private val LocalBodyStyle = staticCompositionLocalOf<TextStyle?> { null }

@Composable
private fun BlockView(
    block: MdBlock,
    onToggleTask: ((Int) -> Unit)?,
    links: LinkHandler,
    onOpenBerkas: (String) -> Unit,
    depth: Int,
) {
    val onLink = links
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    when (block) {
        is MdBlock.Heading -> InlineText(
            block.text,
            style = when (block.level) {
                1 -> type.headlineSmall.copy(fontWeight = FontWeight.Bold)
                2 -> type.titleLarge.copy(fontWeight = FontWeight.Bold)
                3 -> type.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                else -> type.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            },
            links = onLink,
            modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
        )
        is MdBlock.Paragraph -> InlineText(block.text, LocalBodyStyle.current ?: type.bodyLarge, onLink)
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item -> ListItemView(item, onToggleTask, onLink) }
        }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(colors.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(12.dp))
            NestedBlocks(
                block.text, onLink, onOpenBerkas, depth,
                type.bodyLarge.copy(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant),
            )
        }
        is MdBlock.Callout -> CalloutView(block, onLink, onOpenBerkas, depth)
        is MdBlock.Code -> CodeBlockView(block)
        MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
        is MdBlock.Table -> TableView(block, onLink)
        is MdBlock.Image -> ImageBlock(block.target, block.caption, block.size.fraction, onOpenBerkas)
        is MdBlock.Toc -> TocView(block, onLink)
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.Code) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dark = colors.surface.luminance() < 0.5f
    val highlighted = remember(block, dark) { highlightCode(block, CodePalette.of(dark)) }
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("md_code"),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.language.ifBlank { stringResource(R.string.md_code) },
                    style = type.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(block.code))
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp).testTag("md_code_copy"),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.md_copy_code),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                highlighted,
                style = type.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** Token colours that read well on light and dark code backgrounds. */
private data class CodePalette(val keyword: Color, val string: Color, val comment: Color, val number: Color, val type: Color) {
    companion object {
        fun of(dark: Boolean) = if (dark) {
            CodePalette(Color(0xFFC792EA), Color(0xFFC3E88D), Color(0xFF8A93A6), Color(0xFFF78C6C), Color(0xFF82AAFF))
        } else {
            CodePalette(Color(0xFF7B2FBE), Color(0xFF2E7D32), Color(0xFF6B7280), Color(0xFFC2410C), Color(0xFF1D4ED8))
        }
    }
}

private fun highlightCode(block: MdBlock.Code, p: CodePalette): AnnotatedString = buildAnnotatedString {
    append(block.code)
    CodeHighlighter.highlight(block.language, block.code).forEach { span ->
        val style = when (span.kind) {
            CodeTokenKind.KEYWORD -> SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold)
            CodeTokenKind.STRING -> SpanStyle(color = p.string)
            CodeTokenKind.COMMENT -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
            CodeTokenKind.NUMBER -> SpanStyle(color = p.number)
            CodeTokenKind.TYPE -> SpanStyle(color = p.type)
        }
        addStyle(style, span.start, span.end)
    }
}

@Composable
private fun TocView(block: MdBlock.Toc, links: LinkHandler) {
    if (block.headings.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("md_toc"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.md_toc), style = MaterialTheme.typography.titleSmall, color = colors.primary)
            val top = block.headings.minOf { it.level }
            block.headings.forEach { h ->
                Row(Modifier.padding(start = ((h.level - top) * 16).dp)) {
                    Text("–", color = colors.onSurfaceVariant, modifier = Modifier.width(14.dp))
                    InlineText(h.text, MaterialTheme.typography.bodyMedium, links)
                }
            }
        }
    }
}

@Composable
private fun ListItemView(item: MdBlock.ListItem, onToggleTask: ((Int) -> Unit)?, onLink: LinkHandler) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = (item.depth * 20).dp),
        verticalAlignment = if (item.kind == ListKind.TASK) Alignment.CenterVertically else Alignment.Top,
    ) {
        when (item.kind) {
            ListKind.BULLET -> Text(
                if (item.depth == 0) "•" else if (item.depth == 1) "◦" else "▪",
                style = type.bodyLarge,
                color = colors.primary,
                modifier = Modifier.width(20.dp),
            )
            ListKind.ORDERED -> Text(
                "${item.number}.",
                style = type.bodyLarge,
                color = colors.primary,
                modifier = Modifier.widthIn(min = 24.dp).padding(end = 6.dp),
            )
            ListKind.TASK -> Checkbox(
                checked = item.checked,
                onCheckedChange = if (onToggleTask != null) { _: Boolean -> onToggleTask(item.line) } else null,
                colors = CheckboxDefaults.colors(checkedColor = colors.primary),
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 4.dp)
                    .testTag("task_${item.line}"),
            )
        }
        val body = LocalBodyStyle.current ?: type.bodyLarge
        val style = if (item.kind == ListKind.TASK && item.checked) {
            body.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
        } else {
            body
        }
        InlineText(item.text, style, onLink)
    }
}

/** Colour, icon and default title of a callout kind (also used by the editor's picker). */
data class CalloutStyle(val accent: AccentKey, val icon: ImageVector, val label: Int)

fun calloutStyle(kind: CalloutKind): CalloutStyle = when (kind) {
    CalloutKind.INFO -> CalloutStyle(AccentKey.BLUE, Icons.Outlined.Info, R.string.callout_info)
    CalloutKind.TIP -> CalloutStyle(AccentKey.GREEN, Icons.Outlined.Lightbulb, R.string.callout_tip)
    CalloutKind.IMPORTANT -> CalloutStyle(AccentKey.PURPLE, Icons.Outlined.PriorityHigh, R.string.callout_important)
    CalloutKind.WARNING -> CalloutStyle(AccentKey.ORANGE, Icons.Outlined.WarningAmber, R.string.callout_warning)
    CalloutKind.DANGER -> CalloutStyle(AccentKey.RED, Icons.Outlined.Dangerous, R.string.callout_danger)
    CalloutKind.SUCCESS -> CalloutStyle(AccentKey.TEAL, Icons.Outlined.CheckCircle, R.string.callout_success)
    CalloutKind.QUESTION -> CalloutStyle(AccentKey.YELLOW, Icons.Outlined.Quiz, R.string.callout_question)
    CalloutKind.EXAMPLE -> CalloutStyle(AccentKey.BLUE, Icons.Outlined.Category, R.string.callout_example)
}

@Composable
private fun CalloutView(block: MdBlock.Callout, links: LinkHandler, onOpenBerkas: (String) -> Unit, depth: Int) {
    val look = calloutStyle(block.kind)
    val accent = AidriyColors.accent(look.accent)
    var open by rememberSaveable(block.title, block.text) { mutableStateOf(!block.collapsed) }
    Surface(
        color = accent.container,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.content.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().testTag("md_callout"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (block.foldable) Modifier.fillMaxWidth().clickable { open = !open } else Modifier,
            ) {
                Icon(look.icon, contentDescription = null, tint = accent.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    block.title.ifBlank { stringResource(look.label) },
                    style = MaterialTheme.typography.titleSmall,
                    color = accent.content,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (block.foldable) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(if (open) R.string.md_fold else R.string.md_unfold),
                        tint = accent.content,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (open && block.text.isNotBlank()) {
                NestedBlocks(
                    block.text, links, onOpenBerkas, depth,
                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

@Composable
private fun TableView(block: MdBlock.Table, onLink: LinkHandler) {
    val colors = MaterialTheme.colorScheme
    val columns = block.header.size
    // Width per column from its longest cell, so short tables stay compact.
    val widths = remember(block) {
        (0 until columns).map { c ->
            val longest = (listOf(block.header[c]) + block.rows.map { it[c] }).maxOf { MarkdownInline.plain(it).length }
            (longest * 8 + 24).coerceIn(72, 240).dp
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small),
        ) {
            listOf(block.header).plus(block.rows).forEachIndexed { r, row ->
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .background(if (r == 0) colors.surfaceContainerHigh else Color.Transparent),
                ) {
                    row.forEachIndexed { c, cell ->
                        Box(
                            Modifier
                                .width(widths[c])
                                .fillMaxHeight()
                                .border(0.5.dp, colors.outlineVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            InlineText(
                                cell,
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = when (block.align[c]) {
                                        ColumnAlign.START -> TextAlign.Start
                                        ColumnAlign.CENTER -> TextAlign.Center
                                        ColumnAlign.END -> TextAlign.End
                                    },
                                ),
                                onLink,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** An image block: Berkas images load from app storage; other addresses are not fetched. */
@Composable
fun ImageBlock(
    target: String,
    caption: String,
    widthFraction: Float,
    onOpenBerkas: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = Markdown.berkasId(target)
    val lookup = LocalBerkasLookup.current
    var file by remember(id) { mutableStateOf<StoredFile?>(null) }
    LaunchedEffect(id) { file = id?.let { lookup(it) } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = MaterialTheme.shapes.medium
        val f = file
        if (f != null) {
            AsyncImage(
                model = File(f.localPath),
                contentDescription = caption.ifBlank { f.name },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .heightIn(max = 480.dp)
                    .clip(shape)
                    .clickable { onOpenBerkas(f.id) },
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(120.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(if (id == null) R.string.md_image_external else R.string.md_image_missing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        if (caption.isNotBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun InlineText(text: String, style: TextStyle, links: LinkHandler, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val annotated = remember(text, colors.primary, colors.surfaceContainerHigh, links) {
        inlineAnnotated(text, colors.primary, colors.surfaceContainerHigh, links.canOpen, links.open)
    }
    Text(annotated, style = style, modifier = modifier)
}

private val HighlightColor = Color(0x66FFD54F)

fun inlineAnnotated(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    canOpen: (String) -> Boolean = { true },
    onLink: (String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        MarkdownInline.parse(text).forEach { run ->
            val style = SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = when {
                    run.underline && run.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    run.underline -> TextDecoration.Underline
                    run.strike -> TextDecoration.LineThrough
                    else -> null
                },
                fontFamily = if (run.code) FontFamily.Monospace else null,
                background = when {
                    run.code -> codeBackground
                    run.highlight -> HighlightColor
                    else -> Color.Unspecified
                },
            )
            val link = run.link
            if (link != null && !canOpen(link)) {
                // e.g. [[Judul]] in an AI answer: shown as emphasised text, nothing to tap.
                withStyle(style.merge(SpanStyle(color = linkColor))) { append(run.text) }
            } else if (link != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = link,
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { onLink(link) },
                    ),
                ) { withStyle(style) { append(run.text) } }
            } else {
                withStyle(style) { append(run.text) }
            }
        }
    }
