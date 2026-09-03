#!/usr/bin/env bash
# run-demo.sh — 창작자 온톨로지 데모 스택 기동.
#
#   1) Postgres(artiv-db)   2) 백엔드 :8080   3) 합성 이벤트 시드
#   4) 절벽 대조 검증        5) 프론트 :8081
#
# 관리자 콘솔은 백엔드가 :8080/admin/ 으로 함께 서빙하므로 별도 기동이 없다.
#
# 옵션:
#   --no-seed   시드/검증을 건너뛴다(이미 데이터가 있을 때)
#   --no-front  프론트를 띄우지 않는다
set -euo pipefail

SEED=1; FRONT=1
for a in "$@"; do
  case "$a" in
    --no-seed)  SEED=0 ;;
    --no-front) FRONT=0 ;;
    *) echo "알 수 없는 옵션: $a"; exit 1 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BE="$ROOT/backend"; FE="$ROOT/frontend"; LOG="$BE/build/demo-logs"
mkdir -p "$LOG"
say() { printf "\n\033[1;36m▸ %s\033[0m\n" "$1"; }

say "1/5  Postgres"
docker info >/dev/null 2>&1 || { echo "Docker 데몬이 꺼져 있습니다. Docker Desktop을 먼저 켜세요."; exit 1; }
docker ps --format '{{.Names}}' | grep -q '^artiv-db$' || {
  docker compose -f "$BE/docker-compose.yml" up -d db; sleep 4; }
docker port artiv-db 2>/dev/null | grep -q '5432' || {
  echo "   포트 미발행 — 재생성"; docker compose -f "$BE/docker-compose.yml" up -d --force-recreate db; sleep 4; }
echo "   artiv-db 준비됨"

say "2/5  백엔드 :8080"
if curl -sf http://localhost:8080/api/health >/dev/null 2>&1; then
  echo "   이미 가동 중 — 기존 프로세스를 씁니다 (코드를 바꿨다면 먼저 종료하세요)"
else
  (cd "$BE" && nohup ./gradlew bootRun > "$LOG/backend.log" 2>&1 &)
  printf "   기동 대기"
  for _ in $(seq 1 80); do
    curl -sf http://localhost:8080/api/health >/dev/null 2>&1 && break
    printf "."; sleep 3
  done; echo
fi
curl -sf http://localhost:8080/api/health >/dev/null || { echo "기동 실패 — $LOG/backend.log 확인"; exit 1; }
echo "   http://localhost:8080  (로그: $LOG/backend.log)"

if [ "$SEED" = 1 ]; then
  say "3/5  합성 이벤트 시드"
  python3 "$BE/scripts/seed-reading-events.py" --reset

  say "4/5  절벽 대조 검증"
  python3 "$BE/scripts/verify-cliff.py" || echo "   (기준 미달 — 위 표를 확인하세요)"
else
  say "3-4/5  시드·검증 건너뜀 (--no-seed)"
fi

if [ "$FRONT" = 1 ]; then
  say "5/5  프론트 :8081"
  if lsof -nP -iTCP:8081 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "   이미 8081 가동 중"
  else
    (cd "$FE" && nohup npx expo start --web > "$LOG/frontend.log" 2>&1 &)
    printf "   기동 대기"
    for _ in $(seq 1 60); do
      curl -sf http://localhost:8081 >/dev/null 2>&1 && break
      printf "."; sleep 3
    done; echo
  fi
else
  say "5/5  프론트 건너뜀 (--no-front)"
fi

cat <<'EOF'

────────────────────────────────────────────────────────────
  앱           http://localhost:8081
  관리자 콘솔   http://localhost:8080/admin/
  API 문서      http://localhost:8080/swagger-ui.html

  계정 (docs/local-accounts.md)
    작가    creator@artiv.test / artiv-pass-1   ← 비밀번호 다름 주의
    작가    seed-miru@artiv.test / seedpass123
    관리자  admin@artiv.test   / seedpass123

  변화를 관찰하는 방법
    1. 관리자 콘솔 → 작가 로그인 → "작품 진단" → 작품 선택
       잔존 막대와 빨간 "이탈 절벽" 배지를 확인
    2. 앱(:8081)에서 같은 작품의 회차를 열어 끝까지 스크롤 후 뒤로가기
    3. 진단 화면 새로고침 → 열람 세션 수가 증가
────────────────────────────────────────────────────────────
EOF
