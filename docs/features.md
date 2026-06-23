# AppToon 기능 명세 (features.md)

AppToon 웹툰 플랫폼 백엔드가 제공하는 기능을 실제 소스(`src/main/java`, `db/migration`) 기준으로 정리한 문서. "무엇을 할 수 있는가 + 규칙·가드·예외"를 코드와 어긋나지 않게 기술한다. **엔드포인트 전수·요청/응답 스키마는 Swagger**(`/swagger-ui/index.html`)가 단일 출처이고, **연동 방법·공통 규약은 [`frontend-guide.md`](frontend-guide.md)**다.

---

## 1. 개요 — 이 플랫폼이 제공하는 것

화면(UI) 없는 HTTP API 서버. 앱/웹 프론트가 호출한다. 제공 기능:

- **계정·인증**: 이메일/비밀번호 가입·로그인(JWT, BCrypt), 토큰 회전, **자체 회원가입 + 법적 동의(약관)**, 프로필 편집(닉네임·비번·아바타·소개), **작가 전환 신청**.
- **작품·회차·뷰어**: 작가가 작품 등록·회차 이미지 업로드·예약 발행. 독자는 목록·상세·뷰어. 장르·태그 분류.
- **연령 게이트**: 19금 작품은 만 19세만. 가입 시 만 14세 미만 차단.
- **수익화 0단계(결제 없음)**: 작가가 작품별 **공개정책**(전체무료/기다리면무료) 설정 → 최신 회차 **시간 잠금**(N일 후 무료).
- **개인화·서재**: 구독·읽음·이어보기(UP)·북마크 + **독자 서재**(열람이력·관심·구독)·**활동내역**(내 글·내 댓글·추천·언급).
- **소셜·커뮤니티**: 회차 좋아요·댓글 + **커뮤니티 게시판**(말머리·이미지·추천·댓글/대댓글·`@멘션`) + **팔로우**·**작가 소식 피드**.
- **알림**: 인앱 알림함(댓글·대댓글·팔로우·멘션·새 회차·문의답변), 읽음/안읽음·종류별·라우팅(폴링).
- **안전·운영**: 폴리모픽 **신고** + 자동 블라인드, **1:1 문의**, 운영자(권한·연령·공개·작가신청·신고·커뮤니티·문의 관리).
- **이미지 저장 추상화**: 로컬 디스크 또는 S3 호환(MinIO/R2/AWS) 설정 전환.

응답 규약: 성공은 DTO 그대로(201+`IdResponse`/204), 에러는 `ErrorResponse{status,code,message,fieldErrors}`, 목록은 `PageResponse`(전체개수) 또는 `SliceResponse`(hasNext). **인증 없이 가능한 경로는 `/api/health`·`/api/auth/**`·`/files/**`·Swagger뿐 — 그 외 전부 인증 필요.**

---

## 2. 역할(Role)별로 할 수 있는 일

역할 3종(`Role`): **READER**·**CREATOR**·**ADMIN**. 가입 시 READER, 승격은 운영자만. 권한은 단일 enum(누적 아님).

| 기능 | READER | CREATOR | ADMIN |
|---|:---:|:---:|:---:|
| 가입·로그인·프로필·동의·**작가전환 신청** | O | O | O |
| 작품/회차 조회·구독·읽음·북마크·좋아요·댓글 | O | O | O |
| **커뮤니티**(글·댓글·추천·멘션)·**팔로우**·**알림**·**서재/활동**·**신고**·**문의** | O | O | O |
| **작품 등록·회차 업로드·장르태그·공개정책** | X | **O**(본인 작품) | X(역할상) |
| 비공개·미발행·잠긴 회차 프리뷰 | X | 본인 작품만 | **O(전체)** |
| 글/댓글 삭제 | 본인만 | 본인만 | **O(전체)** |
| **운영**(권한·연령·공개·작가신청·신고·커뮤니티·문의) | X | X | **O** |

