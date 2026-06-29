-- 작품 매체 구분(다매체 전환 슬라이스 2). 기존 작품은 WEBTOON backfill.
-- visible(V7)/adult_only(V8)/genre(V14)/release_policy(V22) add-column 패턴과 동일.
alter table series
    add column content_type varchar(20) not null default 'WEBTOON';
