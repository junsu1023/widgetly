# 개인 디데이 위젯 코어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 개인 디데이(수능, 생일 등)를 만들고, Android/iOS 홈 화면 위젯으로 확인할 수 있게 한다. 백엔드 없이 기기 로컬 저장만 사용한다.

**Architecture:** `shared`(commonMain)가 디데이 모델/계산 로직/저장 인터페이스를 제공한다. Android는 같은 프로세스에서 Glance 위젯이 이 로직을 직접 호출한다. iOS는 WidgetKit 익스텐션(별도 프로세스)이 Shared.framework를 링크해 같은 계산 로직을 Swift에서 호출하고, 저장은 App Group 공유 컨테이너로 메인 앱과 공유한다. 위젯 UI 자체는 플랫폼별로 따로 그린다.

**Tech Stack:** Kotlin Multiplatform, kotlinx-datetime, kotlinx-serialization, Compose Multiplatform(앱 화면), AndroidX DataStore(Android 저장), Android Glance(Android 위젯), WidgetKit + AppIntents(iOS 위젯, 최소 iOS 17)

**Spec:** `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` (섹션 2 아키텍처 원칙, 섹션 3의 "로컬 D-day" 행), `docs/widget-development.md`

## Global Constraints

- iOS 최소 타겟 17 (스펙 섹션 2)
- Android minSdk 24 (기존 `gradle/libs.versions.toml`)
- 위젯 UI는 공유하지 않는다 — `shared`는 로직만 제공 (스펙 섹션 2)
- App Group id는 `group.com.example.widgetly`로 고정 — 메인 앱과 위젯 익스텐션 양쪽 타겟의 Signing & Capabilities에 동일하게 등록해야 한다
- 패키지 베이스는 `com.example.widgetly` (기존 `androidApp`/`shared` namespace와 동일)

## Review Focus

- 생일이 2/29이고 `repeat = YEARLY`인데 올해가 윤년이 아닐 때, 다음 발생일을 계산할 수 있어야 한다 (2/28 또는 3/1로 처리) — Task 2에서 테스트
- `today`는 기기의 로컬 타임존 기준 날짜여야 한다 — UTC로 계산하면 자정 근처에서 날짜가 하루 틀리게 보인다 — Task 2에서 명시
- 제목이 빈 문자열/공백뿐인 엔트리는 저장되지 않아야 한다 — Task 3에서 테스트
- 위젯이 가리키는 D-day 엔트리가 삭제된 뒤에도 위젯이 크래시 없이 "삭제됨" 상태를 보여줘야 한다 — Task 5, 6에서 처리
- 같은 id로 다시 저장(수정)하면 목록에 중복이 생기지 않고 기존 항목이 교체되어야 한다 — Task 3에서 테스트

---

### Task 1: DdayEntry 모델 + 의존성 설정

**Files:**
- Modify: `gradle/libs.versions.toml` — `kotlinx-datetime`, `kotlinx-serialization-json` 버전/라이브러리, `kotlin.plugin.serialization` 플러그인 추가
- Modify: `shared/build.gradle.kts` — `kotlin.plugin.serialization` 플러그인 적용, `commonMain.dependencies`에 두 라이브러리 추가
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntry.kt`
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayEntrySerializationTest.kt`

**Interfaces:**
- Produces: `data class DdayEntry(val id: String, val title: String, val targetDate: LocalDate, val countMode: CountMode = CountMode.START_AT_ZERO, val repeat: RepeatMode = RepeatMode.NONE)`, `enum class CountMode { START_AT_ZERO, START_AT_ONE }`, `enum class RepeatMode { NONE, YEARLY }` — 모두 `@Serializable`

- [ ] **Step 1: Write the failing test**

```kotlin
class DdayEntrySerializationTest {
    @Test
    fun `round-trips through JSON`() {
        val entry = DdayEntry(
            id = "abc-123",
            title = "수능",
            targetDate = LocalDate(2026, 11, 12),
            countMode = CountMode.START_AT_ONE,
            repeat = RepeatMode.NONE,
        )
        val json = Json.encodeToString(entry)
        val decoded = Json.decodeFromString<DdayEntry>(json)
        assertEquals(entry, decoded)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayEntrySerializationTest"`
Expected: FAIL — `DdayEntry`를 찾을 수 없음(컴파일 에러)

- [ ] **Step 3: libs.versions.toml / shared/build.gradle.kts에 의존성 추가, `DdayEntry.kt` 작성**

