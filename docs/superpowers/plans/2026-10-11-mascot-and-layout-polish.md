# 마스코트 캐릭터 + 레이아웃 일관성 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자의 오리지널 쿼카 캐릭터를 위젯 빈 상태/앱 로딩 화면/앱 배경 3곳에 적용하고, 버튼 크기·간격 불일치를 정리한다.

**Architecture:** 쿼카 PNG 하나를 앱 화면(Compose Multiplatform 공유 리소스)·Android 위젯(Glance 네이티브 드로어블)·Android 스플래시(같은 드로어블 재사용)·iOS 위젯 익스텐션(애셋 카탈로그)·iOS 런치 스크린(메인 앱 애셋 카탈로그) 5곳에 플랫폼에 맞는 방식으로 번들한다 — 비주얼 디자인 시스템 플랜의 폰트 번들링과 같은 패턴. 버튼/간격은 새 토큰을 발명하지 않고 이미 코드베이스에 있는 올바른 패턴(`WidgetlyButtonShape`, `PairingScreen`의 `Arrangement.spacedBy`)으로 통일한다.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack Glance, `androidx.core:core-splashscreen`, SwiftUI/WidgetKit, iOS `UILaunchScreen` Info.plist 메커니즘

**Spec:** `docs/superpowers/specs/2026-10-11-mascot-and-layout-polish-design.md`

## Global Constraints

- 마스코트 원본 에셋: `docs/assets/quokka-mascot.png`(1254×1254, 투명 배경) — 사용자 오리지널 창작물, 포즈 1개만 사용(여러 포즈 세트 만들지 않는다)
- 위젯에는 **빈 상태(설정 필요/삭제됨/연결 끊김/불러오는 중)에서만** 노출 — 실제 데이터(디데이 숫자, 스트릭)가 보일 때는 노출하지 않는다
- 앱 배경 장식: 화면 하단 구석, 투명도 18%
- 로딩 화면은 커스텀으로 만들지 않고 Android/iOS가 기본 제공하는 네이티브 스플래시/런치 스크린 기능만 사용한다(인위적 대기시간 없음)
- `AppRoot`의 화면 전환 버튼은 `fillMaxWidth()`를 제거하고 다른 버튼들과 같은 크기 체계(`WidgetlyButtonShape`, 내용 크기)를 쓴다
- `DdayListScreen`의 간격은 `PairingScreen`과 같은 패턴(`Column`에 `Arrangement.spacedBy(16.dp)` 한 번, 각 `Row`는 가로 패딩만)으로 통일한다

## Review Focus

- 위젯의 "불러오는 중"(`Loading`/`ConnectionLoading` 상태)은 매우 짧게 스쳐 지나가는 중간 상태라 여기에 쿼카를 넣으면 깜빡임만 유발함 — 정확히 "설정 필요"/"삭제됨"/"연결 끊김" 세 가지 **확정** 빈 상태에만 넣고 `Loading`은 제외하는지 — Task 2, 3
- Jetpack Glance의 `Image`/`ImageProvider`가 커스텀 폰트(`FontFamily`)와 달리 실제로 잘 지원되는 기능이라는 건 확인했지만, 실제 에뮬레이터 렌더링으로 재확인하지 않으면 또 "되는 줄 알았는데 안 되는" 패턴이 반복될 위험 — Task 2
- 1254×1254 원본을 스플래시 아이콘 드로어블로 그대로 쓰면 빌드/런타임에 문제가 없는지(Android 스플래시 아이콘은 보통 더 작은 크기를 권장) — Task 4
- iOS 관련 두 태스크(위젯 익스텐션은 빌드 가능, 메인 앱 런치 스크린은 Firebase SPM 보류로 빌드 자체가 안 됨)의 검증 가능 범위를 명확히 구분해서 기록하는지 — Task 3, 5
- 배경 쿼카(18% 투명도, 하단 구석)가 실제 화면에서 버튼/텍스트와 겹치지 않는지 에뮬레이터 스크린샷으로 확인하는지 — Task 1

---

### Task 1: 앱 화면 배경에 쿼카 적용 (Compose Multiplatform, Android+iOS 공유)

