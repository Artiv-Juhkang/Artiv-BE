package com.juhkang.artiv.domain.community;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.community.dto.MyCommentResponse;
import com.juhkang.artiv.domain.community.dto.PostCommentResponse;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.domain.notification.NotificationService;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public PostCommentResponse write(Long userId, Long postId, String content, Long parentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        ParentRef parent = resolveParent(postId, parentId);
        Long effectiveParent = parent == null ? null : parent.effectiveParentId();
        PostComment saved = postCommentRepository.save(PostComment.create(postId, userId, effectiveParent, content));

        if (parent == null) {                                  // ① 최상위 댓글 → 글 작성자
            if (!post.getAuthorId().equals(userId)) {
                notificationService.fanOut(List.of(post.getAuthorId()), NotificationType.POST_COMMENT,
                        NotificationTargetType.POST, postId, userId, "새 댓글",
                        "회원님의 글 '" + post.getTitle() + "'에 새 댓글이 달렸어요.",
                        rid -> "POST_CMT:" + saved.getId());
            }
        } else if (!parent.parentAuthorId().equals(userId)) {  // ② 대댓글 → 부모 댓글 작성자
            notificationService.fanOut(List.of(parent.parentAuthorId()), NotificationType.COMMENT_REPLY,
                    NotificationTargetType.COMMENT, parent.effectiveParentId(), userId, "새 답글",
                    "회원님의 댓글에 답글이 달렸어요.", rid -> "CMT_REPLY:" + saved.getId());
        }
        notifyMentions(content, userId, postId, saved.getId());  // ④ 댓글 본문 멘션
        return new PostCommentResponse(saved.getId(), nickname(userId), saved.getContent(), saved.getCreatedAt(), List.of());
    }

    /** 댓글 본문 @닉네임 → 언급된 사용자에게 POST_MENTIONED(작성자 본인 제외). */
    private void notifyMentions(String content, Long actorId, Long postId, Long commentId) {
        Set<String> nicks = Mentions.extract(content);
        if (nicks.isEmpty()) {
            return;
        }
        List<Long> recipients = userRepository.findByNicknameIn(nicks).stream()
                .map(User::getId).filter(id -> !id.equals(actorId)).toList();
        notificationService.fanOut(recipients, NotificationType.POST_MENTIONED, NotificationTargetType.POST,
                postId, actorId, "멘션 알림", "회원님이 댓글에서 언급됐어요.",
                rid -> "CMT_MENTION:" + commentId + ":" + rid);
    }

    /** 내가 쓴 댓글·대댓글(블라인드 포함). 원글 제목/블라인드를 배치로 결합, 원글 삭제 시 행 스킵. */
    public PageResponse<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        Page<PostComment> page = postCommentRepository.findByAuthorIdOrderByIdDesc(userId, Pageables.pageOnly(pageable));
        List<Long> postIds = page.getContent().stream().map(PostComment::getPostId).distinct().toList();
        Map<Long, Post> postById = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<MyCommentResponse> content = page.getContent().stream()
                .map(c -> {
                    Post post = postById.get(c.getPostId());
                    return post == null ? null : new MyCommentResponse(c.getId(), c.getContent(), c.isBlinded(),
                            c.getCreatedAt(), post.getId(), post.getTitle(), post.isBlinded());
                })
                .filter(Objects::nonNull).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public List<PostCommentResponse> getComments(Long postId) {
        List<PostComment> all = postCommentRepository.findByPostIdAndBlindedFalseOrderByIdAsc(postId);
        Map<Long, String> names = userRepository.findAllById(all.stream().map(PostComment::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
        Map<Long, List<PostComment>> repliesByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(PostComment::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(top -> toResponse(top, names, repliesByParent.getOrDefault(top.getId(), List.of())))
                .toList();
    }

    @Transactional
    public void delete(Long userId, boolean isAdmin, Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!comment.isOwnedBy(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        postCommentRepository.delete(comment); // FK cascade로 대댓글도 정리
    }

    /** 평탄화된 부모 댓글 식별(1-depth 강제) + 그 작성자 id(대댓글 알림 수신자). */
    private record ParentRef(Long effectiveParentId, Long parentAuthorId) {
    }

    /** 1-depth 강제: 대댓글에 대댓글이면 최상위 부모로 평탄화. 최상위 부모의 작성자도 함께 반환. */
    private ParentRef resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PostComment parent = postCommentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!parent.getPostId().equals(postId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (parent.isReply()) {     // 대댓글에 단 답글 → 최상위 부모로 평탄화
            PostComment top = postCommentRepository.findById(parent.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
            return new ParentRef(top.getId(), top.getAuthorId());
        }
        return new ParentRef(parent.getId(), parent.getAuthorId());
    }

    private PostCommentResponse toResponse(PostComment c, Map<Long, String> names, List<PostComment> replies) {
        List<PostCommentResponse> replyDtos = replies.stream()
                .map(r -> new PostCommentResponse(r.getId(), names.getOrDefault(r.getAuthorId(), "(탈퇴)"),
                        r.getContent(), r.getCreatedAt(), List.of()))
                .toList();
        return new PostCommentResponse(c.getId(), names.getOrDefault(c.getAuthorId(), "(탈퇴)"),
                c.getContent(), c.getCreatedAt(), replyDtos);
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("(탈퇴)");
    }
}
