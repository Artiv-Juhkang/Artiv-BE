# AppToon 기능 명세 (features.md)

AppToon 웹툰 플랫폼 백엔드 REST API가 제공하는 기능을 실제 소스코드(`src/main/java`, `src/main/resources/db/migration`) 기준으로 정리한 문서다. 비개발자도 "무엇을 할 수 있는가"를 이해할 수 있도록 쓰되, 각 기능의 규칙·가드(접근 제한)·예외 동작은 코드와 어긋나지 않게 기술한다.

---

## 1. 개요 — 이 플랫폼이 제공하는 것

AppToon은 웹툰을 **올리고(작가)·읽고(독자)·관리하는(운영자)** 일을 처리하는 서버다. 화면(UI)은 없고, 앱/웹 프론트엔드가 호출하는 HTTP API만 제공한다.

핵심으로 제공하는 것:

- **계정과 로그인**: 이메일·비밀번호로 가입/로그인하고, JWT 토큰으로 인증한다. 비밀번호는 BCrypt로 해시 저장한다. (`AuthService`)
- **작품(Series)과 회차(Episode)**: 작가는 작품을 만들고, 회차 이미지를 업로드하며, 예약 발행할 수 있다. 독자는 작품 목록·상세·뷰어(회차 이미지)를 본다.
- **개인화**: 구독, 읽음 기록, 이어보기 신호(UP), 북마크를 제공한다.
- **소셜**: 회차에 좋아요, 댓글을 단다.
- **연령 게이트**: 19금(`AGE_19`) 작품은 만 19세 이상만 열람한다. 생년월일(`birthDate`)로 판정한다.
- **운영자 기능**: 사용자 권한 변경, 작품의 연령등급·공개여부·성인전용 분류를 변경한다.
- **이미지 저장 추상화**: 로컬 디스크 또는 S3 호환 스토리지(MinIO/Cloudflare R2/AWS S3) 중 설정으로 전환한다. (`ObjectStorage`)

응답 규약:

- **성공**: 해당 DTO를 그대로 반환한다. 생성은 보통 `201 Created` + `IdResponse`(또는 회차의 `EpisodeNoResponse`), 삭제/해제는 `204 No Content`.
- **에러**: 공통 형식 `ErrorResponse { status, code, message, fieldErrors }`로 반환한다. (`GlobalExceptionHandler`, `ErrorCode`)
- **목록**: 페이지형은 `PageResponse { content, page, size, totalElements, totalPages, last }`, 무한스크롤형은 `SliceResponse { content, page, size, hasNext }`.

인증 없이 접근 가능한 경로(`SecurityConfig`): `/api/health`, `/api/auth/**`, 이미지 정적 서빙 `/files/**`, Swagger 문서(`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`). **그 외 모든 요청은 인증이 필요하다.** 즉 작품 목록·상세·뷰어도 로그인(액세스 토큰)이 있어야 호출된다.

---

## 2. 역할(Role)별로 할 수 있는 일

역할은 세 가지다(`Role`): **READER**, **CREATOR**, **ADMIN**. 가입하면 모두 `READER`로 시작하며(`AuthService.signup`), 권한 승격은 운영자만 한다.

상위 역할은 하위 역할의 일을 포함한다고 가정한다(아래 표는 "그 역할이어야만 할 수 있는 일"을 강조 표기).

| 기능 | READER | CREATOR | ADMIN |
| --- | :---: | :---: | :---: |
| 가입·로그인·토큰 갱신 | O | O | O |
| 내 정보 조회 (`GET /api/users/me`) | O | O | O |
| 작품 목록·상세 조회 | O | O | O |
| 회차 목록·뷰어 조회 | O | O | O |
| 구독·읽음·북마크·좋아요·댓글 | O | O | O |
| **작품 등록** (`POST /api/series`) | X | **O** | X(역할상 불가) |
| **회차 업로드** (`POST .../episodes`) | X | **O**(본인 작품만) | X(역할상 불가) |
| 비공개·미발행 콘텐츠 프리뷰 | X | 본인 작품만 | **O(전체)** |
| 댓글 삭제 | 본인 댓글만 | 본인 댓글만 | **O(전체)** |
| **사용자 역할 변경** (`PATCH /api/admin/...`) | X | X | **O** |
| **작품 연령등급·공개·성인전용 변경** | X | X | **O** |

