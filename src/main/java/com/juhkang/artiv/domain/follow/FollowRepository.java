package com.juhkang.artiv.domain.follow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juhkang.artiv.domain.user.User;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowingId(Long followingId); // 팔로워 수

    long countByFollowerId(Long followerId);   // 팔로잉 수

    /** 내가 팔로우하는 사용자들. */
    @Query("select u from Follow f, User u where u.id = f.followingId and f.followerId = :uid order by f.id desc")
    List<User> findFollowingUsers(@Param("uid") Long uid);

    /** 나를 팔로우하는 사용자들. */
    @Query("select u from Follow f, User u where u.id = f.followerId and f.followingId = :uid order by f.id desc")
    List<User> findFollowerUsers(@Param("uid") Long uid);

    /** 친구(상호 팔로우) — 내가 팔로우하고 나를도 팔로우하는 사용자(자가 조인, CH4). */
    @Query("select u from Follow f1, Follow f2, User u "
            + "where f1.followerId = :uid and f1.followingId = u.id "
            + "and f2.followerId = u.id and f2.followingId = :uid "
            + "order by u.nickname asc")
    List<User> findMutualFollows(@Param("uid") Long uid);
}
