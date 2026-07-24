#!/usr/bin/env python3
"""
seed-demo.py — Artiv 데모 데이터 시드 (개발 전용, 멱등 지향).

improvement.md 2번: "회차 정보를 포함한 임시 데이터를 매체별로 많이 넣어 실제 UI를 보고 싶다".
부수효과로 5번 요일 버그(웹툰이 7요일 전부로 박혀 어느 요일이든 동일)도 영구 해소한다 —
여기서 각 웹툰을 서로 다른 단일 요일(월~일)에 고정해 시드하기 때문.

무엇을 만드나:
  - 창작자 3명(라온=기존 creator@artiv.test, 미루, 단우) + 데모 독자 1명(구름).
  - 웹툰 7편: 각각 다른 요일(MON..SUN)에 고정, 회차 4~7개.
  - 소설 3편: 회차를 텍스트 본문(mediaKind=TEXT)으로 업로드.
  - 음악 2편: 트랙(회차)마다 짧은 톤 WAV(mediaKind=AUDIO).
  - 일러스트/사진/디자인/손그림 각 4~5편: 단일물(회차 1개 + 이미지 여러 장).
  - 성인(AGE_19, adultOnly) 웹툰 1편으로 연령 게이트 시연.
  - 독자가 웹툰 3편 구독 후 새 회차를 올려 EPISODE_PUBLISHED 알림이 실제로 fan-out 되게.
  - 커버(cover_url)·회차 publish_at을 psql로 백필(스태거)해 '최근 업데이트' 정렬이 의미를 갖게.

전제: 백엔드가 :8080 dev 프로파일로 가동 중 + docker 컨테이너 artiv-db 가동.
스토리지는 local(/files/{key} 정적 서빙)이라 업로드 이미지가 그대로 노출된다.

사용:
  python3 scripts/seed-demo.py            # 콘텐츠에 추가(중복 가능 — 한 번만 권장)
  python3 scripts/seed-demo.py --reset    # 콘텐츠 테이블 truncate 후 결정적 재시드(권장)
  ARTIV_API=http://host:8080 python3 scripts/seed-demo.py --reset
"""
import argparse
import json
import math
import os
import subprocess
import sys
import tempfile
import wave
import zlib
import struct

API = os.environ.get("ARTIV_API", "http://localhost:8080")
DB = ["docker", "exec", "-i", "artiv-db", "psql", "-U", "artiv", "-d", "artiv"]
PW = "seedpass123"          # 데모 창작자/독자 공통 비밀번호
ADULT_BIRTH = "1995-03-15"  # 만 19세 이상(성인 콘텐츠 열람 가능)

# 콘텐츠 테이블만 비운다(users·consents·refresh_tokens·community는 보존). CASCADE로 FK 순서 자동 처리.
RESET_SQL = (
    "TRUNCATE series, episodes, episode_images, series_publish_days, series_tags, "
    "subscriptions, bookmarks, read_logs, episode_likes, notifications "
    "RESTART IDENTITY CASCADE;"
)

# ──────────────────────────────────────────────────────────────────────────
#  PNG 생성 (표준 라이브러리만) — 매체 느낌을 다르게 한 그라디언트 풀
# ──────────────────────────────────────────────────────────────────────────

def _lerp(a, b, t):
    return int(a + (b - a) * t)

def _write_png(path, w, h, c1, c2, diagonal=False):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            t = ((x / w + y / h) / 2) if diagonal else (y / max(1, h - 1))
            raw += bytes((_lerp(c1[0], c2[0], t) & 255,
                          _lerp(c1[1], c2[1], t) & 255,
                          _lerp(c1[2], c2[2], t) & 255))

    def chunk(typ, data):
        body = typ + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xffffffff)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
                + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b""))

# (이름, c1, c2, 대각선여부) — 풀에서 인덱스로 골라 회차/페이지에 재사용
_PALETTE = [
    ((26, 32, 74), (242, 140, 90), True),    # 동트는 도시
    ((40, 22, 64), (242, 120, 159), True),   # 보랏빛
    ((18, 60, 84), (110, 190, 210), False),  # 물빛
    ((12, 18, 28), (90, 120, 200), False),   # 심야 블루
    ((60, 20, 30), (220, 80, 70), True),     # 붉은
    ((20, 50, 40), (120, 200, 150), False),  # 숲
    ((48, 40, 20), (230, 200, 110), True),   # 황금
    ((28, 28, 34), (160, 160, 180), False),  # 모노 그레이
    ((30, 16, 48), (180, 120, 230), True),   # 네온 퍼플
    ((16, 40, 60), (80, 170, 230), False),   # 청록
    ((52, 28, 18), (235, 150, 90), True),    # 테라코타
    ((24, 36, 24), (150, 190, 90), False),   # 라임
]

