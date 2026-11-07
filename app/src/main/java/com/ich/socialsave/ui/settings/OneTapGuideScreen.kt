package com.ich.socialsave.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ich.socialsave.R
import com.ich.socialsave.share.SaveLinkTileService
import com.ich.socialsave.ui.components.PrimaryButton
import com.ich.socialsave.ui.theme.Brand

/** How to save a copied link without opening the app: Quick Settings tile and the launcher shortcut. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneTapGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Brand.colors
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.guide_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = c.canvas, titleContentColor = c.ink, navigationIconContentColor = c.ink,
            ),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.guide_headline), color = c.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.guide_subtitle), color = c.gray, fontSize = 16.sp)

            Card(Icons.Filled.GridView, stringResource(R.string.guide_tile_title)) {
                Step(1, stringResource(R.string.guide_tile_step1))
                Step(2, stringResource(R.string.guide_tile_step2))
                Step(3, stringResource(R.string.guide_tile_step3))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton(stringResource(R.string.guide_tile_add), onClick = {
                        val manager = context.getSystemService(StatusBarManager::class.java)
                        manager?.requestAddTileService(
                            ComponentName(context, SaveLinkTileService::class.java),
                            context.getString(R.string.tile_label),
                            AndroidIcon.createWithResource(context, R.drawable.ic_bookmark),
                            context.mainExecutor,
                        ) {}
                    })
                }
            }

            Card(Icons.Filled.Apps, stringResource(R.string.guide_shortcut_title)) {
                Text(stringResource(R.string.guide_shortcut_body), color = c.gray, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Card(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val c = Brand.colors
    val shape = RoundedCornerShape(Brand.cardRadius)
    Column(
        Modifier.fillMaxWidth().background(c.surface, shape).border(1.dp, c.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val c = Brand.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).background(c.ink, CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = c.canvas, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = c.ink, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
