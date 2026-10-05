# Firebase 백엔드 (pairing + signals) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 커플·친구 연결(6자리 코드 pairing)과 신호 주고받기를 처리하는 Firebase 백엔드(Firestore + Cloud Functions + FCM 발송)를 클라이언트 없이 독립적으로 완성하고 테스트한다.

**Architecture:** 모든 쓰기는 Cloud Functions callable 함수를 거친다 — 클라이언트는 `signals`/`connections`에 직접 쓸 수 없다(Firestore 보안 규칙으로 차단). 각 콜러블은 "순수 로직 함수 + onCall 래퍼"로 나눠서, 로직 함수를 Firebase Emulator Suite로 직접 테스트한다. 백엔드 코드는 Node.js/TypeScript로 작성한다 — Cloud Functions가 Kotlin/JVM 런타임을 지원하지 않기 때문에 모바일 앱(Kotlin Multiplatform)과는 별도 코드베이스다.

**Tech Stack:** Firebase (Firestore, Cloud Functions 2nd gen, Cloud Messaging, Anonymous Auth), TypeScript, Firebase Emulator Suite, `@firebase/rules-unit-testing`

**Spec:** `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` (섹션 3 데이터 모델, 섹션 4.2 신호 전송 플로우), `docs/firebase-pairing.md`

## Global Constraints

- Cloud Functions는 Blaze 플랜 필요(Spark에서 실행 불가) — 예산 알림과 Artifact Registry 이미지 정리 정책을 설정한다 (스펙 섹션 6)
- 인증은 익명 인증만 사용한다
- `signals`, `connections`의 서버 전용 필드는 클라이언트 쓰기 금지 — Admin SDK(Cloud Functions)만 쓸 수 있다
- 레이트리밋은 반드시 (connectionId, 보낸 사람) 단위로 건다 — connection 전체 단위로 걸면 상대의 답장이 막힌다
- `sentAt`, `createdAt` 등 시각 필드는 항상 서버 타임스탬프로 기록한다 — 클라이언트 입력값을 신뢰하지 않는다
- pairingCode의 만료 판정은 Firestore TTL 자동 삭제(최대 24시간 지연 가능)에 의존하지 않고 함수가 `expiresAt`을 직접 비교한다
- 레이트리밋 윈도우는 정확히 5초(`RATE_LIMIT_SECONDS = 5`)로 고정 — 이 값은 Plan 4의 클라이언트 쿨다운 UI와 반드시 동일해야 한다
- 코드 무차별 대입 잠금: 15분 윈도우(`LOCKOUT_WINDOW_MINUTES = 15`) 안에 5회(`MAX_CODE_ATTEMPTS = 5`) 실패하면 잠금

## Review Focus

- `createPairingCode`/`redeemPairingCode`를 거의 동시에 두 번 호출해도 같은 코드가 두 개의 커넥션으로 교환되지 않아야 한다 — Task 4에서 트랜잭션으로 테스트
- 만료되거나 존재하지 않는 코드로 반복 실패하면 `codeAttempts.failCount`가 쌓여 결국 잠긴다 — Task 4에서 테스트
- 같은 `signalId`로 `sendSignal`을 두 번 호출해도(WorkManager 재시도 등) `signals` 문서가 중복 생성되거나 streak가 두 번 증가하지 않는다 — Task 5에서 테스트
- A가 신호를 보낸 직후 B가 바로 답장을 보내도 거부되지 않는다(레이트리밋이 발신자별인지) — Task 5에서 회귀 테스트로 명시
- FCM 발송이 실패(무효 토큰)해도 `sendSignal` 트랜잭션 자체는 성공으로 끝나고, 실패한 토큰만 삭제된다 — Task 5에서 테스트

---

### Task 1: Firebase 프로젝트 생성 + 리포지토리 스캐폴딩

