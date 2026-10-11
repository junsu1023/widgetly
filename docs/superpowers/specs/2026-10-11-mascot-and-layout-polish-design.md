# 마스코트 캐릭터 + 레이아웃 일관성 정리

**날짜:** 2026-10-11
**상태:** 설계 완료, 구현 계획 수립 대기

## 배경

[비주얼 디자인 시스템 스펙](2026-10-11-visual-design-system-design.md)으로 컬러/타이포/위젯 테마는 정리됐지만, 사용자가 보기에 여전히 단조롭고 아래 두 가지가 아쉬웠다:

1. 특징점이 될 캐릭터(마스코트)가 없음
2. 버튼 배경색/간격이 화면마다 미묘하게 달라 보임(실제로는 색상값은 동일 — "커플/친구 연결하기" 버튼만 화면 전체 폭을 덮는 색상 바 형태라 다른 알약 모양 버튼들과 "취급"이 달라 보이는 것이었음)

## 범위

1. **마스코트 캐릭터** — 사용자가 직접 만든 오리지널 쿼카 캐릭터를 위젯/로딩 화면/앱 배경에 적용
2. **버튼/간격 일관성 정리** — 버튼 크기·모양 통일, 화면 내 요소 간 간격 통일

## 1. 마스코트 캐릭터

### 캐릭터

사용자가 직접 그린 오리지널 쿼카 캐릭터(통통하고 귀여운 디자인, 피치/탄 톤 — 이번 디자인 시스템의 브랜드 컬러와 잘 어울림). 배경을 제거해 투명 PNG로 준비했고, 라이트/다크/위젯 테마 4종 배경 전부에서 깨끗하게 보이는 것을 확인했다. 포즈는 기본 자세 1개만 사용한다(위젯/로딩화면/배경 전부 같은 이미지 재사용).

**원본 에셋**: `docs/assets/quokka-mascot.png`(1254×1254, 투명 배경, 547KB) — 플랫폼별 번들 작업은 전부 이 파일을 각 위치로 복사하는 것으로 시작한다.

**주의 — 유사 캐릭터 금지:** 이 캐릭터는 사용자 본인의 창작물이다. 구현 과정에서 임의로 "마시마로"나 "Marsh" 같은 기존 캐릭터의 특징(특정 눈매, 특정 귀 색상 등)을 참고하거나 섞지 않는다 — 이미 사용자가 그 방향을 명시적으로 거절했다.

### 배치 위치 (3곳)

| 위치 | 적용 범위 | 비고 |
|---|---|---|
| 위젯 빈 상태 | D-day/연결 위젯 둘 다, "설정 필요"/"삭제됨"/"연결 끊김"/"불러오는 중" 상태에서만 | 실제 데이터(디데이 숫자, 스트릭)가 보일 때는 숫자가 주인공이어야 하므로 넣지 않는다 — 좁은 위젯 공간이 복잡해 보이는 것을 피한다 |
| 앱 로딩 화면 | Android/iOS 네이티브 스플래시 스크린 기능 | 커스텀 로딩 화면(인위적 대기시간)을 만들지 않고, 양 플랫폼이 기본 제공하는 앱 실행 스플래시에 적용한다 |
| 앱 배경화면 | D-day 목록 화면, 페어링 화면 | 화면 하단 구석에 투명도 18%로 "살짝 걸터앉은" 듯 배치 — 텍스트/버튼을 가리지 않는 장식 요소 |

### 구현 아키텍처

폰트 작업 때와 같은 구조: **에셋 하나, 플랫폼별로 올바른 방식으로 번들**.

