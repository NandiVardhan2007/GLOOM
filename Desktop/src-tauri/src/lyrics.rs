use std::sync::OnceLock;

fn url_encode(input: &str) -> String {
    let mut encoded = String::with_capacity(input.len() * 3);
    for b in input.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(b as char);
            }
            b' ' => encoded.push_str("%20"),
            _ => {
                use std::fmt::Write;
                let _ = write!(encoded, "%{:02X}", b);
            }
        }
    }
    encoded
}

fn get_lyrics_client() -> Option<&'static reqwest::Client> {
    static LYRICS_CLIENT: OnceLock<Option<reqwest::Client>> = OnceLock::new();
    LYRICS_CLIENT
        .get_or_init(|| {
            reqwest::Client::builder()
                .user_agent("GLOOM-Desktop/2.5.0 (https://github.com/nandub/gloom)")
                .timeout(std::time::Duration::from_secs(4))
                .connect_timeout(std::time::Duration::from_secs(3))
                .tcp_keepalive(Some(std::time::Duration::from_secs(60)))
                .build()
                .ok()
        })
        .as_ref()
}

fn parse_lrc_content(lrc_text: &str) -> Vec<crate::types::LyricLineDto> {
    let mut lines = Vec::new();
    for raw_line in lrc_text.lines() {
        let trimmed = raw_line.trim();
        if trimmed.is_empty() {
            continue;
        }
        if let Some(rest) = trimmed.strip_prefix('[') {
            if let Some(bracket_idx) = rest.find(']') {
                let time_str = &rest[..bracket_idx];
                let text = rest[bracket_idx + 1..].trim();
                if let Some((min_str, sec_str)) = time_str.split_once(':') {
                    if let (Ok(minutes), Ok(seconds)) = (min_str.parse::<i64>(), sec_str.parse::<f64>()) {
                        let time_ms = (minutes * 60_000) + (seconds * 1000.0).round() as i64;
                        if !text.is_empty() {
                            lines.push(crate::types::LyricLineDto {
                                time_ms,
                                text: text.to_string(),
                            });
                        }
                    }
                }
            }
        }
    }
    lines.sort_by_key(|l| l.time_ms);
    lines
}

fn parse_plain_content(plain_text: &str) -> Vec<crate::types::LyricLineDto> {
    plain_text
        .lines()
        .map(|l| l.trim())
        .filter(|l| !l.is_empty())
        .enumerate()
        .map(|(idx, line)| crate::types::LyricLineDto {
            time_ms: (idx as i64) * 4000,
            text: line.to_string(),
        })
        .collect()
}

#[derive(serde::Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct LrcLibItem {
    id: Option<i64>,
    track_name: Option<String>,
    artist_name: Option<String>,
    synced_lyrics: Option<String>,
    plain_lyrics: Option<String>,
    duration: Option<f64>,
}

