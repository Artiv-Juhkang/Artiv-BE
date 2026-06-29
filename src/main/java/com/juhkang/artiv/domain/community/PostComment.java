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

/** 게시글 댓글 — parentId로 1-depth 대댓글. */
@Entity
@Table(name = "post_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private boolean blinded;

    @Column(name = "blinded_by")
    private Long blindedBy;

    @Column(name = "blinded_at")
    private java.time.Instant blindedAt;

    private PostComment(Long postId, Long authorId, Long parentId, String content) {
        this.postId = postId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.content = content;
    }

    public static PostComment create(Long postId, Long authorId, Long parentId, String content) {
        return new PostComment(postId, authorId, parentId, content);
    }

    public boolean isOwnedBy(Long userId) {
        return authorId.equals(userId);
    }

    public boolean isReply() {
        return parentId != null;
    }

    public void blind(Long byUserId) {
        this.blinded = true;
        this.blindedBy = byUserId;
        this.blindedAt = java.time.Instant.now();
    }
}
