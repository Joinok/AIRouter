package com.airouter.ui.screen.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.airouter.data.model.AiModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    onBack: () -> Unit,
    viewModel: ProviderEditViewModel = koinViewModel(),
) {
    val provider by viewModel.provider.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val customBaseUrl by viewModel.customBaseUrl.collectAsState()
    val showKey by viewModel.showApiKey.collectAsState()
    val isValidating by viewModel.isValidating.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val models by viewModel.models.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()
    val fetchModelsError by viewModel.fetchModelsError.collectAsState()
    val extraBodyFields by viewModel.extraBodyFields.collectAsState()

    // 保存成功后返回
    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    if (provider == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val p = provider!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = apiKey.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 说明卡片
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "配置说明",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (p.type) {
                            com.airouter.data.model.ProviderType.OPENAI_COMPATIBLE ->
                                "支持 OpenAI 兼容协议，填入 API Key 即可使用。\n默认接口：${p.defaultBaseUrl}"
                            else ->
                                "请填写对应的 API Key 和接口地址。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // API Key 输入
            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key") },
                placeholder = { Text("sk-xxx...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleShowApiKey() }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "隐藏" else "显示"
                        )
                    }
                },
                supportingText = {
                    if (validationResult != null) {
                        Text(
                            text = if (validationResult == true) "✓ API Key 有效" else "✗ 验证失败",
                            color = if (validationResult == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            )

            // 验证按钮
            OutlinedButton(
                onClick = { viewModel.validateApiKey() },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() && !isValidating
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isValidating) "验证中..." else "验证 API Key")
            }

            // Base URL
            OutlinedTextField(
                value = customBaseUrl,
                onValueChange = { viewModel.updateCustomBaseUrl(it) },
                label = { Text("Base URL") },
                placeholder = { Text(p.defaultBaseUrl) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("留空使用默认：${p.defaultBaseUrl}")
                }
            )

            // === 自定义可选参数 ===
            HorizontalDivider()
            ExtraParamsSection(
                params = extraBodyFields,
                onUpsert = { key, value -> viewModel.upsertExtraParam(key, value) },
                onRemove = { key -> viewModel.removeExtraParam(key) },
            )

            // 支持的模型列表
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "支持的模型",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = { viewModel.fetchModels() },
                    enabled = !isFetchingModels && apiKey.isNotBlank()
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新模型列表",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isFetchingModels) "拉取中..." else "刷新")
                }
            }

            if (fetchModelsError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                ) {
                    Text(
                        text = fetchModelsError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            models.forEach { model ->
                Spacer(modifier = Modifier.height(8.dp))
                ModelInfoRow(model = model)
            }
        }
    }
}

/**
 * 自定义可选参数编辑区（key-value 对列表）
 */
@Composable
private fun ExtraParamsSection(
    params: Map<String, String>,
    onUpsert: (key: String, value: String) -> Unit,
    onRemove: (key: String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "自定义请求参数",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加参数")
            }
        }

        Text(
            text = "这些参数会附加到 API 请求体中，可覆盖默认值。部分厂商需要额外参数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (params.isEmpty()) {
            Text(
                text = "暂无自定义参数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            params.forEach { (key, value) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onRemove(key) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddParamDialog(
            existingKeys = params.keys.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { key, value ->
                onUpsert(key, value)
                showAddDialog = false
            }
        )
    }
}

/**
 * 添加/编辑参数对话框
 */
@Composable
private fun AddParamDialog(
    existingKeys: Set<String> = emptySet(),
    initialKey: String = "",
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit,
) {
    var key by remember { mutableStateOf(initialKey) }
    var value by remember { mutableStateOf(initialValue) }

    // 常见预设参数
    val presets = listOf(
        "frequency_penalty", "presence_penalty", "seed",
        "stop", "top_k", "min_p", "repetition_penalty",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请求参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 参数名
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("参数名") },
                    placeholder = { Text("如：frequency_penalty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = key.isBlank() || key in existingKeys && key != initialKey,
                    supportingText = {
                        if (key in existingKeys && key != initialKey) {
                            Text("该参数已存在")
                        }
                    }
                )

                // 参数值
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("参数值") },
                    placeholder = { Text("如：0.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 预设快捷选择
                val availablePresets = presets.filter { it !in existingKeys || it == initialKey }
                if (availablePresets.isNotEmpty()) {
                    Text(
                        text = "快捷填入：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        availablePresets.take(4).forEach { preset ->
                            AssistChip(
                                onClick = { key = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                // 常用默认值提示
                if (key.isNotBlank()) {
                    val hint = when (key) {
                        "temperature" -> "控制输出随机性 (0-2)，默认 0.7"
                        "top_p" -> "核采样参数 (0-1)，默认 1.0"
                        "max_tokens" -> "最大输出 token 数，默认 4096"
                        "frequency_penalty" -> "频率惩罚 (-2~2)，默认 0"
                        "presence_penalty" -> "存在惩罚 (-2~2)，默认 0"
                        "seed" -> "随机种子，固定种子可复现结果"
                        "top_k" -> "Top-K 采样参数，默认 40"
                        "min_p" -> "最小概率采样 (0-1)，默认 0"
                        "repetition_penalty" -> "重复惩罚，默认 1.0"
                        else -> ""
                    }
                    if (hint.isNotBlank()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (key.isNotBlank() && value.isNotBlank()) {
                        onConfirm(key.trim(), value.trim())
                    }
                },
                enabled = key.isNotBlank() && value.isNotBlank(),
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModelInfoRow(model: AiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "上下文: ${model.contextLength / 1024}K",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (model.inputPricePerMToken != null) {
                    Text(
                        text = "¥${"%.1f".format(model.inputPricePerMToken * 7.2)}/M输入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (model.supportsVision) {
                    Text(
                        text = "👁 图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
