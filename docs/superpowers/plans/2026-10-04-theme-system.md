# 테마 시스템 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 위젯(개인 디데이, 추후 커플·친구 위젯)에 적용할 테마를 고를 수 있게 한다. MVP에서는 모든 테마가 무료이며 결제/소유권 로직은 없다.

**Architecture:** `shared`(commonMain)에 고정된 테마 카탈로그(정적 목록, 서버 없음)를 두고, 위젯 구성(이미 "개인 디데이 위젯 코어" 플랜에서 만든 Android 구성 액티비티/iOS `WidgetConfigurationIntent`)에 테마 선택 단계를 추가한다. 선택된 테마 id는 디데이 엔트리 id와 같은 방식으로 위젯별 상태에 저장되고, 렌더링 시 해당 테마의 색상을 적용한다.

**Tech Stack:** Kotlin Multiplatform(commonMain), Android Glance, iOS WidgetKit/AppIntents

**Spec:** `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` (섹션 1 MVP 범위의 테마 시스템), `docs/theme-system.md`

**선행 플랜:** `docs/superpowers/plans/2026-10-04-personal-dday-widget.md` — 이 플랜이 만든 `DdayWidgetConfigurationActivity`(Android), `DdayWidgetConfigurationIntent`/`DdayWidget`(iOS), 위젯별 상태 저장 패턴을 확장한다.

## Global Constraints

- MVP에서 테마는 전부 무료 — 소유권/결제 필드를 추가하지 않는다 (`docs/theme-system.md`)
- 테마는 개인 D-day 위젯과 커플·친구 공유 위젯 양쪽에서 재사용 가능해야 한다 — 필드/타입 이름을 D-day 전용으로 짓지 않는다

## Review Focus

- 저장된 테마 id가 카탈로그에 없는 경우(예: 이후 카탈로그에서 제거됨) 기본 테마로 폴백해야 한다 — Task 1에서 테스트
- 아직 테마를 선택한 적 없는 기존 위젯(마이그레이션 전 상태)도 크래시 없이 기본 테마로 보여야 한다 — Task 2, 3에서 처리
- 테마를 변경하면 위젯이 즉시 다시 그려져야 한다 — Task 2, 3에서 확인
- `ThemeId`를 저장하는 상태 키 이름과 타입은 D-day 위젯과 커플·친구 위젯이 공유할 수 있도록 범용으로 짓는다 — Task 2, 3에서 반영
- 테마 색상은 시스템 다크/라이트 모드와 무관하게 고정 색상으로 그려진다(대비 세부 조정은 `docs/design-guidelines.md` 작성 시 범위) — Task 1 카탈로그 정의에 명시

---

### Task 1: Theme 모델 + 카탈로그

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/Theme.kt`
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/theme/ThemeCatalogTest.kt`

**Interfaces:**
- Produces: `data class Theme(val id: String, val name: String, val backgroundColorArgb: Long, val foregroundColorArgb: Long)`, `object ThemeCatalog { const val DEFAULT_THEME_ID: String; val all: List<Theme>; fun find(id: String): Theme }`

`ThemeCatalog.all`은 `DEFAULT_THEME_ID`를 포함해 최소 3개의 고정 테마를 담은 정적 목록(하드코딩, 서버 없음). `find(id)`는 일치하는 테마가 없으면 `DEFAULT_THEME_ID`의 테마를 반환한다(Review Focus 1번, 2번).

- [ ] **Step 1: Write the failing tests**

```kotlin
class ThemeCatalogTest {
    @Test
    fun `catalog contains the default theme`() {
        assertTrue(ThemeCatalog.all.any { it.id == ThemeCatalog.DEFAULT_THEME_ID })
    }

    @Test
    fun `all theme ids are unique`() {
        val ids = ThemeCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `find falls back to the default theme for an unknown id`() {
        val result = ThemeCatalog.find("does-not-exist")
        assertEquals(ThemeCatalog.DEFAULT_THEME_ID, result.id)
    }

    @Test
    fun `find returns the matching theme when the id exists`() {
        val known = ThemeCatalog.all.first { it.id != ThemeCatalog.DEFAULT_THEME_ID }
        assertEquals(known, ThemeCatalog.find(known.id))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.ThemeCatalogTest"`
Expected: FAIL — `Theme`/`ThemeCatalog` 없음

- [ ] **Step 3: `Theme.kt` 구현 — 최소 3개 테마(기본값 포함)를 하드코딩**

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.ThemeCatalogTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/theme/Theme.kt shared/src/commonTest/kotlin/com/example/widgetly/theme/ThemeCatalogTest.kt
git commit -m "feat: add theme catalog"
```

---

### Task 2: Android — 위젯 구성에 테마 선택 추가 + 렌더링 적용

**Files:**
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivity.kt` — 테마 선택 단계 추가
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt` — 저장된 테마로 배경/글자색 적용
- Test: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivityTest.kt` — 기존 테스트 파일에 케이스 추가

