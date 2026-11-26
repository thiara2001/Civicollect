package com.civicollect.repository;

import com.civicollect.domain.NeedHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NeedHistoryRepository extends JpaRepository<NeedHistory, Long> {
    @EntityGraph(attributePaths = "actor")
    List<NeedHistory> findByNeedIdOrderByCreatedAtAsc(Long needId);
}
