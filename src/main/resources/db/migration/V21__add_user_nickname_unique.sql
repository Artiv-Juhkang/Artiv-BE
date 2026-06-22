-- 닉네임 고유화(@멘션이 1명을 정확히 가리키도록). 기존 중복은 접미사로 해소 후 unique 제약.
-- 같은 nickname 그룹에서 id 오름차순 2번째 행부터 '_<id>' 접미사. 단, 생성된 닉이 *기존 리터럴 닉*과
-- 또 겹칠 수 있으므로(예: 누군가 이미 'bob_5'), 충돌이 사라질 때까지 '_'를 덧붙여 보장한다.
do $$
declare
    r record;
    newnick text;
begin
    for r in
        select id, nickname
        from (
            select id, nickname, row_number() over (partition by nickname order by id) as rn
            from users
        ) ranked
        where rn > 1
    loop
        newnick := r.nickname || '_' || r.id;
        while exists (select 1 from users where nickname = newnick) loop
            newnick := newnick || '_';
        end loop;
        update users set nickname = newnick where id = r.id;
    end loop;
end $$;

alter table users add constraint uk_users_nickname unique (nickname);
