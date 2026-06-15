package com.airouter.ui.screen.provider

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
 * 自定义可选参数编辑区（截图样式：快捷按钮 + 自定义输入框）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraParamsSection(
    params: Map<String, String>,
    onUpsert: (key: String, value: String) -> Unit,
    onRemove: (key: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "可选参数",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "不添加则使用服务商默认值。需要时可添加 temperature、top_p、max_tokens 等参数；小数请直接输入，例如 0.95。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 快捷参数按钮
        val quickParams = listOf("temperature", "top_p", "max_tokens", "presence_penalty", "frequency_penalty")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickParams.forEach { param ->
                OutlinedButton(
                    onClick = {
                        if (param in params) {
                            onRemove(param)
                        } else {
                            onUpsert(param, "")
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(if (param in params) "✓ $param" else "+ $param")
                }
            }
        }

        // 已选参数列表
        params.forEach { (key, value) ->
            var currentValue by remember(key) { mutableStateOf(value) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = {},
                    label = null,
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = { newValue ->
                        currentValue = newValue
                        if (newValue.isBlank()) {
                            onRemove(key)
                        } else {
                            onUpsert(key, newValue)
                        }
                    },
                    label = { Text("值") },
                    placeholder = { Text("如 0.95") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 自定义参数输入行
        var customKey by remember { mutableStateOf("") }
        var customValue by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customKey,
                onValueChange = { customKey = it },
                label = { Text("自定义参数") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = customValue,
                onValueChange = { customValue = it },
                label = { Text("值") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    if (customKey.isNotBlank() && customValue.isNotBlank()) {
                        IconButton(onClick = {
                            onUpsert(customKey.trim(), customValue.trim())
                            customKey = ""
                            customValue = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                    }
                }
            )
        }
    }
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
