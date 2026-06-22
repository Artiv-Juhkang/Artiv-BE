package com.juhkang.apptoon.domain.community;

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

import com.juhkang.apptoon.domain.community.dto.PostAdminDetailResponse;
import com.juhkang.apptoon.domain.community.dto.PostAdminResponse;
import com.juhkang.apptoon.domain.community.dto.PostDetailResponse;
import com.juhkang.apptoon.domain.community.dto.MyPostResponse;
import com.juhkang.apptoon.domain.community.dto.PostImageResponse;
import com.juhkang.apptoon.domain.community.dto.PostResponse;
import com.juhkang.apptoon.domain.notification.NotificationService;
import com.juhkang.apptoon.domain.notification.NotificationTargetType;
import com.juhkang.apptoon.domain.notification.NotificationType;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.dto.Pageables;
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
    private final NotificationService notificationService;

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
    public PageResponse<PostAdminResponse> getForAdmin(PostCategory category, String keyword, Boolean blinded, Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.strip() : null;
        Page<Post> page = postRepository.findForAdmin(category, kw, blinded, pageable);
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
                .map(p -> new PostResponse(p.getId(), p.getCategory(), p.getTitle(),
                        names.getOrDefault(p.getAuthorId(), "(탈퇴)"), p.getLikeCount(), p.getCreatedAt()))
                .toList();
        return new PageResponse<>(content, likes.getNumber(), likes.getSize(),
                likes.getTotalElements(), likes.getTotalPages(), likes.isLast());
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
