# 위젯 구현 컨벤션

Android(Glance)와 iOS(WidgetKit)는 네이티브 프레임워크라 위젯 UI는 공유하지 않는다. `shared` 모듈은 데이터/로직만 제공하고, 각 플랫폼이 그 데이터를 가져와 자신의 위젯 프레임워크로 그린다. 전체 배경은 `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` 참고.

## 위젯 인스턴스 ↔ 대상 매핑

위젯 하나는 항상 하나의 대상(커플·친구 `connectionId` 또는 로컬 D-day 엔트리 id)을 보여준다. 다중 연결/다중 D-day를 지원하므로, 위젯을 홈 화면에 추가하는 시점에 OS의 위젯 구성 기능으로 대상을 선택하게 한다.

- **Android**: Glance 위젯 구성 액티비티(또는 `GlanceAppWidgetConfigurationActivity`)에서 대상 id를 선택해 `GlanceStateDefinition`에 저장
- **iOS**: `WidgetConfigurationIntent`로 대상 id를 파라미터화

## 위젯에서 바로 전송하기 (앱 열지 않고)

신호는 클라이언트가 Firestore에 직접 쓰지 않고, 서버의 `sendSignal` 콜러블 함수를 호출해서 보낸다 (이유는 `docs/firebase-pairing.md` 참고 — 레이트리밋/멱등성/보안을 트랜잭션 하나로 처리하기 위함).

**Android**: Glance의 `actionRunCallback<T>`로 `ActionCallback`을 연결한다. `ActionCallback.onAction()`은 백그라운드 실행 제한을 받으므로 여기서 직접 네트워크 I/O를 하지 않고, WorkManager에 전송 작업을 enqueue한다. WorkManager가 네트워크 제약과 재시도를 자동으로 처리하고, Worker 안에서 `sendSignal`을 호출한다.

```kotlin
// androidApp Glance 위젯 액션
class SendSignalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val connectionId = parameters[ConnectionIdKey] ?: return
        val preset = parameters[PresetKey] ?: return
        val signalId = UUID.randomUUID().toString() // WorkManager 재시도 시 멱등성 키로 사용
        val workRequest = OneTimeWorkRequestBuilder<SendSignalWorker>()
            .setInputData(workDataOf("connectionId" to connectionId, "preset" to preset, "signalId" to signalId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
// SendSignalWorker.doWork()에서 sendSignal(connectionId, preset, signalId) 콜러블 함수 호출
```

**iOS (최소 타겟 17)**: App Intents로 위젯 안에 버튼을 넣고, `AppIntent.perform()`에서 `sendSignal` 콜러블 함수를 HTTPS로 호출한다. **Firestore SDK 전체를 익스텐션에 넣지 않는다** — 위젯/인텐트 익스텐션은 메모리 제한이 엄격해서, Auth 토큰 + 가벼운 HTTPS 호출만으로 구현해야 메모리 초과로 인텐트가 죽는 위험을 줄인다. iOS 17 미만 분기는 두지 않는다(버전 분기 비용이 이득보다 크다고 판단).

## 상대 신호를 받았을 때 위젯 갱신

실시간 연결은 없다 — 즉시성은 push가, 지속 표시는 위젯이 담당한다. 플랫폼/iOS 버전별 우선순위:

- **Android**: FCM data message 수신 즉시 로컬 캐시(스냅샷)를 갱신하고 Glance를 다시 그린다 (`GlanceAppWidgetManager.update()` 호출) → 거의 즉시 반영.
- **iOS 26+**: WidgetKit push(`apns-push-type: widgets`)로 시스템에 타임라인 재조회를 요청. FCM이 이 헤더를 그대로 전달하는지는 구현 단계에서 검증 필요(안 되면 APNs 직접 호출).
- **iOS 17~25**: push 도착 시 Notification Service Extension이 App Group 공유 컨테이너에 스냅샷을 쓰고 `WidgetCenter.shared.reloadTimelines(ofKind:)` 호출. **실기기 검증 필수** — 공식적으로 보장된 동작이 아니고, 알림 권한을 끈 사용자는 이 경로가 아예 없다.
- **공통 폴백 (모든 iOS 버전)**: 위젯의 `TimelineProvider`가 시스템 스케줄로 갱신될 때, 캐시된 스냅샷만 다시 그리는 게 아니라 직접 서버에서 최신 상태를 조회한다. NSE/WidgetKit push가 실패하거나 알림이 꺼져 있어도 이 경로로는 결국 반영된다. 단, Firestore 읽기 할당량 때문에 매 갱신마다 조회하지 않고 "푸시 수신 직후" 또는 "마지막 조회 후 30분 경과" 조건에서만 조회한다 (`docs/firebase-pairing.md`의 읽기 할당량 계산 참고).

## 테스트

- Android: Glance 액션 콜백이 WorkManager에 올바르게 enqueue하는지 instrumented test로 확인
- iOS: NSE의 App Group 쓰기 로직은 XCTest로, 위젯까지의 실제 반영은 실기기 수동 검증(자동화 어려움)

## 추후 보완할 점

개인 디데이 위젯(Plan 1) 구현 중 발견한, 지금 당장은 아니지만 나중에 손봐야 할 것들.

