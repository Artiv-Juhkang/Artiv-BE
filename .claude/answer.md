# 각 단계의 작업에 대한 정리내역 및 설명

> AppToon 백엔드를 STEP별로 **무엇을 / 어떻게 / 왜** 작업했는지, **반드시 알아야 할 점**, 그리고 진행 중 만난 **오류와 대처**를 정리한 학습 문서.
> **완료된 STEP만 기록한다** (아직 안 한 STEP은 끝낸 뒤 추가). 계획 원본은 `.claude/start.md`.

---

## 0. 전체 그림 (먼저 잡고 가기)

- **무엇을 만드나:** 웹툰 플랫폼 백엔드(REST API). 역할 3종 — READER(독자) / CREATOR(작가) / ADMIN(관리자).
- **기술 스택:** Java 25(LTS) · Spring Boot 4.1 · Spring Framework 7 · Spring Security 7 · Spring Data JPA(Hibernate) · PostgreSQL 16 · Gradle 9 · Docker. 프론트는 추후 React Native(Expo).
- **목표 아키텍처(앞으로 이 구조로 간다):**
  - **Controller** — HTTP 요청/응답 담당(얇게). 요청 DTO 받고 응답 DTO 반환.
  - **Service** — 비즈니스 로직 + **권한·소유권 검증**(여기서 한다).
  - **Repository** — DB 접근(JPA가 SQL 자동 생성).
  - **Entity** — DB 테이블과 매핑되는 객체. 외부에 직접 노출하지 않고 **DTO로 변환**해서 응답.
  - 데이터 흐름: `Client → Controller(DTO) → Service(검증·로직) → Repository → DB` (응답은 역방향).
- **불변 규칙(전 STEP 공통):** enum `@Enumerated(STRING)` · 연관관계 `LAZY` · Entity 대신 DTO 노출 · 권한검증은 Service · 비밀값은 환경변수(커밋 금지) · 스키마 변경은 Flyway.

---

## STEP 0 — 개발 환경 가동 ✅ 완료 (커밋: `chore: STEP 0 ...`)

### 목표
DB가 붙고, 인증 없이 헬스체크가 되는 빈 서버를 띄운다. = "개발 시작 가능" 상태.

### ★ 반드시 알아야 할 핵심 (이것만은)
1. **이 스택은 매우 최신(SB4 / Java25)이라 옛 블로그·예제가 안 맞는다.** 의존성/패키지/버전은 기억이 아니라 **빌드와 조건평가 리포트로 검증**한다.
2. **스키마의 주인은 Flyway다** (`ddl-auto: validate`). 앞으로 엔티티를 추가하면 **반드시 `V?__*.sql` 마이그레이션도 같이** 만들어야 한다(안 그러면 validate가 "테이블 없음"으로 기동 실패).
3. **설정은 프로파일로 분리**(dev/prod), **비밀값은 `.env`**(커밋 금지, 예시는 `.env.example`).
4. **보안 기본값은 "전부 인증 필요"**, `/api/health`·`/api/auth/**`만 열어둠. 세션 안 쓰는 **STATELESS**(다음 STEP에서 JWT 연결).
5. **테스트는 Testcontainers로 진짜 DB를 띄운다 → Docker가 꺼져 있으면 테스트도 실패한다.**

### 작업 내역 (무엇을 / 왜)

| 파일 | 한 일 | 왜 |
|---|---|---|
| `docker-compose.yml` | PostgreSQL 16 컨테이너 정의(포트 5432, 볼륨, **healthcheck**) | DB를 PC에 직접 설치하지 않고 컨테이너로 격리·재현. healthcheck로 "DB 준비됨" 판단 |
| `application.yml` (+`-dev`,`-prod`) | 공통 설정 + **프로파일 분리** | 개발=SQL 로그 ON, 운영=OFF. 한 파일에 섞으면 운영 전환이 위험 |
| `.env.example` | DB·JWT 환경변수 예시 | 실제 `.env`는 커밋 금지(비밀값). 예시만 공유 |
| `SecurityConfig.java` | 시큐리티 필터 체인 골격 | health·auth만 permitAll, 나머지 인증, **STATELESS** |
| `HealthController.java` | `GET /api/health` → `{"status":"ok"}` | 서버 생존 확인용 최소 엔드포인트 |
| `build.gradle` | `spring-boot-flyway` + Testcontainers 의존성 | 스키마 버전관리 + 진짜 DB로 테스트 |
| 테스트 3종(`AppToonApplicationTests`,`HealthControllerTest`,`TestcontainersConfiguration`) | 통합 테스트 + 공용 컨테이너 설정 | "완료 기준"을 수동 curl이 아니라 **자동 검증**으로 |

설계 결정: `ddl-auto: validate` + **스키마 주인은 Flyway**(개발 편의용 `update`는 추적 불가 → 감점 포인트). `open-in-view: false`(뷰 렌더링 중 커넥션 점유 안티패턴 차단).

### 검증 (어떻게 확인했나)
- `./gradlew test` → **BUILD SUCCESSFUL**. Testcontainers가 진짜 postgres를 띄워 ① 컨텍스트 로드 ② `/api/health` 200·`{"status":"ok"}` 자동 확인.
- `docker compose up -d` 후 `curl localhost:8080/api/health` → `{"status":"ok"}`, 로그 `Started AppToonApplication`.
- 미인증 보호 경로(`/api/series`) → **403** (시큐리티 동작).
- DB에 `flyway_schema_history` 테이블 생성 확인.

### 이 STEP의 키워드
- **컨테이너 / Docker / docker-compose:** (자세히는 아래 "Docker 상세" 절) 앱·DB를 격리된 상자로 실행. compose는 여러 컨테이너를 YAML로 정의·실행.
- **프로파일(profile):** 환경별 설정 묶음. `application-{env}.yml`을 `--spring.profiles.active=prod`로 전환.
- **환경변수 주입 `${VAR:기본값}`:** "환경변수 VAR, 없으면 기본값". 비밀값을 코드에서 분리.
- **`ddl-auto`:** Hibernate 시작 시 테이블 처리. `none`/`validate`(일치 검사)/`update`(자동반영, 추적불가)/`create`·`create-drop`(매번 새로, 운영금지). → 우리는 **validate** + Flyway.
- **Flyway / 마이그레이션:** 스키마 변경을 `V1__init.sql` 버전 스크립트로 관리, 이력은 `flyway_schema_history`에 기록 → 어느 환경이든 동일 스키마 재현.
- **SecurityFilterChain:** 모든 요청이 통과하는 보안 필터 묶음. permitAll/authenticated를 여기서 정함.
- **STATELESS:** 서버가 세션을 안 만든다. 매 요청을 토큰으로 증명 → 확장 유리.
- **401 vs 403:** 401=누구인지 모름(인증 실패), 403=누구인진 알지만 권한 없음.
- **Testcontainers / `@ServiceConnection`:** 테스트 때 진짜 DB를 컨테이너로 잠깐 띄움. `@ServiceConnection`이 접속정보를 스프링에 자동 연결.
- **MockMvc:** 실제 포트 없이 컨트롤러에 가짜 HTTP 요청을 넣어 응답 검증.
- **BOM:** 라이브러리 버전들을 한꺼번에 맞춰주는 "버전 표". Spring Boot가 호환 버전을 관리.

---

## STEP 0 — 오류 사항 및 대처 (유형별 분류)

> 실제로 막혔던 것들. 최신 스택이라 대부분 "옛 방식대로 했더니 안 됨" 류다. **에러 메시지 → 원인 → 대처 → 교훈** 순.

### A. 셸/환경 오류 (zsh)
- **증상:** 헬스 폴링 스크립트의 `status=$(...)` → `read-only variable: status`.
- **원인:** zsh에서 `status`는 예약(읽기전용) 변수(`$?`의 별칭).
- **대처:** 변수명을 `hs`로 변경.
- **교훈:** zsh 스크립트에서 `status`, `path` 같은 예약 변수명 피하기.

### B. 의존성 해결 오류 (Gradle)
- **증상:** `Could not find org.testcontainers:junit-jupiter:.` (버전이 빈 채) → 빌드 실패.
- **원인:** Testcontainers 2.0에서 모듈 좌표가 바뀜. SB4.1 BOM은 `testcontainers-junit-jupiter`/`testcontainers-postgresql`(2.0.5)를 관리하는데, 옛 이름엔 버전이 안 붙어 "버전 없음" 상태가 됨.
- **진단:** spring-boot-dependencies 4.1.0 BOM과 testcontainers-bom 2.0.5의 실제 artifactId 목록을 `curl`로 확인.
- **대처:** 좌표를 `testcontainers-*`로 변경.

### C. 컴파일 오류 (Spring Boot 4 API 이동 / Java 가시성)
- **C-1 증상:** `package org.springframework.boot.test.autoconfigure.web.servlet does not exist`, `cannot find symbol: AutoConfigureMockMvc`.
  - **원인:** SB4 모듈 재편으로 `@AutoConfigureMockMvc`가 `org.springframework.boot.webmvc.test.autoconfigure`로 이동.
  - **진단:** Gradle 캐시의 jar를 뒤져 클래스 실제 경로 확인.
  - **대처:** import 경로 변경.
- **C-2 증상:** `TestcontainersConfiguration is not public ... cannot be accessed from outside package`.
  - **원인:** package-private 클래스를 다른 패키지(`global.health`)의 테스트가 `@Import`.
  - **대처:** 클래스·빈 메서드를 `public`으로.

### D. 런타임/자동설정 오류 — "조용한 실패" (가장 까다로움)
- **증상:** 빌드·기동 모두 성공인데 **Flyway 로그가 0줄**, `flyway_schema_history` 미생성. 에러도 안 남.
- **원인:** SB4가 자동설정을 모듈로 분리 → `flyway-core`만으론 `FlywayAutoConfiguration`이 **등록조차 안 됨**.
- **진단 과정(이 흐름이 핵심):**
  1. `./gradlew dependencies`로 flyway가 런타임 클래스패스에 있는지 → 있음(12.4.0).
  2. spring-boot-dependencies BOM에서 flyway 버전 관리 확인.
  3. `bootRun --args='--debug'`로 **조건평가 리포트(CONDITIONS EVALUATION REPORT)** 출력 → `FlywayAutoConfiguration`이 리포트에 **아예 없음** = 자동설정 후보로 등록 안 됨.
  4. Maven Central에 `spring-boot-flyway` 모듈 존재 확인.
- **대처:** `flyway-core` → `org.springframework.boot:spring-boot-flyway`로 교체. 재기동 시 Flyway 배너 + 히스토리 테이블 생성 확인.
- **교훈:** "에러 없이 그냥 동작 안 함"은 **자동설정 미적용**을 의심하고 `--debug` 조건평가 리포트를 본다.

### E. 비차단 경고 (무해 → 보류 결정)
- **증상:** `TestcontainersConfiguration.java uses ... a deprecated API`.
- **원인:** Testcontainers 2.0에서 일부 생성자 deprecated.
- **대처:** `DockerImageName.parse(...)` 권장형으로 변경(경고 일부 잔존). **테스트 한정·동작 무해라 의도적으로 보류**.

### (보너스) 발견해서 같이 고친 것
- 기존 `application.properties`엔 datasource가 없어 `contextLoads()` 테스트가 **사실상 실패 상태**였다. Testcontainers로 진짜 DB를 붙여 정상화.

---

## STEP 1 — 공통 기반: 전역 예외 처리 ✅ 완료 (커밋: `feat: STEP 1 ...`)

### 목표
잘못된 요청이 들어와도 **앱 전체가 똑같은 형식의 에러 JSON**으로 응답하게 한다(프론트가 의존하는 계약).

### ★ 반드시 알아야 할 핵심
1. **에러 응답 형식을 한 곳에서 통일.** `@RestControllerAdvice`가 모든 컨트롤러의 예외를 가로채 같은 모양의 JSON으로 변환 → 프론트는 항상 `{status, code, message, fieldErrors}`만 보면 됨.
2. **에러는 `ErrorCode` enum 카탈로그로 관리.** 코드·HTTP상태·기본메시지를 한 곳에. 서비스에선 `throw new BusinessException(ErrorCode.XXX)`만.
3. **TDD로 작성.** 테스트(통일 에러 JSON 기대) 먼저 → 실패(RED) → 최소 구현 → 통과(GREEN). "테스트를 먼저 실패시켜 봐야 그 테스트가 진짜 검증하는지 안다."
4. **일부러 덜 만들었다(단순성 우선).** `BaseEntity`+Auditing, 페이징 DTO는 **실제로 쓰는 STEP(2·3)** 에서 엔티티·페이징과 함께 테스트와 같이 추가. 지금 만들면 쓰는 데 없는 죽은 코드.

### 작업 내역
| 파일 | 한 일 | 왜 |
|---|---|---|
| `ErrorCode` | 에러 카탈로그 enum(코드+HTTP상태+메시지) | 에러 정의를 한 곳에, 중복 제거 |
| `BusinessException` | `ErrorCode`를 담는 런타임 예외 | 서비스가 의미 있는 예외를 던지게 |
| `ErrorResponse` | 응답 DTO(record): status/code/message/fieldErrors | 통일된 에러 본문 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`: 비즈니스/검증/그 외(fallback) 처리 | 예외→JSON 변환을 한 곳에서 |
| `GlobalExceptionHandlerTest` | standalone MockMvc로 검증·비즈니스 예외 응답 검증 | 완료기준 자동 증명 |

처리 예외 3종: `BusinessException`(→ ErrorCode의 상태), `MethodArgumentNotValidException`(@Valid 검증 실패 → 400 + 필드별 오류), 그 외 `Exception`(→ 500, 원본 로그).

**실제 에러 응답 예시** (검증 실패 시):
```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "fieldErrors": [ { "field": "name", "reason": "공백일 수 없습니다" } ]
}
```
프론트는 어떤 에러든 이 형식만 보면 된다(성공 응답은 래핑 없이 DTO를 그대로 반환하기로 결정 — error만 표준 envelope).

### 검증
- `./gradlew test` → `GlobalExceptionHandlerTest` 2건 통과(tests=2, failures=0):
  - 검증 실패(POST 빈 `name`) → 400 + `code=INVALID_INPUT` + `fieldErrors[0].field=name`
  - 비즈니스 예외 → 404 + `code=ENTITY_NOT_FOUND`
- DB·서버 없이 도는 빠른 단위 테스트. 실엔드포인트 통합 검증은 STEP 2(signup `@Valid`)에서 자연히 이뤄짐.

### TDD 흐름 (RED → GREEN → REFACTOR)
1. **RED:** 테스트(`GlobalExceptionHandlerTest`)를 먼저 작성 → `./gradlew test`가 **컴파일 실패**(`cannot find symbol: GlobalExceptionHandler / BusinessException / ErrorCode`). "기능이 아직 없음"을 눈으로 확인.
2. **GREEN:** production 4개 클래스를 최소 구현 → 재실행 시 2건 통과(`tests=2, failures=0, skipped=0`).
3. **REFACTOR:** 코드가 이미 최소·명확해 손볼 것 없음.

### 오류/특이사항
- 위 **의도된 RED**(production 부재로 컴파일 실패) 외에 실제로 막힌 오류는 없었다(깨끗한 진행). STEP 0과 달리 별도 "오류 분류" 섹션을 둘 사건이 없음.

### 키워드
- **`@RestControllerAdvice`:** 모든 컨트롤러에 공통 적용되는 예외 처리기.
- **`@ExceptionHandler`:** 특정 예외 타입을 잡아 응답으로 변환하는 메서드 표시.
- **Bean Validation(`@Valid`/`@NotBlank`):** 요청 DTO 제약을 선언, 위반 시 `MethodArgumentNotValidException` 발생.
- **`MethodArgumentNotValidException`:** `@Valid` 본문 검증 실패 시 스프링이 던지는 예외(여기서 필드별 오류 추출).
- **record:** 불변 데이터 객체를 간결히 만드는 Java 문법. Jackson이 JSON으로 직렬화.
- **fallback 핸들러:** 예상 못 한 예외를 500으로 감싸되 **원본은 로그**로 남겨 디버깅 가능하게.
- **standalone MockMvc:** 스프링 컨텍스트 없이 컨트롤러+advice만 올려 빠르게 테스트.

---

## STEP 2 — User & 인증(JWT) ✅ 완료 (커밋: `feat: STEP 2-1/2-2/2-3 ...`)

### 목표
가입·로그인하면 토큰을 받고, 그 토큰으로 보호된 API에 접근한다. 없는/잘못된 토큰은 401로 거부.

### ★ 반드시 알아야 할 핵심
1. **액세스 토큰 = JWT(무상태), 리프레시 토큰 = 불투명 랜덤값(DB 저장).** 액세스는 서명만으로 검증(빠름·무상태), 리프레시는 DB에 두어 **회전·폐기 가능**(로그아웃/탈취 대응). JWT를 리프레시로 쓰면 `iat/exp`가 초 단위라 같은 초 재발급 시 동일 토큰이 되어 충돌 → 저장형은 랜덤 불투명값이 표준.
2. **비밀번호는 BCrypt 단방향 해시로 저장.** 원문 저장 금지. 로그인은 `passwordEncoder.matches(원문, 해시)`로 비교.
3. **인증 흐름:** 요청 → `JwtAuthenticationFilter`가 `Authorization: Bearer <jwt>` 검증 → `SecurityContext`에 사용자(권한 `ROLE_*`) 저장 → 컨트롤러는 `@AuthenticationPrincipal`로 사용자 ID 사용.
4. **401 vs 403:** 토큰 없음/유효하지 않음 = **401**(`RestAuthenticationEntryPoint`), 로그인은 했으나 권한 부족 = **403**. 둘 다 STEP 1의 통일 에러 JSON으로 응답.
5. **엔티티 추가 = Flyway 마이그레이션 동반.** `User`→`V1`, `RefreshToken`→`V2`. `ddl-auto=validate`라 마이그레이션 없으면 기동 실패(STEP 0의 약속대로).
6. **회전(rotation):** 리프레시로 새 토큰쌍을 받으면 **기존 리프레시는 즉시 삭제** → 같은 토큰 재사용 시 401(탈취 토큰 무력화).

### 작업 내역
| 구분 | 핵심 파일 | 한 일 |
|---|---|---|
| 2-1 도메인 | `User`/`Role`/`UserRepository`, `BaseEntity`, `JpaConfig`, `V1` | 사용자 엔티티 + 감사 + 첫 마이그레이션 |
| 2-2 가입·JWT | `AuthService.signup`, `SignupRequest`, PasswordEncoder 빈, `JwtProvider`, `JwtProperties` | BCrypt 가입(중복 검증) + 액세스 JWT 발급/검증 |
| 2-3 로그인·필터 | `AuthService.login/refresh`, `RefreshToken`/Repo/`V2`, `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `SecurityConfig`, `AuthController`, `UserController(/me)` | 로그인·리프레시 회전 + 필터 배선 + 인증 API |

