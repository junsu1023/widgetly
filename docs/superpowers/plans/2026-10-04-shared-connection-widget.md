# 커플·친구 공유 위젯 (클라이언트) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 6자리 코드로 커플/친구를 연결하고, 위젯에서 바로 신호(프리셋 이모지)를 주고받으며, 상대의 최신 신호·연속일을 위젯에서 확인할 수 있게 한다.

**Architecture:** `shared`(commonMain)가 Firebase 백엔드(Firebase 백엔드 플랜에서 만든 콜러블 함수들)를 호출하는 인터페이스와 Connection 도메인 모델을 제공한다. 메인 앱(페어링 화면)과 Android 위젯은 같은 프로세스이므로 GitLive Firebase KMP SDK를 그대로 쓴다. iOS 위젯 익스텐션은 메모리 제한 때문에 전체 SDK 대신 경량 HTTPS 호출만 쓴다(이미 `docs/widget-development.md`에 명시된 제약). 위젯 인스턴스당 대상을 고르는 구성 패턴은 "개인 디데이 위젯 코어" 플랜에서 만든 것을 재사용한다.

**Tech Stack:** Kotlin Multiplatform, GitLive Firebase KMP SDK(auth/firestore/functions), Android Glance + WorkManager + FCM, iOS WidgetKit + AppIntents + Notification Service Extension

**Spec:** `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` (섹션 4.2, 4.3), `docs/widget-development.md`, `docs/firebase-pairing.md`

**선행 플랜:**
- `docs/superpowers/plans/2026-10-04-personal-dday-widget.md` — 위젯 구성 패턴, `WidgetlyApplication`
- `docs/superpowers/plans/2026-10-04-theme-system.md` — 테마 선택 패턴(이 플랜의 위젯도 같은 `theme_id` 키를 재사용)
- `docs/superpowers/plans/2026-10-04-firebase-backend.md` — `registerFcmToken`, `createPairingCode`, `redeemPairingCode`, `sendSignal` 콜러블 함수

## Global Constraints

- 레이트리밋 윈도우는 서버와 동일하게 **5초**로 클라이언트 쿨다운 UI를 맞춘다 (Firebase 백엔드 플랜 Global Constraints)
- iOS 위젯/인텐트 익스텐션에서는 Firebase SDK 전체를 쓰지 않고 Auth 토큰 + HTTPS 호출만 쓴다 (`docs/widget-development.md`)
- 신호는 반드시 `sendSignal` 콜러블을 통해서만 보낸다 — Firestore에 클라이언트가 직접 쓰지 않는다
- App Group id는 `group.com.example.widgetly` (개인 디데이 위젯 플랜과 동일)

## Review Focus

- `sendSignal`이 `rateLimited: true`를 반환하면 클라이언트는 성공으로 오인하지 않고 "잠시 후 다시 보내주세요"를 보여줘야 한다 — Task 4, 5
- 위젯이 가리키는 connection의 `status`가 `disconnected`로 바뀌면 크래시 없이 "연결 끊김"을 보여줘야 한다(개인 디데이 위젯의 "삭제됨" 처리와 동일한 패턴) — Task 4, 5
- Android의 FCM 즉시 반영도 백그라운드 제약상 보장된 즉시성은 아니다 — UI 문구가 이를 과장하지 않는다 — Task 4
- 공통 폴백의 "마지막 서버 조회 후 30분" 판정 기준 시각은 앱 재시작/위젯 프로세스 재생성에도 유지되는 저장소(App Group/DataStore)에 저장해야 한다 — Task 6
- 앱 재설치로 uid가 바뀌면 이전 기기의 FCM 토큰은 새 uid 아래 다시 등록돼야 한다(고아 토큰 방지) — Task 2에서 앱 시작 시마다 토큰을 재동기화하는지 확인

---

