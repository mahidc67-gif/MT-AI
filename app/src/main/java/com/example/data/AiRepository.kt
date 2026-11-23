package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiModelOption
import com.example.model.ChatMessage
import com.example.model.ImageAspectOption
import com.example.model.MemoryFact
import com.example.model.PendingAttachment
import com.example.model.SupportedLanguage
import com.example.model.WebSearchCitation
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AiRepository {

    private val followUpModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "suggestions" to Schema.array(
                    items = Schema.string(),
                    description = "Exactly 3 concise follow-up questions the user might ask next."
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = AiModelOption.FLASH_LITE_3_5.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun streamChat(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        preferredLanguage: SupportedLanguage,
        memories: List<MemoryFact>,
        memoryEnabled: Boolean,
        attachment: PendingAttachment?,
        webCitations: List<WebSearchCitation>,
        domainSystemHint: String?
    ): Flow<String> = flow {
        val systemInstructionText = buildSystemInstruction(
            preferredLanguage = preferredLanguage,
            memories = memories,
            memoryEnabled = memoryEnabled,
            webCitations = webCitations,
            domainSystemHint = domainSystemHint
        )

        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = content { text(systemInstructionText) }
        )

        val sdkHistory: List<Content> = history
            .takeLast(20)
            .filter { it.content.isNotBlank() }
            .map { msg ->
                content(role = if (msg.role == "model") "model" else "user") {
                    text(msg.content)
                }
            }

        val userContent = content(role = "user") {
            if (attachment != null) {
                when (attachment.kind) {
                    PendingAttachment.AttachmentKind.IMAGE -> {
                        attachment.bitmap?.let { image(it) }
                    }
                    PendingAttachment.AttachmentKind.PDF -> {
                        attachment.pdfBytes?.let { bytes ->
                            inlineData(bytes = bytes, mimeType = "application/pdf")
                        }
                    }
                    PendingAttachment.AttachmentKind.DOCUMENT -> {
                        val docBody = attachment.extractedText.orEmpty()
                        text("Attached document (${attachment.displayName}):\n```\n$docBody\n```")
                    }
                }
            }
            if (webCitations.isNotEmpty()) {
                val sourcesBlock = webCitations.mapIndexed { index, cite ->
                    "[${index + 1}] ${cite.title} (${cite.url}): ${cite.snippet}"
                }.joinToString("\n")
                text("Live Web Search Results:\n$sourcesBlock\n\nUse these verified sources where relevant, cite their [number] and URL, and clearly distinguish verified facts from general knowledge.")
            }
            text(prompt)
        }

        val chat = model.startChat(history = sdkHistory)
        val accumulated = StringBuilder()

        withTimeout(60_000L) {
            chat.sendMessageStream(userContent).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("MT AI returned an empty response. Please try again.")
        }
    }

    suspend fun generateFollowUps(lastUserPrompt: String, lastModelReply: String): List<String> {
        return try {
            val response = withTimeout(30_000L) {
                followUpModel.generateContent(
                    "Based on this user question: '${lastUserPrompt.take(400)}' and assistant answer: '${lastModelReply.take(800)}', provide 3 short, helpful follow-up questions."
                )
            }
            val raw = response.text ?: return emptyList()
            val arr = JSONObject(raw).optJSONArray("suggestions") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 3)) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Follow-up generation skipped: ${e.message}")
            emptyList()
        }
    }

    suspend fun generateOrEditImage(
        prompt: String,
        aspectOption: ImageAspectOption,
        sourceBitmap: Bitmap? = null
    ): Result<Bitmap> {
        return try {
            val aspectRatio = when (aspectOption) {
                ImageAspectOption.SQUARE -> AspectRatio.SQUARE_1x1
                ImageAspectOption.LANDSCAPE -> AspectRatio.LANDSCAPE_16x9
                ImageAspectOption.PORTRAIT -> AspectRatio.PORTRAIT_9x16
                ImageAspectOption.CLASSIC -> AspectRatio.LANDSCAPE_4x3
                ImageAspectOption.TALL -> AspectRatio.PORTRAIT_3x4
            }
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(aspectRatio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
            val imageModel = Firebase.ai.generativeModel(
                modelName = AiModelOption.NANO_BANANA_2_1.modelId,
                generationConfig = config
            )
            val response = withTimeout(60_000L) {
                if (sourceBitmap != null) {
                    imageModel.generateContent(
                        content {
                            image(sourceBitmap)
                            text(prompt)
                        }
                    )
                } else {
                    imageModel.generateContent(prompt)
                }
            }
            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
                ?.image
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("No image was returned by the model."))
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Image generation timed out after 60 seconds."))
        } catch (e: QuotaExceededException) {
            Result.failure(Exception("Image generation quota exceeded or Blaze billing plan required."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(e)
        }
    }

    private fun buildSystemInstruction(
        preferredLanguage: SupportedLanguage,
        memories: List<MemoryFact>,
        memoryEnabled: Boolean,
        webCitations: List<WebSearchCitation>,
        domainSystemHint: String?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You are MT AI — tagline: 'Your World, Your Intelligence.'")
        sb.appendLine("You are a helpful, accurate, multilingual next-generation AI assistant.")
        sb.appendLine("Format responses cleanly using Markdown headings, lists, and fenced code blocks with language tags (e.g. ```kotlin, ```python, ```html, ```javascript, ```php).")
        sb.appendLine("User's preferred default language is ${preferredLanguage.displayName} (${preferredLanguage.nativeLabel}), though always respond in whichever language the user writes in unless asked to translate.")

        if (!domainSystemHint.isNullOrBlank()) {
            sb.appendLine("Active Specialist Focus: $domainSystemHint")
        }

        if (memoryEnabled) {
            val activeMemories = memories.filter { it.active }
            if (activeMemories.isNotEmpty()) {
                sb.appendLine("User-Approved Memory Facts (use seamlessly when relevant):")
                activeMemories.take(25).forEach { mem ->
                    sb.appendLine("- [${mem.category}] ${mem.fact}")
                }
            }
        }

        if (webCitations.isEmpty()) {
            sb.appendLine("Important Honesty Rule: Live web search was NOT run for this turn. Do NOT pretend to have browsed the internet in real time; clearly note if information may be post-cutoff or unverified.")
        } else {
            sb.appendLine("Live Web Search WAS executed for this turn. Ground your claims in the provided search results, include clickable markdown source citations, and distinguish verified facts from uncertain details.")
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "AiRepository"
    }
}
