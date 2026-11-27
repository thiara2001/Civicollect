package com.civicollect.repository;

import com.civicollect.domain.Role;
import com.civicollect.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCaseOrPhone(String email, String phone);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    long countByRole(Role role);

    long countByEnabledTrue();

    @Query("select u.role, count(u) from User u group by u.role")
    List<Object[]> countByRoleGroup();
}
