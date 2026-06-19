package com.juhkang.apptoon.domain.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.series.dto.SeriesResponse;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.domain.user.dto.UserResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final SeriesRepository seriesRepository;

    public UserResponse changeUserRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        user.changeRole(role);
        return UserResponse.of(user);
    }

    public SeriesResponse changeSeriesAgeRating(Long seriesId, AgeRating ageRating) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        series.changeAgeRating(ageRating);
        return SeriesResponse.of(series);
    }

    public SeriesResponse changeSeriesVisibility(Long seriesId, boolean visible) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        series.changeVisibility(visible);
        return SeriesResponse.of(series);
    }

    public SeriesResponse changeSeriesAdultOnly(Long seriesId, boolean adultOnly) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        series.changeAdultOnly(adultOnly);
        return SeriesResponse.of(series);
    }
}
