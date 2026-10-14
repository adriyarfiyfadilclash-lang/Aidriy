package com.aidriy.app.ui.markdown

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

/**
 * Renders note Markdown. Tasks are tappable when [onToggleTask] is set (it receives the source
 * line). Links open in the browser; `berkas://` links and images open the Berkas viewer.
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    onToggleTask: ((Int) -> Unit)? = null,
    onOpenBerkas: (String) -> Unit = {},
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val uriHandler = LocalUriHandler.current
    val onLink: (String) -> Unit = { url ->
        val berkas = Markdown.berkasId(url)
        when {
            berkas != null -> onOpenBerkas(berkas)
            url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:") ->
                runCatching { uriHandler.openUri(url) }
        }
    }
    Column(modifier = modifier.testTag("markdown_view"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> BlockView(block, onToggleTask, onLink, onOpenBerkas) }
    }
}

@Composable
private fun BlockView(
    block: MdBlock,
    onToggleTask: ((Int) -> Unit)?,
    onLink: (String) -> Unit,
    onOpenBerkas: (String) -> Unit,
) {
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
            onLink = onLink,
            modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
        )
        is MdBlock.Paragraph -> InlineText(block.text, type.bodyLarge, onLink)
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
            InlineText(block.text, type.bodyLarge.copy(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant), onLink)
        }
        is MdBlock.Callout -> CalloutView(block, onLink)
        is MdBlock.Code -> Surface(
            color = colors.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (block.language.isNotBlank()) {
                    Text(block.language, style = type.labelSmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    block.code,
                    style = type.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
        MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
        is MdBlock.Table -> TableView(block, onLink)
        is MdBlock.Image -> ImageBlock(block.target, block.caption, block.size.fraction, onOpenBerkas)
    }
}

@Composable
private fun ListItemView(item: MdBlock.ListItem, onToggleTask: ((Int) -> Unit)?, onLink: (String) -> Unit) {
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
        val style = if (item.kind == ListKind.TASK && item.checked) {
            type.bodyLarge.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
        } else {
            type.bodyLarge
        }
        InlineText(item.text, style, onLink)
    }
}

@Composable
private fun CalloutView(block: MdBlock.Callout, onLink: (String) -> Unit) {
    val (accentKey, icon, label) = when (block.kind) {
        CalloutKind.INFO -> Triple(AccentKey.BLUE, Icons.Outlined.Info, R.string.callout_info)
        CalloutKind.TIP -> Triple(AccentKey.GREEN, Icons.Outlined.Lightbulb, R.string.callout_tip)
        CalloutKind.IMPORTANT -> Triple(AccentKey.PURPLE, Icons.Outlined.PriorityHigh, R.string.callout_important)
        CalloutKind.WARNING -> Triple(AccentKey.ORANGE, Icons.Outlined.WarningAmber, R.string.callout_warning)
    }
    val accent = AidriyColors.accent(accentKey)
    Surface(
        color = accent.container,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.content.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    block.title.ifBlank { stringResource(label) },
                    style = MaterialTheme.typography.titleSmall,
                    color = accent.content,
                )
            }
            if (block.text.isNotBlank()) {
                InlineText(block.text, MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), onLink)
            }
        }
    }
}

@Composable
private fun TableView(block: MdBlock.Table, onLink: (String) -> Unit) {
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
private fun InlineText(text: String, style: TextStyle, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val annotated = remember(text, colors.primary, colors.surfaceContainerHigh) {
        inlineAnnotated(text, colors.primary, colors.surfaceContainerHigh, onLink)
    }
    Text(annotated, style = style, modifier = modifier)
}

private val HighlightColor = Color(0x66FFD54F)

fun inlineAnnotated(text: String, linkColor: Color, codeBackground: Color, onLink: (String) -> Unit): AnnotatedString =
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
            if (link != null) {
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
