package edu.uemwiki.contrib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {
    private final ContributionService service;
    @Value("${contrib.admin-user:admin}") private String adminUser;
    @Value("${contrib.admin-password-hash:}") private String passwordHash;
    public AdminApiController(ContributionService service) { this.service = service; }

    @GetMapping("/session")
    public Result<?> session(HttpSession session) {
        return Result.ok("会话状态", data("authenticated", auth(session), "csrfToken", csrf(session)));
    }

    @PostMapping("/login")
    public ResponseEntity<Result<?>> login(@RequestBody LoginRequest request, HttpSession session) {
        boolean ok = request != null && adminUser.equals(request.username) && !passwordHash.isEmpty()
                && new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(request.password == null ? "" : request.password, passwordHash);
        if (!ok) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail("账号或密码错误"));
        session.setAttribute("admin", Boolean.TRUE);
        session.setAttribute("csrf", UUID.randomUUID().toString());
        return ResponseEntity.ok(Result.ok("登录成功", data("csrfToken", csrf(session))));
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<?>> logout(@RequestBody CsrfRequest request, HttpSession session) {
        ResponseEntity<Result<?>> denied = requireAdmin(session);
        if (denied != null) return denied;
        if (!validCsrf(session, request == null ? null : request.csrfToken)) return forbidden();
        session.invalidate();
        return ResponseEntity.ok(Result.ok("已退出登录", null));
    }

    @GetMapping("/contributions")
    public ResponseEntity<Result<?>> list(@RequestParam(required=false) String status, HttpSession session) {
        ResponseEntity<Result<?>> denied = requireAdmin(session);
        if (denied != null) return denied;
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        for (Contribution c : service.list(status)) rows.add(view(c, false));
        return ResponseEntity.ok(Result.ok("投稿列表", rows));
    }

    @GetMapping("/contributions/{id}")
    public ResponseEntity<Result<?>> detail(@PathVariable long id, HttpSession session) {
        ResponseEntity<Result<?>> denied = requireAdmin(session);
        if (denied != null) return denied;
        Contribution c = service.get(id);
        if (c == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail("投稿不存在"));
        return ResponseEntity.ok(Result.ok("投稿详情", view(c, true)));
    }

    @PostMapping("/contributions/{id}/review")
    public ResponseEntity<Result<?>> review(@PathVariable long id, @RequestBody ReviewRequest request, HttpSession session) {
        ResponseEntity<Result<?>> denied = requireAdmin(session);
        if (denied != null) return denied;
        if (!validCsrf(session, request == null ? null : request.csrfToken)) return forbidden();
        try {
            service.review(id, request.decision);
            if ("APPROVED".equals(request.decision)) {
                try { service.publish(id); }
                catch (Exception ex) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("审核已通过，但发布失败：" + safeMessage(ex))); }
            }
            return ResponseEntity.ok(Result.ok("审核完成", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Result.fail(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail(ex.getMessage()));
        }
    }

    @PostMapping("/contributions/{id}/publish")
    public ResponseEntity<Result<?>> publish(@PathVariable long id, @RequestBody CsrfRequest request, HttpSession session) {
        ResponseEntity<Result<?>> denied = requireAdmin(session);
        if (denied != null) return denied;
        if (!validCsrf(session, request == null ? null : request.csrfToken)) return forbidden();
        try {
            service.publish(id);
            return ResponseEntity.ok(Result.ok("发布成功", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("发布失败：" + safeMessage(ex)));
        }
    }

    @GetMapping("/contributions/{id}/attachment")
    public ResponseEntity<?> attachment(@PathVariable long id, HttpSession session) throws IOException {
        ResponseEntity<Result<?>> denied = requireAdmin(session);
        if (denied != null) return denied;
        Contribution c = service.get(id);
        if (c == null || c.storedFilename == null) return ResponseEntity.notFound().build();
        Path file = service.file(c);
        if (file == null || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(c.originalFilename, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentLength(Files.size(file)).body(new InputStreamResource(Files.newInputStream(file)));
    }

    private ResponseEntity<Result<?>> requireAdmin(HttpSession session) {
        return auth(session) ? null : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail("请先登录管理员账号"));
    }
    private ResponseEntity<Result<?>> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail("会话校验失败，请刷新后重试")); }
    private boolean auth(HttpSession session) { return Boolean.TRUE.equals(session.getAttribute("admin")); }
    private String csrf(HttpSession session) {
        Object token = session.getAttribute("csrf");
        if (token == null) { token = UUID.randomUUID().toString(); session.setAttribute("csrf", token); }
        return token.toString();
    }
    private boolean validCsrf(HttpSession session, String token) {
        Object expected = session.getAttribute("csrf");
        return expected instanceof String && MessageDigest.isEqual(((String) expected).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (token == null ? "" : token).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private Map<String,Object> data(Object... values) {
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        for (int i=0;i<values.length;i+=2) result.put((String)values[i], values[i+1]);
        return result;
    }
    private Map<String,Object> view(Contribution c, boolean detail) {
        Map<String,Object> m = data("id",c.id,"title",c.title,"courseName",c.courseName,"authorName",c.authorName,
                "reviewStatus",c.reviewStatus,"publishStatus",c.publishStatus,"publishError",c.publishError,
                "createdAt",c.createdAt,"hasAttachment",c.storedFilename!=null,"originalFilename",c.originalFilename,"fileSize",c.fileSize);
        if (detail) { m.put("body",c.body); m.put("contact",c.contact); }
        return m;
    }
    private String safeMessage(Exception ex) { return ex.getMessage()==null ? ex.getClass().getSimpleName() : ex.getMessage(); }

    public static class LoginRequest { public String username; public String password; }
    public static class CsrfRequest { public String csrfToken; }
    public static class ReviewRequest extends CsrfRequest { public String decision; }
}
