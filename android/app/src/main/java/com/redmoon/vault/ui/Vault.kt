package com.redmoon.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel
import org.json.JSONObject

@Composable
internal fun VaultScreen(v: VaultModel, notesOnly: Boolean, initialCategory: String = "") {
    var query by remember(notesOnly) { mutableStateOf("") }
    var matchingIds by remember { mutableStateOf<Set<String>?>(null) }
    var kind by remember(notesOnly) { mutableStateOf("all") }
    var category by remember(notesOnly, initialCategory) { mutableStateOf(initialCategory) }
    var favorites by remember(notesOnly) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(notesOnly, initialCategory) { listState.scrollToItem(0) }
    var showTypes by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        matchingIds = null
        if (query.isNotBlank()) {
            kotlinx.coroutines.delay(180)
            try {
                matchingIds = v.searchRecords(query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                matchingIds = emptySet()
            }
        }
    }
    val categories = v.snapshot.optJSONArray("categories").objects().sortedBy { it.optInt("order") }
    val all =
        v.snapshot.optJSONArray("records").objects().filter {
            !notesOnly || it.optString("kind") == "secure_note"
        }
    val selectedCategory = categories.find { it.matchesCategory(category) }
    val rows =
        all.filter {
                (kind == "all" || it.optString("kind") == kind) &&
                    (category.isBlank() ||
                        selectedCategory?.matchesCategory(it.optString("category")) == true) &&
                    (!favorites || it.optBoolean("favorite")) &&
                    (query.isBlank() || matchingIds?.contains(it.optString("id")) == true)
            }
            .sortedByDescending { it.optLong("modified_at") }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (notesOnly) "Secure notes" else "Your vault",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "${all.size} ${if(all.size==1)"item" else "items"} · Stored on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "ENCRYPTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        item {
            Field(if (notesOnly) "Search notes" else "Search your vault", query) {
                query = it
                v.activity()
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatTab("All", !favorites) { favorites = false }
                FlatTab("Favorites", favorites) { favorites = true }
                Spacer(Modifier.weight(1f))
                if (!notesOnly)
                    Box {
                        TextButton(onClick = { showTypes = true }) {
                            Text(
                                if (kind == "all") "All types ▾" else "${kinds[kind]} ▾",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        DropdownMenu(
                            showTypes,
                            { showTypes = false },
                            modifier = Modifier.heightIn(max = 320.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("All types") },
                                onClick = {
                                    kind = "all"
                                    showTypes = false
                                },
                            )
                            kinds.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        kind = id
                                        showTypes = false
                                    },
                                )
                            }
                        }
                    }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { showCategories = true }) {
                        Text(
                            "${selectedCategory?.optString("name")?:"All categories"} ▾",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        showCategories,
                        { showCategories = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("All categories") },
                            onClick = {
                                category = ""
                                showCategories = false
                            },
                        )
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.optString("name")) },
                                onClick = {
                                    category = c.optString("id")
                                    showCategories = false
                                },
                            )
                        }
                    }
                }
                Text(
                    if (query.isNotBlank()) "${rows.size} results" else "RECENTLY UPDATED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (query.isNotBlank() && matchingIds == null)
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (rows.isEmpty() && (query.isBlank() || matchingIds != null))
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (all.isEmpty())
                            if (notesOnly) "A place for private thoughts."
                            else "Start with your first item."
                        else "No matching items",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (all.isEmpty())
                            if (notesOnly)
                                "Add a note to keep instructions, checklists or personal information encrypted."
                            else
                                "Save a login, document, identity or anything you want to keep private."
                        else "Try another search, type or category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (all.isEmpty())
                        TextButton(
                            onClick = {
                                v.selected = null
                                v.screen = if (notesOnly) "NewNote" else "New"
                            }
                        ) {
                            Text(if (notesOnly) "+ Add a note" else "+ Add your first item")
                        }
                    else
                        TextButton(
                            onClick = {
                                query = ""
                                kind = "all"
                                category = ""
                                favorites = false
                            }
                        ) {
                            Text("Clear filters")
                        }
                }
            }
        items(rows, key = { it.optString("id") }) { record ->
            FlatRecordRow(record, categories) {
                v.selected = record
                v.screen = "Detail"
            }
        }
    }
}

@Composable
private fun FlatTab(label: String, selected: Boolean, click: () -> Unit) {
    Column {
        TextButton(onClick = click) {
            Text(
                label,
                color =
                    if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.width(if (label == "All") 56.dp else 94.dp)
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent
                )
        )
    }
}

@Composable
private fun FlatRecordRow(record: JSONObject, categories: List<JSONObject>, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    record.optString("title").take(1).uppercase().ifBlank { "—" },
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    record.optString("title"),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                            kinds[record.optString("kind")],
                            categories
                                .find { it.matchesCategory(record.optString("category")) }
                                ?.optString("name"),
                        )
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.optBoolean("duplicate"))
                    Text(
                        "Possible duplicate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
            }
            if (record.optBoolean("favorite")) Text("★", color = MaterialTheme.colorScheme.primary)
            Text(
                "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
