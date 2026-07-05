package com.juhkang.artiv.domain.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);
    List<ConversationMember> findByUserId(Long userId);
    List<ConversationMember> findByConversationIdIn(Collection<Long> conversationIds);
}
