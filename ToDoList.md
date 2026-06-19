# AppToon — 앞으로 구현할 기능

## 신규 회차 알림 (인앱) — 미구현
구독한 작품에 새 회차가 발행되면 구독자에게 인앱 알림을 띄운다.

### 트리거 지점
- 즉시 발행: `EpisodeService.upload`로 `PUBLISHED` 회차가 생성될 때
- 예약 발행: `EpisodePublisher.publishDueEpisodes` / `EpisodeService.publishDueEpisodes(now)`가 `SCHEDULED → PUBLISHED`로 전환할 때

### 설계 방향
- `Notification` 엔티티(user, episode 또는 series, type, read 여부, createdAt) + Flyway `V13`(현재 V10까지 사용 중)
- 발행 훅에서 해당 작품 구독자(`SubscriptionRepository`로 조회)에게 fan-out 으로 알림 레코드 생성
- 조회 API: `GET /api/me/notifications`(페이징), 읽음 처리 `PATCH .../{id}/read`
- 미읽음 개수 노출(뱃지용)

### 유의점 / 트레이드오프
- **fan-out 트랜잭션**: 구독자가 많으면 알림 대량 생성 → 배치 insert 고려. 발행 트랜잭션과 분리할지(실패 격리) 결정 필요.
- **중복 방지**: 같은 회차에 대해 구독자당 알림 1건(유니크 or 멱등).
- **외부 푸시(FCM/APNs)는 범위 밖** — 인앱 DB 알림으로 한정.
- 구독 UP 플래그(`PersonalizationService`)와 의미가 일부 겹치므로 역할을 구분할 것(UP=목록 배지, 알림=이벤트 로그).

### 우선순위
조회수·북마크 다음. fan-out 설계 부담이 가장 크므로 추가기능 중 마지막.
