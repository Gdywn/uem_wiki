package edu.uemwiki.contrib.controller;

import edu.uemwiki.contrib.common.Result;
import edu.uemwiki.contrib.service.CatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 向投稿端提供只读的学院与专业目录。 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalogService;
    public CatalogController(CatalogService catalogService) { this.catalogService = catalogService; }

    @GetMapping
    public ResponseEntity<Result<?>> getCatalog() {
        try {
            return ResponseEntity.ok(Result.ok("学院专业分类", catalogService.catalog()));
        } catch (CatalogService.CatalogException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ex.getMessage()));
        }
    }
}
