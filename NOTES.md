# Todo (dev.saikrishna.todo) — build notes

Milestone 1 = our motion spec (docs/motion-spec.md, not included) §10 steps 1–2:
theme and tokens, `Motion.kt`, `Haptics.kt`, `ProvideMotion`, home `LazyColumn` (stable keys + `animateItem`),
add-task composer (send-button morph + `imePadding`), new-task entrance (§6.3, §6.4).

## Setup (box-local, nothing global changed)

```bash
# REQUIRED in every shell before ./gradlew. There is no java on PATH, and the wrapper *launcher*
# needs JAVA_HOME to start at all. gradle.properties' org.gradle.java.home only picks the daemon JVM
# after the launcher is already running, so it is not enough on its own.
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Android/Sdk          # local.properties also has sdk.dir

# Android SDK (installed user-locally)
#   cmdline-tools: commandlinetools-linux-16111833_latest.zip -> $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"

# Build + tests. copyDebugApk / copyReleaseApk put the APKs at out/todo-debug.apk and out/todo-release.apk
cd Todo
./gradlew testDebugUnitTest
./gradlew assembleRelease          # R8 + resource shrinking, signed with the debug key (keep < 25 MB)
./gradlew assembleDebug            # optional
./gradlew --stop                   # free the daemon's RAM on the shared box
```

Memory: `org.gradle.jvmargs=-Xmx2g`, `org.gradle.workers.max=4`, and
`kotlin.compiler.execution.strategy=in-process` (Kotlin compiles inside the Gradle daemon, so there is one JVM instead of two).
A clean debug build takes about 1 minute; `assembleRelease` takes about 45 s.

## Versions chosen (all checked to resolve from Google Maven / Maven Central on 2026-10-20)

| Item | Version | Note |
|---|---|---|
| compileSdk / targetSdk | 37 (`platforms;android-37.0`) | 37.1 / 37.2 also exist; 37.0 is the base SDK the integer `compileSdk = 37` maps to |
| minSdk | 33 | as spec |
| build-tools | 37.0.0 | |
| Gradle (wrapper) | 9.8.2 | current stable |
| AGP | 9.4.1 | latest stable; **built-in Kotlin**, so `org.jetbrains.kotlin.android` is not applied |
| Kotlin (Compose compiler plugin / KGP) | 2.4.21 | |
| KSP | 2.3.12 | works with AGP 9 built-in Kotlin |
| Compose BOM | 2026.09.00 | → ui/foundation/animation **1.12.1**, material3 **1.4.0** (same as spec) |
| activity-compose | 1.13.0 | |
| core-ktx | 1.19.1 | |
| core-splashscreen | 1.2.0 | as spec |
| lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | |
| Room (runtime, ktx, compiler via KSP) | 2.8.5 | schema exported to `app/schemas/`; `fallbackToDestructiveMigration(dropAllTables = true)` until release |
| graphics-shapes | 1.1.0 | checkbox morph (milestone 2) |
| navigation3 (runtime, ui) | removed in v0.6.1 | replaced by our own stack navigator (`ui/nav/AppNav.kt`); navigationevent still arrives via activity |
| datastore-preferences | 1.2.1 | settings |
| Haze (haze, haze-blur) | 2.0.1 | glass header + composer |
| profileinstaller | 1.4.1 | with hand-written `app/src/main/baseline-prof.txt` |
| JUnit | 4.13.2 | |
| Robolectric / Roborazzi (test only) | 4.17 / 1.76.0 | screenshot tests, opt-in with `-Pshots` |
| JDK / bytecode target | 21 / Java 17 | |

Fonts (SIL OFL 1.1, from github.com/google/fonts; licences in `licenses/`):
Inter variable (`inter_variable.ttf`, weights set with `FontVariation`), Newsreader variable roman + italic (replaced Instrument Serif in milestone 4),
JetBrains Mono variable. Bundled under `app/src/main/res/font`.

## What is in the build

- `ui/theme/` — `AppColors` (our palette, light + dark, spec §2.1), `AppTypography` (§2.2), `Radii`/`Shapes`/`Spacing` (§2.3), `AppTheme` (Material 3 only for primitives, mapped onto our tokens).
- `ui/motion/Motion.kt` — `stiffnessFor`, `iosSpring`, `SpringToken`, `Springs` (Smooth, Glide, Snappy, Quick, Sheet, Gesture, Soft, Pop), `Easings`, `Durations`, `Tweens`, `Depth`, `LocalReducedMotion`, `rememberSystemAnimationsOff` (observes `ANIMATOR_DURATION_SCALE`), `ProvideMotion`.
- `ui/motion/SpringMath.kt` — pure maths (stiffness ↔ response, c → ζ, peak overshoot, 1 % settle time, closed-form step response), unit-tested.
- `ui/motion/Haptics.kt` — `Haptics` (tick / thump / rise; checks system haptic setting + in-app toggle; USAGE_TOUCH), `LocalHaptics`, `ProvideHaptics`.
- `ui/home/` — `HomeScreen`, `TodoViewModel` (was `HomeViewModel`), `TaskRow`, `Composer`, `EntranceTracker` (state as of milestone 1; see milestone 3 for the current layout).
  - LazyColumn: `key = id`, `contentType`, `animateItem(fadeIn 220 ms no delay, placement Snappy, fadeOut 150 ms)`.
  - New task: appended at the bottom; +24 dp → 0 and scale 0.94 → 1 from bottom-centre on Snappy (read only in `graphicsLayer`); `tick` haptic on submit; auto-scroll only when the new row would land under the composer (Smooth spring `animateScrollBy`, or `animateScrollToItem` if not laid out).
  - Composer: pinned bottom, `imePadding()` + `navigationBarsPadding()`, radius 24, bevel border; "+" / mic ↔ send (`AnimatedContent`, scale 0.4 ↔ 1 on Quick, 150 ms fade-in / 120 ms fade-out); multi-line up to 5 lines with `animateContentSize(Smooth)`; IME action Send; text fades out 120 ms then clears. List bottom padding follows the measured composer height (including the keyboard), so the last row stays visible.
  - Reduced motion: entrance snaps (fade only through `animateItem`), composer swaps crossfade without scale.
  - Empty state: italic Instrument Serif "Nothing yet." with fadeInSlow + 16 dp rise on Soft (basic version of §6.14).
- `data/` — Room `Task` entity, `TaskDao` (Flow, oldest first), `TodoDatabase`, `TaskRepository`.
- `MainActivity` — `installSplashScreen()` with 150 ms fade exit, `enableEdgeToEdge()`, `isNavigationBarContrastEnforced = false`, manifest `adjustResize` + `enableOnBackInvokedCallback="true"` + VIBRATE.
- Launcher icon: our own vector (iris disc with a white check).

## Milestone 2: complete task (§6.5) and collapse with reflow (§6.9)

- **Persistence:** the `done` and `completed_at` columns already existed, so the schema is still version 1 and needs no migration. Destructive fallback is enabled for future schema bumps.
  `TaskDao.markDone(id, at)` / `markOpen(id)` update only those columns. Re-opening keeps `completed_at` as "last completed", which keeps the Done order stable while a row is leaving.
- **Optimistic toggle:** `HomeViewModel.setDone` puts a `DoneOverride` over the Room flow (`applyOverrides`), so the checkbox reacts on the tap frame and not when Room re-emits. The override is ignored once the database agrees.
- **`CheckCircle`:** 48 dp touch target with a 24 dp mark. The fill grows on **Pop** (Quick under reduced motion) while a graphics-shapes `Morph(circle(8), rectangle(1.7, 1.7, CornerRounding(0.5, 0.6)))` goes circle → squircle.
  It uses `Morph.toPath(progress, android.graphics.Path)`, then `Matrix` scale/translate, and is drawn through a remembered `asComposePath()` wrapper, so nothing is allocated per frame.
  The ring morphs with the fill, and the fill radius follows the raw spring, so Pop's overshoot shows. The check draws from 40 ms over 240 ms. Unchecking reverses: check in 120 ms, fill on Quick.
  Semantics: `Role.Checkbox`, state "Completed" / "Not completed", label = task title.
- **`StrikeText`:** strike drawn left→right across all wrapped lines (60 ms delay, 280 ms emphasized). The title colour goes to ink-38 % on `select` 240 ms. Both values are read only in the draw phase (`BasicText` + `ColorProducer`).
- **Leave flow (`LeaveController` + `buildSections`):**
  - Tap: `thump` (or `tick` when un-completing). The row stays in place for the **700 ms dwell**, stretched by the animator scale when > 1 and never shortened.
  - Tapping again during the dwell cancels the change, so the row stays and the animation reverses.
  - After the dwell, `CollapsingItem` plays §6.9: `shrinkVertically(Smooth, towards top)` + `fadeOut(150)` + `scaleOut(0.97, Smooth)`. The task is dropped from the section only after the collapse finishes, so nothing jumps.
  - The 8 dp row gap sits inside the collapsing item, so it collapses too.
  - Timers live in the controller, not the row. A row that is off-screen when its dwell ends is removed without animating, and one disposed mid-collapse finishes immediately.
- **Done section (minimal):** a collapsible "Done · N" header below Today (collapsed by default; chevron rotates on Smooth). The count slides vertically on Glide with a crossfade and goes up by 1 when the dwell ends.
  Expanded, it lists done tasks newest-first. Un-checking one plays the same dwell and collapse, and the task fades back into Today at its creation-order position.
  Item keys are `t-<id>` / `d-<id>`, so a task moving between sections gets fresh row state.
- **All clear (part of §6.14.2):** when every task is done, an "All clear." item with "N done today" rises in on Soft + fadeInSlow(150). Completing the last open task fires `thump`, then `rise` 250 ms later.
- **Reduced motion / animations off:** `ProvideMotion` now also provides `LocalAnimatorScale`. `LocalReducedMotion` only selects variants (Pop → Quick, collapse without scale, crossfades); it never gates state changes or writes.
  At animator scale 0, Compose finishes every animation on the next frame. The collapse completion callback and the composer's text clear still fire because they wait on animation *end*, which arrives at once. The dwell stays 700 ms, so the cancel window still exists.

