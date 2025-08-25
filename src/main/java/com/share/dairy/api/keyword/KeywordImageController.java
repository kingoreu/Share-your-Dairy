package com.share.dairy.api.keyword;


import com.share.dairy.dto.keyword.keywordImage.CreateRequest;
import com.share.dairy.dto.keyword.keywordImage.WithKeywordsDto;
import com.share.dairy.model.keyword.KeywordImage;
import com.share.dairy.service.keyword.KeywordImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/keyword_images")
// update 추가 바람
public class KeywordImageController {

    private final KeywordImageService service;

    public KeywordImageController(KeywordImageService service) {
        this.service = service;
    }

    // Create
    @PostMapping
    public ResponseEntity<Long> create(@RequestBody CreateRequest req) throws SQLException {
        return ResponseEntity.ok(service.create(req));
    }

    // Read (id)
    @GetMapping("/{id}")
    public ResponseEntity<KeywordImage> get(@PathVariable long id) throws SQLException {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Read (user) — 기본 목록
    @GetMapping
    public ResponseEntity<List<KeywordImage>> listByUser(@RequestParam long userId) throws SQLException {
        return ResponseEntity.ok(service.findByUserId(userId));
    }

    // Read (by user) — 키워드 포함 목록
    @GetMapping(params = {"userId", "withKeywords=true"})
    public ResponseEntity<List<WithKeywordsDto>> listByUserWithKeywords(@RequestParam long userId) throws SQLException {
        return ResponseEntity.ok(service.findWithKeywordsByUserId(userId));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) throws SQLException {
        return service.deleteById(id) > 0 ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}