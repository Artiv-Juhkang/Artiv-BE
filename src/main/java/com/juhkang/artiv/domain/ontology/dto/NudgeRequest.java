package com.juhkang.artiv.domain.ontology.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 요청 바디가 seriesId 하나뿐이다 — 문구는 서버 고정이다.
 *
 * 작가 자유 문구를 받지 않는 이유: notifications.title이 varchar(255), message가 varchar(500)이라
 * 초과 시 DataIntegrityViolation 500이 나고, 길이 검증을 붙여도 스팸·괴롭힘 표면이 열려
 * 신고·검수 도메인을 부른다.
 */
public record NudgeRequest(@NotNull Long seriesId) {
}
