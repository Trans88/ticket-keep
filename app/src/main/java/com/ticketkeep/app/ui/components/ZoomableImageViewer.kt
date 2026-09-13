package com.ticketkeep.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

/**
 * 详情/编辑页顶部长图预览：整图 Fit + 纵向滚动，点击进入全屏缩放。
 * 列表缩略图请继续用 Crop，不要用本组件。
 */
@Composable
fun TallScrollableImage(
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxHeight: androidx.compose.ui.unit.Dp = 200.dp,
    corner: androidx.compose.ui.unit.Dp = 12.dp,
) {
    var showViewer by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(scroll)
            .clickable { showViewer = true },
    ) {
        AsyncImage(
            model = File(imagePath),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
    if (showViewer) {
        ZoomableImageViewer(
            imagePath = imagePath,
            contentDescription = contentDescription,
            onDismiss = { showViewer = false },
        )
    }
}

/** 全屏薄荷纸感看图：捏合缩放 + 拖动，返回关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoomableImageViewer(
    imagePath: String,
    contentDescription: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("图片预览") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }
        }
    }
}
