-- 작품 장르(단일 enum) + 자유 태그(값 컬렉션). 기존 작품은 장르 ETC로 채움.
alter table series add column genre varchar(20) not null default 'ETC';

-- 태그는 publish_days(ElementCollection)와 동일 패턴. (series_id, tag) 복합 PK로 작품 내 중복 방지.
create table series_tags (
    series_id bigint      not null references series (id),
    tag       varchar(30) not null,
    primary key (series_id, tag)
);

-- 태그 필터(:tag member of s.tags) 가속.
create index idx_series_tags_tag on series_tags (tag);
