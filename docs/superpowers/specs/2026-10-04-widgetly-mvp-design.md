# Widgetly MVP 설계 스펙

## 1. 제품 개요

- **타겟**: 한국 10~20대. 숏폼(틱톡/릴스/쇼츠)에서 자연스럽게 퍼지는 바이럴 확산을 지향.
- **운영비**: Firebase Blaze 플랜이지만 무료 사용량 범위 안에서 운용 (아래 섹션 6 참고).
- **MVP 범위**: 아래 세 가지. 수익화(테마 결제/평생 이용권)와 바이럴 공유 장치는 MVP 밖, 트래픽/리텐션 검증 후 다음 단계에서 설계.
  1. 개인 위젯 코어 (디데이)
  2. 테마 시스템 (MVP에서는 전부 무료)
  3. 커플·친구 공유 위젯 (신호 주고받기)

## 2. 아키텍처 원칙

- **Kotlin Multiplatform + Compose Multiplatform**: 앱 본체(설정, 테마 선택, 연결 관리 화면)의 UI/로직을 Android·iOS 간 공유.
- **위젯 UI는 공유하지 않는다**: Android 홈 위젯(Glance)과 iOS 홈 위젯(WidgetKit)은 각 플랫폼 네이티브 프레임워크이므로 위젯 화면 자체는 플랫폼별로 구현한다.
- `shared` 모듈이 공유하는 것: 날짜/디데이 계산, streak 계산, 데이터 모델. 그리는 방식(UI)은 공유하지 않는다.
- **iOS 최소 타겟: iOS 17.** 위젯 내 인터랙티브 전송에 App Intents가 필요하고, 버전 분기 비용이 이득보다 크다고 판단해 분기하지 않는다.
- **Android는 minSdk 24 전체에서 위젯 내 전송 지원.** Glance의 `actionRunCallback`에서 WorkManager에 전송 작업을 enqueue하여 네트워크 제약/재시도를 자동 처리한다.
- **신호 전송은 반드시 서버(Cloud Functions callable) 경유.** 클라이언트가 `signals` 컬렉션에 직접 쓰지 않는다 — 이유는 섹션 4.2 참고.

## 3. 데이터 모델 (Firestore)

| 컬렉션/문서 | 필드 | 설명 |
|---|---|---|
| `users/{uid}` | nickname | 익명 인증 uid 기준. 클라이언트가 쓸 수 있는 건 닉네임 정도로 한정 |
| `users/{uid}/tokens/{tokenId}` | platform, updatedAt | 기기별 FCM 토큰. 재설치/새 기기 시 토큰 추가, push 실패(NotRegistered) 시 해당 토큰만 삭제 |
| `connections/{connectionId}` | memberUids[2], type(`couple`\|`friend`), status(`active`\|`disconnected`), createdAt, anniversaryDate, anniversaryUpdatedBy, streak, streakUpdatedDate, lastSignalByUid[uid]{presetType, sentAt} | 다중 연결 지원(한 uid가 여러 connection에 속할 수 있음). 날짜 경계는 `Asia/Seoul` 고정(MVP는 한국 사용자 전용이라 timezone 필드 없음) |
| `connections/{connectionId}/signals/{signalId}` | senderUid, presetType, sentAt(서버 타임스탬프), expireAt(TTL, 발신 후 30일) | `signalId`는 클라이언트가 생성한 UUID — WorkManager 재시도 시 idempotency key로 사용. **클라이언트 직접 쓰기 금지**(보안 규칙), `sendSignal` 콜러블 함수를 통해서만 생성 |
| `pairingCodes/{code}` | creatorUid, type(`couple`\|`friend`), createdAt, expiresAt | Firestore TTL 삭제는 최대 24시간 지연될 수 있어, 만료 판정은 함수가 `expiresAt`을 직접 비교해서 한다(TTL 자동 삭제에 의존하지 않음) |
| `codeAttempts/{uid}` (서버 전용) | failCount, windowStart | 6자리 코드 무차별 대입 방지. 일정 횟수 실패 시 짧은 시간 잠금 |
| 로컬 D-day (기기 저장, Firestore 아님) | id, title, targetDate, countMode(`startAtOne`\|`startAtZero`), repeat(`none`\|`yearly`) | 한국식 디데이 계산(기준일을 1일로 세는 관습) 지원 |

**엔타이틀먼트(테마 소유권)는 MVP에 존재하지 않는다.** 결제를 도입하는 시점에 `entitlements/{uid}` 컬렉션을 신설하되, **반드시 서버만 쓰기 가능한 별도 컬렉션**으로 만든다 — 클라이언트가 닉네임을 쓰는 `users` 문서에 소유권 필드를 같이 두면 보안 규칙이 꼬인다 (`docs/theme-system.md` 참고).

