package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AiModelOption
import com.example.model.Announcement
import com.example.model.ChatMessage
import com.example.model.ExploreDomain
import com.example.model.PendingAttachment
import com.example.model.SupportedLanguage
import com.example.model.SystemConfig
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.AnnouncementBanner
import com.example.ui.components.MarkdownMessageView
import com.example.ui.components.MtAiLogoBadge
import com.example.ui.components.ThinkingPulseOrb
import com.example.ui.theme.MtCyanPrimary
import com.example.ui.theme.MtIndigoAccent
import com.example.ui.theme.StatusEmerald

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatWorkspaceScreen(
    userProfile: UserProfile?,
    systemConfig: SystemConfig,
    announcements: List<Announcement>,
    dismissedAnnouncementIds: Set<String>,
    messagesState: UiState<List<ChatMessage>>,
    streamingText: String?,
    isGenerating: Boolean,
    followUps: List<String>,
    selectedModelId: String,
    webSearchEnabled: Boolean,
    activeDomain: ExploreDomain?,
    pendingAttachment: PendingAttachment?,
    chatNotice: String?,
    isListeningVoice: Boolean,
    partialVoiceTranscript: String,
    voiceRmsLevel: Float,
    speakingMessageId: String?,
    voiceError: String?,
    draftPrompt: String,
    onDraftPromptChange: (String) -> Unit,
    onClearNotice: () -> Unit,
    onDismissAnnouncement: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleWebSearch: (Boolean) -> Unit,
    onClearDomain: () -> Unit,
    onAttachUri: (Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onSend: (String) -> Unit,
    onRegenerate: () -> Unit,
    onEditMessage: (ChatMessage, String) -> Unit,
    onDownloadCode: (String, String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onStopSpeaking: () -> Unit,
    onClearVoiceError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var showModelMenu by remember { mutableStateOf(false) }
    var showVoiceModal by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachUri(uri)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAttachUri(uri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showVoiceModal = true
            onStartVoiceInput()
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val visibleAnnouncements = announcements.filter { !dismissedAnnouncementIds.contains(it.id) }

    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty() || streamingText != null) {
            val target = (messages.size + if (streamingText != null) 1 else 0).coerceAtLeast(1) - 1
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Active Announcements & Notices
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visibleAnnouncements.take(1).forEach { ann ->
                AnnouncementBanner(
                    announcement = ann,
                    onDismiss = { onDismissAnnouncement(ann.id) }
                )
            }
            if (activeDomain != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Specialist Mode: ${activeDomain.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearDomain, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear mode", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (chatNotice != null || voiceError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chatNotice ?: voiceError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onClearNotice()
                                onClearVoiceError()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Main Messages or Welcome Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (messagesState is UiState.Error) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = messagesState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (messages.isEmpty() && streamingText == null && !isGenerating) {
                WelcomeEmptyChatView(
                    userName = userProfile?.displayName?.substringBefore(" ") ?: "Explorer",
                    language = userProfile?.languageEnum() ?: SupportedLanguage.ENGLISH,
                    onPickPrompt = { onSend(it) },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(messages, key = { idx, msg -> msg.id.ifBlank { "tmp_$idx" } }) { index, msg ->
                        val isLastModel = msg.role == "model" && index == messages.lastIndex && !isGenerating
                        ChatMessageBubble(
                            message = msg,
                            isLastModelMessage = isLastModel,
                            isSpeakingThis = speakingMessageId == msg.id,
                            onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                            onRegenerate = onRegenerate,
                            onEdit = {
                                editingMessage = msg
                                editText = msg.content
                            },
                            onSpeak = { onSpeakMessage(msg.id, msg.content) },
                            onStopSpeak = onStopSpeaking,
                            onDownloadCode = onDownloadCode
                        )
                    }

                    if (isGenerating || streamingText != null) {
                        item(key = "streaming_bubble") {
                            StreamingAssistantBubble(
                                text = streamingText.orEmpty(),
                                onDownloadCode = onDownloadCode
                            )
                        }
                    }

                    if (followUps.isNotEmpty() && !isGenerating) {
                        item(key = "followup_chips") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Suggested Follow-ups",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    followUps.forEach { q ->
                                        AssistChip(
                                            onClick = { onSend(q) },
                                            label = { Text(q, style = MaterialTheme.typography.bodySmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Bar
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pending attachment pill
                AnimatedVisibility(visible = pendingAttachment != null) {
                    pendingAttachment?.let { att ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (att.bitmap != null) {
                                    Image(
                                        bitmap = att.bitmap.asImageBitmap(),
                                        contentDescription = att.displayName,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${att.kind.name} ready for AI analysis",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                IconButton(onClick = onClearAttachment, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Controls Row: Model Selector & Web Search Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        val currentModel = AiModelOption.fromId(selectedModelId)
                        AssistChip(
                            onClick = { showModelMenu = true },
                            label = {
                                Text(
                                    text = currentModel.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ExpandMore, contentDescription = "Select AI Model", modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("model_selector_chip")
                        )
                        DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                            AiModelOption.chatModels().forEach { option ->
                                val enabled = systemConfig.enabledModels.contains(option.modelId)
                                DropdownMenuItem(
                                    enabled = enabled,
                                    text = {
                                        Column {
                                            Text(
                                                "${option.displayName} (${option.badge})",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = if (option.modelId == selectedModelId) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null,
                                    onClick = {
                                        showModelMenu = false
                                        onSelectModel(option.modelId)
                                    }
                                )
                            }
                        }
                    }

                    FilterChip(
                        selected = webSearchEnabled,
                        onClick = { onToggleWebSearch(!webSearchEnabled) },
                        label = { Text("Web Search", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("web_search_toggle")
                    )
                }

                // Input Row: Attach, Photo, TextField, Voice, Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/*",
                                    "application/json",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "image/*"
                                )
                            )
                        },
                        modifier = Modifier.testTag("attach_file_button")
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Upload file (PDF, DOCX, XLSX, CSV, TXT)")
                    }

                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("upload_image_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Upload image")
                    }

                    OutlinedTextField(
                        value = draftPrompt,
                        onValueChange = onDraftPromptChange,
                        placeholder = {
                            Text(
                                text = "Ask MT AI anything...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        maxLines = 4,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    IconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                showVoiceModal = true
                                onStartVoiceInput()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.testTag("voice_input_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice conversation",
                            tint = if (isListeningVoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = {
                            if (!isGenerating) {
                                val text = draftPrompt
                                onDraftPromptChange("")
                                onSend(text)
                            }
                        },
                        enabled = !isGenerating && (draftPrompt.isNotBlank() || pendingAttachment != null),
                        shape = CircleShape,
                        color = if (!isGenerating && (draftPrompt.isNotBlank() || pendingAttachment != null)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (!isGenerating && (draftPrompt.isNotBlank() || pendingAttachment != null)) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message & Regenerate") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = editingMessage
                    if (target != null && editText.isNotBlank()) {
                        onEditMessage(target, editText)
                    }
                    editingMessage = null
                }) {
                    Text("Save & Resend")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVoiceModal) {
        ModalBottomSheet(
            onDismissRequest = {
                onStopVoiceInput()
                showVoiceModal = false
            }
        ) {
            VoiceConversationSheet(
                isListening = isListeningVoice,
                partialTranscript = partialVoiceTranscript,
                rmsLevel = voiceRmsLevel,
                voiceError = voiceError,
                onStartListening = onStartVoiceInput,
                onStopListening = onStopVoiceInput,
                onUseTranscript = { text ->
                    showVoiceModal = false
                    if (text.isNotBlank()) onSend(text)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeEmptyChatView(
    userName: String,
    language: SupportedLanguage,
    onPickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "Build a modern portfolio website with HTML, CSS & JS",
        "Explain quantum computing simply in ${language.displayName}",
        "Design a B2B SaaS launch roadmap & revenue model",
        "Debug why my SQL query is doing a full table scan"
    )
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_mt_ai_hero_1795372914347),
                    contentDescription = "MT AI Welcome Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xDD070D19), Color(0x55070D19))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MtAiLogoBadge(size = 46.dp)
                    Column {
                        Text(
                            text = "Welcome back, $userName",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "MT AI • Your World, Your Intelligence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MtCyanPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Try a prompt or attach a file, photo, or voice query",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suggestions.forEach { prompt ->
                    Surface(
                        onClick = { onPickPrompt(prompt) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.widthIn(min = 150.dp, max = 340.dp)
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isLastModelMessage: Boolean,
    isSpeakingThis: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onDownloadCode: (String, String) -> Unit
) {
    val isUser = message.role == "user"
    val uriHandler = LocalUriHandler.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                MtAiLogoBadge(size = 22.dp)
                Text(
                    text = "MT AI • ${AiModelOption.fromId(message.modelId).displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 6.dp,
                topEnd = if (isUser) 6.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ),
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!message.attachmentName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                text = message.attachmentName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    MarkdownMessageView(
                        markdown = message.content,
                        onDownloadCode = onDownloadCode
                    )
                }

                if (message.citations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Verified Web Citations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        message.citations.forEachIndexed { idx, url ->
                            TextButton(
                                onClick = {
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        uriHandler.openUri(url)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "[${idx + 1}] $url",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (message.edited) {
                    Text(
                        text = "Edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isUser) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit message", modifier = Modifier.size(16.dp))
                }
            } else {
                IconButton(
                    onClick = {
                        onCopy()
                        copied = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy response",
                        tint = if (copied) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { if (isSpeakingThis) onStopSpeak() else onSpeak() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeakingThis) "Stop audio" else "Read aloud",
                        tint = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isLastModelMessage) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Regenerate response",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    onDownloadCode: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            MtAiLogoBadge(size = 22.dp)
            Text(
                text = "MT AI • Streaming",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (text.isBlank()) {
                    ThinkingPulseOrb()
                } else {
                    MarkdownMessageView(markdown = text, onDownloadCode = onDownloadCode)
                }
            }
        }
    }
}

@Composable
private fun VoiceConversationSheet(
    isListening: Boolean,
    partialTranscript: String,
    rmsLevel: Float,
    voiceError: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onUseTranscript: (String) -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "voice_pulse")
    val idleScale by pulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "voice_idle"
    )
    val dynamicScale = if (isListening) (1f + rmsLevel * 0.35f) else idleScale

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MT AI Voice Mode", style = MaterialTheme.typography.headlineSmall)
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(dynamicScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(MtCyanPrimary, MtIndigoAccent))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Voice waveform",
                tint = Color(0xFF04101E),
                modifier = Modifier.size(44.dp)
            )
        }
        Text(
            text = when {
                voiceError != null -> voiceError
                isListening -> partialTranscript.ifBlank { "Listening... speak naturally" }
                partialTranscript.isNotBlank() -> partialTranscript
                else -> "Tap Start Listening to speak"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (voiceError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isListening) {
                Button(onClick = onStopListening) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop")
                }
            } else {
                Button(onClick = onStartListening) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Listen Again")
                }
            }
            if (partialTranscript.isNotBlank()) {
                Button(onClick = { onUseTranscript(partialTranscript) }) {
                    Text("Send to MT AI")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
