# AppToon 마스터 작업 계획

> Docker Desktop 설치·설정까지 끝난 시점에서, **앞으로 해야 할 모든 작업을 순서대로** 정리한 실행 문서.
> 당장 할 일(STEP 0)을 가장 상세히, 이후 백엔드 구현(STEP 1~8)과 프론트 연동을 함께 묶었다.
> 깊은 내용은 다음 문서 참조: `backend-setup-guide.md` · `backend-implementation-roadmap.md` · `fullstack-integration-guide.md`.

---

## ✅ 현재까지 완료

- JDK 25 (LTS) 설치 및 `JAVA_HOME` 설정
- IntelliJ IDEA 설치
- Spring Boot 4.0.x 프로젝트 생성 (Java 25 / Gradle / 의존성: Web, JPA, PostgreSQL Driver, Security, Lombok, Validation)
- Docker Desktop 설치 + 권장 설정(VirtioFS, 리소스)

---

## STEP 0 — 개발 환경 가동 (지금 바로)

**목표**: DB가 붙고, 인증 없이 헬스체크가 되는 빈 서버를 띄운다. 여기까지 되면 "개발 시작 가능" 상태다.

### 0-1. DB 컨테이너 실행 (docker-compose)
프로젝트 루트에 `docker-compose.yml` 작성 후 실행.

```yaml
services:
  db:
    image: postgres:16
    container_name: apptoon-db
    environment:
      POSTGRES_DB: apptoon
      POSTGRES_USER: apptoon
      POSTGRES_PASSWORD: devpass
    ports:
      - "5432:5432"
    volumes:
      - apptoon-db-data:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  apptoon-db-data:
```

```bash
docker compose up -d        # 실행
docker ps                   # apptoon-db 가 떠 있는지 확인
```

### 0-2. application.yml 작성
`src/main/resources/application.properties` → `application.yml`로 교체.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/apptoon
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update          # 개발 초기. 실데이터 운영 전 validate
    properties:
      hibernate:
        format_sql: true
    show-sql: true
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 200MB
server:
  port: 8080
jwt:
  secret: ${JWT_SECRET}
  access-token-validity: 3600000
  refresh-token-validity: 1209600000