규칙 메모:

- 작품 등록·회차 업로드는 `@PreAuthorize("hasRole('CREATOR')")`로 막혀 있다(`SeriesController`, `EpisodeController`). 따라서 ADMIN이라도 CREATOR 역할이 없으면 이 두 작업은 못 한다 — 권한은 단일 enum 값이라 누적되지 않는다.
- 회차 업로드는 CREATOR 통과 후에도 **그 작품의 작가 본인**인지 다시 검사한다(`EpisodeService.upload` → 아니면 `FORBIDDEN`).
- "프리뷰"란 비공개(`visible=false`) 작품이나 미발행(`SCHEDULED`/`DRAFT`) 회차를, 일반 독자에게는 존재하지 않는 것처럼 `404`로 숨기되 작가 본인·ADMIN에게는 보여주는 것을 말한다.

---

## 3. 도메인별 기능 상세

각 항목은 **무엇을 하는가 → 규칙/가드 → 응답** 순으로 적는다. 경로 변수 `{seriesId}`는 작품 ID, `{episodeNo}`는 작품 안에서의 회차 번호(1부터)다.

### 3.1 인증 / 계정 (auth, user)

- **회원가입** `POST /api/auth/signup` → `201` + `IdResponse`
  - 입력: `email`, `password`(8~64자), `nickname`(≤20자), `birthDate`(과거 날짜, 필수). (`SignupRequest`의 `@Email/@Size/@Past/@NotNull`)
  - 규칙: 이메일 중복이면 `DUPLICATE_EMAIL`(409). 비밀번호는 BCrypt 해시로 저장. 역할은 항상 `READER`.
  - `birthDate`는 19금 연령 게이트의 근거다. **필수**이며, 값이 없으면(레거시 데이터 등) 시스템은 보수적으로 미성년으로 취급한다(`User.isAdult`).
- **로그인** `POST /api/auth/login` → `TokenResponse { accessToken, refreshToken }`
  - 자격 증명이 틀리면 `INVALID_CREDENTIALS`(401). 성공 시 액세스 토큰(JWT)과 리프레시 토큰(불투명 랜덤 문자열)을 함께 발급.
- **토큰 갱신** `POST /api/auth/refresh` → `TokenResponse`
  - **회전(rotation)**: 보낸 리프레시 토큰을 검증한 뒤 **즉시 폐기**하고 새 토큰 한 쌍을 발급한다(`AuthService.refresh`). 같은 리프레시 토큰을 두 번 쓸 수 없다.
  - 만료된 토큰이면 폐기 후 `INVALID_TOKEN`(401). 저장에 없는 토큰도 `INVALID_TOKEN`.
- **인증 방식**: 무상태(stateless) JWT. 요청은 `Authorization: Bearer <accessToken>` 헤더로 인증하며, 필터(`JwtAuthenticationFilter`)가 토큰을 검증해 인증 주체(principal)에 `userId`(Long)를 넣는다. 쿠키·세션은 쓰지 않는다(CORS `allowCredentials=false`).
- **내 정보 조회** `GET /api/users/me` → `UserResponse`
  - 인증 주체의 `userId`로 본인 정보를 반환한다.

### 3.2 작품 (series)

- **작품 등록** `POST /api/series` → `201` + `IdResponse` *(CREATOR 전용)*
  - 입력: `title`, `description`, `ageRating`, `status`, `publishDays`(연재 요일 집합), `adultOnly`(선택, 기본 false).
  - 작가는 토큰의 `userId`로 결정된다(요청에 작가 ID를 받지 않는다).
  - 불변식: `adultOnly=true`면 `ageRating`은 반드시 `AGE_19`여야 한다. 어기면 `INVALID_INPUT`(400). (`Series.validateAdultConsistency`)
- **작품 목록** `GET /api/series` → `PageResponse<SeriesSummaryResponse>`
  - 필터(모두 선택): `day`(연재 요일), `ageRating`, `keyword`(제목 검색), `adultOnly`.
  - 정렬 `sort`(`SeriesSort`, 기본 `LATEST`):
    - `LATEST` — 최신 등록순(id 내림차순).
    - `ADULT_FIRST` — 성인 전용 작품을 먼저(adultOnly 내림차순, 그다음 id).
    - (참고: 연령등급은 문자열로 저장돼 사전순 정렬이 연령 강도와 어긋나므로 정렬 키로 노출하지 않는다 — `SeriesSort` 주석.)
  - 페이징 기본 크기 20.
