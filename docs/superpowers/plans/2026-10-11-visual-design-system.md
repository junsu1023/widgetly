# 비주얼 디자인 시스템 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱 화면(설정/페어링 화면)과 위젯 색상 테마에 파스텔·큐트("다꾸") 비주얼 아이덴티티를 적용한다 — 타이포그래피(Do Hyeon/Dongle), 라이트·다크 컬러 토큰, 4종 위젯 테마(피치/레몬/스카이블루/크림), 둥근 모양.

**Architecture:** `shared-core`의 `ThemeCatalog`(위젯 색상 테마)는 내용만 교체한다. `shared`에 새 컬러/타이포 토큰과 `WidgetlyTheme` Composable을 만들어 `AppRoot`(Android/iOS 공유 앱 셸)와 Android의 위젯 설정 화면 2곳을 감싼다 — `DdayListScreen`/`PairingScreen`은 플레인 Material3 컴포넌트만 쓰고 있어 `MaterialTheme` 커스터마이징만으로 자동 반영되므로 별도 수정이 필요 없다. 위젯 자체(Glance/SwiftUI, UI 비공유)는 플랫폼별로 같은 색상·폰트 값을 네이티브 방식으로 적용한다.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform(Material3), Jetpack Glance, SwiftUI/WidgetKit, Google Fonts(Do Hyeon, Dongle — OFL 라이선스)

**Spec:** `docs/superpowers/specs/2026-10-11-visual-design-system-design.md`

## Global Constraints

- 폰트: 제목/버튼/큰 숫자(디데이 표시) = **Do Hyeon**, 본문/목록 = **Dongle** — 앱 화면과 위젯 글자 양쪽에 모두 적용한다
- 앱 화면 라이트 모드: Background `#FFF4E8` · Surface `#FFE7CC` · Primary `#FFA45C` · OnBackground/OnSurface `#4A2F14`
- 앱 화면 다크 모드: Background `#2E2015` · Surface `#3F2C18` · Primary `#FFA45C`(라이트와 동일) · OnBackground/OnSurface `#FFE6C7` · OnPrimary `#2E2015`
- 위젯은 시스템 다크모드 설정과 **무관하게** 사용자가 고른 테마 색을 항상 유지한다(지금 `ColorProvider(day = X, night = X)` 패턴을 그대로 유지 — 바꾸지 않는다)
- 위젯 테마 카탈로그(4종, 기존 라벤더/민트/선셋 전부 교체): `peach`(기본값) bg `#FFF0E5`/fg `#7A3B12`, `lemon` bg `#FFFBE0`/fg `#6B5800`, `skyblue` bg `#E6F4FF`/fg `#1B4B73`, `cream` bg `#FFF8EE`/fg `#5C4A33`
- 모서리: 카드류 20dp/pt, 버튼류 14dp/pt
- 앱 아이콘/브랜드 로고, 숏폼 바이럴 공유 비주얼은 이 플랜의 범위 밖(스펙의 "다음 단계" 참고)

## Review Focus

- 기존에 `lavender`/`mint`/`sunset` id로 저장된 위젯이 새 카탈로그에서 안 깨지고 `peach`로 폴백하는지 — Task 1
- Android Glance 위젯에 커스텀 TTF 폰트가 실제로 적용되는지는 자동 테스트로 검증 불가능하고, Jetpack Glance의 `FontFamily(String)`는 리소스 ID가 아니라 문자열만 받는 제한적인 API라 커스텀 폰트 지원 여부가 불확실함 — 기기/에뮬레이터 수동 확인과 안 되면 시스템 폰트로 폴백하는 경로가 실제로 명시돼 있는지 — Task 3
- 위젯 설정 화면(`DdayWidgetConfigurationActivity`/`ConnectionWidgetConfigurationActivity`)이 지금 커스터마이징 없는 `MaterialTheme { }`를 쓰고 있어 "앱 화면" 범위에 들어가는지 애매했음 — 빠뜨리면 디데이 목록/페어링 화면과 스타일이 어긋나 보임 — Task 5에서 명시적으로 처리
- 폰트 파일을 fetch하는 `curl` 단계가 실패하거나 빈/손상된 파일을 받으면 조용히 깨진 폰트가 들어갈 수 있음 — 각 Task의 fetch 단계에 파일 유효성 확인(`file` 명령, 크기)을 포함
- 다크모드 전환이 앱 화면에는 적용되고 위젯에는 적용되지 않는 비대칭 동작이 의도된 것인지 — 테스트로 두 동작을 분리해서 고정

