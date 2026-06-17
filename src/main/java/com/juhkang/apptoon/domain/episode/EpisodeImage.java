package com.juhkang.apptoon.domain.episode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

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
