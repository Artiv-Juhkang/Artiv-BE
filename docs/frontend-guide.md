# AppToon 백엔드 — 프론트엔드 협업 가이드

React Native(Expo) 프론트가 이 백엔드와 연동하는 데 필요한 것: 서버 실행 → 인증 → 공통 규약 → 도메인 맵 → 타입 생성.

> 스택: Java 25 · Spring Boot 4.1 · PostgreSQL 16 · JWT 인증 · springdoc(OpenAPI). 기본 포트 **8080**.
>
> **이 문서는 온보딩 + 공통 규약 + 도메인 지도**다. **엔드포인트 전수·요청/응답 DTO 스키마의 단일 출처는 Swagger**(아래 §5):
> `http://localhost:8080/swagger-ui/index.html` · `docs/openapi.json`(추출본). 코드와 항상 동기화되니 개별 엔드포인트는 거기서 확인한다.

---

## 1. 서버 실행 (Quick Start)

```bash
docker compose up -d                 # postgres:16, localhost:5432
cp .env.example .env                 # JWT_SECRET 채우기 (openssl rand -base64 48)
./gradlew bootRun                    # http://localhost:8080
curl http://localhost:8080/api/health      # -> {"status":"ok"} (인증 불필요)
```

| 변수 | 기본/예시 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/apptoon` | docker-compose db와 일치 |
| `DB_USER` / `DB_PASSWORD` | `apptoon` / `devpass` | 〃 |
| `JWT_SECRET` | (직접 생성) | HS256 서명키, 32바이트+ |

- 스키마는 **Flyway가 자동 적용**(서버 기동 시 V1~V22). 별도 SQL 실행 불필요.
- 업로드 이미지는 로컬 `storage/`에 저장되고 `/files/**`로 공개 서빙(§3.4).

---

## 2. 인증 (JWT)

흐름: **회원가입 → 로그인(토큰 발급) → 보호 API 호출(Bearer) → 만료 시 refresh**.

```
POST /api/auth/signup   {email, password, nickname, birthDate, consents{...}}  -> 201 {id}
POST /api/auth/login    {email, password}                                      -> {accessToken, refreshToken}
POST /api/auth/refresh  {refreshToken}                                         -> {accessToken, refreshToken}  (회전)
```

- 보호 API는 헤더 `Authorization: Bearer <accessToken>`. **거의 모든 API가 인증 필요**(`/api/auth/**`·`/api/health`·`/files/**`·Swagger만 공개).
- **accessToken 1시간 / refreshToken 14일**. 401 시 refresh로 새 쌍 받고 재시도.
- `refresh`는 **회전(rotation)** — 새 쌍 발급 + 기존 refresh 즉시 폐기(재사용 401). 최신 refresh만 보관(Expo SecureStore).
- **가입 입력 규칙**(가입 화면이 강제할 것):
  - `birthDate` 필수(`YYYY-MM-DD`, 과거). **만 14세 미만 가입 차단**, 19금 작품은 만 19세 게이트.
  - `nickname` **유일**(중복 시 `DUPLICATE_NICKNAME` 409) + **한글·영문·숫자·`_`만**(공백·특수문자 불가 → 400). 멘션 `@닉네임`이 1명을 정확히 가리키게 하기 위함.
  - `consents` 맵 — 필수 동의(`TERMS_OF_SERVICE`,`PRIVACY_POLICY`) 미동의 시 가입 거부. 선택: `MARKETING_EMAIL`(기본 opt-out) 등. 동의 종류는 Swagger의 `ConsentType` 참조.
- 역할: `READER`(독자) / `CREATOR`(작가) / `ADMIN`(관리자). 가입 시 READER, 작가 전환은 `POST /api/users/me/creator-request` 신청 → 관리자 승인.

---

## 3. 공통 규약 (Contracts)

### 3.1 Base URL
- 개발: `http://localhost:8080`. 실기기(Expo Go)는 `localhost`가 폰 자신이라 **PC의 LAN IP**(`http://192.168.0.10:8080`) 또는 `adb reverse tcp:8080 tcp:8080`.

### 3.2 페이징 — Page vs Slice
**Page**(전체 개수 필요 — 목록·댓글·내 활동): `?page=0&size=20`
```json
{ "content": [...], "page":0, "size":20, "totalElements":57, "totalPages":3, "last":false }
```
**Slice**(무한스크롤 — 회차 목록): `{ "content":[...], "page":0, "size":20, "hasNext":true }`

다음 페이지: Page는 `!last`, Slice는 `hasNext`.
> ⚠️ **고정 정렬 목록**(회차·내 글·내 댓글·추천·열람이력·소식피드 등)은 서버 정렬이 고정이라 **`?sort=`를 보내지 말 것**(무시됨). 관리자/탐색 목록 등 정렬 지원 여부는 Swagger 파라미터로 확인.

### 3.3 에러 (모든 에러 공통 형식)
```json
{ "status":400, "code":"INVALID_INPUT", "message":"입력값이 올바르지 않습니다.",
  "fieldErrors":[ {"field":"email","reason":"이메일 형식이 아닙니다"} ] }
```
- `code`로 분기, `message`(한국어) 표시 가능, `fieldErrors`는 검증 실패 시만.
- 주요 code: `INVALID_INPUT`(400) · `ENTITY_NOT_FOUND`(404) · `DUPLICATE_EMAIL`/`DUPLICATE_NICKNAME`(409) · `INVALID_CREDENTIALS`/`UNAUTHORIZED`/`INVALID_TOKEN`(401) · `FORBIDDEN`/`ADULT_ONLY`(403) · `INVALID_IMAGE`(400).
- **성공 응답은 envelope 없이 DTO 그대로**(에러만 위 형식).

### 3.4 이미지 URL
회차/게시글 이미지의 `url`은 **앱 루트 상대경로**(`/files/1/3/0.png`). 프론트가 base URL을 붙인다: `${BASE_URL}${image.url}`. `/files/**`는 **인증 없이** 접근(공개 정적 서빙).

### 3.5 enum / 날짜
- enum은 **문자열 그대로**. 자주 쓰는 값(전체는 Swagger 스키마):
  - `AgeRating`: `ALL|AGE_12|AGE_15|AGE_19` · `SeriesStatus`: `ONGOING|COMPLETED|HIATUS` · `EpisodeStatus`: `DRAFT|SCHEDULED|PUBLISHED`
  - `Genre`: `ROMANCE|FANTASY|ACTION|DRAMA|DAILY|COMEDY|THRILLER|SPORTS|HORROR|ETC` · `SeriesSort`: `LATEST|ADULT_FIRST`
  - `PostCategory`: `RECOMMEND|FREE|FANART|QUESTION` · `PostSort`: `LATEST|BEST`
  - `NotificationType`: `EPISODE_PUBLISHED|INQUIRY_ANSWERED|POST_COMMENT|COMMENT_REPLY|FOLLOWED|POST_MENTIONED`
  - `ReleasePolicy`: `FREE_ALL|WAIT_FREE` · `InquiryType`: `ACCOUNT|PAYMENT|CONTENT|CREATOR|BUG|ETC`
- 날짜시간(`publishAt`,`createdAt`,`freeAt`)은 **Instant=ISO-8601 UTC**(`2026-06-24T12:00:00Z`). `birthDate`는 `LocalDate`(`1990-01-01`). 요일(`publishDays`)은 `DayOfWeek` 대문자(`MONDAY`…).

### 3.6 회차 잠금 (수익화 0단계 — 기다리면무료)
작가가 작품 공개정책을 `WAIT_FREE`로 두면 최신화는 일정 기간 잠긴다. **잠긴 회차는 에러가 아니라 200 + 락 플래그**로 온다:
```json
EpisodeDetail(잠김) { "locked":true, "lockReason":"WAIT", "freeAt":"2026-07-01T00:00:00Z",
                      "images":[], "viewCount":0, ... }   // 이미지 없음, freeAt까지 카운트다운
```
- `locked=true`면 뷰어 대신 "기다리면무료 — `freeAt`에 무료 전환" UI를 띄운다. `lockReason=WAIT`(0단계). 회차 목록(`EpisodeSummary`)에도 `locked`·`freeAt`가 있어 잠긴 화에 자물쇠 배지를 그릴 수 있다.
- 작가 본인·관리자는 항상 열림(프리뷰). 결제(코인·멤버십)는 추후 단계.

### 3.7 알림 라우팅
알림 목록 항목은 클릭 시 이동에 필요한 정보를 담는다: `{ type, targetType, targetId, title, message, read, createdAt }`. 프론트는 `(targetType,targetId)`로 라우팅(예: `INQUIRY`/42 → 문의 상세). **읽음 처리(`PATCH /{id}/read`)는 읽음+라우팅 정보를 한 번에 반환** → "읽음 처리 후 이동"을 1콜로. 미읽음 배지는 `/unread-count` 폴링, 종류별 탭은 `/unread-summary`.

---

## 4. 도메인 지도 (엔드포인트 그룹)

각 그룹의 **개별 엔드포인트·파라미터·DTO는 Swagger**에서 확인(아래 §5). 권한: 🔒 인증 · 👤 작가(CREATOR) · 🛡 관리자(ADMIN).

| 도메인 | 경로 prefix | 무엇 |
|---|---|---|
| 인증 | `/api/auth/**` 🔓 | 가입·로그인·refresh |
| 계정·프로필 | `/api/users/me/**` 🔒 | 내 정보·닉네임·비번·아바타·소개·동의내역·작가신청 |
| 팔로우 | `/api/users/{id}/follow(-stats)`, `/api/users/me/follow(ing\|ers)` 🔒 | 팔로우/통계/목록 |
| 작품 | `/api/series/**` 🔒(생성·정책은 👤) | 목록·상세·등록·장르태그·**공개정책(release-policy)** |
| 회차·뷰어 | `/api/series/{id}/episodes/**` 🔒(업로드 👤) | 목록(Slice)·상세(조회수·19금·**잠금** §3.6)·업로드(multipart)·좋아요 |
| 개인화 | `/api/series/{id}/subscription`·`/read`·`/bookmark` 🔒 | 구독·읽음·북마크(멱등) |
| 독자 서재 | `/api/me/subscriptions`·`/bookmarks`·`/read-history` 🔒 | 구독·관심·열람이력 |
| 회차 댓글 | `/api/series/{id}/episodes/{no}/comments` 🔒 | 회차 댓글 |
| 커뮤니티 | `/api/posts/**` 🔒 | 게시판(말머리·이미지≤5·추천·댓글/대댓글·**@멘션**) |
| 내 활동 | `/api/me/posts`·`/post-comments`·`/liked-posts`·`/author-news-feed` 🔒 | 내 글·댓글·추천·**작가 소식 피드**(팔로우한 작가 글) |
| 작가 공개글 | `/api/authors/{id}/posts` 🔒 | 특정 작가의 공개 게시글 |
| 알림 | `/api/me/notifications/**` 🔒 | 목록(종류필터)·미읽음수·집계·읽음(§3.7). 멘션은 `?type=POST_MENTIONED` |
| 신고 | `/api/reports` 🔒 / `/api/admin/reports/**` 🛡 | 신고 접수 / 관리자 처리 |
| 문의 | `/api/me/inquiries` 🔒 / `/api/admin/inquiries/**` 🛡 | 1:1 문의 / 관리자 답변 |
| 관리자 | `/api/admin/**` 🛡 | 사용자·권한·작품(연령·공개·성인)·작가신청·신고·커뮤니티·문의 |

> **멘션**: 게시글/댓글 본문에 `@닉네임`을 쓰면 그 사용자에게 `POST_MENTIONED` 알림이 간다. 닉네임은 유일·제한문자셋(§2)이라 1명을 정확히 가리킨다.

---

## 5. 타입 자동생성 (OpenAPI / Swagger) — **엔드포인트·DTO의 단일 출처**

서버가 코드에서 OpenAPI를 자동 생성 → **수기 타입 대신 자동생성**.

- **Swagger UI**(탐색·호출): `http://localhost:8080/swagger-ui/index.html` — 우상단 **Authorize**에 accessToken 넣으면 보호 API도 브라우저에서 호출.
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs` (라이브) · `docs/openapi.json` (추출 스냅샷, 70경로).
```bash
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts   # 또는 docs/openapi.json
```
스냅샷 갱신: `curl -s localhost:8080/v3/api-docs | python -m json.tool > docs/openapi.json`

---

## 6. ✅ CORS — 설정됨
- **RN 네이티브**(Expo Go)는 CORS 무관. **Expo Web/브라우저**도 허용.
- 개발 기본 전체 origin(`app.cors.allowed-origins`=`*`). 운영은 `CORS_ALLOWED_ORIGINS`(콤마)로 좁힘. 허용 메서드 `GET,POST,PATCH,PUT,DELETE,OPTIONS` · `allowCredentials=false`(JWT는 Authorization 헤더).

## 7. RN Expo 연동 팁
- `Authorization: Bearer` 자동 첨부 + 401 시 refresh 후 재시도는 **axios 인터셉터 한 곳**에.
- 토큰: `expo-secure-store`(refresh) / 메모리(access).
- 회차 업로드는 `multipart/form-data`(`images` 파트 파일들 + `title`/`publishAt` 폼 필드). 게시글도 multipart(이미지≤5).
- 이미지 렌더 `${BASE_URL}${image.url}`. 무한스크롤은 회차 `hasNext`.
- **잠긴 회차**(§3.6): `locked` 분기로 뷰어 대신 카운트다운 UI. **알림 배지**(§3.7): `/unread-count` 주기 폴링.
