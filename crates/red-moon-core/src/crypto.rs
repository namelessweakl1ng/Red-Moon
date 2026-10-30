use crate::{model::Document, Error, Result};
use argon2::{Algorithm, Argon2, Params, Version};
use chacha20poly1305::{
    aead::{Aead, KeyInit, Payload},
    XChaCha20Poly1305, XNonce,
};
use rand::{rngs::OsRng, RngCore};
use secrecy::{ExposeSecret, SecretString};
use std::time::Instant;
use zeroize::Zeroizing;
pub const MAX_FILE: usize = 16 * 1024 * 1024;
const MAGIC: &[u8; 8] = b"REDMOON\0";
#[derive(Clone)]
pub struct Header {
    pub memory: u32,
    pub iterations: u32,
    pub lanes: u32,
    pub salt: [u8; 16],
}
pub struct UnlockedVault {
    pub document: Document,
    pub key: Zeroizing<[u8; 32]>,
    pub header: Header,
}
pub fn random<const N: usize>() -> Result<[u8; N]> {
    let mut bytes = [0; N];
    OsRng
        .try_fill_bytes(&mut bytes)
        .map_err(|_| Error::Random)?;
    Ok(bytes)
}
pub fn validate_password(password: &SecretString, creating: bool) -> Result<()> {
    let p = password.expose_secret();
    if p.len() > 1024 || (creating && p.chars().count() < 14) {
        return Err(Error::Invalid(
            "master password must contain at least 14 characters and at most 1024 UTF-8 bytes",
        ));
    }
    Ok(())
}
fn derive(password: &SecretString, h: &Header) -> Result<Zeroizing<[u8; 32]>> {
    validate_password(password, false)?;
    if !(65536..=262144).contains(&h.memory)
        || !(3..=10).contains(&h.iterations)
        || !(1..=4).contains(&h.lanes)
    {
        return Err(Error::Format);
    }
    let p = Params::new(h.memory, h.iterations, h.lanes, Some(32)).map_err(|_| Error::Format)?;
    let mut key = Zeroizing::new([0; 32]);
    Argon2::new(Algorithm::Argon2id, Version::V0x13, p)
        .hash_password_into(password.expose_secret().as_bytes(), &h.salt, key.as_mut())
        .map_err(|_| Error::Authentication)?;
    Ok(key)
}
pub fn create(doc: &Document, password: &SecretString) -> Result<UnlockedVault> {
    validate_password(password, true)?;
    doc.validate()?;
    let mut header = Header {
        memory: 65536,
        iterations: 3,
        lanes: 1,
        salt: random()?,
    };
    let start = Instant::now();
    let mut key = derive(password, &header)?;
    // Calibrate once without ever weakening the production floor.
    let elapsed = start.elapsed().as_millis().max(1);
    let desired = ((1500 / elapsed) as u32).clamp(3, 10);
    if desired > 3 {
        header.iterations = desired;
        key = derive(password, &header)?;
    }
    Ok(UnlockedVault {
        document: doc.clone(),
        key,
        header,
    })
}
pub fn encrypt(doc: &Document, key: &[u8; 32], h: &Header) -> Result<Vec<u8>> {
    doc.validate()?;
    let plaintext = Zeroizing::new(serde_json::to_vec(doc)?);
    if plaintext.len() > MAX_FILE - 96 {
        return Err(Error::Invalid("vault size limit"));
    }
    let nonce: [u8; 24] = random()?;
    let mut header = Vec::with_capacity(80);
    header.extend_from_slice(MAGIC);
    header.extend_from_slice(&[2, 1, 1, 19]);
    for n in [h.memory, h.iterations, h.lanes] {
        header.extend_from_slice(&n.to_le_bytes());
    }
    header.extend_from_slice(&h.salt);
    header.extend_from_slice(&nonce);
    header.extend_from_slice(&((plaintext.len() + 16) as u64).to_le_bytes());
    header.extend_from_slice(&[0; 8]);
    let ciphertext = XChaCha20Poly1305::new(key.into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: &plaintext,
                aad: &header,
            },
        )
        .map_err(|_| Error::Authentication)?;
    header.extend(ciphertext);
    Ok(header)
}
fn parse_header(bytes: &[u8]) -> Result<Header> {
    if bytes.len() < 96
        || bytes.len() > MAX_FILE
        || &bytes[..8] != MAGIC
        || ![1, 2].contains(&bytes[8])
        || bytes[9..12] != [1, 1, 19]
        || bytes[72..80] != [0; 8]
    {
        return Err(Error::Format);
    }
    let num = |i| u32::from_le_bytes(bytes[i..i + 4].try_into().expect("validated header"));
    let length = u64::from_le_bytes(bytes[64..72].try_into().expect("validated header"));
    if length != (bytes.len() - 80) as u64 {
        return Err(Error::Format);
    }
    let header = Header {
        memory: num(12),
        iterations: num(16),
        lanes: num(20),
        salt: bytes[24..40].try_into().expect("validated header"),
    };
    if !(65536..=262144).contains(&header.memory)
        || !(3..=10).contains(&header.iterations)
        || !(1..=4).contains(&header.lanes)
    {
        return Err(Error::Format);
    }
    Ok(header)
}
pub fn decrypt(bytes: &[u8], password: &SecretString) -> Result<UnlockedVault> {
    let header = parse_header(bytes)?;
    let key = derive(password, &header)?;
    decrypt_with_key(bytes, &*key)
}
pub fn decrypt_with_key(bytes: &[u8], raw_key: &[u8]) -> Result<UnlockedVault> {
    let header = parse_header(bytes)?;
    let key = Zeroizing::new(<[u8; 32]>::try_from(raw_key).map_err(|_| Error::Authentication)?);
    let plaintext = Zeroizing::new(
        XChaCha20Poly1305::new((&*key).into())
            .decrypt(
                XNonce::from_slice(&bytes[40..64]),
                Payload {
                    msg: &bytes[80..],
                    aad: &bytes[..80],
                },
            )
            .map_err(|_| Error::Authentication)?,
    );
    let mut document: Document = serde_json::from_slice(&plaintext)?;
    document.validate()?;
    document.materialize_legacy_categories()?;
    document.validate()?;
    Ok(UnlockedVault {
        document,
        key,
        header,
    })
}
