# AppToon 백엔드 — 배포 가이드

로컬에서만 돌던 서버를 외부 사용자가 접근할 수 있게 클라우드에 올리는 방법. **빠른 길(PaaS)** 과 **본격(AWS)** 두 트랙을 단계별로 정리했다. 그대로 따라 하면 외부 공개까지 도달한다.

> 핵심 한 줄: **외부 공개 = 24/7 서버 + 관리형 DB + 이미지 스토리지(S3) + HTTPS.** 도메인·AWS는 선택지일 뿐이다.

이 문서는 실제 코드를 근거로 작성됐다. 인용 경로:
- `src/main/resources/application.yml` — 기본값·환경변수 바인딩
- `src/main/resources/application-prod.yml` — 운영 DB 필수 주입, SQL 로그 off
- `src/main/resources/application.yml` — 공통 설정 + 기본 프로파일(`spring.profiles.default: dev`)·`${JWT_SECRET}`(기본값 없음). `application-dev.yml`은 dev 기본값(DB·JWT·로깅)을 덧씌움
- `src/main/java/com/juhkang/apptoon/global/config/SecurityConfig.java` — CORS·stateless·permitAll
- `src/main/java/com/juhkang/apptoon/global/storage/S3Config.java` / `S3ObjectStorage.java` / `LocalObjectStorage.java`
- `src/main/java/com/juhkang/apptoon/global/config/WebConfig.java` — `/files/**` 정적 서빙
- `src/main/java/com/juhkang/apptoon/domain/episode/EpisodePublisher.java` — `@Scheduled` 예약 발행
- `src/main/java/com/juhkang/apptoon/global/health/HealthController.java` — `/api/health`

---

## 0. 배포 전 체크리스트

### 이미 준비된 것 ✅
- **prod 프로파일 분리** — `application-prod.yml`은 DB를 `${DB_URL}/${DB_USER}/${DB_PASSWORD}` 필수 환경변수로만 받고 `show-sql=false`.
- **시크릿 환경변수화(12-factor)** — `application.yml`에서 `${JWT_SECRET}`, `${DB_*}`, `${STORAGE_TYPE}`, `${CORS_ALLOWED_ORIGINS}`, `${S3_*}` 전부 외부 주입. 코드·깃에 운영 비밀값 없음(`.gitignore`가 `.env`, `/storage/` 제외).
- **CORS 환경변수화** — `SecurityConfig`가 `app.cors.allowed-origins`를 `setAllowedOriginPatterns`로 적용. `allowCredentials=false`(쿠키 미사용, `Authorization` 헤더).
- **스키마 자동 적용** — Flyway `enabled`, V1~V12 마이그레이션이 기동 시 자동 적용. Hibernate `ddl-auto=validate`라 스키마 소유권은 Flyway, JPA는 검증만 → 운영 안전.
- **stateless 인증** — `SessionCreationPolicy.STATELESS` + CSRF disable + JWT 필터. 세션 공유 없이 수평 확장 가능.
- **이미지 저장 추상화** — `ObjectStorage` 인터페이스 + `LocalObjectStorage`(local, `matchIfMissing`) / `S3ObjectStorage`(s3). `STORAGE_TYPE=s3`로 코드 변경 없이 MinIO/R2/AWS S3 전환.
- **헬스 엔드포인트** — `GET /api/health` → `{"status":"ok"}` (permitAll). PaaS/로드밸런서 프로브로 즉시 사용.
- **빌드 재현성** — Gradle wrapper(`gradlew`, `gradle/wrapper/`) 포함, `bootJar` 태스크, Java 25 toolchain 고정.

### 배포 전 반드시 처리해야 할 것 ⚠️
| 항목 | 현재 | 운영에서 해야 할 일 |
|---|---|---|
| **Dockerfile** | 루트에 **있음**(`Dockerfile` + `.dockerignore`, docker build 검증 완료) | 그대로 사용 |
| **이미지 저장** | 로컬 `storage/` 폴더 | **오브젝트 스토리지(S3/R2)** — 컨테이너는 재시작 시 디스크 초기화 → 로컬 저장은 이미지가 사라진다. 멀티 인스턴스면 로컬 디스크는 아예 불가(인스턴스 간 공유 안 됨) → **S3 사실상 필수** |
| **DB** | 로컬 Docker Postgres(`devpass`) | 관리형 Postgres(RDS/Supabase/Neon/PaaS 애드온) |
| **JWT_SECRET** | dev는 `application-dev.yml`가 기본값 덧씌움 | **운영 전용 강한 키**. 베이스 `application.yml`의 `${JWT_SECRET}`엔 기본값이 없어 dev 프로파일이 아니면 미주입 시 **기동 실패** |
| **SPRING_PROFILES_ACTIVE** | 미설정 시 `profiles.default=dev` | 운영에선 반드시 `prod` 지정(안 하면 dev JWT/DB 기본값을 씀) |
| **CORS** | `*`(전체 허용) | 실제 프론트 origin으로 좁힘 |

