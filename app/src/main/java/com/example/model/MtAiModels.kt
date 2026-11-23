package com.example.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val BOOTSTRAP_ADMIN_EMAIL = "mahidc67@gmail.com"

enum class UserRole {
    USER, ADMIN;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER
    }
}

enum class SubscriptionPlan {
    FREE, PRO;

    companion object {
        fun fromRaw(raw: String?): SubscriptionPlan =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FREE
    }
}

enum class AccountStatus {
    ACTIVE, SUSPENDED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    DARK("Cosmic Dark"),
    LIGHT("Daylight");

    companion object {
        fun fromRaw(raw: String?): ThemePreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

enum class AnnouncementSeverity {
    INFO, UPDATE, ALERT;

    companion object {
        fun fromRaw(raw: String?): AnnouncementSeverity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: INFO
    }
}

enum class AiModelOption(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val requiresPro: Boolean = false,
    val isImageModel: Boolean = false
) {
    FLASH_3_8(
        modelId = "gemini-3.8-flash",
        displayName = "MT Flash 3.8",
        badge = "Default",
        description = "Fast, frontier-class intelligence for chat, code, and multimodal files."
    ),
    FLASH_LITE_3_5(
        modelId = "gemini-3.5-flash-lite",
        displayName = "MT Lite 3.5",
        badge = "Ultra Fast",
        description = "Lowest latency for instant answers, summaries, and translations."
    ),
    ARGON_4(
        modelId = "gemini-4-argon",
        displayName = "MT Argon 4",
        badge = "Pro Reasoning",
        description = "Deep reasoning for complex math, STEM, architecture, and research.",
        requiresPro = true
    ),
    NANO_BANANA_2_1(
        modelId = "gemini-nano-banana-2.1",
        displayName = "MT Vision Art",
        badge = "Image Studio",
        description = "Text-to-image generation and AI photo editing.",
        requiresPro = true,
        isImageModel = true
    );

    companion object {
        fun chatModels(): List<AiModelOption> = entries.filter { !it.isImageModel }
        fun fromId(id: String?): AiModelOption =
            entries.firstOrNull { it.modelId == id } ?: FLASH_3_8
    }
}

enum class SupportedLanguage(val displayName: String, val localeTag: String, val nativeLabel: String) {
    ENGLISH("English", "en-US", "English"),
    BENGALI("Bengali", "bn-BD", "বাংলা"),
    ARABIC("Arabic", "ar-SA", "العربية"),
    HINDI("Hindi", "hi-IN", "हिन्दी"),
    SPANISH("Spanish", "es-ES", "Español"),
    FRENCH("French", "fr-FR", "Français");

    companion object {
        fun fromName(name: String?): SupportedLanguage =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: ENGLISH
    }
}

enum class ExploreDomain(
    val id: String,
    val title: String,
    val subtitle: String,
    val starterPrompt: String,
    val systemHint: String
) {
    GENERAL_KNOWLEDGE(
        id = "knowledge",
        title = "Knowledge & Research",
        subtitle = "Deep facts, global insights, verified explanations",
        starterPrompt = "Give me a structured research brief on quantum error correction and its practical timeline.",
        systemHint = "Provide accurate, structured general knowledge and cite clear reasoning."
    ),
    MATHEMATICS(
        id = "math",
        title = "Mathematics & Calculations",
        subtitle = "Step-by-step algebra, calculus, statistics & proofs",
        starterPrompt = "Solve and explain step-by-step: find the eigenvalues of [[4, 1], [2, 3]].",
        systemHint = "Show clear step-by-step mathematical derivations and verify final calculations."
    ),
    PROGRAMMING(
        id = "programming",
        title = "Software Engineering",
        subtitle = "Algorithms, debugging, clean architecture & APIs",
        starterPrompt = "Design a clean, production-grade rate limiter in Python with unit tests.",
        systemHint = "Provide clean, well-structured, production-ready code with explanations."
    ),
    WEB_DEV(
        id = "webdev",
        title = "Website Development",
        subtitle = "HTML, CSS, JavaScript, React, PHP & responsive UI",
        starterPrompt = "Build a responsive SaaS pricing page using semantic HTML5, modern CSS Grid, and vanilla JS.",
        systemHint = "Build complete, accessible, responsive web code with clear file breakdown."
    ),
    MOBILE_DEV(
        id = "mobile",
        title = "Mobile App Development",
        subtitle = "Android Kotlin, Jetpack Compose, SwiftUI & Flutter",
        starterPrompt = "Create a Jetpack Compose MVVM architecture blueprint with offline-first sync.",
        systemHint = "Follow modern mobile engineering patterns (MVVM, StateFlow, Material 3)."
    ),
    BUSINESS(
        id = "business",
        title = "Business Planning",
        subtitle = "Go-to-market, unit economics, pitch decks & strategy",
        starterPrompt = "Draft a B2B AI SaaS go-to-market plan including ICP, pricing tiers, and 90-day KPIs.",
        systemHint = "Act as a senior startup strategist with concrete metrics and actionable milestones."
    ),
    CONTENT_WRITING(
        id = "content",
        title = "Content Writing",
        subtitle = "Articles, blogs, marketing copy & scripts",
        starterPrompt = "Write an engaging product launch blog post introducing a next-gen AI workspace.",
        systemHint = "Write compelling, crisp, audience-tailored copy with strong structure."
    ),
    TRANSLATION(
        id = "translation",
        title = "Multilingual Translation",
        subtitle = "Bengali, English, Arabic, Hindi & global localization",
        starterPrompt = "Translate this into natural Bengali, Arabic, and Hindi with tone notes: 'Innovation begins when curiosity meets intelligence.'",
        systemHint = "Provide natural, culturally accurate translations preserving nuance and tone."
    ),
    DOCUMENT_ANALYSIS(
        id = "docs",
        title = "Document Analysis",
        subtitle = "PDF, DOCX, XLSX, CSV extraction & insights",
        starterPrompt = "I'm ready to analyze a document — tell me what formats (PDF, DOCX, XLSX, CSV, TXT) I can upload and how you inspect them.",
        systemHint = "Analyze uploaded documents thoroughly, extracting key figures, risks, and summaries."
    ),
    EDUCATION(
        id = "education",
        title = "Educational Tutor",
        subtitle = "Interactive study guides, quizzes & concept mastery",
        starterPrompt = "Teach me how Transformer self-attention works using an intuitive analogy, then quiz me with 3 questions.",
        systemHint = "Act as a patient Socratic tutor adapting explanations to the learner's level."
    ),
    DATA_ANALYSIS(
        id = "data",
        title = "Data Analysis",
        subtitle = "CSV/Excel insights, SQL queries & KPI trends",
        starterPrompt = "Write a SQL and Python pandas workflow to detect revenue anomalies and cohort churn.",
        systemHint = "Deliver precise analytical insights, formulas, and actionable interpretations."
    ),
    CREATIVE_WRITING(
        id = "creative",
        title = "Creative Writing",
        subtitle = "Stories, worldbuilding, poetry & brand narratives",
        starterPrompt = "Write an atmospheric opening scene of a sci-fi novella set inside an orbital observatory.",
        systemHint = "Craft vivid, original prose with strong pacing and imagery."
    ),
    TROUBLESHOOTING(
        id = "troubleshoot",
        title = "Technical Troubleshooting",
        subtitle = "Root-cause diagnostics for crashes, servers & builds",
        starterPrompt = "Walk me through a systematic checklist for diagnosing HTTP 502 Bad Gateway errors in production.",
        systemHint = "Diagnose issues methodically from symptoms to root cause and concrete fixes."
    ),
    SUMMARIZATION(
        id = "summary",
        title = "Executive Summarization",
        subtitle = "Distill long reports into TL;DR & action items",
        starterPrompt = "Show me your executive summary template (TL;DR, Key Decisions, Risks, Next Steps) and apply it to any text I paste.",
        systemHint = "Produce concise bulleted executive summaries highlighting key facts and actions."
    ),
    COMMUNICATION(
        id = "comms",
        title = "Professional Communication",
        subtitle = "Emails, proposals, negotiation & executive memos",
        starterPrompt = "Draft a tactful, high-clarity email proposing a timeline adjustment to an enterprise client.",
        systemHint = "Write polished, diplomatic, concise professional correspondence."
    )
}

enum class CodingTaskPreset(val title: String, val instruction: String) {
    BUILD_WEBSITE("Website Project", "Build a complete, responsive website structure with clean HTML, CSS, and JS."),
    DEBUG_ERROR("Debug Error", "Identify the exact root cause of the error, explain why it occurs, and provide the fixed code."),
    EXPLAIN_CODE("Explain Code", "Explain the code line-by-line, time/space complexity, and edge cases."),
    DB_DESIGN("Database Design", "Design a normalized relational or Firestore schema with indexes and security considerations."),
    API_INTEGRATION("API Integration", "Generate production-safe client/server API integration code with error handling and retries."),
    PROJECT_STRUCTURE("Project Structure", "Generate an idiomatic, scalable folder architecture and key boilerplate files.")
}

enum class ImageAspectOption(val label: String) {
    SQUARE("1:1"),
    LANDSCAPE("16:9"),
    PORTRAIT("9:16"),
    CLASSIC("4:3"),
    TALL("3:4")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = UserRole.USER.name,
    val plan: String = SubscriptionPlan.FREE.name,
    val status: String = AccountStatus.ACTIVE.name,
    val preferredLanguage: String = SupportedLanguage.ENGLISH.displayName,
    val themePreference: String = ThemePreference.DARK.name,
    val memoryEnabled: Boolean = true,
    val preferredModel: String = AiModelOption.FLASH_3_8.modelId,
    val dailyRequestsUsed: Long = 0L,
    val lastRequestDate: String = "",
    val totalRequests: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): UserRole = UserRole.fromRaw(role)
    fun planEnum(): SubscriptionPlan = SubscriptionPlan.fromRaw(plan)
    fun statusEnum(): AccountStatus = AccountStatus.fromRaw(status)
    fun themeEnum(): ThemePreference = ThemePreference.fromRaw(themePreference)
    fun languageEnum(): SupportedLanguage = SupportedLanguage.fromName(preferredLanguage)
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val modelId: String = AiModelOption.FLASH_3_8.modelId,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val modelId: String = AiModelOption.FLASH_3_8.modelId,
    val edited: Boolean = false,
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val citations: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryFact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fact: String = "",
    val category: String = "General",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SystemConfig(
    @DocumentId val id: String = "",
    val defaultModel: String = AiModelOption.FLASH_3_8.modelId,
    val enabledModels: List<String> = AiModelOption.entries.map { it.modelId },
    val freeDailyLimit: Long = 25L,
    val proDailyLimit: Long = 500L,
    val fallbackEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val severity: String = AnnouncementSeverity.INFO.name,
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun severityEnum(): AnnouncementSeverity = AnnouncementSeverity.fromRaw(severity)
}

@Keep
@IgnoreExtraProperties
data class ErrorLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val source: String = "",
    val modelId: String = "",
    val message: String = "",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class WebSearchCitation(
    val title: String,
    val snippet: String,
    val url: String
)

data class PendingAttachment(
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val bitmap: Bitmap? = null,
    val extractedText: String? = null,
    val pdfBytes: ByteArray? = null
) {
    enum class AttachmentKind { IMAGE, PDF, DOCUMENT }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
