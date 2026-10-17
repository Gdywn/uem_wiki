package edu.uemwiki.contrib.service.impl;

import edu.uemwiki.contrib.service.ContributionService;
import edu.uemwiki.contrib.mapper.ContributionMapper;
import edu.uemwiki.contrib.entity.Contribution;
import edu.uemwiki.contrib.publish.StaticPublisher;
import edu.uemwiki.contrib.publish.GitHubPublisher;
import edu.uemwiki.contrib.service.CatalogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 负责投稿入库、私有附件保存、审核状态更新及静态发布。 */
@Service
public class ContributionServiceImpl implements ContributionService {
    private final ContributionMapper mapper;
    private final StaticPublisher publisher;
    private final GitHubPublisher github;
    private final CatalogService catalogService;
    @Value("${contrib.upload-dir:./data/private-uploads}") private String uploadDir;
    public ContributionServiceImpl(ContributionMapper mapper, StaticPublisher publisher, GitHubPublisher github, CatalogService catalogService) { this.mapper=mapper; this.publisher=publisher; this.github=github; this.catalogService=catalogService; }
    @Override
    public List<Contribution> list(String status) {
        QueryWrapper<Contribution> q=new QueryWrapper<Contribution>().orderByDesc("created_at");
        if (status!=null && !status.isEmpty()) q.eq("review_status", status);
        return mapper.selectList(q);
    }
    @Override
    public Contribution get(long id) { return mapper.find(id); }
    // 根据数据库中的随机存储名定位私有文件，不使用投稿者提供的路径。
    @Override
    public Path file(Contribution c) { return c==null||c.storedFilename==null?null:Paths.get(uploadDir).toAbsolutePath().normalize().resolve(c.storedFilename).normalize(); }
    // 投稿可不带附件；若上传附件，先校验格式并保存到私有目录，再将元数据写入数据库。
    @Transactional
    @Override
    public long submit(String title,String course,String majorId,String body,String author,String contact,MultipartFile file) throws IOException {
        if (blank(title)||blank(course)||blank(body)||blank(author)||blank(contact)) throw new IllegalArgumentException("请填写全部必填字段");
        catalogService.requireActiveMajor(majorId);
        String original=null, stored=null;
        if(file!=null&&!file.isEmpty()) {
        if (file.getSize()>10L*1024*1024) throw new IllegalArgumentException("附件不能超过 10 MiB");
        original=Paths.get(file.getOriginalFilename()==null?"attachment":file.getOriginalFilename().replace('\\','/')).getFileName().toString();
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
        // 公开文件名与原始文件名分开保存：磁盘使用随机名，原名只作为展示元数据。
        String uuid=UUID.randomUUID().toString().replace("-",""); stored=uuid+lower.substring(lower.lastIndexOf('.'));
        Path dir=Paths.get(uploadDir).toAbsolutePath().normalize(); Files.createDirectories(dir);
        Path target=dir.resolve(stored).normalize(); if (!target.startsWith(dir)) throw new IllegalArgumentException("附件路径无效");
        file.transferTo(target.toFile());
        }
        Contribution c=new Contribution(); c.title=title.trim();c.courseName=course.trim();c.majorId=majorId;c.body=body;c.authorName=author.trim();c.contact=contact.trim();c.originalFilename=original;c.storedFilename=stored;c.fileSize=file==null||file.isEmpty()?null:file.getSize();c.contentType=file==null||file.isEmpty()?null:file.getContentType();c.reviewStatus="PENDING";c.publishStatus="NOT_PUBLISHED";
        // 数据库写入失败时清理刚保存的私有文件，避免留下无记录的附件。
        try { mapper.insert(c); return c.id; } catch (RuntimeException e) { if(stored!=null) Files.deleteIfExists(Paths.get(uploadDir).toAbsolutePath().normalize().resolve(stored)); throw e; }
    }
    @Transactional
    @Override
    public void review(long id,String decision) {
        Contribution c=required(id);
        if (!"PENDING".equals(c.reviewStatus)) throw new IllegalStateException("该投稿已审核");
        if (!("APPROVED".equals(decision)||"REJECTED".equals(decision))) throw new IllegalArgumentException("审核状态无效");
        c.reviewStatus=decision; mapper.updateById(c);
    }
    // 只有审核通过的投稿才能发布；相同 ID 重试会覆盖同一静态路径。
    @Transactional
    @Override
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