**수동 작업:**
1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트 생성, **Blaze 플랜으로 업그레이드**
2. Firestore 생성 (프로덕션 모드, 리전 예: `asia-northeast3`)
3. Authentication에서 **익명(Anonymous)** 제공자 활성화
4. Cloud Messaging 활성화 (기본으로 켜져 있음, API 비활성화 여부만 확인)
5. 예산 알림 설정 (Billing > Budgets & alerts), Artifact Registry에 2nd gen Functions 컨테이너 이미지 정리 정책 설정 (Cleanup policy, 예: 7일 이상 된 미사용 이미지 삭제)
6. Firestore 콘솔 또는 `gcloud firestore fields ttls update`로 `signals.expireAt`, `pairingCodes.expiresAt` 필드에 TTL 정책 등록
7. 로컬에 `npm install -g firebase-tools`(미설치 시), `firebase login`, 리포지토리 루트에서 `firebase init` 실행 — Firestore와 Functions(TypeScript, ESLint는 선택) 선택, 기존 프로젝트 연결

**Files (firebase init이 만들거나, 직접 만드는 것):**
- Create/Modify: `firebase.json`, `.firebaserc`
- Create: `firestore.rules` (일단 전부 거부하는 스켈레톤 — Task 2에서 채움)
- Create: `functions/package.json`, `functions/tsconfig.json`, `functions/src/index.ts`
- Create: `functions/src/lib/types.ts` — Firestore 문서 타입 정의

**Interfaces:**
- Produces:
```ts
// functions/src/lib/types.ts
export interface UserDoc { nickname: string }
export interface TokenDoc { platform: 'android' | 'ios'; updatedAt: Timestamp }
export interface ConnectionDoc {
  memberUids: [string, string]
  type: 'couple' | 'friend'
  status: 'active' | 'disconnected'
  createdAt: Timestamp
  anniversaryDate: string // "YYYY-MM-DD"
  anniversaryUpdatedBy: string
  streak: number
  streakUpdatedDate: string // "YYYY-MM-DD", Asia/Seoul 기준
  lastSignalByUid: Record<string, { presetType: string; sentAt: Timestamp }>
}
export interface SignalDoc { senderUid: string; presetType: string; sentAt: Timestamp; expireAt: Timestamp }
export interface PairingCodeDoc { creatorUid: string; type: 'couple' | 'friend'; createdAt: Timestamp; expiresAt: Timestamp }
export interface CodeAttemptsDoc { failCount: number; windowStart: Timestamp }
```

- [ ] **Step 1: 위 수동 작업 1~7 수행**
- [ ] **Step 2: `firebase init`으로 스캐폴딩, `functions/src/lib/types.ts` 작성**
- [ ] **Step 3: 에뮬레이터가 뜨는지 확인**

Run: `firebase emulators:start --only firestore,functions,auth`
Expected: 세 에뮬레이터 모두 기동, 포트 출동 에러 없음

- [ ] **Step 4: Commit**

```bash
git add firebase.json .firebaserc firestore.rules functions/package.json functions/tsconfig.json functions/src/index.ts functions/src/lib/types.ts
git commit -m "chore: scaffold Firebase project"
```

---

### Task 2: Firestore 보안 규칙

**Files:**
- Modify: `firestore.rules`
- Create: `functions/test/rules/firestore.rules.test.ts` (실제로는 리포 루트 또는 별도 `test/` 디렉토리 — `firebase.json`의 테스트 설정에 맞춰 위치, `@firebase/rules-unit-testing` 사용)

**Interfaces:**
- Consumes: `functions/src/lib/types.ts` (Task 1, 필드 구조 참고용)
- Produces: 배포된 `firestore.rules` — 이후 모든 Task가 "클라이언트는 어디까지 읽고 쓸 수 있는가"의 기준으로 삼는다