성공 응답은 DTO 직접 반환(`TokenResponse`/`UserResponse`), 에러만 표준 envelope. **권한·자격 검증은 Service**(가입 중복·로그인 자격), **인증은 필터**.

### 검증
- `./gradlew test` → **14개 전부 통과**. 핵심은 `AuthFlowTest` 5건(통합): 가입→로그인→`/api/users/me` 200 / 토큰 없음 401 / 잘못된 토큰 401 / 틀린 비번 `INVALID_CREDENTIALS` 401 / 리프레시 회전(재사용 401).
- **실제 서버(bootRun + curl)** 로도 확인: signup 201 · login 토큰 · me 200 · 무토큰 401 · 중복 409 — 모두 통일 에러 JSON. Flyway `V1`·`V2`가 개발 DB에 적용(v2).

### 오류 사항 및 대처 (유형별) — STEP 2도 최신 스택 함정이 많았다
- **F. 설계 결함 선제 수정:** 리프레시 토큰을 JWT로 만들면 같은 초 재발급 시 동일 토큰→유니크 충돌. → 구현 전에 **불투명 랜덤(SecureRandom 256bit)** 으로 전환(저장형 표준).
- **G. 의존성(또 모듈 분리):** `ObjectMapper` 컴파일 불가 — **SB4는 `spring-boot-starter-webmvc`에 Jackson을 안 넣는다.** → `spring-boot-starter-json` 추가.
- **H. Jackson 3 메이저 변경:** 패키지가 `com.fasterxml.jackson` → **`tools.jackson`** 으로 바뀜(SB4가 Jackson 3.x 사용). import 전부 교체. `JsonNode.asText()`는 deprecated → `asString()`.
- **I. 부실한 테스트 수정:** "토큰 끝에 한 글자 추가" 변조가 검증을 통과(base64url 특성). 코드 버그가 아니라 **테스트의 변조가 무효** → "다른 키로 서명된 토큰 거부"로 교정(진짜 보안 속성 검증).

### 키워드
- **JWT(JSON Web Token):** `헤더.페이로드.서명`. 서명으로 위변조를 막음(비밀 없이 내용은 누구나 디코딩 가능 → 민감정보 넣지 말 것). 우리 액세스 토큰의 subject=userId, role 클레임.
- **BCrypt:** salt가 포함된 단방향 비밀번호 해시. 같은 비번도 매번 다른 해시, 비교는 `matches`.
- **인증(Authentication) vs 인가(Authorization):** 누구냐(필터에서) vs 이거 해도 되냐(`@PreAuthorize`/권한).
- **`OncePerRequestFilter`:** 요청당 한 번 도는 필터. 여기서 JWT를 풀어 `SecurityContext`에 인증을 심는다.
- **`SecurityContext` / `@AuthenticationPrincipal`:** 현재 요청의 인증 주체 보관소 / 컨트롤러에서 그 주체(여기선 userId)를 주입.
- **`@EnableMethodSecurity` / `@PreAuthorize`:** 메서드 단위 권한 검사 활성화(STEP 7에서 본격 사용).
- **`AuthenticationEntryPoint`:** 미인증 요청이 보호 자원에 닿았을 때의 처리(우리는 401 + 통일 JSON).
- **리프레시 회전(rotation):** 리프레시 사용 시 새것 발급 + 기존 폐기. 탈취된 옛 토큰 무력화.
- **`@ConfigurationProperties`:** `jwt.*` yml 설정을 타입 안전 객체(`JwtProperties`)로 바인딩.

---

## STEP 3 — Series(작품) + 분류 ✅ 완료 (커밋: `feat: STEP 3 ...`)

### 목표
작가(CREATOR)가 작품을 등록하고, 인증된 사용자는 요일/연령으로 필터링해 목록·상세를 조회한다.

### ★ 반드시 알아야 할 핵심
1. **연관관계는 LAZY + DTO 변환.** `Series.author`는 `@ManyToOne(LAZY)`. 목록에서 작가 닉네임이 필요하니 **fetch join**으로 한 쿼리에 가져와 N+1 제거(요약 DTO엔 `publishDays`는 빼서 컬렉션 N+1도 회피, 상세에서만 노출).
2. **연재 요일은 `@ElementCollection`.** `Set<DayOfWeek>`를 별도 테이블 `series_publish_days`에 저장. 요일 필터는 JPQL `:day member of s.publishDays`(없으면 전체).
3. **역할 기반 접근은 `@PreAuthorize`.** 등록은 `hasRole('CREATOR')` — READER 토큰이면 403. 거친 역할 게이트는 선언적 보안, 소유권·도메인 규칙은 Service.
4. **페이징은 `Pageable`/`Page` + 표준 `PageResponse`.** 프론트는 `{content, page, size, totalElements, totalPages, last}`만 보면 됨. STEP 1에서 연기했던 페이징 DTO를 실사용과 함께 도입.
5. **엔티티 추가 = 마이그레이션 동반:** `Series`+`series_publish_days` → `V3`.

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `Series`/`AgeRating`/`SeriesStatus`, `V3` | 작품 엔티티(작가 ManyToOne, 요일 ElementCollection) + 마이그레이션 |
| `SeriesRepository.search` | 요일(member of)·연령 동적 필터 + fetch join + 페이징(countQuery 분리) |
| `SeriesService` | 등록(작가 주입)/목록/상세, DTO 변환은 트랜잭션 안에서 |
| `SeriesController` | POST(CREATOR)·GET 목록(필터/페이징)·GET 상세 |
| `PageResponse<T>` | 공통 페이징 응답 |
| `GlobalExceptionHandler` | `AccessDeniedException` → 통일 403 JSON |

### 검증
- `./gradlew test` → **17개 전부 통과**. `SeriesFlowTest` 3건: 등록 후 `day=MONDAY` 1건·`day=TUESDAY` 0건(요일 분류 정확), 독자 등록 403, 상세 조회.
- 실서버: `V3`가 기존 데이터 있는 개발 DB에 적용(v3), `GET /api/series?day=MONDAY` → 200 + 페이징 envelope.

### 오류/특이사항
- **첫 빌드에 GREEN**(막힌 오류 없음). STEP 0~2에서 스택 함정을 이미 잡아둔 효과.
- **주의(중요):** `@PreAuthorize` 거부는 `AccessDeniedException`을 던지는데, 이게 `@RestControllerAdvice`의 fallback(`Exception`→500)에 먼저 걸리면 **403이 500으로 둔갑**한다. → `AccessDeniedException` 전용 핸들러를 추가해 403(통일 JSON)으로 매핑.
- 목록 요약에서 `publishDays`를 일부러 제외해 컬렉션 N+1 회피(상세에서만 로드).

### 키워드
- **`@ManyToOne`(LAZY):** 다대일 연관. 기본 LAZY, 필요 시 fetch join.
- **`@ElementCollection`:** 엔티티 아닌 값들의 컬렉션(요일 집합)을 별도 테이블에 저장.
- **fetch join:** 연관을 한 쿼리로 함께 조회해 N+1 제거. (단, 컬렉션 fetch join + 페이징은 위험 → 여기선 ManyToOne만 fetch.)
- **`member of`:** JPQL에서 "값이 컬렉션에 속하는지" 검사.
- **`Pageable`/`Page`/`@PageableDefault`:** 페이징 요청/결과. page·size·sort를 쿼리파라미터로.
- **`@PreAuthorize("hasRole('CREATOR')")`:** 메서드 실행 전 역할 검사. 실패 시 403.
- **N+1 문제:** 목록 N건마다 연관을 1건씩 더 조회하는 비효율. fetch join/배치로 해결.

---

## STEP 4 — Episode(회차) + 이미지 업로드 ✅ 완료 (커밋: `feat: STEP 4 ...`)

### 목표
작가(CREATOR)가 **본인 작품에** 회차를 등록하며 이미지 여러 장을 한 번에 업로드한다. 검증→리사이즈→로컬 저장→메타 기록까지 한 흐름으로 처리하고, **즉시 발행**과 **예약 발행**(시각 도래 시 자동 공개)을 지원한다.

### ★ 반드시 알아야 할 핵심
1. **멀티파트 업로드 = `@RequestPart`(파일) + `@RequestParam`(폼필드).** `title`/`publishAt`는 폼필드, `images[]`는 파일 파트. JSON 바디(`@RequestBody`)와는 못 섞는다(요청이 `multipart/form-data`).
2. **이미지 파이프라인은 검증→리사이즈→저장→메타.** 검증(jpeg/png·비어있지 않음) → **Thumbnailator**로 폭 800 초과만 축소(이하 원본 유지) → `{root}/{seriesId}/{episodeNo}/{order}.{ext}` 저장 → DB에 경로·width·height 기록. 저장 루트는 `app.storage.root`(env로 분리), `/storage/`는 gitignore.
3. **소유권은 Service에서.** `@PreAuthorize("hasRole('CREATOR')")`는 거친 역할 게이트일 뿐 — "**남의 작품엔 못 올린다**"는 `series.author.id == userId` 검사로 Service가 FORBIDDEN 처리(불변 규칙: 권한·소유권 검증은 Service).
4. **예약 발행은 `@Scheduled` + 테스트 가능한 설계.** `publishAt`이 미래면 `SCHEDULED`로 저장, `EpisodePublisher`가 주기적으로 `publishDueEpisodes(now)`를 호출해 **도래분만** `PUBLISHED`로 전환. 시간 의존 로직을 **`now`를 인자로 받는 메서드로 분리** → 테스트는 스케줄러를 기다리지 않고 직접 호출해 검증한다(`@EnableScheduling` 필요).
5. **회차 번호는 작품별 채번(`max+1`) + 유니크 제약 `(series_id, episode_no)`.** 엔티티 추가 = 마이그레이션 동반 → `V4`(episodes + episode_images).

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `Episode`/`EpisodeImage`/`EpisodeStatus`, `V4` | 회차·이미지 엔티티(`episodeNo` 유니크, 상태 enum STRING) + 마이그레이션 |
| `ImageStorageService` | 검증·Thumbnailator 리사이즈·로컬 저장, 저장경로/치수 반환 |
| `EpisodeService` | 업로드(소유권 검증·채번·즉시/예약 분기), 상세/발행목록, `publishDueEpisodes(now)` |
| `EpisodePublisher`+`SchedulingConfig` | `@Scheduled`로 도래한 예약 회차 자동 발행, `@EnableScheduling` |
| `EpisodeController` | POST 업로드(멀티파트, CREATOR)·GET 상세(이미지 순서)·GET 발행목록 |
| dto 3종 | `EpisodeDetail`/`Summary`/`ImageResponse` (Entity 대신 DTO 노출) |
| `ErrorCode` | `INVALID_IMAGE`(400)·`STORAGE_FAILED`(500) 추가 |
| `build.gradle`/`application.yml` | thumbnailator 의존성 / `app.storage.root` |

### 검증
- `./gradlew test` → **21개 전부 통과**(신규 `EpisodeFlowTest` 4 + 기존 17). `contextLoads`로 `@EnableScheduling`·Publisher·StorageService 빈 배선 정상 기동 확인.
  - 다중 업로드 시 **순서대로 저장** + 1200폭 이미지 **800으로 리사이즈**(400폭은 원본 유지)
  - 독자 업로드 403, **다른 작가가 남의 작품** 업로드 403
  - 예약 업로드는 `SCHEDULED`·발행목록 제외 → `publishDueEpisodes(도래시각)` 후 `PUBLISHED`·목록 노출

### 오류/특이사항
- **TDD RED→GREEN.** 엔드포인트 없을 땐 404로 먼저 실패(행위적 RED)시키고 구현, 예약발행은 `publishDueEpisodes` 미존재 **컴파일 실패(RED)** 후 구현.
- **`@RequestParam Instant` 바인딩:** ISO-8601 문자열(`Instant.toString()`)이 별도 애너테이션 없이 변환됨(Spring `InstantFormatter`).
- **`EpisodeImage`는 `BaseEntity` 미상속.** 불변 메타라 created/updated 불필요 → `episode_images` 테이블엔 타임스탬프 컬럼 없음.
- **(의도적 범위 한계)** 이미지 바이트 **서빙은 보류**(저장+메타+경로까지). 독자가 `SCHEDULED` 회차 **상세**는 조회 가능(상태만 노출) → 발행 전 숨김은 후속 과제.

### 키워드
- **멀티파트(`multipart/form-data`):** 파일+필드 혼합 전송. `@RequestPart`(파일/파트), `@RequestParam`(폼필드).
- **Thumbnailator:** 자바 이미지 리사이즈 라이브러리. `Thumbnails.of(img).width(n)` 비율 유지 축소.
- **`@Scheduled`/`@EnableScheduling`:** 주기 실행. `fixedDelayString`으로 폴링 간격 설정.
- **채번(`max+1`):** 작품별 회차 일련번호. DB 유니크 제약으로 중복 방지.
- **발행 상태(DRAFT/SCHEDULED/PUBLISHED):** `publishAt` 도래 시 SCHEDULED→PUBLISHED 전환.

---

## STEP 5 — 뷰어 조회 API(무한스크롤·19금 가드) ✅ 완료 (커밋: `feat: STEP 5 ...`)

### 목표
독자가 작품의 **발행 회차를 무한스크롤(`Slice`)로** 넘겨보고, 회차 상세에서 이미지를 **순서·url·크기**와 함께 받는다. **19금(AGE_19)** 작품은 만 19세 이상만 열람한다.

### ★ 반드시 알아야 할 핵심
1. **19금 가드는 "데이터"가 먼저다.** `User`에 나이 정보가 없어 실제 성인 검증이 불가능했다 → `birthDate`(생년월일)를 추가(`V5`)하고 회원가입(`signup`)에서 받는다. 가드는 `User.isAdult(today)`(만 19세 이상)로 판정. **권한·소유권 검증은 Service**(불변 규칙)라 `EpisodeService`에서 `AgeRating==AGE_19`일 때만 뷰어를 조회해 검사, 미성년이면 `ADULT_ONLY`(403).
2. **무한스크롤은 `Page`가 아니라 `Slice`.** 전체 개수(`count` 쿼리) 없이 `hasNext`만 필요 → `Slice<Episode>` + `Pageable`. 공통 `SliceResponse{content, page, size, hasNext}`로 감싸 응답(전체 카운트 쿼리 비용 회피).
3. **응답 필드는 계약이다.** STEP 4의 목록 응답을 단순 `List`→`Slice` 형태로 바꾸면 프론트가 보던 JSON 모양(`[...]`→`{content:[...]}`)이 달라진다. 기존 테스트의 `$.length()`·`$[0]`도 `$.content.length()`·`$.content[0]`로 함께 수정(계약 변경의 파급).
4. **시그니처 변경의 파급.** `User.create`에 `birthDate`를 필수로 넣으니 호출부 6곳(테스트 5 + `AuthService`)이 전부 바뀐다. 내 변경으로 깨진 곳만 최소 수정(테스트엔 성인 기준일 상수 주입).
5. **이미지 서빙은 여전히 보류.** 상세의 `url`은 저장된 **상대 경로**를 그대로 노출(프론트가 base URL 결합). 정적 서빙은 보안설정/연령 직링크 이슈가 있어 후속.

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `User`(+`birthDate`, `isAdult`), `V5` | 생년월일 컬럼(레거시 NULL=미성년 취급) + 만 19세 판정 |
| `SignupRequest`/`AuthService` | 가입 시 `@NotNull @Past birthDate` 수집·저장 |
| `EpisodeRepository` | 목록을 `Slice<Episode> ...(Pageable)`로 변경 |
| `EpisodeService` | `getEpisodes`(Slice+가드), `getDetail`(가드), `verifyAgeAccess` |
| `SliceResponse<T>` | 무한스크롤 공통 응답(content/page/size/hasNext) |
| `EpisodeImageResponse` | `path` → `url` 노출명 변경 |
| `EpisodeController` | 목록/상세에 `@AuthenticationPrincipal`·`Pageable` 주입 |
| `ErrorCode` | `ADULT_ONLY`(403) 추가 |

