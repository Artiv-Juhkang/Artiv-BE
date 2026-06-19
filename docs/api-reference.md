# AppToon API 레퍼런스

AppToon 백엔드 REST API의 **전수 레퍼런스**. 모든 엔드포인트·요청/응답 DTO·enum을 실제 컨트롤러/DTO 소스와 1:1로 정리했다.

> 서버 실행·인증 토큰 발급·RN 연동 등 시작 가이드는 중복하지 않는다. → **[프론트엔드 협업 가이드](./frontend-guide.md)** 의
> [1. 서버 실행](./frontend-guide.md#1-서버-실행-quick-start) · [2. 인증(JWT)](./frontend-guide.md#2-인증-jwt) 참고.
> 배포는 **[배포 가이드](./deploy-guide.md)**.

목차
1. [공통 규약](#1-공통-규약)
2. [엔드포인트 전수](#2-엔드포인트-전수)
3. [응답 DTO 필드 사전](#3-응답-dto-필드-사전)
4. [OpenAPI / Swagger 활용](#4-openapi--swagger-활용)

---

## 1. 공통 규약

### 1.1 Base URL & 인증

| 항목 | 값 |
|---|---|
| Base URL (개발) | `http://localhost:8080` |
| 인증 방식 | JWT 무상태(stateless). 쿠키 미사용 |
| 인증 헤더 | `Authorization: Bearer <accessToken>` |
| 토큰 발급 | `POST /api/auth/login`(또는 `/refresh`)의 `accessToken` |
| CORS | `allowCredentials=false` (쿠키 X), 메서드 GET/POST/PATCH/PUT/DELETE/OPTIONS, origin은 `app.cors.allowed-origins`(개발 기본 `*`) |

비로그인(permitAll) 허용 경로는 다음뿐이고, **그 외 모든 요청은 인증 필요**(`anyRequest().authenticated()`):

- `GET /api/health`
- `POST /api/auth/**` (signup / login / refresh)
- `/files/**` (로컬 스토리지 정적 이미지 서빙)
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`

> 근거: `SecurityConfig.java:55-62`. **주의** — 작품/회차 상세·목록, 댓글 목록 조회도 모두 인증 필요다(비공개·연령 가드가 viewer 식별에 의존). 비로그인 상태에서 작품 열람 화면을 띄우려면 로그인이 선행되어야 한다.

### 1.2 에러 응답 (모든 에러 공통)

모든 에러는 `ErrorResponse` 단일 포맷. `code`는 `ErrorCode` enum 이름이다(근거: `ErrorResponse.java`, `ErrorCode.java`, `GlobalExceptionHandler.java`).

```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "fieldErrors": [
    { "field": "email", "reason": "올바른 형식의 이메일 주소여야 합니다" }
  ]
}
```

- `fieldErrors`는 `@Valid` 본문 검증 실패(`MethodArgumentNotValidException`) 시에만 채워지고, 그 외에는 빈 배열 `[]`.

| code | HTTP | 발생 상황 |
|---|---|---|
| `INVALID_INPUT` | 400 | `@Valid` 검증 실패(이때만 `fieldErrors`), 깨진 JSON 바디, 파라미터 타입 불일치 |
| `INVALID_IMAGE` | 400 | 회차 업로드 이미지 파일 검증 실패 |
| `INVALID_CREDENTIALS` | 401 | 로그인 이메일/비밀번호 불일치 |
| `INVALID_TOKEN` | 401 | 토큰(access/refresh) 유효하지 않음 |
| `UNAUTHORIZED` | 401 | 인증 필요(토큰 없음) — 인증 엔트리포인트 |
| `FORBIDDEN` | 403 | 권한 없음(CREATOR/ADMIN 미보유, 작가 본인 아님, 타인 댓글 삭제 등) |
| `ADULT_ONLY` | 403 | AGE_19 작품을 만 19세 미만이 열람 시도 |
| `ENTITY_NOT_FOUND` | 404 | 대상 없음 + 비공개/미발행 리소스 은닉(존재 자체를 숨김) |
| `DUPLICATE_EMAIL` | 409 | 가입 시 이메일 중복 |
| `STORAGE_FAILED` | 500 | 파일 저장 실패 |
| `INTERNAL_ERROR` | 500 | 처리되지 않은 서버 오류 |

> **404 은닉 패턴**: `visible=false`(비공개) 작품과 미발행(`SCHEDULED`/`DRAFT`) 회차는 작가 본인·ADMIN 외에는 403이 아니라 **404(`ENTITY_NOT_FOUND`)** 로 존재 자체를 숨긴다. 근거: `SeriesService`, `EpisodeService`.

### 1.3 페이징 — Page vs Slice

페이징 규약은 두 종류다. 쿼리 파라미터는 공통(`?page=0&size=20`), **page는 0-base**, 기본 `size=20`(`@PageableDefault`).

**`PageResponse<T>`** — 일반 목록(전체 개수 포함). 근거: `PageResponse.java`.

```json
{ "content": [ ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7, "last": false }
```

**`SliceResponse<T>`** — 무한스크롤(전체 개수 없음, 다음 페이지 존재 여부만). 회차 목록 전용. 근거: `SliceResponse.java`.

```json
{ "content": [ ], "page": 0, "size": 20, "hasNext": true }
```

| 응답 형태 | 사용 엔드포인트 |
|---|---|
| `PageResponse` | `GET /api/series`(작품 목록), 댓글 목록 |
| `SliceResponse` | `GET /api/series/{seriesId}/episodes`(회차 목록) |
| **래퍼 없는 `List`(배열 그대로)** | `GET /api/series/mine`, `GET /api/me/subscriptions`, `GET /api/me/bookmarks` |

### 1.4 이미지 URL

`EpisodeImageResponse.url`은 스토리지 타입(`app.storage.type`)에 따라 달라진다.

| 타입 | url 형태 | 비고 |
|---|---|---|
| `local`(기본) | `/files/{key}` (상대경로) | 백엔드가 `/files/**` 정적 서빙(permitAll). 프론트는 API origin과 합쳐야 함: `http://localhost:8080/files/{key}` |
| `s3` | `{app.storage.s3.public-base-url}/{key}` (절대 URL) | MinIO/R2/AWS S3 호환 |

> 근거: `LocalObjectStorage.java:40-42`, `S3ObjectStorage.java:38-41`.

### 1.5 날짜 / 시각

| 필드 | 타입 | 형식 | 예 |
|---|---|---|---|
| `publishAt`, `createdAt` 등 | `Instant` | ISO-8601 UTC (Jackson 기본) | `2026-06-17T12:00:00Z` |
| `birthDate` (가입 요청) | `LocalDate` | `yyyy-MM-dd` | `2000-03-15` |

- 연령 판정: 뷰어의 `birthDate` 기준 만 나이 계산. AGE_19 작품은 만 19세 미만이면 `ADULT_ONLY`(403).

### 1.6 enum 값 사전

| enum | 값 | 비고 |
|---|---|---|
| `Role` | `READER` · `CREATOR` · `ADMIN` | 사용자 역할 |
| `AgeRating` | `ALL` · `AGE_12` · `AGE_15` · `AGE_19` | 연령등급. `AGE_19`만 성인 게이트 |
| `SeriesStatus` | `ONGOING` · `COMPLETED` · `HIATUS` | 작품 연재 상태 |
| `EpisodeStatus` | `DRAFT` · `SCHEDULED` · `PUBLISHED` | 회차 상태 |
| `SeriesSort` | `LATEST`(기본, id DESC) · `ADULT_FIRST`(adultOnly DESC, id DESC) | 작품 목록 정렬 |
| `DayOfWeek` | `MONDAY` … `SUNDAY` | `java.time` 표준(대문자) |

> 근거: `Role.java`, `AgeRating.java`, `SeriesStatus.java`, `EpisodeStatus.java`, `SeriesSort.java`.

권한 표기: 🔓 비로그인 · 🔒 인증 · CREATOR(`@PreAuthorize hasRole('CREATOR')`) · 🛡 ADMIN(`@PreAuthorize hasRole('ADMIN')`).

---

## 2. 엔드포인트 전수

총 8개 컨트롤러 · 23개 엔드포인트.

### 2.1 인증 (Auth) — `/api/auth/**`

근거: `AuthController.java`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| POST | `/api/auth/signup` | 🔓 | `SignupRequest` (body) | `IdResponse` | **201**. 이메일 중복 → `DUPLICATE_EMAIL`(409) |
| POST | `/api/auth/login` | 🔓 | `LoginRequest` (body) | `TokenResponse` | 200. 불일치 → `INVALID_CREDENTIALS`(401) |
| POST | `/api/auth/refresh` | 🔓 | `RefreshRequest` (body) | `TokenResponse` | 200. refresh 회전(새 access+refresh 발급). 무효 → `INVALID_TOKEN`(401) |

**요청 DTO**
- `SignupRequest`: `email`(`@NotBlank @Email`), `password`(`@NotBlank`, 8~64자), `nickname`(`@NotBlank`, ≤20자), `birthDate`(`@NotNull @Past`, `LocalDate yyyy-MM-dd`). 근거: `SignupRequest.java`.
- `LoginRequest`: `email`(`@NotBlank`), `password`(`@NotBlank`).
- `RefreshRequest`: `refreshToken`(`@NotBlank`).

### 2.2 사용자 (User) — `/api/users`

근거: `UserController.java`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| GET | `/api/users/me` | 🔒 | 없음 (principal=userId) | `UserResponse` | `birthDate`는 민감정보라 의도적 미노출 |

### 2.3 작품 (Series) — `/api/series`

근거: `SeriesController.java`, `SeriesService.java`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| POST | `/api/series` | CREATOR | `SeriesCreateRequest` (body) | `IdResponse` | **201**. CREATOR 아니면 403(`FORBIDDEN`) |
| GET | `/api/series` | 🔒 | 쿼리(아래) | `PageResponse<SeriesSummaryResponse>` | 모든 필터 선택적 |
| GET | `/api/series/mine` | 🔒 | 없음 (principal) | `List<SeriesSummaryResponse>` | 내가 작가인 작품 전체(비공개 포함). **배열 그대로** |
| GET | `/api/series/{id}` | 🔒 | path `id` | `SeriesDetailResponse` | 비공개는 작가·ADMIN만, 그 외 404 |

**`GET /api/series` 쿼리 파라미터** (전부 선택적)

| param | 타입 | 기본 | 설명 |
|---|---|---|---|
| `day` | `DayOfWeek` | — | 연재 요일 필터 |
| `ageRating` | `AgeRating` | — | 연령등급 필터 |
| `keyword` | `String` | — | 제목 검색 |
| `adultOnly` | `Boolean` | — | 성인전용 필터 |
| `sort` | `SeriesSort` | `LATEST` | 정렬 |
| `page` / `size` | int | `0` / `20` | 페이징 |

**`SeriesCreateRequest`**: `title`(`@NotBlank`), `description`(nullable), `ageRating`(`@NotNull`), `status`(`@NotNull SeriesStatus`), `publishDays`(`@NotEmpty Set<DayOfWeek>`, 예 `["MONDAY","THURSDAY"]`), `adultOnly`(`Boolean`, null이면 false). 근거: `SeriesCreateRequest.java`.

> `SeriesDetailResponse`의 `episodeCount`·`latestEpisodeNo`는 **PUBLISHED 회차 기준**, `isSubscribed`는 내 구독 여부.

### 2.4 회차 (Episode) — `/api/series/{seriesId}/episodes`

근거: `EpisodeController.java`, `EpisodeService.java`. 회차는 DB id가 아닌 **작품별 채번 `episodeNo`(int, max+1)** 로 식별한다.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| POST | `/api/series/{seriesId}/episodes` | CREATOR | 멀티파트(아래) | `EpisodeNoResponse` | **201**. 작가 본인 아니면 403. 이미지 검증 실패 → `INVALID_IMAGE`(400) |
| GET | `/api/series/{seriesId}/episodes` | 🔒 | path + `page`/`size` | `SliceResponse<EpisodeSummaryResponse>` | 무한스크롤. PUBLISHED만 `episodeNo` ASC. 이미지 미포함 |
| GET | `/api/series/{seriesId}/episodes/{episodeNo}` | 🔒 | path `seriesId`,`episodeNo`(int) | `EpisodeDetailResponse` | 조회 시 `viewCount +1`. 이미지 포함. `liked`/`likeCount` 포함 |
| POST | `/api/series/{seriesId}/episodes/{episodeNo}/like` | 🔒 | path | 없음(void) | **201**. 멱등(이미 좋아요면 그대로). 회차 없으면 404 |
| DELETE | `/api/series/{seriesId}/episodes/{episodeNo}/like` | 🔒 | path | 없음(void) | **204**. 좋아요 취소(없어도 무에러) |

**회차 업로드 멀티파트** (`multipart/form-data`)

| part/param | 타입 | 위치 | 설명 |
|---|---|---|---|
| `title` | String | `@RequestParam` | 회차 제목 |
| `publishAt` | `Instant` | `@RequestParam` (optional) | 미래면 `SCHEDULED`(예약발행 `@Scheduled`가 발행), 없거나 과거면 즉시 `PUBLISHED` |
| `images` | `List<MultipartFile>` | `@RequestPart("images")` | 회차 이미지들(순서대로) |

> 발행/연령 가드: 비공개 작품은 작가·ADMIN만(그 외 404). 미발행 회차도 작가·ADMIN만 프리뷰(그 외 404). AGE_19 작품은 미성년 `ADULT_ONLY`(403). `viewCount`는 상세 조회 시에만 증가(목록은 증가 안 함).

### 2.5 개인화 / 소셜 (Personalization) — 경로 혼재

> ✅ 읽음·북마크·좋아요는 회차 상세와 동일하게 비공개(작가만)·19금(성인만) 가드를 적용한다(`SeriesAccessChecker`) — 비인가 상호작용은 404/`ADULT_ONLY`. (구독은 작품 단위라 별도.)

근거: `PersonalizationController.java`, `PersonalizationService.java`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| POST | `/api/series/{seriesId}/subscription` | 🔒 | path | 없음(void) | **201**. 구독. 멱등. 작품 없으면 404 |
| DELETE | `/api/series/{seriesId}/subscription` | 🔒 | path | 없음(void) | **204**. 구독 취소(없어도 무에러) |
| POST | `/api/series/{seriesId}/episodes/{episodeNo}/read` | 🔒 | path | 없음(void) | **201**. 읽음 기록. 멱등. UP 판정 근거 |
| GET | `/api/me/subscriptions` | 🔒 | 없음 | `List<SubscriptionResponse>` | **배열**. `up = latestEpisodeNo > lastReadEpisodeNo` |
| POST | `/api/series/{seriesId}/episodes/{episodeNo}/bookmark` | 🔒 | path | 없음(void) | **201**. 북마크. 멱등 |
| DELETE | `/api/series/{seriesId}/episodes/{episodeNo}/bookmark` | 🔒 | path | 없음(void) | **204**. 북마크 취소(없어도 무에러) |
| GET | `/api/me/bookmarks` | 🔒 | 없음 | `List<BookmarkResponse>` | **배열**. `createdAt` 포함 |

> 멱등: like/subscribe/read/bookmark의 POST는 중복 시 무에러로 그대로. 대응 DELETE는 대상 없어도 무에러. void라 응답 바디가 없으므로 최신 `liked`/`isSubscribed` 상태는 **상세 재조회**로 확인한다. `latestEpisodeNo`는 PUBLISHED 기준.

### 2.6 댓글 (Comment) — `/api/series/{seriesId}/episodes/{episodeNo}/comments`

근거: `CommentController.java`, `CommentService.java`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| POST | `.../comments` | 🔒 | `CommentCreateRequest` (body) | `IdResponse` | **201**. 댓글 작성 |
| GET | `.../comments` | 🔒 | path + `page`/`size` | `PageResponse<CommentResponse>` | principal 미사용이나 인증 체인에 걸림 |
| DELETE | `.../comments/{commentId}` | 🔒 | path `commentId` | 없음(void) | **204**. 작성자 본인 또는 ADMIN만, 아니면 403 |

**`CommentCreateRequest`**: `content`(`@NotBlank` + `@Size(max=1000)` — 1~1000자, 초과 시 400 `INVALID_INPUT`). 근거: `CommentCreateRequest.java`.
> DELETE는 경로상 `seriesId`/`episodeNo`가 존재하나 서비스에서 미사용(식별은 `commentId`).

### 2.7 관리자 (Admin) — `/api/admin/**`

클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` — 전부 🛡 ADMIN 전용, 비ADMIN은 403. 근거: `AdminController.java`, `AdminService.java`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| PATCH | `/api/admin/users/{userId}/role` | 🛡 | `RoleUpdateRequest` (body) | `UserResponse` | 역할 변경 |
| PATCH | `/api/admin/series/{seriesId}/age-rating` | 🛡 | `AgeRatingUpdateRequest` (body) | `SeriesResponse` | 연령등급 변경 |
| PATCH | `/api/admin/series/{seriesId}/visibility` | 🛡 | `VisibilityUpdateRequest` (body) | `SeriesResponse` | 공개/비공개 토글. false면 일반 사용자에게 404 은닉 |
| PATCH | `/api/admin/series/{seriesId}/adult-only` | 🛡 | `AdultOnlyUpdateRequest` (body) | `SeriesResponse` | 성인전용 분류 토글(목록 필터·`ADULT_FIRST` 정렬에 반영) |

**요청 DTO** (모두 `@NotNull`)
- `RoleUpdateRequest`: `role`(`Role`)
- `AgeRatingUpdateRequest`: `ageRating`(`AgeRating`)
- `VisibilityUpdateRequest`: `visible`(`Boolean`)
- `AdultOnlyUpdateRequest`: `adultOnly`(`Boolean`)

> 응답은 `SeriesResponse`(ADMIN PATCH 전용 DTO) — 상세 `SeriesDetailResponse`와 별개로 `episodeCount`/`isSubscribed`가 없다.

### 2.8 헬스체크 (Health)

근거: `HealthController.java:11-14`.

| Method | Path | 권한 | 요청 | 응답 | 비고 |
|---|---|---|---|---|---|
| GET | `/api/health` | 🔓 | 없음 | `{"status":"ok"}` | DTO 아닌 고정 `Map<String,String>` |

---

## 3. 응답 DTO 필드 사전

각 DTO의 정확한 필드명·타입(실제 record 소스 기준).

### TokenResponse — `auth/dto/TokenResponse.java`
| 필드 | 타입 |
|---|---|
| `accessToken` | String |
| `refreshToken` | String |

### UserResponse — `user/dto/UserResponse.java`
| 필드 | 타입 |
|---|---|
| `id` | Long |
| `email` | String |
| `nickname` | String |
| `role` | `Role` |

> `birthDate` 의도적 미노출(`/me`와 관리자 `changeUserRole`가 이 DTO 공유 → 관리자가 타인 생년월일 보는 것 방지).

### IdResponse — `global/dto/IdResponse.java`
| 필드 | 타입 |
|---|---|
| `id` | Long |

리소스 생성 공통 응답.

### SeriesSummaryResponse — `series/dto/SeriesSummaryResponse.java`
목록/내 작품용. `description`·`publishDays` 없음.
| 필드 | 타입 |
|---|---|
| `id` | Long |
| `title` | String |
| `authorNickname` | String |
| `ageRating` | `AgeRating` |
| `status` | `SeriesStatus` |
| `visible` | boolean |
| `adultOnly` | boolean |

### SeriesDetailResponse — `series/dto/SeriesDetailResponse.java`
`GET /api/series/{id}` 전용.
| 필드 | 타입 |
|---|---|
| `id` | Long |
| `title` | String |
| `description` | String |
| `authorNickname` | String |
| `ageRating` | `AgeRating` |
| `status` | `SeriesStatus` |
| `publishDays` | `Set<DayOfWeek>` |
| `visible` | boolean |
| `adultOnly` | boolean |
| `createdAt` | `Instant` |
| `episodeCount` | int (PUBLISHED 기준) |
| `latestEpisodeNo` | int (PUBLISHED 기준) |
| `isSubscribed` | boolean |

### SeriesResponse — `series/dto/SeriesResponse.java`
ADMIN PATCH 응답 전용. `episodeCount`/`isSubscribed` 없음.
| 필드 | 타입 |
|---|---|
| `id` | Long |
| `title` | String |
| `description` | String |
| `authorNickname` | String |
| `ageRating` | `AgeRating` |
| `status` | `SeriesStatus` |
| `publishDays` | `Set<DayOfWeek>` |
| `visible` | boolean |
| `adultOnly` | boolean |
| `createdAt` | `Instant` |

### EpisodeSummaryResponse — `episode/dto/EpisodeSummaryResponse.java`
회차 목록용. 이미지 없음.
| 필드 | 타입 |
|---|---|
| `episodeNo` | int |
| `title` | String |
| `publishAt` | `Instant` |

### EpisodeDetailResponse — `episode/dto/EpisodeDetailResponse.java`
| 필드 | 타입 |
|---|---|
| `episodeNo` | int |
| `title` | String |
| `status` | `EpisodeStatus` |
| `publishAt` | `Instant` |
| `images` | `List<EpisodeImageResponse>` |
| `viewCount` | long |
| `likeCount` | long |
| `liked` | boolean (내 좋아요 여부) |

### EpisodeImageResponse — `episode/dto/EpisodeImageResponse.java`
| 필드 | 타입 |
|---|---|
| `sortOrder` | int |
| `url` | String (스토리지 규약: local `/files/{key}`, s3 `{publicBaseUrl}/{key}`) |
| `width` | int |
| `height` | int |

### EpisodeNoResponse — `episode/dto/EpisodeNoResponse.java`
| 필드 | 타입 |
|---|---|
| `episodeNo` | int |

회차 업로드 응답.

### SubscriptionResponse — `personalization/dto/SubscriptionResponse.java`
| 필드 | 타입 |
|---|---|
| `seriesId` | Long |
| `title` | String |
| `latestEpisodeNo` | int (PUBLISHED 기준) |
| `lastReadEpisodeNo` | int |
| `up` | boolean (`latestEpisodeNo > lastReadEpisodeNo`) |

### BookmarkResponse — `personalization/dto/BookmarkResponse.java`
| 필드 | 타입 |
|---|---|
| `seriesId` | Long |
| `seriesTitle` | String |
| `episodeNo` | int |
| `episodeTitle` | String |
| `createdAt` | `Instant` |

### CommentResponse — `comment/dto/CommentResponse.java`
| 필드 | 타입 |
|---|---|
| `id` | Long |
| `content` | String |
| `authorNickname` | String |
| `createdAt` | `Instant` |

### PageResponse<T> — `global/dto/PageResponse.java`
| 필드 | 타입 |
|---|---|
| `content` | `List<T>` |
| `page` | int (0-base) |
| `size` | int |
| `totalElements` | long |
| `totalPages` | int |
| `last` | boolean |

### SliceResponse<T> — `global/dto/SliceResponse.java`
| 필드 | 타입 |
|---|---|
| `content` | `List<T>` |
| `page` | int (0-base) |
| `size` | int |
| `hasNext` | boolean |

### ErrorResponse — `global/exception/ErrorResponse.java`
| 필드 | 타입 |
|---|---|
| `status` | int |
| `code` | String (`ErrorCode` 이름) |
| `message` | String |
| `fieldErrors` | `List<FieldErrorDetail>` |

`FieldErrorDetail`: `field`(String), `reason`(String).

---

## 4. OpenAPI / Swagger 활용

springdoc-openapi(`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`, SB4.1 호환)로 스펙을 자동 생성한다. JWT bearer 보안 스킴이 등록되어 있어 Swagger UI에서 토큰을 넣고 인증 호출이 가능하다(근거: `OpenApiConfig.java`).

### 4.1 접속 (서버 기동 후)

| 용도 | URL |
|---|---|
| Swagger UI(브라우저 탐색·시험 호출) | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI 스펙(JSON) | `http://localhost:8080/v3/api-docs` |

- 두 경로 모두 permitAll(비로그인 접근 가능, 근거: `SecurityConfig.java:58`).
- Swagger UI 우상단 **Authorize** 에 `accessToken`(Bearer)을 넣으면 🔒 엔드포인트도 호출된다.

### 4.2 타입 자동 생성 (openapi-typescript)

서버를 띄운 상태에서 OpenAPI JSON으로 TS 타입을 생성한다.

```bash
# 서버 실행 중(localhost:8080)일 때
npx openapi-typescript http://localhost:8080/v3/api-docs -o ./src/api/schema.d.ts
```

생성된 `schema.d.ts`는 `paths`/`components.schemas`(위 DTO들과 1:1)를 담는다. `openapi-fetch` 등과 조합해 타입 안전한 API 클라이언트를 만든다.

### 4.3 `docs/openapi.json` 스냅샷 갱신

CI/오프라인 타입 생성·diff 리뷰를 위해 스펙을 파일로 커밋해 둘 수 있다. 스펙은 코드에서 자동 생성되므로 **서버를 띄우고 `/v3/api-docs`를 저장**하는 방식으로 갱신한다(직접 손으로 작성하지 않는다).

```bash
# 1) 서버 기동
./gradlew bootRun                    # http://localhost:8080

# 2) 스펙을 docs/openapi.json 으로 저장(다른 터미널)
curl -s http://localhost:8080/v3/api-docs -o docs/openapi.json

# (선택) jq가 있으면 정렬·정형화해 diff를 안정화
curl -s http://localhost:8080/v3/api-docs | jq -S . > docs/openapi.json
```

엔드포인트/DTO를 변경한 뒤에는 이 명령으로 스냅샷을 다시 생성해 커밋한다. 스냅샷 파일 자체가 진실의 원천이 아니라 **코드가 진실의 원천**이며, `openapi.json`은 그 산출물이다.