**규칙 (고정):**
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow get: if request.auth != null && request.auth.uid == uid;
      allow update: if request.auth != null && request.auth.uid == uid
                    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['nickname'])
                    && request.resource.data.nickname is string;
      allow create: if request.auth != null && request.auth.uid == uid;
      allow delete: if false;
      match /tokens/{tokenId} { allow read, write: if false; }
    }
    match /connections/{connectionId} {
      allow get: if request.auth != null && request.auth.uid in resource.data.memberUids;
      allow list, create, update, delete: if false;
      match /signals/{signalId} {
        allow get: if request.auth != null
                    && request.auth.uid in get(/databases/$(database)/documents/connections/$(connectionId)).data.memberUids;
        allow list, create, update, delete: if false;
      }
    }
    match /pairingCodes/{code} { allow read, write: if false; }
    match /codeAttempts/{uid} { allow read, write: if false; }
  }
}
```

- [ ] **Step 1: Write the failing rules tests**

```ts
describe('firestore.rules', () => {
  it('lets a user update only their own nickname', async () => {
    const db = authedDb(userAUid)
    await assertSucceeds(db.doc(`users/${userAUid}`).set({ nickname: 'A' }))
    await assertFails(db.doc(`users/${userAUid}`).set({ nickname: 'A', ownedThemes: ['x'] }))
  })

  it('denies writing another user\'s doc', async () => {
    const db = authedDb(userAUid)
    await assertFails(db.doc(`users/${userBUid}`).set({ nickname: 'hacked' }))
  })

  it('lets a connection member read the connection but not write it', async () => {
    await seedConnection(connectionId, [userAUid, userBUid])
    const db = authedDb(userAUid)
    await assertSucceeds(db.doc(`connections/${connectionId}`).get())
    await assertFails(db.doc(`connections/${connectionId}`).update({ streak: 999 }))
  })

  it('denies a non-member from reading the connection', async () => {
    await seedConnection(connectionId, [userAUid, userBUid])
    const db = authedDb(strangerUid)
    await assertFails(db.doc(`connections/${connectionId}`).get())
  })

  it('denies any client write to signals, even by a connection member', async () => {
    await seedConnection(connectionId, [userAUid, userBUid])
    const db = authedDb(userAUid)
    await assertFails(db.collection(`connections/${connectionId}/signals`).add({ senderUid: userAUid, presetType: 'heart' }))
  })
})
```

- [ ] **Step 2: Run tests to verify they fail (규칙이 전부 거부 스켈레톤이므로 "member can read" 계열이 실패)**

Run: `firebase emulators:exec --only firestore "npm --prefix functions test -- rules"`

- [ ] **Step 3: 위 고정 규칙으로 `firestore.rules` 작성**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add firestore.rules functions/test/rules/firestore.rules.test.ts
git commit -m "feat: add Firestore security rules"
```

---

### Task 3: FCM 토큰 등록/삭제

**Files:**
- Create: `functions/src/registerFcmToken.ts`
- Create: `functions/src/lib/tokens.ts`
- Test: `functions/test/tokens.test.ts`

**Interfaces:**
- Consumes: `TokenDoc` (Task 1)
- Produces:
```ts
// functions/src/lib/tokens.ts
export async function upsertToken(firestore: Firestore, uid: string, token: string, platform: 'android' | 'ios'): Promise<void>
export async function removeToken(firestore: Firestore, uid: string, token: string): Promise<void>
export async function listTokens(firestore: Firestore, uid: string): Promise<string[]>

// functions/src/registerFcmToken.ts
export const registerFcmToken = onCall<{ token: string; platform: 'android' | 'ios' }>(...)
```

`upsertToken`은 문서 id를 토큰 문자열 그대로 사용한다(`users/{uid}/tokens/{token}`) — 같은 토큰으로 다시 호출하면 `updatedAt`만 갱신되고 중복 문서가 생기지 않는다.

- [ ] **Step 1: Write the failing tests**

```ts
describe('tokens', () => {
  it('upsertToken creates a token doc keyed by the token string', async () => {
    await upsertToken(firestore, uidA, 'tok-1', 'android')
    const doc = await firestore.doc(`users/${uidA}/tokens/tok-1`).get()
    expect(doc.get('platform')).toBe('android')
  })

  it('upsertToken called twice with the same token does not create a duplicate', async () => {
    await upsertToken(firestore, uidA, 'tok-1', 'android')
    await upsertToken(firestore, uidA, 'tok-1', 'android')
    const docs = await firestore.collection(`users/${uidA}/tokens`).get()
    expect(docs.size).toBe(1)
  })

  it('removeToken deletes only the given token', async () => {
    await upsertToken(firestore, uidA, 'tok-1', 'android')
    await upsertToken(firestore, uidA, 'tok-2', 'android')
    await removeToken(firestore, uidA, 'tok-1')
    expect(await listTokens(firestore, uidA)).toEqual(['tok-2'])
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `firebase emulators:exec --only firestore,functions "npm --prefix functions test -- tokens"`

- [ ] **Step 3: `tokens.ts`, `registerFcmToken.ts` 구현**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add functions/src/registerFcmToken.ts functions/src/lib/tokens.ts functions/test/tokens.test.ts
git commit -m "feat: add FCM token registration"
```