**공개 결정 필요**: `type == couple`인 connection을 사용자당 1개로 제한할지. 일반적인 커플 앱 관례상 제한하는 쪽으로 가정하고 `createConnection`에 캡을 걸어두지만, 제품 결정이 다르면 변경한다.

위젯 인스턴스는 OS의 위젯 구성 기능으로 "어떤 대상(connectionId 또는 로컬 D-day id)을 보여줄지"를 저장한다 (Android: Glance 위젯 파라미터, iOS: `WidgetConfigurationIntent`).

## 4. 핵심 플로우

### 4.1 페어링 플로우
1. User A가 앱에서 "연결 만들기"(종류 선택: couple/friend) → Cloud Function이 6자리 코드 발급, `pairingCodes/{code}` 생성(10분 만료)
2. User A가 코드를 앱 밖 수단(카카오톡/문자 등)으로 상대에게 전달
3. User B가 앱에 코드 입력 → `redeemPairingCode` 콜러블 함수:
   - `codeAttempts/{uid}` 확인 — 실패 횟수 초과 시 잠금 응답
   - 코드 존재/미만료(`expiresAt` 직접 비교) 확인
   - `type == couple`이고 사용자당 1개 제한 정책이면, 이미 활성 couple connection이 있는지 확인
   - 통과 시 `connections/{connectionId}` 생성(양쪽 uid, status=active), 코드 문서 삭제
4. 관계 디데이 기준일 입력 — 한쪽이 입력하면 즉시 반영, `anniversaryUpdatedBy`에 입력자 기록(상대에게 "OO님이 기준일을 바꿨어요" 알림용), 추후 수정 가능
5. 양쪽에 "연결됨" 알림 + 위젯 추가 유도

### 4.2 신호 전송 플로우

신호는 클라이언트가 Firestore에 직접 쓰지 않고, **`sendSignal` 콜러블 함수 하나로만** 보낸다. (최초 설계는 클라이언트 직접 쓰기 + `onCreate` 트리거로 사후 검증/거부였으나, `onCreate`는 문서가 이미 커밋된 뒤에 실행되어 "쓰기 거부"가 불가능하고 경쟁 상태가 생기는 결함이 있어 폐기했다.)

1. 위젯(또는 앱)에서 프리셋 이모지 탭. 버튼에 쿨다운 UI 적용 — **서버와 동일한 값(N초)을 사용**하고, 서버가 거부하면 그 결과를 클라이언트에 돌려줘 "잠시 후 다시 보내주세요"를 보여준다(클라이언트 쿨다운과 서버 윈도우가 따로 노는 걸 방지)
2. **Android**: Glance `actionRunCallback` → WorkManager에 enqueue(네트워크 재시도 자동 처리) → `sendSignal(connectionId, preset, signalId)` 호출
   **iOS (17+)**: App Intent의 `perform()`에서 `sendSignal(...)` 호출. Firestore SDK 전체를 익스텐션에 넣지 않고 HTTPS 콜러블만 호출 — 위젯/인텐트 익스텐션의 메모리 제한 위험을 줄인다.
3. `sendSignal` (트랜잭션 하나로 처리):
   - 인증 확인, 호출자가 connection 멤버인지 확인, `status == active` 확인
   - `signalId`가 이미 처리된 적 있으면 재시도로 간주하고 성공 응답만 반환(중복 생성 없음)
   - **레이트리밋은 (connectionId, 보낸 사람) 단위**: `lastSignalByUid[내 uid].sentAt + N초 > 지금`이면 `{ rateLimited: true }` 반환하고 종료 — connection 전체 단위로 걸면 상대가 답장을 못 하게 되므로 반드시 발신자별로 건다
   - 통과 시 `signals` 문서 생성(서버 타임스탬프로 `sentAt` 기록 — 클라이언트 값 신뢰 안 함), `lastSignalByUid[내 uid]` 갱신
   - 오늘(Asia/Seoul 기준) 양쪽 모두 신호를 보냈고 `streakUpdatedDate`가 오늘이 아니면 streak +1, `streakUpdatedDate`를 오늘로 갱신(같은 날 중복 증가 방지)
4. 트랜잭션 성공 후 상대에게 push 발송. `collapse-key`/`apns-collapse-id`를 connectionId로 지정해 알림 센터에 최신 것만 남도록 한다.
5. **보안 규칙**: 클라이언트는 `signals`, `connections`의 streak/lastSignalByUid 등 서버 전용 필드에 직접 쓸 수 없다. 모든 변경은 Admin SDK(Cloud Functions)를 통해서만.

### 4.3 상대 위젯에 반영되는 경로 (우선순위)

실시간 연결은 없다 — 즉시성은 push가, 지속 표시는 위젯이 담당한다. 플랫폼별 우선순위:

