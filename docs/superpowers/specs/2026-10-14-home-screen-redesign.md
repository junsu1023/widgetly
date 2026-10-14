# 홈 화면 리디자인 (피그마 기반, 1단계)

**날짜:** 2026-10-14
**상태:** 설계 완료, 구현 계획 수립 대기

## 배경

Figma 파일 `Widgetly`(https://www.figma.com/design/N52YesucQ49bxLHh8rztTf/Widgetly)에 앱 홈 화면 1종 + 위젯 디자인 9종이 있다. 이번 스펙은 그중 **홈 화면(디데이 목록)만** 완성도 있게 구현하는 1단계다. 위젯 모양 확장(원형/정사각형), 위젯 그라데이션 색 교체, 배경 커스텀 위젯은 다음 단계로 분리한다.

## 범위

**이번 스펙에 포함:**
1. `DdayEntry`에 카테고리(`커플`/`친구`/`기타`)와 아이콘 필드 추가
2. 홈 화면: 탭 필터, 카테고리 아이콘 배지, 카드 디자인, 마스코트 표시
3. `AppRoot`를 하단 네비게이션 4탭(홈/위젯갤러리/테마/더보기) 구조로 재편 — 홈만 완성, 나머지 3탭은 최소 화면으로 자리만 잡음
4. 디데이 생성/수정 폼에 아이콘 선택 UI 추가(카테고리는 탭에서 이미 필터링 중이므로 생성 시점엔 카테고리도 같이 고름)

**범위 밖(다음 단계):**
- 위젯 모양 확장(원형/정사각형) — Android Glance/iOS WidgetKit 양쪽 새 레이아웃 필요, 별도 스펙
- 위젯 그라데이션 색 세트 교체, 배경 커스텀 위젯
- "위젯갤러리"/"더보기" 탭의 실제 콘텐츠 설계

## 1. 데이터 모델

`shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayEntry.kt`에 두 필드를 추가한다. 둘 다 `Serializable enum` + 기본값을 줘서, 이미 저장된 기존 항목을 디코딩할 때 깨지지 않게 한다(`kotlinx.serialization`은 누락 필드를 기본값으로 채운다 — 새 필드가 생겨도 과거 JSON을 그대로 읽을 수 있다).

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

data class DdayEntry(
    // ...기존 필드,
    val category: DdayCategory = DdayCategory.OTHER,
    val icon: DdayIcon = DdayIcon.HEART,
)
```

**배지 색은 아이콘에 종속된 값이지 별도로 고르는 게 아니다** — Figma 목업 4장을 픽셀 샘플링해 보면 아이콘 종류와 배지 색이 1:1로 고정돼 있다(하트=연핑크 `#FFE2E7`, 책=연블루 `#E3EDFF`, 케이크=연피치 `#FFE9DD`, 비행기=연블루 `#E4EEFF`). 색을 아이콘과 분리해서 또 하나의 선택 필드로 두면 사용자가 고를 선택지만 늘어나고 Figma가 보여준 조합과 어긋난 배지가 나올 수 있어, `DdayIcon` enum 자체에 색을 들려보낸다.

`DdayCategory`를 `shared`(Firebase 의존) 모듈의 기존 `connection.ConnectionType`(COUPLE/FRIEND)과 통합하지 않고 `shared-core`에 새로 둔다 — 이름이 겹치더라도, `shared-core`는 Firebase 의존성 제로가 원칙이라 `shared`의 타입을 끌어오면 그 경계를 깨게 된다. 두 enum이 같은 이름의 값을 갖는 건 우연이 아니라 의미가 같기 때문이지만, 타입은 각자의 모듈에 독립적으로 둔다 — iOS 쪽 `ConnectionSnapshot` 구조체를 두 Swift 파일에 의도적으로 중복시켜 둔 것과 같은 이유.

**마스코트 표시는 항상 "내 캐릭터" 1개만.** Figma의 "친구랑 여행가기" 카드에 캐릭터가 2마리(토끼+곰) 그려져 있지만, 이건 실제 페어링된 상대의 캐릭터를 끌어오는 게 아니라 디자인 목업의 연출로 본다 — `DdayEntry`는 로컬 전용 데이터라 특정 Firebase 연결과 묶여 있지 않고, 항목별로 "이 디데이는 어떤 연결에 속하는지"를 연결하려면 새로운 데이터 링크(어떤 connectionId와 묶을지)가 필요해져 범위가 커진다. `category`는 순수 분류 라벨이고, 카드에는 항상 로컬에 저장된 내 마스코트 1개만 작게 리사이즈해서 보여준다. 두 마리 표시는 이미 구현된 커플·친구 공유 위젯(`ConnectionGlanceWidget`) 쪽 몫으로 남겨둔다.

## 2. 홈 화면 UI

`shared-core/src/commonMain/kotlin/com/example/widgetly/dday/DdayListScreen.kt`를 아래 구조로 교체한다 (Firebase 비의존 — 이 화면은 지금처럼 `shared-core`에 그대로 둔다).

- **상단 바**: "D-day!" 타이틀(Do Hyeon, `#332621`), 오른쪽에 추가(+)/설정(⚙) 아이콘 버튼 2개. 피그마는 이 둘을 유니코드 글자로 그렸지만, 플랫폼마다 폰트에 따라 글리프가 다르게/깨져 보일 위험이 있어 실제 벡터 아이콘(Material Icons의 `Add`/`Settings` 또는 이미 쓰는 아이콘 세트)으로 구현한다 — 디자인(위치/크기/색)은 그대로 따른다.
- **탭 필터**: `전체/커플/친구/기타` — `전체`는 필터 없음, 나머지 3개는 `entry.category`와 일치하는 것만 보여준다. 활성 탭은 굵은 `#342923`, 비활성 탭은 `#887F7B`(피그마 그대로).
- **카드**: 왼쪽 원형 배지(해당 `icon.badgeColorArgb` 배경 + `icon.glyphColorArgb` 색 아이콘 글리프), 가운데 제목(Dongle 15sp)/숫자(Do Hyeon 27sp)/날짜(`#8D8581` 12sp), 오른쪽에 내 마스코트 전신 일러스트를 작게(약 54×54pt 영역) 리사이즈해서 배치. 카드 배경은 흰색(`#FFFFFF`), 페이지 배경은 거의 흰색에 가까운 크림(`#FFFEFC`).
- **빈 상태**(필터링 결과 0건, 또는 전체 0건)는 기존 빈 상태 패턴 유지, 새로 디자인하지 않는다.

## 3. 하단 네비게이션 4탭

`shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt`의 `AppScreen` enum을 2~3개짜리 임시 토글에서 4탭 구조로 바꾼다:

| 탭 | 내용(이번 스펙) |
|---|---|
| 홈 | 위 2절에서 완성한 디데이 목록 |
| 위젯갤러리 | 최소 placeholder 화면("위젯 설치 방법 안내" 정도의 정적 텍스트) — 콘텐츠 설계는 다음 단계 |
| 테마 | 기존에 이미 구현된 `MascotPickerScreen`을 그대로 연결 |
| 더보기 | 기존에 이미 구현된 `PairingScreen`(커플·친구 연결)을 그대로 연결 |

하단 네비게이션 바 자체(아이콘 4개 + 선택 상태 표시)는 피그마 디자인을 따라 새로 만든다. 탭 전환 로직은 기존 `var currentScreen by remember { mutableStateOf(...) }` + `when` 패턴을 그대로 확장 — 네비게이션 라이브러리를 새로 추가하지 않는다.

## 4. 디데이 생성/수정 폼

피그마에 생성 폼 디자인은 없다(홈 화면 1장뿐). 기존 `DdayListScreen`의 인라인 생성 폼에 **아이콘 4종 선택 UI**(하트/책/케이크/비행기, 탭 한 번으로 선택 — 테마 선택 그리드와 같은 패턴)와 **카테고리 선택**(커플/친구/기타)을 추가한다. 기존 필드(제목/날짜/카운트 방식/반복)는 그대로 유지.

## 구현 아키텍처 비교

- *카테고리를 `shared`의 `ConnectionType`과 공유* — 모듈 경계(shared-core는 Firebase 비의존) 위반이라 기각, `shared-core`에 독립 enum 신설.
- *배지 색을 아이콘과 분리된 별도 필드로* — Figma가 고정 조합으로 보여준 걸 괜히 더 넓히는 과한 설계(YAGNI)라 기각, `DdayIcon` enum에 색을 들려보냄.
- *카드에 파트너 캐릭터까지 표시(피그마 친구 카드 그대로)* — `DdayEntry`를 특정 Firebase 연결과 묶는 새 링크가 필요해져 범위가 커짐. 이미 구현된 커플·친구 공유 위젯이 그 역할을 하므로, 홈 목록은 내 캐릭터 1개로 단순화.
- *하단 네비게이션에 새 라우팅 라이브러리 도입* — 화면 2~3개 규모에 과한 투자, 기존 `enum` + `when` 패턴 확장으로 충분.

## 테스트/검증

- `DdayCategory`/`DdayIcon` 추가가 기존 직렬화를 깨지 않는지: 필드 없는 과거 JSON을 디코딩해 기본값(`OTHER`/`HEART`)으로 채워지는지 유닛 테스트.
- 탭 필터링 로직(카테고리별 필터 + 전체) 유닛 테스트.
- 비주얼 결과(정확한 색/폰트/레이아웃)는 Android 실기기로만 확인 가능 — `docs/cross-platform-verification.md` 관행대로 기록.

## 열린 리스크

- "위젯갤러리"/"더보기" 탭에 기존 화면을 그대로 연결하는 건 임시 배치다 — 나중에 각 탭의 실제 콘텐츠가 정해지면 다시 설계해야 한다.
- 피그마 텍스트가 전부 "Inter"로 찍혀 있어 실제 의도된 폰트인지 불확실 — 기존에 확정한 Do Hyeon/Dongle을 그대로 적용하는 걸 기본으로 하되, 다른 의도였다면 추후 수정.