---

## 1. 빌드 & 컨테이너화 (Dockerfile 신규 작성)

> **프로젝트 루트에 `Dockerfile`(멀티스테이지, JDK 25)과 `.dockerignore`가 이미 있다**(`docker build` 검증 완료, 이미지 ~641MB). 아래는 그 내용이다.

### 로컬에서 jar 확인
```bash
./gradlew bootJar          # build/libs/AppToon-0.0.1-SNAPSHOT.jar
```

### `Dockerfile` (프로젝트 루트)
멀티스테이지 — JDK 25로 빌드하고, 더 가벼운 JRE 25로 실행. Gradle wrapper가 리포에 있으니 그대로 사용한다.
```dockerfile
# --- build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# 의존성 캐시 레이어: 빌드 스크립트/래퍼만 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
# 소스 복사 후 jar 빌드(테스트 제외 — CI에서 별도 수행)
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

# --- run stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app
# 비루트 사용자로 실행(보안)
RUN useradd -r -u 1001 appuser
COPY --from=build /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
# 컨테이너 메모리 인지 + prod 프로파일은 환경변수로 주입
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### `.dockerignore` (프로젝트 루트)
없으면 `storage/`(업로드물)·`build/`·`.git`·`.gradle`까지 빌드 컨텍스트에 들어가 이미지가 비대해지고 비밀이 샐 수 있다.
```gitignore
.git
.gradle
build/
storage/
.env
*.iml
.idea/
.vscode/
graphify-out/
```

### 로컬 컨테이너 동작 확인
```bash
docker build -t apptoon:local .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/apptoon \
  -e DB_USER=apptoon -e DB_PASSWORD=devpass \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  apptoon:local
curl localhost:8080/api/health   # {"status":"ok"}
```

---

## 2. 운영 환경변수 — 완전 표

어느 플랫폼이든 아래를 주입한다. **필수**를 빠뜨리면 기동 실패하거나 dev 기본값으로 떨어진다.

| 변수 | 필수 | 용도 / 근거 | 예시 |
|---|:---:|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | 운영 프로파일 활성화. 미설정 시 `application.yml`의 `profiles.default=dev`로 떨어져 dev JWT/DB 기본값 사용 | `prod` |
| `DB_URL` | ✅ | PostgreSQL JDBC URL. `application-prod.yml`에 기본값 없음 → 미주입 시 기동 실패 | `jdbc:postgresql://db-host:5432/apptoon` |
| `DB_USER` | ✅ | DB 접속 계정 | `apptoon` |
| `DB_PASSWORD` | ✅ | DB 접속 비밀번호(시크릿 매니저 권장) | `<strong-db-password>` |
| `JWT_SECRET` | ✅ | JWT HS256 서명 키(≥32바이트). prod에 기본값 없어 미설정 시 기동 실패 | `$(openssl rand -base64 48)` |
| `CORS_ALLOWED_ORIGINS` | ⚠️ | 허용 프론트 origin 화이트리스트(콤마 구분). 기본값 `*`(전체)이므로 **운영에선 반드시 좁힘**. `SecurityConfig.setAllowedOriginPatterns` 적용 | `https://app.example.com,https://admin.example.com` |
| `STORAGE_TYPE` | ⚠️ | 이미지 저장 백엔드. `local`(디스크+`/files`) \| `s3`(MinIO/R2/AWS S3). 컨테이너·멀티인스턴스 운영은 `s3` 권장 | `s3` |
| `STORAGE_ROOT` | ☐ | `type=local`일 때 업로드 저장 루트(`WebConfig`의 `/files` 서빙 경로와 일치). 영속 볼륨으로 마운트해야 재시작 시 소실 방지 | `/data/storage` |
| `S3_ENDPOINT` | ⚠️ | S3 호환 엔드포인트. `S3Config`에 기본값 없음 → `STORAGE_TYPE=s3`면 **빈 값이라도 주입 필요**(아래 ‘함정’ 참고). MinIO/R2/AWS S3 모두 명시 | `https://s3.ap-northeast-2.amazonaws.com` |
| `S3_REGION` | ☐ | S3 리전. 기본값 `us-east-1` | `ap-northeast-2` |
| `S3_BUCKET` | ☐ | 이미지 저장 버킷명(사전 생성). 기본값 `apptoon` | `apptoon-prod` |
| `S3_ACCESS_KEY` | ⚠️ | S3 접근 키(`StaticCredentialsProvider`). 시크릿 관리 권장 | `<access-key>` |
| `S3_SECRET_KEY` | ⚠️ | S3 시크릿 키 | `<secret-key>` |
| `S3_PUBLIC_BASE_URL` | ⚠️ | 이미지 공개 베이스 URL(또는 CDN). `S3ObjectStorage.urlFor`가 `{base}/{key}`로 내려감. 버킷 public read 또는 CloudFront | `https://cdn.example.com/apptoon` |

