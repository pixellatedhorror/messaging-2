package com.margins.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Urls {
    private Urls() {}
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    /** First http(s) link in a blob of shared text, or null. */
    static String find(String text) {
        if (text == null) return null;
        Matcher m = URL.matcher(text);
        if (!m.find()) return null;
        String u = m.group();
        while (!u.isEmpty() && ".,;:!?)]}»”’".indexOf(u.charAt(u.length() - 1)) >= 0) {
            // keep a closing paren if the URL also contains an opening one (e.g. Wikipedia)
            if (u.endsWith(")") && u.contains("(")) break;
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Remove tracking parameters and fragments so the same article shared twice is detected. */
    static String clean(String url) {
        if (url == null) return "";
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        int q = u.indexOf('?');
        if (q < 0) return u;
        String base = u.substring(0, q);
        StringBuilder kept = new StringBuilder();
        for (String p : u.substring(q + 1).split("&")) {
            if (p.isEmpty()) continue;
            String name = p.split("=", 2)[0].toLowerCase();
            if (name.startsWith("utm_") || name.equals("fbclid") || name.equals("gclid")
                    || name.equals("mc_cid") || name.equals("mc_eid") || name.equals("igshid")
                    || name.equals("ref_src") || name.equals("smid") || name.equals("cmpid")
                    || name.equals("s") && base.contains("twitter.com") || name.equals("share")
                    || name.equals("sh") || name.equals("si") || name.equals("ocid")) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(p);
        }
        return kept.length() == 0 ? base : base + "?" + kept;
    }

    /** Comparison key: cleaned, no scheme, no www., no trailing slash, lower-case host. */
    static String key(String url) {
        String u = clean(url);
        u = u.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "").replaceFirst("(?i)^m\\.", "");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        int slash = u.indexOf('/');
        return slash < 0 ? u.toLowerCase() : u.substring(0, slash).toLowerCase() + u.substring(slash);
    }

    static String host(String url) {
        String u = url.replaceFirst("(?i)^https?://", "");
        int end = u.length();
        for (char c : new char[]{'/', '?', '#', ':'}) { int i = u.indexOf(c); if (i >= 0 && i < end) end = i; }
        return u.substring(0, end).toLowerCase().replaceFirst("^www\\.", "");
    }
}
