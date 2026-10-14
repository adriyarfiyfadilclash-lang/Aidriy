@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.files

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aidriy.app.AidriyApplication
import com.aidriy.app.R
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.ExtractionStatus
import com.aidriy.app.domain.model.FileKind
import com.aidriy.app.domain.model.Folder
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.common.formatBytes
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.common.openWithOtherApp
import com.aidriy.app.ui.common.shareFile
import com.aidriy.app.ui.common.toUiText
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EditorScaffold
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// Lint false positive: every producer below assigns `value` (inside collect / withContext).
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun FileViewerRoute(
    fileId: String,
    onBack: () -> Unit,
    onAskAi: (SourceRef) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AidriyApplication).container
    val repo = container.fileRepository
    val fileFlow = remember(fileId) { repo.observeFile(fileId).catch { emit(null) } }
    var loaded by remember { mutableStateOf(false) }
    val file by produceState<StoredFile?>(null, fileFlow) {
        fileFlow.collect {
            value = it
            loaded = true
        }
    }
    val folders by remember { repo.observeFolders().catch { emit(emptyList()) } }.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var showText by rememberSaveable { mutableStateOf(false) }
    var showMove by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var ocrProgress by remember { mutableStateOf<String?>(null) }

    fun message(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    EditorScaffold(
        title = file?.name ?: stringResource(R.string.files_viewer),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = Modifier.testTag("screen_file_viewer"),
        actions = {
            val f = file
            if (f != null) {
                if (f.extractionStatus == ExtractionStatus.DONE) {
                    IconButton(onClick = { showText = true }) {
                        Icon(Icons.Outlined.TextSnippet, contentDescription = stringResource(R.string.files_view_text))
                    }
                    IconButton(onClick = { onAskAi(SourceRef(SourceType.FILE, f.id, f.name)) }) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.files_ask_ai))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = {
                                menu = false
                                shareFile(context, f.localPath, f.mimeType, f.name)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_open_with)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = {
                                menu = false
                                if (!openWithOtherApp(context, f.localPath, f.mimeType)) {
                                    message(context.getString(R.string.files_no_app))
                                }
                            },
                        )
                        if ((f.isPdf || f.isImage) && f.extractionStatus != ExtractionStatus.PROTECTED) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.files_run_ocr)) },
                                leadingIcon = { Icon(Icons.Outlined.DocumentScanner, null) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        ocrProgress = context.getString(R.string.scan_working)
                                        val result = repo.runOcr(f.id) { page, total ->
                                            ocrProgress = context.getString(R.string.files_ocr_progress, page, total)
                                        }
                                        ocrProgress = null
                                        when (result) {
                                            is DataResult.Success -> message(
                                                context.getString(if (result.value.isBlank()) R.string.scan_no_text else R.string.files_ocr_done),
                                            )
                                            is DataResult.Failure -> message(result.error.toUiText().resolve(context))
                                        }
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_move)) },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                            onClick = {
                                menu = false
                                showMove = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_rename)) },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                            onClick = {
                                menu = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                menu = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        },
    ) {
        val f = file
        when {
            f == null && !loaded -> LoadingState()
            f == null -> ErrorState(stringResource(R.string.error_not_found), onRetry = null)
            else -> Column(Modifier.fillMaxSize()) {
                ocrProgress?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    )
                }
                when {
                    f.isPdf -> PdfViewer(f)
                    f.isImage -> AsyncImage(
                        model = File(f.localPath),
                        contentDescription = f.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.md),
                    )
                    f.isText -> SelectionContainer {
                        Text(
                            f.extractedText.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(Spacing.lg),
                        )
                    }
                    else -> FileInfo(f) {
                        if (!openWithOtherApp(context, f.localPath, f.mimeType)) message(context.getString(R.string.files_no_app))
                    }
                }
            }
        }
    }

    val f = file
    if (showText && f != null) {
        ModalBottomSheet(onDismissRequest = { showText = false }) {
            SelectionContainer {
                Text(
                    f.extractedText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
                )
            }
        }
    }
    if (showMove && f != null) {
        MoveSheet(
            folders = folders,
            current = f.folderId,
            onPick = { folderId ->
                showMove = false
                scope.launch {
                    val r = repo.move(f.id, folderId)
                    message(if (r is DataResult.Success) context.getString(R.string.files_moved) else context.getString(R.string.error_not_found))
                }
            },
            onDismiss = { showMove = false },
        )
    }
    if (showRename && f != null) {
        NameDialog(
            title = stringResource(R.string.files_rename),
            initial = f.name,
            onConfirm = { name ->
                scope.launch {
                    when (val r = repo.rename(f.id, name)) {
                        is DataResult.Success -> showRename = false
                        is DataResult.Failure -> message(r.error.toUiText().resolve(context))
                    }
                }
            },
            onDismiss = { showRename = false },
        )
    }
    if (confirmDelete && f != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.files_delete_title),
            message = stringResource(R.string.files_delete_body, f.name),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    if (repo.delete(f.id) is DataResult.Success) onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FileInfo(file: StoredFile, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        AccentTile(file.icon(), file.accent(), size = 72.dp, iconSize = 36.dp)
        Text(file.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "${formatBytes(file.sizeBytes)} · ${formatTimestamp(file.createdAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val hint = when (file.kind) {
            FileKind.AUDIO -> R.string.files_audio_hint
            FileKind.VIDEO -> R.string.files_video_hint
            else -> R.string.files_other_hint
        }
        Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onOpen) { Text(stringResource(R.string.files_open_with)) }
    }
}

