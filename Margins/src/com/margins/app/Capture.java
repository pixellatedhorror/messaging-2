package com.margins.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever another app shares into: the passage itself, plus where it came from.
 *
 * Handles, among others:
 *   Chrome / browsers   "passage"\nhttps://site/page#:~:text=...
 *   Kindle              "passage"\n\nFrom: Title by Author\nhttps://read.amazon.com/kp/kshare?...
 *   Apple-style books   "passage"\n\nExcerpt From\nTitle\nAuthor\nThis material may be protected by copyright.
 *   Play Books & co     "passage" — Title by Author https://books.google...
 *   PDF readers         just the passage (title, if any, arrives as the share subject)
 * Pure Java so it can be tested off-device.
 */
final class Capture {
    private Capture() {}

    static final class Result {
        String quote = "", title = "", author = "", location = "";
        /** Page the passage is on, used to recognise the same source next time (web only). */
        String sourceUrl = "";
        /** Link straight to the passage, when the app provides one (Chrome's #:~:text= links, Kindle share links). */
        String passageUrl = "";
        String kind = "other"; // web | book | other

        boolean hasSource() { return !sourceUrl.isEmpty() || !title.isEmpty(); }
    }

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOK_HOST = Pattern.compile(
            "(?i)^(?:[\\w-]+\\.)*(?:amazon\\.[a-z.]+|amzn\\.[a-z.]+|a\\.co|kindle\\.[a-z.]+|read\\.amazon\\.[a-z.]+"
            + "|books\\.google\\.[a-z.]+|play\\.google\\.com|goodreads\\.com|kobo\\.com|books\\.apple\\.com|scribd\\.com"
            + "|everand\\.com|libbyapp\\.com|overdrive\\.com|storygraph\\.com)$");
    private static final Pattern BOILERPLATE = Pattern.compile(
            "(?i)^(?:this material may be protected by copyright\\.?|start reading (?:it|this book) for free:?|"
            + "read (?:it|more) (?:for free|on kindle|at):?|check out this book on [\\w ]+:?|shared from [\\w ]+|"
            + "download (?:the )?kindle app.*|get (?:it|this book) on [\\w ]+:?|sent from my \\w+.*|via [\\w ]+ app)$");
    private static final Pattern LOCATION = Pattern.compile(
            "(?i)^[\\(\\[]?\\s*((?:page|pg\\.?|p\\.|location|loc\\.?|chapter|ch\\.)\\s*[\\w.-]+(?:\\s*[,·|-]\\s*(?:page|pg\\.?|p\\.|location|loc\\.?)\\s*[\\w.-]+)?)\\s*[\\)\\]]?\\.?$");
    private static final Pattern FROM_BY = Pattern.compile("(?i)^(?:(?:an? )?(?:excerpt|quote|passage|highlight)\\s+)?from:?\\s+(.+?)\\s+by\\s+(.+?)\\.?$");
    private static final Pattern FROM_ONLY = Pattern.compile("(?i)^(?:excerpt|quote|passage)\\s+from:?\\s*(.*)$");
    private static final Pattern DASH = Pattern.compile("^(?:[—–]|--?)\\s*(.+)$");
    private static final Pattern BY = Pattern.compile("(?i)^(.{1,160}?)\\s+by\\s+(.{1,120}?)\\.?$");
    private static final Pattern LEADING_READING = Pattern.compile("(?i)^i'?m reading\\s+(.+?)\\s+by\\s+(.+?)(?:\\s+(?:and|on)\\b.*)?[.:]?$");

    /** Opening and closing quotation marks we strip from around a passage. */
    private static final String OPEN_Q = "\"“„«‘'「『", CLOSE_Q = "\"”“»’'」』";

