package com.juhkang.artiv.domain.community;

import com.juhkang.artiv.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 커뮤니티 게시글. 추천수 denorm, 블라인드 플래그(신고 자동/수동). 작성자는 비-연관 Long. */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private boolean blinded;

    @Column(name = "blinded_by")
    private Long blindedBy;

    @Column(name = "blinded_at")
    private java.time.Instant blindedAt;

    private Post(Long authorId, PostCategory category, String title, String content) {
        this.authorId = authorId;
        this.category = category;
        this.title = title;
        this.content = content;
    }

    public static Post create(Long authorId, PostCategory category, String title, String content) {
        return new Post(authorId, category, title, content);
    }

    public boolean isOwnedBy(Long userId) {
        return authorId.equals(userId);
    }

    public void increaseLike() {
        this.likeCount++;
    }

    public void decreaseLike() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    /** byUserId=null이면 시스템 자동 블라인드(신고 임계치), 값이 있으면 관리자 수동. */
    public void blind(Long byUserId) {
        this.blinded = true;
        this.blindedBy = byUserId;
        this.blindedAt = java.time.Instant.now();
    }

    public void unblind() {
        this.blinded = false;
        this.blindedBy = null;
        this.blindedAt = null;
    }
}
