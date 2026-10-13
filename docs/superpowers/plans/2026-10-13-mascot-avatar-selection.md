# 마스코트 캐릭터 선택(아바타화) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user pick one of 5 mascot characters (quokka/cat/bear/rabbit/hamster) as "my character," changeable anytime from a new in-app screen, reflected on the personal D-day widget, the app background, and — when paired — on the couple/friend connection widget showing both the user's and their partner's characters side by side.

**Architecture:** A `MascotCatalog` (shared-core) mirrors the existing `ThemeCatalog` pattern. "My character" is a device-local, app-wide preference (`MascotPreferenceStorage`, mirroring `DdayStorage`'s DataStore/NSUserDefaults split) — not a per-widget config like theme. Sharing it with a paired partner goes through a new Cloud Functions callable `updateMascot` writing `connections/{id}.mascotByUid.{uid}`, because `firestore.rules` already blocks every direct client write to `connections/{connectionId}`. Reads need no new path: the in-app pairing flow already listens live on the connection doc, and both Glance widgets already do a one-shot read of the same `Connection` object off Firestore's local cache — `mascotByUid` just rides along.

**Tech Stack:** Kotlin Multiplatform (shared-core/shared), Jetpack Compose + Glance (Android), SwiftUI (iOS widget extension), Firebase Cloud Functions (TypeScript, Admin SDK), GitLive Firestore/Functions client SDK.

**Spec:** `docs/superpowers/specs/2026-10-13-mascot-avatar-selection-design.md`

## Global Constraints

- Exactly 5 catalog ids, fixed: `quokka`, `cat`, `bear`, `rabbit`, `hamster` — no custom upload, no per-character pricing (spec scope).
- `MascotCatalog.DEFAULT_MASCOT_ID = "quokka"` — any missing/unknown id (old client, un-set partner, stale cache) must resolve to this via fallback, never crash. Mirrors `ThemeCatalog.find`.
- `connections/{connectionId}` has zero direct client writes (`firestore.rules:23`, `allow list, create, update, delete: if false`). `mascotByUid` must be written only by a new Cloud Functions callable using the Admin SDK, exactly like every other write to that document (`sendSignal.ts`, `createPairingCode.ts`).
- Firestore database is named `"widgetly"`, not `"(default)"` — already wired via `functions/src/index.ts`'s exported `db`; no new wiring needed.
- **Scope correction from the spec:** the spec said the Android/iOS app-launch splash should also reflect the selected character. Neither platform's splash API supports a runtime-dynamic icon (Android's `SplashScreen` theme attribute and iOS's `INFOPLIST_KEY_UILaunchScreen_ImageName` are both resolved at build time, before any preference can be read) — and the original 10/11 mascot spec deliberately avoided a custom/hand-rolled splash to skip an artificial wait. This plan leaves the splash screen unchanged (fixed quokka icon, as today) and limits the "dynamic per selected character" surfaces to: the Compose app-background decoration, both Glance/WidgetKit widgets, and the new picker screen itself.
- The iOS widget extension target (`DdayWidgetExtensionExtension`) links only `SharedCore` (Firebase-free) — any iOS code this plan adds there must not import Firebase, matching the existing `ConnectionWidget.swift`/`DdayWidgetExtension.swift` pattern of reading cached/passed-in data only.

## Review Focus

- An unpaired user selects a character → must save locally only; calling `updateMascot` with no connection would throw. (Task 9)
- Partner hasn't picked any character yet (`mascotByUid` has no entry for their uid) → connection widget must fall back to `quokka`, not blank/crash. (Task 6, Task 11)
- `updateMascot` called with an id outside the 5-value catalog (stale client, tampered request) → callable rejects with `invalid-argument`, never writes garbage into `mascotByUid`. (Task 4)
- `updateMascot` called by a uid that isn't a member of the given connection → callable rejects, mirroring `sendSignal.ts`'s membership check. (Task 4)
- A connection document created before this feature shipped has no `mascotByUid` field at all (not even an empty map) → decoding it must default to `emptyMap()` without throwing, exactly like `lastSignalByUid` already does. (Task 6)

---

## File Structure

**New:**
- `shared-core/src/commonMain/kotlin/com/example/widgetly/theme/MascotCatalog.kt` — catalog, mirrors `Theme.kt`
- `shared-core/src/commonTest/kotlin/com/example/widgetly/theme/MascotCatalogTest.kt`
- `shared-core/src/commonMain/kotlin/com/example/widgetly/theme/MascotPreferenceStorage.kt` — interface
- `shared-core/src/androidMain/kotlin/com/example/widgetly/theme/AndroidMascotPreferenceStorage.kt`
- `shared-core/src/iosMain/kotlin/com/example/widgetly/theme/IosMascotPreferenceStorage.kt`
- `shared-core/src/androidHostTest/kotlin/com/example/widgetly/theme/AndroidMascotPreferenceStorageTest.kt`
- `shared-core/src/iosTest/kotlin/com/example/widgetly/theme/IosMascotPreferenceStorageTest.kt`
- `functions/src/lib/mascots.ts` — `MASCOT_IDS` allowlist shared by `updateMascot`/validation
- `functions/src/updateMascot.ts`
- `functions/test/updateMascot.test.ts`
- `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerEvent.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerState.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerEffect.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerViewModel.kt`
- `shared/src/commonTest/kotlin/com/example/widgetly/theme/MascotPickerViewModelTest.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerScreen.kt`
- `shared/src/commonMain/composeResources/drawable/{cat,bear,rabbit,hamster}.png`
- `androidApp/src/main/res/drawable-nodpi/{cat,bear,rabbit,hamster}.png` + `{cat,bear,rabbit,hamster}_faint.png`
- `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/ConnectionMascotCornerDecorationTest.kt`
- `iosApp/DdayWidgetExtension/Assets.xcassets/{Cat,Bear,Rabbit,Hamster}.imageset/*`

**Modify:**
- `functions/src/lib/types.ts` — `ConnectionDoc.mascotByUid`
- `functions/src/index.ts` — register `updateMascot`
- `functions/src/getConnection.ts` + `functions/test/getConnection.test.ts` — passthrough `mascotByUid`
- `shared/src/commonMain/kotlin/com/example/widgetly/connection/Connection.kt` — `ConnectionDoc`/`Connection` += `mascotByUid`
- `shared/src/commonTest/kotlin/com/example/widgetly/connection/WireShapeTest.kt`, `ConnectionMappingTest.kt`
- `shared/src/commonMain/kotlin/com/example/widgetly/connection/WidgetlyFunctions.kt` + `FirebaseWidgetlyFunctions.kt` — `updateMascot`
- `shared/src/commonTest/kotlin/com/example/widgetly/push/FcmTokenSyncTest.kt` — `FakeWidgetlyFunctions.updateMascot`
- `shared/src/commonMain/kotlin/com/example/widgetly/theme/Mascot.kt` — `MascotBackground(mascotId, modifier)`
- `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt` — 3rd screen + wiring
- `androidApp/src/main/kotlin/com/example/widgetly/widget/MascotCornerDecoration.kt` — id-aware + new `ConnectionMascotCornerDecoration`
- `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt`, `ConnectionGlanceWidget.kt`
- `androidApp/src/main/kotlin/com/example/widgetly/WidgetlyApplication.kt` — `mascotPreferenceStorage` accessor
- `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/MascotCornerDecorationTest.kt`, `DdayGlanceWidgetTest.kt`, `ConnectionGlanceWidgetTest.kt`
- `iosApp/DdayWidgetExtension/ConnectionWidget.swift`, `DdayWidgetExtension.swift`
- `docs/cross-platform-verification.md`

---

### Task 1: MascotCatalog

**Files:**
- Create: `shared-core/src/commonMain/kotlin/com/example/widgetly/theme/MascotCatalog.kt`
- Test: `shared-core/src/commonTest/kotlin/com/example/widgetly/theme/MascotCatalogTest.kt`

**Interfaces:**
- Produces: `data class Mascot(val id: String, val name: String)`; `object MascotCatalog { const val DEFAULT_MASCOT_ID = "quokka"; val all: List<Mascot>; fun find(id: String): Mascot }`

- [ ] **Step 1: Write the failing tests**, mirroring `ThemeCatalogTest.kt`'s structure:
```kotlin
@Test fun `catalog contains the default mascot`()
@Test fun `all mascot ids are unique`()
@Test fun `find falls back to the default mascot for an unknown id`()
@Test fun `find returns the matching mascot when the id exists`()
@Test fun `catalog has exactly the five mascots with the right names and default`() {
    assertEquals("quokka", MascotCatalog.DEFAULT_MASCOT_ID)
    assertEquals(
        mapOf("quokka" to "쿼카", "cat" to "고양이", "bear" to "곰", "rabbit" to "토끼", "hamster" to "햄스터"),
        MascotCatalog.all.associate { it.id to it.name },
    )
}
```
- [ ] **Step 2: Run** `./gradlew :shared-core:testAndroidHostTest --tests "*.MascotCatalogTest"` — expect FAIL (`MascotCatalog` unresolved)
- [ ] **Step 3: Implement** `Mascot`/`MascotCatalog` exactly mirroring `Theme.kt`'s `find` fallback logic, with the 5 id/name pairs above.
- [ ] **Step 4: Run** the same test command — expect PASS
- [ ] **Step 5: Commit**

```bash
git add shared-core/src/commonMain/kotlin/com/example/widgetly/theme/MascotCatalog.kt shared-core/src/commonTest/kotlin/com/example/widgetly/theme/MascotCatalogTest.kt
git commit -m "feat: add MascotCatalog"
```

---

### Task 2: MascotPreferenceStorage (local "my character")

**Files:**
- Create: `shared-core/src/commonMain/kotlin/com/example/widgetly/theme/MascotPreferenceStorage.kt`
- Create: `shared-core/src/androidMain/kotlin/com/example/widgetly/theme/AndroidMascotPreferenceStorage.kt`
- Create: `shared-core/src/iosMain/kotlin/com/example/widgetly/theme/IosMascotPreferenceStorage.kt`
- Test: `shared-core/src/androidHostTest/kotlin/com/example/widgetly/theme/AndroidMascotPreferenceStorageTest.kt`
- Test: `shared-core/src/iosTest/kotlin/com/example/widgetly/theme/IosMascotPreferenceStorageTest.kt`

**Interfaces:**
- Consumes: `MascotCatalog.DEFAULT_MASCOT_ID` (Task 1)
- Produces: `interface MascotPreferenceStorage { suspend fun read(): String; suspend fun write(mascotId: String) }` — `read()` returns `MascotCatalog.DEFAULT_MASCOT_ID` when nothing is stored yet (never null, never throws).

- [ ] **Step 1: Write the failing tests** for both platform impls, mirroring `AndroidDdayStorageTest.kt`/`IosDdayStorageTest.kt`'s structure (construct with an isolated test DataStore/UserDefaults suite, assert `read()` defaults to `"quokka"` before any `write()`, and round-trips after one).
- [ ] **Step 2: Run** `./gradlew :shared-core:testAndroidHostTest --tests "*.AndroidMascotPreferenceStorageTest"` and `:shared-core:iosSimulatorArm64Test --tests "*.IosMascotPreferenceStorageTest"` — expect FAIL
- [ ] **Step 3: Implement** the interface + both impls, exactly mirroring `AndroidDdayStorage`/`IosDdayStorage`'s DataStore name / `NSUserDefaults(suiteName: "group.com.example.widgetly")` pattern. Use DataStore name `"mascot_prefs"` and key `"selected_mascot_id"` on Android; the same string key `"selected_mascot_id"` on iOS (this exact key name is also what Task 12's iOS widget extension Swift code reads directly — keep it identical).
- [ ] **Step 4: Run** the same test commands — expect PASS
- [ ] **Step 5: Commit**

