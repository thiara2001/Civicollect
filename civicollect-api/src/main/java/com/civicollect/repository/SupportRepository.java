package com.civicollect.repository;

import com.civicollect.domain.Support;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportRepository extends JpaRepository<Support, Long> {

    boolean existsByUserIdAndNeedId(Long userId, Long needId);

    Optional<Support> findByUserIdAndNeedId(Long userId, Long needId);

    long countByNeedId(Long needId);

    @Query("select s.need.id, count(s) from Support s where s.need.id in :ids group by s.need.id")
    List<Object[]> countByNeedIds(@Param("ids") Collection<Long> ids);

    @Query("select s.need.id from Support s where s.user.id = :userId and s.need.id in :ids")
    List<Long> findSupportedNeedIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
