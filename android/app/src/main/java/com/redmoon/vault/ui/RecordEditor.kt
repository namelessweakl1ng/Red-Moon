package com.redmoon.vault.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private fun template(kind: String): List<JSONObject> {
    val fields =
        when (kind) {
            "login" ->
                listOf(
                    "Service" to "text",
                    "Email" to "text",
                    "Username" to "text",
                    "Password" to "secret",
                    "Website" to "url",
                )
            "identity" ->
                listOf(
                    "Full name" to "text",
                    "Date of birth" to "date",
                    "Address" to "multiline",
                    "Phone" to "secret",
                    "Email" to "text",
                )
            "indian_document" ->
                listOf(
                    "Document type" to "text",
                    "Holder name" to "text",
                    "Document number" to "secret",
                    "Date of issue" to "date",
                    "Expiry date" to "date",
                )
            "financial" ->
                listOf(
                    "Bank / issuer" to "text",
                    "Account holder" to "text",
                    "Account / card number" to "secret",
                    "IFSC" to "secret",
                    "Expiry" to "secret",
                    "PIN / CVV" to "secret",
                )
            else -> emptyList()
        }
    return fields.map { (label, type) ->
        JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("label", label)
            .put("field_type", type)
            .put("value", "")
    }
}

@Composable
internal fun RecordEditor(v: VaultModel, newNote: Boolean) {
    val selectedId = v.selected?.optString("id")
    var loaded by remember { mutableStateOf(selectedId == null) }
    var original by remember { mutableStateOf<JSONObject?>(null) }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(if (newNote) "secure_note" else "login") }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf(template(kind)) }
    var checklist by remember { mutableStateOf(emptyList<JSONObject>()) }
    var discard by remember { mutableStateOf(false) }
    var generatorField by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedId, v.busy) {
        if (selectedId != null && !loaded && !v.busy)
            v.run(request("edit_record").put("id", selectedId)) { result ->
                val r = result.payload().record()
                original = r
                title = r.optString("title")
                kind = r.optString("kind")
                category = r.optString("category")
                tags =
                    r.optJSONArray("tags")?.let { a ->
                        (0 until a.length()).joinToString(", ") { a.optString(it) }
                    } ?: ""
                notes = r.optString("notes")
                favorite = r.optBoolean("favorite")
                fields = r.optJSONArray("fields").objects()
                checklist = r.optJSONArray("checklist").objects()
                loaded = true
            }
    }
    fun updateField(index: Int, key: String, value: String) {
        fields = fields.mapIndexed { i, f ->
            if (i == index) JSONObject(f.toString()).put(key, value) else f
        }
        v.activity()
    }
    if (!loaded) {
        Text("Opening encrypted item…")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Heading(
                if (selectedId == null) "Make room for something." else "Edit item",
                "Changes stay on this device, encrypted in your vault.",
            )
        }
        if (selectedId == null)
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    kinds.forEach { (id, name) ->
                        FilterChip(
                            kind == id,
                            {
                                kind = id
                                fields = template(id)
                            },
                            { Text(name) },
                        )
                    }
                }
            }
        item {
            Field("Title", title) {
                title = it
                v.activity()
            }
        }
        item { Section("Details") }
        itemsIndexed(fields, key = { _, f -> f.optString("id") }) { index, f ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Field label", f.optString("label")) { updateField(index, "label", it) }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf("text", "secret", "multiline", "number", "date", "url").forEach {
                            type ->
                            FilterChip(
                                f.optString("field_type") == type,
                                { updateField(index, "field_type", type) },
                                { Text(type.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    Field(
                        if (f.optString("field_type") == "date") "Value (YYYY-MM-DD)" else "Value",
                        f.optString("value"),
                        secret = f.optString("field_type") == "secret",
                        multiline = f.optString("field_type") == "multiline",
                        type = f.optString("field_type"),
                    ) {
                        updateField(index, "value", it)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = {
                                fields =
                                    fields.toMutableList().apply { add(index - 1, removeAt(index)) }
                            },
                            enabled = index > 0,
                        ) {
                            Text("Move up")
                        }
                        TextButton(
                            onClick = {
                                fields =
                                    fields.toMutableList().apply { add(index + 1, removeAt(index)) }
                            },
                            enabled = index < fields.lastIndex,
                        ) {
                            Text("Down")
                        }
                        TextButton(
                            onClick = { fields = fields.filterIndexed { i, _ -> i != index } }
                        ) {
                            Text("Remove")
                        }
                    }
                    if (f.optString("field_type") == "secret")
                        TextButton(
                            onClick = { generatorField = f.optString("id") },
                            enabled = !v.busy,
                        ) {
                            Text("Generate password")
                        }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    fields =
                        fields +
                            JSONObject()
                                .put("id", UUID.randomUUID().toString())
                                .put("label", "")
                                .put(
                                    "field_type",
                                    if (kind in listOf("financial", "indian_document")) "secret"
                                    else "text",
                                )
                                .put("value", "")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Add custom field")
            }
        }
        item {
            Section("Private notes")
            Field("Notes", notes, multiline = true) {
                notes = it
                v.activity()
            }
        }
        itemsIndexed(checklist, key = { _, item -> item.optString("id") }) { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(
                    item.optBoolean("checked"),
                    { checked ->
                        checklist = checklist.mapIndexed { i, c ->
                            if (i == index) JSONObject(c.toString()).put("checked", checked) else c
                        }
                    },
                )
                OutlinedTextField(
                    item.optString("text"),
                    { text ->
                        checklist = checklist.mapIndexed { i, c ->
                            if (i == index) JSONObject(c.toString()).put("text", text) else c
                        }
                    },
                    modifier = Modifier.weight(1f),
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        ),
                    label = { Text("Checklist item") },
                )
                TextButton(
                    onClick = { checklist = checklist.filterIndexed { i, _ -> i != index } }
                ) {
                    Text("×")
                }
            }
        }
        item {
            TextButton(
                onClick = {
                    checklist =
                        checklist +
                            JSONObject()
                                .put("id", UUID.randomUUID().toString())
                                .put("text", "")
                                .put("checked", false)
                }
            ) {
                Text("+ Add checklist item")
            }
        }
        item {
            Section("Organize")
            CategoryChoice(v, category) { category = it }
            Field("Tags, separated by commas", tags) { tags = it }
            Row {
                Checkbox(favorite, { favorite = it })
                Text("Add to favorites", modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            Primary("Save encrypted item", !v.busy && title.isNotBlank()) {
                val record =
                    JSONObject()
                        .put("id", selectedId ?: "")
                        .put("kind", kind)
                        .put("title", title.trim())
                        .put("category", category)
                        .put(
                            "tags",
                            JSONArray(tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }),
                        )
                        .put("favorite", favorite)
                        .put("fields", JSONArray(fields))
                        .put("notes", notes)
                        .put("checklist", JSONArray(checklist))
                        .put("created_at", original?.optLong("created_at") ?: 0)
                        .put("modified_at", original?.optLong("modified_at") ?: 0)
                v.run(request("save_record").put("record", record)) {
                    fields = emptyList()
                    notes = ""
                    checklist = emptyList()
                    v.home()
                }
            }
            TextButton(onClick = { discard = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
    generatorField?.let { fieldId ->
        PasswordGenerator(v, { generatorField = null }) { password ->
            val index = fields.indexOfFirst { it.optString("id") == fieldId }
            if (index >= 0) updateField(index, "value", password)
            generatorField = null
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fields = emptyList()
                        notes = ""
                        v.home()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
}

@Composable
internal fun CategoryChoice(v: VaultModel, value: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val categories = v.snapshot.optJSONArray("categories").objects()
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(categories.find { it.matchesCategory(value) }?.optString("name") ?: "No category")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(
                text = { Text("No category") },
                onClick = {
                    change("")
                    expanded = false
                },
            )
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.optString("name")) },
                    onClick = {
                        change(c.optString("id"))
                        expanded = false
                    },
                )
            }
        }
    }
}
