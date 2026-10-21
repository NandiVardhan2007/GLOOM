use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LyricLine {
    pub time_ms: u64,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LyricsPayload {
    pub lines: Vec<LyricLine>,
    pub synced: bool,
}

impl LyricsPayload {
    pub fn empty() -> Self {
        Self {
            lines: Vec::new(),
            synced: false,
        }
    }
}

fn urlencode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.as_bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(*byte as char)
            }
            _ => out.push_str(&format!("%{:02X}", byte)),
        }
    }
    out
}

fn clean_title(raw: &str) -> String {
    let mut title = raw.to_string();
    for (open, close) in [('(', ')'), ('[', ']')] {
        while let Some(start) = title.find(open) {
            if let Some(end) = title[start..].find(close) {
                title.replace_range(start..start + end + 1, " ");
            } else {
                break;
            }
        }
    }
    let lowered = title.to_ascii_lowercase();
    if let Some(idx) = lowered.find(" feat.") {
        title.truncate(idx);
    }
    title
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .trim()
        .trim_end_matches('-')
        .trim()
        .to_string()
}

fn clean_artist(raw: &str) -> String {
    raw.split([',', '&', ';'])
        .next()
        .unwrap_or(raw)
        .split(" feat.")
        .next()
        .unwrap_or(raw)
        .trim()
        .to_string()
}

fn curl_json(url: &str) -> Option<String> {
    let output = std::process::Command::new("curl.exe")
        .args([
            "--proto",
            "=https",
            "--proto-redir",
            "=https",
            "--max-redirs",
            "3",
            "--tlsv1.2",
            "--max-filesize",
            "2000000",
            "--max-time",
            "8",
            "--retry",
            "1",
            "--retry-delay",
            "1",
            "-sSfL",
            "-A",
            "JARVIS-Dynamic-Island/1.0 (https://github.com/NandiVardhan2007/JARVIS)",
            url,
        ])
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    String::from_utf8(output.stdout).ok()
}

fn parse_lrc(text: &str) -> Vec<LyricLine> {
    let mut lines = Vec::new();
    for raw in text.lines() {
        let mut rest = raw.trim();
        let mut stamps = Vec::new();
        while rest.starts_with('[') {
            let Some(end) = rest.find(']') else { break };
            let stamp = &rest[1..end];
            if let Some(time) = parse_timestamp(stamp) {
                stamps.push(time);
            }
            rest = rest[end + 1..].trim_start();
        }
        let content = rest.trim();
        if content.is_empty() {
            continue;
        }
        for time in stamps {
            lines.push(LyricLine {
                time_ms: time,
                text: content.to_string(),
            });
        }
    }
    lines.sort_by_key(|line| line.time_ms);
    lines
}

fn parse_timestamp(stamp: &str) -> Option<u64> {
    let mut parts = stamp.split(':');
    let minutes: u64 = parts.next()?.trim().parse().ok()?;
    let seconds_part = parts.next()?.trim();
    if parts.next().is_some() {
        return None;
    }
    let (seconds, fraction) = match seconds_part.split_once('.') {
        Some((s, f)) => (s, Some(f)),
        None => (seconds_part, None),
    };
    let seconds: u64 = seconds.parse().ok()?;
    let millis = match fraction {
        Some(f) => {
            let digits: String = f.chars().take(3).collect();
            let padded = format!("{:0<3}", digits);
            padded.parse::<u64>().ok()?
        }
        None => 0,
    };
    Some((minutes * 60 + seconds) * 1000 + millis)
}

fn parse_plain(text: &str) -> Vec<LyricLine> {
    text.lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .map(|line| LyricLine {
            time_ms: 0,
            text: line.to_string(),
        })
        .collect()
}

