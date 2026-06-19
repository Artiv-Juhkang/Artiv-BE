# AppToon 백엔드 — 배포 가이드

로컬에서만 돌던 서버를 외부 사용자가 접근할 수 있게 클라우드에 올리는 방법. **빠른 길(PaaS)** 과 **본격(AWS)** 두 트랙으로 정리.

> 핵심 한 줄: **외부 공개 = 24/7 서버 + 관리형 DB + 이미지 스토리지 + HTTPS.** 도메인·AWS는 선택지일 뿐이다.

---

## 0. 배포 전 체크리스트

### 이미 준비된 것 ✅
- **prod 프로파일 분리** (`application-prod.yml` — DB는 환경변수, SQL 로그 off)
- **시크릿 환경변수화** (`JWT_SECRET`, `DB_*` — 코드/깃에 비밀값 없음)
- **CORS 환경변수** (`CORS_ALLOWED_ORIGINS` — 운영 origin만 허용)
- **스키마 자동 적용** (Flyway V1~V12 — 서버 기동 시 자동 마이그레이션)
- **stateless 인증** (JWT — 서버 여러 대로 늘려도 세션 공유 불필요)

### 배포 전 바꿔야 할 것 ⚠️
| 항목 | 현재 | 운영 |
|---|---|---|
| **이미지 저장** | 로컬 `storage/` 폴더 | **오브젝트 스토리지(S3)** — 아래 2번. *컨테이너는 재시작 시 디스크 초기화 → 로컬 저장은 이미지가 사라진다* |
| **DB** | 로컬 Docker Postgres | 관리형 Postgres(RDS/Supabase/Neon/PaaS 애드온) |
| **JWT_SECRET** | dev 기본값 | **운영 전용 강한 키**(`openssl rand -base64 48`) |
| **CORS** | `*`(전체) | 실제 프론트 origin으로 좁힘 |

---

## 1. 빌드 & 컨테이너화

실행 가능한 jar 생성:
```bash
./gradlew bootJar          # build/libs/AppToon-0.0.1-SNAPSHOT.jar
```