### Task 1: shared — Connection 모델 + WidgetlyFunctions 인터페이스

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/connection/Connection.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/connection/WidgetlyFunctions.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/connection/FirebaseWidgetlyFunctions.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/connection/ConnectionRepository.kt`
- Modify: `shared/build.gradle.kts` — `dev.gitlive:firebase-auth`, `dev.gitlive:firebase-firestore`, `dev.gitlive:firebase-functions` commonMain 의존성 추가
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/connection/ConnectionMappingTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class ConnectionType { COUPLE, FRIEND }
data class SignalSummary(val presetType: String, val sentAt: Instant)
data class Connection(
    val id: String,
    val memberUids: List<String>,
    val type: ConnectionType,
    val status: String, // "active" | "disconnected"
    val anniversaryDate: LocalDate,
    val streak: Int,
    val lastSignalByUid: Map<String, SignalSummary>,
)
fun connectionFromDocument(id: String, data: Map<String, Any?>): Connection

data class SendSignalResult(val rateLimited: Boolean)
interface WidgetlyFunctions {
    suspend fun registerFcmToken(token: String, platform: String)
    suspend fun createPairingCode(type: ConnectionType): String
    suspend fun redeemPairingCode(code: String): String // connectionId
    suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult
}
class FirebaseWidgetlyFunctions(private val functions: FirebaseFunctions) : WidgetlyFunctions

interface ConnectionRepository { fun observeConnection(connectionId: String): Flow<Connection?> }
class FirestoreConnectionRepository(private val firestore: FirebaseFirestore) : ConnectionRepository
```

`connectionFromDocument`는 Firebase 백엔드 플랜의 `ConnectionDoc` 필드 이름과 정확히 일치하는 키로 맵을 읽는다. `anniversaryUpdatedBy`처럼 이 플랜에서 당장 쓰지 않는 필드는 무시해도 된다.

- [ ] **Step 1: Write the failing test**

```kotlin
class ConnectionMappingTest {
    @Test
    fun `maps a Firestore-shaped document into a Connection`() {
        val data = mapOf(
            "memberUids" to listOf("uidA", "uidB"),
            "type" to "couple",
            "status" to "active",
            "anniversaryDate" to "2025-01-01",
            "streak" to 7,
            "lastSignalByUid" to mapOf(
                "uidA" to mapOf("presetType" to "heart", "sentAt" to Instant.parse("2026-10-04T10:00:00Z")),
            ),
        )
        val connection = connectionFromDocument("conn-1", data)
        assertEquals(ConnectionType.COUPLE, connection.type)
        assertEquals(7, connection.streak)
        assertEquals("heart", connection.lastSignalByUid["uidA"]?.presetType)
    }

    @Test
    fun `missing lastSignalByUid maps to an empty map instead of throwing`() {
        val data = mapOf(
            "memberUids" to listOf("uidA", "uidB"), "type" to "friend", "status" to "active",
            "anniversaryDate" to "2025-01-01", "streak" to 0,
        )
        assertEquals(emptyMap(), connectionFromDocument("conn-1", data).lastSignalByUid)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.ConnectionMappingTest"`

- [ ] **Step 3: 위 파일들 구현. `FirebaseWidgetlyFunctions`는 각 메서드에서 `functions.httpsCallable("<함수명>").invoke(...)`를 호출하고 응답 맵을 각 반환형으로 매핑한다.**

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git add shared/build.gradle.kts shared/src/commonMain/kotlin/com/example/widgetly/connection shared/src/commonTest/kotlin/com/example/widgetly/connection/ConnectionMappingTest.kt
git commit -m "feat: add Connection model and Firebase Functions client"
```

---

### Task 2: Firebase 클라이언트 SDK 부트스트랩 (익명 인증 + FCM 토큰 등록)

**수동 작업:**
1. Firebase 콘솔에서 Android 앱(`com.example.widgetly`) 등록 → `google-services.json`을 `androidApp/google-services.json`에 배치
2. Firebase 콘솔에서 iOS 앱(bundle id) 등록 → `GoogleService-Info.plist`를 `iosApp/iosApp/GoogleService-Info.plist`에 배치, Xcode에서 `iosApp`과 `DdayWidgetExtension` 양쪽 타겟의 Copy Bundle Resources에 포함
3. Xcode에서 Swift Package Manager로 `firebase-ios-sdk` 추가, `iosApp` 타겟에 FirebaseCore/FirebaseAuth/FirebaseMessaging 링크

**Files:**
- Modify: 루트 `build.gradle.kts` — `com.google.gms.google-services` 플러그인 classpath
- Modify: `androidApp/build.gradle.kts` — google-services 플러그인 적용, Firebase BoM + `firebase-auth-ktx`, `firebase-messaging-ktx`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/auth/AnonymousAuthenticator.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/push/FcmTokenSync.kt`
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/WidgetlyApplication.kt` — 시작 시 `ensureSignedIn()` 호출 + `FirebaseMessaging.getInstance().token`을 관찰해 `syncFcmToken` 호출
- Modify: `iosApp/iosApp/iOSApp.swift` — 동일하게 시작 시 로그인 보장 + 토큰 동기화
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/push/FcmTokenSyncTest.kt`

