package com.airouter.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airouter.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val streamEnabled by viewModel.streamEnabled.collectAsState()
    val showTokenUsage by viewModel.showTokenUsage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 关于
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AiRouter", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "v1.0.0 · 多模型 AI 对话测试客户端",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "一个 App 聚合所有主流 AI 模型，方便对比测试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 对话设置
            Text("对话", style = MaterialTheme.typography.titleMedium)
            Card {
                Column {
                    SwitchSettingItem(
                        title = "流式输出",
                        subtitle = "实时显示 AI 回复，关闭则等待完整回复",
                        checked = streamEnabled,
                        onCheckedChange = { viewModel.setStreamEnabled(it) }
                    )
                    HorizontalDivider()
                    SwitchSettingItem(
                        title = "显示 Token 用量",
                        subtitle = "在消息下方显示 Token 消耗统计",
                        checked = showTokenUsage,
                        onCheckedChange = { viewModel.setShowTokenUsage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
