package com.juhkang.apptoon.domain.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.comment.dto.CommentResponse;
import com.juhkang.apptoon.domain.episode.Episode;
import com.juhkang.apptoon.domain.episode.EpisodeRepository;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final EpisodeRepository episodeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long write(Long userId, Long seriesId, int episodeNo, String content) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        User user = userRepository.getReferenceById(userId);
        return commentRepository.save(Comment.create(user, episode, content)).getId();
    }

    public PageResponse<CommentResponse> getComments(Long seriesId, int episodeNo, Pageable pageable) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Page<CommentResponse> page = commentRepository.findByEpisodeId(episode.getId(), pageable)
                .map(CommentResponse::of);
        return PageResponse.from(page);
    }

    @Transactional
    public void delete(Long userId, boolean isAdmin, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 삭제 권한: 작성자 본인 또는 ADMIN
        if (!comment.isOwnedBy(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        commentRepository.delete(comment);
    }
}
