package com.juhkang.artiv.domain.comment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.comment.dto.CommentResponse;
import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.access.EpisodeAccessGuard;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final EpisodeRepository episodeRepository;
    private final UserRepository userRepository;
    private final EpisodeAccessGuard episodeAccessGuard;

    @Transactional
    public Long write(Long userId, Long seriesId, int episodeNo, String content, Long parentId) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 회차 상세와 동일한 접근 가드 — 비공개·19금·미발행·잠긴 회차 댓글 작성 차단(F5).
        episodeAccessGuard.verifyInteractable(episode.getSeries(), episode, userId);
        User user = userRepository.getReferenceById(userId);
        Long resolvedParentId = resolveParentId(parentId, episode.getId());
        return commentRepository.save(Comment.create(user, episode, content, resolvedParentId)).getId();
    }

    /** 대댓글 부모를 검증·정규화한다. 대댓글에 다는 답글은 최상위 부모로 평탄화(1-depth 유지). */
    private Long resolveParentId(Long parentId, Long episodeId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!parent.getEpisode().getId().equals(episodeId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return parent.isReply() ? parent.getParentId() : parent.getId();
    }

    public PageResponse<CommentResponse> getComments(Long seriesId, int episodeNo, Long viewerId, Pageable pageable) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 회차 상세와 동일한 접근 가드 — 열람 불가한 회차의 댓글은 목록도 막는다(F5).
        episodeAccessGuard.verifyInteractable(episode.getSeries(), episode, viewerId);

        Page<Comment> parents = commentRepository.findByEpisodeIdAndParentIdIsNull(episode.getId(),
                Pageables.pageOnly(pageable));
        List<Long> parentIds = parents.getContent().stream().map(Comment::getId).toList();
        List<Comment> replies = parentIds.isEmpty()
                ? List.of()
                : commentRepository.findByParentIdInOrderByIdAsc(parentIds);

        // 페이지에 등장하는 모든 댓글/대댓글의 좋아요를 한 번에 집계(N+1 회피).
        List<Long> allIds = new ArrayList<>(parentIds);
        replies.forEach(r -> allIds.add(r.getId()));
        List<CommentLike> likes = allIds.isEmpty()
                ? List.of()
                : commentLikeRepository.findByCommentIdIn(allIds);
        Map<Long, Long> likeCounts = likes.stream()
                .collect(Collectors.groupingBy(CommentLike::getCommentId, Collectors.counting()));
        Set<Long> likedByViewer = likes.stream()
                .filter(l -> l.getUserId().equals(viewerId))
                .map(CommentLike::getCommentId)
                .collect(Collectors.toSet());

        Map<Long, List<CommentResponse>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId, LinkedHashMap::new,
                        Collectors.mapping(r -> toResponse(r, likeCounts, likedByViewer, List.of()),
                                Collectors.toList())));

        Page<CommentResponse> page = parents.map(c ->
                toResponse(c, likeCounts, likedByViewer, repliesByParent.getOrDefault(c.getId(), List.of())));
        return PageResponse.from(page);
    }

    private CommentResponse toResponse(Comment c, Map<Long, Long> likeCounts, Set<Long> likedByViewer,
                                       List<CommentResponse> replies) {
        return CommentResponse.of(c, likeCounts.getOrDefault(c.getId(), 0L), likedByViewer.contains(c.getId()), replies);
    }

    @Transactional
    public void like(Long userId, Long seriesId, int episodeNo, Long commentId) {
        Comment comment = loadInEpisode(commentId, seriesId, episodeNo);
        if (commentLikeRepository.existsByUserIdAndCommentId(userId, comment.getId())) {
            return; // 멱등
        }
        commentLikeRepository.save(CommentLike.create(userId, comment.getId()));
    }

    @Transactional
    public void unlike(Long userId, Long seriesId, int episodeNo, Long commentId) {
        Comment comment = loadInEpisode(commentId, seriesId, episodeNo);
        commentLikeRepository.deleteByUserIdAndCommentId(userId, comment.getId());
    }

    /** 경로의 seriesId·episodeNo에 실제로 속한 댓글만 로드(다른 회차 경로로 위조 시 404 — F17). */
    private Comment loadInEpisode(Long commentId, Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!comment.getEpisode().getId().equals(episode.getId())) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        return comment;
    }

    @Transactional
    public void delete(Long userId, boolean isAdmin, Long seriesId, int episodeNo, Long commentId) {
        Comment comment = loadInEpisode(commentId, seriesId, episodeNo);
        // 삭제 권한: 작성자 본인 또는 ADMIN
        if (!comment.isOwnedBy(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 최상위 댓글이면 대댓글도 함께 제거. 좋아요는 FK(comment_id) 때문에 먼저 지운다.
        List<Comment> replies = comment.isReply()
                ? List.of()
                : commentRepository.findByParentIdInOrderByIdAsc(List.of(commentId));
        List<Long> ids = new ArrayList<>();
        ids.add(commentId);
        replies.forEach(r -> ids.add(r.getId()));
        commentLikeRepository.deleteByCommentIdIn(ids);
        commentRepository.deleteAll(replies);
        commentRepository.delete(comment);
    }
}