- 작품 등록·회차 업로드·장르태그·공개정책은 `@PreAuthorize("hasRole('CREATOR')")` + 서비스에서 **작가 본인** 재검사. ADMIN도 CREATOR 역할 없으면 못 함.
- "프리뷰" = 비공개(`visible=false`)·미발행(`SCHEDULED`/`DRAFT`)·잠긴(기다리면무료) 콘텐츠를 일반 독자에겐 숨기되 작가 본인·ADMIN에겐 보여줌.

---

## 3. 도메인별 기능 상세

경로 변수: `{seriesId}`=작품ID, `{episodeNo}`=작품 내 회차번호(1부터), `{id}`=리소스ID. 개별 엔드포인트·DTO는 Swagger 참조.

### 3.1 인증 / 계정 / 프로필
- **회원가입** `POST /api/auth/signup` → 201 `{id}`. 입력 `email·password(8~64)·nickname·birthDate·consents{}`.
  - `nickname` **유일**(중복 `DUPLICATE_NICKNAME` 409) + **한글·영문·숫자·`_`만**(@멘션이 1명을 정확히 가리키게). 위반 시 400.
  - `birthDate` 필수(과거). **만 14세 미만 가입 차단**. 없으면 보수적으로 미성년 취급.
  - `consents` — 필수 동의(`TERMS_OF_SERVICE`·`PRIVACY_POLICY`) 미동의 시 가입 거부. 가입 시점 동의 기록(버전·시각). `MARKETING_EMAIL` 기본 opt-out.
- **로그인/갱신** `POST /api/auth/login`·`/refresh` → `{accessToken, refreshToken}`. refresh는 **회전**(1회용, 재사용 401). access 1h / refresh 14d.
- **내 정보·프로필**: `GET /api/users/me`(공유DTO, birthDate 미노출) · `GET /api/users/me`의 자기조회는 birthDate 포함(MyProfile). 변경: `PATCH /api/users/me/nickname`(유일성 검사)·`/bio`·`/password`(현재비번 확인)·`POST /api/users/me/avatar`(이미지).
- **동의 내역**: `GET /api/users/me/consents`(현재 동의 상태·버전) · `PATCH`(마케팅 등 선택동의 토글).
- **작가 전환 신청**: `POST /api/users/me/creator-request`(READER→) → 관리자 승인 시 CREATOR. 관리자 오클릭 대비 승인↔거부 **재처리 가능**(역할 변경은 조건부: 독자만 승격/작가만 강등).

### 3.2 팔로우 / 작가 소식 피드
- **팔로우** `POST /api/users/{targetId}/follow`(멱등, 자기 자신 불가) · `DELETE` 해제. `FOLLOWED` 알림 발생(재팔로우는 무알림).
- **목록·통계** `GET /api/users/me/following`·`/followers` · `GET /api/users/{id}/follow-stats`(팔로워·팔로잉 수·내 팔로우 여부).
- **작가 소식 피드** `GET /api/me/author-news-feed` → 팔로우한 사용자들의 **공개 게시글** 최신순(블라인드 제외). `GET /api/authors/{id}/posts`는 특정 작가의 공개 글.

### 3.3 작품 (series)
- **등록** `POST /api/series`(CREATOR) → 201 `{id}`. 입력 `title·description·ageRating·status·publishDays[]·adultOnly?·genre?·tags?`. 불변식 `adultOnly=true → ageRating=AGE_19`.
- **목록** `GET /api/series` → Page. 필터 `day·ageRating·genre·keyword·adultOnly·tag`, 정렬 `LATEST|ADULT_FIRST`.
- **상세** `GET /api/series/{id}` → `episodeCount·latestEpisodeNo·isSubscribed·genre·tags·releasePolicy·waitFreeDays` 포함. 비공개는 작가/ADMIN만(그 외 404).
- **장르·태그** `PATCH /api/series/{id}/genre-tags`(CREATOR 본인). `Genre`(ROMANCE…ETC) 단일 + 태그 집합(정규화).
- **공개정책(수익화)** `PATCH /api/series/{id}/release-policy`(CREATOR 본인) → `{mode, waitFreeDays}`. `mode`=`FREE_ALL|WAIT_FREE`. WAIT_FREE면 `waitFreeDays>0` 필수(아니면 400).