    static Result parse(String text, String subject) {
        Result r = new Result();
        String t = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ').trim();

        // ---- links
        List<String> urls = new ArrayList<>();
        Matcher m = URL.matcher(t);
        while (m.find()) urls.add(trimUrl(m.group()));
        for (String u : urls) t = t.replace(u, " ");
        t = t.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n[ \\t]+", "\n").replaceAll("[ \\t]{2,}", " ").trim();

        String url = urls.isEmpty() ? "" : urls.get(0);
        boolean bookLink = !url.isEmpty() && isBookHost(url);

        // ---- drop boilerplate lines ("This material may be protected by copyright.", "Start reading it for free:" ...)
        StringBuilder kept = new StringBuilder();
        for (String line : t.split("\n", -1)) {
            String l = line.trim();
            if (!l.isEmpty() && BOILERPLATE.matcher(l).matches()) continue;
            kept.append(line).append('\n');
        }
        t = kept.toString().trim();

        // ---- split the passage from any attribution around it
        String quote, extra;
        String[] q = splitQuoted(t);
        if (q != null) { quote = q[0]; extra = q[1]; }
        else {
            // Unquoted: peel attribution-looking lines off the end (and an "I'm reading X by Y" line off the start).
            List<String> lines = new ArrayList<>();
            for (String l : t.split("\n", -1)) lines.add(l);
            StringBuilder ex = new StringBuilder();
            if (!lines.isEmpty() && LEADING_READING.matcher(lines.get(0).trim()).matches()) {
                ex.append(lines.remove(0).trim()).append('\n');
            }
            int peeled = 0;
            while (lines.size() > 1 && peeled < 5) {
                String last = lines.get(lines.size() - 1).trim();
                if (last.isEmpty()) { lines.remove(lines.size() - 1); continue; }
                boolean excerptBlock = false;
                // Apple-style block: "Excerpt From" / Title / Author at the end
                for (int k = Math.max(0, lines.size() - 4); k < lines.size(); k++)
                    if (FROM_ONLY.matcher(lines.get(k).trim()).matches()) { excerptBlock = true;
                        StringBuilder b = new StringBuilder();
                        for (int j = k; j < lines.size(); j++) b.append(lines.get(j).trim()).append('\n');
                        ex.insert(0, b);
                        while (lines.size() > k) lines.remove(lines.size() - 1);
                        break; }
                if (excerptBlock) { peeled = 99; break; }
                if (isAttribution(last, bookLink)) { ex.insert(0, last + "\n"); lines.remove(lines.size() - 1); peeled++; }
                else break;
            }
            StringBuilder b = new StringBuilder();
            for (String l : lines) b.append(l).append('\n');
            quote = b.toString().trim();
            extra = ex.toString();
        }

        // ---- read the attribution
        String[] ex = extra.split("\n");
        for (int i = 0; i < ex.length; i++) {
            String l = ex[i].trim().replaceAll("^[:,;·|]+\\s*|\\s*[:,;·|]+$", "").trim();
            if (l.isEmpty()) continue;
            Matcher x;
            if ((x = LOCATION.matcher(l)).matches()) { if (r.location.isEmpty()) r.location = x.group(1).trim(); continue; }
            if ((x = LEADING_READING.matcher(l)).matches()) { setBook(r, x.group(1), x.group(2)); continue; }
            if ((x = FROM_BY.matcher(l)).matches()) { setBook(r, x.group(1), x.group(2)); continue; }
            if ((x = FROM_ONLY.matcher(l)).matches()) {
                // "Excerpt From" then Title and Author on the next lines (or inline after the colon)
                List<String> rest = new ArrayList<>();
                if (!x.group(1).trim().isEmpty()) rest.add(x.group(1).trim());
                while (i + 1 < ex.length && rest.size() < 2) {
                    String n = ex[i + 1].trim();
                    if (n.isEmpty() || LOCATION.matcher(n).matches()) break;
                    rest.add(n); i++;
                }
                if (!rest.isEmpty()) {
                    Matcher by = BY.matcher(rest.get(0));
                    if (rest.size() == 1 && by.matches()) setBook(r, by.group(1), by.group(2));
                    else setBook(r, rest.get(0), rest.size() > 1 ? rest.get(1) : "");
                }
                continue;
            }
            if ((x = DASH.matcher(l)).matches()) { dashAttribution(r, x.group(1).trim()); continue; }
            if ((x = BY.matcher(l)).matches() && r.title.isEmpty()) { setBook(r, x.group(1), x.group(2)); continue; }
            if (r.title.isEmpty() && l.length() <= 160) r.title = l;            // a bare title line
            else if (r.author.isEmpty() && l.length() <= 80 && !r.title.isEmpty()) r.author = l;
        }

        // A trailing "(p. 12)" on the passage itself
        Matcher loc = Pattern.compile("(?i)\\s*[\\(\\[]((?:page|pg\\.?|p\\.|location|loc\\.?)\\s*[\\w.-]+)[\\)\\]]\\s*$").matcher(quote);
        if (r.location.isEmpty() && loc.find()) { r.location = loc.group(1); quote = quote.substring(0, loc.start()); }

        r.quote = stripQuotes(quote).replaceAll("\\n{3,}", "\n\n").trim();

        // ---- where it's from
        if (!url.isEmpty()) {
            if (bookLink || !r.title.isEmpty() && !r.author.isEmpty() && !"web".equals(r.kind)) {
                // Book share links differ for every highlight, so books are recognised by title instead.
                r.kind = "book";
                r.passageUrl = url;
            } else {
                r.kind = "web";
                r.sourceUrl = Urls.clean(url);
                r.passageUrl = url.contains("#:~:text=") ? url : "";
            }
        } else if (!r.title.isEmpty()) r.kind = "book";

        String s = subject == null ? "" : subject.trim();
        if (r.title.isEmpty() && !s.isEmpty() && !looksLikeQuote(s, r.quote) && URL.matcher(s).replaceAll("").trim().length() > 0) {
            s = URL.matcher(s).replaceAll("").trim();
            s = s.replaceFirst("(?i)\\.(pdf|epub|docx?|txt|mobi)$", "");
            Matcher by = BY.matcher(s);
            if ("web".equals(r.kind)) r.title = s;
            else if (by.matches()) setBook(r, by.group(1), by.group(2));
            else { r.title = s; if (r.kind.equals("other") && s.length() < 160) r.kind = r.sourceUrl.isEmpty() ? "other" : "web"; }
        }
        r.title = r.title.length() > 300 ? r.title.substring(0, 300) : r.title;
        return r;
    }

