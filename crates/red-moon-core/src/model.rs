use serde::{Deserialize, Serialize};
use zeroize::{Zeroize, ZeroizeOnDrop};
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct Entry {
    pub id: String,
    pub title: String,
    pub username: String,
    pub password: String,
    pub url: String,
    pub category: String,
    pub tags: Vec<String>,
    pub notes: String,
    pub favorite: bool,
    pub created_at: u64,
    pub modified_at: u64,
}
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct Settings {
    pub auto_lock_seconds: u64,
    pub theme: String,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            auto_lock_seconds: 300,
            theme: "dark".into(),
        }
    }
}
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct Document {
    #[serde(default = "schema_version")]
    pub schema_version: u32,
    #[serde(default)]
    pub records: Vec<Record>,
    #[serde(default = "default_categories")]
    pub categories: Vec<Category>,
    pub entries: Vec<Entry>,
    pub settings: Settings,
}
impl Document {
    /// Preserve organization from legacy free-form category strings as encrypted objects.
    pub fn materialize_legacy_categories(&mut self) -> crate::Result<()> {
        let values: Vec<String> = self
            .entries
            .iter()
            .map(|e| e.category.clone())
            .chain(self.records.iter().map(|r| r.category.clone()))
            .filter(|s| !s.is_empty())
            .collect();
        for value in values {
            if !self
                .categories
                .iter()
                .any(|c| c.id == value || c.name.to_lowercase() == value.to_lowercase())
            {
                if self.categories.len() >= 1000 {
                    return Err(crate::Error::Invalid("category limit during migration"));
                }
                self.categories.push(Category {
                    id: uuid::Uuid::new_v4().to_string(),
                    name: value,
                    icon: "folder".into(),
                    color: "#64748b".into(),
                    order: self.categories.len() as u32,
                });
            }
        }
        Ok(())
    }
    pub fn validate(&self) -> crate::Result<()> {
        if self.entries.len() + self.records.len() > 10_000
            || self.categories.len() > 1000
            || self.schema_version != 2
            || !(30..=3600).contains(&self.settings.auto_lock_seconds)
            || !["light", "dark", "contrast", "system"].contains(&self.settings.theme.as_str())
        {
            return Err(crate::Error::Invalid("vault limits"));
        }
        let mut ids = std::collections::HashSet::new();
        for e in &self.entries {
            if e.id.is_empty()
                || !ids.insert(&e.id)
                || e.title.trim().is_empty()
                || e.title.len() > 512
                || e.username.len() > 4096
                || e.password.len() > 4096
                || e.url.len() > 4096
                || e.notes.len() > 1024 * 1024
                || e.category.len() > 256
                || e.tags.len() > 50
                || e.tags.iter().any(|t| t.len() > 256)
                || e.modified_at < e.created_at
            {
                return Err(crate::Error::Invalid("entry limits"));
            }
        }
        for r in &self.records {
            if r.id.is_empty()
                || !ids.insert(&r.id)
                || r.title.trim().is_empty()
                || r.title.len() > 512
                || ![
                    "login",
                    "secure_note",
                    "identity",
                    "indian_document",
                    "financial",
                    "custom",
                ]
                .contains(&r.kind.as_str())
                || r.fields.len() > 100
                || r.notes.len() > 1024 * 1024
                || r.category.len() > 256
                || r.tags.len() > 50
                || r.tags.iter().any(|t| t.len() > 256)
                || r.modified_at < r.created_at
                || r.checklist.len() > 1000
            {
                return Err(crate::Error::Invalid("record limits"));
            }
            let mut fields = std::collections::HashSet::new();
            for f in &r.fields {
                if f.id.is_empty()
                    || f.id.len() > 256
                    || !fields.insert(&f.id)
                    || f.label.len() > 512
                    || f.value.len() > 1024 * 1024
                    || !["text", "secret", "multiline", "number", "date", "url"]
                        .contains(&f.field_type.as_str())
                {
                    return Err(crate::Error::Invalid("field limits"));
                }
            }
            let mut checks = std::collections::HashSet::new();
            for c in &r.checklist {
                if c.id.is_empty()
                    || c.id.len() > 256
                    || !checks.insert(&c.id)
                    || c.text.len() > 65536
                {
                    return Err(crate::Error::Invalid("checklist limits"));
                }
            }
        }
        let mut categories = std::collections::HashSet::new();
        for c in &self.categories {
            if c.id.is_empty()
                || c.id.len() > 256
                || !categories.insert(&c.id)
                || c.name.trim().is_empty()
                || c.name.len() > 256
                || c.icon.len() > 256
                || c.color.len() > 128
            {
                return Err(crate::Error::Invalid("category limits"));
            }
        }
        Ok(())
    }
}

fn schema_version() -> u32 {
    2
}
impl Default for Document {
    fn default() -> Self {
        Self {
            schema_version: 2,
            entries: vec![],
            records: vec![],
            categories: default_categories(),
            settings: Settings::default(),
        }
    }
}
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct Record {
    pub id: String,
    pub kind: String,
    pub title: String,
    pub category: String,
    pub tags: Vec<String>,
    pub favorite: bool,
    pub created_at: u64,
    pub modified_at: u64,
    pub fields: Vec<RecordField>,
    pub notes: String,
    pub checklist: Vec<CheckItem>,
}
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct RecordField {
    pub id: String,
    pub label: String,
    pub field_type: String,
    pub value: String,
}
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct CheckItem {
    pub id: String,
    pub text: String,
    pub checked: bool,
}
#[derive(Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct Category {
    pub id: String,
    pub name: String,
    pub icon: String,
    pub color: String,
    pub order: u32,
}
pub fn default_categories() -> Vec<Category> {
    [
        ("email", "Email accounts", "mail", "#2563eb"),
        ("social", "Social media", "people", "#db2777"),
        ("banking", "Banking", "bank", "#059669"),
        ("government", "Government documents", "document", "#b45309"),
        ("shopping", "Shopping", "shopping", "#c026d3"),
        ("education", "Education", "school", "#0891b2"),
        ("other", "Other", "folder", "#64748b"),
        ("personal", "Personal", "person", "#9333ea"),
        ("work", "Work", "briefcase", "#2563eb"),
        ("finance", "Finance", "wallet", "#16a34a"),
    ]
    .into_iter()
    .enumerate()
    .map(|(order, (id, name, icon, color))| Category {
        id: id.into(),
        name: name.into(),
        icon: icon.into(),
        color: color.into(),
        order: order as u32,
    })
    .collect()
}
impl From<&Entry> for Record {
    fn from(e: &Entry) -> Self {
        Self {
            id: e.id.clone(),
            kind: "login".into(),
            title: e.title.clone(),
            category: e.category.clone(),
            tags: e.tags.clone(),
            favorite: e.favorite,
            created_at: e.created_at,
            modified_at: e.modified_at,
            fields: vec![
                RecordField {
                    id: "username".into(),
                    label: "Username".into(),
                    field_type: "text".into(),
                    value: e.username.clone(),
                },
                RecordField {
                    id: "password".into(),
                    label: "Password".into(),
                    field_type: "secret".into(),
                    value: e.password.clone(),
                },
                RecordField {
                    id: "url".into(),
                    label: "Website".into(),
                    field_type: "url".into(),
                    value: e.url.clone(),
                },
            ],
            notes: e.notes.clone(),
            checklist: vec![],
        }
    }
}
impl Document {
    pub fn all_records(&self) -> Vec<Record> {
        self.records
            .iter()
            .cloned()
            .chain(self.entries.iter().map(Record::from))
            .collect()
    }
}