- **내 작품 목록** `GET /api/series/mine` → `List<SeriesSummaryResponse>`
  - 인증된 작가 본인의 작품을 모두 반환(페이징 없음).
- **작품 상세** `GET /api/series/{id}` → `SeriesDetailResponse`
  - 기본 정보에 더해 **발행 회차 수(`episodeCount`)**, **최신 발행 회차 번호(`latestEpisodeNo`)**, **내 구독 여부(`isSubscribed`)**를 한 번에 담아 프론트가 상세 화면을 1요청으로 그리게 한다.
  - 가드: 비공개 작품(`visible=false`)은 작가 본인·ADMIN만 볼 수 있고, 그 외에는 **존재를 숨겨 `404`(`ENTITY_NOT_FOUND`)**로 응답한다(권한 거부가 아니라 "없음"으로 위장).
  - `episodeCount`/`latestEpisodeNo`는 **PUBLISHED 회차만** 집계한다.

`Series` 엔티티 핵심 필드: `title`, `description`, `author`(User), `ageRating`(AgeRating), `status`(SeriesStatus), `publishDays`(요일 집합, 별도 테이블), `visible`(공개 여부, 기본 true), `adultOnly`(성인 전용, 기본 false).

관련 enum:
- `AgeRating`: `ALL`, `AGE_12`, `AGE_15`, `AGE_19`
- `SeriesStatus`: `ONGOING`, `COMPLETED`, `HIATUS`
- `SeriesSort`: `LATEST`, `ADULT_FIRST`

### 3.3 회차 (episode)

- **회차 업로드** `POST /api/series/{seriesId}/episodes` → `201` + `EpisodeNoResponse` *(CREATOR + 작가 본인)*
  - 입력(멀티파트): `title`, `publishAt`(선택, 예약 발행 시각), `images`(이미지 파일 목록).
  - 작가 본인이 아니면 `FORBIDDEN`(403).
  - 회차 번호는 자동: 해당 작품의 현재 최대 회차 번호 + 1.
  - **발행 상태 결정**(`EpisodeService.upload`):
    - `publishAt`이 비었거나 현재 시각 이전이면 즉시 `PUBLISHED`(발행 시각은 현재).
    - `publishAt`이 미래면 `SCHEDULED`(예약).
  - **이미지 처리**(`ImageStorageService`): JPEG/PNG만 허용(`INVALID_IMAGE`), 폭이 800px를 넘으면 800px로 리사이즈(Thumbnailator), 저장 경로 규약 `{seriesId}/{episodeNo}/{order}.{ext}`. 이미지가 없으면 `INVALID_IMAGE`. 저장 실패 시 `STORAGE_FAILED`(500). 저장 후 메타데이터(`path`, `width`, `height`, `sortOrder`)를 기록한다.
- **예약 발행(자동)**: 스케줄러(`EpisodePublisher`, `@Scheduled(fixedDelay=app.episode.publish-poll-ms`, 기본 60000ms)가 주기적으로 돌며, 발행 시각이 지난 `SCHEDULED` 회차를 `PUBLISHED`로 전환한다(`publishDueEpisodes`).
- **회차 목록** `GET /api/series/{seriesId}/episodes` → `SliceResponse<EpisodeSummaryResponse>`
  - 회차 번호 오름차순, 슬라이스(무한 스크롤) 기본 크기 20. `PUBLISHED` 회차만 노출.
  - 가드: 비공개 작품은 작가/ADMIN만 프리뷰(그 외 `404`). 19금 작품은 연령 검사를 통과해야 함(아래 연령 게이트).
