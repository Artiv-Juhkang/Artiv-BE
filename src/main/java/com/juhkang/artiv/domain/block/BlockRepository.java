package com.juhkang.artiv.domain.block;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juhkang.artiv.domain.user.User;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("select u from Block b, User u where u.id = b.blockedId and b.blockerId = :uid order by b.id desc")
    List<User> findBlockedUsers(@Param("uid") Long uid);
}
