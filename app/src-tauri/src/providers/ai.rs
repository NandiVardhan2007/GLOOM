use serde::{Deserialize, Serialize};
use std::path::PathBuf;

const KEYS_DIR_ENV: &str = "JARVIS_KEYS_DIR";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMessage {
    pub role: String,
    pub content: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AiProvider {
    pub id: String,
    pub base_url: String,
    pub key_file: String,
    #[serde(default)]
    pub model: String,
    #[serde(default = "default_true")]
    pub enabled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AiConfig {
    #[serde(default = "default_keys_dir")]
    pub keys_dir: String,
    #[serde(default = "default_provider")]
    pub default_provider: String,
    #[serde(default = "default_providers")]
    pub providers: Vec<AiProvider>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AiReply {
    pub content: String,
    pub provider: String,
    pub model: String,
}

fn default_true() -> bool {
    true
}

fn default_provider() -> String {
    "groq".to_string()
}

fn default_keys_dir() -> String {
    r"C:\Users\nandu\OneDrive\Desktop\Folders\Documents\KEYS".to_string()
}

fn default_providers() -> Vec<AiProvider> {
    vec![
        AiProvider {
            id: "groq".into(),
            base_url: "https://api.groq.com/openai/v1".into(),
            key_file: "GROQKeys.txt".into(),
            model: "openai/gpt-oss-120b".into(),
            enabled: true,
        },
        AiProvider {
            id: "openrouter".into(),
            base_url: "https://openrouter.ai/api/v1".into(),
            key_file: "OpenRouterKeys.txt".into(),
            model: "anthropic/claude-haiku-5.5".into(),
            enabled: true,
        },
        AiProvider {
            id: "zen".into(),
            base_url: "https://opencode.ai/zen/v1".into(),
            key_file: "ZEN_Open_Code.txt".into(),
            model: String::new(),
            enabled: false,
        },
        AiProvider {
            id: "aero".into(),
            base_url: "https://capi.aerolink.lat/v1".into(),
            key_file: "aerokeys.txt".into(),
            model: String::new(),
            enabled: false,
        },
    ]
}

fn app_dir() -> PathBuf {
    crate::config::dirs_fallback().join("JARVIS")
}

fn config_path() -> PathBuf {
    app_dir().join("ai_providers.json")
}

pub fn load_config() -> AiConfig {
    let path = config_path();
    if let Ok(raw) = std::fs::read_to_string(&path) {
        if let Ok(config) = serde_json::from_str::<AiConfig>(&raw) {
            return config;
        }
        log::warn!("ai_providers.json is invalid; using defaults");
    }
    let config = AiConfig {
        keys_dir: default_keys_dir(),
        default_provider: default_provider(),
        providers: default_providers(),
    };
    if let Ok(serialized) = serde_json::to_string_pretty(&config) {
        let _ = std::fs::create_dir_all(app_dir());
        let _ = std::fs::write(&path, serialized);
    }
    config
}

fn keys_dir(config: &AiConfig) -> PathBuf {
    std::env::var(KEYS_DIR_ENV)
        .ok()
        .filter(|value| !value.trim().is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(&config.keys_dir))
}

fn is_key_like(line: &str) -> bool {
    let value = line.trim();
    (value.starts_with("gsk_")
        || value.starts_with("sk-or-")
        || value.starts_with("sk-")
        || value.starts_with("aero_"))
        && value.len() >= 20
        && !value.contains(char::is_whitespace)
}

fn read_keys(path: &std::path::Path) -> Vec<String> {
    let Ok(raw) = std::fs::read_to_string(path) else {
        return Vec::new();
    };
    let mut keys: Vec<String> = raw
        .lines()
        .map(str::trim)
        .filter(|line| is_key_like(line))
        .map(str::to_string)
        .collect();
    keys.dedup();
    keys
}

fn curl_json(
    url: &str,
    method: &str,
    headers: &[(&str, String)],
    body: Option<&str>,
    timeout_secs: u64,
) -> Result<(u16, String), String> {
    let mut command = std::process::Command::new("curl.exe");
    command.args([
        "--proto",
        "=https",
        "--proto-redir",
        "=https",
        "--max-redirs",
        "3",
        "--tlsv1.2",
        "--max-filesize",
        "4194304",
        "--max-time",
        &timeout_secs.to_string(),
        "-sS",
        "-X",
        method,
        "-w",
        "\n%{http_code}",
    ]);
    for (name, value) in headers {
        command.arg("-H").arg(format!("{}: {}", name, value));
    }
    if let Some(body) = body {
        command.arg("--data-binary").arg(body);
    }
    command.arg(url);
    command.stdin(std::process::Stdio::null());

    let output = command
        .output()
        .map_err(|e| format!("Cannot run HTTP client: {}", e))?;
    let raw = String::from_utf8_lossy(&output.stdout).to_string();
    let (payload, code) = raw.rsplit_once('\n').unwrap_or(("", "0"));
    let status = code.trim().parse::<u16>().unwrap_or(0);
    Ok((status, payload.to_string()))
}

fn preferred_models(provider_id: &str) -> &'static [&'static str] {
    match provider_id {
        "groq" => &["openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b"],
        "openrouter" => &[
            "anthropic/claude-haiku-5.5",
            "anthropic/claude-sonnet-5.5",
            "google/gemini-4-argon",
        ],
        _ => &[],
    }
}

fn resolve_model(provider: &AiProvider, key: &str, requested: Option<&str>) -> Option<String> {
    if let Some(requested) = requested.map(str::trim).filter(|m| !m.is_empty()) {
        return Some(requested.to_string());
    }
    if !provider.model.trim().is_empty() {
        return Some(provider.model.trim().to_string());
    }
    let url = format!("{}/models", provider.base_url.trim_end_matches('/'));
    let headers = [("Authorization", format!("Bearer {}", key))];
    if let Ok((status, body)) = curl_json(&url, "GET", &headers, None, 15) {
        if (200..300).contains(&status) {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body) {
                let models = json.get("data").and_then(|d| d.as_array());
                if let Some(models) = models {
                    let mut ids: Vec<String> = models
                        .iter()
                        .filter_map(|m| m.get("id").and_then(|id| id.as_str()))
                        .map(str::to_string)
                        .collect();
                    for preferred in preferred_models(&provider.id) {
                        if ids.iter().any(|id| id == preferred) {
                            return Some((*preferred).to_string());
                        }
                    }
                    ids.sort();
                    if let Some(first) = ids.into_iter().next() {
                        return Some(first);
                    }
                }
            }
        }
    }
    preferred_models(&provider.id).first().map(|m| (*m).to_string())
}

fn extract_reply(body: &str) -> Option<String> {
    let json = serde_json::from_str::<serde_json::Value>(body).ok()?;
    let choice = json.get("choices")?.as_array()?.first()?;
    if let Some(content) = choice
        .get("message")
        .and_then(|m| m.get("content"))
        .and_then(|c| c.as_str())
    {
        if !content.trim().is_empty() {
            return Some(content.trim().to_string());
        }
    }
    choice
        .get("text")
        .and_then(|t| t.as_str())
        .map(|t| t.trim().to_string())
        .filter(|t| !t.is_empty())
}

fn try_provider(
    provider: &AiProvider,
    keys_dir_path: &std::path::Path,
    messages: &[ChatMessage],
    requested_model: Option<&str>,
) -> Result<AiReply, String> {
    let keys = read_keys(&keys_dir_path.join(&provider.key_file));
    if keys.is_empty() {
        return Err(format!("no keys found in {}", provider.key_file));
    }

    let mut last_error = String::from("unknown error");
    // Key files hold several keys and the first ones are not always the live
    // ones. OpenRouter's working keys, for example, sit after four revoked
    // entries; stopping at three made the whole provider look dead.
    for key in keys.iter().take(10) {
        let Some(model) = resolve_model(provider, key, requested_model) else {
            return Err(format!("{} has no usable model", provider.id));
        };
        let url = format!(
            "{}/chat/completions",
            provider.base_url.trim_end_matches('/')
        );
        let payload = serde_json::json!({
            "model": model,
            "messages": messages,
            "temperature": 0.4,
            "max_tokens": 1200
        })
        .to_string();
        let headers = [
            ("Authorization", format!("Bearer {}", key)),
            ("Content-Type", "application/json".to_string()),
            ("HTTP-Referer", "https://github.com/NandiVardhan2007/JARVIS".to_string()),
            ("X-Title", "JARVIS".to_string()),
        ];
        match curl_json(&url, "POST", &headers, Some(&payload), 45) {
            Ok((status, body)) if (200..300).contains(&status) => {
                if let Some(content) = extract_reply(&body) {
                    return Ok(AiReply {
                        content,
                        provider: provider.id.clone(),
                        model,
                    });
                }
                last_error = "provider returned an empty reply".into();
            }
            Ok((status, _)) => {
                last_error = format!("{} returned HTTP {}", provider.id, status);
                if status == 400 {
                    break;
                }
            }
            Err(error) => last_error = error,
        }
    }
    Err(last_error)
}

pub fn chat(
    messages: Vec<ChatMessage>,
    provider: Option<String>,
    model: Option<String>,
) -> Result<AiReply, String> {
    if messages.is_empty() {
        return Err("No messages supplied".into());
    }
    let config = load_config();
    let dir = keys_dir(&config);
    let requested = provider.map(|p| p.trim().to_lowercase()).filter(|p| !p.is_empty());

    let mut order: Vec<&AiProvider> = Vec::new();
    if let Some(requested) = requested.as_deref() {
        if let Some(found) = config.providers.iter().find(|p| p.id == requested && p.enabled) {
            order.push(found);
        }
    }
    if let Some(default) = config
        .providers
        .iter()
        .find(|p| p.id == config.default_provider && p.enabled)
    {
        if !order.iter().any(|p| p.id == default.id) {
            order.push(default);
        }
    }
    for candidate in config.providers.iter().filter(|p| p.enabled) {
        if !order.iter().any(|p| p.id == candidate.id) {
            order.push(candidate);
        }
    }
    if order.is_empty() {
        return Err(format!(
            "No AI provider is enabled. Edit {}",
            config_path().display()
        ));
    }

    let mut errors = Vec::new();
    for candidate in order {
        match try_provider(candidate, &dir, &messages, model.as_deref()) {
            Ok(reply) => return Ok(reply),
            Err(error) => errors.push(format!("{}: {}", candidate.id, error)),
        }
    }
    Err(format!(
        "All AI providers failed ({}). Check keys in {}",
        errors.join("; "),
        dir.display()
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognises_key_lines_and_skips_labels() {
        assert!(is_key_like("gsk_abcdefghijklmnopqrstuvwx"));
        assert!(is_key_like("sk-or-v1-abcdefghijklmnopqrstuvwx"));
        assert!(is_key_like("aero_live_abcdefghijklmnopqrstuvwx"));
        assert!(!is_key_like("GROQ API KEYS"));
        assert!(!is_key_like("sk-short"));
        assert!(!is_key_like("prefix gsk_abcdefghijklmnopqrstuvwx"));
    }

    #[test]
    fn extracts_openai_chat_reply() {
        let body = r#"{"choices":[{"message":{"role":"assistant","content":" Hello there "}}]}"#;
        assert_eq!(extract_reply(body).as_deref(), Some("Hello there"));
        let text_body = r#"{"choices":[{"text":"Legacy reply"}]}"#;
        assert_eq!(extract_reply(text_body).as_deref(), Some("Legacy reply"));
        assert_eq!(extract_reply("{}"), None);
    }

    #[test]
    fn default_config_has_openai_compatible_providers() {
        let providers = default_providers();
        assert!(providers.iter().any(|p| p.id == "groq" && p.enabled));
        assert!(providers.iter().any(|p| p.id == "openrouter" && p.enabled));
        assert!(providers.iter().all(|p| p.base_url.starts_with("https://")));
    }
}
