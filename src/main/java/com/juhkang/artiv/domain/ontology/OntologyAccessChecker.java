package com.juhkang.artiv.domain.ontology;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 온톨로지 접근 통제.
 *
 * 기존 5층(역할 게이트 · 소유권 · 어드민 우회 · 연령 · 존재 은닉)에 얹으며,
 * 새로 도입하는 메커니즘은 k-익명성 하나뿐이다.
 *
 * 타인 작품에 대해 403이 아니라 404를 던진다 — 지표가 존재한다는 사실 자체를 숨긴다.
 * 기존 SeriesAccessChecker·SeriesService의 관례와 동일하다.
 */
@Component
@RequiredArgsConstructor
public class OntologyAccessChecker {

    /**
     * 세그먼트 최소 크기. 이보다 작으면 작가가 구성원을 역추론할 수 있다
     * (예: "최근 3명 이탈" → 댓글 단 사람과 대조하면 개인 특정 가능).
     */
    public static final int MIN_SEGMENT_SIZE = 5;

    private final SeriesRepository seriesRepository;

    /** 작가 본인 작품만 진단 대상. 아니면 존재 은닉. */
    public Series requireOwnedWork(Long seriesId, Long userId) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!series.isAuthoredBy(userId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        return series;
    }

    /** k-익명성 판정. false면 크기를 노출하지 않고 액션도 막는다. */
    public boolean isDisclosable(long segmentSize) {
        return segmentSize >= MIN_SEGMENT_SIZE;
    }

    /**
     * 링크 목록에 이 작품을 노출해도 되는가(가시성 전파).
     *
     * SeriesAccessChecker.verifyInteractable을 재사용하지 않는다 — 그건 예외를 던지는 가드라
     * 목록 필터로 쓰면 **첫 비공개 작품에서 응답 전체가 404**가 된다. 구조적으로 재사용 불가다.
     *
     * 다만 판정 축은 기존과 반드시 일치해야 한다: 성인 판정은 ageRating == AGE_19이지
     * adultOnly가 아니다. 불변식이 adultOnly ⇒ AGE_19 **한 방향뿐**이라 adultOnly를 기준으로
     * 잡으면 adultOnly=false인 AGE_19 작품이 미성년 요청자에게 샌다.
     */
    public boolean isLinkable(Series other, User viewer) {
        if (other.isAuthoredBy(viewer.getId())) {
            return true;
        }
        if (!other.isVisible()) {
            return false;
        }
        return other.getAgeRating() != AgeRating.AGE_19 || viewer.isAdult(LocalDate.now());
    }
}
