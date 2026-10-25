# Dynamic Island Pro – Layouts, Views & Component Architecture

> **Complete XML View Hierarchies & Component Specifications**  
> **Source**: `res/layout/*.xml`, `res/values/*.xml`, `a94.java`, `MediaOverlayManager.java`

---

## 1. Overview of Layout Hierarchy

The application employs specialized layouts for each distinct window overlay phase:

```
Window Overlays
├── Main Pill Overlay (TYPE_APPLICATION_OVERLAY)
│   └── fragment_notch.xml
│       ├── CardView [cardView_notch] (Black background, dynamic corner radii)
│       │   ├── ImageView [bg_image] (Custom wallpapers / skin textures)
│       │   ├── CardView [border_view] (Neon glow / stroke border)
│       │   ├── LottieAnimationView [lottieView] (Pet micro-animations)
│       │   └── ConstraintLayout [constraintLayout_notch2] (Legacy Views)
│       │       ├── ImageView [right_icon_notch] (Status glyphs)
│       │       ├── ShapeableImageView [imageView_left] (Avatars & icons)
│       │       ├── TextView [Notification_AppName] (App title)
│       │       ├── TextView [title_notifiaction] (Marquee text)
│       │       ├── TextView [charging_left_text] (Left battery label)
│       │       └── TextView [charging_text] (Right battery level)
│       └── ComposeView [compose_view] (Primary modern Jetpack Compose surface)
│
├── Expanded Media Island Overlay
│   └── item_medilayout.xml
│       └── CardView [MediaPanelLayout] (Radius 24dp, Elevation 8dp)
│           ├── ImageView [media_image] (48dp x 48dp album art)
│           ├── TextView [media_Name] (Track title, 16sp, Bold white)
│           ├── TextView [artist_name] (Artist name, 14sp, Slate gray)
│           ├── ImageView [imageView_mediabar] (32dp x 32dp close button)
│           ├── TextView [media_time_text] (Elapsed time, MM:SS)
│           ├── SeekBar [media_seekbarnotch] (Custom 2dp white scrubber)
│           ├── TextView [media_time_text_end] (Total duration, MM:SS)
│           ├── ImageButton [backToMusic] (Previous track, 48dp)
│           ├── ImageButton [media_play_pause] (Play/Pause, 48dp)
│           └── ImageButton [next_tomusic] (Next track, 48dp)
│
├── Notification Drawer Overlay
│   ├── fragment_notifiaction_list.xml (RecyclerView container)
│   └── notifiaction_item.xml (Individual notification cards)
│       └── CardView (Radius 12dp, Elevation 6dp)
│           ├── ImageView [app_icon_item] (48dp x 48dp app icon)
│           ├── ImageView [close_icon] (32dp x 32dp dismiss button)
│           ├── TextView [time_notifiaction] (12sp timestamp "Just now")
│           ├── TextView [notifaction_title_item] (16sp bold title)
│           └── TextView [notifaction_description_item] (14sp description)
│
└── App Drawer Quick Launch Overlay
    ├── app_drawer_overlay.xml (Horizontal scrollable dock)
    └── app_driwer_item.xml (Grid application shortcut icon)
```

---

## 2. Detailed View Specifications

### 2.1 Collapsed Notch Layout (`fragment_notch.xml`)

