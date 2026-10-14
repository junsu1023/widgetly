# 홈 화면 리디자인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the D-day list (home) screen to match the Figma design — category tabs, icon-badge cards, per-card mascot — and move `AppRoot` from its 2-button ad-hoc toggle to a 4-tab bottom navigation bar.

**Architecture:** `DdayEntry` gains two small `shared-core` enums (`DdayCategory`, `DdayIcon`) with JSON-decode-safe defaults. The redesigned `DdayListScreen` stays in `shared-core` (no Firebase dependency, same as today) but cannot reach `shared`'s generated mascot `Res.drawable.*` resources directly — it receives a `mascotIcon: @Composable (Modifier) -> Unit` slot from `AppRoot` instead, the same dependency-inversion-via-composable-slot trick `MascotBackground` already uses at the `AppRoot` layer, just threaded one level deeper. `AppRoot` (in `shared`) becomes a 4-tab shell; 2 of the 4 tabs reuse already-built screens (`MascotPickerScreen`, `PairingScreen`) unchanged, 1 is a new placeholder, 1 is the redesigned home screen.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, `org.jetbrains.compose.material:material-icons-extended` (new dependency, added in Task 4).

**Spec:** `docs/superpowers/specs/2026-10-14-home-screen-redesign.md`

## Global Constraints

- Exact colors (from the spec, pixel-sampled off the Figma screenshot): title/body text `#332621`/`#342923`, inactive tab `#887F7B`, date text `#8D8581`, page background `#FFFEFC`, card background `#FFFFFF`. Icon badge colors + glyph color are per-`DdayIcon` (see Task 1).
- Font: reuse `WidgetlyTheme`'s existing typography roles (Do Hyeon via `displayX`/title-mapped styles, Dongle via `withDongleBody()`-mapped body styles) — do not hardcode a new `FontFamily` reference anywhere in this plan's tasks.
- Icons: real vector icons only, never the raw Unicode glyphs (⌂ ▦ ◇ ••• ♡ ▤ ♧ ✈ ＋ ⚙) the Figma mock used as placeholders. Library: `org.jetbrains.compose.material:material-icons-extended`, version-ref `composeMultiplatform` (matches every other Compose Multiplatform artifact already in `gradle/libs.versions.toml`).
- Module boundary: `shared-core` must never reference `shared`'s generated Compose resources (`Res.drawable.quokka` etc.) or anything Firebase-touching. Where `DdayListScreen` (shared-core) needs the user's mascot image, it takes a `@Composable (Modifier) -> Unit` parameter supplied by `AppRoot` (shared) — never a direct resource reference.
- No new navigation library — extend the existing `private enum class AppScreen` + `when` pattern already in `AppRoot.kt`.
- `DdayCategory` is a new, independent `shared-core` enum — do not reuse or import `shared`'s `connection.ConnectionType`, even though the value names overlap (COUPLE/FRIEND). Reusing it would pull a `shared`-module import into `shared-core`.

## Review Focus

- A pre-existing locally-stored `DdayEntry` JSON blob (written before this feature shipped, no `category`/`icon` keys at all) must decode to `DdayCategory.OTHER`/`DdayIcon.HEART`, not throw. (Task 1)
- The "전체" tab must show every entry regardless of category — a filter bug that silently narrows "전체" to just `OTHER`-category entries would look like data loss to a user. (Task 3)
- Creating a new entry while viewing the "커플" (or any non-"전체") tab must still let the user pick ANY category in the form — the active tab filter must not silently force the new entry into that tab's category. (Task 5)
- The 4 existing `DdayEntryFormTest` cases and `DdayEntrySerializationTest` construct `DdayEntry(...)`/call `buildDdayEntry(...)` without category/icon arguments — both sides must keep resolving to the same defaults so none of those pre-existing assertions need to change. (Task 2)
- `mascotIcon` must render a sensible fallback (quokka) even on the first frame, before `MascotPickerViewModel`'s async local-storage read resolves — not a blank gap in every card's corner. (Task 6)

---

## File Structure

**New:**
- `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayFilter.kt`
- `shared-core/src/commonTest/kotlin/com/example/widgetly/dday/DdayFilterTest.kt`
- `shared-core/src/commonTest/kotlin/com/example/widgetly/dday/DdayCategoryIconDecodeTest.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/WidgetGalleryScreen.kt`

**Modify:**
- `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntry.kt`
- `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntryForm.kt`
- `shared-core/src/commonTest/kotlin/com/example/widgetly/dday/DdayEntryFormTest.kt`
- `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt`
- `gradle/libs.versions.toml`
- `shared-core/build.gradle.kts`
- `shared/build.gradle.kts`

---

### Task 1: `DdayCategory` / `DdayIcon` + `DdayEntry` fields

