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

## 사용법

1. Android에서 새 버그를 고치면, 이 표에 행을 추가하고 iOS 상태를 `확인 필요`로 둡니다.
2. iOS를 수동으로 확인할 기회(시뮬레이터 스크린샷, 실기기 등)가 생기면 상태를 갱신합니다.
3. "해당 없음"으로 표시할 땐 반드시 구조적 근거(왜 iOS에서는 애초에 발생할 수 없는지)를 적습니다 — 확인을 안 한 게 아니라 확인이 불필요하다는 걸 구분하기 위함입니다.
