package com.juhkang.artiv.domain.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.user.dto.BioUpdateRequest;
import com.juhkang.artiv.domain.user.dto.MyProfileResponse;
import com.juhkang.artiv.domain.user.dto.ProfileVisibilityRequest;
import com.juhkang.artiv.domain.user.dto.UserProfileResponse;
import com.juhkang.artiv.domain.user.dto.NicknameUpdateRequest;
import com.juhkang.artiv.domain.user.dto.PasswordChangeRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 공개 프로필 열람(CH1) — 모든 인증 사용자. /me 리터럴이 이 패턴보다 우선 매칭된다. */
    @GetMapping("/{userId}")
    public UserProfileResponse profile(@PathVariable Long userId) {
        return userService.getProfile(userId);
    }

    @PatchMapping("/me/profile-visibility")
    public MyProfileResponse changeProfileVisibility(@AuthenticationPrincipal Long userId,
                                                     @RequestBody ProfileVisibilityRequest request) {
        return userService.changeProfileVisibility(userId, request.profilePublic());
    }

    @GetMapping("/me")
    public MyProfileResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getMyProfile(userId);
    }

    @PatchMapping("/me/nickname")
    public MyProfileResponse changeNickname(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody NicknameUpdateRequest request) {
        return userService.changeNickname(userId, request.nickname());
    }

    @PatchMapping("/me/bio")
    public MyProfileResponse changeBio(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody BioUpdateRequest request) {
        return userService.changeBio(userId, request.bio());
    }

    @PatchMapping("/me/password")
    public void changePassword(@AuthenticationPrincipal Long userId,
                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
    }

    @PostMapping("/me/avatar")
    public MyProfileResponse uploadAvatar(@AuthenticationPrincipal Long userId,
                                          @RequestPart("file") MultipartFile file) {
        return userService.uploadAvatar(userId, file);
    }

    @DeleteMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(@AuthenticationPrincipal Long userId) {
        userService.deleteAvatar(userId);
    }
}
