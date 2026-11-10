package com.ayazqureshi.dailywallpaper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Bing's picture-of-the-day feed. Plain Java, so the JVM tests cover it. */
final class Feed {
    static final String HOST = "https://www.bing.com";
    private static final Pattern BASE = Pattern.compile("\"urlbase\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern COPYRIGHT = Pattern.compile("\"copyright\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** What the feed's urlbase looks like: /th?id=OHR.Name_EN-US1234567890. Anything else is refused. */
    private static final Pattern SAFE_BASE = Pattern.compile("/th\\?id=[A-Za-z0-9._-]{1,120}");
    private static final Pattern MARKET = Pattern.compile("[a-z]{2}-[A-Z]{2}");

    private Feed() {}

    /** The feed's address for one of Bing's regional editions. */
    static String url(String market) {
        return HOST + "/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=" + market;
    }

    /** The TV's language and country as a Bing market ("en-IN"), or en-US when either is missing or odd. */
    static String market(Locale locale) {
        String market = locale == null ? "" : locale.getLanguage() + "-" + locale.getCountry();
        return MARKET.matcher(market).matches() ? market : "en-US";
    }

    /** The 1920x1080 picture's address from the feed's JSON, or null when the feed is not what it should be. */
    static String imageUrl(String json) {
        String base = imageBase(json);
        return base == null ? null : HOST + base + "_1920x1080.jpg";
    }

    /** The picture's id in the feed (its urlbase): a new one means a new picture. Null when unreadable. */
    static String imageBase(String json) {
        String base = first(BASE, json);
        return base != null && SAFE_BASE.matcher(base).matches() ? base : null;
    }

    /** The feed's line about the picture (place and photographer), or "". */
    static String caption(String json) {
        String text = first(COPYRIGHT, json);
        return text == null ? "" : text.trim();
    }

    private static String first(Pattern pattern, String json) {
        if (json == null) return null;
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    /** The JSON string escapes the feed uses. */
    static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            if (next == 'u' && i + 4 < text.length()) {
                try {
                    out.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                    i += 4;
                    continue;
                } catch (NumberFormatException ignored) {
                    // fall through and keep the letter
                }
            }
            switch (next) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': case 'b': case 'f': break;
                default: out.append(next);
            }
        }
        return out.toString();
    }
}
