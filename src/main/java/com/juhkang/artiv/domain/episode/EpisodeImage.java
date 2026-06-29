package com.juhkang.artiv.domain.episode;

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
@Table(name = "episode_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id")
    private Episode episode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 500)
    private String path;

    // 다매체 일반화(슬라이스 1): 자산 종류. 기존 이미지 행은 IMAGE(기본값·V24 백필).
    @Enumerated(EnumType.STRING)
    @Column(name = "media_kind", nullable = false, length = 20)
    private MediaKind mediaKind = MediaKind.IMAGE;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "duration_ms")
    private Long durationMs;

    // 픽셀 치수는 IMAGE(·VIDEO)만 가진다 — 오디오/텍스트는 null. 일반화로 nullable.
    @Column
    private Integer width;

    @Column
    private Integer height;

    private EpisodeImage(Episode episode, int sortOrder, String path, int width, int height) {
        this.episode = episode;
        this.sortOrder = sortOrder;
        this.path = path;
        this.width = width;
        this.height = height;
    }

    public static EpisodeImage create(Episode episode, int sortOrder, String path, int width, int height) {
        return new EpisodeImage(episode, sortOrder, path, width, height);
    }
}
