package com.juhkang.artiv.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.user.dto.CreatorRequestAdminResponse;
import com.juhkang.artiv.domain.user.dto.CreatorRequestResponse;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorRequestService {

    private final CreatorRequestRepository creatorRequestRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreatorRequestResponse submit(Long userId, String reason) {
        User user = loadUser(userId);
        if (user.getRole() != Role.READER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 이미 작가/관리자
        }
        if (creatorRequestRepository.existsByUserIdAndStatus(userId, CreatorRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 이미 신청 중
        }
        return CreatorRequestResponse.of(creatorRequestRepository.save(CreatorRequest.create(userId, reason)));
    }

    public CreatorRequestResponse getMine(Long userId) {
        return creatorRequestRepository.findTopByUserIdOrderByIdDesc(userId)
                .map(CreatorRequestResponse::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    public PageResponse<CreatorRequestAdminResponse> getForAdmin(CreatorRequestStatus status, Pageable pageable) {
        Pageable paged = Pageables.pageOnly(pageable);
        Page<CreatorRequest> page = (status == null)
                ? creatorRequestRepository.findAllByOrderByIdDesc(paged)
                : creatorRequestRepository.findByStatusOrderByIdDesc(status, paged);
        return PageResponse.from(page.map(this::toAdmin));
    }

    @Transactional
    public CreatorRequestAdminResponse approve(Long adminId, Long requestId, String note) {
        CreatorRequest request = loadRequest(requestId);
        request.approve(adminId, note);
        User applicant = loadUser(request.getUserId());
        if (applicant.getRole() == Role.READER) {
            applicant.changeRole(Role.CREATOR); // 독자만 승격(이미 작가/관리자는 그대로)
        }
        return toAdmin(request);
    }

    @Transactional
    public CreatorRequestAdminResponse reject(Long adminId, Long requestId, String note) {
        CreatorRequest request = loadRequest(requestId);
        request.reject(adminId, note);
        User applicant = loadUser(request.getUserId());
        if (applicant.getRole() == Role.CREATOR) {
            applicant.changeRole(Role.READER); // 승인 정정 시 작가만 강등(관리자 보호)
        }
        return toAdmin(request);
    }

    private CreatorRequestAdminResponse toAdmin(CreatorRequest request) {
        User applicant = userRepository.findById(request.getUserId()).orElse(null);
        String nickname = applicant != null ? applicant.getNickname() : "(삭제된 사용자)";
        String email = applicant != null ? applicant.getEmail() : "";
        return CreatorRequestAdminResponse.of(request, nickname, email);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private CreatorRequest loadRequest(Long requestId) {
        return creatorRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }
}
