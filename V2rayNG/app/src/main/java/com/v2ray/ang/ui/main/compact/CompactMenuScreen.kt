package com.v2ray.ang.ui.main.compact

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.compose.chordHalfWidth
import com.v2ray.ang.ui.main.MainAction

private const val MENU_VERTICAL_PADDING_DP = 28f

private data class CompactMenuEntry(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val onSelect: () -> Unit,
)

/**
 * Full-screen menu for compact round screens.
 *
 * A bottom sheet would land in the narrowest part of a circular display, so the
 * menu takes the whole screen instead. Only the entries that matter for basic use
 * are listed; everything else stays reachable through Settings.
 */
@Composable
fun CompactMenuScreen(
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val rowWidth = remember(screenWidthDp) {
        val radius = screenWidthDp / 2f
        (2f * chordHalfWidth(radius, radius - MENU_VERTICAL_PADDING_DP)).dp
    }

    val entries = listOf(
        CompactMenuEntry(R.drawable.ic_copy, R.string.menu_item_import_config_clipboard) {
            onAction(MainAction.ImportClipboard)
            onClose()
        },
        CompactMenuEntry(R.drawable.ic_cloud_download_24dp, R.string.title_sub_update) {
            onAction(MainAction.UpdateSubscriptions)
            onClose()
        },
        CompactMenuEntry(R.drawable.ic_settings_24dp, R.string.title_settings) {
            onNavigate("settings")
            onClose()
        },
        CompactMenuEntry(R.drawable.ic_logcat_24dp, R.string.title_logcat) {
            onNavigate("logcat")
            onClose()
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = MENU_VERTICAL_PADDING_DP.dp),
    ) {
        items(items = entries, key = { it.label }) { entry ->
            MenuRow(entry = entry, width = rowWidth)
        }
    }
}

@Composable
private fun MenuRow(
    entry: CompactMenuEntry,
    width: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .width(width)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = entry.onSelect)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(entry.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
