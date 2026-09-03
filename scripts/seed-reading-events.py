#!/usr/bin/env python3
"""
seed-reading-events.py — 창작자 온톨로지용 합성 열람 이벤트 생성기 (개발 전용).

왜 필요한가: Artiv는 실사용자가 0인 학습 프로젝트라 진단 화면이 빈 화면이 된다.
계측(reading_events)을 심어도 흘러들 데이터가 없다. 그래서 현실적인 독자 행동을
시뮬레이션해 최근 90일을 채운다.

정직성 장치: 어느 회차에 "이탈 절벽"을 심었는지를 seed-truth.json에 남긴다.
진단 API가 그 절벽을 실제로 찾아내는지 verify-cliff.py로 대조 검증할 수 있다 —
예쁜 데모가 아니라 검증 가능한 시스템이 되는 지점이다.

행동 모델:
  - 작품마다 잠재 품질 q ∈ [0.55, 0.95] (난수, 시드 고정)
  - 독자 풀 = 실제 구독자 + 합성 독자. 회차를 순서대로 읽어나간다.
  - 회차 진행 = 생존 곡선. 매 회차 q 확률로 잔존, 단 절벽 회차에서는 q*0.4.
  - 진도율: 잔존자는 베타(5,1.4), 이탈자는 베타(1.3,3). completed = progress>=95
  - 유입경로: 구독자는 SUBSCRIPTION/NOTIFICATION 편중, 신규는 DISCOVER/SEARCH 편중
  - 열람 시각: 회차 발행 + 지수분포 지연(평균 1.5일). 발행이 90일 창보다 오래됐으면
    창 안쪽으로 당겨 배치한다(오래된 시드 데이터에서도 진단이 비지 않게).

전제: docker 컨테이너 artiv-db 가동 + seed-demo.py로 작품·회차가 있을 것.

사용:
  python3 scripts/seed-reading-events.py --reset     # 비우고 재생성(권장)
  python3 scripts/seed-reading-events.py --seed 7    # 다른 시나리오
"""
import argparse
import json
import os
import random
import subprocess
import sys
from datetime import datetime, timedelta, timezone

DB = ["docker", "exec", "-i", "artiv-db", "psql", "-U", "artiv", "-d", "artiv"]
HERE = os.path.dirname(os.path.abspath(__file__))
TRUTH_PATH = os.path.join(HERE, "seed-truth.json")
SEP = "\x1f"

SYNTHETIC_READERS = 80          # 합성 독자 풀 크기(전 작품 공유)
MIN_EPISODES = 4                # 잔존 곡선이 의미를 가지려면 회차가 이만큼은 필요
WINDOW_DAYS = 90
ENTRY_SUBSCRIBER = ["SUBSCRIPTION"] * 3 + ["NOTIFICATION"] * 2 + ["DIRECT"]
ENTRY_NEW = ["DISCOVER"] * 3 + ["SEARCH"] * 2 + ["AUTHOR"]


