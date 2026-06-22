-- 작품 공개정책(수익화 0단계: 결제 없음). 전체무료/기다리면무료. 기존 작품은 FREE_ALL backfill.
-- visible(V7)/adult_only(V8) add-column 패턴과 동일.
alter table series
    add column release_policy varchar(20) not null default 'FREE_ALL';

-- 기다리면무료 무료전환 주기(일). FREE_ALL이면 NULL(엔티티 Integer nullable과 정합).
alter table series
    add column wait_free_days integer;