### 3.4 회차 / 뷰어 (+ 수익화 잠금)
- **업로드** `POST /api/series/{seriesId}/episodes`(CREATOR 본인, **multipart**) → 201 `{episodeNo}`. 입력 `title·publishAt?(미래면 예약)·images[]`. 회차번호 자동(+1). 이미지 JPEG/PNG, 폭 800px 리사이즈, 경로 `{seriesId}/{episodeNo}/{order}.{ext}`.
- **예약 발행(자동)**: `EpisodePublisher` 스케줄러가 발행시각 지난 `SCHEDULED`→`PUBLISHED` 전환 + 구독자에게 `EPISODE_PUBLISHED` 알림.
- **목록** `GET /api/series/{seriesId}/episodes` → Slice(무한스크롤), `PUBLISHED`만. **회차별 `locked`·`freeAt`** 포함(잠금 배지용).
- **상세/뷰어** `GET /api/series/{seriesId}/episodes/{episodeNo}` → 이미지·viewCount·likeCount·liked + **`locked·lockReason·freeAt`**. 열 때 조회수 +1(접근 가능 시에만).
  - 가드(순서): ① 비공개=작가/ADMIN만(404) ② 19금=연령검사 ③ 미발행=프리뷰만(404) ④ **수익화 잠금**(아래).
- **수익화 잠금(기다리면무료)** `EpisodeAccessEvaluator`(compute-on-read):
  - `FREE_ALL`이거나 작가/ADMIN이면 항상 열림. `WAIT_FREE`면 `freeAt = publishAt + waitFreeDays일`, `now >= freeAt`면 무료.
  - **잠긴 회차 = 200 + `locked:true·lockReason:"WAIT"·freeAt`**(에러 아님), **이미지·조회수 비노출**. 프론트는 "freeAt에 무료 전환" UI. (코인·멤버십 결제는 추후 단계 — `EpisodeAccessEvaluator`에 주입점만 마련.)
- **좋아요** `POST/DELETE .../like`(멱등).

### 3.5 연령 게이트
- 판정 `User.isAdult(today)`: `birthDate`가 (오늘−19년) 이전이면 성인. 없으면 미성년 취급.
- 적용: `ageRating=AGE_19` 작품의 회차 **목록·상세**에서 미성년이면 `ADULT_ONLY`(403). 작품 정보 카드는 보이되 회차 이미지에서 차단.
- `adultOnly`는 분류·정렬용 메타(불변식 `adultOnly=true → AGE_19`).

### 3.6 개인화 / 독자 서재 / 활동내역
- **구독/읽음/북마크**: `POST/DELETE /api/series/{id}/subscription` · `POST .../episodes/{no}/read` · `POST/DELETE .../bookmark`(모두 멱등). **읽음은 잠긴 회차면 기록 안 함**(못 본 회차가 이어보기/서재 오염 방지).
- **서재**: `GET /api/me/subscriptions`(UP 배지·이어보기) · `/bookmarks`(관심) · `/read-history`(열람한 작품·마지막 본 화).
- **활동내역**: `GET /api/me/posts`(내 글, 블라인드 포함+플래그) · `/post-comments`(내 댓글, 원글 제목) · `/liked-posts`(추천한 글, 블라인드 제외). **언급된 글**은 알림 `?type=POST_MENTIONED` 재사용.
- 전부 **본인만**(@AuthenticationPrincipal). 고정 정렬(클라 `?sort=` 무시). N+1은 배치 로드.

### 3.7 커뮤니티 (게시판)
- **게시글** `POST /api/posts`(multipart, 이미지≤5) · `GET /api/posts`(목록, 말머리·정렬 `LATEST|BEST`) · `GET /api/posts/{id}`(상세) · `POST /{id}/like`(추천 멱등).
  - 말머리 `PostCategory`: `RECOMMEND|FREE|FANART|QUESTION`.
