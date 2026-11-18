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
                .user_agent("GLOOM-Desktop/2.6.0 (https://github.com/nandub/gloom)")
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

pub fn strip_emojis_and_symbols(input: &str) -> String {
    input
        .chars()
        .filter(|&c| {
            !matches!(
                c,
                '\u{1F300}'..='\u{1F9FF}' | // Misc Symbols and Pictographs, Emoticons, Transport, Alchemical
                '\u{2600}'..='\u{26FF}'   | // Misc symbols
                '\u{2700}'..='\u{27BF}'   | // Dingbats
                '\u{FE00}'..='\u{FE0F}'   | // Variation Selectors
                '\u{1F000}'..='\u{1F02F}' | // Mahjong
                '\u{1F0A0}'..='\u{1F0FF}' | // Playing Cards
                '\u{1F100}'..='\u{1F2FF}' | // Enclosed Alphanumeric
                '\u{2300}'..='\u{23FF}'   | // Misc Technical
                '\u{2B50}'                | // Star
                '\u{2022}' | '\u{00B7}' | '\u{2219}' // Bullets
            )
        })
        .collect::<String>()
}

pub fn remove_noise_case_insensitive(mut text: String, noise: &str) -> String {
    let noise_lower = noise.to_lowercase();
    while let Some(pos) = text.to_lowercase().find(&noise_lower) {
        text.replace_range(pos..pos + noise_lower.len(), "");
    }
    text.trim().to_string()
}

