// Independent v1 writer: fixed synthetic salt/nonce/password for a reproducible migration fixture.
#[test]
#[ignore = "explicitly regenerate the checked-in independent synthetic v1 fixture"]
fn make_fixture() {
    use argon2::{Algorithm, Argon2, Params, Version};
    use chacha20poly1305::{
        aead::{Aead, KeyInit, Payload},
        XChaCha20Poly1305, XNonce,
    };
    let plaintext=br#"{"entries":[{"id":"legacy-login","title":"Synthetic legacy account","username":"fixture-user","password":"fixture-secret","url":"https://fixture.invalid","category":"Personal","tags":["legacy"],"notes":"Synthetic legacy notes","favorite":true,"created_at":100,"modified_at":200}],"settings":{"auto_lock_seconds":300,"theme":"dark"}}"#;
    let salt = [17u8; 16];
    let nonce = [23u8; 24];
    let mut key = [0u8; 32];
    Argon2::new(
        Algorithm::Argon2id,
        Version::V0x13,
        Params::new(65536, 3, 1, Some(32)).unwrap(),
    )
    .hash_password_into(b"synthetic legacy fixture password", &salt, &mut key)
    .unwrap();
    let mut header = b"REDMOON\0".to_vec();
    header.extend([1, 1, 1, 19]);
    for n in [65536u32, 3, 1] {
        header.extend(n.to_le_bytes());
    }
    header.extend(salt);
    header.extend(nonce);
    header.extend(((plaintext.len() + 16) as u64).to_le_bytes());
    header.extend([0; 8]);
    let ciphertext = XChaCha20Poly1305::new((&key).into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: plaintext,
                aad: &header,
            },
        )
        .unwrap();
    header.extend(ciphertext);
    std::fs::write(
        concat!(env!("CARGO_MANIFEST_DIR"), "/tests/fixtures/legacy-v1.rm"),
        header,
    )
    .unwrap();
}

// Independent v1 writer: fixed synthetic salt/nonce/password for a reproducible migration fixture.
#[test]
#[ignore = "explicitly regenerate the checked-in independent synthetic v1 fixture"]
fn make_custom_fixture() {
    use argon2::{Algorithm, Argon2, Params, Version};
    use chacha20poly1305::{
        aead::{Aead, KeyInit, Payload},
        XChaCha20Poly1305, XNonce,
    };
    let plaintext=br#"{"entries":[{"id":"legacy-login","title":"Synthetic legacy account","username":"fixture-user","password":"fixture-secret","url":"https://fixture.invalid","category":"Legacy family","tags":["legacy"],"notes":"Synthetic legacy notes","favorite":true,"created_at":100,"modified_at":200}],"settings":{"auto_lock_seconds":300,"theme":"dark"}}"#;
    let salt = [17u8; 16];
    let nonce = [24u8; 24];
    let mut key = [0u8; 32];
    Argon2::new(
        Algorithm::Argon2id,
        Version::V0x13,
        Params::new(65536, 3, 1, Some(32)).unwrap(),
    )
    .hash_password_into(b"synthetic legacy fixture password", &salt, &mut key)
    .unwrap();
    let mut header = b"REDMOON\0".to_vec();
    header.extend([1, 1, 1, 19]);
    for n in [65536u32, 3, 1] {
        header.extend(n.to_le_bytes());
    }
    header.extend(salt);
    header.extend(nonce);
    header.extend(((plaintext.len() + 16) as u64).to_le_bytes());
    header.extend([0; 8]);
    let ciphertext = XChaCha20Poly1305::new((&key).into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: plaintext,
                aad: &header,
            },
        )
        .unwrap();
    header.extend(ciphertext);
    std::fs::write(
        concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/fixtures/legacy-custom-v1.rm"
        ),
        header,
    )
    .unwrap();
}
