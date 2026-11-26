package com.civicollect.service;

import com.civicollect.util.TextUtils;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Exports CSV et PDF génériques (RF21, RF27). */
@Service
public class ExportService {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** CSV UTF-8 avec BOM (lisible par Excel), séparateur « ; » (usage francophone), protégé contre l'injection de formules. */
    public byte[] csv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(";", headers.stream().map(ExportService::cell).toList())).append("\r\n");
        for (List<String> row : rows) {
            sb.append(String.join(";", row.stream().map(ExportService::cell).toList())).append("\r\n");
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    public byte[] pdf(String title, List<String> notes, List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font noteFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph t = new Paragraph(title, titleFont);
            t.setSpacingAfter(6);
            doc.add(t);
            for (String n : notes) {
                doc.add(new Paragraph(n, noteFont));
            }
            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setHeaderRows(1);
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, headFont));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setBackgroundColor(new java.awt.Color(230, 230, 230));
                table.addCell(c);
            }
            for (List<String> row : rows) {
                for (String v : row) {
                    table.addCell(new PdfPCell(new Phrase(v == null ? "" : v, cellFont)));
                }
            }
            doc.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération PDF impossible", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private static String cell(String v) {
        String s = TextUtils.csvSafe(v);
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
