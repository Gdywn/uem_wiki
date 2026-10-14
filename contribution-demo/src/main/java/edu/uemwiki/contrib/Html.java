package edu.uemwiki.contrib;

final class Html {
    private Html() {}
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String page(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"+esc(title)+" - 应大Wiki</title><link rel=\"stylesheet\" href=\"/assets/theme.css\"><link rel=\"stylesheet\" href=\"/assets/site.css\"><body><main class=\"wiki-main\"><p><a href=\"/\">应大Wiki</a> / 资料投稿</p><div class=\"markdown-section\">"+body+"</div></main></body></html>";
    }
}
