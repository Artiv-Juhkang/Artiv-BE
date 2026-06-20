package com.juhkang.apptoon.domain.inquiry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.domain.inquiry.dto.InquiryAdminDetailResponse;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryAdminResponse;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryDetailResponse;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryImageResponse;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryResponse;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;
import com.juhkang.apptoon.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private static final int MAX_IMAGES = 5;
    private static final int MAX_TITLE = 255;
    private static final int MAX_CONTENT = 5000;

    private final InquiryRepository inquiryRepository;
    private final InquiryImageRepository inquiryImageRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    // ---- 사용자 ----

    @Transactional
    public Long create(Long userId, InquiryType type, String title, String content, List<MultipartFile> images) {
        validate(type, title, content, images);
        User user = userRepository.getReferenceById(userId);
        Inquiry inquiry = inquiryRepository.save(Inquiry.create(user, type, title.strip(), content.strip()));
        if (images != null && !images.isEmpty()) {
            String prefix = "inquiries/" + inquiry.getId() + "/" + UUID.randomUUID();
            List<ImageStorageService.Stored> stored = imageStorageService.store(prefix, images);
            for (int order = 0; order < stored.size(); order++) {
                ImageStorageService.Stored s = stored.get(order);
                inquiryImageRepository.save(InquiryImage.create(inquiry, order, s.path(), s.width(), s.height()));
            }
        }
        return inquiry.getId();
    }

    public PageResponse<InquiryResponse> getMine(Long userId, Pageable pageable) {
        return PageResponse.from(inquiryRepository.findByUserId(userId, pageable).map(InquiryResponse::of));
    }

    public InquiryDetailResponse getMineDetail(Long userId, Long inquiryId) {
        Inquiry inquiry = loadById(inquiryId);
        if (!inquiry.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return InquiryDetailResponse.of(inquiry, imagesOf(inquiryId));
    }

    @Transactional
    public void deleteMine(Long userId, boolean isAdmin, Long inquiryId) {
        Inquiry inquiry = loadById(inquiryId);
        if (!inquiry.isOwnedBy(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        deleteWithImages(inquiry);
    }

    // ---- 관리자 ----

    public PageResponse<InquiryAdminResponse> getForAdmin(InquiryType type, InquiryStatus status, Pageable pageable) {
        return PageResponse.from(inquiryRepository.findForAdmin(type, status, pageable).map(InquiryAdminResponse::of));
    }

    public InquiryAdminDetailResponse getAdminDetail(Long inquiryId) {
        return adminDetail(loadById(inquiryId));
    }

    @Transactional
    public InquiryAdminDetailResponse answer(Long inquiryId, String answer) {
        Inquiry inquiry = loadById(inquiryId);
        inquiry.answer(answer);
        return adminDetail(inquiry);
    }

    @Transactional
    public InquiryAdminDetailResponse changeStatus(Long inquiryId, InquiryStatus status) {
        Inquiry inquiry = loadById(inquiryId);
        inquiry.changeStatus(status);
        return adminDetail(inquiry);
    }

    @Transactional
    public void deleteByAdmin(Long inquiryId) {
        deleteWithImages(loadById(inquiryId));
    }

    // ---- 내부 ----

    private void validate(InquiryType type, String title, String content, List<MultipartFile> images) {
        if (type == null
                || title == null || title.isBlank() || title.strip().length() > MAX_TITLE
                || content == null || content.isBlank() || content.strip().length() > MAX_CONTENT
                || (images != null && images.size() > MAX_IMAGES)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Inquiry loadById(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private void deleteWithImages(Inquiry inquiry) {
        inquiryImageRepository.deleteByInquiryId(inquiry.getId());
        inquiryRepository.delete(inquiry);
    }

    private InquiryAdminDetailResponse adminDetail(Inquiry inquiry) {
        return InquiryAdminDetailResponse.of(inquiry, imagesOf(inquiry.getId()));
    }

    private List<InquiryImageResponse> imagesOf(Long inquiryId) {
        return inquiryImageRepository.findByInquiryIdOrderBySortOrderAsc(inquiryId).stream()
                .map(img -> new InquiryImageResponse(
                        imageStorageService.urlFor(img.getPath()),
                        img.getWidth(), img.getHeight(), img.getSortOrder()))
                .toList();
    }
}
