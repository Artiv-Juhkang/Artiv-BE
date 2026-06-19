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

### 구현 방식 선택지와 그에 따른 추가 설정
구현 방식에 따라 의존성·설정·인프라가 달라진다. 결정 후 그 라인만 추가한다.

#### A. 알림 전달 방식 (클라이언트가 알림을 받는 경로)
| 방식 | 추가 의존성 | 추가 설정 | 비고 |
|---|---|---|---|
| **폴링(Polling)** | 없음 | 미읽음 개수 API(`GET /api/me/notifications/unread-count`) | 가장 단순. 클라가 주기 조회. **학습 권장 기본** |
| **SSE(SseEmitter)** | 없음(Spring MVC 내장) | SSE 엔드포인트 + 연결 보관소(메모리 `Map<userId, Emitter>`), 타임아웃/재연결, 인증된 연결 관리 | 서버→클라 단방향 실시간. 다중 서버면 보관소 공유 필요 |
| **WebSocket(STOMP)** | `spring-boot-starter-websocket` | `@EnableWebSocketMessageBroker`, STOMP 엔드포인트, 핸드셰이크 JWT 인증, 사용자 채널(`/user/queue/...`) | 양방향. 설정 부담 큼 |
| **외부 푸시(앱 종료 중에도)** | RN Expo: `expo-notifications` + Expo Push API / 순수 FCM: `firebase-admin` | 디바이스 토큰 저장(`DeviceToken` 엔티티 + V마이그레이션), Expo Push Token 또는 FCM 서버키(환경변수), 토큰 등록 API | **RN Expo는 Expo Push가 가장 단순**(자체 FCM/APNs 키 관리 회피) |

#### B. fan-out 처리 방식 (구독자에게 알림 뿌리기)
| 방식 | 추가 설정 | 비고 |
|---|---|---|
| **동기(발행 트랜잭션 내)** | 없음 | 단순. 구독자 많으면 발행 응답 지연 |
| **비동기 이벤트** | `@EnableAsync` + `TaskExecutor` 빈, 또는 `@TransactionalEventListener`(발행 커밋 후 발송) | 발행 트랜잭션과 분리 → 실패 격리·응답 지연 회피. **소규모면 이게 균형점** |
| **메시지 큐(Kafka/RabbitMQ)** | 브로커 인프라(docker-compose에 추가), 프로듀서/컨슈머 | 대규모 전용. 학습 범위 초과 |

#### C. 저장·성능
- fan-out 다건은 **배치 insert**(`saveAll` 또는 JdbcTemplate batch)로.
- 미읽음 개수는 매번 count 쿼리 vs 캐싱(선택).
- 중복 방지: `(user, episode)` 유니크 또는 멱등 — 같은 회차 알림 1건.

#### 권장 조합 (학습 범위)
**폴링 + `@TransactionalEventListener` 비동기 fan-out + 배치 insert.** 실시간(SSE/WebSocket)·외부푸시(Expo)는 그 위에 단계적으로 얹는다. CORS·정적 이미지 서빙(STEP 9)·인증(JWT)은 이미 갖춰져 있어 알림 자체 로직에만 집중하면 된다.

### 우선순위
조회수·북마크 다음. fan-out 설계 부담이 가장 크므로 추가기능 중 마지막. 전달 방식(A)을 먼저 정하면 나머지 설정이 따라온다.
