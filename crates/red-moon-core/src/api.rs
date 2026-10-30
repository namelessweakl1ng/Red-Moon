use crate::{
    generator::{self, Options},
    model::{Category, Entry, Record, Settings},
    session::Session,
    vault::VaultService,
    Error, Result,
};
use secrecy::{ExposeSecret, SecretString};
use serde::Deserialize;
use serde_json::{json, Value};
use std::path::PathBuf;
#[derive(Deserialize)]
#[serde(tag = "action", rename_all = "snake_case", deny_unknown_fields)]
pub enum Request {
    Status,
    ListRecords,
    SearchRecords {
        query: String,
    },
    RecordDetail {
        id: String,
    },
    EditRecord {
        id: String,
    },
    SaveRecord {
        record: Record,
    },
    DeleteRecord {
        id: String,
    },
    FavoriteRecord {
        id: String,
    },
    RevealField {
        id: String,
        field_id: String,
    },
    SaveCategory {
        category: Category,
    },
    DeleteCategory {
        id: String,
    },
    ReorderCategories {
        ids: Vec<String>,
    },
    DeleteVault {
        password: SecretString,
    },
    Create {
        password: SecretString,
    },
    Unlock {
        password: SecretString,
    },
    Lock,
    Activity,
    List,
    Detail {
        id: String,
    },
    Reveal {
        id: String,
    },
    Save {
        entry: Entry,
    },
    Delete {
        id: String,
    },
    Favorite {
        id: String,
    },
    Generate {
        options: Options,
    },
    Settings {
        settings: Settings,
    },
    ChangePassword {
        current: SecretString,
        new: SecretString,
    },
    CopyUsername {
        id: String,
    },
    CopyPassword {
        id: String,
    },
    CopyGenerated {
        password: SecretString,
    },
    Backup,
    Restore {
        password: SecretString,
        current: Option<SecretString>,
    },
}
pub struct Backend {
    pub vault: VaultService,
    pub session: Session,
}
impl Backend {
    pub fn open(path: PathBuf) -> Result<Self> {
        Ok(Self {
            vault: VaultService::open(path)?,
            session: Session::default(),
        })
    }
    pub fn lock(&mut self) {
        self.vault.lock();
        self.session.lock();
    }
    pub fn check(&mut self, now: u64) -> bool {
        if !self.session.active(now) {
            self.vault.lock();
            return false;
        }
        true
    }
    pub fn guard(&mut self, now: u64) -> Result<()> {
        if !self.check(now) {
            return Err(Error::Locked);
        }
        self.vault.document()?;
        Ok(())
    }
    pub fn start(&mut self, now: u64) -> Result<()> {
        let timeout = self.vault.document()?.settings.auto_lock_seconds;
        self.session.start(now, timeout);
        Ok(())
    }
    pub fn secret(&mut self, id: &str, now: u64, password: bool) -> Result<SecretString> {
        self.guard(now)?;
        let e = self
            .vault
            .document()?
            .entries
            .iter()
            .find(|e| e.id == id)
            .ok_or(Error::Invalid("unknown entry"))?;
        Ok(SecretString::new(if password {
            e.password.clone()
        } else {
            e.username.clone()
        }))
    }
    pub fn execute(&mut self, r: Request, now: u64) -> Result<Value> {
        self.check(now);
        match r {
            Request::Status => {
                return Ok(
                    json!({"exists":self.vault.exists(),"unlocked":self.session.active(now),"generation":self.session.generation}),
                )
            }
            Request::Create { password } => {
                self.vault.create(&password)?;
                self.start(now)?;
                return Ok(json!({"generation":self.session.generation}));
            }
            Request::Unlock { password } => {
                self.vault.unlock(&password)?;
                self.start(now)?;
                return Ok(json!({"generation":self.session.generation}));
            }
            Request::Lock => {
                self.lock();
                return Ok(Value::Null);
            }
            Request::Generate { options } => {
                let p = generator::generate(&options)?;
                return Ok(json!({"password":p.expose_secret()}));
            }
            _ => {}
        }
        self.guard(now)?;
        match r {
            Request::SearchRecords { query } => {
                let query = zeroize::Zeroizing::new(query);
                if query.len() > 4096 {
                    return Err(Error::Invalid("search length"));
                }
                let needle = zeroize::Zeroizing::new(query.to_lowercase());
                let matches = |text: &str| {
                    zeroize::Zeroizing::new(text.to_lowercase()).contains(needle.as_str())
                };
                let d = self.vault.document()?;
                let ids: std::collections::HashSet<String> = d
                    .all_records()
                    .iter()
                    .filter(|r| {
                        matches(&r.title)
                            || matches(&r.notes)
                            || r.tags.iter().any(|t| matches(t))
                            || r.checklist.iter().any(|c| matches(&c.text))
                            || r.fields.iter().any(|f| {
                                f.field_type != "secret" && (matches(&f.label) || matches(&f.value))
                            })
                            || d.categories.iter().any(|c| {
                                (c.id == r.category || c.name.eq_ignore_ascii_case(&r.category))
                                    && matches(&c.name)
                            })
                    })
                    .map(|r| r.id.clone())
                    .collect();
                let mut list = self.execute(Request::ListRecords, now)?;
                list["records"]
                    .as_array_mut()
                    .ok_or(Error::Format)?
                    .retain(|r| r["id"].as_str().is_some_and(|id| ids.contains(id)));
                return Ok(list);
            }
            Request::ListRecords => {
                let d = self.vault.document()?;
                let records = d.all_records();
                let mut duplicate_counts = std::collections::HashMap::new();
                for r in &records {
                    *duplicate_counts
                        .entry((r.kind.as_str(), r.title.trim().to_lowercase()))
                        .or_insert(0usize) += 1;
                }
                let summaries:Vec<Value>=records.iter().map(|r| {let duplicate=duplicate_counts.get(&(r.kind.as_str(),r.title.trim().to_lowercase())).copied().unwrap_or(0)>1;json!({"id":r.id,"kind":r.kind,"title":r.title,"category":r.category,"tags":r.tags,"favorite":r.favorite,"created_at":r.created_at,"modified_at":r.modified_at,"duplicate":duplicate})}).collect();
                return Ok(
                    json!({"records":summaries,"categories":d.categories,"settings":d.settings,"generation":self.session.generation}),
                );
            }
            Request::RecordDetail { id } => {
                let record = self.vault.record(&id)?;
                let mut value = serde_json::to_value(&record)?;
                for (field, original) in value["fields"]
                    .as_array_mut()
                    .ok_or(Error::Format)?
                    .iter_mut()
                    .zip(&record.fields)
                {
                    let secret = original.field_type == "secret";
                    field["concealed"] = json!(secret);
                    if secret {
                        field["value"] = json!("");
                    }
                }
                return Ok(json!({"record":value,"generation":self.session.generation}));
            }
            Request::EditRecord { id } => {
                return Ok(
                    json!({"record":self.vault.record(&id)?,"generation":self.session.generation}),
                )
            }
            Request::RevealField { id, field_id } => {
                let record = self.vault.record(&id)?;
                let field = record
                    .fields
                    .iter()
                    .find(|f| f.id == field_id)
                    .ok_or(Error::Invalid("unknown field"))?;
                return Ok(json!({"value":field.value,"generation":self.session.generation}));
            }
            Request::SaveRecord { record } => self.vault.save_record(record)?,
            Request::DeleteRecord { id } => self.vault.delete_record(&id)?,
            Request::FavoriteRecord { id } => self.vault.favorite_record(&id)?,
            Request::SaveCategory { category } => self.vault.save_category(category)?,
            Request::DeleteCategory { id } => self.vault.delete_category(&id)?,
            Request::ReorderCategories { ids } => self.vault.reorder_categories(&ids)?,
            Request::DeleteVault { password } => {
                self.vault.delete_vault(&password)?;
                self.session.lock();
            }
            Request::Activity => self.session.touch(now),
            Request::List => {
                let d = self.vault.document()?;
                let summaries:Vec<Value>=d.entries.iter().map(|e|{
     let dup=d.entries.iter().any(|other|other.id!=e.id && other.title.trim().to_lowercase()==e.title.trim().to_lowercase() && other.username.trim().to_lowercase()==e.username.trim().to_lowercase() && other.url.trim().trim_end_matches('/').to_lowercase()==e.url.trim().trim_end_matches('/').to_lowercase());
     json!({"id":e.id,"title":e.title,"username":e.username,"url":e.url,"category":e.category,"tags":e.tags,"favorite":e.favorite,"created_at":e.created_at,"modified_at":e.modified_at,"duplicate":dup})
    }).collect();
                return Ok(
                    json!({"entries":summaries,"settings":d.settings,"generation":self.session.generation}),
                );
            }
            Request::Detail { id } => {
                let e = self
                    .vault
                    .document()?
                    .entries
                    .iter()
                    .find(|e| e.id == id)
                    .ok_or(Error::Invalid("unknown entry"))?;
                return Ok(json!({"notes":e.notes,"generation":self.session.generation}));
            }
            Request::Reveal { id } => {
                let p = self.secret(&id, now, true)?;
                return Ok(
                    json!({"password":p.expose_secret(),"generation":self.session.generation}),
                );
            }
            Request::Save { entry } => self.vault.save_entry(entry)?,
            Request::Delete { id } => self.vault.delete_entry(&id)?,
            Request::Favorite { id } => self.vault.favorite(&id)?,
            Request::Settings { settings } => {
                self.vault.settings(settings)?;
                self.start(now)?;
            }
            Request::ChangePassword { current, new } => {
                self.vault.change_password(&current, &new)?;
                self.start(now)?;
            }
            _ => {
                return Err(Error::Invalid(
                    "operation requires a native dialog or clipboard",
                ))
            }
        }
        Ok(Value::Null)
    }
}
