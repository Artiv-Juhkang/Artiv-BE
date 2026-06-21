package com.juhkang.apptoon.domain.user;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.user.dto.ConsentResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentService {

    private final UserConsentRepository userConsentRepository;

    /** 가입 시 동의 캡처 — 필수(약관·개인정보) 미동의면 가입 차단. 제공된 각 동의를 행으로 저장. */
    @Transactional
    public void captureSignup(Long userId, Map<ConsentType, Boolean> consents) {
        Map<ConsentType, Boolean> given = consents != null ? consents : Map.of();
        for (ConsentType type : ConsentType.values()) {
            if (type.isRequired() && !Boolean.TRUE.equals(given.get(type))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT); // 필수 동의 누락
            }
        }
        given.forEach((type, agreed) ->
                userConsentRepository.save(UserConsent.of(userId, type, Boolean.TRUE.equals(agreed))));
    }

    public List<ConsentResponse> getMine(Long userId) {
        return userConsentRepository.findByUserIdOrderByConsentType(userId).stream()
                .map(ConsentResponse::of)
                .toList();
    }

    /** 동의 토글(주로 마케팅). 필수 동의는 false로 철회 불가. */
    @Transactional
    public List<ConsentResponse> update(Long userId, Map<ConsentType, Boolean> consents) {
        consents.forEach((type, value) -> {
            boolean agreed = Boolean.TRUE.equals(value);
            if (type.isRequired() && !agreed) {
                throw new BusinessException(ErrorCode.INVALID_INPUT); // 필수 동의 철회 불가
            }
            userConsentRepository.findByUserIdAndConsentType(userId, type)
                    .ifPresentOrElse(c -> c.update(agreed),
                            () -> userConsentRepository.save(UserConsent.of(userId, type, agreed)));
        });
        return getMine(userId);
    }
}
