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
#[derive(Clone, Default, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(deny_unknown_fields)]
pub struct Document {
    pub entries: Vec<Entry>,
    pub settings: Settings,
}
impl Document {
    pub fn validate(&self) -> crate::Result<()> {
        if self.entries.len() > 10_000
            || !(30..=3600).contains(&self.settings.auto_lock_seconds)
            || !["dark", "contrast", "system"].contains(&self.settings.theme.as_str())
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
                || e.notes.len() > 65536
                || e.category.len() > 256
                || e.tags.len() > 50
                || e.tags.iter().any(|t| t.len() > 256)
                || e.modified_at < e.created_at
            {
                return Err(crate::Error::Invalid("entry limits"));
            }
        }
        Ok(())
    }
}
