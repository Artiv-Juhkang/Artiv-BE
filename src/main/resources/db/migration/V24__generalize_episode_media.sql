-- 회차 자산 다매체 일반화(슬라이스 1). 기존 이미지 행은 IMAGE 백필, 치수 유지.
-- 오디오/텍스트는 픽셀 치수가 없으므로 width/height NOT NULL 완화. mime_type·duration_ms 가산.
-- series.content_type(V23)/genre(V14)/release_policy(V22) add-column 패턴과 동일.
alter table episode_images
    add column media_kind varchar(20) not null default 'IMAGE';
alter table episode_images
    add column mime_type varchar(100);
alter table episode_images
    add column duration_ms bigint;
alter table episode_images
    alter column width drop not null;
alter table episode_images
    alter column height drop not null;
