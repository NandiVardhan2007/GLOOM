# Dynamic Island Pro – Visual Design & Reverse Engineering Knowledge Base

> **Reverse Engineered using REA (`morluto/rea`) Framework**  
> **Target Application**: Dynamic Island Pro - Notch Bar (`tr.com.eywin.dynamic.island.notch`, v2.2.0)

Welcome to the complete reverse-engineered visual design specification for Dynamic Island Pro. This repository contains the complete UI/UX blueprint, exact measurements, animation curves, state machines, color palettes, and component layouts recovered directly from the application's compiled package.

---

## 📚 Documentation Index

1. ### [Master Visual Design & Architecture Specification](DYNAMIC_ISLAND_VISUAL_DESIGN_SPEC.md)
   * **Executive Overview**: Philosophy, WindowManager overlay parameters (`TYPE_APPLICATION_OVERLAY`), dual-engine (XML + Jetpack Compose) rendering.
   * **Coordinate System & Geometry**: Default and configurable dimensions, top margins, horizontal shifts, and dynamic window resize hooks (`p84.java`).
   * **The 16 Island Visual States**: Complete lifecycle, trigger conditions, auto-dismiss delays, layout elements, and visual styling for each state (`Idle`, `Charging`, `Low Battery`, `Unlocked`, `FaceID`, `Earphone`, `Music`, `Incoming Call`, `Active Call`, `Notification`, `Silent Mode`, `Ring Mode`, `Timer`, `Airplane Mode`, `Ask AI`, `Assistive Touch`).
   * **Motion Design & Physics**: Precise durations, interpolators (`FastOutSlowIn`, `Overshoot(3.0f)`, `AccelerateDecelerate`, `Decelerate`), and spring dynamics.
   * **Gestures & Haptics**: Touch thresholds, swipe actions, long-press handlers, and vibration feedback codes.

2. ### [Themes, Gradients & Color Architecture](THEMES_AND_GRADIENTS_REFERENCE.md)
   * **Catalog of 51 Preset Themes**: Complete table from `q04.java` with hex color codes, linear/radial/sweep gradients, neon glowing borders, and stroke widths.
   * **The 20 Custom Gradient Pairs**: Full hex table from `GradientColors.java`.
   * **Surface & Elevation Tokens**: Dark mode surface hierarchy (`dark_surface_1` to `dark_surface_4`), accent greens (`#8DFC63`), and slate subtitles (`#7D889B`).

3. ### [Notch Pets & Micro-Animations Reference](NOTCH_PETS_AND_ANIMATIONS_REFERENCE.md)
   * **Overview & Architecture**: How interactive companion animations integrate with the camera cutout.
   * **The 39 Notch Pets & Characters**: Full inventory from `ng4.java` (Cat, Dog, Ghost, Slime, UFO, Pacman, Crewmate, Mario, Nyan Cat, etc.).
   * **Frame Timing & State Behaviors**: 2-frame retro walk/bounce loops and auto-fade state transitions.

4. ### [Layouts, Views & Component Architecture](LAYOUTS_AND_COMPONENTS_REFERENCE.md)
   * **XML Layout Hierarchy**: Structure of `fragment_notch.xml`, `item_medilayout.xml`, `notifiaction_item.xml`, `app_drawer_overlay.xml`, and `app_driwer_item.xml`.
   * **Component Tables**: Detailed view IDs, class types, dimensions, constraints, text sizes, and colors.
   * **Scrubber Design**: Technical specification of Apple-style `custom_seekbar_progress.xml` (0.75dp track, 2.0dp progress).

---

## 📁 Source Package Artifacts
* **Base Package**: [`base.apk`](file:///d:/Coding/JARVIS/tmp/base.apk) (~53.7 MB)
* **Architectural Configs**:
  * [`split_config.arm64_v8a.apk`](file:///d:/Coding/JARVIS/tmp/split_config.arm64_v8a.apk)
  * [`split_config.xxhdpi.apk`](file:///d:/Coding/JARVIS/tmp/split_config.xxhdpi.apk)
  * [`split_config.en.apk`](file:///d:/Coding/JARVIS/tmp/split_config.en.apk)
* **Decompiled Source Code**: Located under [`d:\Coding\JARVIS\tmp\decompiled`](file:///d:/Coding/JARVIS/tmp/decompiled)
