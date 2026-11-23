package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.example.model.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.max

object DocumentParser {

    private const val MAX_IMAGE_DIMENSION = 1536
    private const val MAX_PDF_BYTES = 14 * 1024 * 1024
    private const val MAX_TEXT_CHARS = 36_000

    suspend fun loadAttachment(context: Context, uri: Uri): Result<PendingAttachment> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = resolveFileName(context, uri)
            val lowerName = displayName.lowercase()

            when {
                mimeType.startsWith("image/") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") -> {
                    val bitmap = decodeScaledBitmap(context, uri)
                        ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode selected image."))
                    Result.success(
                        PendingAttachment(
                            displayName = displayName,
                            mimeType = mimeType,
                            kind = PendingAttachment.AttachmentKind.IMAGE,
                            bitmap = bitmap
                        )
                    )
                }

                mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                    val bytes = resolver.openInputStream(uri)?.use { readBoundedBytes(it, MAX_PDF_BYTES) }
                        ?: return@withContext Result.failure(IllegalArgumentException("Unable to open PDF file."))
                    Result.success(
                        PendingAttachment(
                            displayName = displayName,
                            mimeType = "application/pdf",
                            kind = PendingAttachment.AttachmentKind.PDF,
                            pdfBytes = bytes
                        )
                    )
                }

                lowerName.endsWith(".docx") ||
                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                    val text = resolver.openInputStream(uri)?.use { extractDocxText(it) }
                        ?: return@withContext Result.failure(IllegalArgumentException("Unable to read DOCX document."))
                    Result.success(
                        PendingAttachment(
                            displayName = displayName,
                            mimeType = mimeType,
                            kind = PendingAttachment.AttachmentKind.DOCUMENT,
                            extractedText = text.ifBlank { "[Empty DOCX document]" }.take(MAX_TEXT_CHARS)
                        )
                    )
                }

                lowerName.endsWith(".xlsx") ||
                    mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> {
                    val text = resolver.openInputStream(uri)?.use { extractXlsxText(it) }
                        ?: return@withContext Result.failure(IllegalArgumentException("Unable to read XLSX spreadsheet."))
                    Result.success(
                        PendingAttachment(
                            displayName = displayName,
                            mimeType = mimeType,
                            kind = PendingAttachment.AttachmentKind.DOCUMENT,
                            extractedText = text.ifBlank { "[Empty XLSX workbook]" }.take(MAX_TEXT_CHARS)
                        )
                    )
                }

                else -> {
                    val text = resolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText().take(MAX_TEXT_CHARS)
                    } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read file."))
                    Result.success(
                        PendingAttachment(
                            displayName = displayName,
                            mimeType = mimeType,
                            kind = PendingAttachment.AttachmentKind.DOCUMENT,
                            extractedText = text.ifBlank { "[Empty text file]" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxSide / sampleSize > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    private fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("File exceeds 14 MB limit for inline AI analysis.")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun extractDocxText(input: InputStream): String {
        val sb = StringBuilder()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "p" && sb.isNotEmpty()) sb.append('\n')
                            }
                            XmlPullParser.TEXT -> {
                                sb.append(parser.text)
                            }
                        }
                        if (sb.length >= MAX_TEXT_CHARS) break
                        event = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun extractXlsxText(input: InputStream): String {
        val sharedStrings = mutableListOf<String>()
        val sheets = linkedMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStrings.addAll(parseSharedStrings(zip))
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheets[name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        val sb = StringBuilder()
        sheets.entries.sortedBy { it.key }.forEach { (sheetName, xml) ->
            sb.append("--- ").append(sheetName.substringAfterLast('/')).append(" ---\n")
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var event = parser.eventType
            var cellType = ""
            var inValue = false
            var rowHasCells = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> rowHasCells = false
                            "c" -> cellType = parser.getAttributeValue(null, "t") ?: ""
                            "v", "t" -> inValue = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue) {
                            val raw = parser.text.orEmpty()
                            val cellVal = if (cellType == "s") {
                                raw.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx) } ?: raw
                            } else raw
                            if (rowHasCells) sb.append(" | ")
                            sb.append(cellVal)
                            rowHasCells = true
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v", "t" -> inValue = false
                            "row" -> if (rowHasCells) sb.append('\n')
                        }
                    }
                }
                if (sb.length >= MAX_TEXT_CHARS) break
                event = parser.next()
            }
            sb.append('\n')
        }
        return sb.toString().trim()
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        var current = StringBuilder()
        var inSi = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    inSi = true
                    current = StringBuilder()
                }
                XmlPullParser.TEXT -> if (inSi) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    inSi = false
                    list.add(current.toString())
                }
            }
            event = parser.next()
        }
        return list
    }
}
