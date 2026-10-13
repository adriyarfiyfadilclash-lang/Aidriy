package com.aidriy.app.ui.ideas

import androidx.annotation.StringRes
import com.aidriy.app.R
import com.aidriy.app.domain.model.IdeaStatus

@StringRes
fun IdeaStatus.labelRes(): Int = when (this) {
    IdeaStatus.INBOX -> R.string.idea_status_inbox
    IdeaStatus.IN_PROGRESS -> R.string.idea_status_in_progress
    IdeaStatus.COMPLETED -> R.string.idea_status_completed
}
