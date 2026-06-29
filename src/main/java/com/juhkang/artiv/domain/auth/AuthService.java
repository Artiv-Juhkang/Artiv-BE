package com.juhkang.artiv.domain.auth;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.auth.dto.LoginRequest;
import com.juhkang.artiv.domain.auth.dto.RefreshRequest;
import com.juhkang.artiv.domain.auth.dto.SignupRequest;
import com.juhkang.artiv.domain.auth.dto.TokenResponse;
import com.juhkang.artiv.domain.user.ConsentService;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int MIN_SIGNUP_AGE = 14; // 만 14세 미만 가입 차단(개인정보보호법)

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final ConsentService consentService;

    @Transactional
    public Long signup(SignupRequest request) {
        if (request.birthDate().isAfter(LocalDate.now().minusYears(MIN_SIGNUP_AGE))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 만 14세 미만
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String nickname = Normalizer.normalize(request.nickname(), Normalizer.Form.NFC);  // 멘션 조회와 표현형 일치
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                nickname,
                Role.READER,
                request.birthDate()
        );
        Long userId = userRepository.save(user).getId();
        consentService.captureSignup(userId, request.consents()); // 필수 동의 검증 + 기록
        return userId;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        refreshTokenRepository.delete(stored); // 회전: 사용한 토큰은 즉시 폐기
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = generateOpaqueToken();
        Instant expiresAt = Instant.now().plusMillis(jwtProperties.refreshTokenValidity());
        refreshTokenRepository.save(RefreshToken.create(refreshToken, user.getId(), expiresAt));
        return new TokenResponse(accessToken, refreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
