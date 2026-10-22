package com.gloom.island.automation

/**
 * Target application configurations for full-screen short-form video viewers.
 *
 * Adapted from the open-source reels-tracker (https://github.com/s0uvik/reels-tracker).
 * IDs are the resource entry names; package prefix is added at lookup time.
 */
data class TrackedAppUi(
    val appKey: String,
    val packageName: String,
    /** Any of these present on screen implies the full-screen reel viewer is open. */
    val viewerContainerIds: List<String>,
    /** Nodes whose text / content description identify the visible reel. */
    val fingerprintIds: List<String>,
    /** Substrings used for debug logging of possibly-relevant unknown layouts. */
    val debugIdHints: List<String>
)

object TrackedAppConfig {
    val INSTAGRAM = TrackedAppUi(
        appKey = "instagram",
        packageName = "com.instagram.android",
        viewerContainerIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container"
        ),
        fingerprintIds = listOf(
            "clips_author_username",
            "clips_caption_component",
            "clips_media_component",
            "clips_single_media_component"
        ),
        debugIdHints = listOf("clips", "reel")
    )

    val FACEBOOK = TrackedAppUi(
        appKey = "facebook",
        packageName = "com.facebook.katana",
        viewerContainerIds = listOf(
            "fb_shorts_viewer_container",
            "clips_viewer_container",
            "reel_viewer_container"
        ),
        fingerprintIds = listOf(
            "fb_shorts_author_name",
            "fb_shorts_title",
            "reel_author"
        ),
        debugIdHints = listOf("shorts", "reel")
    )

    val YOUTUBE_SHORTS = TrackedAppUi(
        appKey = "youtube_shorts",
        packageName = "com.google.android.youtube",
        viewerContainerIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player"
        ),
        fingerprintIds = listOf(
            "reel_channel_name",
            "reel_multi_format_title",
            "reel_main_title"
        ),
        debugIdHints = listOf("reel", "shorts")
    )

    val ALL: List<TrackedAppUi> = listOf(INSTAGRAM, FACEBOOK, YOUTUBE_SHORTS)

    fun forPackage(packageName: CharSequence?): TrackedAppUi? {
        if (packageName == null) return null
        return ALL.firstOrNull { it.packageName.contentEquals(packageName) }
    }

    const val MAX_TRAVERSAL_DEPTH = 6
    const val MAX_TRAVERSAL_NODES = 60
    const val INSPECT_SETTLE_MS = 250L
    const val INSPECT_MAX_WAIT_MS = 1000L
}
