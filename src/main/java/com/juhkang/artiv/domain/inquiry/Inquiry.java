package com.juhkang.artiv.domain.inquiry;

import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(length = 2000)
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    private Inquiry(User user, InquiryType type, String title, String content) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.status = InquiryStatus.PENDING;
    }

    public static Inquiry create(User user, InquiryType type, String title, String content) {
        return new Inquiry(user, type, title, content);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /** 관리자 답변 — 답변 내용 기록 후 상태를 ANSWERED로 전이한다. */
    public void answer(String answer) {
        this.answer = answer;
        this.status = InquiryStatus.ANSWERED;
    }

    public void changeStatus(InquiryStatus status) {
        this.status = status;
    }
}
