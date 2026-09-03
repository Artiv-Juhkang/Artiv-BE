package com.juhkang.artiv.domain.ontology;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyActionLogRepository extends JpaRepository<OntologyActionLog, Long> {

    /**
     * 주 1회 스로틀 판정.
     *
     * result = EXECUTED 조건이 반드시 들어가야 한다. 빠뜨리면 한 번 거부된 작품이 영원히
     * 발송 불가가 되는데, "두 번째 호출이 거부된다"만 단언하는 테스트는 그 버그를 통과시킨다.
     */
    boolean existsByObjectIdAndActionTypeAndResultAndOccurredAtAfter(
            Long objectId, ActionType actionType, ActionResult result, Instant after);

    /** 진단 화면의 lastAction 마커 — 성공한 최신 1건. */
    Optional<OntologyActionLog> findTopByObjectIdAndResultOrderByOccurredAtDesc(
            Long objectId, ActionResult result);
}