**Files:**
- Create: `shared/src/commonMain/composeResources/drawable/quokka.png` (= `docs/assets/quokka-mascot.png` 복사)
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/theme/Mascot.kt`
- Modify: `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/connection/PairingScreen.kt`

**Interfaces:**
- Produces: `@Composable fun MascotBackground(modifier: Modifier = Modifier)` — `Res.drawable.quokka`를 `alpha = 0.18f`로 그리는 `Image` 하나
- Consumes: 없음(leaf)

`MascotBackground`는 `shared`에 둔다(이미 `WidgetlyTheme`/`AppColorTokens`가 거기 있고, compose resources 의존성도 `shared`에 있음 — `shared-core`에는 없음, 비주얼 디자인 시스템 플랜 Task 2의 판단과 동일한 이유). `DdayListScreen.kt`는 `shared-core`에 있으므로 `shared`를 참조할 수 없는 방향이라, `MascotBackground` 자체는 `shared`에 두되 `DdayListScreen.kt`가 직접 호출하지 못한다 — **`AppRoot.kt`(`shared`에 있음, 이미 `DdayListScreen`/`PairingScreen`을 호스팅하는 자리)에서 `Box`로 감싸 두 화면 공통으로 한 번만 적용**한다(화면마다 따로 넣지 않아도 됨 — 더 적은 코드).

- [ ] **Step 1: 에셋 복사**

```bash
cp docs/assets/quokka-mascot.png shared/src/commonMain/composeResources/drawable/quokka.png
```

- [ ] **Step 2: `Mascot.kt` 구현**

```kotlin
@Composable
fun MascotBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.quokka),
        contentDescription = null,
        modifier = modifier.size(120.dp).alpha(0.18f),
    )
}
```

- [ ] **Step 3: `AppRoot.kt`에서 `DdayListScreen`/`PairingScreen`을 보여주는 `Box(modifier = Modifier.weight(1f))` 안에, 기존 `when` 블록보다 먼저(뒤에 깔리도록) `MascotBackground(Modifier.align(Alignment.BottomEnd).padding(16.dp))`를 추가**

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`
Expected: 성공

- [ ] **Step 5: 실기기(에뮬레이터)에서 D-day 목록/페어링 화면 스크린샷으로 쿼카가 하단 구석에 옅게 보이고 버튼/텍스트를 안 가리는지 확인**

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/composeResources/drawable/quokka.png shared/src/commonMain/kotlin/com/example/widgetly/theme/Mascot.kt shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt
git commit -m "feat: add quokka mascot as a subtle app-shell background decoration"
```

---

### Task 2: Android 위젯 빈 상태에 쿼카 적용 (Glance)

**Files:**
- Create: `androidApp/src/main/res/drawable-nodpi/quokka.png` (= `docs/assets/quokka-mascot.png` 복사)
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidget.kt`

**Interfaces:**
- Consumes: 없음
- Produces: 없음(leaf)

**중요 — Task 3(비주얼 디자인 시스템 플랜)에서 확인된 Glance 폰트 제약과는 다른 경우다:** `androidx.glance.Image` + `androidx.glance.ImageProvider(resId)`는 RemoteViews의 표준 비트맵 렌더링 경로를 그대로 쓰는 정식 지원 기능이라, 폰트처럼 런처 프로세스에 막히는 문제가 없다 — 그래도 Step 4에서 실제로 확인한다.

- [ ] **Step 1: 리소스 파일명 규칙(소문자+숫자+언더스코어)에 맞춰 에셋 복사**

```bash
mkdir -p androidApp/src/main/res/drawable-nodpi
cp docs/assets/quokka-mascot.png androidApp/src/main/res/drawable-nodpi/quokka.png
```

- [ ] **Step 2: `DdayGlanceWidget.kt`의 `entryId == null`("설정 필요")과 `entryState == null`("삭제됨") 두 분기에, 기존 `Text` 위에 `Image(provider = ImageProvider(R.drawable.quokka), contentDescription = null, modifier = GlanceModifier.size(48.dp))`를 추가(두 `Text`를 `Column(horizontalAlignment = Alignment.CenterHorizontally)`로 감싸서 이미지+텍스트 세로 배치)**

- [ ] **Step 3: `ConnectionGlanceWidget.kt`도 동일하게 — `connectionId == null`("설정 필요"), `connectionState == null`("연결 끊김"), `connection.status == "disconnected"`("연결 끊김") 세 분기에 적용. `connectionState === ConnectionLoading`(빈 분기, "불러오는 중"에 해당하는 로딩 상태)에는 넣지 않는다**

- [ ] **Step 4: 빌드 + 계측 테스트**

Run: `./gradlew :androidApp:assembleDebug :androidApp:connectedAndroidTest`
Expected: 성공, 기존 테스트 전부 통과(이미지 추가가 `ConnectionGlanceWidgetTest`의 텍스트 기반 assertion을 깨지 않아야 함)