**Files:**
- Modify: `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntry.kt`
- Test: `shared-core/src/commonTest/kotlin/com/example/widgetly/dday/DdayCategoryIconDecodeTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Serializable
  enum class DdayCategory { COUPLE, FRIEND, OTHER }

  @Serializable
  enum class DdayIcon(val badgeColorArgb: Long, val glyphColorArgb: Long) {
      HEART(0xFFFFE2E7L, 0xFFF17C92L),
      BOOK(0xFFE3EDFFL, 0xFFF17C92L),
      CAKE(0xFFFFE9DDL, 0xFFF17C92L),
      PLANE(0xFFE4EEFFL, 0xFFF17C92L),
  }
  ```
  `DdayEntry` gains `val category: DdayCategory = DdayCategory.OTHER` and `val icon: DdayIcon = DdayIcon.HEART`, appended after the existing `repeat` field.

- [ ] **Step 1: Write the failing test** in `DdayCategoryIconDecodeTest.kt`, mirroring `DdayEntrySerializationTest.kt`'s style:
  ```kotlin
  @Test fun `decodes a pre-feature entry JSON with no category or icon keys to the defaults`() {
      val legacyJson = """{"id":"1","title":"수능","targetDate":"2026-11-12","countMode":"START_AT_ZERO","repeat":"NONE"}"""
      val decoded = Json.decodeFromString<DdayEntry>(legacyJson)
      assertEquals(DdayCategory.OTHER, decoded.category)
      assertEquals(DdayIcon.HEART, decoded.icon)
  }
  @Test fun `round-trips category and icon through JSON`() {
      val entry = DdayEntry("1", "t", LocalDate(2026, 1, 1), category = DdayCategory.FRIEND, icon = DdayIcon.PLANE)
      assertEquals(entry, Json.decodeFromString<DdayEntry>(Json.encodeToString(entry)))
  }
  ```
- [ ] **Step 2: Run** `./gradlew :shared-core:testAndroidHostTest --tests "*.DdayCategoryIconDecodeTest"` — expect FAIL (unresolved references / missing default)
- [ ] **Step 3: Implement** the two enums and the two new `DdayEntry` fields exactly as shown above, in `DdayEntry.kt`.
- [ ] **Step 4: Run** the test again — expect PASS. Also run `./gradlew :shared-core:testAndroidHostTest --tests "*.DdayEntrySerializationTest"` to confirm the pre-existing test is untouched by the new defaulted fields.
- [ ] **Step 5: Commit**

---

### Task 2: `buildDdayEntry` category/icon parameters

**Files:**
- Modify: `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntryForm.kt`
- Modify: `shared-core/src/commonTest/kotlin/com/example/widgetly/dday/DdayEntryFormTest.kt`

**Interfaces:**
- Consumes: `DdayCategory`, `DdayIcon` (Task 1)
- Produces: `buildDdayEntry(id, title, year, month, day, countStartAtOne, repeatYearly, category: DdayCategory = DdayCategory.OTHER, icon: DdayIcon = DdayIcon.HEART): DdayEntry?` — the two new parameters are added at the end with defaults, so every existing call site (including all 5 existing test cases) keeps compiling and keeps passing unmodified.

- [ ] **Step 1: Write the failing test**, appended to `DdayEntryFormTest.kt`:
  ```kotlin
  @Test fun `category and icon pass through when provided`() {
      val entry = buildDdayEntry(
          id = "1", title = "여행", year = 2026, month = 4, day = 18,
          countStartAtOne = false, repeatYearly = false,
          category = DdayCategory.FRIEND, icon = DdayIcon.PLANE,
      )
      assertEquals(DdayCategory.FRIEND, entry?.category)
      assertEquals(DdayIcon.PLANE, entry?.icon)
  }
  ```
- [ ] **Step 2: Run** `./gradlew :shared-core:testAndroidHostTest --tests "*.DdayEntryFormTest"` — expect FAIL (no such parameters)
- [ ] **Step 3: Implement** — add the two parameters to `buildDdayEntry`'s signature and thread them into the constructed `DdayEntry`.
- [ ] **Step 4: Run** the full `DdayEntryFormTest` suite — expect all 6 tests (5 existing + 1 new) PASS, with the 5 existing ones unmodified.
- [ ] **Step 5: Commit**

---

### Task 3: Category filtering

**Files:**
- Create: `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayFilter.kt`
- Test: `shared-core/src/commonTest/kotlin/com/example/widgetly/dday/DdayFilterTest.kt`

**Interfaces:**
- Consumes: `DdayEntry.category`, `DdayCategory` (Task 1)
- Produces: `fun filterByCategory(entries: List<DdayEntry>, category: DdayCategory?): List<DdayEntry>` — `category = null` means "전체" (no filtering, returns `entries` unchanged); a non-null value returns only entries whose `category` equals it.