def build_image_pool(d, w=720, h=1024):
    paths = []
    for i, (c1, c2, diag) in enumerate(_PALETTE):
        p = os.path.join(d, f"pool{i}.png")
        _write_png(p, w, h, c1, c2, diag)
        paths.append(p)
    return paths

# ──────────────────────────────────────────────────────────────────────────
#  소설 본문(TEXT) / 오디오(WAV) 생성 — 표준 라이브러리만
# ──────────────────────────────────────────────────────────────────────────

_PARAS = [
    "가을 바람이 창틈으로 스며들던 밤, 나는 오래 미뤄둔 편지를 꺼냈다.",
    "도시의 불빛은 강물 위에서 잘게 부서졌고, 그 위로 낮은 뱃고동이 지나갔다.",
    "우리는 아무 말도 하지 않았지만, 그 침묵이 오히려 많은 것을 말해 주었다.",
    "새벽이 오기 전 가장 어두운 골목에서, 그는 처음으로 뒤를 돌아보았다.",
    "기억은 물결처럼 밀려왔다가, 손을 뻗으면 어느새 저만치 물러나 있었다.",
    "봄은 언제나 예고 없이 왔고, 우리는 늘 준비되지 않은 채로 그것을 맞았다.",
]

def _novel_text(path, title, n):
    body = [f"{title} — {n}화", ""]
    for i in range(6):
        body.append(_PARAS[(n + i) % len(_PARAS)])
        body.append("")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(body))

def _write_wav(path, seconds, freq, rate=8000):
    """짧은 사인파 톤 WAV(모노 16bit) — 오디오 플레이어 시연용 최소 자산."""
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        frames = bytearray()
        for i in range(int(seconds * rate)):
            frames += struct.pack("<h", int(32767 * 0.2 * math.sin(2 * math.pi * freq * i / rate)))
        w.writeframes(bytes(frames))

# ──────────────────────────────────────────────────────────────────────────
#  HTTP (curl) / DB (psql)
# ──────────────────────────────────────────────────────────────────────────

def _curl(args):
    r = subprocess.run(["curl", "-s", "-w", "\n%{http_code}", *args],
                       capture_output=True, text=True)
    out = r.stdout.rsplit("\n", 1)
    body = out[0] if len(out) == 2 else ""
    code = int(out[1]) if len(out) == 2 and out[1].isdigit() else 0
    return code, body

def post_json(path, payload, token=None):
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)
        bodyfile = f.name
    args = ["-X", "POST", f"{API}{path}", "-H", "Content-Type: application/json",
            "--data-binary", f"@{bodyfile}"]
    if token:
        args += ["-H", f"Authorization: Bearer {token}"]
    try:
        return _curl(args)
    finally:
        os.unlink(bodyfile)

def upload_episode(token, series_id, title, image_paths):
    args = ["-X", "POST", f"{API}/api/series/{series_id}/episodes",
            "-H", f"Authorization: Bearer {token}", "-F", f"title={title}"]
    for p in image_paths:
        args += ["-F", f"images=@{p};type=image/png"]
    code, _ = _curl(args)
    return code

def upload_media_episode(token, series_id, title, path, mime):
    """비이미지 회차(소설 TEXT / 음악 AUDIO) 업로드 — 파트명은 이미지와 동일('images')."""
    code, _ = _curl(["-X", "POST", f"{API}/api/series/{series_id}/episodes",
                     "-H", f"Authorization: Bearer {token}", "-F", f"title={title}",
                     "-F", f"images=@{path};type={mime}"])
    return code

def subscribe(token, series_id):
    code, _ = _curl(["-X", "POST", f"{API}/api/series/{series_id}/subscription",
                     "-H", f"Authorization: Bearer {token}"])
    return code

def psql(sql):
    r = subprocess.run(DB, input=sql, capture_output=True, text=True)
    if r.returncode != 0:
        print("  psql ERROR:", r.stderr.strip(), file=sys.stderr)
    return r.stdout.strip()

