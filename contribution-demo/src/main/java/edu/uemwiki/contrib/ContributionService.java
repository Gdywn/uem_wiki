package edu.uemwiki.contrib;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContributionService {
    private final ContributionMapper mapper;
    private final StaticPublisher publisher;
    private final GitHubPublisher github;
    @Value("${contrib.upload-dir:./data/private-uploads}") private String uploadDir;
    public ContributionService(ContributionMapper mapper, StaticPublisher publisher, GitHubPublisher github) { this.mapper=mapper; this.publisher=publisher; this.github=github; }
    public List<Contribution> list(String status) {
        QueryWrapper<Contribution> q=new QueryWrapper<Contribution>().orderByDesc("created_at");
        if (status!=null && !status.isEmpty()) q.eq("review_status", status);
        return mapper.selectList(q);
    }
    public Contribution get(long id) { return mapper.find(id); }
    public Path file(Contribution c) { return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(c.storedFilename).normalize(); }
    @Transactional
    public long submit(String title,String course,String body,String author,String contact,MultipartFile file) throws IOException {
        if (blank(title)||blank(course)||blank(body)||blank(author)||blank(contact)||file==null||file.isEmpty()) throw new IllegalArgumentException("请填写全部字段并上传附件");
        if (file.getSize()>10L*1024*1024) throw new IllegalArgumentException("附件不能超过 10 MiB");
        String original=Paths.get(file.getOriginalFilename()==null?"attachment":file.getOriginalFilename().replace('\\','/')).getFileName().toString();
        String lower=original.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".pdf")||lower.endsWith(".docx")||lower.endsWith(".pptx"))) throw new IllegalArgumentException("仅支持 PDF、DOCX、PPTX 文件");
        byte[] signature=new byte[8];try(java.io.InputStream in=file.getInputStream()){int count=in.read(signature);if(count<4)throw new IllegalArgumentException("附件内容无效");}
        boolean pdf=lower.endsWith(".pdf")&&signature[0]=='%'&&signature[1]=='P'&&signature[2]=='D'&&signature[3]=='F'&&signature[4]=='-';
        boolean office=!lower.endsWith(".pdf")&&signature[0]=='P'&&signature[1]=='K'&&signature[2]==3&&signature[3]==4;
        if(!pdf&&!office)throw new IllegalArgumentException("附件扩展名与文件类型不匹配");
        if(office) {
            boolean contentTypes=false,document=false;
            try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(file.getInputStream())) {
                java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){String name=entry.getName();if("[Content_Types].xml".equals(name))contentTypes=true;if(lower.endsWith(".docx")&&"word/document.xml".equals(name))document=true;if(lower.endsWith(".pptx")&&"ppt/presentation.xml".equals(name))document=true;}
            } catch(IOException ex){throw new IllegalArgumentException("Office 附件无效");}
            if(!contentTypes||!document)throw new IllegalArgumentException("附件内容与 DOCX / PPTX 格式不符");
        }
        String uuid=UUID.randomUUID().toString().replace("-",""); String stored=uuid+lower.substring(lower.lastIndexOf('.'));
        Path dir=Paths.get(uploadDir).toAbsolutePath().normalize(); Files.createDirectories(dir);
        Path target=dir.resolve(stored).normalize(); if (!target.startsWith(dir)) throw new IllegalArgumentException("附件路径无效");
        file.transferTo(target.toFile());
        Contribution c=new Contribution(); c.title=title.trim();c.courseName=course.trim();c.body=body;c.authorName=author.trim();c.contact=contact.trim();c.originalFilename=original;c.storedFilename=stored;c.fileSize=file.getSize();c.contentType=file.getContentType();c.reviewStatus="PENDING";c.publishStatus="NOT_PUBLISHED";
        try { mapper.insert(c); return c.id; } catch (RuntimeException e) { Files.deleteIfExists(target); throw e; }
    }
    @Transactional
    public void review(long id,String decision) {
        Contribution c=required(id);
        if (!"PENDING".equals(c.reviewStatus)) throw new IllegalStateException("该投稿已审核");
        if (!("APPROVED".equals(decision)||"REJECTED".equals(decision))) throw new IllegalArgumentException("审核状态无效");
        c.reviewStatus=decision; mapper.updateById(c);
    }
    @Transactional
    public synchronized void publish(long id) throws IOException {
        Contribution c=required(id); if (!"APPROVED".equals(c.reviewStatus)) throw new IllegalStateException("只有通过审核的投稿才能发布");
        if ("PUBLISHED".equals(c.publishStatus)) return;
        c.publishStatus="PUBLISHING";c.publishError=null;mapper.updateById(c);
        try { publisher.export(c); github.publish(id); c.publishStatus="PUBLISHED";c.publishError=null; }
        catch (Exception ex) { c.publishStatus="FAILED"; c.publishError=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage(); mapper.updateById(c); if(ex instanceof IOException) throw (IOException)ex; throw new IOException(ex); }
        mapper.updateById(c);
    }
    private Contribution required(long id) { Contribution c=get(id); if(c==null)throw new IllegalArgumentException("投稿不存在"); return c; }
    private boolean blank(String s) { return s==null||s.trim().isEmpty(); }
}