## Milestone 3: pages, swipe/undo, detail sheet, navigation, search, settings, glass (steps 4, 5, 6, 8)

**Model (Room v2, destructive fallback):** `Task` gains `notes`, `due_day` (epoch day, nullable) and `priority` (0 none / 1 low / 2 high; `Task.priority` is an extension because Room rejects computed entity properties). The composer is unchanged (title only); due date, notes and priority are edited in the detail sheet.

**Pages (`ui/home/Pages.kt`, pure + tested):** `buildPages` → Today / Upcoming / Done.
- Today: open tasks due today, overdue, or with no date, in creation order. Empty → "Nothing yet." (no tasks at all) or "All clear. N done today".
- Upcoming: open with a future date, grouped under day headers ("Tomorrow", weekday names within a week, else "Wed, 28 Oct"), ascending.
- Done: grouped by completion day, newest first, with a "Clear done · N" row (deletes them with one undo).
- Rows show a secondary line: due label ("Overdue · Yesterday" with a warning dot), High/Low priority, "Notes". On complete it fades to 50 % on `select` (the 6.5 step that was missing).
- Leaving rows (dwell + collapse) stay on the page they are leaving from, so completing a row on Today, or reopening one on Done, plays 6.5 → 6.9 exactly as before. The temporary bottom Done section is gone.

**Filter pill (6.2):** `SegmentedPill` (generic, reused by settings and the sheet). The ink pill's x/width are `Animatable`s on Glide, drawn in `drawBehind`; label colours are `ColorProducer`s; tick haptic. The page content is an `AnimatedContent` with a directional slide (±1/6 width on Smooth + fade); reduced motion → crossfade. Each page keeps its own `LazyListState` (scroll is kept per page). The header title crossfades.

**Swipe to delete + undo (6.6):** M3 1.4 `SwipeToDismissBox` (end → start only, 35 % positional threshold, `onDismiss`). A danger capsule grows from the live offset in `drawBehind`, the trash icon fades/scales in `graphicsLayer`, a tick fires at each threshold crossing. Dismiss → the row collapses (6.9) → `vm.delete` (optimistic hide + DB delete under a mutex) → ink undo bar slides up above the composer on Smooth for 4 s. Undo re-inserts the rows with their ids and replays the new-task entrance. Also exposed as an accessibility "Delete" action.

**Detail sheet (6.10, `ui/sheet/SheetHost.kt` + `TaskDetailSheet.kt`):** tap a row →
- sheet rises on the Sheet spring; background scales to 0.94 with 24 dp corners and a 12 % dim (all in `graphicsLayer`/`drawWithContent`);
- drag-to-dismiss from the grabber, or by pulling the content down when it is scrolled to the top (nested scroll); dismisses past 25 % of the height or 1000 dp/s, otherwise springs back on Gesture with the fling velocity;
- predictive back via navigationevent `NavigationBackHandler`: the sheet follows the gesture progress and springs back on cancel; tap outside closes;
- contents: completion checkbox, editable title (serif), notes, due chips (No date / Today / Tomorrow / Next week = next Monday / Pick… → M3 `DatePickerDialog`), priority pill, delete. Chips and priority save immediately; title/notes save when the sheet closes (blank titles are ignored).
- 6.8 inline expand: skipped (the sheet covers it).

**Navigation (Nav3 1.2.0, 6.11):** `ui/nav/AppNav.kt`, back stack of `enum Screen { Home, Search, Settings, Licenses }` saved by name.
- Push: new screen in from +100 % on Smooth; the one underneath slides to −25 %, scales to 0.96, fades to 0.9. Pop and predictive back are the exact reverse with the leaving screen kept on top (`targetContentZIndex = -1`). Reduced motion → 150 ms crossfades.
- Header icons on Home: Search, Settings.

**Search (6.12):** autofocused field, live filter over title + notes (case-insensitive; open first, then done newest first), results animate with `animateItem` (Snappy placement). Rows can be completed in place; tapping one pops back to Home and opens its sheet (`vm.requestOpen`).

**Settings (DataStore):** Reduce motion, Haptics (our `MotionSwitch`: thumb on Quick read in layout, track colour in draw), Theme System / Light / Dark (pill), About: version, Licences screen (OFL texts from `assets/licenses`, expandable on Smooth; Apache-2.0 libraries listed). Settings feed `ProvideMotion`, `ProvideHaptics` and `AppTheme`; status/nav bar icon style follows the theme. The splash stays until settings are read, so the first frame has the right theme.

**Glass (Haze 2.0.1):** header and composer use `Modifier.glassChrome()` = `hazeBlur` (20 dp, noise 0.06, `glassChrome` tint) over a `hazeSource` on the list. Reduced motion or no source → the opaque `glassChromeFallback`. The list's top/bottom padding is a `PaddingValues` that reads the measured chrome heights during measure only (`ChromePadding`), so chrome size changes never recompose the list.

**Smoothness work:**
- Animated values are read only in draw/layout (graphicsLayer, drawBehind, offset {}, ColorProducer); no allocation in draw paths (e.g. swipe offset read with a try/catch helper instead of `runCatching`).
- Stable keys (`r-<id>`, `h-<day>`, …) and `contentType`; models `@Immutable`; `RowActions` is a `@Stable` holder remembered once (its callbacks read the latest data via `rememberUpdatedState`), so rows do not recompose when Home does.
- Compose compiler reports enabled (`app/build/compose_compiler/`) with `compose-stability.conf` (`java.time.LocalDate`, `ZoneId`). Result: every row-path composable (`TaskListPage`, `TaskListRow`, `SwipeToDeleteRow`, `TaskRow`, `MetaLine`, `CheckCircle`, `SegmentedPill`, `UndoBar`, `SheetHost`, `TaskDetailSheet`) is restartable + skippable with stable params; the only unstable classes are the DAO/DB/VM/Activity/Haptics, which are never row params.
- R8 + resource shrinking release; profileinstaller with `baseline-prof.txt` (app code, Compose runtime/ui/foundation/animation, Nav3, navigationevent, Room, graphics-shapes, Haze, coroutines) → `assets/dexopt/baseline.prof` in the APK.
- Everything interruptible: Animatables/`animateTo` retarget mid-flight (pill, sheet, switch, swipe), dwell can be cancelled, back gestures can be cancelled.

## Milestone 4: reference-matched pill and retheme (v0.4.0)

**How it was measured.** Frames from the reference recordings Sai provided (720 × 1564) were pulled with ffmpeg and measured pixel by pixel with PIL. The scale is **1.75 px/dp**, which makes the measured 20 dp screen margin, 12 dp card gap and 24 dp card radius come out on a clean 4/8/12/20/24 grid. There are no published pill or segment dimensions, so every measurement below comes from the frames. The measurement crops were kept locally and are not in the repo.

| Measured (reference) | px | dp | Ours |
|---|---|---|---|
| Pill track height | 86 | 49 | **48 dp** (`PillSpec.Height`) |
| Indicator inset | 4 | 2.3 | **2 dp** |
| Indicator / segment width | 134 (equal thirds) | 77 | equal segments, **max(76 dp, label + 28 dp)**, shrunk evenly to fit |
| Round control beside pill | 74 | 42 | **44 dp** glass circles (touch size) |
| Label | cap height 17 px | ≈ 14 sp | **Inter 14 sp, weight 560, −0.005 em** |
| Unselected label | ink at ≈ 50 % | | `inkSecondary` (ink 50 %) |
| Track fill | ≈ 58 % white over sky + blur, faint light rim | | white 62 % + 1 dp white-55 % rim; dark: slate 62 % + white-9 % rim |
| Selected segment | solid ink capsule, light label | | ink capsule, `onInk` label |
| Cards | 70 % white, r ≈ 23 dp, 20 dp margin, 12 dp gap | | rows white 70 %, r 22 dp, 20 dp margin, 10 dp gap |
| Composer | 49 dp capsule, ≈ 90 % light fill, inset more than cards | | 48 dp capsule, 90 % fill + blur, 24 dp side margins, + and mic on the right |
| Ink buttons | 51 dp capsule, light text | | undo bar 48 dp ink capsule; chips 36 dp capsules |

**The pill (`SegmentedPill`, style `Floating` / `Inline`):**
- Custom `Layout` with equal-width segments. The indicator is an ink capsule drawn in `drawBehind` from one `Animatable` x on the **Quick** spring (0.30 s, ζ 0.86); reduced motion uses a short tween. The reference slide is fast with no visible overshoot.
- Label treatment copies the reference *technique*: a label is light exactly where the capsule covers it, even mid-slide. Labels are drawn once, opaque, in an offscreen layer, then recoloured with `BlendMode.SrcIn`: `onInk` inside the indicator rect and ink 50 % outside it. The slide only touches the draw phase and never recomposes.
- Floating = glass track (translucent fill + rim) at 48 dp. Inline (sheet priority, settings theme) = ink-5 % track at 40 dp.
- Home header follows the reference layout: glass circle (search) · pill · glass circle (settings), floating with no bar. One progressive Haze blur (`glassEdgeTop`) covers the header: full strength behind the controls, fading to none over the 28 dp below them. That replaces the old opaque glass bar and its hairline. The controls add no blur layers of their own.

**Retheme (all hex values our own, not copied):**
- Ink `#1B1C20` at 100 / 50 / 30 / 14 / 7 / 5 %; light on ink `#F7F8FA`.
- New static **sky backdrop** (`skyBackdrop`): our gradient `#66748F → #A3B0C8 → #DAE2EE → #F1F4F9` plus two soft radial glows. It is drawn once through `drawWithCache` (no shader, no animation) and sits inside the Haze source, so the glass picks up its tint.
- Dark: `#070A11 → #111723 → #1E2636`, slate glass, light ink `#F1F2F5`, inverted (light) selection capsule.
- Accent is a warm ember `#E9562B` (dark `#FF7247`), used only for the cursor, the date-picker "Set" and the undo action. Selection, checkbox fill and switches are ink, which follows the reference's monochrome look.
- The display serif is now **Newsreader** (OFL; variable wght + opsz, used at weight 500, opsz 28), at 28/34 sp. It is closer to the reference's sturdy headline serif than Instrument Serif, which was removed. Licence is in `licenses/` and `assets/licenses/`.
- Body is 15/20 sp, row titles 16/22 sp regular, labels 14 sp at weight 560.
- Pages get a serif heading item: "Tuesday, 20 October" with "5 things to do", "Coming up" with "N planned", and "Done". Empty and All clear messages use the roman serif.
- Detail sheet: chips are capsules (`PillChip`, 36 dp), priority uses the inline pill, notes field radius 20, surface 98.5 % solid.
- Settings and Licences use glass cards (white 70 %, r 24, rim) over the sky. Search uses a 48 dp glass field.
- Clear done is a glass capsule.
- Switch: ink track when on, light thumb.