# ──────────────────────────────────────────────────────────────────────────
#  계정: 생성(있으면 통과) → CREATOR 승격 → 로그인
# ──────────────────────────────────────────────────────────────────────────

def ensure_user(email, nick, password, make_creator):
    code, _ = post_json("/api/auth/signup", {
        "email": email, "password": password, "nickname": nick,
        "birthDate": ADULT_BIRTH,
        "consents": {"TERMS_OF_SERVICE": True, "PRIVACY_POLICY": True, "ADULT_CONTENT_19": True},
    })
    # 201 생성 또는 400 중복 모두 정상(멱등). 그 외는 경고.
    if code not in (200, 201, 400, 409):
        print(f"  signup({email}) 예상외 코드 {code}")
    if make_creator:
        psql(f"UPDATE users SET role='CREATOR' WHERE email='{email}';")
    code, body = post_json("/api/auth/login", {"email": email, "password": password})
    if code != 200:
        sys.exit(f"login 실패 {email}: {code} {body}")
    return json.loads(body)["accessToken"]

# ──────────────────────────────────────────────────────────────────────────
#  데이터 정의
# ──────────────────────────────────────────────────────────────────────────

DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

# 웹툰: (제목, 작가키, 요일, 장르, 연령, 성인전용, 회차수, 태그들)
WEBTOONS = [
    ("별빛 너머의 항해", "raon", "MONDAY",    "FANTASY",  "ALL",    False, 6, ["우주", "모험"]),
    ("어제의 골목",     "miru", "TUESDAY",   "DRAMA",    "AGE_15", False, 5, ["일상", "성장"]),
    ("검은 고양이 탐정", "danu", "WEDNESDAY", "THRILLER", "AGE_12", False, 7, ["추리", "반전"]),
    ("심야 식당의 비밀", "raon", "THURSDAY",  "DAILY",    "ALL",    False, 5, ["음식", "힐링"]),
    ("부서진 왕관",     "miru", "FRIDAY",    "ACTION",   "AGE_15", False, 6, ["전투", "왕국"]),
    ("웃음 배달부",     "danu", "SATURDAY",  "COMEDY",   "ALL",    False, 4, ["코미디", "일상"]),
    ("붉은 방",         "raon", "SUNDAY",    "HORROR",   "AGE_19", True,  4, ["공포", "스릴러"]),
]

# 소설: (제목, 작가키, 장르, 연령, 회차수, 태그들)
NOVELS = [
    ("열한 번째 여름", "miru", "ROMANCE", "ALL",    3, ["로맨스", "여름"]),
    ("그래도, 봄",     "danu", "DRAMA",   "AGE_12", 2, ["성장", "가족"]),
    ("무한의 탑",      "raon", "FANTASY", "AGE_15", 3, ["판타지", "성장"]),
]

# 단일물: (제목, 작가키, 콘텐츠타입, 장르, 연령, 이미지수, 태그들)
SINGLES = [
    ("새벽 도시",   "raon", "ILLUSTRATION", "ETC",     "ALL",    4, ["도시", "새벽"]),
    ("빛의 결",     "miru", "ILLUSTRATION", "ETC",     "ALL",    3, ["추상"]),
    ("유리정원",    "danu", "ILLUSTRATION", "FANTASY", "ALL",    5, ["판타지", "정원"]),
    ("고요한 바다", "raon", "ILLUSTRATION", "DAILY",   "ALL",    3, ["바다", "풍경"]),
    ("네온 잔상",   "miru", "ILLUSTRATION", "ETC",     "AGE_15", 4, ["네온", "도시"]),

    ("도시의 틈",   "danu", "PHOTO", "DAILY", "ALL", 4, ["거리", "도시"]),
    ("흑백 거리",   "raon", "PHOTO", "ETC",   "ALL", 4, ["흑백", "거리"]),
    ("여름의 온도", "miru", "PHOTO", "DAILY", "ALL", 3, ["여름", "일상"]),
    ("빗속에서",    "danu", "PHOTO", "DRAMA", "ALL", 3, ["비", "감성"]),

    ("미니멀 포스터", "raon", "DESIGN", "ETC", "ALL", 3, ["미니멀", "포스터"]),
    ("타이포 실험",   "miru", "DESIGN", "ETC", "ALL", 4, ["타이포"]),
    ("브랜드 마크",   "danu", "DESIGN", "ETC", "ALL", 3, ["브랜딩", "로고"]),
    ("그리드 시리즈", "raon", "DESIGN", "ETC", "ALL", 3, ["그리드"]),

    ("손끝 낙서",   "miru", "DRAWING", "DAILY", "ALL", 3, ["낙서", "일상"]),
    ("연필 초상",   "danu", "DRAWING", "ETC",   "ALL", 3, ["초상", "연필"]),
    ("일상 스케치", "raon", "DRAWING", "DAILY", "ALL", 4, ["스케치", "일상"]),
    ("펜선 연습",   "miru", "DRAWING", "ETC",   "ALL", 3, ["펜선"]),
]

