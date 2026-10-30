use red_moon_core::{
    crypto,
    model::{Document, Entry},
    vault::VaultService,
};
use secrecy::SecretString;
const FIXTURE: &[u8] = include_bytes!("fixtures/legacy-v1.rm");
fn password() -> SecretString {
    SecretString::new("synthetic legacy fixture password".into())
}
#[test]
fn authentic_v1_fixture_migrates_once_preserves_original_and_reopens() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("vault.rm");
    std::fs::write(&path, FIXTURE).unwrap();
    let mut v = VaultService::open(path.clone()).unwrap();
    assert!(v.unlock(&SecretString::new("incorrect".into())).is_err());
    assert!(!path.with_extension("v1-safety.rm").exists());
    v.unlock(&password()).unwrap();
    let record = v.record("legacy-login").unwrap();
    assert_eq!(record.fields[1].value, "fixture-secret");
    assert_eq!(record.fields[0].id, "username");
    v.save_record(record).unwrap();
    assert!(v.document().unwrap().entries.is_empty());
    assert_eq!(std::fs::read(&path).unwrap()[8], 2);
    assert_eq!(
        std::fs::read(path.with_extension("v1-safety.rm")).unwrap(),
        FIXTURE
    );
    v.favorite_record("legacy-login").unwrap();
    v.lock();
    v.unlock(&password()).unwrap();
    assert_eq!(
        v.record("legacy-login").unwrap().notes,
        "Synthetic legacy notes"
    );
    assert_eq!(
        std::fs::read(path.with_extension("v1-safety.rm")).unwrap(),
        FIXTURE
    );
}
#[test]
fn fresh_install_import_uses_backup_password_and_key_not_installation_key() {
    let dir = tempfile::tempdir().unwrap();
    let src = dir.path().join("import.rm");
    std::fs::write(&src, FIXTURE).unwrap();
    let path = dir.path().join("fresh.rm");
    let mut v = VaultService::open(path).unwrap();
    v.restore(&src, &password(), None).unwrap();
    assert_eq!(
        v.record("legacy-login").unwrap().fields[1].value,
        "fixture-secret"
    );
    let key = v.biometric_key(&password()).unwrap();
    let new = SecretString::new("independent installation password".into());
    v.change_password(&password(), &new).unwrap();
    let replacement = v.biometric_key(&new).unwrap();
    assert_ne!(&*key, &*replacement);
    v.restore(&src, &password(), Some(&new)).unwrap();
    v.lock();
    assert!(v.unlock_with_key(&replacement).is_err());
    v.unlock_with_key(&key).unwrap();
}
#[test]
fn biometric_authenticates_header_ciphertext_and_rejects_locked_enrollment() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("vault.rm");
    std::fs::write(&path, FIXTURE).unwrap();
    let mut v = VaultService::open(path.clone()).unwrap();
    assert!(v.biometric_key(&password()).is_err());
    v.unlock(&password()).unwrap();
    let key = v.biometric_key(&password()).unwrap();
    for offset in [8, 12, 24, 40, 64, 72, 80, FIXTURE.len() - 1] {
        let mut tampered = FIXTURE.to_vec();
        tampered[offset] ^= 1;
        std::fs::write(&path, &tampered).unwrap();
        assert!(v.unlock_with_key(&key).is_err(), "offset {offset}");
        assert!(v.document().is_err());
    }
    assert!(v.unlock_with_key(&key[..31]).is_err());
}
#[test]
fn schema_limits_unknown_fields_and_light_theme() {
    let mut d = Document::default();
    d.settings.theme = "light".into();
    assert!(d.validate().is_ok());
    let mut value = serde_json::to_value(&d).unwrap();
    value["unknown"] = serde_json::json!(true);
    assert!(serde_json::from_value::<Document>(value).is_err());
    let e:Entry=serde_json::from_value(serde_json::json!({"id":"one","title":"Legacy","username":"","password":"","url":"","category":"","tags":[],"notes":"","favorite":false,"created_at":0,"modified_at":0})).unwrap();
    let mut r = red_moon_core::model::Record::from(&e);
    r.notes = "n".repeat(1024 * 1024);
    d.records.push(r);
    assert!(d.validate().is_ok());
    d.records[0].notes.push('n');
    assert!(d.validate().is_err());
    d.records[0].notes.clear();
    d.records[0].fields[0].field_type = "script".into();
    assert!(d.validate().is_err());
    let unlocked = crypto::decrypt(FIXTURE, &password()).unwrap();
    assert_eq!(unlocked.document.schema_version, 2);
}
#[test]
fn locked_existing_v1_can_restore_after_current_password_reauthentication() {
    let dir = tempfile::tempdir().unwrap();
    let src = dir.path().join("import.rm");
    let path = dir.path().join("vault.rm");
    std::fs::write(&src, FIXTURE).unwrap();
    std::fs::write(&path, FIXTURE).unwrap();
    let mut v = VaultService::open(path.clone()).unwrap();
    v.restore(&src, &password(), Some(&password())).unwrap();
    assert_eq!(
        std::fs::read(path.with_extension("v1-safety.rm")).unwrap(),
        FIXTURE
    );
}
#[test]
fn custom_legacy_category_is_available_for_organization_after_restore() {
    let fixture = include_bytes!("fixtures/legacy-custom-v1.rm");
    let unlocked = crypto::decrypt(fixture, &password()).unwrap();
    let category = unlocked
        .document
        .categories
        .iter()
        .find(|c| c.name == "Legacy family")
        .expect("legacy custom category must have an encrypted category object");
    assert!(
        unlocked.document.entries[0].category == category.id
            || unlocked.document.entries[0].category == category.name
    );
    let dir = tempfile::tempdir().unwrap();
    let source = dir.path().join("backup.rm");
    std::fs::write(&source, fixture).unwrap();
    let mut vault = VaultService::open(dir.path().join("vault.rm")).unwrap();
    vault.restore(&source, &password(), None).unwrap();
    let category = vault
        .document()
        .unwrap()
        .categories
        .iter()
        .find(|c| c.name == "Legacy family")
        .unwrap()
        .clone();
    vault.delete_category(&category.id).unwrap();
    assert_eq!(vault.record("legacy-login").unwrap().category, "");
}
