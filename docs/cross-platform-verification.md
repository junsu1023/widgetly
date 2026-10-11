# 크로스플랫폼 검증 체크리스트

사용자는 Android 기기로만 직접 테스트할 수 있고, iOS는 시뮬레이터 빌드/유닛 테스트까지만 자동으로 확인됩니다. **Android에서 발견되거나 고친 문제는 반드시 여기에 기록하고, iOS(실기기 또는 시뮬레이터 수동 확인)에서도 같은 증상이 있는지 추적합니다.** 새 플랫폼별 버그를 고칠 때마다 이 표를 갱신하세요.

## 상태 값
- `확인 필요` — 아직 iOS에서 수동으로 안 봄
- `해당 없음` — 플랫폼 아키텍처상 구조적으로 발생할 수 없음 (근거 명시)
- `확인됨 (정상)` — iOS에서 실제로 확인했고 문제 없음
- `확인됨 (버그 있음)` — iOS에서도 같은 증상 발견, 별도 항목으로 분리

## 목록

| # | 증상 / 수정 내용 | Android 상태 | iOS 상태 | 비고 |
|---|---|---|---|---|
| 1 | 위젯을 홈 화면에 추가하면 2x1 같은 지정 크기가 아니라 전체 화면을 덮음 | **오진이었음** — 실제로는 `dday_widget_info.xml`의 `targetCellWidth="2"`가 처음부터 정상 동작(위젯 피커에 "2×1"로 정확히 표시됨을 실기기에서 확인). "전체 화면"처럼 보였던 건 `android:configure`로 지정한 설정 액티비티가 스타일 없이 떠서 깨진 화면처럼 보인 것 — MaterialTheme/Scaffold/TopAppBar 적용해 해결(2026-10-05, 실기기 확인 완료) | 해당 없음 — iOS는 `WidgetConfigurationIntent`의 AppIntents 피커가 OS 표준 UI라 "못생긴 설정 화면"류 문제가 구조적으로 없음 | `DdayWidgetConfigurationActivity.kt` |
| 2 | 위젯이 앱에서 디데이 추가/삭제한 뒤 다시 그려지는지 (Critical #1) | **근본 원인을 찾아 재수정함(2026-10-05), 실기기 확인 완료.** Glance의 `GlanceAppWidget.provideGlance()`는 세션당 한 번만 호출됨 — `update()`/`updateAll()`은 이미 떠 있는 세션을 재사용하면서 `provideContent{}`에 넘긴 컴포저블만 재구성하므로, `provideGlance()` 안에서 상태를 한 번만 읽어 람다에 캡처해두면 그 값은 영원히 고정됨. `currentState()`로 컴포저블 내부에서 반응형으로 읽도록 수정. 추가로 Glance의 저장 `Preferences`는 구조적 동일성 비교라서 "선택된 entryId는 그대로인데 그 디데이가 삭제/수정된" 경우는 내용이 안 바뀌어 재구성이 트리거 안 됨 — 그래서 레포지토리 변경마다 각 위젯에 `dday_refresh_token`(타임스탬프)을 강제로 덮어써서 매번 다른 값이 되도록 함. 선택 직후 반영 + 선택된 디데이 삭제 시 "삭제됨"으로 전환 둘 다 실기기에서 직접 확인 | 확인 필요 — iOS는 cross-app `WidgetCenter.reloadAllTimelines()` 훅을 의도적으로 안 만들었음(최종 리뷰에서 Ruling으로 보류). 자정 정렬 타임라인만 있어서 변경 후 반영까지 더 걸릴 수 있음 — Android와 동일한 "클로저 캡처" 버그 클래스는 WidgetKit의 `TimelineProvider`가 매번 `getTimeline`을 새로 호출하는 구조라 원칙적으로 발생하지 않을 것으로 보이나, 실기기/시뮬레이터로 아직 확인 안 함 | `DdayGlanceWidget.kt`, `WidgetlyApplication.kt` |
| 3 | 위젯이 가리키는 디데이가 삭제됐을 때 "삭제됨", 아직 선택 안 했을 때 "설정 필요"로 구분 표시 | 수정함, 실기기에서 상태 전환 확인 완료(행 2와 동일 수정으로 해결됨) | 확인 필요 — 코드는 동일 패턴으로 구현(`DdayWidgetExtension.swift`), 실기기/시뮬레이터 렌더링 미검증 | |
| 4 | 위젯 구성 화면(설정 액티비티)이 비어있을 때(디데이 하나도 없을 때) 안내 문구 없음 | 수정함 — 행 1과 같은 패스에서 빈 상태 안내 문구 + "앱에서 디데이 만들기" 버튼 추가 | 해당 없음 — iOS는 `WidgetConfigurationIntent`의 AppIntents 피커가 OS 표준 UI라 같은 종류의 "빈 화면" 문제가 안 생김 | `DdayWidgetConfigurationActivity.kt` |
| 5 | 위젯 설정 화면에서 디데이를 선택해도 홈 화면 위젯이 "설정 필요" 상태로 멈춰 있음 (선택이 반영 안 됨) | **수정함(2026-10-05), 실기기 확인 완료.** 행 2와 동일한 근본 원인(Glance 세션 재사용 시 `provideGlance()`가 재호출되지 않음) | 확인 필요 — iOS 설정 피커(AppIntents)는 OS가 선택 즉시 `getTimeline`을 새로 호출하는 구조라 구조적으로 이 버그 클래스가 없을 가능성이 높지만, 실기기/시뮬레이터 미확인 | `DdayGlanceWidget.kt`, `DdayWidgetConfigurationActivity.kt` |
| 6 | 테마 시스템(Plan 2) — 위젯에 고정 색상(배경/글자) 적용, 설정 화면에서 선택 | 구현함(2026-10-05), **실기기 확인 완료** — `라벤더`/`민트`/`선셋` 각각 배치 직후 올바른 색으로 렌더링됨, 재구성(아래 행 7)으로 테마 변경도 확인 | 확인 필요 — 빌드(`xcodebuild build`, 위젯 익스텐션 타겟 포함) 성공 + 시뮬레이터 설치/실행까지만 확인함. `simctl`에는 adb 같은 터치 합성 CLI가 없어 "위젯 추가 → 설정 화면 열기 → 테마 선택"을 실제로 조작해 화면을 보는 것 자체가 불가능 — 사용자가 Xcode/시뮬레이터에서 직접 눌러봐야 함. `Color(argb:)`(ARGB Int64 → SwiftUI Color 변환, `DdayWidgetExtension.swift`)는 코드 리뷰로 채널 순서까지 수동 검증했지만 자동/시각 검증은 없음 | `Theme.kt`, `DdayGlanceWidget.kt`, `DdayWidgetConfigurationActivity.kt`, `AppIntent.swift`, `DdayWidgetExtension.swift` |
| 7 | 테마 시스템 — 위젯을 홈 화면에 배치한 뒤에도 테마를 바꿀 수 있어야 함 | **구조적 비대칭 발견, 수정함(2026-10-05).** `dday_widget_info.xml`에 `android:widgetFeatures="reconfigurable"`이 없으면 런처가 재구성 진입점을 전혀 안 줘서, 배치 후에는 테마를 절대 못 바꾸고 위젯을 지우고 새로 추가해야 했음 — 추가 후 실기기에서 길게 눌러 연필 아이콘(편집) 확인, 탭해서 라벤더→민트→선셋 전환까지 확인 | 가능(확인됨, 정상) — WidgetKit은 `AppIntentConfiguration` 위젯에 길게 눌러 "위젯 편집" 진입점을 OS가 기본 제공하므로 이 버그 클래스 자체가 Android만의 문제였음 | `dday_widget_info.xml` |
| 8 | 커플·친구 공유 위젯 플랜을 머지하면 이미 출시된 iOS 디데이 위젯 익스텐션의 링크가 깨짐(미정의 Firebase ObjC 심볼 약 30개) | 해당 없음 — Android는 JVM 클래스패스라 Kotlin/Native의 "모듈 전체를 한 바이너리로 정적 링크" 문제가 구조적으로 없음. `androidApp`은 `:shared` → `:shared-core` 전이 의존으로 그대로 빌드됨(`assembleDebug` 그린) | **확인됨 (수정 완료, 2026-10-11)** — iOS만의 문제였고 `shared`를 `shared-core`(Firebase-free) / `shared`(Firebase 의존)로 분리해 해결. `DdayWidgetExtensionExtension`과 `DdayWidgetExtensionTests` 둘 다 clean `xcodebuild build`에서 미정의 심볼 0개로 성공함을 직접 확인. 다만 **위젯이 실제로 홈 화면에서 렌더링되는지는 여전히 미검증**(시뮬레이터 수동 조작 필요) | `shared-core/build.gradle.kts`, `iosApp/iosApp.xcodeproj/project.pbxproj`, `DdayWidgetExtension.swift`, `AppIntent.swift` |
| 9 | Firebase 콜러블/Firestore 응답 파싱이 런타임에 전부 throw(`data<Map<String, Any?>>()` — kotlinx.serialization에 `Any` 시리얼라이저가 없음). 페어링 코드 생성/입력, 신호 전송, 연결 구독 전부 end-to-end로 동작 불가 | 수정함(2026-10-11) — 콜러블별 `@Serializable` 응답 클래스 + `ConnectionDoc`/`SignalDoc`로 교체. **실기기 미검증** — 자동 테스트는 생성된 시리얼라이저와 필드 이름까지만 검증하고, SDK→디코드 실제 경로는 라이브 Firebase 프로젝트 없이는 테스트 불가 | 확인 필요 — 공통 코드(`commonMain`)라 Android/iOS 동일 경로지만, iOS는 Firebase SPM 수동 작업 전까지 실행 자체가 불가 | `FirebaseWidgetlyFunctions.kt`, `ConnectionRepository.kt`, `Connection.kt` |
| 10 | 루트 스코프 코루틴(`CoroutineScope(Dispatchers.Default)`)에서 예외가 나면 프로세스 전체가 죽음 | 수정함(2026-10-11) — `SupervisorJob` + `CoroutineExceptionHandler`로 격리/로깅. **실기기 미검증**(푸시 수신 중 네트워크 실패를 인위적으로 만들어야 확인 가능) | 해당 없음 — iOS에는 대응되는 Application/FirebaseMessagingService 루트 스코프가 없음(`IosBootstrap.kt`는 suspend 함수를 Swift 쪽에서 호출) | `WidgetlyApplication.kt`, `WidgetlyFirebaseMessagingService.kt` |

## 사용법

1. Android에서 새 버그를 고치면, 이 표에 행을 추가하고 iOS 상태를 `확인 필요`로 둡니다.
2. iOS를 수동으로 확인할 기회(시뮬레이터 스크린샷, 실기기 등)가 생기면 상태를 갱신합니다.
3. "해당 없음"으로 표시할 땐 반드시 구조적 근거(왜 iOS에서는 애초에 발생할 수 없는지)를 적습니다 — 확인을 안 한 게 아니라 확인이 불필요하다는 걸 구분하기 위함입니다.
