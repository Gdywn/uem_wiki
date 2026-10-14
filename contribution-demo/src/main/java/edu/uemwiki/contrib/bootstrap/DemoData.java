package edu.uemwiki.contrib.bootstrap;

import edu.uemwiki.contrib.mapper.ContributionMapper;
import edu.uemwiki.contrib.entity.Contribution;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoData implements CommandLineRunner {
    private final ContributionMapper mapper;
    @Value("${contrib.upload-dir:./data/demo-private}") private String uploadDir;
    public DemoData(ContributionMapper mapper) { this.mapper=mapper; }
    @Override public void run(String... args) throws Exception {
        if(mapper.selectCount(null)>0)return;
        Path dir=Paths.get(uploadDir).toAbsolutePath().normalize();Files.createDirectories(dir);
        byte[] pdf="%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n% Demo attachment\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(dir.resolve("demo-sample.pdf"),pdf);
        Contribution c=new Contribution();c.title="高等数学期末复习提纲（演示）";c.courseName="高等数学";c.body="这是用于预览投稿审核流程的演示内容。\n正文按纯文本提交，特殊字符会安全转义。\n登录管理员后可查看并下载私有附件，选择通过后会生成独立静态页面。";c.authorName="匿名同学";c.contact="demo@example.invalid（演示）";c.originalFilename="期末复习提纲.pdf";c.storedFilename="demo-sample.pdf";c.contentType="application/pdf";c.fileSize=(long)pdf.length;c.reviewStatus="PENDING";c.publishStatus="NOT_PUBLISHED";mapper.insert(c);
    }
}
