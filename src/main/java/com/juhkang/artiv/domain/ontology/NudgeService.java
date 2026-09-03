package com.juhkang.artiv.domain.ontology;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.block.BlockRepository;
import com.juhkang.artiv.domain.notification.NotificationService;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.personalization.SubscriptionRepository;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 이탈 독자 알림(NUDGE_LAPSED_AUDIENCE) — 온톨로지에서 유일하게 실제 사람에게 무언가를 보내는 액션.
 *
 * **수신 동의는 구독이다.** 신규 notification_preferences 테이블을 만들지 않는다:
 * 구독은 사용자가 직접 켰고, 앱에서 언제든 끄고, 작가가 조작할 수 없고, 이미 EPISODE_PUBLISHED의
 * 수신자 집합이다 — 설계 §4-5의 "작가 권한이 독자 설정을 이길 수 없다"를 이미 전부 만족한다.
 * 한계(묶음 동의라 '새 회차는 받되 리마인더는 싫다'를 표현 못 함, 미구독 이탈 독자에게는 못 보냄)는
 * docs/fde/04-permissions.md에 적는다.
 *
 * 필터 순서가 계약이다. 하나라도 k 게이트 뒤로 밀리면 "LAPSED 20명 중 구독자 3명에게 발송"이
 * 조용히 열린다 — k는 반드시 이 메서드의 **최종 결과**에 걸어야 한다.
 */
@Service
@RequiredArgsConstructor
public class NudgeService {

    /** 스로틀 주기(일). "작품당 주 1회". */
    public static final int THROTTLE_DAYS = 7;
    /** 알림 제목 — 작품명을 넣지 않는다(series.title이 255라 조합하면 notifications.title 255를 넘긴다). */
    private static final String TITLE = "이어보기 기다리는 작품";

    private final ReadingEventRepository readingEventRepository;
    private final OntologyActionLogRepository logRepository;
    private final OntologyAccessChecker accessChecker;
    private final NotificationService notificationService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;

    /**
     * 액션 실행. **가드 실패 시 예외를 던지지 않고 결과를 반환한다.**
     *
     * BusinessException은 RuntimeException이라 "로그 저장 → throw"를 같은 트랜잭션 안에서 하면
     * 방금 저장한 BLOCKED 로그가 함께 롤백된다. 더 나쁜 것은 이 결함이 테스트에서 안 잡힌다는 점이다 —
     * @SpringBootTest + @Transactional에서는 서비스 트랜잭션이 테스트 트랜잭션에 참여해
     * rollback-only 마킹만 되고 count() 쿼리가 flush를 유발해 행이 보인다(테스트 그린 + 프로덕션 무기록).
     * 표준 해법인 REQUIRES_NEW는 이 프로젝트에서 이미 막혀 있다(ChatService:88~92에 시도와 실패 기록).
     * 결과 객체 반환 + 컨트롤러 번역이 신규 인프라 0으로 같은 보장을 준다.
     *
     * 이 계약을 완화하려면 docs/fde/04-permissions.md를 함께 고쳐야 한다.
     */
    @Transactional
    public ActionResult execute(Long seriesId, Long actorId) {
        // 404 존재 은닉. 이 경로는 로그를 남기지 않으므로 예외를 던져도 안전하다.
        Series work = accessChecker.requireOwnedWork(seriesId, actorId);
        Instant now = Instant.now();

        // 주 1회 — result=EXECUTED 조건이 반드시 들어간다. 빠뜨리면 한 번 거부된 작품이
        // 영원히 발송 불가가 되는데, "두 번째가 거부된다"만 단언하는 테스트는 그 버그를 통과시킨다.
        boolean sentThisWeek = logRepository.existsByObjectIdAndActionTypeAndResultAndOccurredAtAfter(
                seriesId, ActionType.NUDGE_LAPSED_AUDIENCE, ActionResult.EXECUTED,
                now.minus(THROTTLE_DAYS, ChronoUnit.DAYS));
        if (sentThisWeek) {
            logRepository.save(OntologyActionLog.blocked(
                    actorId, ActionType.NUDGE_LAPSED_AUDIENCE, seriesId, ActionResult.BLOCKED_THROTTLED));
            return ActionResult.BLOCKED_THROTTLED;
        }

        List<Long> recipients = resolveRecipients(work, now);
        // k는 반드시 최종 수신자 목록에 건다 — 세그먼트 표시 수가 아니다.
        if (!accessChecker.isDisclosable(recipients.size())) {
            logRepository.save(OntologyActionLog.blocked(
                    actorId, ActionType.NUDGE_LAPSED_AUDIENCE, seriesId, ActionResult.BLOCKED_TOO_SMALL));
            return ActionResult.BLOCKED_TOO_SMALL;
        }

        // dedupKey는 수신자 단위 멱등(같은 주에 같은 작품 알림이 두 번 쌓이지 않게).
        // 주 1회 거부는 위 로그 선조회가 담당한다 — fanOut의 dedup 충돌은 예외가 아니라
        // continue라서, dedup만으로 구현하면 두 번째 실행이 200 + 0건 발송이 된다.
        String week = "" + now.atZone(java.time.ZoneOffset.UTC).get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
                + "W" + now.atZone(java.time.ZoneOffset.UTC).get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        notificationService.fanOut(recipients, NotificationType.NUDGE, NotificationTargetType.SERIES,
                seriesId, actorId, TITLE,
                "「" + work.getTitle() + "」의 다음 이야기가 기다리고 있어요.",
                id -> "NUDGE:" + seriesId + ":" + week);

        logRepository.save(OntologyActionLog.of(actorId, ActionType.NUDGE_LAPSED_AUDIENCE,
                seriesId, ActionResult.EXECUTED, recipients.size()));
        return ActionResult.EXECUTED;
    }

    /**
     * 발송 대상 산출. 이 메서드는 아무것도 보내지 않는다.
     *
     * ① 이탈 독자 → ② 작가 본인 제외 → ③ 구독자와 교집합(수신 동의) → ④ 탈퇴자 제외
     * → ⑤ 작가를 차단한 독자 제외.
     */
    @Transactional(readOnly = true)
    public List<Long> resolveRecipients(Series work, Instant now) {
        Long authorId = work.getAuthor().getId();

        List<Long> lapsed = readingEventRepository.lapsedReaderIds(
                work.getId(), AudienceSegment.lapsedCutoff(now));
        if (lapsed.isEmpty()) {
            return List.of();
        }

        // 수신 동의 = 구독. 이 교집합이 이 설계의 척추다.
        Set<Long> subscribers = new HashSet<>(
                subscriptionRepository.findSubscriberIdsBySeriesId(work.getId()));

        List<Long> candidates = lapsed.stream()
                .filter(id -> !id.equals(authorId))
                .filter(subscribers::contains)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 탈퇴자 제외. 탈퇴는 soft delete라 users 행이 남고 FK가 막아주지 않는다 —
        // deletedAt을 직접 봐야 한다(NotificationService.fanOut은 어떤 필터도 하지 않는다).
        return userRepository.findAllById(candidates).stream()
                .filter(u -> u.getDeletedAt() == null)
                .map(User::getId)
                .filter(readerId -> !blockRepository.existsByBlockerIdAndBlockedId(readerId, authorId))
                .toList();
    }
}
