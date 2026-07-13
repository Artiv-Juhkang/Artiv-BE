package com.juhkang.artiv.domain.block;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.block.dto.BlockedUserResponse;

import lombok.RequiredArgsConstructor;

/** 사용자 차단(CB) — 차단/차단해제, 내 차단 목록. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/{targetId}/block")
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@AuthenticationPrincipal Long userId, @PathVariable Long targetId) {
        blockService.block(userId, targetId);
    }

    @DeleteMapping("/{targetId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Long userId, @PathVariable Long targetId) {
        blockService.unblock(userId, targetId);
    }

    @GetMapping("/me/blocks")
    public List<BlockedUserResponse> blocks(@AuthenticationPrincipal Long userId) {
        return blockService.getBlocked(userId);
    }
}
