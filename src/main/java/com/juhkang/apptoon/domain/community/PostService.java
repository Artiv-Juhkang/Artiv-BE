package com.juhkang.apptoon.domain.community;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.domain.community.dto.PostAdminResponse;
import com.juhkang.apptoon.domain.community.dto.PostDetailResponse;
import com.juhkang.apptoon.domain.community.dto.PostImageResponse;
import com.juhkang.apptoon.domain.community.dto.PostResponse;
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
public class PostService {

    private static final int MAX_IMAGES = 5;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public Long create(Long authorId, PostCategory category, String title, String content, List<MultipartFile> images) {
        if (category == null || title == null || title.isBlank() || title.strip().length() > 255
                || content == null || content.isBlank() || content.strip().length() > 5000
                || (images != null && images.size() > MAX_IMAGES)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Post post = postRepository.save(Post.create(authorId, category, title.strip(), content.strip()));
        if (images != null && !images.isEmpty()) {
            String prefix = "posts/" + post.getId() + "/" + UUID.randomUUID();
            List<ImageStorageService.Stored> stored = imageStorageService.store(prefix, images);
            for (int i = 0; i < stored.size(); i++) {
                ImageStorageService.Stored s = stored.get(i);
                postImageRepository.save(PostImage.create(post.getId(), i, s.path(), s.width(), s.height()));
            }
        }
        return post.getId();
    }

    public PageResponse<PostResponse> getList(PostCategory category, PostSort sort, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.toSort());
        Page<Post> page = postRepository.findVisible(category, sorted);
        Map<Long, String> names = nicknames(page.getContent());
        return PageResponse.from(page.map(p -> new PostResponse(p.getId(), p.getCategory(), p.getTitle(),
                names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.getCreatedAt())));
    }

    public PostDetailResponse getDetail(Long id, Long viewerId, boolean isAdmin) {
        Post post = load(id);
        if (post.isBlinded() && !isAdmin) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND); // 블라인드는 존재를 숨김
        }
        List<PostImageResponse> images = postImageRepository.findByPostIdOrderBySortOrderAsc(id).stream()
                .map(im -> new PostImageResponse(imageStorageService.urlFor(im.getPath()),
                        im.getWidth(), im.getHeight(), im.getSortOrder()))
                .toList();
        boolean liked = postLikeRepository.existsByUserIdAndPostId(viewerId, id);
        return new PostDetailResponse(post.getId(), post.getCategory(), post.getTitle(), post.getContent(),
                nickname(post.getAuthorId()), post.getLikeCount(), liked, images, post.getCreatedAt());
    }

    @Transactional
    public void delete(Long userId, boolean isAdmin, Long id) {
        Post post = load(id);
        if (!post.isOwnedBy(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        postImageRepository.findByPostIdOrderBySortOrderAsc(id)
                .forEach(im -> imageStorageService.delete(im.getPath()));
        postRepository.delete(post); // FK cascade: 이미지/추천/댓글 행 정리
    }

    @Transactional
    public void like(Long userId, Long id) {
        Post post = load(id);
        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        if (postLikeRepository.existsByUserIdAndPostId(userId, id)) {
            return; // 멱등
        }
        postLikeRepository.save(PostLike.create(userId, id));
        post.increaseLike();
    }

    @Transactional
    public void unlike(Long userId, Long id) {
        Post post = load(id);
        if (postLikeRepository.existsByUserIdAndPostId(userId, id)) {
            postLikeRepository.deleteByUserIdAndPostId(userId, id);
            post.decreaseLike();
        }
    }

    // ---- 관리자 ----
    public PageResponse<PostAdminResponse> getForAdmin(Pageable pageable) {
        Page<Post> page = postRepository.findAllByOrderByIdDesc(pageable);
        Map<Long, String> names = nicknames(page.getContent());
        return PageResponse.from(page.map(p -> new PostAdminResponse(p.getId(), p.getCategory(), p.getTitle(),
                names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.isBlinded(), p.getCreatedAt())));
    }

    @Transactional
    public void setBlinded(Long id, boolean blinded) {
        Post post = load(id);
        if (blinded) {
            post.blind();
        } else {
            post.unblind();
        }
    }

    private Post load(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("(탈퇴)");
    }

    private Map<Long, String> nicknames(List<Post> posts) {
        List<Long> ids = posts.stream().map(Post::getAuthorId).distinct().toList();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }
}