- [ ] **Step 5: 에뮬레이터에 위젯을 실제로 배치해(또는 기존 통합 테스트 하네스로) 빈 상태에서 쿼카가 실제로 렌더링되는지 확인**

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/main/res/drawable-nodpi/quokka.png androidApp/src/main/kotlin/com/example/widgetly/widget/DdayGlanceWidget.kt androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidget.kt
git commit -m "feat: show quokka mascot on Android widget empty states"
```

---

### Task 3: iOS 위젯 익스텐션 빈 상태에 쿼카 적용 (SwiftUI)

**Files:**
- Create: `iosApp/DdayWidgetExtension/Assets.xcassets/Quokka.imageset/Contents.json`
- Create: `iosApp/DdayWidgetExtension/Assets.xcassets/Quokka.imageset/quokka.png` (= `docs/assets/quokka-mascot.png` 복사)
- Modify: `iosApp/DdayWidgetExtension/ConnectionWidget.swift`
- Modify: `iosApp/DdayWidgetExtension/DdayWidgetExtension.swift`

**Interfaces:**
- Consumes: 없음
- Produces: 없음(leaf)

- [ ] **Step 1: 이미지 세트 생성**

```bash
mkdir -p "iosApp/DdayWidgetExtension/Assets.xcassets/Quokka.imageset"
cp docs/assets/quokka-mascot.png "iosApp/DdayWidgetExtension/Assets.xcassets/Quokka.imageset/quokka.png"
```

`Contents.json`:
```json
{
  "images" : [
    { "filename" : "quokka.png", "idiom" : "universal", "scale" : "1x" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
```

- [ ] **Step 2: `ConnectionWidget.swift`의 `entry.hasEverLoaded`가 true인 "연결 끊김"과, `entry.connectionId == nil`인 "설정 필요" 두 분기에 `Image("Quokka").resizable().frame(width: 48, height: 48)`를 텍스트 위에 추가(`VStack`으로 감싸기). `entry.hasEverLoaded`가 false인 "불러오는 중" 분기에는 넣지 않는다**

- [ ] **Step 3: `DdayWidgetExtension.swift`의 "삭제됨"/"설정 필요" 레이블(`DdayWidgetExtensionEntryView`에서 `entry.label`이 해당 문자열일 때)에도 동일하게 적용 — `mapToWidgetState`가 반환하는 라벨 문자열로 분기해 이미지를 보여줄지 판단**

- [ ] **Step 4: C3 회귀 확인(CLAUDE.md)**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -target DdayWidgetExtensionExtension -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=NO -configuration Debug clean build`
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 5: Commit**

```bash
git add "iosApp/DdayWidgetExtension/Assets.xcassets/Quokka.imageset" iosApp/DdayWidgetExtension/ConnectionWidget.swift iosApp/DdayWidgetExtension/DdayWidgetExtension.swift
git commit -m "feat: show quokka mascot on iOS widget empty states"
```

- [ ] **Step 6: 시뮬레이터에서 실제 렌더링 확인하고 `docs/cross-platform-verification.md`에 결과 기록 후 커밋**

---

### Task 4: Android 네이티브 스플래시 스크린

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `androidApp/build.gradle.kts`
- Create: `androidApp/src/main/res/values/colors.xml`
- Create: `androidApp/src/main/res/values/themes.xml`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/MainActivity.kt`

**Interfaces:**
- Consumes: `androidApp/src/main/res/drawable-nodpi/quokka.png`(Task 2에서 생성) — Task 2 이후에 진행
- Produces: 없음(leaf)

- [ ] **Step 1: `libs.versions.toml`에 버전/라이브러리 추가**

```toml
androidx-core-splashscreen = "1.0.1"
# [libraries] 섹션에
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "androidx-core-splashscreen" }
```

- [ ] **Step 2: `androidApp/build.gradle.kts`에 `implementation(libs.androidx.core.splashscreen)` 추가**

- [ ] **Step 3: `colors.xml` 생성**

```xml
<resources>
    <color name="splash_background">#FFF4E8</color>
</resources>
```

- [ ] **Step 4: `themes.xml` 생성**

```xml
<resources>
    <style name="Theme.Widgetly.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/quokka</item>
        <item name="postSplashScreenTheme">@android:style/Theme.Material.Light.NoActionBar</item>
    </style>
</resources>
```

1254×1254 원본을 아이콘으로 그대로 쓴다 — Android 스플래시 아이콘은 보통 더 작은 사이즈(240dp 안전 영역)를 권장하지만, `windowSplashScreenAnimatedIcon`은 큰 비트맵도 받아들여 자동으로 맞춰 그린다. Step 7에서 실제로 비율이 이상하게 잘리거나 과하게 크게 보이지 않는지 확인하고, 문제가 있으면 이 단계에서 리사이즈를 추가한다(지금은 그대로 시도).

- [ ] **Step 5: `AndroidManifest.xml`의 `android:theme="@android:style/Theme.Material.Light.NoActionBar"`를 `android:theme="@style/Theme.Widgetly.Splash"`로 교체**

- [ ] **Step 6: `MainActivity.kt`의 `onCreate()` 맨 앞(`super.onCreate()`보다 먼저)에 `installSplashScreen()` 추가**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    ...
```

- [ ] **Step 7: 빌드 + 에뮬레이터에서 앱을 콜드 스타트해 스플래시가 실제로 쿼카 아이콘과 함께 뜨는지 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: 성공, 에뮬레이터에서 앱 아이콘 탭 시 쿼카가 잠깐 보인 뒤 D-day 목록 화면으로 전환됨

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml androidApp/build.gradle.kts androidApp/src/main/res/values/colors.xml androidApp/src/main/res/values/themes.xml androidApp/src/main/AndroidManifest.xml androidApp/src/main/kotlin/com/example/widgetly/MainActivity.kt
git commit -m "feat: add quokka splash screen on Android"
```

---

### Task 5: iOS 네이티브 런치 스크린

**Files:**
- Create: `iosApp/iosApp/Assets.xcassets/Quokka.imageset/Contents.json`
- Create: `iosApp/iosApp/Assets.xcassets/Quokka.imageset/quokka.png` (= `docs/assets/quokka-mascot.png` 복사)
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: 없음
- Produces: 없음(leaf)

iOS 메인 앱은 보류 중인 Firebase SPM 수동 작업 전까지 빌드 자체가 안 되므로, 이 태스크는 xcodebuild로 검증할 수 없다 — pbxproj 구조와 JSON 유효성만 확인하고, 실제 동작 확인은 그 작업이 끝난 뒤로 미룬다. **이 사실 자체를 `docs/cross-platform-verification.md`에 명시적으로 기록하는 것까지가 이 태스크의 완료 조건이다.**

- [ ] **Step 1: 이미지 세트 생성**

```bash
mkdir -p "iosApp/iosApp/Assets.xcassets/Quokka.imageset"
cp docs/assets/quokka-mascot.png "iosApp/iosApp/Assets.xcassets/Quokka.imageset/quokka.png"
```

`Contents.json`은 Task 3과 동일한 내용.

- [ ] **Step 2: `project.pbxproj`에서 `INFOPLIST_KEY_UILaunchScreen_Generation = YES;`가 나오는 두 곳(Debug/Release 빌드 설정, `iosApp` 타겟) 바로 다음 줄에 `INFOPLIST_KEY_UILaunchScreen_ImageName = Quokka;`를 추가**

- [ ] **Step 3: JSON 유효성 확인**

Run: `python3 -c "import json; json.load(open('iosApp/iosApp/Assets.xcassets/Quokka.imageset/Contents.json'))"`
Expected: 에러 없이 종료

- [ ] **Step 4: pbxproj에 두 줄이 정확히 들어갔는지 확인**

Run: `grep -c "INFOPLIST_KEY_UILaunchScreen_ImageName = Quokka;" iosApp/iosApp.xcodeproj/project.pbxproj`
Expected: `2`

- [ ] **Step 5: `docs/cross-platform-verification.md`에 "iOS 런치 스크린 쿼카 이미지 — Firebase SPM 작업 전까지 빌드/확인 불가" 행 추가**

- [ ] **Step 6: Commit**

```bash
git add "iosApp/iosApp/Assets.xcassets/Quokka.imageset" iosApp/iosApp.xcodeproj/project.pbxproj docs/cross-platform-verification.md
git commit -m "feat: add quokka launch screen image on iOS (unverifiable until Firebase SPM step is done)"
```

---

### Task 6: 버튼 크기 + 간격 일관성 정리

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt`
- Modify: `shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt`

**Interfaces:**
- Consumes: `WidgetlyButtonShape`(이미 있음)
- Produces: 없음(leaf)

- [ ] **Step 1: `AppRoot.kt`의 화면 전환 `Button`에서 `Modifier.fillMaxWidth().padding(16.dp)`를 `Modifier.padding(16.dp)`로 바꾸고(내용 크기로), 이 `Button`을 `Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center)`로 감싸 가로 중앙 정렬**

- [ ] **Step 2: `DdayListScreen.kt`의 바깥 `Column`에 `verticalArrangement = Arrangement.spacedBy(16.dp)`를 추가하고, 그 안의 각 `Row`에서 `.padding(16.dp)`(모든 방향)로 돼 있는 것들을 `.padding(horizontal = 16.dp)`로 통일 — `PairingScreen.kt`와 똑같은 패턴**

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew :shared:testAndroidHostTest :shared-core:testAndroidHostTest :androidApp:assembleDebug`
Expected: 성공

- [ ] **Step 4: 에뮬레이터에서 D-day 목록/페어링 화면/AppRoot 전환 버튼을 스크린샷으로 확인 — 버튼이 다른 버튼들과 같은 크기로 보이고, 행 간 간격이 고르게 보이는지**

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt
git commit -m "fix: unify button sizing and row spacing across app shell screens"
```