> ✅ 항상 필수 · ⚠️ 해당 모드/운영에서 사실상 필수 · ☐ 기본값 있어 선택.

> **S3_ENDPOINT 함정:** `S3Config`의 `@Value("${app.storage.s3.endpoint}")`에 기본값이 없고 `URI.create(endpoint)`를 호출한다. `STORAGE_TYPE=s3`면 AWS S3라도 엔드포인트를 **명시**해야 한다(이전 버전 서술의 ‘AWS는 생략 가능’은 코드와 충돌). AWS는 `https://s3.<region>.amazonaws.com` 형태로 넣는다.

---

## 3. 배포 방법 비교 — 어떤 길로 갈까

| 방법 | 난이도 | 비용(소규모) | 인프라 제어 | HTTPS·DB | 추천 상황 |
|---|---|---|---|---|---|
| **PaaS** (Railway / Render / Fly.io) | ★☆☆ | 무료~$5~10/월 | 낮음 | 자동 | 학습·MVP·개인 프로젝트. **가장 빠름(~30분)** |
| **AWS App Runner** | ★★☆ | ~$25~40/월~ | 중간(관리형 컨테이너) | ACM 연동, RDS 별도 | AWS 생태계에 머물되 EC2 운영 부담은 피하고 싶을 때 |
| **AWS EC2 + RDS + S3 (+ALB)** | ★★★ | ~$30~70/월~ | 높음(전부 직접) | ACM+ALB 직접 | 실무 인프라 학습, 세밀한 제어·VPC 구성이 필요할 때 |

> 권장 경로: **PaaS로 먼저 외부 공개 → 감을 잡은 뒤 App Runner → 필요 시 EC2/RDS/S3 풀스택**으로 단계적으로 옮긴다.

---

## 4. 트랙 A — PaaS로 빠르게 (Railway 기준, ~30분)

Railway 예시. Render/Fly.io도 흐름은 동일하다(차이는 끝에 정리).

### 4-1. 리포 연결
1. https://railway.app 가입(GitHub 로그인).
2. **New Project → Deploy from GitHub repo** → `AppToon` 리포 선택.
3. Railway가 루트의 `Dockerfile`을 감지해 빌드한다(리포에 포함됨). Java 25/`bootJar` 안정성을 위해 Dockerfile을 사용한다.

### 4-2. Postgres 애드온 추가
1. 프로젝트 캔버스에서 **New → Database → Add PostgreSQL**.
2. 생성되면 Postgres 서비스의 **Variables**에 `DATABASE_URL`, `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`가 제공된다.
3. Railway의 `DATABASE_URL`은 `postgresql://user:pass@host:port/db` 형식이라 **JDBC URL과 형식이 다르다.** 앱 서비스 변수에 아래처럼 **참조 변수**로 매핑한다(`${{Postgres.PGHOST}}`는 Railway 참조 문법):
   - `DB_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   - `DB_USER` = `${{Postgres.PGUSER}}`
   - `DB_PASSWORD` = `${{Postgres.PGPASSWORD}}`

### 4-3. 환경변수 입력 (앱 서비스 → Variables)
| 변수 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `JWT_SECRET` | `openssl rand -base64 48` 결과 붙여넣기 |
| `CORS_ALLOWED_ORIGINS` | `https://your-frontend.example.com` |
| `STORAGE_TYPE` | `s3` (R2/S3 사용 시. 미사용 시 `local`이지만 재시작 소실 주의) |
| `S3_ENDPOINT` `S3_REGION` `S3_BUCKET` `S3_ACCESS_KEY` `S3_SECRET_KEY` `S3_PUBLIC_BASE_URL` | 6번 스토리지 절차 참고 |

