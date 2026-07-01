package com.juhkang.artiv.domain.comment;

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

/**
 * 회차 댓글/대댓글 좋아요. user-comment 멱등(유니크). 목록에서 다수 댓글의 카운트를
 * 배치로 집계하려고 관계 대신 평면 컬럼(userId/commentId)을 쓴다(LAZY 로딩 회피).
 */
@Entity
@Table(name = "comment_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    private CommentLike(Long userId, Long commentId) {
        this.userId = userId;
        this.commentId = commentId;
    }

    public static CommentLike create(Long userId, Long commentId) {
        return new CommentLike(userId, commentId);
    }
}
