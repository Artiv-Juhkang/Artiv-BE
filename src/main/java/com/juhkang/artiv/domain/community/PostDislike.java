package com.juhkang.artiv.domain.community;

import com.juhkang.artiv.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 비추천(user-post 멱등). PostLike 패턴 — 추천과 상호배타는 서비스가 강제. */
@Entity
@Table(name = "post_dislikes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostDislike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    private PostDislike(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public static PostDislike create(Long userId, Long postId) {
        return new PostDislike(userId, postId);
    }
}
