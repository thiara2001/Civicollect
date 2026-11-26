package com.civicollect.service;

import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import com.civicollect.dto.AnalyticsDtos.DashboardResponse;
import com.civicollect.dto.AnalyticsDtos.KeyCount;
import com.civicollect.dto.NeedDtos.MapPoint;
import com.civicollect.dto.NeedDtos.NeedFilter;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.repository.NeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Tableaux de bord, cartographie, tendances et exports (RF18-RF21). */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final NeedRepository needs;
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
