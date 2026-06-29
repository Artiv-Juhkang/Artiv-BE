package com.juhkang.artiv.domain.inquiry;

/** 문의 라이프사이클: 접수(PENDING) → 답변(ANSWERED) → 종료(CLOSED). */
public enum InquiryStatus {
    PENDING,
    ANSWERED,
    CLOSED
}