위 Interfaces 블록의 시그니처 그대로. `LocalDate`는 kotlinx-datetime의 타입을 그대로 쓴다.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayEntrySerializationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntry.kt shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayEntrySerializationTest.kt
git commit -m "feat: add DdayEntry model"
```

---

### Task 2: DdayCalculator — 디데이 계산 로직

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayCalculator.kt`
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayCalculatorTest.kt`

**Interfaces:**
- Consumes: `DdayEntry`, `CountMode`, `RepeatMode` (Task 1)
- Produces: `data class DdayResult(val label: String, val calendarDiffDays: Int)`, `fun calculateDday(entry: DdayEntry, today: LocalDate): DdayResult`

**계산 규칙 (고정값):**
1. `repeat == YEARLY`면 `targetDate`의 (월, 일)을 기준으로 `today`와 같거나 이후인 가장 가까운 발생일을 `effectiveTarget`으로 삼는다(이미 지났으면 다음 해로). `repeat == NONE`이면 `effectiveTarget = targetDate`.
   - 2/29 생일이고 해당 연도가 윤년이 아니면 `effectiveTarget`은 그 해의 2/28으로 대체한다.
2. `calendarDiffDays = effectiveTarget.toEpochDays() - today.toEpochDays()`
3. `calendarDiffDays == 0` → `label = "D-DAY"`
4. `calendarDiffDays > 0` (미래) → `displayNumber = calendarDiffDays + (1.takeIf { entry.countMode == CountMode.START_AT_ONE } ?: 0)`, `label = "D-$displayNumber"`
5. `calendarDiffDays < 0` (과거) → `displayNumber = -calendarDiffDays + (1.takeIf { entry.countMode == CountMode.START_AT_ONE } ?: 0)`, `label = "D+$displayNumber"`
6. `today`는 항상 호출자가 `Clock.System.todayIn(TimeZone.currentSystemDefault())`로 구해서 넘긴다 — 함수 내부에서 UTC나 시스템 기본값을 임의로 재계산하지 않는다(디바이스 로컬 타임존 보장, Review Focus 2번).

- [ ] **Step 1: Write the failing tests**

```kotlin
class DdayCalculatorTest {
    @Test
    fun `today is D-DAY`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 10, 4), CountMode.START_AT_ZERO, RepeatMode.NONE)
        assertEquals(DdayResult("D-DAY", 0), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `future date with START_AT_ZERO`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 10, 7), CountMode.START_AT_ZERO, RepeatMode.NONE)
        assertEquals(DdayResult("D-3", 3), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `future date with START_AT_ONE adds one to the displayed number`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 10, 7), CountMode.START_AT_ONE, RepeatMode.NONE)
        assertEquals(DdayResult("D-4", 3), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `past date counts up as D+n`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 9, 29), CountMode.START_AT_ZERO, RepeatMode.NONE)
        assertEquals(DdayResult("D+5", -5), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `yearly repeat rolls over to next year once this year's date has passed`() {
        val entry = DdayEntry("1", "bday", LocalDate(1999, 10, 1), CountMode.START_AT_ZERO, RepeatMode.YEARLY)
        // 2026-10-01은 이미 지났으므로 2027-10-01까지 계산
        assertEquals(DdayResult("D-362", 362), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `yearly repeat on a leap-day birthday falls back to Feb 28 in a non-leap year`() {
        val entry = DdayEntry("1", "leap", LocalDate(2000, 2, 29), CountMode.START_AT_ZERO, RepeatMode.YEARLY)
        // 2027은 윤년이 아님 -> 2/28로 대체, today=2027-02-28 -> D-DAY
        assertEquals(DdayResult("D-DAY", 0), calculateDday(entry, LocalDate(2027, 2, 28)))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayCalculatorTest"`
Expected: FAIL — `calculateDday` 없음

- [ ] **Step 3: `DdayCalculator.kt`에 위 계산 규칙 그대로 구현**

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayCalculatorTest"`
Expected: PASS (6개 테스트 모두)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayCalculator.kt shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayCalculatorTest.kt
git commit -m "feat: add D-day calculation logic"
```

---

### Task 3: DdayStorage + DdayRepository

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayStorage.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayRepository.kt`
- Create: `shared/src/androidMain/kotlin/com/example/widgetly/dday/AndroidDdayStorage.kt`
- Create: `shared/src/iosMain/kotlin/com/example/widgetly/dday/IosDdayStorage.kt`
- Modify: `gradle/libs.versions.toml`, `shared/build.gradle.kts` — `androidx.datastore:datastore-preferences`를 `androidMain.dependencies`에 추가
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayRepositoryTest.kt` (인메모리 fake storage)
- Test: `shared/src/androidHostTest/kotlin/com/example/widgetly/dday/AndroidDdayStorageTest.kt` (실제 DataStore)
- Test: `shared/src/iosTest/kotlin/com/example/widgetly/dday/IosDdayStorageTest.kt` (실제 UserDefaults)

**Interfaces:**
- Consumes: `DdayEntry` (Task 1)
- Produces: `interface DdayStorage { suspend fun readAll(): List<DdayEntry>; suspend fun writeAll(entries: List<DdayEntry>) }`, `class DdayRepository(private val storage: DdayStorage) { suspend fun list(): List<DdayEntry>; suspend fun upsert(entry: DdayEntry); suspend fun delete(id: String) }`, `class AndroidDdayStorage(context: Context) : DdayStorage`, `class IosDdayStorage(suiteName: String = "group.com.example.widgetly") : DdayStorage`

`DdayRepository.upsert`: `title.isBlank()`이면 `IllegalArgumentException`을 던진다(Review Focus 3번). 같은 `id`가 이미 있으면 교체, 없으면 추가한다(Review Focus 5번).

`AndroidDdayStorage`/`IosDdayStorage`는 엔트리 목록 전체를 JSON 한 덩어리로 단일 키에 저장한다(엔트리 수가 적어 충분).

- [ ] **Step 1: Write the failing repository test (commonTest, fake storage)**

```kotlin
class FakeDdayStorage(initial: List<DdayEntry> = emptyList()) : DdayStorage {
    private var entries = initial
    override suspend fun readAll() = entries
    override suspend fun writeAll(entries: List<DdayEntry>) { this.entries = entries }
}

class DdayRepositoryTest {
    @Test
    fun `upsert replaces an existing entry with the same id instead of duplicating it`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry.copy(title = "old"))))
        repo.upsert(sampleEntry.copy(title = "new"))
        val result = repo.list()
        assertEquals(1, result.size)
        assertEquals("new", result.single().title)
    }

    @Test
    fun `upsert rejects a blank title`() = runTest {
        val repo = DdayRepository(FakeDdayStorage())
        assertFailsWith<IllegalArgumentException> { repo.upsert(sampleEntry.copy(title = "   ")) }
    }

    @Test
    fun `delete removes the entry with the given id`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry)))
        repo.delete(sampleEntry.id)
        assertEquals(emptyList(), repo.list())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayRepositoryTest"`