### 검증
- `./gradlew test` → **26개 전부 통과**(신규 `EpisodeViewerTest` 4 + `AuthServiceTest` 생년월일 1 + 기존).
  - `size=2`로 회차 3개 중 2개 + `hasNext=true`(무한스크롤)
  - 상세에서 이미지 `url`·`width`·`height` 동반
  - 미성년 토큰 → 19금 목록/상세 **403 `ADULT_ONLY`**, 성인 토큰 → **200**

### 오류/특이사항
- **설계 막힘 → 데이터 모델 확장으로 해결.** "19금 가드"가 `User`에 나이가 없어 불가능 → 임의 가정 대신 *생년월일 추가(정석)*을 선택해 진행(STEP 2 일부 동반 수정).
- **TDD.** signup 생년월일 저장(컴파일 RED) → 구현, 뷰어 Slice/url/가드(행위적 RED, 단 "성인 200"은 가드 전에도 통과하는 회귀 보호 테스트) → 구현.
- **`Slice` vs `Page`:** `Slice`는 `count` 쿼리를 날리지 않아 다음 페이지 유무만 알면 되는 무한스크롤에 적합.

### 키워드
- **`Slice`/`Page`:** `Page`=전체 개수 포함(추가 count 쿼리), `Slice`=`hasNext`만(무한스크롤용, 더 가벼움).
- **만 나이 가드:** `birthDate <= 기준일 − 19년` 이면 성인. 생년월일 없으면 보수적으로 미성년.
- **`@AuthenticationPrincipal`:** 현재 요청자(userId) 주입 → 연령 가드에서 조회자 식별.
- **계약(Contract) 변경:** 응답 JSON 모양이 바뀌면 그 모양에 의존하던 테스트/프론트도 함께 바뀌어야 함.
- **`@Past`/`@NotNull`(검증):** 생년월일은 과거 날짜 + 필수.

---

## STEP 6 — 개인화(구독·읽음·UP) ✅ 완료 (커밋: `feat: STEP 6 ...`)

### 목표
독자가 작품을 **구독**하고 회차를 **읽음 처리**한다. 구독 목록에선 **안 본 새 회차가 있으면 UP**(읽으면 꺼짐), 마지막 읽은 회차로 **이어보기**를 안내한다.

### ★ 반드시 알아야 할 핵심
1. **UP은 "시각"이 아니라 "회차번호"로.** 계획서 정의(`최신 발행 시각 > 마지막 읽음 시각`)는 **예약발행(미래 publishAt) 시 읽어도 UP가 안 꺼지고**, 밀리초 충돌로 비결정적이다. → `최신 발행 회차번호 > 마지막 읽은 회차번호`로 구현. "읽으면 사라짐"을 결정적으로 만족하고, 같은 데이터로 이어보기(마지막 읽은 화)도 제공.
2. **멱등(idempotent) 설계.** 중복 구독/중복 읽음은 에러가 아니라 **무시**(이미 있으면 그대로). 유니크 제약 `(user,series)`·`(user,episode)`로 DB에서도 한 번 더 보장.
3. **N+1 회피 = 배치 집계.** 구독 N건마다 "최신 회차"·"읽은 회차"를 따로 조회하면 N+1 → `... where series_id in :ids group by series_id` 한 쿼리로 묶어 `Map`으로. STEP 3에서 세운 N+1 경계 유지.
4. **인터페이스 프로젝션.** 집계 결과 `(seriesId, maxNo)`만 `SeriesMaxNo` 인터페이스 getter로 매핑(JPQL 별칭 `as seriesId/as maxNo` ↔ `getSeriesId()/getMaxNo()`). 엔티티 통째 로딩 없이 필요한 두 값만.
5. **연관은 LAZY + `getReferenceById`.** 구독/읽음 insert엔 User·Series·Episode의 **프록시 참조**만 있으면 됨 → 불필요한 SELECT 없이 FK만 채운다. 엔티티 추가 = 마이그레이션 → `V6`.

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `Subscription`/`ReadLog`, `V6` | 구독·읽음 엔티티(각 유니크 제약) + 마이그레이션 |
| `SubscriptionRepository` | exists/delete + 구독목록(`join fetch series`) |
| `ReadLogRepository` | exists + 작품별 **마지막 읽은 회차** 배치 집계 |
| `EpisodeRepository` | 작품별 **최신 발행 회차** 배치 집계 추가 |
| `SeriesMaxNo`(projection) | `(seriesId, maxNo)` 집계 프로젝션(공통) |
| `PersonalizationService` | 구독/취소(멱등)·읽음(멱등)·UP 계산(배치 2회) |
| `PersonalizationController` | 구독·취소·읽음·내 구독목록 API |
| `SubscriptionResponse` | seriesId/title/latestEpisodeNo/lastReadEpisodeNo/up |

### 검증
- `./gradlew test` → **28개 전부 통과**(신규 `PersonalizationFlowTest` 2 + 기존 26).
  - 구독 → 1화 미열람 `up=true` → 1화 읽음 `up=false` → 2화 발행 `up=true` → 2화 읽음 `up=false`(latest/lastRead 회차번호 동반)
  - 구독취소 → 목록에서 사라짐

### 오류/특이사항
- **계획서 UP 정의(시각 비교)를 회차번호 비교로 교체.** 사유: 예약발행 미래시각 시 "읽어도 UP 유지" 결함 + 밀리초 충돌 비결정성. 임의 변경이 아니라 *더 정확·결정적*이라 판단해 명시 후 진행.
- **TDD.** 엔드포인트 부재 404(행위적 RED) → 구현 GREEN.
- **멱등을 양쪽에서.** 코드(`exists` 선검사) + DB(유니크 제약) 모두로 중복 방지.

### 키워드
- **UP 플래그:** 구독 작품에 안 본 새 회차가 있음(`최신 발행 회차 > 마지막 읽은 회차`).
- **멱등(idempotent):** 같은 요청을 여러 번 해도 결과 동일(중복 구독/읽음 무시).
- **`getReferenceById`:** DB 조회 없이 프록시 참조만 얻어 FK 설정에 사용.
- **인터페이스 프로젝션:** 쿼리 결과의 일부 컬럼만 인터페이스 getter로 매핑(가벼움).
- **배치 집계(`group by` + `in`):** 작품 묶음을 한 쿼리로 집계해 N+1 제거.

---

## STEP 7 — 관리자 기능(ADMIN) ✅ 완료 (커밋: `feat: STEP 7 ...`)

### 목표
관리자(ADMIN)만 **작가 권한 부여**(역할 변경)·**작품 연령등급 변경**·**작품 공개/비공개**를 처리한다. 비관리자는 403.

### ★ 반드시 알아야 할 핵심
1. **거친 역할 게이트는 `@PreAuthorize`, 클래스 레벨로.** `AdminController`에 클래스 단위 `@PreAuthorize("hasRole('ADMIN')")` 하나면 **모든 메서드**가 ADMIN 전용이 된다(메서드마다 안 붙여도 됨). 비관리자 → `AccessDeniedException` → STEP 3에서 만든 핸들러가 **통일 403 JSON**으로.
2. **상태 변경은 도메인 메서드로(setter 남발 금지).** 엔티티에 `changeRole`·`changeAgeRating`·`changeVisibility`만 열어 의미 있는 변경점만 노출. JPA **더티 체킹**이 `@Transactional` 종료 시 UPDATE를 자동 생성(별도 save 불필요).
3. **"작품 공개"는 목록 노출로 한정(MVP 경계).** `Series.visible`(기본 `true`) 추가 + 공개 목록 쿼리(`search`)에 `and s.visible = true`. **기본 true라 STEP 3~6 동작·테스트 전부 불변**. 상세 직접조회·회차 게이팅까지 숨기는 건 후속.
4. **새 컬럼 = 마이그레이션 + 기존행 보존.** `V7`는 `add column visible boolean not null default true` → 기존 작품은 자동 공개. `ddl-auto=validate`라 컬럼·엔티티 일치 필요.
5. **PATCH = 부분 수정.** 리소스 일부 필드만 바꾸는 의미. CSRF는 STATELESS라 비활성(토큰 인증).

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `User.changeRole` | 역할 변경 도메인 메서드 |
| `Series`(+`visible`), `changeAgeRating`/`changeVisibility`, `V7` | 공개여부 필드(기본 true) + 변경 메서드 + 마이그레이션 |
| `SeriesRepository.search` | 공개 목록에 `s.visible = true` 필터(query+countQuery) |
| `AdminService` | 역할/연령등급/공개여부 변경(더티 체킹) |
| `AdminController` | 클래스 레벨 `@PreAuthorize(ADMIN)` + PATCH 3종 |
| dto 3종 | `Role`/`AgeRating`/`Visibility`UpdateRequest(`@NotNull`) |

### 검증
- `./gradlew test` → **32개 전부 통과**(신규 `AdminFlowTest` 4 + 기존 28). `SeriesFlowTest` 3건 그대로 → 기본 공개라 기존 동작 보존.
  - 비관리자(CREATOR/READER) → admin API **403**
  - 관리자 → 연령등급 변경 200(`ageRating=AGE_19`), 역할 부여 200(`role=CREATOR`)
  - 관리자가 비공개 처리 → 공개 목록 `totalElements` 1 → 0

### 오류/특이사항
- **TDD.** 엔드포인트 부재 시 비관리자 요청은 404(403 아님)로 먼저 실패(행위적 RED) → 구현 후 403/200.
- **범위 해석 명시:** "작품 공개"를 *브라우즈 목록 노출* 범위로 한정(상세 by-id·회차는 후속). 임의 결정 대신 가장 파급 적은 선택.
- **`visible` 기본 true** 라 `Series.create` 시그니처 불변 → STEP 3~6 호출부/테스트 영향 없음(STEP 5 생년월일 때와 달리 파급 0).

### 키워드
- **클래스 레벨 `@PreAuthorize`:** 컨트롤러 전체 메서드에 같은 권한 규칙 일괄 적용.
- **도메인 메서드 vs setter:** 의미 있는 변경점(`changeRole`)만 노출해 무분별한 상태 변경 차단.
- **더티 체킹(dirty checking):** 영속 엔티티의 변경을 트랜잭션 커밋 때 자동 UPDATE.
- **PATCH:** 리소스의 일부만 부분 수정하는 HTTP 메서드.
- **소프트 가시성(visible):** 삭제하지 않고 노출만 끄는 공개/비공개 플래그.

---

## STEP 8 — 문서화(springdoc/OpenAPI) ✅ 완료 (커밋: `feat: STEP 8 ...`)

### 목표
실행 중인 컨트롤러에서 **OpenAPI 문서를 자동 생성**해 Swagger UI로 보고, `openapi.json`으로 프론트 타입 자동생성의 기반을 만든다.

### ★ 반드시 알아야 할 핵심
1. **이 스택이라 "버전부터" 검증.** 계획서가 *"springdoc은 호환 버전 확인 후 투입"*이라 적어둔 대로, 기억이 아니라 **Maven Central**로 확인 → springdoc **2.x는 Boot3용, 3.0.x가 Boot4용**(POM이 SB4 모듈명 `spring-boot-tomcat`·`spring-boot-health` 참조)임을 보고 `3.0.3` 채택. 최종 확인은 빌드+테스트. (우려와 달리 정상 동작)
2. **문서 경로는 인증 예외로.** "전부 인증" 정책이라 `/v3/api-docs`·`/swagger-ui`를 `SecurityConfig`에서 `permitAll` 안 하면 문서도 401로 못 본다.
3. **자동 생성 = 코드가 진실.** 컨트롤러/DTO를 스캔해 문서를 만들어 코드-문서 불일치가 없다. JWT 보안 스킴(`bearerAuth`)을 `OpenAPI` 빈에 등록 → Swagger에서 토큰 넣고 호출 가능.
4. **설정성 작업도 자동 검증.** TDD처럼 "`GET /v3/api-docs` 200 + 주요 경로 포함" 통합 테스트로 문서 생성 자체를 회귀 보호.

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `build.gradle` | `springdoc-openapi-starter-webmvc-ui:3.0.3` (SB4.1 호환) |
| `SecurityConfig` | `/v3/api-docs/**`·`/swagger-ui/**` permitAll |
| `OpenApiConfig` | `OpenAPI` 빈(title/version + JWT bearer 스킴) |
| `OpenApiDocsTest` | 문서 생성·주요 경로 포함 검증 |

### 검증
- `./gradlew test` → **33개 전부 통과**(신규 `OpenApiDocsTest` 1 + 기존 32). `/v3/api-docs` 200 + `openapi`/`info.title` 필드 + `/api/series`·`/api/auth/signup` 경로 포함.
- (수동) `bootRun` 후 `/swagger-ui/index.html`에서 전체 API 열람·호출 가능.

### 오류/특이사항
- **호환성 결론:** 우려와 달리 springdoc **3.0.3이 SB4.1과 정상 동작**(빌드·테스트 통과). "옛 버전(2.x)은 Boot3용"이라는 스택 함정만 피하면 됨 — `start.md`가 STEP 8로 미뤄둔 이유가 바로 이 검증.
- 설정 위주라 RED는 "문서 경로 부재 시 인증 실패(401)" → 의존성+permit+빈 추가 후 GREEN.

### 키워드
- **OpenAPI / Swagger:** REST API를 기계가 읽는 규격(JSON)으로 기술. Swagger UI는 그 문서를 사람이 보는 화면.
- **springdoc:** 런타임에 컨트롤러를 스캔해 OpenAPI 문서를 자동 생성하는 라이브러리.
- **`/v3/api-docs`:** 생성된 OpenAPI JSON 엔드포인트(프론트 타입 생성의 입력).
- **보안 스킴(securityScheme):** 문서에 인증 방식(JWT Bearer)을 기술 → Swagger에서 토큰 넣고 호출.
- **코드-우선 문서화:** 코드에서 문서를 뽑아 수기 문서 대비 불일치 제거.

---

## STEP 9 — 회차 이미지 정적 서빙(/files) ✅ 완료 (커밋: `feat: STEP 9 ...`)

### 목표
STEP 4·5에서 **보류**했던 이미지 서빙을 닫는다. 저장만 하고 HTTP로 내려주지 못하던 회차 이미지를 `/files/**`로 정적 서빙해 **뷰어가 실제로 렌더**되게 한다. (재설계 워크플로 결론: 뷰어 코어가 이미지 서빙 부재로 완전 비동작 → 단독 최우선.)

### ★ 반드시 알아야 할 핵심
1. **정적 리소스 핸들러가 코드에 전무했다.** `WebMvcConfigurer` 구현(`WebConfig`)으로 `/files/**` → `file:${app.storage.root}/` 매핑. 저장 경로 규약(`{root}/{seriesId}/{episodeNo}/{order}.{ext}`)을 그대로 URL 경로로 노출. `EpisodeImageResponse.of()`가 저장 상대경로 앞에 **`/files/` 접두**를 붙여 프론트가 바로 GET할 절대경로를 준다(STEP 5의 "url=상대경로" 미결을 종결).
2. **permitAll 트레이드오프(명시).** `/files/**`를 `SecurityConfig`에서 `permitAll` → URL을 알면 **비공개/성인물 이미지도 인가 없이 직접 접근** 가능. 학습 범위상 뷰어 즉시 동작을 우선해 정적 서빙 채택, 실제 문제화 시 **컨트롤러 기반 인증 서빙으로 승격**(보류). 디렉터리 트래버설(`../`)은 Spring `PathResourceResolver`가 location 하위로 제한해 차단.
3. **미존재 정적 리소스 500→404 교정(TDD가 드러낸 결함).** `/files/없는파일`이 **500**으로 응답했다 — `GlobalExceptionHandler`의 catch-all(`Exception`→500)이 Spring의 `NoResourceFoundException`을 삼켰기 때문. 전용 핸들러를 추가해 **404(`ENTITY_NOT_FOUND`)**. 부수효과로 **알 수 없는 라우트**(예: 오타 `/api/...`)도 500→404로 정상화(프론트가 "없음"과 "서버오류"를 구분 가능).
4. **마이그레이션 없음.** 스키마 변경 0, `app.storage.root` 설정만 사용. 기존 33개 테스트 회귀 없음(STEP 5의 `EpisodeViewerTest`는 `url` 존재만 단언 → 절대경로로 바뀌어도 통과).

### 작업 내역
| 파일 | 한 일 | 왜 |
|---|---|---|
| `WebConfig`(신규, `WebMvcConfigurer`) | `/files/**` → `file:${app.storage.root}/` 리소스 핸들러 + 트래버설 차단 주석 | 저장 이미지를 HTTP로 서빙 |
| `SecurityConfig` | `/files/**` `permitAll` 한 줄 | 뷰어 이미지는 비인증 GET(트레이드오프 명시) |
| `EpisodeImageResponse.of()` | `url`에 `/files/` 접두 | 프론트가 바로 GET할 절대경로 계약 |
| `GlobalExceptionHandler` | `@ExceptionHandler(NoResourceFoundException)` → 404 | catch-all이 미존재 리소스를 500으로 삼키던 것 교정 |
| `EpisodeImageResponseTest`(신규, 단위) | `of()`가 `/files/{path}` 반환 검증 | url 계약을 Spring 없이 빠르게 고정 |
| `StaticFileServingTest`(신규, 통합) | 비인증 서빙 200·바이트 일치, 미존재 404(401 아님) | permitAll+핸들러 배선·404 회귀 보호 |

