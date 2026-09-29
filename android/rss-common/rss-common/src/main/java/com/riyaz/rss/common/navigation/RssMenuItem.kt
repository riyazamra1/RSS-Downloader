package com.riyaz.rss.common.navigation

import androidx.compose.ui.graphics.vector.ImageVector

data class RssMenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