Expected: FAIL — `DdayStorage`/`DdayRepository` 없음

- [ ] **Step 3: `DdayStorage.kt`, `DdayRepository.kt` 구현**

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Write the failing Android storage test**

```kotlin
class AndroidDdayStorageTest {
    @Test
    fun `writeAll then readAll round-trips entries`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AndroidDdayStorage(context)
        storage.writeAll(listOf(sampleEntry))
        assertEquals(listOf(sampleEntry), storage.readAll())
    }
}
```

- [ ] **Step 6: Run test to verify it fails, then implement `AndroidDdayStorage` (DataStore + JSON), then verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.AndroidDdayStorageTest"`

- [ ] **Step 7: Write the failing iOS storage test, implement `IosDdayStorage` (NSUserDefaults + JSON), verify it passes**

Run: `./gradlew :shared:iosSimulatorArm64Test --tests "*.IosDdayStorageTest"`

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayStorage.kt shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayRepository.kt shared/src/androidMain/kotlin/com/example/widgetly/dday/AndroidDdayStorage.kt shared/src/iosMain/kotlin/com/example/widgetly/dday/IosDdayStorage.kt shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayRepositoryTest.kt shared/src/androidHostTest/kotlin/com/example/widgetly/dday/AndroidDdayStorageTest.kt shared/src/iosTest/kotlin/com/example/widgetly/dday/IosDdayStorageTest.kt gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "feat: add D-day local storage and repository"
```

---

### Task 4: Compose UI — 디데이 목록/생성/수정 화면

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/WidgetlyApplication.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/MainActivity.kt` — `DdayListScreen` 호스팅
- Modify: `androidApp/src/main/AndroidManifest.xml` — `android:name=".WidgetlyApplication"` 등록
- Modify: `shared/src/iosMain/kotlin/com/example/widgetly/MainViewController.kt` — `DdayListScreen` 호스팅
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayViewModelTest.kt`

**Interfaces:**
- Consumes: `DdayRepository`, `calculateDday` (Task 2, 3)
- Produces: `class DdayViewModel(private val repository: DdayRepository) : ViewModel() { val entries: StateFlow<List<DdayEntry>>; fun refresh(); fun upsert(entry: DdayEntry); fun remove(id: String) }`, `@Composable fun DdayListScreen(viewModel: DdayViewModel)`, `object WidgetlyApplication 전역 접근점: val ddayRepository: DdayRepository`

`WidgetlyApplication`은 `android.app.Application`을 상속하고 `ddayRepository by lazy { DdayRepository(AndroidDdayStorage(this)) }`를 노출한다 — Task 5(Glance 위젯)가 같은 인스턴스를 재사용한다.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
class DdayViewModelTest {
    @Test
    fun `refresh loads entries from the repository`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry)))
        val viewModel = DdayViewModel(repo)
        viewModel.refresh()
        assertEquals(listOf(sampleEntry), viewModel.entries.value)
    }

    @Test
    fun `upsert adds the entry and reflects it in entries`() = runTest {
        val viewModel = DdayViewModel(DdayRepository(FakeDdayStorage()))
        viewModel.upsert(sampleEntry)
        assertEquals(listOf(sampleEntry), viewModel.entries.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.DdayViewModelTest"`

