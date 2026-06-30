-- 작품 커버 아트 URL(표출 seam — 프론트의 coverUrl prop이 기다리던 필드).
-- 없으면 프론트가 placeholder(tint)로 degrade. 작가 업로드/설정은 후속(인증 슬라이스).
alter table series
    add column cover_url varchar(500);
