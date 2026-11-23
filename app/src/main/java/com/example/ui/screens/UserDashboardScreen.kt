package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.MemoryFact
import com.example.model.SubscriptionPlan
import com.example.model.SupportedLanguage
import com.example.model.SystemConfig
import com.example.model.ThemePreference
import com.example.model.UserProfile
import com.example.ui.theme.StatusEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserDashboardScreen(
    profile: UserProfile?,
    systemConfig: SystemConfig,
    memories: List<MemoryFact>,
    isAdmin: Boolean,
    onUpdateTheme: (ThemePreference) -> Unit,
    onUpdateLanguage: (SupportedLanguage) -> Unit,
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onToggleMemoryItem: (String, Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newMemoryText by remember { mutableStateOf("") }
    var newMemoryCategory by remember { mutableStateOf("Preference") }
    var showBillingNotice by remember { mutableStateOf(false) }

    val dailyLimit = if (profile?.planEnum() == SubscriptionPlan.PRO || isAdmin) {
        systemConfig.proDailyLimit
    } else {
        systemConfig.freeDailyLimit
    }
    val used = profile?.dailyRequestsUsed ?: 0L
    val progress = (used.toFloat() / dailyLimit.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Account & Dashboard", style = MaterialTheme.typography.headlineMedium)

            // Profile Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!profile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.photoUrl,
                            contentDescription = "User avatar",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile?.displayName ?: "MT AI User",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = profile?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Role: ${profile?.role ?: "USER"} • Status: ${profile?.status ?: "ACTIVE"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusEmerald
                        )
                    }
                }
            }

            // Usage & Subscription Plan Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${profile?.plan ?: "FREE"} PLAN",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "Lifetime Requests: ${profile?.totalRequests ?: 0}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Daily AI Quota: $used / $dailyLimit requests used today",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )

                    if (profile?.planEnum() == SubscriptionPlan.FREE) {
                        OutlinedButton(onClick = { showBillingNotice = !showBillingNotice }) {
                            Text("View Pro Plan Info")
                        }
                        if (showBillingNotice) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Pro Plan includes ${systemConfig.proDailyLimit} daily requests, MT Argon 4 reasoning, and Image Studio. Payment gateway is not yet active; contact your administrator for Pro activation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Theme & Language Preferences
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Theme Preference", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = profile?.themeEnum() == pref,
                                onClick = { onUpdateTheme(pref) },
                                label = { Text(pref.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Language Preference", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = profile?.languageEnum() == lang,
                                onClick = { onUpdateLanguage(lang) },
                                label = { Text("${lang.displayName} (${lang.nativeLabel})") }
                            )
                        }
                    }
                }
            }

            // Conversation Memory Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Conversation Memory", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Allow MT AI to personalize replies using saved facts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = profile?.memoryEnabled ?: true,
                            onCheckedChange = onToggleMemoryEnabled
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            label = { Text("Add a memory fact (e.g. I prefer Kotlin)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newMemoryText.isNotBlank()) {
                                    onAddMemory(newMemoryText, newMemoryCategory)
                                    newMemoryText = ""
                                }
                            },
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add memory")
                        }
                    }

                    memories.forEach { mem ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mem.fact, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        mem.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = mem.active,
                                    onCheckedChange = { onToggleMemoryItem(mem.id, it) }
                                )
                                IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete memory")
                                }
                            }
                        }
                    }
                }
            }

            if (isAdmin) {
                Button(
                    onClick = onOpenAdmin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Admin Console (/admin)")
                }
            }

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Secure Logout")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
