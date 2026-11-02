package com.margins.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal, dependency-free .xlsx writer (same look as ReadShelf's export): a Highlights sheet and a Sources sheet. */
final class Xlsx {
    private Xlsx() {}

    private static final String[] H_HEAD = {"Date", "Highlight", "Note", "Tags", "Source", "Author", "Type", "Page / location", "Link", "From app"};
    private static final double[] H_W = {12, 70, 40, 18, 32, 22, 9, 14, 36, 14};
    private static final String[] S_HEAD = {"Title", "Author", "Type", "Highlights", "First", "Latest", "Link"};
    private static final double[] S_W = {44, 24, 9, 11, 12, 12, 40};

    static void write(JSONObject db, OutputStream os) throws Exception {
        JSONArray src = db.optJSONArray("sources");
        List<JSONObject> hs = Store.highlights(db);
        Collections.sort(hs, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) { return b.optString("created").compareTo(a.optString("created")); }
        });
        final Map<String, String> used = Store.lastUsed(db);
        List<JSONObject> sources = Store.recentSources(db);

        ZipOutputStream z = new ZipOutputStream(os);
        put(z, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "</Types>");
        put(z, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>");
        put(z, "docProps/app.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>Margins</Application></Properties>");
        put(z, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Highlights\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Sources\" sheetId=\"2\" r:id=\"rId2\"/></sheets>"
                + "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">Highlights!$A$1:$J$"
                + Math.max(1, hs.size() + 1) + "</definedName><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"1\" hidden=\"1\">Sources!$A$1:$G$"
                + Math.max(1, sources.size() + 1) + "</definedName></definedNames>"
                + "<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>");
        put(z, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>");
        // Styles: 0 normal, 1 header, 2 date, 3 wrapped text, 4 hyperlink, 5 wrapped italic (the highlight)
        put(z, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/></numFmts>"
                + "<fonts count=\"4\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "<font><u/><sz val=\"11\"/><color rgb=\"FF0563C1\"/><name val=\"Calibri\"/></font>"
                + "<font><i/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2F4858\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"6\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"top\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>");

        // ---- Sheet 1: Highlights (newest first)
        Links l1 = new Links();
        StringBuilder s = sheetStart(H_W);
        s.append("<row r=\"1\">");
        for (int i = 0; i < H_HEAD.length; i++) str(s, col(i) + "1", H_HEAD[i], 1);
        s.append("</row>");
        int r = 1;
        for (JSONObject h : hs) {
            r++;
            JSONObject so = Store.find(src, h.optString("sourceId"));
            s.append("<row r=\"").append(r).append("\">");
            date(s, "A" + r, h.optString("created"));
            str(s, "B" + r, h.optString("text"), 5);
            str(s, "C" + r, h.optString("note"), 3);
            str(s, "D" + r, Store.tagsText(h), 3);
            str(s, "E" + r, so == null ? "Unsorted" : Store.title(so), 3);
            str(s, "F" + r, so == null ? "" : so.optString("author"), 0);
            str(s, "G" + r, so == null ? "" : kind(so.optString("kind")), 0);
            str(s, "H" + r, h.optString("location"), 0);
            String link = !h.optString("url").isEmpty() ? h.optString("url") : so == null ? "" : so.optString("url");
            l1.cell(s, "I" + r, link);
            str(s, "J" + r, h.optString("app"), 0);
            s.append("</row>");
        }
        sheetEnd(s, "J", r, l1);
        put(z, "xl/worksheets/sheet1.xml", s.toString());
        l1.rels(z, 1);

        // ---- Sheet 2: Sources (most recently used first). Counts are live formulas over the Highlights sheet.
        Map<String, String[]> span = new HashMap<>();
        for (JSONObject h : hs) {
            String sid = h.optString("sourceId"), d = h.optString("created");
            String[] v = span.get(sid);
            if (v == null) span.put(sid, new String[]{d, d});
            else { if (d.compareTo(v[0]) < 0) v[0] = d; if (d.compareTo(v[1]) > 0) v[1] = d; }
        }
        Links l2 = new Links();
        StringBuilder t = sheetStart(S_W);
        t.append("<row r=\"1\">");
        for (int i = 0; i < S_HEAD.length; i++) str(t, col(i) + "1", S_HEAD[i], 1);
        t.append("</row>");
        int last = Math.max(2, hs.size() + 1);
        r = 1;
        for (JSONObject so : sources) {
            r++;
            String[] v = span.get(so.optString("id"));
            t.append("<row r=\"").append(r).append("\">");
            str(t, "A" + r, Store.title(so), 3);
            str(t, "B" + r, so.optString("author"), 0);
            str(t, "C" + r, kind(so.optString("kind")), 0);
            // Titles can repeat across sources; the formula counts by title, which matches what you see on the sheet.
            t.append("<c r=\"D").append(r).append("\"><f>").append(esc("COUNTIF(Highlights!$E$2:$E$" + last + ",A" + r + ")"))
             .append("</f><v>").append(Store.count(db, so.optString("id"))).append("</v></c>");
            if (v != null) { date(t, "E" + r, v[0]); date(t, "F" + r, v[1]); }
            l2.cell(t, "G" + r, so.optString("url"));
            t.append("</row>");
        }
        sheetEnd(t, "G", r, l2);
        put(z, "xl/worksheets/sheet2.xml", t.toString());
        l2.rels(z, 2);
        z.finish();
    }

    static String kind(String k) { return "book".equals(k) ? "Book" : "web".equals(k) ? "Web" : "Other"; }

    // ------------------------------------------------------------------ sheet helpers

    private static StringBuilder sheetStart(double[] widths) {
        StringBuilder s = new StringBuilder();
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
         .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
         .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
         .append("<cols>");
        for (int i = 0; i < widths.length; i++)
            s.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        return s.append("</cols><sheetData>");
    }

    private static void sheetEnd(StringBuilder s, String lastCol, int lastRow, Links links) {
        s.append("</sheetData>");
        s.append("<autoFilter ref=\"A1:").append(lastCol).append(Math.max(1, lastRow)).append("\"/>");
        if (links.n > 0) s.append("<hyperlinks>").append(links.refs).append("</hyperlinks>");
        s.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>");
        s.append("</worksheet>");
    }

    /** Clickable link cells for one sheet. */
    private static final class Links {
        int n; StringBuilder refs = new StringBuilder(), rels = new StringBuilder();
        void cell(StringBuilder s, String ref, String url) {
            str(s, ref, url, url.isEmpty() ? 0 : 4);
            if (!url.startsWith("http")) return;
            n++;
            refs.append("<hyperlink ref=\"").append(ref).append("\" r:id=\"rId").append(n).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(n)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"")
                .append(esc(url)).append("\" TargetMode=\"External\"/>");
        }
        void rels(ZipOutputStream z, int sheet) throws Exception {
            if (n == 0) return;
            put(z, "xl/worksheets/_rels/sheet" + sheet + ".xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + rels + "</Relationships>");
        }
    }

    private static void put(ZipOutputStream z, String name, String content) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes("UTF-8"));
        z.closeEntry();
    }

    private static String col(int i) { return String.valueOf((char) ('A' + i)); }

    private static void str(StringBuilder s, String ref, String v, int style) {
        if (v == null || v.isEmpty()) return;
        s.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
         .append(esc(v.length() > 32000 ? v.substring(0, 32000) : v)).append("</t></is></c>");
    }

    private static void date(StringBuilder s, String ref, String iso) {
        Double serial = serial(iso);
        if (serial != null) s.append("<c r=\"").append(ref).append("\" s=\"2\"><v>").append(serial.longValue()).append("</v></c>");
        else str(s, ref, iso, 0);
    }

    /** yyyy-MM-dd... -> Excel serial day number. */
    static Double serial(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}.*")) return null;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.substring(0, 10));
            return (double) Math.round((d.getTime() - -2209161600000L) / 86400000.0);
        } catch (Exception e) { return null; }
    }

    static String esc(String v) {
        StringBuilder b = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') continue; // illegal in XML
                    b.append(c);
            }
        }
        return b.toString();
    }
}
