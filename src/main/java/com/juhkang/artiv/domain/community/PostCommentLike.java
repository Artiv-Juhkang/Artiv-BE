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

/** 게시글 댓글 좋아요(user-comment 멱등). PostLike 패턴. */
@Entity
@Table(name = "post_comment_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCommentLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    private PostCommentLike(Long userId, Long commentId) {
        this.userId = userId;
        this.commentId = commentId;
    }

    public static PostCommentLike create(Long userId, Long commentId) {
        return new PostCommentLike(userId, commentId);
    }
}
