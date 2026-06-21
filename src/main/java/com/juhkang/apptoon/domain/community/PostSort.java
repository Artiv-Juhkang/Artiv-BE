package com.juhkang.apptoon.domain.community;

import org.springframework.data.domain.Sort;

/** 게시글 정렬: 최신순 / 추천순(베스트). */
public enum PostSort {
    LATEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    BEST(Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt")));

    private final Sort sort;
    PostSort(Sort sort) { this.sort = sort; }
    public Sort toSort() { return sort; }
}
