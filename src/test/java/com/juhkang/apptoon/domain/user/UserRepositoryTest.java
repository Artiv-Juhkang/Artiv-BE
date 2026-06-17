package com.juhkang.apptoon.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.juhkang.apptoon.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 사용자를_저장하고_이메일로_조회하며_생성시각이_기록된다() {
        User saved = userRepository.save(
                User.create("test@example.com", "encoded-pw", "테스터", Role.READER, LocalDate.of(1990, 1, 1)));

        User found = userRepository.findByEmail("test@example.com").orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("테스터", found.getNickname());
        assertEquals(Role.READER, found.getRole());
        assertNotNull(found.getCreatedAt(), "JPA Auditing 으로 createdAt 이 채워져야 한다");
        assertTrue(userRepository.existsByEmail("test@example.com"));
    }
}