@Composable
private fun MoveSheet(folders: List<Folder>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.files_move),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.files_no_folder)) },
            trailingContent = { if (current == null) Text("✓") },
            modifier = Modifier.clickableRow { onPick(null) },
        )
        folders.forEach { folder ->
            ListItem(
                headlineContent = { Text(folder.name) },
                trailingContent = { if (current == folder.id) Text("✓") },
                modifier = Modifier.clickableRow { onPick(folder.id) },
            )
        }
        if (folders.isEmpty()) {
            Text(
                stringResource(R.string.files_no_folders_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
        Box(Modifier.height(Spacing.xxl))
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this
    .fillMaxWidth()
    .then(Modifier.clickable(onClick = onClick))

// ---------------------------------------------------------------------------------------------- PDF

/** Renders pages on demand with the platform PdfRenderer (one page at a time, thread-safe). */
private class PdfDocument(file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, targetWidth: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                renderer.openPage(index).use { page ->
                    val width = targetWidth.coerceIn(200, 2000)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(AndroidColor.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            } catch (e: IllegalStateException) {
                null
            }
        }
    }

    fun close() {
        try {
            renderer.close()
            pfd.close()
        } catch (e: IOException) {
            // Already closed.
        } catch (e: IllegalStateException) {
            // Already closed.
        }
    }
}

// Lint false positive: every producer below assigns `value` (inside collect / withContext).
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun PdfViewer(file: StoredFile) {
    val document by produceState<Result<PdfDocument>?>(null, file.localPath) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDocument(File(file.localPath)) } }
    }
    DisposableEffect(document) {
        onDispose { document?.getOrNull()?.close() }
    }
    val doc = document
    when {
        doc == null -> LoadingState()
        doc.isFailure -> ErrorState(
            stringResource(
                if (file.extractionStatus == ExtractionStatus.PROTECTED) R.string.extract_protected else R.string.files_pdf_error,
            ),
            onRetry = null,
        )
        else -> PdfPages(doc.getOrThrow())
    }
}

// Lint false positive: every producer below assigns `value` (inside collect / withContext).
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun PdfPages(doc: PdfDocument) {
    val pagerState = rememberPagerState { doc.pageCount }
    val thumbs = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { thumbs.animateScrollToItem(pagerState.currentPage) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.md)) { index ->
                val bitmap by produceState<Bitmap?>(null, index) { value = doc.render(index, 1400) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val b = bitmap
                    if (b == null) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    } else {
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = stringResource(R.string.files_page, index + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.md),
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${doc.pageCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
        LazyRow(
            state = thumbs,
            contentPadding = PaddingValues(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(doc.pageCount) { index ->
                val thumb by produceState<Bitmap?>(null, index) { value = doc.render(index, 160) }
                val selected = index == pagerState.currentPage
                Surface(
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(width = 54.dp, height = 72.dp),
                ) {
                    thumb?.let {
                        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}
