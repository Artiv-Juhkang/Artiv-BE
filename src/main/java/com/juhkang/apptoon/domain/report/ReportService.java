package com.juhkang.apptoon.domain.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.community.Post;
import com.juhkang.apptoon.domain.community.PostComment;
import com.juhkang.apptoon.domain.community.PostCommentRepository;
import com.juhkang.apptoon.domain.community.PostRepository;
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

    @Transactional
    public void create(Long reporterId, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 같은 대상 중복 신고 불가
        }
        reportRepository.save(Report.create(reporterId, targetType, targetId, reason, detail));
        autoBlind(targetType, targetId);
    }

    /** 한 대상에 접수(PENDING) 신고가 임계치 이상이면 자동 블라인드. */
    private void autoBlind(ReportTargetType targetType, Long targetId) {
        if (targetType != ReportTargetType.POST && targetType != ReportTargetType.COMMENT) {
            return;
        }
        long pending = reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING);
        if (pending < AUTO_BLIND_THRESHOLD) {
            return;
        }
        if (targetType == ReportTargetType.POST) {
            postRepository.findById(targetId).ifPresent(Post::blind);
        } else {
            postCommentRepository.findById(targetId).ifPresent(PostComment::blind);
        }
    }

    public PageResponse<ReportAdminResponse> getForAdmin(ReportStatus status, Pageable pageable) {
        Page<Report> page = (status == null)
                ? reportRepository.findAllByOrderByIdDesc(pageable)
                : reportRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(r -> ReportAdminResponse.of(r, nickname(r.getReporterId()))));
    }

    @Transactional
    public ReportAdminResponse resolve(Long adminId, Long reportId) {
        Report report = load(reportId);
        report.resolve(adminId);
        return ReportAdminResponse.of(report, nickname(report.getReporterId()));
    }

    @Transactional
    public ReportAdminResponse dismiss(Long adminId, Long reportId) {
        Report report = load(reportId);
        report.dismiss(adminId);
        return ReportAdminResponse.of(report, nickname(report.getReporterId()));
    }

    private Report load(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("(탈퇴)");
    }
}
