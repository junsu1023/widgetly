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