#[tauri::command]
pub async fn fetch_lyrics(
    title: String,
    artist: String,
    duration_ms: Option<i64>,
) -> Result<crate::types::LyricsResult, String> {
    let Some(client) = get_lyrics_client() else {
        return Err("Lyrics HTTP client unavailable".into());
    };

    // Clean title and artist
    let mut clean_title = title.trim().to_string();
    let mut clean_artist = artist.trim().to_string();

    // Remove common bracketed/parenthetical video and audio markers
    let noise_patterns = [
        "(official video)", "[official video]", "(official audio)", "[official audio]",
        "(official music video)", "[official music video]", "(music video)", "[music video]",
        "(lyrics)", "[lyrics]", "(lyric video)", "[lyric video]", "(audio)", "[audio]",
        "(visualizer)", "[visualizer]", "(full song)", "[full song]", "(remastered)", "[remastered]",
        "(video)", "[video]", "(hd)", "[hd]", "(4k)", "[4k]"
    ];
    let lower_title = clean_title.to_lowercase();
    for noise in noise_patterns {
        if lower_title.contains(noise) {
            clean_title = clean_title
                .replace(noise, "")
                .replace(&noise.to_uppercase(), "")
                .trim()
                .to_string();
        }
    }

    // Strip "- Topic" suffix from artist (YouTube autogen channels)
    if clean_artist.ends_with("- Topic") {
        clean_artist = clean_artist.trim_end_matches("- Topic").trim().to_string();
    }

    // Check if artist is empty or a browser/platform placeholder
    let is_generic_artist = clean_artist.is_empty()
        || clean_artist.eq_ignore_ascii_case("youtube")
        || clean_artist.eq_ignore_ascii_case("youtube music")
        || clean_artist.eq_ignore_ascii_case("google chrome")
        || clean_artist.eq_ignore_ascii_case("chrome")
        || clean_artist.eq_ignore_ascii_case("brave")
        || clean_artist.eq_ignore_ascii_case("firefox")
        || clean_artist.eq_ignore_ascii_case("edge")
        || clean_artist.eq_ignore_ascii_case("spotify free")
        || clean_artist.eq_ignore_ascii_case("unknown artist");

    // If artist is generic, try splitting "Artist - Title" from clean_title
    if is_generic_artist {
        for sep in [" - ", " – ", " — ", " : "] {
            if let Some((detected_artist, detected_track)) = clean_title.split_once(sep) {
                if !detected_artist.trim().is_empty() && !detected_track.trim().is_empty() {
                    clean_artist = detected_artist.trim().to_string();
                    clean_title = detected_track.trim().to_string();
                    break;
                }
            }
        }
    }

    // Take primary artist if multiple are comma-separated or joined by &
    let primary_artist = clean_artist
        .split(|c| c == ',' || c == '&' || c == '/')
        .next()
        .unwrap_or(&clean_artist)
        .trim();

    // 1. Try exact get first if primary artist is known
    if !primary_artist.is_empty() && !primary_artist.eq_ignore_ascii_case("unknown artist") {
        let exact_url = format!(
            "https://lrclib.net/api/get?track_name={}&artist_name={}",
            url_encode(&clean_title),
            url_encode(primary_artist)
        );

        if let Ok(resp) = client.get(&exact_url).send().await {
            if resp.status().is_success() {
                if let Ok(item) = resp.json::<LrcLibItem>().await {
                    if let Some(ref synced) = item.synced_lyrics {
                        if !synced.trim().is_empty() {
                            let lines = parse_lrc_content(synced);
                            return Ok(crate::types::LyricsResult {
                                id: item.id,
                                track_name: item.track_name.or(Some(clean_title)),
                                artist_name: item.artist_name.or(Some(clean_artist)),
                                synced_lyrics: item.synced_lyrics,
                                plain_lyrics: item.plain_lyrics,
                                lines,
                                is_synced: true,
                            });
                        }
                    } else if let Some(ref plain) = item.plain_lyrics {
                        if !plain.trim().is_empty() {
                            let lines = parse_plain_content(plain);
                            return Ok(crate::types::LyricsResult {
                                id: item.id,
                                track_name: item.track_name.or(Some(clean_title)),
                                artist_name: item.artist_name.or(Some(clean_artist)),
                                synced_lyrics: None,
                                plain_lyrics: item.plain_lyrics,
                                lines,
                                is_synced: false,
                            });
                        }
                    }
                }
            }
        }
    }

    // 2. Fallback to /api/search with multiple fallback query variants
    let search_queries = [
        format!(
            "https://lrclib.net/api/search?track_name={}&artist_name={}",
            url_encode(&clean_title),
            url_encode(primary_artist)
        ),
        format!(
            "https://lrclib.net/api/search?q={}",
            url_encode(&format!("{} {}", clean_title, primary_artist).trim())
        ),
        format!(
            "https://lrclib.net/api/search?track_name={}",
            url_encode(&clean_title)
        ),
    ];

    for search_url in search_queries {
        if let Ok(resp) = client.get(&search_url).send().await {
            if resp.status().is_success() {
                if let Ok(items) = resp.json::<Vec<LrcLibItem>>().await {
                    if !items.is_empty() {
                        // Pick candidate with synced lyrics closest to duration, else first with synced, else first with plain
                        let best = if let Some(target_dur) = duration_ms {
                            let target_secs = (target_dur as f64) / 1000.0;
                            items
                                .iter()
                                .filter(|i| i.synced_lyrics.as_ref().map(|s| !s.trim().is_empty()).unwrap_or(false))
                                .min_by(|a, b| {
                                    let da = (a.duration.unwrap_or(0.0) - target_secs).abs();
                                    let db = (b.duration.unwrap_or(0.0) - target_secs).abs();
                                    da.partial_cmp(&db).unwrap_or(std::cmp::Ordering::Equal)
                                })
                                .or_else(|| items.iter().find(|i| i.synced_lyrics.as_ref().map(|s| !s.trim().is_empty()).unwrap_or(false)))
                                .or_else(|| items.first())
                        } else {
                            items
                                .iter()
                                .find(|i| i.synced_lyrics.as_ref().map(|s| !s.trim().is_empty()).unwrap_or(false))
                                .or_else(|| items.first())
                        };

                        if let Some(item) = best {
                            if let Some(ref synced) = item.synced_lyrics {
                                if !synced.trim().is_empty() {
                                    let lines = parse_lrc_content(synced);
                                    return Ok(crate::types::LyricsResult {
                                        id: item.id,
                                        track_name: item.track_name.clone().or(Some(clean_title)),
                                        artist_name: item.artist_name.clone().or(Some(clean_artist)),
                                        synced_lyrics: item.synced_lyrics.clone(),
                                        plain_lyrics: item.plain_lyrics.clone(),
                                        lines,
                                        is_synced: true,
                                    });
                                }
                            }
                            if let Some(ref plain) = item.plain_lyrics {
                                if !plain.trim().is_empty() {
                                    let lines = parse_plain_content(plain);
                                    return Ok(crate::types::LyricsResult {
                                        id: item.id,
                                        track_name: item.track_name.clone().or(Some(clean_title)),
                                        artist_name: item.artist_name.clone().or(Some(clean_artist)),
                                        synced_lyrics: None,
                                        plain_lyrics: item.plain_lyrics.clone(),
                                        lines,
                                        is_synced: false,
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Err("No lyrics found for track".into())
}

#[cfg(test)]
mod lyrics_tests {
    use super::*;

    #[test]
    fn test_url_encode_unreserved_and_reserved() {
        let clean = "hello-world_test.123~";
        assert_eq!(url_encode(clean), clean);

        let special = "Artist & Band / Track #1?";
        assert_eq!(url_encode(special), "Artist%20%26%20Band%20%2F%20Track%20%231%3F");
    }

    #[test]
    fn test_parse_lrc_content() {
        let lrc = r#"
[ar: Test Artist]
[ti: Test Track]
[00:05.20] First lyric line
[00:10.50] Second lyric line
[01:02.10] Third lyric line after a minute
"#;
        let lines = parse_lrc_content(lrc);
        assert_eq!(lines.len(), 3);
        assert_eq!(lines[0].text, "First lyric line");
        assert_eq!(lines[0].time_ms, 5200);

        assert_eq!(lines[1].text, "Second lyric line");
        assert_eq!(lines[1].time_ms, 10500);

        assert_eq!(lines[2].text, "Third lyric line after a minute");
        assert_eq!(lines[2].time_ms, 62100);
    }

    #[test]
    fn test_parse_plain_content() {
        let plain = "Line one\n\nLine two\nLine three";
        let lines = parse_plain_content(plain);
        assert_eq!(lines.len(), 3);
        assert_eq!(lines[0].text, "Line one");
        assert_eq!(lines[0].time_ms, 0);
        assert_eq!(lines[1].text, "Line two");
        assert_eq!(lines[1].time_ms, 4000);
        assert_eq!(lines[2].text, "Line three");
        assert_eq!(lines[2].time_ms, 8000);
    }
}
