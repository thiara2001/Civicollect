package com.civicollect.repository;

import com.civicollect.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findAllByOrderByNameAsc();
}
