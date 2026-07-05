package com.juhkang.artiv.domain.chat;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 대화 메시지(최신순 고정 — 클라 sort 미신뢰, Pageables.pageOnly와 함께 사용). */
    @Query(value = "select m from Message m where m.conversationId = :conversationId order by m.id desc",
            countQuery = "select count(m) from Message m where m.conversationId = :conversationId")
    Page<Message> findPageByConversationId(@Param("conversationId") Long conversationId, Pageable pageable);

    /** 대화별 마지막 메시지 1건씩(인박스 미리보기 배치 — N+1 없음). */
    @Query("select m from Message m where m.id in "
            + "(select max(m2.id) from Message m2 where m2.conversationId in :ids group by m2.conversationId)")
    List<Message> findLatestByConversationIds(@Param("ids") Collection<Long> ids);

    /** 대화별 미읽음 수(본인 발신 제외, high-water-mark 초과분). */
    @Query("select m.conversationId, count(m) from Message m, ConversationMember cm "
            + "where cm.conversationId = m.conversationId and cm.userId = :userId "
            + "and m.conversationId in :ids and m.senderId <> :userId "
            + "and (cm.lastReadMessageId is null or m.id > cm.lastReadMessageId) "
            + "group by m.conversationId")
    List<Object[]> countUnreadByConversationIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    /** 전체 미읽음(탭 뱃지 폴링) — DECLINED 대화 제외. */
    @Query("select count(m) from Message m, ConversationMember cm, Conversation c "
            + "where cm.conversationId = m.conversationId and cm.userId = :userId "
            + "and c.id = m.conversationId and c.status <> com.juhkang.artiv.domain.chat.ConversationStatus.DECLINED "
            + "and m.senderId <> :userId "
            + "and (cm.lastReadMessageId is null or m.id > cm.lastReadMessageId)")
    long countUnreadTotal(@Param("userId") Long userId);
}