    // ------------------------------------------------------------------ helpers

    /** "quoted passage" + attribution (either side). Returns {passage, attribution} or null if not quoted. */
    static String[] splitQuoted(String t) {
        if (t.length() < 2) return null;
        // quote first, attribution after
        if (OPEN_Q.indexOf(t.charAt(0)) >= 0) {
            int end = lastClosingQuote(t);
            if (end > 0) {
                String after = t.substring(end + 1).trim();
                if (after.isEmpty() || after.length() <= 400 && attributionOnly(after))
                    return new String[]{t.substring(1, end), after};
            }
        }
        // attribution first (one or two short lines), quote after
        int nl = t.indexOf('\n');
        if (nl > 0 && nl <= 300) {
            String rest = t.substring(nl).trim();
            if (rest.length() > 1 && OPEN_Q.indexOf(rest.charAt(0)) >= 0 && CLOSE_Q.indexOf(rest.charAt(rest.length() - 1)) >= 0)
                return new String[]{rest.substring(1, rest.length() - 1), t.substring(0, nl)};
        }
        return null;
    }

    private static int lastClosingQuote(String t) {
        for (int i = t.length() - 1; i > 0; i--) if (CLOSE_Q.indexOf(t.charAt(i)) >= 0) {
            // ignore apostrophes inside words (don't, it's)
            if (t.charAt(i) == '\'' || t.charAt(i) == '’') {
                boolean wordBefore = Character.isLetter(t.charAt(i - 1));
                boolean wordAfter = i + 1 < t.length() && Character.isLetter(t.charAt(i + 1));
                if (wordBefore && wordAfter) continue;
            }
            return i;
        }
        return -1;
    }

    /** Every non-empty line after the closing quote looks like attribution/location, not more prose. */
    private static boolean attributionOnly(String s) {
        for (String l : s.split("\n")) {
            String x = l.trim();
            if (x.isEmpty()) continue;
            if (x.length() > 200) return false;
        }
        return true;
    }

    private static boolean isAttribution(String l, boolean bookLink) {
        if (LOCATION.matcher(l).matches() || FROM_BY.matcher(l).matches()) return true;
        if (DASH.matcher(l).matches() && l.length() <= 160) return true;
        return bookLink && BY.matcher(l).matches() && !l.endsWith(".");
    }

    private static void dashAttribution(Result r, String s) {
        Matcher by = BY.matcher(s);
        if (by.matches()) { setBook(r, by.group(1), by.group(2)); return; }
        int c = s.indexOf(',');
        if (c > 0) { // "— Author, Title"
            if (r.author.isEmpty()) r.author = clean(s.substring(0, c));
            if (r.title.isEmpty()) r.title = clean(s.substring(c + 1));
        } else if (r.author.isEmpty()) r.author = clean(s);
        if (!r.title.isEmpty()) r.kind = "book";
    }

    private static void setBook(Result r, String title, String author) {
        if (r.title.isEmpty()) r.title = clean(title);
        if (r.author.isEmpty()) r.author = clean(author);
        r.kind = "book";
    }

    private static String clean(String s) {
        s = s.trim().replaceAll("\\s+", " ");
        s = s.replaceAll("^[\"“'‘*_]+|[\"”'’*_]+$", "").trim();
        return s.replaceAll("[\\s,;:–—-]+$", "").trim();
    }

    static String stripQuotes(String s) {
        s = s.trim();
        while (s.length() >= 2 && OPEN_Q.indexOf(s.charAt(0)) >= 0 && CLOSE_Q.indexOf(s.charAt(s.length() - 1)) >= 0)
            s = s.substring(1, s.length() - 1).trim();
        return s;
    }

    private static boolean looksLikeQuote(String subject, String quote) {
        String a = subject.replaceAll("\\W+", "").toLowerCase(Locale.ROOT), b = quote.replaceAll("\\W+", "").toLowerCase(Locale.ROOT);
        return !a.isEmpty() && (b.startsWith(a) || a.startsWith(b));
    }

    static boolean isBookHost(String url) {
        String h = Urls.host(url);
        if (h.equals("play.google.com")) return url.contains("/books");
        return BOOK_HOST.matcher(h).matches();
    }

    private static String trimUrl(String u) {
        while (!u.isEmpty() && ".,;:!?)]}»”’'".indexOf(u.charAt(u.length() - 1)) >= 0) {
            if (u.endsWith(")") && u.contains("(")) break;
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