- **회차 상세 / 뷰어** `GET /api/series/{seriesId}/episodes/{episodeNo}` → `EpisodeDetailResponse`
  - 응답: 이미지 목록(정렬·URL 포함), `viewCount`, `likeCount`, `liked`(내가 좋아요 눌렀는지), 회차 상태·발행시각.
  - **조회수**: 상세를 열 때마다 1 증가한다(`Episode.increaseViewCount`, 더티 체킹으로 트랜잭션 종료 시 UPDATE).
  - 가드(겹겹):
    1. 비공개 작품은 작가/ADMIN만(그 외 `404`).
    2. 19금 작품은 연령 검사 통과 필요.
    3. 미발행 회차(`SCHEDULED`/`DRAFT`)는 작가/ADMIN만 프리뷰(그 외 `404`).
  - 이미지 URL은 저장소 설정에 따라 로컬은 `/files/{key}`, S3는 `publicBase/{key}`로 만들어진다(`urlFor`).
- **좋아요** `POST /{episodeNo}/like` → `201` / **좋아요 취소** `DELETE /{episodeNo}/like` → `204`
  - 사용자-회차 단위로 **멱등**(유니크 제약 `uq_episode_like_user_episode`). 이미 좋아요면 그대로 두고, 취소는 없으면 조용히 통과.

`Episode` 핵심 필드: `series`, `episodeNo`, `title`, `status`(EpisodeStatus), `publishAt`, `viewCount`(기본 0).
`EpisodeImage`: `episode`, `sortOrder`, `path`, `width`, `height`.
`EpisodeStatus`: `DRAFT`, `SCHEDULED`, `PUBLISHED`.

### 3.4 연령 게이트 (19금 열람)

- 판정 기준(`User.isAdult(today)`): `birthDate`가 있고, 그 날짜가 (오늘 − 19년) 이전(같거나 이전)이면 성인. **`birthDate`가 없으면 미성년으로 취급**(보수적).
- 적용 지점(`EpisodeService.verifyAgeAccess`): 작품의 `ageRating`이 `AGE_19`인 경우에만 검사한다. 미성년이면 `ADULT_ONLY`(403, "성인만 열람할 수 있습니다.").
- 적용 범위: 회차 **목록**과 **상세/뷰어**. (작품 상세 `SeriesDetailResponse`에는 19금 연령 거부가 걸려 있지 않다 — 작품 정보 카드는 보이되, 실제 회차 이미지에서 막는 설계.)
- `adultOnly`(성인 전용 분류)는 위 `AGE_19` 연령 게이트와 별개의 메타 플래그다. 목록 필터·정렬과 불변식(`adultOnly` → `ageRating=AGE_19`)에 쓰인다. 즉 `adultOnly=true`인 작품은 반드시 `AGE_19`이므로 결과적으로 연령 게이트도 함께 걸린다.

### 3.5 개인화 (personalization)

> ✅ **가드**: 읽음·북마크·좋아요는 회차 상세와 동일하게 비공개(작가 본인만)·19금(만 19세 이상만) 가드를 적용한다(`SeriesAccessChecker.verifyInteractable`). `episodeNo`만 알아도 비공개·성인 작품엔 404/`ADULT_ONLY`로 차단된다. (구독은 작품 단위라 별도 — 현재 미가드.)

- **구독** `POST /api/series/{seriesId}/subscription` → `201` / **구독 취소** `DELETE` → `204`
  - 사용자-작품 멱등(유니크 `uq_subscription_user_series`). 없는 작품 구독 시 `ENTITY_NOT_FOUND`.
- **읽음 기록** `POST /api/series/{seriesId}/episodes/{episodeNo}/read` → `201`
  - 사용자-회차 멱등(유니크 `uq_readlog_user_episode`). 이어보기/UP 계산의 근거.
- **내 구독 목록 (이어보기·UP)** `GET /api/me/subscriptions` → `List<SubscriptionResponse>`
  - 각 항목: `seriesId`, `title`, `latestEpisodeNo`(최신 발행 회차), `lastReadEpisodeNo`(내가 마지막으로 읽은 회차), `up`.
  - **UP 신호**: `latestEpisodeNo > lastReadEpisodeNo`이면 `up=true` = "안 본 새 회차가 있다"(이어보기 신호).
  - 성능: 작품별 최신 회차·마지막 읽은 회차를 **배치 집계 쿼리**로 모아 N+1을 피한다(`findMaxEpisodeNoBySeriesIds`, `findMaxReadEpisodeNo`).
- **북마크** `POST /api/series/{seriesId}/episodes/{episodeNo}/bookmark` → `201` / **해제** `DELETE` → `204`
  - 사용자-회차 멱등(유니크 `uq_bookmark_user_episode`).