> JWT_SECRET 생성: `openssl rand -base64 48`

### 4-4. 빌드·배포
1. 변수 저장 후 Railway가 자동 재배포. 빌드 로그에서 Dockerfile 빌드 → `bootJar` → 컨테이너 기동 확인.
2. 기동 시 Flyway가 V1~V12를 자동 적용한다. 로그에 `Successfully applied N migrations`가 보이면 정상.

### 4-5. 도메인 / HTTPS
1. 앱 서비스 → **Settings → Networking → Generate Domain** → `https://apptoon-xxx.up.railway.app` 발급(HTTPS 자동).
2. 커스텀 도메인은 **Custom Domain** 추가 → 안내된 CNAME을 DNS에 등록 → 인증서 자동 발급.

### Render / Fly.io 차이
- **Render**: New → Web Service → 리포 연결, **Docker** 런타임 선택. PostgreSQL은 별도 **New → PostgreSQL**로 생성하고 Internal Database URL을 위와 같이 JDBC로 매핑. Health Check Path에 `/api/health` 지정. HTTPS·서브도메인 자동.
- **Fly.io**: `fly launch`(Dockerfile 감지) → `fly postgres create` 후 `fly postgres attach`로 DB 연결. `fly secrets set JWT_SECRET=... CORS_ALLOWED_ORIGINS=...`로 시크릿 주입. `fly.toml`의 `[[services]]`에 `internal_port = 8080`, health check `/api/health` 설정. `fly deploy`.

**장점**: HTTPS·DB·배포 자동, 무료~저가. **한계**: VPC·세밀한 네트워크 제어는 약함. **이미지 영속성**: PaaS 컨테이너 디스크는 휘발 → 6번 S3/R2 적용 필수(또는 Railway Volume/Fly Volume 마운트 + `STORAGE_ROOT`).

---

## 5. 트랙 B — AWS 본격 구성 (실무 학습용)

전형적 구성과 역할:

| 구성요소 | 역할 |
|---|---|
| **RDS for PostgreSQL** | 관리형 DB (`DB_URL`로 연결) |
| **S3** | 업로드 이미지 저장(`STORAGE_TYPE=s3`) |
| **ECR** | 컨테이너 이미지 레지스트리 |
| **App Runner** *또는* **ECS Fargate** | 서버 컨테이너 실행(App Runner가 더 쉬움) |
| **ALB + ACM** | HTTPS 인증서 + 로드밸런서(TLS 종료, 다중 인스턴스) — ECS 트랙에서 사용 |
| **Route 53** | 도메인 구입·DNS (`apptoon.com` → 서버) |
| **Secrets Manager / SSM Parameter Store** | `JWT_SECRET`·DB·S3 키 관리 |

아래 명령은 `aws` CLI v2 + 리전 `ap-northeast-2`(서울) 기준 예시. 콘솔로도 동일하게 가능.

### 5-1. RDS PostgreSQL 생성
```bash
aws rds create-db-instance \
  --db-instance-identifier apptoon-prod \
  --engine postgres --engine-version 16 \
  --db-instance-class db.t4g.micro \
  --allocated-storage 20 \
  --master-username apptoon \
  --master-user-password '<strong-db-password>' \
  --db-name apptoon \
  --no-publicly-accessible \
  --backup-retention-period 7
```
- 보안그룹: **앱(App Runner VPC 커넥터 / ECS 태스크) 보안그룹에서만 5432 인바운드 허용**. 공개 금지.
- 생성 후 엔드포인트로 `DB_URL` 구성: `jdbc:postgresql://apptoon-prod.xxxx.ap-northeast-2.rds.amazonaws.com:5432/apptoon`.

