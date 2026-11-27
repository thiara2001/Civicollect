package com.civicollect.service.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pattern Builder : construit un classeur Excel (.xlsx / OOXML) multi-feuilles valide
 * sans dépendance externe lourde.
 */
public class ExcelWorkbookBuilder {

    public record SheetData(String name, List<String> headers, List<List<String>> rows) {}

    private final List<SheetData> sheets = new ArrayList<>();

    public ExcelWorkbookBuilder addSheet(String name, List<String> headers, List<List<String>> rows) {
        String safeName = sanitizeSheetName(name);
        sheets.add(new SheetData(safeName, headers, rows));
        return this;
    }

    public byte[] build() {
        if (sheets.isEmpty()) {
            addSheet("Donnees", List.of("Info"), List.of(List.of("Aucune donnée")));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            writeEntry(zip, "[Content_Types].xml", contentTypesXml());
            writeEntry(zip, "_rels/.rels", rootRelsXml());
            writeEntry(zip, "xl/workbook.xml", workbookXml());
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            writeEntry(zip, "xl/styles.xml", stylesXml());
            for (int i = 0; i < sheets.size(); i++) {
                writeEntry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de générer le fichier Excel", e);
        }
        return out.toByteArray();
    }

    private String contentTypesXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private static String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbookXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        sb.append("<sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escapeXml(sheets.get(i).name()))
              .append("\" sheetId=\"").append(i + 1)
              .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets></workbook>");
        return sb.toString();
    }

    private String workbookRelsXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
              .append("Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets.size() + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        sb.append("</Relationships>");
        return sb.toString();
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE6ECF5\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"1\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "</cellXfs></styleSheet>";
    }

    private static String sheetXml(SheetData sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int rowIndex = 1;
        if (sheet.headers() != null && !sheet.headers().isEmpty()) {
            appendRow(sb, rowIndex++, sheet.headers(), true);
        }
        for (List<String> row : sheet.rows()) {
            appendRow(sb, rowIndex++, row, false);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, int rowIndex, List<String> values, boolean header) {
        sb.append("<row r=\"").append(rowIndex).append("\">");
        for (int col = 0; col < values.size(); col++) {
            String ref = colName(col) + rowIndex;
            String style = header ? " s=\"1\"" : "";
            sb.append("<c r=\"").append(ref).append("\"").append(style).append(" t=\"inlineStr\"><is><t>")
              .append(escapeXml(values.get(col) == null ? "" : values.get(col)))
              .append("</t></is></c>");
        }
        sb.append("</row>");
    }

    private static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = (n / 26) - 1;
        }
        return sb.toString();
    }

    private static String sanitizeSheetName(String name) {
        String cleaned = (name == null || name.isBlank() ? "Feuille" : name)
                .replaceAll("[\\\\/?*\\[\\]:]", "_");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private static String escapeXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
