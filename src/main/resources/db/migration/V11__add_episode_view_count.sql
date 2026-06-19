-- 회차 조회수. 상세 조회마다 증가한다. 기존 회차는 0 으로 시작.
alter table episodes
    add column view_count bigint not null default 0;
