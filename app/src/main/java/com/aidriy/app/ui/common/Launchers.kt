package com.aidriy.app.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.aidriy.app.AidriyApplication
import java.io.File

/**
 * Speech-to-text through the system recognizer (no microphone permission needed in this app).
 * Returns a function that starts dictation; [onUnavailable] runs if the device has no recognizer.
 */
@Composable
fun rememberSpeechToText(onResult: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) currentOnResult.value(text)
        }
    }
    return remember(launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara sekarang…")
            try {
                context.suppressLock()
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                onUnavailable()
            }
        }
    }
}

/** Photo picker (no storage permission needed). */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) current.value(uri)
    }
    return remember(launcher) {
        {
            context.suppressLock()
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** Any document via the Storage Access Framework. */
@Composable
fun rememberDocumentPicker(mimeTypes: Array<String> = arrayOf("*/*"), onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) current.value(uri)
    }
    return remember(launcher) {
        {
            context.suppressLock()
            launcher.launch(mimeTypes)
        }
    }
}

/**
 * Camera capture into the app cache through FileProvider. The system camera app takes the photo,
 * so this app needs no camera permission.
 */
@Composable
fun rememberCameraCapture(onCaptured: (Uri) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val current = rememberUpdatedState(onCaptured)
    val pending = remember { arrayOfNulls<Uri>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending[0]
        if (ok && uri != null) current.value(uri)
    }
    return remember(launcher) {
        {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pending[0] = uri
            try {
                context.suppressLock()
                launcher.launch(uri)
            } catch (e: ActivityNotFoundException) {
                onUnavailable()
            }
        }
    }
}

/** Opens a stored file in another app (PDF reader, music player…). Returns false if none can. */
fun openWithOtherApp(context: Context, path: String, mimeType: String): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.suppressLock()
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Shares a stored file through the system share sheet. */
fun shareFile(context: Context, path: String, mimeType: String, name: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.suppressLock()
    context.startActivity(Intent.createChooser(intent, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.suppressLock()
    context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Pickers and other apps briefly put AIDRIY in the background; that should not lock it. */
fun Context.suppressLock() {
    (applicationContext as? AidriyApplication)?.container?.appLock?.suppressNextLock()
}
