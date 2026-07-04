package com.juhkang.artiv.domain.personalization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    long deleteByUserIdAndEpisodeId(Long userId, Long episodeId);

    // fetch join + 페이징: episode·series는 ManyToOne 체인이라 SQL 레벨 limit 안전. count는 fetch 없이 별도.
    @Query(value = "select b from Bookmark b join fetch b.episode e join fetch e.series "
            + "where b.user.id = :userId order by b.id desc",
            countQuery = "select count(b) from Bookmark b where b.user.id = :userId")
    Page<Bookmark> findByUserIdWithEpisode(@Param("userId") Long userId, Pageable pageable);
}
