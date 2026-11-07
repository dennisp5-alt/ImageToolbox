# Smart Auto (sandbox)

One-tap auto edit for Image Toolbox's **Filters** screen. It analyses the photo and adds a
chain of *existing* Image Toolbox filters, so the render engine is untouched and every
step stays editable.

## Files

| File | Role |
|---|---|
| `SmartAutoAnalyser.kt` | `measure()` reads stats from a 512 px copy; `decide()` turns stats into `SmartAutoParams`. **Swap `decide()` for the LumaWorks Smart Auto logic.** |
| `SmartAutoMapper.kt` | `SmartAutoParams` → filters: Tone Curves (luma + R/B white balance + Hue‑vs‑Sat for greens/yellows), Highlights & Shadows, Vibrance. |
| `SmartAuto.kt` | Entry point `SmartAuto.run(bitmap)` + debug report text. |
| `SmartAutoControls.kt` | Button, "each image" batch toggle, debug card (stateless Compose). |

## Changes outside this module

- `settings.gradle.kts` – `include(":feature:smart-auto")`
- `feature/filters/build.gradle.kts` – `implementation(projects.feature.smartAuto)`
- `feature/filters/.../FiltersComponent.kt` – `applySmartAuto()`, state, per-image chain in `saveBitmaps()`
- `feature/filters/.../FiltersContentControls.kt` – one `SmartAutoControls(...)` call
- `.github/workflows/smart_auto_debug.yml` – builds the FOSS debug APK on push to `smart-auto`

## Behaviour

- Tapping Smart Auto again replaces the previous Smart Auto filters (unless you edited them).
- Batch + "Smart Auto each image" re-analyses every photo at save time. Sharing still uses
  the preview photo's chain.
