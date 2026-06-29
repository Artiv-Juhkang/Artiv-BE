package com.juhkang.artiv.domain.user;

/**
 * 법적 동의 종류. required=가입 시 필수 동의(미동의 시 가입 차단), currentVersion=현재 약관 버전(개정 시 증가→재동의).
 */
public enum ConsentType {
    TERMS_OF_SERVICE(true, 1),   // 서비스 이용약관(필수)
    PRIVACY_POLICY(true, 1),     // 개인정보 처리방침(필수)
    MARKETING_EMAIL(false, 1),   // 마케팅 수신(선택, 기본 미동의)
    ADULT_CONTENT_19(false, 1);  // 성인 콘텐츠 열람 동의(선택, 만19세 이상)

    private final boolean required;
    private final int currentVersion;

    ConsentType(boolean required, int currentVersion) {
        this.required = required;
        this.currentVersion = currentVersion;
    }

    public boolean isRequired() {
        return required;
    }

    public int currentVersion() {
        return currentVersion;
    }
}
