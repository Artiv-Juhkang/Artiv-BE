package com.juhkang.apptoon.domain.episode;

import java.time.Instant;

import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.global.entity.BaseEntity;

import jakarta.persistence.Column;
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
@Table(name = "episodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id")
    private Series series;

    @Column(name = "episode_no", nullable = false)
    private int episodeNo;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EpisodeStatus status;

    @Column(name = "publish_at")
    private Instant publishAt;

    private Episode(Series series, int episodeNo, String title, EpisodeStatus status, Instant publishAt) {
        this.series = series;
        this.episodeNo = episodeNo;
        this.title = title;
        this.status = status;
        this.publishAt = publishAt;
    }

    public static Episode create(Series series, int episodeNo, String title, EpisodeStatus status, Instant publishAt) {
        return new Episode(series, episodeNo, title, status, publishAt);
    }

    public void markPublished() {
        this.status = EpisodeStatus.PUBLISHED;
    }
}
