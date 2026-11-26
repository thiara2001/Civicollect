package com.civicollect.repository;

import com.civicollect.domain.Criterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CriterionRepository extends JpaRepository<Criterion, Long> {
    List<Criterion> findByActiveTrueOrderByIdAsc();

    List<Criterion> findAllByOrderByIdAsc();

    Optional<Criterion> findByCode(String code);
}
