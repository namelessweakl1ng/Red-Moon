#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod platform_lock;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
static DIALOG_OPEN: AtomicBool = AtomicBool::new(false);
static SECURITY_EPOCH: AtomicU64 = AtomicU64::new(0);
struct DialogGuard;
impl DialogGuard {
    fn enter() -> Self {
        DIALOG_OPEN.store(true, Ordering::SeqCst);
        Self
    }
}
impl Drop for DialogGuard {
    fn drop(&mut self) {
        DIALOG_OPEN.store(false, Ordering::SeqCst);
    }
}
use red_moon_core::{
    api::{Backend, Request},
    session::ClipboardLease,
    storage, Error,
};
use secrecy::ExposeSecret;
use serde_json::{json, Value};
use std::{
    sync::{Arc, Mutex},
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::{Emitter, Manager};
use tauri_plugin_dialog::DialogExt;
struct State {
    backend: Backend,
    clipboard: Option<arboard::Clipboard>,
    lease: Option<ClipboardLease>,
    copy_generation: u64,
}
fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}
impl State {
    fn clear_clipboard(&mut self, force: bool) {
        if let (Some(l), Some(c)) = (&self.lease, &mut self.clipboard) {
            if let Ok(current) = c.get_text() {
                if (force && l.owns(&current))
                    || l.should_clear(&current, now(), self.copy_generation)
                {
                    let _ = c.clear();
                }
            }
        }
        if force || self.lease.as_ref().is_some_and(|l| l.expired(now())) {
            self.lease = None;
        }
    }
    fn lock(&mut self) {
        self.backend.lock();
        self.clear_clipboard(true);
    }
    fn copy(&mut self, text: String, secret: bool) -> Result<Value, Error> {
        if self.clipboard.is_none() {
            self.clipboard = Some(
                arboard::Clipboard::new().map_err(|_| Error::Invalid("clipboard unavailable"))?,
            );
        }
        self.clipboard
            .as_mut()
            .ok_or(Error::Storage)?
            .set_text(text.clone())
            .map_err(|_| Error::Invalid("clipboard unavailable"))?;
        self.copy_generation = self.copy_generation.wrapping_add(1);
        self.lease = if secret {
            Some(ClipboardLease::new(text, now(), self.copy_generation))
        } else {
            None
        };
        Ok(Value::Null)
    }
}
type Shared = Arc<Mutex<State>>;
#[tauri::command]
async fn execute(
    app: tauri::AppHandle,
    state: tauri::State<'_, Shared>,
    request: Request,
) -> Result<Value, String> {
    let shared = state.inner().clone();
    if matches!(&request, Request::Lock) {
        SECURITY_EPOCH.fetch_add(1, Ordering::SeqCst);
    }
    let security_epoch = SECURITY_EPOCH.load(Ordering::SeqCst);
    tauri::async_runtime::spawn_blocking(move || -> Result<Value, Error> {
        // File paths originate exclusively from native dialogs; IPC cannot supply filesystem paths.
        let result = match request {
            Request::Backup => {
                let _dialog = DialogGuard::enter();
                let bytes = {
                    let mut s = shared.lock().map_err(|_| Error::Storage)?;
                    s.backend.guard(now())?;
                    s.backend.vault.backup_snapshot()?
                };
                let Some(file) = app
                    .dialog()
                    .file()
                    .add_filter("Encrypted Red Moon vault", &["rm"])
                    .set_file_name("red-moon-backup.rm")
                    .blocking_save_file()
                else {
                    return Ok(json!({"cancelled":true}));
                };
                let path = file.into_path().map_err(|_| Error::Storage)?;
                storage::copy_new(&bytes, &path)?;
                Ok(json!({"saved":true}))
            }
            Request::Restore { password, current } => {
                let _dialog = DialogGuard::enter();
                let Some(file) = app
                    .dialog()
                    .file()
                    .add_filter("Encrypted Red Moon vault", &["rm"])
                    .blocking_pick_file()
                else {
                    return Ok(json!({"cancelled":true}));
                };
                let path = file.into_path().map_err(|_| Error::Storage)?;
                let mut s = shared.lock().map_err(|_| Error::Storage)?;
                s.backend
                    .vault
                    .restore(&path, &password, current.as_ref())?;
                s.backend.start(now())?;
                Ok(json!({"generation":s.backend.session.generation}))
            }
            Request::CopyPassword { id } => {
                let mut s = shared.lock().map_err(|_| Error::Storage)?;
                let text = s.backend.secret(&id, now(), true)?;
                s.copy(text.expose_secret().clone(), true)
            }
            Request::CopyUsername { id } => {
                let mut s = shared.lock().map_err(|_| Error::Storage)?;
                let text = s.backend.secret(&id, now(), false)?;
                s.copy(text.expose_secret().clone(), false)
            }
            Request::CopyGenerated { password } => {
                let mut s = shared.lock().map_err(|_| Error::Storage)?;
                s.backend.guard(now())?;
                if password.expose_secret().len() > 128 {
                    return Err(Error::Invalid("generated password size"));
                }
                s.copy(password.expose_secret().clone(), true)
            }
            Request::Lock => {
                let mut s = shared.lock().map_err(|_| Error::Storage)?;
                s.lock();
                let _ = app.emit("vault-locked", ());
                Ok(Value::Null)
            }
            other => {
                let mut s = shared.lock().map_err(|_| Error::Storage)?;
                let result = s.backend.execute(other, now());
                if matches!(result, Err(Error::Locked)) {
                    s.clear_clipboard(true);
                    let _ = app.emit("vault-locked", ());
                }
                result
            }
        };
        if security_epoch != SECURITY_EPOCH.load(Ordering::SeqCst) {
            if let Ok(mut s) = shared.lock() {
                s.lock();
            }
            return Err(Error::Locked);
        }
        result
    })
    .await
    .map_err(|_| "Operation failed safely.".to_string())?
    .map_err(|e| e.to_string())
}
fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let path = app.path().app_data_dir()?.join("vault.rm");
            let state = Arc::new(Mutex::new(State {
                backend: Backend::open(path)?,
                clipboard: None,
                lease: None,
                copy_generation: 0,
            }));
            app.manage(state.clone());
            let handle = app.handle().clone();
            let security_state = state.clone();
            let security_handle = handle.clone();
            platform_lock::install(move || {
                SECURITY_EPOCH.fetch_add(1, Ordering::SeqCst);
                if let Ok(mut s) = security_state.lock() {
                    s.lock();
                }
                let _ = security_handle.emit("vault-locked", ());
            });
            std::thread::spawn(move || {
                let mut previous = now();
                loop {
                    std::thread::sleep(std::time::Duration::from_secs(1));
                    if let Ok(mut s) = state.lock() {
                        let current = now();
                        if current < previous || current.saturating_sub(previous) > 5 {
                            SECURITY_EPOCH.fetch_add(1, Ordering::SeqCst);
                            s.lock();
                            let _ = handle.emit("vault-locked", ());
                        }
                        previous = current;
                        let before = s.backend.session.generation;
                        s.backend.check(now());
                        s.clear_clipboard(false);
                        if before != s.backend.session.generation {
                            let _ = handle.emit("vault-locked", ());
                        }
                    }
                }
            });
            Ok(())
        })
        .on_window_event(|window, event| {
            if matches!(event, tauri::WindowEvent::Focused(false))
                && !DIALOG_OPEN.load(Ordering::SeqCst)
            {
                SECURITY_EPOCH.fetch_add(1, Ordering::SeqCst);
                if let Some(state) = window.try_state::<Shared>() {
                    if let Ok(mut s) = state.lock() {
                        s.lock();
                    }
                }
                let _ = window.emit("vault-locked", ());
            }
        })
        .invoke_handler(tauri::generate_handler![execute])
        .run(tauri::generate_context!())
        .expect("Unable to initialize Red Moon");
}
