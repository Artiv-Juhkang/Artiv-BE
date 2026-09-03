package com.juhkang.artiv.domain.ontology;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.ontology.dto.NudgeRequest;
import com.juhkang.artiv.domain.ontology.dto.OntologySchemaResponse;
import com.juhkang.artiv.domain.ontology.dto.ReadingEventRequest;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 온톨로지 — 계측 수집 + 스키마 + 진단.
 *
 * 계측은 fire-and-forget 의미론이라 202를 반환한다: 프론트는 응답을 기다리지 않고,
 * 이 엔드포인트가 실패해도 뷰어는 정상 동작해야 한다. 다만 서버측 접근 가드는 생략하지 않는다
 * (ReadingEventService 참조).
 */
@RestController
@RequiredArgsConstructor
public class OntologyController {

    private final ReadingEventService readingEventService;
    private final NudgeService nudgeService;
    private final InsightsService insightsService;

    @GetMapping("/api/ontology/schema")
    public OntologySchemaResponse schema() {
        return OntologySchemaResponse.of();
    }

    @GetMapping("/api/ontology/works/{seriesId}/insights")
    @PreAuthorize("hasRole('CREATOR')")
    public WorkInsightsResponse insights(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long seriesId) {
        return insightsService.diagnose(seriesId, userId);
    }

    /**
     * 이탈 독자 알림 실행. 성공은 200 + 빈 바디 — **수신자 수를 반환하지 않는다.**
     *
     * 진단이 "LAPSED 6명"을 보여주는데 액션이 "5명에게 보냈어요"를 반환하면 그 차이가 곧
     * "나를 차단했거나 탈퇴한 사람 수"다. k=5를 도입한 근거(소수 델타 + 댓글 닉네임 대조로
     * 개인 특정)가 델타 1~2에도 그대로 적용되고, 주 단위로 반복하면 시계열 델타까지 얻는다.
     *
     * 서비스가 던지지 않은 거부를 **트랜잭션 밖에서** 예외로 번역한다(NudgeService.execute 주석 참조).
     */
    @PostMapping("/api/ontology/actions/nudge-lapsed-audience")
    @PreAuthorize("hasRole('CREATOR')")
    public void nudgeLapsedAudience(@AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody NudgeRequest req) {
        ActionResult result = nudgeService.execute(req.seriesId(), userId);
        switch (result) {
            case BLOCKED_THROTTLED -> throw new BusinessException(ErrorCode.ACTION_THROTTLED);
            case BLOCKED_TOO_SMALL -> throw new BusinessException(ErrorCode.SEGMENT_TOO_SMALL);
            case EXECUTED -> { }
        }
    }

    @PostMapping("/api/reading-events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void collect(@AuthenticationPrincipal Long userId,
                        @Valid @RequestBody ReadingEventRequest req) {
        readingEventService.record(userId, req.seriesId(), req.episodeNo(), req.entryPoint(),
                req.progressPct().shortValue(), req.completed(), req.dwellMs(), req.sessionId());
    }
}
