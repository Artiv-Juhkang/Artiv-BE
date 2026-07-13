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

/**
 * 게시글 말머리 등록제(C7, 확정 D1=B) — name 자체가 표시 라벨(별도 라벨 매핑 없음).
 * created_by=null이면 시드(추천/자유/팬아트/질문), 값이 있으면 사용자가 등록.
 * 삭제 API 없음 — 이미 글이 매달려 있을 수 있어 등록만 가능(가산 전용).
 */
@Entity
@Table(name = "post_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "created_by")
    private Long createdBy;

    private PostCategory(String name, Long createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    public static PostCategory create(String name, Long createdBy) {
        return new PostCategory(name, createdBy);
    }
}
