package com.juhkang.artiv.domain.community;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.community.dto.PostAdminDetailResponse;
import com.juhkang.artiv.domain.community.dto.PostAdminResponse;
import com.juhkang.artiv.domain.community.dto.PostDetailResponse;
import com.juhkang.artiv.domain.community.dto.MyPostResponse;
import com.juhkang.artiv.domain.community.dto.PostImageResponse;
import com.juhkang.artiv.domain.community.dto.PostResponse;
import com.juhkang.artiv.domain.notification.NotificationService;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;
import com.juhkang.artiv.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int MAX_IMAGES = 5;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostDislikeRepository postDislikeRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final NotificationService notificationService;

    @Transactional
    public Long create(Long authorId, String category, String title, String content, List<MultipartFile> images) {
        if (category == null || !postCategoryRepository.existsByName(category)
                || title == null || title.isBlank() || title.strip().length() > 255
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
        notifyMentions(content.strip(), authorId, post.getId(),
                "회원님이 게시글 '" + post.getTitle() + "'에서 언급됐어요.", "POST_MENTION:" + post.getId());
        return post.getId();
    }

    /** 본문 @닉네임 → 언급된 사용자에게 POST_MENTIONED(작성자 본인 제외). dedupBase는 (글/댓글) 고유 prefix. */
    private void notifyMentions(String content, Long actorId, Long postId, String message, String dedupBase) {
        Set<String> nicks = Mentions.extract(content);
        if (nicks.isEmpty()) {
            return;
        }
        List<Long> recipients = userRepository.findByNicknameIn(nicks).stream()
                .map(User::getId).filter(id -> !id.equals(actorId)).toList();
        notificationService.fanOut(recipients, NotificationType.POST_MENTIONED, NotificationTargetType.POST,
                postId, actorId, "멘션 알림", message, rid -> dedupBase + ":" + rid);
    }

    public PageResponse<PostResponse> getList(String category, PostSort sort, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.toSort());
        Page<Post> page = postRepository.findVisible(category, sorted);
        Map<Long, String> names = nicknames(page.getContent());
        return PageResponse.from(page.map(p -> new PostResponse(p.getId(), p.getAuthorId(), p.getCategory(), p.getTitle(),
                names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.getDislikeCount(), p.getCreatedAt())));
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
        boolean disliked = postDislikeRepository.existsByUserIdAndPostId(viewerId, id);
        return new PostDetailResponse(post.getId(), post.getAuthorId(), post.getCategory(), post.getTitle(), post.getContent(),
                nickname(post.getAuthorId()), post.getLikeCount(), liked, post.getDislikeCount(), disliked, images, post.getCreatedAt());
    }

    /**
     * 게시글 수정 — 작성자 전용(관리자는 블라인드로 모더레이션, 삭제와 달리 수정 권한 없음).
     * 검증은 작성과 동일 규칙(텍스트만). 멘션 재발송은 dedupKey(POST_MENTION:{postId}:{rid})가
     * 기발송 수신자를 억제하므로 새로 추가된 멘션에만 알림이 간다.
     */
    @Transactional
    public void update(Long userId, Long id, String category, String title, String content) {
        if (category == null || !postCategoryRepository.existsByName(category)
                || title == null || title.isBlank() || title.strip().length() > 255
                || content == null || content.isBlank() || content.strip().length() > 5000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Post post = load(id);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.update(category, title.strip(), content.strip());
        notifyMentions(content.strip(), userId, post.getId(),
                "회원님이 게시글 '" + post.getTitle() + "'에서 언급됐어요.", "POST_MENTION:" + post.getId());
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
        if (postDislikeRepository.existsByUserIdAndPostId(userId, id)) {
            postDislikeRepository.deleteByUserIdAndPostId(userId, id); // 추천↔비추천 상호배타
            post.decreaseDislike();
        }
        postLikeRepository.save(PostLike.create(userId, id));
        post.increaseLike();
    }

    /** 비추천(멱등) — 추천 중이었다면 자동 해제(상호배타). */
    @Transactional
    public void dislike(Long userId, Long id) {
        Post post = load(id);
        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        if (postDislikeRepository.existsByUserIdAndPostId(userId, id)) {
            return; // 멱등
        }
        if (postLikeRepository.existsByUserIdAndPostId(userId, id)) {
            postLikeRepository.deleteByUserIdAndPostId(userId, id);
            post.decreaseLike();
        }
        postDislikeRepository.save(PostDislike.create(userId, id));
        post.increaseDislike();
    }

    /** 비추천 취소(멱등). */
    @Transactional
    public void undislike(Long userId, Long id) {
        Post post = load(id);
        if (postDislikeRepository.existsByUserIdAndPostId(userId, id)) {
            postDislikeRepository.deleteByUserIdAndPostId(userId, id);
            post.decreaseDislike();
        }
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
    public PageResponse<PostAdminResponse> getForAdmin(String category, String keyword, Boolean blinded, Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.strip() : null;
        Page<Post> page = postRepository.findForAdmin(category, kw, blinded, Pageables.pageOnly(pageable));
        Map<Long, String> names = nicknames(page.getContent());
        return PageResponse.from(page.map(p -> new PostAdminResponse(p.getId(), p.getCategory(), p.getTitle(),
                names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.isBlinded(), p.getCreatedAt())));
    }

    public PostAdminDetailResponse getAdminDetail(Long id) {
        Post post = load(id);
        List<PostImageResponse> images = postImageRepository.findByPostIdOrderBySortOrderAsc(id).stream()
                .map(im -> new PostImageResponse(imageStorageService.urlFor(im.getPath()),
                        im.getWidth(), im.getHeight(), im.getSortOrder()))
                .toList();
        return new PostAdminDetailResponse(post.getId(), post.getCategory(), post.getTitle(), post.getContent(),
                nickname(post.getAuthorId()), post.getLikeCount(), post.isBlinded(), post.getBlindedAt(),
                images, post.getCreatedAt());
    }

    @Transactional
    public void setBlinded(Long adminId, Long id, boolean blinded) {
        Post post = load(id);
        if (blinded) {
            post.blind(adminId);
        } else {
            post.unblind();
        }
    }

    /** 내가 쓴 글(블라인드 포함, 본인 것만 — userId 외 입력 없음 IDOR 안전). 정렬 고정(클라 sort 무시). */
    public PageResponse<MyPostResponse> getMyPosts(Long userId, Pageable pageable) {
        return PageResponse.from(postRepository.findByAuthorIdOrderByIdDesc(userId, Pageables.pageOnly(pageable)).map(MyPostResponse::of));
    }

    /** 내가 추천한 글(좋아요 시점순). 삭제·블라인드 글 제외, 좋아요 순서 보존, 닉네임 배치. 정렬 고정(클라 sort 무시). */
    public PageResponse<PostResponse> getMyLikedPosts(Long userId, Pageable pageable) {
        Page<PostLike> likes = postLikeRepository.findByUserIdOrderByIdDesc(userId, Pageables.pageOnly(pageable));
        List<Long> postIds = likes.getContent().stream().map(PostLike::getPostId).toList();
        Map<Long, Post> postById = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> posts = postIds.stream().map(postById::get)
                .filter(p -> p != null && !p.isBlinded()).toList();   // 삭제·블라인드 제외, 좋아요순 보존
        Map<Long, String> names = nicknames(posts);
        List<PostResponse> content = posts.stream()
                .map(p -> new PostResponse(p.getId(), p.getAuthorId(), p.getCategory(), p.getTitle(),
                        names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.getDislikeCount(), p.getCreatedAt()))
                .toList();
        return new PageResponse<>(content, likes.getNumber(), likes.getSize(),
                likes.getTotalElements(), likes.getTotalPages(), likes.isLast());
    }

    /** 작가 소식 피드 — 팔로우한 사용자들의 공개 글(최신순). 정렬 고정. */
    public PageResponse<PostResponse> getAuthorNewsFeed(Long userId, Pageable pageable) {
        return toPostResponses(postRepository.findFeedByFollower(userId, Pageables.pageOnly(pageable)));
    }

    /** 특정 작가의 공개 글(블라인드 제외, 최신순). */
    public PageResponse<PostResponse> getAuthorPosts(Long authorId, Pageable pageable) {
        return toPostResponses(postRepository.findByAuthorIdAndBlindedFalseOrderByIdDesc(authorId, Pageables.pageOnly(pageable)));
    }

    /** Page<Post> → PageResponse<PostResponse>(작성자 닉네임 배치, N+1 없음). */
    private PageResponse<PostResponse> toPostResponses(Page<Post> page) {
        Map<Long, String> names = nicknames(page.getContent());
        return PageResponse.from(page.map(p -> new PostResponse(p.getId(), p.getAuthorId(), p.getCategory(), p.getTitle(),
                names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.getDislikeCount(), p.getCreatedAt())));
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
