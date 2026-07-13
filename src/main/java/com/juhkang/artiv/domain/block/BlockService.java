package com.juhkang.artiv.domain.block;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.block.dto.BlockedUserResponse;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;
import com.juhkang.artiv.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

/** 사용자 차단(CB, 확정 D7=a) — 효과는 ChatService의 DM 생성 가드 + FE 글·댓글 숨김(BE 무필터). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public void block(Long blockerId, Long targetId) {
        if (blockerId.equals(targetId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 자기 자신 차단 불가
        }
        if (!userRepository.existsById(targetId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
            return; // 멱등
        }
        blockRepository.save(Block.create(blockerId, targetId));
    }

    @Transactional
    public void unblock(Long blockerId, Long targetId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, targetId);
    }

    public List<BlockedUserResponse> getBlocked(Long userId) {
        return blockRepository.findBlockedUsers(userId).stream().map(this::toUser).toList();
    }

    private BlockedUserResponse toUser(User u) {
        String avatarUrl = u.getAvatarKey() != null ? imageStorageService.urlFor(u.getAvatarKey()) : null;
        return new BlockedUserResponse(u.getId(), u.getNickname(), avatarUrl);
    }
}
