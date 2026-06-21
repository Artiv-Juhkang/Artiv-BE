package com.juhkang.apptoon.domain.community;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.community.dto.PostCommentResponse;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostCommentResponse write(Long userId, Long postId, String content, Long parentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        Long effectiveParent = resolveParent(postId, parentId);
        PostComment saved = postCommentRepository.save(PostComment.create(postId, userId, effectiveParent, content));
        return new PostCommentResponse(saved.getId(), nickname(userId), saved.getContent(), saved.getCreatedAt(), List.of());
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

    /** 1-depth 강제: 대댓글에 대댓글이면 최상위 부모로 평탄화. */
    private Long resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PostComment parent = postCommentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!parent.getPostId().equals(postId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return parent.isReply() ? parent.getParentId() : parent.getId();
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
