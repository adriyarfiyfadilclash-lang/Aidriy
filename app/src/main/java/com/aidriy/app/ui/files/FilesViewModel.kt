package com.aidriy.app.ui.files

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.files.FileRepository
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.FileKind
import com.aidriy.app.domain.model.Folder
import com.aidriy.app.domain.model.GroupStats
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.common.SEARCH_DEBOUNCE_MS
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toUiText
import com.aidriy.app.ui.navigation.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Category cards on the Berkas tab. */
data class FilesOverview(
    val notes: GroupStats = GroupStats.EMPTY,
    val archived: GroupStats = GroupStats.EMPTY,
    val documents: GroupStats = GroupStats.EMPTY,
    val images: GroupStats = GroupStats.EMPTY,
    val audio: GroupStats = GroupStats.EMPTY,
    val video: GroupStats = GroupStats.EMPTY,
    val folders: List<Folder> = emptyList(),
)

/**
 * Shared by the Berkas tab (no folder/kind) and the folder/category screen.
 * Import copies the picked file into app storage and starts text extraction.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val files: FileRepository,
    handle: SavedStateHandle,
) : ViewModel() {

    val folderId: String? = handle.get<String>(Routes.ARG_FOLDER_ID)
    val kind: FileKind? = handle.get<String>(Routes.ARG_KIND)?.let { v -> FileKind.entries.firstOrNull { it.name == v } }

    var query by mutableStateOf("")
        private set
    var chip by mutableStateOf<FileKind?>(kind)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var folderName by mutableStateOf<String?>(null)
        private set

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = messageChannel.receiveAsFlow()

    val overview: StateFlow<FilesOverview> = combine(
        files.observeNoteStats(archived = false),
        files.observeNoteStats(archived = true),
        files.observeStats(FileKind.DOCUMENT),
        files.observeStats(FileKind.IMAGE),
        combine(files.observeStats(FileKind.AUDIO), files.observeStats(FileKind.VIDEO), files.observeFolders()) { a, v, f -> Triple(a, v, f) },
    ) { notes, archived, docs, images, rest ->
        FilesOverview(notes, archived, docs, images, rest.first, rest.second, rest.third)
    }
        .catch { emit(FilesOverview()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesOverview())

    /** Files matching the search, chip and (optional) folder. */
    val fileList: StateFlow<List<StoredFile>?> = combine(
        snapshotFlow { query }.map(SearchQuery::normalize).distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        snapshotFlow { chip },
    ) { q, k -> q to k }
        .flatMapLatest { (q, k) -> files.observeFiles(q, k, folderId) }
        .map<List<StoredFile>, List<StoredFile>?> { it }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        folderId?.let { id -> viewModelScope.launch { folderName = files.getFolderName(id) } }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun onChipChange(value: FileKind?) {
        chip = value
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            when (val result = files.import(uri, folderId, null)) {
                is DataResult.Success -> messageChannel.send(UiText(R.string.files_imported, listOf(result.value.name)))
                is DataResult.Failure -> messageChannel.send(result.error.toUiText())
            }
            isImporting = false
        }
    }

    fun createFolder(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = files.createFolder(name)) {
                is DataResult.Success -> {
                    onDone()
                    messageChannel.send(UiText(R.string.files_folder_created))
                }
                is DataResult.Failure -> messageChannel.send(result.error.toUiText())
            }
        }
    }

    fun renameFolder(name: String, onDone: () -> Unit) {
        val id = folderId ?: return
        viewModelScope.launch {
            when (val result = files.renameFolder(id, name)) {
                is DataResult.Success -> {
                    folderName = name.trim()
                    onDone()
                }
                is DataResult.Failure -> messageChannel.send(result.error.toUiText())
            }
        }
    }

    fun deleteFolder(onDone: () -> Unit) {
        val id = folderId ?: return
        viewModelScope.launch {
            when (val result = files.deleteFolder(id)) {
                is DataResult.Success -> onDone()
                is DataResult.Failure -> messageChannel.send(result.error.toUiText())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { FilesViewModel(appContainer().fileRepository, createSavedStateHandle()) }
        }
    }
}