- **내 북마크 목록** `GET /api/me/bookmarks` → `List<BookmarkResponse>`

엔티티: `Subscription`(user·series), `ReadLog`(user·episode), `Bookmark`(user·episode). 모두 멱등을 보장하는 유니크 제약을 둔다.

### 3.6 소셜 (comment, 좋아요)

- **댓글 작성** `POST /api/series/{seriesId}/episodes/{episodeNo}/comments` → `201` + `IdResponse`
  - 입력: `content`(필수, ≤1000자). 회차가 없으면 `ENTITY_NOT_FOUND`.
- **댓글 목록** `GET .../comments` → `PageResponse<CommentResponse>` (페이징 기본 20).
- **댓글 삭제** `DELETE .../comments/{commentId}` → `204`
  - **작성자 본인 또는 ADMIN**만 삭제 가능(`Comment.isOwnedBy` + `AuthSupport.isAdmin`). 둘 다 아니면 `FORBIDDEN`.
- 좋아요는 회차(episode) 기능이라 3.3에 함께 기술했다.

`Comment` 엔티티(평면 구조, 대댓글 없음): `user`, `episode`, `content`(≤1000).

### 3.7 운영자 (admin)

모든 운영자 API는 컨트롤러 레벨 `@PreAuthorize("hasRole('ADMIN')")`로 보호된다(`AdminController`).

- **사용자 역할 변경** `PATCH /api/admin/users/{userId}/role` → `UserResponse`
  - 입력 `role`(READER/CREATOR/ADMIN). 일반 독자를 작가(CREATOR)로 승격하는 경로가 여기다.
- **작품 연령등급 변경** `PATCH /api/admin/series/{seriesId}/age-rating` → `SeriesResponse`
  - 입력 `ageRating`. 변경 시에도 성인 불변식(`changeAgeRating` → `validateAdultConsistency`)을 검사한다.
- **작품 공개/비공개 변경** `PATCH /api/admin/series/{seriesId}/visibility` → `SeriesResponse`
  - 입력 `visible`(true/false). 비공개로 바꾸면 일반 독자에게는 작품·회차가 `404`로 숨겨진다.
- **작품 성인전용 분류 변경** `PATCH /api/admin/series/{seriesId}/adult-only` → `SeriesResponse`
  - 입력 `adultOnly`. 불변식상 `adultOnly=true`는 `ageRating=AGE_19`인 작품에만 적용 가능(아니면 `INVALID_INPUT`).

운영자 도메인은 별도 엔티티 없이 User/Series 도메인 엔티티를 권한 게이트 뒤에서 변경한다.

### 3.8 공통 / 인프라 (global)

- **헬스 체크** `GET /api/health` → `{"status":"ok"}` (인증 불필요).
- **API 문서**: springdoc-openapi(3.0.3, Spring Boot 4.1 호환). Swagger UI는 인증 없이 접근 가능.
- **에러 규약**: `ErrorResponse { status, code, message, fieldErrors }` + `ErrorCode`(예: `INVALID_INPUT` 400, `ENTITY_NOT_FOUND` 404, `DUPLICATE_EMAIL` 409, `INVALID_CREDENTIALS`/`INVALID_TOKEN` 401, `FORBIDDEN`/`ADULT_ONLY` 403, `INVALID_IMAGE` 400, `STORAGE_FAILED`/`INTERNAL_ERROR` 500).
- **페이징**: `PageResponse`(전체 개수 포함), `SliceResponse`(다음 페이지 존재 여부만 — 무한 스크롤).
- **이미지 저장 추상화** `ObjectStorage`(port):
  - `LocalObjectStorage` — 로컬 디스크 저장 + `/files/**` 정적 서빙. `app.storage.type`이 없거나 `local`일 때 활성.
  - `S3ObjectStorage` — AWS SDK v2 기반, MinIO/Cloudflare R2/AWS S3 호환. `app.storage.type=s3`(환경변수 `STORAGE_TYPE`)로 전환.
  - 앱 코드는 `put`/`urlFor` 두 메서드만 알면 되므로 저장 백엔드 교체가 설정 한 줄로 끝난다(포트-어댑터 패턴).