---

### Task 3: Firestore `mascotByUid` schema (functions side)

**Files:**
- Create: `functions/src/lib/mascots.ts`
- Modify: `functions/src/lib/types.ts`

**Interfaces:**
- Produces: `export const MASCOT_IDS = ["quokka", "cat", "bear", "rabbit", "hamster"] as const; export type MascotId = typeof MASCOT_IDS[number];`
- Produces: `ConnectionDoc.mascotByUid?: Record<string, string>` (optional — old docs have none)

- [ ] **Step 1: Add** `functions/src/lib/mascots.ts` with `MASCOT_IDS`/`MascotId` as above.
- [ ] **Step 2: Modify** `ConnectionDoc` in `functions/src/lib/types.ts` to add `mascotByUid?: Record<string, string>;` under the existing `lastSignalByUid` field.
- [ ] **Step 3: Run** `npx firebase-tools emulators:exec --only firestore --project widgetly-53e09 "npm --prefix functions test"` — expect the existing suite still PASSes (pure type-level addition, nothing to newly test here; Task 4/5 add the behavioral tests).
- [ ] **Step 4: Commit**

---

### Task 4: `updateMascot` Cloud Functions callable

**Files:**
- Create: `functions/src/updateMascot.ts`
- Test: `functions/test/updateMascot.test.ts`
- Modify: `functions/src/index.ts`

