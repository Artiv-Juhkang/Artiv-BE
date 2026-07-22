package com.juhkang.artiv.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.community.PostCommentService;
import com.juhkang.artiv.domain.community.PostService;
import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeService;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.follow.FollowService;
import com.juhkang.artiv.domain.personalization.Subscription;
import com.juhkang.artiv.domain.personalization.SubscriptionRepository;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class NotificationTriggerTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private PostService postService;
    @Autowired private PostCommentService postCommentService;
    @Autowired private FollowService followService;
    @Autowired private EpisodeService episodeService;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long user(String nickname, Role role) {
        return userRepository.save(User.create(nickname + "@trg.com", passwordEncoder.encode("password123"), nickname, role, ADULT)).getId();
    }

    private List<Notification> notis(Long uid) {
        return notificationRepository.findByRecipientIdOrderByIdDesc(uid, PageRequest.of(0, 50)).getContent();
    }

    // ① POST_COMMENT
    @Test
    void 타인이_내_글에_댓글을_달면_글쓴이에게_알림() {
        Long author = user("작가A", Role.CREATOR);
        Long commenter = user("독자A", Role.READER);
        Long postId = postService.create(author, "자유", "제목", "내용", null);

        postCommentService.write(commenter, postId, "잘 봤어요", null);

        List<Notification> n = notis(author);
        assertThat(n).hasSize(1);
        assertThat(n.get(0).getType()).isEqualTo(NotificationType.POST_COMMENT);
        assertThat(n.get(0).getTargetType()).isEqualTo(NotificationTargetType.POST);
        assertThat(n.get(0).getTargetId()).isEqualTo(postId);
        assertThat(n.get(0).getActorId()).isEqualTo(commenter);
    }

    @Test
    void 내_글에_내가_댓글을_달면_알림_없음() {
        Long author = user("작가B", Role.CREATOR);
        Long postId = postService.create(author, "자유", "제목", "내용", null);
        postCommentService.write(author, postId, "자답", null);
        assertThat(notis(author)).isEmpty();
    }

    // ② COMMENT_REPLY
    @Test
    void 내_댓글에_답글이_달리면_부모_댓글_작성자에게_알림() {
        Long author = user("작가C", Role.CREATOR);
        Long parentWriter = user("독자C", Role.READER);
        Long replier = user("독자C2", Role.READER);
        Long postId = postService.create(author, "자유", "제목", "내용", null);
        var parent = postCommentService.write(parentWriter, postId, "원댓글", null);

        postCommentService.write(replier, postId, "답글", parent.id());

        List<Notification> n = notis(parentWriter);
        assertThat(n).hasSize(1);
        assertThat(n.get(0).getType()).isEqualTo(NotificationType.COMMENT_REPLY);
        // 알림 목적지는 원글(POST)·postId — 프론트가 게시글 상세로 딥링크할 수 있게(F7).
        // 댓글 id를 targetId로 두면 원글을 알 수 없어 라우팅이 데드엔드가 된다.
        assertThat(n.get(0).getTargetType()).isEqualTo(NotificationTargetType.POST);
        assertThat(n.get(0).getTargetId()).isEqualTo(postId);
    }

    // ③ FOLLOWED
    @Test
    void 팔로우하면_상대에게_알림_재팔로우는_멱등() {
        Long follower = user("팔로워D", Role.READER);
        Long target = user("작가D", Role.CREATOR);

        followService.follow(follower, target);
        followService.follow(follower, target); // 멱등

        List<Notification> n = notis(target);
        assertThat(n).hasSize(1);
        assertThat(n.get(0).getType()).isEqualTo(NotificationType.FOLLOWED);
        assertThat(n.get(0).getTargetType()).isEqualTo(NotificationTargetType.USER);
        assertThat(n.get(0).getTargetId()).isEqualTo(follower);
    }

    // ④ POST_MENTIONED
    @Test
    void 게시글_본문에서_멘션하면_언급된_사용자에게_알림() {
        Long author = user("작가E", Role.CREATOR);
        Long mentioned = user("멘션이", Role.READER);
        postService.create(author, "자유", "제목", "안녕 @멘션이 이거 봐줘", null);

        List<Notification> n = notis(mentioned);
        assertThat(n).hasSize(1);
        assertThat(n.get(0).getType()).isEqualTo(NotificationType.POST_MENTIONED);
        assertThat(n.get(0).getActorId()).isEqualTo(author);
    }

    @Test
    void 댓글_본문에서_멘션하면_언급된_사용자에게_알림() {
        Long author = user("작가F", Role.CREATOR);
        Long commenter = user("독자F", Role.READER);
        Long mentioned = user("멘션F", Role.READER);
        Long postId = postService.create(author, "자유", "제목", "내용", null);

        postCommentService.write(commenter, postId, "@멘션F 이거 봤어?", null);

        List<Notification> n = notis(mentioned);
        assertThat(n).hasSize(1);
        assertThat(n.get(0).getType()).isEqualTo(NotificationType.POST_MENTIONED);
        assertThat(n.get(0).getTargetType()).isEqualTo(NotificationTargetType.POST);
        assertThat(n.get(0).getTargetId()).isEqualTo(postId);
    }

    @Test
    void NFD로_분해된_한글_멘션도_NFC_닉네임에_도달한다() {
        Long author = user("작가NFD", Role.CREATOR);
        Long mentioned = user("철수정", Role.READER);  // 소스 리터럴은 NFC로 저장됨
        String nfd = Normalizer.normalize("좋아요 @철수정 봐줘", Normalizer.Form.NFD);  // 본문은 NFD(분해형)
        postService.create(author, "자유", "제목", nfd, null);

        assertThat(notis(mentioned)).hasSize(1);
        assertThat(notis(mentioned).get(0).getType()).isEqualTo(NotificationType.POST_MENTIONED);
    }

    @Test
    void 존재하지_않는_닉네임_멘션은_무시되고_자기_멘션은_제외() {
        Long author = user("작가G", Role.CREATOR);
        postService.create(author, "자유", "제목", "@없는닉네임999 @작가G 본인멘션", null);
        assertThat(notis(author)).isEmpty(); // 자기 멘션 제외 + 미존재 닉 무시
    }

    // ⑤ EPISODE_PUBLISHED
    @Test
    void 예약회차가_발행되면_구독자에게_알림_작가는_제외() {
        Long authorId = user("연재작가", Role.CREATOR);
        Long subscriberId = user("구독자H", Role.READER);
        Long nonSubId = user("비구독H", Role.READER);
        User author = userRepository.findById(authorId).orElseThrow();
        User subscriber = userRepository.findById(subscriberId).orElseThrow();

        Series series = seriesRepository.save(Series.create("연재작", "설명", author,
                AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        subscriptionRepository.save(Subscription.create(subscriber, series));
        episodeRepository.save(Episode.create(series, 1, "1화",
                EpisodeStatus.SCHEDULED, Instant.now().minusSeconds(60)));

        episodeService.publishDueEpisodes(Instant.now());

        List<Notification> sub = notis(subscriberId);
        assertThat(sub).hasSize(1);
        assertThat(sub.get(0).getType()).isEqualTo(NotificationType.EPISODE_PUBLISHED);
        // 목적지는 작품 상세(SERIES) — 프론트가 상세로 딥링크해 새 회차를 바로 볼 수 있다.
        assertThat(sub.get(0).getTargetType()).isEqualTo(NotificationTargetType.SERIES);
        assertThat(sub.get(0).getTargetId()).isEqualTo(series.getId());
        assertThat(notis(nonSubId)).isEmpty();
        assertThat(notis(authorId)).isEmpty(); // 작가 자신 제외(구독 안 함)
    }
}
