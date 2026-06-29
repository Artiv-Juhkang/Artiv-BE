package com.juhkang.artiv.domain.personalization;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    long deleteByUserIdAndEpisodeId(Long userId, Long episodeId);

    @Query("select b from Bookmark b join fetch b.episode e join fetch e.series "
            + "where b.user.id = :userId order by b.id desc")
    List<Bookmark> findByUserIdWithEpisode(@Param("userId") Long userId);
}