---

### Task 1: 위젯 테마 카탈로그 교체 (`shared-core`)

**Files:**
- Modify: `shared-core/src/commonMain/kotlin/com/example/widgetly/theme/Theme.kt`
- Test: `shared-core/src/commonTest/kotlin/com/example/widgetly/theme/ThemeCatalogTest.kt`

**Interfaces:**
- Produces: `ThemeCatalog.all`(4개 `Theme`), `ThemeCatalog.DEFAULT_THEME_ID = "peach"` — `Theme` 데이터 클래스 구조(`id`/`name`/`backgroundColorArgb`/`foregroundColorArgb`)는 변경 없음
- Consumes: 없음(leaf)

- [ ] **Step 1: Write the failing tests**

```kotlin
// ThemeCatalogTest.kt에 추가
@Test
fun `catalog has exactly the four v2 themes with the right default`() {
    assertEquals("peach", ThemeCatalog.DEFAULT_THEME_ID)
    assertEquals(setOf("peach", "lemon", "skyblue", "cream"), ThemeCatalog.all.map { it.id }.toSet())
}

@Test
fun `an old pre-redesign theme id falls back to the default instead of crashing`() {
    // lavender/mint/sunset were removed entirely - this must resolve like any
    // other unknown id, not throw.
    assertEquals(ThemeCatalog.DEFAULT_THEME_ID, ThemeCatalog.find("lavender").id)
    assertEquals(ThemeCatalog.DEFAULT_THEME_ID, ThemeCatalog.find("sunset").id)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared-core:testAndroidHostTest --tests "*.ThemeCatalogTest"`
Expected: FAIL (현재 카탈로그는 `lavender`/`mint`/`sunset`이고 기본값은 `lavender`)

- [ ] **Step 3: `ThemeCatalog.all`과 `DEFAULT_THEME_ID`를 아래 값으로 교체**

```kotlin
const val DEFAULT_THEME_ID: String = "peach"

val all: List<Theme> = listOf(
    Theme(id = DEFAULT_THEME_ID, name = "피치", backgroundColorArgb = 0xFFFFF0E5L, foregroundColorArgb = 0xFF7A3B12L),
    Theme(id = "lemon", name = "레몬", backgroundColorArgb = 0xFFFFFBE0L, foregroundColorArgb = 0xFF6B5800L),
    Theme(id = "skyblue", name = "스카이블루", backgroundColorArgb = 0xFFE6F4FFL, foregroundColorArgb = 0xFF1B4B73L),
    Theme(id = "cream", name = "크림", backgroundColorArgb = 0xFFFFF8EEL, foregroundColorArgb = 0xFF5C4A33L),
)
```

기존 `lavender`/`mint`/`sunset` 항목은 전부 제거한다.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared-core:testAndroidHostTest --tests "*.ThemeCatalogTest"`
Expected: PASS (기존 4개 테스트 + 새 2개 테스트 전부 통과)

- [ ] **Step 5: Commit**

```bash
git add shared-core/src/commonMain/kotlin/com/example/widgetly/theme/Theme.kt shared-core/src/commonTest/kotlin/com/example/widgetly/theme/ThemeCatalogTest.kt
git commit -m "feat: replace widget theme catalog with peach/lemon/skyblue/cream"
```

---

### Task 2: 앱 화면 컬러/타이포 토큰 + `WidgetlyTheme` (`shared`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/AppColorTokens.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/WidgetlyTheme.kt`
- Create: `shared/src/commonMain/composeResources/font/do_hyeon_regular.ttf`
- Create: `shared/src/commonMain/composeResources/font/dongle_regular.ttf`
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt`
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/theme/AppColorTokensTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class AppColorPalette(
    val background: Long, val surface: Long, val primary: Long, val onPrimary: Long, val onBackground: Long,
)
object AppColorTokens {
    val light: AppColorPalette
    val dark: AppColorPalette
}

@Composable
fun WidgetlyTheme(content: @Composable () -> Unit)
```
- Consumes: 없음(leaf) — Task 5가 `WidgetlyTheme`을 소비한다

- [ ] **Step 1: 폰트 파일을 내려받는다 (Google Fonts, OFL 라이선스)**

