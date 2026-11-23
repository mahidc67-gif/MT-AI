package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AiRepository
import com.example.data.DocumentParser
import com.example.data.FirestoreRepository
import com.example.data.WebSearchService
import com.example.model.AccountStatus
import com.example.model.AiModelOption
import com.example.model.Announcement
import com.example.model.AnnouncementSeverity
import com.example.model.BOOTSTRAP_ADMIN_EMAIL
import com.example.model.ChatMessage
import com.example.model.CodingTaskPreset
import com.example.model.Conversation
import com.example.model.ErrorLog
import com.example.model.ExploreDomain
import com.example.model.ImageAspectOption
import com.example.model.MemoryFact
import com.example.model.PendingAttachment
import com.example.model.SubscriptionPlan
import com.example.model.SupportedLanguage
import com.example.model.SystemConfig
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.WebSearchCitation
import com.google.firebase.Firebase
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

data class CodeLabUiState(
    val isLoading: Boolean = false,
    val outputMarkdown: String = "",
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MtAiViewModel(
    private val firestoreRepo: FirestoreRepository,
    private val aiRepo: AiRepository,
    private val webSearchService: WebSearchService,
    val currentUserId: String
) : ViewModel() {

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _followUps = MutableStateFlow<List<String>>(emptyList())
    val followUps: StateFlow<List<String>> = _followUps.asStateFlow()

    private val _selectedModelId = MutableStateFlow(AiModelOption.FLASH_3_8.modelId)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    private val _activeDomain = MutableStateFlow<ExploreDomain?>(null)
    val activeDomain: StateFlow<ExploreDomain?> = _activeDomain.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _chatNotice = MutableStateFlow<String?>(null)
    val chatNotice: StateFlow<String?> = _chatNotice.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _imageStudioState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val imageStudioState: StateFlow<ImageStudioUiState> = _imageStudioState.asStateFlow()

    private val _codeLabState = MutableStateFlow(CodeLabUiState())
    val codeLabState: StateFlow<CodeLabUiState> = _codeLabState.asStateFlow()

    private val _dismissedAnnouncementIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedAnnouncementIds: StateFlow<Set<String>> = _dismissedAnnouncementIds.asStateFlow()

    val userProfile: StateFlow<UiState<UserProfile?>> = firestoreRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load user profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val systemConfig: StateFlow<SystemConfig> = firestoreRepo.observeSystemConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SystemConfig())

    val activeAnnouncements: StateFlow<List<Announcement>> = firestoreRepo.observeActiveAnnouncements()
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<UiState<List<Conversation>>> = firestoreRepo.observeConversations(currentUserId)
        .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load conversations"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val filteredConversations: StateFlow<List<Conversation>> = combine(
        conversations,
        _searchQuery
    ) { state, query ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val q = query.trim()
        if (q.isEmpty()) list
        else list.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.lastMessagePreview.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeMessages: StateFlow<UiState<List<ChatMessage>>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                firestoreRepo.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = unwrapFirestoreError(error)
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load messages"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    val memories: StateFlow<List<MemoryFact>> = firestoreRepo.observeMemories(currentUserId)
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isAdminUser: StateFlow<Boolean> = userProfile.map { state ->
        val profile = (state as? UiState.Success)?.data
        val authUser = Firebase.auth.currentUser
        val emailMatch = authUser?.isEmailVerified == true &&
            authUser.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)
        emailMatch || profile?.roleEnum() == UserRole.ADMIN
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val adminUsers: StateFlow<UiState<List<UserProfile>>> = isAdminUser
        .flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else firestoreRepo.observeAllUsers()
                .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    emit(UiState.Error((fsErr ?: error).message ?: "Failed to load users"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminAnnouncements: StateFlow<UiState<List<Announcement>>> = isAdminUser
        .flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else firestoreRepo.observeAllAnnouncements()
                .map<List<Announcement>, UiState<List<Announcement>>> { UiState.Success(it) }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    emit(UiState.Error((fsErr ?: error).message ?: "Failed to load announcements"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminErrorLogs: StateFlow<UiState<List<ErrorLog>>> = isAdminUser
        .flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else firestoreRepo.observeErrorLogs()
                .map<List<ErrorLog>, UiState<List<ErrorLog>>> { UiState.Success(it) }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    emit(UiState.Error((fsErr ?: error).message ?: "Failed to load error logs"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile()
                .onSuccess { profile ->
                    _selectedModelId.value = profile.preferredModel
                }
                .onFailure { e ->
                    _chatNotice.value = e.localizedMessage ?: "Failed to sync account profile."
                }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun todayUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun clearNotice() {
        _chatNotice.value = null
    }

    fun dismissAnnouncement(id: String) {
        _dismissedAnnouncementIds.value = _dismissedAnnouncementIds.value + id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleWebSearch(enabled: Boolean) {
        _webSearchEnabled.value = enabled
    }

    fun selectDomain(domain: ExploreDomain?) {
        _activeDomain.value = domain
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        viewModelScope.launch {
            firestoreRepo.updateUserPreferences(preferredModel = modelId)
        }
    }

    fun startNewChat() {
        _activeConversationId.value = null
        _streamingText.value = null
        _followUps.value = emptyList()
        _pendingAttachment.value = null
        _chatNotice.value = null
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        _streamingText.value = null
        _followUps.value = emptyList()
        _chatNotice.value = null
    }

    fun attachFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            DocumentParser.loadAttachment(context, uri)
                .onSuccess { attachment ->
                    _pendingAttachment.value = attachment
                }
                .onFailure { e ->
                    _chatNotice.value = e.localizedMessage ?: "Failed to attach file."
                }
        }
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        val attachment = _pendingAttachment.value
        if (prompt.isEmpty() && attachment == null) return
        if (_isGenerating.value) return

        val effectivePrompt = prompt.ifEmpty { "Please analyze the attached file (${attachment?.displayName})." }
        val profile = (userProfile.value as? UiState.Success)?.data
        val config = systemConfig.value
        val isAdmin = isAdminUser.value

        if (profile?.statusEnum() == AccountStatus.SUSPENDED) {
            _chatNotice.value = "Your account is currently suspended. Contact an administrator."
            return
        }
        if (config.maintenanceMode && !isAdmin) {
            _chatNotice.value = "MT AI is currently undergoing scheduled maintenance. Please check back shortly."
            return
        }

        val today = todayUtc()
        val usedToday = if (profile?.lastRequestDate == today) profile.dailyRequestsUsed else 0L
        val limit = if (profile?.planEnum() == SubscriptionPlan.PRO || isAdmin) {
            config.proDailyLimit
        } else {
            config.freeDailyLimit
        }
        if (!isAdmin && usedToday >= limit) {
            _chatNotice.value = "Daily request limit ($limit) reached for ${profile?.plan ?: "FREE"} plan. Resets at 00:00 UTC."
            return
        }

        var chosenModel = _selectedModelId.value
        if (!config.enabledModels.contains(chosenModel)) {
            chosenModel = config.defaultModel.takeIf { config.enabledModels.contains(it) }
                ?: AiModelOption.FLASH_3_8.modelId
        }
        val modelOption = AiModelOption.fromId(chosenModel)
        if (modelOption.requiresPro && profile?.planEnum() == SubscriptionPlan.FREE && !isAdmin) {
            if (config.fallbackEnabled) {
                chosenModel = AiModelOption.FLASH_3_8.modelId
                _chatNotice.value = "${modelOption.displayName} requires Pro Plan; routed to MT Flash 3.8."
            } else {
                _chatNotice.value = "${modelOption.displayName} requires the Pro Plan."
                return
            }
        }

        viewModelScope.launch {
            _isGenerating.value = true
            _followUps.value = emptyList()
            _pendingAttachment.value = null

            val existingHistory = (activeMessages.value as? UiState.Success)?.data.orEmpty()
            var convId = _activeConversationId.value
            if (convId == null) {
                val title = effectivePrompt.replace("\n", " ").take(48)
                val created = firestoreRepo.createConversation(title, chosenModel)
                if (created.isFailure) {
                    _isGenerating.value = false
                    _chatNotice.value = created.exceptionOrNull()?.localizedMessage ?: "Could not start conversation."
                    return@launch
                }
                convId = created.getOrThrow()
                _activeConversationId.value = convId
            }

            firestoreRepo.addMessage(
                conversationId = convId,
                role = "user",
                content = effectivePrompt,
                modelId = chosenModel,
                attachmentName = attachment?.displayName,
                attachmentType = attachment?.kind?.name
            ).onFailure { e ->
                _isGenerating.value = false
                _chatNotice.value = e.localizedMessage ?: "Failed to save message."
                return@launch
            }

            executeAiTurn(
                conversationId = convId,
                prompt = effectivePrompt,
                history = existingHistory,
                chosenModel = chosenModel,
                profile = profile,
                config = config,
                attachment = attachment,
                targetAssistantMessageId = null
            )
        }
    }

    fun regenerateLastResponse() {
        if (_isGenerating.value) return
        val convId = _activeConversationId.value ?: return
        val msgs = (activeMessages.value as? UiState.Success)?.data.orEmpty()
        val lastUserIdx = msgs.indexOfLast { it.role == "user" }
        if (lastUserIdx == -1) return
        val lastUserMsg = msgs[lastUserIdx]
        val historyBefore = msgs.take(lastUserIdx)
        val trailingModelMsg = msgs.drop(lastUserIdx + 1).firstOrNull { it.role == "model" }
        val extraToDelete = msgs.drop(lastUserIdx + 1)
            .filter { it.id != trailingModelMsg?.id }
            .map { it.id }

        viewModelScope.launch {
            _isGenerating.value = true
            _followUps.value = emptyList()
            if (extraToDelete.isNotEmpty()) {
                firestoreRepo.deleteMessagesAfter(convId, extraToDelete)
            }
            executeAiTurn(
                conversationId = convId,
                prompt = lastUserMsg.content,
                history = historyBefore,
                chosenModel = _selectedModelId.value,
                profile = (userProfile.value as? UiState.Success)?.data,
                config = systemConfig.value,
                attachment = null,
                targetAssistantMessageId = trailingModelMsg?.id
            )
        }
    }

    fun editAndResendMessage(message: ChatMessage, updatedPrompt: String) {
        val trimmed = updatedPrompt.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return
        val convId = _activeConversationId.value ?: return
        val msgs = (activeMessages.value as? UiState.Success)?.data.orEmpty()
        val idx = msgs.indexOfFirst { it.id == message.id }
        if (idx == -1) return
        val historyBefore = msgs.take(idx)
        val idsToDelete = msgs.drop(idx + 1).map { it.id }

        viewModelScope.launch {
            _isGenerating.value = true
            _followUps.value = emptyList()
            firestoreRepo.updateMessageContent(
                conversationId = convId,
                messageId = message.id,
                content = trimmed,
                modelId = message.modelId,
                edited = true
            )
            if (idsToDelete.isNotEmpty()) {
                firestoreRepo.deleteMessagesAfter(convId, idsToDelete)
            }
            executeAiTurn(
                conversationId = convId,
                prompt = trimmed,
                history = historyBefore,
                chosenModel = _selectedModelId.value,
                profile = (userProfile.value as? UiState.Success)?.data,
                config = systemConfig.value,
                attachment = null,
                targetAssistantMessageId = null
            )
        }
    }

    private suspend fun executeAiTurn(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        chosenModel: String,
        profile: UserProfile?,
        config: SystemConfig,
        attachment: PendingAttachment?,
        targetAssistantMessageId: String?
    ) {
        var citations: List<WebSearchCitation> = emptyList()
        if (_webSearchEnabled.value) {
            webSearchService.searchWeb(prompt)
                .onSuccess { results ->
                    citations = results
                    if (results.isEmpty()) {
                        _chatNotice.value = "Live web search returned no instant matches; answering without external citations."
                    }
                }
                .onFailure { e ->
                    _chatNotice.value = "Live web search unavailable (${e.localizedMessage ?: "network error"}). Answering without internet results."
                }
        }

        val language = profile?.languageEnum() ?: SupportedLanguage.ENGLISH
        val memoryEnabled = profile?.memoryEnabled ?: true
        val activeMemories = memories.value
        val domainHint = _activeDomain.value?.systemHint

        var usedModel = chosenModel
        var finalReply = ""

        suspend fun runStream(modelIdToUse: String): String {
            val sb = StringBuilder()
            _streamingText.value = ""
            aiRepo.streamChat(
                modelId = modelIdToUse,
                prompt = prompt,
                history = history,
                preferredLanguage = language,
                memories = activeMemories,
                memoryEnabled = memoryEnabled,
                attachment = attachment,
                webCitations = citations,
                domainSystemHint = domainHint
            ).collect { partial ->
                sb.clear()
                sb.append(partial)
                _streamingText.value = partial
            }
            return sb.toString()
        }

        try {
            finalReply = runStream(usedModel)
        } catch (e: CancellationException) {
            _streamingText.value = null
            _isGenerating.value = false
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Primary model $usedModel failed", e)
            firestoreRepo.logError("ChatAI", usedModel, e.localizedMessage ?: e.toString())

            val canFallback = config.fallbackEnabled &&
                usedModel != AiModelOption.FLASH_3_8.modelId &&
                e !is TimeoutCancellationException
            if (canFallback) {
                try {
                    usedModel = AiModelOption.FLASH_3_8.modelId
                    _chatNotice.value = "Primary model unavailable; switched to fallback MT Flash 3.8."
                    finalReply = runStream(usedModel)
                } catch (fallbackErr: CancellationException) {
                    _streamingText.value = null
                    _isGenerating.value = false
                    throw fallbackErr
                } catch (fallbackErr: Exception) {
                    Log.e(TAG, "Fallback model failed", fallbackErr)
                    firestoreRepo.logError("ChatAIFallback", usedModel, fallbackErr.localizedMessage ?: fallbackErr.toString())
                    _streamingText.value = null
                    _isGenerating.value = false
                    _chatNotice.value = friendlyAiError(fallbackErr)
                    return
                }
            } else {
                _streamingText.value = null
                _isGenerating.value = false
                _chatNotice.value = friendlyAiError(e)
                return
            }
        }

        _streamingText.value = null
        val citationUrls = citations.map { it.url }

        if (targetAssistantMessageId != null) {
            firestoreRepo.updateMessageContent(
                conversationId = conversationId,
                messageId = targetAssistantMessageId,
                content = finalReply,
                modelId = usedModel,
                edited = true,
                citations = citationUrls
            )
        } else {
            firestoreRepo.addMessage(
                conversationId = conversationId,
                role = "model",
                content = finalReply,
                modelId = usedModel,
                citations = citationUrls
            )
        }

        if (profile != null) {
            firestoreRepo.incrementUsage(profile)
        }
        _isGenerating.value = false

        // Non-blocking follow-up suggestions
        viewModelScope.launch {
            _followUps.value = aiRepo.generateFollowUps(prompt, finalReply)
        }
    }

    private fun friendlyAiError(e: Throwable): String = when (e) {
        is TimeoutCancellationException -> "Request timed out after 60 seconds. Please retry."
        is QuotaExceededException -> "AI quota exceeded or Blaze plan required for this model."
        else -> e.localizedMessage ?: "AI request failed. Please check connectivity and App Check."
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { firestoreRepo.renameConversation(id, title) }
    }

    fun togglePinConversation(id: String, pinned: Boolean) {
        viewModelScope.launch { firestoreRepo.togglePinConversation(id, pinned) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            firestoreRepo.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    // --- Memory & Preferences ---

    fun addMemory(fact: String, category: String) {
        viewModelScope.launch {
            firestoreRepo.addMemory(fact, category)
                .onFailure { _chatNotice.value = it.localizedMessage ?: "Failed to save memory." }
        }
    }

    fun toggleMemory(id: String, active: Boolean) {
        viewModelScope.launch { firestoreRepo.toggleMemory(id, active) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { firestoreRepo.deleteMemory(id) }
    }

    fun updateLanguage(language: SupportedLanguage) {
        viewModelScope.launch { firestoreRepo.updateUserPreferences(preferredLanguage = language.displayName) }
    }

    fun updateTheme(theme: ThemePreference) {
        viewModelScope.launch { firestoreRepo.updateUserPreferences(themePreference = theme) }
    }

    fun updateMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { firestoreRepo.updateUserPreferences(memoryEnabled = enabled) }
    }

    // --- Image Studio ---

    fun generateOrEditImage(
        prompt: String,
        aspectOption: ImageAspectOption,
        sourceBitmap: Bitmap?
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        val profile = (userProfile.value as? UiState.Success)?.data
        if (profile?.statusEnum() == AccountStatus.SUSPENDED) {
            _imageStudioState.value = ImageStudioUiState.Error("Account suspended.")
            return
        }
        viewModelScope.launch {
            _imageStudioState.value = ImageStudioUiState.Loading
            aiRepo.generateOrEditImage(cleanPrompt, aspectOption, sourceBitmap)
                .onSuccess { bmp ->
                    _imageStudioState.value = ImageStudioUiState.Success(bmp, cleanPrompt)
                    if (profile != null) firestoreRepo.incrementUsage(profile)
                }
                .onFailure { err ->
                    firestoreRepo.logError("ImageStudio", AiModelOption.NANO_BANANA_2_1.modelId, err.localizedMessage ?: err.toString())
                    _imageStudioState.value = ImageStudioUiState.Error(
                        err.localizedMessage ?: "Image generation failed."
                    )
                }
        }
    }

    // --- Code Lab ---

    fun runCodeLabTask(
        language: String,
        preset: CodingTaskPreset,
        codeOrPrompt: String
    ) {
        val trimmed = codeOrPrompt.trim()
        if (trimmed.isEmpty()) return
        val profile = (userProfile.value as? UiState.Success)?.data
        if (profile?.statusEnum() == AccountStatus.SUSPENDED) {
            _codeLabState.value = CodeLabUiState(error = "Account suspended.")
            return
        }
        viewModelScope.launch {
            _codeLabState.value = CodeLabUiState(isLoading = true, outputMarkdown = "")
            val fullPrompt = "Target Language/Stack: $language\nTask: ${preset.title} (${preset.instruction})\n\nInput:\n$trimmed"
            val modelId = _selectedModelId.value.takeIf { it != AiModelOption.NANO_BANANA_2_1.modelId }
                ?: AiModelOption.FLASH_3_8.modelId
            try {
                aiRepo.streamChat(
                    modelId = modelId,
                    prompt = fullPrompt,
                    history = emptyList(),
                    preferredLanguage = profile?.languageEnum() ?: SupportedLanguage.ENGLISH,
                    memories = emptyList(),
                    memoryEnabled = false,
                    attachment = null,
                    webCitations = emptyList(),
                    domainSystemHint = "You are MT AI Code Architect. Output clean, well-structured, production-ready code with fenced markdown blocks."
                ).collect { partial ->
                    _codeLabState.value = CodeLabUiState(isLoading = true, outputMarkdown = partial)
                }
                _codeLabState.value = _codeLabState.value.copy(isLoading = false)
                if (profile != null) firestoreRepo.incrementUsage(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                firestoreRepo.logError("CodeLab", modelId, e.localizedMessage ?: e.toString())
                _codeLabState.value = CodeLabUiState(
                    isLoading = false,
                    outputMarkdown = _codeLabState.value.outputMarkdown,
                    error = friendlyAiError(e)
                )
            }
        }
    }

    // --- Admin Actions ---

    fun adminUpdateUser(target: UserProfile, plan: SubscriptionPlan, status: AccountStatus, role: UserRole) {
        viewModelScope.launch {
            firestoreRepo.adminUpdateUser(target.userId, target.email, plan, status, role)
                .onFailure { _chatNotice.value = it.localizedMessage ?: "Admin update failed." }
        }
    }

    fun adminSaveSystemConfig(config: SystemConfig) {
        viewModelScope.launch {
            firestoreRepo.adminUpdateSystemConfig(config)
                .onSuccess { _chatNotice.value = "System settings saved." }
                .onFailure { _chatNotice.value = it.localizedMessage ?: "Failed to save settings." }
        }
    }

    fun adminCreateAnnouncement(title: String, body: String, severity: AnnouncementSeverity) {
        viewModelScope.launch {
            firestoreRepo.adminCreateAnnouncement(title, body, severity)
                .onFailure { _chatNotice.value = it.localizedMessage ?: "Failed to publish announcement." }
        }
    }

    fun adminToggleAnnouncement(id: String, active: Boolean) {
        viewModelScope.launch { firestoreRepo.adminToggleAnnouncement(id, active) }
    }

    fun adminDeleteAnnouncement(id: String) {
        viewModelScope.launch { firestoreRepo.adminDeleteAnnouncement(id) }
    }

    fun adminResolveErrorLog(id: String, resolved: Boolean) {
        viewModelScope.launch { firestoreRepo.adminResolveErrorLog(id, resolved) }
    }

    fun adminDeleteErrorLog(id: String) {
        viewModelScope.launch { firestoreRepo.adminDeleteErrorLog(id) }
    }

    companion object {
        private const val TAG = "MtAiViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val firestoreRepo = FirestoreRepository(app)
                MtAiViewModel(
                    firestoreRepo = firestoreRepo,
                    aiRepo = AiRepository(),
                    webSearchService = WebSearchService(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
