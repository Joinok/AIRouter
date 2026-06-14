package com.airouter.ui.screen.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
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
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, baseUrl, apiKey, modelId, displayName ->
                viewModel.addCustomProvider(name, baseUrl, apiKey, modelId, displayName)
                showAddDialog = false
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
            // 状态图标
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
                        text = "已配置 · ${provider.supportedModels.size} 个模型",
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

            // 删除按钮（仅自定义 Provider）
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, modelId: String, displayName: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义 Provider") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("如：我的 DeepSeek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // API 地址
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址 / Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showApiKey) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "隐藏" else "显示"
                            )
                        }
                    }
                )

                // 模型 ID
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID") },
                    placeholder = { Text("如：deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 显示名称
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    placeholder = { Text("如：DeepSeek V3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank()) {
                        onConfirm(
                            name,
                            baseUrl,
                            apiKey,
                            modelId.ifBlank { "default" },
                            displayName.ifBlank { modelId }
                        )
                    }
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