### 5-2. S3 버킷 생성 (이미지 저장)
```bash
aws s3api create-bucket --bucket apptoon-prod \
  --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
```
공개 read 정책(이미지를 URL로 바로 노출하는 경우 — CDN을 안 쓸 때). 6번의 보안 절도 함께 검토할 것:
```bash
aws s3api put-public-access-block --bucket apptoon-prod \
  --public-access-block-configuration BlockPublicPolicy=false,IgnorePublicAcls=true,BlockPublicAcls=true,RestrictPublicBuckets=false

aws s3api put-bucket-policy --bucket apptoon-prod --policy '{
  "Version":"2012-10-17",
  "Statement":[{"Sid":"PublicReadGetObject","Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::apptoon-prod/*"}]
}'
```
- `S3_ENDPOINT=https://s3.ap-northeast-2.amazonaws.com`, `S3_REGION=ap-northeast-2`, `S3_BUCKET=apptoon-prod`.
- `S3_PUBLIC_BASE_URL=https://apptoon-prod.s3.ap-northeast-2.amazonaws.com` (또는 6번 CloudFront 도메인).
- 코드는 `forcePathStyle(true)`라 path-style 접근도 동작한다.

### 5-3. ECR에 이미지 푸시
```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-northeast-2
aws ecr create-repository --repository-name apptoon
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ACCOUNT.dkr.ecr.$REGION.amazonaws.com
docker build -t apptoon:latest .
docker tag apptoon:latest $ACCOUNT.dkr.ecr.$REGION.amazonaws.com/apptoon:latest
docker push $ACCOUNT.dkr.ecr.$REGION.amazonaws.com/apptoon:latest
```
> Apple Silicon(arm64)에서 빌드한다면 App Runner/Fargate 플랫폼과 아키텍처를 맞춰라(`docker build --platform linux/amd64 ...` 또는 태스크를 ARM64로 설정).

### 5-4-A. App Runner로 실행 (권장 — 더 쉬움)
1. **App Runner → Create service → Source: Container registry(ECR)** → 위 이미지 선택, 자동 배포 on.
2. **Port: 8080**. **Health check path: `/api/health`** (HTTP).
3. **Environment variables**에 2번 표의 운영 변수 입력(`SPRING_PROFILES_ACTIVE=prod`, `DB_URL/USER/PASSWORD`, `JWT_SECRET`, `CORS_*`, `STORAGE_TYPE=s3`, `S3_*`). 시크릿은 Secrets Manager 참조로 주입 가능(아래 9번).
4. **VPC Connector** 생성 → RDS와 같은 VPC/서브넷에 연결해야 비공개 RDS에 접근 가능.
5. 생성되면 `https://xxxx.ap-northeast-2.awsapprunner.com` HTTPS 주소 자동 발급.
6. 커스텀 도메인: App Runner → **Custom domains**에서 도메인 추가 → 제시된 검증 CNAME을 Route 53에 등록(인증서 자동).

### 5-4-B. ECS Fargate + ALB로 실행 (제어 우선)
1. **ECS 클러스터(Fargate)** 생성.
2. **Task Definition**: 컨테이너 이미지=ECR URI, 포트 8080, 환경변수/시크릿 주입, 로그 드라이버 `awslogs`(CloudWatch).
3. **ALB(Application Load Balancer)** 생성 → **Target Group(HTTP, 8080)** → 헬스체크 경로 `/api/health`.
4. **ACM**에서 인증서 발급(`apptoon.com`, DNS 검증) → ALB **HTTPS:443 리스너**에 연결, HTTP:80은 443으로 리다이렉트.
5. **Service** 생성: Task N개, ALB Target Group 연결. RDS 보안그룹에 태스크 보안그룹 5432 허용.
6. **Route 53**: `apptoon.com`(또는 `api.apptoon.com`) **A 레코드(Alias) → ALB**.

> **멀티 인스턴스 주의(중요):** Task/인스턴스를 2개 이상으로 두면 `@Scheduled` 예약 발행 폴러가 인스턴스마다 동시에 돈다. 8번 ‘예약 발행 멀티인스턴스’ 절을 반드시 읽어라.

---

## 6. 이미지 스토리지 운영 (S3 / R2 / CDN)

### 왜 S3가 사실상 필수인가
- 컨테이너 디스크는 재시작 시 초기화 → `STORAGE_TYPE=local`이면 업로드 이미지가 사라진다.
- 멀티 인스턴스면 로컬 디스크는 인스턴스 간 공유가 안 돼 이미지 일관성이 깨진다.
- 따라서 운영은 `STORAGE_TYPE=s3` 권장. 단일 인스턴스에서 굳이 local을 쓰려면 **영속 볼륨을 `STORAGE_ROOT`에 마운트**해야 한다.

