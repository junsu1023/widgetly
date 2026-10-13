# 마스코트 캐릭터 선택 (아바타화)

**날짜:** 2026-10-13
**상태:** 설계 완료, 구현 계획 수립 대기

## 배경

[마스코트 캐릭터 스펙](2026-10-11-mascot-and-layout-polish-design.md)으로 쿼카 1종을 위젯/로딩화면/앱 배경에 고정 장식으로 넣었다. open-design으로 비주얼 아이덴티티를 재검토하는 과정에서, 사용자가 고양이/곰/토끼/햄스터 4종 캐릭터를 직접 추가로 준비했고, "5종을 전부 장식용으로 쓰기"보다 "사용자가 자기 캐릭터를 고르는 아바타" 쪽이 더 좋은 아이디어라는 데 합의했다.

## 범위

**이번 스펙에 포함:**
- 5종 캐릭터(쿼카/고양이/곰/토끼/햄스터) 카탈로그
- 사용자가 설정 화면에서 언제든 자기 캐릭터를 바꿀 수 있는 기능
- 선택한 캐릭터가 개인 D-day 위젯 + 커플·친구 연결 위젯(페어링된 경우 내 캐릭터와 파트너 캐릭터 둘 다) + 앱 배경/스플래시에 반영

**범위 밖:**
- 캐릭터 추가 업로드/커스텀 캐릭터 제작 — 고정 5종만
- 캐릭터별 유료화 — MVP는 테마와 동일하게 전부 무료

## 데이터 모델

**카탈로그 (`shared-core`):** 기존 `ThemeCatalog`와 동일한 패턴.

```
shared-core/theme/Mascot.kt
  data class Mascot(id: String, name: String)
  object MascotCatalog {
      val all: List<Mascot>  // quokka, cat, bear, rabbit, hamster
      val DEFAULT_MASCOT_ID = "quokka"
      fun find(id: String): Mascot =
          all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_MASCOT_ID }
  }
```

**내 캐릭터 (로컬):** 위젯별 설정인 테마와 달리, 아바타는 "나"를 나타내는 앱 전체 단위 설정이라 위젯 구성 파라미터가 아니라 `DdayStorage`가 이미 쓰는 것과 같은 로컬 저장 방식을 그대로 재사용한다 — Android는 `AndroidDdayStorage.kt`와 같은 Jetpack DataStore, iOS는 `IosDdayStorage.kt`와 같은 `NSUserDefaults(suiteName: "group.com.example.widgetly")`. 앱 그룹을 공유하는 이유도 동일: 위젯 익스텐션이 메인 앱이 저장한 값을 직접 읽어야 하기 때문.

**파트너에게 보이기 (Firestore):** `connections/{connectionId}` 문서에 `lastSignalByUid`와 동일한 패턴으로 `mascotByUid: Map<uid, mascotId>` 필드를 추가한다.

`firestore.rules`를 확인한 결과 `connections/{connectionId}`는 `allow list, create, update, delete: if false`로 **모든 클라이언트 쓰기가 완전히 막혀 있다** — CLAUDE.md의 "신호 전송/연결 생성은 Cloud Functions callable 경유" 원칙이 이 문서 전체에 이미 적용된 상태다. 따라서 `mascotByUid` 역시 장식용 필드라도 클라이언트가 직접 쓸 수 없고, 새 Cloud Functions callable `updateMascot(connectionId, mascotId)`을 추가해야 한다 — 호출자가 해당 connection의 멤버인지 확인 후 Admin SDK로 `mascotByUid.{uid}`만 merge. 시그널 전송처럼 레이트리밋/멱등성이 필요한 작업은 아니지만, 쓰기 경로 자체가 규칙상 콜러블 외에 존재하지 않는다.

(참고: `users/{uid}` 문서에도 `nickname` 필드에 한해 클라이언트 스코프 쓰기 규칙이 이미 있지만 — `docs/firebase-pairing.md`에 설계만 돼 있고 실제 코드는 없는 상태 — `allow get`이 `request.auth.uid == uid`로 본인만 읽을 수 있어 파트너가 읽을 방법이 없다. 그래서 아바타는 처음부터 `connections` 문서 쪽으로 설계한다.)

읽기는 새 경로가 필요 없다 — 앱 화면은 `ConnectionRepository.observeConnection`의 기존 실시간 리스너를 그대로 타고, 백그라운드 위젯은 스트릭/파트너 데이터가 내려오는 기존 캐시 경로(`docs/firebase-pairing.md`의 푸시 트리거/30분 캐시)를 그대로 탄다.

**마이그레이션:** `mascotByUid`에 값이 없는 uid는 `MascotCatalog.find()`의 기본값 폴백으로 자동으로 `quokka` — 기존 사용자/연결에 아무 영향 없음.

