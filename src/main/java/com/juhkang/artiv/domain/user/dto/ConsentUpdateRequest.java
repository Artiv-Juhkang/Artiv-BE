package com.juhkang.artiv.domain.user.dto;

import java.util.Map;

import com.juhkang.artiv.domain.user.ConsentType;

import jakarta.validation.constraints.NotEmpty;

/** 동의 토글(주로 마케팅). 필수 동의는 false로 철회 불가(서비스에서 검증). */
public record ConsentUpdateRequest(
        @NotEmpty Map<ConsentType, Boolean> consents
) {
}
