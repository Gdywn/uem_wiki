package edu.uemwiki.contrib.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adds the static Wiki page, attachment, directory entry, and page assets in one GitHub commit. */
@Component
public class GitHubPublisher {
    @Value("${contrib.github-token:}") private String token;
    @Value("${contrib.github-repo:}") private String repo;
    @Value("${contrib.github-branch:main}") private String branch;
    @Value("${contrib.export-dir:./export}") private String exportDir;
    private final ObjectMapper json = new ObjectMapper();

    public boolean configured() {
        return token != null && !token.trim().isEmpty() && repo != null && !repo.trim().isEmpty();
    }

    public void publish(long id) throws IOException {
        if (!configured()) return;
        String[] parts = repo.split("/");
        if (parts.length != 2) throw new IOException("GITHUB_REPO 格式必须为 owner/repository");
        if ("uem-wiki".equalsIgnoreCase(parts[0]) && "uem_wiki".equalsIgnoreCase(parts[1])) {
            throw new IOException("拒绝向正式 Wiki 仓库发布；请使用独立测试仓库");
        }
        String api = "https://api.github.com/repos/" + parts[0] + "/" + parts[1];
        String ref = "/git/ref/heads/" + encode(branch);
        String commitSha = request("GET", api + ref, null).path("object").path("sha").asText();
        String treeSha = request("GET", api + "/git/commits/" + commitSha, null).path("tree").path("sha").asText();

        Path exportRoot = Paths.get(exportDir).toAbsolutePath().normalize();
        Path articleRoot = exportRoot.resolve("docs/submissions").resolve(String.valueOf(id)).normalize();
        if (!articleRoot.startsWith(exportRoot) || !Files.isDirectory(articleRoot)) {
            throw new IOException("静态投稿页面不存在，无法发布");
        }
        Map<String, Path> files = new LinkedHashMap<String, Path>();
        try (java.util.stream.Stream<Path> stream = Files.walk(articleRoot)) {
            java.util.Iterator<Path> iterator = stream.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                String relative = articleRoot.relativize(file).toString().replace('\\', '/');
                files.put("docs/submissions/" + id + "/" + relative, file);
            }
        }
        Path listing = exportRoot.resolve("docs/submissions/index.html");
        if (Files.isRegularFile(listing)) files.put("docs/submissions/index.html", listing);
        for (String relative : StaticPublisher.PUBLIC_FILES) {
            Path file = exportRoot.resolve(relative).normalize();
            if (file.startsWith(exportRoot) && Files.isRegularFile(file)) files.put(relative, file);
        }

        ObjectNode treeRequest = json.createObjectNode();
        treeRequest.put("base_tree", treeSha);
        ArrayNode tree = treeRequest.putArray("tree");
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            ObjectNode blob = json.createObjectNode();
            blob.put("content", Base64.getEncoder().encodeToString(Files.readAllBytes(entry.getValue())));
            blob.put("encoding", "base64");
            String blobSha = request("POST", api + "/git/blobs", blob).path("sha").asText();
            ObjectNode item = tree.addObject();
            item.put("path", entry.getKey());
            item.put("mode", "100644");
            item.put("type", "blob");
            item.put("sha", blobSha);
        }
        String newTree = request("POST", api + "/git/trees", treeRequest).path("sha").asText();
        ObjectNode body = json.createObjectNode();
        body.put("message", "Publish wiki contribution #" + id);
        body.put("tree", newTree);
        body.putArray("parents").add(commitSha);
        String newCommit = request("POST", api + "/git/commits", body).path("sha").asText();
        ObjectNode update = json.createObjectNode();
        update.put("sha", newCommit);
        update.put("force", false);
        request("PATCH", api + ref, update);
    }

    private JsonNode request(String method, String url, ObjectNode payload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        if (payload != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) { json.writeValue(output, payload); }
        }
        int status = connection.getResponseCode();
        InputStream input = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (input != null) {
            try (InputStream stream = input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
        }
        String response = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) throw new IOException("GitHub API 返回 HTTP " + status + ": " + response);
        return response.isEmpty() ? json.createObjectNode() : json.readTree(response);
    }

    private String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (Exception ex) { throw new IllegalArgumentException(ex); }
    }
}