### 버킷 준비 공통
- 버킷을 **미리 생성**(AWS S3 5-2 / R2 대시보드 / MinIO `mc mb`).
- 이미지 객체를 **public read**로 노출하거나 CDN 뒤에 둔다. `S3ObjectStorage.urlFor`는 `S3_PUBLIC_BASE_URL + "/" + key`를 그대로 응답하므로, 그 베이스 URL이 외부에서 GET 가능해야 한다.
- 저장 키 규약(코드 기준): `{seriesId}/{episodeNo}/{order}.{ext}`. 버킷 루트에 이 구조로 쌓인다.

### Cloudflare R2
- R2 버킷 생성 → R2 API 토큰(Access Key/Secret) 발급.
- `S3_ENDPOINT=https://<accountid>.r2.cloudflarestorage.com`, `S3_REGION=auto`(or `us-east-1`), `S3_BUCKET=apptoon`.
- public 접근은 R2의 **Public bucket(r2.dev) 또는 커스텀 도메인 연결** → 그 도메인을 `S3_PUBLIC_BASE_URL`로.

### CDN(CloudFront) 얹기 — 권장
1. CloudFront 배포 생성, **Origin = S3 버킷**(OAC로 S3 직접 공개 없이 CloudFront만 read 허용 가능).
2. `S3_PUBLIC_BASE_URL`을 **CloudFront 도메인**(`https://dxxxx.cloudfront.net` 또는 커스텀 도메인)으로 설정.
3. 이미지 캐싱으로 응답 빨라지고 S3 비용 절감. 객체가 immutable(키에 order 포함)이라 캐시 무효화 부담 적음.

### ⚠️ 정적 이미지 인가 공백(설계 트레이드오프)
`WebConfig`의 `/files/**`와 `SecurityConfig`가 모두 **permitAll**이라, URL만 알면 비공개(`visible=false`)·성인물(`adultOnly`) 이미지에도 인가 없이 접근된다(코드 주석에 명시됨). S3 public read로 전환해도 동일 문제다. 운영에서 민감 이미지 보호가 필요하면:
- **CloudFront 서명 URL / 서명 쿠키**로 전환하거나,
- 컨트롤러 기반 인증 서빙(인가 후 스트리밍)으로 승격.

현재는 학습 범위상 보류 상태이므로, 성인물/비공개를 실제로 운영한다면 이 항목을 우선 처리할 것.

---

## 7. DB 마이그레이션 운영 (Flyway)

- **자동 적용**: 기동 시 Flyway가 `db/migration`의 V1~V12를 순서대로 적용. `baseline-on-migrate=true`라 기존 DB에 baseline을 깐다. Hibernate는 `ddl-auto=validate`로 **검증만** 하므로, 엔티티와 스키마가 어긋나면 기동이 막혀 사고를 조기에 잡는다.
- **새 마이그레이션 추가 규칙**: 항상 `V13__설명.sql`처럼 다음 번호로 추가. **이미 적용된 마이그레이션 파일은 절대 수정하지 말 것**(체크섬 불일치로 기동 실패). 컬럼 변경은 새 V 파일로.
- **롤백 주의**: Flyway Community(이 프로젝트가 쓰는 OSS)는 **자동 다운(undo) 미지원**. 잘못된 마이그레이션은 “앞으로 가는” 보정 마이그레이션(V14에서 되돌리는 DDL)으로 푼다. 운영 DB는 변경 전 **스냅샷/백업**(RDS 자동 백업, PaaS 백업) 확보 후 배포.
- **체크섬 깨짐 복구**: 의도치 않게 적용본을 건드려 기동이 막히면 `flyway repair`로 `flyway_schema_history`를 정정한다(원인 파악이 먼저).
- **다중 인스턴스 동시 기동**: Flyway가 DB 락을 잡아 동시 마이그레이션 경합은 막는다. 다만 첫 인스턴스가 마이그레이션을 끝낼 때까지 나머지는 대기하니, 롤링 배포 시 기동 타임아웃을 넉넉히.

---

## 8. 예약 발행(@Scheduled) — 멀티 인스턴스 중복 실행 ⚠️

