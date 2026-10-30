use base64::{engine::general_purpose::STANDARD, Engine};
use jni::{
    objects::{JClass, JString},
    sys::jstring,
    JNIEnv,
};
use red_moon_core::{
    api::{Backend, Request},
    Error,
};
use secrecy::{ExposeSecret, SecretString};
use serde_json::{json, Value};
use std::{
    path::PathBuf,
    sync::{Mutex, OnceLock},
};
use zeroize::Zeroizing;
pub struct Bridge {
    backend: Backend,
    directory: PathBuf,
}
impl Bridge {
    pub fn open(path: PathBuf) -> Result<Self, String> {
        let directory = path.parent().ok_or("Invalid storage path")?.to_owned();
        Ok(Self {
            backend: Backend::open(path).map_err(|e| e.to_string())?,
            directory,
        })
    }
    pub fn request(&mut self, input: &str, now: u64) -> Value {
        let result = (|| -> red_moon_core::Result<Value> {
            if input.len() > 24 * 1024 * 1024 {
                return Err(Error::Format);
            }
            let v: Value = serde_json::from_str(input)?;
            match v["action"].as_str() {
                Some("backup") => {
                    self.backend.guard(now)?;
                    Ok(json!({"data":STANDARD.encode(self.backend.vault.backup_snapshot()?)}))
                }
                Some("restore") => {
                    let data = STANDARD
                        .decode(v["data"].as_str().ok_or(Error::Format)?)
                        .map_err(|_| Error::Format)?;
                    if data.len() > red_moon_core::crypto::MAX_FILE {
                        return Err(Error::Format);
                    }
                    let password =
                        SecretString::new(v["password"].as_str().ok_or(Error::Format)?.to_owned());
                    let current = v["current"]
                        .as_str()
                        .map(|p| SecretString::new(p.to_owned()));
                    let name = red_moon_core::crypto::random::<16>()?
                        .iter()
                        .map(|b| format!("{b:02x}"))
                        .collect::<String>();
                    let path = self.directory.join(format!(".restore-{name}.rm"));
                    red_moon_core::storage::copy_new(&data, &path)?;
                    let restored = self
                        .backend
                        .vault
                        .restore(&path, &password, current.as_ref());
                    let _ = std::fs::remove_file(path);
                    restored?;
                    self.backend.start(now)?;
                    Ok(Value::Null)
                }
                Some("copy_password") | Some("copy_username") => {
                    let p = self.backend.secret(
                        v["id"].as_str().ok_or(Error::Format)?,
                        now,
                        v["action"] == "copy_password",
                    )?;
                    Ok(json!({"text":p.expose_secret()}))
                }
                _ => self
                    .backend
                    .execute(serde_json::from_str::<Request>(input)?, now),
            }
        })();
        match result {
            Ok(v) => json!({"ok":v}),
            Err(e) => json!({"error":e.to_string()}),
        }
    }
}
static BRIDGE: OnceLock<Mutex<Bridge>> = OnceLock::new();
fn output(env: &mut JNIEnv<'_>, value: String) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
#[no_mangle]
pub extern "system" fn Java_com_redmoon_vault_NativeVault_initialize(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if BRIDGE.get().is_some() {
            return json!({"ok":true});
        }
        let path: String = match env.get_string(&path) {
            Ok(p) => p.into(),
            Err(_) => return json!({"error":"Invalid storage path"}),
        };
        match Bridge::open(PathBuf::from(path)) {
            Ok(b) => {
                let _ = BRIDGE.set(Mutex::new(b));
                json!({"ok":true})
            }
            Err(e) => json!({"error":e}),
        }
    }))
    .unwrap_or_else(|_| json!({"error":"Vault initialization failed"}));
    output(&mut env, result.to_string())
}
#[no_mangle]
pub extern "system" fn Java_com_redmoon_vault_NativeVault_request(
    mut env: JNIEnv,
    _: JClass,
    input: JString,
    now: jni::sys::jlong,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let input: Zeroizing<String> = match env.get_string(&input) {
            Ok(s) => Zeroizing::new(s.into()),
            Err(_) => return json!({"error":"Invalid request"}),
        };
        match BRIDGE.get().and_then(|m| m.lock().ok()) {
            Some(mut b) => b.request(&input, now.max(0) as u64),
            None => json!({"error":"Vault unavailable"}),
        }
    }))
    .unwrap_or_else(|_| json!({"error":"Vault operation failed safely"}));
    output(&mut env, result.to_string())
}
