package com.juhkang.artiv.domain.user;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.auth.RefreshTokenRepository;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.ontology.ReadingEventRepository;
import com.juhkang.artiv.domain.user.dto.MyProfileResponse;
import com.juhkang.artiv.domain.user.dto.UserProfileResponse;
import com.juhkang.artiv.domain.user.dto.UserResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;
import com.juhkang.artiv.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService imageStorageService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SeriesRepository seriesRepository;
    private final ReadingEventRepository readingEventRepository;

    public UserResponse getMyInfo(Long userId) {
        return UserResponse.of(load(userId));
    }

    public MyProfileResponse getMyProfile(Long userId) {
        return toProfile(load(userId));
    }

    /** 공개 프로필(타인 열람) — 비공개 마스킹은 DTO(UserProfileResponse.of)가 담당. */
    public UserProfileResponse getProfile(Long targetId) {
        User user = load(targetId);
        String avatarUrl = user.getAvatarKey() != null ? imageStorageService.urlFor(user.getAvatarKey()) : null;
        return UserProfileResponse.of(user, avatarUrl);
    }

    @Transactional
    public MyProfileResponse changeProfileVisibility(Long userId, boolean profilePublic) {
        User user = load(userId);
        user.changeProfileVisibility(profilePublic);
        return toProfile(user);
    }

    @Transactional
    public MyProfileResponse changeNickname(Long userId, String nickname) {
        String normalized = java.text.Normalizer.normalize(nickname, java.text.Normalizer.Form.NFC);  // 멘션 조회와 표현형 일치
        User user = load(userId);
        if (!user.getNickname().equals(normalized) && userRepository.existsByNickname(normalized)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(normalized);
        return toProfile(user);
    }

    @Transactional
    public MyProfileResponse changeBio(Long userId, String bio) {
        User user = load(userId);
        user.changeBio(bio);
        return toProfile(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = load(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 현재 비밀번호 불일치
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public MyProfileResponse uploadAvatar(Long userId, MultipartFile file) {
        User user = load(userId);
        String oldKey = user.getAvatarKey();
        String prefix = "avatars/" + userId + "/" + UUID.randomUUID();
        List<ImageStorageService.Stored> stored = imageStorageService.store(prefix, List.of(file));
        user.changeAvatar(stored.get(0).path());
        if (oldKey != null) {
            imageStorageService.delete(oldKey); // 이전 아바타 정리
        }
        return toProfile(user);
    }

    @Transactional
    public void deleteAvatar(Long userId) {
        User user = load(userId);
        String key = user.getAvatarKey();
        if (key != null) {
            imageStorageService.delete(key);
            user.changeAvatar(null);
        }
    }

    /** 회원 탈퇴 — 비번 검증→아바타 삭제→soft delete(센티널)→refresh 토큰 전량 폐기→(작가면) 작품 일괄 비공개(D6=B). */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = load(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 현재 비밀번호 불일치
        }
        String avatarKey = user.getAvatarKey();
        if (avatarKey != null) {
            imageStorageService.delete(avatarKey);
        }
        user.withdraw();
        refreshTokenRepository.deleteByUserId(userId);
        if (user.getRole() == Role.CREATOR) {
            seriesRepository.findByAuthorId(userId).forEach(series -> series.changeVisibility(false));
        }
        // 열람 계측 익명화 — 행은 남기고 사용자 연결만 끊는다(설계문서 §9). 탈퇴가 soft delete라
        // reading_events의 FK on delete set null은 발화하지 않으므로 명시적으로 지운다.
        // 벌크 UPDATE가 컨텍스트를 비우므로 반드시 마지막에 — 앞의 더티체킹 변경을 삼키지 않게 한다.
        readingEventRepository.anonymizeUser(userId);
    }

    private MyProfileResponse toProfile(User user) {
        String avatarUrl = user.getAvatarKey() != null ? imageStorageService.urlFor(user.getAvatarKey()) : null;
        return MyProfileResponse.of(user, avatarUrl);
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }
}
