package com.civicollect.repository;

import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NeedRepository extends JpaRepository<Need, Long>, JpaSpecificationExecutor<Need> {

    @Override
    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    Page<Need> findAll(Specification<Need> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    List<Need> findAll(Specification<Need> spec);

    Optional<Need> findByTrackingNumber(String trackingNumber);

    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    Page<Need> findByRequesterId(Long requesterId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    Page<Need> findByStatusAndMergedIntoIsNull(NeedStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "zone"})
    List<Need> findByStatusInAndMergedIntoIsNull(Collection<NeedStatus> statuses);

    List<Need> findByMergedIntoIn(Collection<Need> parents);

    List<Need> findByMergedInto(Need parent);

    List<Need> findTop200ByCategoryIdAndIdNotAndMergedIntoIsNullAndStatusInOrderBySubmittedAtDesc(
            Long categoryId, Long id, Collection<NeedStatus> statuses);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByZoneId(Long zoneId);

    boolean existsByMergedIntoId(Long mergedIntoId);

    long countByMergedIntoIsNull();

    long countByStatusAndMergedIntoIsNull(NeedStatus status);

    @EntityGraph(attributePaths = {"category", "zone"})
    List<Need> findByStatusInAndMergedIntoIsNullOrderBySubmittedAtDesc(Collection<NeedStatus> statuses);

    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    List<Need> findByStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(NeedStatus status);

    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    List<Need> findByZoneIdAndStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(Long zoneId, NeedStatus status);

    @EntityGraph(attributePaths = {"category", "zone", "assignedService"})
    List<Need> findByCategoryIdAndStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(Long categoryId, NeedStatus status);

    // ---------- Statistiques (RF18, RF20) ----------
    String FILTER = " where n.status <> com.civicollect.domain.NeedStatus.BROUILLON and n.mergedInto is null"
            + " and n.submittedAt between :from and :to ";

    @Query("select c.name, count(n) from Need n join n.category c" + FILTER + "group by c.name order by count(n) desc")
    List<Object[]> countByCategory(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(z.name, 'Non renseigné'), count(n) from Need n left join n.zone z" + FILTER
            + "group by z.name order by count(n) desc")
    List<Object[]> countByZoneName(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select n.status, count(n) from Need n" + FILTER + "group by n.status")
    List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select n.requesterType, count(n) from Need n" + FILTER + "group by n.requesterType")
    List<Object[]> countByRequesterType(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select year(n.submittedAt), month(n.submittedAt), count(n) from Need n" + FILTER
            + "group by year(n.submittedAt), month(n.submittedAt) order by year(n.submittedAt), month(n.submittedAt)")
    List<Object[]> countByMonth(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(n) from Need n" + FILTER)
    long countAll(@Param("from") Instant from, @Param("to") Instant to);

    /** Nombre de besoins déposés par zone (base du critère d'équité territoriale). */
    @Query("select n.zone.id, count(n) from Need n where n.zone is not null"
            + " and n.status <> com.civicollect.domain.NeedStatus.BROUILLON and n.mergedInto is null group by n.zone.id")
    List<Object[]> countPerZoneId();
}
