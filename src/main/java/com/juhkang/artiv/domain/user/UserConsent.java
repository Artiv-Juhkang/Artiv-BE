package com.juhkang.artiv.domain.user;

import java.time.Instant;

import com.juhkang.artiv.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 동의 현재상태(type당 1행). 갱신 시 version/agreedAt 기록(감사). */
@Entity
@Table(name = "user_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    private UserConsent(Long userId, ConsentType consentType, int version, boolean agreed) {
        this.userId = userId;
        this.consentType = consentType;
        this.version = version;
        this.agreed = agreed;
        this.agreedAt = agreed ? Instant.now() : null;
    }

    public static UserConsent of(Long userId, ConsentType consentType, boolean agreed) {
        return new UserConsent(userId, consentType, consentType.currentVersion(), agreed);
    }

    /** 동의 상태 갱신 — 동의 시 현재 버전·시각 기록, 철회 시 시각 제거. */
    public void update(boolean agreed) {
        this.agreed = agreed;
        this.version = consentType.currentVersion();
        this.agreedAt = agreed ? Instant.now() : null;
    }
}