---

### Task 4: 코드 발급/교환 (`createPairingCode`, `redeemPairingCode`)

**Files:**
- Create: `functions/src/createPairingCode.ts`
- Create: `functions/src/redeemPairingCode.ts`
- Create: `functions/src/lib/codeAttempts.ts`
- Test: `functions/test/pairingCode.test.ts`

**Interfaces:**
- Consumes: `PairingCodeDoc`, `ConnectionDoc`, `CodeAttemptsDoc` (Task 1)
- Produces:
```ts
// functions/src/lib/codeAttempts.ts
export async function checkAndRecordFailure(firestore: Firestore, uid: string, now: Timestamp): Promise<{ locked: boolean }>
export async function resetAttempts(firestore: Firestore, uid: string): Promise<void>

// functions/src/createPairingCode.ts
export async function createPairingCodeLogic(uid: string, input: { type: 'couple' | 'friend' }, firestore: Firestore, now: Timestamp): Promise<{ code: string }>
export const createPairingCode = onCall<{ type: 'couple' | 'friend' }>(...)

// functions/src/redeemPairingCode.ts
export async function redeemPairingCodeLogic(uid: string, input: { code: string }, firestore: Firestore, now: Timestamp): Promise<{ connectionId: string }>
export const redeemPairingCode = onCall<{ code: string }>(...)
```

**고정 값/규칙:**
- 코드는 6자리 숫자 문자열(`000000`~`999999`), 트랜잭션으로 충돌 시 재생성(최대 5회 재시도)
- `expiresAt = now + 10분`
- `checkAndRecordFailure`: `windowStart`가 `LOCKOUT_WINDOW_MINUTES` 이전이면 `failCount`를 0으로 리셋하고 1 기록; 아니면 `failCount += 1`. `failCount >= MAX_CODE_ATTEMPTS`면 `{ locked: true }`
- `redeemPairingCodeLogic`: 시작 시 `checkAndRecordFailure`로 사전 잠금 여부를 확인하지 않고, **실패했을 때만** 기록한다(성공 시도는 카운트에 영향 없음) — 잠금 판정은 매 실패 시 갱신된 `failCount`로 즉시 반영
- `type === 'couple'`이면 교환 전 `connections` 쿼리로 호출자가 이미 활성 couple connection을 갖고 있는지 확인, 있으면 `already-exists` 에러

- [ ] **Step 1: Write the failing tests**

```ts
describe('createPairingCodeLogic', () => {
  it('creates a 6-digit code document that expires in 10 minutes', async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: 'friend' }, firestore, now)
    expect(code).toMatch(/^\d{6}$/)
    const doc = await firestore.doc(`pairingCodes/${code}`).get()
    expect(doc.get('creatorUid')).toBe(uidA)
  })
})

describe('redeemPairingCodeLogic', () => {
  it('creates a connection with both uids and deletes the code', async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: 'friend' }, firestore, now)
    const { connectionId } = await redeemPairingCodeLogic(uidB, { code }, firestore, now)
    const conn = await firestore.doc(`connections/${connectionId}`).get()
    expect(conn.get('memberUids')).toEqual(expect.arrayContaining([uidA, uidB]))
    expect((await firestore.doc(`pairingCodes/${code}`).get()).exists).toBe(false)
  })

  it('rejects an expired code and records a failed attempt', async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: 'friend' }, firestore, now)
    const later = Timestamp.fromMillis(now.toMillis() + 11 * 60 * 1000)
    await expect(redeemPairingCodeLogic(uidB, { code }, firestore, later)).rejects.toThrow()
    const attempts = await firestore.doc(`codeAttempts/${uidB}`).get()
    expect(attempts.get('failCount')).toBe(1)
  })

  it('locks out after 5 failed attempts within the window', async () => {
    for (let i = 0; i < 5; i++) {
      await expect(redeemPairingCodeLogic(uidB, { code: 'wrong0' + i }, firestore, now)).rejects.toThrow()
    }
    await expect(redeemPairingCodeLogic(uidB, { code: 'wrong06' }, firestore, now)).rejects.toThrow(/locked/i)
  })

  it('rejects a couple code when the redeemer already has an active couple connection', async () => {
    await seedActiveCoupleConnection(firestore, uidB)
    const { code } = await createPairingCodeLogic(uidA, { type: 'couple' }, firestore, now)
    await expect(redeemPairingCodeLogic(uidB, { code }, firestore, now)).rejects.toThrow(/already-exists/)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `firebase emulators:exec --only firestore,functions "npm --prefix functions test -- pairingCode"`

- [ ] **Step 3: `codeAttempts.ts`, `createPairingCode.ts`, `redeemPairingCode.ts` 구현**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add functions/src/createPairingCode.ts functions/src/redeemPairingCode.ts functions/src/lib/codeAttempts.ts functions/test/pairingCode.test.ts
git commit -m "feat: add pairing code issuance and redemption"
```

