package com.cirno9half.touhoupet.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cirno9half.touhoupet.R
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toDrawable
import com.cirno9half.touhoupet.PetInfo
import com.cirno9half.touhoupet.PetManager
import com.cirno9half.touhoupet.makeStream
import kotlinx.coroutines.launch
import kotlin.io.path.Path
import kotlin.io.path.div

@Preview
@Composable
fun PetManagementScreen(
    onSettingsClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onCloseAllClick: () -> Unit = {},
    onButton1Click: () -> Unit = {},
    onButton2Click: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onPetToggle: (PetInfo, Boolean) -> Unit = { _, _ -> }
) {
    val pets = remember { PetManager }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
    ) {
        // ---- 顶部栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
//                    contentColor = MaterialTheme.colorScheme.secondaryContainer,
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.settings_24px),
                    contentDescription = "设置",
                    modifier = Modifier.size(20.dp),
//                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "🚧UI界面仍在施工中，部分按钮/开关没用，等待后续更新，点击左下角关闭应用",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(40.dp),
//                colors = IconButtonDefaults.filledIconButtonColors(
//                    contentColor = MaterialTheme.colorScheme.secondaryContainer,
//                    containerColor = MaterialTheme.colorScheme.primary
//                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.info_24px),
                    contentDescription = "信息",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // ---- 标题 ----
        Text(
            text = "桌宠列表",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )

        // ---- 滚动列表容器（浅色背景 + 边框）----
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant  // 浅色背景
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)  // 添加边框
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            ) {
                items(pets.petList.values.toList()) { pet ->
                    PetCard(
                        pet = pet,
                        onToggle = { checked -> onPetToggle(pet, checked) }
                    )
                }
            }
        }

        // ---- 底部按钮行 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onCloseAllClick,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.power_settings_new_24px),
                    contentDescription = "关闭",
                    modifier = Modifier.size(24.dp),
//                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = onButton1Click, modifier = Modifier.weight(1f)) {
                Text("按钮1")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = onButton2Click, modifier = Modifier.weight(1f)) {
                Text("按钮2")
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(onClick = onUploadClick,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.file_open_24px),
                    contentDescription = "上传",
                    modifier = Modifier.size(24.dp),
//                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun PetCard(
    pet: PetInfo,
    onToggle: (Boolean) -> Unit
) {
//    var checkedState by remember { mutableStateOf(pet.isEnabled) }
    var checkedState = true
    // 浅色卡片 + 边框，去除阴影
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        onClick = {},
        elevation = CardDefaults.cardElevation(0.dp),   // 取消阴影
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White   // 浅色背景（纯白）
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))   // 浅灰边框
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val path = Path(pet.path) / "icon.png"
            val bitmap = BitmapFactory.decodeStream(makeStream(LocalContext.current, pet.fromAsset, path.toString()))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = pet.name,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = pet.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pet.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LoadingSwitch(
                checked = checkedState,
                onCheckedChange = { newState ->
                    checkedState = newState
                    onToggle(newState)
                },
                loadAction = { newValue ->
                    // 模拟耗时操作，例如保存到服务器
                    delay(2000)
                }
            )
        }
    }
}

@Composable
fun LoadingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    loadAction: suspend (Boolean) -> Unit // 异步任务
) {
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Switch(
        checked = checked,
        onCheckedChange = { newValue ->
            if (!isLoading) { // 防止重复点击
                scope.launch {
                    isLoading = true
                    try {
                        loadAction(newValue)      // 执行耗时操作
                        onCheckedChange(newValue) // 成功才真正切换状态
                    } catch (e: Exception) {
                        // 失败不改变状态
                    } finally {
                        isLoading = false
                    }
                }
            }
        },
        // 自定义滑块内容
        thumbContent = {
            if (isLoading) {
                // 在滑块位置显示一个旋转的加载圈
                Box(
                    modifier = Modifier
//                        .size(SwitchDefaults.IconSize) // 默认滑块大小
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // 默认情况下可以不放置内容，Switch 会自动显示实心圆；
                // 如果希望完全控制，也可以画一个圆。
            }
        },
        // 可选：调整颜色以匹配加载状态
        colors = SwitchDefaults.colors(
            // 加载时可以让 thumb 和 track 变淡一些
            disabledCheckedThumbColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledCheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}