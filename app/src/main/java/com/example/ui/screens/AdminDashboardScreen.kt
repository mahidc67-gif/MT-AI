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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.AccountStatus
import com.example.model.AiModelOption
import com.example.model.Announcement
import com.example.model.AnnouncementSeverity
import com.example.model.ErrorLog
import com.example.model.SubscriptionPlan
import com.example.model.SystemConfig
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRose

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    isAdmin: Boolean,
    usersState: UiState<List<UserProfile>>,
    systemConfig: SystemConfig,
    announcementsState: UiState<List<Announcement>>,
    errorLogsState: UiState<List<ErrorLog>>,
    onUpdateUser: (UserProfile, SubscriptionPlan, AccountStatus, UserRole) -> Unit,
    onSaveSystemConfig: (SystemConfig) -> Unit,
    onCreateAnnouncement: (String, String, AnnouncementSeverity) -> Unit,
    onToggleAnnouncement: (String, Boolean) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onResolveError: (String, Boolean) -> Unit,
    onDeleteError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Access denied",
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Access Denied (/admin)", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Administrator authorization is required to view this console.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Users", "Models & Limits", "Alerts & Errors")

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val announcements = (announcementsState as? UiState.Success)?.data.orEmpty()
    val errors = (errorLogsState as? UiState.Success)?.data.orEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("MT AI Admin Console (/admin)", style = MaterialTheme.typography.headlineSmall)
        }

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> AdminOverviewTab(users = users, errors = errors, systemConfig = systemConfig)
                    1 -> AdminUsersTab(users = users, onUpdateUser = onUpdateUser)
                    2 -> AdminModelsLimitsTab(systemConfig = systemConfig, onSaveSystemConfig = onSaveSystemConfig)
                    3 -> AdminAlertsErrorsTab(
                        announcements = announcements,
                        errors = errors,
                        onCreateAnnouncement = onCreateAnnouncement,
                        onToggleAnnouncement = onToggleAnnouncement,
                        onDeleteAnnouncement = onDeleteAnnouncement,
                        onResolveError = onResolveError,
                        onDeleteError = onDeleteError
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminOverviewTab(
    users: List<UserProfile>,
    errors: List<ErrorLog>,
    systemConfig: SystemConfig
) {
    val activeUsers = users.count { it.statusEnum() == AccountStatus.ACTIVE }
    val proUsers = users.count { it.planEnum() == SubscriptionPlan.PRO }
    val dailyRequests = users.sumOf { it.dailyRequestsUsed }
    val totalRequests = users.sumOf { it.totalRequests }
    val unresolvedErrors = errors.count { !it.resolved }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KpiCard("Registered Users", "${users.size}")
        KpiCard("Active Users", "$activeUsers")
        KpiCard("Pro Plan Users", "$proUsers")
        KpiCard("Daily AI Requests", "$dailyRequests")
        KpiCard("Total AI Requests", "$totalRequests")
        KpiCard("Open Errors", "$unresolvedErrors")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Platform Health & Active Limits", style = MaterialTheme.typography.titleMedium)
            Text("Default Model: ${systemConfig.defaultModel}", style = MaterialTheme.typography.bodyMedium)
            Text("Free Daily Limit: ${systemConfig.freeDailyLimit} • Pro Daily Limit: ${systemConfig.proDailyLimit}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (systemConfig.maintenanceMode) "Maintenance Mode: ENABLED" else "Platform Status: OPERATIONAL",
                color = if (systemConfig.maintenanceMode) StatusAmber else StatusEmerald,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.widthIn(min = 155.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<UserProfile>,
    onUpdateUser: (UserProfile, SubscriptionPlan, AccountStatus, UserRole) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it },
        label = { Text("Search users by name or email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    val filtered = users.filter {
        filter.isBlank() || it.displayName.contains(filter, true) || it.email.contains(filter, true)
    }

    filtered.forEach { user ->
        val isSuspended = user.statusEnum() == AccountStatus.SUSPENDED
        val isPro = user.planEnum() == SubscriptionPlan.PRO
        val isAdminRole = user.roleEnum() == UserRole.ADMIN
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Requests: ${user.dailyRequestsUsed} today • ${user.totalRequests} total",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = user.status,
                        color = if (isSuspended) StatusRose else StatusEmerald,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isPro,
                        onClick = {
                            onUpdateUser(
                                user,
                                if (isPro) SubscriptionPlan.FREE else SubscriptionPlan.PRO,
                                user.statusEnum(),
                                user.roleEnum()
                            )
                        },
                        label = { Text(if (isPro) "PRO Plan" else "FREE Plan") }
                    )
                    FilterChip(
                        selected = !isSuspended,
                        onClick = {
                            onUpdateUser(
                                user,
                                user.planEnum(),
                                if (isSuspended) AccountStatus.ACTIVE else AccountStatus.SUSPENDED,
                                user.roleEnum()
                            )
                        },
                        label = { Text(if (isSuspended) "Activate" else "Suspend") }
                    )
                    FilterChip(
                        selected = isAdminRole,
                        onClick = {
                            onUpdateUser(
                                user,
                                user.planEnum(),
                                user.statusEnum(),
                                if (isAdminRole) UserRole.USER else UserRole.ADMIN
                            )
                        },
                        label = { Text(if (isAdminRole) "Admin" else "Make Admin") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminModelsLimitsTab(
    systemConfig: SystemConfig,
    onSaveSystemConfig: (SystemConfig) -> Unit
) {
    var defaultModel by remember(systemConfig) { mutableStateOf(systemConfig.defaultModel) }
    var enabledModels by remember(systemConfig) { mutableStateOf(systemConfig.enabledModels.toSet()) }
    var freeLimitText by remember(systemConfig) { mutableStateOf(systemConfig.freeDailyLimit.toString()) }
    var proLimitText by remember(systemConfig) { mutableStateOf(systemConfig.proDailyLimit.toString()) }
    var fallbackEnabled by remember(systemConfig) { mutableStateOf(systemConfig.fallbackEnabled) }
    var maintenanceMode by remember(systemConfig) { mutableStateOf(systemConfig.maintenanceMode) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enabled AI Models", style = MaterialTheme.typography.titleMedium)
            AiModelOption.entries.forEach { option ->
                val isEnabled = enabledModels.contains(option.modelId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${option.displayName} (${option.modelId})", style = MaterialTheme.typography.bodyMedium)
                        Text(option.badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            enabledModels = if (checked) enabledModels + option.modelId else enabledModels - option.modelId
                        }
                    )
                }
            }

            HorizontalDivider()

            Text("Default Chat Model", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiModelOption.chatModels().forEach { option ->
                    FilterChip(
                        selected = defaultModel == option.modelId,
                        onClick = { defaultModel = option.modelId },
                        label = { Text(option.displayName) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = freeLimitText,
                    onValueChange = { freeLimitText = it.filter(Char::isDigit) },
                    label = { Text("Free Daily Limit") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = proLimitText,
                    onValueChange = { proLimitText = it.filter(Char::isDigit) },
                    label = { Text("Pro Daily Limit") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fallback Model Support", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = fallbackEnabled, onCheckedChange = { fallbackEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Maintenance Mode", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = maintenanceMode, onCheckedChange = { maintenanceMode = it })
            }

            Button(
                onClick = {
                    onSaveSystemConfig(
                        systemConfig.copy(
                            defaultModel = defaultModel,
                            enabledModels = enabledModels.toList().ifEmpty { listOf(AiModelOption.FLASH_3_8.modelId) },
                            freeDailyLimit = freeLimitText.toLongOrNull()?.coerceIn(1L, 1000L) ?: 25L,
                            proDailyLimit = proLimitText.toLongOrNull()?.coerceIn(1L, 10000L) ?: 500L,
                            fallbackEnabled = fallbackEnabled,
                            maintenanceMode = maintenanceMode
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save System Settings")
            }
        }
    }
}

@Composable
private fun AdminAlertsErrorsTab(
    announcements: List<Announcement>,
    errors: List<ErrorLog>,
    onCreateAnnouncement: (String, String, AnnouncementSeverity) -> Unit,
    onToggleAnnouncement: (String, Boolean) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onResolveError: (String, Boolean) -> Unit,
    onDeleteError: (String) -> Unit
) {
    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(AnnouncementSeverity.INFO) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Publish Announcement", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = annTitle,
                onValueChange = { annTitle = it },
                label = { Text("Announcement Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = annBody,
                onValueChange = { annBody = it },
                label = { Text("Message") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnnouncementSeverity.entries.forEach { sev ->
                    FilterChip(
                        selected = severity == sev,
                        onClick = { severity = sev },
                        label = { Text(sev.name) }
                    )
                }
            }
            Button(
                onClick = {
                    if (annTitle.isNotBlank() && annBody.isNotBlank()) {
                        onCreateAnnouncement(annTitle, annBody, severity)
                        annTitle = ""
                        annBody = ""
                    }
                },
                enabled = annTitle.isNotBlank() && annBody.isNotBlank()
            ) {
                Text("Publish Announcement")
            }

            announcements.forEach { ann ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ann.title, style = MaterialTheme.typography.labelLarge)
                            Text(ann.body, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = ann.active, onCheckedChange = { onToggleAnnouncement(ann.id, it) })
                        IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete announcement")
                        }
                    }
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Runtime Error Monitor", style = MaterialTheme.typography.titleMedium)
            if (errors.isEmpty()) {
                Text("No logged runtime errors.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            errors.forEach { err ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${err.source} • ${err.modelId}", style = MaterialTheme.typography.labelLarge)
                            Text(err.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text(err.userEmail.ifBlank { err.userId }, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onResolveError(err.id, !err.resolved) }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (err.resolved) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(0.dp))
                            Text(if (err.resolved) "Resolved" else "Resolve")
                        }
                        IconButton(onClick = { onDeleteError(err.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete error")
                        }
                    }
                }
            }
        }
    }
}