| View ID | View Class | Dimensions | Layout Constraints / Margins | Visual Style / Attributes |
| :--- | :--- | :--- | :--- | :--- |
| `layout_fragment_notch` | `ConstraintLayout` | `match_parent` x `match_parent` | Root View | Transparent container |
| `cardView_notch` | `CardView` | `match_parent` x `match_parent` | Centered horizontally | `cardBackgroundColor`: `@color/black`, `cardElevation`: `4dp` |
| `bg_image` | `ImageView` | `match_parent` x `match_parent` | Fills card | `scaleType`: `centerCrop`, `visibility`: `gone` (enabled on wallpaper themes) |
| `border_view` | `CardView` | `match_parent` x `match_parent` | Fills card | `cardBackgroundColor`: `@android:color/transparent`, stroke set dynamically |
| `lottieView` | `LottieAnimationView` | `40dp` x `28dp` | Centered top | AutoPlay=true, Loop=true, loads pet Lottie JSON |
| `constraintLayout_notch2` | `ConstraintLayout` | `match_parent` x `match_parent` | Fills card | Host container for native notch elements |
| `imageView_left` | `ShapeableImageView` | `16dp` x `16dp` | Start: `4dp`, Centered vertically | Shape: `@style/RoundedImageView` (circular crop) |
| `Notification_AppName` | `TextView` | `wrap_content` x `wrap_content` | Start: `8dp` to `imageView_left` | Size: `10sp`, Color: `@color/white`, `singleLine`: true, ellipsize |
| `title_notifiaction` | `TextView` | `130dp` x `wrap_content` | End of parent | Size: `10sp`, Color: `@color/white`, `marqueeRepeatLimit`: `forever` |
| `charging_left_text` | `TextView` | `wrap_content` x `wrap_content` | Start: `8dp` | Size: `10sp`, Color: `@color/white`, shows "Charging" |
| `charging_text` | `TextView` | `wrap_content` x `wrap_content` | End: `8dp` to icon, MaxWidth: `150dp` | Size: `10sp`, Color: `@color/button_active_color` (`#8DFC63`), marquee |
| `right_icon_notch` | `ImageView` | `16dp` x `16dp` | End: `8dp`, Centered vertically | Battery glyph, indicator, or ring icon |
| `compose_view` | `ComposeView` | `match_parent` x `match_parent` | Root constraint bounds | Primary surface for Jetpack Compose `bg2.b` tree |

---

### 2.2 Expanded Media Player (`item_medilayout.xml`)

| View ID | View Class | Dimensions | Layout Constraints / Margins | Visual Style / Attributes |
| :--- | :--- | :--- | :--- | :--- |
| `MediaPanelLayout` | `CardView` | Width: `0dp` (match constraints), Height: `wrap_content` | Start: `8dp`, End: `8dp`, Top: `24dp`, Bottom: `16dp` | `cardBackgroundColor`: `@color/black`, `cardCornerRadius`: `24dp`, `elevation`: `8dp` |
| `media_image` | `ImageView` | `48dp` x `48dp` | Top: `8dp`, Start: `8dp` | Album Art thumbnail with `RoundedImageView` style |
| `media_Name` | `TextView` | Width: `0dp`, Height: `wrap_content` | Start: `8dp` to image, End: `16dp` to close | Size: `16sp`, Color: `@color/white`, singleLine=true, ellipsize end |
| `artist_name` | `TextView` | Width: `wrap_content`, Height: `wrap_content` | Start aligned to `media_Name`, below it | Size: `14sp`, Color: `@color/subtitle_color` (`#7D889B`) |
| `imageView_mediabar` | `ImageView` | `32dp` x `32dp` | Top: `8dp`, End: `8dp` | Close icon (`@drawable/baseline_close_24`), `scaleType`: fitCenter |
| `media_time_text` | `TextView` | Width: `wrap_content`, Height: `wrap_content` | Below album art, Top: `24dp`, Start: `8dp` | Size: `12sp`, Color: `@color/subtitle_color`, displays elapsed `MM:SS` |
| `media_seekbarnotch` | `SeekBar` | Width: `0dp`, Height: `wrap_content` | Between elapsed and remaining text | `progressDrawable`: `@drawable/custom_seekbar_progress`, `thumb`: `@null` |
| `media_time_text_end`| `TextView` | Width: `wrap_content`, Height: `wrap_content` | End: `8dp` of parent | Size: `12sp`, Color: `@color/subtitle_color`, displays remaining `MM:SS` |
| `media_play_pause` | `ImageButton` | `48dp` x `48dp` | Center aligned, Top: `16dp` below seekbar | Background: `@null`, Icon: `@drawable/baseline_pause_24` |
| `backToMusic` | `ImageButton` | `48dp` x `48dp` | End: `24dp` from play button | Background: `@null`, Icon: `@drawable/baseline_skip_previous_24` |
| `next_tomusic` | `ImageButton` | `48dp` x `48dp` | Start: `24dp` from play button | Background: `@null`, Icon: `@drawable/baseline_skip_next_24` |

