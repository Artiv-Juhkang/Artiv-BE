package com.juhkang.apptoon.domain.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query(value = "select c from Comment c join fetch c.user where c.episode.id = :episodeId order by c.id desc",
            countQuery = "select count(c) from Comment c where c.episode.id = :episodeId")
    Page<Comment> findByEpisodeId(@Param("episodeId") Long episodeId, Pageable pageable);
}
