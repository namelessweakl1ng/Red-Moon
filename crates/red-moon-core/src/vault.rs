use crate::{
    crypto::{self, UnlockedVault},
    model::{Document, Entry, Settings},
    storage::{self, Storage},
    Error, Result,
};
use secrecy::SecretString;
use std::{
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};
pub struct VaultService {
    storage: Storage,
    unlocked: Option<UnlockedVault>,
}
impl VaultService {
    pub fn open(path: PathBuf) -> Result<Self> {
        Ok(Self {
            storage: Storage::open(path)?,
            unlocked: None,
        })
    }
    pub fn exists(&self) -> bool {
        self.storage.exists()
    }
    pub fn create(&mut self, p: &SecretString) -> Result<()> {
        if self.exists() {
            return Err(Error::Invalid("vault already exists"));
        }
        let v = crypto::create(&Document::default(), p)?;
        self.storage
            .commit(&crypto::encrypt(&v.document, &v.key, &v.header)?)?;
        self.unlocked = Some(v);
        Ok(())
    }
    pub fn unlock(&mut self, p: &SecretString) -> Result<()> {
        let v = crypto::decrypt(&self.storage.read()?, p)?;
        self.unlocked = Some(v);
        Ok(())
    }
    pub fn lock(&mut self) {
        self.unlocked = None;
    }
    pub fn document(&self) -> Result<&Document> {
        self.unlocked
            .as_ref()
            .map(|v| &v.document)
            .ok_or(Error::Locked)
    }
    fn save(&mut self, doc: Document) -> Result<()> {
        let v = self.unlocked.as_ref().ok_or(Error::Locked)?;
        let bytes = crypto::encrypt(&doc, &v.key, &v.header)?;
        if let Err(e) = self.storage.commit(&bytes) {
            if matches!(e, Error::Uncertain) {
                self.lock();
            }
            return Err(e);
        }
        self.unlocked.as_mut().ok_or(Error::Locked)?.document = doc;
        Ok(())
    }
    pub fn save_entry(&mut self, mut e: Entry) -> Result<()> {
        let mut doc = self.document()?.clone();
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| Error::Storage)?
            .as_secs();
        if e.id.is_empty() {
            e.id = uuid::Uuid::new_v4().to_string();
            e.created_at = now;
            e.modified_at = now;
            doc.entries.push(e);
        } else {
            let old = doc
                .entries
                .iter_mut()
                .find(|old| old.id == e.id)
                .ok_or(Error::Invalid("unknown entry"))?;
            e.created_at = old.created_at;
            e.modified_at = now.max(old.modified_at);
            *old = e;
        }
        doc.validate()?;
        self.save(doc)
    }
    pub fn delete_entry(&mut self, id: &str) -> Result<()> {
        let mut d = self.document()?.clone();
        let before = d.entries.len();
        d.entries.retain(|e| e.id != id);
        if d.entries.len() == before {
            return Err(Error::Invalid("unknown entry"));
        }
        self.save(d)
    }
    pub fn favorite(&mut self, id: &str) -> Result<()> {
        let mut d = self.document()?.clone();
        let e = d
            .entries
            .iter_mut()
            .find(|e| e.id == id)
            .ok_or(Error::Invalid("unknown entry"))?;
        e.favorite = !e.favorite;
        self.save(d)
    }
    pub fn settings(&mut self, s: Settings) -> Result<()> {
        let mut d = self.document()?.clone();
        d.settings = s;
        d.validate()?;
        self.save(d)
    }
    pub fn change_password(&mut self, old: &SecretString, new: &SecretString) -> Result<()> {
        self.document()?;
        crypto::decrypt(&self.storage.read()?, old)?;
        let v = crypto::create(self.document()?, new)?;
        let bytes = crypto::encrypt(&v.document, &v.key, &v.header)?;
        if let Err(e) = self.storage.commit(&bytes) {
            if matches!(e, Error::Uncertain) {
                self.lock();
            }
            return Err(e);
        }
        self.unlocked = Some(v);
        Ok(())
    }
    pub fn backup_snapshot(&self) -> Result<Vec<u8>> {
        let v = self.unlocked.as_ref().ok_or(Error::Locked)?;
        crypto::encrypt(&v.document, &v.key, &v.header)
    }
    pub fn backup(&self, dst: &Path) -> Result<()> {
        self.document()?;
        storage::copy_new(&self.storage.read()?, dst)
    }
    pub fn restore(
        &mut self,
        src: &Path,
        p: &SecretString,
        current: Option<&SecretString>,
    ) -> Result<()> {
        let bytes = storage::read_bounded(src)?;
        let v = crypto::decrypt(&bytes, p)?;
        if self.exists() {
            crypto::decrypt(&self.storage.read()?, current.ok_or(Error::Authentication)?)?;
            self.storage.safety_copy()?;
        }
        // Re-encryption guarantees a fresh nonce even when restoring an old authenticated backup.
        let bytes = crypto::encrypt(&v.document, &v.key, &v.header)?;
        if let Err(e) = self.storage.commit(&bytes) {
            if matches!(e, Error::Uncertain) {
                self.lock();
            }
            return Err(e);
        }
        self.unlocked = Some(v);
        Ok(())
    }
}