### 검증
- `./gradlew test` → **36개 전부 통과**(신규 단위 1 + 통합 2 + 기존 33, failures=0).
- **RED 3건 확인**(의도된 실패): 단위 `expected "/files/1/2/0.png" but was "1/2/0.png"`, 통합 서빙 `expected 200 but was 401`(보안 차단), 미존재 `expected 404 but was 401`. → 구현 후 GREEN.
- 통합 테스트는 `@DynamicPropertySource`로 `app.storage.root`를 임시 디렉터리로 지정하고 실제 파일을 써서 서빙을 검증(`@TempDir` 정적 필드 순서 함정 회피).

### 오류/특이사항
- **TDD가 잠복 결함을 드러냄.** "미존재 → 404" 테스트가 처음엔 **500**으로 실패 → catch-all이 `NoResourceFoundException`을 삼키는 구조를 발견 → 전용 404 핸들러로 교정(STEP 3의 `AccessDeniedException`→403 교정과 동일한 패턴: catch-all보다 **구체 핸들러**를 먼저).
- **단위 테스트 격리:** `EpisodeImage.create(null, 0, "1/2/0.png", 800, 1200)` — `of()`는 `episode`를 안 읽으므로 연관(Episode→Series→User) 없이 path/크기만으로 검증 가능.
- **content-type은 확장자 기반.** 정적 서빙 시 `.png`는 `MediaTypeFactory`가 `image/png`로 판정(파일 내용과 무관) → 테스트는 임의 바이트로도 content-type 검증 가능.

### 키워드
- **`WebMvcConfigurer`/`addResourceHandlers`:** URL 패턴(`/files/**`)을 파일 위치(`file:...`)에 매핑하는 정적 리소스 설정 훅.
- **`addResourceLocations`:** 리소스를 찾을 베이스(끝에 `/` 필요). 우리는 `Path.toUri()`로 절대경로 + 트레일링 슬래시 보장.
- **`PathResourceResolver`:** 요청 경로를 location 하위로 정규화·제한 → 디렉터리 트래버설 차단.
- **`NoResourceFoundException`:** 매핑된 핸들러/정적 리소스가 없을 때 Spring이 던지는 예외(전용 처리 없으면 catch-all로 500 둔갑).
- **`@DynamicPropertySource`:** 컨텍스트 기동 전 프로퍼티를 동적 주입(임시 storage 루트 지정).

---

## STEP 10 — Entity-DTO 명확화 + 상세 가드 ✅ 완료 (커밋: `feat: STEP 10-1/10-2 ...`)

### 목표
재설계 워크플로 결론에 따라 **(1) Entity↔DTO 관계를 명확히** 하고(누락 필드·약타입 보강 + 매핑 명세), **(2) 목록에서 빠진 비공개·미발행 콘텐츠가 ID 직접접근으로 노출되던 계약 구멍을 닫는다**(상세 가드). 프론트가 의존하는 응답 계약을 정합하게 만든다.

### ★ 반드시 알아야 할 핵심
1. **누락 필드는 "관리자 기능의 의미 구멍"이었다.** `Series.visible`이 `SeriesResponse`에 없어, 관리자가 공개/비공개를 바꿔도(`AdminService.changeSeriesVisibility`) 응답에 결과가 안 드러났다 → `visible` 추가로 변경 결과를 응답에 노출.
2. **생성 응답을 `Map.of` → record로.** `Map.of("id", n)`/`Map.of("episodeNo", n)`는 JSON은 같아도 OpenAPI에 **명명 스키마가 안 생겨** 프론트 타입 자동생성이 부실해진다 → `IdResponse(Long id)`(범용·global/dto), `EpisodeNoResponse(int episodeNo)`(episode/dto) record로 교체. **JSON 계약 동일이라 회귀 0**(리팩토링).
3. **민감정보는 단순성과 프라이버시 둘 다로 미노출.** `birthDate`를 공용 `UserResponse`에 넣으면 `/me`뿐 아니라 `AdminService.changeUserRole`도 같은 DTO라 **관리자가 타인 생년월일을 보게 된다** → 비노출 유지 + 의도 주석. 본인 프로필 표시가 필요해지면 `/me` 전용 응답으로 분리(YAGNI).
4. **목록-상세 계약 일치 = 가드는 Service에서.** 목록(`search`)은 `visible=true`만, 회차 목록은 `PUBLISHED`만 보여주는데, **상세 by-id는 그 필터를 우회**해 비공개 작품·예약(SCHEDULED) 회차가 직접조회로 노출됐다 → `getDetail`에 가드 추가(불변 규칙: 권한·소유권 검증은 Service). **비공개/미발행은 "존재를 숨겨" 404(403 아님)** — 정보 누출 최소화.
5. **프리뷰 주체 = 작가 본인 + ADMIN.** 비공개/미발행도 작가 본인(`series.isAuthoredBy(viewerId)`)·ADMIN은 프리뷰 허용. ADMIN 판정은 컨트롤러에서 `Authentication`의 `ROLE_ADMIN` 권한을 추출해 Service에 boolean으로 전달(principal=userId는 role을 모르므로). 이 정책 변경으로 STEP 4의 "독자도 예약 회차 상세 조회 가능"이 **작가 프리뷰로 교체**됨(EpisodeFlowTest 수정).

### Entity → DTO 매핑표 (사용자 요구: "관계를 명확히 구분")
| 엔티티 | 테이블 | 응답 DTO | 요청 DTO | 노출 정책 |
|---|---|---|---|---|
| `User` | users | `UserResponse`(id·email·nickname·role) | `SignupRequest`·`LoginRequest` | **password·birthDate 미노출**(민감) |
| `RefreshToken` | refresh_tokens | — (내부 전용) | — | 토큰은 `TokenResponse`로만 |
| `Series` | series | `SeriesResponse`(+**visible**), `SeriesSummaryResponse` | `SeriesCreateRequest` | 목록 요약엔 publishDays 제외(N+1 회피) |
| `Episode` | episodes | `EpisodeDetailResponse`, `EpisodeSummaryResponse` | 멀티파트 폼(title·publishAt·images) | 생성 응답 `EpisodeNoResponse` |
| `EpisodeImage` | episode_images | `EpisodeImageResponse`(detail에 중첩) | — | path→**url**(`/files/` 변환, STEP 9) |
| `Subscription` | subscriptions | `SubscriptionResponse`(집계 조합) | — | of() 없이 Service 조합(3집계값) |
| `ReadLog` | read_logs | — (내부 전용) | — | 읽음/UP 계산 입력 |
| (생성 공통) | — | **`IdResponse`**, **`EpisodeNoResponse`** | — | Map.of 대체 |

**DTO 규약(이번에 명문화):** ① 엔티티 직접 노출 금지, `of(entity)` 정적 팩토리로 매핑 캡슐화(집계 조합형은 `of(entity, 집계인자)` 허용). ② 단일 도메인 전용 요청/응답은 `domain/{x}/dto`, 여러 도메인 공유 범용 래퍼(`PageResponse`/`SliceResponse`/`IdResponse`)만 `global/dto`. ③ 민감값(password·birthDate)은 응답 화이트리스트에서 제외. ④ 엔티티명-필드명은 의미가 같으면 동일 명명, 의도적 변환(path→url)은 변환 로직을 `of()`에 둔다.

### 작업 내역
| 단계 | 파일 | 한 일 |
|---|---|---|
| 10-1 | `SeriesResponse`(+visible), `IdResponse`(신규), `EpisodeNoResponse`(신규), `Series/EpisodeController` | visible 매핑 + 생성응답 record화 |
| 10-1 | `UserResponse` | birthDate 비노출 의도 주석 |
| 10-2 | `Series.isAuthoredBy(userId)` | 작가 본인 판정 도메인 메서드 |
| 10-2 | `SeriesService.getDetail`, `EpisodeService.getDetail/getEpisodes`(+`verifyVisibleAccess`) | 비공개·미발행 가드(작가/ADMIN 프리뷰) |
| 10-2 | `Series/EpisodeController` | `Authentication`에서 ROLE_ADMIN 추출 전달 |
| 테스트 | `SeriesResponseTest`(신규), `ViewerGuardTest`(신규 6), `AdminFlowTest`·`SeriesFlowTest`·`EpisodeFlowTest`(보강/정책수정) | visible 매핑·가드·프리뷰 검증 |

### 검증
- `./gradlew test` → **43개 전부 통과**(10-1 후 37, 10-2 후 +`ViewerGuardTest` 6 = 43).
  - 비공개 작품 상세/회차목록: 타인 **404**, 작가 본인·ADMIN **200**
  - 미발행(SCHEDULED) 회차 상세: 타인 **404**, 작가 본인 **200**(status=SCHEDULED 프리뷰)
  - 관리자 비공개 처리 응답에 `visible=false` 노출
- **RED→GREEN.** 10-1은 `visible()` 부재 컴파일 RED, 10-2는 타인 직접조회가 200→404로 바뀌는 행위적 RED 4건 확인 후 구현.

### 오류/특이사항
- **STEP 4 정책 교체 명시.** STEP 4 노트의 "독자가 SCHEDULED 상세 조회 가능"을 **작가 프리뷰로 교체** — `EpisodeFlowTest`의 해당 단언을 creator 토큰(200)+reader(404)로 수정. 임의 변경이 아니라 "목록-상세 계약 일치"라는 사용자 요구(프론트 효율) 반영.
- **(의도적 범위 한계) 비로그인 `viewerId=null` 가드는 추가하지 않음.** `SecurityConfig`상 `/api/series/**`는 `authenticated()` 필수(permitAll은 health·auth·files·swagger뿐)라 viewerId는 **항상 non-null** → `verifyAgeAccess`의 `findById(null)` 경로는 현재 도달 불가능. CLAUDE.md "발생 불가능 시나리오 예외처리 금지(YAGNI)"에 따라 보류. **STEP 11에서 비로그인 브라우징(`/api/series` permitAll)을 열면 그때 가드 추가 필요.**
- **ADMIN 판정 중복.** `hasAdminRole(Authentication)`이 Series/Episode 컨트롤러 2곳에 복제됨. 3번째가 생기면 공통 유틸로 추출(YAGNI, 현재는 복제 유지).
- **CLAUDE.md 미수정.** 계획의 "CLAUDE.md에 DTO 규약 한 줄"은 CLAUDE.md가 사용자 소유 행동지침 파일이라 임의 수정하지 않고, 도메인 규약은 본 학습노트에 명문화.

### 키워드
- **목록-상세 계약 일치:** 목록 필터(visible/PUBLISHED)와 상세 by-id 접근 정책을 같게 — 한쪽만 막으면 우회 노출.
- **존재 숨김 404 vs 403:** 비공개 리소스는 "없는 것처럼" 404로 응답해 존재 자체를 숨김(403은 "있지만 권한 없음"이라 존재가 드러남).
- **프리뷰(preview):** 발행/공개 전 콘텐츠를 작성자·관리자가 미리 보는 것. 일반 사용자에겐 404.
- **`Authentication.getAuthorities()`:** 현재 인증 주체의 권한(`ROLE_*`) 목록. principal(userId)과 별개로 역할 판정에 사용.
- **record 리팩토링:** 약타입(`Map`) 응답을 명명 record로 — JSON은 같아도 OpenAPI 스키마·타입 안정성 확보.

---

## STEP 11 — 프론트 효율: 작가 본인작·제목검색·상세집계 ✅ 완료 (커밋: `feat: STEP 11-1/11-2/11-3 ...`)

### 목표
프론트가 화면을 **적은 요청으로** 그릴 수 있게 세 가지를 보강한다 — (11-1) 제목 **검색**, (11-2) 작가 자기작 목록 `GET /api/series/mine`(비공개 포함), (11-3) 작품 상세에 **발행회차수·최신회차번호·구독여부** 동봉. 모두 기존 인프라(`search` 동적 필터, `existsByUserIdAndSeriesId`, 배치 집계 패턴) 재사용.

### ★ 반드시 알아야 할 핵심
1. **null 파라미터 like 검색은 `cast(:keyword as string)` 필수.** `search`에 `and (:keyword is null or lower(s.title) like lower(concat('%', :keyword, '%')))`를 넣었더니 keyword=null인 기존 호출이 **500**(`function lower(bytea) does not exist`) — Hibernate가 null 파라미터 타입을 못 정해 PostgreSQL이 `bytea`로 추론한 탓. `cast(:keyword as string)`로 타입을 명시해 해결. **동적 필터에 null+문자열 함수를 쓰면 캐스팅으로 타입을 고정**.
2. **구체 경로가 경로변수보다 우선 — `/api/series/mine`은 `/{id}`와 충돌 안 함.** Spring은 정확 매칭(`/mine`)을 패턴(`/{id}`)보다 우선 라우팅하므로 별도 컨트롤러 없이 `SeriesController`에 둔다. 작가 자기작은 **visible 무관(hidden 포함)** 조회 → `SeriesSummaryResponse`에 `visible` 추가해 공개여부 구분(공개 목록엔 항상 true라 무해). 권한 게이트 없이 `@AuthenticationPrincipal`로 자기 author 작품만 보므로 안전(타인은 빈 목록).
3. **뷰어 맥락 응답은 전용 DTO로 분리.** 상세의 `episodeCount`·`latestEpisodeNo`·`isSubscribed`는 admin 변경 응답(`SeriesResponse`)엔 무의미하므로 **`SeriesDetailResponse`를 신설**(SeriesResponse는 admin·공통용 유지). `latestEpisodeNo`는 **PUBLISHED 기준 max**(예약분 제외) — `findMaxEpisodeNoBySeriesIdAndStatus`. `isSubscribed`는 `existsByUserIdAndSeriesId` 재사용.
4. **상세 1요청 = cross-domain 조합.** 작품 상세에 episode 집계 + subscription 여부를 합치려고 `SeriesService`가 `EpisodeRepository`·`SubscriptionRepository`를 참조한다. 이는 series↔episode·series↔personalization **패키지 상호참조**를 만든다(트레이드오프). 프론트 1요청 효율을 위해 수용했고, graphify로 결합 신호를 모니터링한다. 진짜 문제화되면 조합을 컨트롤러/BFF 계층으로 올린다.

### 작업 내역
| 단계 | 파일 | 한 일 |
|---|---|---|
| 11-1 | `SeriesRepository.search`(+keyword·cast), `SeriesService.getList`, `SeriesController.list` | 제목 ILIKE 검색 |
| 11-2 | `SeriesRepository.findByAuthorId`, `SeriesService.getMySeries`, `SeriesController`(/mine), `SeriesSummaryResponse`(+visible) | 작가 자기작(hidden 포함) 목록 |
| 11-3 | `SeriesDetailResponse`(신규), `EpisodeRepository`(countBy·maxByStatus), `SeriesService.getDetail`(집계 조합), `SeriesController.detail`(반환타입) | 상세에 회차수·최신회차·구독여부 |
| 테스트 | `MySeriesTest`(2), `SeriesDetailTest`(2), `SeriesFlowTest`(검색 1) | 검색·자기작·집계 검증 |

### 검증
- `./gradlew test` → **48개 전부 통과**(STEP 10 후 43 → 검색 1 → mine 2 → detail 2 = 48).
  - `?keyword=로맨스` → 제목 매칭 1건, `무협` → 0건
  - `/api/series/mine`: 작가는 공개+비공개 2건(visible 구분), 타인은 0건
  - 상세: 발행 2화+예약 1화 → `episodeCount=2`·`latestEpisodeNo=2`, 구독자 `isSubscribed=true`/비구독자 false
- **RED→GREEN×3.** 각 하위단계 실패 테스트 먼저(검색 미반영 2건 반환, /mine 404, 집계필드 부재) → 구현.

### 오류/특이사항
- **`lower(bytea)` 회귀 즉시 수정.** 11-1에서 내 변경이 기존 목록 조회를 500으로 깨뜨림 → `cast(:keyword as string)`로 해결(systematic-debugging: 가설→`lower(bytea) does not exist` 증거→캐스팅).
- **(트레이드오프) 패키지 상호참조 발생.** `SeriesService`→`EpisodeRepository`·`SubscriptionRepository`로 series↔episode·series↔personalization 양방향 의존. 단순성·1요청 효율 우선해 수용, graphify로 추적.
- **(의도적 범위 한계) 비로그인 브라우징 미오픈.** `/api/series/**`는 여전히 `authenticated()` → viewerId non-null 유지, STEP 10 보류분(`findById(null)` 가드)도 계속 보류. 비로그인 탐색을 열 때 함께 처리.
- **정렬(sort)은 STEP 12로.** ToDoList의 '정렬'은 adultOnly와 함께 STEP 12에서 구현(검색·필터까지가 STEP 11 범위).

### 키워드
- **동적 검색 필터 `:param is null or ...`:** 파라미터 없으면 전체, 있으면 조건. null+문자열 함수는 `cast(:param as string)`로 타입 고정.
- **ILIKE(대소문자 무시 검색):** JPQL엔 ILIKE 없어 `lower(title) like lower(...)`로 구현(한글은 대소문자 무관).
- **경로 매칭 우선순위:** 구체 경로(`/mine`)가 경로변수(`/{id}`)보다 먼저 — 별도 컨트롤러 불필요.
- **뷰어 맥락 DTO:** 같은 엔티티라도 화면 맥락(상세 vs 변경응답)이 다르면 DTO를 분리(`SeriesDetailResponse` vs `SeriesResponse`).
- **cross-domain 조합:** 한 응답에 여러 도메인 데이터를 합칠 때의 결합 트레이드오프 — 서비스 직접참조(단순) vs 상위 계층 조합(결합↓).