- [ ] **Step 1: Write the failing tests**:
  ```kotlin
  @Test fun `null category returns every entry unchanged`()
  @Test fun `a specific category returns only matching entries`()
  @Test fun `a category with no matching entries returns an empty list`()
  ```
  Build a fixed 3-entry list (one `COUPLE`, one `FRIEND`, one `OTHER`) as a shared fixture at the top of the test file and assert against it for all three cases.
- [ ] **Step 2: Run** `./gradlew :shared-core:testAndroidHostTest --tests "*.DdayFilterTest"` — expect FAIL
- [ ] **Step 3: Implement** `filterByCategory` in `DdayFilter.kt` — a one-line `if (category == null) entries else entries.filter { it.category == category }` is sufficient; no class, no extra abstraction.
- [ ] **Step 4: Run** the test again — expect PASS
- [ ] **Step 5: Commit**

---

### Task 4: Add the Compose Multiplatform icons dependency

Not a TDD task — a version-catalog + two `build.gradle.kts` edits, verified by the build resolving the new artifact.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared-core/build.gradle.kts`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1:** In `gradle/libs.versions.toml`'s `[libraries]` section, add, next to the other `compose-*` entries:
  ```toml
  compose-material-icons-extended = { module = "org.jetbrains.compose.material:material-icons-extended", version.ref = "composeMultiplatform" }
  ```
- [ ] **Step 2:** In `shared-core/build.gradle.kts`, add `implementation(libs.compose.material.icons.extended)` alongside the existing `implementation(libs.compose.material3)` line in the `commonMain` source set dependencies block.
- [ ] **Step 3:** Do the same in `shared/build.gradle.kts` (find its equivalent `commonMain` dependencies block — `shared` declares its own Compose dependencies separately from `shared-core`, per CLAUDE.md's module-split note).
- [ ] **Step 4: Run** `./gradlew :shared-core:testAndroidHostTest :shared:testAndroidHostTest` — expect SUCCESS (confirms the new dependency resolves and nothing broke; neither module has code using it yet).
- [ ] **Step 5: Commit**

---

### Task 5: Home screen redesign

**Files:**
- Modify: `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt`

**Interfaces:**
- Consumes: `DdayCategory`, `DdayIcon` (Task 1), `buildDdayEntry`'s new parameters (Task 2), `filterByCategory` (Task 3), `org.jetbrains.compose.material.icons.*` (Task 4)
- Produces: `DdayListScreen(viewModel: DdayViewModel, mascotIcon: @Composable (Modifier) -> Unit)` — the function signature changes (new required second parameter); `AppRoot`'s call site is updated in Task 6, not here.

This task has no new unit tests of its own beyond what Tasks 1-3 already cover — it is UI wiring over already-tested logic. "Done" means it compiles and the wiring is structurally correct; the actual pixel-level visual match is a manual check (see step 4).

- [ ] **Step 1:** Add `var selectedCategory by remember { mutableStateOf<DdayCategory?>(null) }` (null = "전체"). Render the 4 tabs (전체/커플/친구/기타) as a `Row` of `Text`/clickable labels: active tab bold `#342923`, inactive `#887F7B` — tapping a tab sets `selectedCategory` to `null` (전체) or the matching `DdayCategory`. Compute the list to render as `filterByCategory(uiState.entries, selectedCategory)`.
- [ ] **Step 2:** Rebuild the top bar: "D-day!" title (`MaterialTheme.typography`'s title-mapped/Do-Hyeon style, `#332621`), `Icon(Icons.Filled.Add, ...)` and `Icon(Icons.Filled.Settings, ...)` as the two top-right actions (the gear icon has no destination yet in this plan — wire its `onClick` to a no-op `{}` with a `// TODO` comment noting it needs a destination once "더보기"/settings content is designed, per the spec's "범위 밖" note).
- [ ] **Step 3:** Rebuild each list row as a card: white (`#FFFFFF`) background, rounded corners (reuse whatever corner-radius token `WidgetlyTheme`/`WidgetlyButtonShape`'s sibling shape tokens already define for cards elsewhere in the app — if none exists, 20dp, matching the widget card radius documented in `docs/superpowers/specs/2026-10-11-visual-design-system-design.md`). Inside: a circular badge (`Color(entry.icon.badgeColorArgb)` background, an `Icon` in `Color(entry.icon.glyphColorArgb)` — map `DdayIcon.HEART/BOOK/CAKE/PLANE` to `Icons.Filled.Favorite/MenuBook/Cake/Flight` respectively), then title/number/date text (title + number bold Do-Hyeon-mapped style, date `#8D8581` Dongle-mapped style), then `mascotIcon(Modifier.size(54.dp))` on the trailing edge.
- [ ] **Step 4:** Add the icon/category pickers to the existing inline creation form: a `Row` of 4 selectable icon chips (one per `DdayIcon`, highlight the selected one) and a `Row` of 3 category chips (one per `DdayCategory`), both defaulting to the form's current `buildDdayEntry` defaults until tapped. Wire their selected values into the existing `buildDdayEntry(...)` call's new `category`/`icon` arguments (Task 2).
- [ ] **Step 5: Run** `./gradlew :shared-core:testAndroidHostTest :androidApp:assembleDebug` — expect SUCCESS.
- [ ] **Step 6: Commit**

---

### Task 6: `AppRoot` 4-tab bottom navigation

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/WidgetGalleryScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt`

**Interfaces:**
- Consumes: `DdayListScreen(viewModel, mascotIcon)` (Task 5's new signature), `mascotDrawable`/the existing `when`-mapping already used by `MascotBackground` in `shared/src/commonMain/kotlin/com/example/widgetly/theme/Mascot.kt` (read that file — reuse its existing id→`Res.drawable`/`Image` rendering logic for the new `mascotIcon` lambda rather than duplicating it; factor out a small shared composable there if that avoids copy-pasting the `when`, but don't force it if the existing code isn't structured to share cleanly — a short, clearly-labelled duplicate `when` is an acceptable fallback).
- Produces: `WidgetGalleryScreen()` — a bare `Scaffold` with a centered `Text("위젯갤러리 - 준비 중")`, nothing else; `AppScreen` enum becomes `{ HOME, WIDGET_GALLERY, THEME, MORE }`; a bottom `NavigationBar`/`Row` of 4 icon items (`Icons.Filled.Home`, `Icons.Filled.GridView`, `Icons.Filled.Palette`, `Icons.Filled.MoreHoriz`) replaces the current top `Row` of 2 buttons.

This task also has no new unit tests of its own — UI wiring over already-tested/already-built screens. "Done" means it compiles, all 4 tabs are reachable, and the fallback behavior named in Review Focus item 5 is visibly correct (see step 3).

- [ ] **Step 1:** Rename `AppScreen`'s values to `HOME`, `WIDGET_GALLERY`, `THEME`, `MORE` (was `DDAY_LIST`, `PAIRING`, `MASCOT_PICKER` plus no gallery case) and update every reference.
- [ ] **Step 2:** Replace the current top `Row` of 2 `Button`s with a bottom bar (placed after the content `Box`, not before it) of 4 tappable icon items using the 4 icons named above; the active tab's icon is tinted `MaterialTheme.colorScheme.primary`, inactive ones a muted color (reuse whatever `AppColorTokens` already exposes for a muted/secondary tone — read that file first rather than inventing a new token).
- [ ] **Step 3:** Build the `mascotIcon` lambda once at the top of `AppRoot` (not inside the `when`): `val myMascotId = mascotPickerViewModel.state.collectAsState().value.selectedMascotId` (this already defaults to `MascotCatalog.DEFAULT_MASCOT_ID` — i.e. quokka — until the async read resolves, per `MascotPickerState`'s existing default, which is exactly Review Focus item 5's required fallback), then a small `@Composable fun mascotIcon(modifier: Modifier) { /* same id→Res.drawable resolution MascotBackground already uses, at full opacity, no 18% alpha */ }`. Pass this down as `DdayListScreen(ddayViewModel, mascotIcon = ::mascotIcon)` (or an equivalent lambda) in the `HOME` branch of the `when`.
- [ ] **Step 4:** Wire the 4 branches: `HOME -> DdayListScreen(ddayViewModel, mascotIcon)`, `WIDGET_GALLERY -> WidgetGalleryScreen()`, `THEME -> MascotPickerScreen(mascotPickerViewModel, currentConnectionId)` (unchanged from today), `MORE -> PairingScreen(pairingViewModel)` (unchanged from today).
- [ ] **Step 5: Run** `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` — expect SUCCESS.
- [ ] **Step 6: Commit**

---

## Self-Review Notes

- **Spec coverage:** §1 데이터 모델 → Task 1-2; §2 홈 화면 UI → Task 5; §3 하단 네비게이션 → Task 6; §4 생성 폼 → Task 5 step 4. Every spec section has an owning task.
- **Review Focus coverage:** all 5 items map to a task's own test or an explicit implementation step, listed inline above.
- **Module boundary:** re-checked — Task 5's `DdayListScreen` never imports anything from `shared` or `Res.drawable`; the only place `Res.drawable`/Firebase-adjacent code appears is Task 6's `AppRoot`, which is already in `shared`.
- **Deliberately not covered by a task:** the gear/settings icon's destination (Task 5 step 2 leaves it as a no-op with a TODO, since the spec marks "더보기" tab's real content as out of scope) and "위젯갤러리"/"테마" tab content beyond what's already built — both match the spec's explicit 범위 밖 list.
