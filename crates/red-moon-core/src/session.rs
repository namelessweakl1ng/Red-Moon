use secrecy::{ExposeSecret, SecretString};
#[derive(Default)]
pub struct Session {
    pub generation: u64,
    last: Option<u64>,
    timeout: u64,
}
impl Session {
    pub fn start(&mut self, now: u64, timeout: u64) {
        self.generation = self.generation.wrapping_add(1);
        self.last = Some(now);
        self.timeout = timeout;
    }
    pub fn active(&mut self, now: u64) -> bool {
        if let Some(last) = self.last {
            if now < last || now - last >= self.timeout {
                self.lock();
            }
        }
        self.last.is_some()
    }
    pub fn touch(&mut self, now: u64) {
        if self.active(now) {
            self.last = Some(now);
        }
    }
    pub fn lock(&mut self) {
        self.last = None;
        self.generation = self.generation.wrapping_add(1);
    }
}
pub struct ClipboardLease {
    secret: SecretString,
    expires: u64,
    generation: u64,
}
impl ClipboardLease {
    pub fn new(secret: String, now: u64, generation: u64) -> Self {
        Self {
            secret: SecretString::new(secret),
            expires: now.saturating_add(20),
            generation,
        }
    }
    pub fn should_clear(&self, current: &str, now: u64, generation: u64) -> bool {
        now >= self.expires
            && generation == self.generation
            && current == self.secret.expose_secret()
    }
    pub fn owns(&self, current: &str) -> bool {
        current == self.secret.expose_secret()
    }
    pub fn expired(&self, now: u64) -> bool {
        now >= self.expires
    }
}
