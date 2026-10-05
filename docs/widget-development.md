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
- **(신규, 2026-10-06 발견) `Shared.framework`를 임베드하는 어떤 iOS 타겟도 실제 Firebase iOS SDK 프레임워크 없이는 링크 자체가 안 됨.** `shared/build.gradle.kts`가 GitLive Firebase(Auth/Firestore/Functions)를 `commonMain`에 `api`로 물려놨기 때문에(firebase-backend 플랜 Task 2 fix round), `Shared.framework`는 그 GitLive 바인딩이 참조하는 `FIRAuth`/`FIRFirestore`/`FIRFunctions` 등 Objective-C 클래스 심볼을 외부에서 해석해야 하는 상태로 빌드된다. Kotlin/Native 프레임워크는 모듈 전체를 하나의 바이너리로 묶기 때문에, 위젯 익스텐션 쪽 Swift 코드가 Firebase를 직접 import하지 않고 `Connection`처럼 순수한 타입만 써도 무관하게 이 문제가 발생한다 — **`DdayWidgetExtensionExtension`과 `DdayWidgetExtensionTests` 둘 다, 내 변경 없이 원본 상태로도 동일하게 링크 실패함을 직접 확인**(`git stash`로 재현, `ld: symbol(s) not found for architecture arm64`, 29개 `_OBJC_CLASS_$_FIR*`/`_FIR*ErrorDomain` 심볼). firebase-backend 플랜 Task 2의 보류 중인 수동 작업(Xcode SPM으로 `firebase-ios-sdk` 추가)이 완료되기 전까지는 `xcodebuild build`/`test`로 이 타겟들을 끝까지 실행할 방법이 없다 — Swift 컴파일 자체는 전부 깨끗하게 통과하는 것으로 코드 정확성은 확인했지만, 실제 "빌드 성공"/"테스트 통과" 콘솔 출력은 그 수동 작업이 끝난 뒤에야 받을 수 있다. 위젯/인텐트 익스텐션에 Firebase SDK를 넣지 않는다는 이 문서 상단의 원칙이 소스 레벨에서는 지켜지고 있지만(아무 파일도 Firebase를 직접 import 안 함), 빌드 그래프 레벨에서는 이미 깨져 있다는 뜻 — Task 2 수동 작업이 끝나면 저절로 해소되겠지만, 혹시 익스텐션을 Firebase 없이 완전히 독립시키고 싶다면 `shared`를 모듈 분리(위젯이 쓰는 순수 타입 전용 KMP 타겟 vs 메인 앱 전용 Firebase 연동 타겟)하는 더 큰 리팩터가 필요하다.