`Dockerfile` (프로젝트 루트에 생성 — 멀티스테이지, JDK 25):
```dockerfile
# --- build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# --- run ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
> 대부분의 PaaS는 이 Dockerfile만 있으면 자동 빌드·배포한다.

---

## 2. ✅ 이미지 저장 — S3 호환으로 전환 가능 (구현 완료)

`ObjectStorage` 인터페이스로 저장 백엔드를 추상화해 두어, **코드 변경 없이 `app.storage.type` 환경변수만으로 로컬↔S3 전환**이 된다. 컨테이너의 휘발성 디스크 문제는 `type=s3`로 해결한다.

- `type=local`(기본): 로컬 디스크 + `/files/**` 서빙 — 개발용
- `type=s3`: AWS SDK v2 `S3Client`(endpoint override) — **MinIO·Cloudflare R2·AWS S3 모두 호환**(벤더 종속 없음)

**S3 모드 환경변수:**
```bash
STORAGE_TYPE=s3
S3_ENDPOINT=https://<s3-endpoint>          # MinIO: http://minio:9000 / R2·AWS S3 엔드포인트
S3_REGION=us-east-1
S3_BUCKET=apptoon
S3_ACCESS_KEY=...    S3_SECRET_KEY=...
S3_PUBLIC_BASE_URL=https://<cdn-or-bucket-public-base>   # 이미지 공개 베이스. 예: https://cdn.example.com/apptoon
```
- 버킷은 **미리 생성**하고 이미지 객체는 **public read**(또는 CDN)로 노출. `EpisodeImageResponse.url`이 `S3_PUBLIC_BASE_URL/{key}`로 내려간다.
- 운영 전환은 위 환경변수만 주입하면 끝(코드 동일). AWS S3는 `S3_ENDPOINT` 생략 가능(기본 AWS), R2는 R2 엔드포인트 지정.

**로컬에서 S3 모드 시험(MinIO):** `docker compose up -d`가 MinIO + 버킷(`apptoon`, public read)을 자동 구성한다.
```bash
docker compose up -d                 # db + minio + 버킷 생성(minio-init)
STORAGE_TYPE=s3 S3_ENDPOINT=http://localhost:9000 S3_REGION=us-east-1 \
S3_BUCKET=apptoon S3_ACCESS_KEY=minioadmin S3_SECRET_KEY=minioadmin \
S3_PUBLIC_BASE_URL=http://localhost:9000/apptoon ./gradlew bootRun
# 회차 업로드 → 이미지가 MinIO에 저장되고 url이 http://localhost:9000/apptoon/{key} 로 내려감
```
MinIO 콘솔: http://localhost:9001 (minioadmin/minioadmin).

---

## 3. 운영 환경변수 (공통)

어느 플랫폼이든 아래를 주입한다:
```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<db-host>:5432/apptoon
DB_USER=<user>
DB_PASSWORD=<password>
JWT_SECRET=<openssl rand -base64 48 로 생성한 강한 키>
CORS_ALLOWED_ORIGINS=https://your-frontend.example.com   # 콤마로 여러 개
STORAGE_ROOT=/data/storage                               # 볼륨 회피책 쓸 때
```

---

## 4. 트랙 A — PaaS로 빠르게 (학습·MVP 추천, ~30분)

**Railway / Render / Fly.io** 중 하나. 공통 흐름:

1. GitHub 저장소 연결 (또는 Docker 이미지 푸시)
2. **Postgres 애드온 추가** → 플랫폼이 `DATABASE_URL` 제공 → `DB_URL/USER/PASSWORD`로 매핑
3. **환경변수 입력** (위 3번 목록)
4. 배포 → 플랫폼이 빌드(Dockerfile or Gradle 감지) → **HTTPS 주소 자동 발급** (`https://apptoon-xxx.up.railway.app`)
5. Flyway가 기동 시 스키마 자동 생성 → 끝

> 도메인 없이도 플랫폼 제공 HTTPS 주소로 외부 공개 완료. 이미지는 2번(볼륨 또는 S3) 적용.

**장점**: HTTPS·DB·배포 자동, 무료~저가. **한계**: 세밀한 인프라 제어는 적음.

---

## 5. 트랙 B — AWS 본격 구성 (실무 학습용)

전형적 구성과 역할:

| 구성요소 | 역할 |
|---|---|
| **EC2** (또는 ECS/App Runner) | 서버 컨테이너 실행 |
| **RDS for PostgreSQL** | 관리형 DB (`DB_URL`로 연결) |
| **S3** | 업로드 이미지 저장 (2번 코드 변경 필요) |
| **CloudFront** | S3 이미지 CDN 배포(선택, 빠름) |
| **Route 53** | **도메인** 구입·DNS (`apptoon.com` → 서버) |
| **ACM + ALB** | **HTTPS 인증서** + 로드밸런서 (TLS 종료, 다중 인스턴스) |
| **Secrets Manager / SSM** | `JWT_SECRET` 등 시크릿 관리 |

**대략 순서:**
1. RDS Postgres 생성 → 보안그룹에서 서버만 접근 허용
2. S3 버킷 생성 + `ImageStorageService` S3 전환 배포
3. 서버 배포: EC2에 Docker로 jar 실행 / 또는 **App Runner·ECS Fargate**(관리형이 더 쉬움)
4. ALB + ACM 인증서로 HTTPS, Route 53에서 도메인 연결
5. 환경변수(SSM/Secrets) 주입 + `SPRING_PROFILES_ACTIVE=prod`

> AWS는 유연하지만 구성요소가 많아 비용·운영 부담이 크다. **처음이면 App Runner나 트랙 A로 먼저** 감을 잡고, 실무 학습 목적일 때 EC2/RDS/S3 풀스택으로 확장하길 권한다.

---

## 6. 도메인 & HTTPS — 꼭 필요한가?

- **외부 접근만**이면 도메인 불필요: 퍼블릭 IP나 PaaS 서브도메인으로 충분.
- **HTTPS는 사실상 필수**: 모바일 앱·브라우저가 평문 HTTP를 점점 막고, 토큰 전송 보안상 필요. PaaS는 자동, AWS는 ACM+ALB로.
- **도메인은 "예쁜 주소 + 인증서 발급 편의 + 브랜딩"** 용도 — 준비되면 붙이는 선택사항.

---

## 7. 배포 후 점검 (스모크 테스트)
```bash
BASE=https://<배포주소>
curl $BASE/api/health                       # {"status":"ok"}
curl $BASE/v3/api-docs | head -c 100        # OpenAPI 문서
# signup → login → /api/users/me 흐름 (frontend-guide.md 2번 참고)
```
- DB 마이그레이션 적용 확인(`flyway_schema_history`), HTTPS 인증서 유효, CORS가 프론트 origin 허용하는지 확인.

---

## 8. 앱(프론트)은 별개
RN Expo 앱은 백엔드와 **독립적으로** EAS Build → 앱스토어/플레이스토어(또는 Expo Go)로 배포한다. 앱의 API Base URL을 배포된 백엔드 주소로 바꾸면 끝. 백엔드만 위 절차로 올리면 된다.

---

## 요약: 가장 빠른 길
1. `Dockerfile` 추가(1번) → 2. 이미지 S3 전환 또는 볼륨(2번) → 3. **Railway에 GitHub 연결 + Postgres 애드온 + 환경변수**(트랙 A) → 4. 발급된 HTTPS 주소로 외부 공개.
실무 감각을 키우려면 같은 걸 **AWS App Runner → EC2/RDS/S3**(트랙 B)로 단계적으로 옮겨본다.
