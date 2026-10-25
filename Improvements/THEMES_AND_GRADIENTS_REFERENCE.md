# Dynamic Island Pro – Themes, Gradients & Color Architecture

> **Extracted from `tr.com.eywin.dynamic.island.notch` (`q04.java`, `GradientColors.java`, `ThemeManager.java`)**

---

## 1. Complete Catalog of 51 Visual Themes

The application features 51 built-in theme presets configured through `ThemeManager.kt`. Each theme defines a background fill (solid color, linear gradient, radial gradient, or angular sweep), optional neon border colors, border stroke widths, and light/dark contrasting modes.

| # | Theme ID | Display Name | Background Fill Type | Color / Gradient Stops | Border Color | Border Width | Premium | Visual Description |
|---|:---|:---|:---|:---|:---|:---|:---:|:---|
| 1 | `classic_black` | **Classic Black** | Solid | `#000000` (OLED Pitch Black) | Transparent (`0L`) | `0.0 dp` | No | Authentic iOS pitch-black pill |
| 2 | `minimal_gray` | **Minimal Gray** | Solid | `#2B2B2C` (`0xFF2B2B2C`) | `#3C3C3D` (`0xFF3C3C3D`) | `0.5 dp` | No | Refined industrial matte gray with subtle hairline border |
| 3 | `midnight_blue` | **Midnight** | Solid | `#131820` (`0xFF131820`) | `#232B35` (`0xFF232B35`) | `1.0 dp` | No | Deep nocturnal navy blue |
| 4 | `amethyst` | **Amethyst** | Horizontal Gradient | `#8A2BE2` -> `#5D3FD3` | Transparent | `0.0 dp` | Yes | Rich glowing purple-violet |
| 5 | `sunset` | **Sunset** | Linear Gradient | `#FF5722` -> `#C2185B` | Transparent | `0.0 dp` | Yes | Warm tropical orange into deep magenta |
| 6 | `neon_cyber` | **Neon Cyber** | Solid | `#000000` | `#00FFCC` (`0xFF00FFCC`) | `1.0 dp` | Yes | Pitch black with an electric neon cyan glowing outline |
| 7 | `royal_gold` | **Royal Gold** | Horizontal Gradient | `#D4AF37` -> `#FFD700` -> `#C5A028` | `#FFDF00` | `1.0 dp` | Yes | Three-stop luxury metallic gold with gold stroke |
| 8 | `ocean_breeze` | **Ocean Breeze** | Linear Gradient | `#007799` -> `#004466` | Transparent | `0.0 dp` | Yes | Oceanic deep sea cyan to navy |
| 9 | `crimson_fire` | **Crimson Fire** | Horizontal Gradient | `#D32F2F` -> `#FF1744` | Transparent | `0.0 dp` | Yes | Vivid racing red fire gradient |
| 10 | `deep_space` | **Deep Space** | Solid | `#0B0C10` | `#1F2833` | `1.0 dp` | Yes | Interstellar black with metallic graphite border |
| 11 | `forest_rain` | **Forest Rain** | Linear Gradient | `#0F2027` -> `#203A43` | Transparent | `0.0 dp` | No | Dark emerald green fading into atmospheric rain |
| 12 | `purple_haze` | **Purple Haze** | Solid | `#301934` | `#58111A` | `1.0 dp` | No | Dark plum purple with deep wine border |
| 13 | `cherry_blossom` | **Cherry Blossom** | Horizontal Gradient | `#FFB7B2` -> `#FF9AA2` | Transparent | `0.0 dp` | No | Soft Japanese sakura pastel blossom |
| 14 | `golden_hour` | **Golden Hour** | Linear Gradient | `#FFA000` -> `#FF6F00` | Transparent | `0.0 dp` | No | Dusk amber sunbeams |
| 15 | `matrix` | **The Matrix** | Solid | `#000000` | `#00FF00` (`0xFF00FF00`) | `1.5 dp` | Yes | Pure black with 1.5dp radiant terminal green outline |
| 16 | `cyberpunk_pink` | **Cyber Pink** | Solid | `#1E002B` | `#FF007F` (`0xFFFF007F`) | `1.0 dp` | Yes | Synthwave dark violet with neon magenta edge |
| 17 | `mint_fresh` | **Mint Fresh** | Horizontal Gradient | `#00F260` -> `#0575E6` | Transparent | `0.0 dp` | No | Refreshing mint green to electric blue |
| 18 | `silver_lining` | **Silver Lining** | Horizontal Gradient | `#DCDCDC` -> `#E8E8E8` | `#B0B0B0` | `0.5 dp` | No | Brushed platinum silver with chrome border |
| 19 | `carbon_fiber` | **Carbon Fiber** | Solid | `#1A1A1A` | `#333333` | `1.0 dp` | No | Matte carbon weave dark gray |
| 20 | `electric_blue` | **Electric Blue** | Solid | `#001F3F` | `#0074D9` | `2.0 dp` | No | Deep navy base with 2.0dp bold electric blue rim |
| 21 | `sweet_candy` | **Sweet Candy** | Horizontal Gradient | `#FBC5EC` -> `#A6C1EE` | Transparent | `0.0 dp` | No | Pastel candyfloss and soft sky blue |
| 22 | `northern_lights`| **Northern Lights**| Sweep Gradient | `#00C9FF` -> `#92FE9D` | Transparent | `0.0 dp` | No | Aurora polar borealis radial sweep |
| 23 | `pure_white` | **Pure White** | Solid | `#FFFFFF` (Crisp White) | `#D1D1D6` | `0.5 dp` | No | Inverted high-contrast iOS light-mode look |
| 24 | `vampire_red` | **Vampire Red** | Solid | `#2A0808` | `#FF0000` | `1.0 dp` | No | Deep blood mahogany with crimson edge |
| 25 | `emerald_city` | **Emerald City** | Horizontal Gradient | `#004B23` -> `#007200` | Transparent | `0.0 dp` | No | Lush deep emerald jewel gradient |
| 26 | `cosmic_void` | **Cosmic Void** | Solid | `#080014` | `#8A2BE2` | `0.5 dp` | No | Void black with neon purple glow |
| 27 | `rose_gold` | **Rose Gold** | Horizontal Gradient | `#F7CAC9` -> `#DEC2BA` | Transparent | `0.0 dp` | No | Elegant soft rose gold shimmer |
| 28 | `ice_cold` | **Ice Cold** | Linear Gradient | `#74EBD5` -> `#9FACE6` -> `#FFFFFF` | Transparent | `0.0 dp` | No | Frostbite icy crystal blue and white |
| 29 | `lemon_twist` | **Lemon Twist** | Horizontal Gradient | `#FFE000` -> `#799F0C` | Transparent | `0.0 dp` | No | Citrus neon lime and yellow |
| 30 | `twilight` | **Twilight** | Horizontal Gradient | `#0F2027` -> `#203A43` -> `#2C5364` | Transparent | `0.0 dp` | No | 3-stop dusk gradient |
| 31 | `hot_lava` | **Hot Lava** | Horizontal Gradient | `#FF4E50` -> `#F9D423` | `#FF6600` | `1.0 dp` | Yes | Volcanic magma gradient with fire border |
| 32 | `sea_foam` | **Sea Foam** | Solid | `#1E3F3B` | `#40826D` | `1.0 dp` | No | Deep oceanic teal with seafoam border |
| 33 | `nebula` | **Nebula** | Radial Gradient | `#4A00E0` -> `#8E2DE2` | Transparent | `0.0 dp` | No | Intergalactic violet-purple swirl |
| 34 | `bumblebee` | **Bumblebee** | Solid | `#FFCC00` | `#000000` | `2.0 dp` | No | High-visibility yellow pill with bold black border |
| 35 | `slate` | **Slate** | Solid | `#2E3440` | `#4C566A` | `1.0 dp` | No | Nordic architectural slate gray |
| 36 | `rainbow` | **Rainbow** | Horizontal Gradient | `#FF0000` -> `#FF7F00` -> `#FFFF00` -> `#00FF00` -> `#0000FF` | Transparent | `0.0 dp` | No | Full 5-stop spectrum pride rainbow |
| 37 | `stormy_night` | **Stormy Night** | Linear Gradient | `#232526` -> `#414345` | Transparent | `0.0 dp` | No | Thundercloud dark gray metallic gradient |
| 38 | `bubblegum` | **Bubblegum** | Solid | `#FF69B4` | `#FF1493` | `1.5 dp` | No | Hot bubblegum pink with deep fuchsia border |
| 39 | `toxic_green` | **Toxic Green** | Solid | `#0D1F0D` | `#39FF14` | `1.0 dp` | No | Biohazard dark base with glowing green stroke |
| 40 | `ruby_red` | **Ruby Red** | Radial Gradient | `#870000` -> `#190A05` | Transparent | `0.0 dp` | No | Deep gemstone ruby luster |
| 41 | `deep_sea` | **Deep Sea** | Solid | `#001122` | `#003366` | `0.5 dp` | No | Abyssal ocean midnight |
| 42 | `pumpkin_spice`| **Pumpkin** | Solid | `#D35400` | `#873600` | `1.0 dp` | No | Autumn burnt orange |
| 43 | `titanium` | **Titanium** | Horizontal Gradient | `#3A3D40` -> `#181719` | Transparent | `0.0 dp` | No | Brushed titanium alloy |
| 44 | `insta_style` | **Influencer** | Horizontal Gradient | `#833AB4` -> `#FD1D1D` -> `#FCB045` | Transparent | `0.0 dp` | No | Signature Instagram sunset gradient |
| 45 | `glitch` | **Glitch** | Solid | `#111122` | `#00FFFF` | `1.0 dp` | No | Cyber glitch dark slate with cyan stroke |
| 46 | `copper` | **Copper** | Horizontal Gradient | `#B87333` -> `#E59866` | Transparent | `0.0 dp` | No | Polished burnished copper |
| 47 | `lavender` | **Lavender** | Solid | `#E6E6FA` | `#9370DB` | `1.0 dp` | No | Soft pastel lavender with purple trim |
| 48 | `stealth` | **Stealth** | Solid | `#1C1C1E` | `#2C2C2E` | `0.5 dp` | No | Matte tactical stealth black |
| 49 | `miami_vice` | **Miami Vice** | Sweep Gradient | `#00F0FF` -> `#FF007F` | Transparent | `0.0 dp` | No | 80s retro synthwave cyan and magenta |
| 50 | `miami_vice_real`| **Miami Vibes**| Sweep Gradient | `#FF1493` -> `#00CED1` | Transparent | `0.0 dp` | No | Vibrant neon tropical evening vibes |
| 51 | `zen_garden` | **Zen Garden** | Solid | `#2D3A27` | `#4B5320` | `1.0 dp` | No | Organic calming matcha olive green |

