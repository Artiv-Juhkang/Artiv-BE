# AppToon 백엔드 — 프론트엔드 협업 가이드

React Native(Expo) 프론트가 이 백엔드와 연동하는 데 필요한 모든 것. 서버 실행 → 인증 → 공통 규약 → 엔드포인트 → 타입 생성 순.

> 스택: Java 25 · Spring Boot 4.1 · PostgreSQL 16 · JWT 인증 · springdoc(OpenAPI). 기본 포트 **8080**.

---

## 1. 서버 실행 (Quick Start)

```bash
# 1) DB 컨테이너 기동 (Docker Desktop 실행 중이어야 함)
docker compose up -d                 # postgres:16, localhost:5432, healthcheck 내장

# 2) 환경변수 준비
cp .env.example .env                 # 그리고 JWT_SECRET 채우기
#   JWT_SECRET 은 32바이트 이상 임의 문자열: openssl rand -base64 48

# 3) 서버 실행
./gradlew bootRun                    # http://localhost:8080

# 4) 살아있는지 확인 (인증 불필요)
curl http://localhost:8080/api/health      # -> {"status":"ok"}
```

필요 환경변수(.env 또는 IntelliJ Run Config):

| 변수 | 기본/예시 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/apptoon` | docker-compose db와 일치 |
| `DB_USER` / `DB_PASSWORD` | `apptoon` / `devpass` | 〃 |
| `JWT_SECRET` | (직접 생성) | HS256 서명키, 32바이트+ |

- 스키마는 **Flyway가 자동 적용**(서버 기동 시 V1~V12). 별도 SQL 실행 불필요.
- 업로드 이미지는 로컬 `storage/`에 저장되고 `/files/**`로 서빙된다(아래 5.3).

---

## 2. 인증 (JWT)

흐름: **회원가입 → 로그인(토큰 발급) → 보호 API 호출(Bearer) → 만료 시 refresh**.

```
POST /api/auth/signup   {email, password, nickname, birthDate}  -> 201 {id}
POST /api/auth/login    {email, password}                       -> {accessToken, refreshToken}
POST /api/auth/refresh  {refreshToken}                          -> {accessToken, refreshToken}  (회전: 기존 refresh 폐기)
```

- 보호 API는 헤더 `Authorization: Bearer <accessToken>` 필요.
- **accessToken 유효 1시간**, **refreshToken 14일**. accessToken 만료(401) 시 refresh로 새 쌍을 받고 재시도.
- `refresh`는 **회전(rotation)** — 호출 시 새 쌍 발급 + 기존 refresh 즉시 폐기(재사용하면 401). refreshToken은 최신 것만 안전 보관(예: Expo SecureStore).
- 비로그인/잘못된 토큰 = **401**, 로그인했지만 권한 부족 = **403**. 둘 다 아래 표준 에러 JSON(2.1과 동일 형식).

`birthDate`(생년월일)는 **필수**(`YYYY-MM-DD`, 과거 날짜) — 19금 작품 열람 게이트(만 19세)에 쓰인다.

권한 역할: `READER`(독자) / `CREATOR`(작가) / `ADMIN`(관리자). 가입 시 기본 READER, 작가 전환은 관리자가.

---

## 3. 공통 규약 (Contracts)

### 3.1 Base URL
- 개발: `http://localhost:8080`
- 실기기(Expo Go)에서 `localhost`는 **폰 자신**을 가리킨다 → PC의 LAN IP 사용(예: `http://192.168.0.10:8080`). `adb reverse tcp:8080 tcp:8080`(안드로이드) 도 가능.

### 3.2 페이징
두 종류가 용도별로 다르다.

**Page**(전체 개수 필요 — 작품 목록, 댓글): 요청 `?page=0&size=20&sort=...`
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3, "last": false }
```
**Slice**(무한스크롤 — 회차 목록): 전체 개수 없이 `hasNext`만
```json
{ "content": [...], "page": 0, "size": 20, "hasNext": true }
```
다음 페이지 판단: Page는 `!last`, Slice는 `hasNext`.

### 3.3 에러 (모든 에러 공통 형식)
```json
{ "status": 400, "code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.",
  "fieldErrors": [ { "field": "email", "reason": "이메일 형식이 아닙니다" } ] }
```
- `code`(머신용)로 분기, `message`(한국어)는 그대로 표시 가능, `fieldErrors`는 검증 실패 시에만 채워짐.
- 주요 code: `INVALID_INPUT`(400) · `ENTITY_NOT_FOUND`(404) · `DUPLICATE_EMAIL`(409) · `INVALID_CREDENTIALS`(401) · `UNAUTHORIZED`(401) · `FORBIDDEN`(403) · `ADULT_ONLY`(403) · `INVALID_IMAGE`(400).
- **성공 응답은 envelope 없이 DTO를 그대로** 반환(에러만 위 형식).

### 3.4 이미지 URL
회차 상세의 `images[].url`은 **앱 루트 상대경로**(`/files/1/3/0.png`). 프론트는 base URL을 붙여 사용:
```
<img src={`${BASE_URL}${image.url}`} />   // http://localhost:8080/files/1/3/0.png
```
`/files/**`는 **인증 없이** 접근 가능(공개 정적 서빙).

### 3.5 enum / 날짜 형식
- enum은 **문자열 그대로** 직렬화. `AgeRating`: `ALL|AGE_12|AGE_15|AGE_19` · `SeriesStatus`: `ONGOING|COMPLETED|HIATUS` · `EpisodeStatus`: `DRAFT|SCHEDULED|PUBLISHED` · `Role`: `READER|CREATOR|ADMIN` · `SeriesSort`: `LATEST|ADULT_FIRST`.
- 날짜시간(`publishAt`, `createdAt`)은 **Instant = ISO-8601 UTC**(`2026-06-19T12:00:00Z`). `birthDate`는 `LocalDate`(`1990-01-01`).
- 요일(`publishDays`)은 `DayOfWeek` 대문자 영어(`MONDAY`...`SUNDAY`).

---

## 4. API 엔드포인트 인벤토리

🔓=비로그인 가능, 🔒=인증 필요, 👤=작가(CREATOR), 🛡=관리자(ADMIN).

### 인증 / 사용자
| | 메서드·경로 | 요청 | 응답 |
|---|---|---|---|
| 🔓 | `POST /api/auth/signup` | `{email, password(8~64), nickname(~20), birthDate}` | 201 `{id}` |
| 🔓 | `POST /api/auth/login` | `{email, password}` | `{accessToken, refreshToken}` |
| 🔓 | `POST /api/auth/refresh` | `{refreshToken}` | `{accessToken, refreshToken}` |
| 🔒 | `GET /api/users/me` | — | `{id, email, nickname, role}` |
| 🔓 | `GET /api/health` | — | `{status:"ok"}` |

### 작품(Series)
| | 메서드·경로 | 비고 |
|---|---|---|
| 🔒 | `GET /api/series` | 목록. 쿼리: `day, ageRating, keyword, adultOnly, sort(LATEST\|ADULT_FIRST), page, size` → **Page**<SeriesSummary> |
| 🔒 | `GET /api/series/mine` | 작가 자기 작품(비공개 포함) → List<SeriesSummary> |
| 🔒 | `GET /api/series/{id}` | 상세 → SeriesDetail (비공개·미발행은 작가/ADMIN만, 그 외 404) |
| 👤 | `POST /api/series` | `{title, description, ageRating, status, publishDays[], adultOnly}` → 201 `{id}` |

### 회차(Episode)
| | 메서드·경로 | 비고 |
|---|---|---|
| 🔒 | `GET /api/series/{seriesId}/episodes` | 발행 회차 무한스크롤 → **Slice**<EpisodeSummary> |
| 🔒 | `GET /api/series/{seriesId}/episodes/{episodeNo}` | 상세 → EpisodeDetail. **호출 시 조회수 +1**. 19금은 만19세만 |
| 👤 | `POST /api/series/{seriesId}/episodes` | **multipart/form-data**: `title`, `publishAt`(선택, 미래면 예약), `images`(파일 여러 장) → 201 `{episodeNo}` |
| 🔒 | `POST /api/series/{seriesId}/episodes/{episodeNo}/like` | 좋아요(멱등) → 201 |
| 🔒 | `DELETE /api/series/{seriesId}/episodes/{episodeNo}/like` | 좋아요 취소 → 204 |

### 개인화 / 소셜
| | 메서드·경로 | 비고 |
|---|---|---|
| 🔒 | `POST/DELETE /api/series/{seriesId}/subscription` | 구독/해지(멱등) → 201/204 |
| 🔒 | `POST /api/series/{seriesId}/episodes/{episodeNo}/read` | 읽음 처리(멱등) → 201 |
| 🔒 | `GET /api/me/subscriptions` | 내 구독(UP 배지·이어보기) → List<Subscription> |
| 🔒 | `POST/DELETE /api/series/{seriesId}/episodes/{episodeNo}/bookmark` | 북마크/취소(멱등) → 201/204 |
| 🔒 | `GET /api/me/bookmarks` | 내 북마크 → List<Bookmark> |
| 🔒 | `POST /api/series/{seriesId}/episodes/{episodeNo}/comments` | `{content}` → 201 `{id}` |
| 🔒 | `GET /api/series/{seriesId}/episodes/{episodeNo}/comments` | **Page**<Comment> (`page,size`) |
| 🔒 | `DELETE /.../comments/{commentId}` | 본인·ADMIN만 → 204 |

### 관리자(🛡 ADMIN 전용)
| 메서드·경로 | 요청 |
|---|---|
| `PATCH /api/admin/users/{userId}/role` | `{role}` (작가 권한 부여 등) |
| `PATCH /api/admin/series/{seriesId}/age-rating` | `{ageRating}` |
| `PATCH /api/admin/series/{seriesId}/visibility` | `{visible}` (공개/비공개) |
| `PATCH /api/admin/series/{seriesId}/adult-only` | `{adultOnly}` (성인 전용; AGE_19여야 함) |

### 주요 응답 DTO 형태
```
SeriesSummary  { id, title, authorNickname, ageRating, status, visible, adultOnly }
SeriesDetail   { id, title, description, authorNickname, ageRating, status, publishDays[],
                 visible, adultOnly, createdAt, episodeCount, latestEpisodeNo, isSubscribed }
EpisodeSummary { episodeNo, title, publishAt }
EpisodeDetail  { episodeNo, title, status, publishAt,
                 images[{sortOrder, url, width, height}], viewCount, likeCount, liked }
Subscription   { seriesId, title, latestEpisodeNo, lastReadEpisodeNo, up }
Bookmark       { seriesId, seriesTitle, episodeNo, episodeTitle, createdAt }
Comment        { id, content, authorNickname, createdAt }
User           { id, email, nickname, role }
```

---

## 5. 타입 자동생성 (OpenAPI / Swagger)

서버가 코드에서 OpenAPI 문서를 자동 생성한다 — **수기 타입 작성 대신 자동생성** 권장.

- Swagger UI(브라우저에서 직접 호출·탐색): `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON(타입 생성 입력): `http://localhost:8080/v3/api-docs`

```bash
# 예: openapi-typescript 로 TS 타입 생성
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts
```
Swagger UI 우상단 **Authorize**에 accessToken을 넣으면 보호 API도 브라우저에서 바로 호출해볼 수 있다.

---

## 6. ✅ CORS — 설정됨

- **RN 네이티브**(Expo Go 앱/빌드)는 CORS 무관 — 그대로 호출 가능.
- **Expo Web/브라우저**에서도 호출 가능하도록 CORS를 허용한다(`SecurityConfig`).
- **개발 기본은 전체 origin 허용**(`app.cors.allowed-origins` 기본 `*`). **운영은 환경변수 `CORS_ALLOWED_ORIGINS`**(콤마 구분)로 좁힌다 — 예: `CORS_ALLOWED_ORIGINS=https://app.example.com`.
- 허용 메서드: `GET,POST,PATCH,PUT,DELETE,OPTIONS` · 헤더: 전체 · `allowCredentials=false`(JWT는 Authorization 헤더라 쿠키 미사용).

---

## 7. RN Expo 연동 팁
- `Authorization: Bearer` 자동 첨부 + 401 시 refresh 후 재시도는 axios 인터셉터로 한 곳에 처리.
- 토큰 저장은 `expo-secure-store`(refresh) / 메모리(access).
- 회차 업로드는 `multipart/form-data` — RN `FormData`에 `images` 파트로 파일들, `title`/`publishAt`은 폼 필드.
- 이미지 렌더는 `${BASE_URL}${image.url}`. 무한스크롤은 회차 목록의 `hasNext`로 다음 페이지 로드.
