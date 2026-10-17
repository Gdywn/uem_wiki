package edu.uemwiki.contrib.controller;

import edu.uemwiki.contrib.common.Result;
import edu.uemwiki.contrib.service.CatalogService;
import edu.uemwiki.contrib.service.ContributionService;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contributions")
public class SubmissionApiController {
    private final ContributionService service;
    public SubmissionApiController(ContributionService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<?>> submit(@ModelAttribute SubmissionRequest request) {
        try {
            long id = service.submit(request.title, request.course, request.majorId, request.body,
                    request.author, request.contact, request.file);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Result.ok("投稿已提交，等待管理员审核", Collections.singletonMap("id", id)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Result.fail(ex.getMessage()));
        } catch (CatalogService.CatalogException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("附件保存失败，请稍后重试"));
        }
    }
}