# Widgetly

홈 화면 위젯 개인화 앱. 디데이 위젯, 커플·친구 공유 위젯, 테마를 중심으로 Android/iOS에 동시 출시한다.

## 제품 개요

- **타겟**: 한국 10~20대. 숏폼(틱톡/릴스/쇼츠)에서 자연스럽게 퍼지는 바이럴 확산을 지향.
- **운영비**: Firebase Blaze 플랜(Cloud Functions는 Spark 무료 플랜에서 실행 불가)이지만 무료 사용량 범위 안에서 운용.
- **수익화**: 테마 소액 결제 + 평생 이용권 (MVP 이후 단계, 아래 범위 참고).

## MVP 범위

1. **개인 위젯 코어** — 디데이 계산, 데이터 모델, 로컬 저장
2. **테마 시스템** — 위젯 꾸미기, 적용/미리보기
3. **커플·친구 공유 위젯** — Firebase 실시간 동기화, 초대/연결 흐름

수익화(테마 결제/평생 이용권)와 바이럴 공유 장치는 MVP 범위 밖. 트래픽/리텐션 검증 후 다음 단계에서 설계한다.

## 아키텍처 원칙

- **Kotlin Multiplatform + Compose Multiplatform**: 앱 본체(설정, 테마 선택 화면 등)의 UI와 로직을 Android/iOS 간 공유.
- **위젯 UI는 공유하지 않는다**: Android 홈 위젯(Glance/RemoteViews)과 iOS 홈 위젯(WidgetKit/SwiftUI)은 각 플랫폼의 네이티브 프레임워크이므로, 위젯 화면 자체는 플랫폼별로 따로 구현한다.
- `shared` 모듈이 공유하는 것: 날짜 계산 등 도메인 로직, 데이터 모델, Firebase 동기화 로직, 결제 상태 — 즉 위젯이 그리는 데이터와 그 데이터를 만드는 로직까지만 공유하고, 그리는 방식(UI)은 공유하지 않는다.
- **iOS 최소 타겟은 iOS 17.** 위젯 내 인터랙티브 전송(App Intents)에 필요하고, 구버전 분기 비용이 이득보다 크다고 판단해 버전 분기를 두지 않는다.
- **백엔드는 Firebase 전체 사용** (Firestore + Cloud Functions + FCM + 익명 인증). 커플·친구 연결은 6자리 코드 기반 pairing으로, 전화번호 인증 비용과 Dynamic Links 종료 문제를 모두 회피한다.
- **신호 전송/연결 생성은 반드시 Cloud Functions callable 경유.** 클라이언트가 `signals` 등 서버 전용 컬렉션에 직접 쓰지 않는다 — 레이트리밋/멱등성/보안을 트랜잭션 하나로 보장하기 위함.

자세한 아키텍처/데이터 모델/플로우는 `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` 참고.

## 모듈 구조

- `androidApp` — Android 앱 엔트리 포인트. 홈 위젯(Glance)도 이 안에 위치.
- `iosApp` — iOS 앱 엔트리 포인트(SwiftUI). 홈 위젯(WidgetKit) extension도 이 프로젝트 안에 위치.
- `shared` — 공통 로직/데이터 모델/Compose Multiplatform UI(`commonMain`), 플랫폼별 구현(`androidMain`/`iosMain`).

## 빌드 & 테스트

- Android 앱: `./gradlew :androidApp:assembleDebug`
- iOS 앱: `/iosApp` 디렉토리를 Xcode로 열어서 실행
- Android 테스트: `./gradlew :shared:testAndroidHostTest`
- iOS 테스트: `./gradlew :shared:iosSimulatorArm64Test`

## 문서

- `docs/superpowers/specs/` — 아키텍처 설계 스펙 (brainstorming 프로세스 결과물)
- `docs/widget-development.md` — Android Glance / iOS WidgetKit 구현 컨벤션 *(설계 확정 후 작성)*
- `docs/theme-system.md` — 테마 데이터 모델/적용 방식 *(설계 확정 후 작성)*
- `docs/firebase-pairing.md` — 커플·친구 연결, Firestore 스키마, 무료 티어 제약 *(설계 확정 후 작성)*
- `docs/billing-iap.md` — Google Play Billing / StoreKit 연동 *(MVP 이후 단계에서 작성)*
- `docs/design-guidelines.md` — 비주얼 아이덴티티, 타겟 감성 가이드 *(UI 설계 단계에서 작성)*
- `docs/cross-platform-verification.md` — **사용자는 Android만 실기기 테스트 가능.** Android에서 버그를 발견/수정할 때마다 여기 기록하고 iOS 쪽 확인 여부를 추적한다. 플랫폼별 버그 수정 시 반드시 갱신.
