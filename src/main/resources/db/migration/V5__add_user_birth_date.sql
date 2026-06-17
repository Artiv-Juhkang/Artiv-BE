-- 19금 연령 가드를 위한 생년월일. 기존 사용자(레거시)는 NULL → 미성년으로 취급.
alter table users
    add column birth_date date;
