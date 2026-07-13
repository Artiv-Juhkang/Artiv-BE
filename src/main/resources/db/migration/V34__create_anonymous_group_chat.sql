-- 익명 단체방 (CH5, 확정 D4=b) — 2컬럼 가산. anonymous면 메시지 응답의 발신자 표기가
-- '익명N'으로 마스킹되지만(서버 응답 레이어) 서버는 senderId를 그대로 저장·반환한다.
-- anon_alias는 방 생성 시 멤버 전원에게 1부터 순번 배정(생성자=1).
alter table conversations add column anonymous boolean not null default false;
alter table conversation_members add column anon_alias integer;
