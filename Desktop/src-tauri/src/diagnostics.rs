use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_LOG_BYTES: u64 = 1024 * 1024;
static LOG_PATH: OnceLock<Mutex<PathBuf>> = OnceLock::new();

pub fn initialize() {
    let Some(local_data) = std::env::var_os("LOCALAPPDATA") else {
        return;
    };
    let directory = PathBuf::from(local_data).join("com.nandu.gloom");
    if fs::create_dir_all(&directory).is_err() {
        return;
    }
    let _ = LOG_PATH.set(Mutex::new(directory.join("diagnostics.log")));

    let previous_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let location = info
            .location()
            .map(|location| format!("{}:{}", location.file(), location.line()))
            .unwrap_or_else(|| "unknown".into());
        let message = info
            .payload()
            .downcast_ref::<&str>()
            .map(|value| (*value).to_string())
            .or_else(|| info.payload().downcast_ref::<String>().cloned())
            .unwrap_or_else(|| "unknown panic".into());
        record("panic", &format!("at={location} message={message}"));
        previous_hook(info);
    }));
    record("startup", concat!("version=", env!("CARGO_PKG_VERSION")));
}

pub fn record(event: &str, detail: &str) {
    let Some(path) = LOG_PATH.get() else {
        return;
    };
    // Threads log at the same moment during startup, so wait briefly for the
    // lock rather than dropping the line. The wait is bounded, so a panic
    // raised while this thread already holds the lock can't deadlock.
    let mut attempts = 0;
    let path = loop {
        match path.try_lock() {
            Ok(path) => break path,
            // A thread that panicked mid-write leaves the path itself intact.
            Err(std::sync::TryLockError::Poisoned(poisoned)) => break poisoned.into_inner(),
            Err(_) if attempts < 50 => {
                attempts += 1;
                std::thread::sleep(std::time::Duration::from_millis(2));
            }
            Err(_) => return,
        }
    };
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let detail: String = detail
        .chars()
        .take(500)
        .map(|ch| if ch == '\r' || ch == '\n' { ' ' } else { ch })
        .collect();
    let line = format!(
        "{}.{:03} pid={} {} {}\n",
        now.as_secs(),
        now.subsec_millis(),
        std::process::id(),
        event,
        detail
    );
    let _ = append_line(&path, &line);
}

fn append_line(path: &Path, line: &str) -> io::Result<()> {
    if fs::metadata(path)
        .map(|metadata| metadata.len() + line.len() as u64 > MAX_LOG_BYTES)
        .unwrap_or(false)
    {
        let previous = path.with_extension("log.old");
        let _ = fs::remove_file(&previous);
        fs::rename(path, previous)?;
    }
    let mut file = OpenOptions::new().create(true).append(true).open(path)?;
    file.write_all(line.as_bytes())
}

#[cfg(test)]
mod tests {
    use super::{append_line, MAX_LOG_BYTES};

    #[test]
    fn log_rotates_before_exceeding_limit() {
        let directory = std::env::temp_dir().join(format!(
            "gloom-log-test-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        std::fs::create_dir_all(&directory).unwrap();
        let path = directory.join("diagnostics.log");
        std::fs::write(&path, vec![b'x'; MAX_LOG_BYTES as usize]).unwrap();
        append_line(&path, "new event\n").unwrap();
        assert_eq!(std::fs::read(&path).unwrap(), b"new event\n");
        assert_eq!(
            std::fs::metadata(path.with_extension("log.old"))
                .unwrap()
                .len(),
            MAX_LOG_BYTES
        );
        std::fs::remove_file(&path).unwrap();
        std::fs::remove_file(path.with_extension("log.old")).unwrap();
        std::fs::remove_dir(directory).unwrap();
    }
}
