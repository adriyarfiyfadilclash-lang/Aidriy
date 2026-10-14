@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.domain.model.ExtractionStatus
import com.aidriy.app.domain.model.FileKind
import com.aidriy.app.domain.model.GroupStats
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.common.formatBytes
import com.aidriy.app.ui.common.formatRelative
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.common.rememberDocumentPicker
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.ChipOption
import com.aidriy.app.ui.components.ChoiceChipRow
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.components.SectionTitle
import com.aidriy.app.ui.theme.Accent
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing

data class FilesActions(
    val openNotes: () -> Unit,
    val openArchive: () -> Unit,
    val openCategory: (FileKind) -> Unit,
    val openFolder: (String) -> Unit,
    val openFile: (String) -> Unit,
    val back: (() -> Unit)? = null,
)

/** Berkas tab ([FilesViewModel.folderId] == null) and the folder/category screen. */
@Composable
fun FilesRoute(
    actions: FilesActions,
    viewModel: FilesViewModel = viewModel(factory = FilesViewModel.Factory),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val files by viewModel.fileList.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var folderDialog by rememberSaveable { mutableStateOf<String?>(null) } // "new" or "rename"
    var confirmDeleteFolder by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val pick = rememberDocumentPicker { viewModel.import(it) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    val isRoot = viewModel.folderId == null && viewModel.kind == null
    val title = when {
        viewModel.folderId != null -> viewModel.folderName ?: stringResource(R.string.files_folder)
        viewModel.kind != null -> stringResource(viewModel.kind.labelRes())
        else -> stringResource(R.string.nav_files)
    }
    val showList = !isRoot || viewModel.query.isNotBlank() || viewModel.chip != null

    Box(
        Modifier
            .fillMaxSize()
            .testTag(if (isRoot) "screen_berkas" else "screen_folder"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.fabClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                AppHeaderBar(
                    title = title,
                    navigation = actions.back?.let { back ->
                        @Composable {
                            IconButton(onClick = back) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        if (viewModel.folderId != null) {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.files_rename_folder)) },
                                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                        onClick = {
                                            menu = false
                                            folderDialog = "rename"
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.files_delete_folder)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            menu = false
                                            confirmDeleteFolder = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                if (isRoot) {
                    Text(
                        stringResource(R.string.files_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.sm),
                    )
                }
            }
            item {
                SearchField(
                    query = viewModel.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.files_search_hint),
                    modifier = Modifier.testTag("files_search"),
                )
            }
            if (viewModel.kind == null) {
                item {
                    ChoiceChipRow(
                        options = listOf(ChipOption<FileKind?>(null, stringResource(R.string.filter_all), "files_all")) +
                            FileKind.entries.map { ChipOption<FileKind?>(it, stringResource(it.labelRes()), "files_${it.name}") },
                        selected = viewModel.chip,
                        onSelect = viewModel::onChipChange,
                    )
                }
            }
            if (viewModel.isImporting) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (showList) {
                fileItems(files, actions.openFile)
            } else {
                categoryItems(overview, actions)
                if (files?.isNotEmpty() == true) {
                    item { SectionTitle(stringResource(R.string.files_recent)) }
                    items(files.orEmpty().take(5), key = { "recent_${it.id}" }) { file -> FileRow(file) { actions.openFile(file.id) } }
                }
            }
        }

        FloatingActionButton(
            onClick = { if (isRoot) showAddSheet = true else pick() },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
                .testTag("files_add"),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.files_add))
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.files_import)) },
                supportingContent = { Text(stringResource(R.string.files_import_sub)) },
                leadingContent = { Icon(Icons.Outlined.UploadFile, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showAddSheet = false
                        pick()
                    }
                    .testTag("files_import"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.files_new_folder)) },
                supportingContent = { Text(stringResource(R.string.files_new_folder_sub)) },
                leadingContent = { Icon(Icons.Outlined.CreateNewFolder, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showAddSheet = false
                        folderDialog = "new"
                    }
                    .padding(bottom = Spacing.xl),
            )
        }
    }
    folderDialog?.let { mode ->
        NameDialog(
            title = stringResource(if (mode == "new") R.string.files_new_folder else R.string.files_rename_folder),
            initial = if (mode == "rename") viewModel.folderName.orEmpty() else "",
            onConfirm = { name ->
                if (mode == "new") {
                    viewModel.createFolder(name) { folderDialog = null }
                } else {
                    viewModel.renameFolder(name) { folderDialog = null }
                }
            },
            onDismiss = { folderDialog = null },
        )
    }
    if (confirmDeleteFolder) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.files_delete_folder_title),
            message = stringResource(R.string.files_delete_folder_body),
            onConfirm = {
                confirmDeleteFolder = false
                viewModel.deleteFolder { actions.back?.invoke() }
            },
            onDismiss = { confirmDeleteFolder = false },
        )
    }
}

