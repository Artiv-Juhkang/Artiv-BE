package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.global.entity.BaseEntity;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "series")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Series extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeriesStatus status;

    @ElementCollection
    @CollectionTable(name = "series_publish_days", joinColumns = @JoinColumn(name = "series_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "publish_day", length = 10)
    private Set<DayOfWeek> publishDays = new HashSet<>();

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private boolean adultOnly = false;

    private Series(String title, String description, User author, AgeRating ageRating, SeriesStatus status,
                   Set<DayOfWeek> publishDays, boolean adultOnly) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.ageRating = ageRating;
        this.status = status;
        this.publishDays = new HashSet<>(publishDays);
        this.adultOnly = adultOnly;
        validateAdultConsistency();
    }

    public static Series create(String title, String description, User author, AgeRating ageRating,
                                SeriesStatus status, Set<DayOfWeek> publishDays) {
        return create(title, description, author, ageRating, status, publishDays, false);
    }

    public static Series create(String title, String description, User author, AgeRating ageRating,
                                SeriesStatus status, Set<DayOfWeek> publishDays, boolean adultOnly) {
        return new Series(title, description, author, ageRating, status, publishDays, adultOnly);
    }

    public void changeAgeRating(AgeRating ageRating) {
        this.ageRating = ageRating;
        validateAdultConsistency();
    }

    public void changeVisibility(boolean visible) {
        this.visible = visible;
    }

    public void changeAdultOnly(boolean adultOnly) {
        this.adultOnly = adultOnly;
        validateAdultConsistency();
    }

    /** 불변식: 성인 전용(adultOnly)은 연령등급이 AGE_19 여야 한다. */
    private void validateAdultConsistency() {
        if (adultOnly && ageRating != AgeRating.AGE_19) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 이 작품의 작가 본인인지. 비공개·미발행 프리뷰 권한 판정에 사용. */
    public boolean isAuthoredBy(Long userId) {
        return author.getId().equals(userId);
    }
}