def psql(sql):
    r = subprocess.run(DB + ["-v", "ON_ERROR_STOP=1", "-tAF", SEP, "-c", sql],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(f"[psql 실패] {r.stderr.strip()}", file=sys.stderr)
        sys.exit(1)
    return r.stdout


def rows(sql):
    out = psql(sql).strip()
    return [line.split(SEP) for line in out.splitlines()] if out else []


def ensure_readers(n):
    """합성 독자 user 행을 확보하고 id 목록을 돌려준다. 비밀번호 해시는 기존 유저에서 복사."""
    existing = rows("select id from users where email like 'sim%@artiv.test' order by id;")
    if len(existing) < n:
        # 로그인 불가 해시. 기존 유저의 해시를 복사하면 그 비밀번호로 실제 로그인되는 계정이
        # 80개 생긴다 — 개발 전용이라도 만들 이유가 없다. bcrypt가 아닌 문자열이라 matches()가
        # 항상 false다(BCryptPasswordEncoder는 형식 불일치 시 경고 후 false).
        pw = "!synthetic-no-login"
        values = [f"('sim{i}@artiv.test', '{pw}', '시뮬독자{i:03d}', 'READER', "
                  f"DATE '1995-03-15', now(), now())" for i in range(len(existing), n)]
        psql("insert into users (email, password, nickname, role, birth_date, created_at, updated_at) "
             "values " + ", ".join(values) + " on conflict (email) do nothing;")
        existing = rows("select id from users where email like 'sim%@artiv.test' order by id;")
    return [int(r[0]) for r in existing[:n]]


def load_works():
    data = rows(f"""
        select s.id, s.title, e.episode_no, e.id,
               to_char(coalesce(e.publish_at, e.created_at) at time zone 'UTC',
                       'YYYY-MM-DD"T"HH24:MI:SS')
        from series s
        join episodes e on e.series_id = s.id
        where e.status = 'PUBLISHED'
        order by s.id, e.episode_no;
    """)
    works = {}
    for sid, title, no, eid, pub in data:
        w = works.setdefault(int(sid), {"seriesId": int(sid), "title": title, "episodes": []})
        w["episodes"].append({"no": int(no), "id": int(eid), "publishAt": pub})
    return [w for w in works.values() if len(w["episodes"]) >= MIN_EPISODES]


def load_subscriptions():
    out = {}
    for sid, uid in rows("select series_id, user_id from subscriptions;"):
        out.setdefault(int(sid), []).append(int(uid))
    return out


def progress_of(rng, retained):
    p = rng.betavariate(5.0, 1.4) if retained else rng.betavariate(1.3, 3.0)
    return max(0, min(100, int(100 * p)))


def generate(rng, works, subs_by_series, reader_pool, now):
    window_start = now - timedelta(days=WINDOW_DAYS)
    events, truth = [], []

    for w in works:
        eps = sorted(w["episodes"], key=lambda e: e["no"])
        n = len(eps)
        quality = rng.uniform(0.55, 0.95)
        # 절벽 = 낙폭이 '관측되는' 회차. 독자는 그 직전 회차에서 이탈하므로
        # 페널티는 cliff_no로 넘어가는 전환에 건다(진단 API의 정의와 일치).
        lo, hi = 3, n
        cliff_no = eps[rng.randint(lo, hi) - 1]["no"] if hi >= lo else eps[-1]["no"]

        subs = subs_by_series.get(w["seriesId"], [])
        extra = rng.sample(reader_pool, k=rng.randint(20, min(48, len(reader_pool))))
        audience = [(u, True) for u in subs] + [(u, False) for u in extra if u not in subs]

        # 회차 발행 시각을 90일 창 안으로 정규화한다. 시드 데이터의 publish_at이
        # 창보다 오래되면 진단이 전부 비므로, 창 안에서 회차 순서를 유지해 재배치한다.
        span = timedelta(days=WINDOW_DAYS - 5)
        starts = [window_start + span * (i / max(1, n)) for i in range(n)]

        for uid, is_sub in audience:
            for idx, ep in enumerate(eps):
                base = starts[idx]
                at = base + timedelta(days=min(rng.expovariate(1 / 1.5), 14))
                if at > now:
                    break

                next_no = eps[idx + 1]["no"] if idx + 1 < n else None
                retain_p = quality * (0.4 if next_no == cliff_no else 1.0)
                retained = rng.random() < retain_p
                pct = progress_of(rng, retained)
                entry = rng.choice(ENTRY_SUBSCRIBER if is_sub else ENTRY_NEW)

                events.append((at, uid, w["seriesId"], ep["id"], ep["no"], entry,
                               pct, pct >= 95, rng.randint(8_000, 420_000),
                               str(_uuid(rng))))
                if not retained:
                    break

        truth.append({"seriesId": w["seriesId"], "title": w["title"], "episodes": n,
                      "cliffEpisodeNo": cliff_no, "audience": len(audience),
                      "quality": round(quality, 3)})
    return events, truth


def _uuid(rng):
    h = f"{rng.getrandbits(128):032x}"
    return f"{h[:8]}-{h[8:12]}-4{h[13:16]}-a{h[17:20]}-{h[20:32]}"


def insert(events):
    """COPY로 한 번에 적재한다 — 수만 건을 INSERT로 넣으면 docker exec 왕복이 병목."""
    lines = []
    for at, uid, sid, eid, no, entry, pct, done, dwell, sess in events:
        u = str(uid) if uid is not None else "\\N"
        lines.append(f"{at.isoformat()}\t{u}\t{sid}\t{eid}\t{no}\t{entry}\t{pct}\t"
                     f"{'t' if done else 'f'}\t{dwell}\t{sess}")
    payload = ("copy reading_events (occurred_at, user_id, series_id, episode_id, episode_no, "
               "entry_point, progress_pct, completed, dwell_ms, session_id) from stdin;\n"
               + "\n".join(lines) + "\n\\.\n")
    r = subprocess.run(DB + ["-v", "ON_ERROR_STOP=1"], input=payload,
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(f"[COPY 실패] {r.stderr.strip()[:500]}", file=sys.stderr)
        sys.exit(1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--reset", action="store_true", help="reading_events를 비우고 재생성")
    ap.add_argument("--seed", type=int, default=20260903, help="난수 시드(재현성)")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    now = datetime.now(timezone.utc).replace(microsecond=0)

    if args.reset:
        psql("truncate reading_events restart identity;")
        print("reading_events 비움")

    works = load_works()
    if not works:
        print(f"[중단] 발행 회차 {MIN_EPISODES}개 이상인 작품이 없습니다. "
              "scripts/seed-demo.py --reset 먼저 실행하세요.", file=sys.stderr)
        sys.exit(1)
    print(f"대상 작품 {len(works)}편 (회차 {MIN_EPISODES}개 이상)")

    readers = ensure_readers(SYNTHETIC_READERS)
    print(f"합성 독자 {len(readers)}명 확보")

    subs = load_subscriptions()
    events, truth = generate(rng, works, subs, readers, now)
    print(f"이벤트 {len(events):,}건 생성 — COPY 적재 중")
    insert(events)

    payload = {"generatedAt": now.isoformat(), "seed": args.seed,
               "windowDays": WINDOW_DAYS, "events": len(events), "works": truth}
    with open(TRUTH_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"\n정답 파일: {TRUTH_PATH}")
    for w in truth[:12]:
        print(f"  [{w['seriesId']:>3}] {w['title'][:24]:<26} {w['episodes']:>2}화 · 절벽 {w['cliffEpisodeNo']}화 · 독자 {w['audience']}")
    if len(truth) > 12:
        print(f"  … 외 {len(truth) - 12}편")


if __name__ == "__main__":
    main()
