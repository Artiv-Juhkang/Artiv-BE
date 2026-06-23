# AppToon 문서 — 프론트엔드 시작점 (Start Here)

웹툰 플랫폼 백엔드와 프론트(React Native/Expo, 웹)를 연동하는 데 필요한 모든 문서의 지도. **이 파일부터 읽고**, 아래 순서대로 진행한다.

> 스택: Java 25 · Spring Boot 4.1 · PostgreSQL 16 · JWT · springdoc(OpenAPI). 포트 **8080**. 역할 READER/CREATOR/ADMIN.

---

## 1. 백엔드 띄우기 (프론트가 붙을 서버)

```bash
docker compose up -d                 # postgres:16(+minio) — DB 컨테이너
cp .env.example .env                 # JWT_SECRET 채우기: openssl rand -base64 48
./gradlew bootRun                    # http://localhost:8080  (Flyway가 스키마 자동 적용)
curl http://localhost:8080/api/health      # -> {"status":"ok"}
```
- 실기기(Expo Go)는 `localhost`가 폰 자신 → PC의 **LAN IP**(`http://192.168.x.x:8080`)로 접속하거나 `adb reverse tcp:8080 tcp:8080`.
- 외부에서 붙거나 배포하려면 → [`deploy-guide.md`](deploy-guide.md).

## 2. API 둘러보기 (단일 출처)

| | 위치 | 용도 |
|---|---|---|
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html | 70개 엔드포인트 탐색·요청/응답 스키마·**Try it out**(우상단 Authorize에 JWT) |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs · [`openapi.json`](openapi.json) | 타입 코드젠·Postman import |

```bash
npx openapi-typescript docs/openapi.json -o src/api/schema.d.ts   # TS 타입 자동생성
```

---

## 3. 프론트 작업에 필요한 파일 (이게 전부)

읽기 순서대로:

| # | 파일 | 무엇 | 언제 |
|---|---|---|---|
| 1 | **[`frontend-guide.md`](frontend-guide.md)** | 온보딩 + **공통 규약**(인증·페이징·에러·이미지URL·**회차 잠금**·**알림 라우팅**·enum) + **도메인 지도** | **제일 먼저 정독** |
| 2 | **[`openapi.json`](openapi.json)** / Swagger | 엔드포인트 전수·요청/응답 DTO·enum 값의 **단일 출처** | 화면별 API 찾을 때 |
| 3 | **[`features.md`](features.md)** | 각 기능이 **무엇을 하는가 + 규칙·가드·예외**(역할별 권한, 연령 게이트, 기다리면무료, 블라인드 등) | 동작·정책 이해할 때 |
| 4 | [`deploy-guide.md`](deploy-guide.md) | 서버 실행·환경변수·배포 | 서버 직접 띄우거나 배포할 때 |
| 5 | [`api-reference.md`](api-reference.md) | (슬림) Swagger로 일원화된 포인터 | 참고 |

루트의 실행 파일: **`.env.example`**(환경변수 템플릿: DB·`JWT_SECRET`) · **`docker-compose.yml`**(postgres + minio).

설계 배경(프론트는 보통 불필요): [`roadmap-and-monetization.md`](roadmap-and-monetization.md)(수익모델) · [`design-notification.md`](design-notification.md) · [`design-moderation-activity.md`](design-moderation-activity.md).

---

## 4. 화면 짤 때 꼭 아는 공통 규약 (요약 — 자세히는 frontend-guide §3)

1. **인증**: `/auth/**`·`/health`·`/files/**`·Swagger 외 **전부 로그인 필요**. 헤더 `Authorization: Bearer <accessToken>`. 401이면 `/auth/refresh`(회전)로 새 쌍 받고 재시도.
2. **에러**: `{status, code, message, fieldErrors}` — `code`로 분기. 성공은 envelope 없이 DTO 그대로.
3. **페이징**: 목록은 `Page`(`!last`로 다음) / 회차는 `Slice`(`hasNext`). **고정 정렬 목록엔 `?sort=` 보내지 말 것**(무시·일부는 400).
4. **이미지**: `image.url`은 상대경로 → `${BASE_URL}${url}`. `/files/**` 공개.
5. **회차 잠금**(기다리면무료): 잠긴 회차는 **200 + `locked:true·freeAt`**(에러 아님, 이미지 빈 배열) → 카운트다운 UI.
6. **알림**: 항목의 `(targetType,targetId)`로 라우팅. `/unread-count` 폴링 배지, `PATCH /{id}/read`가 읽음+라우팅 정보 동시 반환.
7. **가입**: `nickname` 유일·한글/영문/숫자/`_`만, `birthDate` 필수(만14세+), `consents` 필수동의.
