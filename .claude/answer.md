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
