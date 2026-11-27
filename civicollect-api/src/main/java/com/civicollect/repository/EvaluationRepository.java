package com.civicollect.repository;

import com.civicollect.domain.Evaluation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findByNeedIdAndCriterionId(Long needId, Long criterionId);

    @EntityGraph(attributePaths = {"criterion", "need"})
    List<Evaluation> findByNeedIdIn(Collection<Long> needIds);

    void deleteByNeedId(Long needId);
}