- **앱 화면 배경 (Android+iOS 공유, Compose Multiplatform)**: `shared/src/commonMain/composeResources/drawable/quokka.png`에 번들 → `painterResource(Res.drawable.quokka)`로 `DdayListScreen`/`PairingScreen`에 낮은 투명도로 그린다.
- **Android 위젯 (Glance)**: `androidApp/src/main/res/drawable-nodpi/quokka.png`에 네이티브 리소스로 번들 → Glance의 `Image(provider = ImageProvider(R.drawable.quokka), ...)`로 그린다. (주의: 이건 지난 작업에서 확인된 "Glance가 커스텀 폰트를 못 읽는" 제약과는 다른 경우다 — Glance의 `Image`/`ImageProvider`는 RemoteViews의 표준 비트맵 렌더링 경로를 그대로 쓰는 잘 지원되는 기능이라 폰트처럼 런처 프로세스에 막히는 문제가 없다.)
- **Android 로딩 화면**: `androidx.core:core-splashscreen` 라이브러리 추가 + 같은 이미지를 스플래시 아이콘 리소스로 등록 + `MainActivity.onCreate()`에서 `installSplashScreen()` 호출.
- **iOS 위젯 익스텐션 (SwiftUI)**: `iosApp/DdayWidgetExtension/Assets.xcassets`에 이미지 세트로 추가(기존 애셋 카탈로그 재사용, 폰트처럼 느슨한 파일로 안 둔다) → `Image("quokka")`로 그린다.
- **iOS 로딩 화면**: `iosApp/iosApp/Assets.xcassets`(메인 앱 전용 카탈로그)에 별도로 이미지 세트 추가 → `INFOPLIST_KEY_UILaunchScreen_ImageName` 빌드 설정으로 지정(현재 `INFOPLIST_KEY_UILaunchScreen_Generation = YES`로 자동 생성되는 스플래시에 이미지만 추가하는 것 — 코드 작성 불필요, Xcode 프로젝트 설정만 바뀜).

### 다른 접근과 비교

- *Compose `Canvas`로 코드 기반 벡터 드로잉(각 플랫폼 네이티브 벡터 API 재구현)* — 폰트 작업처럼 번들 파일 관리가 필요 없다는 장점은 있지만, 이미 완성된 래스터 이미지가 있는 지금 상황에서는 불필요한 재작업(Kotlin Canvas + Android VectorDrawable path 변환 + SwiftUI Path, 3벌을 전부 손으로 다시 그려야 함)이라 기각.
- *위젯에서도 실제 데이터 상태에 캐릭터 노출* — 좁은 공간에 숫자와 캐릭터가 경쟁해서 가독성이 떨어질 위험, 스펙 범위를 빈 상태로만 한정.

## 2. 버튼/간격 일관성 정리

### 버튼 크기/모양

`AppRoot`의 화면 전환 버튼("커플/친구 연결하기" / "디데이 목록으로")만 `fillMaxWidth()`로 화면 전체 폭을 덮는 색상 바 형태다 — 다른 모든 버튼("추가", "다음", "취소", "커플 코드 생성" 등)은 내용 크기에 맞는 알약 버튼이다. 이 버튼도 `fillMaxWidth()`를 제거하고 다른 버튼들과 같은 크기 체계(내용 크기 + `WidgetlyButtonShape`)로 바꾸고, 가로 중앙 정렬한다.

### 간격

`PairingScreen`은 이미 `Column`에 `verticalArrangement = Arrangement.spacedBy(16.dp)`를 걸고 자식 `Row`들은 패딩이 없는 깔끔한 패턴을 쓰고 있다. `DdayListScreen`은 반대로 각 `Row`마다 개별적으로 `.padding(16.dp)`(모든 방향) 또는 `.padding(horizontal = 16.dp)`(가로만)를 제각각 걸고 있어서, 연속된 두 "모든 방향 패딩" `Row` 사이는 32dp(양쪽 패딩이 더해짐)로 벌어지고 "가로만 패딩" `Row`들 사이는 0dp로 붙는 등 간격이 들쭉날쭉하다. `DdayListScreen`을 `PairingScreen`과 같은 패턴(`Column`에 `Arrangement.spacedBy(16.dp)` 한 번, 각 `Row`는 가로 패딩만)으로 맞춘다 — 새 간격 체계를 발명하지 않고 이미 코드베이스에 있는 올바른 패턴을 재사용한다.

## 열린 리스크

- iOS 로딩 화면(`UILaunchScreen_ImageName`)과 앱 배경 장식은 iOS 메인 앱이 빌드 자체가 안 되는 상태(Firebase SPM 보류)라 실기기/시뮬레이터 검증이 불가능하다 — `docs/cross-platform-verification.md`에 기록하고 추후 확인.
- 쿼카 이미지(547KB)가 4곳(Compose 리소스, Android 위젯 드로어블, iOS 익스텐션 애셋, iOS 메인 앱 애셋)에 번들되어 앱 용량이 다소 늘어난다 — 폰트 작업 때와 같은 수준의 비용으로 판단해 지금은 최적화(리사이즈/압축)하지 않는다.
