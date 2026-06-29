package com.juhkang.artiv.domain.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUserIdOrderByConsentType(Long userId);

    Optional<UserConsent> findByUserIdAndConsentType(Long userId, ConsentType consentType);
}
