package com.juhkang.artiv.domain.series;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 회차 상호작용(좋아요·읽음·북마크)의 접근 가드.
 * 상세/목록과 동일하게 비공개(visible=false)는 작가 본인만, 19금은 만19세 이상만 허용한다.
 */
@Component
@RequiredArgsConstructor
public class SeriesAccessChecker {

    private final UserRepository userRepository;

    public void verifyInteractable(Series series, Long viewerId) {
        // 비공개 작품은 작가 본인만 — 그 외에는 존재를 숨겨 404
        if (!series.isVisible() && !series.isAuthoredBy(viewerId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        // 19금 작품은 만 19세 이상만
        if (series.getAgeRating() == AgeRating.AGE_19) {
            User viewer = userRepository.findById(viewerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
            if (!viewer.isAdult(LocalDate.now())) {
                throw new BusinessException(ErrorCode.ADULT_ONLY);
            }
        }
    }
}
