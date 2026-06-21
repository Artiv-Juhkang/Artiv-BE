package com.juhkang.apptoon.domain.user.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.user.ConsentType;
import com.juhkang.apptoon.domain.user.UserConsent;

/** 동의 내역(무엇·동의여부·버전·언제) — 설정 화면 조회용. */
public record ConsentResponse(
        ConsentType consentType,
        boolean required,
        int version,
        boolean agreed,
        Instant agreedAt
) {

    public static ConsentResponse of(UserConsent c) {
        return new ConsentResponse(c.getConsentType(), c.getConsentType().isRequired(),
                c.getVersion(), c.isAgreed(), c.getAgreedAt());
    }
}