---

### 2.3 Individual Notification Card (`notifiaction_item.xml`)

| View ID | View Class | Dimensions | Layout Constraints / Margins | Visual Style / Attributes |
| :--- | :--- | :--- | :--- | :--- |
| Root | `ConstraintLayout` | `match_parent` x `wrap_content` | Padding: Left/Right `16dp`, Top `8dp` | Outer padding bounds |
| Card Container | `CardView` | `match_parent` x `wrap_content` | Bottom: `8dp` | `cardBackgroundColor`: `@color/black`, `cardCornerRadius`: `12dp`, `elevation`: `6dp` |
| `app_icon_item` | `ImageView` | `48dp` x `48dp` | Start: `16dp`, Centered vertically | Branded app icon / sender photo (`scaleType`: centerCrop) |
| `time_notifiaction` | `TextView` | `wrap_content` x `wrap_content` | Start: `8dp` to icon, Top of parent | Size: `12sp`, Color: `@color/button_active_color` (`#8DFC63`), "Just now" |
| `notifaction_title_item` | `TextView` | Width: `0dp` (flex), Height: `wrap_content` | Below time, End: `close_icon` | Size: `16sp`, Bold, Color: `@color/primary_color`, maxLines=1 |
| `notifaction_description_item` | `TextView` | Width: `0dp` (flex), Height: `wrap_content` | Below title, Bottom: `8dp` | Size: `14sp`, Color: `@color/subtitle_color`, maxLines=2, ellipsize end |
| `close_icon` | `ImageView` | `32dp` x `32dp` | Top: `8dp`, End: `8dp` | White dismiss cross (`@drawable/close_icon`) |

---

### 2.4 Quick Launch App Drawer Overlay (`app_drawer_overlay.xml` & `app_driwer_item.xml`)

* **Host Card**: `app_drawer_card` inside `app_drawer_overlay.xml`:
  * Background: Solid black (`@color/black`).
  * Corner Radius: `16dp`.
  * Elevation: `8dp`.
* **Dock Items (`app_driwer_item.xml`)**:
  * **App Icon Button**: `56dp x 56dp`.
    * Background: `@drawable/bg_radius20_black` (20dp corner radius).
    * Background Tint: `#40FFFFFF` (25% translucent frosted glass).
    * Inner Padding: `16dp`.
    * Focusable & Clickable with ripple feedback.
  * **App Label**: `12sp`, White `#FFFFFF`, Center aligned, MaxLines=2, MaxLength=16 characters.

---

## 3. Custom SeekBar Architecture (`custom_seekbar_progress.xml`)

The music seekbar is engineered to mirror iOS scrubber ergonomics with an ultra-thin line and rounded caps:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Unfilled Track -->
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <size android:height="0.75dp" />
            <solid android:color="#1AFFFFFF" />
            <corners android:radius="5dp" />
        </shape>
    </item>
    
    <!-- Elapsed Progress Fill -->
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <size android:height="2.0dp" />
                <solid android:color="#FFFFFF" />
                <corners android:radius="5dp" />
            </shape>
        </clip>
    </item>
</layer-list>
```
* **Background Height**: Exactly `0.75dp` — razor thin, non-intrusive.
* **Progress Height**: `2.0dp` — slightly thicker than the background to give active visual emphasis.
* **Thumb**: Completely hidden (`thumb="@null"`), allowing scrubbing by dragging anywhere across the horizontal bar.
