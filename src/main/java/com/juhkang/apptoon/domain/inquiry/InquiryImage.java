package com.juhkang.apptoon.domain.inquiry;

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

/** 문의 첨부 이미지. EpisodeImage 구조를 복제(타임스탬프 불필요). */
@Entity
@Table(name = "inquiry_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id")
    private Inquiry inquiry;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    private InquiryImage(Inquiry inquiry, int sortOrder, String path, int width, int height) {
        this.inquiry = inquiry;
        this.sortOrder = sortOrder;
        this.path = path;
        this.width = width;
        this.height = height;
    }

    public static InquiryImage create(Inquiry inquiry, int sortOrder, String path, int width, int height) {
        return new InquiryImage(inquiry, sortOrder, path, width, height);
    }
}
