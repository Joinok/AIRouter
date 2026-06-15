package com.airouter.ui.screen.provider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
            onConfirm = { name, baseUrl, apiKey, modelId, displayName, extraBodyFields ->
                viewModel.addCustomProvider(name, baseUrl, apiKey, modelId, displayName, extraBodyFields)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, modelId: String, displayName: String, extraBodyFields: Map<String, String>) -> Unit,
    viewModel: ProviderListViewModel = koinViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    // 额外参数
    var extraParams by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showExtraParams by remember { mutableStateOf(false) }
    var showAddParamDialog by remember { mutableStateOf(false) }

    // 模型拉取状态
    val fetchState by viewModel.fetchState.collectAsState()
    var showModelPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义 Provider") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
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
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
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

                // 模型 ID + 拉取按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("如：deepseek-chat") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (baseUrl.isNotBlank()) {
                                viewModel.fetchModels(baseUrl, apiKey)
                                showModelPicker = true
                            }
                        },
                        enabled = baseUrl.isNotBlank() && fetchState !is ProviderListViewModel.FetchState.Loading,
                    ) {
                        if (fetchState is ProviderListViewModel.FetchState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("拉取")
                    }
                }

                // 显示名称
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    placeholder = { Text("如：DeepSeek V3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // === 自定义可选参数 ===
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExtraParams = !showExtraParams }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "自定义请求参数",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (extraParams.isNotEmpty()) {
                        Text(
                            text = "${extraParams.size} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = if (showExtraParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showExtraParams) "收起" else "展开",
                        modifier = Modifier.size(20.dp),
                    )
                }

                AnimatedVisibility(visible = showExtraParams) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "这些参数会作为额外字段附加到 API 请求体中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        extraParams.forEach { (key, value) ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            extraParams = extraParams - key
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddParamDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加参数")
                        }
                    }
                }
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
                            displayName.ifBlank { modelId },
                            extraParams,
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

    // 模型选择弹窗
    if (showModelPicker) {
        ModelPickerDialog(
            fetchState = fetchState,
            onDismiss = {
                showModelPicker = false
                viewModel.clearFetchState()
            },
            onModelSelected = { selectedId ->
                modelId = selectedId
                if (displayName.isBlank()) {
                    displayName = selectedId
                }
                showModelPicker = false
                viewModel.clearFetchState()
            }
        )
    }

    // 添加参数对话框
    if (showAddParamDialog) {
        AddParamMiniDialog(
            existingKeys = extraParams.keys,
            onDismiss = { showAddParamDialog = false },
            onConfirm = { key, value ->
                extraParams = extraParams + (key to value)
                showAddParamDialog = false
            }
        )
    }
}

/**
 * 简易的添加参数对话框（用于添加 Provider 时使用）
 */
@Composable
private fun AddParamMiniDialog(
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    val presets = listOf("frequency_penalty", "presence_penalty", "seed", "top_k")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请求参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("参数名") },
                    placeholder = { Text("如：frequency_penalty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = key in existingKeys,
                    supportingText = { if (key in existingKeys) Text("该参数已存在") }
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("参数值") },
                    placeholder = { Text("如：0.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val availablePresets = presets.filter { it !in existingKeys }
                if (availablePresets.isNotEmpty()) {
                    Text("快捷填入：", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availablePresets.take(4).forEach { preset ->
                            AssistChip(
                                onClick = { key = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                when (key) {
                    "frequency_penalty" -> Text("频率惩罚 (-2~2)，默认 0", style = MaterialTheme.typography.labelSmall)
                    "presence_penalty" -> Text("存在惩罚 (-2~2)，默认 0", style = MaterialTheme.typography.labelSmall)
                    "seed" -> Text("随机种子，固定可复现结果", style = MaterialTheme.typography.labelSmall)
                    "top_k" -> Text("Top-K 采样参数，默认 40", style = MaterialTheme.typography.labelSmall)
                    "temperature" -> Text("控制随机性 (0-2)，默认 0.7", style = MaterialTheme.typography.labelSmall)
                    "top_p" -> Text("核采样 (0-1)，默认 1.0", style = MaterialTheme.typography.labelSmall)
                    "max_tokens" -> Text("最大输出 Token，默认 4096", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (key.isNotBlank() && value.isNotBlank()) onConfirm(key.trim(), value.trim()) },
                enabled = key.isNotBlank() && value.isNotBlank() && key !in existingKeys,
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ModelPickerDialog(
    fetchState: ProviderListViewModel.FetchState,
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; filterText = it },
                    label = { Text("搜索模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                when (fetchState) {
                    is ProviderListViewModel.FetchState.Idle,
                    is ProviderListViewModel.FetchState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                    is ProviderListViewModel.FetchState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = (fetchState as ProviderListViewModel.FetchState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    is ProviderListViewModel.FetchState.Success -> {
                        val models = (fetchState as ProviderListViewModel.FetchState.Success).models
                        val filteredModels = if (filterText.isBlank()) models
                        else models.filter { it.id.contains(filterText, ignoreCase = true) }

                        if (filteredModels.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("无匹配模型", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredModels.size) { index ->
                                    val model = filteredModels[index]
                                    Card(
                                        onClick = { onModelSelected(model.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = model.id, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                                if (model.ownedBy.isNotBlank()) {
                                                    Text(text = model.ownedBy, style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = "共 ${filteredModels.size} / ${models.size} 个模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
