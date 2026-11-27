package com.civicollect.repository;

import com.civicollect.domain.DuplicateProposal;
import com.civicollect.domain.DuplicateStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DuplicateProposalRepository extends JpaRepository<DuplicateProposal, Long> {

    @EntityGraph(attributePaths = {"sourceNeed", "targetNeed", "reviewedBy"})
    List<DuplicateProposal> findByStatusOrderBySimilarityDesc(DuplicateStatus status);

    @EntityGraph(attributePaths = {"sourceNeed", "targetNeed", "reviewedBy"})
    @Query("select d from DuplicateProposal d where d.sourceNeed.id = :needId or d.targetNeed.id = :needId order by d.similarity desc")
    List<DuplicateProposal> findByNeedId(@Param("needId") Long needId);

    @Query("select d from DuplicateProposal d where (d.sourceNeed.id = :a and d.targetNeed.id = :b) or (d.sourceNeed.id = :b and d.targetNeed.id = :a)")
    Optional<DuplicateProposal> findBetween(@Param("a") Long a, @Param("b") Long b);

    long countByStatus(DuplicateStatus status);

    void deleteBySourceNeedIdOrTargetNeedId(Long sourceId, Long targetId);
}
