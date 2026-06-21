package com.juhkang.apptoon.domain.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 공개(블라인드 아님) 목록. category null이면 전체. 정렬은 Pageable로. */
    @Query(value = "select p from Post p where p.blinded = false and (:category is null or p.category = :category)",
            countQuery = "select count(p) from Post p where p.blinded = false and (:category is null or p.category = :category)")
    Page<Post> findVisible(@Param("category") PostCategory category, Pageable pageable);

    /** 관리자: 블라인드 포함 전체 + 카테고리·제목키워드·블라인드상태 필터. */
    @Query(value = "select p from Post p where (:category is null or p.category = :category) "
            + "and (:keyword is null or lower(p.title) like lower(concat('%', cast(:keyword as string), '%'))) "
            + "and (:blinded is null or p.blinded = :blinded) order by p.id desc",
            countQuery = "select count(p) from Post p where (:category is null or p.category = :category) "
                    + "and (:keyword is null or lower(p.title) like lower(concat('%', cast(:keyword as string), '%'))) "
                    + "and (:blinded is null or p.blinded = :blinded)")
    Page<Post> findForAdmin(@Param("category") PostCategory category, @Param("keyword") String keyword,
                            @Param("blinded") Boolean blinded, Pageable pageable);
}
