package com.juhkang.artiv.domain.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorRequestRepository extends JpaRepository<CreatorRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, CreatorRequestStatus status);

    Optional<CreatorRequest> findTopByUserIdOrderByIdDesc(Long userId);

    Page<CreatorRequest> findByStatusOrderByIdDesc(CreatorRequestStatus status, Pageable pageable);

    Page<CreatorRequest> findAllByOrderByIdDesc(Pageable pageable);
}
