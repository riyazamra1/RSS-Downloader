package com.riyaz.rss.common.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Icon
import com.riyaz.rss.common.RssBrand
import com.riyaz.rss.common.components.RssSettingRow

@Composable
fun RssSlideMenu(
    userName: String?,
    userEmail: String?,
    items: List<RssMenuItem>,
    logo: Painter? = null,
    companyLogo: Painter? = null,
    selectedTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    ModalDrawerSheet(
        modifier = modifier.fillMaxHeight().padding(vertical = 10.dp, horizontal = 8.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape),
        drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = shape,
        tonalElevation = 8.dp
    ) {
        Column(Modifier.fillMaxHeight().fillMaxWidth().padding(vertical = 18.dp, horizontal = 10.dp)) {
            AnimatedVisibility(logo != null, enter = fadeIn(tween(220)) + slideInHorizontally(initialOffsetX = { -it / 5 }, animationSpec = tween(260))) {
                if (logo != null) {
                    Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Image(logo, "RSS app logo", Modifier.size(64.dp))
                    }
                }
            }
            Text("Welcome" + (userName?.let { ", $it" } ?: ""), Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            if (!userEmail.isNullOrBlank()) Text(userEmail, Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            Text(RssBrand.SHORT_NAME, Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            items.forEachIndexed { index, item ->
                val selected = item.title == selectedTitle
                val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(), label = "menuScale")
                val tint by animateColorAsState(
                    when (item.title) {
                        "Social Downloader" -> Color(0xFF42A5F5)
                        "Audio Downloader" -> Color(0xFFAB47BC)
                        "Image Downloader" -> Color(0xFF26A69A)
                        "Movies" -> Color(0xFFFF7043)
                        "Settings" -> Color(0xFFFFCA28)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }, label = "menuIconColor"
                )
                AnimatedVisibility(true, enter = fadeIn(tween(180, delayMillis = 70 * index)) +
                    slideInHorizontally(initialOffsetX = { -it / 8 }, animationSpec = tween(220, delayMillis = 70 * index))) {
                    Row(
                        Modifier.fillMaxWidth().scale(scale)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(if (selected) tint.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { item.onClick() }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                        Text(item.title, modifier = Modifier.padding(start = 14.dp), fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (companyLogo != null) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(companyLogo, "Razeen Secure Solution logo", Modifier.size(46.dp))
                    Text(RssBrand.COMPANY_NAME, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