# 음악(AUDIO, 연재): (제목, 작가키, 장르, 연령, 트랙수, 태그들) — 트랙(회차)마다 짧은 톤 WAV
AUDIOS = [
    ("밤의 라디오", "raon", "DAILY", "ALL",    3, ["로파이", "밤"]),
    ("빗소리 연작", "miru", "ETC",   "ALL",    2, ["앰비언트", "비"]),
]


def pick(pool, *seeds):
    """풀에서 deterministic하게 1장 선택(랜덤 금지)."""
    return pool[sum(seeds) % len(pool)]


def create_series(token, title, content_type, genre, age, status, adult, days, tags):
    payload = {"title": title, "description": f"{title} — 데모 작품.",
               "ageRating": age, "status": status, "contentType": content_type,
               "adultOnly": adult, "genre": genre, "tags": tags}
    if days:
        payload["publishDays"] = days
    code, body = post_json("/api/series", payload, token)
    if code not in (200, 201):
        print(f"  series 생성 실패 '{title}': {code} {body}")
        return None
    return json.loads(body)["id"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--reset", action="store_true", help="콘텐츠 테이블 truncate 후 재시드")
    args = ap.parse_args()

    if args.reset:
        print("• 콘텐츠 테이블 리셋(TRUNCATE … CASCADE) …")
        psql(RESET_SQL)

    tmp = tempfile.mkdtemp(prefix="artiv-seed-")
    pool = build_image_pool(tmp)
    print(f"• 이미지 풀 {len(pool)}장 생성 ({tmp})")

    print("• 계정 준비 …")
    tokens = {
        "raon": ensure_user("creator@artiv.test", "라온", "artiv-pass-1", True),
        "miru": ensure_user("seed-miru@artiv.test", "미루", PW, True),
        "danu": ensure_user("seed-danu@artiv.test", "단우", PW, True),
    }
    reader = ensure_user("seed-reader@artiv.test", "구름", PW, False)
    # 운영자(ADMIN) — 관리자 콘솔(/admin)·모더레이션 검증용. 가입 후 role만 승격.
    ensure_user("admin@artiv.test", "운영자", PW, False)
    psql("UPDATE users SET role='ADMIN' WHERE email='admin@artiv.test';")

    created = []  # (series_id, content_type, title)
    notify_targets = []  # 구독→새회차 알림용 (series_id, author_token)

    print("• 웹툰 7편(요일 분산) …")
    for title, ck, day, genre, age, adult, eps, tags in WEBTOONS:
        sid = create_series(tokens[ck], title, "WEBTOON", genre, age, "ONGOING", adult, [day], tags)
        if not sid:
            continue
        for n in range(1, eps + 1):
            imgs = [pick(pool, sid, n, 0), pick(pool, sid, n, 1)]  # 회차당 2컷
            upload_episode(tokens[ck], sid, f"{n}화", imgs)
        created.append((sid, "WEBTOON", title))
        if day in ("MONDAY", "WEDNESDAY", "FRIDAY"):  # 알림 시연용 구독 대상
            notify_targets.append((sid, tokens[ck], f"{eps + 1}화"))

    print("• 소설 3편(텍스트 본문) …")
    for title, ck, genre, age, eps, tags in NOVELS:
        sid = create_series(tokens[ck], title, "NOVEL", genre, age, "ONGOING", False, None, tags)
        if not sid:
            continue
        for n in range(1, eps + 1):
            txt = os.path.join(tmp, f"novel-{sid}-{n}.txt")
            _novel_text(txt, title, n)
            upload_media_episode(tokens[ck], sid, f"{n}화", txt, "text/plain")
        created.append((sid, "NOVEL", title))

    print("• 음악 2편(오디오 트랙) …")
    for title, ck, genre, age, tracks, tags in AUDIOS:
        sid = create_series(tokens[ck], title, "AUDIO", genre, age, "ONGOING", False, None, tags)
        if not sid:
            continue
        for n in range(1, tracks + 1):
            wav = os.path.join(tmp, f"audio-{sid}-{n}.wav")
            _write_wav(wav, seconds=2.0 + n, freq=330 + 60 * n)
            upload_media_episode(tokens[ck], sid, f"{n}번 트랙", wav, "audio/wav")
        created.append((sid, "AUDIO", title))

    print("• 단일물(일러/사진/디자인/손그림) …")
    for title, ck, ct, genre, age, imgn, tags in SINGLES:
        sid = create_series(tokens[ck], title, ct, genre, age, "COMPLETED", False, None, tags)
        if not sid:
            continue
        imgs = [pick(pool, sid, i) for i in range(imgn)]
        upload_episode(tokens[ck], sid, title, imgs)
        created.append((sid, ct, title))

    print(f"• 구독 + 새 회차 발행으로 알림 fan-out({len(notify_targets)}건) …")
    for sid, author_token, next_title in notify_targets:
        subscribe(reader, sid)                                  # 독자 구독
        upload_episode(author_token, sid, next_title, [pick(pool, sid, 99)])  # 새 회차 → 알림

    print("• 커버 백필(cover_url ← 회차1 첫 이미지, 이미지 타입만) …")
    # 소설(TEXT)·음악(AUDIO)은 회차1 자산이 .png가 아니라 커버 대상에서 제외 → 프론트 placeholder.
    psql("UPDATE series SET cover_url = '/files/' || id || '/1/0.png' "
         "WHERE cover_url IS NULL "
         "AND content_type IN ('WEBTOON','ILLUSTRATION','DESIGN','PHOTO','DRAWING') "
         "AND EXISTS (SELECT 1 FROM episodes e WHERE e.series_id = series.id AND e.episode_no = 1);")

    print("• publish_at 스태거 백필('최근 업데이트' 정렬이 의미를 갖도록) …")
    # 각 시리즈의 '최신 회차'를 앵커로 과거 방향으로만 스태거한다(미래 발행 금지):
    #   - 시리즈 간: id 작을수록 최근(8h 간격). 시드가 웹툰을 먼저 만들므로 낮은 id=웹툰 →
    #     장수 웹툰이 '최근 업데이트' 상위에 오고 상위 3편만 24h 이내라 작품 UP가 붙는다(현실적).
    #   - 시리즈 내: episode_no 클수록 최근(최신 회차=앵커, 이전 회차는 2일씩 과거) →
    #     한 작품에서 최신 1편만 24h 이내라 '회차 UP'가 새 회차에만 붙는다.
    psql("UPDATE episodes e SET publish_at = now() "
         "- (e.series_id - (SELECT min(id) FROM series)) * interval '8 hour' "
         "- (m.maxno - e.episode_no) * interval '2 day' "
         "FROM (SELECT series_id AS sid, max(episode_no) AS maxno FROM episodes "
         "      WHERE status = 'PUBLISHED' GROUP BY series_id) m "
         "WHERE e.series_id = m.sid AND e.status = 'PUBLISHED';")

    print("• last_published_at 재동기화(비정규화 필드 ← 실제 최신 발행 회차) …")
    # UP 배지·'최근 업데이트' 정렬이 쓰는 series.last_published_at을 백필된 회차 시각과 맞춘다.
    # (시드가 회차를 API로 만든 뒤 publish_at만 SQL로 백데이트해 이 필드가 시드 실행 시각에 고정돼
    #  전 작품이 UP로 보이던 결함을 바로잡는다.)
    psql("UPDATE series s SET last_published_at = sub.mx "
         "FROM (SELECT series_id AS sid, max(publish_at) AS mx FROM episodes "
         "      WHERE status = 'PUBLISHED' GROUP BY series_id) sub "
         "WHERE s.id = sub.sid;")

    print("\n완료. 시리즈 %d편 시드(요일 분산 웹툰 + 매체별 단일물 + 소설)." % len(created))
    print("검증: 창작물 '전체' 레일 / 웹툰 요일탭(요일마다 다른 작품) / 단일물 갤러리 / 19금 게이트 / 알림.")


if __name__ == "__main__":
    main()
