package com.juhkang.artiv.domain.ontology;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingEventRepository extends JpaRepository<ReadingEvent, Long> {

    /** 회차별 고유 독자·완독 수·세션 수. 잔존 곡선의 원재료. */
    @Query("""
            select e.episodeNo,
                   count(distinct e.userId),
                   sum(case when e.completed then 1 else 0 end),
                   count(e)
            from ReadingEvent e
            where e.seriesId = :seriesId and e.occurredAt >= :from
            group by e.episodeNo
            order by e.episodeNo
            """)
    List<Object[]> retentionRows(@Param("seriesId") Long seriesId, @Param("from") Instant from);

    /** 유입 경로별 세션 수. */
    @Query("""
            select e.entryPoint, count(e)
            from ReadingEvent e
            where e.seriesId = :seriesId and e.occurredAt >= :from
            group by e.entryPoint
            order by count(e) desc
            """)
    List<Object[]> entryPointRows(@Param("seriesId") Long seriesId, @Param("from") Instant from);

    /** 요약: 세션 수 · 고유 독자 · 완독 수. */
    @Query("""
            select count(e),
                   count(distinct e.userId),
                   sum(case when e.completed then 1 else 0 end)
            from ReadingEvent e
            where e.seriesId = :seriesId and e.occurredAt >= :from
            """)
    List<Object[]> summaryRows(@Param("seriesId") Long seriesId, @Param("from") Instant from);

    /** 독자별 (마지막 열람, 세션 수) — 세그먼트 분류의 원재료. 개인 id는 반환하지 않는다. */
    @Query("""
            select max(e.occurredAt), count(e)
            from ReadingEvent e
            where e.seriesId = :seriesId and e.userId is not null
            group by e.userId
            """)
    List<Object[]> readerActivityRows(@Param("seriesId") Long seriesId);
}