---

## STEP 12 — 성인 전용(adultOnly) 분류 + 필터·정렬 ✅ 완료 (커밋: `feat: STEP 12-1/12-2 ...`)

### 목표
ToDoList 원문 *"19태그가 붙은 웹툰 중 성인 웹툰이 따로 있고, 일반 웹툰에 붙은 19로도 정렬할 수 있다"* 를 충족한다 — '성인 전용'과 '일반 작품의 19등급'을 데이터로 **구분(필터)**하고 **정렬**한다. STEP 7 이후 **첫 스키마 변경(V8)**.

### ★ 반드시 알아야 할 핵심
1. **직교 2차원 분해 = 게이트 회귀 0.** 열람 게이트(`ageRating==AGE_19` → 만19세, `verifyAgeAccess`)는 **전혀 안 건드리고**, 분류용 `boolean adultOnly` 컬럼 1개만 추가. 성인전용 = `adultOnly=true`, 일반물의19 = `ageRating=AGE_19 AND adultOnly=false`로 자연 구분. 별도 enum 세분화나 다대다 태그는 YAGNI라 배제(V7 visible 복제 최소 변경).
2. **불변식 `adultOnly=true ⇒ ageRating=AGE_19`는 엔티티가 보장.** `Series`의 생성자·`changeAgeRating`·`changeAdultOnly`에서 `validateAdultConsistency()`로 검증 → 위반 시 `INVALID_INPUT`(400). 모순 데이터(성인전용인데 전체이용가)를 DB가 아니라 도메인이 막는다. 관리자가 AGE_19 작품을 ALL로 낮추려면 먼저 adultOnly를 꺼야 한다(명시적).
3. **`Series.create` 오버로드로 파급 0.** 기존 6인자 `create(...)`는 `adultOnly=false`로 7인자 버전에 위임 → STEP 4~11의 모든 `Series.create` 호출부·테스트가 **무수정**. STEP 5(birthDate)처럼 시그니처를 깨지 않는 선택.
4. **Jackson 3 primitive null 함정.** `SeriesCreateRequest.adultOnly`를 `boolean`(primitive)으로 두니, adultOnly를 **안 보내는 기존 요청이 전부 500**(`Cannot map null into type boolean`, FAIL_ON_NULL_FOR_PRIMITIVES). → `Boolean`(nullable)으로 바꾸고 Service에서 `Boolean.TRUE.equals(...)`로 false 기본 처리. 프론트는 일반 작품엔 adultOnly를 생략해도 된다.
5. **정렬은 boolean 기준 `SeriesSort` enum.** ageRating은 STRING 저장이라 `ORDER BY ageRating`이 사전순(AGE_12<AGE_15<AGE_19<ALL)으로 연령 강도와 어긋난다 → 정렬 키로 노출하지 않음. 대신 `adultOnly`(boolean) 기준 `SeriesSort{LATEST, ADULT_FIRST}`를 Service에서 `Sort`로 매핑(`PageRequest`로 Pageable의 sort 교체). 임의 컬럼명 노출 없이 안전.
6. **V8 마이그레이션 데이터 정합.** `add column adult_only boolean not null default false` → 기존 작품(기존 AGE_19 포함)은 일괄 **false(일반물의19)**. 즉 기존 성인작이 있었다면 '일반물의19'로 보이므로, 운영자가 `PATCH /api/admin/series/{id}/adult-only`로 재분류한다(학습 데이터 규모상 백필 스크립트는 과설계).

### 작업 내역
| 단계 | 파일 | 한 일 |
|---|---|---|
| 12-1 | `V8__add_series_adult_only.sql` | adult_only 컬럼(default false) |
| 12-1 | `Series`(+adultOnly·오버로드 create·changeAdultOnly·validateAdultConsistency) | 불변식 보장 도메인 |
| 12-1 | `SeriesCreateRequest`(Boolean adultOnly), `SeriesService.create` | 생성 시 분류 지정 |
| 12-1 | `SeriesResponse`·`SeriesSummaryResponse`·`SeriesDetailResponse`(+adultOnly) | 노출 |
| 12-1 | `AdultOnlyUpdateRequest`, `AdminService.changeSeriesAdultOnly`, `AdminController`(/adult-only) | 관리자 재분류 |
| 12-2 | `SeriesSort`(enum), `SeriesRepository.search`(+adultOnly 필터), `SeriesService.getList`·`SeriesController.list` | 필터·정렬 |
| 테스트 | `AdultClassificationTest`(7) | 불변식·노출·전환·필터·정렬 |

### 검증
- `./gradlew test` → **55개 전부 통과**(STEP 11 후 48 → 12-1 모델 4 → 12-2 필터·정렬 3 = 55).
  - 생성: adultOnly=true+AGE_19 → 상세 adultOnly=true / adultOnly=true+ALL → **400**(불변식)
  - 관리자: AGE_19 작품 → adultOnly 전환 200 / ALL 작품 전환 → 400
  - 필터: `?adultOnly=true` 성인전용만(1건), `?adultOnly=false` 제외(2건)
  - 정렬: `?sort=ADULT_FIRST` → content[0].adultOnly=true
- **RED→GREEN×2.** 12-1(adultOnly 미지원 4건 실패) → 모델 구현, 12-2(필터·정렬 미지원 3건) → search·enum 구현.

### 오류/특이사항
- **Jackson primitive null 회귀 즉시 수정.** 12-1에서 `boolean adultOnly`가 기존 생성요청을 500으로 깨뜨림 → `Boolean`으로 교정(systematic-debugging: `Cannot map null into type boolean` 증거 → nullable). 참고: 잘못된 요청 바디가 500으로 떨어지는 것(HttpMessageNotReadableException 미처리)은 기존부터의 한계 — 400 매핑은 별도 후속.
- **게이트 무변경 확인.** `verifyAgeAccess`·EpisodeViewerTest 4건 그대로 → 직교 분해라 19금 가드 회귀 0.
- **(범위 유지) 비로그인 브라우징 미오픈** — search는 여전히 인증 필요. viewerId=null 가드 계속 보류.

### 키워드
- **직교 분해(orthogonal decomposition):** 한 값(AGE_19)에 뭉쳐 있던 두 관심사(열람 게이트 vs 분류)를 별 컬럼으로 분리 → 한쪽 변경이 다른 쪽에 영향 없음.
- **도메인 불변식(invariant):** 엔티티가 항상 만족해야 하는 조건(adultOnly⇒AGE_19)을 엔티티 메서드가 스스로 보호.
- **`FAIL_ON_NULL_FOR_PRIMITIVES`:** Jackson이 JSON의 누락/null을 primitive에 매핑할 때 실패시키는 기본 동작 → 선택 필드는 wrapper(Boolean) 사용.
- **정렬 키 화이트리스트:** 임의 컬럼 sort 노출 대신 enum(SeriesSort)으로 허용 정렬만 제공 — STRING enum 사전순 오작동·오정렬 방지.

---

## STEP 13 — 댓글(Comment) + 좋아요(Like) ✅ 완료 (커밋: `feat: STEP 13-1/13-2 ...`)

### 목표
사용자가 ToDoList에 직접 적은 핵심 요구 — **회차 좋아요**(멱등 토글 + 좋아요 수)와 **회차 댓글**(작성/목록/삭제)을 기존 멱등·페이징 패턴 복제로 추가한다.

### ★ 반드시 알아야 할 핵심
1. **멱등 토글은 STEP 6 패턴을 그대로 복제.** `EpisodeLike`는 `ReadLog`와 동일한 user-episode 구조(`@UniqueConstraint` + `exists` 선검사 + `getReferenceById`). 중복 좋아요는 에러가 아니라 무시(멱등), 취소는 `deleteByUserIdAndEpisodeId`. V9는 `read_logs` 마이그레이션 복제.
2. **좋아요는 episode 패키지에 둬서 순환을 피한다.** 좋아요 수·내 좋아요 여부를 **회차 상세에 동봉**(1요청)하려면 집계가 필요한데, 이를 episode 도메인 안(`EpisodeLikeRepository`)에 두면 `EpisodeService`가 같은 패키지를 참조해 **새 import 순환이 안 생긴다**. (STEP 11에서 series↔episode·series↔personalization 순환을 만든 cross-domain 조합과 대비 — 같은 "상세 1요청" 목표를 순환 없이 달성.)
3. **댓글은 신규 `comment` 패키지(단방향 의존).** `Comment`(user·episode·content) → comment가 episode·user를 참조할 뿐 역참조 없음. 목록은 `join fetch c.user` + `PageResponse` 페이징(STEP 3 패턴). 작성 응답은 `IdResponse` 재사용. 대댓글/멘션/신고는 보류(YAGNI).
4. **삭제 권한은 Service에서(본인·ADMIN).** `comment.isOwnedBy(userId) || isAdmin` 아니면 `FORBIDDEN`(불변 규칙: 권한 검증은 Service). ADMIN 판정은 컨트롤러가 `Authentication`에서 추출해 전달.
5. **`AuthSupport` 추출(rule of three).** ADMIN 판정 `hasAdminRole`이 Series·Episode에 이어 Comment에서 **3번째**로 필요해져, `domain/auth/AuthSupport.isAdmin(Authentication)`으로 추출하고 3곳을 통일. global이 아니라 auth 도메인에 둬서 `Role`을 자연스럽게 참조(global→domain 역의존 회피).

### 작업 내역
| 단계 | 파일 | 한 일 |
|---|---|---|
| 13-1 | `V9__create_episode_likes.sql`, `EpisodeLike`, `EpisodeLikeRepository` | 좋아요 엔티티(멱등) |
| 13-1 | `EpisodeService`(+like·unlike, getDetail 집계), `EpisodeController`(POST/DELETE /like), `EpisodeDetailResponse`(+likeCount·liked) | 토글 + 상세 노출 |
| 13-2 | `V10__create_comments.sql`, `Comment`, `CommentRepository`, `CommentService`, `CommentController`, `CommentResponse`·`CommentCreateRequest` | 댓글 작성/목록/삭제 |
| 13-2 | `AuthSupport`(신규), `Series/EpisodeController`(hasAdminRole → AuthSupport.isAdmin) | ADMIN 판정 통일 |
| 테스트 | `EpisodeLikeTest`(1), `CommentTest`(4) | 멱등·집계·권한 |

### 검증
- `./gradlew test` → **60개 전부 통과**(STEP 12 후 55 → 좋아요 1 → 댓글 4 = 60).
  - 좋아요: 토글 멱등(중복 무시), 상세에 likeCount·liked, 취소 시 0/false
  - 댓글: 작성→목록(내용·작성자), 본인 삭제 204, 타인 삭제 **403**, ADMIN 삭제 204
- **RED→GREEN×2.** 13-1(like 엔드포인트 404·likeCount 부재) → 좋아요 구현, 13-2(comments 404·권한) → 댓글+AuthSupport 구현.

### 오류/특이사항
- **순환 회피 설계.** 좋아요를 personalization이 아니라 episode 패키지에 둔 것은 의도적 — episode↔personalization 순환을 안 만들고 상세 1요청을 달성. 읽음(ReadLog)은 UP 계산용이라 personalization, 좋아요는 상세 노출용이라 episode로 용도에 따라 배치.
- **기존 코드 정리는 STEP 범위 내로 한정.** AuthSupport 추출 시 Series/Episode의 hasAdminRole만 교체(동작 동일, 테스트 그대로 통과). 무관한 리팩토링은 안 함.
- **재설계 로드맵 STEP(9~13) 완료.** 이후는 추가기능(조회수·북마크·인앱알림)만 남음.

### 키워드
- **멱등 토글(idempotent toggle):** 같은 요청 반복해도 상태 동일(중복 좋아요 무시), unique 제약 + exists 선검사로 보장.
- **도메인 배치와 결합:** 같은 데이터라도 어느 도메인에 두느냐로 패키지 의존 방향이 갈림 — 좋아요를 episode에 둬 순환 회피.
- **rule of three:** 같은 코드가 3번째 중복되면 추출(AuthSupport) — 2번까진 복제 허용, 3번째에 통일.
- **평면 댓글(flat comment):** 대댓글 트리 없이 회차당 1단 목록 — 학습 범위 단순화.

---

## STEP 14 — 회차 조회수(viewCount) ✅ 완료 (커밋: `feat: STEP 14 ...`)

### 목표
회차 상세를 열 때마다 조회수를 올리고 상세 응답에 노출한다(추가기능 1/3).

### ★ 반드시 알아야 할 핵심
1. **더티 체킹으로 증가.** `Episode.increaseViewCount()`(`this.viewCount++`)를 `getDetail`에서 호출 → `@Transactional` 종료 시 Hibernate가 UPDATE 자동 생성(별도 save 불필요). `getDetail`을 클래스 기본 `readOnly=true`에서 **메서드 `@Transactional`(쓰기)로 오버라이드**해야 UPDATE가 나간다.
2. **조회 = 가드 통과 시점.** viewCount 증가를 visible/status 가드 **뒤**에 둬서, 404로 막히는 비공개·미발행 조회는 카운트되지 않는다(작가 프리뷰는 카운트됨 — 단순화).
3. **동시성 한계(정직하게 기록).** 더티 체킹은 read-modify-write라 동시 조회 시 **lost update**(두 요청이 같은 값을 읽고 +1 → 하나만 반영) 가능. 정확한 카운트가 필요하면 원자적 `update episodes set view_count = view_count + 1`(@Modifying)로 바꿔야 한다. 학습 범위상 더티 체킹 채택.
4. **V11**: `add column view_count bigint not null default 0` — 기존 회차는 0부터.

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `V11__add_episode_view_count.sql` | view_count 컬럼 |
| `Episode`(+viewCount·increaseViewCount) | 증가 도메인 메서드 |
| `EpisodeService.getDetail`(@Transactional) | 조회 시 증가 |
| `EpisodeDetailResponse`(+viewCount) | 노출 |
| `EpisodeViewCountTest` | 조회마다 증가 검증 |

### 검증
- `./gradlew test` → **61개 통과**. 같은 회차 2회 조회 → viewCount 1 → 2.
- RED(viewCount 필드 부재) → 더티 체킹 구현 GREEN.

### 키워드
- **더티 체킹(dirty checking):** 영속 엔티티의 변경을 트랜잭션 커밋 시 자동 UPDATE.
- **lost update:** 동시 read-modify-write가 서로의 갱신을 덮어쓰는 경합 — 원자적 UPDATE/락으로 해결.
- **메서드 단위 트랜잭션 오버라이드:** 클래스 readOnly 위에서 쓰기 메서드만 `@Transactional`로 승격.

---

## STEP 15 — 회차 북마크(Bookmark) ✅ 완료 (커밋: `feat: STEP 15 ...`)

### 목표
회차를 북마크(저장)하고 내 북마크 목록을 본다(추가기능 2/3).

### ★ 반드시 알아야 할 핵심
1. **STEP 13 좋아요 패턴을 그대로 복제.** `Bookmark`(user-episode `@UniqueConstraint`) + 멱등 토글(`exists` 선검사 → `getReferenceById` save / `deleteByUserIdAndEpisodeId`). V12는 `episode_likes` 복제.
2. **배치는 personalization — 좋아요(episode)와 다른 이유.** 좋아요는 회차 상세에 카운트를 **노출**해야 해서 episode 패키지였지만, 북마크는 "내 북마크 목록"이라 **개인화 표면**(구독·읽음과 함께)이다. 데이터 구조는 같아도 용도(상세 집계 vs 내 목록)에 따라 도메인을 배치 → 순환 없이 PersonalizationService에 자연스럽게 흡수.
3. **내 목록은 join fetch로 N+1 회피.** `findByUserIdWithEpisode`가 `join fetch b.episode e join fetch e.series`로 한 쿼리에 회차·작품을 끌어와 `BookmarkResponse`(작품 제목·회차번호)를 만든다(STEP 6 구독목록 패턴).

### 작업 내역
| 파일 | 한 일 |
|---|---|
| `V12__create_bookmarks.sql`, `Bookmark`, `BookmarkRepository` | 북마크 엔티티(멱등) |
| `PersonalizationService`(+bookmark·unbookmark·getMyBookmarks), `PersonalizationController` | 토글 + 내 목록 |
| `BookmarkResponse` | 작품·회차 정보 |
| `BookmarkTest` | 멱등·목록 검증 |

### 검증
- `./gradlew test` → **62개 통과**. 북마크 멱등(중복 무시) → `/api/me/bookmarks`에 작품제목·회차번호 1건 → 취소 시 0건.
- RED(bookmark 404·목록 부재) → 구현 GREEN.

### 키워드
- **멱등 토글 재사용:** 같은 user-X 유니크 패턴을 구독·읽음·좋아요·북마크가 공유(코드 반복으로 학습 정착).
- **용도에 따른 도메인 배치:** 동일 데이터라도 노출 맥락(상세 집계 vs 내 목록)이 패키지 선택을 가른다.

---

## STEP 16 — 이미지 S3 호환 스토리지 전환 ✅ 완료 (커밋: `refactor/feat: ObjectStorage ...`)

