package edu.uemwiki.contrib.publish;

import edu.uemwiki.contrib.entity.Contribution;
import edu.uemwiki.contrib.util.Html;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StaticPublisher {
    static final List<String> PUBLIC_FILES = Arrays.asList(
            "navigation.json",
            "圆形logo.png",
            "assets/theme.css",
            "assets/site.css",
            "assets/site.js",
            "assets/search-index.json",
            "assets/vendor/twikoo.css",
            "assets/vendor/twikoo.min.js"
    );

    @Value("${contrib.export-dir:./export}") private String exportDir;
    @Value("${contrib.wiki-root:../}") private String wikiRoot;
    @Value("${contrib.upload-dir:./data/private-uploads}") private String uploadDir;

    private Path out() { return Paths.get(exportDir).toAbsolutePath().normalize(); }

    public void export(Contribution c) throws IOException {
        Path root = out();
        Path article = root.resolve("docs/submissions").resolve(String.valueOf(c.id)).normalize();
        if (!article.startsWith(root)) throw new IOException("导出路径无效");
        Files.createDirectories(article);

        Path wiki = Paths.get(wikiRoot).toAbsolutePath().normalize();
        Path templatePath = wiki.resolve("templates/base.html");
        if (!Files.isRegularFile(templatePath)) throw new IOException("Wiki 页面模板不存在: " + templatePath);
        String template = new String(Files.readAllBytes(templatePath), StandardCharsets.UTF_8);
        copyPublicFiles(wiki, root);

        String attachmentLink = "";
        if (c.storedFilename != null && c.originalFilename != null) {
            Path privateDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path attachment = privateDir.resolve(c.storedFilename).normalize();
            if (!attachment.startsWith(privateDir) || !Files.isRegularFile(attachment)) {
                throw new IOException("私有附件不存在");
            }
            String safeName = Paths.get(c.originalFilename.replace('\\', '/')).getFileName().toString();
            Files.copy(attachment, article.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
            String encodedName;
            try { encodedName = java.net.URLEncoder.encode(safeName, "UTF-8").replace("+", "%20"); }
            catch (Exception ex) { throw new IOException(ex); }
            attachmentLink = "<p><a download href=\"" + encodedName + "\">下载附件：" + Html.esc(safeName) + "</a></p>";
        }

        String content = "<h1>" + Html.esc(c.title) + "</h1>"
                + "<p>课程：" + Html.esc(c.courseName) + "</p>"
                + "<p>投稿：" + Html.esc(c.authorName) + "</p>"
                + "<div>" + Html.esc(c.body).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>\n") + "</div>"
                + attachmentLink;
        Files.write(article.resolve("index.html"),
                Html.wikiPage(template, c.title, content).getBytes(StandardCharsets.UTF_8));

        Path listing = root.resolve("docs/submissions/index.html");
        Files.createDirectories(listing.getParent());
        String item = "<li><a href=\"" + c.id + "/\">" + Html.esc(c.title)
                + "</a> <span>" + Html.esc(c.courseName) + "</span></li>";
        String previous = Files.exists(listing)
                ? new String(Files.readAllBytes(listing), StandardCharsets.UTF_8)
                : Html.wikiPage(template, "资料分享", "<h1>课程资料分享</h1><ul id=\"entries\">{{entries}}</ul>");
        String marker = "href=\"" + c.id + "/\"";
        if (!previous.contains(marker)) previous = previous.replace("{{entries}}", item + "{{entries}}");
        previous = previous.replace("{{entries}}", "");
        Files.write(listing, previous.getBytes(StandardCharsets.UTF_8));
    }

    private void copyPublicFiles(Path wiki, Path destination) throws IOException {
        for (String relative : PUBLIC_FILES) {
            Path source = wiki.resolve(relative).normalize();
            if (!source.startsWith(wiki) || !Files.isRegularFile(source)) {
                if ("navigation.json".equals(relative) || "assets/search-index.json".equals(relative)
                        || relative.startsWith("assets/vendor/")) continue;
                throw new IOException("Wiki 发布资源不存在: " + relative);
            }
            Path target = destination.resolve(relative).normalize();
            if (!target.startsWith(destination)) throw new IOException("发布资源路径无效");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
