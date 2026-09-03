#!/usr/bin/env python3
"""
verify-cliff.py — 진단 API가 seed-reading-events.py가 심어둔 이탈 절벽을 실제로 찾아내는지 대조.

이것이 Phase A의 핵심 검증이다. 합성 데이터가 "예쁜 그림"에 그치지 않고
탐지 알고리즘의 정답이 있는 벤치마크로 기능한다는 것을 보인다.

전제: 백엔드 :8080 가동 + seed-reading-events.py 실행 완료(seed-truth.json 존재).

사용:
  python3 scripts/verify-cliff.py
  ARTIV_API=http://localhost:8080 python3 scripts/verify-cliff.py
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

API = os.environ.get("ARTIV_API", "http://localhost:8080")
HERE = os.path.dirname(os.path.abspath(__file__))
TRUTH = os.path.join(HERE, "seed-truth.json")
DB = ["docker", "exec", "-i", "artiv-db", "psql", "-U", "artiv", "-d", "artiv"]
# 시드 계정마다 비밀번호가 다르다(docs/local-accounts.md). 순서대로 시도한다.
PASSWORDS = ["seedpass123", "artiv-pass-1", "password123"]
# 합성 데이터에 난수가 섞여 있어 100%를 요구하지 않는다. 두 값 모두 경험적 기준이며,
# 회귀 감지용 하한이지 통계적 유의성을 주장하지 않는다.
PASS_THRESHOLD = 60.0
MIN_COVERAGE = 80.0


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(API + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            raw = resp.read().decode()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except urllib.error.URLError as e:
        return 0, str(e)


def login(email):
    for pw in PASSWORDS:
        st, body = req("POST", "/api/auth/login", {"email": email, "password": pw})
        if st == 200:
            return body["accessToken"]
    return None


def authors_by_series():
    out = subprocess.run(
        DB + ["-tAF", "\x1f", "-c",
              "select s.id, u.email from series s join users u on u.id = s.author_id;"],
        capture_output=True, text=True).stdout.strip()
    return {int(l.split("\x1f")[0]): l.split("\x1f")[1] for l in out.splitlines() if l}


def main():
    if not os.path.exists(TRUTH):
        print("[중단] seed-truth.json이 없습니다. scripts/seed-reading-events.py를 먼저 실행하세요.")
        sys.exit(1)
    truth = json.load(open(TRUTH, encoding="utf-8"))
    authors = authors_by_series()

    tokens = {}
    hits = miss = undetected = skipped = 0

    print(f"작품 {len(truth['works'])}편 대조 — 창 {truth['windowDays']}일, 시드 {truth['seed']}, "
          f"이벤트 {truth['events']:,}건\n")
    print(f"{'작품':<26}{'정답':>5}{'탐지':>5}   결과")
    print("-" * 52)

    for w in truth["works"]:
        sid, title = w["seriesId"], w["title"][:24]
        email = authors.get(sid)
        if not email:
            skipped += 1
            continue
        if email not in tokens:
            tokens[email] = login(email)
        tok = tokens[email]
        if not tok:
            print(f"{title:<26}{'':>5}{'':>5}   로그인 실패 — 건너뜀")
            skipped += 1
            continue

        st, ins = req("GET", f"/api/ontology/works/{sid}/insights", token=tok)
        if st != 200:
            print(f"{title:<26}{'':>5}{'':>5}   진단 실패({st}) — 건너뜀")
            skipped += 1
            continue

        expected = w["cliffEpisodeNo"]
        found = ins["cliff"]["episodeNo"] if ins.get("cliff") else None
        if found == expected:
            hits += 1
            mark = "✅ 일치"
        elif found is None:
            undetected += 1
            mark = "⚠ 미탐지"
        else:
            miss += 1
            mark = "❌ 불일치"
        print(f"{title:<26}{expected:>5}{(found if found else '-'):>5}   {mark}")

    total = hits + miss + undetected
    planned = len(truth["works"])
    print("-" * 52)
    if total == 0:
        print("대조 가능한 작품이 없습니다.")
        sys.exit(1)
    rate = 100.0 * hits / total
    print(f"탐지 정확도 {hits}/{total} ({rate:.0f}%)  ·  불일치 {miss} · 미탐지 {undetected}")

    # 건너뜀을 분모에서 빼면 대부분 실패해도 합격이 보고된다. 명시적으로 드러내고,
    # 커버리지가 낮으면 정확도와 무관하게 실패시킨다(조용한 축소 금지).
    coverage = 100.0 * total / planned
    print(f"커버리지 {total}/{planned} ({coverage:.0f}%)  ·  건너뜀 {skipped}")
    eps = sorted({w["episodes"] for w in truth["works"]})
    print(f"벤치마크 회차 범위 {min(eps)}~{max(eps)}화 — 15화 이상 장편은 이 시드에 없다.")
    print("  (장편 후반 절벽은 InsightsRegressionTest.기하감소_곡선의_후반_절벽을_찾아낸다 가 덮는다)")

    ok = rate >= PASS_THRESHOLD and coverage >= MIN_COVERAGE
    if not ok:
        why = []
        if rate < PASS_THRESHOLD:
            why.append(f"정확도 {rate:.0f}% < {PASS_THRESHOLD:.0f}%")
        if coverage < MIN_COVERAGE:
            why.append(f"커버리지 {coverage:.0f}% < {MIN_COVERAGE:.0f}%")
        print("불합격 — " + ", ".join(why))
    else:
        print("합격")
    sys.exit(0 if ok else 2)


if __name__ == "__main__":
    main()