---

### Task 5: 신호 전송 (`sendSignal`)

**Files:**
- Create: `functions/src/sendSignal.ts`
- Create: `functions/src/lib/push.ts`
- Test: `functions/test/sendSignal.test.ts`
- Test: `functions/test/push.test.ts`

**Interfaces:**
- Consumes: `ConnectionDoc`, `SignalDoc` (Task 1), `listTokens`, `removeToken` (Task 3)
- Produces:
```ts
// functions/src/lib/push.ts
export async function sendPushToPartner(
  messaging: Messaging, firestore: Firestore, partnerUid: string, connectionId: string, payload: { title: string; body: string },
): Promise<void>

// functions/src/sendSignal.ts
export async function sendSignalLogic(
  uid: string, input: { connectionId: string; preset: string; signalId: string }, firestore: Firestore, now: Timestamp,
): Promise<{ rateLimited: boolean }>
export const sendSignal = onCall<{ connectionId: string; preset: string; signalId: string }>(...)
```

**고정 로직 (트랜잭션 하나, `sendSignalLogic`):**
1. `connections/{connectionId}` 읽기 — `status !== 'active'` 또는 `uid`가 `memberUids`에 없으면 `failed-precondition` 에러
2. `connections/{connectionId}/signals/{signalId}` 존재 확인 — 이미 있으면 트랜잭션을 더 진행하지 않고 `{ rateLimited: false }` 반환(멱등성, Review Focus 3번)
3. `lastSignalByUid[uid]`가 있고 `now - lastSignalByUid[uid].sentAt < RATE_LIMIT_SECONDS(5)`초면 `{ rateLimited: true }` 반환하고 종료 — **이 검사는 `uid`(호출자) 전용 필드만 보고, 상대방의 `lastSignalByUid`는 보지 않는다**(Review Focus 4번 회귀 테스트)
4. `signals/{signalId}` 생성(`senderUid: uid, presetType: preset, sentAt: now, expireAt: now+30일`), `lastSignalByUid[uid] = { presetType: preset, sentAt: now }` 갱신
5. `today = toSeoulDateString(now)`. 상대 uid를 `partnerUid`라 하면, `lastSignalByUid[uid]`와 `lastSignalByUid[partnerUid]` 둘 다 `today`에 보냈고 `streakUpdatedDate !== today`면 `streak += 1`, `streakUpdatedDate = today`
6. 트랜잭션 커밋 성공 후(트랜잭션 밖에서) `rateLimited`가 아니었다면 `sendPushToPartner` 호출 — `messaging.send`가 토큰별로 실패하면 `removeToken`으로 해당 토큰만 삭제하고 나머지는 계속 시도(Review Focus 5번)

`sendPushToPartner`는 FCM 메시지에 `android.collapseKey`와 `apns.headers['apns-collapse-id']`를 모두 `connectionId`로 설정한다.

- [ ] **Step 1: Write the failing `sendSignalLogic` tests**

