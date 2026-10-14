package com.aidriy.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.aidriy.app.domain.model.ExtractionStatus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

data class ExtractionResult(val status: ExtractionStatus, val text: String?)

/** Pulls readable text out of stored files. Runs entirely on the device. */
class TextExtractor(private val context: Context) {

    @Volatile private var pdfBoxReady = false

    suspend fun extract(file: File, mimeType: String, name: String): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            when {
                mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> extractPdf(file)
                mimeType.startsWith("text/") || name.endsWith(".md", true) || name.endsWith(".txt", true) -> {
                    val text = file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(MAX_CHARS)
                        val read = reader.read(buffer)
                        if (read <= 0) "" else String(buffer, 0, read)
                    }
                    if (text.isBlank()) ExtractionResult(ExtractionStatus.NO_TEXT, null) else ExtractionResult(ExtractionStatus.DONE, text)
                }
                mimeType.startsWith("image/") -> ExtractionResult(ExtractionStatus.NO_TEXT, null)
                else -> ExtractionResult(ExtractionStatus.UNSUPPORTED, null)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Extraction failed: ${e.javaClass.simpleName}")
            ExtractionResult(ExtractionStatus.FAILED, null)
        } catch (e: OutOfMemoryError) {
            ExtractionResult(ExtractionStatus.FAILED, null)
        }
    }

    private fun extractPdf(file: File): ExtractionResult {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
        return try {
            PDDocument.load(file).use { document ->
                val text = PDFTextStripper().getText(document).trim()
                when {
                    text.isEmpty() -> ExtractionResult(ExtractionStatus.NO_TEXT, null)
                    else -> ExtractionResult(ExtractionStatus.DONE, text.take(MAX_CHARS))
                }
            }
        } catch (e: InvalidPasswordException) {
            ExtractionResult(ExtractionStatus.PROTECTED, null)
        }
    }

    private companion object {
        const val TAG = "TextExtractor"
        const val MAX_CHARS = 500_000
    }
}

/** On-device OCR with ML Kit (bundled Latin model: works offline, no data leaves the phone). */
class OcrService(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(uri: Uri): String {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        return process(image)
    }

    suspend fun recognize(bitmap: Bitmap): String = process(InputImage.fromBitmap(bitmap, 0))

    /** Renders each PDF page (up to [maxPages]) and runs OCR on it. For scanned documents. */
    suspend fun recognizePdf(file: File, maxPages: Int = 20, onProgress: (Int, Int) -> Unit = { _, _ -> }): String {
        val pages = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = minOf(renderer.pageCount, maxPages)
                    for (index in 0 until count) {
                        onProgress(index + 1, count)
                        val bitmap = renderer.openPage(index).use { page ->
                            val scale = 2
                            Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        val text = process(InputImage.fromBitmap(bitmap, 0))
                        bitmap.recycle()
                        if (text.isNotBlank()) pages += text
                    }
                }
            }
        }
        return pages.joinToString("\n\n")
    }

    private suspend fun process(image: InputImage): String = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWith(Result.failure(e)) }
    }
}
