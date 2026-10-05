# Firebase 연결(pairing) & 신호

커플·친구 연결과 신호 주고받기의 백엔드 구현 컨벤션. 전체 배경/플로우는 `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` 참고.

## 백엔드 구성

Firebase 전체(Firestore + Cloud Functions + FCM + 익명 인증)를 사용한다. Supabase 등으로 나누지 않는 이유: FCM은 어차피 필요하고 완전 무료이며, Firestore 무료 사용량(일일 쓰기 2만 건·읽기 5만 건)은 Blaze 플랜에서도 그대로 적용된다. 백엔드를 나누면 배관만 늘어난다.

**Cloud Functions는 Blaze 플랜이 처음부터 필요하다** — Spark(무료) 플랜에서는 Cloud Functions 자체를 실행할 수 없다(공식 요구사항). Blaze에서도 무료 사용량이 적용되어 초기 비용은 거의 0이지만, 2nd gen Functions는 배포마다 Artifact Registry에 컨테이너 이미지를 쌓으므로 무료 구간(0.5GB)을 넘기지 않도록 이미지 정리 정책과 예산 알림을 설정해야 한다.

인증은 **익명 인증**만 사용한다 — 전화번호 인증(SMS 비용 발생 가능)을 쓰지 않기 위함. 연결은 **6자리 코드**로 한다 — Firebase Dynamic Links가 2025년 8월 종료되어 디퍼드 딥링크 기반 초대 링크를 쓸 수 없기 때문이다.

## Firestore 스키마

| 컬렉션/문서 | 필드 | 설명 |
|---|---|---|
| `users/{uid}` | nickname | 클라이언트가 쓸 수 있는 건 닉네임 정도로 한정 |
| `users/{uid}/tokens/{tokenId}` | platform, updatedAt | 기기별 FCM 토큰. push 실패(NotRegistered) 시 해당 토큰만 삭제 |
| `connections/{connectionId}` | memberUids[2], type(`couple`\|`friend`), status(`active`\|`disconnected`), createdAt, anniversaryDate, anniversaryUpdatedBy, streak, streakUpdatedDate, lastSignalByUid[uid]{presetType, sentAt} | 다중 연결 지원. 날짜 경계는 `Asia/Seoul` 고정 |
| `connections/{connectionId}/signals/{signalId}` | senderUid, presetType, sentAt(서버 타임스탬프), expireAt(TTL 30일) | `signalId`는 클라이언트가 만든 UUID(멱등성 키). **클라이언트 직접 쓰기 금지** — `sendSignal` 콜러블만 쓸 수 있음 |
| `pairingCodes/{code}` | creatorUid, type, createdAt, expiresAt | TTL 자동 삭제는 최대 24시간 지연될 수 있어 만료 판정은 함수가 `expiresAt`을 직접 비교 |
| `codeAttempts/{uid}` | failCount, windowStart | 6자리 코드 무차별 대입 방지 |

## Cloud Functions

### `sendSignal(connectionId, preset, signalId)` — 신호 전송의 유일한 경로

최초 설계는 클라이언트가 `signals`에 직접 쓰고 `onCreate` 트리거로 사후에 레이트리밋을 검증/거부하는 방식이었다. **이건 동작하지 않는다** — `onCreate`는 문서가 이미 Firestore에 커밋된 뒤에 실행되므로 "쓰기 거부"가 불가능하고, 그 사이 상대 클라이언트의 리스너가 그 문서를 볼 수 있다. 또한 거의 동시에 들어온 두 트리거 실행 사이에 경쟁 상태가 생길 수 있고, 클라이언트가 `sentAt`을 직접 써서 보내면 조작도 가능하다.

그래서 모든 검증과 쓰기를 **트랜잭션 하나**로 처리하는 콜러블 함수로 바꿨다:

1. 인증 확인, 호출자가 connection 멤버인지 확인, `status == active` 확인
2. `signalId`가 이미 처리됐으면(WorkManager 재시도 등) 새로 쓰지 않고 성공만 응답
3. **레이트리밋은 (connectionId, 보낸 사람) 단위**로 `lastSignalByUid[내 uid].sentAt`을 확인 — N초 이내면 `{ rateLimited: true }` 반환. **connection 전체 단위로 걸면 상대의 답장까지 막혀서 핵심 루프("받으면 답하고 싶다")가 깨진다.**
4. 통과 시 `signals` 문서 생성(서버 타임스탬프로 `sentAt` 기록 — 클라이언트 값은 신뢰하지 않음), `lastSignalByUid[내 uid]` 갱신
5. 오늘(Asia/Seoul) 양쪽 모두 보냈고 `streakUpdatedDate`가 오늘이 아니면 streak +1, `streakUpdatedDate` 갱신
6. 트랜잭션 성공 후 push 발송. `collapse-key`/`apns-collapse-id`를 connectionId로 지정해 알림 센터에 최신 것만 남도록 한다.

클라이언트 쿨다운 UI는 이 함수의 N초 값과 **동일하게** 맞추고, 거부 응답을 받으면 클라이언트에 "잠시 후 다시 보내주세요"를 표시한다 — 클라이언트 쿨다운과 서버 윈도우가 따로 노는 걸 방지.

### `redeemPairingCode(code)`

- `codeAttempts/{uid}`로 실패 횟수 확인, 초과 시 잠금
- 코드 존재/미만료(`expiresAt` 직접 비교) 확인
- `type == couple`이고 사용자당 1개 제한 정책이면 기존 활성 couple connection 여부 확인 (제한 여부는 아직 제품 결정 미확정 — 스펙 섹션 3 참고)
- 통과 시 `connections` 생성, 코드 문서 삭제

## 보안 규칙

- `signals`, `connections`의 서버 전용 필드(streak, lastSignalByUid 등)는 클라이언트 쓰기 금지 — Admin SDK(Cloud Functions)만 쓸 수 있음
- `users` 문서는 닉네임만 클라이언트 쓰기 허용

## 예외 처리

- 코드 만료/오타 → 안내 후 재생성 유도, `codeAttempts`로 무차별 대입 방지
- 연타 → `sendSignal` 안에서 (connection, 발신자) 단위로 거부
- 상대 FCM 토큰 무효(앱 삭제 등) → 해당 토큰 문서만 삭제, 신호 자체는 저장되어 다음 접속 시 공통 폴백 경로(`docs/widget-development.md`)로 반영됨
- 연결 해제 → `status`를 `disconnected`로 변경(문서를 바로 지우지 않음 — "연결 끊김"과 "네트워크 오류" 구분 가능) + 상대에게 알림

## Firestore 읽기 할당량 — 쓰기보다 먼저 찰 수 있다

위젯의 공통 폴백 경로(`docs/widget-development.md`)가 매 갱신마다 서버를 조회한다고 가정하면, 위젯 3개 × 하루 약 50회 갱신 = 사용자 1명당 하루 150회 읽기. 일일 읽기 한도(5만 건) 기준 활성 사용자 300명대에서 한도에 닿는다.

대응:
- 개인 D-day는 로컬 계산만 하고 서버 조회 자체가 없음
- 커플/친구 위젯의 서버 조회는 "푸시 수신 직후" 또는 "마지막 조회 후 30분 경과" 조건에서만 수행
- 신호 1건 전송은 대략 읽기 2회·쓰기 2회로 추정(정확한 수치는 구현 시 측정)

## 테스트

Firebase Emulator Suite로 `sendSignal`의 레이트리밋/멱등성/streak 갱신, `redeemPairingCode`의 만료·무차별 대입 방지 로직을 테스트한다.
