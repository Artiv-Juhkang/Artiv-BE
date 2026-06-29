package com.juhkang.artiv.domain.comment;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.global.entity.BaseEntity;

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
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id")
    private Episode episode;

    @Column(nullable = false, length = 1000)
    private String content;

    private Comment(User user, Episode episode, String content) {
        this.user = user;
        this.episode = episode;
        this.content = content;
    }

    public static Comment create(User user, Episode episode, String content) {
        return new Comment(user, episode, content);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
