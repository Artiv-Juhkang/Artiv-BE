package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.global.entity.BaseEntity;

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

    private Series(String title, String description, User author, AgeRating ageRating, SeriesStatus status,
                   Set<DayOfWeek> publishDays) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.ageRating = ageRating;
        this.status = status;
        this.publishDays = new HashSet<>(publishDays);
    }

    public static Series create(String title, String description, User author, AgeRating ageRating,
                                SeriesStatus status, Set<DayOfWeek> publishDays) {
        return new Series(title, description, author, ageRating, status, publishDays);
    }
}
