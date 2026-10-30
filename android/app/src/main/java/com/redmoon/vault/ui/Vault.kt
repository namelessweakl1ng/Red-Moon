package com.redmoon.vault.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel
import org.json.JSONObject

@Composable
internal fun VaultScreen(v: VaultModel, notesOnly: Boolean, initialCategory: String = "") {
    var query by remember { mutableStateOf("") }
    var matchingIds by remember { mutableStateOf<Set<String>?>(null) }
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
    var kind by remember { mutableStateOf("all") }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var favorites by remember { mutableStateOf(false) }
    val categories = v.snapshot.optJSONArray("categories").objects().sortedBy { it.optInt("order") }
    val all =
        v.snapshot.optJSONArray("records").objects().filter {
            !notesOnly || it.optString("kind") == "secure_note"
        }
    val rows =
        all.filter {
                (kind == "all" || it.optString("kind") == kind) &&
                    (category.isBlank() ||
                        categories
                            .find { c -> c.matchesCategory(category) }
                            ?.matchesCategory(it.optString("category")) == true) &&
                    (!favorites || it.optBoolean("favorite")) &&
                    (query.isBlank() || matchingIds?.contains(it.optString("id")) == true)
            }
            .sortedByDescending { it.optLong("modified_at") }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        item {
            Text(
                "Welcome back",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            Heading(
                if (notesOnly) "Your notes" else "Everything, safely kept.",
                if (notesOnly) "A little space for thoughts that are yours."
                else "Vault unlocked · ${all.size} encrypted items · This device only",
            )
        }
        if (!notesOnly)
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                            "login" to "Passwords",
                            "secure_note" to "Notes",
                            "identity" to "Identities",
                        )
                        .forEach { (id, label) ->
                            FilledTonalButton(
                                onClick = {
                                    kind = id
                                    favorites = false
                                }
                            ) {
                                Text("$label  ${all.count { it.optString("kind") == id }}")
                            }
                        }
                    FilledTonalButton(
                        onClick = {
                            kind = "all"
                            favorites = true
                        }
                    ) {
                        Text("Favorites  ${all.count { it.optBoolean("favorite") }}")
                    }
                }
            }
        item {
            Field("Search your vault", query) {
                query = it
                v.activity()
            }
        }
        if (!notesOnly)
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(kind == "all", { kind = "all" }, { Text("All items") })
                    kinds.forEach { (id, name) ->
                        FilterChip(kind == id, { kind = id }, { Text(name) })
                    }
                }
            }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(favorites, { favorites = !favorites }, { Text("☆ Favorites") })
                if (categories.isNotEmpty()) {
                    FilterChip(category.isEmpty(), { category = "" }, { Text("All categories") })
                    categories.forEach { c ->
                        FilterChip(
                            category == c.optString("id"),
                            { category = c.optString("id") },
                            { Text(c.optString("name")) },
                        )
                    }
                }
            }
        }
        item {
            Section(
                if (favorites) "Favorites"
                else if (query.isNotEmpty()) "Search results" else "Recently updated"
            )
        }
        if (query.isNotBlank() && matchingIds == null)
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (rows.isEmpty() && (query.isBlank() || matchingIds != null))
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (all.isEmpty()) "Make yourself at home." else "No matching items",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (all.isEmpty())
                                "Tap ${if(notesOnly) "New note" else "Add item"} to keep something safe."
                            else "Try another search or filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        items(rows, key = { it.optString("id") }) { r ->
            RecordCard(r, categories) {
                v.selected = r
                v.screen = "Detail"
            }
        }
    }
}

@Composable
private fun RecordCard(record: JSONObject, categories: List<JSONObject>, open: () -> Unit) {
    ElevatedCard(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    record.optString("title").take(1).uppercase().ifBlank { "◇" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    record.optString("title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
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
                )
                if (record.optBoolean("duplicate"))
                    Text(
                        "Possible duplicate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
            }
            if (record.optBoolean("favorite")) Text("★", color = MaterialTheme.colorScheme.primary)
        }
    }
}
