package com.juhkang.artiv.domain.comment;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 최상위 댓글만 페이지네이션(대댓글은 parentId in 으로 따로 조회해 중첩). 최신순. */
    @Query(value = "select c from Comment c join fetch c.user "
            + "where c.episode.id = :episodeId and c.parentId is null order by c.id desc",
            countQuery = "select count(c) from Comment c where c.episode.id = :episodeId and c.parentId is null")
    Page<Comment> findByEpisodeIdAndParentIdIsNull(@Param("episodeId") Long episodeId, Pageable pageable);

    /** 여러 최상위 댓글의 대댓글을 한 번에 조회. 스레드 안에선 오래된 순으로 읽힌다. */
    @Query("select c from Comment c join fetch c.user where c.parentId in :parentIds order by c.id asc")
    List<Comment> findByParentIdInOrderByIdAsc(@Param("parentIds") Collection<Long> parentIds);

    /** 회차 총 댓글 수(대댓글 포함) — 리모컨 댓글수 배지용. */
    long countByEpisodeId(Long episodeId);
}
