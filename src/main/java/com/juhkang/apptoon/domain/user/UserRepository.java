package com.juhkang.apptoon.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    List<User> findByNicknameIn(Collection<String> nicknames);   // 멘션 일괄 해석(1쿼리)

    @Query(value = "select u from User u "
            + "where (:keyword is null or lower(u.nickname) like lower(concat('%', cast(:keyword as string), '%')) "
            + "or lower(u.email) like lower(concat('%', cast(:keyword as string), '%'))) "
            + "and (:role is null or u.role = :role) order by u.id desc",
            countQuery = "select count(u) from User u "
                    + "where (:keyword is null or lower(u.nickname) like lower(concat('%', cast(:keyword as string), '%')) "
                    + "or lower(u.email) like lower(concat('%', cast(:keyword as string), '%'))) "
                    + "and (:role is null or u.role = :role)")
    Page<User> search(@Param("keyword") String keyword, @Param("role") Role role, Pageable pageable);
}
