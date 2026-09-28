package com.riyaz.rss.common.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val RssIconPalette = listOf(
    Color(0xFF3F51B5),
    Color(0xFF00897B),
    Color(0xFFE67E22),
    Color(0xFF8E44AD),
    Color(0xFF2E86C1),
    Color(0xFFD35400),
    Color(0xFF16A085),
    Color(0xFFC0392B)
)

@Composable
fun RssSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    iconTint: Color? = null
) {
    val resolvedTint = iconTint ?: RssIconPalette[
        title.hashCode().ushr(1) % RssIconPalette.size
    ]

    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = resolvedTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = if (subtitle.isNullOrBlank()) title else "$title\n$subtitle",
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