---

## 2. The 20 Custom Gradient Pairs (`GradientColors.java`)

Users can customize custom gradients on top of the pill bar via `selected_gradient`. The app defines 20 dual-stop color recipes:

| Pair # | Start Color Hex | End Color Hex | Visual Description |
|:---:|:---|:---|:---|
| **01** | `#FF5733` | `#C70039` | Crimson Sunset Orange to Cherry Red |
| **02** | `#6A0572` | `#AB83A1` | Deep Royal Violet to Heather Purple |
| **03** | `#1B1464` | `#5C2E91` | Midnight Navy to Vibrant Purple |
| **04** | `#009688` | `#4CAF50` | Teal Green to Material Forest Green |
| **05** | `#F44336` | `#E91E63` | Material Red to Pink Magenta |
| **06** | `#3F51B5` | `#2196F3` | Indigo to Material Sky Blue |
| **07** | `#FFC107` | `#FF9800` | Amber to Deep Tangerine Orange |
| **08** | `#8BC34A` | `#CDDC39` | Light Lime Green to Electric Lime |
| **09** | `#673AB7` | `#9C27B0` | Deep Purple to Magenta Orchid |
| **10** | `#795548` | `#FF5722` | Earth Warm Brown to Deep Orange |
| **11** | `#00BCD4` | `#009688` | Vivid Cyan to Dark Sea Teal |
| **12** | `#E91E63` | `#F06292` | Vivid Pink to Soft Cotton Candy Pink |
| **13** | `#3F51B5` | `#7986CB` | Indigo Blue to Soft Periwinkle Blue |
| **14** | `#FFEB3B` | `#FFC107` | Bright Canary Yellow to Golden Amber |
| **15** | `#FF9800` | `#FF5722` | Vivid Orange to Flame Red-Orange |
| **16** | `#CDDC39` | `#8BC34A` | Yellow-Lime to Fresh Grass Green |
| **17** | `#9E9E9E` | `#607D8B` | Neutral Gray to Cool Blue Gray |
| **18** | `#2196F3` | `#03A9F4` | Material Primary Blue to Light Cyan Blue |
| **19** | `#4CAF50` | `#8BC34A` | Vivid Green to Spring Bud Green |
| **20** | `#B71C1C` | `#F44336` | Dark Ruby Blood Red to Bright Fire Red |

---

## 3. Dark Surface Elevation Tokens

The app structures its window background hierarchy using dark surface tokens defined in `colors.xml`:
* **`dark_surface_2` (`#0D0D0D`)**: Primary backdrop and window canvas.
* **`dark_surface_1` (`#1A1731`)**: Dialog and bottom-sheet surface (deep purple-black tint).
* **`dark_surface_3` (`#201D27`)**: Intermediate card containers and item cards.
* **`dark_surface_4` (`#28365B`)**: Active click state / focused edit-text backgrounds.
* **`button_active_color` (`#FF8DFC63`)**: Fluorescent electric green used for active state toggles and battery percentages.
* **`subtitle_color` (`#7D889B`)**: Clean slate gray for secondary metadata, timestamps, and artist labels.
