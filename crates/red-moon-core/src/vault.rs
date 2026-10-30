use crate::{
    crypto::{self, UnlockedVault},
    model::{Category, Document, Entry, Record, Settings},
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
    fn save(&mut self, mut doc: Document) -> Result<()> {
        doc.materialize_legacy_categories()?;
        let v = self.unlocked.as_ref().ok_or(Error::Locked)?;
        let bytes = crypto::encrypt(&doc, &v.key, &v.header)?;
        let original = self.storage.read()?;
        if original.get(8) == Some(&1) {
            crypto::decrypt_with_key(&original, &*v.key)?;
        }
        crypto::decrypt_with_key(&bytes, &*v.key)?;
        self.preserve_v1()?;
        if let Err(e) = self.commit_verified(&bytes, &*v.key) {
            if matches!(e, Error::Uncertain) {
                self.lock();
            }
            return Err(e);
        }
        self.unlocked.as_mut().ok_or(Error::Locked)?.document = doc;
        Ok(())
    }
    fn commit_verified(&self, bytes: &[u8], key: &[u8]) -> Result<()> {
        self.storage.commit_with(bytes, |file| {
            use std::io::{Read, Seek};
            let mut file = file.try_clone()?;
            file.rewind()?;
            let mut persisted = Vec::new();
            file.read_to_end(&mut persisted)?;
            crypto::decrypt_with_key(&persisted, key)?;
            Ok(())
        })
    }
    fn preserve_v1(&self) -> Result<()> {
        if self.storage.exists() {
            let original = self.storage.read()?;
            if original.get(8) == Some(&1) {
                let dst = self.storage.path().with_extension("v1-safety.rm");
                if dst.exists() {
                    if storage::read_bounded(&dst)? != original {
                        return Err(Error::Invalid("migration safety copy differs"));
                    }
                } else {
                    storage::copy_new(&original, &dst)?;
                }
            }
        }
        Ok(())
    }
    pub fn biometric_key(&self, password: &SecretString) -> Result<zeroize::Zeroizing<Vec<u8>>> {
        let current = self.unlocked.as_ref().ok_or(Error::Locked)?;
        let verified = crypto::decrypt(&self.storage.read()?, password)?;
        if *verified.key != *current.key {
            return Err(Error::Authentication);
        }
        Ok(zeroize::Zeroizing::new(verified.key.to_vec()))
    }
    pub fn unlock_with_key(&mut self, key: &[u8]) -> Result<()> {
        self.lock();
        let v = crypto::decrypt_with_key(&self.storage.read()?, key)?;
        self.unlocked = Some(v);
        Ok(())
    }
    pub fn record(&self, id: &str) -> Result<Record> {
        let d = self.document()?;
        if let Some(record) = d.records.iter().find(|r| r.id == id) {
            return Ok(record.clone());
        }
        d.entries
            .iter()
            .find(|e| e.id == id)
            .map(Record::from)
            .ok_or(Error::Invalid("unknown record"))
    }
    pub fn save_record(&mut self, mut record: Record) -> Result<()> {
        let mut d = self.document()?.clone();
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| Error::Storage)?
            .as_secs();
        if record.id.is_empty() {
            record.id = uuid::Uuid::new_v4().to_string();
            record.created_at = now;
        } else {
            let old = self.record(&record.id)?;
            record.created_at = old.created_at;
            record.modified_at = old.modified_at;
        }
        record.modified_at = now.max(record.modified_at);
        d.entries.retain(|e| e.id != record.id);
        d.records.retain(|r| r.id != record.id);
        d.records.push(record);
        self.save(d)
    }
    pub fn delete_record(&mut self, id: &str) -> Result<()> {
        self.record(id)?;
        let mut d = self.document()?.clone();
        d.entries.retain(|e| e.id != id);
        d.records.retain(|r| r.id != id);
        self.save(d)
    }
    pub fn favorite_record(&mut self, id: &str) -> Result<()> {
        let mut r = self.record(id)?;
        r.favorite = !r.favorite;
        self.save_record(r)
    }
    pub fn save_category(&mut self, mut category: Category) -> Result<()> {
        let mut d = self.document()?.clone();
        if category.id.is_empty() {
            category.id = uuid::Uuid::new_v4().to_string();
            d.categories.push(category);
        } else {
            let old = d
                .categories
                .iter_mut()
                .find(|c| c.id == category.id)
                .ok_or(Error::Invalid("unknown category"))?;
            let old_name = old.name.clone();
            let category_id = old.id.clone();
            *old = category;
            for r in &mut d.records {
                if r.category.eq_ignore_ascii_case(&old_name) {
                    r.category = category_id.clone();
                }
            }
            for e in &mut d.entries {
                if e.category.eq_ignore_ascii_case(&old_name) {
                    e.category = category_id.clone();
                }
            }
        }
        self.save(d)
    }
    pub fn delete_category(&mut self, id: &str) -> Result<()> {
        let mut d = self.document()?.clone();
        let count = d.categories.len();
        let old_name = d
            .categories
            .iter()
            .find(|c| c.id == id)
            .map(|c| c.name.clone())
            .ok_or(Error::Invalid("unknown category"))?;
        d.categories.retain(|c| c.id != id);
        if count == d.categories.len() {
            return Err(Error::Invalid("unknown category"));
        }
        for r in &mut d.records {
            if r.category == id || r.category.eq_ignore_ascii_case(&old_name) {
                r.category.clear();
            }
        }
        for e in &mut d.entries {
            if e.category == id || e.category.eq_ignore_ascii_case(&old_name) {
                e.category.clear();
            }
        }
        self.save(d)
    }
    pub fn reorder_categories(&mut self, ids: &[String]) -> Result<()> {
        let mut d = self.document()?.clone();
        if ids.len() != d.categories.len()
            || ids.iter().collect::<std::collections::HashSet<_>>().len() != ids.len()
        {
            return Err(Error::Invalid("category order"));
        }
        for (order, id) in ids.iter().enumerate() {
            let c = d
                .categories
                .iter_mut()
                .find(|c| &c.id == id)
                .ok_or(Error::Invalid("unknown category"))?;
            c.order = order as u32;
        }
        d.categories.sort_by_key(|c| c.order);
        self.save(d)
    }
    pub fn delete_vault(&mut self, password: &SecretString) -> Result<()> {
        self.document()?;
        crypto::decrypt(&self.storage.read()?, password)?;
        std::fs::remove_file(self.storage.path())?;
        self.lock();
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
        crypto::decrypt_with_key(&bytes, &*v.key)?;
        self.preserve_v1()?;
        if let Err(e) = self.commit_verified(&bytes, &*v.key) {
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
        crypto::decrypt_with_key(&bytes, &*v.key)?;
        self.preserve_v1()?;
        if let Err(e) = self.commit_verified(&bytes, &*v.key) {
            if matches!(e, Error::Uncertain) {
                self.lock();
            }
            return Err(e);
        }
        self.unlocked = Some(v);
        Ok(())
    }
}
