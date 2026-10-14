package edu.uemwiki.contrib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adds the static page and binary attachment in one GitHub commit. */
@Component
public class GitHubPublisher {
    @Value("${contrib.github-token:}") private String token;
    @Value("${contrib.github-repo:}") private String repo;
    @Value("${contrib.github-branch:main}") private String branch;
    @Value("${contrib.export-dir:./export}") private String exportDir;
    private final ObjectMapper json=new ObjectMapper();
    public boolean configured(){return token!=null&&!token.trim().isEmpty()&&repo!=null&&!repo.trim().isEmpty();}
    public void publish(long id) throws IOException {
        if(!configured())return;
        String[] parts=repo.split("/"); if(parts.length!=2)throw new IOException("GITHUB_REPO 格式必须为 owner/repository");
        if("uem-wiki".equalsIgnoreCase(parts[0])&&"uem_wiki".equalsIgnoreCase(parts[1]))throw new IOException("拒绝向正式 Wiki 仓库发布；请使用独立测试仓库");
        String api="https://api.github.com/repos/"+parts[0]+"/"+parts[1];
        String ref="/git/ref/heads/"+encode(branch);
        String commitSha=request("GET",api+ref,null).path("object").path("sha").asText();
        String treeSha=request("GET",api+"/git/commits/"+commitSha,null).path("tree").path("sha").asText();
        Path root=Paths.get(exportDir).toAbsolutePath().normalize().resolve("docs/submissions").resolve(String.valueOf(id));
        ObjectNode treeRequest=json.createObjectNode();treeRequest.put("base_tree",treeSha);ArrayNode tree=treeRequest.putArray("tree");
        try(java.util.stream.Stream<Path> files=Files.walk(root)) {
            java.util.Iterator<Path> it=files.filter(Files::isRegularFile).iterator();
            while(it.hasNext()) {
                Path file=it.next();ObjectNode blob=json.createObjectNode();blob.put("content",Base64.getEncoder().encodeToString(Files.readAllBytes(file)));blob.put("encoding","base64");
                String blobSha=request("POST",api+"/git/blobs",blob).path("sha").asText();
                ObjectNode item=tree.addObject();item.put("path","docs/submissions/"+id+"/"+root.relativize(file).toString().replace('\\','/'));item.put("mode","100644");item.put("type","blob");item.put("sha",blobSha);
            }
        }
        Path listing=root.getParent().resolve("index.html");
        if(Files.isRegularFile(listing)) {
            ObjectNode blob=json.createObjectNode();blob.put("content",Base64.getEncoder().encodeToString(Files.readAllBytes(listing)));blob.put("encoding","base64");
            String sha=request("POST",api+"/git/blobs",blob).path("sha").asText();ObjectNode item=tree.addObject();item.put("path","docs/submissions/index.html");item.put("mode","100644");item.put("type","blob");item.put("sha",sha);
        }
        String newTree=request("POST",api+"/git/trees",treeRequest).path("sha").asText();
        ObjectNode body=json.createObjectNode();body.put("message","Publish wiki contribution #"+id);body.put("tree",newTree);body.putArray("parents").add(commitSha);
        String newCommit=request("POST",api+"/git/commits",body).path("sha").asText();
        ObjectNode update=json.createObjectNode();update.put("sha",newCommit);update.put("force",false);
        request("PATCH",api+ref,update);
    }
    private JsonNode request(String method,String url,ObjectNode payload) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(30000);
        c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        if(payload!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){json.writeValue(out,payload);}}
        int status=c.getResponseCode();InputStream in=status<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);in.close();}
        String response=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);if(status<200||status>=300)throw new IOException("GitHub API 返回 HTTP "+status+": "+response);
        return response.isEmpty()?json.createObjectNode():json.readTree(response);
    }
    private String encode(String s) { try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);} }
}