- **Glance 세션 재사용 패턴을 커플·친구 공유 위젯(Plan 4)에도 반드시 그대로 적용할 것.** `GlanceAppWidget.provideGlance()`는 위젯 세션당 한 번만 호출되고, `update()`/`updateAll()`은 기존 세션을 재사용하며 `provideContent{}`의 컴포저블만 재구성한다. `provideGlance()` 안에서 상태를 한 번만 읽어 컴포저블 람다에 캡처하면 이후 어떤 갱신도 반영되지 않는다(2026-10-05에 개인 위젯에서 실제로 겪은 버그, `docs/cross-platform-verification.md` #2/#5 참고). 공유 위젯도 같은 `GlanceAppWidget` 기반이므로 상태는 `currentState()`로 컴포저블 내부에서 반응형으로 읽고, "선택된 대상은 그대로인데 내용만 바뀐" 경우를 위해 refresh-token 패턴(또는 동급 장치)을 처음부터 넣어야 한다.
- **위젯 레이아웃/타이포그래피가 아직 placeholder 수준.** 테마 시스템(Plan 2)으로 배경/글자 색상은 적용했지만, `Box` + 가운데 정렬 `Text` 뿐인 레이아웃 자체는 그대로다 — 다음 작은 UI 패스에서 다듬을 것.
- **2×1 외의 리사이즈 상태는 실기기 미검증.** `resizeMode="horizontal|vertical"`을 허용해뒀지만, 사용자가 위젯을 확대/축소했을 때 레이아웃이 실제로 어떻게 보이는지는 아직 확인 안 함.
- iOS 쪽 동일 버그 클래스(세션/타임라인 재사용으로 인한 미반영) 확인 필요 — `docs/cross-platform-verification.md`에서 추적 중.
- **`DdayWidgetExtensionTests`가 위젯 익스텐션 전용 Swift 코드를 테스트할 방법이 없던 문제, 부분 해결함(2026-10-06, 공유 커플·친구 위젯 플랜 Task 5).** `DdayWidgetExtension` synchronized group(`84C19F5D3073444700B9B960`)을 테스트 타겟의 `fileSystemSynchronizedGroups`에 추가하되, `AppIntent.swift`/`ConnectionWidget.swift`/`ConnectionWidgetConfigurationIntent.swift`/`DdayWidgetExtension.swift`/`DdayWidgetExtensionBundle.swift`/`Info.plist`/`Assets.xcassets`는 전용 `PBXFileSystemSynchronizedBuildFileExceptionSet`(`AD2B71EAA87F4B94B64FA106`)으로 테스트 타겟 멤버십에서 빼서, **`SendSignalHttpClient.swift`만** 테스트 타겟에 들어가도록 최소 범위로 열었다(WidgetKit 의존 코드까지 전부 넣는 건 이번 태스크 범위 밖이라 보수적으로 좁힘). `AppIntent.swift`/`DdayWidgetExtension.swift`처럼 WidgetKit에 의존하는 나머지 파일을 테스트하려면 이 섹션 처음에 적힌 원래 아이디어(전체를 열고 `@main` 파일만 빼기)를 시도하되, WidgetKit/SwiftUI framework가 테스트 타겟에 링크되는지부터 확인할 것 — 아직 미검증.
- **(2026-10-06 발견 → 2026-10-11 해결) `Shared.framework`를 링크하는 iOS 타겟은 실제 Firebase iOS SDK 없이는 링크가 안 된다 — 그래서 위젯 익스텐션은 `SharedCore.framework`를 링크한다.** Kotlin/Native는 모듈의 의존성 그래프 전체를 하나의 정적 프레임워크 바이너리로 묶고 미사용 cinterop 코드를 dead-strip 해주지 않는다. `shared`의 `commonMain`에 GitLive Firebase(Auth/Firestore/Functions)가 들어온 뒤부터 `Shared.framework`는 `FIRAuth`/`FIRFirestore`/`FIRFunctions` 등 Objective-C 클래스 심볼을 외부에서 해석해야 하는 상태로 빌드되고, 익스텐션 쪽 Swift가 Firebase를 직접 import하지 않아도(`Connection` 같은 순수 타입만 써도) 무관하게 `ld: symbol(s) not found for architecture arm64`(약 30개의 `_OBJC_CLASS_$_FIR*`/`_FIR*ErrorDomain`)로 실패했다 — 이미 출시된 디데이 위젯의 회귀. **해결**: `shared`를 Firebase-free `shared-core`(→ `SharedCore.framework`)와 Firebase 의존 `shared`(→ `Shared.framework`)로 분리하고, `DdayWidgetExtensionExtension`/`DdayWidgetExtensionTests` 타겟의 `Compile Kotlin Framework` 스크립트 페이즈를 `:shared-core:embedAndSignAppleFrameworkForXcode`로, Swift 쪽은 `import Shared` → `import SharedCore`로 바꿨다. 두 타겟 모두 clean 빌드에서 미정의 심볼 0개로 성공. **유지 규칙**: Firebase를 쓰는 코드는 절대 `shared-core`에 넣지 말 것 — 넣는 순간 이 회귀가 그대로 돌아온다. `iosApp` 메인 타겟은 여전히 `Shared.framework`를 링크하므로 Xcode SPM으로 `firebase-ios-sdk`를 추가하는 수동 작업이 끝나기 전까지는 빌드되지 않는다(`error: Unable to find module dependency: 'FirebaseCore'`); 같은 이유로 `./gradlew :shared:iosSimulatorArm64Test`도 `ld: framework 'FirebaseCore' not found`로 실패한다. `:shared-core:iosSimulatorArm64Test`는 정상 통과한다.
