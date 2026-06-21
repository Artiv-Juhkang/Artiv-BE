-- 블라인드 이력(누가·언제). 자동 블라인드는 blinded_by NULL(시스템).
alter table posts add column blinded_by bigint;
alter table posts add column blinded_at timestamptz;

alter table post_comments add column blinded_by bigint;
alter table post_comments add column blinded_at timestamptz;
