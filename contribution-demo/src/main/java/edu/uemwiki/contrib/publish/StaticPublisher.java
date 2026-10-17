package edu.uemwiki.contrib.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.uemwiki.contrib.entity.Contribution;
import edu.uemwiki.contrib.service.CatalogService;
import edu.uemwiki.contrib.service.CatalogService.MajorInfo;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
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
    private static final Pattern INVALID_PATH_SEGMENT = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");

    @Value("${contrib.export-dir:./export}") private String exportDir;
    @Value("${contrib.wiki-root:../}") private String wikiRoot;
    @Value("${contrib.upload-dir:./data/private-uploads}") private String uploadDir;

    private final CatalogService catalogService;
    private final ObjectMapper objectMapper;

    public StaticPublisher(CatalogService catalogService, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    private Path out() { return Paths.get(exportDir).toAbsolutePath().normalize(); }

    /** Copy an approved attachment into its catalog-backed directory and refresh the library search index. */
    public void export(Contribution c) throws IOException {
        if (c == null || c.id == null) throw new IOException("投稿 ID 不存在，无法稳定导出");
        if (c.majorId == null || c.majorId.trim().isEmpty()) {
            throw new IOException("该投稿 majorId 为空，不能分类导出；请先补充分配分类");
        }

        MajorInfo category = catalogService.describeMajor(c.majorId);
        if ("未分类".equals(category.collegeName) || "未分类".equals(category.majorName)) {
            throw new IOException("投稿 majorId 未在 catalog.json 中找到，不能猜测分类导出");
        }
        String collegeDirName = safeSegment(category.collegeName, "学院");
        String majorDirName = safeSegment(category.majorName, "专业");

        Path root = out();
        Path wiki = Paths.get(wikiRoot).toAbsolutePath().normalize();
        Path sourceLibrary = wiki.resolve("docs/资料库").normalize();
        if (!sourceLibrary.startsWith(wiki) || !Files.isDirectory(sourceLibrary)) {
            throw new IOException("Wiki 资料库目录不存在，不能解析投稿分类目录");
        }
        Path sourceCollege = sourceLibrary.resolve(collegeDirName).normalize();
        if (!sourceCollege.startsWith(sourceLibrary) || !Files.isDirectory(sourceCollege)) {
            throw new IOException("catalog.json 中的学院目录不存在：" + collegeDirName);
        }
        Path sourceMajor = sourceCollege.resolve(majorDirName).normalize();
        if (!sourceMajor.startsWith(sourceCollege)) {
            throw new IOException("catalog.json 中的专业目录映射无效");
        }
        // 通用资料在源站以单独目录保存；它的 catalog 专业是固定分类名，发布时仍按两级目录落盘。
        if (!catalogService.isGeneralMaterial(c.majorId)
                && !Files.isDirectory(sourceMajor)) {
            throw new IOException("catalog.json 中的专业目录不存在：" + collegeDirName + "/" + majorDirName);
        }

        Path categoryRoot = root.resolve("docs/资料库").normalize();
        Path contributionDir = categoryRoot.resolve(collegeDirName);
        if (!catalogService.isGeneralMaterial(c.majorId)) contributionDir = contributionDir.resolve(majorDirName);
        contributionDir = contributionDir.resolve(String.valueOf(c.id)).normalize();
        if (!contributionDir.startsWith(categoryRoot)) throw new IOException("资料导出目录映射无效");

        String safeName = null;
        Path publicAttachment = null;
        if (c.storedFilename != null || c.originalFilename != null) {
            if (c.storedFilename == null || c.originalFilename == null || c.originalFilename.trim().isEmpty()) {
                throw new IOException("附件元数据不完整，不能导出");
            }
            Path privateDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path attachment = privateDir.resolve(c.storedFilename).normalize();
            if (!attachment.startsWith(privateDir) || !Files.isRegularFile(attachment)) {
                throw new IOException("私有附件不存在或存储路径无效");
            }
            safeName = safeFilename(c.originalFilename);
            Files.createDirectories(contributionDir);
            publicAttachment = contributionDir.resolve(safeName).normalize();
            if (!publicAttachment.startsWith(contributionDir)) throw new IOException("附件文件名映射无效");
            // Copy to the same stable path on retries; the private original remains untouched.
            Files.copy(attachment, publicAttachment, StandardCopyOption.REPLACE_EXISTING);
        }

        copyPublicFiles(wiki, root);
        preserveLibraryHomepage(sourceLibrary, root);
        updateMaterialsIndex(root, c, category, safeName, publicAttachment);
    }

    private void updateMaterialsIndex(Path root, Contribution c, MajorInfo category,
                                      String safeName, Path publicAttachment) throws IOException {
        Path index = root.resolve("assets/materials-index.json").normalize();
        if (!index.startsWith(root)) throw new IOException("资料索引路径无效");
        Files.createDirectories(index.getParent());

        JsonNode document;
        if (Files.isRegularFile(index)) {
            try { document = objectMapper.readTree(index.toFile()); }
            catch (IOException ex) { throw new IOException("本地资料索引 assets/materials-index.json 格式错误", ex); }
        } else {
            document = objectMapper.createArrayNode();
        }

        ArrayNode materials;
        ObjectNode wrappedDocument = null;
        if (document != null && document.isArray()) {
            materials = (ArrayNode) document;
        } else if (document != null && document.isObject() && document.path("materials").isArray()) {
            wrappedDocument = (ObjectNode) document;
            materials = (ArrayNode) wrappedDocument.get("materials");
        } else {
            throw new IOException("本地资料索引格式无效：需要数组或包含 materials 数组的对象");
        }

        String materialId = "submission-" + c.id;
        ArrayNode updated = objectMapper.createArrayNode();
        Iterator<JsonNode> iterator = materials.elements();
        while (iterator.hasNext()) {
            JsonNode current = iterator.next();
            if (!materialId.equals(current.path("id").asText())) updated.add(current);
        }
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", materialId);
        item.put("title", c.title == null ? "未命名资料" : c.title);
        item.put("courseName", c.courseName == null ? "" : c.courseName);
        item.put("description", c.body == null ? "" : c.body);
        item.put("collegeId", category.collegeId);
        item.put("collegeName", category.collegeName);
        item.put("majorId", category.majorId);
        item.put("majorName", category.majorName);
        item.put("authorName", c.authorName == null ? "" : c.authorName);
        item.put("format", "");
        item.put("downloadUrl", "");
        if (safeName != null && publicAttachment != null) {
            String relative = root.relativize(publicAttachment).toString().replace('\\', '/');
            item.put("format", extension(safeName));
            item.put("downloadUrl", "/" + encodePath(relative));
            item.put("originalFilename", c.originalFilename);
        }
        updated.add(item);

        JsonNode output = updated;
        if (wrappedDocument != null) {
            wrappedDocument.set("materials", updated);
            output = wrappedDocument;
        }
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(output);
        Path temporary = Files.createTempFile(index.getParent(), "materials-index-", ".tmp");
        boolean replaced = false;
        try {
            Files.write(temporary, json, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING);
            }
            replaced = true;
        } finally {
            if (!replaced) Files.deleteIfExists(temporary);
        }
    }

    private void preserveLibraryHomepage(Path sourceLibrary, Path root) throws IOException {
        Path source = sourceLibrary.resolve("index.html").normalize();
        Path target = root.resolve("docs/资料库/index.html").normalize();
        if (!source.startsWith(sourceLibrary) || !target.startsWith(root)) throw new IOException("资料库主页路径无效");
        if (!Files.isRegularFile(source)) throw new IOException("Wiki 资料库主页不存在：" + source);
        // Copy the current Wiki homepage only once; subsequent exports preserve local customization.
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            String html = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            // The Wiki page already renders links from materials-index.json. Show the preserved
            // original filename as the link label in this local export copy only.
            String genericLabel = "a.textContent='下载 ↗';";
            String filenameLabel = "a.textContent=m.originalFilename?('下载：'+m.originalFilename+' ↗'):'下载 ↗';";
            if (html.contains(genericLabel)) html = html.replace(genericLabel, filenameLabel);
            Files.write(target, html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String safeSegment(String value, String type) throws IOException {
        if (value == null || value.trim().isEmpty() || ".".equals(value) || "..".equals(value)
                || value.endsWith(".") || value.endsWith(" ") || INVALID_PATH_SEGMENT.matcher(value).find()
                || value.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$")) {
            throw new IOException("catalog.json 中的" + type + "目录名无效，不能分类导出");
        }
        return value;
    }

    private String safeFilename(String original) throws IOException {
        String basename;
        try { basename = Paths.get(original.replace('\\', '/')).getFileName().toString(); }
        catch (RuntimeException ex) { throw new IOException("原始附件名无效", ex); }
        String safe = INVALID_PATH_SEGMENT.matcher(basename).replaceAll("_").trim();
        while (safe.endsWith(".") || safe.endsWith(" ")) safe = safe.substring(0, safe.length() - 1);
        if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) throw new IOException("附件文件名无效");
        if (safe.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$")) safe = "_" + safe;
        return safe;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "FILE" : fileName.substring(dot + 1).toUpperCase(java.util.Locale.ROOT);
    }

    private String encodePath(String path) throws IOException {
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (encoded.length() > 0) encoded.append('/');
            try { encoded.append(URLEncoder.encode(segment, "UTF-8").replace("+", "%20")); }
            catch (Exception ex) { throw new IOException("无法编码附件下载链接", ex); }
        }
        return encoded.toString();
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
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
    }
}
