package com.juhkang.apptoon.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.dto.SignupRequest;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 회원가입은_비밀번호를_암호화해_저장한다() {
        Long id = authService.signup(new SignupRequest("a@b.com", "password123", "닉네임", LocalDate.of(2000, 1, 1)));

        User user = userRepository.findById(id).orElseThrow();
        assertNotEquals("password123", user.getPassword());
        assertTrue(passwordEncoder.matches("password123", user.getPassword()));
        assertEquals(Role.READER, user.getRole());
    }

    @Test
    void 회원가입은_생년월일을_저장한다() {
        Long id = authService.signup(new SignupRequest("birth@b.com", "password123", "닉", LocalDate.of(2000, 3, 15)));

        User user = userRepository.findById(id).orElseThrow();
        assertEquals(LocalDate.of(2000, 3, 15), user.getBirthDate());
    }

    @Test
    void 중복_이메일_가입은_DUPLICATE_EMAIL_예외를_던진다() {
        authService.signup(new SignupRequest("dup@b.com", "password123", "닉1", LocalDate.of(2000, 1, 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.signup(new SignupRequest("dup@b.com", "password123", "닉2", LocalDate.of(2000, 1, 1))));
        assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.getErrorCode());
    }
}
