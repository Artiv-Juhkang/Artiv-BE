package com.juhkang.apptoon.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.user.dto.UserResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return UserResponse.of(user);
    }
}