`EpisodePublisher.publishDue()`가 `fixedDelayString="${app.episode.publish-poll-ms:60000}"`(기본 60초)로 `EpisodeService.publishDueEpisodes(now)`를 호출한다. 내부 구현은:
```java
episodeRepository.findByStatusAndPublishAtLessThanEqual(EpisodeStatus.SCHEDULED, now)
        .forEach(Episode::markPublished);
```
**행 잠금이 없는 조회→상태전환**이라, 인스턴스 N대면 같은 SCHEDULED 회차를 N번 동시에 집어 발행을 시도한다. 단일 트랜잭션 내 update라 결과적으로 PUBLISHED로 수렴은 하지만, 발행에 부가 효과(알림 발송 등)가 붙는 순간 중복 실행이 문제가 된다.

대응(택1):
- **단일 인스턴스 가정**: 현재 코드의 전제. PaaS/App Runner를 1 인스턴스로 두면 안전.
- **스케줄러 분리**: 폴러를 별도 단일 워커 프로세스로 빼고, 웹 서버 인스턴스에선 `@Scheduled`를 끈다(프로파일/조건부 빈으로 분리).
- **분산 락(ShedLock 등)**: DB/Redis 락으로 “한 번에 한 인스턴스만” 실행 보장. 수평 확장 + 예약 발행을 동시에 원하면 이 길.

> 수평 확장(인스턴스 ≥2)을 켜기 전에 위 셋 중 하나를 반드시 적용한다.

---

## 9. 시크릿 관리 · 로그 · 헬스체크

### 시크릿 관리
- **PaaS**: 서비스 Variables/Secrets에 입력(Railway Variables, Render Environment, `fly secrets`). 평문 노출 최소화.
- **AWS Secrets Manager / SSM Parameter Store**: `JWT_SECRET`, `DB_PASSWORD`, `S3_SECRET_KEY`를 저장하고, App Runner/ECS Task Definition에서 **secret 참조**로 환경변수에 매핑(이미지·태스크 정의에 평문으로 남지 않음).
- **JWT_SECRET 로테이션 주의**: 키를 교체하면 **기존 발급 토큰이 전부 무효화**된다(서명 검증 실패 → 재로그인 필요). 액세스 토큰 1시간/리프레시 14일 수명을 고려해, 유지보수 창 또는 점진 교체 전략을 잡는다.
- `.env`·`/storage/`는 `.gitignore`로 커밋 차단됨(확인 완료). 운영 비밀은 절대 리포에 넣지 않는다.

### 로그
- `application-prod.yml`에는 현재 로깅 설정이 없다(dev만 `org.hibernate.SQL=debug`). 운영은 컨테이너 **stdout**으로 나가 PaaS/CloudWatch가 수집한다.
- 권장 보강: 운영 로그 레벨을 `INFO`로 고정, 가능하면 **JSON 구조화 로깅**(로그 수집·검색 용이)과 **요청 correlation id**를 도입. 비밀값·토큰이 로그에 남지 않도록 마스킹 점검.

### 헬스체크
- `GET /api/health` → `{"status":"ok"}` 고정 반환(`HealthController`). permitAll이라 로드밸런서 프로브로 바로 쓸 수 있다.
- **한계**: DB·스토리지 연결 상태를 보지 않아 항상 ok다. DB가 끊긴 인스턴스를 LB가 정상으로 오인할 수 있다.
- 권장 보강: `spring-boot-starter-actuator`를 추가(현재 `build.gradle`에 없음)하고 `/actuator/health/readiness`·`/actuator/health/liveness`를 노출해 DB 헬스 인디케이터까지 본다. 단, actuator 경로는 SecurityConfig permitAll 목록에 추가하고 민감 엔드포인트는 노출 제한해야 한다.

### HTTPS / TLS
- **평문 HTTP로 JWT 전송 금지.** PaaS는 HTTPS 자동, AWS는 ACM 인증서 + ALB/App Runner가 TLS 종료.
- 커스텀 도메인은 ACM(또는 PaaS 자동)에서 인증서 발급 후 DNS(CNAME/Alias) 연결. 인증서 만료 자동 갱신 여부 확인.

---

## 10. 배포 후 스모크 테스트 체크리스트