### 목표
로컬 디스크에만 저장하던 회차 이미지를, **코드 변경 없이 환경변수만으로 S3 호환 스토리지(MinIO/R2/AWS S3)로 전환**할 수 있게 한다. 컨테이너 배포 시 디스크가 휘발되는 문제(STEP 9 정적 서빙의 운영 한계)를 닫는 배포 선결과제.

### ★ 반드시 알아야 할 핵심
1. **저장 백엔드를 `ObjectStorage` 인터페이스로 추상화.** `put(key, bytes, contentType)` + `urlFor(key)` 두 메서드. `ImageStorageService`는 검증·리사이즈만 하고 저장은 ObjectStorage에 위임 → 구현 교체만으로 백엔드 전환. (16-1은 동작 동일 리팩토링: `/files` URL 유지, 68개 그대로 통과.)
2. **AWS SDK v2 S3Client가 벤더 호환의 핵심.** `endpointOverride`로 endpoint만 바꾸면 **MinIO·Cloudflare R2·AWS S3 모두 같은 코드**로 붙는다. `forcePathStyle(true)`는 MinIO/R2의 path-style 접근에 필요. 의존성은 BOM으로 버전 관리(`software.amazon.awssdk:bom` + `s3`).
3. **구현 선택은 `@ConditionalOnProperty`.** `app.storage.type=local`(기본, `matchIfMissing`) → `LocalObjectStorage`, `=s3` → `S3ObjectStorage`+`S3Config`. **빈이 조건부 생성**되므로 local 모드에선 S3 설정(`@Value`)이 평가조차 안 된다.
4. **key는 불변, url은 동적.** `EpisodeImage.path`에 저장 key(`{seriesId}/{episodeNo}/{order}.{ext}`)만 보관하고, URL은 `urlFor(key)`로 그때그때 생성 — local은 `/files/{key}`, s3는 `{S3_PUBLIC_BASE_URL}/{key}`. URL 조립 책임을 DTO에서 **Service로 이동**(`EpisodeImageResponse.of(image, url)`)해 DTO가 스토리지를 모르게.
5. **Testcontainers MinIO로 진짜 S3 호환 검증.** `@Transactional`/mock이 아니라 실제 MinIO 컨테이너에 put→getObject로 바이트 일치 확인(`S3ObjectStorageTest`). 실서버에서도 `STORAGE_TYPE=s3`로 업로드→MinIO 저장→public url GET 200까지 검증(코드 0 변경 전환 입증).

### 작업 내역
| 단계 | 파일 | 한 일 |
|---|---|---|
| 16-1 | `ObjectStorage`, `LocalObjectStorage`(신규), `ImageStorageService`(리팩토링) | 저장 추상화 + 로컬 구현 |
| 16-1 | `EpisodeImageResponse.of(image,url)`, `EpisodeDetailResponse.of`, `EpisodeService` | url 조립을 Service로 |
| 16-2 | `build.gradle`(aws sdk bom+s3, testcontainers-minio), `S3Config`, `S3ObjectStorage`(신규) | S3 호환 구현 |
| 16-2 | `application.yml`(app.storage.type/s3.*), `docker-compose.yml`(MinIO+버킷init) | 설정 + 로컬 MinIO |
| 테스트 | `LocalObjectStorageTest`(2), `S3ObjectStorageTest`(Testcontainers MinIO 2) | 양쪽 구현 검증 |

### 검증
- `./gradlew test` → **70개 통과**(16-1 후 68 → S3 2 = 70). MinIO 컨테이너 기동으로 빌드 시간↑.
- **실서버 전환 검증**: `STORAGE_TYPE=s3 S3_ENDPOINT=http://localhost:9000 ...`로 bootRun → 회차 업로드 → `imageUrl=http://localhost:9000/apptoon/{key}` → 실제 GET 200, MinIO에 객체 저장 확인. local 모드(`/files`)와 코드 동일.

### 오류/특이사항
- **의존성은 빌드로 검증(스택 함정 회피).** AWS SDK BOM 최신(2.46.14)·testcontainers-minio 좌표를 Maven Central에서 확인 후 추가 → `dependencies`/컴파일로 해결 확인(SB4/Java25 호환 OK).
- **버킷·공개 정책은 인프라 책임.** 앱은 `put`만 — 버킷 생성·public read는 docker-compose `minio-init`(mc)나 운영 IAM이 담당(앱이 createBucket 권한을 갖지 않는 운영 가정).
- **`/files` 정적 핸들러는 local 전용**이지만 s3 모드에서도 남아있다(무해, 매핑만). 필요 시 조건부로 끌 수 있음(YAGNI라 보류).

### 키워드
- **포트-어댑터(추상화):** `ObjectStorage`(포트) + Local/S3(어댑터) — 도메인이 인프라 구현을 모름.
- **S3 호환 API:** AWS SDK v2 + `endpointOverride`/`forcePathStyle` → MinIO·R2·AWS S3 단일 코드.
- **`@ConditionalOnProperty`:** 설정값으로 빈을 켜고 끔(구현 선택). `matchIfMissing`으로 기본 구현 지정.
- **Testcontainers MinIO:** 실제 S3 호환 서버를 컨테이너로 띄워 저장 동작을 통합 검증.
- **휘발성 디스크(ephemeral):** 컨테이너 재시작 시 로컬 파일 소멸 → 오브젝트 스토리지로 영속화.

---

## Docker — 역할 · 사용법 · 작동 방식 (자세히)

### 1) Docker가 푸는 문제 (왜 쓰나)
- **"내 PC에선 되는데 서버에선 안 됨" 제거.** 앱/DB를 OS·설치상태와 무관하게 **동일한 환경**으로 실행.
- DB를 PC에 직접 설치(버전 충돌, 잔여 설정 찌꺼기)하지 않고, **컨테이너로 깔끔히 띄웠다 지웠다** 가능.

### 2) 핵심 개념
- **이미지(Image):** 실행에 필요한 파일+설정의 **읽기전용 스냅샷**(예: `postgres:16`). 레이어로 쌓여 캐시·재사용됨.
- **컨테이너(Container):** 이미지를 **실행한 인스턴스**(살아있는 격리된 프로세스). 같은 이미지로 여러 개 가능.
- **레지스트리(Registry) / Docker Hub:** 이미지 저장소. `postgres:16`을 여기서 `pull`(내려받음).
- **볼륨(Volume):** 컨테이너 **바깥**에 데이터를 보관. 컨테이너를 지워도 DB 데이터 유지. 우리: `apptoon-db-data`.
- **포트 매핑 `5432:5432`:** `호스트포트:컨테이너포트`. 그래서 내 PC `localhost:5432`로 컨테이너 안 DB에 접속.
- **환경변수:** 컨테이너에 설정 주입(`POSTGRES_USER`, `POSTGRES_PASSWORD` 등).
- **네트워크:** compose가 자동으로 네트워크를 만들어 컨테이너끼리 **이름으로** 통신.
- **healthcheck:** 컨테이너가 "정상/준비됨"인지 주기적으로 검사(우리는 `pg_isready`로 DB 준비 판단).

### 3) 작동 방식 (어떻게 돌아가나)
- 구조: 클라이언트(`docker` 명령) ↔ **Docker Engine(데몬)**. 내 명령을 데몬이 받아 컨테이너를 만들고 굴린다.
- 리눅스에선 커널 기능 **namespace**(격리)와 **cgroups**(자원 제한)로 "가벼운 격리"를 함. VM처럼 OS를 통째로 올리지 않아 **빠르고 가볍다**.
- **macOS엔 리눅스 커널이 없다.** 그래서 **Docker Desktop이 경량 리눅스 VM을 띄우고 그 안에서** 컨테이너를 돌린다 → Docker Desktop이 실행 중이어야 `docker` 명령이 동작한다.

### 4) docker-compose (우리가 쓴 방식)
- 여러 컨테이너 설정을 **YAML 하나**(`docker-compose.yml`)에 **선언적으로** 적고 `docker compose up`으로 한 번에 실행.
- 우리 `docker-compose.yml`이 하는 일: `postgres:16` 이미지로 `apptoon-db` 컨테이너 생성 → 포트 5432 매핑 → 볼륨으로 데이터 영속 → 환경변수로 DB/계정 자동 생성 → healthcheck로 준비 판단 → `restart: unless-stopped`(죽으면 자동 재시작).

### 5) 이 프로젝트에서의 Docker 두 갈래 사용
1. **개발용 (docker-compose):** 코딩하는 동안 붙어 있는 **상시 DB**. `docker compose up -d`로 띄워두면 `bootRun`이 `localhost:5432`로 접속.
2. **테스트용 (Testcontainers):** `./gradlew test` 때 코드가 **자동으로** postgres 컨테이너를 띄우고 테스트가 끝나면 **자동 삭제**. → "진짜 DB로 테스트"하면서 내 개발 DB는 안 건드림.
- ※ 둘 다 결국 **Docker Engine을 쓴다.** 그래서 Docker Desktop이 꺼져 있으면 개발 서버도, 테스트도 못 돈다.

### 6) 자주 쓴 명령어와 의미
```bash
docker compose up -d                  # 정의된 컨테이너 백그라운드 기동
docker ps                             # 실행 중 컨테이너 + 상태(healthy 등)
docker inspect --format '{{.State.Health.Status}}' apptoon-db   # 헬스 상태만 추출
docker exec apptoon-db psql -U apptoon -d apptoon -c "\dt"      # 컨테이너 안에서 psql 실행(테이블 목록)
docker compose logs -f db             # DB 로그 실시간 보기
docker compose down                   # 중지·삭제 (볼륨은 기본 유지)
docker compose down -v                # 볼륨까지 삭제 = DB 데이터 완전 삭제 ⚠️
```

### 7) 라이프사이클 한눈에
`이미지 pull → 컨테이너 생성/시작 → (사용) → 중지 → 삭제`. **데이터는 볼륨에 남아** 다음 컨테이너가 재사용한다(그래서 `down` 해도 데이터가 안 날아감, `down -v`라야 날아감).

---

## STEP 17 — 콘솔 디자인 시스템(라이트/다크) + 설정 모달 ✅ 완료 (커밋: `feat: 콘솔 디자인 "작업실" 리스킨 ...`)

> 기존 만화-브루탈리즘(잉크 패널+버밀리언) 콘솔을 "작업실" 컨셉으로 리스킨하고, **하나의 디자인 시스템을 라이트/다크 두 스킨**으로 만들었다. 정적 자산(html/css/js)만 바뀌고 백엔드는 무변경 → 79개 테스트 그대로 GREEN.

### 1) 라이트/다크는 "두 디자인"이 아니라 "한 시스템 두 스킨"
- 사용자가 "라이트=A안, 다크=C안" 섞기를 제안 → **반대**했다. 토글은 같은 정체성을 표면색만 바꾸는 것. 서로 다른 컨셉을 끼우면 버튼 하나에 두 브랜드가 튀어나오고 유지보수가 2배.
- 구현 핵심: **CSS 커스텀 프로퍼티(토큰) + `[data-theme]` 속성 선택자**.
  ```css
  :root, [data-theme="dark"] { --bg:#16142A; --surface:#211E3C; --amber-ink:#FFB661; ... }
  [data-theme="light"]       { --bg:#F4F2EE; --surface:#FFFFFF; --amber-ink:#B26A12; ... }
  .card { background:var(--surface); color:var(--text); }   /* 컴포넌트는 토큰만 참조 */
  ```
  컴포넌트 CSS는 색을 직접 쓰지 않고 토큰만 본다 → 스킨 추가/변경이 토큰 블록 한 곳에서 끝난다.
- **대비(contrast) 보정 토큰**: 같은 "앰버"라도 다크에선 글자색으로 써도 밝아서 OK(`--amber-ink:#FFB661`), 라이트에선 흰 배경에 안 보이니 글자용은 진한 번트앰버(`#B26A12`)로 분리. "칠하는 색"과 "글자 색"을 다른 토큰으로 가른 게 포인트.

### 2) 테마 우선순위 & 플래시(FOUC) 방지
- 우선순위: `localStorage('apptoon_theme')` 명시값 > 없으면 OS 설정(`matchMedia('(prefers-color-scheme: dark)')`). 값은 `system|light|dark` 3종. `system` 선택 시 localStorage를 **지워서** OS를 따라가게 함.
- **첫 페인트 전 적용**: `index.html` `<head>`에 인라인 `<script>`로 data-theme를 먼저 박는다. 그래야 CSS 로드 전 잘못된 테마가 한 번 깜빡이는 FOUC를 막는다. (SPA 본 스크립트는 늦게 뜨므로 늦음)
- 시스템 모드일 때 OS 테마가 바뀌면 `sysDark.addEventListener('change', ...)`로 실시간 반영.

### 3) 설정 모달 = "있는 기능만" (YAGNI)
- claude.ai 계정 메뉴처럼 **좌상단 사용자 정보 클릭 → 설정 모달**. 들어간 건 실제 API가 받쳐주는 것만: 내 정보(읽기 전용, `GET /api/users/me`)·테마·로그아웃.
- 문의/비밀번호 변경/알림은 **백엔드가 없으므로 가짜 메뉴를 그리지 않았다**(죽은 UI 금지). 모달을 섹션 구조로 만들어 나중에 끼우기만 하면 되게 함.

### 4) 모달 접근성 — 포커스 관리
- 모달을 열면 `dialog.setAttribute('tabindex','-1'); dialog.focus()`로 **포커스를 모달로 이동**. 이유: ① 스크린리더가 모달을 읽게 함 ② `Esc` 키 핸들러가 확실히 동작(포커스가 모달 안에 있어야 keydown이 document로 버블링). `.modal:focus{outline:none}`로 프로그램적 포커스 링은 숨김.
- 검증 함정: puppeteer `page.keyboard.press('Escape')`는 headless에서 탭 포커스가 없어 안 먹을 수 있다 → `el.click()`/`dispatchEvent`로 핸들러 자체가 도는지 따로 확인. **실제 브라우저에선 클릭이 포커스를 주므로 정상 동작.**

### 5) 실행 중 서버에 정적 변경 반영
- `bootRun`은 `build/resources/main`에서 정적 파일을 서빙한다(`src/main/resources` 아님). 따라서 src만 고치면 **실행 중 서버엔 안 보인다**.
- 해결: `processResources`(= bootRun 재기동이 의존으로 실행) 또는 바뀐 파일을 `build/resources/main/static/admin/`로 직접 복사. `curl http://localhost:8080/admin/app.js | grep` 으로 반영 확인.

### 6) 디자인 의사결정 메모
- 컨셉 후보를 **Visual Companion**(탭 전환 + iframe 라이브 렌더 + 토큰 패널 HTML)으로 비교 제시 → 사용자가 C(심야 작업실) 선택. 정적 mockup은 비교용일 뿐, 채택 후 실제 SPA에 토큰을 이식했다.
- 시그니처: **데스크 램프 앰비언트 글로우**(`.main:before` radial-gradient) + "작업 중" 카드만 앰버로 점등. 라이트/다크 모두에서 같은 시그니처가 톤만 바뀌어 유지됨.

---

## STEP 18 — 문의(Inquiry) 도메인 ✅ 완료 (커밋: `feat: STEP 18 문의 도메인 ...`)

> 독자·작가가 관리자에게 문의(종류·내용·이미지 복수)를 보내고 관리자가 답변·상태 관리. 한 도메인을 **수직 슬라이스 한 묶음**(마이그레이션→엔티티→리포지토리→서비스→컨트롤러 2개→콘솔 UI→통합 테스트)으로 구현. 89개 테스트 GREEN.

### 1) 멀티에이전트로 "설계 확정 → 구현 → 적대적 리뷰"
- ultracode 워크플로로 ① 기존 패턴 6개 병렬 매핑 → 종합 설계 → 보안/데이터/UX 3렌즈 적대적 비평, ② 구현 후 다시 리뷰 워크플로(4차원 발견 → 각 발견 반박 검증). **설계 단계 비평이 IDOR·DoS·권한 3개 high를 미리 잡아** 구현에 반영. 단, 비평 에이전트는 코드를 추정하기도 해서(없는 `ImageStorageService.store(seriesId,episodeNo)` 시그니처 가정 등) **실제 코드로 직접 검증 후 채택**하는 게 핵심.

### 2) TDD — 통합 플로우 먼저(RED), 함정 하나
- `InquiryFlowTest`(독자 제출→목록/상세, 타인 403, 관리자 필터/답변, 삭제 등 10케이스)를 먼저 작성해 RED 확인 후 구현. **함정**: 테스트에서 `ObjectMapper`를 autowire했더니 컨텍스트가 `NoSuchBeanDefinitionException`으로 통째로 실패 — "엉뚱한 RED". 응답 id 파싱은 `com.jayway.jsonpath.JsonPath`(이미 테스트 클래스패스)로 교체해 해결. **RED는 "기대한 이유"로 실패해야 한다.**

### 3) `ddl-auto: validate` — 엔티티 타입이 스키마와 일치해야
- Hibernate가 스키마를 검증만 하므로 `@Column(length=…)`이 Flyway 컬럼과 안 맞으면 부팅 실패. content를 `text` 대신 **`varchar(5000)`**로 둬 검증 마찰을 피함(프로젝트가 varchar 일색). enum은 `varchar(20)` + `@Enumerated(STRING)`.

