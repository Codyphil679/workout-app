package com.codyphil.liftlog.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.codyphil.liftlog.data.Exercise
import com.codyphil.liftlog.ui.theme.Brand

/**
 * Bottom sheet to choose an exercise, or create one from the search text.
 * Long-press an exercise to delete it (only allowed when it isn't used anywhere).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Exercise) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }
    val filtered = remember(query, exercises) {
        val q = query.trim()
        if (q.isEmpty()) exercises else exercises.filter { it.name.contains(q, ignoreCase = true) }
    }
    val exactMatch = exercises.any { it.name.equals(query.trim(), ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Brand.Panel) {
        Text("Add exercise", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search or type a new exercise") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(Modifier.fillMaxHeight(0.75f)) {
            if (query.isNotBlank() && !exactMatch) {
                item {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { onCreate(query.trim()) })
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(Icons.Rounded.Add, size = 36.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Create “${query.trim()}”", style = MaterialTheme.typography.titleMedium, color = Brand.Volt)
                    }
                    HorizontalDivider(color = Brand.Line)
                }
            }
            items(filtered, key = { it.id }) { ex ->
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onPick(ex) }, onLongClick = { pendingDelete = ex })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Rounded.FitnessCenter, tint = Brand.TextMid, size = 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(ex.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    pendingDelete?.let { ex ->
        ConfirmDialog(
            title = "Delete ${ex.name}?",
            message = "Exercises used in a program or a logged workout can't be deleted.",
            confirmText = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = { onDelete(ex); pendingDelete = null },
        )
    }
}