```bash
BASE=https://<배포주소>

# 1) 헬스 — {"status":"ok"}
curl -s $BASE/api/health

# 2) OpenAPI 문서 응답
curl -s $BASE/v3/api-docs | head -c 120
#   브라우저: $BASE/swagger-ui/index.html

# 3) 회원가입 → 로그인 → 인증된 내 정보 (frontend-guide.md 참고)
curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"smoke@test.com","password":"Passw0rd!","nickname":"smoke","birthDate":"1990-01-01"}'
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"smoke@test.com","password":"Passw0rd!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
curl -s $BASE/api/users/me -H "Authorization: Bearer $TOKEN"
```

확인 항목:
- [ ] `/api/health` 200 OK
- [ ] Flyway 적용 완료 — 기동 로그 `Successfully applied N migrations`, DB `flyway_schema_history`에 V1~V12 기록
- [ ] signup→login→`/api/users/me` 흐름 정상(JWT 발급·검증)
- [ ] **CORS**: 실제 프론트 origin에서 브라우저 요청이 차단되지 않음(`CORS_ALLOWED_ORIGINS`에 포함됐는지)
- [ ] **HTTPS** 인증서 유효(브라우저 자물쇠), HTTP→HTTPS 리다이렉트
- [ ] **이미지 업로드 후 영속성**: 회차 업로드 → 응답 `url`이 외부에서 GET 200 → **컨테이너 재시작 후에도** 같은 url 접근 가능(local이면 여기서 실패해야 정상 = S3 필요 신호)
- [ ] `SPRING_PROFILES_ACTIVE=prod` 적용 확인(기동 로그의 active profile, SQL 로그가 안 찍히는지)
- [ ] DB 보안그룹/방화벽이 외부 직접 접근을 막는지(앱에서만 접근)

---

## 11. 앱(프론트)은 별개

RN Expo 앱은 백엔드와 **독립적으로** EAS Build → 앱스토어/플레이스토어(또는 Expo Go)로 배포한다. 앱의 API Base URL을 배포된 백엔드 주소로 바꾸고, 그 origin을 백엔드 `CORS_ALLOWED_ORIGINS`에 추가하면 끝. 백엔드만 위 절차로 올리면 된다.

---

## 요약: 가장 빠른 길
1. **`Dockerfile` + `.dockerignore`**(1번 — 리포에 포함됨, 빌드 검증 완료) →
2. **이미지 스토리지 결정**: S3/R2 버킷 생성 + `STORAGE_TYPE=s3` & `S3_*` 주입(권장), 또는 단일 인스턴스 + 영속 볼륨 `STORAGE_ROOT`(6번) →
3. **Railway에 GitHub 연결 + Postgres 애드온 + 환경변수 표**(트랙 A, 4번) →
4. 발급된 **HTTPS 주소로 외부 공개** → 5. **스모크 테스트**(10번).

실무 감각을 키우려면 같은 걸 **AWS App Runner → ECS/RDS/S3**(트랙 B, 5번)로 단계적으로 옮긴다. 수평 확장을 켜기 전엔 **8번(예약 발행 중복)** 과 **6번(이미지 인가)** 를 먼저 처리한다.


---

## 11. 추가 운영 고려사항

### 멀티파트 업로드 한도
`application.yml`: `spring.servlet.multipart.max-file-size=20MB`, `max-request-size=200MB`. 회차 이미지 업로드가 크면 이 값과 **리버스 프록시/PaaS의 바디 크기 제한**(nginx `client_max_body_size`, ALB, 플랫폼 기본값)을 함께 키워야 한다 — 한쪽만 작으면 413(Payload Too Large).

### 로컬에서 S3 모드 시험 (리포 내장 MinIO)
`docker-compose.yml`에 **MinIO + 버킷 자동 생성/공개(minio-init)** 가 포함돼 있어 별도 구축 없이 S3 경로를 바로 시험할 수 있다.
```bash
docker compose up -d        # db + minio + 버킷(apptoon, public read) 자동 구성
STORAGE_TYPE=s3 S3_ENDPOINT=http://localhost:9000 S3_REGION=us-east-1 \
  S3_BUCKET=apptoon S3_ACCESS_KEY=minioadmin S3_SECRET_KEY=minioadmin \
  S3_PUBLIC_BASE_URL=http://localhost:9000/apptoon ./gradlew bootRun
```
MinIO 콘솔: http://localhost:9001 (minioadmin/minioadmin). 운영은 같은 환경변수에 R2/AWS S3 값만 넣으면 된다.