fn payload_from_json(body: &str) -> LyricsPayload {
    let Ok(value) = serde_json::from_str::<serde_json::Value>(body) else {
        return LyricsPayload::empty();
    };
    let synced = value
        .get("syncedLyrics")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    if !synced.trim().is_empty() {
        let lines = parse_lrc(synced);
        if !lines.is_empty() {
            return LyricsPayload { lines, synced: true };
        }
    }
    let plain = value
        .get("plainLyrics")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    if !plain.trim().is_empty() {
        let lines = parse_plain(plain);
        if !lines.is_empty() {
            return LyricsPayload {
                lines,
                synced: false,
            };
        }
    }
    LyricsPayload::empty()
}

fn direct_get(title: &str, artist: &str) -> LyricsPayload {
    let url = format!(
        "https://lrclib.net/api/get?track_name={}&artist_name={}",
        urlencode(title),
        urlencode(artist)
    );
    curl_json(&url)
        .map(|body| payload_from_json(&body))
        .unwrap_or_else(LyricsPayload::empty)
}

fn search(title: &str, artist: &str) -> LyricsPayload {
    let query = if artist.is_empty() {
        title.to_string()
    } else {
        format!("{} {}", title, artist)
    };
    let url = format!("https://lrclib.net/api/search?q={}", urlencode(&query));
    let Some(body) = curl_json(&url) else {
        return LyricsPayload::empty();
    };
    let Ok(items) = serde_json::from_str::<serde_json::Value>(&body) else {
        return LyricsPayload::empty();
    };
    let Some(array) = items.as_array() else {
        return LyricsPayload::empty();
    };

    for item in array.iter().take(5) {
        if let Some(synced) = item.get("syncedLyrics").and_then(|v| v.as_str()) {
            let lines = parse_lrc(synced);
            if !lines.is_empty() {
                return LyricsPayload { lines, synced: true };
            }
        }
    }
    for item in array.iter().take(3) {
        if let Some(plain) = item.get("plainLyrics").and_then(|v| v.as_str()) {
            let lines = parse_plain(plain);
            if !lines.is_empty() {
                return LyricsPayload {
                    lines,
                    synced: false,
                };
            }
        }
    }
    LyricsPayload::empty()
}

pub fn fetch_lyrics(title: &str, artist: &str, _duration_ms: u64) -> LyricsPayload {
    let raw_title = title.trim();
    if raw_title.is_empty() {
        return LyricsPayload::empty();
    }
    let title = clean_title(raw_title);
    let artist = clean_artist(artist);

    let mut result = direct_get(&title, &artist);
    if !result.lines.is_empty() {
        return result;
    }

    if let Some(primary) = artist.split_whitespace().next() {
        if primary.len() >= 3 && primary != artist {
            result = direct_get(&title, primary);
            if !result.lines.is_empty() {
                return result;
            }
        }
    }

    result = search(&title, &artist);
    if !result.lines.is_empty() {
        return result;
    }
    result = search(&title, "");
    if !result.lines.is_empty() {
        return result;
    }
    if title != raw_title {
        result = search(raw_title, "");
        if !result.lines.is_empty() {
            return result;
        }
    }
    LyricsPayload::empty()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_synced_lrc_with_multiple_stamps() {
        let lines = parse_lrc("[00:12.34]First\n[00:15.500][01:02.10]Second\n");
        assert_eq!(lines.len(), 3);
        assert_eq!(lines[0].time_ms, 12_340);
        assert_eq!(lines[2].time_ms, 62_100);
        assert_eq!(lines[2].text, "Second");
    }

    #[test]
    fn cleans_title_and_artist() {
        assert_eq!(clean_title("Song (From \"Movie\") feat. Someone"), "Song");
        assert_eq!(clean_artist("A, B & C"), "A");
    }

    #[test]
    fn builds_payload_from_plain_lyrics() {
        let payload = payload_from_json(r#"{"plainLyrics":"one\ntwo"}"#);
        assert!(!payload.synced);
        assert_eq!(payload.lines.len(), 2);
    }
}
