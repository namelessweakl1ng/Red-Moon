#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Incorrect password or damaged vault.")]
    Authentication,
    #[error("Unsupported or invalid vault format.")]
    Format,
    #[error("Invalid input: {0}")]
    Invalid(&'static str),
    #[error("Vault is locked.")]
    Locked,
    #[error("Another instance is using this vault.")]
    Busy,
    #[error("Storage operation failed; existing data has been preserved where possible.")]
    Storage,
    #[error("Save committed but durability is uncertain. Lock and reopen before continuing.")]
    Uncertain,
    #[error("System randomness unavailable.")]
    Random,
}
pub type Result<T> = std::result::Result<T, Error>;
impl From<std::io::Error> for Error {
    fn from(_: std::io::Error) -> Self {
        Self::Storage
    }
}
impl From<serde_json::Error> for Error {
    fn from(_: serde_json::Error) -> Self {
        Self::Format
    }
}
