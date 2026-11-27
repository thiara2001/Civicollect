package com.civicollect.service;

import com.civicollect.domain.Category;
import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import com.civicollect.domain.Zone;
import com.civicollect.dto.AnalyticsDtos.*;
import com.civicollect.dto.NeedDtos.MapPoint;
import com.civicollect.dto.NeedDtos.NeedFilter;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.repository.CategoryRepository;
import com.civicollect.repository.NeedRepository;
import com.civicollect.repository.SupportRepository;
import com.civicollect.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Tableaux de bord, cartographie, tendances et exports (RF18-RF21). */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final NeedRepository needs;
    private final ZoneRepository zones;
    private final CategoryRepository categories;
    private final SupportRepository supports;
    private final ExportService exportService;

    /** RF18 + RF20 : répartition par catégorie, quartier, statut, type de demandeur et évolution mensuelle. */
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(LocalDate from, LocalDate to) {
        Instant f = from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant t = to == null ? Instant.now().plus(1, ChronoUnit.DAYS) : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DashboardResponse(
                needs.countAll(f, t),
                toKeyCounts(needs.countByCategory(f, t)),
                toKeyCounts(needs.countByZoneName(f, t)),
                toKeyCounts(needs.countByStatus(f, t)),
                toKeyCounts(needs.countByRequesterType(f, t)),
                needs.countByMonth(f, t).stream()
                        .map(r -> new KeyCount(String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue()),
                                ((Number) r[2]).longValue()))
                        .toList());
    }

    /** RF19 : points géolocalisés filtrables (le front les affiche avec Leaflet / OpenStreetMap). */
    @Transactional(readOnly = true)
    public List<MapPoint> map(NeedFilter filter) {
        Specification<Need> spec = Specification.where(NeedSpecifications.staffVisible())
                .and(NeedSpecifications.hasCoordinates()).and(NeedSpecifications.filter(filter));
        return needs.findAll(spec).stream()
                .map(n -> new MapPoint(n.getId(), n.getTitle(), n.getLatitude(), n.getLongitude(),
                        n.getCategory() != null ? n.getCategory().getName() : null, n.getStatus(), n.getUrgency(),
                        n.getPriorityScore()))
                .toList();
    }

    public record Export(byte[] content, String contentType, String filename) {}

    /** RF21 : export CSV ou PDF des résultats filtrés. */
    @Transactional(readOnly = true)
    public Export export(String format, NeedFilter filter) {
        Specification<Need> spec = Specification.where(NeedSpecifications.staffVisible()).and(NeedSpecifications.filter(filter));
        List<Need> found = needs.findAll(spec);
        if (found.size() > MAX_EXPORT_ROWS) found = found.subList(0, MAX_EXPORT_ROWS);

        List<String> headers = List.of("N° suivi", "Titre", "Catégorie", "Zone", "Urgence", "Statut", "Demandeur",
                "Date de dépôt", "Coût estimé", "Bénéficiaires", "Score");
        List<List<String>> rows = new ArrayList<>();
        for (Need n : found) {
            rows.add(List.of(
                    nz(n.getTrackingNumber()), n.getTitle(),
                    n.getCategory() != null ? n.getCategory().getName() : "",
                    n.getZone() != null ? n.getZone().getName() : "",
                    n.getUrgency().name(), n.getStatus().getLabel(),
                    n.getRequesterType() != null ? n.getRequesterType().name() : "ANONYME",
                    n.getSubmittedAt() != null ? n.getSubmittedAt().toString() : "",
                    n.getEstimatedCost() != null ? n.getEstimatedCost().toPlainString() : "",
                    n.getEstimatedBeneficiaries() != null ? n.getEstimatedBeneficiaries().toString() : "",
                    n.getPriorityScore() != null ? String.valueOf(Math.round(n.getPriorityScore() * 100) / 100.0) : ""));
        }
        String date = LocalDate.now().toString();
        if ("pdf".equalsIgnoreCase(format)) {
            return new Export(exportService.pdf("CiviCollect — Export des besoins",
                    List.of("Généré le " + date + " — " + rows.size() + " besoin(s)"), headers, rows),
                    "application/pdf", "besoins-" + date + ".pdf");
        }
        if ("csv".equalsIgnoreCase(format)) {
            return new Export(exportService.csv(headers, rows), "text/csv; charset=UTF-8", "besoins-" + date + ".csv");
        }
        throw new BusinessRuleException("Format non supporté (pdf ou csv)");
    }

    @Transactional(readOnly = true)
    public GlobalStatsResponse globalStats() {
        List<Need> all = needs.findAll().stream().filter(n -> n.getMergedInto() == null).toList();
        long total = all.stream().filter(n -> n.getStatus() != NeedStatus.BROUILLON).count();
        long drafts = all.stream().filter(n -> n.getStatus() == NeedStatus.BROUILLON).count();
        long pending = all.stream().filter(n -> n.getStatus() == NeedStatus.RECU).count();
        long validated = all.stream().filter(n -> n.getStatus().isValidated()).count();
        long realized = all.stream().filter(n -> n.getStatus() == NeedStatus.REALISE).count();
        long rejected = all.stream().filter(n -> n.getStatus() == NeedStatus.REJETE).count();
        long totalSupports = supports.count();
        double validationRate = total > 0 ? round2(100.0 * validated / total) : 0.0;
        double realizationRate = validated > 0 ? round2(100.0 * realized / validated) : 0.0;
        Double avgScore = all.stream()
                .map(Need::getPriorityScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .mapToObj(AnalyticsService::round2)
                .findFirst()
                .orElse(null);
        List<KeyCount> byRequester = toKeyCounts(
                needs.countByRequesterType(Instant.EPOCH, Instant.now().plus(1, ChronoUnit.DAYS)));
        return new GlobalStatsResponse(total, drafts, pending, validated, realized, rejected,
                totalSupports, validationRate, realizationRate, avgScore, byRequester);
    }

    @Transactional(readOnly = true)
    public List<QuartierAnalysisResponse> analyzeByQuartier() {
        List<Need> submitted = needs.findByStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(NeedStatus.BROUILLON);
        Map<Long, List<Need>> byZone = submitted.stream()
                .filter(n -> n.getZone() != null)
                .collect(Collectors.groupingBy(n -> n.getZone().getId()));
        Map<Long, Long> supportByZone = toIdCountMap(supports.countSupportsPerZone());

        return zones.findAllByOrderByNameAsc().stream().map(z -> {
            List<Need> zn = byZone.getOrDefault(z.getId(), List.of());
            long total = zn.size();
            long pending = zn.stream().filter(n -> n.getStatus() == NeedStatus.RECU).count();
            long validated = zn.stream().filter(n -> n.getStatus().isValidated()).count();
            long realized = zn.stream().filter(n -> n.getStatus() == NeedStatus.REALISE).count();
            Double avgScore = averageScore(zn);
            return new QuartierAnalysisResponse(z.getId(), z.getName(), total, pending, validated, realized,
                    supportByZone.getOrDefault(z.getId(), 0L), avgScore);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryAnalysisResponse> analyzeByCategory() {
        List<Need> submitted = needs.findByStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(NeedStatus.BROUILLON);
        Map<Long, List<Need>> byCat = submitted.stream()
                .filter(n -> n.getCategory() != null)
                .collect(Collectors.groupingBy(n -> n.getCategory().getId()));
        Map<Long, Long> supportByCat = toIdCountMap(supports.countSupportsPerCategory());

        return categories.findAll(Sort.by("name")).stream().map(c -> {
            List<Need> cn = byCat.getOrDefault(c.getId(), List.of());
            long total = cn.size();
            long validated = cn.stream().filter(n -> n.getStatus().isValidated()).count();
            long realized = cn.stream().filter(n -> n.getStatus() == NeedStatus.REALISE).count();
            return new CategoryAnalysisResponse(c.getId(), c.getName(), total, validated, realized,
                    supportByCat.getOrDefault(c.getId(), 0L), averageScore(cn));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<StatusAnalysisResponse> analyzeByStatus() {
        List<Need> all = needs.findAll().stream().filter(n -> n.getMergedInto() == null).toList();
        long total = all.size();
        Map<NeedStatus, Long> counts = all.stream().collect(Collectors.groupingBy(Need::getStatus, Collectors.counting()));
        return Arrays.stream(NeedStatus.values())
                .map(st -> {
                    long c = counts.getOrDefault(st, 0L);
                    double pct = total > 0 ? round2(100.0 * c / total) : 0.0;
                    return new StatusAnalysisResponse(st, st.getLabel(), c, pct);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KeyCount> evolution(LocalDate from, LocalDate to) {
        Instant f = from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant t = to == null ? Instant.now().plus(1, ChronoUnit.DAYS) : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return needs.countByMonth(f, t).stream()
                .map(r -> new KeyCount(String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue()),
                        ((Number) r[2]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuartierComparisonResponse> compareQuartiers() {
        List<Need> submitted = needs.findByStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(NeedStatus.BROUILLON);
        Map<Long, List<Need>> byZone = submitted.stream()
                .filter(n -> n.getZone() != null)
                .collect(Collectors.groupingBy(n -> n.getZone().getId()));
        Map<Long, Long> supportByZone = toIdCountMap(supports.countSupportsPerZone());

        return zones.findAllByOrderByNameAsc().stream().map(z -> {
            List<Need> zn = byZone.getOrDefault(z.getId(), List.of());
            long total = zn.size();
            long realized = zn.stream().filter(n -> n.getStatus() == NeedStatus.REALISE).count();
            long validated = zn.stream().filter(n -> n.getStatus().isValidated()).count();
            Double per1000 = (z.getPopulation() != null && z.getPopulation() > 0)
                    ? round2(total * 1000.0 / z.getPopulation()) : null;
            double realizationRate = validated > 0 ? round2(100.0 * realized / validated) : 0.0;
            return new QuartierComparisonResponse(
                    z.getId(), z.getName(), z.getPopulation(), total, per1000,
                    realized, realizationRate, supportByZone.getOrDefault(z.getId(), 0L), averageScore(zn));
        }).sorted(Comparator.comparingLong(QuartierComparisonResponse::totalContributions).reversed()).toList();
    }

    private static Map<Long, Long> toIdCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) {
                map.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
            }
        }
        return map;
    }

    private static Double averageScore(List<Need> list) {
        return list.stream()
                .map(Need::getPriorityScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .mapToObj(AnalyticsService::round2)
                .findFirst()
                .orElse(null);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static List<KeyCount> toKeyCounts(List<Object[]> rows) {
        List<KeyCount> out = new ArrayList<>();
        for (Object[] r : rows) {
            Object k = r[0];
            String key = k == null ? "NON_RENSEIGNE" : (k instanceof NeedStatus s ? s.name() : k.toString());
            out.add(new KeyCount(key, ((Number) r[1]).longValue()));
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
