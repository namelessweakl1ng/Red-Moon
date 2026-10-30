use crate::{Error, Result};
use secrecy::SecretString;
use serde::{Deserialize, Serialize};
use zeroize::Zeroizing;
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Options {
    pub length: usize,
    pub lowercase: bool,
    pub uppercase: bool,
    pub digits: bool,
    pub symbols: bool,
}
impl Default for Options {
    fn default() -> Self {
        Self {
            length: 24,
            lowercase: true,
            uppercase: true,
            digits: true,
            symbols: true,
        }
    }
}
fn index(n: usize) -> Result<usize> {
    let limit = 256 - (256 % n);
    loop {
        let b = crate::crypto::random::<1>()?[0] as usize;
        if b < limit {
            return Ok(b % n);
        }
    }
}
pub fn generate(o: &Options) -> Result<SecretString> {
    if !(12..=128).contains(&o.length) {
        return Err(Error::Invalid("password length must be 12–128"));
    }
    let sets: Vec<&[u8]> = [
        (o.lowercase, b"abcdefghijklmnopqrstuvwxyz".as_slice()),
        (o.uppercase, b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".as_slice()),
        (o.digits, b"0123456789".as_slice()),
        (o.symbols, b"!@#$%^&*-_=+?".as_slice()),
    ]
    .into_iter()
    .filter(|(enabled, _)| *enabled)
    .map(|(_, s)| s)
    .collect();
    if sets.is_empty() {
        return Err(Error::Invalid("select a character type"));
    }
    let alphabet: Vec<u8> = sets.iter().flat_map(|s| s.iter().copied()).collect();
    // Rejection sampling from the full alphabet yields a uniform distribution over valid passwords.
    loop {
        let mut out = Zeroizing::new(Vec::with_capacity(o.length));
        for _ in 0..o.length {
            out.push(alphabet[index(alphabet.len())?]);
        }
        if sets.iter().all(|s| out.iter().any(|c| s.contains(c))) {
            return Ok(SecretString::new(
                String::from_utf8(out.to_vec()).map_err(|_| Error::Random)?,
            ));
        }
    }
}
