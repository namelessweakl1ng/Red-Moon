use crate::{
    generator::{self, Options},
    model::{Entry, Settings},
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