```

### 0-3. 환경변수 등록
IntelliJ `Run/Debug Configurations → Environment variables`에 등록:
`DB_USER=apptoon`, `DB_PASSWORD=devpass`, `JWT_SECRET=<충분히 긴 임의 문자열>`

### 0-4. Git 초기화
- `.gitignore`에 추가: `.env`, `/build`, `.gradle`, `.idea`, `*.log`, 업로드 이미지 디렉터리(`/storage`)
- `git init` → 첫 커밋 → GitHub 원격 저장소 생성 후 연결(`git remote add origin …`)

### 0-5. 첫 실행 확인
- 임시 `SecurityConfig`: `/api/health`, `/api/auth/**`는 `permitAll()`, 나머지는 인증.
- 헬스 컨트롤러(`GET /api/health` → `{"status":"ok"}`) 작성.
- `AppToonApplication` 실행(▶).

**완료 기준**
```bash
curl http://localhost:8080/api/health   # {"status":"ok"}
```
콘솔에 `Started AppToonApplication ...`, DB 연결 에러 없음 → STEP 1로.

---

## STEP 1~8 — 백엔드 구현

> 각 단계는 **끝내고 git 커밋 → 다음 단계**. 상세 구현은 `backend-implementation-roadmap.md`의 동일 Phase 참조.

### STEP 1. 공통 기반
- 패키지 구조(`global/` + `domain/`), 전역 예외 처리(`@RestControllerAdvice`), 공통 응답/페이징 DTO, Security 골격.
- **완료 기준**: 잘못된 요청에 통일된 에러 JSON 반환.

### STEP 2. User & 인증 (JWT)
- 의존성 추가: jjwt. `User` 엔티티(role=STRING), BCrypt, JWT 발급·검증 필터.
- API: `signup` / `login` / `refresh`, 메서드 시큐리티(`@PreAuthorize`).
- **완료 기준**: 가입→로그인→토큰으로 보호된 엔드포인트 200.

### STEP 3. Series (작품) + 분류
- `Series` 엔티티(ageRating·status=STRING, publishDays=`@ElementCollection`, author=`@ManyToOne`).
- API: 작품 등록(CREATOR), 목록(요일/연령 필터·페이징), 상세.
- **완료 기준**: 작품 생성 후 요일 필터로 정확히 분류 조회.

### STEP 4. Episode + 이미지 업로드 ★핵심
- 의존성: Thumbnailator. `Episode`·`EpisodeImage`(order/width/height) 엔티티.
- 업로드 API(`MultipartFile[]`) → 검증 → 리사이즈 → `/storage/{seriesId}/{episodeNo}/{order}` 저장 → 메타 기록.
- 발행: 즉시 / 예약(`@Scheduled`로 상태 전환).
- **완료 기준**: 이미지 다중 업로드 시 순서대로 저장 + DB에 메타데이터 생성.

### STEP 5. 뷰어 조회 API
- 회차 목록(`Slice` 무한스크롤), 회차 상세(이미지 order/url/width/height 포함), 19금 가드.
- **완료 기준**: 회차 조회 시 이미지가 순서·크기와 함께 내려옴.

### STEP 6. 개인화 (구독·읽음·UP)
- `Subscription`, `ReadLog`. UP 계산(최신 발행 publishedAt > 마지막 ReadLog). 이어보기.
- **완료 기준**: 새 회차 발행 후 구독자에게 UP 플래그, 읽으면 사라짐.

### STEP 7. 관리자 기능
- 작품 공개/연령등급/작가 권한 처리(ADMIN).
- **완료 기준**: 비관리자 403, 관리자 200.

### STEP 8. 확장
- **먼저**: springdoc(Swagger) → 프론트 타입 자동 생성 기반.
- 이후: QueryDSL(복잡 필터·검색), 댓글·좋아요·통계·푸시 등 우선순위대로.

---

## 프론트엔드 병행 작업 (RN Expo)

> 백엔드 각 STEP의 API가 나오면 그 계약대로 연동. API 계약은 `fullstack-integration-guide.md` §3·§4.

| 백엔드 STEP | 프론트 작업 |
|-------------|-------------|
| 0~1 | 프로젝트 구조 정비, API 클라이언트(axios+인터셉터), Zustand auth 스토어 |
| 2 | 로그인/회원가입 화면, `expo-secure-store` 토큰 저장, 401 자동 refresh |
| 3 | 요일 그리드, 작품/회차 목록 (TanStack Query) |
| 4~5 | 세로 스크롤 뷰어(`expo-image` + `getItemLayout`), 이전/다음화 |
| 6 | 관심 목록, 이어보기, UP 배지 |
| 7 | (별도 웹 패널) 작가 업로드·관리 화면 |
| 8 | OpenAPI로 TS 타입 자동 생성, 댓글/검색/알림 화면 |

**Expo SDK 56 주의**: 내비게이션 import는 `expo-router`에서(React Navigation 분리), New Architecture·Hermes V1 기본, `@expo/vector-icons` 수동 설치, 패키지는 `npx expo install`로 추가.

---

## 전체 진행 체크리스트

**환경 (STEP 0)**
- [ ] docker-compose로 DB 실행, `docker ps` 확인
- [ ] application.yml 작성 + 환경변수 등록
- [ ] .gitignore + git init + GitHub 원격 연결
- [ ] 헬스체크 200 + DB 연결 성공

**백엔드**
- [ ] STEP 1 공통 기반
- [ ] STEP 2 인증
- [ ] STEP 3 작품·분류
- [ ] STEP 4 회차·업로드
- [ ] STEP 5 뷰어 조회
- [ ] STEP 6 개인화
- [ ] STEP 7 관리자
- [ ] STEP 8 문서화·확장

**프론트**
- [ ] API 클라이언트 + 인증 연동
- [ ] 작품/회차 목록·뷰어
- [ ] 개인화 화면
- [ ] 타입 자동 생성 파이프라인

---

## Claude Code 작업 루틴

1. **한 STEP씩** 지시 → 코드 받기 → "완료 기준" 테스트 → 통과하면 커밋.
2. 지시할 때 이 문서 + `fullstack-integration-guide.md`(API 계약)를 컨텍스트로 함께 제공.
3. 규칙 명시: "enum은 STRING, 연관관계 LAZY, Entity 대신 DTO 노출, 권한검증은 Service에서".
4. 받은 코드는 직접 읽고 이해하고 넘어가기(면접·실무에서 설명 가능해야 함).
5. 큰 기능은 더 작은 작업으로 쪼개서 실행·확인.

---

## 전 단계 공통 주의

- 역할 3개: READER / CREATOR / ADMIN.
- 권한·소유권 검증은 항상 Service 계층.
- enum 필드 `@Enumerated(EnumType.STRING)`.
- 이미지: `order`·`width`·`height` 반드시 저장(뷰어 안정성).
- 민감정보는 환경변수로, 저장소 커밋 금지.
- `ddl-auto`: 개발 `update` → 운영 전 `validate`, `create` 계열 금지.
- DB 백업(홈 서버 배포 시): `pg_dump` 크론 + `/storage` 백업.
- 각 STEP 완료 시 커밋.
