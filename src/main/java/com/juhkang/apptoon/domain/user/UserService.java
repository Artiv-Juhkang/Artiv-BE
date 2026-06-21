package com.juhkang.apptoon.domain.user;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.domain.user.dto.MyProfileResponse;
import com.juhkang.apptoon.domain.user.dto.UserResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;
import com.juhkang.apptoon.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService imageStorageService;

    public UserResponse getMyInfo(Long userId) {
        return UserResponse.of(load(userId));
    }

    public MyProfileResponse getMyProfile(Long userId) {
        return toProfile(load(userId));
    }

    @Transactional
    public MyProfileResponse changeNickname(Long userId, String nickname) {
        User user = load(userId);
        user.changeNickname(nickname);
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

    private MyProfileResponse toProfile(User user) {
        String avatarUrl = user.getAvatarKey() != null ? imageStorageService.urlFor(user.getAvatarKey()) : null;
        return MyProfileResponse.of(user, avatarUrl);
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }
}
