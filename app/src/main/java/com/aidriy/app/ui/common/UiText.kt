package com.aidriy.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aidriy.app.R
import com.aidriy.app.data.backup.BackupProblem
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.TagProblem
import com.aidriy.app.domain.model.AiError

/** Text produced by ViewModels without needing a Context; resolved in Compose. */
data class UiText(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
) {
    @Composable
    fun asString(): String = stringResource(resId, *args.toTypedArray())

    /** For non-composable callers such as snackbars launched from effects. */
    fun resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
}

/** Every repository error maps to an understandable Bahasa Indonesia message. */
fun DataError.toUiText(): UiText = when (this) {
    DataError.EmptyNote -> UiText(R.string.error_empty_note)
    DataError.EmptyIdeaTitle -> UiText(R.string.error_empty_idea)
    DataError.EmptyJournal -> UiText(R.string.error_empty_journal)
    is DataError.TitleTooLong -> UiText(R.string.error_title_too_long, listOf(max))
    is DataError.ContentTooLong -> UiText(R.string.error_content_too_long, listOf(max))
    DataError.FutureJournalDate -> UiText(R.string.error_future_date)
    is DataError.DuplicateJournalDate -> UiText(R.string.error_duplicate_journal)
    is DataError.InvalidTag -> problem.toUiText()
    DataError.InvalidIdeaStatus -> UiText(R.string.error_invalid_status)
    DataError.NotFound -> UiText(R.string.error_not_found)
    is DataError.FileTooLarge -> UiText(R.string.error_file_too_large, listOf((maxBytes / (1024 * 1024)).toInt()))
    DataError.FileUnreadable -> UiText(R.string.error_file_unreadable)
    DataError.OcrFailed -> UiText(R.string.error_ocr_failed)
    DataError.EmptyName -> UiText(R.string.error_empty_name)
    DataError.DuplicateName -> UiText(R.string.error_duplicate_name)
    is DataError.Storage -> UiText(R.string.error_storage)
}

fun TagProblem.toUiText(): UiText = when (this) {
    TagProblem.EMPTY -> UiText(R.string.error_tag_empty)
    TagProblem.TOO_LONG -> UiText(R.string.error_tag_too_long, listOf(TagNames.MAX_LENGTH))
    TagProblem.TOO_MANY -> UiText(R.string.error_tag_too_many, listOf(TagNames.MAX_PER_RECORD))
}

fun AiError.toUiText(): UiText = when (this) {
    AiError.MissingKey -> UiText(R.string.ai_error_missing_key)
    AiError.InvalidKey -> UiText(R.string.ai_error_invalid_key)
    AiError.RateLimited -> UiText(R.string.ai_error_rate_limited)
    is AiError.ModelNotFound -> UiText(R.string.ai_error_model, listOf(model))
    AiError.Offline -> UiText(R.string.ai_error_offline)
    AiError.Timeout -> UiText(R.string.ai_error_timeout)
    is AiError.Server -> UiText(R.string.ai_error_server, listOf(code))
    AiError.BadResponse -> UiText(R.string.ai_error_bad_response)
    is AiError.Rejected -> if (detail.isBlank()) {
        UiText(R.string.ai_error_rejected, listOf(code))
    } else {
        UiText(R.string.ai_error_rejected_detail, listOf(code, detail))
    }
}

fun BackupProblem.toUiText(): UiText = when (this) {
    BackupProblem.NOT_A_BACKUP -> UiText(R.string.backup_error_not_backup)
    BackupProblem.NEWER_VERSION -> UiText(R.string.backup_error_newer)
    BackupProblem.DAMAGED -> UiText(R.string.backup_error_damaged)
    BackupProblem.IO -> UiText(R.string.backup_error_io)
}

/** Shown when observing local data fails. */
val LoadErrorText = UiText(R.string.error_load)