```bash
mkdir -p shared/src/commonMain/composeResources/font
curl -sL -o shared/src/commonMain/composeResources/font/do_hyeon_regular.ttf \
  https://raw.githubusercontent.com/google/fonts/main/ofl/dohyeon/DoHyeon-Regular.ttf
curl -sL -o shared/src/commonMain/composeResources/font/dongle_regular.ttf \
  https://raw.githubusercontent.com/google/fonts/main/ofl/dongle/Dongle-Regular.ttf
file shared/src/commonMain/composeResources/font/*.ttf
```

Expected: 둘 다 `TrueType Font data`로 나오고, 파일 크기가 각각 수백KB~수MB(0바이트/HTML 에러 페이지가 아님)여야 한다. URL이 바뀌었다면 `https://fonts.google.com/specimen/Do-Hyeon`, `https://fonts.google.com/specimen/Dongle`에서 직접 다운로드해도 된다.

- [ ] **Step 2: Write the failing test**

```kotlin
class AppColorTokensTest {
    @Test
    fun `primary accent is identical across light and dark`() {
        assertEquals(AppColorTokens.light.primary, AppColorTokens.dark.primary)
    }

    @Test
    fun `background is distinct between light and dark`() {
        assertNotEquals(AppColorTokens.light.background, AppColorTokens.dark.background)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.AppColorTokensTest"`
Expected: FAIL with "AppColorTokens를 찾을 수 없음" (컴파일 에러)

- [ ] **Step 4: `AppColorTokens.kt` 구현**

```kotlin
object AppColorTokens {
    val light = AppColorPalette(
        background = 0xFFFFF4E8L, surface = 0xFFFFE7CCL, primary = 0xFFFFA45CL,
        onPrimary = 0xFF4A2F14L, onBackground = 0xFF4A2F14L,
    )
    val dark = AppColorPalette(
        background = 0xFF2E2015L, surface = 0xFF3F2C18L, primary = 0xFFFFA45CL,
        onPrimary = 0xFF2E2015L, onBackground = 0xFFFFE6C7L,
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

- [ ] **Step 6: `WidgetlyTheme.kt` 구현**

`isSystemInDarkTheme()`로 라이트/다크를 고르고, `AppColorTokens`의 해당 팔레트로 M3 `ColorScheme`의 `background`/`surface`/`primary`/`onPrimary`/`onBackground`/`onSurface`(= `onBackground`와 같은 값)를 덮어쓴다. `Font(Res.font.do_hyeon_regular)`/`Font(Res.font.dongle_regular)`로 두 `FontFamily`를 만들고, M3 `Typography`에서 `display*`/`headline*`/`title*`/`label*`(버튼 텍스트가 `labelLarge`를 씀) 계열은 Do Hyeon, `body*` 계열은 Dongle로 지정한다. `Shapes`는 전체적으로 크게 둥근 값(대략 카드류 20dp, 버튼류 14dp에 대응하는 M3 shape 토큰)으로 구성한다. 전부 `MaterialTheme(colorScheme = ..., typography = ..., shapes = ..., content = content)`로 감싸 반환한다.

- [ ] **Step 7: `AppRoot.kt`에서 기존 `Column { ... }` 전체를 `WidgetlyTheme { Column { ... } }`로 감싼다**

- [ ] **Step 8: Run tests + build to verify nothing broke**

Run: `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`
Expected: 전부 성공

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/theme shared/src/commonMain/composeResources/font shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt shared/src/commonTest/kotlin/com/example/widgetly/theme/AppColorTokensTest.kt
git commit -m "feat: add light/dark app-shell color+type tokens, wrap AppRoot in WidgetlyTheme"
```

---

### Task 3: Android 위젯(Glance) 폰트 + 모서리 적용

**Files:**
- Create: `androidApp/src/main/res/font/do_hyeon_regular.ttf`
- Create: `androidApp/src/main/res/font/dongle_regular.ttf`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidget.kt`

**Interfaces:**
- Consumes: `ThemeCatalog`(Task 1, 이미 사용 중)
- Produces: 없음(leaf)

**중요 — 알려지지 않은 영역:** Jetpack Glance의 `androidx.glance.text.FontFamily`는 문자열 하나만 받는 생성자(`FontFamily(familyName: String)`)뿐이고, 일반 Compose의 `Font(resourceId)` 같은 리소스 기반 로딩 API가 없다(디컴파일로 확인됨 — Glance 1.1.1 기준). 번들된 커스텀 TTF가 이 문자열 방식으로 실제로 로드되는지 공식 문서로 확정할 수 없으므로, **Step 6의 수동 확인이 이 태스크의 핵심 완료 조건**이다.

- [ ] **Step 1: 폰트 파일을 내려받는다**

```bash
mkdir -p androidApp/src/main/res/font
curl -sL -o androidApp/src/main/res/font/do_hyeon_regular.ttf \
  https://raw.githubusercontent.com/google/fonts/main/ofl/dohyeon/DoHyeon-Regular.ttf
