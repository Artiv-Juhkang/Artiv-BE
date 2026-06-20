package com.juhkang.apptoon.domain.inquiry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryImageRepository extends JpaRepository<InquiryImage, Long> {

    List<InquiryImage> findByInquiryIdOrderBySortOrderAsc(Long inquiryId);

    void deleteByInquiryId(Long inquiryId);
}
