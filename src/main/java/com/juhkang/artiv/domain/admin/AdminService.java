package com.juhkang.artiv.domain.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.dto.SeriesResponse;
import com.juhkang.artiv.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.domain.user.dto.UserResponse;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final SeriesRepository seriesRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(String keyword, Role role, Pageable pageable) {
        return PageResponse.from(userRepository.search(keyword, role, Pageables.pageOnly(pageable)).map(UserResponse::of));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeriesSummaryResponse> getAllSeries(Boolean visible, Pageable pageable) {
        return PageResponse.from(seriesRepository.findForAdmin(visible, Pageables.pageOnly(pageable)).map(SeriesSummaryResponse::of));
    }

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