curl -sL -o androidApp/src/main/res/font/dongle_regular.ttf \
  https://raw.githubusercontent.com/google/fonts/main/ofl/dongle/Dongle-Regular.ttf
file androidApp/src/main/res/font/*.ttf
```

Expected: Task 2의 Step 1과 동일한 검증(`TrueType Font data`, 0바이트 아님). Android 리소스 파일명 규칙(소문자+숫자+언더스코어만)을 지키고 있는지 확인.

- [ ] **Step 2: `DdayGlanceWidget.kt`/`ConnectionGlanceWidget.kt`를 수정**

양쪽 파일의 모든 `TextStyle(...)` 생성에 `fontFamily = FontFamily("do_hyeon_regular")`(큰 디데이 숫자/제목류 텍스트) 또는 `FontFamily("dongle_regular")`(그 외 보조 텍스트)를 추가한다. `cornerRadius(16.dp)`를 `cornerRadius(20.dp)`로 바꾼다(두 파일 모두).

- [ ] **Step 3: 기존 계측 테스트가 깨지지 않는지 확인**

Run: `./gradlew :androidApp:connectedAndroidTest --tests "*.ConnectionGlanceWidgetTest"` (에뮬레이터/기기 필요)
Expected: 전부 통과 — 이 테스트들은 색상/텍스트 내용만 검증하고 폰트 렌더링은 검증하지 않으므로, 폰트 관련 회귀는 여기서 안 잡힌다는 걸 알고 있어야 한다.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/res/font androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidget.kt
git commit -m "feat: apply Do Hyeon/Dongle fonts and larger corner radius to Android widgets"
```

- [ ] **Step 5: 실기기/에뮬레이터에서 위젯을 홈 화면에 배치해 실제로 커스텀 폰트가 적용됐는지 확인**

Expected: D-day/연결 위젯의 글자가 Do Hyeon/Dongle로 보임. **만약 시스템 기본 폰트 그대로 보인다면(Glance의 알려진 제약일 수 있음)**: `fontFamily` 지정을 되돌리지 말고 그대로 남겨두되(향후 Glance 버전 업그레이드 시 자동으로 해결될 수도 있음), `docs/cross-platform-verification.md`에 새 행을 추가해 "Android 위젯 커스텀 폰트 미적용, 색상/모서리는 정상" 상태를 기록한다.

- [ ] **Step 6: Step 5의 결과를 `docs/cross-platform-verification.md`에 기록하고 커밋**

```bash
git add docs/cross-platform-verification.md
git commit -m "docs: record Android widget custom font verification result"
```

---

### Task 4: iOS 위젯 익스텐션 폰트 적용

**Files:**
- Create: `iosApp/DdayWidgetExtension/DoHyeon-Regular.ttf`
- Create: `iosApp/DdayWidgetExtension/Dongle-Regular.ttf`
- Modify: `iosApp/DdayWidgetExtension/Info.plist`
- Modify: `iosApp/DdayWidgetExtension/DdayWidgetExtension.swift`
- Modify: `iosApp/DdayWidgetExtension/ConnectionWidget.swift`

**Interfaces:**
- Consumes: 없음
- Produces: 없음(leaf)

`iosApp/DdayWidgetExtension/`은 `PBXFileSystemSynchronizedRootGroup`(폴더 자동 동기화)이므로, 이 폴더에 파일을 추가하면 `DdayWidgetExtensionExtension` 타겟에 자동으로 포함된다 — pbxproj를 직접 편집할 필요 없다.

- [ ] **Step 1: 폰트 파일을 내려받는다**

```bash
curl -sL -o iosApp/DdayWidgetExtension/DoHyeon-Regular.ttf \
  https://raw.githubusercontent.com/google/fonts/main/ofl/dohyeon/DoHyeon-Regular.ttf
curl -sL -o iosApp/DdayWidgetExtension/Dongle-Regular.ttf \
  https://raw.githubusercontent.com/google/fonts/main/ofl/dongle/Dongle-Regular.ttf
file iosApp/DdayWidgetExtension/*.ttf
```

Expected: Task 2/3와 동일한 검증.

- [ ] **Step 2: `Info.plist`에 `UIAppFonts` 배열 추가**

```xml
<key>UIAppFonts</key>
<array>
    <string>DoHyeon-Regular.ttf</string>
    <string>Dongle-Regular.ttf</string>
</array>
```

- [ ] **Step 3: `DdayWidgetExtension.swift`의 `DdayWidgetExtensionEntryView`에서 `entry.label`을 보여주는 `Text`에 `.font(.custom("Do Hyeon", size: 20))`를 적용**

실제 PostScript 폰트 이름(`"Do Hyeon"`)은 1단계에서 받은 ttf 파일을 더블클릭해 Font Book(또는 `fc-scan`/`otfinfo -i`)으로 확인한다 — 파일명과 PostScript 이름이 다를 수 있다.

- [ ] **Step 4: `ConnectionWidget.swift`의 `ConnectionWidgetEntryView`에 폰트 적용**

"스트릭 N", "상대 신호: ..." 등 주요 텍스트에 `.font(.custom("Do Hyeon", size: ...))`(스트릭 숫자류)와 `.font(.custom("Dongle", size: ...))`(그 외 보조 텍스트, 기존 `.font(.caption)` 자리)를 적용한다. PostScript 이름은 Step 3과 동일하게 확인한 값을 쓴다.

- [ ] **Step 5: 링크 회귀 확인 (C3 회귀 테스트 — CLAUDE.md 참고)**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -target DdayWidgetExtensionExtension -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=NO -configuration Debug clean build`
Expected: `** BUILD SUCCEEDED **`, 미정의 Firebase/gitlive 심볼 없음(이 태스크는 `Shared`/`SharedCore` 프레임워크를 건드리지 않으므로 그대로 통과해야 함).

- [ ] **Step 6: Commit**

```bash
git add iosApp/DdayWidgetExtension/DoHyeon-Regular.ttf iosApp/DdayWidgetExtension/Dongle-Regular.ttf iosApp/DdayWidgetExtension/Info.plist iosApp/DdayWidgetExtension/DdayWidgetExtension.swift iosApp/DdayWidgetExtension/ConnectionWidget.swift
git commit -m "feat: bundle and apply Do Hyeon/Dongle fonts in the iOS widget extension"
```

- [ ] **Step 7: 시뮬레이터에서 위젯을 실제로 렌더링해 폰트가 적용됐는지 확인하고, `docs/cross-platform-verification.md`에 결과를 기록 후 커밋**

```bash
git add docs/cross-platform-verification.md
git commit -m "docs: record iOS widget custom font verification result"
```

---

### Task 5: Android 위젯 설정 화면에 `WidgetlyTheme` 적용

**Files:**
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivity.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionWidgetConfigurationActivity.kt`

**Interfaces:**
- Consumes: `WidgetlyTheme`(Task 2) — `MaterialTheme { ... }` 호출부를 `WidgetlyTheme { ... }`로 그대로 교체(시그니처가 동일한 `@Composable fun(content: @Composable () -> Unit)`이라 드롭인 교체)
- Produces: 없음(leaf)

이 두 화면은 위젯을 배치하기 전에 디데이/연결을 고르고 테마를 미리보는 설정 화면이라, "앱 화면" 범위(설정 화면)에 포함되는 것으로 판단함 — 안 하면 `DdayListScreen`/`PairingScreen`과 스타일이 어긋나 보인다.

- [ ] **Step 1: 두 파일에서 `import androidx.compose.material3.MaterialTheme`를 `import com.example.widgetly.theme.WidgetlyTheme`로 바꾸고, `MaterialTheme {`를 `WidgetlyTheme {`로 바꾼다**

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: 성공

- [ ] **Step 3: 실기기에서 두 설정 화면을 열어 새 컬러/폰트가 적용됐는지 확인**

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/com/example/widgetly/widget/DdayWidgetConfigurationActivity.kt androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionWidgetConfigurationActivity.kt
git commit -m "feat: apply WidgetlyTheme to the widget configuration screens"
```

---

## 스펙 항목 중 이 플랜에 없는 것 (의도적)

스펙의 "모양 & 아이콘" 중 **아이콘(채워진 둥근 스타일)** 은 이 플랜에 태스크가 없다 — 지금 코드베이스 어디에도 `Icon(...)`/`Icons.*` 사용이 없어서(검색 확인됨) 적용할 대상 자체가 없다. 앞으로 화면에 아이콘을 추가할 때 이 스타일 방향(필드/둥근)을 따르면 된다 — 지금 억지로 아이콘을 추가하는 태스크는 만들지 않았다(YAGNI).
