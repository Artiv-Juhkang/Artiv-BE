package com.juhkang.apptoon.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.AuthService;
import com.juhkang.apptoon.domain.auth.dto.SignupRequest;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class NicknameUniquenessTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);
    private static final Map<ConsentType, Boolean> CONSENTS =
            Map.of(ConsentType.TERMS_OF_SERVICE, true, ConsentType.PRIVACY_POLICY, true);

    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void 이미_쓰는_닉네임으로_가입하면_409() {
        userRepository.save(User.create("taken@nick.com", passwordEncoder.encode("password123"), "고유닉", Role.READER, ADULT));
        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("new@nick.com", "password123", "고유닉", ADULT, CONSENTS)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 다른_사용자_닉네임으로_변경하면_409() {
        userRepository.save(User.create("a@nick.com", passwordEncoder.encode("password123"), "닉앨리스", Role.READER, ADULT));
        Long bob = userRepository.save(User.create("b@nick.com", passwordEncoder.encode("password123"), "닉밥", Role.READER, ADULT)).getId();
        assertThatThrownBy(() -> userService.changeNickname(bob, "닉앨리스"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 내_닉네임_그대로_변경은_성공() {
        Long me = userRepository.save(User.create("me@nick.com", passwordEncoder.encode("password123"), "닉그대로", Role.READER, ADULT)).getId();
        assertThatCode(() -> userService.changeNickname(me, "닉그대로")).doesNotThrowAnyException();
        assertThat(userRepository.findById(me).orElseThrow().getNickname()).isEqualTo("닉그대로");
    }
}