**Visual check (no device):** `HomeScreenshotTest` renders the real screens (real ViewModel, in-memory Room, Robolectric native graphics, SDK 36, 411 × 891 dp at 420 dpi) to `out/shots/`:
- `home-light.png`, `home-dark.png`, `home-light-reduced-motion.png`
- `upcoming-light.png` (pill moved by a real click)
- `sheet-light.png`, `sheet-dark.png`, `settings-light.png`

Run them with `./gradlew testDebugUnitTest -Pshots`. Without `-Pshots` they are excluded, so the normal test run stays light. JDK 21 needs `--add-exports java.base/jdk.internal.access` for Robolectric's SDK 36 interceptor (set in `unitTests.all`). Status-bar insets are 0 under Robolectric, so the header sits at the very top in the shots.

## Milestone 5: smoothness, pill flicker, composer contrast, glass tokens, floating panel (v0.5.0)

From Sai's on-phone test of v0.4.0 (screen recordings, frame by frame).

**Box note.** At the start of this session the box had lost `out/` (incl. the local measurement crops and the v0.4 shots), `~/.gradle/caches` and `/tmp`. Sources, SDK and JDK were intact. Gradle re-downloaded everything; the measurement crops were not regenerated (they are not needed for the build; the source recordings are still available).

### 1. Pill flicker (fixed)
Root cause: `SegmentedPill` kept the indicator in **pixels**, starting at `-1` ("unknown"). It was only positioned by a `LaunchedEffect(index, segW)`, where `segW` is written during measure, and the capsule was skipped while `x < 0`. On a Nav3 pop, Home's entry is composed from scratch, so the capsule depended on measure, then a state write, then recomposition, then effect dispatch. Any delay in that chain left the capsule missing (on the device: for the whole pop) and then popping in. Under reduced motion or "Remove animations" there is nothing to hide it.

Fix: the indicator lives in **segment units**, initialised to the current index (`Animatable(index)`). It is drawn at `x × segW`, and `segW` is measured in the same frame before draw, so the capsule is in the very first frame the pill draws. Only an index change animates (Quick). No effect or measurement-dependent init remains. `filter` is already `rememberSaveable`, so the restored index is right on frame 1. Test: `PillFirstFrameTest` (clock paused, capsule pixel under the selected segment and not under the others).

### 2. Smoothness (transition stutter)
Audit and changes:
- **Header blur was Haze *progressive* blur.** It renders several blur layers per frame. It is now **one blur pass with a gradient mask** (`mask(verticalGradient)`), radius capped at **16 dp** (was 20), `HazePerformanceMode.Performance` (half-resolution input), noise 0.
- **Blur during navigation.** Each NavEntry now provides `LocalBlurFrozen = transition.currentState != targetState` from `LocalNavAnimatedContentScope`. While a scene is entering/exiting (push, pop, predictive back seek), its live blurs switch to Haze's scrim path (`blurEnabled(false)`, flat tint: no capture, no RenderEffect). They come back when the scene settles. Search and Settings have no live blur at all.
- **Push/pop content lightened.** The underlying screen's `fadeOut(0.9)`/`fadeIn(0.9)` is dropped: alpha < 1 forced a full-screen offscreen layer every frame. Kept slide −25 % + scale 0.96 (pure RenderNode transforms).
- **Search autofocus** waits for the push to settle (`LocalNavMoving`), so the IME no longer animates in (re-laying out the screen per frame) on top of the slide.
- **Tab switch (Today↔Upcoming) mid-phase drops.** The page fade was an `AnimatedContent` fadeIn/Out, i.e. a full-screen offscreen layer per page while both pages are visible (exactly the middle phase). It is now `transition.animateFloat` applied as `graphicsLayer { alpha; compositingStrategy = ModulateAlpha }` (no offscreen buffer). The spatial slide is still Smooth.
- **Blur radius is never animated anywhere.** The panel backdrop fades by its layer alpha only.
- **Fonts** are preloaded behind the splash (`FontFamilyResolver.preload` for Inter, Newsreader, JetBrains Mono), so the first serif heading on a page switch doesn't load a font mid-animation.
- **Baseline profile** already covers `dev/saikrishna/todo/**`, compose ui/foundation/animation, navigation3, navigationevent and haze (wildcards), so the new panel code is included.
- **One spring set**: Smooth for push/pop, tab slide and the panel; Quick for the pill; Gesture for drag/back settle; reduced = `Tweens.reduced()`.

