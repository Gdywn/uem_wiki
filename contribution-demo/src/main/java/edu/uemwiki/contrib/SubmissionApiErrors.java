package edu.uemwiki.contrib;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes=SubmissionApiController.class)
public class SubmissionApiErrors {
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> missingField(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(Result.fail("请填写全部必填字段"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Result.fail("附件不能超过 10 MiB"));
    }
}
