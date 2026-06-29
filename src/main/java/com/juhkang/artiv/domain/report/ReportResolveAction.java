package com.juhkang.artiv.domain.report;

/** 신고 처리 시 대상에 취할 조치. */
public enum ReportResolveAction {
    NONE,          // 처리만(대상 유지)
    BLIND_TARGET,  // 대상(게시글/댓글) 블라인드
    DELETE_TARGET  // 대상 삭제
}
