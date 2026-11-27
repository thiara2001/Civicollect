package com.civicollect.service;

import com.civicollect.domain.Category;
import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;
import com.civicollect.domain.Zone;
import com.civicollect.dto.AnalyticsDtos.*;
import com.civicollect.dto.ReportDtos.ReportDescriptor;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.CategoryRepository;
import com.civicollect.repository.NeedRepository;
import com.civicollect.repository.ZoneRepository;
import com.civicollect.service.report.ExcelReportExporter;
import com.civicollect.service.report.ExcelWorkbookBuilder;
import com.civicollect.service.report.ReportExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pattern Facade : unifie la génération des rapports PDF, CSV et Excel à partir des services d'analyse
 * et des stratégies d'export ({@link ReportExporter}).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final NeedRepository needs;
    private final ZoneRepository zones;
    private final CategoryRepository categories;
    private final AnalyticsService analyticsService;
    private final List<ReportExporter> exporters;

    public record GeneratedReport(byte[] content, String contentType, String filename) {}

    public List<ReportDescriptor> availableReports() {
        return List.of(
                new ReportDescriptor("GLOBAL_PDF", "Rapport global PDF",
                        "Synthèse des indicateurs et liste complète des contributions soumises",
                        "/api/rapports/pdf", List.of("pdf")),
                new ReportDescriptor("QUARTIER_REPORT", "Rapport par quartier",
                        "Contributions et indicateurs filtrés pour un quartier donné",
                        "/api/rapports/quartier/{id}", List.of("pdf", "csv", "excel")),
                new ReportDescriptor("CATEGORIE_REPORT", "Rapport par catégorie",
                        "Contributions et indicateurs filtrés pour une catégorie donnée",
                        "/api/rapports/categorie/{id}", List.of("pdf", "csv", "excel")),
                new ReportDescriptor("EXCEL_STATS", "Export Excel des statistiques",
                        "Classeur multi-feuilles (Statistiques globales, Quartiers, Catégories, Contributions)",
                        "/api/rapports/export/excel", List.of("xlsx"))
        );
    }

    @Transactional(readOnly = true)
    public GeneratedReport globalPdf() {
        List<Need> list = needs.findByStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(NeedStatus.BROUILLON);
        GlobalStatsResponse stats = analyticsService.globalStats();
        List<String> notes = List.of(
                "Date de génération : " + LocalDate.now(),
                "Contributions soumises : " + stats.totalContributions()
                        + " | En attente : " + stats.totalPending()
                        + " | Validées : " + stats.totalValidated()
                        + " | Réalisées : " + stats.totalRealized(),
                "Taux de validation : " + stats.validationRate() + "% | Taux de réalisation : " + stats.realizationRate() + "%",
                "Total des soutiens citoyens : " + stats.totalSupports()
        );
        return exportWith("pdf", "CiviCollect — Rapport général des contributions", notes,
                needHeaders(), toNeedRows(list), "rapport-global-" + LocalDate.now());
    }

    @Transactional(readOnly = true)
    public GeneratedReport byQuartier(Long quartierId, String format) {
        Zone zone = zones.findById(quartierId)
                .orElseThrow(() -> new NotFoundException("Quartier introuvable"));
        List<Need> list = needs.findByZoneIdAndStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(
                quartierId, NeedStatus.BROUILLON);
        List<String> notes = List.of(
                "Quartier : " + zone.getName() + (zone.getPopulation() != null ? " (Population : " + zone.getPopulation() + ")" : ""),
                "Nombre de contributions : " + list.size(),
                "Date de génération : " + LocalDate.now()
        );
        return exportWith(format, "CiviCollect — Rapport du quartier " + zone.getName(), notes,
                needHeaders(), toNeedRows(list), "rapport-quartier-" + quartierId + "-" + LocalDate.now());
    }

    @Transactional(readOnly = true)
    public GeneratedReport byCategory(Long categoryId, String format) {
        Category cat = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
        List<Need> list = needs.findByCategoryIdAndStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(
                categoryId, NeedStatus.BROUILLON);
        List<String> notes = List.of(
                "Catégorie : " + cat.getName(),
                "Nombre de contributions : " + list.size(),
                "Date de génération : " + LocalDate.now()
        );
        return exportWith(format, "CiviCollect — Rapport de la catégorie " + cat.getName(), notes,
                needHeaders(), toNeedRows(list), "rapport-categorie-" + categoryId + "-" + LocalDate.now());
    }

    @Transactional(readOnly = true)
    public GeneratedReport exportExcelStatistics() {
        GlobalStatsResponse g = analyticsService.globalStats();
        List<QuartierAnalysisResponse> quartiers = analyticsService.analyzeByQuartier();
        List<CategoryAnalysisResponse> cats = analyticsService.analyzeByCategory();
        List<StatusAnalysisResponse> statuses = analyticsService.analyzeByStatus();
        List<Need> allNeeds = needs.findByStatusNotAndMergedIntoIsNullOrderBySubmittedAtDesc(NeedStatus.BROUILLON);

        ExcelWorkbookBuilder builder = new ExcelWorkbookBuilder();

        builder.addSheet("Statistiques",
                List.of("Indicateur", "Valeur"),
                List.of(
                        List.of("Total contributions soumises", String.valueOf(g.totalContributions())),
                        List.of("Brouillons", String.valueOf(g.totalDrafts())),
                        List.of("En attente de modération", String.valueOf(g.totalPending())),
                        List.of("Validées", String.valueOf(g.totalValidated())),
                        List.of("Réalisées", String.valueOf(g.totalRealized())),
                        List.of("Rejetées", String.valueOf(g.totalRejected())),
                        List.of("Total soutiens", String.valueOf(g.totalSupports())),
                        List.of("Taux de validation (%)", String.valueOf(g.validationRate())),
                        List.of("Taux de réalisation (%)", String.valueOf(g.realizationRate())),
                        List.of("Score moyen de priorité", g.averagePriorityScore() != null ? String.valueOf(g.averagePriorityScore()) : "N/A")
                ));

        List<List<String>> qRows = quartiers.stream().map(q -> List.of(
                q.quartierName(),
                String.valueOf(q.totalContributions()),
                String.valueOf(q.pendingContributions()),
                String.valueOf(q.validatedContributions()),
                String.valueOf(q.realizedContributions()),
                String.valueOf(q.totalSupports()),
                q.averagePriorityScore() != null ? String.valueOf(q.averagePriorityScore()) : ""
        )).toList();
        builder.addSheet("Quartiers",
                List.of("Quartier", "Total", "En attente", "Validées", "Réalisées", "Soutiens", "Score moyen"),
                qRows);

        List<List<String>> cRows = cats.stream().map(c -> List.of(
                c.categoryName(),
                String.valueOf(c.totalContributions()),
                String.valueOf(c.validatedContributions()),
                String.valueOf(c.realizedContributions()),
                String.valueOf(c.totalSupports()),
                c.averagePriorityScore() != null ? String.valueOf(c.averagePriorityScore()) : ""
        )).toList();
        builder.addSheet("Categories",
                List.of("Catégorie", "Total", "Validées", "Réalisées", "Soutiens", "Score moyen"),
                cRows);

        List<List<String>> sRows = statuses.stream().map(s -> List.of(
                s.status().name(), s.label(), String.valueOf(s.count()), s.percentage() + "%"
        )).toList();
        builder.addSheet("Statuts", List.of("Code", "Libellé", "Nombre", "Part"), sRows);

        builder.addSheet("Contributions", needHeaders(), toNeedRows(allNeeds));

        String filename = "statistiques-civicollect-" + LocalDate.now() + ".xlsx";
        return new GeneratedReport(builder.build(), ExcelReportExporter.XLSX_MEDIA_TYPE, filename);
    }

    private GeneratedReport exportWith(String format, String title, List<String> notes,
                                       List<String> headers, List<List<String>> rows, String baseFilename) {
        ReportExporter exporter = exporters.stream()
                .filter(e -> e.supports(format))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Format de rapport non supporté : " + format));
        ReportExporter.ReportPayload payload = exporter.export(title, notes, headers, rows);
        return new GeneratedReport(payload.content(), payload.contentType(), baseFilename + "." + payload.extension());
    }

    private static List<String> needHeaders() {
        return List.of("N° suivi", "Titre", "Catégorie", "Quartier", "Urgence", "Statut",
                "Coût estimé", "Bénéficiaires", "Score");
    }

    private static List<List<String>> toNeedRows(List<Need> list) {
        List<List<String>> rows = new ArrayList<>(list.size());
        for (Need n : list) {
            rows.add(List.of(
                    nz(n.getTrackingNumber()),
                    nz(n.getTitle()),
                    n.getCategory() != null ? n.getCategory().getName() : "",
                    n.getZone() != null ? n.getZone().getName() : "",
                    n.getUrgency().name(),
                    n.getStatus().getLabel(),
                    n.getEstimatedCost() != null ? n.getEstimatedCost().toPlainString() : "",
                    n.getEstimatedBeneficiaries() != null ? n.getEstimatedBeneficiaries().toString() : "",
                    n.getPriorityScore() != null ? String.valueOf(Math.round(n.getPriorityScore() * 100) / 100.0) : ""
            ));
        }
        return rows;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
