package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Conversation
import com.example.model.UserProfile
import com.example.ui.components.MtAiLogoBadge

object MtRoutes {
    const val CHAT = "chat"
    const val EXPLORE = "explore"
    const val IMAGE_STUDIO = "image_studio"
    const val CODE_LAB = "code_lab"
    const val DASHBOARD = "dashboard"
    const val ADMIN = "admin"
}

@Composable
fun SidebarDrawerContent(
    currentRoute: String,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    conversations: List<Conversation>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onPinConversation: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var renamingConv by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MtAiLogoBadge(size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text("MT AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Your World, Your Intelligence.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                onNewChat()
                onNavigate(MtRoutes.CHAT)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_chat_button"),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search chats...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search chats", modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_chats_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Workspace Navigation
        NavigationDrawerItem(
            selected = currentRoute == MtRoutes.EXPLORE,
            onClick = { onNavigate(MtRoutes.EXPLORE) },
            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
            label = { Text("Explore Features") },
            colors = NavigationDrawerItemDefaults.colors()
        )
        NavigationDrawerItem(
            selected = currentRoute == MtRoutes.IMAGE_STUDIO,
            onClick = { onNavigate(MtRoutes.IMAGE_STUDIO) },
            icon = { Icon(Icons.Default.Image, contentDescription = null) },
            label = { Text("Image Intelligence") }
        )
        NavigationDrawerItem(
            selected = currentRoute == MtRoutes.CODE_LAB,
            onClick = { onNavigate(MtRoutes.CODE_LAB) },
            icon = { Icon(Icons.Default.Code, contentDescription = null) },
            label = { Text("AI Code Lab") }
        )
        NavigationDrawerItem(
            selected = currentRoute == MtRoutes.DASHBOARD,
            onClick = { onNavigate(MtRoutes.DASHBOARD) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings & Dashboard") }
        )
        if (isAdmin) {
            NavigationDrawerItem(
                selected = currentRoute == MtRoutes.ADMIN,
                onClick = { onNavigate(MtRoutes.ADMIN) },
                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                label = { Text("Admin Console (/admin)") },
                modifier = Modifier.testTag("nav_admin_console")
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Recent Conversations",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (conversations.isEmpty()) {
                item {
                    Text(
                        text = "No conversations yet. Start a new chat!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            items(conversations, key = { it.id }) { conv ->
                var menuOpen by remember { mutableStateOf(false) }
                val isSelected = currentRoute == MtRoutes.CHAT && conv.id == activeConversationId
                NavigationDrawerItem(
                    selected = isSelected,
                    onClick = {
                        onSelectConversation(conv.id)
                        onNavigate(MtRoutes.CHAT)
                    },
                    icon = {
                        Icon(
                            imageVector = if (conv.pinned) Icons.Default.PushPin else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = conv.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    badge = {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Conversation actions",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (conv.pinned) "Unpin" else "Pin") },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onPinConversation(conv.id, !conv.pinned)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        renamingConv = conv
                                        renameText = conv.title
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onDeleteConversation(conv.id)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Surface(
            onClick = { onNavigate(MtRoutes.DASHBOARD) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!userProfile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userProfile?.photoUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userProfile?.displayName?.ifBlank { "MT User" } ?: "MT User",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${userProfile?.plan ?: "FREE"} Plan • ${userProfile?.preferredLanguage ?: "English"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (renamingConv != null) {
        AlertDialog(
            onDismissRequest = { renamingConv = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renamingConv
                    if (target != null && renameText.isNotBlank()) {
                        onRenameConversation(target.id, renameText)
                    }
                    renamingConv = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingConv = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
