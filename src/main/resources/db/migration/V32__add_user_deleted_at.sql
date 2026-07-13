-- 회원 탈퇴 (M4, 확정 D5=A) — soft delete. null이면 활성 계정. 탈퇴 시 email/nickname을
-- 센티널로 치환해 unique 제약을 비우고 재가입을 허용한다.
alter table users add column deleted_at timestamptz;
