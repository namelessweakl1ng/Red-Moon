use crate::{crypto::MAX_FILE, Error, Result};
use fs2::FileExt;
use std::{
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    path::{Path, PathBuf},
};
pub struct Storage {
    path: PathBuf,
    _lock: File,
}
fn reject_symlink(path: &Path) -> Result<()> {
    for p in path.ancestors() {
        if let Ok(m) = fs::symlink_metadata(p) {
            if m.file_type().is_symlink() {
                return Err(Error::Storage);
            }
        }
    }
    Ok(())
}
pub fn private_new(path: &Path) -> Result<File> {
    reject_symlink(path)?;
    let mut opts = OpenOptions::new();
    opts.write(true).read(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        opts.mode(0o600);
    }
    Ok(opts.open(path)?)
}
pub fn read_bounded(path: &Path) -> Result<Vec<u8>> {
    reject_symlink(path)?;
    let f = File::open(path)?;
    if f.metadata()?.len() > MAX_FILE as u64 {
        return Err(Error::Format);
    }
    let mut bytes = Vec::new();
    f.take(MAX_FILE as u64 + 1).read_to_end(&mut bytes)?;
    if bytes.len() > MAX_FILE {
        return Err(Error::Format);
    }
    Ok(bytes)
}
#[cfg(not(windows))]
fn replace(from: &Path, to: &Path) -> Result<()> {
    fs::rename(from, to)?;
    Ok(())
}
#[cfg(windows)]
fn replace(from: &Path, to: &Path) -> Result<()> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::{
        MoveFileExW, MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH,
    };
    let a: Vec<u16> = from.as_os_str().encode_wide().chain(Some(0)).collect();
    let b: Vec<u16> = to.as_os_str().encode_wide().chain(Some(0)).collect();
    // Both UTF-16 buffers remain alive and nul-terminated for the system call.
    if unsafe {
        MoveFileExW(
            a.as_ptr(),
            b.as_ptr(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
        )
    } == 0
    {
        return Err(Error::Storage);
    }
    Ok(())
}
impl Storage {
    pub fn open(path: PathBuf) -> Result<Self> {
        reject_symlink(&path)?;
        let parent = path.parent().ok_or(Error::Storage)?;
        if !parent.exists() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::DirBuilderExt;
                let mut b = fs::DirBuilder::new();
                b.recursive(true).mode(0o700);
                b.create(parent)?;
            }
            #[cfg(not(unix))]
            fs::create_dir_all(parent)?;
        }
        let lock_path = path.with_extension("lock");
        reject_symlink(&lock_path)?;
        let mut opts = OpenOptions::new();
        opts.create(true).truncate(false).read(true).write(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            opts.mode(0o600);
        }
        let lock = opts.open(lock_path)?;
        lock.try_lock_exclusive().map_err(|_| Error::Busy)?;
        Ok(Self { path, _lock: lock })
    }
    pub fn exists(&self) -> bool {
        self.path.exists()
    }
    pub fn path(&self) -> &Path {
        &self.path
    }
    pub fn read(&self) -> Result<Vec<u8>> {
        read_bounded(&self.path)
    }
    pub fn commit(&self, bytes: &[u8]) -> Result<()> {
        self.commit_with(bytes, |_| Ok(()))
    }
    pub fn commit_with(
        &self,
        bytes: &[u8],
        before_replace: impl FnOnce(&File) -> Result<()>,
    ) -> Result<()> {
        if bytes.len() > MAX_FILE {
            return Err(Error::Format);
        }
        reject_symlink(&self.path)?;
        let temp = self
            .path
            .with_file_name(format!(".red-moon-{}.tmp", uuid::Uuid::new_v4()));
        let result = (|| {
            let mut f = private_new(&temp)?;
            f.write_all(bytes)?;
            f.sync_all()?;
            before_replace(&f)?;
            drop(f);
            replace(&temp, &self.path)?;
            #[cfg(unix)]
            File::open(self.path.parent().ok_or(Error::Storage)?)?
                .sync_all()
                .map_err(|_| Error::Uncertain)?;
            Ok(())
        })();
        let _ = fs::remove_file(temp);
        result
    }
    pub fn safety_copy(&self) -> Result<()> {
        let dst = self.path.with_file_name(format!(
            "red-moon-before-restore-{}.rm",
            uuid::Uuid::new_v4()
        ));
        copy_new(&self.read()?, &dst)
    }
}
pub fn copy_new(bytes: &[u8], destination: &Path) -> Result<()> {
    let mut f = private_new(destination)?;
    if let Err(e) = f.write_all(bytes).and_then(|_| f.sync_all()) {
        drop(f);
        let _ = fs::remove_file(destination);
        return Err(e.into());
    }
    Ok(())
}
