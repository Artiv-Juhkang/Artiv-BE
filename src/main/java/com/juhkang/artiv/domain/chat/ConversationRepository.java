package com.juhkang.artiv.domain.chat;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByDirectKey(String directKey);
}
