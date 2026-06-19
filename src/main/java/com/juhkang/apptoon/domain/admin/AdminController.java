package com.juhkang.apptoon.domain.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.admin.dto.AdultOnlyUpdateRequest;
import com.juhkang.apptoon.domain.admin.dto.AgeRatingUpdateRequest;
import com.juhkang.apptoon.domain.admin.dto.RoleUpdateRequest;
import com.juhkang.apptoon.domain.admin.dto.VisibilityUpdateRequest;
import com.juhkang.apptoon.domain.series.dto.SeriesResponse;
import com.juhkang.apptoon.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.dto.UserResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Role role,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return adminService.getUsers(keyword, role, pageable);
    }

    @GetMapping("/series")
    public PageResponse<SeriesSummaryResponse> series(@RequestParam(required = false) Boolean visible,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return adminService.getAllSeries(visible, pageable);
    }

    @PatchMapping("/users/{userId}/role")
    public UserResponse changeRole(@PathVariable Long userId, @Valid @RequestBody RoleUpdateRequest request) {
        return adminService.changeUserRole(userId, request.role());
    }

    @PatchMapping("/series/{seriesId}/age-rating")
    public SeriesResponse changeAgeRating(@PathVariable Long seriesId, @Valid @RequestBody AgeRatingUpdateRequest request) {
        return adminService.changeSeriesAgeRating(seriesId, request.ageRating());
    }

    @PatchMapping("/series/{seriesId}/visibility")
    public SeriesResponse changeVisibility(@PathVariable Long seriesId, @Valid @RequestBody VisibilityUpdateRequest request) {
        return adminService.changeSeriesVisibility(seriesId, request.visible());
    }

    @PatchMapping("/series/{seriesId}/adult-only")
    public SeriesResponse changeAdultOnly(@PathVariable Long seriesId, @Valid @RequestBody AdultOnlyUpdateRequest request) {
        return adminService.changeSeriesAdultOnly(seriesId, request.adultOnly());
    }
}
