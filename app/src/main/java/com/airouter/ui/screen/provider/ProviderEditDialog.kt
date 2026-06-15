package com.airouter.ui.screen.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.airouter.data.model.ProviderType

/**
 * 统一的 Provider 编辑对话框（用于添加和编辑）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderEditDialog(
    title: String,
    initialName: String = "",
    initialBaseUrl: String = "",
    initialApiKey: String = "",
    initialModelId: String = "",
    initialDisplayName: String = "",
    initialExtraParams: Map<String, String> = emptyMap(),
    providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    existingNames: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, modelId: String, displayName: String, extraParams: Map<String, String>) -> Unit,
    onFetchModels: ((baseUrl: String, apiKey: String, onResult: (Result<List<String>>) -> Unit) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var modelId by remember { mutableStateOf(initialModelId) }
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var extraParams by remember { mutableStateOf(initialExtraParams) }
    var showApiKey by remember { mutableStateOf(false) }
    
    // 拉取模型状态
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember { mutableStateOf(false) }

    val nameError = name.isBlank() || (name in existingNames && name != initialName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider 名称") },
                    placeholder = { Text("如：DeepSeek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = { if (name in existingNames && name != initialName) Text("名称已存在") }
                )

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("如：https://api.deepseek.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("留空则使用默认值") }
                )

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-xxx...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "隐藏" else "显示"
                            )
                        }
                    }
                )

                // 模型选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("如：deepseek-chat") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (onFetchModels != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                isFetchingModels = true
                                fetchError = null
                                val url = if (baseUrl.isNotBlank()) baseUrl else "https://api.openai.com"
                                onFetchModels.invoke(url, apiKey) { result ->
                                    isFetchingModels = false
                                    result.onSuccess { models ->
                                        availableModels = models
                                        showModelPicker = true
                                    }.onFailure { e ->
                                        fetchError = e.message ?: "拉取失败"
                                    }
                                }
                            },
                            enabled = apiKey.isNotBlank() && !isFetchingModels
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "拉取模型")
                            }
                        }
                    }
                }

                if (fetchError != null) {
                    Text(fetchError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                // 显示名称
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // === 可选参数区 ===
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
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
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = "不填则使用默认值。如需添加 temperature、top_p 等，点击下方按钮或自定义输入。",
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
                                if (param in extraParams) {
                                    extraParams = extraParams - param
                                } else {
                                    extraParams = extraParams + (param to "")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(if (param in extraParams) "✓ $param" else "+ $param", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // 已选参数列表
                extraParams.forEach { (key, value) ->
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
                                    extraParams = extraParams - key
                                } else {
                                    extraParams = extraParams + (key to newValue)
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
                                    extraParams = extraParams + (customKey.trim() to customValue.trim())
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        baseUrl.trim(),
                        apiKey.trim(),
                        modelId.trim().ifBlank { "default" },
                        displayName.trim().ifBlank { modelId.trim() },
                        extraParams
                    )
                },
                enabled = name.isNotBlank() && !nameError
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
    if (showModelPicker && availableModels.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择模型") },
            text = {
                LazyColumn {
                    items(availableModels.size) { index ->
                        TextButton(
                            onClick = {
                                modelId = availableModels[index]
                                if (displayName.isBlank()) {
                                    displayName = availableModels[index]
                                }
                                showModelPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(availableModels[index])
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModelPicker = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