- **CORS**: 허용 origin은 `app.cors.allowed-origins`(개발 기본 전체 허용, 운영은 좁힘). 메서드 GET/POST/PATCH/PUT/DELETE/OPTIONS 허용, 쿠키 미사용.
- **감사 필드**: `BaseEntity`가 `createdAt`/`updatedAt`을 Auditing으로 채운다.

---

## 4. 데이터 모델 요약

### 4.1 엔티티 관계

```
User (계정·역할·생년월일)
  └─< Series (author_id)          작가 1 : 작품 N
        └─< Episode (series_id)   작품 1 : 회차 N   [유니크 (series_id, episode_no)]
              └─< EpisodeImage    회차 1 : 이미지 N (sort_order로 정렬)

개인화·소셜 (모두 user × episode 또는 user × series, 멱등 유니크):
  Subscription   user × series    uq_subscription_user_series
  ReadLog        user × episode   uq_readlog_user_episode
  Bookmark       user × episode   uq_bookmark_user_episode
  EpisodeLike    user × episode   uq_episode_like_user_episode
  Comment        user × episode   (평면, 멱등 아님 — 한 회차에 여러 댓글)

인증:
  RefreshToken   token(unique) · user_id · expires_at
```

- 작품의 연재 요일(`publishDays`)은 `series_publish_days` 별도 테이블(요일 enum 컬렉션)로 저장된다.
- `Series` 핵심 플래그: `visible`(공개), `adultOnly`(성인 전용). 불변식 `adultOnly=true → ageRating=AGE_19`.
- 멱등성을 보장하는 4개 테이블(Subscription/ReadLog/Bookmark/EpisodeLike)은 모두 동일한 (user_id, episode_id 또는 series_id) 유니크 패턴을 복제해 일관성을 유지한다.

### 4.2 Flyway 마이그레이션 (V1~V12)

| 버전 | 내용 |
| --- | --- |
| V1 | `users` 생성(email unique·password·nickname·role) |
| V2 | `refresh_tokens` 생성(token unique·user_id·expires_at) + user_id 인덱스 |
| V3 | `series` 생성(author_id FK·age_rating·status) + `series_publish_days`(요일 컬렉션) |
| V4 | `episodes`(uq_episode_series_no) + `episode_images`(sort_order·path·width·height) |
| V5 | `users.birth_date` 추가 — 19금 연령 게이트용(레거시 NULL = 미성년 취급) |
| V6 | `subscriptions`(uq_subscription_user_series) + `read_logs`(uq_readlog_user_episode) |
| V7 | `series.visible`(boolean, default true) — 공개/비공개 제어 |
| V8 | `series.adult_only`(boolean, default false) — 성인 전용 분류 |
| V9 | `episode_likes`(uq_episode_like_user_episode) |
| V10 | `comments`(평면, content ≤ 1000) |
| V11 | `episodes.view_count`(bigint, default 0) — 상세 조회 시 증가 |
| V12 | `bookmarks`(uq_bookmark_user_episode) |

마이그레이션은 애플리케이션 기동 시 Flyway가 자동 적용한다.

---

## 5. 알려진 설계 결정 / 트레이드오프

실제 코드와 그래프 분석에서 확인된, 의도된 결정과 그 한계다.

1. **성인 분류의 이중 축: `ageRating=AGE_19` vs `adultOnly`**
   - 연령등급(`AGE_19`)은 **열람 차단**의 근거이고, `adultOnly`는 **목록 분류·정렬**용 메타 플래그다. 둘을 분리해 "19금이지만 일반 목록에 노출", "성인관 우선 노출" 같은 정책을 구분한다.
   - 안전을 위해 불변식 `adultOnly=true → ageRating=AGE_19`를 엔티티에서 강제한다(`Series.validateAdultConsistency`). 생성·연령등급 변경·성인분류 변경 모든 경로에서 재검사하므로 모순 상태가 저장될 수 없다.
   - 트레이드오프: `ageRating`은 STRING으로 저장돼 사전순 정렬이 연령 강도와 어긋난다. 그래서 정렬 키로는 boolean인 `adultOnly`만 노출하고 연령등급 정렬은 제공하지 않는다(`SeriesSort` 주석).

