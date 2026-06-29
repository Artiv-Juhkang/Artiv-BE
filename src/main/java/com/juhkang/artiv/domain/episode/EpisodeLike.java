package com.juhkang.artiv.domain.episode;

import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.global.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "episode_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_episode_like_user_episode", columnNames = {"user_id", "episode_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id")
    private Episode episode;

    private EpisodeLike(User user, Episode episode) {
        this.user = user;
        this.episode = episode;
    }

    public static EpisodeLike create(User user, Episode episode) {
        return new EpisodeLike(user, episode);
    }
}
