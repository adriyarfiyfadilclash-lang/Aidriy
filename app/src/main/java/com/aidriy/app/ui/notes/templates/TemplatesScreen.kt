package com.aidriy.app.ui.notes.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.aidriy.app.R
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.ChipOption
import com.aidriy.app.ui.components.ChoiceChipRow
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: (String) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<TemplateCategory?>(null) }
    val visible = NoteTemplates.all.filter { category == null || it.category == category }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_templates"),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            AppHeaderBar(
                title = stringResource(R.string.templates_title),
                navigation = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        }
        item {
            ChoiceChipRow(
                options = listOf(ChipOption<TemplateCategory?>(null, stringResource(R.string.filter_all))) +
                    TemplateCategory.entries.map { ChipOption<TemplateCategory?>(it, it.label) },
                selected = category,
                onSelect = { category = it },
            )
        }
        items(visible, key = { it.id }) { template ->
            RecordCard(
                title = template.title,
                preview = template.description,
                meta = template.category.label,
                onClick = { onUseTemplate(template.id) },
                leading = { AccentTile(template.icon(), AidriyColors.accent(template.accent)) },
                modifier = Modifier.testTag("template_${template.id}"),
            )
        }
    }
}

private fun NoteTemplate.icon(): ImageVector = when (id) {
    "catatan_cepat" -> Icons.Outlined.EditNote
    "todo" -> Icons.Outlined.CheckBox
    "rangkuman" -> Icons.Outlined.MenuBook
    "meeting" -> Icons.Outlined.Groups
    "proyek" -> Icons.Outlined.Timeline
    "jurnal" -> Icons.Outlined.Book
    "rencana_belajar" -> Icons.Outlined.School
    else -> Icons.Outlined.Lightbulb
}