**Interfaces:**
- Consumes: `db` export from `./index`; `MASCOT_IDS`/`MascotId` (Task 3); `ConnectionDoc` (Task 3)
- Produces: `export async function updateMascotLogic(uid: string, input: { connectionId: string; mascotId: string }, firestore: Firestore): Promise<void>`; `export const updateMascot = onCall<{ connectionId: string; mascotId: string }>(...)`

- [ ] **Step 1: Write the failing tests** in `functions/test/updateMascot.test.ts`, mirroring `sendSignal.test.ts`'s harness (fake/emulator `Firestore`, seeded `connections/{id}` doc):
```ts
test("writes mascotByUid.{uid} on an active connection the caller belongs to")
test("rejects an id outside the 5-value catalog with invalid-argument")
test("rejects a caller who is not a member of the connection")
test("rejects when the connection does not exist")
test("does not touch the partner's existing mascotByUid entry")
```
- [ ] **Step 2: Run** `npx firebase-tools emulators:exec --only firestore --project widgetly-53e09 "npm --prefix functions test -- updateMascot"` — expect FAIL
- [ ] **Step 3: Implement** `updateMascotLogic`/`updateMascot`, mirroring `sendSignal.ts`'s transaction shape (`tx.get` the connection, check `connection.status === "active" && connection.memberUids.includes(uid)`, throw `HttpsError("invalid-argument", ...)` if `!MASCOT_IDS.includes(input.mascotId as MascotId)`, then `tx.update(ref, { [`mascotByUid.${uid}`]: input.mascotId })` — the dot-path key merges into the map without touching other entries, same as Firestore's `FieldValue`-free dot-notation update elsewhere in this codebase). No rate limit, no push notification — this is cosmetic, unlike `sendSignal`.
- [ ] **Step 4: Register** `export { updateMascot } from "./updateMascot";` in `functions/src/index.ts`.
- [ ] **Step 5: Run** the test command again — expect PASS
- [ ] **Step 6: Commit**

