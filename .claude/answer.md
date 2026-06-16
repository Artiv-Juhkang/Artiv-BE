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
