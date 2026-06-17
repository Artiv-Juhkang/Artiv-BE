-- 관리자가 작품 공개/비공개를 제어한다. 기존 작품은 공개(true)로 둔다.
alter table series
    add column visible boolean not null default true;
