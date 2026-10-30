use red_moon_core::{crypto, model::Document};
use secrecy::SecretString;
fn password() -> SecretString {
    SecretString::new("long winter moon passphrase".into())
}
#[test]
fn round_trip_unicode_and_secret_metadata() {
    let doc = Document::default();
    let unlocked = crypto::create(&doc, &password()).unwrap();
    let bytes = crypto::encrypt(&unlocked.document, &unlocked.key, &unlocked.header).unwrap();
    assert!(crypto::decrypt(&bytes, &password())
        .unwrap()
        .document
        .entries
        .is_empty());
}
#[test]
fn wrong_password_corruption_and_tampering_fail() {
    let unlocked = crypto::create(&Document::default(), &password()).unwrap();
    let bytes = crypto::encrypt(&unlocked.document, &unlocked.key, &unlocked.header).unwrap();
    assert!(crypto::decrypt(&bytes, &SecretString::new("wrong password".into())).is_err());
    for offset in [0, 8, 10, 12, 24, 40, 64, 72, 80, bytes.len() - 1] {
        let mut bad = bytes.clone();
        bad[offset] ^= 1;
        assert!(
            crypto::decrypt(&bad, &password()).is_err(),
            "offset {offset}"
        );
    }
    assert!(crypto::decrypt(&bytes[..bytes.len() - 1], &password()).is_err());
    let mut extra = bytes.clone();
    extra.push(0);
    assert!(crypto::decrypt(&extra, &password()).is_err());
}
#[test]
fn fresh_nonce_each_save() {
    let unlocked = crypto::create(&Document::default(), &password()).unwrap();
    let mut seen = std::collections::HashSet::new();
    for _ in 0..1000 {
        let bytes = crypto::encrypt(&unlocked.document, &unlocked.key, &unlocked.header).unwrap();
        assert!(seen.insert(bytes[40..64].to_vec()));
    }
}
#[test]
fn untrusted_resource_bounds_rejected() {
    let unlocked = crypto::create(&Document::default(), &password()).unwrap();
    let mut bytes = crypto::encrypt(&unlocked.document, &unlocked.key, &unlocked.header).unwrap();
    bytes[12..16].copy_from_slice(&u32::MAX.to_le_bytes());
    assert!(crypto::decrypt(&bytes, &password()).is_err());
    assert!(crypto::decrypt(&vec![0; 16 * 1024 * 1024 + 1], &password()).is_err());
}
