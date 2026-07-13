package com.juhkang.artiv.domain.user;

import java.time.Instant;
import java.time.LocalDate;

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

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "avatar_key", length = 500)
    private String avatarKey;

    @Column(length = 500)
    private String bio;

    @Column(name = "profile_public", nullable = false)
    private boolean profilePublic = true;

    /** 탈퇴 시각(soft delete, 확정 D5=A). null이면 활성 계정. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    private User(String email, String password, String nickname, Role role, LocalDate birthDate) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.birthDate = birthDate;
    }

    public static User create(String email, String password, String nickname, Role role, LocalDate birthDate) {
        return new User(email, password, nickname, role, birthDate);
    }

    /** 기준일(today)에 만 19세 이상이면 성인. 생년월일 미보유는 미성년으로 취급(보수적). */
    public boolean isAdult(LocalDate today) {
        return birthDate != null && !birthDate.isAfter(today.minusYears(19));
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 프로필 공개 설정(CH1) — 비공개여도 닉네임·아바타·역할은 공개, bio·가입일만 숨김. */
    public void changeProfileVisibility(boolean profilePublic) {
        this.profilePublic = profilePublic;
    }

    public void changeBio(String bio) {
        this.bio = bio;
    }

    /** 아바타 저장 key 교체(삭제 시 null). 공개 URL은 스토리지에서 동적 계산. */
    public void changeAvatar(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 회원 탈퇴 — email/nickname을 센티널로 치환해 unique를 비우고(재가입 허용) deletedAt을 찍는다. */
    public void withdraw() {
        this.email = "withdrawn+" + id + "@artiv.invalid";
        this.nickname = "탈퇴회원" + id;
        this.deletedAt = Instant.now();
    }
}