### 3. Light-mode composer
Restrained liquid glass:
- Light live blur (16 dp, half-res) under a **white 50 % tint** (was #F2F3F6 at 90 %).
- **1 dp gradient rim** (white 95 % top → slate 16 % bottom).
- **Soft diffuse shadow** (`GlassLevel.Mid`).
- The sky now **deepens at the bottom** (`skyDeep` #B7C2D6 light / #151B29 dark, new last gradient stop), so the composer reads as a separate floating object. See `out/shots/composer-light.png`.

### 4. Glass tokens
New colours: `glassFill`, `glassRimTop`, `glassRimBottom`, `glassShadow`, `glassTint`, `skyDeep`. Helpers in `Glass.kt`: `glassShadow(shape, GlassLevel)`, `glassRim`, `glassFill`, `glassSurface`, `glassBlurFill`.
- **Shadow.** Stacked expanding outline rings clipped *outside* the shape, so translucent fills never show a dark core. It is cached per size and uses no blur. Levels: Low (cards, pill track, round buttons, panel buttons), Mid (composer, undo bar), High-equivalent drawn by the panel.
- **Cards** (task rows, settings and licence cards) use translucent fill + rim + shadow and have **no live blur**.
- **Live blur only** on the header edge, the composer and the panel backdrop.

### 5. Floating task panel (replaces the bottom sheet)
`ui/panel/TaskPanel.kt` + `TaskPanelContent.kt`; `ui/sheet/*` deleted.
- **Backdrop.** One full-screen Haze blur (fixed 16 dp) of the whole home layer (a second `hazeSource` wraps content + header + composer). It fades in by its layer alpha. A **separate flat dim layer** sits above it (18 % light / 40 % dark). Neither depends on reduced motion or on any background transform.
  - Why v0.4's background treatment "didn't show" on the phone: the sheet was 92 % tall, so the 6 % scale + 12 % dim were only visible in the status-bar strip.
  - Also, the scale was skipped whenever reduced motion was on, which includes system "Remove animations" (animator scale 0).
  - No background scale any more.
- **Container transform.**
  - Rows register their `LayoutCoordinates` in `PanelAnchors` (plain map, no state). The panel reads the card rect in host coordinates when opening and again when closing (current bounds).
  - One `progress` on Smooth. Surface rect, corner radius (22 → 24 dp), fill (card → panel) and shadow are lerped in the **draw phase** with one reusable `Path`.
  - Content is laid out once at panel size and translated. Its first row mirrors `TaskRow` (check circle, paddings, title style, meta line), so at p = 0 the panel *is* the card.
  - The lifted card is hidden via `graphicsLayer` alpha.
- **Placement.** Inset 16 dp from the screen edges. Top aligned to the card, clamped below the status bar and above IME/nav bar. The insets are read in the layout phase, so it rides the keyboard without recomposition.
- **Dismiss.**
  - Tap outside.
  - Back. Predictive back pulls it 40 % toward the card and scales it to 0.96; the backdrop eases; cancel springs back on Gesture.
  - Swipe down, via nested scroll at the top of the content: > 110 dp or > 1000 dp/s dismisses.
  - Close flies back into the card's current bounds. A deleted or off-screen card fades and scales in place.
- **Contents.** Editable title + meta line, notes, due chips (+ date picker), priority pill, "Mark done"/"Mark not done", Delete (closes in place, then the row collapses).
- **Reduced motion.** Crossfade at the final position; blur + dim still shown.
- Tests: `TaskPanelTest` (open from a card tap; real tap outside closes; reduced motion opens/closes; delete removes the task).

### Screenshots (`./gradlew testDebugUnitTest -Pshots` → `out/shots/`)
`home-light`, `home-dark`, `panel-light`, `panel-dark` (opened by a real tap on "Draft the Q4 plan"), `panel-opening-light` (≈260 ms into the container transform), `composer-light` (bottom 190 dp crop), `home-light-reduced-motion`, `upcoming-light`, `settings-light`.

### Remaining risks
- Robolectric renders the Haze blur, but frame timing can only be judged on a device; the perf changes are reasoned from the recordings, not measured (no KVM emulator here).
- The blur→tint swap at the start/end of a push/pop is a small tone change in the header/composer of the moving scene; on device it should be hidden by the motion but is worth a look.
- The ring shadow is an approximation of a Gaussian shadow (visible banding is unlikely at 6–12 rings but not verified on an OLED panel).
- The panel's content width (screen − 32 dp) differs from the card's (screen − 40 dp) by 8 dp; at p = 0 a long title can wrap at a slightly different point under the clip.
- Input injected with a paused test clock is not delivered, so the mid-transform shot opens through the app's open request (same anchor path) rather than a tap.

## Milestone 6: natural-language capture, reminders, repeats, snooze, voice (v0.6.0 / code 6)

Same rules as before: theme/glass tokens only, one spring set, motion read in the draw/layout phase, reduced-motion variants that only swap the animation, haptics only through `Haptics`, no third-party material.

### 1. Natural-language capture (`domain/QuickParse.kt`, `ui/home/Composer.kt`)
- Our own pure-Kotlin parser, no network. `QuickParse.parse(text, now, ignored)` returns the cleaned title plus `dueDay`, `dueMinute`, `priority`, `repeat` and the recognised `tokens` (character spans).
- Dates: today, tonight (8 pm unless a time is given), tomorrow/tmrw/tmr, day after tomorrow, weekday names (with "next fri" meaning next week's Friday), next week (Monday), next month (the 1st), in N days/weeks/months, "24 Oct", "Oct 24", "24/10", "24/10/26", "24-10-2026". India locale: DD/MM. A date without a year that has already passed rolls to next year. Optional prefixes: on/by/due.
- Times: 4pm, 4:30 pm, 16:30, noon, "at 9" (1–7 means pm, 8–23 is read as is). A time with no date means today if it is still ahead, else tomorrow.
- Priority: `!high` / `!hi` / `!h` / `!!` → High, `!low` / `!lo` / `!l` → Low.
- Repeats: every day/daily, every N days, every weekday, every Monday / every Mon and Thu / on mondays, every week/weekly, every 2 weeks / every other week [on Fri], monthly / every month on the 31st / every 15th, and "every 10 days after done" / "3 days after completion".
- Rules run in a fixed order (repeat → date → time → priority). Spans may not overlap, and only the first match of each kind applies. The parsed words are removed from the saved title; if nothing would be left, the raw text is kept.
- UI: each recognised span gets a soft accent capsule drawn behind the text, one per line, from the live `TextLayoutResult` in `drawBehind` (draw phase only). A row of small glass chips (`glassSurface` Low, 30 dp, icon + label + ×) rises above the capsule (expand on Smooth, each chip pops in on Quick; fade only under reduced motion). Tapping a chip adds its key to `ignored`: that interpretation is dropped but the words stay in the title. Plays a `tick` haptic. Ignored keys reset when the field is cleared or the task is submitted.
- After adding, the composer switches to the page the task lands on (Today, or Upcoming for a future date).

### 2. Reminders (`reminders/`)
- New columns `due_minute` and `repeat_rule`. The panel has a Time row: No time · 9 am · 1 pm · 6 pm · Pick… (M3 TimePicker). A custom time is shown first. Setting a time on an undated task also gives it a date (today if the time is still ahead, else tomorrow).
- `TodoApp` watches the task flow and diffs id → scheduled-at, so only tasks whose time changed touch AlarmManager. Removed and done tasks have their alarms cancelled.
- Scheduling: `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` is true, otherwise `setAndAllowWhileIdle` (inexact; may arrive a few minutes late).
- Notification ("reminders" channel, high importance, our own ring+check status icon): title = task, text = "Due 4 pm · notes". Actions: **Done** (completes the task; a repeating task spawns its next occurrence, which is then scheduled), **Snooze 1h**, **Tomorrow** (same time tomorrow). Tapping the notification opens the task's panel.
- `RescheduleReceiver` resyncs every timed task on BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED and SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.
- Settings has a new **Reminders** group: notification status (opens the app's notification settings) and exact timing status (opens the "Alarms & reminders" special access screen). Both are re-read on resume.

### 3. Repeating tasks (`domain/RepeatRule.kt`)
- Rules: Daily(every N), Weekdays, Weekly(days, every N weeks), Monthly(day; clamped to the month's end), AfterCompletion(N days). Stored as a compact string (`D:1`, `WD`, `W:2:1,4`, `M:31`, `A:3`). Decoding is lenient: garbage → no repeat.
- `nextOccurrence(rule, due, completedOn)` returns the first scheduled day strictly after max(due, completed). Daily keeps its phase, weekly counts N-week cycles from the due week, monthly clamps (31st → 30 Nov → 31 Dec → 28/29 Feb), AfterCompletion = completed + N.
- Completing a repeating task, in one Room transaction, inserts the next occurrence (same title, notes, priority, time, rule) and clears the rule on the completed copy, so un-completing it can't create duplicates. The new row gets the normal new-task entrance on its page. Rows show a small repeat glyph in the meta line.
- Panel picker: Never · Daily · Weekdays · Weekly · Monthly · After done, plus a detail line: an interval stepper, weekday toggles for Weekly, a day-of-month stepper for Monthly, and a plain-English summary ("Every Mon, Thu"). Picking a rule on an undated task gives it its first date.

### 4. Swipe right to snooze
- `SwipeToDeleteRow` now dismisses in both directions. Swiping right reveals a quiet glass capsule (glassFill + gradient rim) with a clock that scales 0.7 → 1 at the 35 % threshold. A `tick` plays at each threshold crossing, in both directions.
- Releasing past the threshold snaps the card back and opens the snooze picker in the **same floating panel** as the task details (container transform from the row; the panel waits one frame so it measures the settled card). Options are listed in time order: Later today (+3 h, rounded up to 5 min, only while still today), Tonight 8 pm (only before 7 pm), Tomorrow 9 am, Next week Mon 9 am, and Pick… (date, then time).
- If the new time moves the task off its page (e.g. Today → Upcoming), the row runs the same dwell → collapse as completing a task.
- Accessibility: custom actions "Snooze" and "Delete" on each row.

### 5. Voice capture (`ui/home/VoiceInput.kt`)
- Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` when `isOnDeviceRecognitionAvailable`, else the default service. If the on-device recognizer fails with a language, client or server error, it retries once on the default service. The language is en-IN, with partial results on.
- RECORD_AUDIO is requested on the first mic tap. The transcript streams into the field after any text already typed, so chips and highlights update live. The user confirms with send (stop ends listening; send also stops it).
- While listening: the placeholder reads "Listening…", and the mic becomes an ink stop button with a soft accent ring. The ring's radius and alpha follow the smoothed input level (onRmsChanged → Animatable on Quick), drawn in `drawBehind`. Under reduced motion the ring is static.
- No recognizer, permission denied, or a network-only recognizer offline → a short inline hint in the placeholder for 3 s; nothing crashes. NO_MATCH and silence just stop quietly.

### Room migration (data preserved)
- Schema 2 → 3 is a real `Migration` (two `ALTER TABLE … ADD COLUMN`s). Destructive fallback is kept only from v1 (pre-release builds).
- `MigrationTest` builds a real v2 database from `app/schemas/.../2.json`, inserts rows, opens it through `TodoDatabase.build()` and checks that every row and field is identical, the new columns are null and autoincrement continues.

### Permissions behaviour
- **POST_NOTIFICATIONS**: never asked at launch. Asked once, the first time a timed task is created (from the composer, the panel's time row, or a snooze), and only if it isn't already granted. A DataStore flag stops a second prompt. If it's denied, reminders are still scheduled but don't show; Settings → Reminders shows "Off" and links to the system page.
- **SCHEDULE_EXACT_ALARM** (special access, user-granted; denied by default on Android 14+): not requested in a dialog. Without it, reminders use inexact `setAndAllowWhileIdle`. Settings links to the toggle, and the permission-state broadcast reschedules everything. **USE_EXACT_ALARM is not declared**: Play restricts it to alarm/calendar apps.
- **RECORD_AUDIO**: asked on the first mic tap only.
- **RECEIVE_BOOT_COMPLETED**: install-time.

### Simplified
- Reminders fire at the due time only (no "10 min before" offset).
- One interpretation per kind (the first date/time/priority/repeat wins). "at 1"–"at 7" means pm. A dashed date needs a year (24-10-2026) so it isn't confused with ranges.
- Un-completing a completed repeating task doesn't delete the occurrence it spawned (the rule has already moved to the new one).
- The snooze picker shows the quick options; "Pick…" uses the stock M3 date and time dialogs.

### Screenshots (`out/shots/`)
`composer-chips-light.png`, `composer-chips-dark.png` (typed "Dentist tomorrow at 4pm !high every week"), `snooze-picker-light.png`, `panel-repeat-time-light.png` (4:30 pm, every Mon & Thu), plus the earlier set.

### Risks
- Exact timing depends on the user granting "Alarms & reminders"; otherwise Doze can delay reminders by minutes.
- OEM battery managers (some Xiaomi/Oppo/Vivo builds) can drop alarms or the boot broadcast for apps that aren't allowlisted.
- On-device speech needs a downloaded en-IN or en language pack; otherwise we fall back to the default (often network) recognizer, which needs a connection.
- The parser is English-only and rule-based: unusual phrasings fall through as plain title text. Mistakes are visible and reversible via the chips.
- Voice and real notification delivery aren't covered by Robolectric tests; they need a device check.

## Milestone 6.1: push/pop transitions, proven frame by frame (v0.6.1 / code 7)

Sai's recording of v0.6.0 showed four problems: (A) a black band at the top while Home scaled down on push; (B) a back-arrow pop with Home missing, then popping in 0.5–1.5 s later; (C) an end snap after the predictive-back pop; (D) a possible blur → tint tone pop.

### Root causes
- **B (Home missing on arrow pop).** Navigation 3's `NavDisplay` *disposes* the covered entry once a push settles, so Home was composed from scratch on the first frame of every pop. Measured on v0.6.0 with the new test: `Home enters=1, disposals=1` per push+pop cycle, and Home **not composed or placed on pop frames 0 and 1**. On a phone, Home's first composition (lists, Haze, text layout, Room-backed state) is far heavier than in Robolectric, which matches the 0.5–1.5 s gap. Search is worse because the IME closes in the same window and Home also re-measures for it. The predictive gesture "worked" only because NavDisplay composes the target as soon as the gesture starts, so Home was already there when the user lifted their finger.
- **C/D (end snap).** The moving scene's live blur was swapped for a flat tint through a CompositionLocal derived from the transition state. The swap back lands one recomposition *after* the motion settles. Measured on v0.6.0: the first frame at rest differed from the settled frame by **29.2** (mean |Δ| on 0–255, worst 1/64 region) on **every** pop (arrow and gesture), then 0. That is a visible one-frame tone and blur pop at the end, which is the "micro-jump". The swap was also a tone change at the start of each transition.
- **A (black band).** Not reproduced in Robolectric: the window background is `@color/paper`, and no frame of v0.6.0 had a dark band. The only geometry that exposed anything behind the scene was the 0.96 scale on the covered screen (about 2 % at the top). The likely device mechanism is Haze's header blur (and its frozen-tint mask layer) drawn inside a *scaled* parent layer: Haze places its blur from on-screen positions that include the scale, so the top strip samples outside the recorded source. **Unverified without a device.**

### What changed
- `ui/nav/AppNav.kt`: our own small stack navigator replaces NavDisplay (Navigation 3 removed from the build).
  - Every screen on the stack stays composed. Covered screens get `alpha = 0` (not drawn), cleared semantics, and no touches, so Home is never disposed and is ready on frame 0 of any pop.
  - A single `pop()` serves the back arrow, system back and the predictive-back commit (`PredictiveBackHandler`). It hides the IME and clears focus in the same frame, never waiting for the keyboard.
  - Motion is a single float `x` set synchronously and animated on `Springs.Smooth` (reduced motion: the same `x` drives a crossfade). It is read only in `graphicsLayer` blocks: top at `x·w`, the screen under it at `−¼·w·(1−x)`.
  - **No scale anywhere**, so nothing behind a scene can be exposed. End values are exact (x is set to exactly 0 or 1 when the animation ends), so the last animated frame is the settled frame.
  - A root backdrop (paper + sky) is drawn behind all scenes and is never part of a moving layer.
- **No blur → tint swap any more.** Scenes move by translation only, so each live blur keeps its exact geometry relative to its source. `LocalBlurFrozen` is no longer set by navigation.
- `TodoViewModel.tasks` uses `SharingStarted.Eagerly`, so it is never a fresh empty value on re-entry.

### Verification (`ui/nav/TransitionTest`, real MainActivity, paused clock, one 16 ms frame at a time, every frame captured)
| Check | v0.6.0 | v0.6.1 |
|---|---|---|
| (i) Home composed and placed on every pop frame (arrow) | missing frames 0–1 | 0 missing, all pops |
| (i) frames where the exposed Home area is blank | 0 | 0 |
| (ii) frames with a dark band in the top 10 % (push + pop) | 0 (not reproduced) | 0 |
| (iii) max diff vs settled once at rest (pop) | 29.2 on every pop | 0.00 on every pop and push |
| (iv) Home disposals per push+pop | 1 | 0 (Settings and Licences are disposed when popped) |

- At rest: 528 ms after a push, 544 ms after an arrow pop, 512 ms after a gesture release. Reduced-motion crossfade: 176 ms.
- Home's composition on the first pop frame is now 0 (it is already composed). The old Robolectric wall times (≈0–0.4 ms per frame) are not representative of a phone.
- Filmstrips (every 3rd frame) in `out/shots/`: `transition-push-settings.png`, `transition-push-search.png`, `transition-pop-settings-arrow.png`, `transition-pop-search-arrow.png`, `transition-pop-settings-gesture.png`, `transition-pop-search-gesture.png`, `transition-pop-settings-arrow-reduced.png`. Reports: `transition-report-*.txt`.
- The Search push end check excludes the top 3/8, because the field autofocuses (cursor) and the "Search" hint finishes its own rise about 160 ms after the slide.

### Not verifiable without a device
- The black band (A) itself: real GPU and Haze RenderEffect behaviour on Sai's phone.
- Real first-frame cost and the IME animation during the Search pop.
- The system's predictive-back animation curve (the test drives `OnBackPressedDispatcher` with synthetic progress).

## Milestone 6.2: header edge and backdrop band (v0.6.2 / code 8)

Sai saw a thin hard horizontal line in light mode just above the date heading on Home.

### Root cause
- `glassEdgeTop` masked the Haze blur and tint with a `Brush.verticalGradient(startY = solidPx, endY = solidPx + 28dp)` in absolute pixels.
- Haze builds the mask shader at the size of its blur buffer (`contentSize * scale`). In `Performance` mode that buffer is half resolution, so the fade sat below the buffer and the mask was about 1 everywhere.
- The result: the blur and tint never faded. They stopped at the header box's hard bottom edge (y≈283 px on a 24 dp status bar).
- That edge was also exactly where the mask was meant to end.
- In dark mode the same edge existed (the measured step is actually larger) but has low perceived contrast.

### What changed (`ui/components/Glass.kt`, `ui/home/HomeScreen.kt`)
- `GlassEdgeTop(solidHeight)` replaces `Modifier.glassEdgeTop`. It is a header-wide sibling layer.
- The layer is solid + 30 dp fade + 12 dp fully transparent tail. It overflows downward and takes no layout height, so the blur bounds extend past the mask end.
- The mask uses **fractional** stops, so it does not depend on Haze's scale. It is a 12-step smoothstep fade (C¹, no Mach band). The fade starts 12 dp above the pill bottom and reaches exactly 0 30 dp below it, well above the heading.
- Haze applies the mask DstIn after blur and tint, so one mask covers both.
- It is still one Haze pass with noise 0. No dithering was needed (no banding measured).
- Reduced motion / no Haze state: the flat fallback tint is drawn as the same eased gradient (no blend mode, no layer). The first attempt used DstIn and produced a black band, which `TransitionTest.reducedMotionArrowPop` caught.
- Backdrop: `skyBackdrop` keys (0 / .30 / .60 / .76 / 1) are now interpolated with `smoothStops` (smoothstep, 10 sub-stops per segment). Before, they were linear at 0/.30/.62/.80/1, and the slope jump at 0.80 read as a bright band above the composer. Palette colours are unchanged.

### Verification (`ui/home/HeaderEdgeTest`, Robolectric NATIVE, empty list, simulated 24 dp status bar)
- Measurement: mean luminance per row over a content-free strip (86–94 % width), from below the pill to 86 % height.
- Asserts: max row step < 1.5/255 and max slope change (24-row windows) < 0.08.

| | max row step | max slope change | backdrop slope change |
|---|---|---|---|
| light, v0.6.1 | **2.44** at y=284 (the edge) | 0.120 | 0.105 at y=1878 (the 80 % band) |
| light, v0.6.2 | **1.28** | 0.045 | 0.039 |
| dark, v0.6.1 | **4.01** at y=284 | 0.161 | 0.046 |
| dark, v0.6.2 | **0.86** | 0.061 | 0.052 |
| light reduced motion, v0.6.2 | 0.94 | 0.327 (intended high-contrast fade; reported only) | 0.039 |

- "Before" is v0.6.1's mask and stops temporarily restored in the same test.
- The residual ±0.4 alternating-row ripple is the half-res blur upsample, under the threshold.
- Crops (top 30 %, contrast ×≤8): `out/shots/header-edge-before.png`, `header-edge-after.png`, plus `-before-/-after-light|dark` .png and .txt (with full profiles) and `header-edge-light-reduced`.
- Not device-verified. The status bar is simulated; the real phone (1216×2640) should be re-checked.

## Milestone 7: organisation, batch 2 (v0.7.0 / code 9)

Lists, tags, subtasks, focus mode, drag to reorder, templates, and the reminder-while-Settings fix.

### Data (Room schema 4, `MIGRATION_3_4`, data preserved)
- `tasks` gains `list_id` (default 1 = Personal), `tags` (space-separated, lower-case, default "") and `sort_order` (set to `created_at` in the migration, so the old order is kept).
- New tables `lists` (built-ins Personal id 1 and Work id 2, seeded in the migration and in `onCreate`), `subtasks` (indexed by `task_id`) and `templates` (JSON body, 2 examples seeded).
- No foreign keys: the repository deletes and restores subtasks itself. A REPLACE on `tasks` would otherwise cascade.
- `MigrationTest`: v3 rows keep every field, land in Personal with no tags and sort_order = created_at, the lists and templates exist, ids continue, and the new columns and tables work. The 2→3 test now runs 2→4.

### Features
1. **Lists.** A chip under the date heading ("All lists ▾" / "● Work ▾"), or a tap on the heading, opens a floating glass picker from the chip (same container transform as the task panel). The picker has Focus, All lists, the lists with open counts, and Manage. Settings > Organise > Lists lets you create, rename, set an emoji, recolour (8 colours of our own), set the area, drag to reorder, and delete with "move tasks to…". Built-in lists cannot be deleted.
2. **Composer.** `#work` / `#listname` sets the list (chip with a dot). `+tag`, or an unknown `#tag`, becomes a tag. Only the first list hashtag counts; later ones become tags.
3. **Tags.** Edited as chips in the panel. Rows show up to 2 pills plus "+N", and pills that don't fit fold into the count. Tapping a pill opens Search filtered to that tag. Search has filter chips for lists and tags.
4. **Subtasks.** Panel checklist: add, check, drag by the grip, delete. Rows show "✓ 2/5". Completing a parent completes all its subtasks, and Undo ("Completed with N subtasks") restores their states. A repeating task's next occurrence gets the subtasks copied unchecked, in order.
5. **Focus (Off / Work / Personal).** Set from the picker. Optional schedule in Settings > Focus: days, From/To and "Outside work hours" (Personal or All lists). Default Mon–Fri 9:30–18:30, off until enabled. Times use the device zone; start is inclusive, end exclusive; overnight windows are supported. A manual pick lasts until the schedule's next boundary. The header shows "◎ Work focus". **Reminders from hidden lists still fire**, and tapping one shows all lists so its card exists.
6. **Drag to reorder** (`sh.calvin.reorderable` 3.1.0, Apache 2.0, notice in Licences). Long-press lifts the row (scale 1.03 plus a lifted shadow, set in the draw phase; reduced motion snaps) with a haptic tick on pickup and each slot change. Auto-scroll near the edges. Today's manual order is persisted. On Upcoming, dropping under another day header moves the task to that day and keeps its time. Done is not reorderable. Swipe still works: the drag needs a long press, and swipe is off while dragging.
7. **Templates.** The composer '+' opens a floating "Use a template" menu. Using one inserts its tasks (list, relative day offsets, time, priority, tags, subtasks) with a 70 ms staggered entrance. Templates are created in Settings > Templates (text, one task per line, "- " for subtasks, parsed with the composer grammar) or from "Template" in the task panel. The 2 examples ("Weekly review", "Trip packing") can be deleted.
8. **Bug fix.** A reminder tapped while Settings, Search, Licences, Lists or Templates is open now runs `popToRoot()`. The screens in between are dropped and the top one slides out with the normal `pop()`. The panel opens only after Home is the settled top screen.

### Simplified
- Tags are a string column, not a join table.
- Templates can be created and deleted, not edited.
- Upcoming ordering is per day.
- "All lists" outside work hours shows everything.
- A `#list` naming a list hidden by focus stays hidden.

### Milestone 7.1: list pill + picker morph (v0.7.1 / code 10)

Sai on v0.7.0: the list menu's close ended with the card disappearing on its last frame, because it shrank into plain text. The list label is now a glass pill and the picker morphs into and out of it. The menu is also faster.

### What changed
- **Glass list pill** (`ui/home/OrgMenus.kt`: `ListChip`, `ListPillContent`). Uses `glassControl(Shapes.full)`, the same tokens as the round header buttons and the pill track: glass fill, 1 dp gradient rim, Low shadow. 34 dp tall, fully rounded. Contents: dot, emoji or lists icon, the name and a chevron. With a focus mode on, a hairline and an accent "◎ Work" segment sit inside the pill; the old separate badge is gone. The touch target is 48 dp tall (ripple clipped to the pill). The "All work lists" label is now "All lists" plus the focus segment.
- **Shared-bounds morph** (`ui/panel/TaskPanel.kt`: `PanelStyle.Menu`, `drawMorph`), all in the draw phase.
  - Rect: from the pill's exact bounds to the panel. Corner: from height/2 to 24 dp.
  - Fill, rim and shadow interpolate. The pill's Low shadow crossfades into the panel's lifted shadow, so g = 0 matches `glassShadow(Low)` exactly.
  - Panel content has its own time-based alpha: in over 120 ms once the geometry passes 40 %, out over 90 ms from the start of the close (gone by about 35 % of the close).
  - The pill's own content (a "ghost" composed in the host, always current, so a list picked in the menu is already right) fades in inside the capsule over the last 30 % of the geometry. The capsule therefore lands as the pill itself.
- **Landing.** Close ends the moment the capsule is within 0.5 dp of the pill (it stops the spring tail there). On that frame the pill is drawn again and the capsule paints nothing over it, so nothing is doubled. The host leaves on the next frame. A bug where the last frame painted the capsule over the revealed pill (doubled fill) is fixed: `closing` now stays set until the next open.
- **Open.** The pill is hidden from the first capsule frame, and that frame's capsule is exactly the pill.
- **Faster.** New token `Springs.Morph` (response 0.38 s, damping 0.90). The backdrop blur and dim are driven by the same progress, so they follow automatically.
- **Scope.** The list picker and the composer's "+" templates menu share the menu host, so both use `PanelStyle.Menu`. The "+" is a bare icon, so its capsule's glass fades out as it lands (`bareAnchors`). The task panel and the snooze picker share the other host (`PanelStyle.Card`) and are unchanged: Smooth spring, same drawing.

### Numbers (`ui/home/ListPillMorphTest`, real HomeScreen, paused clock, 16 ms frames, every frame drawn)
- **Open:** 592 ms before (v0.7.0 path, Card style + Smooth) → **432 ms** after (−27 %). Content starts at frame 10 of 27.
- **Close:** last capsule frame at 576 ms before → **416 ms** after (−28 %). Content gone by 144 ms.
- **Landing:** capsule error 0.000 dp; landing frame vs settled pill pixels: mean difference 0.0 (previous frame 3.7 of 255).
- **Asserted:** no frame where neither pill nor capsule is drawn (open and close); the pill is never drawn under a moving capsule; content stays hidden until about 40 % of the open.
- **Outputs:** `out/shots/list-picker-open-filmstrip.png`, `out/shots/list-picker-close-filmstrip.png`, `home-list-pill-{light,dark}.png`, `home-list-pill-focus-{light,dark}.png`.

### Risks
- The morph assumes a fully rounded anchor (true for the pill and the "+").
- Spring physics give the open a slow first 2–3 frames (zero initial velocity).
- Feel on a device is not yet checked.

## Milestone 7.2: frosted composer + bottom fade (v0.7.2 / code 11)

Sai (light mode, on the phone): rows scrolling behind the composer were clearly readable through it, almost unblurred.

### Root cause (reproduced in Robolectric, fixed)
- `HomeScreen` built the list's Haze source as `Box(Modifier.skyBackdrop(c).hazeSource(hazeState))`.
- `hazeSource` records only what is drawn after it in the modifier chain, plus the children. The opaque sky was therefore outside the recording, and the source was just the rows on a transparent background.
- The blurred copy was mostly transparent, so it could not cover the sharp rows drawn underneath. What you saw was the real list through a 50 % white tint, not a weak blur.
- The same applied to the header edge. The comment claimed "the sky is part of the haze source"; it wasn't.
- An isolated probe confirmed it: sparse text in a source with no background shows sharp through every Haze mode (default, Performance, Quality); with the background inside the source it is properly frosted.
- Blur radius and the half-res mask were not the cause.
- Fix: `.hazeSource(hazeState).skyBackdrop(c)`.

### Stronger frost (composer only, `glassBlurFill`)
- Blur `Depth.FrostBlur` = 26 dp (the header stays at 16 dp).
- A saturation lift of 1.15 (one colour matrix, applied before the tint).
- Tint: light white 72 % (was 50 %); dark slate `#262C39` at 70 % (unchanged).
- Rim (1 dp) and Mid shadow unchanged. Reduced motion or frozen blur: the same flat fallback as before.

### Bottom fade (`Modifier.bottomFade`, Glass.kt)
- The list layer (AnimatedContent) gets an offscreen layer with a DstIn alpha mask: 1 down to 36 dp above the composer top, then a smoothstep (C¹) fall to 0.25 at the composer top, held below. It is a mask, not a scrim: the sky shows through.
- The edge is read in the draw phase from the measured bottom chrome. The undo bar or parse chips move it with a redraw only.
- The floor is 0.25 rather than 0, so rows are still hinted (colour and shape) through the frost.
- The list's bottom padding grew by the fade height (+40 dp instead of +8 dp), so the last row comes to rest clear of the fade. `BottomFadeTest.lastRowClearsTheFade`: the card bottom sits about 22 dp above the fade start.

### Header edge
- The header blur now really samples the sky. Its mask needed a gentler start: `fadeAbove` 12 → 20 dp.
- HeaderEdgeTest light: max row step 1.33, slope change 0.039 (it was 0.085 before the `fadeAbove` change, against a limit of 0.08).
- HeaderEdgeTest dark: max row step 0.84, slope change 0.052.

### Numbers (`shots/ComposerFrostScreenshotTest`, 13 rows, a row title half behind the composer)
- **Leak proxy:** std dev of the high-passed luminance *difference* between the same composer with rows behind and with the list emptied (only what shows through counts; 0 = nothing visible).

| | before | after |
|---|---|---|
| light | 1.79 | **0.17** (−90 %) |
| dark | 1.09 | **0.14** (−87 %) |
| light, blur off (tint only, for scale) | 0.80 | 0.17 |

- `BottomFadeTest` mask (opaque card colour over the deepest sky):
  - light: 46.6 luminance levels over 94 rows; max row step 1.00 (8-bit quantisation); step at fade start/end 0.00.
  - dark: 16.0 levels; max row step 1.00; start/end 0.00.
  - Slope change is reported only (0.47 / 0.19): this is an intended, high-contrast fade, as with reduced motion's header.
- **Shots:** `out/shots/composer-frost-{before,after}-{light,dark}.png`, with `-crop` versions around the composer, and `.txt` files with the numbers.

### Risks
- The offscreen layer for the list is one full-screen buffer. Only device profiling can confirm the cost; the page cross-fade still uses ModulateAlpha inside it.
- The header now looks slightly more frosted, because it blurs the sky too.

## Milestone 7.3 (v0.7.3, code 12): collapsing composer pill

- **What**: scrolling down collapses the bottom composer into a centred glass pill (`+` | mic, hairline
  divider, 120 x 48 dp, same frost/rim/shadow); scrolling up expands it. Never hidden.
- **One surface**: the capsule's bounds are set in the layout phase from `ComposerChrome.progress`
  (0 = full, 1 = pill), width lerped and centred, so it is symmetric about the centre with
  fully rounded corners (24 dp radius on a 48 dp bar). Children stay in full-composer coordinates
  minus the capsule's left edge: the field keeps its width (no reflow) and is clipped; `+` and mic
  glide to either side of the centre; the divider sits midway between them with alpha = progress.
  Placeholder: fades out over the first 35 % of a collapse, in after 40 % of an expand.
  Spring `Springs.Morph` (0.38 s, damping 0.9). No recomposition per frame.
- **Triggers** (`CollapseMachine`, pure, `ComposerChrome.connection` = NestedScrollConnection on the
  sky Box, `onPostScroll` consumed y): collapse after 28 dp accumulated downward scroll, expand
  after 16 dp upward; the accumulator resets on a direction flip. Expands at the top or bottom of
  the list, on a page switch, when Home is on top again (back from a pushed screen), on tap.
- **Never collapses** (and expands at once) while: field focused, text, voice listening, NL chips,
  undo bar visible, a drag in progress (`RowActions.onDragging`), list not scrollable.
- **"+" decision**: collapsed pill: tap on `+` or the pill body = expand + focus the field (one light
  tick, the feature's only haptic); long-press `+` = templates menu (expands too); mic = start voice
  + expand. Expanded: `+` tap = templates (unchanged).
- **TalkBack**: collapsed pill is one node "Add a task" with click "Add a task" and custom action
  "Voice input"; the field is cleared from semantics while collapsed.
- **Reduced motion**: 150 ms fade-through of the one surface (75 out, snap bounds, 75 in), no bounds animation.
- **Bottom fade** follows progress in the draw phase: 36 dp / floor 0.25 -> 28 dp / floor 0.45 beside the
  pill. The bar's height is 48 dp in both states, so the list bottom padding and the undo bar
  position are unchanged.
- **Numbers**: collapse and expand both land at frame 24 (400 ms at 16 ms frames), 1 surface every
  frame, centreX constant within 0.5 dp, landing frame vs settled diff 0.0 (pixel-identical).
- **Tests**: `CollapseMachineTest` (8), `ComposerCollapseTest` (4: frame-step morph + filmstrips,
  real swipe collapse / swipe-up and tap expand / focused blocks, page switch, reduced motion),
  `ComposerPillScreenshotTest` (2). Shots: `composer-collapse-filmstrip.png`, `composer-expand-filmstrip.png`,
  `composer-pill-light/dark(.png, -crop.png)`.
- **Frost proxy drift** (re-run of `ComposerFrostScreenshotTest`): light 0.17 -> 0.19, dark 0.14 -> 0.18,
  tint-only 0.17 -> 0.25 (v0.7.2 before was 1.79 / 1.09 / 0.80); the capsule is now under a layout + alpha
  layer, which slightly shifts blur sampling. Still far below the v0.7.2 baseline.

## Milestone 7.4 (v0.7.4, code 13): composer focus + touch fixes

- **Bug 1, focus never cleared. Root cause**: nothing ever cleared focus. Compose keeps focus on a text
  field until something calls `clearFocus`, and a tap on a row or on empty space does not move focus.
  So `focused` stayed true, `composerBusy` stayed true, and the bar could never collapse again.
  **Fix**:
  - **Tap outside**: an Initial-pass `pointerInput` on the home layer. While the field is focused,
    a down outside `ComposerChrome.composerBounds` (the chips plus the bar) is consumed, and focus and
    the IME are dismissed. That first tap does nothing else: it opens no task, ticks no checkbox and
    switches no page. A drag that starts there still scrolls. Menus and panels are outside that layer.
  - **Draft text** is kept, and the bar stays expanded because the text rule still applies.
  - **IME hidden** (`WindowInsets.isImeVisible` true -> false), for example by system back: clears focus.
  - **Scroll start** (`onPreScroll`, UserInput): dismisses.
  - **Page switch** (header) and **Home leaving the top** of the stack: dismiss. AppNav already cleared
    focus on push.
- **Bug 2, touches through the bar. Root cause**: Compose hit-tests siblings top-down and falls
  through to the next sibling when nothing on the top one has a pointer modifier at that point.
  The expanded capsule had pointer handlers only on the field and buttons, so these went to the
  LazyColumn row behind (tap = open the panel, long-press = drag):
  - the rim and corners;
  - the 2 dp insets;
  - the strip right of the mic;
  - the shadow above;
  - the area just around the pill and around the mid-morph surface.

  **Fix**: a `composer-shield` node, child 0 of a custom Layout under the composer column. Being
  under the column, the field and buttons still get their touches first. It consumes every pointer
  event and covers 6 dp above the capsule down to the bottom of the composer (bottom padding and
  nav bar). Its width follows the morph in the layout phase: full width when expanded, the pill
  plus 8 dp per side when collapsed. So taps beside the pill and in the bottom fade above the bar
  reach rows.
- **Numbers** (`ComposerTouchTest`, `out/shots/composer-touch-probe-{before,after}.txt`): 79 taps and
  long-presses over the expanded bar, the collapsed pill and the surface parked at 25/50/75 %.
  Before: **28 leaked**. After: **0**. Taps 40 dp beside the pill and 14 dp above the bar (in the
  fade) reach the row.
- **Tests**: `ComposerTouchTest` (4):
  - focus, tap a row: focus cleared, one IME-hide request, no panel; the next tap opens it; a
    scroll collapses again;
  - a draft is kept and stays expanded;
  - scroll and page switch clear focus;
  - the touch probe.

  `ComposerCollapseTest` was updated: the scroll now ends editing, and a draft keeps the bar expanded.

## Milestone 8 (v0.8.0, code 14): quick capture and planning (batch 3)

Local git: one commit per step (`git log`): baseline 0.7.4, domain + schema 5, UI (search, plan, capture, tile), widget, tests + polish.

### Data (Room schema 5, `MIGRATION_4_5`, data preserved)
- New `saved_views` table (`id, name, query, filter, position`). The filter is stored as `ViewFilter.encode()` (`list=5;prio=1;due=ThisWeek;status=Open;rem=1;rep=1`), and `decode` is lenient.
- `MigrationTest`: 4 → 5 and 2 → 5 keep every row. The test helper now also creates schema indices.
- Settings (DataStore): `plan_enabled` (default off) and `plan_minute` (default 480 = 8:00 AM).

### Share-sheet capture (`ui/capture/CaptureActivity.kt`, `CaptureScreen.kt`, `domain/ShareCapture.kt`)
- **Window:** `Theme.Todo.Capture` is translucent, with no window animation. The activity has its own `taskAffinity` and `excludeFromRecents`, so the full app never launches.
- **Backdrop:** cross-window blur (20 dp) when the device allows it, otherwise our scrim (0.34).
- **Card:** a bottom glass card with the title (NL parse chips, as in the composer), notes, a list chip row and Cancel/Save. It rises on Glide; reduced motion uses fades.
- **Parsing:**
  - A subject becomes the title. Otherwise the first line is the title and the rest goes to notes.
  - URLs move to notes. A link-only share gets the title "Read <host>".
  - Titles over 120 characters are cut at a word boundary with the full line kept in notes.
- **Save:** shows "Added to Today / Added for <date>" with a check pop for 650 ms, then fades out.

### Quick Settings tile (`AddTaskTileService`)
- Opens the same card, empty, with the keyboard up (`stateVisible`).
- API 34+ uses `startActivityAndCollapse(PendingIntent)`; API 33 uses the Intent overload. When locked it calls `unlockAndRun`.

### Home-screen widget (`widget/TodayWidget.kt`, `WidgetData.kt`, Glance 1.2.0)
- **Content:** the Today page's open tasks (due today, overdue, undated) in manual order, at most 6, then "+N more". Overdue rows are marked red; timed rows show their time.
- **Actions:**
  - The ring completes the task through `CompleteTaskAction` (repository `setDone`, so repeats and subtasks work).
  - A row opens the app at that task's panel (`EXTRA_OPEN_TASK`).
  - "+" opens the capture card.
- **Refresh:** `TodoApp` maps `tasks` → `WidgetData`, applies `distinctUntilChanged` and calls `updateAll` only when a widget is placed. `updatePeriodMillis` is 30 min, which also rolls the day over.
- **Styling:** day/night colours from our ink and accent tokens. Rounded 24 dp shape drawable plus `cornerRadius`. Picker preview is `@layout/widget_today_preview` (static, sample tasks).

### Morning planning (`domain/Planner.kt`, `ui/plan/PlanScreen.kt`, `reminders/PlanReminder.kt`)
- **Reminder:** an inexact daily alarm (`setAndAllowWhileIdle`, so no exact-alarm permission). It is re-armed after each fire, on setting change, on boot and on clock change. The notification reads "2 overdue · 3 due today" and opens the plan screen.
- **Queue:** overdue (oldest first), then due today (timed first), then up to 3 suggestions (2 from the next 7 days, then the oldest undated).
- **Card stack:** depth scale 1−0.05·d and 14 dp lift on Glide. Swipe right = today, left = tomorrow (threshold 0.28 width, rotation 7°·dx). Buttons for Today, Tomorrow, Pick date, Done, and Skip.
- **Motion fix found by the filmstrip:** the exit fling now awaits its own axis and releases once off-screen (~190 ms). Before, a horizontal exit was cut short at once. Cards have an opaque base so the stack never shows through.
- **Summary:** "4 for today · 1 moved · 1 done".
- **Entry points:** Settings → Planning, and the list menu row "Plan your day".

### Search filters and saved views (`domain/ViewFilter.kt`, `ui/search/SearchScreen.kt`)
- **Chips:** Open/Done, Overdue/Today/This week/No date, High/Low, Reminder, Recurring, plus list/tag chips. They combine with the text.
- **Saved views:** "Save view" names and stores the set. Saved views sit in Search's top row and in the list menu; tapping the active one clears it, and "Delete view" removes it.

### Numbers
- Plan card flight in the frame test is monotonic and off-screen by frame 12 (16 ms frames). The next card is live by about 0.2 s.
- Release APK is +0.99 MB (Glance).

### Risks
- The widget render test uses `GlanceRemoteViews` in Robolectric. Launcher hosts can differ.
- Inexact alarm: the planning nudge can arrive a few minutes late in Doze.
- Cross-window blur depends on the device and battery saver; the fallback is the scrim.

## Tests
- `org/QuickParseOrgTest` (10)
- `FocusTest` (7, IST, edge minutes, overnight, manual until boundary)
- `ReorderTest` (7, keys and drag-to-day)
- `TemplatesTest` (6, relative dates)
- `OrgRepositoryTest` (9, subtasks copied on repeat, complete-all + undo, delete/restore, delete list moves tasks, reorder + day keeps time, template use, codec)
- `MigrationTest` +1 (3→4)
- `ui/nav/NotificationOpenTest` (4, real MainActivity via onNewIntent from Licences two deep, Settings, Search and Home: frame-by-frame pop, Home never re-created, panel opens after the pop)

### Screenshots (`out/shots/`, light + dark)
- `list-picker-*`
- `rows-tags-progress-*`
- `panel-subtasks-tags-*`
- `drag-lifted-*` (real long-press + move, not released)
- `templates-menu-*`
- `focus-indicator-*`

### Risks
- The drag and swipe interplay, auto-scroll speed and haptic feel need checking on a device.
- Drag-to-day on Upcoming depends on dropping below a header.
- The focus schedule re-evaluates once a minute while Home is visible.

## Tests

`./gradlew testDebugUnitTest`: **185 tests, all pass** (v0.8: +`ShareCaptureTest` 9, +`ViewFilterTest` 4, +`PlannerTest` 3, +`MigrationTest` 2 (4 → 5, 2 → 5), +`WidgetDataTest` 3, +`PlanReminderTest` 1, +`CaptureIntentTest` 3; v0.7.4: +`ComposerTouchTest` 4; v0.7.3: +`CollapseMachineTest` 8, +`ComposerCollapseTest` 4; v0.7.2: +`BottomFadeTest` 3; v0.7.1: +`ListPillMorphTest` 2; v0.7: +`QuickParseOrgTest` 10, +`FocusTest` 7, +`ReorderTest` 7, +`TemplatesTest` 6, +`OrgRepositoryTest` 9, +`MigrationTest` 1 (3 → 4), +`NotificationOpenTest` 4; v0.6.2: +`HeaderEdgeTest` 3; v0.6.1: +`TransitionTest` 6; v0.6: +`QuickParseTest` 21, +`RepeatRuleTest` 12, +`SnoozeTest` 6, +`ComposerTest` 2 (chips appear, submit carries the parse, tapping a chip keeps the words), +`MigrationTest` 1 (v2 → v3 preserves data), +`RepeatRepositoryTest` 2; v0.5: +`PillFirstFrameTest` 1, +`TaskPanelTest` 3). `-Pshots --tests '*ScreenshotTest*'`: **44** screenshot tests (v0.8: +10 in `V08ScreenshotTest`: capture light/dark + open filmstrip + save/confirm, plan light/dark + keep filmstrip + summary, search filters light/dark, widget + preview light/dark) (v0.7.3: +`ComposerPillScreenshotTest` 2) (v0.7.2: +3 `ComposerFrostScreenshotTest`; v0.7.1: +4 pill shots; v0.7: +12 in `OrgScreenshotTest`), all pass. `SectionsTest` was replaced by:
- `PagesTest` (11): Today/Upcoming/Done classification; Today = open today/overdue/undated in creation order with "Overdue · Yesterday"; Upcoming grouped by day with headers (Tomorrow / Friday / Wed, 28 Oct) and no repeated date; Done grouped by completion day newest first with Clear done count; empty states and All clear count; leaving rows stay on their page (dwell vs collapsing, reopen from Done); day labels (Locale.UK); row meta; overrides + hidden deletes; due chips (next week = next Monday); entrance tracker baseline + forget.
- `SettingsTest` (4): defaults when nothing stored, stored values, unknown theme → System, `ThemeMode.isDark`.
- `SearchTest` (3): blank query, title+notes case-insensitive match with open-first order, no match.
- `CheckShapesTest`: the morph starts as the unit circle, every step stays inside the unit circle (so it never pokes past the ring), and the end is a squircle.

Milestone 1 tests:
- `SpringMathTest` (13): stiffness formula and every stiffness in spec table 3.1, response round-trip, c → ζ, the overshoot table in spec §1 (checked against the closed-form step response), critical 1 % constant (6.638), every settle time in table 3.1, settled response stays in the 1 % envelope, overdamped > critical, "only Pop visibly bounces", rejects response ≤ 0.
- `MotionTokensTest` (4): `SpringToken` → Compose `SpringSpec` carries the right ζ/stiffness; **Compose's own spring integrator** produces the predicted peak overshoot for every token; slower response → longer Compose duration; haptic rise waveform is 6 increasing pulses ending at 255.

## Deviations from the spec

1. Haze is in (milestone 3) for header + composer, and (v0.5) the panel backdrop; the flat fallback is used under reduced motion for header/composer (the panel backdrop keeps its blur). Shadows are our own ring shadows (Glass.kt).
2. **No kotlinx-serialization or material-icons-extended**: Nav3 keys are a plain enum (saved by name), icons are our own small `ImageVector`s.
3. **AGP 9 built-in Kotlin**: the spec's `org.jetbrains.kotlin.android` plugin is not applied (AGP 9 provides Kotlin itself); no serialization plugin needed.
4. **Persistence: Room (as spec)** via KSP; no friction.
5. "+" and mic: also `expandHorizontally`/`shrinkHorizontally` on Smooth so the text field width glides instead of jumping when they leave (the spec's `animateContentSize` on the Row can't animate a child's width in a fixed-width row). "+" and mic currently just focus the field (list/date picker and dictation are later).
6. Placeholder is a static "Add a task" (rotating placeholder §6.13 is build step 9).
   The un-complete dwell reuses the 700 ms complete dwell (the spec doesn't give one).
7. Header is glass with title + search/settings + filter pill; the open-task count digit slide was dropped with the old header. Launch entrance §6.1 not done (step 9). §6.8 inline expand skipped; v0.5 replaces the sheet with the contextual floating panel (6.7-style).
8. In-app reduce motion / haptics / theme are real settings (DataStore). Theme changes switch instantly (no animated palette crossfade).
9. `Settings.System.HAPTIC_FEEDBACK_ENABLED` is deprecated in SDK 37 but is still read (suppressed) as the spec asks.
10. Debug APK (milestone 2 build) is about 31 MB because it is unminified. **Release APK (v0.8.0 / code 14): 5,647,619 bytes (about 5.6 MB), apksigner verify OK (V2, Android Debug), aapt2 badging versionCode 14 / versionName 0.8.0.** v0.7.4 / code 13: 4,655,871 bytes. v0.7.1 / code 10: 4,639,487 bytes. v0.7.0 / code 9: 4,623,103 bytes. v0.6.2 / code 8: 4,442,879 bytes. v0.6.1 / code 7: 4,442,879 bytes (Navigation 3 removed). v0.6.0 / code 6: 4,476,032 bytes. v0.5.0 / code 5: 4,301,408 bytes (about 4.3 MB). Milestone 4 (v0.4.0 / code 4): 4,285,024 bytes (about 4.3 MB; +0.9 MB for the two Newsreader variable fonts, minus Instrument Serif). Milestone 3 was 3,769,333 bytes, with R8, resource shrinking, baseline profile, debug-key signing (apksigner verify OK, V2).
11. Auto-scroll for a new row that is fully off-screen uses `animateScrollToItem(index)`, which lands it at the top of the viewport unless it is the last list item. The common case (a partly hidden row) scrolls by the overflow on Smooth.
12. Swipe-to-delete fling velocity threshold is M3's internal default (not configurable in 1.4.0); only the 35 % positional threshold is set. Swipe is end→start only.
13. "Next week" chip = next Monday. Search results are not grouped by page. Licences screen lists libraries by name + licence (only the font OFL texts are bundled).

## Emulator

Not run. `/dev/kvm` is `root:103 crw-rw----` and user `box` is not in group 103, so there is no hardware acceleration; free RAM was ~1–4 GB during the build. No screenshots.

## Verified signatures for later milestones (read from the real sources jars)

**Navigation3 `navigation3-ui` 1.2.0 — `NavDisplay`.** Current (non-deprecated) overload:
```kotlin
@Composable
fun <T : Any> NavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: () -> Unit = { if (backStack is MutableList<T>) backStack.removeLastOrNull() },
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    entryProvider: (key: T) -> NavEntry<T>,
)
```
So the predictive lambda receives the swipe edge: write `predictivePopTransitionSpec = { _ -> popSpec() }` (or `{ edge -> ... }`).
The older overload with a single `sceneStrategy: SceneStrategy<T>` is `@Deprecated(level = HIDDEN)`.
Other overloads take `entries: List<NavEntry<T>>` (+ required `onBack`) or `sceneState`/`navigationEventState`; same three transition params.

**Material3 1.4.0 — `SwipeToDismissBox`.**
```kotlin
@Composable
fun SwipeToDismissBox(
    state: SwipeToDismissBoxState,
    backgroundContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = true,
    enableDismissFromEndToStart: Boolean = true,
    gesturesEnabled: Boolean = true,
    onDismiss: (SwipeToDismissBoxValue) -> Unit = {},
    content: @Composable RowScope.() -> Unit,
)
@Composable
fun rememberSwipeToDismissBoxState(
    initialValue: SwipeToDismissBoxValue = SwipeToDismissBoxValue.Settled,
    positionalThreshold: (totalDistance: Float) -> Float = SwipeToDismissBoxDefaults.positionalThreshold,
): SwipeToDismissBoxState
```
`onDismiss` takes the direction (the spec's `onDismiss = { onDelete() }` compiles as an implicit-`it` lambda).
The overload without `onDismiss` is HIDDEN (binary compat). The `confirmValueChange` overloads of
`rememberSwipeToDismissBoxState` / the state constructor are `@Deprecated(WARNING)` "without replacement".
`SwipeToDismissBoxState` also has `suspend fun reset()`, `snapTo(value)`, `dismiss(direction)`, `requireOffset()`.

**graphics-shapes 1.1.0 — `Morph.toPath`** (androidMain, `androidx.graphics.shapes`):
```kotlin
fun Morph.toPath(progress: Float, path: android.graphics.Path = android.graphics.Path()): android.graphics.Path
fun RoundedPolygon.toPath(path: android.graphics.Path = android.graphics.Path()): android.graphics.Path
```
It returns an **android.graphics.Path** (not a Compose Path): use `.asComposePath()`. Common code also has
`Morph.asCubics(progress): List<Cubic>` and `forEachCubic(...)`.

**Haze 2.0.1** (bonus check; spec §5/§6.7 names are correct): `rememberHazeState()`, `Modifier.hazeSource(state, zIndex = 0f, key = null)`,
`Modifier.hazeBlur(input: HazeInput, style: HazeBlurStyle = HazeBlurStyle, performanceMode: HazePerformanceMode? = null, expandLayerBounds = true)` (package `dev.chrisbanes.haze.blur`),
`HazeInput.Sources(state, selection = HazeSourceSelection.Behind, retention = KeepLastFrame, ...)`,
`HazeBlurStyle { blurRadius(Dp); noiseFactor(Float); colorEffects(List<HazeColorEffect>); fallbackColorEffect(HazeColorEffect?) }`,
`HazeColorEffect.tint(...)` / `colorFilter(...)`.

## IP

No third-party code, shaders, fonts, assets, logos or palette were used. Only the motion spec's numbers/techniques; palette, icons and launcher mark are our own; fonts are OFL.
Milestone 4: the reference recordings Sai provided were used only to measure proportions, alphas and tonality. No frames from them are in the repo or shipped in the APK. Every colour value is our own pick, close in feel but not the same hex. No third-party fonts, drawables, shaders or logos are used. The serif is Newsreader (OFL) and the sans is Inter (OFL).
