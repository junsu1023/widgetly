# 테마 시스템

위젯의 색상/그래픽을 꾸미는 기능. 전체 배경은 `docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md` 참고.

## MVP 범위

MVP에서는 **모든 테마를 무료로 제공**한다. 소유권/결제 개념 자체가 MVP엔 없다 — 엔타이틀먼트 필드를 미리 만들어두지 않는다(플레이스홀더 금지). 테마 소액 결제/평생 이용권은 MVP 이후 단계(`docs/billing-iap.md`, 아직 미작성)에서 다룬다.

## 적용 범위

테마는 개인 D-day 위젯과 커플·친구 공유 위젯 양쪽에 적용 가능하다. 적용된 테마는 `shared` 모듈의 데이터 모델에 저장하고, 각 플랫폼의 위젯 렌더링 시 참조한다 (렌더링 자체는 플랫폼별 — `docs/widget-development.md` 참고).

## 데이터 모델

- MVP: 소유권 필드 없음. 위젯별 적용 테마만 위젯 구성(Android Glance 파라미터 / iOS `WidgetConfigurationIntent`)에 저장
- **엔타이틀먼트 도입 시(MVP 이후) 지켜야 할 원칙**: 소유 테마 목록은 `entitlements/{uid}` 같은 **별도의 서버 전용 쓰기(Admin SDK만) 컬렉션**으로 만든다. `users/{uid}` 문서는 닉네임처럼 클라이언트가 직접 쓰는 문서라, 같은 문서에 소유권 필드를 같이 두면 결제를 붙이는 순간 클라이언트가 자기 소유권을 직접 조작할 수 있는 보안 구멍이 생긴다.

## 확장 포인트 (MVP 이후)

- 테마 구매 플로우 (Google Play Billing / StoreKit) — `entitlements/{uid}` 신설과 함께 설계
- 상대에게 테마 선물하기 — 커플 연결 관계를 이용해 상대의 `entitlements`에도 부여하는 흐름 (서버 함수를 통해서만, 클라이언트 직접 쓰기 금지)
