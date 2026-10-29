package com.spaco.app.feature.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaco.app.core.domain.model.PracticeItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

object DeckDetailTags {
    const val ADD_ITEM = "deck-detail-add"
    const val ITEM_LIST = "deck-detail-items"
    const val PROMPT_FIELD = "item-editor-prompt"
    const val ANSWER_FIELD = "item-editor-answer"
    const val EXPLANATION_FIELD = "item-editor-explanation"
    const val FREE_RECALL = "item-editor-free-recall"
    const val SAVE = "item-editor-save"
    const val PROMPT_ERROR = "item-editor-prompt-error"
    const val ANSWER_ERROR = "item-editor-answer-error"
    const val BACK = "deck-detail-back"
    fun itemRow(id: String) = "item-row-$id"
}

@Composable
fun DeckDetailRoute(
    onBack: () -> Unit,
    viewModel: DeckDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeckDetailScreen(
        state = state,
        onBack = onBack,
        onAddItem = viewModel::addItem,
        onUpdateItem = viewModel::updateItem,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    state: DeckDetailUiState,
    onBack: () -> Unit,
    onAddItem: (prompt: String, expectedAnswer: String, explanation: String?, isFreeRecall: Boolean) -> Unit,
    onUpdateItem: (id: String, prompt: String, expectedAnswer: String, explanation: String?, isFreeRecall: Boolean) -> Unit,
) {
    var editing by remember { mutableStateOf<PracticeItem?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deckName.ifBlank { "Deck" }) },
                navigationIcon = {
                    IconButton(modifier = Modifier.testTag(DeckDetailTags.BACK), onClick = onBack) {
                        Text("←")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.testTag(DeckDetailTags.ADD_ITEM),
                onClick = { adding = true },
            ) {
                Text("+")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(DeckDetailTags.ITEM_LIST),
        ) {
            items(state.items, key = { it.id }) { item ->
                ItemRow(item = item, onClick = { editing = item })
            }
        }
    }

    if (adding) {
        ItemEditorDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { prompt, answer, explanation, isFreeRecall ->
                adding = false
                onAddItem(prompt, answer, explanation, isFreeRecall)
            },
        )
    }

    editing?.let { item ->
        ItemEditorDialog(
            initial = item,
            onDismiss = { editing = null },
            onSave = { prompt, answer, explanation, isFreeRecall ->
                editing = null
                onUpdateItem(item.id, prompt, answer, explanation, isFreeRecall)
            },
        )
    }
}

@Composable
private fun ItemRow(item: PracticeItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag(DeckDetailTags.itemRow(item.id)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.prompt, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.expectedAnswer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.explanation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemEditorDialog(
    initial: PracticeItem?,
    onDismiss: () -> Unit,
    onSave: (prompt: String, expectedAnswer: String, explanation: String?, isFreeRecall: Boolean) -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf(initial?.prompt.orEmpty()) }
    var answer by rememberSaveable { mutableStateOf(initial?.expectedAnswer.orEmpty()) }
    var explanation by rememberSaveable { mutableStateOf(initial?.explanation.orEmpty()) }
    var isFreeRecall by rememberSaveable { mutableStateOf(initial?.isFreeRecall ?: false) }
    var promptError by remember { mutableStateOf<String?>(null) }
    var answerError by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New item" else "Edit item") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it; promptError = null },
                    label = { Text("Prompt") },
                    isError = promptError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DeckDetailTags.PROMPT_FIELD),
                )
                promptError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(DeckDetailTags.PROMPT_ERROR),
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it; answerError = null },
                    label = { Text("Expected answer") },
                    isError = answerError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DeckDetailTags.ANSWER_FIELD),
                )
                answerError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(DeckDetailTags.ANSWER_ERROR),
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Explanation (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DeckDetailTags.EXPLANATION_FIELD),
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFreeRecall = !isFreeRecall }
                        .testTag(DeckDetailTags.FREE_RECALL),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isFreeRecall,
                        onCheckedChange = { isFreeRecall = it },
                    )
                    Text("Free recall (I grade myself)")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                modifier = Modifier.testTag(DeckDetailTags.SAVE),
                onClick = {
                    promptError = if (prompt.isBlank()) "Prompt is required." else null
                    answerError = if (answer.isBlank()) "Expected answer is required." else null
                    if (promptError == null && answerError == null) {
                        onSave(prompt, answer, explanation.ifBlank { null }, isFreeRecall)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