```ts
describe('sendSignalLogic', () => {
  it('creates a signal and updates lastSignalByUid for the sender', async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB])
    const result = await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-1' }, firestore, now)
    expect(result.rateLimited).toBe(false)
    const conn = await firestore.doc(`connections/${connectionId}`).get()
    expect(conn.get('lastSignalByUid')[uidA].presetType).toBe('heart')
  })

  it('is idempotent for a repeated signalId', async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB])
    await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-1' }, firestore, now)
    const result = await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-1' }, firestore, now)
    expect(result.rateLimited).toBe(false)
    const signals = await firestore.collection(`connections/${connectionId}/signals`).get()
    expect(signals.size).toBe(1)
  })

  it('rate-limits a second signal from the same sender within 5 seconds', async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB])
    await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-1' }, firestore, now)
    const soon = Timestamp.fromMillis(now.toMillis() + 2000)
    const result = await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-2' }, firestore, soon)
    expect(result.rateLimited).toBe(true)
  })

  it('does not rate-limit the partner replying immediately after', async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB])
    await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-1' }, firestore, now)
    const soon = Timestamp.fromMillis(now.toMillis() + 1000)
    const result = await sendSignalLogic(uidB, { connectionId, preset: 'heart', signalId: 'sig-2' }, firestore, soon)
    expect(result.rateLimited).toBe(false)
  })

  it('increments streak once when both partners have signaled the same day, and not again on a third signal that day', async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB])
    await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-1' }, firestore, now)
    const laterSameDay = Timestamp.fromMillis(now.toMillis() + 6000)
    await sendSignalLogic(uidB, { connectionId, preset: 'heart', signalId: 'sig-2' }, firestore, laterSameDay)
    let conn = await firestore.doc(`connections/${connectionId}`).get()
    expect(conn.get('streak')).toBe(1)
    const evenLater = Timestamp.fromMillis(laterSameDay.toMillis() + 6000)
    await sendSignalLogic(uidA, { connectionId, preset: 'heart', signalId: 'sig-3' }, firestore, evenLater)
    conn = await firestore.doc(`connections/${connectionId}`).get()
    expect(conn.get('streak')).toBe(1)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `firebase emulators:exec --only firestore,functions "npm --prefix functions test -- sendSignal"`

- [ ] **Step 3: `sendSignal.ts`(트랜잭션 로직) 구현. `toSeoulDateString(timestamp: Timestamp): string` 헬퍼도 같은 파일에 작은 함수로 둔다.**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Write the failing `push` tests (Messaging은 fake/mock 주입)**

```ts
describe('sendPushToPartner', () => {
  it('sets the same collapse key on android and apns payloads', async () => {
    const fakeMessaging = { send: jest.fn().mockResolvedValue('ok') } as unknown as Messaging
    await upsertToken(firestore, partnerUid, 'tok-1', 'android')
    await sendPushToPartner(fakeMessaging, firestore, partnerUid, connectionId, { title: 't', body: 'b' })
    const sentMessage = fakeMessaging.send.mock.calls[0][0]
    expect(sentMessage.android.collapseKey).toBe(connectionId)
    expect(sentMessage.apns.headers['apns-collapse-id']).toBe(connectionId)
  })

  it('removes a token that fails with a not-registered error and still tries the remaining tokens', async () => {
    const fakeMessaging = {
      send: jest.fn()
        .mockRejectedValueOnce({ code: 'messaging/registration-token-not-registered' })
        .mockResolvedValueOnce('ok'),
    } as unknown as Messaging
    await upsertToken(firestore, partnerUid, 'bad-token', 'android')
    await upsertToken(firestore, partnerUid, 'good-token', 'android')
    await sendPushToPartner(fakeMessaging, firestore, partnerUid, connectionId, { title: 't', body: 'b' })
    expect(await listTokens(firestore, partnerUid)).toEqual(['good-token'])
  })
})
```

- [ ] **Step 6: Run tests to verify they fail, implement `push.ts`, verify they pass**

- [ ] **Step 7: Commit**

```bash
git add functions/src/sendSignal.ts functions/src/lib/push.ts functions/test/sendSignal.test.ts functions/test/push.test.ts
git commit -m "feat: add signal sending with rate limiting and push delivery"
```