- [ ] **Step 3: `DdayViewModel` 구현 (내부적으로 `refresh`/`upsert`/`remove` 후 `repository.list()` 결과로 `entries` 갱신)**

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: `DdayListScreen` Composable 작성 (목록 표시, 각 항목에 `calculateDday` 결과 표시, 추가/삭제 UI). 화면 레이아웃/스타일은 이 작업을 수행하는 사람이 정한다 — 디자인 가이드가 없으므로 Material3 기본 컴포넌트로 충분하다.**

- [ ] **Step 6: `WidgetlyApplication` 작성, `MainActivity`/`MainViewController`에서 `DdayListScreen(DdayViewModel(WidgetlyApplication.ddayRepository))` 호스팅**

- [ ] **Step 7: Android 앱 빌드 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayViewModel.kt shared/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt shared/src/commonTest/kotlin/com/example/widgetly/dday/DdayViewModelTest.kt androidApp/src/main/kotlin/com/example/widgetly/WidgetlyApplication.kt androidApp/src/main/kotlin/com/example/widgetly/MainActivity.kt androidApp/src/main/AndroidManifest.xml shared/src/iosMain/kotlin/com/example/widgetly/MainViewController.kt
git commit -m "feat: add D-day list screen"
```

---

### Task 5: Android Glance 위젯

**Files:**
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidgetReceiver.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivity.kt`
- Create: `androidApp/src/main/res/xml/dday_widget_info.xml`
- Modify: `androidApp/src/main/AndroidManifest.xml` — receiver + configuration activity 등록
- Modify: `gradle/libs.versions.toml`, `androidApp/build.gradle.kts` — `androidx.glance:glance-appwidget`(최신 stable 버전 확인 후 추가) 의존성
- Test: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivityTest.kt`

**Interfaces:**
- Consumes: `WidgetlyApplication.ddayRepository`, `calculateDday` (Task 2, 4)
- Produces: 없음 (leaf task) — 단, 위젯 상태 키 이름 `stringPreferencesKey("dday_entry_id")`와 `DdayWidgetConfigurationActivity.saveSelectedEntry(context, glanceId, entryId)` 함수는 이 작업에서 고정된다

**동작:**
- `DdayWidgetConfigurationActivity`: `WidgetlyApplication.ddayRepository.list()`로 엔트리 목록을 보여주고, 선택한 `id`를 해당 `glanceId`의 Glance 상태(`updateAppWidgetState`)에 `"dday_entry_id"` 키로 저장한 뒤 `RESULT_OK`로 종료
- `DdayGlanceWidget.provideGlance`: 상태에서 `dday_entry_id`를 읽고, `ddayRepository.list()`에서 해당 id를 찾아 `calculateDday`로 렌더링. **id가 없거나 엔트리가 삭제된 경우("삭제됨" 텍스트를 보여주고 크래시하지 않는다 — Review Focus 4번)**
- 위젯을 홈 화면에 추가하면 OS가 `DdayWidgetConfigurationActivity`를 먼저 띄우도록 `dday_widget_info.xml`에 `android:configure`를 지정

- [ ] **Step 1: Write the failing configuration test**

```kotlin
class DdayWidgetConfigurationActivityTest {
    @Test
    fun `selecting an entry stores its id in the widget state for that glanceId`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glanceId = GlanceAppWidgetManager(context).testGlanceId() // 테스트용 glanceId 발급(Glance 테스트 유틸)
        DdayWidgetConfigurationActivity.saveSelectedEntry(context, glanceId, sampleEntry.id)
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        assertEquals(sampleEntry.id, state[stringPreferencesKey("dday_entry_id")])
    }
}
```

`saveSelectedEntry(context, glanceId, entryId)`는 `DdayWidgetConfigurationActivity`의 선택 확정 로직을 분리한 작은 함수로, 액티비티의 `onResult` 콜백에서도 재사용한다.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :androidApp:connectedAndroidTest --tests "*.DdayWidgetConfigurationActivityTest"`

