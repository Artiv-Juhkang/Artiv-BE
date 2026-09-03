package com.juhkang.artiv.domain.ontology;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingEventRepository extends JpaRepository<ReadingEvent, Long> {

    /**
     * 회차별 고유 독자·완독 수·세션 수. 잔존 곡선의 원재료.
     *
     * 날짜 창으로 자르지 않는다 — 잔존은 "1화를 본 사람 중 몇 %가 N화까지 왔나"라는
     * 작품 생애 전체의 퍼널이지 기간 지표가 아니다. 주간 연재 7화는 49일에 걸치므로
     * 30일 창으로 자르면 곡선의 앞부분이 통째로 사라진다(2026-09-03 실측으로 발견).
     * 요약·유입경로·세그먼트만 창을 적용한다.
     */
    @Query("""
            select e.episodeNo,
                   count(distinct e.userId),
                   sum(case when e.completed then 1 else 0 end),
                   count(e)
            from ReadingEvent e
            where e.seriesId = :seriesId
            group by e.episodeNo
            order by e.episodeNo
            """)
    List<Object[]> retentionRows(@Param("seriesId") Long seriesId);

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

    /**
     * 독자별 (첫 열람, 마지막 열람) — 세그먼트 분류의 원재료. 개인 id는 반환하지 않는다.
     *
     * 30일 창을 걸지 않는다: 창을 걸면 LAPSED(30일 초과 무열람) 독자가 조회 대상에서 빠져
     * 구조적으로 0이 된다. 세그먼트는 생애 기준 상태 분류다(AudienceSegment 주석 참조).
     * NEW 판정에 첫 열람이 필요해 min도 함께 가져온다 — max만 있으면 '오래된 독자가 어제 다시 읽음'을
     * 신규로 오분류한다.
     */
    @Query("""
            select min(e.occurredAt), max(e.occurredAt)
            from ReadingEvent e
            where e.seriesId = :seriesId and e.userId is not null
            group by e.userId
            """)
    List<Object[]> readerActivityRows(@Param("seriesId") Long seriesId);

    /**
     * 탈퇴 시 익명화 — 행은 남기고 사용자 연결만 끊는다(설계문서 §9).
     *
     * FK의 on delete set null에 기댈 수 없다: 이 프로젝트의 탈퇴는 soft delete(User.withdraw()가
     * deletedAt만 찍고 행을 남김)라 FK가 영원히 발화하지 않는다. 그래서 명시적으로 지운다.
     *
     * flushAutomatically가 없으면 같은 트랜잭션에서 더티체킹으로 잡아둔 변경(User.withdraw()의
     * deletedAt·센티널 치환)이 벌크 UPDATE 뒤의 컨텍스트 clear에 함께 버려져 탈퇴가 통째로 무효화된다
     * (2026-09-03 WithdrawalFlowTest가 잡아냄).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReadingEvent e set e.userId = null where e.userId = :userId")
    int anonymizeUser(@Param("userId") Long userId);
}
