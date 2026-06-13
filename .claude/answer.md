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
