-- 프로필 공개 설정 (CH1, 확정 D-확3) — 디폴트 공개. 비공개여도 닉네임·아바타·역할은
-- 항상 공개(댓글·채팅 표기에 필수)이고 bio·가입일만 숨긴다. DM 수신 차단 의미 아님.
alter table users add column profile_public boolean not null default true;
