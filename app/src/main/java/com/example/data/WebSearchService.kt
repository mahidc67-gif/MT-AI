package com.example.data

import android.util.Log
import com.example.model.WebSearchCitation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun searchWeb(query: String): Result<List<WebSearchCitation>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val encoded = URLEncoder.encode(trimmed, "UTF-8")

            val results = coroutineScope {
                val wikiDeferred = async { fetchWikipediaResults(encoded) }
                val ddgDeferred = async { fetchDuckDuckGoResults(encoded) }
                val wiki = wikiDeferred.await()
                val ddg = ddgDeferred.await()
                (ddg + wiki).distinctBy { it.url }.take(6)
            }

            Result.success(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WebSearchService", "Live web search failed", e)
            Result.failure(e)
        }
    }

    private fun fetchWikipediaResults(encodedQuery: String): List<WebSearchCitation> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1&srlimit=4"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MT-AI-Android/1.0 (https://mtai.app)")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val searchArray = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            buildList {
                for (i in 0 until searchArray.length()) {
                    val item = searchArray.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val rawSnippet = item.optString("snippet")
                    val cleanSnippet = rawSnippet
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&#039;", "'")
                        .trim()
                    if (title.isNotEmpty()) {
                        val slug = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                        add(
                            WebSearchCitation(
                                title = "Wikipedia: $title",
                                snippet = cleanSnippet,
                                url = "https://en.wikipedia.org/wiki/$slug"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun fetchDuckDuckGoResults(encodedQuery: String): List<WebSearchCitation> {
        val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MT-AI-Android/1.0")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val list = mutableListOf<WebSearchCitation>()

            val abstractText = json.optString("AbstractText").trim()
            val abstractUrl = json.optString("AbstractURL").trim()
            val heading = json.optString("Heading").trim()
            if (abstractText.isNotEmpty() && abstractUrl.startsWith("http")) {
                list.add(
                    WebSearchCitation(
                        title = heading.ifEmpty { "DuckDuckGo Summary" },
                        snippet = abstractText,
                        url = abstractUrl
                    )
                )
            }

            val topics = json.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until minOf(topics.length(), 4)) {
                    val obj = topics.optJSONObject(i) ?: continue
                    val text = obj.optString("Text").trim()
                    val firstUrl = obj.optString("FirstURL").trim()
                    if (text.isNotEmpty() && firstUrl.startsWith("http")) {
                        list.add(
                            WebSearchCitation(
                                title = text.substringBefore(" - ").take(80),
                                snippet = text.take(260),
                                url = firstUrl
                            )
                        )
                    }
                }
            }
            list
        }
    }
}
