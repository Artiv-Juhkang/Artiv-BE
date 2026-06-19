-- 성인 전용(adult_only) 분류. '성인 전용 웹툰'과 '일반 작품에 붙은 19등급'을 구분한다.
-- 불변식: adult_only=true 인 작품은 age_rating=AGE_19 여야 한다(검증은 Service/Entity).
-- 기존 작품(기존 AGE_19 포함)은 false(일반 작품의 19)로 두고, 운영자가 필요 시 재분류한다.
alter table series
    add column adult_only boolean not null default false;