- **댓글/대댓글** `POST /api/posts/{postId}/comments`(`parentId` 주면 대댓글, **1-depth 강제** — 대댓글에 대댓글이면 최상위 부모로 평탄화) · 조회 · 삭제(본인·ADMIN).
- **@멘션**: 게시글/댓글 본문에 `@닉네임` → 그 사용자에게 `POST_MENTIONED` 알림(닉네임 유일·제한문자셋이라 1명, 상한 10, 자기멘션 제외).
- **블라인드**: 신고 누적 또는 관리자 처리로 블라인드되면 공개 조회에서 숨김(작성자 본인 활동내역에는 플래그와 함께 보임).

### 3.8 알림 (인앱 알림함)
- **종류**(`NotificationType`): `EPISODE_PUBLISHED`(구독작 새 회차)·`INQUIRY_ANSWERED`(문의 답변)·`POST_COMMENT`(내 글 댓글)·`COMMENT_REPLY`(내 댓글 답글)·`FOLLOWED`(새 팔로워)·`POST_MENTIONED`(멘션).
- **조회·처리** `GET /api/me/notifications?type=`(종류 필터) · `/unread-count`(폴링 배지) · `/unread-summary`(종류별 집계 탭) · `PATCH /{id}/read`(**읽음 + 라우팅 정보 반환**) · `/read-all`.
- **라우팅**: 항목의 `(targetType, targetId)`로 클라가 해당 화면 이동. 푸시(FCM)는 추후 — 저장소·읽음·폴링은 외부 의존 0.

### 3.9 신고 / 문의
- **신고** `POST /api/reports` — 폴리모픽 대상(`ReportTargetType`: POST/COMMENT/USER/SERIES/EPISODE), 사유(`ReportReason`), 한 신고자가 한 대상 1회. **자동 블라인드**(접수 5건). 관리자: `GET /api/admin/reports`(필터)·`/{id}`(대상 내용 조회)·`/resolve`(기각/블라인드/삭제)·`/dismiss`.
- **문의** `POST /api/me/inquiries`(multipart, type·내용·이미지) · 내 문의 조회. 관리자: `/api/admin/inquiries`(필터)·답변(→ `INQUIRY_ANSWERED` 알림)·상태·삭제. `InquiryType`: ACCOUNT/PAYMENT/CONTENT/CREATOR/BUG/ETC.

### 3.10 회차 댓글·좋아요 (뷰어 소셜)
- 회차 댓글 `POST/GET/DELETE /api/series/{id}/episodes/{no}/comments`(평면, ≤1000자, 삭제는 본인·ADMIN). 좋아요는 3.4. (커뮤니티 게시글 댓글과 별개 도메인.)

### 3.11 운영자 (admin) — `@PreAuthorize("hasRole('ADMIN')")`
- 사용자 `GET /api/admin/users`(검색)·`PATCH /{id}/role`. 작가신청 `GET /api/admin/creator-requests`·`/approve`·`/reject`.
- 작품 `PATCH /api/admin/series/{id}/age-rating`·`/visibility`·`/adult-only`.
- 신고 `/api/admin/reports/**`, 커뮤니티 `/api/admin/posts/**`(검색·상세·블라인드), 문의 `/api/admin/inquiries/**`.

### 3.12 공통 / 인프라
- 헬스 `GET /api/health`. API문서 springdoc(Swagger). 에러 `ErrorResponse`+`ErrorCode`. 페이징 Page/Slice.
- 이미지 저장 `ObjectStorage`(로컬 `/files/**` ↔ S3, `app.storage.type` 전환). CORS(개발 `*`, 운영 `CORS_ALLOWED_ORIGINS`). 감사필드 `BaseEntity(createdAt/updatedAt)`.

---

## 4. 데이터 모델 요약

