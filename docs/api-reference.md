# AppToon API 레퍼런스 → Swagger로 일원화

이 문서가 담던 **엔드포인트 전수 + 요청/응답 DTO 필드 사전**은 코드와 자주 어긋나 유지보수 부채가 컸다.
그 역할은 **코드에서 자동 생성되는 OpenAPI(Swagger)**로 일원화했다 — 항상 코드와 동기화된다.

## 어디를 볼까

| 필요한 것 | 출처 |
|---|---|
| **엔드포인트 전수 · 요청/응답 스키마 · enum 값 · 시도(Try it out)** | **Swagger UI** `http://localhost:8080/swagger-ui/index.html` |
| **머신용 스펙(타입 코드젠·Postman import)** | `http://localhost:8080/v3/api-docs` · 추출본 `docs/openapi.json`(70경로) |
| **온보딩 · 공통 규약(인증·페이징·에러·이미지·잠금·알림 라우팅) · 도메인 지도** | [`frontend-guide.md`](frontend-guide.md) |
| **기능 동작·정책(연령 게이트·블라인드·기다리면무료 등)** | [`features.md`](features.md) |

## 타입 자동생성
```bash
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts   # 또는 docs/openapi.json
```
스냅샷 갱신: `curl -s localhost:8080/v3/api-docs | python -m json.tool > docs/openapi.json`
