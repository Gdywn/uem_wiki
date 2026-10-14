package edu.uemwiki.contrib.controller;

import edu.uemwiki.contrib.service.ContributionService;
import edu.uemwiki.contrib.common.Result;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contributions")
public class SubmissionApiController {
    private final ContributionService service;
    public SubmissionApiController(ContributionService service){this.service=service;}

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<?>> submit(@RequestParam String title,@RequestParam String course,
            @RequestParam String body,@RequestParam String author,@RequestParam String contact,
            @RequestParam(required=false) MultipartFile file) {
        try {
            long id=service.submit(title,course,body,author,contact,file);
            return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok("投稿已提交，等待管理员审核",Collections.singletonMap("id",id)));
        } catch(IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Result.fail(ex.getMessage()));
        } catch(IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("附件保存失败，请稍后重试"));
        }
    }
}