**Interfaces:**
- Consumes: `Theme`, `ThemeCatalog` (Task 1); `DdayWidgetConfigurationActivity.saveSelectedEntry`, `stringPreferencesKey("dday_entry_id")` (개인 디데이 위젯 플랜 Task 5)
- Produces: `DdayWidgetConfigurationActivity.saveSelectedTheme(context, glanceId, themeId)`, 위젯 상태 키 `stringPreferencesKey("theme_id")` — 이름과 타입을 범용으로 유지해 커플·친구 위젯 플랜에서 그대로 재사용한다(Review Focus 4번)

**동작:** `DdayGlanceWidget.provideGlance`가 상태에서 `"theme_id"`를 읽어 `ThemeCatalog.find(themeId ?: ThemeCatalog.DEFAULT_THEME_ID)`로 테마를 얻고, 배경/글자 색상에 적용한다. 키가 없는 기존 위젯은 `DEFAULT_THEME_ID`로 폴백한다.

- [ ] **Step 1: Write the failing test (기존 `DdayWidgetConfigurationActivityTest`에 추가)**

```kotlin
@Test
fun `selecting a theme stores its id in the widget state for that glanceId`() = runTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val glanceId = GlanceAppWidgetManager(context).testGlanceId()
    val theme = ThemeCatalog.all.first { it.id != ThemeCatalog.DEFAULT_THEME_ID }
    DdayWidgetConfigurationActivity.saveSelectedTheme(context, glanceId, theme.id)
    val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    assertEquals(theme.id, state[stringPreferencesKey("theme_id")])
}

@Test
fun `a widget with no stored theme falls back to the default theme id when rendering`() = runTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val glanceId = GlanceAppWidgetManager(context).testGlanceId()
    val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    val themeId = state[stringPreferencesKey("theme_id")] ?: ThemeCatalog.DEFAULT_THEME_ID
    assertEquals(ThemeCatalog.DEFAULT_THEME_ID, themeId)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :androidApp:connectedAndroidTest --tests "*.DdayWidgetConfigurationActivityTest"`

- [ ] **Step 3: `saveSelectedTheme` 추가, 구성 화면에 테마 선택 UI 추가, `DdayGlanceWidget`이 `theme_id`를 읽어 색상에 반영하도록 수정**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: 에뮬레이터에서 수동 확인 — 테마를 바꾸면 위젯이 바로 다시 그려지는지**

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/main/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivity.kt androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt androidApp/src/androidTest/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivityTest.kt
git commit -m "feat: add theme selection to Android D-day widget"
```

---

### Task 3: iOS — 위젯 구성에 테마 선택 추가 + 렌더링 적용

**Files:**
- Modify: `iosApp/DdayWidgetExtension/DdayWidgetConfigurationIntent.swift` — 테마 선택 파라미터 추가
- Modify: `iosApp/DdayWidgetExtension/DdayWidget.swift` — 저장된 테마로 배경/글자색 적용
- Test: `iosApp/DdayWidgetExtensionTests/DdayDisplayMappingTests.swift` — 기존 테스트 파일에 케이스 추가

**Interfaces:**
- Consumes: `Theme`, `ThemeCatalog` (Task 1, Shared.framework를 통해 Swift에서 호출)
- Produces: 없음 (leaf task) — Android와 동일한 "theme_id" 개념을 iOS 쪽 `WidgetConfigurationIntent` 파라미터로 노출

**동작:** `DdayWidgetConfigurationIntent`에 `ThemeEntity`/`EntityQuery`(`ThemeCatalog.all` 기반) 파라미터를 추가한다. `mapToWidgetState`가 선택된 테마 id로 `ThemeCatalog.find`를 호출해 배경/글자 색상을 결정하고, 파라미터가 없는 기존 위젯은 `ThemeCatalog.DEFAULT_THEME_ID`로 폴백한다(Review Focus 2번).

- [ ] **Step 1: Write the failing test**

```swift
func testMissingThemeFallsBackToDefault() {
    let state = mapToWidgetState(entry: sampleEntry, themeId: nil, today: Date())
    XCTAssertEqual(state.themeId, ThemeCatalogDefaultThemeId())
}

func testKnownThemeIdIsApplied() {
    let theme = ThemeCatalogAll().first(where: { $0.id != ThemeCatalogDefaultThemeId() })!
    let state = mapToWidgetState(entry: sampleEntry, themeId: theme.id, today: Date())
    XCTAssertEqual(state.backgroundColorArgb, theme.backgroundColorArgb)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: Xcode에서 `DdayWidgetExtensionTests` 타겟 테스트 실행 (⌘U)

- [ ] **Step 3: `DdayWidgetConfigurationIntent`에 테마 파라미터 추가, `mapToWidgetState`가 테마 색상을 반영하도록 수정, `DdayWidget`의 뷰가 배경/글자 색상을 적용**

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: 시뮬레이터에서 수동 확인 — 구성 화면에서 테마 선택 가능한지, 바꾸면 위젯이 반영되는지**

- [ ] **Step 6: Commit**

```bash
git add iosApp/DdayWidgetExtension/DdayWidgetConfigurationIntent.swift iosApp/DdayWidgetExtension/DdayWidget.swift iosApp/DdayWidgetExtensionTests/DdayDisplayMappingTests.swift
git commit -m "feat: add theme selection to iOS D-day widget"
```
