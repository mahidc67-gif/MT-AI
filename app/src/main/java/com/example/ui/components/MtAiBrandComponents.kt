package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Announcement
import com.example.model.AnnouncementSeverity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MtCyanPrimary
import com.example.ui.theme.MtIndigoAccent
import com.example.ui.theme.MtVioletGlow
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRose

@Composable
fun MtAiLogoBadge(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Color(0xFF010102))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MtCyanPrimary.copy(alpha = 0.7f),
                        MtVioletGlow.copy(alpha = 0.7f)
                    )
                ),
                shape = RoundedCornerShape(size * 0.26f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_mt_logo_emblem),
            contentDescription = "MT AI logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun ThinkingPulseOrb(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(MtCyanPrimary, MtIndigoAccent))
                )
        )
        Text(
            text = "MT AI is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AnnouncementBanner(
    announcement: Announcement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (announcement.severityEnum()) {
        AnnouncementSeverity.ALERT -> StatusRose
        AnnouncementSeverity.UPDATE -> StatusEmerald
        AnnouncementSeverity.INFO -> MtCyanPrimary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = "Announcement",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss announcement",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

@Composable
fun MarkdownMessageView(
    markdown: String,
    onDownloadCode: (code: String, language: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(markdown) { splitMarkdownSegments(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    CodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        onDownload = { onDownloadCode(segment.code, segment.language) }
                    )
                }
                is MarkdownSegment.TextBlock -> {
                    MarkdownTextSection(text = segment.text)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onDownload: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0A101D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF233252))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121C33))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MtCyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MtCyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) StatusEmerald else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download code file",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightCode(code),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun MarkdownTextSection(text: String) {
    val lines = remember(text) { text.lines() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("### "), primaryColor, codeBg),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("## "), primaryColor, codeBg),
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("# "), primaryColor, codeBg),
                        style = MaterialTheme.typography.headlineSmall,
                        color = textColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 6.dp)) {
                        Text("•  ", color = primaryColor, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = formatInlineMarkdown(trimmed.drop(2), primaryColor, codeBg),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
                trimmed.isNotBlank() -> {
                    Text(
                        text = formatInlineMarkdown(rawLine, primaryColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun formatInlineMarkdown(
    line: String,
    linkColor: Color,
    inlineCodeBg: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(line.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(line[i])
                    i++
                }
            }
            line[i] == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            background = inlineCodeBg,
                            color = linkColor
                        )
                    ) {
                        append(" ${line.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(line[i])
                    i++
                }
            }
            line[i] == '*' -> {
                val end = line.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(line.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(line[i])
                    i++
                }
            }
            line[i] == '[' -> {
                val closeBracket = line.indexOf(']', i + 1)
                val openParen = if (closeBracket != -1 && closeBracket + 1 < line.length && line[closeBracket + 1] == '(') closeBracket + 1 else -1
                val closeParen = if (openParen != -1) line.indexOf(')', openParen + 1) else -1
                if (closeBracket != -1 && openParen != -1 && closeParen != -1) {
                    val label = line.substring(i + 1, closeBracket)
                    val url = line.substring(openParen + 1, closeParen).trim()
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            )
                        ) {
                            append(label)
                        }
                    } else {
                        append(label)
                    }
                    i = closeParen + 1
                } else {
                    append(line[i])
                    i++
                }
            }
            else -> {
                append(line[i])
                i++
            }
        }
    }
}

private fun splitMarkdownSegments(input: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val regex = Regex("```([a-zA-Z0-9_+\\-#]*)\\n([\\s\\S]*?)```")
    var lastEnd = 0
    for (match in regex.findAll(input)) {
        if (match.range.first > lastEnd) {
            val textPart = input.substring(lastEnd, match.range.first).trim()
            if (textPart.isNotEmpty()) result.add(MarkdownSegment.TextBlock(textPart))
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        result.add(MarkdownSegment.CodeBlock(lang, code))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < input.length) {
        val tail = input.substring(lastEnd).trim()
        if (tail.isNotEmpty()) result.add(MarkdownSegment.TextBlock(tail))
    }
    if (result.isEmpty() && input.isNotBlank()) {
        result.add(MarkdownSegment.TextBlock(input))
    }
    return result
}

private val codeKeywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when",
    "for", "while", "import", "package", "def", "async", "await", "function", "const",
    "let", "export", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "php",
    "echo", "public", "private", "suspend", "data", "true", "false", "null", "None"
)

private fun highlightCode(code: String): AnnotatedString = buildAnnotatedString {
    val lines = code.lines()
    lines.forEachIndexed { idx, line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
            withStyle(SpanStyle(color = Color(0xFF64748B))) {
                append(line)
            }
        } else {
            val tokenRegex = Regex("(\"[^\"]*\"|'[^']*'|\\b[A-Za-z_][A-Za-z0-9_]*\\b|\\b\\d+\\b)")
            var cursor = 0
            for (m in tokenRegex.findAll(line)) {
                if (m.range.first > cursor) {
                    withStyle(SpanStyle(color = Color(0xFFE2E8F0))) {
                        append(line.substring(cursor, m.range.first))
                    }
                }
                val token = m.value
                val color = when {
                    token.startsWith("\"") || token.startsWith("'") -> StatusEmerald
                    token in codeKeywords -> MtCyanPrimary
                    token.all { it.isDigit() } -> StatusAmber
                    else -> Color(0xFFE2E8F0)
                }
                withStyle(SpanStyle(color = color)) {
                    append(token)
                }
                cursor = m.range.last + 1
            }
            if (cursor < line.length) {
                withStyle(SpanStyle(color = Color(0xFFE2E8F0))) {
                    append(line.substring(cursor))
                }
            }
        }
        if (idx < lines.lastIndex) append('\n')
    }
}
