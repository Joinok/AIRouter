package com.airouter.ui.screen.provider

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    onBack: () -> Unit,
    viewModel: ProviderEditViewModel = koinViewModel(),
) {
    val provider by viewModel.provider.collectAsState()
    val saved by viewModel.saved.collectAsState()

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

    // 从 Provider 对象中提取初始值
    val initialName = p.name
    val initialBaseUrl = p.customBaseUrl
    val initialApiKey = p.apiKey
    val initialModelId = p.supportedModels.firstOrNull()?.modelId ?: ""
    val initialDisplayName = p.supportedModels.firstOrNull()?.displayName ?: ""
    val initialExtraParams = p.extraBodyFields

    // 内置 Provider（Claude/Gemini）不可修改名称和模型
    val readOnlyNameAndModel = p.isBuiltIn

    // 进入即弹对话框（无需额外状态）
    ProviderEditDialog(
        title = if (readOnlyNameAndModel) "配置 $initialName" else "编辑 $initialName",
        initialName = initialName,
        initialBaseUrl = initialBaseUrl,
        initialApiKey = initialApiKey,
        initialModelId = initialModelId,
        initialDisplayName = initialDisplayName,
        initialExtraParams = initialExtraParams,
        readOnlyFields = if (readOnlyNameAndModel) setOf("name", "modelId") else emptySet(),
        onDismiss = onBack,
        onConfirm = { name, baseUrl, apiKey, modelId, displayName, extraParams ->
            viewModel.saveFullConfig(name, baseUrl, apiKey, modelId, displayName, extraParams)
        }
    )
}