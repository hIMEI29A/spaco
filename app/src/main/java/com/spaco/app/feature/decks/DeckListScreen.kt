package com.spaco.app.feature.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaco.app.core.domain.model.Deck

object DeckListTags {
    const val ADD = "deck-list-add"
    const val LIST = "deck-list"
    const val PRACTICE = "deck-list-practice"
    const val PROGRESS = "deck-list-progress"
    const val SETTINGS = "deck-list-settings"
    const val SKILL_FIELD = "deck-create-skill"
    const val DECK_FIELD = "deck-create-deck"
    const val CONFIRM_CREATE = "deck-create-confirm"
    const val ERROR = "deck-create-error"
    const val ARCHIVED_SECTION = "deck-archived-section"
    fun deckRow(id: String) = "deck-row-$id"
    fun activeBadge(id: String) = "deck-active-$id"
    fun archive(id: String) = "deck-archive-$id"
    fun restore(id: String) = "deck-restore-$id"
}

@Composable
fun DeckListRoute(
    onOpenDeck: (String) -> Unit,
    onStartPractice: (String) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeckListScreen(
        state = state,
        onOpenDeck = onOpenDeck,
        onStartPractice = onStartPractice,
        onOpenProgress = onOpenProgress,
        onOpenSettings = onOpenSettings,
        onCreateDeck = viewModel::createDeck,
        onSelectDeck = viewModel::selectDeck,
        onRenameDeck = viewModel::renameDeck,
        onArchiveDeck = viewModel::archiveDeck,
        onRestoreDeck = viewModel::restoreDeck,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    state: DeckListUiState,
    onOpenDeck: (String) -> Unit = {},
    onStartPractice: (String) -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCreateDeck: (skillName: String, deckName: String) -> Unit = { _, _ -> },
    onSelectDeck: (String) -> Unit = {},
    onRenameDeck: (String, String) -> Unit = { _, _ -> },
    onArchiveDeck: (String) -> Unit = {},
    onRestoreDeck: (String) -> Unit = {},
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Deck?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decks") },
                actions = {
                    state.activeDeckId?.let { activeId ->
                        androidx.compose.material3.TextButton(
                            modifier = Modifier.testTag(DeckListTags.PRACTICE),
                            onClick = { onStartPractice(activeId) },
                        ) { Text("Practice") }
                    }
                    androidx.compose.material3.TextButton(
                        modifier = Modifier.testTag(DeckListTags.PROGRESS),
                        onClick = onOpenProgress,
                    ) { Text("Progress") }
                    androidx.compose.material3.TextButton(
                        modifier = Modifier.testTag(DeckListTags.SETTINGS),
                        onClick = onOpenSettings,
                    ) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.testTag(DeckListTags.ADD),
                onClick = { showCreateDialog = true },
            ) {
                Text("+")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(DeckListTags.LIST),
        ) {
            state.groups.forEach { group ->
                item(key = "skill-${group.skillId}") {
                    Text(
                        text = group.skillName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(group.decks, key = { it.id }) { deck ->
                    DeckRow(
                        deck = deck,
                        isActive = deck.id == state.activeDeckId,
                        onOpen = { onOpenDeck(deck.id) },
                        onSelect = { onSelectDeck(deck.id) },
                        onRename = { renameTarget = deck },
                        onArchive = { onArchiveDeck(deck.id) },
                    )
                }
            }

            if (state.archivedDecks.isNotEmpty()) {
                item(key = "archived-header") {
                    Text(
                        text = "Archived",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(DeckListTags.ARCHIVED_SECTION),
                    )
                }
                items(state.archivedDecks, key = { "archived-${it.deck.id}" }) { archived ->
                    DeckRow(
                        deck = archived.deck,
                        isActive = false,
                        onOpen = {},
                        onSelect = {},
                        onRename = null,
                        onArchive = null,
                        onRestore = { onRestoreDeck(archived.deck.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDeckDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { skillName, deckName ->
                showCreateDialog = false
                onCreateDeck(skillName, deckName)
            },
        )
    }

    renameTarget?.let { deck ->
        RenameDeckDialog(
            initialName = deck.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                onRenameDeck(deck.id, newName)
            },
        )
    }
}

@Composable
private fun DeckRow(
    deck: Deck,
    isActive: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onRename: (() -> Unit)?,
    onArchive: (() -> Unit)?,
    onRestore: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(DeckListTags.deckRow(deck.id)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (isActive) {
                AssistChip(
                    modifier = Modifier
                        .testTag(DeckListTags.activeBadge(deck.id))
                        .semantics { contentDescription = "Active deck" },
                    onClick = {},
                    label = { Text("Active") },
                )
                Spacer(Modifier.width(8.dp))
            }
            if (onRestore != null) {
                AssistChip(
                    modifier = Modifier.testTag(DeckListTags.restore(deck.id)),
                    onClick = onRestore,
                    label = { Text("Restore") },
                )
            } else {
                AssistChip(
                    modifier = Modifier.testTag("deck-select-${deck.id}"),
                    onClick = onSelect,
                    label = { Text("Select") },
                )
                if (onRename != null && onArchive != null) {
                    IconButton(onClick = { menuOpen = true }) {
                        Text("⋮")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            modifier = Modifier.testTag(DeckListTags.archive(deck.id)),
                            onClick = {
                                menuOpen = false
                                onArchive()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateDeckDialog(
    onDismiss: () -> Unit,
    onConfirm: (skillName: String, deckName: String) -> Unit,
) {
    var skillName by rememberSaveable { mutableStateOf("") }
    var deckName by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New deck") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = skillName,
                    onValueChange = { skillName = it },
                    label = { Text("Skill") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DeckListTags.SKILL_FIELD),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text("Deck name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DeckListTags.DECK_FIELD),
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(DeckListTags.ERROR),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                modifier = Modifier.testTag(DeckListTags.CONFIRM_CREATE),
                onClick = {
                    when {
                        skillName.isBlank() -> error = "Enter a skill name."
                        deckName.isBlank() -> error = "Enter a deck name."
                        else -> onConfirm(skillName, deckName)
                    }
                },
            ) { Text("Create") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenameDeckDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename deck") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deck-rename-field"),
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                modifier = Modifier.testTag("deck-rename-confirm"),
                onClick = {
                    if (name.isBlank()) error = "Enter a deck name." else onConfirm(name)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