**Interfaces:**
- Consumes: `WidgetlyFunctions` (Task 1)
- Produces: `suspend fun ensureSignedIn(auth: FirebaseAuth): String`(uid 반환, 이미 로그인 상태면 그대로), `suspend fun syncFcmToken(functions: WidgetlyFunctions, token: String, platform: String)`

- [ ] **Step 1: Write the failing test**

```kotlin
class FakeWidgetlyFunctions : WidgetlyFunctions {
    val registeredTokens = mutableListOf<Pair<String, String>>()
    override suspend fun registerFcmToken(token: String, platform: String) { registeredTokens += token to platform }
    override suspend fun createPairingCode(type: ConnectionType) = error("not used")
    override suspend fun redeemPairingCode(code: String) = error("not used")
    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String) = error("not used")
}

class FcmTokenSyncTest {
    @Test
    fun `forwards the token and platform to the backend`() = runTest {
        val fake = FakeWidgetlyFunctions()
        syncFcmToken(fake, "tok-1", "android")
        assertEquals(listOf("tok-1" to "android"), fake.registeredTokens)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.FcmTokenSyncTest"`

- [ ] **Step 3: 수동 작업 1~3 수행, `AnonymousAuthenticator.kt`/`FcmTokenSync.kt` 구현, `WidgetlyApplication`/`iOSApp.swift`에 부트스트랩 연결**

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: 앱을 실행해 Firebase 콘솔의 Authentication 탭에 익명 사용자가 생성되는지, Firestore의 `users/{uid}/tokens`에 토큰 문서가 생기는지 수동 확인**

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts androidApp/build.gradle.kts shared/src/commonMain/kotlin/com/example/widgetly/auth shared/src/commonMain/kotlin/com/example/widgetly/push shared/src/commonTest/kotlin/com/example/widgetly/push/FcmTokenSyncTest.kt androidApp/src/main/kotlin/com/example/widgetly/WidgetlyApplication.kt iosApp/iosApp/iOSApp.swift androidApp/google-services.json iosApp/iosApp/GoogleService-Info.plist
git commit -m "feat: bootstrap Firebase auth and FCM token sync"
```

---

### Task 3: 페어링 UI (코드 생성/입력 화면)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/connection/PairingViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/connection/PairingScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt` — 디데이 목록 화면과 페어링 화면을 전환하는 최소한의 화면 전환 컨테이너
- Modify: `androidApp/src/main/kotlin/com/example/widgetly/MainActivity.kt`, `shared/src/iosMain/kotlin/com/example/widgetly/MainViewController.kt` — `AppRoot` 호스팅으로 교체
- Test: `shared/src/commonTest/kotlin/com/example/widgetly/connection/PairingViewModelTest.kt`

**Interfaces:**
- Consumes: `WidgetlyFunctions` (Task 1)
- Produces:
```kotlin
sealed interface PairingUiState {
    object Idle : PairingUiState
    data class CodeCreated(val code: String) : PairingUiState
    data class Connected(val connectionId: String) : PairingUiState
    data class Error(val message: String) : PairingUiState
}
class PairingViewModel(private val functions: WidgetlyFunctions) : ViewModel() {
    val state: StateFlow<PairingUiState>
    fun createCode(type: ConnectionType)
    fun redeem(code: String)
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
class PairingViewModelTest {
    @Test
    fun `createCode exposes the returned code as CodeCreated`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextCode = "123456" }
        val viewModel = PairingViewModel(fake)
        viewModel.createCode(ConnectionType.FRIEND)
        assertEquals(PairingUiState.CodeCreated("123456"), viewModel.state.value)
    }

    @Test
    fun `redeem exposes the returned connectionId as Connected`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextConnectionId = "conn-1" }
        val viewModel = PairingViewModel(fake)
        viewModel.redeem("123456")
        assertEquals(PairingUiState.Connected("conn-1"), viewModel.state.value)
    }

    @Test
    fun `a failed redeem exposes an Error state instead of throwing`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { shouldThrow = true }
        val viewModel = PairingViewModel(fake)
        viewModel.redeem("000000")
        assertTrue(viewModel.state.value is PairingUiState.Error)
    }
}
```