```
User (계정·역할·생년월일·닉네임 unique·avatar·bio)
  ├─< Series (author_id) ──< Episode ──< EpisodeImage        작가1:작품N:회차N(uq series,no):이미지N
  ├─< CreatorRequest        작가전환 신청(상태)
  ├─< UserConsent           법적 동의(종류·버전·시각)
  └─< Follow (follower_id, following_id) 비연관 Long·멱등

개인화·소셜(멱등 유니크): Subscription(user×series) · ReadLog(user×episode) · Bookmark(user×episode) · EpisodeLike(user×episode) · Comment(user×episode, 평면)

커뮤니티: Post(author_id·category·blinded) ──< PostImage(≤5) / PostComment(post_id·parent_id 1-depth·blinded) / PostLike(user×post 멱등)
신고/문의/알림(폴리모픽·비연관 Long): Report(target_type+target_id, uq reporter+target) · Inquiry(user·type·answer) + InquiryImage · Notification(recipient_id·type·target_type+target_id·read_at·dedup_key)
인증: RefreshToken(token unique·expires_at)
```
- 멱등 테이블은 (user_id, episode/series/post_id) 유니크 패턴 복제. 신고·알림은 **폴리모픽**(target_type+target_id, FK 안 검). 알림 dedup은 **(recipient_id, dedup_key)** 복합 유니크.
- Series 정책 플래그: `visible`·`adultOnly`(불변식 →AGE_19)·`release_policy`/`wait_free_days`(수익화 0단계).

### 4.2 Flyway 마이그레이션 (V1~V22)
| 버전 | 내용 | 버전 | 내용 |
|---|---|---|---|
| V1~V2 | users·refresh_tokens | V13 | inquiries(+images) |
| V3~V4 | series(+요일)·episodes(+images) | V14 | series 장르·태그 |
| V5 | users.birth_date(연령) | V15 | 계정·프로필·동의·작가전환 |
| V6 | subscriptions·read_logs | V16 | follows |
| V7~V8 | series.visible·adult_only | V17 | community(posts·images·likes·comments) |
| V9~V10 | episode_likes·comments | V18 | reports |
| V11~V12 | episodes.view_count·bookmarks | V19 | 블라인드 이력(blinded_by/at) |
| | | V20 | notifications(폴리모픽·dedup) |
| | | V21 | nickname unique(+중복 접미사 해소) |
| | | V22 | series 공개정책(release_policy·wait_free_days, backfill FREE_ALL) |

기동 시 Flyway 자동 적용.

---

## 5. 알려진 설계 결정 / 트레이드오프
1. **성인 이중 축**: `AGE_19`(열람차단) vs `adultOnly`(분류·정렬), 불변식으로 모순 차단.
2. **비공개·미발행·잠긴 회차는 숨김**: 비공개·미발행은 404(존재 은닉). **잠긴(기다리면무료) 회차는 200+locked**(존재 은닉 아닌 "지연된 접근" + freeAt 카운트다운 — ErrorResponse가 freeAt 못 실어 throw 대신 플래그).
3. **수익화 잠금 = compute-on-read**: 상태 저장·스케줄러 없이 `publishAt+N일`로 계산. 정책 전환 시 과거 회차 소급 안 잠김. `EpisodeAccessEvaluator`에 **미래 결제(엔타이틀먼트·멤버십) 주입점**만 마련(코인·테이블은 YAGNI). → [`roadmap-and-monetization.md`].
4. **알림 = 인앱 저장소(폴링)**: fan-out 인라인 동기(이벤트 인프라 폐기 — 테스트 결정성·단순성). dedup은 **수신자 단위**. 푸시(FCM)만 외부.
5. **활동내역 = 런타임 조합**: ActivityLog 테이블 없이 기존 도메인 조합. 멘션 조회는 POST_MENTIONED 알림 재사용.
6. **닉네임 유일·제한문자셋**: @멘션이 1명을 가리키도록 unique + `@Pattern`(V21 기존 중복 접미사 해소).
7. **콘텐츠 가드는 모든 소비 경로에 대칭**: 읽기(getDetail)뿐 아니라 읽음 마킹(markRead)도 잠금 평가(우회·신호오염 방지).
8. **조회수 lost update 허용**(읽고-증가-쓰기, 대략 지표) · **리프레시 토큰 회전** · **무상태 JWT(헤더, 쿠키 미사용)** · **이미지 포트-어댑터(로컬↔S3)**.

---
근거: `domain/{auth,user,series,episode,personalization,community,follow,notification,report,inquiry}`의 Controller·Service·Entity·enum + `db/migration/V1~V22`.
