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
import com.riyaz.rss.common.RssBrand
import com.riyaz.rss.common.components.RssSettingRow

@Composable
fun RssSlideMenu(
    userName: String?,
    userEmail: String?,
    items: List<RssMenuItem>,
    logo: Painter? = null,
    companyLogo: Painter? = null,
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
            AnimatedVisibility(logo != null, enter = fadeIn(tween(220)) + slideInHorizontally({ -it / 5 }, tween(260))) {
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
                AnimatedVisibility(true, enter = fadeIn(tween(180, delayMillis = 70 * index)) +
                    slideInHorizontally({ -it / 8 }, tween(220, delayMillis = 70 * index))) {
                    RssSettingRow(item.icon, item.title, modifier = Modifier.fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .clickable { item.onClick() }.padding(vertical = 2.dp))
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