### 4) OSIV off에서 LAZY는 "서비스 트랜잭션 안에서" 평탄화
- `open-in-view: false`라 컨트롤러에서 LAZY 접근하면 터진다([[apptoon-lazy-serialization-gotcha]]). 그래서 **DTO 매핑을 서비스 `@Transactional` 안에서** 수행(세션 열림 → LAZY user/images 로드). `answer()`/`changeStatus()`는 더티체킹으로 flush되고, 반환 `InquiryAdminDetailResponse`도 같은 트랜잭션 안에서 user를 읽어 안전. 관리자 목록은 `join fetch i.user`로 N+1 회피.

### 5) 이미지 파이프라인 재사용 — 정밀 수정(오버로드)
- `ImageStorageService`가 회차 전용(`store(seriesId, episodeNo, …)`, 키 `{seriesId}/{episodeNo}/{order}.ext`)이었다. 내부를 **keyPrefix 기반**으로 일반화하고 기존 메서드는 `store(seriesId+"/"+episodeNo, …)`로 위임 → **기존 호출부 0 변경**(정밀 수정). 문의는 prefix `inquiries/{id}/{uuid}`.

### 6) 보안 설계 메모
- **IDOR**: `getMineDetail`은 `inquiry.isOwnedBy(userId)`(=`user.getId().equals(userId)`, 프록시 getId만) 검증 + 목록은 `findByUserId`로 SQL 격리. userId는 `@AuthenticationPrincipal`에서만(쿼리파라미터 금지).
- **이미지 URL 열거**: 키에 `UUID.randomUUID()`를 넣어 추측 불가(서빙은 기존 `/files` 정적 유지).
- **관리자 게이팅**: `SecurityConfig`는 `/api/admin/**`을 따로 막지 않고 `anyRequest().authenticated()`만 — 그래서 **컨트롤러 `@PreAuthorize("hasRole('ADMIN')")`**(메서드 시큐리티)로 보호. 비관리자 403 테스트로 회귀 방지.
- **동적 필터**: 관리자 목록 `(:type is null or i.type = :type) and (:status is null or i.status = :status)` — enum null 파라미터도 JPQL에서 정상 동작(우려했으나 OK).

### 7) 알아둘 트레이드오프
- `ObjectStorage`에 `delete()`가 없어 문의 삭제 시 **DB 행은 cascade 정리되지만 스토리지 파일은 고아로 남는다**(에피소드와 동일 정책). 정리 배치/`delete()` 추가는 향후 과제.

---

## STEP 19 — 작품 장르·태그 ✅ 완료 (커밋: `feat: STEP 19 작품 장르·태그 ...`)

> 탐색·추천·랭킹의 받침대. **외부 설정 0**(첫 "build now" 받침대). 단일 주(主)장르(enum) + 자유 태그(다대다 값). 101개 테스트 GREEN.

### 1) 태그는 ManyToMany 대신 `@ElementCollection Set<String>`
- `publishDays`가 이미 ElementCollection(series_publish_days)인 전례를 복제 → 태그도 `series_tags(series_id, tag)` 값 컬렉션. **ManyToMany 함정(고아·cascade·조인중복) 회피**, 별도 Tag 엔티티/마스터 불필요(YAGNI). 필터는 `:tag member of s.tags`(요일 필터와 동일 패턴).
- OSIV off라 DTO 매핑 시 LAZY 컬렉션은 **`Set.copyOf(series.getTags())`로 트랜잭션 안에서 복사**(publishDays와 동일). [[apptoon-lazy-serialization-gotcha]]

### 2) 깨지지 않는 스키마 진화 (정밀 수정)
- `series`에 `genre varchar(20) not null default 'ETC'` → **기존 작품 행은 ETC로 자동 채움**(not null 유지).
- `Series.create(...)` 팩토리는 **무변경** — genre/tags는 필드 기본값(ETC/빈Set)으로 두고, 서비스가 생성 후 `changeGenre()`/`replaceTags()`로 설정. → 기존 `create()` 호출부(테스트 다수) 0 변경. (adultOnly 추가 때와 같은 전략.)
- `SeriesCreateRequest.genre/tags`는 **선택(nullable)** → 기존 API 클라이언트/테스트가 안 깨짐. genre null이면 서비스에서 ETC.

### 3) 검색 필터 통합 & 정규화
- 기존 `search` JPQL에 `(:genre is null or s.genre = :genre)` + `(:tag is null or :tag member of s.tags)` 추가(value/countQuery 양쪽). enum/문자열 null 파라미터 정상 동작.
- 태그 정규화는 서비스에서: 공백제거(strip)·빈값/30자초과 제외·중복제거(Set)·최대 10개(limit).
- 수정: `PATCH /api/series/{id}/genre-tags`(본인만 — `@PreAuthorize(CREATOR)` + `isAuthoredBy` 서비스 검증, 남이면 403).

### 4) 다음
- 받침대 중 **알림 센터(인앱)**는 설계 완료([`docs/design-notification.md`](design-notification.md)) — genre·tag가 V14를 가져가서 **알림은 V15**가 됨(구현 시 부여). 외부 설정 불필요 목록은 [`docs/roadmap-and-monetization.md`].

---

## STEP 20 — 계정·프로필 + 법적 동의 + 작가 전환 신청 ✅ 완료 (커밋: `feat: STEP 20 ...`)

> 회원가입/인증 전체를 설계(자체·이메일인증·OAuth·법적동의)하되 **외부 설정 0인 부분만** TDD 구현. 외부 의존(OAuth 키·이메일 SMTP)·프로덕션 하드닝(rate limit·토큰 무효화)은 설계 문서로 미룸. 115개 테스트.

### 1) 멀티에이전트 설계 비평을 "취사선택"
- 설계 워크플로의 비평(보안·법적·데이터)이 광범위했으나 상당수가 **MVP/학습 범위 밖**(Bucket4j rate limit, JWT 블랙리스트, Redis/Vault, GDPR 전면 준수) 또는 **외부 의존**. 실제 코드로 검증해 **지금 슬라이스에 맞는 것만** 반영(동의 인프라·만14세·필수동의 차단·비번변경 현재비번 확인·MyProfileResponse·작가신청 심사). **비평은 입력이지 명령이 아니다.**
- 정정: 비평이 "birthDate nullable이라 미성년 차단 불가"라 했으나 실제 `SignupRequest.birthDate`는 이미 `@NotNull @Past` → 검증 한 줄만 추가.

### 2) 가입 스키마 변경은 동반 테스트 수정 필수
- `SignupRequest`에 `consents` 추가 → 기존 가입 테스트(`AuthFlowTest` JSON, `AuthServiceTest`의 4-arg 생성자)가 **컴파일/검증 깨짐** → 같이 수정. 스키마/계약 변경 시 호출부(특히 테스트) 동반 수정이 원칙.

### 3) FK on delete cascade — 비-트랜잭션 테스트의 함정
- `user_consents`/`creator_requests` FK 추가 후 전체 빌드에서 `SeriesDetailSerializationTest`(비-트랜잭션, 커밋 후 사용자 cleanup)가 **FK 위반으로 실패**: `AuthFlowTest`(비-트랜잭션)가 signup으로 **동의 행을 커밋**해두면 다른 테스트의 사용자 삭제를 FK가 막음. → FK를 **`on delete cascade`**로(사용자 삭제 시 동의/신청 정리 — 향후 계정삭제에도 정합). ⚠️ **@Transactional 안 붙은 E2E 테스트는 서로의 커밋 데이터를 본다.**

### 4) 도메인/응답 분리 결정
- **MyProfileResponse**(본인 `/me`, birthDate·bio·avatar 포함) vs **UserResponse**(관리자, birthDate 비노출). 같은 사용자라도 보는 주체에 따라 DTO 분리.
- 아바타: `avatar_key`(스토리지 key) 저장 + `urlFor`로 URL 동적계산(EpisodeImage 패턴), 교체 시 이전 key `ObjectStorage.delete`.
- 동의: **현재상태 스냅샷**(UNIQUE user+type, version·agreed_at). 필수(약관·개인정보)는 가입 차단 + 철회 불가, 마케팅 기본 opt-out(법적 정답). `userId`는 비-연관 Long.
- 작가 전환: 현행 "관리자 수동 role 부여" → **신청·심사**(독자 신청→관리자 승인 시 role CREATOR). 중복·이미작가 차단.

### 5) 미룬 것(외부/프로덕션) — `docs/roadmap-and-monetization.md`
- OAuth(Google/Kakao/Apple), 이메일 인증·비번 재설정·이메일 변경(SMTP/SES), rate limiting·로그인 잠금·JWT 무효화/로그아웃, 계정 삭제(잊혀질권리). 스키마/플로우는 설계 단계에 대비.

---

## STEP 21 — 팔로우 + 작가신청 결정 정정 ✅ 완료 (커밋: `feat: STEP 21 ...`)

> 소셜의 시작(팔로우). 외부 0. 122개 테스트. 더불어 관리자 오클릭 대비로 작가신청 결정을 정정 가능하게.

### 1) 팔로우 — 사용자↔사용자
- V16 `follows(follower_id, following_id, UNIQUE 멱등, FK on delete cascade)`. 엔티티는 **비-연관 Long**(집계·대량조회 위주, notification/consent와 동일 선택).
- 목록은 **JPQL 크로스 조인**으로 상대 User를 가져옴: `select u from Follow f, User u where u.id = f.followingId and f.followerId = :uid`. (연관 매핑 없이도 조인.)
- 멱등: 팔로우 전 `existsBy...`로 중복 스킵. 가드: 자기 자신 차단, 없는 유저 404.
- 엔드포인트: `POST/DELETE /api/users/{id}/follow`, `GET /me/following·/me/followers`, `GET /{id}/follow-stats`(팔로워수·팔로잉수·isFollowing).
- **라우팅 주의**: `/api/users/{id}/follow`(FollowController)와 `/api/users/me/...`(UserController)가 같은 base path를 공유하지만, `me/following`·`{id}/follow-stats`는 2번째 세그먼트 리터럴이 달라 충돌 없음. Spring은 리터럴(`me`)을 `{id}`보다 우선.

### 2) "재처리 허용 + 부작용은 신중하게"
- 작가신청 결정을 **언제든 정정**(가드 제거): 관리자가 잘못 누른 승인/거부를 뒤집을 수 있게.
- 단 **역할 변경은 조건부**: 승인은 신청자가 **READER일 때만** CREATOR 승격(이미 작가/관리자는 무변경), 거부 되돌림은 **CREATOR일 때만** READER 강등. → 다른 경로(관리자 직접 부여 등)로 권한 받은 사용자를 잘못 강등하는 사고 방지. **"상태 전이는 자유롭게, 사이드이펙트는 보수적으로."**
- 테스트로 양방향 정정(승인→거부 강등 / 거부→승인 승격) 검증.

---

## STEP 22 — 커뮤니티 + 신고 + 자동 블라인드 ✅ 완료 (커밋: `feat: STEP 22 ...`)

> 두 도메인을 한 STEP에(신고 대상이 곧 게시글/댓글이라 같이 봐야 함). 외부 0. 132개 테스트. 가장 큰 슬라이스.

### 1) 폴리모픽 신고 + 신고↔커뮤니티 연결
- `reports(target_type, target_id)`로 **게시글·댓글·유저·작품·회차 무엇이든 신고**(알림 설계의 폴리모픽 패턴 재사용). 한 신고자가 한 대상을 1회만(UNIQUE).
- **자동 블라인드**: `ReportService.create` 후 해당 대상의 PENDING 신고 수를 집계, **≥5면 Post/PostComment를 blind()**(더티체킹). → report 도메인이 community 도메인에 **단방향 의존**(community는 report를 모름 → 순환 없음). "신고 ↔ 콘텐츠 연결"이 이 자동 블라인드.

### 2) 1-depth 대댓글 평탄화
- 댓글 작성 시 `parentId`가 **이미 대댓글이면(parent.isReply()) 그 최상위 부모로 평탄화** → 무한 중첩 방지(네이버식 1-depth). 조회는 top(parentId null) + parentId로 그룹핑해 replies 동봉.

### 3) 비-연관 Long + 닉네임 배치 로드
- Post/PostComment의 작성자는 비-연관 `Long authorId`. 목록 DTO의 닉네임은 **`userRepository.findAllById(distinct ids)` → Map**으로 한 번에 로드(N+1 회피). 단건은 findById.

### 4) 자잘한 결정
- 멀티파트 빈 요청: 이미지 없는 글도 `multipart()`로 보내야 `@RequestPart(required=false) images`가 바인딩(JSON POST면 "not a multipart request" 에러). [[apptoon-redesign-roadmap]]의 inquiry와 동일.
- 블라인드 글: 공개 목록(`findVisible` where blinded=false)·상세(blinded면 404)에서 숨김. 관리자만 `/api/admin/posts`로 전체.
- 추천수는 **denorm 카운터**(post.like_count) — lost update 가능, 정확히는 원자적 `update ... set like_count=like_count+1`. 학습 단계라 수용([[apptoon-redesign-roadmap]] 조회수와 동일 트레이드오프).
- 삭제 cascade: 글 삭제 시 이미지 파일은 `imageStorageService.delete`, DB의 이미지/추천/댓글 행은 **FK on delete cascade**.

---

## STEP 23 — 신고/커뮤니티 모더레이션 상세·처리·검색 (활동/알림 단계 설계) ✅ 완료 (커밋: `feat: STEP 23 ...`)

> 멀티에이전트로 "모더레이션 상세 + 활동내역/서재 + 작가소식피드 + 인앱 알림저장소"를 9단계로 설계 → **즉시 필요한 2슬라이스(신고 상세·처리·필터 / 커뮤니티 상세·검색)만 구현**, 나머지(알림저장소→멘션→서재→피드)는 [`docs/design-moderation-activity.md`]로 박제. 137개 테스트. 과대 슬라이스 회피.

### 1) 신고 상세 — 폴리모픽 대상 내용 조회
- 관리자가 신고 클릭 → **대상 게시글/댓글 실제 내용** 표시. `ReportService.detailOf`가 `targetType` switch로 Post/PostComment/User 분기 조회(content·author). 관련 신고 수는 `countByTargetTypeAndTargetId`로 역집계. → 폴리모픽 신고 대상의 내용을 보여주는 표준 패턴.

### 2) 신고 처리 = 액션 선택 (report → community 단방향 위임)
- `PATCH /resolve {action: NONE|BLIND_TARGET|DELETE_TARGET}`. 대상이 게시글/댓글일 때만 조치. **삭제는 `PostService.delete`/`PostCommentService.delete`로 위임**(이미지 정리·cascade 재사용) → ReportService가 community 서비스에 **단방향 의존**(순환 없음). 블라인드는 엔티티 직접 `blind(adminId)`.

### 3) 블라인드 이력 — 한 메서드로 자동/수동 구분
- `Post/PostComment.blind(Long byUserId)`: **자동 블라인드(신고 임계치)는 `byUserId=null`(시스템)**, 관리자 수동은 adminId. V19에 `blinded_by`·`blinded_at` 추가. 메서드참조(`Post::blind`)가 인자 추가로 깨져 람다(`p -> p.blind(null)`)로 교체 — 시그니처 변경 시 호출부(메서드참조 포함) 확인.

### 4) 커뮤니티 관리 검색·필터
- `PostRepository.findForAdmin(category, titleKeyword, blinded, pageable)` — 기존 series `search`의 `lower(title) like lower(concat('%', cast(:kw as string), '%'))` + null 파라미터 패턴 재사용. 관리자 상세는 블라인드 포함 전체 조회(공개 `findVisible`과 분리).

### 5) 설계는 멀티에이전트, 구현은 슬라이스
- 워크플로가 9단계·각 ~1주로 산정 → **한 턴에 다 하지 않고** 즉시 가치 높은 2개만 TDD 구현, 나머지는 단계 문서로. 비평이 잡은 "미구현"은 대부분 다음 단계의 할 일(=정상). **설계 범위 ≠ 구현 범위.**

---

## STEP 24 — 인앱 알림 저장소 (Phase 3: 폴링·읽음·종류별·라우팅) ✅ 완료 (커밋: `feat: STEP 24 ...`)

> 푸시로 끝나지 않고 **나중에 다시 보고, 읽음/안읽음 처리하고, 종류별로 분류하고, 클릭하면 어디로 갈지(라우팅) 정보를 주는** 인앱 알림 저장소. 외부 의존 0(폴링), 푸시(FCM)만 외부. 실서버 종단 검증(문의→답변→알림→읽음→라우팅) + 콘솔 벨 스크린샷. 144테스트.

### 1) 폴리모픽 타겟 = 라우팅 신호
- 알림은 `(targetType, targetId)`만 들고 있고(예: INQUIRY/3) 클라가 이걸로 딥링크. 비정규화 `title`/`message`로 목록 조회 시 조인 0. `recipientId`는 **비연관 Long**(대량 fan-out 프록시 비용 회피). → 신고/모더레이션의 폴리모픽 패턴을 알림에 재사용.

### 2) "읽음 처리 + 라우팅 정보"를 한 호출로
- 사용자 지적("확인 처리와 함께 라우팅"): `PATCH /{id}/read`가 읽음 표시 **후 NotificationResponse(targetType·targetId 포함) 반환** → 클라가 한 번에 "읽음+이동". 별도 조회 불필요.

### 3) 종류별 분류 + 미읽음 집계
- 모바일 "전체/[종류별]" 탭: `GET ?type=` 필터 + `GET /unread-summary`(`group by type` → `{total, byType:{INQUIRY_ANSWERED:1}}`)로 탭별 배지. `unread-count`는 폴링 배지용.

