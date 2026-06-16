package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.series.dto.SeriesCreateRequest;
import com.juhkang.apptoon.domain.series.dto.SeriesResponse;
import com.juhkang.apptoon.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long authorId, SeriesCreateRequest request) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Series series = Series.create(
                request.title(),
                request.description(),
                author,
                request.ageRating(),
                request.status(),
                request.publishDays()
        );
        return seriesRepository.save(series).getId();
    }

    public PageResponse<SeriesSummaryResponse> getList(DayOfWeek day, AgeRating ageRating, Pageable pageable) {
        Page<SeriesSummaryResponse> page = seriesRepository.search(day, ageRating, pageable)
                .map(SeriesSummaryResponse::of);
        return PageResponse.from(page);
    }

    public SeriesResponse getDetail(Long id) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return SeriesResponse.of(series);
    }
}