---

### Task 5: `getConnection` passthrough

**Files:**
- Modify: `functions/src/getConnection.ts`
- Modify: `functions/test/getConnection.test.ts`

**Interfaces:**
- Consumes: `ConnectionDoc.mascotByUid` (Task 3)
- Produces: `GetConnectionResult.mascotByUid: Record<string, string>`

- [ ] **Step 1: Write the failing test** — a connection with `mascotByUid` set returns it unchanged in the result; a connection with no `mascotByUid` field returns `{}`.
- [ ] **Step 2: Run** `npx firebase-tools emulators:exec --only firestore --project widgetly-53e09 "npm --prefix functions test -- getConnection"` — expect FAIL
- [ ] **Step 3: Implement** — add `mascotByUid: Record<string, string>` to the `GetConnectionResult` type and `mascotByUid: connection.mascotByUid ?? {}` to the returned object (same `?? {}` guard pattern `lastSignalByUid` would need if it weren't already defaulted upstream).
- [ ] **Step 4: Run** the test again — expect PASS
- [ ] **Step 5: Commit**

---

### Task 6: `Connection`/`ConnectionDoc` wire shape (Kotlin client)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/connection/Connection.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/widgetly/connection/WireShapeTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/widgetly/connection/ConnectionMappingTest.kt`

**Interfaces:**
- Produces: `ConnectionDoc.mascotByUid: Map<String, String> = emptyMap()`; `Connection.mascotByUid: Map<String, String>`

- [ ] **Step 1: Write the failing tests**:
  - In `WireShapeTest.kt`: decoding a `ConnectionDoc` JSON with a `mascotByUid` object produces the matching map; decoding one **without** the field at all does not throw and yields `emptyMap()` (this is the Review Focus case — old connections predate this field).
  - In `ConnectionMappingTest.kt`: `connectionFromDocument` carries `mascotByUid` through unchanged into the domain `Connection`.
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests "*.WireShapeTest" --tests "*.ConnectionMappingTest"` — expect FAIL
- [ ] **Step 3: Implement** — add `val mascotByUid: Map<String, String> = emptyMap()` to both `ConnectionDoc` and `Connection`, and copy it through in `connectionFromDocument`.
- [ ] **Step 4: Run** the test command again — expect PASS
- [ ] **Step 5: Commit**

---

### Task 7: `WidgetlyFunctions.updateMascot` (client)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/connection/WidgetlyFunctions.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/connection/FirebaseWidgetlyFunctions.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/widgetly/push/FcmTokenSyncTest.kt` (where `FakeWidgetlyFunctions` lives)

**Interfaces:**
- Produces: `WidgetlyFunctions.updateMascot(connectionId: String, mascotId: String)` (suspend, no return value)

- [ ] **Step 1: Add** `suspend fun updateMascot(connectionId: String, mascotId: String)` to the `WidgetlyFunctions` interface.
- [ ] **Step 2: Implement** in `FirebaseWidgetlyFunctions`: `functions.httpsCallable("updateMascot").invoke(mapOf("connectionId" to connectionId, "mascotId" to mascotId))` — no response body to decode (callable returns void on success, throws on failure, same shape the backend task gives it).
- [ ] **Step 3: Extend `FakeWidgetlyFunctions`** in `FcmTokenSyncTest.kt` with an `updateMascot` override recording its args (e.g. `var updateMascotCalls: MutableList<Pair<String, String>>`) and an optional `shouldThrow` it already supports for other methods — this is what Task 9's `MascotPickerViewModelTest` will assert against.
- [ ] **Step 4: Run** `./gradlew :shared:testAndroidHostTest` — expect existing suite still PASSes (no new assertions yet; Task 9 exercises the fake).
- [ ] **Step 5: Commit**

---

### Task 8: Character assets

Not a TDD task (binary assets) — verified by the build compiling and the preview checks below, not unit tests.

**Files:**
- Create: `shared/src/commonMain/composeResources/drawable/{cat,bear,rabbit,hamster}.png`
- Create: `androidApp/src/main/res/drawable-nodpi/{cat,bear,rabbit,hamster}.png` and `{cat,bear,rabbit,hamster}_faint.png`
- Create: `iosApp/DdayWidgetExtension/Assets.xcassets/{Cat,Bear,Rabbit,Hamster}.imageset/*` (image + `Contents.json`, mirroring the existing `Quokka.imageset`)
- (iOS main app assets are **not** needed — the picker screen and app background are Compose Multiplatform, served from `shared`'s `composeResources`, same as the existing `quokka` resource.)

- [ ] **Step 1:** Starting from the transparent, background-removed cat/bear/rabbit/hamster cutouts already produced during the open-design mockup pass (`final_{cat,bear,rabbit,hamster}.png` in this session's scratchpad), review each at full size and touch up any stray background-removal artifacts around fur/ear edges before treating them as production assets — the mockup pass only needed "good enough to compare," this is the real bundled asset.
- [ ] **Step 2:** Copy each into `shared/src/commonMain/composeResources/drawable/` as `cat.png`/`bear.png`/`rabbit.png`/`hamster.png`, and into `androidApp/src/main/res/drawable-nodpi/` under the same names, sized/compressed comparably to the existing `quokka.png` in each location.
- [ ] **Step 3:** For each of the 4, bake a `_faint` alpha variant at the same ratio as the existing pair (`quokka_faint.png` carries ~30% of `quokka.png`'s alpha — sample-verified: alpha 255→76). Multiply each pixel's alpha channel by that ratio; save into `androidApp/src/main/res/drawable-nodpi/`.
- [ ] **Step 4:** Add the 4 iOS widget-extension image sets under `iosApp/DdayWidgetExtension/Assets.xcassets/`, copying the existing `Quokka.imageset/Contents.json` structure for each.
- [ ] **Step 5:** `./gradlew :shared:build :androidApp:assembleDebug` — expect SUCCESS (new resources compile/package).
- [ ] **Step 6: Commit**

---

### Task 9: MascotPickerViewModel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerEvent.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerState.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerEffect.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/theme/MascotPickerViewModelTest.kt`

**Interfaces:**
- Consumes: `MascotPreferenceStorage` (Task 2), `MascotCatalog.DEFAULT_MASCOT_ID` (Task 1), `WidgetlyFunctions.updateMascot` (Task 7)
- Produces:
  - `data class MascotPickerState(val selectedMascotId: String = MascotCatalog.DEFAULT_MASCOT_ID)`
  - `sealed interface MascotPickerEvent { data class Select(val mascotId: String, val connectionId: String?) : MascotPickerEvent }`
  - `sealed interface MascotPickerEffect { data class ShowError(val message: String) : MascotPickerEffect }`
  - `class MascotPickerViewModel(storage: MascotPreferenceStorage, functions: WidgetlyFunctions) : ViewModel() { val state: StateFlow<MascotPickerState>; val effects: Flow<MascotPickerEffect>; fun onEvent(event: MascotPickerEvent) }`

`connectionId` travels on the `Select` event itself (not held by the ViewModel) — this is how an unpaired selection is told apart from a paired one without the ViewModel tracking pairing state of its own (Review Focus item 1).

- [ ] **Step 1: Write the failing tests**, mirroring `PairingViewModelTest.kt`'s `FakeWidgetlyFunctions` usage:
```kotlin
@Test fun `state reflects the storage's current value on init`()
@Test fun `Select with connectionId null saves locally and never calls updateMascot`()
@Test fun `Select with a connectionId saves locally and calls updateMascot with the same id`()
@Test fun `a failed updateMascot emits ShowError but the local selection still sticks`()
```
(Use an in-memory fake `MascotPreferenceStorage` for these — a `MutableMap`-backed one is enough, no need for a shared fake file.)
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests "*.MascotPickerViewModelTest"` — expect FAIL
- [ ] **Step 3: Implement** `MascotPickerViewModel`: on init, launch a coroutine reading `storage.read()` into `_state`; on `Select`, write locally first (so the UI reflects the tap immediately regardless of network), then — only if `event.connectionId != null` — call `functions.updateMascot(event.connectionId, event.mascotId)` inside a try/catch that emits `MascotPickerEffect.ShowError` on failure without reverting the local state.
- [ ] **Step 4: Run** the test again — expect PASS
- [ ] **Step 5: Commit**

---

### Task 10: MascotPickerScreen + AppRoot wiring + MascotBackground parameterization

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/theme/Mascot.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/MascotPickerScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt`

**Interfaces:**
- Consumes: `MascotCatalog.all` (Task 1), `MascotPickerViewModel`/state/event (Task 9)
- Produces: `MascotBackground(mascotId: String, modifier: Modifier = Modifier)` (breaking change to its existing zero-arg-besides-modifier signature); `MascotPickerScreen(viewModel: MascotPickerViewModel, currentConnectionId: String?)`

- [ ] **Step 1: Modify `MascotBackground`** in `Mascot.kt` to take `mascotId: String` and resolve it to the matching `Res.drawable.{quokka,cat,bear,rabbit,hamster}` via a local `when` (five Compose resource references can't be looked up by a dynamic string — Compose Multiplatform resource accessors are compile-time generated vals, not resolvable by name — so this `when` is the one place that maps `MascotCatalog` ids to the 5 generated accessors).
- [ ] **Step 2: Write `MascotPickerScreen`**: a grid (`LazyVerticalGrid` or a simple `Row`/`Column` wrap, matching this app's existing light-UI style — no library beyond what `DdayListScreen`/`PairingScreen` already use) over `MascotCatalog.all`, each cell showing the resolved character image (reuse the same `when`-mapping as `MascotBackground`, or factor it into a small shared `mascotPainter(id: String)` helper both call) and a selected-state outline when `it.id == state.selectedMascotId`, calling `viewModel.onEvent(MascotPickerEvent.Select(it.id, currentConnectionId))` on tap.
- [ ] **Step 3: Wire into `AppRoot`**: add `MASCOT_PICKER` to the `AppScreen` enum, construct a `MascotPickerViewModel` alongside the existing `ddayViewModel`/`pairingViewModel` parameters (new `AppRoot` parameter, like the other two), add a way to reach it from the existing single nav button area (a third small button/row, following the existing unstyled-`Button`+`WidgetlyButtonShape` pattern already there), and pass `mascotPickerViewModel.state.collectAsState().value.selectedMascotId` into `MascotBackground(...)` at its current call site instead of the old no-arg call. `currentConnectionId` for the picker comes from observing `pairingViewModel.state` for a `PairingUiState.Connected` case (null otherwise).
- [ ] **Step 4: Run** `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` — expect SUCCESS (this task is UI wiring; Task 9 already covers the ViewModel logic under test — there is no new testable logic here beyond what compiles).
- [ ] **Step 5: Commit**

---

### Task 11: Android D-day widget — selected character

**Files:**
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/MascotCornerDecoration.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/WidgetlyApplication.kt`
- Modify: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/MascotCornerDecorationTest.kt`
- Modify: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/DdayGlanceWidgetTest.kt`

**Interfaces:**
- Consumes: `MascotPreferenceStorage`/`AndroidMascotPreferenceStorage` (Task 2), `MascotCatalog` (Task 1)
- Produces: `WidgetlyApplication.mascotPreferenceStorage: MascotPreferenceStorage` (companion accessor, mirrors `ddayRepository`); `MascotCornerDecoration(mascotId: String)`; `EmptyStateWithMascot(mascotId: String, message: String, style: TextStyle)`; `internal fun mascotDrawableRes(id: String): Int` / `internal fun mascotFaintDrawableRes(id: String): Int` (both in `MascotCornerDecoration.kt`, `when`-mapping the 5 catalog ids to `R.drawable.*`, falling back to the quokka resources for any unrecognized id — same fallback discipline as `MascotCatalog.find`)

- [ ] **Step 1: Write the failing tests**, updating `MascotCornerDecorationTest.kt`'s three existing tests to pass a `mascotId` (e.g. `"cat"`) and assert on a `"cat_corner"`-style content description instead of the hardcoded `"quokka_corner"` (change `MASCOT_CONTENT_DESCRIPTION` from a constant to `"${mascotId}_corner"`), plus one new case confirming an unknown id falls back to the quokka drawable rather than crashing. Mirror the same `mascotId` threading into the relevant `DdayGlanceWidgetTest.kt` cases (empty-state and data-state).
- [ ] **Step 2: Run** `./gradlew :androidApp:connectedAndroidTest --tests "*.MascotCornerDecorationTest" --tests "*.DdayGlanceWidgetTest"` — expect FAIL
- [ ] **Step 3: Implement**:
  - Add `mascotPreferenceStorage` to `WidgetlyApplication`, same `by lazy { AndroidMascotPreferenceStorage(this) }` + companion `val` pattern as `ddayRepository`.
  - `MascotCornerDecoration(mascotId: String)` and `EmptyStateWithMascot(mascotId: String, message: String, style: TextStyle)` resolve their drawable via the new `mascotDrawableRes`/`mascotFaintDrawableRes` functions instead of the hardcoded `R.drawable.quokka`/`R.drawable.quokka_faint`.
  - In `DdayGlanceWidget.provideGlance`, read `WidgetlyApplication.mascotPreferenceStorage.read()` the same one-shot way `connectionState` is already read (a `produceState`, since it's a suspend call), and thread the result into `DdayWidgetContent`'s new `mascotId: String` parameter.
- [ ] **Step 4: Run** the test commands again — expect PASS
- [ ] **Step 5: Commit**

---

### Task 12: Android connection widget — paired characters side by side

**Files:**
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/MascotCornerDecoration.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidget.kt`
- Create: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/ConnectionMascotCornerDecorationTest.kt`
- Modify: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/ConnectionGlanceWidgetTest.kt`

**Interfaces:**
- Consumes: `Connection.mascotByUid` (Task 6), `MascotCatalog.find` (Task 1), `WidgetlyApplication.mascotPreferenceStorage`/`currentUid` (Task 11, existing)
- Produces: `ConnectionMascotCornerDecoration(myMascotId: String, partnerMascotId: String)` — always renders exactly 2 icons, their pixel size (not count) scaled by `LocalSize.current` the same 3 buckets `mascotCountForSize` already recognizes.

- [ ] **Step 1: Write the failing tests** in `ConnectionMascotCornerDecorationTest.kt`, mirroring `MascotCornerDecorationTest.kt`'s `runGlanceAppWidgetUnitTest` harness: at every one of the 3 sizes, exactly one node with content description `"${myMascotId}_corner"` and exactly one with `"${partnerMascotId}_corner"` (never 1 or 3 — this is the behavioral difference from the solo decoration). Update `ConnectionGlanceWidgetTest.kt`'s data-state case to assert both icons appear using the test's fixed `connection.mascotByUid` map.
- [ ] **Step 2: Run** `./gradlew :androidApp:connectedAndroidTest --tests "*.ConnectionMascotCornerDecorationTest" --tests "*.ConnectionGlanceWidgetTest"` — expect FAIL
- [ ] **Step 3: Implement** `ConnectionMascotCornerDecoration` in `MascotCornerDecoration.kt` (two `Image`s side by side in a `Row`, each sized by a small size-bucket→dp map instead of `mascotCountForSize`'s count). In `ConnectionGlanceWidget.kt`: read `WidgetlyApplication.mascotPreferenceStorage.read()` the same one-shot way as Task 11; derive `partnerMascotId` as `myUid?.let { connection.memberUids.firstOrNull { m -> m != it } }?.let { connection.mascotByUid[it] } ?: MascotCatalog.DEFAULT_MASCOT_ID` (Review Focus: partner hasn't picked yet → falls back to quokka, not blank); replace the bare `MascotCornerDecoration()` call at the data-state branch with `ConnectionMascotCornerDecoration(myMascotId, partnerMascotId)`; thread `myMascotId` into the two `EmptyStateWithMascot(...)` call sites the same way Task 11 did for the D-day widget.
- [ ] **Step 4: Run** the test commands again — expect PASS
- [ ] **Step 5: Commit**

---

### Task 13: iOS mirror (best-effort, Android-verified only)

**Files:**
- Modify: `iosApp/DdayWidgetExtension/ConnectionWidget.swift`
- Modify: `iosApp/DdayWidgetExtension/DdayWidgetExtension.swift`
- Modify: `docs/cross-platform-verification.md`

**Interfaces:**
- Consumes: the same `"selected_mascot_id"` UserDefaults key Task 2's `IosMascotPreferenceStorage` writes (suite `"group.com.example.widgetly"`), read directly in Swift — the extension links only `SharedCore`, so it does not call into `IosMascotPreferenceStorage` itself, it just has to agree on the same key name (Global Constraints).
- Produces: `ConnectionSnapshot` (in `ConnectionWidget.swift`) gains a `mascotByUid: [String: String]` field, `Codable`-decoded (missing key ⇒ `nil` ⇒ treat as `[:]`, per that file's existing Codable-missing-key note).

- [ ] **Step 1:** In `ConnectionWidget.swift`, add `mascotByUid: [String: String]?` to `ConnectionSnapshot`, and derive/display `myMascotId`/`partnerMascotId` the same way `ConnectionGlanceWidget.kt` does (own uid read from the UserDefaults key above, defaulting to `"quokka"`; partner's looked up from `mascotByUid`, defaulting to `"quokka"` if absent) — swap the view's existing fixed mascot image for these two, side by side.
- [ ] **Step 2:** In `DdayWidgetExtension.swift`, swap the fixed mascot image for the one read from the same UserDefaults key.
- [ ] **Step 3: Run** `xcodebuild test -project iosApp/iosApp.xcodeproj -scheme DdayWidgetExtensionTests -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=NO` — this target has no Firebase SPM dependency, so it runs despite the rest of the iOS app being unbuildable; expect PASS (or fix until it does).
- [ ] **Step 4: Run** the link-check regression test: `xcodebuild -project iosApp/iosApp.xcodeproj -target DdayWidgetExtensionExtension -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — expect SUCCESS (confirms the extension still links `SharedCore` only, no accidental `Shared`/Firebase import).
- [ ] **Step 5:** Add a row to `docs/cross-platform-verification.md` recording that this feature's iOS half (widget extension mascot rendering, and the entire app-shell picker screen / connection-screen `updateMascot` call) is implemented but unverified on a simulator/device — per this project's standing Android-only testing constraint.
- [ ] **Step 6: Commit**

---

## Self-Review Notes

- **Spec coverage:** catalog (Task 1), local storage (Task 2), Firestore schema + callable (Tasks 3-5), client wire shape + functions client (Tasks 6-7), assets (Task 8), picker UI (Tasks 9-10), both widgets (Tasks 11-12), iOS (Task 13) — every section of the spec has an owning task. The splash-screen line is deliberately **not** implemented, with the reason recorded in Global Constraints rather than silently dropped.
- **Review Focus coverage:** all 5 items map to a task's own test, listed inline above.
- **Fallback consistency:** every read path (`MascotCatalog.find`, `AndroidMascotPreferenceStorage`/`IosMascotPreferenceStorage` defaults, the connection widget's partner lookup, the Android drawable-resolution `when`s) resolves an unknown/missing id to `"quokka"` the same way `ThemeCatalog`/theme already does — no new failure mode introduced.
