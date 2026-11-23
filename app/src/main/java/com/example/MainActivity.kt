package com.example

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.attemptAutoSignIn
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOut
import com.example.model.PendingAttachment
import com.example.model.SupportedLanguage
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.CodeLabScreen
import com.example.ui.screens.ExploreFeaturesScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.MtRoutes
import com.example.ui.screens.SidebarDrawerContent
import com.example.ui.screens.UserDashboardScreen
import com.example.ui.theme.MtAiTheme
import com.example.viewmodel.MtAiViewModel
import com.example.voice.VoiceAssistantManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent BEFORE Compose UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MtAiRootApp()
        }
    }
}

@Composable
fun MtAiRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        MtAiTheme(themePreference = ThemePreference.DARK) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            AuthenticatedMtAiWorkspace(
                currentUserId = user.uid,
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMtAiWorkspace(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val viewModel: MtAiViewModel = viewModel(
        key = currentUserId,
        factory = MtAiViewModel.provideFactory(currentUserId)
    )

    val voiceManager = remember(context) { VoiceAssistantManager(context) }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.destroy() }
    }

    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val userProfile = (userProfileState as? UiState.Success)?.data
    val themePreference = userProfile?.themeEnum() ?: ThemePreference.DARK
    val systemConfig by viewModel.systemConfig.collectAsStateWithLifecycle()
    val announcements by viewModel.activeAnnouncements.collectAsStateWithLifecycle()
    val dismissedAnnouncements by viewModel.dismissedAnnouncementIds.collectAsStateWithLifecycle()
    val filteredConversations by viewModel.filteredConversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messagesState by viewModel.activeMessages.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val followUps by viewModel.followUps.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val activeDomain by viewModel.activeDomain.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val chatNotice by viewModel.chatNotice.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminUser.collectAsStateWithLifecycle()
    val imageStudioState by viewModel.imageStudioState.collectAsStateWithLifecycle()
    val codeLabState by viewModel.codeLabState.collectAsStateWithLifecycle()
    val adminUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
    val adminAnnouncements by viewModel.adminAnnouncements.collectAsStateWithLifecycle()
    val adminErrorLogs by viewModel.adminErrorLogs.collectAsStateWithLifecycle()

    val isListeningVoice by voiceManager.isListening.collectAsStateWithLifecycle()
    val partialTranscript by voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val rmsLevel by voiceManager.rmsLevel.collectAsStateWithLifecycle()
    val speakingMessageId by voiceManager.speakingMessageId.collectAsStateWithLifecycle()
    val voiceError by voiceManager.voiceError.collectAsStateWithLifecycle()

    var draftPrompt by remember { mutableStateOf("") }
    var pendingCodeDownload by remember { mutableStateOf<String?>(null) }

    val saveCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingCodeDownload
        if (uri != null && content != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
        }
        pendingCodeDownload = null
    }

    val onDownloadCode: (String, String) -> Unit = { code, lang ->
        pendingCodeDownload = code
        val ext = when (lang.lowercase()) {
            "kotlin", "kt" -> "kt"
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "html" -> "html"
            "css" -> "css"
            "php" -> "php"
            "sql" -> "sql"
            else -> "txt"
        }
        saveCodeLauncher.launch("mt_ai_snippet.$ext")
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MtRoutes.CHAT

    val navigateTo: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(MtRoutes.CHAT) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        scope.launch { drawerState.close() }
    }

    MtAiTheme(themePreference = themePreference) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 840.dp

            val drawerContent: @Composable () -> Unit = {
                SidebarDrawerContent(
                    currentRoute = currentRoute,
                    userProfile = userProfile,
                    isAdmin = isAdmin,
                    conversations = filteredConversations,
                    activeConversationId = activeConversationId,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onNewChat = viewModel::startNewChat,
                    onSelectConversation = viewModel::selectConversation,
                    onRenameConversation = viewModel::renameConversation,
                    onPinConversation = viewModel::togglePinConversation,
                    onDeleteConversation = viewModel::deleteConversation,
                    onNavigate = navigateTo
                )
            }

            val mainScaffold: @Composable () -> Unit = {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                val title = when (currentRoute) {
                                    MtRoutes.EXPLORE -> "Explore Capabilities"
                                    MtRoutes.IMAGE_STUDIO -> "Image Intelligence"
                                    MtRoutes.CODE_LAB -> "AI Code Lab"
                                    MtRoutes.DASHBOARD -> "Dashboard & Settings"
                                    MtRoutes.ADMIN -> "Admin Console"
                                    else -> "MT AI"
                                }
                                Text(title, style = MaterialTheme.typography.titleLarge)
                            },
                            navigationIcon = {
                                if (!isExpandedScreen) {
                                    if (currentRoute != MtRoutes.CHAT) {
                                        IconButton(onClick = { navController.navigateUp() }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier.testTag("open_drawer_button")
                                        ) {
                                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                                        }
                                    }
                                }
                            },
                            actions = {
                                if (currentRoute == MtRoutes.CHAT) {
                                    IconButton(
                                        onClick = viewModel::startNewChat,
                                        modifier = Modifier.testTag("topbar_new_chat_button")
                                    ) {
                                        Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = MtRoutes.CHAT,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(MtRoutes.CHAT) {
                            ChatWorkspaceScreen(
                                userProfile = userProfile,
                                systemConfig = systemConfig,
                                announcements = announcements,
                                dismissedAnnouncementIds = dismissedAnnouncements,
                                messagesState = messagesState,
                                streamingText = streamingText,
                                isGenerating = isGenerating,
                                followUps = followUps,
                                selectedModelId = selectedModelId,
                                webSearchEnabled = webSearchEnabled,
                                activeDomain = activeDomain,
                                pendingAttachment = pendingAttachment,
                                chatNotice = chatNotice,
                                isListeningVoice = isListeningVoice,
                                partialVoiceTranscript = partialTranscript,
                                voiceRmsLevel = rmsLevel,
                                speakingMessageId = speakingMessageId,
                                voiceError = voiceError,
                                draftPrompt = draftPrompt,
                                onDraftPromptChange = { draftPrompt = it },
                                onClearNotice = viewModel::clearNotice,
                                onDismissAnnouncement = viewModel::dismissAnnouncement,
                                onSelectModel = viewModel::selectModel,
                                onToggleWebSearch = viewModel::toggleWebSearch,
                                onClearDomain = { viewModel.selectDomain(null) },
                                onAttachUri = { uri -> viewModel.attachFile(context, uri) },
                                onClearAttachment = viewModel::clearAttachment,
                                onSend = viewModel::sendMessage,
                                onRegenerate = viewModel::regenerateLastResponse,
                                onEditMessage = viewModel::editAndResendMessage,
                                onDownloadCode = onDownloadCode,
                                onStartVoiceInput = {
                                    val lang = userProfile?.languageEnum() ?: SupportedLanguage.ENGLISH
                                    voiceManager.startListening(lang) { recognized ->
                                        draftPrompt = recognized
                                    }
                                },
                                onStopVoiceInput = voiceManager::stopListening,
                                onSpeakMessage = { msgId, text ->
                                    val lang = userProfile?.languageEnum() ?: SupportedLanguage.ENGLISH
                                    voiceManager.speak(msgId, text, lang)
                                },
                                onStopSpeaking = voiceManager::stopSpeaking,
                                onClearVoiceError = voiceManager::clearError
                            )
                        }

                        composable(MtRoutes.EXPLORE) {
                            ExploreFeaturesScreen(
                                onLaunchDomain = { domain ->
                                    viewModel.selectDomain(domain)
                                    viewModel.startNewChat()
                                    draftPrompt = domain.starterPrompt
                                    navigateTo(MtRoutes.CHAT)
                                }
                            )
                        }

                        composable(MtRoutes.IMAGE_STUDIO) {
                            ImageStudioScreen(
                                uiState = imageStudioState,
                                onGenerateOrEdit = viewModel::generateOrEditImage,
                                onSendImageToChatVision = { bmp: Bitmap, promptText: String ->
                                    viewModel.startNewChat()
                                    draftPrompt = promptText
                                    // Attach via temp bitmap file or directly send vision prompt:
                                    // Save bitmap to temp cache Uri so DocumentParser attaches cleanly
                                    val tempFile = java.io.File(context.cacheDir, "vision_input_${System.currentTimeMillis()}.png")
                                    tempFile.outputStream().use { out ->
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    viewModel.attachFile(context, android.net.Uri.fromFile(tempFile))
                                    navigateTo(MtRoutes.CHAT)
                                }
                            )
                        }

                        composable(MtRoutes.CODE_LAB) {
                            CodeLabScreen(
                                uiState = codeLabState,
                                onRunTask = viewModel::runCodeLabTask,
                                onDownloadCode = onDownloadCode
                            )
                        }

                        composable(MtRoutes.DASHBOARD) {
                            UserDashboardScreen(
                                profile = userProfile,
                                systemConfig = systemConfig,
                                memories = memories,
                                isAdmin = isAdmin,
                                onUpdateTheme = viewModel::updateTheme,
                                onUpdateLanguage = viewModel::updateLanguage,
                                onToggleMemoryEnabled = viewModel::updateMemoryEnabled,
                                onAddMemory = viewModel::addMemory,
                                onToggleMemoryItem = viewModel::toggleMemory,
                                onDeleteMemory = viewModel::deleteMemory,
                                onOpenAdmin = { navigateTo(MtRoutes.ADMIN) },
                                onSignOut = onSignOut
                            )
                        }

                        composable(MtRoutes.ADMIN) {
                            AdminDashboardScreen(
                                isAdmin = isAdmin,
                                usersState = adminUsers,
                                systemConfig = systemConfig,
                                announcementsState = adminAnnouncements,
                                errorLogsState = adminErrorLogs,
                                onUpdateUser = viewModel::adminUpdateUser,
                                onSaveSystemConfig = viewModel::adminSaveSystemConfig,
                                onCreateAnnouncement = viewModel::adminCreateAnnouncement,
                                onToggleAnnouncement = viewModel::adminToggleAnnouncement,
                                onDeleteAnnouncement = viewModel::adminDeleteAnnouncement,
                                onResolveError = viewModel::adminResolveErrorLog,
                                onDeleteError = viewModel::adminDeleteErrorLog
                            )
                        }
                    }
                }
            }

            if (isExpandedScreen) {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(modifier = Modifier.width(300.dp)) {
                            drawerContent()
                        }
                    },
                    content = mainScaffold
                )
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                            drawerContent()
                        }
                    },
                    content = mainScaffold
                )
            }
        }
    }
}
