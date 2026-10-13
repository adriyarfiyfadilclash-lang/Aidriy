package com.aidriy.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aidriy.app.R
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.ui.theme.Spacing

@Composable
fun SortMenu(
    sort: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.SwapVert, contentDescription = null)
            Text(
                text = stringResource(
                    if (sort == SortOrder.UPDATED) R.string.sort_updated_short else R.string.sort_created_short,
                ),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_updated)) },
                onClick = {
                    onSortChange(SortOrder.UPDATED)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_created)) },
                onClick = {
                    onSortChange(SortOrder.CREATED)
                    expanded = false
                },
            )
        }
    }
}
