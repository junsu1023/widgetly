# Widgetly 비주얼 디자인 시스템

**날짜:** 2026-10-11
**상태:** 설계 완료, 구현 계획 수립 대기

## 배경

MVP 3대 기능(개인 디데이 위젯, 테마 시스템, 커플·친구 공유 위젯)은 모두 구현되어 `develop`에 머지되었지만, 지금까지 비주얼 디자인은 전혀 다뤄지지 않았다. 앱 화면(설정/페어링)은 Material3 기본값 그대로이고, 위젯 "테마"는 배경/글자색 3개 팔레트(라벤더/민트/선셋)뿐이다. `docs/design-guidelines.md`도 CLAUDE.md에 "UI 설계 단계에서 작성"이라고 명시된 대로 아직 비어 있다.

타겟이 한국 10~20대, 숏폼(틱톡/릴스/쇼츠)에서 바이럴로 퍼지는 걸 지향하는 앱이라는 점을 고려해 지금 시점에 전체적인 비주얼 아이덴티티를 설계한다.

## 범위

**이번 스펙에 포함:**
1. 앱 화면(설정/페어링 등, Compose Multiplatform 공유 UI) — 컬러, 타이포그래피, 모양/아이콘 스타일
2. 위젯 색상 테마 — 스타일 방향 및 테마 종류 확장/교체

**범위 밖 (다음 단계로 분리, 이 스펙에서 상세 설계 안 함):**
- 앱 아이콘/브랜드 로고
- 숏폼(틱톡/릴스) 바이럴 공유 시 노출되는 비주얼(위젯 캡쳐 공유 화면 등)

## 디자인 방향

**무드:** 파스텔·큐트 — 한국의 "다꾸(다이어리 꾸미기)" 감성. 둥근 모양, 부드러운 파스텔톤, 손맛 있는 폰트.

**라이트/다크 모드:** 둘 다 지원한다. 단, **앱 화면에만 적용** — 위젯은 시스템 다크모드 설정과 무관하게 사용자가 고른 테마 색을 항상 유지한다(지금 `ConnectionGlanceWidget.kt`/`DdayGlanceWidget.kt`가 이미 `ColorProvider(day = X, night = X)`로 day/night에 같은 색을 쓰는 패턴이라 — 이 기존 동작을 바꾸지 않는다).

이 방향은 사용자와 함께 브라우저 기반 비주얼 브레인스토밍 도구로 실제 목업(디데이 카드/버튼/목록, 라이트·다크 미리보기)을 비교해 확정했다.

## 타이포그래피

| 용도 | 폰트 |
|---|---|
| 제목, 큰 숫자(D+127 같은 디데이 표시), 버튼 | **Do Hyeon** |
| 본문, 목록, 설명 텍스트 | **Dongle** |

둘 다 구글 폰트(OFL 라이선스, 무료 상업 이용 가능)다.

**적용 범위:** 앱 화면뿐 아니라 **위젯 글자에도 동일하게 적용**한다 — 지금 위젯은 시스템 기본 폰트를 쓰고 있는데, D+127 같은 숫자도 Do Hyeon, 보조 텍스트는 Dongle로 바꾼다.

**폰트 번들링 — 플랫폼/타겟별로 따로 등록해야 함 (중요한 구현 비용):**
- **앱 화면 (Android+iOS 공통)**: Compose Multiplatform 리소스 메커니즘으로 `shared-core`에 한 번만 선언하면 `AppRoot`를 호스팅하는 양쪽 메인 앱에 자동 적용된다.
- **Android 위젯(Glance)**: Glance의 `androidx.glance.text.FontFamily`는 Compose 리소스를 직접 소비하지 않으므로, `androidApp`에 네이티브 Android 폰트 리소스(`res/font/*.ttf`)로 별도 등록해야 한다.
- **iOS 위젯 익스텐션(SwiftUI)**: 익스텐션은 메인 앱과 별도 바운들이라, `DdayWidgetExtensionExtension` 타겟 자체의 `Info.plist`에 폰트 파일을 추가하고 `UIAppFonts`로 선언해야 한다 — 메인 앱에 등록해도 익스텐션에는 적용되지 않는다.

## 컬러 시스템 (앱 화면)

**브랜드 컬러:** 피치/옐로우 계열. 라이트/다크 공통 포인트 컬러 `#FFA45C`.

| 토큰 | 라이트 | 다크 |
|---|---|---|
| Background | `#FFF4E8` | `#2E2015` |
| Surface(카드) | `#FFE7CC` | `#3F2C18` |
| Primary(버튼/강조) | `#FFA45C` | `#FFA45C` |
| OnBackground/OnSurface(텍스트) | `#4A2F14` | `#FFE6C7` |

시스템 다크모드 설정을 따라 자동으로 라이트/다크 전환한다(Compose `isSystemInDarkTheme()` 또는 동급 메커니즘).

## 위젯 테마 카탈로그

기존 3개(라벤더/민트/선셋)를 전부 교체해 4개로 재구성한다. 기본 테마(`DEFAULT_THEME_ID`)는 `lavender`에서 `peach`로 바뀐다.

