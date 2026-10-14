package edu.uemwiki.contrib.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Html {
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{(title|content|page_style|page_scripts)\\}\\}");
    private Html() {}

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Render the existing Wiki base template without interpreting user text as template markup. */
    public static String wikiPage(String template, String title, String content) {
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String value;
            switch (matcher.group(1)) {
                case "title": value = esc(title); break;
                case "content": value = content == null ? "" : content; break;
                default: value = "";
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