- **Android**: FCM data message 수신 즉시 로컬 캐시 갱신 → Glance 다시 그리기. 거의 즉시 반영된다.
- **iOS 26+**: WidgetKit push(`apns-push-type: widgets`)로 시스템에 타임라인 재조회를 요청한다. 이 토큰은 FCM 토큰과 별개이므로 Cloud Function이 APNs로 직접 보내야 한다 — **FCM이 이 헤더를 그대로 전달해주는지는 구현 단계에서 실제로 검증 필요** (안 되면 APNs HTTP/2 API 직접 호출). 다만 Apple 문서상 이 푸시도 시스템의 갱신 한도 안에서 기회가 될 때 전달되는 것이라 즉시 반영이 보장되지는 않는다.
- **iOS 17~25**: push 도착 시 Notification Service Extension이 App Group 공유 컨테이너에 스냅샷을 쓰고 `WidgetCenter.shared.reloadTimelines(ofKind:)` 호출. 알림 권한을 끈 사용자는 이 경로가 아예 없다. 실기기 검증 필수.
- **공통 폴백 (모든 iOS 버전, 알림 꺼진 사용자 포함)**: 위젯의 `TimelineProvider`가 시스템 스케줄에 따라 갱신될 때, 캐시된 스냅샷만 다시 그리는 게 아니라 **직접 서버에서 connection의 최신 상태를 조회**한다. 이게 있어야 NSE/WidgetKit push가 실패해도 "언젠가는" 최신 데이터가 반영된다. 다만 이 조회는 섹션 6의 Firestore 읽기 할당량과 직결되므로 무조건 매 갱신마다 조회하지 않고, "푸시를 받은 직후"이거나 "마지막 조회 후 30분 이상 경과"했을 때만 조회한다.

### 4.4 예외 처리
- 코드 만료/오타 → 안내 후 재생성 유도, `codeAttempts`로 무차별 대입 방지
- 연타 → `sendSignal` 트랜잭션 내에서 (connection, 발신자) 단위로 거부, 결과를 클라이언트에 반환해 UI 피드백
- 상대 FCM 토큰 무효(앱 삭제 등) → 해당 토큰 문서만 삭제, 신호 자체는 저장되어 다음 접속 시 공통 폴백 경로로 반영됨
- 연결 해제 → `status`를 `disconnected`로 변경(문서를 바로 지우지 않음 — 위젯이 "연결 끊김"과 "네트워크 오류"를 구분할 수 있도록) + 상대에게 알림

## 5. 테스트 전략

- `shared`(commonTest): 디데이/streak 계산(countMode 포함), pairing 코드 검증 로직 — 순수 유닛 테스트
- Cloud Functions: Firebase Emulator Suite로 `sendSignal`의 레이트리밋/멱등성/streak 갱신, `redeemPairingCode`의 만료·무차별 대입 방지 테스트
- Android: Glance 액션 콜백 → WorkManager enqueue 여부 instrumented test
- iOS: NSE의 App Group 쓰기 로직은 XCTest, 위젯까지 실제 반영되는지는 실기기 수동 검증(자동화 어려움, 리스크로 유지)

## 6. 알려진 리스크

- **Cloud Functions는 Blaze 플랜이 MVP 첫날부터 필요하다** (Spark 플랜에서는 Functions 자체를 실행할 수 없음). 다만 Blaze에서도 Firestore/Functions 무료 사용량은 그대로 적용되어 초기 비용은 거의 0이다. Cloud Functions 2nd gen은 배포 시마다 컨테이너 이미지를 Artifact Registry에 쌓는데, 무료 구간(0.5GB)을 넘으면 소액이 청구된다 — 이미지 정리 정책과 예산 알림을 설정해야 한다.
- **Firestore 읽기 할당량이 쓰기보다 먼저 찰 가능성이 크다.** 공통 폴백 경로(4.3)가 매 위젯 갱신마다 서버를 조회한다고 가정하면, 위젯 3개 × 하루 약 50회 갱신 = 사용자 1명당 하루 150회 읽기이고, 일일 읽기 한도(5만 건) 기준 활성 사용자 300명대에서 한도에 닿는다. 대응: 개인 D-day는 로컬 계산만 하고 서버 조회 자체가 없음, 커플/친구 위젯의 서버 조회는 "푸시 수신 직후" 또는 "마지막 조회 후 30분 경과"로 제한(4.3 반영). 신호 1건 전송은 대략 읽기 2회·쓰기 2회로 추정.
- iOS 위젯 즉시 갱신 경로(WidgetKit push/NSE)의 안정성은 실기기 검증 전까지 불확실. 알림 권한을 끈 사용자는 NSE 경로가 없다 — 공통 폴백(4.3)만이 유일한 경로.
- `type == couple` 연결의 1개 제한 여부는 아직 제품 결정이 확정되지 않았다(섹션 3 참고).
