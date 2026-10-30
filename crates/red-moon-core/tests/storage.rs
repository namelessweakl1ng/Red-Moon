use red_moon_core::{model::Entry, storage::Storage, vault::VaultService};
use secrecy::SecretString;
fn p() -> SecretString {
    SecretString::new("long winter moon passphrase".into())
}
#[test]
fn create_save_reopen_change_password_and_lock_contention() {
    let temp = tempfile::tempdir().unwrap();
    let path = temp.path().join("vault.rm");
    let mut vault = VaultService::open(path.clone()).unwrap();
    assert!(VaultService::open(path.clone()).is_err());
    vault.create(&p()).unwrap();
    vault
        .save_entry(Entry {
            id: String::new(),
            title: "私の Bank".into(),
            username: "alice".into(),
            password: "secret sentinel".into(),
            url: String::new(),
            category: String::new(),
            tags: vec![],
            notes: "private note".into(),
            favorite: false,
            created_at: 0,
            modified_at: 0,
        })
        .unwrap();
    let raw = std::fs::read(&path).unwrap();
    assert!(!raw.windows(15).any(|w| w == b"secret sentinel"));
    vault.lock();
    assert!(vault.document().is_err());
    assert!(vault.unlock(&SecretString::new("bad".into())).is_err());
    vault.unlock(&p()).unwrap();
    assert_eq!(vault.document().unwrap().entries[0].title, "私の Bank");
    let new = SecretString::new("a different strong passphrase".into());
    vault.change_password(&p(), &new).unwrap();
    vault.lock();
    assert!(vault.unlock(&p()).is_err());
    vault.unlock(&new).unwrap();
    drop(vault);
    let mut reopened = VaultService::open(path).unwrap();
    reopened.unlock(&new).unwrap();
    assert_eq!(reopened.document().unwrap().entries.len(), 1);
}
#[test]
fn valid_and_invalid_restore_preserve_data() {
    let temp = tempfile::tempdir().unwrap();
    let path = temp.path().join("vault.rm");
    let backup = temp.path().join("backup.rm");
    let mut vault = VaultService::open(path.clone()).unwrap();
    vault.create(&p()).unwrap();
    vault.backup(&backup).unwrap();
    let before = std::fs::read(&path).unwrap();
    assert!(vault
        .restore(&backup, &SecretString::new("bad".into()), Some(&p()))
        .is_err());
    assert_eq!(std::fs::read(&path).unwrap(), before);
    vault.restore(&backup, &p(), Some(&p())).unwrap();
    assert!(vault.document().is_ok());
    let mut damaged = std::fs::read(&backup).unwrap();
    damaged[85] ^= 1;
    let bad = temp.path().join("bad.rm");
    std::fs::write(&bad, damaged).unwrap();
    let before = std::fs::read(&path).unwrap();
    assert!(vault.restore(&bad, &p(), Some(&p())).is_err());
    assert_eq!(std::fs::read(&path).unwrap(), before);
}
#[test]
fn interrupted_write_preserves_old_vault() {
    let temp = tempfile::tempdir().unwrap();
    let path = temp.path().join("vault.rm");
    let storage = Storage::open(path.clone()).unwrap();
    storage.commit(b"old encrypted fixture").unwrap();
    assert!(storage
        .commit_with(b"new encrypted fixture", |_| Err(
            red_moon_core::Error::Storage
        ))
        .is_err());
    assert_eq!(storage.read().unwrap(), b"old encrypted fixture");
}
#[cfg(unix)]
#[test]
fn files_private_and_symlinks_rejected() {
    use std::os::unix::fs::{symlink, PermissionsExt};
    let temp = tempfile::tempdir().unwrap();
    let path = temp.path().join("vault.rm");
    let storage = Storage::open(path.clone()).unwrap();
    storage.commit(b"encrypted").unwrap();
    assert_eq!(
        std::fs::metadata(&path).unwrap().permissions().mode() & 0o777,
        0o600
    );
    let link = temp.path().join("link.rm");
    symlink(&path, &link).unwrap();
    assert!(Storage::open(link).is_err());
}
