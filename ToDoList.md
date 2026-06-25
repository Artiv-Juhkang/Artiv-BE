# Artiv — 빌드 로드맵

> (구) "AppToon 웹툰 플랫폼" → **Artiv 다매체 아트 창작 플랫폼**(웹툰·일러스트·음악/오디오 등 + 커뮤니티·채팅·서재·후원)으로 컨셉 확장 중.
> 이름 `Artiv`는 **잠정**(확정 전 상표/도메인 조회 필요). 작명 후보·근거: `AppToon_Front/design-concepts/naming-concepts.md`.
> 재설계 스펙: `AppToon_Front/docs/superpowers/specs/`.

## 현황 (2026-06)

- **백엔드는 대부분 구현됨**(Flyway **V22**): 인증·작품·회차·개인화·커뮤니티(게시글/댓글)·팔로우·**알림**·신고·1:1문의·작가전환·관리자.
  - ⚠️ 과거 이 문서는 "신규 회차 알림 — 미구현 / 현재 V10까지"로 적혀 있었으나, **알림은 V20에서 구현 완료**(polymorphic Notification: recipient/type/target/read/dedup). 상세는 `docs/design-notification.md`·`docs/features.md §3.8`.
- **프론트는 인증·작품·회차·서재만 화면 연결.** 커뮤니티·알림·팔로우 등은 백엔드(또는 프론트 클라이언트)는 있으나 **화면이 비어 있는 "휴면" 상태** → 화면을 얹는 작업이 다수.
- 아직 없는 것: 다매체 콘텐츠 모델(현재 웹툰 전용 Series→Episode→EpisodeImage), 실시간 채팅, 실결제 후원.

## 빌드 순서 (Artiv 재설계)

1. JWT access/refresh **자동로그인 검증** (이미 구현됨 — 동작 확인만)
2. **멀티미디어 콘텐츠 모델** — `ContentType` 구분자 + `EpisodeImage→MediaAsset` 일반화(가산 Flyway V23+), 회차 뷰어 실구현
3. **리브랜드 + 5탭 셸** — Artiv 식별자/UI, 5탭(창작물·커뮤니티·채팅·서재·내정보), 커뮤니티·채팅 플레이스홀더 ✅ *진행/완료*
4. 후원 **플레이스홀더 유지** (실결제 전)
5. **커뮤니티 탭** — 휴면 posts API에 화면(피드·작성·댓글)
6. **프로필 + 소셜** — 알림·팔로우·프로필수정·신고·문의 화면
6.5 **작가 업로드/창작 도구** — 작품 등록·회차 업로드(현재 통째 휴면)
7. **채팅** — 폴링 MVP(1:1+그룹·텍스트) → 이후 WebSocket
8. **실결제 후원** — PG(PortOne/Toss) 연동·정산 원장(외부 의존 큼, 최후)

> 도메인 기능 명세: `docs/features.md` · 화면/연동: `docs/frontend-*.md` · 수익모델: `docs/roadmap-and-monetization.md`.