2. **비공개·미발행은 403이 아니라 404로 숨긴다**
   - 권한이 없는 사용자에게 "있지만 못 본다(403)"가 아니라 "없다(404)"로 응답해, 비공개 작품/미발행 회차의 **존재 자체를 노출하지 않는다**. 작가 본인·ADMIN만 프리뷰한다(`SeriesService.getDetail`, `EpisodeService.getDetail`/`getEpisodes`).

3. **이미지 저장의 포트-어댑터 추상화**
   - `ObjectStorage`(put/urlFor) 인터페이스 뒤에 로컬·S3 두 어댑터를 두고 `app.storage.type` 설정으로 전환한다. 앱 로직(`ImageStorageService`)은 저장 위치를 모른다. 로컬은 `/files/{key}` 정적 서빙, S3는 `publicBase/{key}` URL.
   - 트레이드오프: 로컬 모드는 단일 서버 디스크에 의존하므로 다중 인스턴스 운영에는 부적합 — 운영은 S3 호환을 전제한다.

4. **조회수의 lost update 가능성**
   - 회차 상세 조회마다 엔티티의 `viewCount`를 메모리에서 +1 하고 더티 체킹으로 UPDATE 한다(`Episode.increaseViewCount`). 원자적 `UPDATE ... SET view_count = view_count + 1`이 아니라 **읽고-증가-쓰기** 방식이라, 동시 조회가 몰리면 일부 증가분이 유실(lost update)될 수 있다.
   - 학습 프로젝트 범위에서 "대략적인 인기 지표"로 충분하다는 판단의 의도적 단순화다. 정확한 카운트가 필요하면 원자적 UPDATE나 별도 집계로 교체해야 한다.

5. **리프레시 토큰 회전(rotation)**
   - 리프레시 토큰은 1회용이다. 사용 즉시 폐기하고 새 토큰을 발급하므로, 탈취된 토큰의 재사용 창을 좁힌다. 액세스 토큰은 무상태 JWT라 서버가 즉시 무효화할 수단이 없는 점은 회전의 짧은 수명으로 보완한다.

6. **패키지 간 의도된 순환 의존(Series 애그리거트 주변)**
   - `series ↔ episode`, `series ↔ personalization`가 양방향으로 import한다(예: `SeriesService`가 `EpisodeRepository`·`SubscriptionRepository`를 끌어와 상세의 `episodeCount`·`isSubscribed`를 채운다). `personalization → episode`는 단방향이라 순환이 아니다.
   - "에피소드는 작품에 속한다"는 본질적 부모-자식 관계라 결합 자체는 정당하지만, 읽기 측 집계(개수·구독 여부·이어보기)가 **리포지토리를 가로질러 도달**하는 형태다. 결합이 한 곳(Series/Episode 부모-자식 경계)에 집중돼 있어 통제 가능하며, 규모가 커지면 읽기 전용 쿼리/리드모델 경계를 도입할 후보로 식별된다.
   - 참고: 자동 그래프 리포트는 "Import Cycles: None"이라고 표기하지만, 이는 탐지기가 패키지가 아닌 심볼 단위로 동작하기 때문이며 패키지 수준 순환은 소스 import로 직접 확인된다(설계 메모로 기록).

7. **읽기 측 결합이 가장 두꺼운 곳 = `episode ↔ series` 경계**
   - 두 도메인 사이가 시스템에서 가장 조밀한 결합 지점이다. 더불어 `EpisodeService`가 작품 도메인의 정책(공개 가드·연령 가드)을 다시 구현(`verifyVisibleAccess`/`verifyAgeAccess`)하는 부분이 있어, 작품 정책 로직이 회차 도메인으로 일부 번진다. 현재 규모에선 명료성·1요청 응답 이득이 더 크다는 판단이다.

8. **무상태·헤더 기반 인증, CORS는 쿠키 미사용**
   - JWT를 `Authorization` 헤더로 전달하고 세션을 만들지 않는다(`SessionCreationPolicy.STATELESS`, CSRF 비활성, `allowCredentials=false`). 프론트는 토큰을 직접 보관·전송해야 한다.

---

문서 작성 근거: `domain/{auth,user,series,episode,personalization,comment,admin}`의 Controller·Service·Entity·enum, `global/{config,storage,exception,dto}`, `src/main/resources/db/migration/V1~V12`를 Read/Grep으로 확인해 작성했다.
