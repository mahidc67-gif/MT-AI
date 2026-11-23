package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.AccountStatus
import com.example.model.AiModelOption
import com.example.model.Announcement
import com.example.model.AnnouncementSeverity
import com.example.model.BOOTSTRAP_ADMIN_EMAIL
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.ErrorLog
import com.example.model.MemoryFact
import com.example.model.SubscriptionPlan
import com.example.model.SupportedLanguage
import com.example.model.SystemConfig
import com.example.model.ThemePreference
import com.example.model.UserProfile
import com.example.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun todayUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun unwrapFirestoreError(error: Throwable): Throwable? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        var docPath = "users/unknown"
        return try {
            val user = auth.currentUser ?: error("Not authenticated")
            val uid = user.uid
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                val profile = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: error("Failed to parse user profile.")
                Result.success(profile)
            } else {
                val isBootstrapAdmin = user.isEmailVerified &&
                    user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)
                val today = todayUtc()
                val displayName = (user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "MT Explorer").take(100)
                val email = (user.email ?: "").take(150)
                val photoUrl = user.photoUrl?.toString()?.take(500)

                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "role" to if (isBootstrapAdmin) UserRole.ADMIN.name else UserRole.USER.name,
                    "plan" to if (isBootstrapAdmin) SubscriptionPlan.PRO.name else SubscriptionPlan.FREE.name,
                    "status" to AccountStatus.ACTIVE.name,
                    "preferredLanguage" to SupportedLanguage.ENGLISH.displayName,
                    "themePreference" to ThemePreference.DARK.name,
                    "memoryEnabled" to true,
                    "preferredModel" to AiModelOption.FLASH_3_8.modelId,
                    "dailyRequestsUsed" to 0L,
                    "lastRequestDate" to today,
                    "totalRequests" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                ref.set(payload).await()
                val created = ref.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = displayName,
                    email = email,
                    photoUrl = photoUrl,
                    role = if (isBootstrapAdmin) UserRole.ADMIN.name else UserRole.USER.name,
                    plan = if (isBootstrapAdmin) SubscriptionPlan.PRO.name else SubscriptionPlan.FREE.name,
                    lastRequestDate = today
                )
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val path = "users/$uid"
        return db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeSystemConfig(): Flow<SystemConfig> {
        val path = "system_config/settings"
        return db.collection("system_config").document("settings")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(SystemConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: SystemConfig()
                } else SystemConfig()
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                }
                emit(SystemConfig())
            }
    }

    fun observeActiveAnnouncements(): Flow<List<Announcement>> {
        val path = "announcements"
        return db.collection("announcements")
            .whereEqualTo("active", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users").document(uid).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt?.seconds ?: 0L })
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(uid: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMemories(uid: String): Flow<List<MemoryFact>> = flow {
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MemoryFact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(title: String, modelId: String): Result<String> {
        var docPath = "users/unknown/conversations"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("conversations").document()
            docPath = ref.path
            val safeTitle = title.trim().ifBlank { "New Chat" }.take(120)
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "modelId" to modelId.take(60),
                "lastMessagePreview" to "",
                "messageCount" to 0L,
                "pinned" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Unit> {
        var docPath = "users/unknown/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank())
            val safeTitle = newTitle.trim().ifBlank { "Untitled Chat" }.take(120)
            val ref = db.collection("users").document(uid).collection("conversations").document(conversationId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePinConversation(conversationId: String, pinned: Boolean): Result<Unit> {
        var docPath = "users/unknown/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("conversations").document(conversationId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var docPath = "users/unknown/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            docPath = convRef.path
            val msgs = convRef.collection("messages").get().await()
            if (!msgs.isEmpty) {
                val batch = db.batch()
                msgs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        modelId: String,
        attachmentName: String? = null,
        attachmentType: String? = null,
        citations: List<String> = emptyList()
    ): Result<String> {
        var docPath = "users/unknown/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document()
            docPath = msgRef.path
            val safeContent = content.take(50000)
            val safeCitations = citations.map { it.take(500) }.take(10)
            val payload = mapOf(
                "userId" to uid,
                "role" to if (role == "model") "model" else "user",
                "content" to safeContent,
                "modelId" to modelId.take(60),
                "edited" to false,
                "attachmentName" to attachmentName?.take(200),
                "attachmentType" to attachmentType?.take(60),
                "citations" to safeCitations,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            msgRef.set(payload).await()

            val preview = safeContent.replace("\n", " ").trim().take(280)
            convRef.update(
                mapOf(
                    "lastMessagePreview" to preview,
                    "modelId" to modelId.take(60),
                    "messageCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMessageContent(
        conversationId: String,
        messageId: String,
        content: String,
        modelId: String,
        edited: Boolean,
        citations: List<String> = emptyList()
    ): Result<Unit> {
        var docPath = "users/unknown/conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document(messageId)
            docPath = msgRef.path
            val safeContent = content.take(50000)
            msgRef.update(
                mapOf(
                    "content" to safeContent,
                    "edited" to edited,
                    "modelId" to modelId.take(60),
                    "citations" to citations.map { it.take(500) }.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            convRef.update(
                mapOf(
                    "lastMessagePreview" to safeContent.replace("\n", " ").trim().take(280),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMessagesAfter(conversationId: String, messageIdsToDelete: List<String>): Result<Unit> {
        if (messageIdsToDelete.isEmpty()) return Result.success(Unit)
        var docPath = "users/unknown/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            docPath = convRef.path
            val batch = db.batch()
            messageIdsToDelete.forEach { msgId ->
                batch.delete(convRef.collection("messages").document(msgId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMemory(fact: String, category: String): Result<Unit> {
        var docPath = "users/unknown/memories"
        return try {
            val uid = requireUserId()
            val safeFact = fact.trim().take(400)
            require(safeFact.isNotEmpty()) { "Memory fact cannot be empty." }
            val safeCategory = category.trim().ifBlank { "General" }.take(40)
            val ref = db.collection("users").document(uid).collection("memories").document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "fact" to safeFact,
                    "category" to safeCategory,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleMemory(memoryId: String, active: Boolean): Result<Unit> {
        var docPath = "users/unknown/memories/$memoryId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("memories").document(memoryId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var docPath = "users/unknown/memories/$memoryId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("memories").document(memoryId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        preferredLanguage: String? = null,
        themePreference: ThemePreference? = null,
        memoryEnabled: Boolean? = null,
        preferredModel: String? = null
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            preferredLanguage?.let { updates["preferredLanguage"] = it.take(40) }
            themePreference?.let { updates["themePreference"] = it.name }
            memoryEnabled?.let { updates["memoryEnabled"] = it }
            preferredModel?.let { updates["preferredModel"] = it.take(60) }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementUsage(currentProfile: UserProfile): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val today = todayUtc()
            val nextDaily = if (currentProfile.lastRequestDate == today) {
                currentProfile.dailyRequestsUsed + 1L
            } else 1L
            ref.update(
                mapOf(
                    "dailyRequestsUsed" to nextDaily,
                    "lastRequestDate" to today,
                    "totalRequests" to (currentProfile.totalRequests + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun logError(source: String, modelId: String, message: String) {
        try {
            val user = auth.currentUser ?: return
            val ref = db.collection("error_logs").document()
            ref.set(
                mapOf(
                    "userId" to user.uid,
                    "userEmail" to (user.email ?: "").take(150),
                    "source" to source.take(60),
                    "modelId" to modelId.take(60),
                    "message" to message.ifBlank { "Unknown error" }.take(1000),
                    "resolved" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirestoreRepo", "Could not write error log: ${e.message}")
        }
    }

    // --- ADMIN OPERATIONS ---

    fun observeAllUsers(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection("users")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.totalRequests }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllAnnouncements(): Flow<List<Announcement>> {
        val path = "announcements"
        return db.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeErrorLogs(): Flow<List<ErrorLog>> {
        val path = "error_logs"
        return db.collection("error_logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ErrorLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun adminUpdateUser(
        targetUid: String,
        targetEmail: String,
        plan: SubscriptionPlan,
        status: AccountStatus,
        role: UserRole
    ): Result<Unit> {
        var docPath = "users/$targetUid"
        return try {
            val adminUid = requireUserId()
            val userRef = db.collection("users").document(targetUid)
            docPath = userRef.path
            userRef.update(
                mapOf(
                    "plan" to plan.name,
                    "status" to status.name,
                    "role" to role.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val adminGrantRef = db.collection("admins").document(targetUid)
            if (role == UserRole.ADMIN) {
                adminGrantRef.set(
                    mapOf(
                        "uid" to targetUid,
                        "email" to targetEmail.take(150),
                        "grantedBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                adminGrantRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateSystemConfig(config: SystemConfig): Result<Unit> {
        val docPath = "system_config/settings"
        return try {
            val adminUid = requireUserId()
            val ref = db.collection("system_config").document("settings")
            val payload = mapOf(
                "defaultModel" to config.defaultModel.take(60),
                "enabledModels" to config.enabledModels.take(10).ifEmpty { listOf(AiModelOption.FLASH_3_8.modelId) },
                "freeDailyLimit" to config.freeDailyLimit.coerceIn(1L, 1000L),
                "proDailyLimit" to config.proDailyLimit.coerceIn(1L, 10000L),
                "fallbackEnabled" to config.fallbackEnabled,
                "maintenanceMode" to config.maintenanceMode,
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminCreateAnnouncement(
        title: String,
        body: String,
        severity: AnnouncementSeverity
    ): Result<Unit> {
        var docPath = "announcements"
        return try {
            val adminUid = requireUserId()
            val safeTitle = title.trim().take(120)
            val safeBody = body.trim().take(1000)
            require(safeTitle.isNotEmpty() && safeBody.isNotEmpty()) { "Title and body are required." }
            val ref = db.collection("announcements").document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "title" to safeTitle,
                    "body" to safeBody,
                    "severity" to severity.name,
                    "active" to true,
                    "createdBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminToggleAnnouncement(announcementId: String, active: Boolean): Result<Unit> {
        val docPath = "announcements/$announcementId"
        return try {
            requireUserId()
            db.collection("announcements").document(announcementId).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteAnnouncement(announcementId: String): Result<Unit> {
        val docPath = "announcements/$announcementId"
        return try {
            requireUserId()
            db.collection("announcements").document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminResolveErrorLog(logId: String, resolved: Boolean): Result<Unit> {
        val docPath = "error_logs/$logId"
        return try {
            requireUserId()
            db.collection("error_logs").document(logId).update("resolved", resolved).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteErrorLog(logId: String): Result<Unit> {
        val docPath = "error_logs/$logId"
        return try {
            requireUserId()
            db.collection("error_logs").document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
