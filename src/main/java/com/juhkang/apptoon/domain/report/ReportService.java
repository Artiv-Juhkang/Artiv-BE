package com.juhkang.apptoon.domain.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.community.Post;
import com.juhkang.apptoon.domain.community.PostComment;
import com.juhkang.apptoon.domain.community.PostCommentRepository;
import com.juhkang.apptoon.domain.community.PostCommentService;
import com.juhkang.apptoon.domain.community.PostRepository;
import com.juhkang.apptoon.domain.community.PostService;
import com.juhkang.apptoon.domain.report.dto.ReportAdminDetailResponse;
import com.juhkang.apptoon.domain.report.dto.ReportAdminResponse;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final int AUTO_BLIND_THRESHOLD = 5;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostService postService;
    private final PostCommentService postCommentService;

    @Transactional
    public void create(Long reporterId, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 같은 대상 중복 신고 불가
        }
        reportRepository.save(Report.create(reporterId, targetType, targetId, reason, detail));
        autoBlind(targetType, targetId);
    }

    /** 접수 신고 임계치 이상이면 시스템 자동 블라인드(blinded_by=null). */
    private void autoBlind(ReportTargetType targetType, Long targetId) {
        if (targetType != ReportTargetType.POST && targetType != ReportTargetType.COMMENT) {
            return;
        }
        long pending = reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING);
        if (pending < AUTO_BLIND_THRESHOLD) {
            return;
        }
        if (targetType == ReportTargetType.POST) {
            postRepository.findById(targetId).ifPresent(p -> p.blind(null));
        } else {
            postCommentRepository.findById(targetId).ifPresent(c -> c.blind(null));
        }
    }

    public PageResponse<ReportAdminResponse> getForAdmin(ReportStatus status, ReportTargetType targetType,
                                                         ReportReason reason, Pageable pageable) {
        Page<Report> page = reportRepository.findForAdmin(status, targetType, reason, pageable);
        return PageResponse.from(page.map(r -> ReportAdminResponse.of(r, nickname(r.getReporterId()))));
    }

    public ReportAdminDetailResponse getDetail(Long reportId) {
        return detailOf(load(reportId));
    }

    @Transactional
    public ReportAdminDetailResponse resolve(Long adminId, Long reportId, ReportResolveAction action) {
        Report report = load(reportId);
        report.resolve(adminId);
        applyAction(adminId, report, action);
        return detailOf(report);
    }

    @Transactional
    public ReportAdminDetailResponse dismiss(Long adminId, Long reportId) {
        Report report = load(reportId);
        report.dismiss(adminId);
        return detailOf(report);
    }

    /** 처리 시 대상에 조치(블라인드/삭제). 게시글·댓글만 대상. */
    private void applyAction(Long adminId, Report report, ReportResolveAction action) {
        if (action == null || action == ReportResolveAction.NONE) {
            return;
        }
        if (report.getTargetType() == ReportTargetType.POST) {
            if (action == ReportResolveAction.BLIND_TARGET) {
                postRepository.findById(report.getTargetId()).ifPresent(p -> p.blind(adminId));
            } else if (action == ReportResolveAction.DELETE_TARGET) {
                postService.delete(adminId, true, report.getTargetId());
            }
        } else if (report.getTargetType() == ReportTargetType.COMMENT) {
            if (action == ReportResolveAction.BLIND_TARGET) {
                postCommentRepository.findById(report.getTargetId()).ifPresent(c -> c.blind(adminId));
            } else if (action == ReportResolveAction.DELETE_TARGET) {
                postCommentService.delete(adminId, true, report.getTargetId());
            }
        }
    }

    private ReportAdminDetailResponse detailOf(Report report) {
        String content = "";
        String author = "";
        switch (report.getTargetType()) {
            case POST -> {
                Post p = postRepository.findById(report.getTargetId()).orElse(null);
                if (p != null) { content = p.getContent(); author = nickname(p.getAuthorId()); }
                else { content = "(삭제된 게시글)"; }
            }
            case COMMENT -> {
                PostComment c = postCommentRepository.findById(report.getTargetId()).orElse(null);
                if (c != null) { content = c.getContent(); author = nickname(c.getAuthorId()); }
                else { content = "(삭제된 댓글)"; }
            }
            case USER -> { author = nickname(report.getTargetId()); content = "(사용자 신고)"; }
            default -> content = "(" + report.getTargetType() + " 신고)";
        }
        long related = reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId());
        return ReportAdminDetailResponse.of(report, nickname(report.getReporterId()), content, author, related);
    }

    private Report load(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("(탈퇴)");
    }
}
