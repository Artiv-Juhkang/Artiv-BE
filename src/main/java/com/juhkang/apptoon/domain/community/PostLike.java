package com.juhkang.apptoon.domain.community;

import com.juhkang.apptoon.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 추천(user-post 멱등). EpisodeLike 패턴. */
@Entity
@Table(name = "post_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    private PostLike(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public static PostLike create(Long userId, Long postId) {
        return new PostLike(userId, postId);
    }
}
