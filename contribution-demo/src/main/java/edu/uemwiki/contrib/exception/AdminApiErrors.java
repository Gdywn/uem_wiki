package edu.uemwiki.contrib.exception;

import edu.uemwiki.contrib.controller.AdminApiController;
import edu.uemwiki.contrib.service.CatalogService;
import edu.uemwiki.contrib.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=AdminApiController.class)
public class AdminApiErrors {
    @ExceptionHandler(CatalogService.CatalogException.class)
    public ResponseEntity<Result<?>> catalogFailure(CatalogService.CatalogException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ex.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> invalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Result.fail("请求数据格式无效"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> unexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("管理员请求处理失败"));
    }
}
