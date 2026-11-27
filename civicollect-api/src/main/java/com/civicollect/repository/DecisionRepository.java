package com.civicollect.repository;

import com.civicollect.domain.Decision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionRepository extends JpaRepository<Decision, Long> {
    @EntityGraph(attributePaths = "decider")
    List<Decision> findByNeedIdOrderByCreatedAtDesc(Long needId);

    void deleteByNeedId(Long needId);
}
