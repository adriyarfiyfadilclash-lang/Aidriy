package com.aidriy.app.ui.insight

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aidriy.app.R
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.IconBadge
import com.aidriy.app.ui.components.ScreenHeader
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.AidriyTheme
import com.aidriy.app.ui.theme.Spacing

/**
 * Insight. Until a provider is configured (Phase 4) this shows an informative
 * "not configured" state instead of a chat UI that cannot work.
 */
@Composable
fun InsightScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(bottom = Spacing.xl)
            .testTag("screen_insight"),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_insight),
            subtitle = stringResource(R.string.insight_subtitle),
        )

        AidriyCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    IconBadge(icon = TopLevelDestination.INSIGHT.icon)
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(
                            text = stringResource(R.string.insight_setup_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.insight_setup_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.lg),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    PrivacyPoint(Icons.Outlined.Key, R.string.insight_point_key)
                    PrivacyPoint(Icons.Outlined.Lock, R.string.insight_point_consent)
                    PrivacyPoint(Icons.Outlined.Link, R.string.insight_point_sources)
                }
            }
        }

        Text(
            text = stringResource(R.string.insight_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}

@Composable
private fun PrivacyPoint(
    icon: ImageVector,
    @StringRes textRes: Int,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8FA, widthDp = 360)
@Composable
private fun InsightScreenPreview() {
    AidriyTheme { InsightScreen() }
}
