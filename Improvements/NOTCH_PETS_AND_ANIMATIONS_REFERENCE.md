# Dynamic Island Pro – Notch Pets & Micro-Animations Reference

> **Extracted from `tr.com.eywin.dynamic.island.notch` (`ng4.java`, `oj2.java`, `fragment_notch.xml`)**

---

## 1. Overview of Notch Pets

In addition to standard system indicators, Dynamic Island Pro introduces an interactive **Notch Pet / Island Companion** feature. These are 16-bit retro pixel-art characters that sit, walk, sleep, or emote directly on top of or adjacent to the camera cutout pill when the island is in its `Idle` state (`ep2.a`).

### Technical Implementation
* **Enum Registry**: Defined in `ng4.java`.
* **Renderer**: Rendered via Jetpack Compose composable `oj2.b(...)` inside the root container `bg2.b`.
* **Fallback View**: Legacy Lottie animation view (`LottieAnimationView` with `id/lottieView`) inside `fragment_notch.xml`.
* **Frame System**: Standard `frameCount = 2` (alternating sprite steps for idle walk/bounce cycles).
* **Storage Key**: `selected_animation` in `AppPreferences`.

---

## 2. Complete Inventory of the 39 Notch Pets & Characters

| # | Pet ID | Display Name | Category | Frame Count | Premium Required | Behavior / Visual Character |
|:---:|:---|:---|:---:|:---:|:---:|:---|
| 0 | `none` | **None** | Default | - | No | Standard clean black pill without any animations |
| 1 | `pixel_cat` | **Cat** | Pets | 2 | No | Walking white kitten with moving tail and ear twitch |
| 2 | `pixel_dog` | **Dog** | Pets | 2 | Yes | Golden puppy trotting along the notch boundary |
| 3 | `pixel_ghost` | **Ghost** | Spooky | 2 | Yes | Floating retro 8-bit ghost with waving bottom hem |
| 4 | `pixel_slime` | **Slime** | Fantasy | 2 | No | Bouncing, squishy gelatinous green cube |
| 5 | `pixel_ufo` | **UFO** | Sci-Fi | 2 | Yes | Hovering saucer with pulsing beam light |
| 6 | `pixel_heart` | **Heart** | Emoticons | 2 | Yes | Beating 8-bit red pixel heart with pulse scaling |
| 7 | `pixel_music` | **Music** | Media | 2 | No | Floating musical notes swaying to rhythmic beats |
| 8 | `pixel_car` | **Race Car** | Vehicles | 2 | No | High-speed pixel sports car with spinning wheels |
| 9 | `pixel_jet` | **Fighter Jet** | Vehicles | 2 | Yes | Supersonic fighter jet with pulsing afterburner flame |
| 10 | `pixel_sword` | **Epic Sword** | Gaming | 2 | Yes | Shimmering legendary RPG pixel sword |
| 11 | `pixel_flame` | **Fire** | Elements | 2 | Yes | Dancing campfire flame particle effect |
| 12 | `pixel_skull` | **Skull** | Spooky | 2 | Yes | Glowing skeleton skull with flashing eye sockets |
| 13 | `pixel_potion` | **Potion** | Fantasy | 2 | No | Magical bubbling health/mana flask |
| 14 | `pixel_rocket` | **Rocket** | Sci-Fi | 2 | No | Launching spacecraft with trailing exhaust smoke |
| 15 | `pixel_robot` | **Robot** | Sci-Fi | 2 | Yes | Retro tin robot with blinking antenna light |
| 16 | `pixel_ninja` | **Ninja** | Characters | 2 | No | Stealthy ninja jumping across the camera hole |
| 17 | `pixel_fox` | **Fox** | Animals | 2 | No | Prowling red fox with fluffy animated tail |
| 18 | `pixel_owl` | **Owl** | Animals | 2 | No | Wise nocturnal owl with rotating head glance |
| 19 | `pixel_wizard` | **Wizard** | Fantasy | 2 | No | Mystical wizard casting spells with glowing staff |
| 20 | `pixel_zombie` | **Zombie** | Spooky | 2 | No | Shuffling undead zombie with outstretched arms |
| 21 | `pixel_snail` | **Snail** | Animals | 2 | No | Slow crawling garden snail leaving tiny trail |
| 22 | `pixel_cloud` | **Cloud** | Nature | 2 | No | Drifting fluffy cloud with occasional rain droplet |
| 23 | `pixel_diamond` | **Diamond** | Items | 2 | No | Twinkling cut gemstone reflecting specular highlights |
| 24 | `pixel_controller`| **Gamer** | Gaming | 2 | No | Classic retro gamepad with flashing LED buttons |
| 25 | `pixel_nyancat` | **Rainbow Cat** | Memes | 2 | No | Iconic pop-tart cat trailing a colorful rainbow band |
| 26 | `pixel_eye` | **Watcher** | Sci-Fi | 2 | No | Floating ocular eye tracking user touch inputs |
| 27 | `pixel_dino` | **Runner** | Games | 2 | No | Chrome offline T-Rex running animation |
| 28 | `pixel_matrix` | **Code Rain** | Sci-Fi | 2 | Yes | Digital falling green matrix glyph cascade |
| 29 | `pixel_pacman` | **Pacman** | Gaming | 2 | No | Chomping yellow character chasing pellets |
| 30 | `pixel_crewmate`| **Crewmate** | Gaming | 2 | No | Among Us spacesuit astronaut running across notch |
| 31 | `pixel_mario` | **Plumber** | Gaming | 2 | No | Nostalgic red-capped plumber running and hopping |
| 32 | `pixel_pickaxe` | **Pickaxe** | Gaming | 2 | No | Minecraft-style mining pickaxe swinging downward |
| 33 | `pixel_pokeball`| **Pokeball** | Gaming | 2 | No | Red/white monster capture ball wobbling gently |
| 34 | `pixel_burger` | **Burger** | Food | 2 | No | Delicious stacked pixel cheeseburger |
| 35 | `pixel_alien` | **Invader** | Gaming | 2 | No | Classic Space Invaders pixel alien marching |
| 36 | `pixel_sun` | **Sun** | Nature | 2 | No | Happy spinning sunny sunbeam character |
| 37 | `pixel_coffee` | **Coffee** | Food | 2 | No | Steaming ceramic coffee mug with rising steam |
| 38 | `pixel_bat` | **Bat** | Animals | 2 | No | Flapping nocturnal bat hovering around the pill |

---

## 3. Placement & Sizing Within the Notch Bar

* **Bounding Container**: Embedded within a `32dp x 28dp` bounding box centered horizontally over the camera cutout.
* **Layout Rule**:
  ```xml
  <com.airbnb.lottie.LottieAnimationView
      android:id="@+id/lottieView"
      android:layout_width="40dp"
      android:layout_height="28dp"
      app:layout_constraintEnd_toEndOf="parent"
      app:layout_constraintStart_toStartOf="parent"
      app:layout_constraintTop_toBottomOf="@+id/cardView_notch"
      app:lottie_autoPlay="true"
      app:lottie_loop="true" />
  ```
* **State Behavior**:
  * **When Idle (`ep2.a`)**: The pet is fully visible and plays its 2-frame loop continuously.
  * **When an Island Event Triggers (e.g. Call, Battery, Music)**: The pet immediately fades out (`alpha -> 0.0f` in `150ms`) to give priority to system notification content, and smoothly fades back in once the island returns to idle.
