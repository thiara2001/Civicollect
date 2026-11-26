package com.civicollect.repository;

import com.civicollect.domain.MunicipalService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MunicipalServiceRepository extends JpaRepository<MunicipalService, Long> {
    List<MunicipalService> findAllByOrderByNameAsc();
}
