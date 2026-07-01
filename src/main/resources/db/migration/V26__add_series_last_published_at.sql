-- '최근 업데이트' 정렬을 위한 비정규화 컬럼.
-- 각 작품의 최신 PUBLISHED 회차 발행시각을 들고 있어, 목록을 회차 발행 최신순(UPDATED)으로
-- 정렬할 때 episodes 집계 없이 series 스칼라 정렬만으로 처리한다(읽기 부하↓). 회차 발행 시
-- EpisodeService가 전진 갱신한다. 회차가 없는 작품은 NULL → 정렬 시 NULLS LAST.
alter table series add column last_published_at timestamptz;

update series s set last_published_at = (
    select max(e.publish_at) from episodes e
    where e.series_id = s.id and e.status = 'PUBLISHED'
);