| id | 이름 | 배경 | 글자 | 비고 |
|---|---|---|---|---|
| `peach` | 피치 | `#FFF0E5` | `#7A3B12` | 기존 `sunset`의 색값을 그대로 유지, id/이름만 변경 — 앱 브랜드 컬러 계열과 통일 |
| `lemon` | 레몬 | `#FFFBE0` | `#6B5800` | 신규 |
| `skyblue` | 스카이블루 | `#E6F4FF` | `#1B4B73` | 신규 |
| `cream` | 크림 | `#FFF8EE` | `#5C4A33` | 신규 |

`lavender`/`mint` 테마는 제거한다. `Theme` 데이터 클래스 구조(`id`/`name`/`backgroundColorArgb`/`foregroundColorArgb`)는 변경하지 않는다 — `ThemeCatalog.all`의 내용만 바뀐다.

**마이그레이션 고려사항:** 이미 `lavender`/`mint`/`sunset`을 선택해 위젯을 배치해둔 사용자가 있다면(아직 공개 출시 전이라 가능성은 낮음), 저장된 `theme_id`가 더 이상 카탈로그에 없는 값이 된다. `ThemeCatalog.find(id)`는 이미 "찾지 못하면 기본 테마로 폴백"하는 방어 로직이 있으므로(`all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_THEME_ID }`) 크래시 없이 자동으로 `peach`로 대체된다 — 별도 마이그레이션 코드 불필요.

## 모양 & 아이콘

- **모서리(corner radius):** 카드 20dp/pt, 버튼 14dp/pt — 전체적으로 크게 둥근 "스티커" 느낌
- **그림자/엘리베이션:** 무거운 그림자 대신 얇은 보더 또는 아주 옅은 그림자만 사용 — 평평하고 깔끔한 카드
- **아이콘:** 외곽선(outline)보다 꽉 채운(filled) 둥근 스타일 선호

## 구현 아키텍처

**토큰은 하나, 적용은 두 곳.**

```
shared-core/theme/
  ├─ Theme.kt            (기존, 위젯 색상 테마 — 내용만 4개로 교체)
  ├─ AppColorTokens.kt    (신규, 앱 화면용 라이트/다크 컬러 토큰)
  └─ (폰트 참조는 Compose 리소스 선언으로, 별도 Kotlin 상수 불필요)
```

- **앱 화면 (Compose Multiplatform, Android+iOS 공유):** `AppColorTokens`를 읽어 라이트/다크 `ColorScheme`과 `Typography`(Do Hyeon/Dongle)를 구성하는 `WidgetlyTheme` Composable을 만들고, `AppRoot`를 이 `MaterialTheme` 래퍼로 감싼다. 한 번만 작성하면 `MainActivity.kt`(Android)와 `MainViewController.kt`(iOS) 양쪽에 자동 적용된다.
- **위젯 (플랫폼별 네이티브, UI는 공유하지 않음 — 기존 아키텍처 원칙 유지):** `ConnectionGlanceWidget.kt`/`DdayGlanceWidget.kt`(Android)와 `ConnectionWidget.swift`/`DdayWidgetExtension.swift`(iOS)는 지금처럼 `ThemeCatalog`의 raw ARGB 값을 각자 읽어서 그린다 — UI 코드 공유 원칙(CLAUDE.md)은 바꾸지 않는다.

**다른 접근과 비교:**
- *플랫폼마다 디자인값을 따로 선언* — 두 플랫폼의 값이 따로 놀 위험(드리프트)이 있어 기각.
- *별도 디자인 시스템/컴포넌트 라이브러리 구축* — 화면이 2~3개뿐인 지금 앱 규모에는 과한 투자(YAGNI)라 기각.

## 테스트/검증

디자인 시스템 자체는 시각적 산출물이라 자동 테스트로 커버할 수 있는 부분이 제한적이다:
- `ThemeCatalog`/`AppColorTokens`의 데이터 정확성(색상값, `DEFAULT_THEME_ID` 변경 등)은 기존 `ThemeCatalogTest.kt` 패턴으로 유닛 테스트.
- 실제 렌더링 결과(색감, 폰트 적용, 다크모드 전환)는 Android는 실기기, iOS는 시뮬레이터/실기기 수동 확인이 필요하다 — `docs/cross-platform-verification.md` 관행을 그대로 따른다.

## 다음 단계 (이 스펙 범위 밖, 추후 별도 브레인스토밍 필요)

- **앱 아이콘/브랜드 로고** — 이번에 정한 피치 브랜드 컬러 + Do Hyeon 느낌을 이어받아 설계
- **숏폼 바이럴 공유 비주얼** — 위젯을 캡쳐/공유할 때 보이는 화면(워터마크, 공유 카드 등)

## 열린 리스크

- 폰트 번들링이 4곳(Compose 리소스, Android Glance 리소스, iOS 메인 앱, iOS 위젯 익스텐션)에 걸쳐 있어 구현 공수가 예상보다 늘어날 수 있다.
- 파스텔 톤 배색의 명암 대비(contrast ratio)가 접근성 기준(WCAG AA 등)을 만족하는지는 이번 설계에서 수치로 검증하지 않았다 — 구현 단계에서 실제 렌더링 후 확인 필요.