- [ ] **Step 3: `dday_widget_info.xml`, `DdayGlanceWidgetReceiver`, `DdayGlanceWidget`, `DdayWidgetConfigurationActivity` 구현, AndroidManifest 등록**

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: 에뮬레이터에서 수동 확인 — 위젯 추가 → 엔트리 선택 → 홈 화면에 올바른 D-day 라벨이 보이는지, 엔트리를 앱에서 삭제한 뒤 위젯이 "삭제됨"으로 바뀌는지**

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/main/kotlin/com/example/widgetly/widget androidApp/src/main/res/xml/dday_widget_info.xml androidApp/src/main/AndroidManifest.xml androidApp/src/androidTest/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivityTest.kt gradle/libs.versions.toml androidApp/build.gradle.kts
git commit -m "feat: add Android D-day home screen widget"
```

---

### Task 6: iOS WidgetKit 위젯

**Files:**
- **수동(Xcode GUI) 사전 작업**: `iosApp.xcodeproj` 열기 → File > New > Target > Widget Extension 선택 → 이름 `DdayWidgetExtension`, "Include Live Activity" 체크 해제, `iosApp` 타겟에 embed. 이후 `iosApp`과 `DdayWidgetExtension` 양쪽 타겟의 Signing & Capabilities에 App Groups 추가, `group.com.example.widgetly` 등록. `DdayWidgetExtension` 타겟의 "Frameworks and Libraries"에 `Shared.framework` 추가
- Create: `iosApp/DdayWidgetExtension/DdayWidgetConfigurationIntent.swift`
- Create: `iosApp/DdayWidgetExtension/DdayWidget.swift`
- Test: `iosApp/DdayWidgetExtensionTests/DdayDisplayMappingTests.swift` (Xcode가 타겟 생성 시 같이 만들어주는 테스트 타겟 활용)

**Interfaces:**
- Consumes: `IosDdayStorage`, `calculateDday`, `DdayEntry` — Shared.framework를 통해 Swift에서 직접 호출 (Task 2, 3)
- Produces: 없음 (leaf task)

**동작:**
- `DdayWidgetConfigurationIntent`: `AppIntent` + `WidgetConfigurationIntent` 준수, `IosDdayStorage().readAll()`로 선택 가능한 엔트리 목록을 `AppEntity`/`EntityQuery`로 노출
- `DdayWidget`의 `TimelineProvider`(`AppIntentTimelineProvider`): 선택된 엔트리 id로 `IosDdayStorage().readAll()`에서 엔트리를 찾아 `calculateDday(entry, today)` 호출 → `SharedKit.DdayResult`를 엔트리로 매핑. 엔트리를 못 찾으면(삭제됨) "삭제됨" 상태를 보여준다(Review Focus 4번)
- `today`는 Swift의 `Date()`를 kotlinx-datetime `LocalDate`로 변환해서 넘긴다 — 변환 함수는 이 파일 안에 작게 둔다

- [ ] **Step 1: 위 Xcode 수동 사전 작업을 수행하고, 체크리스트로 확인: 두 타겟 모두 같은 App Group이 보이는가, `Shared.framework`가 `DdayWidgetExtension`의 General > Frameworks에 보이는가**

- [ ] **Step 2: Write the failing display-mapping test**

```swift
final class DdayDisplayMappingTests: XCTestCase {
    func testMissingEntryMapsToDeletedState() {
        let state = mapToWidgetState(entry: nil, today: Date())
        XCTAssertEqual(state.label, "삭제됨")
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: Xcode에서 `DdayWidgetExtensionTests` 타겟 테스트 실행 (⌘U)

- [ ] **Step 4: `DdayWidgetConfigurationIntent.swift`, `DdayWidget.swift` 구현 (위 동작대로), `mapToWidgetState` 함수 포함**

- [ ] **Step 5: Run test to verify it passes**

- [ ] **Step 6: 시뮬레이터에서 수동 확인 — 위젯 추가 시 구성 화면에서 엔트리 선택 가능한지, 홈 화면에 올바른 D-day가 보이는지, 앱에서 해당 엔트리를 지우면 위젯이 "삭제됨"으로 바뀌는지(다음 타임라인 갱신 후)**

- [ ] **Step 7: Commit**

```bash
git add iosApp/DdayWidgetExtension iosApp/DdayWidgetExtensionTests iosApp/iosApp.xcodeproj
git commit -m "feat: add iOS D-day home screen widget"
```
