package edu.uemwiki.contrib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StaticPublisher {
    @Value("${contrib.export-dir:./export}") private String exportDir;
    @Value("${contrib.wiki-root:../}") private String wikiRoot;
    @Value("${contrib.upload-dir:./data/private-uploads}") private String uploadDir;
    private Path out() { return Paths.get(exportDir).toAbsolutePath().normalize(); }
    public void export(Contribution c) throws IOException {
        Path root=out(), article=root.resolve("docs/submissions").resolve(String.valueOf(c.id)).normalize();
        if (!article.startsWith(root)) throw new IOException("导出路径无效");
        Files.createDirectories(article);
        Path source=Paths.get(wikiRoot).toAbsolutePath().normalize();
        for (String asset : new String[]{"theme.css","site.css"}) {
            Path from=source.resolve("assets").resolve(asset);
            if (Files.isRegularFile(from)) { Path dest=root.resolve("assets").resolve(asset); Files.createDirectories(dest.getParent()); Files.copy(from,dest,StandardCopyOption.REPLACE_EXISTING); }
        }
        Path privateDir=Paths.get(uploadDir).toAbsolutePath().normalize();
        Path attachment=privateDir.resolve(c.storedFilename).normalize();
        if (!attachment.startsWith(privateDir) || !Files.isRegularFile(attachment)) throw new IOException("私有附件不存在");
        String safeName=Paths.get(c.originalFilename.replace('\\','/')).getFileName().toString();
        Files.copy(attachment,article.resolve(safeName),StandardCopyOption.REPLACE_EXISTING);
        String encodedName;try{encodedName=java.net.URLEncoder.encode(safeName,"UTF-8").replace("+","%20");}catch(Exception ex){throw new IOException(ex);}
        String body="<h1>"+Html.esc(c.title)+"</h1><p>课程："+Html.esc(c.courseName)+"</p><p>投稿："+Html.esc(c.authorName)+"</p><div>"+Html.esc(c.body).replace("\r\n","\n").replace("\n","<br>\n")+"</div><p><a download href=\""+encodedName+"\">下载附件："+Html.esc(safeName)+"</a></p>";
        Files.write(article.resolve("index.html"),Html.page(c.title,body).getBytes(StandardCharsets.UTF_8));
        Path listing=root.resolve("docs/submissions/index.html");
        String item="<li><a href=\""+c.id+"/\">"+Html.esc(c.title)+"</a> <span>"+Html.esc(c.courseName)+"</span></li>";
        String previous=Files.exists(listing)?new String(Files.readAllBytes(listing),StandardCharsets.UTF_8):Html.page("资料分享","<h1>课程资料分享</h1><ul id=\"entries\">{{entries}}</ul>");
        if (!previous.contains("href=\""+c.id+"/\"")) previous=previous.replace("{{entries}}",item+"{{entries}}");
        previous=previous.replace("{{entries}}",""); Files.write(listing,previous.getBytes(StandardCharsets.UTF_8));
    }
}