(`FakeWidgetlyFunctions`에 `nextCode`/`nextConnectionId`/`shouldThrow` 필드를 Task 1의 fake에 추가한다.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "*.PairingViewModelTest"`

- [ ] **Step 3: `PairingViewModel`, `PairingScreen`, `AppRoot` 구현. 화면 전환은 네비게이션 라이브러리 없이 `AppRoot`의 로컬 state(`var currentScreen`)로 충분하다.**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/widgetly/connection/PairingViewModel.kt shared/src/commonMain/kotlin/com/example/widgetly/connection/PairingScreen.kt shared/src/commonMain/kotlin/com/example/widgetly/AppRoot.kt shared/src/commonTest/kotlin/com/example/widgetly/connection/PairingViewModelTest.kt androidApp/src/main/kotlin/com/example/widgetly/MainActivity.kt shared/src/iosMain/kotlin/com/example/widgetly/MainViewController.kt
git commit -m "feat: add pairing screen"
```

---

### Task 4: Android — 신호 전송 + 공유 위젯

**Files:**
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidget.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionGlanceWidgetReceiver.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/ConnectionWidgetConfigurationActivity.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/widget/SendSignalWorker.kt`
- Create: `androidApp/src/main/kotlin/com/example/widgetly/push/WidgetlyFirebaseMessagingService.kt`
- Create: `androidApp/src/main/res/xml/connection_widget_info.xml`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Test: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/SendSignalWorkerTest.kt`
- Test: `androidApp/src/androidTest/kotlin/com/example/widgetly/widget/ConnectionGlanceWidgetTest.kt`

**Interfaces:**
- Consumes: `ConnectionRepository`, `WidgetlyFunctions.sendSignal` (Task 1), `stringPreferencesKey("theme_id")`(테마 시스템 플랜 Task 2), `DdayWidgetConfigurationActivity.saveSelectedEntry`와 동일한 패턴(개인 디데이 위젯 플랜 Task 5) — `ConnectionWidgetConfigurationActivity`는 같은 패턴으로 `stringPreferencesKey("connection_id")`를 저장한다
- Produces: 없음(leaf task)

**동작:**
- `ConnectionGlanceWidget.provideGlance`: 상태에서 `connection_id` 읽기 → `ConnectionRepository.observeConnection`의 최신 캐시(아래 `WidgetlyFirebaseMessagingService`가 갱신)로 렌더링. `status == "disconnected"` 또는 connection을 못 찾으면 "연결 끊김" 표시(Review Focus 2번)
- 신호 버튼 탭 → `actionRunCallback` → `SendSignalWorker`를 WorkManager에 enqueue(`connectionId`, `preset`, `signalId = UUID.randomUUID()` 입력 데이터로)
- `SendSignalWorker.doWork()`: `functions.sendSignal(...)` 호출. `rateLimited == true`면 `Result.success()`로 끝내되 위젯/알림에 "잠시 후 다시 보내주세요"를 표시할 수 있도록 결과를 Glance 상태에 남긴다(Review Focus 1번). 네트워크 예외면 `Result.retry()`
- `WidgetlyFirebaseMessagingService.onMessageReceived`: data message를 받으면 즉시 해당 connection을 Firestore에서 한 번 읽어(또는 payload에 포함된 값으로) 로컬 캐시를 갱신하고 `GlanceAppWidgetManager(context).updateAll(ConnectionGlanceWidget::class)` 호출(Review Focus 3번 — "즉시"가 과장이 아니도록 주석에 백그라운드 제약 한계를 남긴다)

- [ ] **Step 1: Write the failing `SendSignalWorker` test**

```kotlin
class SendSignalWorkerTest {
    @Test
    fun `returns success without retry when the backend reports rateLimited`() {
        val fake = FakeWidgetlyFunctions().apply { nextSendSignalResult = SendSignalResult(rateLimited = true) }
        val worker = TestListenableWorkerBuilder<SendSignalWorker>(context)
            .setWorkerFactory(fakeFactory(fake))
            .build()
        val result = worker.startWork().get()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `retries on a network failure`() {
        val fake = FakeWidgetlyFunctions().apply { shouldThrowNetworkError = true }
        val worker = TestListenableWorkerBuilder<SendSignalWorker>(context)
            .setWorkerFactory(fakeFactory(fake))
            .build()
        val result = worker.startWork().get()
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :androidApp:connectedAndroidTest --tests "*.SendSignalWorkerTest"`

- [ ] **Step 3: 위 Files 구현**

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Write the failing `ConnectionGlanceWidget` rendering test (개인 디데이 위젯 플랜 Task 5의 glanceId 테스트 유틸 재사용), "연결 끊김" 케이스 포함**

- [ ] **Step 6: Run test to verify it fails, implement rendering, verify it passes**

- [ ] **Step 7: 에뮬레이터 두 대(또는 계정 두 개)로 수동 확인 — 신호를 보내면 상대 위젯에 반영되는지, 연타 시 거부되는지**

- [ ] **Step 8: Commit**

```bash
git add androidApp/src/main/kotlin/com/example/widgetly/widget androidApp/src/main/kotlin/com/example/widgetly/push androidApp/src/main/res/xml/connection_widget_info.xml androidApp/src/main/AndroidManifest.xml androidApp/src/androidTest/kotlin/com/example/widgetly/widget
git commit -m "feat: add Android connection widget and signal sending"
```

---

### Task 5: iOS — 공유 위젯 렌더링 + 신호 전송

**Files:**
- Create: `iosApp/DdayWidgetExtension/ConnectionWidget.swift`
- Create: `iosApp/DdayWidgetExtension/ConnectionWidgetConfigurationIntent.swift`
- Create: `iosApp/DdayWidgetExtension/SendSignalHttpClient.swift`
- Modify: `iosApp/DdayWidgetExtension/DdayWidgetBundle.swift`(또는 동급 `@main` 파일) — `ConnectionWidget`을 번들에 추가
- Test: `iosApp/DdayWidgetExtensionTests/SendSignalHttpClientTests.swift`

**Interfaces:**
- Consumes: `Connection`, `connectionFromDocument` (Task 1, Shared.framework 경유), App Group 저장 패턴(개인 디데이 위젯 플랜)
- Produces: `func sendSignal(connectionId: String, preset: String, signalId: String, authToken: String) async throws -> SendSignalResult` — Firebase SDK 없이 `URLSession`으로 Cloud Functions callable HTTPS 엔드포인트를 직접 호출(엔드포인트 URL과 요청 바디 형식은 Firebase callable 프로토콜 규격을 따른다)

**동작:** `ConnectionWidgetConfigurationIntent`로 connection 선택. `ConnectionWidget`의 뷰에 신호 버튼(App Intent)을 두고, `perform()`에서 `sendSignal`을 호출한다. `rateLimited == true`면 위젯에 짧게 "곧 다시 보내보세요" 상태를 반영한다(Review Focus 1번). connection을 로컬 스냅샷에서 못 찾거나 `status == "disconnected"`면 "연결 끊김" 표시(Review Focus 2번).

- [ ] **Step 1: Write the failing test**

```swift
final class SendSignalHttpClientTests: XCTestCase {
    func testRateLimitedResponseIsParsedWithoutThrowing() async throws {
        let client = SendSignalHttpClient(session: fakeSession(returning: #"{"result":{"rateLimited":true}}"#))
        let result = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s1", authToken: "tok")
        XCTAssertTrue(result.rateLimited)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: Xcode `DdayWidgetExtensionTests` 타겟 실행 (⌘U)

- [ ] **Step 3: `SendSignalHttpClient`, `ConnectionWidget`, `ConnectionWidgetConfigurationIntent` 구현**

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: 시뮬레이터에서 수동 확인**

- [ ] **Step 6: Commit**

```bash
git add iosApp/DdayWidgetExtension/ConnectionWidget.swift iosApp/DdayWidgetExtension/ConnectionWidgetConfigurationIntent.swift iosApp/DdayWidgetExtension/SendSignalHttpClient.swift iosApp/DdayWidgetExtension/DdayWidgetBundle.swift iosApp/DdayWidgetExtensionTests/SendSignalHttpClientTests.swift
git commit -m "feat: add iOS connection widget and signal sending"
```

---

### Task 6: iOS — 즉시 반영 경로 (NSE + 공통 폴백)

**Files:**
- **수동(Xcode GUI)**: File > New > Target > Notification Service Extension, 이름 `SignalNotificationService`. 이 타겟과 `iosApp`, `DdayWidgetExtension` 세 타겟 모두 App Group `group.com.example.widgetly`를 Signing & Capabilities에 등록
- Create: `iosApp/SignalNotificationService/NotificationService.swift`
- Create: `iosApp/DdayWidgetExtension/TimelineRefreshPolicy.swift`
- Modify: `iosApp/DdayWidgetExtension/ConnectionWidget.swift` — `TimelineProvider`가 `TimelineRefreshPolicy`를 거쳐 서버 조회 여부를 결정

**Interfaces:**
- Consumes: App Group 저장소(개인 디데이 위젯 플랜), `Connection` 모델(Task 1)
- Produces: `struct TimelineRefreshPolicy { func shouldFetchFromServer(now: Date, lastFetchedAt: Date?, pushArrivedSinceLastFetch: Bool) -> Bool }` — "푸시 수신 직후" 또는 "마지막 조회 후 30분 경과" 조건

**동작:**
- `NotificationService.didReceive`: 알림 payload에서 connection 스냅샷을 읽어 App Group에 쓰고, `pushArrivedSinceLastFetch` 플래그를 세운 뒤 `WidgetCenter.shared.reloadTimelines(ofKind: "ConnectionWidget")` 호출. 메모리 제한 때문에 실패할 수 있음을 주석으로 남긴다(실기기 검증 필요).
- `TimelineRefreshPolicy.shouldFetchFromServer`: `pushArrivedSinceLastFetch`가 true면 항상 true. 아니면 `now - (lastFetchedAt ?? .distantPast) >= 30분`일 때만 true. `lastFetchedAt`은 App Group에 영속 저장되어 앱/위젯 프로세스 재시작에도 유지된다(Review Focus 4번).
- 폴백 조회가 일어나면 `lastFetchedAt`을 갱신하고 `pushArrivedSinceLastFetch`를 false로 리셋한다.

- [ ] **Step 1: 수동 Xcode 작업 수행**

- [ ] **Step 2: Write the failing test**

```swift
final class TimelineRefreshPolicyTests: XCTestCase {
    func testFetchesImmediatelyWhenPushArrived() {
        let policy = TimelineRefreshPolicy()
        XCTAssertTrue(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: Date(), pushArrivedSinceLastFetch: true))
    }

    func testDoesNotFetchBeforeThirtyMinutesWithoutAPush() {
        let policy = TimelineRefreshPolicy()
        let tenMinutesAgo = Date().addingTimeInterval(-10 * 60)
        XCTAssertFalse(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: tenMinutesAgo, pushArrivedSinceLastFetch: false))
    }

    func testFetchesAfterThirtyMinutesWithoutAPush() {
        let policy = TimelineRefreshPolicy()
        let fortyMinutesAgo = Date().addingTimeInterval(-40 * 60)
        XCTAssertTrue(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: fortyMinutesAgo, pushArrivedSinceLastFetch: false))
    }

    func testFetchesWhenThereHasBeenNoFetchYet() {
        let policy = TimelineRefreshPolicy()
        XCTAssertTrue(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: nil, pushArrivedSinceLastFetch: false))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

- [ ] **Step 4: `TimelineRefreshPolicy` 구현, `NotificationService.swift` 구현, `ConnectionWidget`의 `TimelineProvider`에 연결**

- [ ] **Step 5: Run tests to verify they pass**

- [ ] **Step 6: 실기기에서 수동 검증 — 알림 권한을 켠 상태/끈 상태 각각에서 상대 신호가 위젯에 반영되는 데 걸리는 시간을 확인하고, 안 되면 스펙 섹션 6에 결과를 기록**

- [ ] **Step 7: Commit**

```bash
git add iosApp/SignalNotificationService iosApp/DdayWidgetExtension/TimelineRefreshPolicy.swift iosApp/DdayWidgetExtension/ConnectionWidget.swift iosApp/iosApp.xcodeproj
git commit -m "feat: add push-triggered and fallback widget refresh for iOS"
```
