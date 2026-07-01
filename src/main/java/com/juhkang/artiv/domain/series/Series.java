package com.juhkang.artiv.domain.series;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.global.entity.BaseEntity;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

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

    // 다매체 전환(슬라이스 2): 작품 매체 구분. WEBTOON만 연재요일·회차번호를 사용한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType = ContentType.WEBTOON;

    @ElementCollection
    @CollectionTable(name = "series_publish_days", joinColumns = @JoinColumn(name = "series_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "publish_day", length = 10)
    private Set<DayOfWeek> publishDays = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Genre genre = Genre.ETC;

    @ElementCollection
    @CollectionTable(name = "series_tags", joinColumns = @JoinColumn(name = "series_id"))
    @Column(name = "tag", length = 30)
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private boolean adultOnly = false;

    // 표출 seam(다매체): 커버 아트 URL. 없으면 프론트 placeholder. 설정은 후속(작가 업로드).
    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    // 비정규화: 최신 PUBLISHED 회차 발행시각('최근 업데이트' 정렬용, V26). 발행 시 전진 갱신.
    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    // 수익화 0단계: 작품 공개정책(결제 없음). 기존 작품은 V22에서 FREE_ALL backfill.
    @Enumerated(EnumType.STRING)
    @Column(name = "release_policy", nullable = false, length = 20)
    private ReleasePolicy releasePolicy = ReleasePolicy.FREE_ALL;

    /** WAIT_FREE 무료전환 주기(일). 불변식: non-null ⟺ WAIT_FREE. */
    @Column(name = "wait_free_days")
    private Integer waitFreeDays;

    private Series(String title, String description, User author, AgeRating ageRating, SeriesStatus status,
                   Set<DayOfWeek> publishDays, boolean adultOnly, ContentType contentType) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.ageRating = ageRating;
        this.status = status;
        this.publishDays = new HashSet<>(publishDays);
        this.adultOnly = adultOnly;
        this.contentType = contentType;
        validateAdultConsistency();
    }

    public static Series create(String title, String description, User author, AgeRating ageRating,
                                SeriesStatus status, Set<DayOfWeek> publishDays) {
        return create(title, description, author, ageRating, status, publishDays, false, ContentType.WEBTOON);
    }

    public static Series create(String title, String description, User author, AgeRating ageRating,
                                SeriesStatus status, Set<DayOfWeek> publishDays, boolean adultOnly) {
        return create(title, description, author, ageRating, status, publishDays, adultOnly, ContentType.WEBTOON);
    }

    public static Series create(String title, String description, User author, AgeRating ageRating,
                                SeriesStatus status, Set<DayOfWeek> publishDays, boolean adultOnly,
                                ContentType contentType) {
        return new Series(title, description, author, ageRating, status, publishDays, adultOnly, contentType);
    }

    public void changeAgeRating(AgeRating ageRating) {
        this.ageRating = ageRating;
        validateAdultConsistency();
    }

    public void changeVisibility(boolean visible) {
        this.visible = visible;
    }

    public void changeGenre(Genre genre) {
        this.genre = genre;
    }

    /** 태그 전체 교체(정규화된 집합을 받음). 관리형 컬렉션을 clear+addAll로 안전하게 갱신. */
    public void replaceTags(Set<String> tags) {
        this.tags.clear();
        this.tags.addAll(tags);
    }

    public void changeAdultOnly(boolean adultOnly) {
        this.adultOnly = adultOnly;
        validateAdultConsistency();
    }

    /** 회차 발행 시 최신 발행시각 반영(비정규화 — '최근 업데이트' 정렬용). 더 최신일 때만 전진. */
    public void markEpisodePublished(Instant publishedAt) {
        if (publishedAt != null && (lastPublishedAt == null || publishedAt.isAfter(lastPublishedAt))) {
            this.lastPublishedAt = publishedAt;
        }
    }

    /** 작가 공개정책 변경. FREE_ALL로 바꾸면 waitFreeDays 잔여값을 청소(불변식 유지). */
    public void changeReleasePolicy(ReleasePolicy policy, Integer waitFreeDays) {
        this.releasePolicy = policy;
        this.waitFreeDays = (policy == ReleasePolicy.WAIT_FREE) ? waitFreeDays : null;
        validateReleasePolicy();
    }

    /** 불변식: WAIT_FREE면 waitFreeDays>0, FREE_ALL이면 null. */
    private void validateReleasePolicy() {
        if (releasePolicy == ReleasePolicy.WAIT_FREE && (waitFreeDays == null || waitFreeDays <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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
