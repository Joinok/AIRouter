package com.airouter.ui.screen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.airouter.data.local.AttachmentStorage
import com.airouter.data.local.prefs.AppConfig
import com.airouter.data.model.ChatMessage
import com.airouter.data.model.MessageAttachment
import com.airouter.data.model.MessageRole
import com.airouter.debug.DebugLog
import com.airouter.rememberImeHeight
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
    appConfig: AppConfig = koinInject(),
) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val attachmentStorage = remember { AttachmentStorage(context) }

    // View 层直接读 IME 高度，不受 NavHost insets 消费影响
    val imeHeight = rememberImeHeight()

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        viewModel.handleSelectedUris(uris, context)
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        viewModel.handleSelectedUris(uris, context)
    }

    // 自动滚动：使用 reverseLayout，列表从底部开始排列，天然在底部
    // 只需在新消息/内容变化时滚到顶部（即 reverseLayout 的 index 0）
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content, messages.lastOrNull()?.isStreaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 初次加载完成滚到底
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            listState.scrollToItem(0)
        }
    }

    // 键盘弹出时滚到底
    val isImeVisible = imeHeight > 0.dp
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val canSend = (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) && !isSending

    // 调试日志面板状态
    var showDebugPanel by remember { mutableStateOf(false) }
    val debugEnabled by remember { derivedStateOf { appConfig.debugLogEnabled } }
    val debugLogs by DebugLog.logs.collectAsState()
    val debugListState = rememberLazyListState()

    // 调试日志开关同步
    LaunchedEffect(debugEnabled) {
        DebugLog.enabled = debugEnabled
    }

    // 微信式布局：TopBar 固定，消息区被键盘挤压
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // === 顶层：TopBar，不受键盘影响 ===
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Text(
                    text = currentModel ?: "对话",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        // === 中间层+底层：消息列表 + 输入框 ===
        Column(modifier = Modifier.weight(1f)) {
            // 消息列表
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "开始对话吧",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    reverseLayout = true,
                ) {
                    items(messages.reversed(), key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            showTokenUsage = appConfig.showTokenUsage,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val copyText = message.content.ifEmpty { message.errorMessage ?: "" }
                                clipboard.setPrimaryClip(ClipData.newPlainText("消息", copyText))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { viewModel.deleteMessage(message.id) },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // 输入框区域
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = imeHeight),
                ) {
                    // 附件预览条
                    if (pendingAttachments.isNotEmpty()) {
                        AttachmentPreviewRow(
                            attachments = pendingAttachments,
                            onRemove = { viewModel.removeAttachment(it) },
                        )
                    }

                    // 输入框行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 图片按钮
                        IconButton(onClick = {
                            imagePickerLauncher.launch("image/*")
                        }) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "选择图片",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 文件按钮
                        IconButton(onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "选择文件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { viewModel.updateInput(it) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 120.dp),
                            placeholder = { Text("输入消息...") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (canSend)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(24.dp),
                                ),
                            enabled = canSend,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (canSend)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // 调试日志浮动按钮（仅调试模式开启时显示）
    if (debugEnabled) {
        FloatingActionButton(
            onClick = { showDebugPanel = !showDebugPanel },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
                .size(40.dp),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Text("🔧", fontSize = 16.sp)
        }

        // 调试日志面板（底部弹出）
        if (showDebugPanel) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                shadowElevation = 16.dp,
            ) {
                Column {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("调试日志", style = MaterialTheme.typography.titleSmall)
                        Row {
                            TextButton(onClick = { DebugLog.clear() }) {
                                Text("清空")
                            }
                            TextButton(onClick = { showDebugPanel = false }) {
                                Text("关闭")
                            }
                        }
                    }
                    HorizontalDivider()
                    // 日志列表
                    LazyColumn(
                        state = debugListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(debugLogs) { entry ->
                            Text(
                                text = "${entry.time} [${entry.tag}] ${entry.message}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = when {
                                    entry.message.startsWith("✗") -> MaterialTheme.colorScheme.error
                                    entry.message.startsWith("●") -> MaterialTheme.colorScheme.primary
                                    entry.message.startsWith("✓") -> MaterialTheme.colorScheme.tertiary
                                    entry.tag == "🧠" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    } // end debugEnabled
    } // end Box
}

/**
 * 附件预览条（输入框上方的水平滚动列表）
 */
@Composable
private fun AttachmentPreviewRow(
    attachments: List<MessageAttachment>,
    onRemove: (MessageAttachment) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .heightIn(max = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            when {
                attachment.isImage -> {
                    // 图片缩略图预览（从本地文件加载）
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    ) {
                        AsyncImage(
                            model = File(attachment.localPath),
                            contentDescription = attachment.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // 删除按钮
                        IconButton(
                            onClick = { onRemove(attachment) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "移除",
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
                else -> {
                    // 文件预览卡片
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(80.dp),
                    ) {
                        Box {
                            Column(
                                modifier = Modifier
                                    .width(120.dp)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = attachment.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onRemove(attachment) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    showTokenUsage: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == MessageRole.USER) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.role == MessageRole.USER) 16.dp else 4.dp,
                bottomEnd = if (message.role == MessageRole.USER) 4.dp else 16.dp,
            ),
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                message.role == MessageRole.USER -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (message.role == MessageRole.USER) 0.dp else 1.dp,
            border = if (message.isError) {
                androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error
                )
            } else null,
            modifier = Modifier.combinedClickable(
                onClick = { },
                onLongClick = { if (message.content.isNotEmpty() || message.isError) showMenu = true },
            ),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(12.dp)
            ) {
                // 图片附件展示（从本地文件加载）
                val imageAttachments = message.attachments.filter { it.isImage }
                if (imageAttachments.isNotEmpty()) {
                    imageAttachments.forEach { attachment ->
                        val file = File(attachment.localPath)
                        if (file.exists()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            AsyncImage(
                                model = file,
                                contentDescription = attachment.fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }

                // 文件附件展示
                val fileAttachments = message.attachments.filter { !it.isImage }
                if (fileAttachments.isNotEmpty()) {
                    fileAttachments.forEach { attachment ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (message.role) {
                                MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = when (message.role) {
                                        MessageRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = when (message.role) {
                                            MessageRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (attachment.fileSize > 0) {
                                        Text(
                                            text = formatFileSize(attachment.fileSize),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (message.role) {
                                                MessageRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 推理中提示（Kimi K2.6 等思考模型）
                if (message.isReasoning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "思考中...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                // 正常内容
                if (message.content.isNotEmpty()) {
                    if (message.role == MessageRole.ASSISTANT) {
                        // AI 回复使用 Markdown 渲染
                        com.mikepenz.markdown.m3.Markdown(
                            content = message.content,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // 用户消息支持选取复制
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = when (message.role) {
                                    MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                                    MessageRole.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }

                // 错误信息
                if (message.isError) {
                    Text(
                        text = message.errorMessage ?: "发生未知错误",
                        style = if (message.content.isEmpty()) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Token 用量
                if (showTokenUsage && message.tokenUsage != null && message.tokenUsage!!.totalTokens > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${message.tokenUsage!!.totalTokens} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (message.role) {
                            MessageRole.USER -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            MessageRole.ASSISTANT -> MaterialTheme.colorScheme.onSurfaceVariant
                            MessageRole.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                // 流式输出指示器
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = when (message.role) {
                            MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // 长按菜单
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        showMenu = false
                        onCopy()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    },
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