## 위젯 표시 로직

- **개인 D-day 위젯:** 빈 상태의 프로미넌트 마스코트, 데이터 상태의 코너 장식(현재 크기별 1~3개 반복, `MascotCornerDecoration.kt`) 둘 다 고정 쿼카 대신 로컬에 저장된 "내 캐릭터"로 교체한다. 반복 개수 스케일링 로직(`mascotCountForSize`)은 그대로 유지 — 같은 캐릭터를 1~3개 반복하는 장식적 의미는 변하지 않는다.
- **커플·친구 연결 위젯:** 페어링 전(연결 끊김 등 빈 상태)에는 내 캐릭터만 보인다. 페어링되고 실데이터(스트릭 등)가 있을 때는 지금처럼 "같은 캐릭터 1~3개 반복"이 아니라 **내 캐릭터 + 파트너 캐릭터, 정확히 2개**를 나란히 보여준다 — 두 사람을 나타내는 아이덴티티이지 장식적 반복이 아니기 때문. 이를 위해 `MascotCornerDecoration`을 개인용(기존 그대로)과 연결용(새로 분리, 두 캐릭터를 파라미터로 받음)으로 나눈다. 크기 단계(소/중/대)는 "개수"가 아니라 "아이콘 크기"에 매핑한다.
- **빈 상태들:** D-day/연결 위젯의 "설정 필요"/"삭제됨"/"연결 끊김"/"불러오는 중"은 전부 내 캐릭터만 사용 — 파트너 캐릭터는 실제 연결 데이터가 있을 때만 의미가 있다.
- **앱 배경/스플래시 (Compose Multiplatform 공유):** `MascotBackground` 컴포저블과 Android 스플래시, iOS `UILaunchScreen_ImageName`도 고정 쿼카 대신 로컬에 저장된 내 캐릭터를 사용한다.

## 설정 화면

기존 설정/프로필 영역에 "캐릭터 선택" 섹션을 추가한다 — 5개 캐릭터를 그리드로 나열, 탭하면:
1. 로컬 저장소에 즉시 반영(낙관적 업데이트, UI는 바로 바뀐 캐릭터를 보여줌)
2. 커플·친구로 페어링된 상태라면 `updateMascot` 콜러블을 호출해 파트너 쪽에도 반영

페어링되어 있지 않다면 2번은 건너뛴다 — 로컬 저장만으로 충분하다.

## 구현 아키텍처 비교

- *아바타를 위젯 구성 파라미터(테마처럼)로 저장* — 위젯마다 다른 캐릭터를 고를 수 있게 되는데, "나를 나타내는 정체성"이라는 이번 요구와 맞지 않는다(위젯마다 다른 사람으로 보이는 꼴). 기각.
- *`mascotByUid` 쓰기를 클라이언트 직접 Firestore 쓰기로* — `firestore.rules`가 `connections` 문서 전체를 콜러블 전용으로 이미 막아놔서 애초에 불가능. 새 콜러블 추가로 확정.
- *파트너 캐릭터를 위젯에서 실시간 리스너로 직접 구독* — 위젯(Glance/WidgetKit)은 포그라운드 앱처럼 상시 리스너를 유지할 수 없고, 기존에 이미 스트릭/연결 데이터를 가져오는 캐시 경로가 있으므로 그걸 재사용하는 게 자연스럽다. 새 경로를 만들지 않는다.

## 열린 리스크

- 사용자가 전달한 고양이/곰/토끼/햄스터 이미지는 크림색 배경이 포함된 사각 이미지라, 위젯/배경 장식에 쓰려면 쿼카 원본처럼 배경을 제거한 투명 PNG로 먼저 다듬어야 한다 — 초벌 배경 제거 작업을 진행했으나 최종 품질은 실제 구현 단계에서 재검증 필요.
- 캐릭터가 1종 → 5종으로 늘면서 플랫폼별 번들 비용(Compose 리소스 / Android Glance drawable + alpha-baked "faint" 변종 / iOS 위젯 익스텐션 애셋 / iOS 메인 앱 애셋, 총 4곳)도 5배로 늘어난다 — 10/11 스펙에서 이미 "비용으로 받아들이고 최적화하지 않는다"고 판단한 것의 연장선.
- 새 `updateMascot` Cloud Functions 콜러블은 기존 함수 테스트 관례(`npx firebase-tools emulators:exec --only firestore ...`)에 맞춰 유닛 테스트 + 규칙 테스트가 필요하다.
- iOS 위젯 익스텐션/메인 앱은 Firebase SPM 미해결 상태라 `mascotByUid` 동기화 경로의 실기기 검증이 Android만 가능하다 — `docs/cross-platform-verification.md`에 기록하고 추후 확인.
