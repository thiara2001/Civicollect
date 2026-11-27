package com.civicollect.service.report;

import java.util.List;

/**
 * Pattern Strategy (SOLID - Ouvert/Fermé & Ségrégation d'interfaces) : format d'export de rapport.
 */
public interface ReportExporter {

    record ReportPayload(byte[] content, String contentType, String extension) {}

    boolean supports(String format);

    ReportPayload export(String title, List<String> notes, List<String> headers, List<List<String>> rows);
}
