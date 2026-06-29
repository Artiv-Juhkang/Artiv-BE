package com.juhkang.artiv.domain.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 첨부 이미지. EpisodeImage/InquiryImage 패턴 복제. */
@Entity
@Table(name = "post_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    private PostImage(Long postId, int sortOrder, String path, int width, int height) {
        this.postId = postId;
        this.sortOrder = sortOrder;
        this.path = path;
        this.width = width;
        this.height = height;
    }

    public static PostImage create(Long postId, int sortOrder, String path, int width, int height) {
        return new PostImage(postId, sortOrder, path, width, height);
    }
}
