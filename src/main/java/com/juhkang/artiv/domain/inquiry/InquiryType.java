package com.juhkang.artiv.domain.inquiry;

/** 문의 종류 — 독자·작가 모두 커버. 표시 라벨은 프론트가 매핑한다. */
public enum InquiryType {
    ACCOUNT,  // 계정/로그인
    PAYMENT,  // 결제/환불
    CONTENT,  // 콘텐츠/신고
    CREATOR,  // 작가/작품 운영
    BUG,      // 오류
    ETC       // 기타
}