### 4) fan-out 디커플링 — 발행 도메인은 알림을 모른다
- `NotificationService.fanOut(recipientIds, type, targetType, targetId, actorId, title, message, dedupKeyFn)` 한 메서드. InquiryService.answer가 이것만 호출(단방향 의존, 순환 없음). Phase 4에서 회차/댓글/팔로우/멘션 트리거가 같은 메서드에 붙음. (이벤트 리스너/@Async 인프라는 Phase 4.)

### 5) 멱등 dedup은 **수신자 단위** — 적대적 리뷰가 잡은 사일런트 드롭 함정
- 14에이전트 리뷰가 한 결함으로 수렴: 전역 `unique(dedup_key)` + `existsByDedupKey`인데 fanOut 시그니처는 `Function<Long,String>`(수신자별 키). **다대상 fan-out 시 A의 알림 때문에 B가 자기 알림을 못 받는다.** Phase3는 단일 수신자라 안 터지지만 **지금 만드는 공유 프리미티브가 시그니처와 모순** → 복합 `unique(recipient_id, dedup_key)` + `existsByRecipientIdAndDedupKey`로 정정. **검증 에이전트는 "Phase4로 미뤄도 됨"이라 했지만, 추측성 유연성이 아니라 이미 약속된 계약과의 정합 수정이라 지금 고침.** (TOCTOU 500은 1인 단일관리자 기준 발생불가 → 보류, native upsert는 Phase4.)
- **마이그레이션 이미 적용 후 수정**: V20가 dev DB에 적용된 뒤 인덱스 정정 → `drop table notifications; delete from flyway_schema_history where version='20'`로 **V20만 롤백**(전체 wipe 없이 시드 보존) 후 재적용. 적용된 마이그레이션 수정 = checksum 불일치(validate 실패) 주의.

### 6) 적대적 리뷰의 메타 교훈
- 리뷰 비평은 **명령이 아니라 입력** — "isReal=true지만 inScopeForPhase3=false"가 4건. 그중 dedup 정합은 내 판단으로 **승격해서 지금 수정**, TOCTOU는 가이드라인대로 **보류**. 비평을 그대로 따르지도, 무시하지도 않고 프로젝트 원칙(단순성·발생불가 예외처리 금지·사일런트 데이터손실 방지)으로 재정렬.

---

## STEP 25 — 알림 트리거 5종 + 닉네임 고유화 (Phase 4: 이벤트·멘션) ✅ 완료 (커밋: `feat: STEP 25 ...`)

> 댓글·대댓글·팔로우·@멘션·새 회차를 알림에 연결. 설계 패널이 내 잠정안(이벤트 아키텍처)을 **뒤집어** 인라인 동기 fanOut 채택. 적대적 리뷰가 3건(멘션 문자셋·NFC·마이그레이션 충돌) 잡아 반영. 157테스트 + 실서버 종단검증.

### 1) 아키텍처 결정 번복 — 이벤트 폐기, 인라인 동기 fanOut
- 처음엔 `@TransactionalEventListener(AFTER_COMMIT)+@Async`로 디커플링하려 했으나, **설계 패널이 반대**: 기존 37개 `@Transactional` 테스트에서 AFTER_COMMIT은 **발화 안 됨**(커밋이 안 일어나니) → 회귀. 디커플링은 요청 안 됨. INQUIRY_ANSWERED가 이미 인라인 동기로 검증됨. → 각 Service 쓰기 메서드에서 `notificationService.fanOut(...)` 직접 호출. **단순성·테스트 결정성·원자성(같은 tx 롤백)** 승리. 멀티에이전트 비평은 명령이 아니라 입력 — 내 잠정안도 뒤집힐 수 있다.

### 2) 수신자·dedup 매트릭스(5트리거)
- POST_COMMENT(글 작성자, `POST_CMT:{commentId}`) / COMMENT_REPLY(**평탄화 부모** 작성자 — resolveParent를 `Long`→`ParentRef(effectiveParentId, parentAuthorId)`로 확장) / FOLLOWED(팔로우 당한 이, `FOLLOW:{followerId}` 영구멱등=재팔로우 무알림) / POST_MENTIONED(글·댓글 본문, `(POST|CMT)_MENTION:{id}:{rid}`) / EPISODE_PUBLISHED(구독자−작가, `EP_PUB:{episodeId}` 두 발행경로 멱등). 모두 actor/self 제외.

### 3) 닉네임 고유화 — @멘션이 1명을 가리키도록
- nickname에 unique 없었음(시드 '작가'×N). V21로 기존 중복 접미사 해소 + unique. 가입·변경 사전검사(`DUPLICATE_NICKNAME` 409). 멘션은 `findByNicknameIn`(1쿼리)로 해석. **닉네임 unique 추가 시 커밋(비-@Transactional)테스트 충돌**: AuthFlowTest/AuthServiceTest 둘 다 '닉' 커밋 → SeriesDetailSerializationTest의 deleteAll이 사이에 끼어 우연히 통과(순서의존). 내 변경이 만든 취약점이라 두 곳 고유화.

### 4) 적대적 리뷰 3건 반영 (멀티에이전트가 잡은 실결함)
- **(high) V21 접미사 재충돌**: `nick||'_'||id`가 *기존 리터럴* 닉('bob_5')과 또 겹치면 ALTER 실패 → 부팅 중단. 설계 패널은 "단일 패스로 충돌 완전 해소"라 단언했으나 **틀림**. → 충돌 없을 때까지 `_` 덧붙이는 `do $$` 루프로. **마이그레이션은 정확성>단순성**(실패 시 부팅 불가).
- **(medium) 멘션 문자셋<닉네임 문자셋**: 닉네임은 `@Size`만이라 공백·특수문자 가능한데 멘션 regex는 한글+영숫자+_만 → 일부 유저 영구 멘션 불가(사일런트 누락). → 닉네임 DTO에 `@Pattern([\p{IsHangul}A-Za-z0-9_]{1,20})` 추가해 **유효닉=멘션가능** 일치.
- **(low) NFD/NFC**: Apple 입력의 NFD 분해 한글 @멘션이 NFC 저장 닉과 String 불일치 → 무음 미스. → `Mentions.extract`(본문)+가입/변경(저장) 양쪽 `Normalizer.NFC`. **반쪽 정규화는 더 나쁨**(불일치 잔존)이라 양쪽.
- 보류(노트): publishDueEpisodes 회차당 N+1(순수 성능), 블라인드 부모 댓글 답글→블라인드 작성자 알림(모더레이션 엣지).

### 5) 마이그레이션 이미 적용 후 정정 패턴(재확인)
- V21을 dev DB 적용 후 로직 정정 → `drop constraint + delete flyway_schema_history where version='21'` 후 재적용(데이터 이미 deduped라 루프 no-op + constraint 재생성). Testcontainers는 매번 fresh라 새 V21 그대로 검증.

---

## STEP 26 — 독자 서재 / 활동내역 (Phase 5: 런타임 조합·재사용) ✅ 완료 (커밋: `feat: STEP 26 ...`)

> 서재(열람·관심·구독)+활동(내 글·내 댓글·추천·언급)을 **별도 테이블 없이 기존 도메인 조합**으로. 설계 패널이 "재사용 vs 신규" 경계를 확정. 163테스트. **사용자가 프로브 테스트로 sort 500 버그를 직접 발견** → 수정 후 적대적 리뷰가 같은 버그를 독립 확인.

### 1) 재사용 경계 — 이미 있는 걸 다시 만들지 않기
- 구독(`/api/me/subscriptions`)·북마크(`/api/me/bookmarks`)·**멘션(`/api/me/notifications?type=POST_MENTIONED`)은 코드 0으로 재사용**. 멘션은 Phase4 알림이 곧 기록이라 별도 테이블/엔드포인트 불필요(YAGNI). 신규는 4종만: read-history·posts·post-comments·liked-posts.

### 2) 블라인드 비대칭(의도적) + N+1 배치
- **내 콘텐츠(내 글·내 댓글)=블라인드 포함+flag** / **타인 콘텐츠(liked-posts)=블라인드 제외**. liked-posts/post-comments의 `totalElements`는 원본 페이지(PostLike/PostComment) 기준 유지(필터로 content<size 가능 — 의도). 닉네임·원글제목은 `findAllById` Map 배치(쿼리 2개 고정).

### 3) **고정 정렬 목록에 클라 `?sort=`가 들어오면 500** (사용자 프로브 발견)
- read-history는 group-by 프로젝션 + 고정 `order by max(createdAt)`인데 Pageable의 `sort`가 쿼리에 append됨 → `?sort=createdAt`/`?sort=garbage` 시 **SQL 오류 500**(group-by 비호환/미존재 컬럼). posts 등 파생쿼리도 미존재 속성 sort→PropertyReferenceException 500. **어떤 인증 사용자든 sort 파라미터 하나로 엔드포인트를 죽일 수 있음.**
- **수정**: `Pageables.pageOnly(pageable)`(page/size만, sort 버림)을 4개 서비스에 적용. 서버 정렬이 고정인 목록은 클라 sort를 받지 않는 게 맞다. 회귀 테스트로 4×3 sort 조합 200 단언. **기존에 PostSort enum 화이트리스트 패턴이 있었는데 신규 엔드포인트가 그걸 안 따른 게 원인** → 고정정렬은 sort-strip으로 통일.
- read-history **2차 정렬키**(`, series.id desc`) 추가 — `max(createdAt)` 동률 시 페이지 경계 중복/누락 방지(stable pagination).

### 4) 리뷰가 틀린 케이스 — FK를 안 봄
- 리뷰가 "원글 삭제 시 내 댓글 스킵 미검증"이라 했으나 `post_comments.post_id → posts on delete cascade`라 **원글 삭제 시 댓글도 함께 삭제 → 고아 댓글 자체가 발생 불가능**. 서비스의 `Map.get` null-safety는 방어로 유지하되, 발생 불가능 시나리오라 테스트는 안 만듦. **비평도 스키마까지 봐야 — 코드만 보면 놓침.**

### 5) DTO 평면 record라 LAZY 직렬화 위험 없음
- 모든 응답이 평면 record(서비스 tx 내 완전 구성, LAZY 연관 없음)라 [[apptoon-lazy-serialization-gotcha]]의 OSIV-off 500이 발생할 수 없음 → @Transactional 테스트로 충분(직렬화는 라이브 스모크로 추가 확인).

---

## STEP 27 — 작가 소식 피드 (Phase 6: Follow⨝Post 재사용) ✅ 완료 (커밋: `feat: STEP 27 ...`)

> 팔로우한 사용자들의 공개 글을 최신순 피드로 + 작가별 공개 글. **새 테이블·새 엔티티 0** — Follow(STEP21)+Post(STEP22)+PostResponse+닉네임배치+`Pageables.pageOnly`(STEP26) 전부 재사용. 166테스트 + 라이브 검증. ultracode 아님 → 워크플로 없이 TDD 직접.

### 1) 피드 = 서브쿼리 한 방
- `GET /api/me/author-news-feed`: `select p from Post p where p.blinded=false and p.authorId in (select f.followingId from Follow f where f.followerId=:userId) order by p.id desc`. 팔로우 ids를 따로 안 뽑고 JPQL 서브쿼리로 1쿼리. 블라인드 제외(타인 콘텐츠). 닉네임만 배치(N+1 없음).
- `GET /api/authors/{id}/posts`: 작가 공개 프로필용 — `findByAuthorIdAndBlindedFalseOrderByIdDesc`(파생쿼리). 내 글(`/api/me/posts`, 블라인드 포함)과 대비 — **공개 뷰는 블라인드 제외**.

### 2) 직전 단계 패턴 그대로 흡수(연관성)
- 고정정렬이라 STEP26의 `Pageables.pageOnly`로 클라 sort 무시(라이브에서 `?sort=garbage`→200 확인). `PostResponse` 재사용, `toPostResponses(Page<Post>)` 헬퍼로 닉네임 배치 매핑(getList와 중복 최소화하되 getList는 PostSort 정렬이라 별도 유지=정밀수정).

### 3) 도메인 배치
- 피드는 me-스코프라 `MyActivityController`(/api/me)에, 작가 공개글은 `AuthorController`(/api/authors) 신설. 커뮤니티 전체가 인증 게이트(`anyRequest().authenticated()`)라 둘 다 로그인 필요(일관성, SecurityConfig 무변경).

---

## STEP 28 — 수익화 0단계: 작가 공개정책 + 기다리면무료 (수익모델 척추) ✅ 완료 (커밋: `feat: STEP 28 ...`)

> 결제 0으로 작가가 작품 단위 공개정책(전체무료/기다리면무료)을 설정하고, 회차 열람에 **엔타이틀먼트 가드 seam**을 끼움. **사용자 핵심요구=미래 결제·정산을 재작업 없이 얹는 설계.** 180테스트 + 라이브. 설계는 멀티에이전트 패널(코드검증 스펙), 검증은 적대적 리뷰.

### 1) 기다리면무료 = compute-on-read (스케줄러 0)
- 잠금을 상태로 저장하지 않고 **읽을 때 계산**: `freeAt = publishAt + waitFreeDays일`, `now >= freeAt`면 무료. 스케줄러·backfill·상태전이 0, 멱등. 설계 문서는 "스케줄러가 자동 전환"이라 했으나 패널이 compute-on-read로 단순화(상태 없음이 승). 정책 전환 시 과거 회차는 freeAt 이미 지나 **소급 안 잠김**(직관적·의도). EpisodeStatus는 불변(잠금≠상태).

### 2) 엔타이틀먼트 가드 seam — 미래 재작업 0의 핵심
- `EpisodeAccessEvaluator`(@Component) + `AccessResult(accessible, lockReason{NONE,WAIT}, freeAt)`. 0단계는 owner/FREE_ALL/시간계산만. **미래 주입점 ①**(waitLocked 반환 직전 한 자리)에 엔타이틀먼트·멤버십이 `if(...) return open()` 1줄로 끼움(viewerId·series 이미 파라미터). @Component라 미래 의존성주입 수용. `LockReason`은 2값만(PAID/MEMBERSHIP 미래=YAGNI). **정직한 표현: 구조 0재작업 + 데이터 증분**(PAID 가격 노출은 AccessResult/DTO에 price 필드 추가=스키마 증분). 확장점 전체 매핑은 [`docs/roadmap-and-monetization.md`] §3.6.

### 3) 잠긴 회차 = 200 + locked (throw 아님)
- 결정근거: `ErrorResponse(status,code,message,fieldErrors)`가 **freeAt 같은 임의필드를 못 실음** → 403/404 throw면 카운트다운 메타 전달 불가. 잠금은 "존재 은닉"이 아니라 "지연된 접근"이라 의미론도 200. **이미지 URL은 절대 비노출**(`/files` 정적서빙 URL 유추 차단) — locked 팩토리가 images=[], viewCount 미증가.

### 4) 적대적 리뷰가 잡은 가드 비대칭 — markRead 잠금 우회
- getDetail은 evaluate로 잠그는데 **markRead(읽음)는 `verifyInteractable`(visible·연령)만 거쳐 WAIT 락을 안 봄** → 못 본 잠긴 회차를 `POST /read`로 읽음 처리 가능 → `findMaxReadEpisodeNo`→lastReadNo 올라가 **UP 배지 꺼지고 서재에 안 본 작품 섞임**. 미래 PAID면 "안 사고 읽음"=정산 오염 seam 리스크. → markRead에 동일 evaluate(isPrivileged=isAuthoredBy) 추가, 잠기면 **no-op 스킵**(throw 아닌, 200+locked 설계와 일관). **교훈: 콘텐츠 가드는 읽기 한 곳이 아니라 모든 소비/상호작용 경로에 대칭으로.**

### 5) 범위 균형 — 확장성 ≠ 과설계
- 미래 끼울 "자리"는 명시하되 코인·엔타이틀먼트테이블·원장·PG·previewPrice 컬럼은 **안 만듦**(YAGNI — 사용처 없는 추상화/컬럼=검증·문서·테스트 부채). 플랫컬럼(Series에 @Embeddable 전례 0), 단위테스트는 `new EpisodeAccessEvaluator()` 직접 생성(0단계 무의존이라 정상).

---

## 부록 A. 자주 쓰는 명령어
```bash
docker compose up -d                 # DB 컨테이너 기동(백그라운드)
docker ps                            # 떠 있는 컨테이너 확인
docker compose down                  # 중지(데이터는 볼륨에 유지)
./gradlew test                       # 테스트(Testcontainers 포함)
./gradlew bootRun                    # 서버 실행
curl http://localhost:8080/api/health
docker exec apptoon-db psql -U apptoon -d apptoon -c "\dt"   # 테이블 목록
```

## 부록 B. 헷갈리기 쉬운 개념 한 줄 정리
- **Entity vs DTO:** Entity=DB 매핑 객체(내부용), DTO=API 입출력 객체(외부용). 섞으면 보안·순환참조·계약 깨짐.
- **인증(Authentication) vs 인가(Authorization):** 인증=너 누구냐, 인가=너 이거 해도 되냐.
- **이미지 vs 컨테이너:** 이미지=설계도(읽기전용), 컨테이너=설계도로 찍어낸 실행 중 인스턴스.
- **`down` vs `down -v`:** 전자는 컨테이너만 삭제(데이터 유지), 후자는 볼륨까지 삭제(데이터 소멸).
