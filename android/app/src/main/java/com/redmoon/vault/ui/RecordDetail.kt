package com.redmoon.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel
import org.json.JSONObject

@Composable
internal fun RecordDetail(v: VaultModel) {
    val id = v.selected?.optString("id") ?: return
    var record by remember(id) { mutableStateOf<JSONObject?>(null) }
    var revealed by remember(id) { mutableStateOf(mapOf<String, String>()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(id, v.busy) {
        if (record == null && !v.busy)
            v.run(request("record_detail").put("id", id)) { record = it.payload().record() }
    }
    val r = record
    if (r == null) {
        Text("Opening item…")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Heading(r.optString("title"), kinds[r.optString("kind")] ?: "Item")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        revealed = emptyMap()
                        v.screen = "Edit"
                    },
                    enabled = !v.busy,
                ) {
                    Text("Edit item")
                }
                OutlinedButton(
                    onClick = {
                        v.run(request("favorite_record").put("id", id)) {
                            record =
                                JSONObject(r.toString()).put("favorite", !r.optBoolean("favorite"))
                            v.load()
                        }
                    },
                    enabled = !v.busy,
                ) {
                    Text(if (r.optBoolean("favorite")) "Favorited" else "Favorite")
                }
            }
        }
        items(r.optJSONArray("fields").objects(), key = { it.optString("id") }) { field ->
            val fid = field.optString("id")
            val secret = field.optString("field_type") == "secret" || field.optBoolean("concealed")
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    field.optString("label"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (secret) revealed[fid] ?: "••••••••••••" else field.optString("value"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (secret)
                        TextButton(
                            onClick = {
                                if (revealed.containsKey(fid)) revealed = revealed - fid
                                else
                                    v.run(
                                        request("reveal_field").put("id", id).put("field_id", fid)
                                    ) {
                                        revealed =
                                            revealed + (fid to it.payload().getString("value"))
                                    }
                            },
                            enabled = !v.busy,
                        ) {
                            Text(if (revealed.containsKey(fid)) "Hide" else "Reveal")
                        }
                    TextButton(
                        onClick = {
                            if (secret)
                                v.run(request("reveal_field").put("id", id).put("field_id", fid)) {
                                    v.copyText(it.payload().getString("value"), true)
                                }
                            else v.copyText(field.optString("value"), false)
                        },
                        enabled = !v.busy,
                    ) {
                        Text(if (secret) "Copy · clears in 20s" else "Copy")
                    }
                }
                HorizontalDivider()
            }
        }
        if (r.optString("notes").isNotBlank())
            item {
                Section("Private notes")
                Text(r.optString("notes"))
            }
        items(r.optJSONArray("checklist").objects()) { item ->
            Row {
                Checkbox(item.optBoolean("checked"), onCheckedChange = null)
                Text(item.optString("text"), modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            val tags = r.optJSONArray("tags")
            if (tags != null && tags.length() > 0)
                Text(
                    (0 until tags.length()).joinToString("  ") { "#${tags.optString(it)}" },
                    color = MaterialTheme.colorScheme.primary,
                )
            Text(
                "Created ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(r.optLong("created_at") * 1000))}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Updated ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(r.optLong("modified_at") * 1000))}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            TextButton(onClick = { confirm = true }, enabled = !v.busy) {
                Text("Delete item", color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = {
                    revealed = emptyMap()
                    v.home()
                }
            ) {
                Text("Back to vault")
            }
        }
    }
    if (confirm)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this item?") },
            text = {
                Text("This removes it from this vault. Existing backups may still contain it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        v.run(request("delete_record").put("id", id)) {
                            revealed = emptyMap()
                            v.home()
                        }
                    },
                    enabled = !v.busy,
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
}
