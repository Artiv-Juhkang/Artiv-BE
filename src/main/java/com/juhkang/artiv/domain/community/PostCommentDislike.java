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

/** 게시글 댓글 싫어요(user-comment 멱등). 좋아요와 상호배타는 서비스가 강제. */
@Entity
@Table(name = "post_comment_dislikes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCommentDislike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    private PostCommentDislike(Long userId, Long commentId) {
        this.userId = userId;
        this.commentId = commentId;
    }

    public static PostCommentDislike create(Long userId, Long commentId) {
        return new PostCommentDislike(userId, commentId);
    }
}
