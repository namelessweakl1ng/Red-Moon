package com.redmoon.vault.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.redmoon.vault.VaultModel
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun CategoriesScreen(v: VaultModel) {
    val categories = v.snapshot.optJSONArray("categories").objects().sortedBy { it.optInt("order") }
    val records = v.snapshot.optJSONArray("records").objects()
    var query by remember { mutableStateOf("") }
    val visible = categories.filter { it.optString("name").contains(query, ignoreCase = true) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    fun reorder(from: Int, to: Int) {
        val reordered = categories.toMutableList().apply { add(to, removeAt(from)) }
        v.run(
            request("reorder_categories")
                .put("ids", JSONArray(reordered.map { it.optString("id") }))
        ) {
            v.load()
        }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Heading("A place for everything.", "Organize your vault in a way that feels like you.")
            Primary("+ New category", !v.busy) { editing = JSONObject() }
            Spacer(Modifier.height(12.dp))
            Field("Search categories", query) {
                query = it
                v.activity()
            }
        }
        if (categories.isEmpty())
            item {
                Text(
                    "Your categories will appear here. Start with Personal, Work or Family.",
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        itemsIndexed(visible, key = { _, c -> c.optString("id") }) { _, c ->
            val index = categories.indexOf(c)
            OutlinedCard(
                onClick = { v.screen = "Category:${c.optString("id")}" },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                c.optString("icon", "folder"),
                                color =
                                    runCatching {
                                            Color(c.optString("color").toColorInt())
                                        }
                                        .getOrDefault(MaterialTheme.colorScheme.primary),
                            )
                            Column {
                                Text(
                                    c.optString("name"),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "${records.count { c.matchesCategory(it.optString("category")) }} items",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        TextButton(onClick = { editing = JSONObject(c.toString()) }) {
                            Text("Edit")
                        }
                    }
                    Row {
                        TextButton(
                            onClick = { reorder(index, index - 1) },
                            enabled = index > 0 && !v.busy,
                        ) {
                            Text("Move up")
                        }
                        TextButton(
                            onClick = { reorder(index, index + 1) },
                            enabled = index < categories.lastIndex && !v.busy,
                        ) {
                            Text("Down")
                        }
                        TextButton(onClick = { delete = c }, enabled = !v.busy) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    editing?.let { c ->
        CategoryDialog(c, !v.busy, { editing = null }) { category ->
            v.run(
                request("save_category")
                    .put(
                        "category",
                        category.put(
                            "order",
                            if (c.has("order")) c.optInt("order") else categories.size,
                        ),
                    )
            ) {
                editing = null
                v.load()
            }
        }
    }
    delete?.let { c ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("Delete ${c.optString("name")}?") },
            text = { Text("Items remain in your vault without this category.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        v.run(request("delete_category").put("id", c.optString("id"))) {
                            delete = null
                            v.load()
                        }
                    },
                    enabled = !v.busy,
                ) {
                    Text("Delete category")
                }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategoryDialog(
    original: JSONObject,
    enabled: Boolean,
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit,
) {
    var name by remember { mutableStateOf(original.optString("name")) }
    var icon by remember { mutableStateOf(original.optString("icon", "folder")) }
    var color by remember { mutableStateOf(original.optString("color", "#B62346")) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (original.has("id")) "Edit category" else "New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", name) { name = it }
                Field("Icon (short label or symbol)", icon) { icon = it }
                Text("Color")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("#B62346", "#7755AA", "#337788", "#32865A", "#B47328").forEach { hex ->
                        FilterChip(
                            color == hex,
                            { color = hex },
                            { Text("●", color = Color(hex.toColorInt())) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    save(
                        JSONObject(original.toString())
                            .put("id", original.optString("id"))
                            .put("name", name.trim())
                            .put("icon", icon)
                            .put("color", color)
                    )
                },
                enabled = enabled && name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
