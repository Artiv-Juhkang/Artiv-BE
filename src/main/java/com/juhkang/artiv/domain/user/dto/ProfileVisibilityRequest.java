package com.juhkang.artiv.domain.user.dto;

/** 프로필 공개 설정 변경 — {"profilePublic": bool}. ('public'은 Java 예약어라 필드명으로 못 쓴다.) */
public record ProfileVisibilityRequest(boolean profilePublic) {
}