private fun LazyListScope.categoryItems(overview: FilesOverview, actions: FilesActions) {
    item {
        CategoryRow(Icons.Outlined.Folder, AccentKey.YELLOW, R.string.files_cat_notes, overview.notes, onClick = actions.openNotes)
    }
    item {
        CategoryRow(Icons.Outlined.Folder, AccentKey.BLUE, R.string.files_cat_documents, overview.documents) {
            actions.openCategory(FileKind.DOCUMENT)
        }
    }
    item {
        CategoryRow(Icons.Outlined.Image, AccentKey.TEAL, R.string.files_cat_images, overview.images) {
            actions.openCategory(FileKind.IMAGE)
        }
    }
    item {
        CategoryRow(Icons.Outlined.AudioFile, AccentKey.PURPLE, R.string.files_cat_audio, overview.audio) {
            actions.openCategory(FileKind.AUDIO)
        }
    }
    item {
        CategoryRow(Icons.Outlined.Movie, AccentKey.RED, R.string.files_cat_video, overview.video) {
            actions.openCategory(FileKind.VIDEO)
        }
    }
    item {
        CategoryRow(Icons.Outlined.Inventory2, AccentKey.ORANGE, R.string.files_cat_archive, overview.archived, onClick = actions.openArchive)
    }
    if (overview.folders.isNotEmpty()) {
        item { SectionTitle("Folder") }
        items(overview.folders, key = { it.id }) { folder ->
            CategoryRow(
                icon = Icons.Outlined.Folder,
                accent = AccentKey.YELLOW,
                label = null,
                labelText = folder.name,
                stats = GroupStats(folder.itemCount, folder.latest),
                onClick = { actions.openFolder(folder.id) },
            )
        }
    }
}

private fun LazyListScope.fileItems(files: List<StoredFile>?, onOpen: (String) -> Unit) {
    when {
        files == null -> item { LoadingState() }
        files.isEmpty() -> item {
            EmptyState(
                icon = Icons.Outlined.FolderOff,
                title = stringResource(R.string.files_empty_title),
                body = stringResource(R.string.files_empty_body),
            )
        }
        else -> items(files, key = { it.id }) { file -> FileRow(file) { onOpen(file.id) } }
    }
}

@Composable
private fun CategoryRow(
    icon: ImageVector,
    accent: AccentKey,
    label: Int?,
    stats: GroupStats,
    labelText: String? = null,
    onClick: () -> Unit,
) {
    RecordCard(
        title = labelText ?: stringResource(label ?: R.string.files_folder),
        preview = "",
        meta = if (stats.latest != null) {
            stringResource(R.string.files_item_count_date, stats.count, formatTimestamp(stats.latest))
        } else {
            stringResource(R.string.files_item_count, stats.count)
        },
        onClick = onClick,
        leading = { AccentTile(icon, AidriyColors.accent(accent)) },
    )
}

@Composable
fun FileRow(file: StoredFile, onClick: () -> Unit) {
    RecordCard(
        title = file.name,
        preview = stringResource(file.extractionStatus.labelRes(file)),
        meta = "${formatBytes(file.sizeBytes)} · ${formatRelative(file.createdAt)}",
        onClick = onClick,
        leading = { AccentTile(file.icon(), file.accent()) },
        modifier = Modifier.testTag("file_item"),
    )
}

fun StoredFile.icon(): ImageVector = when {
    isPdf -> Icons.Outlined.PictureAsPdf
    kind == FileKind.IMAGE -> Icons.Outlined.Image
    kind == FileKind.AUDIO -> Icons.Outlined.AudioFile
    kind == FileKind.VIDEO -> Icons.Outlined.Movie
    isText -> Icons.Outlined.Description
    else -> Icons.Outlined.InsertDriveFile
}

@Composable
fun StoredFile.accent(): Accent = when {
    isPdf -> AidriyColors.accent(AccentKey.RED)
    kind == FileKind.IMAGE -> AidriyColors.accent(AccentKey.TEAL)
    kind == FileKind.AUDIO -> AidriyColors.accent(AccentKey.PURPLE)
    kind == FileKind.VIDEO -> AidriyColors.accent(AccentKey.ORANGE)
    else -> AidriyColors.accent(AccentKey.BLUE)
}

fun FileKind.labelRes(): Int = when (this) {
    FileKind.DOCUMENT -> R.string.files_cat_documents
    FileKind.IMAGE -> R.string.files_cat_images
    FileKind.AUDIO -> R.string.files_cat_audio
    FileKind.VIDEO -> R.string.files_cat_video
}

fun ExtractionStatus.labelRes(file: StoredFile): Int = when (this) {
    ExtractionStatus.PENDING -> R.string.extract_pending
    ExtractionStatus.DONE -> R.string.extract_done
    ExtractionStatus.NO_TEXT -> if (file.isImage) R.string.extract_image else R.string.extract_no_text
    ExtractionStatus.PROTECTED -> R.string.extract_protected
    ExtractionStatus.UNSUPPORTED -> R.string.extract_unsupported
    ExtractionStatus.FAILED -> R.string.extract_failed
}

@Composable
fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.testTag("name_input"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
