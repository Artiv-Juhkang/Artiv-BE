package com.juhkang.artiv.domain.ontology;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.ontology.dto.SharedAudienceResponse;
import com.juhkang.artiv.domain.ontology.dto.SharedAudienceResponse.Link;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * sharesAudienceWith — 타인 작가의 데이터를 건드리는 유일한 지점이라 3중 방어가 전부 필요하고,
 * **셋 중 하나라도 코드 순서상 뒤로 밀리면 조용히 샌다.**
 *
 * ① 방향성 — 분모는 항상 내 작품(requireOwnedWork로 소유 확인 후 myReaderCount를 분모로)
 * ② k-익명성 — 겹침 5명 미만 제거. 추가로 **분모에도 게이트**: 내 독자가 2k(10) 미만이면
 *    링크를 아예 계산하지 않는다. 내 독자 5명·겹침 5(share 100%)면 작가가 대체로 아는
 *    자기 독자 5명의 타 작품 열람이 사실상 특정되기 때문이다.
 * ③ 가시성 전파 — 비공개·연령 미달 작품 제외. **절단보다 먼저 건다**: 절단을 먼저 하면
 *    숨겨진 작품이 슬롯만 먹어 결손 개수로 "보이지 않는 작품이 있다"가 새고(존재 은닉 위반),
 *    가시적 6~8위가 부당하게 빠진다.
 */
@Service
@RequiredArgsConstructor
public class SharedAudienceService {

    /** 응답에 담는 최대 링크 수. */
    public static final int MAX_LINKS = 5;

    private final ReadingEventRepository readingEventRepository;
    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;
    private final OntologyAccessChecker accessChecker;

    @Transactional(readOnly = true)
    public SharedAudienceResponse of(Long seriesId, Long userId) {
        Series work = accessChecker.requireOwnedWork(seriesId, userId);   // ① 방향성

        long myReaders = readingEventRepository.myReaderCount(seriesId);
        if (myReaders < 2L * OntologyAccessChecker.MIN_SEGMENT_SIZE) {    // ② 분모 게이트
            return SharedAudienceResponse.suppressed();
        }

        Map<Long, Long> overlaps = readingEventRepository.overlapRows(seriesId).stream()
                .filter(r -> accessChecker.isDisclosable(((Number) r[1]).longValue()))  // ② 겹침 게이트
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).longValue(),
                        r -> ((Number) r[1]).longValue(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        if (overlaps.isEmpty()) {
            return new SharedAudienceResponse(myReaders, List.of());
        }

        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // 후보를 한 번에 로드한다(N+1 회피). tags는 건드리지 않는다 — LAZY @ElementCollection이라
        // OSIV=false 환경에서 트랜잭션 밖으로 새면 터진다.
        Map<Long, Series> candidates = seriesRepository.findAllById(overlaps.keySet()).stream()
                .collect(Collectors.toMap(Series::getId, Function.identity()));

        return new SharedAudienceResponse(myReaders, overlaps.entrySet().stream()
                .map(e -> candidates.get(e.getKey()))
                .filter(java.util.Objects::nonNull)
                .filter(s -> accessChecker.isLinkable(s, viewer))          // ③ 가시성 — 절단 앞
                .limit(MAX_LINKS)                                          // 절단은 마지막
                .map(s -> new Link(s.getId(), s.getTitle(), s.getContentType().name(),
                        s.getContentType().getLabel(),
                        round3((double) overlaps.get(s.getId()) / myReaders)))
                .toList());
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
