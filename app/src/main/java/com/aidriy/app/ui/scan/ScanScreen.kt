package com.aidriy.app.ui.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.files.OcrService
import com.aidriy.app.di.appContainer
import com.aidriy.app.ui.common.rememberCameraCapture
import com.aidriy.app.ui.common.rememberImagePicker
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.EditorScaffold
import com.aidriy.app.ui.components.InlineError
import com.aidriy.app.ui.components.QuickActionCard
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** OCR runs on the device with ML Kit; the photo is not uploaded anywhere. */
class ScanViewModel(private val ocr: OcrService) : ViewModel() {
    var text by mutableStateOf("")
    var isWorking by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var noTextFound by mutableStateOf(false)
        private set

    fun recognize(uri: Uri) {
        viewModelScope.launch {
            isWorking = true
            failed = false
            noTextFound = false
            try {
                val result = ocr.recognize(uri).trim()
                if (result.isEmpty()) noTextFound = true else text = if (text.isBlank()) result else "$text\n\n$result"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                isWorking = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(appContainer().ocrService) }
        }
    }
}

@Composable
fun ScanRoute(
    onBack: () -> Unit,
    onSaveAsNote: (String) -> Unit,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }
    val capture = rememberCameraCapture(onCaptured = viewModel::recognize, onUnavailable = { cameraUnavailable = true })
    val pick = rememberImagePicker(viewModel::recognize)

    EditorScaffold(
        title = stringResource(R.string.scan_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = Modifier.testTag("screen_scan"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.scan_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                QuickActionCard(
                    Icons.Outlined.PhotoCamera, AidriyColors.accent(AccentKey.BLUE),
                    stringResource(R.string.scan_camera), stringResource(R.string.scan_camera_sub),
                    capture, Modifier.weight(1f),
                )
                QuickActionCard(
                    Icons.Outlined.PhotoLibrary, AidriyColors.accent(AccentKey.PURPLE),
                    stringResource(R.string.scan_gallery), stringResource(R.string.scan_gallery_sub),
                    pick, Modifier.weight(1f),
                )
            }
            if (cameraUnavailable) InlineError(stringResource(R.string.scan_camera_unavailable))
            if (viewModel.isWorking) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.scan_working), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (viewModel.failed) InlineError(stringResource(R.string.error_ocr_failed))
            if (viewModel.noTextFound) InlineError(stringResource(R.string.scan_no_text))
            if (viewModel.text.isNotEmpty() || !viewModel.isWorking) {
                OutlinedTextField(
                    value = viewModel.text,
                    onValueChange = { viewModel.text = it },
                    label = { Text(stringResource(R.string.scan_result_label)) },
                    placeholder = { Text(stringResource(R.string.scan_result_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .testTag("scan_text"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(
                    onClick = {
                        copy(context, viewModel.text)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.copied)) }
                    },
                    enabled = viewModel.text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.sm))
                    Text(stringResource(R.string.action_copy))
                }
                Button(
                    onClick = { onSaveAsNote(viewModel.text) },
                    enabled = viewModel.text.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_save"),
                ) { Text(stringResource(R.string.scan_save_note)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AccentTile(Icons.Outlined.PhotoLibrary, AidriyColors.accent(AccentKey.GREEN), size = 28.dp, iconSize = 16.dp)
                Text(
                    stringResource(R.string.scan_privacy),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("NotesByAdriy", text))
}
