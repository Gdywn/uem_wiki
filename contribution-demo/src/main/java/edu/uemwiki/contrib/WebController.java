package edu.uemwiki.contrib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class WebController {
    private final ContributionService service;
    @Value("${contrib.admin-user:admin}") private String adminUser;
    @Value("${contrib.admin-password-hash:}") private String passwordHash;
    public WebController(ContributionService service) { this.service=service; }
    @GetMapping("/") public String home() { return "redirect:/submit"; }
    @GetMapping("/submit") @ResponseBody public String form() {
        return Html.page("资料投稿","<h1>课程资料投稿</h1><p>审核通过后将发布到 Wiki。联系方式仅管理员可见。</p><form method=\"post\" action=\"/submit\" enctype=\"multipart/form-data\"><label>标题<input name=\"title\" required maxlength=\"200\"></label><label>课程名称<input name=\"course\" required maxlength=\"200\"></label><label>正文<textarea name=\"body\" required rows=\"12\"></textarea></label><label>投稿署名<input name=\"author\" required maxlength=\"100\"></label><label>联系方式（仅后台可见）<input name=\"contact\" required maxlength=\"255\"></label><label>附件（PDF / DOCX / PPTX，最大 10 MiB）<input type=\"file\" name=\"file\" accept=\".pdf,.docx,.pptx\" required></label><button>提交</button></form>");
    }
    @PostMapping("/submit") @ResponseBody public String submit(@RequestParam String title,@RequestParam String course,@RequestParam String body,@RequestParam String author,@RequestParam String contact,@RequestParam MultipartFile file) throws IOException {
        try { long id=service.submit(title,course,body,author,contact,file); return Html.page("提交成功","<h1>投稿已提交</h1><p>编号："+id+"。管理员审核后将发布。</p><a href=\"/submit\">继续投稿</a>"); }
        catch(IllegalArgumentException ex) { return Html.page("提交未完成","<h1>提交未完成</h1><p>"+Html.esc(ex.getMessage())+"</p><a href=\"/submit\">返回</a>"); }
    }
    @GetMapping("/admin/login") @ResponseBody public String login() { return Html.page("管理员登录","<h1>管理员登录</h1><form method=\"post\"><label>账号<input name=\"username\" autocomplete=\"username\"></label><label>密码<input type=\"password\" name=\"password\" autocomplete=\"current-password\"></label><button>登录</button></form>"); }
    @PostMapping("/admin/login") public String login(@RequestParam String username,@RequestParam String password,HttpSession session) {
        boolean ok=adminUser.equals(username)&&!passwordHash.isEmpty()&&new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(password,passwordHash);
        if(ok){session.setAttribute("admin",Boolean.TRUE);session.setAttribute("csrf",java.util.UUID.randomUUID().toString());return "redirect:/admin";} return "redirect:/admin/login?error";
    }
    @PostMapping("/admin/logout") public String logout(HttpSession session,@RequestParam String csrf){if(auth(session)&&validCsrf(session,csrf))session.invalidate();return "redirect:/admin/login";}
    @GetMapping("/admin") @ResponseBody public String list(@RequestParam(required=false) String status,HttpSession s) {
        if(!auth(s))return "<a href=\"/admin/login\">管理员登录</a>";
        String token=csrf(s);List<Contribution> rows=service.list(status);StringBuilder b=new StringBuilder("<h1>投稿审核</h1><p><a href=\"/admin?status=PENDING\">待审核</a> · <a href=\"/admin?status=REJECTED\">不通过</a> · <a href=\"/admin\">全部</a></p><form method=\"post\" action=\"/admin/logout\"><input type=\"hidden\" name=\"csrf\" value=\""+token+"\"><button>退出</button></form><table><tr><th>ID</th><th>标题</th><th>审核</th><th>发布</th><th>操作</th></tr>");
        for(Contribution c:rows)b.append("<tr><td>").append(c.id).append("</td><td><a href=\"/admin/entry/").append(c.id).append("\">").append(Html.esc(c.title)).append("</a></td><td>").append(c.reviewStatus).append("</td><td>").append(c.publishStatus).append("</td><td><a href=\"/admin/entry/").append(c.id).append("\">详情</a></td></tr>");
        return Html.page("投稿审核",b.append("</table>").toString());
    }
    @GetMapping("/admin/entry/{id}") @ResponseBody public String detail(@PathVariable long id,HttpSession s) {
        if(!auth(s))return "<a href=\"/admin/login\">管理员登录</a>";Contribution c=service.get(id);if(c==null)return "投稿不存在";
        String token=csrf(s);String b="<h1>"+Html.esc(c.title)+"</h1><p>课程："+Html.esc(c.courseName)+"</p><p>署名："+Html.esc(c.authorName)+"</p><p>联系方式："+Html.esc(c.contact)+"</p><pre>"+Html.esc(c.body)+"</pre><p>附件："+Html.esc(c.originalFilename)+"（"+c.fileSize+" 字节） <a href=\"/admin/entry/"+id+"/attachment\">下载</a></p><p>审核："+c.reviewStatus+"；发布："+c.publishStatus+"；"+Html.esc(c.publishError)+"</p>";
        if("PENDING".equals(c.reviewStatus)) b+="<form method=\"post\" action=\"/admin/entry/"+id+"/review\"><input type=\"hidden\" name=\"csrf\" value=\""+token+"\"><button name=\"decision\" value=\"APPROVED\">通过</button><button name=\"decision\" value=\"REJECTED\">不通过</button></form>";
        if("APPROVED".equals(c.reviewStatus)&&!"PUBLISHED".equals(c.publishStatus)) b+="<form method=\"post\" action=\"/admin/entry/"+id+"/publish\"><input type=\"hidden\" name=\"csrf\" value=\""+token+"\"><button>导出 / 重试</button></form>";
        if("PUBLISHED".equals(c.publishStatus)) b+="<p>静态页面：<code>export/docs/submissions/"+id+"/index.html</code></p>";
        return Html.page("投稿详情",b);
    }
    @PostMapping("/admin/entry/{id}/review") public String review(@PathVariable long id,@RequestParam String decision,@RequestParam String csrf,HttpSession s){if(!auth(s))return "redirect:/admin/login";if(!validCsrf(s,csrf))return "redirect:/admin/entry/"+id;service.review(id,decision);if("APPROVED".equals(decision)){try{service.publish(id);}catch(Exception ignored){}}return "redirect:/admin/entry/"+id;}
    @PostMapping("/admin/entry/{id}/publish") public String publish(@PathVariable long id,@RequestParam String csrf,HttpSession s) {if(!auth(s))return "redirect:/admin/login";if(!validCsrf(s,csrf))return "redirect:/admin/entry/"+id;try{service.publish(id);}catch(Exception ignored){}return "redirect:/admin/entry/"+id;}
    @GetMapping("/admin/entry/{id}/attachment") public ResponseEntity<byte[]> attachment(@PathVariable long id,HttpSession s) throws IOException {
        if(!auth(s))return ResponseEntity.status(HttpStatus.FORBIDDEN).build();Contribution c=service.get(id);if(c==null)return ResponseEntity.notFound().build();Path p=service.file(c);if(!Files.isRegularFile(p))return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(c.originalFilename,java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(Files.readAllBytes(p));
    }
    private boolean auth(HttpSession s){return Boolean.TRUE.equals(s.getAttribute("admin"));}
    private String csrf(HttpSession s){Object t=s.getAttribute("csrf");if(t==null){t=java.util.UUID.randomUUID().toString();s.setAttribute("csrf",t);}return Html.esc(t.toString());}
    private boolean validCsrf(HttpSession s,String token){Object expected=s.getAttribute("csrf");return expected instanceof String&&java.security.MessageDigest.isEqual(((String)expected).getBytes(java.nio.charset.StandardCharsets.UTF_8),(token==null?"":token).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
