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

    /** 대댓글이면 최상위 부모 댓글 id, 최상위 댓글이면 null(1-depth). */
    @Column(name = "parent_id")
    private Long parentId;

    private Comment(User user, Episode episode, String content, Long parentId) {
        this.user = user;
        this.episode = episode;
        this.content = content;
        this.parentId = parentId;
    }

    public static Comment create(User user, Episode episode, String content, Long parentId) {
        return new Comment(user, episode, content, parentId);
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
