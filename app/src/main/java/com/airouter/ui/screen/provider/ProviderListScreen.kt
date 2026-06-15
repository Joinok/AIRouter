package com.airouter.ui.screen.provider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderListScreen(
    onNavigateToEdit: (String) -> Unit,
    onNavigateToLocalModel: () -> Unit = {},
    viewModel: ProviderListViewModel = koinViewModel(),
) {
    val providers by viewModel.providers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<Provider?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加自定义 Provider"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "配置 AI 模型的 API Key 后即可使用。大部分模型支持 OpenAI 兼容协议，填入 Key 就能用。\n点击右上角「+」可添加自定义 OpenAI 兼容端点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    onClick = {
                        if (provider.type == ProviderType.LOCAL) {
                            onNavigateToLocalModel()
                        } else {
                            onNavigateToEdit(provider.id)
                        }
                    },
                    onDelete = if (provider.isCustom) {
                        { providerToDelete = provider }
                    } else null
                )
            }
        }
    }

    // 添加自定义 Provider 对话框
    if (showAddDialog) {
        val existingNames = providers.filter { !it.isCustom }.map { it.name }.toSet()
        ProviderEditDialog(
            title = "添加自定义 Provider",
            existingNames = existingNames,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, baseUrl, apiKey, modelId, displayName, extraParams ->
                viewModel.addCustomProvider(name, baseUrl, apiKey, modelId, displayName, extraParams)
                showAddDialog = false
            },
            onFetchModels = { url, key, callback ->
                viewModel.fetchModelsWithCallback(url, key, callback)
            }
        )
    }

    // 删除确认对话框
    if (providerToDelete != null) {
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text("删除 Provider") },
            text = { Text("确定要删除「${providerToDelete?.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        providerToDelete?.let { viewModel.deleteCustomProvider(it.id) }
                        providerToDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (provider.isConfigured) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (provider.isConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name + if (provider.isCustom) " (自定义)" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (provider.isConfigured) {
                    Text(
                        text = "已配置 · ${provider.supportedModels.size} 个模型" +
                            if (provider.extraBodyFields.isNotEmpty()) " · ${provider.extraBodyFields.size} 个自定义参数" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "未配置 · 点击添加 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