pub fn sanitize_lyrics_metadata(title: &str, artist: &str) -> (Vec<String>, Vec<String>) {
    let raw_t = title.trim();
    let raw_a = artist.trim();

    // 1. Clean noise from artist
    let mut clean_a = raw_a.to_string();
    let bracket_noise = [
        "[original motion picture soundtrack]", "(original motion picture soundtrack)",
        "[motion picture soundtrack]", "(motion picture soundtrack)",
        "[soundtrack]", "(soundtrack)", "[ost]", "(ost)",
        "[deluxe edition]", "(deluxe edition)", "[deluxe]", "(deluxe)",
        "[expanded edition]", "(expanded edition)", "[remastered]", "(remastered)",
    ];
    for noise in bracket_noise {
        clean_a = remove_noise_case_insensitive(clean_a, noise);
    }

    // Strip "- Topic" suffix from artist
    if clean_a.to_lowercase().ends_with("- topic") {
        clean_a = clean_a[..clean_a.len() - 7].trim().to_string();
    }

    // If artist contains " - " followed by movie/album info (e.g. "Anirudh ... - Remo (Telugu)"), strip after " - "
    if let Some((main_artist, _album_part)) = clean_a.split_once(" - ") {
        if !main_artist.trim().is_empty() {
            clean_a = main_artist.trim().to_string();
        }
    }

    // Check if artist is a generic browser/platform name
    let is_generic_artist = clean_a.is_empty()
        || clean_a.eq_ignore_ascii_case("youtube")
        || clean_a.eq_ignore_ascii_case("youtube music")
        || clean_a.eq_ignore_ascii_case("google chrome")
        || clean_a.eq_ignore_ascii_case("chrome")
        || clean_a.eq_ignore_ascii_case("brave")
        || clean_a.eq_ignore_ascii_case("firefox")
        || clean_a.eq_ignore_ascii_case("edge")
        || clean_a.eq_ignore_ascii_case("spotify free")
        || clean_a.eq_ignore_ascii_case("unknown artist");

    let mut clean_t = raw_t.to_string();

    // If artist is generic, try splitting "Artist - Title" from clean_t
    if is_generic_artist {
        for sep in [" - ", " – ", " — ", " : "] {
            if let Some((detected_artist, detected_track)) = clean_t.split_once(sep) {
                if !detected_artist.trim().is_empty() && !detected_track.trim().is_empty() {
                    clean_a = detected_artist.trim().to_string();
                    clean_t = detected_track.trim().to_string();
                    break;
                }
            }
        }
    }

    // 2. Clean noise from title
    let title_noise = [
        "(official video)", "[official video]", "(official audio)", "[official audio]",
        "(official music video)", "[official music video]", "(music video)", "[music video]",
        "(lyrics)", "[lyrics]", "(lyric video)", "[lyric video]", "(audio)", "[audio]",
        "(visualizer)", "[visualizer]", "(full song)", "[full song]", "(remastered)", "[remastered]",
        "(video)", "[video]", "(hd)", "[hd]", "(4k)", "[4k]", "(mv)", "[mv]", "(hq)", "[hq]",
    ];
    for noise in title_noise {
        clean_t = remove_noise_case_insensitive(clean_t, noise);
    }

    // Strip file extensions like .mp3, .wav, .m4a
    for ext in [".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma", ".opus"] {
        if clean_t.to_lowercase().ends_with(ext) {
            clean_t = clean_t[..clean_t.len() - ext.len()].trim().to_string();
        }
    }

    // Strip leading track numbers like "01. ", "01 - "
    if let Some(first_space) = clean_t.find(' ') {
        let prefix = &clean_t[..first_space];
        if prefix.chars().all(|c| c.is_ascii_digit() || c == '.' || c == '-') {
            clean_t = clean_t[first_space + 1..].trim().to_string();
        }
    }

    // 3. Extract primary title (before bullets •, ·, ∙, pipe |, tilde ~)
    let primary_title_segment = clean_t
        .split(|c| c == '•' || c == '·' || c == '∙' || c == '|' || c == '~')
        .next()
        .unwrap_or(&clean_t)
        .trim();

    let primary_title_no_emojis = strip_emojis_and_symbols(primary_title_segment)
        .trim()
        .to_string();

    let clean_title_no_emojis = strip_emojis_and_symbols(&clean_t)
        .trim()
        .to_string();

    // 4. Candidate Titles
    let mut candidate_titles = Vec::with_capacity(3);
    if !primary_title_no_emojis.is_empty() {
        candidate_titles.push(primary_title_no_emojis);
    }
    if !clean_title_no_emojis.is_empty() && !candidate_titles.contains(&clean_title_no_emojis) {
        candidate_titles.push(clean_title_no_emojis);
    }
    if !raw_t.is_empty() && !candidate_titles.iter().any(|t| t.eq_ignore_ascii_case(raw_t)) {
        candidate_titles.push(raw_t.to_string());
    }

    // 5. Candidate Artists
    let primary_artist = clean_a
        .split(|c| c == ',' || c == '&' || c == '/')
        .next()
        .unwrap_or(&clean_a)
        .trim();

    // Strip "feat." or "ft." in primary artist
    let primary_artist = if let Some((lead, _)) = primary_artist.split_once(" feat. ") {
        lead.trim()
    } else if let Some((lead, _)) = primary_artist.split_once(" feat ") {
        lead.trim()
    } else if let Some((lead, _)) = primary_artist.split_once(" ft. ") {
        lead.trim()
    } else if let Some((lead, _)) = primary_artist.split_once(" ft ") {
        lead.trim()
    } else {
        primary_artist
    };

    let primary_artist_no_emojis = strip_emojis_and_symbols(primary_artist)
        .trim()
        .to_string();
    let clean_a_no_emojis = strip_emojis_and_symbols(&clean_a)
        .trim()
        .to_string();

    let mut candidate_artists = Vec::with_capacity(2);
    if !primary_artist_no_emojis.is_empty() && !primary_artist_no_emojis.eq_ignore_ascii_case("unknown artist") {
        candidate_artists.push(primary_artist_no_emojis);
    }
    if !clean_a_no_emojis.is_empty() && !candidate_artists.contains(&clean_a_no_emojis) && !clean_a_no_emojis.eq_ignore_ascii_case("unknown artist") {
        candidate_artists.push(clean_a_no_emojis);
    }

    (candidate_titles, candidate_artists)
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

    let (candidate_titles, candidate_artists) = sanitize_lyrics_metadata(&title, &artist);
    if candidate_titles.is_empty() {
        return Err("Empty track title".into());
    }

    let primary_title = candidate_titles.first().cloned().unwrap_or_else(|| title.clone());
    let primary_artist = candidate_artists.first().cloned().unwrap_or_else(|| artist.clone());

    // 1. Waterfall Tier 1: Exact GET for candidate combinations
    for c_title in &candidate_titles {
        for c_artist in &candidate_artists {
            let exact_url = format!(
                "https://lrclib.net/api/get?track_name={}&artist_name={}",
                url_encode(c_title),
                url_encode(c_artist)
            );

            if let Ok(resp) = client.get(&exact_url).send().await {
                if resp.status().is_success() {
                    if let Ok(item) = resp.json::<LrcLibItem>().await {
                        if let Some(ref synced) = item.synced_lyrics {
                            if !synced.trim().is_empty() {
                                let lines = parse_lrc_content(synced);
                                return Ok(crate::types::LyricsResult {
                                    id: item.id,
                                    track_name: item.track_name.or(Some(primary_title)),
                                    artist_name: item.artist_name.or(Some(primary_artist)),
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
                                    track_name: item.track_name.or(Some(primary_title)),
                                    artist_name: item.artist_name.or(Some(primary_artist)),
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
    }

    // Helper to evaluate search candidates with duration proximity
    let evaluate_search_items = |items: Vec<LrcLibItem>| -> Option<crate::types::LyricsResult> {
        if items.is_empty() {
            return None;
        }

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
                    return Some(crate::types::LyricsResult {
                        id: item.id,
                        track_name: item.track_name.clone().or_else(|| Some(primary_title.clone())),
                        artist_name: item.artist_name.clone().or_else(|| Some(primary_artist.clone())),
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
                    return Some(crate::types::LyricsResult {
                        id: item.id,
                        track_name: item.track_name.clone().or_else(|| Some(primary_title.clone())),
                        artist_name: item.artist_name.clone().or_else(|| Some(primary_artist.clone())),
                        synced_lyrics: None,
                        plain_lyrics: item.plain_lyrics.clone(),
                        lines,
                        is_synced: false,
                    });
                }
            }
        }
        None
    };

    // 2. Waterfall Tier 2: Search with track_name & artist_name
    for c_title in &candidate_titles {
        for c_artist in &candidate_artists {
            let search_url = format!(
                "https://lrclib.net/api/search?track_name={}&artist_name={}",
                url_encode(c_title),
                url_encode(c_artist)
            );
            if let Ok(resp) = client.get(&search_url).send().await {
                if resp.status().is_success() {
                    if let Ok(items) = resp.json::<Vec<LrcLibItem>>().await {
                        if let Some(result) = evaluate_search_items(items) {
                            return Ok(result);
                        }
                    }
                }
            }
        }
    }

    // 3. Waterfall Tier 3: Search with combined query `q={title} {artist}`
    for c_title in &candidate_titles {
        for c_artist in &candidate_artists {
            let search_url = format!(
                "https://lrclib.net/api/search?q={}",
                url_encode(&format!("{} {}", c_title, c_artist).trim())
            );
            if let Ok(resp) = client.get(&search_url).send().await {
                if resp.status().is_success() {
                    if let Ok(items) = resp.json::<Vec<LrcLibItem>>().await {
                        if let Some(result) = evaluate_search_items(items) {
                            return Ok(result);
                        }
                    }
                }
            }
        }
    }

    // 4. Waterfall Tier 4: Search with track_name alone
    for c_title in &candidate_titles {
        let search_url = format!(
            "https://lrclib.net/api/search?track_name={}",
            url_encode(c_title)
        );
        if let Ok(resp) = client.get(&search_url).send().await {
            if resp.status().is_success() {
                if let Ok(items) = resp.json::<Vec<LrcLibItem>>().await {
                    if let Some(result) = evaluate_search_items(items) {
                        return Ok(result);
                    }
                }
            }
        }
    }

    // 5. Waterfall Tier 5: Search with `q={title}` alone
    for c_title in &candidate_titles {
        let search_url = format!(
            "https://lrclib.net/api/search?q={}",
            url_encode(c_title)
        );
        if let Ok(resp) = client.get(&search_url).send().await {
            if resp.status().is_success() {
                if let Ok(items) = resp.json::<Vec<LrcLibItem>>().await {
                    if let Some(result) = evaluate_search_items(items) {
                        return Ok(result);
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

    #[test]
    fn test_strip_emojis_and_symbols() {
        let dirty = "Kathakaadhey • 🎶 ఒదిలేయవే ఒదిలేయవే 🔥";
        let stripped = strip_emojis_and_symbols(dirty);
        assert_eq!(stripped.trim(), "Kathakaadhey   ఒదిలేయవే ఒదిలేయవే");
    }

    #[test]
    fn test_sanitize_lyrics_metadata_multilingual_track() {
        let title = "Kathakaadhey • 🎶 ఒదిలేయవే ఒదిలేయవే";
        let artist = "Anirudh Ravichander, Ajesh, Srinidhi Venkatesh - Remo (Telugu) [Original Motion Picture Soundtrack]";
        let (candidates_t, candidates_a) = sanitize_lyrics_metadata(title, artist);
        
        assert!(!candidates_t.is_empty());
        assert_eq!(candidates_t[0], "Kathakaadhey");
        assert!(!candidates_a.is_empty());
        assert_eq!(candidates_a[0], "Anirudh Ravichander");
    }

    #[test]
    fn test_sanitize_lyrics_metadata_youtube_topic() {
        let title = "The Weeknd - Blinding Lights (Official Audio)";
        let artist = "YouTube";
        let (candidates_t, candidates_a) = sanitize_lyrics_metadata(title, artist);
        
        assert_eq!(candidates_t[0], "Blinding Lights");
        assert_eq!(candidates_a[0], "The Weeknd");
    }
}
