# 모더레이션 · 활동내역 · 인앱 알림 — 단계 설계 (Living Design Doc)

> 신고/커뮤니티 모더레이션 상세화 + 독자 서재(활동내역) + 작가 소식 피드 + **인앱 알림 저장소(멘션 포함)**.
> 멀티에이전트 설계(2026-06-22) 결과를 단계화. **외부 0(폴링)** 우선, **푸시(FCM)만 외부**로 분리.
> 상세 알림 데이터모델은 [`design-notification.md`](design-notification.md), 로드맵은 [`roadmap-and-monetization.md`](roadmap-and-monetization.md).

## 핵심 결정 (열린 질문 정리)
- **활동내역 = 런타임 조합**: ActivityLog 테이블 없이 기존 ReadLog/Bookmark/Subscription/Post/PostComment/PostLike를 서비스에서 합쳐 정렬(YAGNI). 성능 병목 시 ActivityLog 도입.
- **멘션 = inline 정규식 파싱**: `@닉네임` → User 조회 → Mention 저장 + 알림. 별도 배치 없이 동기.
- **작가 소식 피드 = Post 재사용**: 팔로우한 작가의 Post 스트림(Follow JOIN Post). 별도 엔티티 없음.
- **신고 처리 = 액션 선택**: 기각 / 처리(유지) / 블라인드 / 대상 삭제.
- **알림 = 폴링**: `design-notification.md` 활성화(폴리모픽·dedup·read_at). 푸시는 그 위에 후처리.

## 단계 계획
| Phase | 범위 | 상태 |
|---|---|---|
| **1. 신고 상세·처리·필터** | ReportAdminDetailResponse(대상 content·author·관련신고수) + `GET /api/admin/reports/{id}` + resolve 액션(NONE/BLIND_TARGET/DELETE_TARGET) + 필터(status·targetType·reason) + 블라인드 이력(V19 blinded_by/at) + 콘솔 상세 모달 | ✅ **완료(STEP 23)** |
| **2. 커뮤니티 관리 상세·검색** | PostAdminDetailResponse(content·images) + `GET /api/admin/posts/{id}` + `findForAdmin`(category·제목키워드·블라인드필터) + 콘솔 검색·필터·상세 모달 | ✅ **완료(STEP 23)** |
| **3. 인앱 알림 저장소** | V20 notifications(폴리모픽·title/message·read_at·dedup_key) + Notification 엔티티/enum + NotificationService(fanOut 멱등·getMine(종류필터)·unreadCount·unreadSummary·markRead(라우팅정보 반환)/markAllRead) + NotificationController(폴링 API) + INQUIRY_ANSWERED 배선 + 콘솔 알림 벨(폴링 배지·종류탭·읽음·라우팅) | ✅ **완료(STEP 24)** |
| **4. 이벤트 연결 + 멘션** | 도메인 이벤트(`@TransactionalEventListener` AFTER_COMMIT + @Async) → 신규회차·문의답변·게시글댓글·대댓글·팔로우 알림. **멘션**: Post/PostComment content `@닉네임` 파싱 → 알림 + Mention 저장 | ⬜ |
| **5. 독자 서재 / 활동내역** | `GET /api/me/activity?type=`(열람·관심·구독·내글·내댓글·추천·언급, 런타임 조합) + `GET /api/me/posts·/post-comments·/liked-posts·/mentioned` | ⬜ |
| **6. 작가 소식 피드** | `GET /api/me/author-news-feed`(Follow JOIN Post 최신순) + `GET /api/authors/{id}/posts` | ⬜ |
| **7. (외부) 푸시** | FcmService.pushAsync(recipientId) — NotificationService.fanOut 후처리. SSE 실시간(폴링 위 승격). 알림 뮤트(notification_preferences)·정리 배치 | ⛔ 외부 |

## 비평 반영 메모 (구현 시)
- 블라인드 이력: 자동 블라인드는 `blinded_by=NULL`(시스템), 관리자는 adminId 기록. ✅
- 멘션 대상 범위: Post author + 모든 Comment author(자신 제외). 미존재 닉네임·스팸 멘션 방어(중복 제거·횟수 상한).
- 활동/서재/알림은 **본인만**(IDOR — userId는 `@AuthenticationPrincipal`). 블라인드된 글은 활동/멘션 노출에서 제외.
- liked-posts·활동 조회 N+1: `findByIdIn` 배치 로드.
- 알림 보존정책: 지금 무한, 향후 읽음+90일 정리 배치.
- **알림 dedup은 수신자 단위**(STEP 24, 적대적 리뷰 반영): unique `(recipient_id, dedup_key)` + `existsByRecipientIdAndDedupKey`. fanOut 시그니처 `Function<Long,String>`(수신자별 키)와 정합 — 전역 유니크였다면 Phase 4 다대상 fanOut(회차/게시글)에서 한 수신자가 다른 수신자 알림을 묵음 처리하는 사일런트 드롭 발생.
- **(보류) fanOut TOCTOU**: 동시 같은 (recipient,dedup_key) 삽입 시 부분 유니크가 두 번째를 거부→500. 1인 단일관리자 기준 사실상 불가능(CLAUDE.md 발생불가 시나리오 예외처리 금지). Phase 4 다대상·고빈도 트리거 배선 시 native upsert(ON CONFLICT DO NOTHING) 또는 REQUIRES_NEW catch-continue로 처리.

## 완료(STEP 23) 엔드포인트 요약
- `GET /api/admin/reports?status&targetType&reason` · `GET /api/admin/reports/{id}` · `PATCH .../resolve {action}` · `PATCH .../dismiss`
- `GET /api/admin/posts?category&keyword&blinded` · `GET /api/admin/posts/{id}` · `PATCH .../blind|unblind`(관리자 기록)
- posts/post_comments에 `blinded_by`·`blinded_at`(V19).
